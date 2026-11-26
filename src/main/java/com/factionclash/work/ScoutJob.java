package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseWatch;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.MapBounds;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.ReconOrder;
import com.factionclash.campaign.ReconOrders;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.RollCall;
import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import com.factionclash.logic.EscapeRoute;
import com.factionclash.logic.DirtPatch;
import com.factionclash.logic.ScoutRules;
import com.factionclash.logic.Zones;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.logic.campaign.ReconWatch;
import com.factionclash.logic.campaign.Whereabouts;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 *
 * <p>Focused reconnaissance ({@code docs/design/recon.md}, task R4b): a scout at home (reporting at the station, or at
 * the storehouse before it sets out) takes its council's open recon order instead of exploring, fetches or digs the dirt
 * for a lookout pillar, walks to the side of the enemy base the order names, finds a vantage column outside the guards'
 * reach ({@link ReconVantage}), climbs it looking after each block ({@link PillarClimb}), watches the yard for a minute
 * counting each soldier once by its face ({@link ReconWatch}), takes the pillar down and carries what it saw home, held
 * apart from its other reports and told first at the station ({@link ReconTrip}).</p>
 */
public class ScoutJob extends Job {
    enum State { HOME, DIG, OUTBOUND, RETURN, REPORT }

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

    /**
     * The recon trip it is on (null: exploring, or at home with no order), with what it brings home, held apart from
     * {@link #pending} until the station ({@code docs/design/recon.md} 4). Saved.
     */
    private ReconTrip recon;
    /** Breaks the pillar's blocks on the way down, and digs dirt at home. */
    private final BlockWork.Breaker breaker = new BlockWork.Breaker();
    /**
     * Dirt it digs for its pillar at home when the storehouse is short (recon.md 3.5, under {@link DirtDig}'s rules, in
     * its own base's home area): the search, the patch's cells and the one it digs now. Not saved: a reload goes back to
     * the storehouse and looks again.
     */
    private DirtDig.Search digSearch;
    private final List<BlockPos> digCells = new ArrayList<>();
    private final Set<Long> digPatch = new HashSet<>();
    private BlockPos digTarget;
    private boolean digChecked, digTried;
    private long digStart;
    private int digSearches, digWalk;
    /** PROPOSED: how long it digs dirt for a pillar before it sets out with what it has, and the searches it makes. */
    static final int DIG_GIVE_UP_TICKS = 2400, MAX_DIG_SEARCHES = 2;
    /** PROPOSED: vantage columns of one side it tries to walk to before it gives the side up. */
    static final int MAX_COLUMNS = 6;
    /** The watch's cadence: one look every 40 ticks (as {@link #observe}). */
    static final int SAMPLE_TICKS = 40;

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
    /**
     * PROPOSED: escapes on one expedition before the scout gives the trip up and heads home: past this the land is
     * too thick with monsters to get anywhere (a typical trip needs well under 20).
     */
    static final int MAX_TRIP_ESCAPES = 40;
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
        if (state == State.RETURN && retreating || reconPerched()) {
            return true; // (on its lookout pillar it holds still: a hit brings it down, recon.md 3.5)
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

    /** On its lookout pillar (or jumping up onto it): nothing moves it off before it has taken the pillar down. */
    @Override
    public boolean onScaffold() {
        return reconPerched();
    }

    /**
     * Before an errand home (food, a tool): down its lookout pillar first, block by block, until its feet are on the
     * ground (the watch ends there, its report by the watch's rule); then home with what it saw.
     */
    @Override
    protected boolean beforeErrand() {
        if (!reconAtColumn()) {
            return true;
        }
        if (recon.step != ReconTrip.Step.DOWN) {
            recon.end = ReconTrip.End.HOME;
            recon.why = "an errand home";
            startDown(recon);
        }
        BaseState t = reconBase(recon);
        if (t == null) {
            recon.pillar = null; // (nothing to watch any more: on the ground it simply goes)
            return true;
        }
        if (reconAtColumn()) {
            reconPillar(recon, t);
        }
        return !reconAtColumn();
    }

    /** At its column on the recon trip: a pillar begun and not yet taken down (on it, or still at its foot). */
    private boolean reconAtColumn() {
        return recon != null && state == State.OUTBOUND && recon.phase == ReconTrip.Phase.PILLAR && recon.pillar != null;
    }

    /** Its lookout pillar's dirt (recon.md 3.5) stays with it when it unloads: {@link ReconRules#BLOCKS}, dirt first. */
    @Override
    protected int keep(ItemStack s) {
        if (s.is(Items.DIRT)) {
            return ReconRules.BLOCKS;
        }
        if (s.is(Items.COARSE_DIRT)) {
            return Math.max(0, ReconRules.BLOCKS - Inv.count(npc.inventory(), x -> x.is(Items.DIRT)));
        }
        return 0;
    }

    @Override
    public String workplaceInfo() {
        if (recon != null && state == State.OUTBOUND) {
            return reconInfo();
        }
        return switch (state) {
            case HOME -> recon != null ? "At home, about to set out to watch " + recon.what() : "At home between expeditions";
            case DIG -> "Digging dirt for a lookout pillar (" + Scaffold.carried(npc) + "/" + ReconRules.BLOCKS + ")";
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
        // a rival the faction knows of: a settlement its scouts reported standing (not seen in ruins since); the true list
        // of bases is not the faction's to read (knowledge review of 2026-11-19)
        boolean rivalKnown = f.knowledge.stream().anyMatch(r -> r.kind == com.factionclash.campaign.Report.Kind.ENEMY_BASE && !r.fallen
                && com.factionclash.campaign.Diplomacy.hostile(npc.getFaction(), r.subject));
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
            case DIG -> dig(b);
            case OUTBOUND -> {
                if (recon != null) {
                    reconOutbound(b);
                } else {
                    outbound(b);
                }
            }
            case RETURN -> returning(b);
            case REPORT -> report(b);
        }
    }

    /**
     * Its trip's plan from its departure: an expedition's bound ({@code war.scoutOverdueTicks}; it turns home at half of
     * it), or a recon trip's own ({@link Whereabouts#reconPlan}: the walk there, the vantage allowance, the watch and the
     * walk home, recon.md 2.5).
     */
    @Override
    public long tripPlanTicks(long now) {
        if (recon != null) {
            return Whereabouts.reconPlan(now, departedAt, recon.distance);
        }
        return Whereabouts.scoutPlan(now, departedAt, FCConfig.SCOUT_OVERDUE_TICKS.get());
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
        if (recon == null) {
            ReconOrder o = openOrder(b); // an open order wins over exploring (recon.md 2.1)
            if (o != null) {
                takeOrder(b, o);
            }
        }
        if (recon != null) {
            prepareRecon(b);
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
                && e instanceof FactionNpc o && com.factionclash.campaign.Diplomacy.hostile(npc.getFaction(), o.getFaction())
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
        if (evasions >= MAX_TRIP_ESCAPES) {
            retreatFrom(threats, "too many monsters about, last " + evadeInfo);
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
     * One swimming (a drowned in the river beside the scout's path) counts only when it is close or hunting the scout:
     * it is slow in the water and walking along a shore past it is safe.
     */
    private List<Monster> threatening(List<Monster> around) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : around) {
            double d = npc.distanceTo(m), keep = keepAway(m);
            boolean close = d <= CLOSE && (Math.abs(m.getY() - npc.getY()) <= 3 || npc.hasLineOfSight(m));
            boolean hunting = m.getTarget() == npc && d <= keep + 4;
            boolean seen = d <= keep && npc.hasLineOfSight(m) && !m.isInWater();
            if (close || hunting || seen) {
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
     * pillagers and witches shoot from about 15; a drowned only when it holds a trident), 12 from anything else.
     */
    public static double keepAway(Monster m) {
        if (m instanceof Creeper) {
            return 16;
        }
        boolean ranged = m instanceof Drowned d ? d.getMainHandItem().is(net.minecraft.world.item.Items.TRIDENT)
                : m instanceof RangedAttackMob;
        return ranged ? 18 : 12;
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
            if (other.faction == npc.getFaction()) {
                continue;
            }
            double d = Math.sqrt(other.center.distSqr(new BlockPos(here.getX(), other.center.getY(), here.getZ())));
            if (d <= BaseWatch.SIGHT) {
                // what it sees of the base from here (knowledge review of 2026-11-19): its walls or yard in view, the
                // soldiers and heroes in sight and how much of the yard it could see, or its ruins; nothing behind a hill
                Report look = BaseWatch.look(level(), npc, other, now);
                if (look != null) {
                    addPending(look);
                }
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
        if (fresh.base() && old.base()) {
            return Report.mergeBase(old, fresh, true); // the look that saw most of the yard, the heroes seen on either
        }
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
        if (recon != null) {
            deliverRecon(f); // the focused report first (recon.md 4), told to its order, then the other reports
        }
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
        ReconOrder o = openOrder(b); // an order the council gave while it was away is taken here, at the station
        if (o != null) {
            takeOrder(b, o);
        }
        waitFor(200);
    }

    // ------------------------------------------------------------------ the recon trip (docs/design/recon.md, task R4b)

    /**
     * Its base's open recon order no scout has taken yet (recon.md 2.1): the urgent one first. Orders are given at the
     * station and taken only at home, at the station or the storehouse; a scout out exploring knows of none until it is
     * back.
     */
    private ReconOrder openOrder(BaseState b) {
        FactionState f = data().faction(npc.getFaction());
        ReconOrder best = null;
        for (ReconOrder o : f.reconOrders) {
            if (!o.taken() && o.homeBase == b.id && o.target != null && o.kind != null && (best == null || o.urgent && !best.urgent)) {
                best = o;
            }
        }
        return best;
    }

    /**
     * Takes the order ({@link ReconOrders#take}) and plans the trip from what its faction knows: the target's reported
     * position and kind, its own base, and the sides tried since the last good recon of it (the kept report's mask). The
     * sides go gate facing home first ({@link ReconRules#sideOrder}); a settlement's gates are west and east, a lair has a
     * gap in each wall.
     */
    private void takeOrder(BaseState b, ReconOrder o) {
        FactionState f = data().faction(npc.getFaction());
        Report kept = ReconOrders.report(f, o);
        int tried = kept == null ? 0 : kept.reconSidesTried;
        boolean settlement = o.kind != Report.Kind.LAIR;
        List<Side> order = ReconRules.sideOrder(b.center.getX() - o.target.getX(), b.center.getZ() - o.target.getZ(),
                ReconVantage.gates(settlement));
        ReconOrders.take(o, npc.getUUID(), now());
        recon = new ReconTrip(o.target, o.subject, o.kind, o.urgent, Math.sqrt(b.center.distSqr(o.target)), ReconRules.tripSides(order, tried));
        digTried = false;
        data().setDirty();
        com.factionclash.FactionClash.LOGGER.info("[{}] Scout {} takes the {}order to watch {}", npc.getFaction().displayName, npc.npcName(),
                o.urgent ? "urgent " : "", recon.what());
    }

    /**
     * At the storehouse with an order: the dirt for its pillar ({@link ReconRules#BLOCKS}, a settlement only: a lair is
     * watched from the ground), from the storehouse first and dug by hand when it is short (once a trip); then it sets out.
     */
    private void prepareRecon(BaseState b) {
        if (!recon.lair()) {
            int more = ReconRules.BLOCKS - Scaffold.carried(npc);
            if (more > 0) {
                more -= takeFromStorage(x -> x.is(Items.DIRT), more);
            }
            if (more > 0) {
                takeFromStorage(x -> x.is(Items.COARSE_DIRT), more);
            }
            if (Scaffold.carried(npc) < ReconRules.BLOCKS && !digTried) {
                startDig();
                return;
            }
        }
        departRecon(b);
    }

    /** Sets out on the recon trip: the trip's plan runs from now (the roll call reads it when it is seen leaving). */
    private void departRecon(BaseState b) {
        departedAt = now();
        caveChecked.clear();
        sightings.clear();
        sectorsVisited = 0;
        failedTargets = 0;
        retreating = false;
        evasions = 0;
        target = null;
        endEncounter();
        NpcRecord rec = npc.record();
        if (rec != null) {
            rec.departedAt = departedAt;
        }
        recon.phase = ReconTrip.Phase.APPROACH;
        recon.waypoints = null;
        state = State.OUTBOUND;
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Scout " + npc.npcName() + " sets out to watch "
                + recon.what() + (recon.urgent ? " (urgent)" : "") + "."));
    }

    // ---- digging dirt at home

    private void startDig() {
        state = State.DIG;
        digStart = now();
        digSearch = null;
        digCells.clear();
        digPatch.clear();
        digTarget = null;
        digSearches = 0;
        breaker.reset(npc);
        npc.navigator().reset();
        status("Looking for open ground to dig dirt for a lookout pillar", "Digging");
    }

    /** The dig is over (enough dirt, no ground, or too long at it): back to the storehouse, and out from there. */
    private void endDig() {
        digTried = true;
        digSearch = null;
        digCells.clear();
        digTarget = null;
        breaker.reset(npc);
        state = State.HOME;
    }

    /**
     * One tick of digging dirt for its pillar (recon.md 3.5): a patch of open ground in its own base's home area, outside
     * every base's walls box, under {@link DirtDig}'s rules (the woodcutter's), found round the gate facing the target;
     * the top block of each cell dug by hand at player speed (grass drops dirt). It stops with {@link ReconRules#BLOCKS}
     * carried, with no ground to dig, or after {@link #DIG_GIVE_UP_TICKS}, and sets out with what it has.
     */
    private void dig(BaseState b) {
        int carried = Scaffold.carried(npc);
        if (recon == null || carried >= ReconRules.BLOCKS || Inv.freeSlots(npc.inventory()) == 0 || now() - digStart > DIG_GIVE_UP_TICKS) {
            endDig();
            return;
        }
        int radius = RollCall.homeRadius(b);
        if (digCells.isEmpty()) {
            if (digSearch == null) {
                if (digSearches >= MAX_DIG_SEARCHES) {
                    endDig();
                    return;
                }
                digSearches++;
                digSearch = new DirtDig.Search(level(), data(), b, digSeed(b), ReconRules.BLOCKS - carried, p -> false, radius);
            }
            status("Looking for open ground to dig dirt for a lookout pillar", "Digging");
            List<BlockPos> found = digSearch.step();
            if (found == null) {
                return;
            }
            digSearch = null;
            if (found.isEmpty()) {
                endDig(); // no ground to dig: out with what it has
                return;
            }
            digCells.addAll(found);
            digPatch.clear();
            for (BlockPos p : found) {
                digPatch.add(DirtPatch.key(p.getX(), p.getZ()));
            }
            digTarget = null;
        }
        if (digTarget == null) {
            double best = Double.MAX_VALUE;
            for (BlockPos c : digCells) {
                double d = c.distToCenterSqr(npc.getX(), npc.getY() - 0.5, npc.getZ())
                        + (npc.getBoundingBox().intersects(new AABB(c.above())) ? 1000 : 0);
                if (d < best) {
                    best = d;
                    digTarget = c;
                }
            }
            digWalk = 0;
            digChecked = false;
        }
        if (!level().isLoaded(digTarget) || !DirtDig.earth(level().getBlockState(digTarget))) {
            dropDigCell();
            return;
        }
        if (!BlockWork.inReach(npc, digTarget, 4.5)) {
            if (++digWalk > 600) {
                digCells.clear(); // cannot get to it: another search
                digTarget = null;
                return;
            }
            goTo(digTarget.above(), 2.0, "open ground to dig dirt");
            return;
        }
        npc.getNavigation().stop();
        if (!digChecked) {
            if (!DirtDig.mayDigNow(level(), data(), b, digPatch, digTarget, p -> false, radius)) {
                dropDigCell();
                return;
            }
            digChecked = true;
        }
        status("Digging dirt for a lookout pillar (" + carried + "/" + ReconRules.BLOCKS + ")", "Digging");
        if (breaker.tick(npc, digTarget)) {
            dropDigCell();
        }
    }

    private void dropDigCell() {
        digCells.remove(digTarget);
        digTarget = null;
        breaker.reset(npc);
    }

    /** Where it looks for ground to dig: outside its walls, toward the base it will watch. */
    private BlockPos digSeed(BaseState b) {
        double dx = recon.target.getX() - b.center.getX(), dz = recon.target.getZ() - b.center.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        int out = BaseZones.half(b) + 12;
        return d < 1 ? b.center.east(out) : b.center.offset((int) Math.round(dx / d * out), 0, (int) Math.round(dz / d * out));
    }

    // ---- the trip

    /** The base it was ordered to watch, as it finds it there (the faction's report names its position and kind), or null. */
    private BaseState reconBase(ReconTrip r) {
        BaseState best = null;
        for (BaseState x : data().bases.values()) {
            if (x.faction == r.subject && x.center != null && x.isSettlement() == !r.lair() && x.center.distSqr(r.target) <= 40 * 40
                    && (best == null || x.center.distSqr(r.target) < best.center.distSqr(r.target))) {
                best = x;
            }
        }
        return best;
    }

    /** On its lookout pillar now, or jumping up onto it: it holds still there, and only a hit or a broken block brings it down. */
    private boolean reconPerched() {
        ReconTrip r = recon;
        if (r == null || state != State.OUTBOUND || r.phase != ReconTrip.Phase.PILLAR || r.pillar == null) {
            return false;
        }
        return r.pillar.height > 0 || r.pillar.phase == Scaffold.Phase.CLIMB && !npc.onGround();
    }

    /** The column's foot (null: none yet), the next corner on the way round, or the side's approach, for its walk. */
    private BlockPos reconGoal(ReconTrip r, BaseState t) {
        if (r.foot != null && (r.phase == ReconTrip.Phase.FOOT || r.phase == ReconTrip.Phase.PILLAR)) {
            return r.foot;
        }
        Side s = r.current();
        if (s == null) {
            return t.center;
        }
        if (r.waypoints != null && !r.waypoints.isEmpty()) {
            ReconRules.Point p = r.waypoints.get(0);
            return surface(t.center.getX() + p.dx(), t.center.getZ() + p.dz());
        }
        int out = ReconVantage.band(t).standoff() + 1;
        return surface(t.center.getX() + s.dx * out, t.center.getZ() + s.dz * out);
    }

    /** Where a walker stands in column (x, z): on the ground when its chunk is loaded, else at the scout's own level. */
    private BlockPos surface(int x, int z) {
        if (level().hasChunk(x >> 4, z >> 4)) {
            return level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        }
        return new BlockPos(x, npc.getBlockY(), z);
    }

    /**
     * One tick of the recon trip away from home. On the ground it looks out for monsters and enemy soldiers as on any
     * trip ({@link #dangerCheck}, {@link #evade}) and keeps its eyes open ({@link #observe}); on its pillar it holds
     * still. It turns home in time to be back by its trip's plan.
     */
    private void reconOutbound(BaseState b) {
        ReconTrip r = recon;
        BaseState t = reconBase(r);
        if (t == null) {
            r.phase = ReconTrip.Phase.DONE; // nothing stands where the report said: home with what it saw on the way
            beginReturn(false, "");
            return;
        }
        if (!reconPerched()) {
            BlockPos goal = reconGoal(r, t);
            if (dangerCheck(b, goal)) {
                leaveColumn(r); // retreating home now
                return;
            }
            observe(b);
            if (escape != null) {
                leaveColumn(r);
                if (r.phase == ReconTrip.Phase.DONE) {
                    state = State.RETURN; // (a watch from the ground ended: home, stepping aside on the way)
                    return;
                }
                evade(goal);
                return;
            }
        }
        long turnHome = departedAt + Whereabouts.reconPlanTicks(r.distance) - Whereabouts.walkTicks(r.distance);
        if (now() >= turnHome && r.end == ReconTrip.End.NONE && r.phase != ReconTrip.Phase.DONE) {
            // the trip's time is up (it must be home by its plan): down, and home with what it saw
            r.end = ReconTrip.End.HOME;
            r.why = "out of time";
            if (r.phase == ReconTrip.Phase.PILLAR && r.pillar != null) {
                startDown(r);
            } else {
                r.phase = ReconTrip.Phase.DONE;
                beginReturn(false, "");
                return;
            }
        }
        switch (r.phase) {
            case PREPARE, APPROACH -> reconApproach(r, t);
            case FOOT -> reconFoot(r, t);
            case PILLAR -> reconPillar(r, t);
            case DONE -> beginReturn(false, "");
        }
    }

    /**
     * On the ground at its column when it must step aside or retreat: a pillar not climbed yet is given up (it walks back
     * to the foot afterwards); a watch kept from the ground (a lair's) ends there, its report by the watch's rule.
     */
    private void leaveColumn(ReconTrip r) {
        if (r.phase != ReconTrip.Phase.PILLAR || r.pillar == null || r.pillar.height > 0) {
            return;
        }
        if (r.step == ReconTrip.Step.WATCH) {
            r.end = ReconTrip.End.HOME;
            r.why = "monsters about";
            r.phase = ReconTrip.Phase.DONE;
        } else {
            r.phase = ReconTrip.Phase.FOOT;
        }
        r.pillar = null;
        breaker.reset(npc);
    }

    /**
     * To the side it tries now (recon.md 3.5): round the base by the corners of the standoff's square when it comes from
     * another side ({@link ReconRules#round}), then to the side's axis just beyond the standoff, where it looks for the
     * side's vantage columns ({@link ReconVantage#columns}, once): none, and it goes on to the next side.
     */
    private void reconApproach(ReconTrip r, BaseState t) {
        r.phase = ReconTrip.Phase.APPROACH;
        Side s = r.current();
        if (s == null) {
            r.phase = ReconTrip.Phase.DONE;
            beginReturn(false, "");
            return;
        }
        ReconRules.Band band = ReconVantage.band(t);
        if (r.waypoints == null) {
            BaseState home = base();
            int hx = home == null ? 0 : home.center.getX() - t.center.getX(), hz = home == null ? 0 : home.center.getZ() - t.center.getZ();
            int fx = npc.getBlockX() - t.center.getX(), fz = npc.getBlockZ() - t.center.getZ();
            r.waypoints = new ArrayList<>(ReconRules.round(fx, fz, s, band.standoff(), hx, hz));
            r.walkTicks = 0;
            r.routeFailBase = routeFailures();
        }
        BlockPos goal = reconGoal(r, t);
        boolean leg = !r.waypoints.isEmpty();
        boolean stuck = ++r.walkTicks > 2400 || routeFailures() - r.routeFailBase >= 3;
        if (nearColumn(goal, leg ? 3.0 : 4.0) || stuck) {
            r.walkTicks = 0;
            r.routeFailBase = routeFailures();
            if (leg) {
                r.waypoints.remove(0); // by this corner (or past it, when it could not get there)
                return;
            }
            if (stuck && !level().hasChunk(goal.getX() >> 4, goal.getZ() >> 4)) {
                nextSide(r); // could not get there, and nothing of it is loaded to look at
                return;
            }
            r.tried |= s.bit();
            r.columns = ReconVantage.columns(level(), data(), t, s);
            r.columnIndex = 0;
            if (r.columns.isEmpty()) {
                status("No vantage on the " + side(s) + " side of " + r.what(), "Scouting");
                nextSide(r);
                return;
            }
            pickColumn(r);
            return;
        }
        goTo(goal, leg ? 2.5 : 3.0, (leg ? "the way round " : "the " + side(s) + " side of ") + r.what());
    }

    private static String side(Side s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    private void pickColumn(ReconTrip r) {
        ReconVantage.Vantage v = r.columns.get(r.columnIndex);
        r.foot = v.foot();
        r.rise = v.rise();
        r.pillar = null;
        r.phase = ReconTrip.Phase.FOOT;
        r.walkTicks = 0;
        r.routeFailBase = routeFailures();
    }

    /** The next column of this side (it could not get to this one), or the next side after {@link #MAX_COLUMNS}. */
    private void nextColumn(ReconTrip r) {
        r.pillar = null;
        if (r.columns != null && ++r.columnIndex < Math.min(r.columns.size(), MAX_COLUMNS)) {
            pickColumn(r);
            return;
        }
        nextSide(r);
    }

    /** On to the next side of its trip, or home when it has tried them all (with a blind report, or none). */
    private void nextSide(ReconTrip r) {
        Side s = r.current();
        if (s != null && r.columns != null) {
            r.tried |= s.bit();
        }
        r.side++;
        r.columns = null;
        r.waypoints = null;
        r.foot = null;
        r.pillar = null;
        r.firstGood = -1;
        r.lookedAt = -1;
        r.step = ReconTrip.Step.CLIMB;
        if (r.current() == null) {
            r.phase = ReconTrip.Phase.DONE;
            beginReturn(false, "");
        } else {
            r.phase = ReconTrip.Phase.APPROACH;
        }
    }

    /**
     * To the column's foot (recon.md 3.5): there it stands over the column's middle and starts its pillar (a lair's
     * vantage is the ground: no pillar). A column it cannot get to is left for the next.
     */
    private void reconFoot(ReconTrip r, BaseState t) {
        if (r.columns == null) {
            // after a load: the side's columns again, from the one it was walking to
            r.columns = ReconVantage.columns(level(), data(), t, r.current());
            r.columnIndex = 0;
            for (int i = 0; i < r.columns.size(); i++) {
                if (r.columns.get(i).foot().equals(r.foot)) {
                    r.columnIndex = i;
                }
            }
            if (r.columns.isEmpty()) {
                nextSide(r);
                return;
            }
        }
        if (r.pillar == null) {
            r.pillar = new Scaffold.Pillar(r.foot, r.lair() ? 0 : ReconRules.PILLAR_MAX, false);
        }
        Scaffold.Pillar p = r.pillar;
        if (PillarClimb.atFoot(npc, p)) {
            PillarClimb.begin(npc, p);
            r.phase = ReconTrip.Phase.PILLAR;
            r.step = ReconTrip.Step.CLIMB;
            r.firstGood = -1;
            r.lookedAt = -1;
            breaker.reset(npc);
            return;
        }
        double d = Math.sqrt(npc.distanceToSqr(Vec3.atBottomCenterOf(r.foot)));
        if (++r.walkTicks > Whereabouts.walkTicks(d) * 3 + 400 || routeFailures() - r.routeFailBase >= 2) {
            nextColumn(r);
            return;
        }
        goTo(r.foot, 0.6, "a vantage on the " + side(r.current()) + " side of " + r.what());
    }

    /**
     * At its column (recon.md 3.5, steps 4 to 7, and "If enemies come near"): the climb and its looks, the watch from the
     * top, the hold while an enemy is near, the way down. A hit or a block of its pillar broken under it ends the watch at
     * once: it comes down block by block and retreats home, what it saw counting only past
     * {@link ReconRules#MIN_WATCH_TICKS} ({@link Intel.Recon#good}).
     */
    private void reconPillar(ReconTrip r, BaseState t) {
        Scaffold.Pillar p = r.pillar;
        if (PillarClimb.offColumn(npc, p)) {
            // moved off its column (it cannot be knocked off): the watch ends there, the blocks still standing stay
            r.end = ReconTrip.End.RETREAT;
            r.why = "off its pillar";
            r.pillar = null;
            r.phase = ReconTrip.Phase.DONE;
            beginReturn(true, r.why);
            return;
        }
        if (r.step != ReconTrip.Step.DOWN && r.end == ReconTrip.End.NONE) {
            String hit = npc.hurtTime > 0 ? "hurt on its pillar" : broken(p) ? "a block of its pillar broken" : null;
            if (hit != null) {
                r.end = ReconTrip.End.RETREAT;
                r.why = hit;
                startDown(r);
                if (r.pillar == null) {
                    return; // (it had not climbed yet)
                }
            }
        }
        switch (p.phase) {
            case WALK -> PillarClimb.begin(npc, p);
            case CLIMB -> {
                if (PillarClimb.climb(npc, p, breaker, climber(r, t)) == PillarClimb.Climb.JUMP) {
                    status("Building a lookout pillar on the " + side(r.current()) + " side of " + r.what() + " (" + p.height + " high)",
                            "Building");
                }
            }
            case WORK -> reconTop(r, t, p);
            case DOWN -> {
                switch (PillarClimb.stepDown(npc, p, breaker, climber(r, t))) {
                    case GROUND -> groundReached(r);
                    // a block it cannot break underfoot (someone put it there): left to the stuck recovery on its way
                    case STUCK -> groundReached(r);
                    default -> {
                    }
                }
            }
        }
    }

    /** A block of its pillar under its feet is gone (broken by someone). */
    private boolean broken(Scaffold.Pillar p) {
        for (int k = 0; k < p.height; k++) {
            if (!Scaffold.isBlock(level().getBlockState(p.base.above(k)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The scout's side of the climb ({@link PillarClimb.Climber}): the look once from the ground and after each block from
     * {@link ReconRules#LOOK_FROM} up, which sets where it stops; nothing over its head is cleared (the column had open
     * sky: the pillar ends under anything that came since); the step down's status line.
     */
    private PillarClimb.Climber climber(ReconTrip r, BaseState t) {
        return new PillarClimb.Climber() {
            @Override
            public void look(Scaffold.Pillar q) {
                if (ReconRules.looksAt(q.height)) {
                    reconLook(r, t, q);
                }
            }

            @Override
            public void breaking(Scaffold.Pillar q) {
                status("Taking its lookout pillar down (" + q.height + " high" + (r.why.isEmpty() ? "" : ", " + r.why) + ")", "Building");
            }
        };
    }

    /**
     * One look from where it stands (a recon sample, {@link BaseWatch#reconSample}): the trip keeps its best; the first
     * good one ({@link ReconRules#goodLook}) sets the pillar's stop {@link ReconRules#CLIMB_PAST} blocks higher, never past
     * {@link ReconRules#PILLAR_MAX}; with no good look at the cap it stops there (the next side).
     */
    private void reconLook(ReconTrip r, BaseState t, Scaffold.Pillar q) {
        ReconWatch.Sample s = BaseWatch.reconSample(level(), npc, t, now());
        r.looked(s, q.height);
        if (s != null && r.firstGood < 0 && ReconRules.goodLook(s.points(), s.middle())) {
            r.firstGood = q.height;
            q.goal = Math.max(q.height, ReconRules.stopHeight(q.height));
        }
        if (ReconRules.climb(q.height, r.firstGood) != ReconRules.Climb.UP) {
            q.goal = Math.min(q.goal, Math.max(q.height, r.firstGood < 0 ? q.height : ReconRules.stopHeight(r.firstGood)));
        }
    }

    /** On top (or on the ground at a lair): the climb is over, the watch, or a step of the way down. */
    private void reconTop(ReconTrip r, BaseState t, Scaffold.Pillar p) {
        if (!PillarClimb.settled(npc, p)) {
            return; // still landing on the block below
        }
        switch (r.step) {
            case CLIMB -> {
                if (r.lookedAt != p.height && ReconRules.looksAt(p.height) && r.end == ReconTrip.End.NONE) {
                    reconLook(r, t, p); // the look from the ground at a lair (no climb), or where a climb stopped short
                }
                if (r.end == ReconTrip.End.NONE && r.firstGood >= 0) {
                    startWatch(r, t, p);
                } else {
                    startDown(r); // no good look under the cap: down, and the next side
                }
            }
            case WATCH -> reconWatch(r, t, p);
            case DOWN -> {
                if (!PillarClimb.down(p)) {
                    groundReached(r);
                }
            }
        }
    }

    private void startWatch(ReconTrip r, BaseState t, Scaffold.Pillar p) {
        r.step = ReconTrip.Step.WATCH;
        r.watch = new ReconWatch();
        r.watchStartedAt = now();
        r.vantage = ReconRules.vantage(p.height, r.rise);
        r.watch.add(BaseWatch.reconSample(level(), npc, t, now()));
        com.factionclash.FactionClash.LOGGER.info("[{}] Scout {} watches {} from {} ({} of the yard's points in view at first)",
                npc.getFaction().displayName, npc.npcName(), r.what(), r.vantage, r.watch.points());
    }

    /**
     * The watch (recon.md 3.4 and 3.6): a look every {@link #SAMPLE_TICKS} for {@link ReconRules#WATCH_TICKS}, each
     * soldier counted once by its face; enemies near or not, what it sees counts. After the watch it holds still while an
     * enemy is close below ({@link ReconRules#perch}, at most {@link ReconRules#HOLD_MAX_TICKS}), then comes down.
     */
    private void reconWatch(ReconTrip r, BaseState t, Scaffold.Pillar p) {
        long now = now(), end = r.watchStartedAt + ReconRules.WATCH_TICKS, last = r.watch.last();
        if (last < end && (last < 0 || now - last >= SAMPLE_TICKS || now >= end)) {
            r.watch.add(BaseWatch.reconSample(level(), npc, t, now));
        }
        switch (ReconRules.perch(now, r.watchStartedAt, ReconRules.WATCH_TICKS, enemyNear(), false, false)) {
            case WATCH -> status("Watching " + r.what() + " from " + r.vantage + ": " + r.watch.soldiers().size()
                    + (r.lair() ? " monsters" : " soldiers") + " counted, " + Math.round(Intel.coverage(r.watch.points()) * 100)
                    + "% of the yard seen", "Watching");
            case HOLD -> status("Holding still on its lookout pillar: an enemy close below", "Watching");
            case DOWN, RETREAT -> {
                r.end = ReconTrip.End.HOME;
                startDown(r);
            }
        }
    }

    /**
     * An enemy in sight within {@link ReconRules#THREAT_RANGE}: a soldier or hero of a faction at war with ours, or an
     * enemy player ({@link CombatRules#isEnemyPlayer}). While one is that close the scout does not start down into it.
     */
    private boolean enemyNear() {
        AABB box = npc.getBoundingBox().inflate(ReconRules.THREAT_RANGE);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != npc)) {
            boolean enemy = e instanceof FactionNpc o ? !o.isDisplayOnly() && o.fightsLikeSoldier()
                    && com.factionclash.campaign.Diplomacy.hostile(npc.getFaction(), o.getFaction())
                    : e instanceof ServerPlayer sp && CombatRules.isEnemyPlayer(npc, sp);
            if (enemy && ReconRules.threat(npc.distanceTo(e), npc.hasLineOfSight(e))) {
                return true;
            }
        }
        return false;
    }

    /** It starts down its pillar now (block by block, as it came up); not climbed yet, it is simply on the ground. */
    private void startDown(ReconTrip r) {
        r.step = ReconTrip.Step.DOWN;
        breaker.reset(npc);
        Scaffold.Pillar p = r.pillar;
        if (p == null || PillarClimb.comeDown(npc, p, r.why.isEmpty() ? "done" : r.why)) {
            groundReached(r);
        }
    }

    /**
     * Its pillar is down (every block back in its backpack): home with what it saw (in a retreat when it was hurt or its
     * pillar broken), or on to the next side when it found no good look.
     */
    private void groundReached(ReconTrip r) {
        r.pillar = null;
        breaker.reset(npc);
        if (r.end != ReconTrip.End.NONE) {
            r.phase = ReconTrip.Phase.DONE;
            if (r.end == ReconTrip.End.RETREAT) {
                beginReturn(true, r.why);
            } else {
                beginReturn(false, "");
            }
            return;
        }
        nextSide(r);
    }

    /**
     * At the station (recon.md 4): the order learns what it brought ({@link ReconOrders#atStation}: a good recon, a blind
     * one, or none of the target, with the sides tried), and a recon, good or blind, is filed first among the faction's
     * reports, apart from the trip's other observations and their cap. Told in chat as the design's lines.
     */
    private void deliverRecon(FactionState f) {
        ReconTrip r = recon;
        recon = null;
        long now = now();
        Report brought = r.brought(npc.npcName(), now);
        ReconOrders.atStation(f, npc.getUUID(), brought);
        String line;
        if (brought != null && brought.recon != null) {
            brought.deliveredAt = now;
            brought.by = npc.npcName();
            f.addReport(brought);
            line = reconLine(r, brought.recon);
        } else {
            line = "Scout " + npc.npcName() + " returned from " + r.what() + " without a look inside ("
                    + (r.tried == 0 ? "it did not get there" : "no vantage found") + ").";
        }
        data().setDirty();
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal(line));
    }

    /**
     * "Scout Edrin Cole returned from watching the Stoneguard settlement: about 4 soldiers and Hinae Shirasuzu seen (40%
     * of the yard in view, watched 60 s)", or "...: could not see inside the walls".
     */
    private String reconLine(ReconTrip r, Intel.Recon c) {
        String head = "Scout " + npc.npcName() + " returned from watching " + r.what() + ": ";
        if (!c.good()) {
            return head + (r.lair() ? "could not see the lair's ground well enough" : "could not see inside the walls")
                    + " (" + Math.round(c.coverage() * 100) + "% in view at best" + (c.watchTicks() > 0
                    ? ", watched " + c.watchTicks() / 20 + " s before it had to come down" : "") + ").";
        }
        List<String> names = new ArrayList<>();
        for (String id : c.heroes().keySet()) {
            com.factionclash.hero.HeroEntry e = com.factionclash.hero.HeroRegistry.get(id);
            names.add(e == null ? id : e.name());
        }
        String who = c.soldiers() == 0 && names.isEmpty() ? (r.lair() ? "no monsters" : "an empty yard")
                : "about " + c.soldiers() + (r.lair() ? (c.soldiers() == 1 ? " monster" : " monsters") : (c.soldiers() == 1 ? " soldier" : " soldiers"))
                + (names.isEmpty() ? "" : " and " + String.join(", ", names));
        return head + who + " seen (" + Math.round(c.coverage() * 100) + "% of " + (r.lair() ? "its ground" : "the yard")
                + " in view, watched " + c.watchTicks() / 20 + " s from " + c.vantage() + ").";
    }

    /** The trip as the station's info shows it. */
    private String reconInfo() {
        ReconTrip r = recon;
        Side s = r.current();
        String at = s == null ? "" : " (" + side(s) + " side)";
        return switch (r.phase) {
            case PREPARE, APPROACH -> "On its way to watch " + r.what() + at;
            case FOOT -> "Walking to a vantage by " + r.what() + at + (r.foot == null ? "" : " at " + r.foot.toShortString());
            case PILLAR -> switch (r.step) {
                case CLIMB -> "Climbing a lookout pillar by " + r.what() + at;
                case WATCH -> "Watching " + r.what() + " from " + r.vantage + at;
                case DOWN -> "Taking its lookout pillar down" + at;
            };
            case DONE -> "Going home from " + r.what();
        };
    }

    /** The recon trip it is on, or null (for the GameTests): its phase and step, the sides tried, the watch, its pillar. */
    public String reconPhase() {
        return recon == null ? "none" : recon.phase.name() + (recon.phase == ReconTrip.Phase.PILLAR ? "/" + recon.step.name() : "");
    }

    /** The report it carries home from its recon trip, apart from {@link #pending} (null: none, or no recon trip). */
    public Report reconReport() {
        return recon == null ? null : recon.brought(npc.npcName(), now());
    }

    /** The recon trip's pillar now (null: none), the first good height (-1: none) and the watch (null: not started). */
    public Scaffold.Pillar reconPillar() {
        return recon == null ? null : recon.pillar;
    }

    public int reconFirstGood() {
        return recon == null ? -1 : recon.firstGood;
    }

    public ReconWatch reconWatch() {
        return recon == null ? null : recon.watch;
    }

    /** The sides the recon trip tried so far (a mask of {@link Side} bits; 0 with no trip). */
    public int reconTried() {
        return recon == null ? 0 : recon.tried;
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
        if (recon != null) {
            tag.put("recon", recon.save());
        }
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
        recon = tag.contains("recon") ? ReconTrip.load(tag.getCompound("recon")) : null;
        endEncounter(); // an escape in progress is not resumed: the scout looks around afresh
        sightings.clear();
        if (state == State.DIG) {
            state = State.HOME; // (the dig is not saved: back to the storehouse, which looks again)
        }
        if (state == State.OUTBOUND && target == null && recon == null) {
            state = State.RETURN;
        }
        if (state == State.REPORT) {
            state = State.RETURN;
        }
    }
}
