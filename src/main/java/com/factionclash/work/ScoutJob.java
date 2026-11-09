package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import com.factionclash.logic.EscapeRoute;
import com.factionclash.logic.ScoutRules;
import com.factionclash.logic.Zones;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scout (spec 12): leaves the base, explores unexplored sectors, records only what it actually observes (settlements,
 * lairs, monsters, caves, ore, and the villages, pillager outposts and woodland mansions it comes within sight of), and
 * publishes its reports by reaching the central reporting station. Monsters met on the way are stepped around, not
 * fought and not run home from ({@link #dangerCheck}); enemy soldiers, being badly hurt among monsters, being hit again
 * and again or failing to shake them make it retreat home with a fixed destination.
 */
public class ScoutJob extends Job {
    enum State { HOME, OUTBOUND, RETURN, REPORT }

    private State state = State.HOME;
    private BlockPos target;
    private final List<Report> pending = new ArrayList<>();
    private final Set<Long> pendingSectors = new HashSet<>();
    private long departedAt;
    private int sectorsVisited;
    /** Separate cadences: observing every 40 ticks, the danger check every 10. */
    private int observeTimer, dangerTimer;
    private int failedTargets;
    private boolean retreating;
    private String retreatReason = "";
    private int timer;
    private final Set<Long> caveChecked = new HashSet<>();
    /** The structures seen on this trip, by start chunk: each is reported once (not saved; reports merge anyway). */
    private final Map<Long, StructureSightings.Sighting> sightings = new HashMap<>();

    // Stepping aside from monsters. Not saved: after a reload the scout looks around afresh.
    /** Where it is running to (null: not evading), for how many ticks, and the direction of its last escape. */
    private BlockPos escape;
    private int escapeTicks;
    /** How clear of the monsters the escape point was when chosen (below 0 when nothing better than a gain was found). */
    private double escapeMargin;
    private double lastDirX, lastDirZ;
    /**
     * In this encounter: escapes that left monsters within reach, hits taken, escapes started (successful or not), the
     * last monster seen, a cornered fight.
     */
    private int failedEvasions, hits, lastHurtStamp, encounterEscapes;
    private long lastThreatAt = Long.MIN_VALUE / 2, defendUntil;
    private String evadeInfo = "", escapedFrom = "";
    /** Times this trip the scout stepped aside from monsters (saved, for its report). */
    private int evasions;

    /** PROPOSED: below this share of health, with monsters about, it retreats home instead of stepping aside. */
    static final float BADLY_HURT = 0.4F;
    /** Enemy soldiers: home when two threats are near, or one while under this share of health (as before). */
    static final float WOUNDED = 0.6F;
    /** PROPOSED: escapes in a row that did not shake the monsters, and hits taken in one encounter, before it retreats. */
    static final int MAX_FAILED_EVASIONS = 4, MAX_HITS = 3;
    /**
     * PROPOSED: escapes in one encounter (it ends after 100 quiet ticks) before the scout gives up and retreats home,
     * even when each one shook the monsters for a moment: a monster that keeps standing in the way home, or a stream
     * of them, must not keep it stepping aside forever (on the way home there is no overdue check to stop it).
     */
    static final int MAX_ENCOUNTER_ESCAPES = 10;
    /** PROPOSED: the longest run to one escape point, and how long without a monster in reach ends an encounter. */
    static final int ESCAPE_TICKS = 160, QUIET_TICKS = 100;
    /** PROPOSED: monsters are looked for this far around (8 up or down); this close they count even unseen. */
    static final int LOOK = 20, CLOSE = 6;
    /** PROPOSED: cornered with a monster this close, a scout over half health fights back for this long. */
    static final double MELEE_REACH = 3.5;
    static final int DEFEND_TICKS = 100;
    static final double RUN_SPEED = 1.3;
    /** Route checks (path searches) per escape decision, and how far inside the map edge an escape point stays. */
    static final int ROUTE_CHECKS = 4, ESCAPE_MAP_MARGIN = 8;

    public ScoutJob(FactionNpc npc) {
        super(npc);
    }

    /**
     * Retreating home it keeps moving even when attacked. Away from home it does not take up a fight with a monster -
     * its own rules step around them - unless it is cornered and fighting back while fit.
     */
    @Override
    public boolean ignoresCombat() {
        if (state == State.RETURN && retreating) {
            return true;
        }
        if (state != State.OUTBOUND && state != State.RETURN || defending()) {
            return false;
        }
        return npc.getTarget() instanceof Monster m && CombatRules.isHostileMonster(m);
    }

    /** Away from home the scout decides itself when to step aside, fight back or go home. */
    @Override
    public boolean handlesOwnSafety() {
        return state == State.OUTBOUND || state == State.RETURN;
    }

    @Override
    public String workplaceInfo() {
        return switch (state) {
            case HOME -> "At home between expeditions";
            case OUTBOUND -> (escape != null ? "Evading " + evadeInfo + " on the way to " : "Exploring toward ") + targetInfo() + ", "
                    + pending.size() + " observations held";
            case RETURN -> (retreating ? "RETREATING (" + retreatReason + ")" : escape != null ? "Evading " + evadeInfo + " on the way home"
                    : "Returning") + " with " + pending.size() + " observations";
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
            case RETURN -> returning(b);
            case REPORT -> report(b);
        }
    }

    private void returning(BaseState b) {
        BlockPos station = b.point(BaseState.STATION_STAND);
        if (!retreating) {
            if (dangerCheck(b, station)) {
                return; // retreating now
            }
            observe(b);
            if (escape != null) {
                evade(station);
                return;
            }
        }
        double speed = retreating ? RUN_SPEED : 1.0;
        if (goTo(station, 1.8, "the reporting station", speed)) {
            state = State.REPORT;
            timer = 0;
        } else if (retreating) {
            status("Retreating home: " + retreatReason, "Retreating");
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
        sightings.clear();
        sectorsVisited = 0;
        failedTargets = 0;
        retreating = false;
        evasions = 0;
        endEncounter();
        NpcRecord rec = npc.record();
        if (rec != null) {
            rec.departedAt = departedAt;
        }
        state = State.OUTBOUND;
    }

    /**
     * The nearest unexplored sector (with some chance) at least 96 blocks from home, within range and the campaign
     * map, and not within 48 blocks of a pillager outpost or woodland mansion the faction knows of or this scout has
     * seen on this trip.
     */
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
                if (f.explored.contains(key) || pendingSectors.contains(key)
                        || nearDanger(f, FactionState.sectorCenterX(key), FactionState.sectorCenterZ(key))) {
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

    /** Within 48 blocks (Chebyshev) of a pillager outpost or woodland mansion known to the faction or seen on this trip. */
    private boolean nearDanger(FactionState f, int x, int z) {
        BlockPos p = new BlockPos(x, 0, z);
        if (BaseZones.nearKnownDanger(f, p, BaseZones.DANGER_RADIUS)) {
            return true;
        }
        for (Report r : pending) {
            if (r.kind.dangerousGround() && Zones.chebyshev(x, z, r.pos.getX(), r.pos.getZ()) <= BaseZones.DANGER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void outbound(BaseState b) {
        BlockPos goal = resolveTarget();
        if (dangerCheck(b, goal)) {
            return; // retreating home now
        }
        observe(b);
        long away = now() - departedAt;
        if (away > FCConfig.SCOUT_OVERDUE_TICKS.get() / 2) {
            beginReturn(false, ""); // overdue: home, stepping around monsters on the way if need be
            return;
        }
        if (escape != null) {
            evade(goal);
            return;
        }
        if (nearDanger(data().faction(npc.getFaction()), target.getX(), target.getZ())) {
            // a pillager outpost or woodland mansion turned up by the target: explore somewhere else
            target = pickTarget(b);
            if (target == null) {
                beginReturn(false, "");
            }
            return;
        }
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
            return;
        }
        status("Scouting toward " + targetInfo() + " (" + pending.size() + " observations)", "Scouting");
    }

    private void beginReturn(boolean retreat, String reason) {
        state = State.RETURN;
        retreating = retreat;
        retreatReason = reason;
        escape = null;
        defendUntil = 0;
        npc.navigator().reset();
        if (retreat) {
            npc.setTarget(null);
            status("Retreating home: " + reason, "Retreating");
        }
    }

    // ------------------------------------------------------------------ danger

    /**
     * Every 10 ticks away from home (not inside its own walls, where the guards are). Enemy soldiers mean an announced
     * retreat home as before: two threats near, or one while under 60% health; once announced the home destination is
     * fixed and later threats do not change it. Monsters do not end the trip: the scout steps aside from them
     * ({@link #startEscape}, {@link #evade}) and goes on. It retreats home from monsters only when badly hurt (under
     * 40%), when it keeps being hit (3 hits in one encounter), when stepping aside has failed 4 times in a row, or
     * after 10 escapes in one encounter.
     * Returns true when a retreat began.
     */
    private boolean dangerCheck(BaseState b, BlockPos goal) {
        if (++dangerTimer % 10 != 0) {
            return false;
        }
        if (BaseZones.insideWalls(b, npc.blockPosition(), 0)) {
            endEncounter();
            return false;
        }
        List<LivingEntity> soldiers = level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(10), e -> e.isAlive()
                && e instanceof FactionNpc o && com.factionclash.campaign.Diplomacy.hostile(npc.getServer(), npc.getFaction(), o.getFaction())
                && o.getRole().combatant);
        List<Monster> around = monstersAround();
        List<Monster> threats = threatening(around);
        float health = npc.getHealth() / npc.getMaxHealth();
        int total = soldiers.size() + threats.size();
        if (!soldiers.isEmpty() && (total >= 2 || health < WOUNDED)) {
            LivingEntity t = soldiers.get(0);
            addPending(new Report(Report.Kind.HOSTILES, t.blockPosition(), now(), ((FactionNpc) t).getFaction(), total,
                    total + " hostile(s) near " + t.blockPosition().toShortString()));
            endEncounter();
            beginReturn(true, total + " hostile(s) nearby" + (health < WOUNDED ? ", wounded" : ""));
            return true;
        }
        countHits();
        if (threats.isEmpty()) {
            if (escape == null && now() - lastThreatAt > QUIET_TICKS) {
                endEncounter();
            }
            return false;
        }
        lastThreatAt = now();
        evadeInfo = describe(threats);
        if (health < BADLY_HURT) {
            return retreatFrom(threats, "badly hurt, " + evadeInfo + " about");
        }
        if (hits >= MAX_HITS) {
            return retreatFrom(threats, "kept being hit by " + evadeInfo);
        }
        if (escape == null) {
            noteHostiles(threats);
            startEscape(around, threats, goal);
            return state == State.RETURN && retreating;
        }
        if (EscapeRoute.margin(escape.getX() + 0.5, escape.getZ() + 0.5, toThreats(clearanceSet(around, threats))) < Math.min(0, escapeMargin) - 2) {
            // a monster now stands by the escape point (chasers closing in): another way, counted as a failed escape
            escape = null;
            if (++failedEvasions >= MAX_FAILED_EVASIONS) {
                return retreatFrom(threats, "could not shake " + evadeInfo);
            }
            startEscape(around, threats, goal);
            return state == State.RETURN && retreating;
        }
        return false;
    }

    /** One tick of the run to the escape point; when the run is over, back on the route or another escape. */
    private void evade(BlockPos goal) {
        escapeTicks++;
        NpcNavigator.Result r = npc.navigator().travel(escape, 1.5, RUN_SPEED);
        status("Evading " + evadeInfo, "Evading");
        if (r == NpcNavigator.Result.MOVING && escapeTicks < ESCAPE_TICKS) {
            return;
        }
        escape = null;
        npc.navigator().reset();
        List<Monster> around = monstersAround();
        List<Monster> threats = threatening(around);
        if (threats.isEmpty()) {
            failedEvasions = 0; // shaken off: on along the route (the encounter ends once it stays quiet)
            status("Slipped past " + escapedFrom + ", going on", "Scouting");
            return;
        }
        evadeInfo = describe(threats);
        if (++failedEvasions >= MAX_FAILED_EVASIONS) {
            retreatFrom(threats, "could not shake " + evadeInfo);
            return;
        }
        startEscape(around, threats, goal);
    }

    /**
     * Picks an escape point ({@link EscapeRoute}) and starts running there. Cornered - nowhere clear to run - with a
     * monster in melee reach, a scout over half health fights back for a while; otherwise the failed attempt counts
     * and, after 4 in a row, it retreats home. After 10 escapes in one encounter it retreats home instead.
     */
    private void startEscape(List<Monster> around, List<Monster> threats, BlockPos goal) {
        if (encounterEscapes >= MAX_ENCOUNTER_ESCAPES) {
            retreatFrom(threats, "could not get past " + evadeInfo);
            return;
        }
        BlockPos to = pickEscape(toThreats(clearanceSet(around, threats)), goal);
        if (to != null) {
            escape = to;
            escapeTicks = 0;
            escapedFrom = evadeInfo;
            evasions++;
            encounterEscapes++;
            npc.setTarget(null);
            npc.navigator().reset();
            status("Evading " + evadeInfo, "Evading");
            return;
        }
        failedEvasions++;
        Monster close = null;
        for (Monster m : threats) {
            if (npc.distanceTo(m) <= MELEE_REACH && (close == null || npc.distanceToSqr(m) < npc.distanceToSqr(close))) {
                close = m;
            }
        }
        if (close != null && npc.getHealth() >= npc.getMaxHealth() * 0.5F) {
            defendUntil = now() + DEFEND_TICKS;
            npc.setTarget(close);
            status("Cornered by " + evadeInfo + " - fighting back", "Fighting");
            return;
        }
        if (failedEvasions >= MAX_FAILED_EVASIONS) {
            retreatFrom(threats, "cornered by " + evadeInfo);
            return;
        }
        status("Looking for a way past " + evadeInfo, "Evading");
    }

    /**
     * The best escape point that has firm, dry ground (in a loaded chunk, on the campaign map, away from known
     * outposts and mansions) and a walking route; null when there is none worth taking.
     */
    private BlockPos pickEscape(List<EscapeRoute.Threat> threats, BlockPos goal) {
        double sx = npc.getX(), sz = npc.getZ();
        boolean hasGoal = goal != null;
        List<EscapeRoute.Option> options = EscapeRoute.rank(sx, sz, threats, hasGoal ? goal.getX() + 0.5 : sx, hasGoal ? goal.getZ() + 0.5 : sz,
                hasGoal, lastDirX, lastDirZ);
        double here = EscapeRoute.margin(sx, sz, threats);
        FactionState f = data().faction(npc.getFaction());
        int routes = 0;
        for (EscapeRoute.Option o : options) {
            if (!o.clear() && o.margin() < here + 4) {
                break; // nothing clear, and the rest gains too little over standing here
            }
            if (!MapBounds.inside(data(), o.x(), o.z(), ESCAPE_MAP_MARGIN) || nearDanger(f, o.x(), o.z())) {
                continue;
            }
            BlockPos feet = standable(o.x(), o.z());
            if (feet == null) {
                continue;
            }
            if (++routes > ROUTE_CHECKS) {
                break;
            }
            Path p = npc.getNavigation().createPath(feet, 1);
            if (p == null || !p.canReach()) {
                continue;
            }
            lastDirX = o.dirX();
            lastDirZ = o.dirZ();
            escapeMargin = o.margin();
            return feet;
        }
        return null;
    }

    /**
     * Firm, dry ground to stand on in column (x, z) of a loaded chunk: its surface when that is within 6 blocks of the
     * scout's own level, else (in a cave, or by a cliff) a spot within 2 of its level. Not on water, lava, leaves or a
     * trunk, and not at a cliff edge or a shore.
     */
    private BlockPos standable(int x, int z) {
        LevelChunk c = level().getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null) {
            return null;
        }
        int surface = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        int y0 = npc.getBlockY();
        int[] ys = Math.abs(surface - y0) <= 6 ? new int[]{surface} : new int[]{y0, y0 + 1, y0 - 1, y0 + 2, y0 - 2};
        for (int y : ys) {
            BlockPos feet = new BlockPos(x, y, z);
            if (NpcNavigator.isSafeStandingSpot(level(), npc, feet) && noDropBeside(feet)) {
                return feet;
            }
        }
        return null;
    }

    /** No drop of more than 3 blocks and no water or lava right beside the spot (all four sides loaded). */
    private boolean noDropBeside(BlockPos feet) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(d);
            if (!level().isLoaded(n)) {
                return false;
            }
            boolean ground = false;
            for (int dy = 0; dy <= 4 && !ground; dy++) {
                BlockPos q = n.below(dy);
                BlockState s = level().getBlockState(q);
                if (!s.getFluidState().isEmpty()) {
                    return false;
                }
                ground = !s.getCollisionShape(level(), q).isEmpty();
            }
            if (!ground) {
                return false;
            }
        }
        return true;
    }

    /** Retreats home from monsters, with the hostile group on record. Always true. */
    private boolean retreatFrom(List<Monster> threats, String reason) {
        noteHostiles(threats);
        endEncounter();
        beginReturn(true, reason);
        return true;
    }

    /** Two or more monsters make a hostile group worth reporting (where they gather). */
    private void noteHostiles(List<Monster> threats) {
        if (threats.size() < 2) {
            return;
        }
        double x = 0, z = 0;
        for (Monster m : threats) {
            x += m.getX();
            z += m.getZ();
        }
        BlockPos at = new BlockPos((int) Math.floor(x / threats.size()), threats.get(0).getBlockY(), (int) Math.floor(z / threats.size()));
        addPending(new Report(Report.Kind.HOSTILES, at, now(), null, threats.size(), describe(threats) + " near " + at.toShortString()));
    }

    /** The encounter is over: nothing to run from, nothing counted against the next one. */
    private void endEncounter() {
        escape = null;
        failedEvasions = 0;
        encounterEscapes = 0;
        hits = 0;
        lastHurtStamp = 0;
        lastDirX = 0;
        lastDirZ = 0;
        defendUntil = 0;
        evadeInfo = "";
    }

    /** Fighting back while cornered: for a short while, and only while over half health. */
    private boolean defending() {
        return defendUntil > now() && npc.getHealth() >= npc.getMaxHealth() * 0.5F;
    }

    /** Counts a fresh hit by a monster (within the last second) during an encounter. */
    private void countHits() {
        int stamp = npc.getLastHurtByMobTimestamp();
        if (stamp == lastHurtStamp) {
            return;
        }
        lastHurtStamp = stamp;
        if (npc.getLastHurtByMob() instanceof Monster m && CombatRules.isHostileMonster(m) && npc.tickCount - stamp <= 20
                && (escape != null || now() - lastThreatAt <= QUIET_TICKS)) {
            hits++;
        }
    }

    /** Hostile monsters within 20 blocks (8 up or down). */
    private List<Monster> monstersAround() {
        return level().getEntitiesOfClass(Monster.class, npc.getBoundingBox().inflate(LOOK, 8, LOOK), CombatRules::isHostileMonster);
    }

    /**
     * The monsters to get away from: any within 6 blocks on about the scout's level (or in sight), one hunting the
     * scout within its keep-away distance plus 4, and any other within its keep-away distance that the scout can see.
     * A monster behind a hill or down in a cave under its feet does not count, and archers only shoot what they can see.
     */
    private List<Monster> threatening(List<Monster> around) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : around) {
            double d = npc.distanceTo(m), keep = keepAway(m);
            boolean close = d <= CLOSE && (Math.abs(m.getY() - npc.getY()) <= 3 || npc.hasLineOfSight(m));
            if (close || m.getTarget() == npc && d <= keep + 4 || d <= keep && npc.hasLineOfSight(m)) {
                out.add(m);
            }
        }
        return out;
    }

    /** The monsters an escape point must be clear of: the threats, and any other on about the scout's level. */
    private List<Monster> clearanceSet(List<Monster> around, List<Monster> threats) {
        List<Monster> out = new ArrayList<>(threats);
        for (Monster m : around) {
            if (!out.contains(m) && Math.abs(m.getY() - npc.getY()) <= 3) {
                out.add(m);
            }
        }
        return out;
    }

    private static List<EscapeRoute.Threat> toThreats(List<Monster> monsters) {
        List<EscapeRoute.Threat> out = new ArrayList<>();
        for (Monster m : monsters) {
            out.add(new EscapeRoute.Threat(m.getX(), m.getZ(), keepAway(m)));
        }
        return out;
    }

    /**
     * PROPOSED: how far from a monster counts as clear: 16 from a creeper (its blast), 18 from an archer (skeletons,
     * pillagers and witches shoot from about 15), 12 from anything else.
     */
    public static double keepAway(Monster m) {
        if (m instanceof Creeper) {
            return 16;
        }
        return m instanceof RangedAttackMob ? 18 : 12;
    }

    /** "a zombie", or "3 monsters (zombie, skeleton)": the kinds most numerous first, at most three named. */
    public static String describe(List<? extends LivingEntity> monsters) {
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (LivingEntity m : monsters) {
            kinds.merge(m.getType().getDescription().getString().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        List<String> names = new ArrayList<>(kinds.keySet());
        names.sort((a, b) -> kinds.get(b) - kinds.get(a));
        if (monsters.size() == 1) {
            String n = names.get(0);
            return ("aeiou".indexOf(n.charAt(0)) >= 0 ? "an " : "a ") + n;
        }
        return monsters.size() + " monsters (" + String.join(", ", names.subList(0, Math.min(3, names.size()))) + ")";
    }

    // ------------------------------------------------------------------ observing

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
        // the world's structures in sight (loaded chunks only), each reported once per trip: villages - abandoned ones
        // too - pillager outposts and woodland mansions
        for (StructureSightings.Sighting s : StructureSightings.look(level(), here, sightings.keySet())) {
            sightings.put(s.start(), s);
            addPending(StructureSightings.report(level(), here, s, now));
        }
        // a village: villagers with a trade, worth a trader's trip (reported at the village they belong to when its
        // structure is in sight, else where they gather)
        List<net.minecraft.world.entity.npc.Villager> villagers = level().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                npc.getBoundingBox().inflate(32, 12, 32), com.factionclash.work.TraderJob::trades);
        if (villagers.size() >= 2) {
            double vx = 0, vz = 0;
            for (var v : villagers) {
                vx += v.getX();
                vz += v.getZ();
            }
            BlockPos at = new BlockPos((int) Math.floor(vx / villagers.size()), villagers.get(0).getBlockY(), (int) Math.floor(vz / villagers.size()));
            String traders = villagers.size() + " villagers with trades (" + StructureSightings.professions(villagers) + ")";
            StructureSightings.Sighting village = villageAround(at);
            addPending(village != null ? new Report(Report.Kind.VILLAGE, village.pos(), now, null, villagers.size(), village.name() + ", " + traders)
                    : new Report(Report.Kind.VILLAGE, at, now, null, villagers.size(), traders));
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

    /** A village seen on this trip whose bounds (16 blocks around them) hold (x, z) of {@code p}, or null. */
    private StructureSightings.Sighting villageAround(BlockPos p) {
        for (StructureSightings.Sighting s : sightings.values()) {
            if (s.kind() == Report.Kind.VILLAGE && p.getX() >= s.box().minX() - 16 && p.getX() <= s.box().maxX() + 16
                    && p.getZ() >= s.box().minZ() - 16 && p.getZ() <= s.box().maxZ() + 16) {
                return s;
            }
        }
        return null;
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

    /**
     * Holds an observation for the report, replacing an earlier one of the same subject on this trip. Looks at one
     * village keep the most traders seen at once (a glance from afar sees fewer) and the village's own position.
     */
    private void addPending(Report r) {
        for (int i = 0; i < pending.size(); i++) {
            Report old = pending.get(i);
            if (old.sameSubject(r)) {
                pending.set(i, mergeOnTrip(old, r));
                return;
            }
        }
        if (pending.size() < 24) {
            pending.add(r);
        }
    }

    /** Two looks at the same subject on one trip (see {@link #addPending}). */
    public static Report mergeOnTrip(Report old, Report fresh) {
        if (fresh.kind != Report.Kind.VILLAGE) {
            return fresh;
        }
        Report m = fresh.copy();
        if (old.count > fresh.count) {
            m.count = old.count;
            m.note = old.note;
        }
        return m;
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
        Map<String, Integer> places = new LinkedHashMap<>();
        for (Report r : pending) {
            r.deliveredAt = now();
            r.by = npc.npcName();
            if (f.addReport(r)) {
                fresh++;
                if (r.kind.structure()) {
                    places.merge(r.kind.label.toLowerCase(Locale.ROOT), 1, Integer::sum);
                }
            }
            xp += ScoutRules.reportXp(r.kind.xpValue, Math.sqrt(r.pos.distSqr(b.center)));
        }
        f.explored.addAll(pendingSectors);
        f.reportsDelivered += pending.size();
        data().setDirty();
        npc.awardXp(xp);
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Scout " + npc.npcName() + " returned"
                + (retreating ? " after retreating (" + retreatReason + ")" : "") + " and reported " + pending.size()
                + " observations (" + fresh + " new)" + (places.isEmpty() ? "" : "; new on the map: " + placesText(places))
                + (evasions > 0 ? "; stepped aside from monsters " + evasions + (evasions == 1 ? " time" : " times") : "") + "."));
        pending.clear();
        pendingSectors.clear();
        NpcRecord rec = npc.record();
        if (rec != null) {
            rec.departedAt = 0;
        }
        departedAt = 0;
        retreating = false;
        evasions = 0;
        state = State.HOME;
        waitFor(200);
    }

    /** "village, 2 pillager outposts": the new places on the map by kind. */
    static String placesText(Map<String, Integer> places) {
        List<String> parts = new ArrayList<>();
        places.forEach((label, n) -> parts.add(n == 1 ? label : n + " " + label + "s"));
        return String.join(", ", parts);
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
        tag.putInt("evasions", evasions);
    }

    public String targetInfo() {
        return target == null ? "none" : target.getX() + ", " + (target.getY() == UNKNOWN_Y ? "?" : target.getY()) + ", " + target.getZ();
    }

    /** The state's name, and whether the scout is retreating home (for the GameTests). */
    public String stateName() {
        return state.name();
    }

    public boolean isRetreating() {
        return retreating;
    }

    /** Whether it is running to an escape point now, and how often it stepped aside this trip (for the GameTests). */
    public boolean isEvading() {
        return escape != null;
    }

    public int evasions() {
        return evasions;
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
        evasions = tag.getInt("evasions");
        endEncounter(); // an escape in progress is not resumed: the scout looks around afresh
        sightings.clear();
        if (state == State.OUTBOUND && target == null) {
            state = State.RETURN;
        }
        if (state == State.REPORT) {
            state = State.RETURN;
        }
    }
}
