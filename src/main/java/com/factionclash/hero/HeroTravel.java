package com.factionclash.hero;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.FlightPlanner;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.npc.TravelHelper;
import com.factionclash.util.ChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hero's trips beyond walking (MOBILITY_AND_WORK 2.2, 3.5 and 3.8; task A11), as {@link NpcNavigator}'s helper: while
 * a link of its navigation runs the trip waits for it, and a flyer takes a leg in the air when that is the better way.
 *
 * <p><b>The mode</b> is chosen per waypoint, after the ground path to it was searched ({@link FlightPlanner#mode}): it
 * flies when there is no ground route or it is at least 1.5 times the straight line, or the trip is longer than 40
 * blocks and its average flight speed at its level beats walking, with the stamina for the leg, a legal landing, 4
 * blocks of headroom and the sky over it, out of a thunderstorm and not exhausted; legs under 8 blocks with a ground
 * route are walked (PROPOSED). A ground navigation that would give a trip up asks for a flight first. Out of a fight
 * only: a fight's flights are A12's.
 *
 * <p><b>In the air</b> it follows the cruise layer of {@link FlightPlanner}: the route round hostile bases' berth on a
 * trip, legs of one 24-block waypoint planned over loaded, entity-ticking terrain only (nothing is loaded or generated
 * for it) at 6-10 blocks over the highest terrain, the take-off and landing of {@link HeroMovement}, a 5x5 chunk ticket
 * while it flies; climbing, it rises in place while something overhangs the way ahead. Within 16 blocks of the
 * destination it picks a legal landing (the destination's column or the nearest open, standable cell within 8 blocks;
 * never water, lava, fire, powder snow, farmland or leaves) and lands on it; the ground navigation walks the rest.
 *
 * <p><b>The ground fallback</b>: at the edge of loaded terrain it hovers at most 3 s, then lands; a head bump while it
 * climbs for 10 ticks or under a block of progress in 2 s ends the flight (it lands, that leg is dead for 10 minutes);
 * its stamina at 25% or a downing lands it (A10); a fight lands it. The trip then goes on with the ground navigation,
 * whose failure count includes the flight's, so flying and walking cannot alternate forever. Nothing teleports.
 */
public final class HeroTravel implements TravelHelper {
    /** PROPOSED: a leg shorter than this is walked when it has a ground route at all. */
    public static final double MIN_FLIGHT = 8;
    /** PROPOSED: after a flight that ended without getting there, no take-off for a trip for this long. */
    public static final int REFLIGHT_TICKS = 200;
    /** No call from the trip for this long (the job stopped, a fight began): the flight lands. */
    static final int ABANDON_TICKS = 20;
    /** A point of the leg counts as reached this close (blocks, horizontal); a route's corner a little farther. */
    static final double POINT_REACHED = 2.0, CORNER_REACHED = 3.0;
    /** The landing is chosen within this far of the destination, in rings out to this far around it. */
    static final double LAND_NEAR = FlightPlanner.NEAR_ENDS;
    static final int LAND_SEARCH = 8;
    /** A landing cell is at most this far above or below the destination. */
    static final int LAND_RISE = 4;
    /** Climbing more than this, it looks for an overhang ahead before it moves on (blocks). */
    static final double RISE_FIRST = 0.5;

    /**
     * GameTests only: columns that read as not loaded (a test field's own chunks all tick, and its barrier walls stand
     * where unloaded terrain would begin). Null in play.
     */
    public static volatile java.util.function.BiPredicate<Integer, Integer> unloadedForTests;

    private final HeroEntity hero;
    private Flight flight;
    private long noFlightUntil = Long.MIN_VALUE / 4;
    private Map<Long, Long> deadLegs;
    private int lastCall = -1000;
    /** Flights started, flights that ended at their landing, ended stuck in the air, ended at the edge of loaded terrain. */
    public int flights, landed, stuckEnds, edgeEnds;

    /** One travel flight. */
    private static final class Flight {
        BlockPos target;
        List<double[]> route;
        int corner = 1;
        FlightPlanner.Leg leg;
        int point = 1;
        BlockPos landing;
        boolean landingChosen;
        int edgeWait;
        /** The forward probe's floor for the flight's height (NaN: none): it climbs over what lies ahead. */
        double probeLift = Double.NaN;
        int probeTicks;
        /** Climbing under something ahead (an eave, a canopy's edge): it rises in place until that is clear. */
        boolean riseFirst;
        /** The height it flies to its landing column at (fixed when the landing is chosen). */
        double landingY;
        final FlightPlanner.Watch watch = new FlightPlanner.Watch();
        String ended;

        Flight(BlockPos target) {
            this.target = target.immutable();
        }
    }

    public HeroTravel(HeroEntity hero) {
        this.hero = hero;
    }

    /** Whether a travel flight is under way (its take-off, the flight, its landing). */
    public boolean flying() {
        return flight != null;
    }

    /** Why the last flight ended (null: none yet, or it landed where it meant to). */
    public String lastEnd() {
        return lastEnd;
    }

    private String lastEnd;

    /** The leg the flight follows now (tests). */
    public FlightPlanner.Leg leg() {
        return flight == null ? null : flight.leg;
    }

    /** The landing cell chosen (tests; null before it is chosen). */
    public BlockPos landing() {
        return flight == null ? null : flight.landing;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    // ------------------------------------------------------------------ the trip's calls

    @Override
    public NpcNavigator.Result travel(NpcNavigator nav, BlockPos target, double reach, double speed) {
        lastCall = hero.tickCount;
        HeroMovement mv = hero.movement();
        if (flight != null) {
            if (!flight.target.equals(target)) {
                Flight f = new Flight(target);
                flight = f; // a new destination in the air: plan again from here
            }
            return fly(nav, mv);
        }
        if (mv.controls() && mv.navOwned()) {
            return NpcNavigator.Result.MOVING; // a link of its navigation runs: the trip waits for it
        }
        return null;
    }

    @Override
    public boolean chooseFlight(NpcNavigator nav, BlockPos goal, Path ground, BlockPos target) {
        if (!mayTakeOff(target)) {
            return false;
        }
        HeroMovement mv = hero.movement();
        MobilityRules.Profile p = mv.profile();
        double straight = horizontal(goal), trip = horizontal(target);
        boolean noGround = ground == null || !ground.canReach();
        if (straight < (noGround ? 2 : MIN_FLIGHT)) {
            return false;
        }
        double route = noGround ? Double.POSITIVE_INFINITY : length(ground);
        boolean legal = trip > FlightPlanner.WAYPOINT + LAND_NEAR || landingCell(target) != null;
        FlightPlanner.Mode m = FlightPlanner.mode(p, new FlightPlanner.ModeInput(straight, route, false, trip,
                covers(p, mv, Math.min(straight, FlightPlanner.WAYPOINT)), legal, mv.headroom(),
                hero.level().isThundering(), HeroNodeEvaluator.situation(hero), mv.stamina().exhausted()));
        return m == FlightPlanner.Mode.FLY && takeOff(target);
    }

    @Override
    public boolean rescue(NpcNavigator nav, BlockPos target) {
        if (!mayTakeOff(target) || horizontal(target) < 2 && Math.abs(target.getY() - hero.getY()) < 2) {
            return false;
        }
        HeroMovement mv = hero.movement();
        return covers(mv.profile(), mv, Math.min(horizontal(target), FlightPlanner.WAYPOINT)) && takeOff(target);
    }

    /**
     * An escape's flight (MOBILITY_AND_WORK 7.7, task A12): an escaping flyer takes off toward its refuge (or the wild)
     * whenever it may, in any weather, with no air budget and the travel ceiling: only its stamina limits it (it does not
     * land at 25%; empty, it glides down). It needs the take-off's headroom and the sky over it, as a trip's does. Its
     * {@code travel: false} does not hold it on foot: an escaping hero uses any mobility it has. Returns whether it took
     * off.
     */
    public boolean escapeTakeOff(BlockPos target) {
        if (flight != null || hero.level().isClientSide || !hero.combat().boss().escape().running()) {
            return false;
        }
        HeroMovement mv = hero.movement();
        if (!mv.profile().flies() || deadLeg(target) || mv.takeOffRefusal() != null || !underOpenSky()) {
            return false;
        }
        return takeOff(target);
    }

    /**
     * A raid's flight over the walls (MOBILITY_AND_WORK 5.2, task A12): the way in {@code WallEntry} chose, out of a
     * fight, at the raid's ceiling (10 above the ground, a wall top counting as ground) and its air budget (10 s per
     * take-off, 6 s on the ground between), with the take-off's headroom and the sky over it. Returns whether it took off.
     */
    public boolean raidTakeOff(BlockPos target) {
        if (flight != null || hero.level().isClientSide || hero.combat().boss().fighting()) {
            return false;
        }
        HeroMovement mv = hero.movement();
        if (!mv.profile().flies() || deadLeg(target) || mv.takeOffRefusal() != null || !underOpenSky()) {
            return false;
        }
        return takeOff(target);
    }

    /**
     * An escape that starts in the air (7.6: it does not land first): the flight it is in becomes its escape's flight
     * toward {@code target}. Returns whether it is one now.
     */
    public boolean adoptFlight(BlockPos target) {
        HeroMovement mv = hero.movement();
        if (flight != null) {
            return true;
        }
        if (hero.level().isClientSide || !mv.flying()) {
            return false;
        }
        mv.navOwned(true);
        flight = new Flight(target);
        flights++;
        lastEnd = null;
        ChunkLoading.updateTraveler(hero);
        return true;
    }

    /** Whether a travel flight may start now: a flyer that travels by air, out of a fight, its leg not dead, rested. */
    private boolean mayTakeOff(BlockPos target) {
        if (flight != null || hero.level().isClientSide || now() < noFlightUntil) {
            return false;
        }
        HeroMovement mv = hero.movement();
        MobilityRules.Profile p = mv.profile();
        if (!p.flies() || !travelsByAir() || deadLeg(target)) {
            return false;
        }
        HeroBoss boss = hero.combat().boss();
        if (boss.fighting() || hero.getTarget() != null && hero.getTarget().isAlive() || boss.escape().running()) {
            return false; // a fight's flights (reach flights, aerial windows) and an escape's are A12's
        }
        return mv.takeOffRefusal() == null && underOpenSky();
    }

    /**
     * A trip's take-off needs the sky over its footprint, not only the 4 blocks of the take-off itself (3.5 "Cover":
     * roofs and dense trees force a walk): under a building's 4-high ceiling or a canopy it would rise into it and end
     * stuck. It walks out from under it, and the trip's next path search may fly from there.
     */
    private boolean underOpenSky() {
        net.minecraft.world.phys.AABB b = hero.getBoundingBox();
        int head = net.minecraft.util.Mth.ceil(b.maxY);
        for (int x = net.minecraft.util.Mth.floor(b.minX); x <= net.minecraft.util.Mth.floor(b.maxX); x++) {
            for (int z = net.minecraft.util.Mth.floor(b.minZ); z <= net.minecraft.util.Mth.floor(b.maxZ); z++) {
                double s = surface(x, z);
                if (Double.isNaN(s) || s > head) {
                    return false; // a block over its head somewhere in the column (a roof, leaves)
                }
            }
        }
        return true;
    }

    /** {@code mobility.travel}: false makes the hero walk everywhere by choice (8.1). */
    private boolean travelsByAir() {
        HeroDef d = hero.dormant() ? null : hero.definition();
        HeroDef.Mobility m = d == null || d.v2() == null ? null : d.v2().mobility();
        return m == null || m.travel();
    }

    /** Whether its stamina over the 25% it lands at carries it {@code blocks} at cruise. */
    private static boolean covers(MobilityRules.Profile p, HeroMovement mv, double blocks) {
        MobilityRules.Stamina s = mv.stamina();
        double cruise = p.raw(MobilityRules.Stat.CRUISE_SPEED);
        if (s.max() <= 0 || Double.isNaN(cruise)) {
            return false;
        }
        double spare = s.value() - MobilityRules.LOW_SHARE * s.max();
        return spare > 0 && spare / MobilityRules.DRAIN_CRUISE * cruise >= blocks;
    }

    private boolean takeOff(BlockPos target) {
        HeroMovement mv = hero.movement();
        if (mv.takeOff() != null) {
            return false;
        }
        mv.navOwned(true);
        flight = new Flight(target);
        flights++;
        lastEnd = null;
        ChunkLoading.updateTraveler(hero);
        return true;
    }

    /** One tick from the hero's own tick: a flight with no trip calling for it any more (a fight, an errand) lands. */
    public void tick() {
        if (flight == null) {
            return;
        }
        HeroMovement mv = hero.movement();
        if (hero.tickCount - lastCall > ABANDON_TICKS) {
            if (mv.state() == HeroMovement.State.FLY) {
                mv.land();
            } else if (!mv.controls()) {
                end(null);
            }
        }
        if (hero.tickCount % 10 == 0) {
            ChunkLoading.updateTraveler(hero);
        }
    }

    // ------------------------------------------------------------------ in the air

    private NpcNavigator.Result fly(NpcNavigator nav, HeroMovement mv) {
        switch (mv.state()) {
            case TAKEOFF, RISE, LAND, LANDING, GLIDE -> {
                mv.navOwned(true);
                return NpcNavigator.Result.MOVING;
            }
            case FLY -> {
                mv.navOwned(true);
                steer(nav, mv);
                return NpcNavigator.Result.MOVING;
            }
            default -> {
                end(null);
                return null; // landed, downed or fallen: the trip goes on on the ground
            }
        }
    }

    private void end(String why) {
        Flight f = flight;
        flight = null;
        if (f != null && f.ended == null && why == null && f.landingChosen) {
            landed++;
        }
        if (f != null && f.ended != null) {
            lastEnd = f.ended;
        }
        ChunkLoading.updateTraveler(hero);
    }

    /** Steering in the air: the landing near the destination, else the cruise layer's next point. */
    private void steer(NpcNavigator nav, HeroMovement mv) {
        Flight f = flight;
        HeroBoss boss = hero.combat().boss();
        if (boss.fighting() || hero.getTarget() != null && hero.getTarget().isAlive()) {
            f.ended = "a fight";
            mv.land(); // a fight began: it comes down (a fight's flights are A12's)
            return;
        }
        Vec3 pos = hero.position();
        if (!f.landingChosen && horizontal(f.target) <= LAND_NEAR) {
            f.landingChosen = true;
            f.landing = landingCell(f.target);
            f.landingY = f.landing == null ? pos.y : Math.max(pos.y, f.landing.getY() + 2);
            f.watch.reset();
        }
        if (f.landingChosen) {
            BlockPos l = f.landing != null ? f.landing : hero.blockPosition();
            double dx = l.getX() + 0.5 - pos.x, dz = l.getZ() + 0.5 - pos.z;
            if (dx * dx + dz * dz <= 0.36) {
                mv.land();
                return;
            }
            Vec3 aim = new Vec3(l.getX() + 0.5, probe(mv, f, f.landingY, l.getX() + 0.5, l.getZ() + 0.5), l.getZ() + 0.5);
            mv.flyTo(f.riseFirst ? new Vec3(pos.x, aim.y, pos.z) : aim);
            watch(nav, mv, f, aim.distanceTo(pos));
            return;
        }
        if (f.route == null) {
            List<FlightPlanner.Box> hostile = new ArrayList<>();
            hostileBoxes(hero, hostile);
            f.route = FlightPlanner.route(pos.x, pos.z, f.target.getX() + 0.5, f.target.getZ() + 0.5, hostile,
                    HeroNodeEvaluator.situation(hero));
        }
        double[] corner = f.route.get(Math.min(f.corner, f.route.size() - 1));
        if (f.corner < f.route.size() - 1 && Math.hypot(corner[0] - pos.x, corner[1] - pos.z) <= CORNER_REACHED) {
            f.corner++;
            f.leg = null;
            corner = f.route.get(f.corner);
        }
        if (f.leg == null || f.point >= f.leg.points().size()) {
            if (f.leg != null && f.leg.end() == FlightPlanner.End.UNLOADED) {
                edge(nav, mv, f, corner);
                return;
            }
            plan(f, corner);
            if (f.leg.points().size() <= 1) {
                if (f.leg.end() == FlightPlanner.End.UNLOADED) {
                    edge(nav, mv, f, corner);
                    return;
                }
                f.landingChosen = true; // nowhere left to cruise to: down it comes where it can
                f.landing = landingCell(hero.blockPosition());
                return;
            }
        }
        f.edgeWait = 0;
        FlightPlanner.Point p = f.leg.points().get(f.point);
        if (Math.hypot(p.x() - pos.x, p.z() - pos.z) <= POINT_REACHED) {
            f.point++;
            f.watch.reset();
            return;
        }
        Vec3 aim = new Vec3(p.x(), probe(mv, f, p.y(), p.x(), p.z()), p.z());
        mv.flyTo(f.riseFirst ? new Vec3(pos.x, aim.y, pos.z) : aim);
        // progress toward the point in the air (it climbs to the cruise layer as it goes; rising in place counts)
        watch(nav, mv, f, aim.distanceTo(pos));
    }

    /** A leg of the cruise layer from here toward {@code corner}, over loaded terrain only. */
    private void plan(Flight f, double[] corner) {
        Vec3 pos = hero.position();
        HeroBoss boss = hero.combat().boss();
        double enemy = boss.highestEngagedFeet();
        List<Double> feet = Double.isNaN(enemy) ? List.of() : List.of(enemy);
        f.leg = FlightPlanner.leg(this::surface, new FlightPlanner.Point(pos.x, pos.y, pos.z), corner[0], corner[1],
                HeroNodeEvaluator.situation(hero), feet, FCConfig.MOBILITY_TRAVEL_ALTITUDE.get());
        f.point = 1;
        f.watch.reset();
    }

    /**
     * The forward probe (3.5): every 4 ticks, 4 blocks ahead along its heading at its body's height; a block there (a
     * spruce top, a tower, a cliff between the planner's samples) makes it climb until the probe is clear, unless that
     * would pass its altitude ceiling: then it turns along the planner's next point instead. Returns the height to fly
     * at now.
     *
     * <p>While it climbs, the same probe looks over the way ahead from its feet up to that height, as far as it flies
     * before the next probe and a block more: something there (an eave, the edge of a canopy, a ledge's overhang) makes
     * it rise in place first ({@link Flight#riseFirst}), so a climb on the slant never wedges its head under it.
     */
    private double probe(HeroMovement mv, Flight f, double wantY, double toX, double toZ) {
        if (--f.probeTicks <= 0) {
            f.probeTicks = FlightPlanner.PROBE_TICKS;
            // along its heading: toward the point it flies to (its speed is nothing while a wall stops it)
            double hx = toX - hero.getX(), hz = toZ - hero.getZ();
            double h = Math.sqrt(hx * hx + hz * hz);
            boolean blocked = false;
            double top = Double.NEGATIVE_INFINITY;
            double ux = 0, uz = 0;
            net.minecraft.world.phys.AABB box = hero.getBoundingBox();
            if (h > 0.3) {
                ux = hx / h;
                uz = hz / h;
                for (double d = 1; d <= Math.min(FlightPlanner.PROBE_AHEAD, Math.ceil(h)); d++) {
                    if (!hero.level().noCollision(hero, box.move(ux * d, 0, uz * d))) {
                        blocked = true;
                        double s = surface(net.minecraft.util.Mth.floor(hero.getX() + ux * d), net.minecraft.util.Mth.floor(hero.getZ() + uz * d));
                        top = Math.max(top, Double.isNaN(s) ? hero.getY() + 2 : s);
                    }
                }
            }
            double ground = surface(hero.getBlockX(), hero.getBlockZ());
            double ceiling = (Double.isNaN(ground) ? hero.getY() : ground)
                    + MobilityRules.travelAltitude(FCConfig.MOBILITY_TRAVEL_ALTITUDE.get());
            switch (FlightPlanner.probe(blocked, hero.getY(), top, ceiling)) {
                case CLIMB -> f.probeLift = top + 1;
                case TURN -> {
                    f.probeLift = Double.NaN;
                    if (f.leg != null && f.point < f.leg.points().size() - 1) {
                        f.point++; // along the planner's next point
                    }
                }
                case CLEAR -> f.probeLift = Double.NaN;
            }
            double rise = (Double.isNaN(f.probeLift) ? wantY : Math.max(wantY, f.probeLift)) - hero.getY();
            f.riseFirst = false;
            if (h > 0.3 && rise > RISE_FIRST) {
                double ahead = Math.min(h, 1 + mv.profile().raw(MobilityRules.Stat.CRUISE_SPEED)
                        * FlightPlanner.PROBE_TICKS * com.factionclash.logic.hero.MovementRules.TICK);
                f.riseFirst = !hero.level().noCollision(hero, box.expandTowards(ux * ahead, rise, uz * ahead));
            }
        }
        return Double.isNaN(f.probeLift) ? wantY : Math.max(wantY, f.probeLift);
    }

    /** At the edge of loaded terrain (3.5): it hovers, planning again now and then; after 3 s it lands. */
    private void edge(NpcNavigator nav, HeroMovement mv, Flight f, double[] corner) {
        mv.hover();
        f.edgeWait++;
        if (f.edgeWait % 10 == 0) {
            plan(f, corner);
            Vec3 pos = hero.position();
            FlightPlanner.Point last = f.leg.last();
            boolean on = f.leg.end() == FlightPlanner.End.WAYPOINT
                    || Math.hypot(last.x() - pos.x, last.z() - pos.z) >= FlightPlanner.SAMPLE_STEP;
            if (f.leg.points().size() > 1 && on) {
                f.edgeWait = 0; // the next waypoint is loaded now
                return;
            }
            f.point = f.leg.points().size(); // not a station further yet: it keeps hovering (no creeping on the edge)
        }
        if (FlightPlanner.landAtEdge(f.edgeWait)) {
            f.ended = "the edge of loaded terrain";
            edgeEnds++;
            noFlightUntil = now() + REFLIGHT_TICKS;
            nav.otherWayFailed(f.ended);
            mv.land();
        }
    }

    /** Stuck in the air (3.5): a head bump while it climbs for 10 ticks, or under a block of progress in 2 s. */
    private void watch(NpcNavigator nav, HeroMovement mv, Flight f, double remaining) {
        boolean bump = hero.verticalCollision && !hero.verticalCollisionBelow;
        if (f.watch.tick(bump, remaining)) {
            f.ended = "stuck in the air";
            stuckEnds++;
            deadLeg(f.target, true);
            noFlightUntil = now() + REFLIGHT_TICKS;
            nav.otherWayFailed(f.ended);
            mv.land();
        }
    }

    // ------------------------------------------------------------------ the ground under it

    /**
     * The surface a hero would stand on in a column (the highest block that blocks motion, leaves included), or NaN when
     * its chunk is not loaded or its entities would not tick there: the planner never reaches past them.
     */
    double surface(int x, int z) {
        java.util.function.BiPredicate<Integer, Integer> unloaded = unloadedForTests;
        if (unloaded != null && unloaded.test(x, z)) {
            return Double.NaN;
        }
        Level level = hero.level();
        LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (c == null || level instanceof ServerLevel sl && !sl.isPositionEntityTicking(new BlockPos(x, hero.getBlockY(), z))) {
            return Double.NaN;
        }
        return c.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
    }

    /**
     * A legal landing near {@code target} (2.2): its own column first, then rings out to 8 blocks; a standable cell on
     * the surface, open to the sky, with 2 blocks of headroom, within 4 blocks of the target's height, not on water,
     * lava, fire, powder snow, farmland, leaves or a block that hurts. Null when there is none in loaded terrain.
     */
    BlockPos landingCell(BlockPos target) {
        for (int r = 0; r <= LAND_SEARCH; r++) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = target.getX() + dx, z = target.getZ() + dz;
                    double s = surface(x, z);
                    if (Double.isNaN(s) || Math.abs(s - target.getY()) > LAND_RISE) {
                        continue;
                    }
                    BlockPos c = new BlockPos(x, (int) s, z);
                    double d = dx * dx + dz * dz;
                    if (d < bestD && legal(c)) {
                        best = c;
                        bestD = d;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private boolean legal(BlockPos c) {
        Level level = hero.level();
        BlockState below = level.getBlockState(c.below());
        FlightPlanner.Surface s;
        if (below.getBlock() instanceof FarmBlock) {
            s = FlightPlanner.Surface.FARMLAND;
        } else if (below.getFluidState().is(FluidTags.LAVA)) {
            s = FlightPlanner.Surface.LAVA;
        } else if (below.is(BlockTags.FIRE)) {
            s = FlightPlanner.Surface.FIRE;
        } else if (below.is(Blocks.POWDER_SNOW)) {
            s = FlightPlanner.Surface.POWDER_SNOW;
        } else if (!below.getFluidState().isEmpty() || below.getCollisionShape(level, c.below()).isEmpty()) {
            s = FlightPlanner.Surface.WATER;
        } else if (below.is(BlockTags.LEAVES) || NpcNavigator.isDangerous(below)) {
            return false;
        } else {
            s = FlightPlanner.Surface.GROUND;
        }
        int headroom = 0;
        for (int k = 0; k < 2; k++) {
            BlockPos p = c.above(k);
            BlockState st = level.getBlockState(p);
            if (st.getCollisionShape(level, p).isEmpty() && st.getFluidState().isEmpty()) {
                headroom++;
            }
        }
        return FlightPlanner.legalLanding(s, headroom, false, false);
    }

    /** Every standing base hostile to the hero's faction: its walls box (the planner adds the berth margin). */
    private static void hostileBoxes(HeroEntity hero, List<FlightPlanner.Box> out) {
        List<FlightPlanner.Box> grown = new ArrayList<>();
        HeroNodeEvaluator.hostileBases(hero, grown);
        for (FlightPlanner.Box b : grown) {
            out.add(b.grow(-com.factionclash.logic.hero.HeroLinks.BERTH_MARGIN));
        }
    }

    // ------------------------------------------------------------------ dead legs, distances

    private boolean deadLeg(BlockPos target) {
        if (deadLegs == null) {
            return false;
        }
        Long until = deadLegs.get(target.asLong());
        return until != null && until > now();
    }

    private void deadLeg(BlockPos target, boolean on) {
        if (deadLegs == null) {
            deadLegs = new HashMap<>();
        }
        if (deadLegs.size() >= 64) {
            long t = now();
            deadLegs.values().removeIf(u -> u <= t);
        }
        deadLegs.put(target.asLong(), now() + FlightPlanner.DEAD_LEG_TICKS);
    }

    private double horizontal(BlockPos p) {
        double dx = p.getX() + 0.5 - hero.getX(), dz = p.getZ() + 0.5 - hero.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A path's length walked, node to node. */
    private static double length(Path p) {
        double len = 0;
        for (int i = 1; i < p.getNodeCount(); i++) {
            Node a = p.getNode(i - 1), b = p.getNode(i);
            len += a.distanceTo(b);
        }
        return len;
    }
}
