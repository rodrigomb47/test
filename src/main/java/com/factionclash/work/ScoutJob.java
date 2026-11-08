package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import com.factionclash.logic.ScoutRules;
import com.factionclash.npc.FactionNpc;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scout (spec 12): leaves the base, explores unexplored sectors, records only what it actually observes, retreats
 * on danger with a fixed home destination, and publishes its reports by reaching the central reporting station.
 */
public class ScoutJob extends Job {
    enum State { HOME, OUTBOUND, RETURN, REPORT }

    private State state = State.HOME;
    private BlockPos target;
    private final List<Report> pending = new ArrayList<>();
    private final Set<Long> pendingSectors = new HashSet<>();
    private long departedAt;
    private int sectorsVisited;
    private int observeTimer;
    private int failedTargets;
    private boolean retreating;
    private String retreatReason = "";
    private int timer;
    private final Set<Long> caveChecked = new HashSet<>();

    public ScoutJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public boolean ignoresCombat() {
        return state == State.RETURN && retreating;
    }

    @Override
    public String workplaceInfo() {
        return switch (state) {
            case HOME -> "At home between expeditions";
            case OUTBOUND -> "Exploring toward " + targetInfo() + ", " + pending.size() + " observations held";
            case RETURN -> (retreating ? "RETREATING (" + retreatReason + ")" : "Returning") + " with " + pending.size() + " observations";
            case REPORT -> "Reporting at the station";
        };
    }

    /** Scouts explore beyond their own settlement (PROPOSED: at least 96 blocks out). */
    private static final int MIN_TARGET_DISTANCE = 96;
    private static final int SECTORS_PER_EXPEDITION = 4;
    private static final int UNKNOWN_Y = -2000; // fits BlockPos.asLong (12-bit y), below any world

    /** Resolves the target's surface height when its chunk is loaded; returns a walkable goal. */
    private BlockPos resolveTarget() {
        if (target.getY() <= level().getMinBuildHeight()) {
            target = new BlockPos(target.getX(), UNKNOWN_Y, target.getZ()); // never trust a floor-height reading
        }
        if (target.getY() == UNKNOWN_Y) {
            if (level().hasChunk(target.getX() >> 4, target.getZ() >> 4)) {
                target = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(target.getX(), 0, target.getZ()));
                return target;
            }
            return new BlockPos(target.getX(), npc.getBlockY(), target.getZ());
        }
        return target;
    }

    private boolean nearColumn(BlockPos p, double reach) {
        double dx = npc.getX() - (p.getX() + 0.5), dz = npc.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach;
    }

    private int range() {
        return FCConfig.SCOUT_RANGE.get() + data().faction(npc.getFaction()).scoutReach + (int) npc.affix(Affix.PATHFINDER);
    }

    /**
     * Everything in range is explored. While no living rival settlement is known, the scouts push the frontier out
     * (64 blocks at a time, up to the edge of the campaign map) instead of going over the same ground again.
     */
    private boolean extendReach(BaseState b) {
        CampaignData data = data();
        FactionState f = data.faction(npc.getFaction());
        boolean rivalKnown = f.knowledge.stream().anyMatch(r -> r.kind == com.factionclash.campaign.Report.Kind.ENEMY_BASE
                && com.factionclash.campaign.Diplomacy.hostile(npc.getServer(), npc.getFaction(), r.subject)
                && data.bases.values().stream().anyMatch(o -> o.faction == r.subject && !o.destroyed && o.center.distSqr(r.pos) < 32 * 32));
        int dx = Math.abs(b.center.getX() - data.center.getX()) + data.radius, dz = Math.abs(b.center.getZ() - data.center.getZ()) + data.radius;
        int maxReach = (int) Math.sqrt((double) dx * dx + dz * dz) - FCConfig.SCOUT_RANGE.get();
        if (rivalKnown || f.scoutReach >= maxReach) {
            return false;
        }
        f.scoutReach = Math.min(maxReach, f.scoutReach + 64);
        data.setDirty();
        com.factionclash.FactionClash.LOGGER.info("[{}] No rival settlement found within reach: scouts now range up to {} blocks from home",
                npc.getFaction().displayName, range());
        return true;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case HOME -> home(b);
            case OUTBOUND -> outbound(b);
            case RETURN -> {
                if (!retreating) {
                    observe(b);
                }
                double speed = retreating ? 1.3 : 1.0;
                if (goTo(b.point(BaseState.STATION_STAND), 1.8, "the reporting station", speed)) {
                    state = State.REPORT;
                    timer = 0;
                } else if (retreating) {
                    status("Retreating home: " + retreatReason, "Retreating");
                }
            }
            case REPORT -> report(b);
        }
    }

    private void home(BaseState b) {
        if (!atStorage()) {
            return;
        }
        unloadNow();
        upgradeEquipment();
        if (npc.getHealth() < npc.getMaxHealth() * 0.75F) {
            status("Recovering before the next expedition", "Resting");
            waitFor(200);
            return;
        }
        target = pickTarget(b);
        if (target == null && extendReach(b)) {
            target = pickTarget(b);
        }
        if (target == null) {
            status("Everything in range explored; waiting to re-scout", "Idle");
            data().faction(npc.getFaction()).explored.clear();
            waitFor(2400);
            return;
        }
        departedAt = now();
        caveChecked.clear();
        sectorsVisited = 0;
        failedTargets = 0;
        retreating = false;
        NpcRecord rec = npc.record();
        if (rec != null) {
            rec.departedAt = departedAt;
        }
        state = State.OUTBOUND;
    }

    private BlockPos pickTarget(BaseState b) {
        CampaignData data = data();
        FactionState f = data.faction(npc.getFaction());
        int r = range();
        BlockPos from = npc.blockPosition();
        long best = 0;
        double bestScore = Double.MAX_VALUE;
        boolean found = false;
        for (int dx = -r; dx <= r; dx += 32) {
            for (int dz = -r; dz <= r; dz += 32) {
                if (dx * dx + dz * dz > r * r || dx * dx + dz * dz < MIN_TARGET_DISTANCE * MIN_TARGET_DISTANCE) {
                    continue;
                }
                int x = b.center.getX() + dx, z = b.center.getZ() + dz;
                if (b.contains(new BlockPos(x, b.center.getY(), z), 16)) {
                    continue; // the home area is not "the wider world"
                }
                if (Math.abs(x - data.center.getX()) > data.radius || Math.abs(z - data.center.getZ()) > data.radius) {
                    continue;
                }
                long key = FactionState.sector(x, z);
                if (f.explored.contains(key) || pendingSectors.contains(key)) {
                    continue;
                }
                double score = Math.sqrt(from.distSqr(new BlockPos(x, from.getY(), z))) + level().random.nextInt(48);
                if (score < bestScore) {
                    bestScore = score;
                    best = key;
                    found = true;
                }
            }
        }
        if (!found) {
            return null;
        }
        int x = FactionState.sectorCenterX(best), z = FactionState.sectorCenterZ(best);
        // the real surface height is filled in once the scout's travel has loaded that chunk
        return new BlockPos(x, UNKNOWN_Y, z);
    }

    private void outbound(BaseState b) {
        if (dangerCheck()) {
            return;
        }
        observe(b);
        long away = now() - departedAt;
        BlockPos goal = resolveTarget();
        boolean arrived = nearColumn(goal, 8.0);
        if (!arrived) {
            goTo(goal, 6.0, "unexplored land");
        } else {
            npc.navigator().reset();
        }
        if (arrived) {
            pendingSectors.add(FactionState.sector(target.getX(), target.getZ()));
            sectorsVisited++;
            if (sectorsVisited >= SECTORS_PER_EXPEDITION || pending.size() >= 10 || away > FCConfig.SCOUT_OVERDUE_TICKS.get() / 3) {
                beginReturn(false, "");
                return;
            }
            target = pickTarget(b);
            if (target == null) {
                beginReturn(false, "");
            }
            return;
        }
        if (npc.navigator().target() == null && !arrived) {
            // navigation gave up on this target: mark it unreachable and pick another
            pendingSectors.add(FactionState.sector(target.getX(), target.getZ()));
            if (++failedTargets >= 3) {
                beginReturn(false, "");
            } else {
                target = pickTarget(b);
                if (target == null) {
                    beginReturn(false, "");
                }
            }
        }
        if (away > FCConfig.SCOUT_OVERDUE_TICKS.get() / 2) {
            beginReturn(false, "");
        } else {
            status("Scouting toward " + targetInfo() + " (" + pending.size() + " observations)", "Scouting");
        }
    }

    private void beginReturn(boolean retreat, String reason) {
        state = State.RETURN;
        retreating = retreat;
        retreatReason = reason;
        npc.navigator().reset();
        if (retreat) {
            npc.setTarget(null);
            status("Retreating home: " + reason, "Retreating");
        }
    }

    /** Announced retreat: once triggered the home destination is fixed and is not overwritten by later threats. */
    private boolean dangerCheck() {
        if (++observeTimer % 10 != 0) {
            return false;
        }
        AABB box = npc.getBoundingBox().inflate(10);
        List<LivingEntity> threats = level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
                && ((e instanceof Monster m && CombatRules.isHostileMonster(m))
                || (e instanceof FactionNpc o && com.factionclash.campaign.Diplomacy.hostile(npc.getServer(), npc.getFaction(), o.getFaction())
                && o.getRole().combatant)));
        boolean hurt = npc.getHealth() < npc.getMaxHealth() * 0.6F;
        if (threats.size() >= 2 || (!threats.isEmpty() && hurt)) {
            LivingEntity t = threats.get(0);
            pending.add(new Report(Report.Kind.HOSTILES, t.blockPosition(), now(),
                    t instanceof FactionNpc o ? o.getFaction() : null, threats.size(),
                    threats.size() + " hostile(s) near " + t.blockPosition().toShortString()));
            beginReturn(true, threats.size() + " hostile(s) nearby" + (hurt ? ", wounded" : ""));
            return true;
        }
        return false;
    }

    /** Records observations the scout can actually make from where it stands. */
    private void observe(BaseState home) {
        if (++observeTimer % 40 != 0) {
            return;
        }
        BlockPos here = npc.blockPosition();
        if (here.closerThan(home.center, 40)) {
            return;
        }
        long now = now();
        CampaignData data = data();
        pendingSectors.add(FactionState.sector(here.getX(), here.getZ()));
        for (BaseState other : data.bases.values()) {
            if (other.faction == npc.getFaction() || other.destroyed) {
                continue;
            }
            double d = Math.sqrt(other.center.distSqr(new BlockPos(here.getX(), other.center.getY(), here.getZ())));
            if (d <= 64) {
                int seen = level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(40), e -> e.isAlive()
                        && ((e instanceof FactionNpc o && o.getFaction() == other.faction)
                        || (other.faction == FactionType.HOLLOW && other.lairMobs.contains(e.getUUID())))).size();
                Report.Kind kind = other.faction == FactionType.HOLLOW ? Report.Kind.LAIR : Report.Kind.ENEMY_BASE;
                addPending(new Report(kind, other.center, now, other.faction, seen,
                        other.faction.displayName + (kind == Report.Kind.LAIR ? " lair" : " settlement") + ", " + seen + " seen"));
            }
        }
        int hostiles = level().getEntitiesOfClass(Monster.class, npc.getBoundingBox().inflate(24), CombatRules::isHostileMonster).size();
        if (hostiles >= 3) {
            addPending(new Report(Report.Kind.HOSTILES, here, now, null, hostiles, hostiles + " monsters"));
        }
        // a village: villagers with a trade, worth a trader's trip (reported where they gather)
        List<net.minecraft.world.entity.npc.Villager> villagers = level().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                npc.getBoundingBox().inflate(32, 12, 32), com.factionclash.work.TraderJob::trades);
        if (villagers.size() >= 2) {
            double vx = 0, vz = 0;
            for (var v : villagers) {
                vx += v.getX();
                vz += v.getZ();
            }
            BlockPos at = new BlockPos((int) Math.floor(vx / villagers.size()), villagers.get(0).getBlockY(), (int) Math.floor(vz / villagers.size()));
            String jobs = villagers.stream().map(v -> net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(v.getVillagerData().getProfession()).getPath()).distinct().sorted().collect(java.util.stream.Collectors.joining(", "));
            addPending(new Report(Report.Kind.VILLAGE, at, now, null, villagers.size(), villagers.size() + " villagers with trades (" + jobs + ")"));
        }
        // Only things worth a trip are reported: caves and exposed ore. Trees and water are easy to find and are
        // not reported.
        for (int i = 0; i < 10; i++) {
            int x = here.getX() + level().random.nextInt(33) - 16;
            int z = here.getZ() + level().random.nextInt(33) - 16;
            if (!level().isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos top = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
            BlockPos cave = findCave(x, z);
            // looking for a cave's way in takes a while: once per 32x32 sector per expedition is enough
            if (cave != null && caveChecked.add(FactionState.sector(x, z)) && !CaveWork.ownWorkings(data, home, cave)) {
                // a scout only reports a cave it can see a way into: its mouth, pit or ravine opening
                BlockPos mouth = CaveWork.findEntrance(level(), cave, here, 6000);
                if (mouth != null && mouth.closerThan(here, 48)) {
                    addPending(new Report(Report.Kind.CAVE, mouth, now, null, 1, "cave entrance at y=" + mouth.getY()
                            + ", cave down to y=" + cave.getY()));
                }
            }
            BlockPos ore = findExposedOre(top);
            if (ore != null) {
                addPending(new Report(Report.Kind.ORE, ore, now, null, 1,
                        level().getBlockState(ore).getBlock().getName().getString()));
            }
        }
    }

    private BlockPos findCave(int x, int z) {
        BlockPos top = level().getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, new BlockPos(x, 0, z));
        for (int y = top.getY() - 6; y > top.getY() - 30 && y > level().getMinBuildHeight() + 5; y -= 2) {
            BlockPos p = new BlockPos(x, y, z);
            if (level().getBlockState(p).isAir() && level().getBlockState(p.above()).isAir() && !level().canSeeSky(p)) {
                int air = 0;
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                    if (level().getBlockState(q).isAir()) {
                        air++;
                    }
                }
                if (air >= 14) {
                    return p.immutable();
                }
            }
        }
        return null;
    }

    private BlockPos findExposedOre(BlockPos top) {
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos p = top.above(dy);
            BlockState s = level().getBlockState(p);
            if (s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                    || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.DIAMOND_ORES)) {
                return p;
            }
        }
        return null;
    }

    private void addPending(Report r) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).sameSubject(r)) {
                pending.set(i, r);
                return;
            }
        }
        if (pending.size() < 24) {
            pending.add(r);
        }
    }

    /** Publishing requires physically reaching and interacting with the station (spec 12). */
    private void report(BaseState b) {
        BlockPos station = b.point(BaseState.STATION);
        lookAt(station);
        if (!level().getBlockState(station).is(com.factionclash.registry.ModBlocks.REPORTING_STATION.get())) {
            status("The reporting station is destroyed - holding " + pending.size() + " reports", "Waiting");
            waitFor(400);
            return;
        }
        if (++timer < 40) {
            status("Reporting at the station", "Reporting");
            return;
        }
        npc.swing(InteractionHand.MAIN_HAND);
        FactionState f = data().faction(npc.getFaction());
        int fresh = 0, xp = 0;
        for (Report r : pending) {
            r.deliveredAt = now();
            r.by = npc.npcName();
            if (f.addReport(r)) {
                fresh++;
            }
            xp += ScoutRules.reportXp(r.kind.xpValue, Math.sqrt(r.pos.distSqr(b.center)));
        }
        f.explored.addAll(pendingSectors);
        f.reportsDelivered += pending.size();
        data().setDirty();
        npc.awardXp(xp);
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Scout " + npc.npcName() + " returned"
                + (retreating ? " after retreating (" + retreatReason + ")" : "") + " and reported " + pending.size()
                + " observations (" + fresh + " new)."));
        pending.clear();
        pendingSectors.clear();
        NpcRecord rec = npc.record();
        if (rec != null) {
            rec.departedAt = 0;
        }
        departedAt = 0;
        retreating = false;
        state = State.HOME;
        waitFor(200);
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        if (target != null) {
            tag.putLong("target", target.asLong());
        }
        ListTag l = new ListTag();
        for (Report r : pending) {
            l.add(r.save());
        }
        tag.put("pending", l);
        tag.putLongArray("sectors", pendingSectors.stream().mapToLong(Long::longValue).toArray());
        tag.putLong("departed", departedAt);
        tag.putInt("visited", sectorsVisited);
        tag.putBoolean("retreating", retreating);
        tag.putString("reason", retreatReason);
    }

    public String targetInfo() {
        return target == null ? "none" : target.getX() + ", " + (target.getY() == UNKNOWN_Y ? "?" : target.getY()) + ", " + target.getZ();
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.HOME;
        }
        target = readPos(tag, "target");
        pending.clear();
        ListTag l = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Report r = Report.load(l.getCompound(i));
            if (r != null) {
                pending.add(r);
            }
        }
        pendingSectors.clear();
        for (long s : tag.getLongArray("sectors")) {
            pendingSectors.add(s);
        }
        departedAt = tag.getLong("departed");
        sectorsVisited = tag.getInt("visited");
        retreating = tag.getBoolean("retreating");
        retreatReason = tag.getString("reason");
        if (state == State.OUTBOUND && target == null) {
            state = State.RETURN;
        }
        if (state == State.REPORT) {
            state = State.RETURN;
        }
    }
}
