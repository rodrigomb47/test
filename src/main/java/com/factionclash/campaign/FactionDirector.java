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
    /**
     * The last war council of each faction that found a target (HEROES_V2 8.1, SLICE_PLAN 6.4): both sides' counted
     * power, the heroes' share, the decision and whether the heroes decided it. Read by {@code /fc admin dump}.
     */
    public static final Map<FactionType, String> LAST_COUNCIL = new HashMap<>();
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
        BaseBuilder.ensureWorkplaces(data, b);
        com.factionclash.work.FishingSpots.maintain(level, data, b, t); // real water for the fishing spot, before a fisherman is hired
        // "Minecraft": the workshop's crafting table is the faction's from the start
        com.factionclash.achieve.FactionAchievements.criterion(level.getServer(), b.faction, "story/root", "crafting_table", null);
        rosterUpkeep(level, data, f, b, t);
        Logistics.tick(level, data, b, t);
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
            if (living(f, b, r) == 0 && !b.barren(r, t)) { // a barren role's last worker had nothing to do here
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
        int sw = living(f, b, NpcRole.SWORDSMAN) + living(f, b, NpcRole.AXE_FIGHTER);
        int ar = living(f, b, NpcRole.ARCHER) + living(f, b, NpcRole.CROSSBOWMAN);
        if (living(f, b, NpcRole.SWORDSMAN) < 3) {
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
        // a fisherman only where there may be water to fish: found, not looked for yet, or not found for two days now
        WorkplaceState fishery = com.factionclash.work.FishingSpots.fishery(data, b);
        if (living(f, b, NpcRole.FISHERMAN) == 0 && fishery != null
                && com.factionclash.logic.FishingWater.waterKnown(fishery.spot, fishery.searched, t) && !b.barren(NpcRole.FISHERMAN, t)
                && Recruitment.storedFoodValue(level, b) < FCConfig.RECRUIT_FOOD_VALUE.get() * 15) {
            if (FactionStorage.count(level, b, s -> s.is(Items.FISHING_ROD)) > 0 || FactionStorage.count(level, b, s -> s.is(Items.STRING)) >= 2) {
                out.add(new Need(NpcRole.FISHERMAN, "more food")); // only with a rod, or the string for one
            }
        }
        // mines and caves: every workplace a miner can work should have one. A miner of the base that holds neither
        // an open mine nor a cave takes such a place up itself (a mine first, else a cave) the next time it looks, so
        // while one is free nobody is hired or retrained for it: the base would only gain a miner with nothing to do
        int cavesToMine = 0, caveMiners = 0;
        boolean mineUnworked = false;
        java.util.Set<UUID> holders = new java.util.HashSet<>();
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && !com.factionclash.work.Workplaces.unworked(data, w)
                    && ((w.type == WorkplaceState.Type.MINE && w.active()) || (w.type == WorkplaceState.Type.CAVE && !w.workedOut))) {
                holders.add(w.assigned);
            }
        }
        boolean minerFree = f.roster.values().stream()
                .anyMatch(r -> r.alive && r.baseId == b.id && r.role == NpcRole.MINER && !holders.contains(r.uuid));
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null) {
                continue;
            }
            boolean unworked = com.factionclash.work.Workplaces.unworked(data, w);
            if (w.type == WorkplaceState.Type.MINE && w.active() && unworked) {
                mineUnworked = true; // an open mine (neither worked out nor resting) that nobody works
            } else if (w.type == WorkplaceState.Type.CAVE && !w.workedOut && !w.marks.isEmpty()) {
                if (unworked) {
                    cavesToMine++;
                } else {
                    caveMiners++;
                }
            }
        }
        if (mineUnworked && living(f, b, NpcRole.MINER) > 0 && !minerFree) {
            out.add(new Need(NpcRole.MINER, "an open mine has no miner"));
        }
        if (cavesToMine > 0 && caveMiners < MAX_CAVE_MINERS && !minerFree) {
            out.add(new Need(NpcRole.MINER, "a surveyed cave with ore to mine"));
        }
        if (living(f, b, NpcRole.CAVE_EXPLORER) == 0 && unsurveyedCave(data, f, b)) {
            out.add(new Need(NpcRole.CAVE_EXPLORER, "caves the scouts reported are unexplored"));
        }
        if (living(f, b, NpcRole.ENCHANTER) == 0 && enchantingWorthwhile(level, data, f, b)) {
            out.add(new Need(NpcRole.ENCHANTER, "gear, lapis and experience for the enchanting table"));
        }
        if (living(f, b, NpcRole.TRADER) == 0 && livingAll(f, b) >= TRADER_POPULATION && villageInReach(f, b)) {
            out.add(new Need(NpcRole.TRADER, "a village within reach to trade surplus with"));
        }
        if (living(f, b, NpcRole.HAULER) == 0 && livingAll(f, b) >= HAULER_POPULATION) {
            out.add(new Need(NpcRole.HAULER, "a growing base needs its storehouse kept in order"));
        }
        int soldiers = sw + ar;
        int pop = livingAll(f, b);
        if (soldiers < 12 && pop < b.beds.size() - 1
                && Recruitment.storedFoodValue(level, b) >= FCConfig.RECRUIT_FOOD_VALUE.get() * 3) {
            boolean bowAvailable = FactionStorage.count(level, b, s -> s.getItem() instanceof BowItem) > 0
                    || FactionStorage.count(level, b, s -> s.is(Items.STRING)) >= 3;
            // every third ranged soldier carries a crossbow when one can be paid for, every third melee one an axe
            boolean crossbowAvailable = FactionStorage.count(level, b, s -> s.is(Items.CROSSBOW)) > 0
                    || (FactionStorage.count(level, b, s -> s.is(Items.IRON_INGOT)) >= 2 && FactionStorage.count(level, b, s -> s.is(Items.STRING)) >= 2);
            if (ar < Math.round(FCConfig.RANGED_SHARE.get() * (soldiers + 1)) && (bowAvailable || crossbowAvailable)) {
                boolean crossbow = crossbowAvailable && (living(f, b, NpcRole.CROSSBOWMAN) + 1) * 3 <= ar + 1 || !bowAvailable;
                out.add(new Need(crossbow ? NpcRole.CROSSBOWMAN : NpcRole.ARCHER, "ranged support"));
            } else {
                boolean axe = (living(f, b, NpcRole.AXE_FIGHTER) + 1) * 3 <= sw + 1;
                out.add(new Need(axe ? NpcRole.AXE_FIGHTER : NpcRole.SWORDSMAN, "garrison growth"));
            }
        }
        return out;
    }

    /** PROPOSED: a base hires a trader once it has this many people (its surplus is worth a trip). */
    private static final int TRADER_POPULATION = 14;

    /** A village with traders the scouts reported within a trader's reach of the base. */
    private static boolean villageInReach(FactionState f, BaseState b) {
        double r = com.factionclash.work.TraderJob.TRADE_RANGE;
        return f.knowledge.stream().anyMatch(rep -> rep.tradingVillage() && rep.pos.distSqr(b.center) <= r * r);
    }

    /**
     * PROPOSED: an enchanter is worth taking on once the table stands, there is lapis and unenchanted good gear in
     * the storehouse, and the faction has picked up enough experience for a few enchantments (level 10).
     */
    private static boolean enchantingWorthwhile(ServerLevel level, CampaignData data, FactionState f, BaseState b) {
        BlockPos table = b.point(BaseState.ENCHANTING);
        return table != null && level.getBlockState(table).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)
                && f.xpBank >= com.factionclash.logic.XpLevels.pointsFor(10)
                && FactionStorage.count(level, b, s -> s.is(Items.LAPIS_LAZULI)) >= 6
                && FactionStorage.count(level, b, com.factionclash.work.EnchanterJob::candidate) > 0;
    }

    /** PROPOSED: a base takes on a hauler once it has this many NPCs. */
    public static final int HAULER_POPULATION = 12;

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
        if (Logistics.rationing(b)) {
            // short of food: every new mouth makes it worse - only those who bring food in are taken on
            Need food = needs.stream().filter(n -> Logistics.producesFood(n.role())).findFirst().orElse(null);
            if (food == null) {
                LAST_RECRUIT_NOTE.put(b.id, "Rationing: no recruitment until food recovers (only food producers are taken on)");
                return;
            }
            top = food;
        }
        if (retrainIdleWorker(level, data, f, b, top, t)) {
            b.lastRecruitAt = t;
            return;
        }
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

    /** PROPOSED: a worker with nothing to do (or no tool) for this long takes up the faction's most needed role. */
    public static final long RETRAIN_IDLE_TICKS = 12000;
    /** PROPOSED: after an idle worker is retrained, its old role is not hired again at that base for this long. */
    public static final long BARREN_TICKS = 48000;

    /**
     * Instead of recruiting, a worker who has had nothing to do for half a day (a miner with no mine or cave, any
     * worker whose job reports itself idle, a miner that only helps in another's mine) or no tool for its job takes
     * up the job the base needs most, keeping its
     * level and traits - like a player switching jobs. Taking the new role hands back its old workplaces. When it was
     * idle rather than toolless and no other worker of its old role at the base still has work, that role is barren
     * for two days: the base does not hire into the same dead end.
     */
    private static boolean retrainIdleWorker(ServerLevel level, CampaignData data, FactionState f, BaseState b, Need top, long t) {
        for (NpcRecord r : f.roster.values()) {
            if (!r.alive || r.baseId != b.id || r.role == top.role() || r.role.combatant || r.isHero()) {
                continue; // (a hero is never retrained: its duty is its own)
            }
            if (!(level.getEntity(r.uuid) instanceof FactionNpc n) || n.job() == null) {
                continue;
            }
            com.factionclash.work.Job job = n.job();
            // a spare hand (a miner only helping in another's mine) counts as idle, as it stood idle before it could help
            boolean spare = !job.idleFor(RETRAIN_IDLE_TICKS) && job.spareFor(RETRAIN_IDLE_TICKS);
            boolean idle = job.idleFor(RETRAIN_IDLE_TICKS) || spare;
            // a fisherman, butcher or miner who has not been able to get its tool for half a day (no string for a
            // rod, nothing to make it from) does something useful instead
            boolean toolless = job.missingToolFor(RETRAIN_IDLE_TICKS);
            if (!idle && !toolless) {
                continue;
            }
            NpcRole old = r.role;
            String why = spare ? job.spareReason() : idle ? (job.idleReason().isEmpty() ? "nothing to do" : job.idleReason()) : "no " + old.tool.noun();
            if (idle && !toolless && !othersBusy(level, f, b, r)) {
                b.markBarren(old, t + BARREN_TICKS); // nothing to do here even with its tool: do not hire into it again
            }
            r.role = top.role();
            n.role(top.role());
            data.setDirty();
            LAST_RECRUIT_NOTE.put(b.id, n.npcName() + " retrained as " + top.role().displayName + " (" + top.reason() + ")");
            Messages.toFaction(level.getServer(), b.faction, Component.literal(retrainNote(n.npcName(), old, why, top)));
            return true;
        }
        return false;
    }

    /**
     * What the faction hears when a worker is retrained: "Elsa Wells (Fisherman) has had no fishing rod for half a day
     * and becomes a Miner (a surveyed cave with ore to mine)". {@code why} reads after "has had": a job's idle reason,
     * or "no" and the tool it could not get.
     */
    public static String retrainNote(String name, NpcRole old, String why, Need top) {
        return name + " (" + old.displayName + ") has had " + why + " for half a day and becomes " + withArticle(top.role().displayName)
                + " (" + top.reason() + ")";
    }

    /** "a Swordsman", "an Axe Fighter". */
    public static String withArticle(String noun) {
        return (!noun.isEmpty() && "AEIOUaeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
    }

    /**
     * True when another living worker of {@code idle}'s role at this base still has work (or is not loaded, so it
     * may). The idle one was then only one too many - every mine taken, say - and the role is not barren: if the
     * busy ones die, the base must hire again. A role is barren only when none of its workers has anything to do.
     */
    private static boolean othersBusy(ServerLevel level, FactionState f, BaseState b, NpcRecord idle) {
        for (NpcRecord o : f.roster.values()) {
            if (o == idle || !o.alive || o.baseId != b.id || o.role != idle.role) {
                continue;
            }
            if (!(level.getEntity(o.uuid) instanceof FactionNpc n) || n.job() == null || !n.job().idleFor(RETRAIN_IDLE_TICKS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Base defence: intruders inside the walls (enemy NPCs, enemy players, hostile raiders) are assigned to the
     * nearest free guards even without line of sight, and anything at the heart draws every guard (spec 14).
     */
    public static int defenceAlarm(ServerLevel level, BaseState b) {
        BlockPos heart = b.point(BaseState.HEART);
        AABB walls = new AABB(b.center).inflate(com.factionclash.structure.BaseLayout.HALF + 4, 12, com.factionclash.structure.BaseLayout.HALF + 4);
        List<net.minecraft.world.entity.LivingEntity> intruders = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, walls,
                e -> e.isAlive() && ((e instanceof FactionNpc n && Diplomacy.hostile(level.getServer(), n.getFaction(), b.faction))
                        // a monster only in the yard or close to it, not in a cave under the base (live check: a creeper
                        // shut in a pocket of the ground five blocks under the yard was handed to the guards over and over)
                        || (e instanceof net.minecraft.world.entity.monster.Monster m && com.factionclash.combat.CombatRules.isHostileMonster(m)
                        && m.getY() >= b.center.getY() - 2)
                        || (e instanceof net.minecraft.server.level.ServerPlayer p && !p.isCreative() && !p.isSpectator()
                        && CampaignData.get(level.getServer()).players.get(p.getUUID()) != null
                        && Diplomacy.hostile(level.getServer(), CampaignData.get(level.getServer()).players.get(p.getUUID()), b.faction))));
        if (intruders.isEmpty()) {
            return 0;
        }
        List<FactionNpc> guards = level.getEntitiesOfClass(FactionNpc.class, walls.inflate(24, 8, 24),
                g -> g.isAlive() && g.getFaction() == b.faction && g.getRole().combatant && !g.isDisplayOnly());
        // raiders and enemy players first, then monsters: two guards on a monster must not leave an intruder unattended
        intruders.sort(Comparator.comparingInt(e -> e instanceof net.minecraft.world.entity.monster.Monster ? 1 : 0));
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
                if (!busy && !g.pursuit().ignores(intruder)) { // (not one it gave up on: a monster it cannot get at)
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
            if (!f.raiders.isEmpty() && f.raidParty > 0 && f.raiders.size() * 2 < f.raidParty && target != null && !target.destroyed) {
                // more than half the party is down: the survivors fall back rather than die one by one
                Messages.toFaction(level.getServer(), f.type, Component.literal("The raid on " + target.faction.displayName
                        + " is beaten back - " + f.raiders.size() + " of " + f.raidParty + " soldiers retreat home"));
                f.raiders.clear();
            }
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
        int interval = FCConfig.RAID_INTERVAL_TICKS.get();
        // a counter-attack may go out sooner (a quarter of the interval) - but only into a weakened enemy, below
        if (t - f.lastRaidAt < interval / 4 || t - data.startedAt < interval) {
            return;
        }
        boolean intervalOver = t - f.lastRaidAt >= interval;
        BlockPos min = home.point(BaseState.MIN), max = home.point(BaseState.MAX);
        AABB area = new AABB(min.getX(), min.getY() - 40, min.getZ(), max.getX() + 1, max.getY() + 40, max.getZ() + 1);
        List<FactionNpc> soldiers = level.getEntitiesOfClass(FactionNpc.class, area, n -> n.isAlive() && n.getFaction() == f.type
                && n.getRole().combatant && n.baseId() == home.id && !n.isDisplayOnly());
        // a real attack: every soldier beyond a third of the garrison (never fewer than raidPartySize, always leaving
        // homeDefenders at home) - small parties against a full garrison only feed an endless war of attrition
        int keep = Math.max(FCConfig.HOME_DEFENDERS.get(), (soldiers.size() + 2) / 3);
        int party = Math.max(FCConfig.RAID_PARTY_SIZE.get(), soldiers.size() - keep);
        // a hero counts as one soldier of its role for the garrison and the party size, but the soldier sort never
        // picks it: it joins a raid only through its own rules (HEROES_V2 3.2: a leader going with the raid, its
        // behaviour), which come with the leaders
        List<FactionNpc> draftable = soldiers.stream().filter(n -> !(n instanceof com.factionclash.hero.HeroEntity)).toList();
        if (!draftable.isEmpty()) {
            // how well armed the garrison is, as the war councils of other factions will judge it: ordinary soldiers
            // only (HEROES_V2 8.1), a home hero's war power is added on top of them, so one hero does not inflate eight
            f.soldierPower = (float) draftable.stream().mapToDouble(FactionDirector::power).average().orElse(1.0);
        }
        party = Math.min(party, draftable.size());
        if (soldiers.size() < FCConfig.HOME_DEFENDERS.get() + party || party < FCConfig.RAID_PARTY_SIZE.get()) {
            return;
        }
        // war council: of the bases the scouts reported, the weakest garrison first (then the nearest); attack when
        // the party is at least two thirds of its defenders, or - sooner - when it outnumbers a beaten enemy 3 to 2.
        // Strength is weighed by arms and armour, not only heads: eight soldiers in leather do not storm diamond.
        BaseState target = null;
        double best = Double.MAX_VALUE;
        int targetDefence = 0;
        // in a pact: only the conqueror, never an ally
        FactionType sworn = Diplomacy.swornEnemy(data, f.type);
        for (Report r : f.knowledge) {
            if (r.kind != Report.Kind.ENEMY_BASE && r.kind != Report.Kind.LAIR || Diplomacy.allied(data, f.type, r.subject)
                    || sworn != null && r.subject != sworn) {
                continue;
            }
            for (BaseState b : data.bases.values()) {
                if (b.faction == r.subject && !b.destroyed && b.center.distSqr(r.pos) < 32 * 32) {
                    int heads = defenders(data, b);
                    // in soldier units: heads, plus the home heroes' war power as so many soldiers of that garrison
                    double unit = b.isSettlement() ? Math.max(0.1, data.faction(b.faction).soldierPower) : 1.0;
                    double defence = defencePower(level, data, b) / unit;
                    double score = defence * 1000.0 + Math.sqrt(b.center.distSqr(home.center));
                    if (score < best) {
                        best = score;
                        target = b;
                        targetDefence = heads;
                    }
                }
            }
        }
        if (target == null) {
            return;
        }
        List<FactionNpc> picks = new ArrayList<>(draftable);
        picks.sort(Comparator.comparingInt((FactionNpc n) -> n.npcLevel()).thenComparingDouble(n -> n.getArmorValue()).reversed());
        double partySoldiers = picks.subList(0, party).stream().mapToDouble(FactionDirector::power).sum();
        double partyHeroes = 0; // heroes go only by their own rules (leaders, behaviour.raids), which come with L4
        double partyPower = com.factionclash.logic.hero.HeroRules.countedPower(partySoldiers, partyHeroes);
        double defenceSoldiers = targetDefence * (target.isSettlement() ? data.faction(target.faction).soldierPower : 1.0);
        double defenceHeroes = heroDefence(level, data, target);
        double defencePower = com.factionclash.logic.hero.HeroRules.countedPower(defenceSoldiers, defenceHeroes);
        boolean counter = !intervalOver && partyPower * 2 >= defencePower * 3;
        // a pact offensive: allies already marching on the same base count toward the strength needed
        double alliedPower = 0;
        if (sworn != null) {
            for (FactionType m : data.pactMembers) {
                FactionState ally = data.faction(m);
                if (m != f.type && ally.raidTarget == target.id) {
                    alliedPower += ally.raiders.size() * ally.soldierPower;
                }
            }
        }
        boolean joint = alliedPower > 0 && (partyPower + alliedPower) * 3 >= defencePower * 2;
        boolean go = !(!joint && !counter && (!intervalOver || partyPower * 3 < defencePower * 2));
        // the same council with the heroes counted out: did their presence (or absence) decide it? (SLICE_PLAN 6.4)
        boolean goWithout = !intervalOver && partySoldiers * 2 >= defenceSoldiers * 3
                || alliedPower > 0 && (partySoldiers + alliedPower) * 3 >= defenceSoldiers * 2
                || intervalOver && partySoldiers * 3 >= defenceSoldiers * 2;
        LAST_COUNCIL.put(f.type, String.format(java.util.Locale.ROOT,
                "day %.2f: %s base %d (%s): party %.1f (heroes %.1f), defence %.1f (soldiers %.1f, heroes %.1f), %s%s",
                t / 24000.0, target.faction.id, target.id, target.isSettlement() ? "settlement" : "lair", partyPower, partyHeroes,
                defencePower, defenceSoldiers, defenceHeroes, go ? "attack" : "wait",
                go == goWithout ? "" : go ? " (the heroes decided it)" : " (the defending heroes held it off)"));
        if (!go) {
            return; // not yet, or not strong enough for that garrison
        }
        f.raiders.clear();
        for (int i = 0; i < party; i++) {
            f.raiders.add(picks.get(i).getUUID());
        }
        f.raidTarget = target.id;
        f.raidParty = party;
        f.raidMusteredAt = 0;
        // the party gathers 28 blocks short of the target, on the home side, before going in together
        double dx = home.center.getX() - target.center.getX(), dz = home.center.getZ() - target.center.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int rx = target.center.getX() + (int) Math.round(dx / len * 28), rz = target.center.getZ() + (int) Math.round(dz / len * 28);
        f.raidRally = new BlockPos(rx, target.center.getY(), rz).asLong();
        f.raidStartedAt = t;
        f.lastRaidAt = t;
        f.raidsLaunched++;
        data.setDirty();
        Messages.toFaction(level.getServer(), f.type, Component.literal((joint ? "Joining the pact's offensive! " : counter ? "Counter-attack! " : "")
                + "Launching a raid with " + party
                + " soldiers (against about " + targetDefence + " defenders) on the "
                + target.faction.displayName + (target.isSettlement() ? " settlement" : " lair") + " reported at " + target.center.toShortString()));
    }

    /**
     * One soldier's fighting power (PROPOSED): 1, plus up to 1 for armour (20 points = full diamond) and up to 1 for
     * the weapon's material (diamond 0.75, netherite 1).
     */
    static double power(FactionNpc n) {
        int tier = com.factionclash.npc.ToolKind.tierOf(n.getMainHandItem());
        return 1.0 + Math.min(20, n.getArmorValue()) / 20.0 + Math.max(0, tier) / 4.0;
    }

    /**
     * Fighting strength at a base by heads: its living ordinary soldiers at home (a lair: its monsters); heroes count by
     * their war power ({@link #heroDefence}), or as one head each with {@code war.heroPowerPercent} 0.
     */
    private static int defenders(CampaignData data, BaseState b) {
        if (!b.isSettlement()) {
            return b.lairMobs.size();
        }
        FactionState f = data.faction(b.faction);
        boolean heroHeads = FCConfig.HERO_POWER_PERCENT.get() == 0; // war.heroPowerPercent 0: a hero is one soldier by heads
        int n = 0;
        for (NpcRecord r : f.roster.values()) {
            if (r.alive && r.baseId == b.id && r.role.combatant && (heroHeads || !r.isHero()) && !f.raiders.contains(r.uuid)) {
                n++;
            }
        }
        return n;
    }

    /**
     * A base's defence power in the director's units (HEROES_V2 8.1): its soldiers at home times the faction's
     * {@code soldierPower}, plus the war power of each hero whose home it is (alive, at home, not raiding; half under
     * half health), the heroes counted at most 1.5 times the soldiers (the soldier backbone).
     */
    public static double defencePower(ServerLevel level, CampaignData data, BaseState b) {
        if (!b.isSettlement()) {
            return b.lairMobs.size();
        }
        double soldiers = defenders(data, b) * data.faction(b.faction).soldierPower;
        return com.factionclash.logic.hero.HeroRules.countedPower(soldiers, heroDefence(level, data, b));
    }

    /** The summed war power of the heroes defending a base (before the backbone cap). */
    public static double heroDefence(ServerLevel level, CampaignData data, BaseState b) {
        if (!b.isSettlement()) {
            return 0;
        }
        FactionState f = data.faction(b.faction);
        double sum = 0;
        for (HeroState s : data.heroes.values()) {
            if (s.owner != b.faction || !s.alive || s.baseId != b.id || s.uuid == null || f.raiders.contains(s.uuid)) {
                continue;
            }
            sum += heroWarPower(level, s, true);
        }
        return sum;
    }

    /**
     * A hero's war power (HEROES_V2 8.1, {@code HeroRules.heroPower}) from its real maximum health and level: its loaded
     * entity's, else what the campaign last saw of it (or its definition's numbers). With {@code defence} it counts half
     * while under half health. {@code war.heroPowerPercent} scales it; 0 leaves only its head.
     */
    public static double heroWarPower(ServerLevel level, HeroState s, boolean defence) {
        double max = s.maxHealth, health = s.health;
        int lv = s.level;
        if (level != null && s.uuid != null && level.getEntity(s.uuid) instanceof com.factionclash.hero.HeroEntity h && h.isAlive()) {
            max = h.getMaxHealth();
            health = h.getHealth();
            lv = h.npcLevel();
        } else if (max <= 0) {
            com.factionclash.logic.hero.HeroDef d = com.factionclash.hero.HeroRegistry.definition(s.id);
            max = d == null ? 20 : com.factionclash.logic.hero.HeroLoadout.numbers(d, lv, false, FCConfig.BOSS_HEALTH_SCALE.get()).maxHealth();
            health = max;
        }
        double p = com.factionclash.logic.hero.HeroRules.heroPower(max, lv, FCConfig.HERO_POWER_PERCENT.get());
        return defence ? com.factionclash.logic.hero.HeroRules.defencePower(p, max > 0 ? health / max : 1) : p;
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
