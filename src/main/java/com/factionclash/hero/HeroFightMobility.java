package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.FightMobility;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.logic.hero.Sense;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A hero's mobility in a fight (MOBILITY_AND_WORK 3.7, 5.1, 7.2-7.6; task A12), ticked with its fight runtime:
 * <ul>
 * <li><b>Aerial windows</b> (7.4): a flyer whose {@code mobility.aerial.phases} holds its phase takes off when its move
 * selector chooses the take-off like a move (its weight against the moves eligible now, after the gap), with its
 * stamina threshold, 4 blocks of headroom, no low ceiling and its ground time met; in the air it hovers off its target
 * under the fight's ceiling, starts at most 3 light ranged moves and winds up dives and rains; a dive's impact is a
 * touch-and-go (0.5 s on the ground, up again) while the window has dives left, else it stays down for its full
 * recovery; the air budget (8 s, A10), the reach clock or a drawn bow (60%, once in 8 s: 7.5's default) bring it
 * down. A phase threshold crossed in the air lands it first.</li>
 * <li><b>Reach flights</b> (3.7): outside its aerial phases a flyer takes off only to reach a target standing where no
 * walk or link reaches it (a pillar, a wall top); it flies beside it (or lands on a standable cell beside it), starts
 * no move in the air, under the same budget, clock and ground time.</li>
 * <li><b>The strike from a link</b> (3.7): on a target that left no standable cell within melee reach (a 1x1 pillar
 * top), its basic attack (never a move) lands from where its mobility holds it: the air, a cling, a jump's apex.</li>
 * <li><b>The fight-reach clock</b> (5.1.6, 5.1.15): 4 s at a time where no engaged enemy can reach it (in the air more
 * than 3 blocks over every engaged enemy's standing height; on the ground where none of them can walk, tower, swim or
 * sprint-jump to it, an enemy hero by its own links), held while it winds up a move; then it comes back: it lands, or
 * walks back toward its nearest engaged enemy ({@code combat/HeroComeBackGoal}). Never while escaping.</li>
 * </ul>
 * Engaged enemies are of any kind: nothing here reads whether anyone is a player. An escape that starts in the air
 * goes on from there (7.6): the escape's movement takes the flight over.
 */
public final class HeroFightMobility {
    /** Why it is in the air in this fight. */
    public enum Air { NONE, AERIAL, REACH }

    /** How often the ground half of the reach clock searches who reaches it (ticks). */
    static final int GROUND_CHECK_TICKS = 20;
    /** How often a flyer checks whether its target needs a reach flight (a path search; ticks). */
    static final int REACH_CHECK_TICKS = 20;
    /** A target this far over its feet may need a reach flight (lower ones a walker reaches). */
    static final double REACH_RISE = 2;

    private final HeroEntity hero;
    private final HeroCombat combat;
    private Air air = Air.NONE;
    private final FightMobility.Window window = new FightMobility.Window();
    private int touchDownAt = -1;
    private final BossRules.ReachClock clock = new BossRules.ReachClock();
    private boolean outOfReach;
    private boolean groundOut;
    private int groundCheckAt;
    private long groundKey = Long.MIN_VALUE;
    private int returnUntil = -1;
    private int reachCheckAt;
    private int strikeReadyAt;
    private long bowAnsweredAt = Long.MIN_VALUE / 4;
    private final HeroNodeEvaluator.WorldGrid grid = new HeroNodeEvaluator.WorldGrid();
    /** Counters (tests, the campaign's lines): take-offs, reach flights, dives, touch-and-goes, clock fires, strikes. */
    public int takeOffs, reachFlights, dives, touchAndGos, clockFires, linkStrikes, bowAnswers, phaseLandings;

    HeroFightMobility(HeroEntity hero, HeroCombat combat) {
        this.hero = hero;
        this.combat = combat;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    private HeroDef.Aerial aerial() {
        HeroDef d = combat.def();
        HeroDef.Mobility m = d == null || d.v2() == null ? null : d.v2().mobility();
        return m == null ? null : m.aerial();
    }

    /** Why it is in the air now ({@link Air#NONE}: not in a fight flight). */
    public Air air() {
        return air;
    }

    /** The aerial window (its light moves and dives). */
    public FightMobility.Window window() {
        return window;
    }

    /** Whether its reach clock counts it out of reach of every engaged enemy now. */
    public boolean outOfReach() {
        return outOfReach;
    }

    /** The reach clock's ticks in a row (tests). */
    public int clockTicks() {
        return clock.ticks();
    }

    /** Walking back toward its enemies after the clock fired. */
    public boolean returning() {
        return hero.tickCount < returnUntil;
    }

    /**
     * Whether the fight's mobility owns its movement now (a fight flight, a dive, the jump of an apex dive): the hold
     * goal leaves it be, so its moves start in the air, and its basic-attack goals wait (the strike from a link is
     * this class's).
     */
    public boolean ownsMovement() {
        HeroMovement mv = hero.movement();
        if (mv.diving()) {
            return true;
        }
        HeroMovement.State s = mv.state();
        if (air != Air.NONE && (s == HeroMovement.State.RISE || s == HeroMovement.State.FLY || s == HeroMovement.State.LAND)) {
            return true;
        }
        return s == HeroMovement.State.JUMP && combat.runningMove() != null && combat.runningMove().form() == HeroDef.Form.DIVE;
    }

    /** Its basic attacks wait while its fight mobility holds it in the air (5.1.5: melee on the ground, or a dive). */
    public boolean holdsBasicAttack() {
        return ownsMovement();
    }

    /** Light ranged moves started in this take-off (the sense's {@code airLights}). */
    int airLights() {
        return window.lights();
    }

    // ------------------------------------------------------------------ the tick

    /** Every tick from the fight runtime, after the boss layer. Nothing allocates out of a fight. */
    void tick() {
        if (hero.isPreview() || !hero.isAlive()) {
            return;
        }
        HeroBoss boss = combat.boss();
        HeroMovement mv = hero.movement();
        if (touchDownAt >= 0 && hero.tickCount >= touchDownAt) {
            touchDownAt = -1;
            if (mv.relift() == null) {
                touchAndGos++;
            } else {
                endWindow();
            }
        }
        if (boss.escape().running()) {
            // 7.6: an escape that starts in the air goes on from there; the escape's movement takes the flight over
            air = Air.NONE;
            window.close();
            clock.reset();
            outOfReach = false;
            returnUntil = -1;
            return;
        }
        if (!boss.fighting()) {
            if (air != Air.NONE && mv.flying() && mv.state() == HeroMovement.State.FLY) {
                mv.land();
            }
            if (air != Air.NONE && !mv.flying()) {
                air = Air.NONE;
            }
            window.close();
            clock.reset();
            outOfReach = false;
            returnUntil = -1;
            return;
        }
        // the window ends once it stands on the ground with no touch-and-go to come
        if (air != Air.NONE && touchDownAt < 0 && hero.onGround() && !mv.controls() && !mv.flying()) {
            endWindow();
        }
        LivingEntity t = hero.getTarget();
        if (air != Air.NONE && mv.state() == HeroMovement.State.FLY) {
            steer(t, mv);
        }
        if (t != null && t.isAlive() && hero.tickCount >= strikeReadyAt) {
            strikeFromLink(t, mv);
        }
        reachClock(mv);
        // a phase transition plays on the ground: a hero that crossed a threshold in the air lands first (7.4)
        if (air != Air.NONE && mv.state() == HeroMovement.State.FLY) {
            BossRules.Phases ph = boss.phases();
            if (ph != null && ph.due() && !combat.running()) {
                phaseLandings++;
                mv.land();
            }
        }
        if (air == Air.AERIAL && mv.state() == HeroMovement.State.FLY && t != null) {
            bowAnswer(t, mv);
        }
        if (air == Air.NONE && t != null && t.isAlive() && hero.tickCount >= reachCheckAt && !combat.running()
                && !boss.held()) {
            reachCheckAt = hero.tickCount + REACH_CHECK_TICKS;
            reachFlight(t, mv);
        }
    }

    private void endWindow() {
        air = Air.NONE;
        window.close();
        touchDownAt = -1;
    }

    // ------------------------------------------------------------------ aerial windows (7.4)

    /**
     * From the move check (every 5 ticks while it fights and runs no move): a flyer in an aerial phase may take off
     * now when its selector chooses the take-off like a move. True when it took off (no move is picked then).
     */
    boolean tryAerialTakeOff(MoveSelector sel, Sense s) {
        HeroDef.Aerial a = aerial();
        HeroMovement mv = hero.movement();
        if (air != Air.NONE || a == null || !mv.profile().flies() || s.airborne || s.lowCeiling || s.inWater
                || !FightMobility.aerialPhase(a, combat.boss().phase()) || !hero.onGround()) {
            return false;
        }
        if (mv.takeOffRefusal() != null || !room(FightMobility.AERIAL_HEADROOM + 2)) {
            return false;
        }
        if (!sel.chooseTakeOff(now(), s, FightMobility.takeOffWeight(a))) {
            return false;
        }
        if (mv.takeOff() != null) {
            return false;
        }
        air = Air.AERIAL;
        window.open(now());
        takeOffs++;
        if (HeroCombatLog.ON) {
            FactionClash.LOGGER.info("[Heroes] combat {} takes to the air (aerial window, phase {})", hero.heroId(),
                    combat.boss().phase());
        }
        return true;
    }

    /**
     * An aerial window now, whatever its phase and the selector's choice (an operator's {@code mobility <id> aerial},
     * tests): in a fight, a flyer that may take off. Returns null, or why not.
     */
    public String aerialTakeOff() {
        HeroMovement mv = hero.movement();
        if (!mv.profile().flies()) {
            return "it does not fly";
        }
        if (air != Air.NONE) {
            return "already in the air";
        }
        if (!combat.boss().fighting()) {
            return "not in a fight";
        }
        String no = mv.takeOff();
        if (no != null) {
            return no;
        }
        air = Air.AERIAL;
        window.open(now());
        takeOffs++;
        return null;
    }

    /** Free blocks over its footprint up to {@code blocks} (the fight's ceiling is 6 over the ground). */
    private boolean room(int blocks) {
        AABB b = hero.getBoundingBox();
        return hero.level().noCollision(hero, new AABB(b.minX, b.minY + 0.01, b.minZ, b.maxX, b.minY + blocks, b.maxZ));
    }

    /** In an aerial window it hovers off its target, over it within the ceiling; on a reach flight beside it. */
    private void steer(LivingEntity t, HeroMovement mv) {
        if (t == null || !t.isAlive()) {
            mv.hover();
            if (air == Air.REACH) {
                mv.land();
            }
            return;
        }
        Vec3 pos = hero.position();
        double dx = pos.x - t.getX(), dz = pos.z - t.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        double ux = h < 1e-3 ? 1 : dx / h, uz = h < 1e-3 ? 0 : dz / h;
        if (air == Air.REACH) {
            BlockPos stand = standableNear(t);
            if (stand != null) {
                // a cell beside the target it can stand on: it lands there and fights on the ground
                Vec3 to = Vec3.atBottomCenterOf(stand);
                mv.flyTo(new Vec3(to.x, to.y + 0.6, to.z));
                if (pos.distanceToSqr(to.x, pos.y, to.z) < 0.5 * 0.5 && Math.abs(pos.y - to.y) < 1.5) {
                    mv.land();
                }
                return;
            }
            mv.flyTo(new Vec3(t.getX() + ux * FightMobility.REACH_BESIDE, t.getY() + FightMobility.REACH_ABOVE_FEET,
                    t.getZ() + uz * FightMobility.REACH_BESIDE));
        } else if (combat.running()) {
            mv.hover(); // a wind-up in the air holds still
        } else {
            mv.flyTo(new Vec3(t.getX() + ux * FightMobility.HOVER_DISTANCE, t.getY() + FightMobility.HOVER_ABOVE,
                    t.getZ() + uz * FightMobility.HOVER_DISTANCE));
        }
        hero.getLookControl().setLookAt(t, 30F, 30F);
    }

    /** A move started: in the air it counts toward the window's light moves or dives. */
    void moveStarted(HeroDef.Move m) {
        if (window.isOpen() && !hero.onGround()) {
            window.moveStarted(m.form());
        }
        if (m.form() == HeroDef.Form.DIVE) {
            dives++;
        }
    }

    /**
     * A dive came down (the move runner's impact): a touch-and-go while the window has dives left and the air budget
     * and stamina allow, else it stays down for its full recovery. Returns the ticks of the touch-down (0: none).
     */
    int diveLanded() {
        HeroDef.Aerial a = aerial();
        HeroMovement mv = hero.movement();
        if (!window.isOpen() || a == null || !mv.profile().flies()) {
            endWindow();
            return 0;
        }
        MobilityRules.AirBudget b = mv.airBudget();
        double limit = MobilityRules.airLimit(MobilityRules.Situation.FIGHT,
                com.factionclash.config.FCConfig.MOBILITY_BOSS_AIR_SECONDS.get(), a.maxAir());
        double used = b == null ? 0 : b.airSeconds(now());
        FightMobility.AfterDive next = FightMobility.afterDive(window.dives(), a.dives(), limit - used,
                mv.stamina().share(), mv.profile().takeOffShare());
        if (next == FightMobility.AfterDive.TOUCH_AND_GO) {
            touchDownAt = hero.tickCount + FightMobility.TOUCH_DOWN_TICKS;
            return FightMobility.TOUCH_DOWN_TICKS;
        }
        endWindow();
        return 0;
    }

    /** 7.5's default answer to a drawn bow while it is in the air: it lands (60%, once in 8 s), unless authored. */
    private void bowAnswer(LivingEntity t, HeroMovement mv) {
        long now = now();
        if (now - bowAnsweredAt < FightMobility.BOW_ANSWER_COOLDOWN_TICKS || combat.running() || !drawingBow(t)
                || authoredBowReaction()) {
            return;
        }
        bowAnsweredAt = now;
        if (hero.getRandom().nextInt(100) < FightMobility.BOW_ANSWER_CHANCE) {
            bowAnswers++;
            mv.land();
        }
    }

    private boolean drawingBow(LivingEntity t) {
        if ((combat.forcedTargetState() & HeroCombat.FORCE_BOW) != 0) {
            return true;
        }
        var using = t.isUsingItem() ? t.getUseItem() : net.minecraft.world.item.ItemStack.EMPTY;
        return using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem;
    }

    private boolean authoredBowReaction() {
        HeroDef d = combat.def();
        if (d == null || d.v2() == null) {
            return false;
        }
        for (HeroDef.Reaction r : d.v2().boss().reactions()) {
            if (r.when() != null && r.when().type() == HeroDef.ConditionType.TARGET_DRAWING_BOW) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ reach flights and the strike from a link (3.7)

    /**
     * A flyer outside its aerial phases whose target stands where no walk or link reaches (its path does not get
     * there, or it holds out of reach): it takes off to it, when it may.
     */
    private void reachFlight(LivingEntity t, HeroMovement mv) {
        if (!mv.profile().flies() || !hero.onGround() || t.getY() - hero.getY() < REACH_RISE || !standing(t)
                || FightMobility.aerialPhase(aerial(), combat.boss().phase())) {
            return;
        }
        if (mv.takeOffRefusal() != null || !room(FightMobility.AERIAL_HEADROOM)) {
            return;
        }
        Path p = hero.pursuit().pathTo(t);
        boolean onFoot = p != null && p.canReach();
        if (!FightMobility.reachFlight(true, false, onFoot, true, true) || mv.takeOff() != null) {
            return;
        }
        air = Air.REACH;
        reachFlights++;
        if (HeroCombatLog.ON) {
            FactionClash.LOGGER.info("[Heroes] combat {} takes off to reach {}", hero.heroId(), HeroEntity.nameOf(t));
        }
    }

    private static boolean standing(LivingEntity t) {
        return t.onGround() || t.isInWater();
    }

    /**
     * The nearest cell (not the target's own) a hero could stand on within melee reach of the target's feet, or null
     * (a 1x1 pillar top, a 1-wide wall top: 3.7's case for the strike from a link).
     */
    BlockPos standableNear(LivingEntity t) {
        Level level = hero.level();
        BlockPos feet = t.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        double r = HeroLinks.MELEE_REACH;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        int n = (int) Math.ceil(r);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    c.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r + 1e-9 || d >= bestD || !standable(level, c)) {
                        continue;
                    }
                    best = c.immutable();
                    bestD = d;
                }
            }
        }
        return best;
    }

    private static boolean standable(Level level, BlockPos.MutableBlockPos c) {
        if (!level.isLoaded(c)) {
            return false;
        }
        BlockPos below = c.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                || !level.getBlockState(c).getCollisionShape(level, c).isEmpty()
                || !level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()) {
            return false;
        }
        return level.getFluidState(c).getType() != Fluids.LAVA && level.getFluidState(below).getType() != Fluids.LAVA;
    }

    /**
     * The strike from a link (3.7): its basic attack, never a move, on a target that left no standable cell within
     * melee reach, from the air, a cling or the apex of its jump; once a second, as a soldier swings.
     */
    private void strikeFromLink(LivingEntity t, HeroMovement mv) {
        boolean off = mv.flying() || mv.onWall() || mv.airborneForPoise() && !hero.onGround();
        double dist = HeroForms.distance(t, hero.position().add(0, hero.getBbHeight() / 2, 0));
        if (!off || combat.running() || !HeroForms.enemy(hero, t)) {
            return;
        }
        if (dist > HeroForms.MELEE_REACH || !hero.getSensing().hasLineOfSight(t)) {
            return;
        }
        if (!FightMobility.strikeFromLink(true, standableNear(t) != null, dist, HeroForms.MELEE_REACH, true)) {
            return;
        }
        strikeReadyAt = hero.tickCount + FightMobility.LINK_STRIKE_TICKS;
        hero.swing(InteractionHand.MAIN_HAND);
        if (hero.doHurtTarget(t)) {
            linkStrikes++;
        }
    }

    // ------------------------------------------------------------------ the fight-reach clock (5.1.6, 5.1.15)

    private void reachClock(HeroMovement mv) {
        outOfReach = outOfReachNow(mv);
        boolean fired = clock.tick(outOfReach, combat.windingUp(), false);
        FightMobility.ComeBack cb = FightMobility.comeBack(fired, false, mv.flying());
        if (cb == FightMobility.ComeBack.NONE) {
            return;
        }
        clockFires++;
        clock.reset();
        if (HeroCombatLog.ON) {
            FactionClash.LOGGER.info("[Heroes] combat {} out of reach 4 s: {}", hero.heroId(), cb);
        }
        if (cb == FightMobility.ComeBack.LAND) {
            mv.land();
        } else {
            returnUntil = hero.tickCount + FightMobility.RETURN_TICKS;
        }
    }

    /** The engaged enemies of any kind (the target among them), alive, within the fight's radius. */
    private List<LivingEntity> engaged() {
        List<LivingEntity> out = new ArrayList<>(4);
        LivingEntity t = hero.getTarget();
        if (t != null && t.isAlive() && HeroForms.enemy(hero, t) && t.distanceToSqr(hero) <= HeroBoss.ENGAGE_RADIUS * HeroBoss.ENGAGE_RADIUS) {
            out.add(t);
        }
        if (hero.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (UUID u : combat.boss().engagedEnemies()) {
                Entity e = sl.getEntity(u);
                if (e instanceof LivingEntity l && !out.contains(l)) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /**
     * Out of reach of every engaged enemy (5.1.6, 5.1.15): in the air more than 3 blocks above all their standing
     * heights; on the ground where none of them can get to it as they move (searched once a second while none is
     * within melee reach); in deep water or lava is the A13/escape half and not counted here.
     */
    private boolean outOfReachNow(HeroMovement mv) {
        boolean inAir = mv.flying() || mv.gliding() || mv.diving() || mv.airborneForPoise() && !hero.onGround();
        if (inAir) {
            if (hero.tickCount % 2 != 0) {
                return outOfReach;
            }
            List<LivingEntity> es = engaged();
            if (es.isEmpty()) {
                return false;
            }
            List<Double> standing = new ArrayList<>(es.size());
            for (LivingEntity e : es) {
                standing.add(e.getY());
            }
            return BossRules.aboveReach(hero.getY(), standing);
        }
        if (!hero.onGround()) {
            return outOfReach;
        }
        if (hero.tickCount < groundCheckAt) {
            if (groundOut) {
                // between searches: an enemy in melee reach now ends it at once (it came back, or one came to it)
                for (LivingEntity e : engaged()) {
                    if (HeroForms.distance(e, hero.position().add(0, hero.getBbHeight() / 2, 0)) <= HeroLinks.MELEE_REACH + 0.5) {
                        groundOut = false;
                        groundKey = Long.MIN_VALUE;
                        break;
                    }
                }
            }
            return groundOut;
        }
        groundCheckAt = hero.tickCount + GROUND_CHECK_TICKS;
        List<LivingEntity> es = engaged();
        if (es.isEmpty()) {
            groundOut = false;
            return false;
        }
        long key = hero.blockPosition().asLong();
        for (LivingEntity e : es) {
            if (HeroForms.distance(e, hero.position().add(0, hero.getBbHeight() / 2, 0)) <= HeroLinks.MELEE_REACH + 0.5) {
                groundOut = false;
                groundKey = Long.MIN_VALUE;
                return false;
            }
            if (e instanceof HeroEntity eh && eh.movement().profile().flies()) {
                groundOut = false; // a flying enemy hero reaches anywhere
                return false;
            }
            key = key * 31 + e.blockPosition().asLong();
        }
        if (key == groundKey) {
            return groundOut;
        }
        groundKey = key;
        grid.reset(hero.level(), hero.level().getMinBuildHeight());
        List<HeroLinks.Enemy> list = new ArrayList<>(es.size());
        for (LivingEntity e : es) {
            BlockPos p = e.blockPosition();
            MobilityRules.Profile prof = e instanceof HeroEntity eh ? eh.movement().profile() : MobilityRules.NONE;
            list.add(new HeroLinks.Enemy(new HeroLinks.Pos(p.getX(), p.getY(), p.getZ()), prof));
        }
        BlockPos hp = hero.blockPosition();
        groundOut = !HeroLinks.anyEnemyReaches(grid, new HeroLinks.Pos(hp.getX(), hp.getY(), hp.getZ()), list);
        grid.clear();
        return groundOut;
    }

    /** The nearest engaged enemy (the come-back walks toward it), or null. */
    public LivingEntity nearestEngagedEnemy() {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : engaged()) {
            double d = e.distanceToSqr(hero);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Death, a reload, a release. */
    void reset() {
        air = Air.NONE;
        window.close();
        touchDownAt = -1;
        clock.reset();
        outOfReach = groundOut = false;
        returnUntil = -1;
        groundKey = Long.MIN_VALUE;
    }

    /** For the sheet and the dump. */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "air %s, window %s (lights %d, dives %d), reach clock %d, take-offs %d,"
                        + " reach flights %d, dives %d, touch-and-go %d, clock fires %d, link strikes %d",
                air.name().toLowerCase(java.util.Locale.ROOT), window.isOpen() ? "open" : "closed", window.lights(),
                window.dives(), clock.ticks(), takeOffs, reachFlights, dives, touchAndGos, clockFires, linkStrikes);
    }
}
