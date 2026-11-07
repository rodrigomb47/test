package com.factionclash.campaign;

import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import com.factionclash.util.ChunkLoading;
import com.factionclash.util.Messages;
import com.factionclash.work.SmelterJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The autonomous faction AI, run per base on a bounded schedule (spec 4, 20): replaces missing workers, grows the
 * garrison, queues construction, finds damage to repair, launches raids on known targets and runs Hollow lairs.
 */
public final class FactionDirector {
    private static final int PERIOD = 100;
    private static final int REPAIR_SCAN_BUDGET = 384;
    /** Last recruitment decision per base, for the station's information panel. */
    public static final Map<Integer, String> LAST_RECRUIT_NOTE = new HashMap<>();
    private static final Map<UUID, Long> MISSING_SINCE = new HashMap<>();

    private FactionDirector() {
    }

    public static void tick(MinecraftServer server, CampaignData data, long t) {
        ServerLevel level = server.overworld();
        if (t % 20 == 0) {
            for (BaseState b : data.bases.values()) {
                if (!b.destroyed && b.isSettlement() && level.isLoaded(b.center)) {
                    defenceAlarm(level, b);
                }
            }
        }
        for (BaseState b : List.copyOf(data.bases.values())) {
            if (b.destroyed || (t + b.id * 17L) % PERIOD != 0 || !level.isLoaded(b.center)) {
                continue;
            }
            if (b.isSettlement()) {
                settlement(level, data, b, t);
            } else {
                lair(level, data, b, t);
            }
        }
    }

    // ------------------------------------------------------------------ settlements

    private static void settlement(ServerLevel level, CampaignData data, BaseState b, long t) {
        FactionState f = data.faction(b.faction);
        if (!f.alive) {
            return;
        }
        BaseBuilder.ensureAnimalAndFishingWorkplaces(data, b);
        rosterUpkeep(level, data, f, b, t);
        recruitment(level, data, f, b, t);
        projects(level, data, b, t);
        repairScan(level, data, b);
        raids(level, data, f, b, t);
    }

    private static void rosterUpkeep(ServerLevel level, CampaignData data, FactionState f, BaseState b, long t) {
        for (NpcRecord r : f.roster.values()) {
            if (!r.alive || r.baseId != b.id) {
                continue;
            }
            Entity e = level.getEntity(r.uuid);
            if (e == null && level.isPositionEntityTicking(r.lastPos) && t - r.lastSeen > 2400) {
                r.alive = false;
                data.setDirty();
                Messages.toFaction(level.getServer(), b.faction, Component.literal(r.name + " (" + r.role.displayName + ") is missing and presumed lost"));
            }
        }
    }

    private static int living(FactionState f, BaseState b, NpcRole role) {
        int n = 0;
        for (NpcRecord r : f.roster.values()) {
            if (r.alive && r.baseId == b.id && r.role == role) {
                n++;
            }
        }
        return n;
    }

    private static int livingAll(FactionState f, BaseState b) {
        int n = 0;
        for (NpcRecord r : f.roster.values()) {
            if (r.alive && r.baseId == b.id) {
                n++;
            }
        }
        return n;
    }

    public record Need(NpcRole role, String reason) {
    }

    /** Recruitment needs in priority order. Recruits always have a job or workplace to go to. */
    public static List<Need> needs(ServerLevel level, CampaignData data, BaseState b, long t) {
        FactionState f = data.faction(b.faction);
        List<Need> out = new ArrayList<>();
        for (NpcRole r : new NpcRole[]{NpcRole.MINER, NpcRole.WOODCUTTER, NpcRole.FARMER, NpcRole.BUILDER, NpcRole.BLACKSMITH,
                NpcRole.CHEF, NpcRole.SCOUT}) {
            if (living(f, b, r) == 0) {
                out.add(new Need(r, "replacing the missing " + r.displayName.toLowerCase()));
            }
        }
        int scouts = living(f, b, NpcRole.SCOUT);
        if (scouts == 1) {
            for (NpcRecord r : f.roster.values()) {
                if (r.alive && r.baseId == b.id && r.role == NpcRole.SCOUT && r.departedAt > 0
                        && t - r.departedAt > FCConfig.SCOUT_OVERDUE_TICKS.get()) {
                    out.add(new Need(NpcRole.SCOUT, "follow-up for overdue scout " + r.name));
                }
            }
        }
        int sw = living(f, b, NpcRole.SWORDSMAN);
        int ar = living(f, b, NpcRole.ARCHER);
        if (sw < 3) {
            out.add(new Need(NpcRole.SWORDSMAN, "home defence"));
        }
        int[] pen = penCounts(level, data, b);
        if (living(f, b, NpcRole.ANIMAL_CARER) == 0 && pen[0] >= 2) {
            out.add(new Need(NpcRole.ANIMAL_CARER, "animals in the pen need breeding and care"));
        }
        if (living(f, b, NpcRole.BUTCHER) == 0 && pen[1] > com.factionclash.work.ButcherJob.BREEDING_STOCK + 1) {
            out.add(new Need(NpcRole.BUTCHER, "surplus livestock"));
        }
        if (living(f, b, NpcRole.SMELTER) == 0 && FactionStorage.count(level, b, SmelterJob::isOre) >= 16) {
            out.add(new Need(NpcRole.SMELTER, "raw ore backlog"));
        }
        if (living(f, b, NpcRole.FISHERMAN) == 0
                && Recruitment.storedFoodValue(level, b) < FCConfig.RECRUIT_FOOD_VALUE.get() * 15) {
            out.add(new Need(NpcRole.FISHERMAN, "more food"));
        }
        // mines and caves: every workplace a miner can work should have one
        int cavesToMine = 0, caveMiners = 0;
        boolean mineUnworked = false;
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null) {
                continue;
            }
            boolean unworked = com.factionclash.work.Workplaces.unworked(data, w);
            if (w.type == WorkplaceState.Type.MINE && unworked) {
                mineUnworked = true;
            } else if (w.type == WorkplaceState.Type.CAVE && !w.workedOut && !w.marks.isEmpty()) {
                if (unworked) {
                    cavesToMine++;
                } else {
                    caveMiners++;
                }
            }
        }
        if (mineUnworked && living(f, b, NpcRole.MINER) > 0) {
            out.add(new Need(NpcRole.MINER, "the constructed mine has no miner"));
        }
        if (cavesToMine > 0 && caveMiners < MAX_CAVE_MINERS) {
            out.add(new Need(NpcRole.MINER, "a surveyed cave with ore to mine"));
        }
        if (living(f, b, NpcRole.CAVE_EXPLORER) == 0 && unsurveyedCave(data, f, b)) {
            out.add(new Need(NpcRole.CAVE_EXPLORER, "caves the scouts reported are unexplored"));
        }
        int soldiers = sw + ar;
        int pop = livingAll(f, b);
        if (soldiers < 12 && pop < b.beds.size() - 1
                && Recruitment.storedFoodValue(level, b) >= FCConfig.RECRUIT_FOOD_VALUE.get() * 3) {
            boolean bowAvailable = FactionStorage.count(level, b, s -> s.getItem() instanceof BowItem) > 0
                    || FactionStorage.count(level, b, s -> s.is(Items.STRING)) >= 3;
            if (ar < Math.round(FCConfig.RANGED_SHARE.get() * (soldiers + 1)) && bowAvailable) {
                out.add(new Need(NpcRole.ARCHER, "ranged support"));
            } else {
                out.add(new Need(NpcRole.SWORDSMAN, "garrison growth"));
            }
        }
        return out;
    }

    /** PROPOSED: at most this many miners work surveyed caves for one base at a time. */
    public static final int MAX_CAVE_MINERS = 2;

    /** True if the faction knows of a cave within reach of this base that the base has no survey of yet. */
    private static boolean unsurveyedCave(CampaignData data, FactionState f, BaseState b) {
        for (Report r : f.knowledge) {
            if (r.kind != Report.Kind.CAVE) {
                continue;
            }
            double dx = r.pos.getX() - b.center.getX(), dz = r.pos.getZ() - b.center.getZ();
            if (dx * dx + dz * dz > (double) com.factionclash.work.CaveWork.MAX_CAVE_DISTANCE * com.factionclash.work.CaveWork.MAX_CAVE_DISTANCE
                    || com.factionclash.work.CaveWork.ownWorkings(data, b, r.pos)) {
                continue;
            }
            boolean known = false;
            for (int id : b.workplaces) {
                WorkplaceState w = data.workplace(id);
                if (w != null && w.type == WorkplaceState.Type.CAVE && w.extent.closerThan(r.pos, 32)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return true;
            }
        }
        return false;
    }

    /** [animals in the pen, adults of the most numerous species]. */
    private static int[] penCounts(ServerLevel level, CampaignData data, BaseState b) {
        BlockPos c = b.point("pen");
        if (c == null) {
            return new int[]{0, 0};
        }
        AABB box = new AABB(c).inflate(5, 2, 3);
        int total = 0, maxAdults = 0;
        for (com.factionclash.work.PenJob.Species sp : com.factionclash.work.PenJob.SPECIES) {
            var list = level.getEntities(sp.type(), box, net.minecraft.world.entity.LivingEntity::isAlive);
            total += list.size();
            maxAdults = Math.max(maxAdults, (int) list.stream().filter(a -> !a.isBaby()).count());
        }
        return new int[]{total, maxAdults};
    }

    private static void recruitment(ServerLevel level, CampaignData data, FactionState f, BaseState b, long t) {
        if (t - b.lastRecruitAt < FCConfig.RECRUIT_INTERVAL_TICKS.get()) {
            return;
        }
        List<Need> needs = needs(level, data, b, t);
        if (needs.isEmpty()) {
            LAST_RECRUIT_NOTE.put(b.id, "No recruitment needed");
            return;
        }
        Need top = needs.get(0);
        if (livingAll(f, b) >= b.beds.size()) {
            LAST_RECRUIT_NOTE.put(b.id, "Want " + top.role().displayName + " (" + top.reason() + ") but housing is full");
            return;
        }
        Recruitment.Result r = Recruitment.recruit(level, data, b, top.role(), top.reason());
        LAST_RECRUIT_NOTE.put(b.id, r.message());
        if (!r.ok()) {
            b.lastRecruitAt = t - FCConfig.RECRUIT_INTERVAL_TICKS.get() / 2; // retry sooner, but not every run
        }
    }

    /**
     * Base defence: intruders inside the walls (enemy NPCs, enemy players, hostile raiders) are assigned to the
     * nearest free guards even without line of sight, and anything at the heart draws every guard (spec 14).
     */
    public static int defenceAlarm(ServerLevel level, BaseState b) {
        BlockPos heart = b.point(BaseState.HEART);
        AABB walls = new AABB(b.center).inflate(com.factionclash.structure.BaseLayout.HALF + 4, 12, com.factionclash.structure.BaseLayout.HALF + 4);
        List<net.minecraft.world.entity.LivingEntity> intruders = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, walls,
                e -> e.isAlive() && ((e instanceof FactionNpc n && n.getFaction() != b.faction)
                        || (e instanceof net.minecraft.world.entity.monster.Monster m && com.factionclash.combat.CombatRules.isHostileMonster(m))
                        || (e instanceof net.minecraft.server.level.ServerPlayer p && !p.isCreative() && !p.isSpectator()
                        && CampaignData.get(level.getServer()).players.get(p.getUUID()) != null
                        && CampaignData.get(level.getServer()).players.get(p.getUUID()) != b.faction)));
        if (intruders.isEmpty()) {
            return 0;
        }
        List<FactionNpc> guards = level.getEntitiesOfClass(FactionNpc.class, walls.inflate(24, 8, 24),
                g -> g.isAlive() && g.getFaction() == b.faction && g.getRole().combatant);
        int assigned = 0;
        for (net.minecraft.world.entity.LivingEntity intruder : intruders) {
            boolean atHeart = heart != null && intruder.blockPosition().closerThan(heart, 8);
            guards.sort(Comparator.comparingDouble(g -> g.distanceToSqr(intruder)));
            int want = atHeart ? guards.size() : 2;
            for (FactionNpc g : guards) {
                if (want <= 0) {
                    break;
                }
                net.minecraft.world.entity.LivingEntity cur = g.getTarget();
                boolean busy = cur != null && cur.isAlive() && !(atHeart && !cur.blockPosition().closerThan(heart, 8));
                if (!busy) {
                    g.setTarget(intruder);
                    assigned++;
                }
                want--;
            }
        }
        return assigned;
    }

    // ------------------------------------------------------------------ construction

    private static void projects(ServerLevel level, CampaignData data, BaseState b, long t) {
        for (Project p : b.projects) {
            if (p.status != Project.Status.DONE) {
                return;
            }
        }
        if (t - b.lastProjectAt < 2400) {
            return;
        }
        FactionState f = data.faction(b.faction);
        int pop = livingAll(f, b);
        Project p = null;
        if (b.beds.size() - pop <= 1) {
            for (BlockPos lot : List.copyOf(b.expansionLots)) {
                if (lotClear(level, lot)) {
                    p = newProject(data, Project.Type.HOUSE, lot, t);
                    break;
                }
                b.expansionLots.remove(lot);
                Messages.toFaction(level.getServer(), b.faction, Component.literal("Expansion lot at " + lot.toShortString()
                        + " is occupied; leaving it untouched"));
            }
        }
        if (p == null && b.storageExpansionSlots.size() >= 2 && FactionStorage.fillRatio(level, b) > 0.7) {
            p = newProject(data, Project.Type.STORAGE_EXPANSION, b.storageExpansionSlots.get(0), t);
        }
        if (p != null) {
            b.projects.add(p);
            b.lastProjectAt = t;
            data.setDirty();
            Messages.toFaction(level.getServer(), b.faction, Component.literal("New construction project: " + p.type.label + " #" + p.id));
        }
    }

    private static Project newProject(CampaignData data, Project.Type type, BlockPos origin, long t) {
        Project p = new Project();
        p.id = data.nextProjectId++;
        p.type = type;
        p.origin = origin;
        p.createdAt = t;
        return p;
    }

    /** A lot may be built on only if nothing but air or plants occupies it (preserves player builds). */
    private static boolean lotClear(ServerLevel level, BlockPos lot) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos p = lot.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!(s.isAir() || s.canBeReplaced()) || s.hasBlockEntity()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Budgeted scan of saved plans; damaged blocks become pending repairs that are never forgotten. */
    private static void repairScan(ServerLevel level, CampaignData data, BaseState b) {
        if (b.structures.isEmpty() || b.pendingRepairs.size() > 1024) {
            return;
        }
        int budget = REPAIR_SCAN_BUDGET;
        while (budget > 0) {
            if (b.repairStructure >= b.structures.size()) {
                b.repairStructure = 0;
                b.repairIndex = 0;
                return;
            }
            StructureRecord r = b.structures.get(b.repairStructure);
            for (; b.repairIndex < r.size() && budget > 0; b.repairIndex++, budget--) {
                BlockPos p = r.pos(b.repairIndex);
                if (!level.isLoaded(p) || b.playerEdits.contains(r.positions[b.repairIndex])) {
                    continue;
                }
                if (isDamaged(level, p, r.state(b.repairIndex))) {
                    b.pendingRepairs.add(r.positions[b.repairIndex]);
                    data.setDirty();
                }
            }
            if (b.repairIndex >= r.size()) {
                b.repairStructure++;
                b.repairIndex = 0;
            }
        }
    }

    public static boolean isDamaged(ServerLevel level, BlockPos p, BlockState planned) {
        if (planned.getBlock() instanceof DoorBlock && planned.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return false;
        }
        BlockState cur = level.getBlockState(p);
        if (cur.getBlock() == planned.getBlock()) {
            return false;
        }
        return cur.isAir() || !cur.getFluidState().isEmpty() || cur.is(BlockTags.FIRE) || cur.is(Blocks.SNOW);
    }

    // ------------------------------------------------------------------ raids

    private static void raids(ServerLevel level, CampaignData data, FactionState f, BaseState home, long t) {
        if (f.raidTarget >= 0) {
            f.raiders.removeIf(u -> {
                NpcRecord r = f.roster.get(u);
                return r == null || !r.alive;
            });
            BaseState target = data.base(f.raidTarget);
            if (f.raiders.isEmpty() || t - f.raidStartedAt > 72000) {
                Messages.toFaction(level.getServer(), f.type, Component.literal("Raid on " + (target == null ? "?" : target.faction.displayName)
                        + " has ended" + (target != null && target.destroyed ? " in victory" : "")));
                f.raidTarget = -1;
                if (t - f.raidStartedAt > 72000) {
                    f.raiders.clear();
                }
                data.setDirty();
            }
            return;
        }
        if (t - f.lastRaidAt < FCConfig.RAID_INTERVAL_TICKS.get() || t - data.startedAt < FCConfig.RAID_INTERVAL_TICKS.get()) {
            return;
        }
        BlockPos min = home.point(BaseState.MIN), max = home.point(BaseState.MAX);
        AABB area = new AABB(min.getX(), min.getY() - 40, min.getZ(), max.getX() + 1, max.getY() + 40, max.getZ() + 1);
        List<FactionNpc> soldiers = level.getEntitiesOfClass(FactionNpc.class, area, n -> n.isAlive() && n.getFaction() == f.type
                && n.getRole().combatant && n.baseId() == home.id);
        int party = FCConfig.RAID_PARTY_SIZE.get();
        if (soldiers.size() < FCConfig.HOME_DEFENDERS.get() + party) {
            return;
        }
        BaseState target = null;
        double best = Double.MAX_VALUE;
        for (Report r : f.knowledge) {
            if (r.kind != Report.Kind.ENEMY_BASE && r.kind != Report.Kind.LAIR) {
                continue;
            }
            for (BaseState b : data.bases.values()) {
                if (b.faction == r.subject && !b.destroyed && b.center.distSqr(r.pos) < 32 * 32) {
                    double d = b.center.distSqr(home.center);
                    if (d < best) {
                        best = d;
                        target = b;
                    }
                }
            }
        }
        if (target == null) {
            return;
        }
        soldiers.sort(Comparator.comparingInt((FactionNpc n) -> n.npcLevel()).thenComparingDouble(n -> n.getArmorValue()).reversed());
        f.raiders.clear();
        for (int i = 0; i < party; i++) {
            f.raiders.add(soldiers.get(i).getUUID());
        }
        f.raidTarget = target.id;
        f.raidStartedAt = t;
        f.lastRaidAt = t;
        f.raidsLaunched++;
        data.setDirty();
        Messages.toFaction(level.getServer(), f.type, Component.literal("Launching a raid with " + party + " soldiers on the "
                + target.faction.displayName + (target.isSettlement() ? " settlement" : " lair") + " reported at " + target.center.toShortString()));
    }

    // ------------------------------------------------------------------ Hollow lairs

    private static void lair(ServerLevel level, CampaignData data, BaseState lair, long t) {
        FactionState f = data.faction(FactionType.HOLLOW);
        if (!f.alive) {
            return;
        }
        List<Mob> mobs = new ArrayList<>();
        for (UUID u : List.copyOf(lair.lairMobs)) {
            Entity e = level.getEntity(u);
            if (e instanceof Mob m && m.isAlive()) {
                mobs.add(m);
                MISSING_SINCE.remove(u);
            } else if (e != null) {
                lair.lairMobs.remove(u); // present but dead
                data.setDirty();
            } else {
                // Entities load asynchronously after a restart: only forget a monster after a long absence,
                // otherwise the lair would respawn monsters that still exist (duplication on reload).
                long since = MISSING_SINCE.computeIfAbsent(u, k -> t);
                if (t - since > 6000) {
                    lair.lairMobs.remove(u);
                    MISSING_SINCE.remove(u);
                    data.setDirty();
                }
            }
        }
        int tracked = lair.lairMobs.size();
        if (t - lair.lastLairGrowth >= 24000) {
            lair.lastLairGrowth = t;
            lair.lairCap = Math.min(FCConfig.LAIR_MAX_CAP.get(), lair.lairCap + 1);
            data.setDirty();
        }
        if (tracked < lair.lairCap && (t / PERIOD) % 4 == 0) {
            Mob m = BaseBuilder.spawnLairMob(level, data, lair);
            if (m != null) {
                mobs.add(m);
            }
        }
        for (Mob m : mobs) {
            ChunkLoading.updateTraveler(m, m.distanceToSqr(lair.center.getX(), m.getY(), lair.center.getZ()) > 28 * 28);
        }
        lairRaid(level, data, lair, mobs, t);
    }

    private static void lairRaid(ServerLevel level, CampaignData data, BaseState lair, List<Mob> mobs, long t) {
        if (lair.lairRaidTarget >= 0) {
            BaseState target = data.base(lair.lairRaidTarget);
            List<Mob> raiders = mobs.stream().filter(m -> m.getPersistentData().getInt("factionclash_raid") == lair.lairRaidTarget + 1).toList();
            boolean over = target == null || target.destroyed || raiders.isEmpty() || t - lair.lastLairRaid > 24000;
            if (!over) {
                BlockPos heart = target.point(BaseState.HEART);
                int atHeart = 0;
                for (Mob m : raiders) {
                    if (m.blockPosition().closerThan(heart, 4.5)) {
                        atHeart++;
                    }
                    if (m.getTarget() == null && m.blockPosition().closerThan(heart, 30)) {
                        m.getNavigation().moveTo(heart.getX() + 0.5, heart.getY(), heart.getZ() + 0.5, 1.0);
                    }
                }
                if (atHeart > 0) {
                    lair.lairRaidProgress += PERIOD * atHeart;
                    level.destroyBlockProgress(-lair.id - 1000, heart, Math.min(9, lair.lairRaidProgress * 10 / 3000));
                    if (lair.lairRaidProgress >= 3000) {
                        level.destroyBlockProgress(-lair.id - 1000, heart, -1);
                        CampaignManager.destroyBase(level, target, FactionType.HOLLOW, "Hollow raiders");
                        over = true;
                    }
                }
            }
            if (over) {
                for (Mob m : raiders) {
                    m.getPersistentData().remove("factionclash_raid");
                    if (m instanceof PathfinderMob pm) {
                        pm.restrictTo(lair.center, 10);
                    }
                }
                lair.lairRaidTarget = -1;
                lair.lairRaidProgress = 0;
                data.setDirty();
            }
            return;
        }
        if (t - lair.lastLairRaid < FCConfig.LAIR_RAID_INTERVAL_TICKS.get() || mobs.size() < 4) {
            return;
        }
        // The Hollow senses settlements within its territory (PROPOSED: 300 blocks); no global knowledge.
        BaseState target = null;
        double best = 300 * 300;
        for (BaseState b : data.bases.values()) {
            if (b.isSettlement() && !b.destroyed && b.center.distSqr(lair.center) < best) {
                best = b.center.distSqr(lair.center);
                target = b;
            }
        }
        if (target == null) {
            return;
        }
        int n = Math.max(2, mobs.size() / 2);
        for (int i = 0; i < n && i < mobs.size(); i++) {
            Mob m = mobs.get(i);
            m.getPersistentData().putInt("factionclash_raid", target.id + 1);
            if (m instanceof PathfinderMob pm) {
                pm.restrictTo(target.point(BaseState.HEART), 4);
            }
        }
        lair.lairRaidTarget = target.id;
        lair.lastLairRaid = t;
        lair.lairRaidProgress = 0;
        data.setDirty();
        com.factionclash.FactionClash.LOGGER.info("Hollow lair {} raids {} with {} monsters", lair.id, target.faction.displayName, n);
    }
}
