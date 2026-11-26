package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.CampaignManager;
import com.factionclash.campaign.FactionState;
import com.factionclash.combat.Reach;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.logic.hero.WallEntry;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.storage.FactionStorage;
import com.factionclash.work.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * A hero on a raid (MOBILITY_AND_WORK 5.2; task A12), whatever its duty: once it is in its faction's raid party
 * ({@code f.raiders}) it musters at the rally point with the soldiers, and within 24 blocks of the target's walls it
 * chooses its way in ({@link WallEntry#choose}: over the wall by flight, a climb, a jump onto the top or a glide, only
 * with a way out from inside; else the gate or the breach with the soldiers) and its one goal inside
 * ({@link WallEntry#goal}: open the way for a party held outside, hunt the home hero, clear the archers, the storehouse,
 * else the heart). Every goal ends (its own condition, the raid's end, 60 s without progress), and then it joins the
 * party at the heart, which it strikes as any raider does, or carries its loot home. The raid ends as today (the heart
 * destroyed, the fall-back share, the time limit); a lost raid sends an escaping hero near the target home with any
 * mobility ({@link HeroEscape#lostRaid}). Soldiers still go through the gate or a breach: nothing here moves them.
 *
 * <p>Heroes are not yet drafted into raid parties by the director (HEROES_V2 3.2: leaders and {@code behaviour.raids}
 * come with L4); an operator's {@code /fc admin hero raid <id>} or a test puts one in. It runs through
 * {@code combat/HeroRaidGoal} (priority 4: a fight, an escape or a stagger come first).
 */
public final class HeroRaid {
    /** Where it is in its raid. */
    public enum Stage { NONE, RALLY, APPROACH, ENTRY, GOAL, HEART, HOME }

    /** A heart takes this many ticks of a raider's blows (as a soldier's, {@code GuardJob}). */
    static final int HEART_TICKS = 400;

    private final HeroEntity hero;
    private Stage stage = Stage.NONE;
    private int raidOf = -1;
    private WallEntry.Entry entry;
    private WallEntry.Goal goal;
    private int goalStart, progressAt;
    private double best = Double.MAX_VALUE;
    private int lastPress;
    private int assault;
    private boolean looted, crossed;
    private HeroEntity hunted;
    private int huntedSeenAt;
    /** The wall it meets: the side of the base it comes from, the outside foot of the face it climbs, its top. */
    private Direction side;
    private BlockPos foot;
    private int wallTopY;
    private boolean overStarted;
    private int footTicks;
    /** Counters (tests, the campaign's lines): wall crossings by way, presses of a button or lever, goals ended. */
    public int wallCrossings, presses, goalsEnded;
    /** Climbs or jump-ups it started up a wall face it chose (tests). */
    public int overStarts;
    /** How its last goal ended (tests; null: none ended yet). */
    public WallEntry.GoalEnd lastEnd;

    HeroRaid(HeroEntity hero) {
        this.hero = hero;
    }

    public Stage stage() {
        return stage;
    }

    public WallEntry.Entry entry() {
        return entry;
    }

    public WallEntry.Goal goal() {
        return goal;
    }

    private CampaignData data() {
        return CampaignData.get(hero.getServer());
    }

    private FactionState faction() {
        return hero.getServer() == null || hero.getFaction() == null ? null : data().factions.get(hero.getFaction());
    }

    /** The base it raids, while it is in its faction's party for a raid on it (null: no raid). */
    public BaseState target() {
        FactionState f = faction();
        if (f == null || f.raidTarget < 0 || !f.raiders.contains(hero.getUUID())) {
            return null;
        }
        return data().base(f.raidTarget);
    }

    /** Whether its raid layer runs now: in a raid party, not escaping. */
    public boolean active() {
        if (hero.level().isClientSide || hero.isPreview() || hero.combat().boss().escape().running()) {
            return false;
        }
        BaseState t = target();
        if (t == null) {
            // the raid ended (the heart fell, the party fell back, the time ran out) or it left the party: it goes home,
            // out of the walls it is in by its own way out (5.2: a hero never ends up shut inside)
            if (stage != Stage.NONE && stage != Stage.HOME) {
                stage = Stage.HOME;
                nav().reset();
            }
            return stage == Stage.HOME;
        }
        if (raidOf != t.id) {
            raidOf = t.id;
            stage = Stage.RALLY;
            entry = null;
            goal = null;
            looted = crossed = overStarted = false;
            assault = 0;
            footTicks = 0;
        }
        return true;
    }

    // ------------------------------------------------------------------ the tick (from the goal)

    void tick() {
        BaseState t = target();
        FactionState f = faction();
        switch (stage) {
            case RALLY -> rally(f, t);
            case APPROACH -> approach(t);
            case ENTRY -> entry(t);
            case GOAL -> goal(f, t);
            case HEART -> heart(t);
            case HOME -> home(f);
            default -> {
            }
        }
    }

    private NpcNavigator nav() {
        return hero.navigator();
    }

    /**
     * Whether the hero sees that its target has fallen (its heart gone, the place in view): a fall far off is not its
     * news on the march (knowledge review of 2026-11-19; the raid's end reaches it with the party). Ruins it sees, the
     * party brings home ({@code FactionDirector.partySawRuins}).
     */
    private boolean seesFall(BaseState t) {
        if (!com.factionclash.campaign.BaseWatch.seesFall(hero, t)) {
            return false;
        }
        com.factionclash.campaign.FactionDirector.partySawRuins(data(), faction(), t, hero.level().getGameTime());
        return true;
    }

    private void rally(FactionState f, BaseState t) {
        if (t == null || seesFall(t)) {
            stage = Stage.HOME;
            return;
        }
        if (f.raidMusteredAt == 0 && f.raidRally != Long.MIN_VALUE) {
            BlockPos rally = BlockPos.of(f.raidRally);
            if (hero.level().isLoaded(rally)) {
                rally = hero.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rally);
            }
            nav().travel(rally, 6.0, 1.0);
            return;
        }
        nav().reset();
        stage = Stage.APPROACH;
    }

    /** Within 24 blocks of the walls it chooses its way in and its goal (5.2). */
    private void approach(BaseState t) {
        if (t == null || seesFall(t)) {
            stage = Stage.HOME;
            return;
        }
        int gap = BaseZones.chebyshev(t, hero.blockPosition()) - BaseZones.half(t);
        if (WallEntry.chooseNow(true, gap)) {
            choose(t);
            nav().reset();
            stage = Stage.ENTRY;
            return;
        }
        nav().travel(t.center, 3, 1.0);
    }

    /** Its way in and its goal, from its mobility at its level, the wall's blocks and the party's state. */
    public void choose(BaseState t) {
        HeroMovement mv = hero.movement();
        MobilityRules.Profile p = mv.profile();
        WallEntry.Wall w = wall(t);
        double blocks = Math.max(WallEntry.CHOOSE_AT, Math.sqrt(hero.blockPosition().distSqr(t.center)));
        // whether it may take off for the flight (not whether it can this very tick: a step or a slope does not count)
        WallEntry.Approach a = new WallEntry.Approach(mv.stamina().max() > 0 ? mv.stamina().share() : 0, blocks, false,
                mv.mayTakeOffSoon());
        entry = WallEntry.choose(p, w, a);
        HeroDef d = hero.definition();
        HeroDef.Behaviour b = d == null ? null : d.behaviour();
        HeroDef.Personality pers = d == null || d.v2() == null ? null : d.v2().personality();
        int greed = pers == null ? 50 : pers.trait(HeroDef.Trait.GREED);
        boolean held = !w.gate().soldiersOpen() && partyOutside(t);
        // a defending hero is known to be home when the recon its raid was launched on saw one there (docs/design/recon.md
        // 8, task R9), and seen when it stands inside the walls in its own line of sight now; a hero merely somewhere
        // near is neither (it was, before: any of the base's heroes within 64 blocks, seen or not)
        FactionState f = faction();
        boolean known = f != null && !f.raidReconHeroes.isEmpty();
        HeroEntity home = homeHero(t);
        goal = WallEntry.goal(new WallEntry.GoalInput(held, b == null ? null : b.focus(), b == null ? null : b.role(),
                b == null ? null : b.duelStyle(), greed, known, home != null && BaseZones.insideWalls(t, home.blockPosition(), 0),
                archers(t) > 0, !t.allStoragePositions().isEmpty()));
        goalStart = hero.tickCount;
        progressAt = hero.tickCount;
        hunted = null;
        huntedSeenAt = hero.tickCount; // (a hunt that finds nobody ends after a minute unseen, WallEntry.HUNT_UNSEEN_TICKS)
        best = Double.MAX_VALUE;
        FactionClash.LOGGER.info("[Heroes] {} raids {}: in by {}, out by {}, goal {} (wall {} high, gate {})", hero.heroId(),
                t.name, entry.way(), entry.exit(), goal, w.outside(), w.gate());
    }

    /** The wall the raid meets (5.2: the blocks decide): its height on each side nearest the hero, and its gate. */
    WallEntry.Wall wall(BaseState t) {
        int half = BaseZones.half(t);
        int dx = hero.getBlockX() - t.center.getX(), dz = hero.getBlockZ() - t.center.getZ();
        Direction side = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        int along = side.getAxis() == Direction.Axis.X ? Math.max(-half + 1, Math.min(half - 1, dz))
                : Math.max(-half + 1, Math.min(half - 1, dx));
        BlockPos col = side.getAxis() == Direction.Axis.X
                ? t.center.offset(side.getStepX() * half, 0, along) : t.center.offset(along, 0, side.getStepZ() * half);
        // the face it climbs keeps 3 blocks from a gate (a gate beside it would be the cheaper path)
        for (String k : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.GATE_SOUTH}) {
            BlockPos g = t.point(k);
            if (g != null && Math.abs(g.getX() - col.getX()) + Math.abs(g.getZ() - col.getZ()) < 3) {
                int shift = along >= 0 ? -3 : 3;
                col = side.getAxis() == Direction.Axis.X ? col.offset(0, 0, shift) : col.offset(shift, 0, 0);
            }
        }
        int top = topOf(col, t.center.getY());
        int outsideGround = groundAt(col.relative(side, 1), t.center.getY());
        int outside = top - groundAt(col.relative(side, 2), t.center.getY());
        this.side = side;
        this.foot = new BlockPos(col.relative(side, 1).getX(), outsideGround, col.relative(side, 1).getZ());
        this.wallTopY = top;
        int inside = top - groundAt(col.relative(side.getOpposite(), 2), t.center.getY());
        return new WallEntry.Wall(Math.max(0, outside), Math.max(0, inside), gate(t), 0, 0, 0, 0);
    }

    /** The top of the solid blocks of a wall's column (its feet would stand there), from the base's floor. */
    private int topOf(BlockPos col, int y0) {
        int top = y0;
        for (int y = y0 - 1; y <= y0 + 16; y++) {
            BlockPos p = new BlockPos(col.getX(), y, col.getZ());
            if (!hero.level().getBlockState(p).getCollisionShape(hero.level(), p).isEmpty()) {
                top = y + 1;
            }
        }
        return top;
    }

    /** The ground a figure stands on beside a wall: the first block holding it from a little above the floor down. */
    private int groundAt(BlockPos p, int y0) {
        for (int y = y0 + 1; y >= y0 - 8; y--) {
            BlockPos q = new BlockPos(p.getX(), y, p.getZ());
            if (!hero.level().getBlockState(q).getCollisionShape(hero.level(), q).isEmpty()) {
                return y + 1;
            }
        }
        return y0;
    }

    /** How the base's gates open from inside (the most open of them). */
    WallEntry.Gate gate(BaseState t) {
        WallEntry.Gate best = null;
        for (String k : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.GATE_SOUTH}) {
            BlockPos g = t.point(k);
            if (g == null) {
                continue;
            }
            WallEntry.Gate one = gateAt(t, g);
            if (best == null || one.ordinal() < best.ordinal()) {
                best = one;
            }
        }
        return best == null ? WallEntry.Gate.WOODEN : best;
    }

    private WallEntry.Gate gateAt(BaseState t, BlockPos g) {
        BlockState s = hero.level().getBlockState(g);
        if (s.getBlock() instanceof DoorBlock door) {
            if (door.type().canOpenByHand()) {
                return WallEntry.Gate.WOODEN;
            }
            return switchNear(t, g) != null ? WallEntry.Gate.BUTTON_OR_LEVER : WallEntry.Gate.SHUT;
        }
        if (s.getBlock() instanceof FenceGateBlock || Reach.passable((ServerLevel) hero.level(), g, s)) {
            return WallEntry.Gate.WOODEN;
        }
        return hero.level() instanceof ServerLevel sl && Reach.mayBreak(sl, g, hero) ? WallEntry.Gate.BREAKABLE
                : WallEntry.Gate.SHUT;
    }

    /** A button or lever within 2 blocks of a gate, on the inside of the walls (null: none). */
    BlockPos switchNear(BaseState t, BlockPos g) {
        for (BlockPos p : BlockPos.betweenClosed(g.offset(-2, -1, -2), g.offset(2, 2, 2))) {
            BlockState s = hero.level().getBlockState(p);
            if ((s.getBlock() instanceof ButtonBlock || s.getBlock() instanceof LeverBlock)
                    && BaseZones.insideWalls(t, p, 0) && BaseZones.chebyshev(t, p) < BaseZones.half(t)) {
                return p.immutable();
            }
        }
        return null;
    }

    /** Soldiers of its party outside the walls box. */
    private boolean partyOutside(BaseState t) {
        FactionState f = faction();
        if (f == null || !(hero.level() instanceof ServerLevel sl)) {
            return false;
        }
        for (java.util.UUID u : f.raiders) {
            if (!u.equals(hero.getUUID()) && sl.getEntity(u) instanceof FactionNpc n && n.isAlive() && !(n instanceof HeroEntity)
                    && !BaseZones.insideWalls(t, n.blockPosition(), 0)) {
                return true;
            }
        }
        return false;
    }

    /** A living hero of the target base (the home hero). */
    /** A hero of the raided base it has in its own line of sight within 64 blocks (null: none in sight). */
    private HeroEntity homeHero(BaseState t) {
        for (HeroEntity h : HeroEntity.live()) {
            if (h != hero && h.isAlive() && h.baseId() == t.id && h.level() == hero.level()
                    && h.distanceToSqr(hero) <= 64 * 64 && hero.hasLineOfSight(h)) {
                return h;
            }
        }
        return null;
    }

    /** The base's archers and crossbowmen in or near its walls. */
    private int archers(BaseState t) {
        int n = 0;
        for (FactionNpc a : hero.level().getEntitiesOfClass(FactionNpc.class, hero.getBoundingBox().inflate(48),
                a -> a.isAlive() && a.baseId() == t.id && a.rangedFighter() && !(a instanceof HeroEntity))) {
            n++;
        }
        return n;
    }

    /** Where its goal takes it inside. */
    private BlockPos goalPoint(BaseState t) {
        if (goal == null) {
            return t.point(BaseState.HEART) != null ? t.point(BaseState.HEART) : t.center;
        }
        return switch (goal) {
            case OPEN_THE_WAY -> gateInside(t);
            case STOREHOUSE -> t.point(BaseState.STORAGE_STAND) != null ? t.point(BaseState.STORAGE_STAND) : t.center;
            case HEART -> t.point(BaseState.HEART) != null ? t.point(BaseState.HEART) : t.center;
            default -> t.center;
        };
    }

    /** The cell just inside the gate the raid meets (its switch's side). */
    private BlockPos gateInside(BaseState t) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (String k : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.GATE_SOUTH}) {
            BlockPos g = t.point(k);
            if (g == null) {
                continue;
            }
            int sx = Integer.signum(t.center.getX() - g.getX()), sz = Integer.signum(t.center.getZ() - g.getZ());
            BlockPos in = g.offset(sx * 2, 0, sz * 2);
            double d = in.distSqr(hero.blockPosition());
            if (d < bd) {
                bd = d;
                best = in;
            }
        }
        return best == null ? t.center : best;
    }

    /** The way in: over the wall by its mobility (its links and flights cross it), or the gate with the soldiers. */
    private void entry(BaseState t) {
        if (t == null || seesFall(t)) {
            stage = Stage.HOME;
            return;
        }
        if (BaseZones.insideWalls(t, hero.blockPosition(), -1) && !hero.movement().controls()) {
            if (entry != null && entry.overTheWall() && !crossed) {
                crossed = true;
                wallCrossings++;
            }
            nav().reset();
            stage = Stage.GOAL;
            goalStart = hero.tickCount;
            progressAt = hero.tickCount;
            huntedSeenAt = hero.tickCount; // (a hunt's minute unseen runs from the goal's start inside)
            best = Double.MAX_VALUE;
            return;
        }
        BlockPos to = goalPoint(t);
        HeroTravel tr = hero.travel();
        HeroMovement mv = hero.movement();
        if (entry != null && entry.way() == WallEntry.Way.FLY_OVER && !tr.flying() && hero.onGround() && !mv.controls()) {
            tr.raidTakeOff(to);
        }
        if (entry != null && !entry.overTheWall()) {
            // the ground way (5.2: the gate or the breach with the soldiers): to the outside of the gate it meets, and
            // in once one is open (any of its gates: its path goes round to an open one); while all are shut it waits at
            // the nearest. Its path search takes no link over the wall now
            // ({@link #wallsClosedTo}), so it is never shut in without a way out (the review of A10-A12)
            BlockPos g = nearestGate(t);
            if (g != null && !anyGateOpen(t)) {
                BlockPos out = g.offset(Integer.signum(g.getX() - t.center.getX()) * 2, 0,
                        Integer.signum(g.getZ() - t.center.getZ()) * 2);
                if (NpcNavigator.within(hero, out, 2.0)) {
                    nav().reset();
                    hero.getNavigation().stop();
                } else {
                    nav().travel(out, 1.5, 1.0);
                }
                return;
            }
        }
        if (entry != null && (entry.way() == WallEntry.Way.CLIMB_OVER || entry.way() == WallEntry.Way.LEAP_ON)
                && !overStarted && foot != null && side != null) {
            // up the face it chose (the nearest to it, clear of the gates) and over: the climb or the jump-up of A10
            if (mv.controls()) {
                return;
            }
            boolean there = hero.onGround() && Math.abs(hero.getX() - (foot.getX() + 0.5)) < 0.45
                    && Math.abs(hero.getZ() - (foot.getZ() + 0.5)) < 0.45;
            if (!there && ++footTicks < 400) {
                if (nav().travel(foot, 0.4, 1.0) == NpcNavigator.Result.ARRIVED || hero.blockPosition().equals(foot)) {
                    hero.getMoveControl().setWantedPosition(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0.8);
                }
                return;
            }
            nav().reset();
            Direction face = side.getOpposite();
            String no = entry.way() == WallEntry.Way.CLIMB_OVER ? mv.climb(face) : mv.jumpUp(face, wallTopY);
            overStarted = true; // (refused: its navigation's links take it over instead)
            if (no == null) {
                overStarts++;
            } else {
                FactionClash.LOGGER.info("[Heroes] {} could not start over the wall ({}): its links take it", hero.heroId(), no);
            }
            return;
        }
        nav().travel(to, 1.5, 1.0);
    }

    /** A base's gate points. */
    private static final String[] GATES = {BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.GATE_SOUTH};

    /** The gate of the target nearest the hero (null: none recorded). */
    private BlockPos nearestGate(BaseState t) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (String k : GATES) {
            BlockPos g = t.point(k);
            if (g != null && g.distSqr(hero.blockPosition()) < bd) {
                bd = g.distSqr(hero.blockPosition());
                best = g;
            }
        }
        return best;
    }

    /** Whether any gate of the target lets it through now ({@link #gateOpen}). */
    private boolean anyGateOpen(BaseState t) {
        for (String k : GATES) {
            BlockPos g = t.point(k);
            if (g != null && gateOpen(t, g)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a gate lets it through now: open, a door any NPC opens by hand, a fence gate, or broken through. */
    private boolean gateOpen(BaseState t, BlockPos g) {
        BlockState s = hero.level().getBlockState(g);
        if (s.getBlock() instanceof DoorBlock door) {
            return door.isOpen(s) || door.type().canOpenByHand();
        }
        return gateAt(t, g) == WallEntry.Gate.WOODEN;
    }

    /**
     * The base whose walls its path search must not cross into now (null: none). On a raid, until it has chosen a way
     * over the wall (5.2: a way over is chosen only with a way out), no link takes it from outside the target's walls
     * box onto the wall or inside it: a hero whose way in is the gate goes through the gate with the soldiers, and is
     * never shut in where it cannot get out (the review of A10-A12: before, its links took it over the wall anyway).
     */
    public BaseState wallsClosedTo() {
        if (stage != Stage.RALLY && stage != Stage.APPROACH && stage != Stage.ENTRY
                || entry != null && entry.overTheWall()) {
            return null;
        }
        BaseState t = target();
        return t == null || seesFall(t) ? null : t;
    }

    /** The goal inside, until it ends (its own condition, the raid's end, 60 s without progress). */
    private void goal(FactionState f, BaseState t) {
        boolean over = t == null || seesFall(t) || f == null || f.raidTarget < 0;
        BlockPos to = over ? null : goalPoint(t);
        double d = to == null ? 0 : Math.sqrt(hero.blockPosition().distSqr(to));
        if (d < best - 1) {
            best = d;
            progressAt = hero.tickCount;
        }
        boolean everyoneIn = true;
        if (!over && hero.level() instanceof ServerLevel sl) {
            for (java.util.UUID u : f.raiders) {
                if (sl.getEntity(u) instanceof FactionNpc n && n.isAlive() && !BaseZones.insideWalls(t, n.blockPosition(), 0)) {
                    everyoneIn = false;
                }
            }
        }
        if (goal == WallEntry.Goal.HUNT_THE_HERO && hunted == null && !over) {
            hunted = homeHero(t); // (the one it hunts is one it sees; until then the hunt's minute unseen runs)
            if (hunted != null) {
                huntedSeenAt = hero.tickCount;
            }
        }
        if (hunted != null && hunted.isAlive() && hero.hasLineOfSight(hunted)) {
            huntedSeenAt = hero.tickCount;
        }
        int archersNear = over ? 0 : archers(t);
        WallEntry.GoalEnd end = WallEntry.end(goal, new WallEntry.GoalState(over, hero.tickCount - progressAt, everyoneIn,
                hunted != null && (!hunted.isAlive() || hunted.combat().boss().escape().running()),
                hero.tickCount - huntedSeenAt, archersNear > 0, looted, !over && !t.allStoragePositions().isEmpty(),
                over && t != null && seesFall(t)));
        if (end != WallEntry.GoalEnd.ONGOING) {
            goalsEnded++;
            lastEnd = end;
            WallEntry.After after = WallEntry.after(goal, end, looted);
            FactionClash.LOGGER.info("[Heroes] {} goal {} ended ({}): {}", hero.heroId(), goal, end, after);
            stage = after == WallEntry.After.JOIN_AT_THE_HEART ? Stage.HEART : Stage.HOME;
            nav().reset();
            return;
        }
        switch (goal) {
            case OPEN_THE_WAY -> openTheWay(t, to);
            case HUNT_THE_HERO -> {
                if (hunted != null && hunted.isAlive() && hero.getTarget() != hunted) {
                    hero.setTarget(hunted);
                }
                if (hunted != null) {
                    nav().travel(hunted.blockPosition(), 2, 1.0);
                } else if (to != null) {
                    nav().travel(to, 3, 1.0); // none in sight yet: it looks for the hero across the yard
                }
            }
            case CLEAR_THE_ARCHERS -> {
                FactionNpc a = nearestArcher(t);
                if (a != null && hero.getTarget() != a) {
                    hero.setTarget(a);
                }
            }
            case STOREHOUSE -> {
                if (nav().travel(to, 2.5, 1.0) == NpcNavigator.Result.ARRIVED) {
                    loot(t);
                }
            }
            case HEART -> {
                stage = Stage.HEART;
            }
        }
    }

    /**
     * Open the way (5.2): at the inside of the gate it opens it as a player would: a wooden door by hand, an iron door
     * by its button (pressed again whenever it springs back) or its lever, while soldiers of its party wait outside.
     */
    private void openTheWay(BaseState t, BlockPos inside) {
        if (nav().travel(inside, 1.5, 1.0) != NpcNavigator.Result.ARRIVED || hero.tickCount - lastPress < 10) {
            return;
        }
        lastPress = hero.tickCount;
        for (String k : new String[]{BaseState.GATE_WEST, BaseState.GATE_EAST, BaseState.GATE_SOUTH}) {
            BlockPos g = t.point(k);
            if (g == null || g.distSqr(hero.blockPosition()) > 6 * 6) {
                continue;
            }
            BlockState s = hero.level().getBlockState(g);
            if (s.getBlock() instanceof DoorBlock door && !door.isOpen(s)) {
                if (door.type().canOpenByHand()) {
                    door.setOpen(hero, hero.level(), s, g, true);
                    presses++;
                } else {
                    BlockPos sw = switchNear(t, g);
                    if (sw != null) {
                        press(sw);
                    }
                }
            }
        }
    }

    private void press(BlockPos sw) {
        BlockState s = hero.level().getBlockState(sw);
        hero.getLookControl().setLookAt(sw.getX() + 0.5, sw.getY() + 0.5, sw.getZ() + 0.5);
        hero.swing(InteractionHand.MAIN_HAND);
        if (s.getBlock() instanceof ButtonBlock b && !s.getValue(BlockStateProperties.POWERED)) {
            b.press(s, hero.level(), sw, null);
            presses++;
        } else if (s.getBlock() instanceof LeverBlock l && !s.getValue(BlockStateProperties.POWERED)) {
            l.pull(s, hero.level(), sw, null);
            presses++;
        }
    }

    private FactionNpc nearestArcher(BaseState t) {
        FactionNpc best = null;
        double bd = Double.MAX_VALUE;
        for (FactionNpc a : hero.level().getEntitiesOfClass(FactionNpc.class, hero.getBoundingBox().inflate(32),
                a -> a.isAlive() && a.baseId() == t.id && a.rangedFighter() && !(a instanceof HeroEntity))) {
            double d = a.distanceToSqr(hero);
            if (d < bd) {
                bd = d;
                best = a;
            }
        }
        return best;
    }

    /** The storehouse (5.2): one backpack of loot, as {@code GuardJob}'s looting does. */
    private void loot(BaseState t) {
        int taken = 0;
        for (int i = 0; i < 6 && Inv.freeSlots(hero.inventory()) > 1; i++) {
            List<ItemStack> got = FactionStorage.extract((ServerLevel) hero.level(), t, s -> true, 64);
            for (ItemStack s : got) {
                ItemStack rest = com.factionclash.work.Crafting.addAll(hero.inventory(), s);
                if (!rest.isEmpty()) {
                    FactionStorage.insert((ServerLevel) hero.level(), t, rest);
                }
                taken += s.getCount();
            }
            if (got.isEmpty()) {
                break;
            }
        }
        looted = true;
        hero.swing(InteractionHand.MAIN_HAND);
        FactionClash.LOGGER.info("[Heroes] {} took {} items from the storehouse of {}", hero.heroId(), taken, t.name);
    }

    /** The party's goal, the heart, struck as any raider strikes it. */
    private void heart(BaseState t) {
        if (t == null || seesFall(t)) {
            stage = Stage.HOME;
            return;
        }
        BlockPos heart = t.point(BaseState.HEART);
        if (heart == null) {
            stage = Stage.HOME;
            return;
        }
        if (!NpcNavigator.within(hero, heart, 4.0)) {
            nav().travel(heart, 3.5, 1.0);
            return;
        }
        hero.getLookControl().setLookAt(heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5);
        assault++;
        hero.level().destroyBlockProgress(hero.getId(), heart, Math.min(9, assault / 40));
        if (assault % 20 == 0) {
            hero.swing(InteractionHand.MAIN_HAND);
        }
        if (assault >= HEART_TICKS) {
            hero.level().destroyBlockProgress(hero.getId(), heart, -1);
            CampaignManager.destroyBase((ServerLevel) hero.level(), t, hero.getFaction(), hero.npcName());
            stage = Stage.HOME;
        }
    }

    /** Home: after its loot or the raid's end it goes home; with loot it counts in the party until it is there. */
    private void home(FactionState f) {
        BaseState b = hero.base();
        if (b == null || b.destroyed) {
            stage = Stage.NONE;
            return;
        }
        BlockPos spawn = b.point(BaseState.SPAWN) != null ? b.point(BaseState.SPAWN) : b.center;
        if (nav().travel(spawn, 3, 1.0) == NpcNavigator.Result.ARRIVED || BaseZones.insideWalls(b, hero.blockPosition(), 0)) {
            if (f != null && f.leaveRaid(hero.getUUID())) {
                data().setDirty();
            }
            stage = Stage.NONE;
            nav().reset();
        }
    }

    /** Death, a release, a reload: the layer starts again from the rally when the hero comes back in a raid. */
    void reset() {
        stage = Stage.NONE;
        raidOf = -1;
        hunted = null;
    }

    public String describe() {
        return "raid " + stage.name().toLowerCase(java.util.Locale.ROOT) + (entry == null ? "" : ", in by " + entry.way()
                + ", out by " + entry.exit()) + (goal == null ? "" : ", goal " + goal) + ", crossings " + wallCrossings
                + ", presses " + presses;
    }
}
