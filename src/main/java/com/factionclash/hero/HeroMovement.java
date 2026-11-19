package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.FightMobility;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.LeapSolver;
import com.factionclash.logic.hero.MobilityRules;
import com.factionclash.logic.hero.MobilityRules.Feature;
import com.factionclash.logic.hero.MobilityRules.Stat;
import com.factionclash.logic.hero.MovementRules;
import com.factionclash.logic.hero.SoundIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A hero's superhuman movement on the server (MOBILITY_AND_WORK 1-2, 3.1, 3.3, 3.4, 4.2 and 5.3; task A10): the
 * level-scaled attribute modifiers of its mobility (jump strength, step height, safe fall and fall damage, travel speed,
 * movement and water efficiency, swimming, the charger's knockback while travelling; {@link MobilityRules}, every
 * feature from level 1 and the numbers growing with the power {@code p(L)}), and the physical actions: jumps, a solved
 * leap with its 0.3 s crouch tell, air jumps, wall kicks, the mantle, climbing and clinging (vanilla's ladder physics
 * through {@link HeroEntity#onClimbable}), gliding (a vertical-speed clamp and a forward push), flight (take-off with its
 * 0.6 s tell, powered flight with its stamina, the forced landing, the glide down when the stamina is gone), the
 * bursts of runners and chargers, air poise and the downing (2.4: it falls with no control, lies 2 s taking +20% and
 * cannot take off for 12 s), and the {@code DATA_LOCO} byte the client animates from.
 *
 * <p>Every movement is physical: the server sets the hero's velocity and vanilla moves it, stopped by blocks, with
 * vanilla's own air physics (a leap's impulse is the one {@link LeapSolver} solved over the real blocks, and the take-off
 * tick counts as airborne, as the solver does). Nothing teleports. While an action runs the hero's own walking is held
 * ({@code combat/HeroHoldGoal}) and its input is zeroed (mobs get no air control). Nothing here reads whether anyone is
 * a player: air poise counts the same arrow the same from an NPC archer and a player, the air budget and the altitude
 * ceiling read engaged enemies of any kind. Workers and soldiers have no movement engine: only heroes do.
 *
 * <p>A11 drives these actions from its navigation links; A12 from fights. Until then an operator drives them with
 * {@code /fc admin hero mobility}, and a glider opens its glide by itself when it falls from a height.
 */
public final class HeroMovement {
    /** What the hero is doing with its mobility. */
    public enum State {
        /** walking, standing, swimming or falling as any NPC (vanilla), maybe in the air on its own jump */
        NONE,
        /** the 0.3 s crouch before a leap (the tell) */
        TELL,
        /** in the air on a solved leap */
        LEAP,
        /** in the air on its own jump, air jump or wall kick */
        JUMP,
        /** climbing up a wall face */
        CLIMB,
        /** clinging still to a wall */
        CLING,
        /** climbing down a wall face */
        CLIMB_DOWN,
        /** pulling itself onto a ledge */
        MANTLE,
        GLIDE,
        /** the 0.6 s take-off tell on the ground */
        TAKEOFF,
        /** the take-off's 1.5-block push */
        RISE,
        /** powered flight, toward a point or hovering */
        FLY,
        /** a powered descent to the ground */
        LAND,
        /** the 0.4 s landing on the ground */
        LANDING,
        /** shot down: falling with no control */
        DOWNED_FALL,
        /** shot down: lying 2 s */
        DOWNED,
        /** a jump-up link (A11): the crouch, a straight jump with its air jumps, then over the ledge's edge */
        JUMP_UP,
        /** a chimney link (A11): the crouch, a jump, then wall kicks from side to side up the shaft and out at its top */
        CHIMNEY,
        /** a climb-down link (A11): backing over the edge of a ledge onto the face below it */
        DESCEND,
        /** a {@code dive} (A12, 7.3): pushed along a straight line to its marker, stopped by blocks */
        DIVE,
        /**
         * an exhausted flyer that landed, catching its breath out of a fight ({@code move.winded}, MOBILITY_AND_WORK
         * 2.2: "a flyer lands, rests and goes on"): it stands until it is no longer exhausted (50%); a fight, a target,
         * an escape or a blow ends it at once
         */
        WINDED
    }

    /** What downed it (the log and the tests). */
    public enum Cause { AIR_POISE, STAGGER, CONTROL, LIGHTNING, OPERATOR }

    /** A leap's tell, the crouch (0.3 s). */
    public static final int TELL_TICKS = 6;
    /** A climber needs this long after letting go before it can grip again (0.5 s, 1.8). */
    public static final int GRIP_AFTER = 10;
    /** A climber pushes into the face this hard (vanilla clamps it to 0.15 on a climbable). */
    static final double CLIMB_PUSH = 0.12;
    /** Over the edge and the mantle: the forward step (blocks a tick). */
    static final double EDGE_PUSH = 0.2;
    /** The share of the way to the flight's wanted velocity taken each tick (a knock-back still moves a flyer). */
    static final double FLIGHT_BLEND = 0.35;
    /** An action that never ends on its own (a bug, an odd world) gives up after this many ticks. */
    static final int ACTION_TIMEOUT = 200;

    private static final ResourceLocation LANDING_SLOW = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "mobility_landing");
    private static final ResourceLocation BURST = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "mobility_burst");
    /** Every trait's modifier name ({@code factionclash:mobility_<trait>}, 1.1). */
    private static final ResourceLocation[] TRAIT_IDS;
    private static final Map<MobilityRules.Attr, Holder<Attribute>> ATTRS = new EnumMap<>(MobilityRules.Attr.class);

    static {
        HeroDef.MobilityTrait[] all = HeroDef.MobilityTrait.values();
        TRAIT_IDS = new ResourceLocation[all.length];
        for (int i = 0; i < all.length; i++) {
            TRAIT_IDS[i] = ResourceLocation.parse(MobilityRules.modifierId(all[i]));
        }
        ATTRS.put(MobilityRules.Attr.JUMP_STRENGTH, Attributes.JUMP_STRENGTH);
        ATTRS.put(MobilityRules.Attr.STEP_HEIGHT, Attributes.STEP_HEIGHT);
        ATTRS.put(MobilityRules.Attr.SAFE_FALL_DISTANCE, Attributes.SAFE_FALL_DISTANCE);
        ATTRS.put(MobilityRules.Attr.FALL_DAMAGE_MULTIPLIER, Attributes.FALL_DAMAGE_MULTIPLIER);
        ATTRS.put(MobilityRules.Attr.MOVEMENT_SPEED, Attributes.MOVEMENT_SPEED);
        ATTRS.put(MobilityRules.Attr.MOVEMENT_EFFICIENCY, Attributes.MOVEMENT_EFFICIENCY);
        ATTRS.put(MobilityRules.Attr.KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE);
        ATTRS.put(MobilityRules.Attr.SWIM_SPEED, net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
        ATTRS.put(MobilityRules.Attr.WATER_MOVEMENT_EFFICIENCY, Attributes.WATER_MOVEMENT_EFFICIENCY);
        ATTRS.put(MobilityRules.Attr.OXYGEN_BONUS, Attributes.OXYGEN_BONUS);
        ATTRS.put(MobilityRules.Attr.SUBMERGED_MINING_SPEED, Attributes.SUBMERGED_MINING_SPEED);
        ATTRS.put(MobilityRules.Attr.BURNING_TIME, Attributes.BURNING_TIME);
    }

    private final HeroEntity hero;

    private MobilityRules.Profile profile = MobilityRules.NONE;
    private MobilityRules.Config cfg = MobilityRules.Config.DEFAULT;
    private int configCheck;
    /** The modifiers on the hero now, and what they were made for. */
    private List<MobilityRules.Modifier> applied = List.of();
    private boolean appliedTravelling, appliedFast;
    private MobilityRules.Profile appliedFor;

    private State state = State.NONE;
    private int stateTicks;
    private final MovementRules.Loco loco = new MovementRules.Loco();

    // stamina, air poise, air budget
    private MobilityRules.Stamina stamina = new MobilityRules.Stamina(0, 0);
    private final BossRules.AirPoise airPoise = new BossRules.AirPoise();
    private MobilityRules.AirBudget airBudget;
    private boolean staminaRestored;

    // airtime
    private int airJumpsUsed;
    private int lastKickFace = MovementRules.NO_FACE;
    private boolean ownAirtime;
    private boolean inAir;
    private double peakY;
    private boolean noGlideUntilLanding;
    private boolean glideChecked;

    // the leap
    private BlockPos leapStart, leapTarget;
    private int leapDx, leapDz;
    private LeapSolver.Leap leapPlan;
    private boolean apexChecked;
    private double leapApex;
    private LeapResult lastLeap;

    // climbing
    private Direction climbFace;
    /** The column of the face it climbs (it steps onto it at the top), and the ticks it has been stepping in. */
    private BlockPos climbColumn;
    private int settleTicks;
    private double climbStartY;
    private boolean climbLeftGround;
    private long gripAfter;
    private double mantleTop;

    // gliding
    private double glideX, glideZ;
    private int gustsUsed;
    private int glideTicks;

    // flight
    private Vec3 flyTarget;
    private boolean hovering;
    private final double[] wanted = new double[3];
    private double groundYCache = Double.NaN;
    private int groundCheck;
    private boolean loaded;
    private int downs;
    private long landedAt = Long.MIN_VALUE / 4;

    // the navigation's links (A11): a jump-up's ledge, a chimney's exit, a climb down
    private Direction linkFace;
    private double linkTop;
    private BlockPos linkColumn;
    private BlockPos linkExit;
    private boolean descending;
    /** The action was started by the hero's navigation (a link or a travel flight): the hold goal leaves it be. */
    private boolean navOwned;

    // bursts and landings
    private final MovementRules.BurstClock burst = new MovementRules.BurstClock();
    private boolean bursting;
    private int landingSlowUntil = -1;
    private boolean landingSlowOn;

    public HeroMovement(HeroEntity hero) {
        this.hero = hero;
    }

    // ------------------------------------------------------------------ the profile and its modifiers (1.1)

    /** The config keys of 8.5 as they are now. */
    public static MobilityRules.Config config() {
        return new MobilityRules.Config(FCConfig.MOBILITY_ENABLED.get(), FCConfig.MOBILITY_FLIGHT.get(),
                new MobilityRules.Curve(FCConfig.MOBILITY_POWER_AT_ONE.get(), FCConfig.MOBILITY_FULL_POWER_LEVEL.get()),
                FCConfig.MOBILITY_STAMINA_PERCENT.get());
    }

    /** The hero's mobility at its level now ({@link MobilityRules#NONE} when it has none, is dormant or a preview). */
    public MobilityRules.Profile profile() {
        return profile;
    }

    /**
     * Works the profile out again from the definition, the level and the config (a definition applied, a level gained,
     * a config reload) and re-applies the modifiers; the stamina keeps its share of the new maximum.
     */
    public void refresh() {
        if (hero.level().isClientSide) {
            return;
        }
        cfg = config();
        HeroDef d = hero.isPreview() || hero.dormant() ? null : hero.definition();
        MobilityRules.Profile p = d == null || d.v2() == null ? MobilityRules.NONE
                : MobilityRules.profile(d.v2().mobility(), hero.npcLevel(), cfg);
        if (p != profile && !samePowers(p, profile)) {
            double share = stamina.max() > 0 ? stamina.share() : 1;
            boolean exhausted = stamina.exhausted();
            profile = p;
            stamina = new MobilityRules.Stamina(p);
            if (stamina.max() > 0) {
                stamina.set(share * stamina.max(), exhausted);
            }
        } else {
            profile = p;
        }
        rider = mountCarrier();
        if (!profile.flies() && (state == State.FLY || state == State.RISE || state == State.LAND || state == State.TAKEOFF)) {
            stopPowered();
            setState(State.NONE);
        }
        applyModifiers(true);
    }

    private static boolean samePowers(MobilityRules.Profile a, MobilityRules.Profile b) {
        return a.power() == b.power() && a.features().equals(b.features()) && a.raw(Stat.STAMINA) == b.raw(Stat.STAMINA)
                && a.raw(Stat.REGAIN) == b.raw(Stat.REGAIN);
    }

    /** On a trip with no fight (an escape is travel, 5.1.10): the runner's speed and the charger's knock-back are on. */
    private boolean travelling() {
        HeroBoss boss = hero.combat().boss();
        if (boss.escape().running()) {
            return true;
        }
        return hero.getTarget() == null && !boss.fighting() && !hero.getNavigation().isDone() && state == State.NONE;
    }

    private void applyModifiers(boolean force) {
        boolean travelling = profile.any() && travelling();
        Vec3 v = hero.getDeltaMovement();
        boolean fast = travelling && v.x * v.x + v.z * v.z > (MobilityRules.WALK_SPEED * MovementRules.TICK)
                * (MobilityRules.WALK_SPEED * MovementRules.TICK);
        if (!force && appliedFor == profile && travelling == appliedTravelling && fast == appliedFast) {
            return;
        }
        List<MobilityRules.Modifier> mods = MobilityRules.modifiers(profile, travelling, fast);
        if (!force && mods.equals(applied)) {
            appliedTravelling = travelling;
            appliedFast = fast;
            return;
        }
        for (Map.Entry<MobilityRules.Attr, Holder<Attribute>> e : ATTRS.entrySet()) {
            AttributeInstance inst = hero.getAttribute(e.getValue());
            if (inst != null) {
                for (ResourceLocation id : TRAIT_IDS) {
                    inst.removeModifier(id);
                }
            }
        }
        for (MobilityRules.Modifier m : mods) {
            Holder<Attribute> h = ATTRS.get(m.attr());
            AttributeInstance inst = h == null ? null : hero.getAttribute(h);
            if (inst != null) {
                inst.addTransientModifier(new AttributeModifier(ResourceLocation.parse(m.id()), m.amount(), op(m.op())));
            }
        }
        applied = mods;
        appliedFor = profile;
        appliedTravelling = travelling;
        appliedFast = fast;
    }

    private static AttributeModifier.Operation op(MobilityRules.Op o) {
        return switch (o) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    /** The modifiers on the hero now (tests). */
    public List<MobilityRules.Modifier> modifiers() {
        return applied;
    }

    // ------------------------------------------------------------------ what the entity asks

    public State state() {
        return state;
    }

    /** Whether an action owns the hero's movement now: its input is zeroed and its walking held. */
    public boolean controls() {
        return state != State.NONE;
    }

    /** Whether it is held still on the ground (a tell, a landing, downed): no move starts. */
    public boolean holdsStill() {
        return state == State.TELL || state == State.TAKEOFF || state == State.LANDING || state == State.DOWNED
                || state == State.WINDED
                || (state == State.JUMP_UP || state == State.CHIMNEY) && stateTicks < TELL_TICKS;
    }

    /** Climbing, clinging or climbing down: vanilla's ladder physics move it ({@link HeroEntity#onClimbable}). */
    public boolean onWall() {
        return state == State.CLIMB || state == State.CLING || state == State.CLIMB_DOWN;
    }

    public boolean clinging() {
        return state == State.CLING;
    }

    /** In powered flight (its take-off push, the flight, a powered landing). */
    public boolean flying() {
        return state == State.RISE || state == State.FLY || state == State.LAND;
    }

    public boolean gliding() {
        return state == State.GLIDE;
    }

    /** Lying downed (2 s, +20% damage taken). */
    public boolean downed() {
        return state == State.DOWNED;
    }

    /** Falling after a downing (the fall counts as one hit for the per-hit cap, 2.4). */
    public boolean downedFall() {
        return state == State.DOWNED_FALL;
    }

    /**
     * Whether it is airborne for air poise (2.4): flying, gliding, or a leaper or acrobat in the air on its own leap or
     * jump. Not a hero lifted by a blow's knock-back or falling off a ledge: air poise knocks a hero out of its own
     * airtime, so a grounded hero bounced by four blows is not "shot down" (found by the escape GameTests: the player's
     * blows every 11 ticks landed while the last one's knock-back still held it up).
     */
    public boolean airborneForPoise() {
        return switch (state) {
            case RISE, FLY, LAND, GLIDE, LEAP, JUMP, MANTLE, DIVE -> true;
            case JUMP_UP, CHIMNEY -> stateTicks >= TELL_TICKS;
            case NONE -> ownAirtime && profile.airborne() && !hero.onGround() && !hero.isInWater() && !hero.onClimbable()
                    && !hero.isPassenger();
            default -> false;
        };
    }

    public MobilityRules.Stamina stamina() {
        return stamina;
    }

    public BossRules.AirPoise airPoise() {
        return airPoise;
    }

    public int airJumpsUsed() {
        return airJumpsUsed;
    }

    /** How many times it was downed since it was made (tests, the campaign's counters). */
    public int downs() {
        return downs;
    }

    /** The last leap's outcome (null before any). */
    public LeapResult lastLeap() {
        return lastLeap;
    }

    /** A leap's outcome: the plan, where it landed, its highest point above the take-off, its ticks in the air. */
    public record LeapResult(LeapSolver.Leap plan, boolean landed, boolean onTarget, double apex, double along,
                             double landedY, int ticks, String refusal) {
    }

    /** Whether a block keeps it from slowing (1.5, 1.13: cobwebs and berry bushes for a charger II-III, powder snow). */
    public boolean ignoresStuck(BlockState s) {
        if (profile.has(Feature.WEB_IMMUNE) && (s.is(Blocks.COBWEB) || s.is(Blocks.SWEET_BERRY_BUSH))) {
            return true;
        }
        return profile.has(Feature.POWDER_SNOW_FREE) && s.is(Blocks.POWDER_SNOW);
    }

    /** {@code snowstride}: it never freezes. */
    public boolean neverFreezes() {
        return profile.has(Feature.NEVER_FREEZES);
    }

    /** {@code ember_stride}: fire- and lava-proof. */
    public boolean fireproof() {
        return profile.has(Feature.FIREPROOF);
    }

    /** Whether its carrier is a mount (worked out with the profile). */
    private boolean rider;

    private boolean rides() {
        return rider;
    }

    /** A mount carrier (a broom, a cloud, a sword, a crane): it rides instead of flying on wings (4.3). */
    private boolean mountCarrier() {
        HeroDef d = hero.dormant() ? null : hero.definition();
        HeroDef.Mobility m = d == null || d.v2() == null ? null : d.v2().mobility();
        HeroDef.Carrier k = m == null || m.carrier() == null ? null : m.carrier().kind();
        return k == HeroDef.Carrier.BROOM || k == HeroDef.Carrier.CLOUD || k == HeroDef.Carrier.SWORD
                || k == HeroDef.Carrier.CRANE;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    private MobilityRules.Situation situation() {
        HeroBoss boss = hero.combat().boss();
        if (boss.escape().running()) {
            return MobilityRules.Situation.ESCAPE;
        }
        if (boss.fighting()) {
            return MobilityRules.Situation.FIGHT;
        }
        // a raid's approach and wall crossing, before anyone engages it (A12: 5.1.4's 10 s, 2.3's 10 blocks)
        return HeroNodeEvaluator.onRaid(hero) ? MobilityRules.Situation.RAID : MobilityRules.Situation.TRAVEL;
    }

    private void setState(State s) {
        state = s;
        stateTicks = 0;
        if (s == State.NONE) {
            navOwned = false;
            descending = false;
        }
    }

    /**
     * Whether the running action was started by the hero's own navigation (a link it follows, a travel flight, task
     * A11): its walking and its job go on around it (the navigation waits for the action), so the hold goal does not
     * hold it. An action of a fight or an operator is not.
     */
    public boolean navOwned() {
        return navOwned && state != State.NONE;
    }

    /** Marks the running action as the navigation's (or not). */
    public void navOwned(boolean owned) {
        navOwned = owned && state != State.NONE;
    }

    // ------------------------------------------------------------------ the tick (server, before vanilla's travel)

    /** One server tick, from {@link HeroEntity#aiStep} before vanilla's AI and travel. */
    public void tick() {
        if (++configCheck >= 100) {
            configCheck = 0;
            if (!config().equals(cfg)) {
                refresh();
            }
        }
        long now = now();
        airPoise.decay(now);
        boolean ground = hero.onGround();
        stateTicks++;
        if (airborneForPoise() && MovementRules.downsAtOnce(HeroStatus.stunned(hero), HeroStatus.frozen(hero),
                slowness(), false)) {
            down(Cause.CONTROL);
        }
        switch (state) {
            case NONE -> noneTick(ground);
            case TELL -> tellTick();
            case LEAP -> leapTick(ground);
            case JUMP -> jumpTick(ground);
            case CLIMB -> climbTick(ground);
            case CLING -> clingTick();
            case CLIMB_DOWN -> climbDownTick(ground);
            case MANTLE -> mantleTick(ground);
            case GLIDE -> glideTick(ground);
            case TAKEOFF -> takeOffTick();
            case RISE -> riseTick();
            case FLY -> flyTick(now);
            case LAND -> landTick(ground);
            case LANDING -> landingTick();
            case DOWNED_FALL -> downedFallTick(ground);
            case DOWNED -> downedTick();
            case JUMP_UP -> jumpUpTick(ground);
            case CHIMNEY -> chimneyTick(ground);
            case DESCEND -> descendTick(ground);
            case DIVE -> diveTick(ground);
            case WINDED -> windedTick();
        }
        if (windedPending) {
            windedPending = false;
            if (state == State.NONE && hero.onGround() && mayRest()) {
                setState(State.WINDED); // 2.2: exhausted on landing, it pants where it stands
            }
        }
        staminaTick(now);
        if (state == State.WINDED && !stamina.exhausted()) {
            setState(State.NONE); // rested (50%): on with its job this tick
        }
        if (profile.any()) {
            if (bursting || hero.tickCount % 5 == 0) {
                burstTick(now);
            }
            applyModifiers(false);
        }
        landingSlowTick();
        loco.tick();
        publishLoco();
    }

    /** {@code DATA_LOCO} from the state now. */
    private void publishLoco() {
        loco.mode(shownMode());
        loco.tired(profile.flies() && stamina.max() > 0 && stamina.low());
        byte b = loco.pack();
        if (hero.loco() != b) {
            hero.setLoco(b);
        }
    }

    /**
     * After vanilla's travel this tick (from {@link HeroEntity#travel}): a downed fall that touched down here goes on to
     * {@code move.downed} on this same tick, so the client never sees the hero on the ground in the ground mode between
     * {@code move.fall} and {@code move.downed} (the live check of 2026-11-19 saw an idle frame there); any other airtime
     * that ends here starts its landing one-shot on this tick too.
     */
    public void afterTravel() {
        boolean down = hero.onGround() || hero.isInWater() || hero.onClimbable();
        if (state == State.DOWNED_FALL && down) {
            downedFallTick(true);
            publishLoco();
        } else if (state == State.NONE && inAir && down && !hero.isPreview()) {
            // the same for any airtime that ends here (a jump, a dive's last bit): its landing one-shot from this tick, not
            // one tick in the air clip on the ground first
            noneTick(hero.onGround());
            publishLoco();
        }
    }

    private int slowness() {
        MobEffectInstance e = hero.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e == null ? -1 : e.getAmplifier();
    }

    /** The mode {@code DATA_LOCO} shows now (a one-shot shows its own until it has played). */
    private int shownMode() {
        boolean rider = (state == State.TAKEOFF || state == State.RISE || state == State.FLY || state == State.LAND
                || state == State.LANDING || state == State.DIVE) && rides();
        return switch (state) {
            case NONE -> {
                if (profile.flies() && MovementRules.winded(stamina.exhausted(), hero.onGround(), horizontalSpeed())) {
                    yield MovementRules.WINDED;
                }
                if (bursting) {
                    yield profile.has(Feature.TRAVERSAL_CHARGE) ? MovementRules.CHARGE_RUN : MovementRules.SPRINT;
                }
                yield ownAirtime && !hero.onGround() ? MovementRules.AIRBORNE : MovementRules.GROUND;
            }
            case TELL, DOWNED_FALL, DESCEND -> MovementRules.GROUND;
            case LEAP, JUMP -> MovementRules.AIRBORNE;
            case JUMP_UP, CHIMNEY -> stateTicks < TELL_TICKS ? MovementRules.GROUND : MovementRules.AIRBORNE;
            case CLIMB, CLIMB_DOWN, MANTLE -> MovementRules.CLIMB;
            case CLING -> MovementRules.CLING;
            case GLIDE -> MovementRules.GLIDE;
            case TAKEOFF, RISE, LAND -> rider ? MovementRules.RIDE : MovementRules.FLY;
            // landed: the landing one-shot shows its own mode (the ride's or the flight's) while it plays; under it the
            // hero stands, so no tick of the ride loop shows after move.fly_land (the live check of 2026-11-19)
            case LANDING -> MovementRules.GROUND;
            case WINDED -> MovementRules.WINDED;
            case DIVE -> rider ? MovementRules.RIDE : profile.flies() ? MovementRules.FLY : MovementRules.AIRBORNE;
            case FLY -> rider ? MovementRules.RIDE : hovering ? MovementRules.FLY_HOVER : MovementRules.FLY;
            case DOWNED -> MovementRules.DOWNED;
        };
    }

    private double horizontalSpeed() {
        Vec3 v = hero.getDeltaMovement();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    /** Starts a one-shot named by {@code mode} and {@code kind}, for its clip's length in the hero's timing files. */
    private void oneShot(int mode, int kind) {
        String clip = HeroClips.oneShotClip(mode, kind);
        HeroTimings.Clip t = clip == null ? null : HeroTimingFiles.clip(hero.entry(), clip);
        int ticks = t != null && t.ticks() > 0 ? t.ticks() : HeroClips.oneShotTicks(clip);
        loco.oneShot(mode, kind, ticks);
    }

    /** The ticks of a one-shot clip in this hero's timing files (the take-off's tell, the landing's hold). */
    private int clipTicks(String clip) {
        HeroTimings.Clip t = HeroTimingFiles.clip(hero.entry(), clip);
        return t != null && t.ticks() > 0 ? t.ticks() : HeroClips.oneShotTicks(clip);
    }

    // ------------------------------------------------------------------ walking (vanilla) and its airtime

    private void noneTick(boolean ground) {
        if (hero.isPreview()) {
            return;
        }
        boolean airNow = !ground && !hero.isInWater() && !hero.onClimbable() && !hero.isPassenger();
        if (airNow) {
            if (!inAir) {
                inAir = true;
                peakY = hero.getY();
                glideChecked = false;
            }
            peakY = Math.max(peakY, hero.getY());
            // a glider opens its glide when it falls from a height (1.9)
            if (profile.glides() && !noGlideUntilLanding && !glideChecked && hero.getDeltaMovement().y < -MovementRules.GLIDE_OPEN_SPEED) {
                glideChecked = true;
                if (MovementRules.opensGlide(hero.getDeltaMovement().y, footprintDrop(40))) {
                    Vec3 v = hero.getDeltaMovement();
                    double h = Math.sqrt(v.x * v.x + v.z * v.z);
                    Vec3 look = hero.getLookAngle();
                    glide(h > 0.02 ? new Vec3(v.x, 0, v.z) : new Vec3(look.x, 0, look.z));
                    return;
                }
            }
        } else if (inAir) {
            // a dive that reached its marker just above the ground touches down here: a dive's heavy landing all the same,
            // so its one-shot covers the touch-down instead of a run while the client's smoothed speed falls (live check)
            landed(diveLanding, peakY - hero.getY());
        }
    }

    /** A dive ended just above the ground: its touch-down is a dive's landing (A12; the live check of 2026-11-19). */
    private boolean diveLanding;
    /** It landed exhausted: the winded rest begins after this tick's state, if it may rest (2.2). */
    private boolean windedPending;
    /** The winded rest never lasts longer than this (a safety bound: 50% comes back in about 10 s at level 1). */
    static final int WINDED_MAX_TICKS = 30 * 20;

    /** Out of a fight, with no target, not escaping and not in water: an exhausted flyer may stand and pant (2.2). */
    private boolean mayRest() {
        return !hero.isPreview() && hero.getTarget() == null && !hero.isInWater() && !hero.combat().boss().fighting()
                && !hero.combat().boss().escape().running();
    }

    /** The winded rest: it stands still (no walking, no job) until it is no longer exhausted, or a fight calls it. */
    private void windedTick() {
        if (!stamina.exhausted() || !mayRest() || !hero.onGround() || hero.hurtTime > 0 || stateTicks > WINDED_MAX_TICKS) {
            setState(State.NONE);
            return;
        }
        hero.getNavigation().stop();
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(0, Math.min(0, v.y), 0);
    }

    /** Whether it stands catching its breath (2.2). */
    public boolean winded() {
        return state == State.WINDED;
    }

    /** It touched down after an airtime: the landing of {@link MovementRules#landing}; the counts reset. */
    private void landed(boolean afterLeap, double fall) {
        inAir = false;
        ownAirtime = false;
        diveLanding = false;
        if (profile.flies() && stamina.exhausted()) {
            windedPending = true; // 2.2: move.winded on landing (taken after this tick's state, out of a fight)
        }
        noGlideUntilLanding = false;
        airJumpsUsed = 0;
        lastKickFace = MovementRules.NO_FACE;
        if (hero.isInWater()) {
            return;
        }
        MovementRules.Landing l = MovementRules.landing(afterLeap, fall, profile.has(Feature.LANDING_ROLL));
        switch (l) {
            case LAND -> oneShot(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_END);
            case LAND_HEAVY -> oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_END);
            case LAND_ROLL -> oneShot(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_SPECIAL);
            default -> {
            }
        }
        if (MovementRules.slows(l)) {
            landingSlowUntil = hero.tickCount + MovementRules.LANDING_SLOW_TICKS;
        }
        if (afterLeap) {
            HeroSounds.cue(hero, SoundIndex.LANDING); // a heavy landing (SOUND.md 7)
        }
    }

    /** Vanilla made it jump (its navigation's jump control): a hero with a jump bonus plays its jump (4.1). */
    public void onVanillaJump() {
        if (profile.jumpHeight() > MobilityRules.WORKER_JUMP + 1e-6 && state == State.NONE) {
            ownAirtime = true;
            inAir = true;
            peakY = hero.getY();
            oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_START);
        }
    }

    /** Whether vanilla's own jump is held (an action owns its movement). */
    public boolean holdsVanillaJump() {
        return state != State.NONE;
    }

    // ------------------------------------------------------------------ jumps, leaps, air jumps, wall kicks (1.6, 1.7, 3.3)

    /** A plain jump with its jump strength, now (null), or why not. */
    public String jump() {
        if (state != State.NONE || !hero.onGround() || hero.isInWater()) {
            return "not standing on the ground";
        }
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x, LeapSolver.attributeFor(profile.jumpHeight()) * hero.blockJumpFactor(), v.z);
        hero.setOnGround(false);
        hero.hasImpulse = true;
        ownAirtime = true;
        inAir = true;
        peakY = hero.getY();
        setState(State.JUMP);
        oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_START);
        return null;
    }

    /**
     * A leap from the cell it stands on to {@code target} (the cell its feet land in; straight along x or z), solved over
     * the real blocks within its current limits: the 0.3 s crouch (the tell, held still and facing the landing), then the
     * impulse. Returns null when it starts, or why it does not (no solution: no leap).
     */
    public String leapTo(BlockPos target) {
        if (state != State.NONE || !hero.onGround()) {
            return "not standing on the ground";
        }
        BlockPos start = hero.blockPosition();
        int dx = target.getX() - start.getX(), dz = target.getZ() - start.getZ();
        if (dx != 0 && dz != 0 || dx == 0 && dz == 0) {
            return "a leap goes straight along x or z";
        }
        int n = Math.abs(dx + dz);
        leapDx = Integer.signum(dx);
        leapDz = Integer.signum(dz);
        leapStart = start;
        leapTarget = target;
        LeapSolver.Leap plan = solveLeap(n, target.getY() - start.getY(), 0, 0);
        if (plan == null) {
            lastLeap = new LeapResult(null, false, false, 0, 0, 0, 0, "no leap within its limits lands there");
            return lastLeap.refusal();
        }
        leapPlan = plan;
        face(leapDx, leapDz);
        setState(State.TELL);
        oneShot(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_START);
        return null;
    }

    /** The hero's limits on a leap: its jump height, and its widest gap (a jump-up to the next cell: any horizontal). */
    private LeapSolver.Limits limits(int columns) {
        if (columns <= 1) {
            return LeapSolver.Limits.jump(profile.jumpHeight());
        }
        if (columns - 1 > profile.gap()) {
            return new LeapSolver.Limits(LeapSolver.PLAIN_JUMP, 0); // wider than its gap: no leap
        }
        return LeapSolver.Limits.forGap(profile.gap(), profile.jumpHeight());
    }

    private LeapSolver.Leap solveLeap(int columns, int rise, double startX, double startY) {
        LeapSolver.Limits lim = limits(columns);
        if (lim.maxHorizontal() <= 0 && columns > 1) {
            return null;
        }
        return LeapSolver.solve(startX, startY, columns, rise, lim, slice());
    }

    /** The blocks along the leap as {@link LeapSolver} reads them (column 0 is the start cell). */
    private LeapSolver.Slice slice() {
        Level level = hero.level();
        BlockPos s = leapStart;
        int dx = leapDx, dz = leapDz;
        return (c, y) -> {
            BlockPos p = s.offset(dx * c, y, dz * c);
            if (!level.isLoaded(p)) {
                return 1;
            }
            BlockState st = level.getBlockState(p);
            VoxelShape shape = st.getCollisionShape(level, p);
            return shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
        };
    }

    private void face(double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        hero.setYRot(yaw);
        hero.yBodyRot = yaw;
        hero.yHeadRot = yaw;
    }

    private void tellTick() {
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(0, Math.min(0, v.y), 0);
        face(leapDx, leapDz);
        if (stateTicks < TELL_TICKS) {
            return;
        }
        String no = LeapSolver.refusal(takeOffState(), false);
        double along = (hero.getX() - leapStart.getX() - 0.5) * leapDx + (hero.getZ() - leapStart.getZ() - 0.5) * leapDz;
        double up = hero.getY() - leapStart.getY();
        int n = Math.abs(leapTarget.getX() - leapStart.getX() + leapTarget.getZ() - leapStart.getZ());
        LeapSolver.Leap plan = no != null ? null : solveLeap(n, leapTarget.getY() - leapStart.getY(), along, up);
        if (plan == null) {
            lastLeap = new LeapResult(leapPlan, false, false, 0, 0, 0, 0, no != null ? no : "no leap lands there from where it stands");
            setState(State.NONE);
            return;
        }
        leapPlan = plan;
        hero.setDeltaMovement(leapDx * plan.horizontal(), plan.vertical(), leapDz * plan.horizontal());
        // the impulse leaves the ground this tick: vanilla's travel treats it as airborne (horizontal x 0.91), as the solver does
        hero.setOnGround(false);
        hero.hasImpulse = true;
        ownAirtime = true;
        inAir = true;
        peakY = hero.getY();
        leapApex = hero.getY();
        apexChecked = false;
        setState(State.LEAP);
    }

    private LeapSolver.TakeOff takeOffState() {
        Vec3 v = hero.getDeltaMovement();
        BlockState in = hero.getInBlockState();
        return new LeapSolver.TakeOff(Math.sqrt(v.x * v.x + v.z * v.z), hero.blockJumpFactor(), hero.blockSpeedFactor(),
                hero.isInWater(), hero.isInPowderSnow, in.is(Blocks.COBWEB), hero.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                hero.hasEffect(MobEffects.LEVITATION), hero.hasEffect(MobEffects.SLOW_FALLING));
    }

    private void leapTick(boolean ground) {
        leapApex = Math.max(leapApex, hero.getY());
        Vec3 v = hero.getDeltaMovement();
        if (!apexChecked && v.y <= 0 && !ground) {
            apexChecked = true;
            // 3.3: at the apex the rest of the arc is simulated again; an acrobat corrects a miss with its air jump
            double along = (hero.getX() - leapStart.getX() - 0.5) * leapDx + (hero.getZ() - leapStart.getZ() - 0.5) * leapDz;
            double vx = v.x * leapDx + v.z * leapDz;
            int n = Math.abs(leapTarget.getX() - leapStart.getX() + leapTarget.getZ() - leapStart.getZ());
            double up = hero.getY() - leapStart.getY(), rise = leapTarget.getY() - leapStart.getY();
            if (!LeapSolver.stillLands(along, up, vx, v.y, n, rise, slice()) && profile.has(Feature.AIR_JUMP)
                    && MovementRules.airJumpLeft(airJumpsUsed, profile.count(MobilityRules.Count.AIR_JUMPS))) {
                double aj = LeapSolver.strengthFor(profile.airJumpHeight());
                double fix = LeapSolver.airJumpCorrection(along, up, aj, n, rise, limits(n), slice());
                if (!Double.isNaN(fix)) {
                    hero.setDeltaMovement(leapDx * fix, aj, leapDz * fix);
                    hero.resetFallDistance();
                    airJumpsUsed++;
                    oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_SPECIAL);
                }
            }
        }
        if (ground && stateTicks > 1) {
            double along = (hero.getX() - leapStart.getX() - 0.5) * leapDx + (hero.getZ() - leapStart.getZ() - 0.5) * leapDz;
            BlockPos at = hero.blockPosition();
            boolean on = at.getX() == leapTarget.getX() && at.getZ() == leapTarget.getZ()
                    && Math.abs(hero.getY() - leapTarget.getY()) < 0.6;
            lastLeap = new LeapResult(leapPlan, true, on, leapApex - leapStart.getY(), along, hero.getY() - leapStart.getY(),
                    stateTicks, null);
            setState(State.NONE);
            landed(true, leapApex - hero.getY());
        } else if (hero.isInWater() || stateTicks > ACTION_TIMEOUT) {
            lastLeap = new LeapResult(leapPlan, false, false, leapApex - leapStart.getY(), 0, hero.getY() - leapStart.getY(),
                    stateTicks, "it did not land");
            setState(State.NONE);
        }
    }

    private void jumpTick(boolean ground) {
        peakY = Math.max(peakY, hero.getY());
        if (ground && stateTicks > 1 || hero.isInWater() || hero.onClimbable() || stateTicks > ACTION_TIMEOUT) {
            setState(State.NONE);
            if (ground) {
                landed(false, peakY - hero.getY());
            }
        }
    }

    /**
     * An air jump (1.7): in the air, the vertical speed set again to its air-jump height's (1.25 blocks at full power),
     * once per air jump of its ceiling in one airtime. Returns null when it jumps, or why not.
     */
    public String airJump() {
        if (!profile.has(Feature.AIR_JUMP)) {
            return "it has no air jump";
        }
        if (hero.onGround() || hero.isInWater() || onWall() || state == State.DOWNED_FALL || flying() || gliding()) {
            return "not in the air";
        }
        if (!MovementRules.airJumpLeft(airJumpsUsed, profile.count(MobilityRules.Count.AIR_JUMPS))) {
            return "no air jump left before it lands";
        }
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x, LeapSolver.strengthFor(profile.airJumpHeight()), v.z);
        hero.hasImpulse = true;
        hero.resetFallDistance(); // the fall that lands is the one from this jump's apex (the review of A10-A12)
        airJumpsUsed++;
        airborneOwn();
        oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_SPECIAL);
        return null;
    }

    private void airborneOwn() {
        ownAirtime = true;
        if (!inAir) {
            inAir = true;
            peakY = hero.getY();
        }
        if (state == State.NONE) {
            setState(State.JUMP);
        }
    }

    /**
     * A wall kick (1.7, acrobats II-III): in the air against a wall face, an air jump off it pushing 0.4 away from the
     * wall; never off the same face twice in a row (a chimney alternates its walls). Returns null, or why not.
     */
    public String wallKick() {
        if (!profile.has(Feature.WALL_KICK)) {
            return "it has no wall kick";
        }
        if (hero.onGround() || hero.isInWater() || state == State.DOWNED_FALL || flying() || gliding()) {
            return "not in the air";
        }
        Direction wall = touchingWall();
        if (wall == null) {
            return "no wall to kick off";
        }
        if (!MovementRules.wallKickAllowed(lastKickFace, wall.get2DDataValue())) {
            return "not off the same wall twice in a row";
        }
        if (onWall()) {
            letGo();
        }
        lastKickFace = wall.get2DDataValue();
        double push = MovementRules.WALL_KICK_PUSH;
        hero.setDeltaMovement(-wall.getStepX() * push, LeapSolver.strengthFor(profile.airJumpHeight()), -wall.getStepZ() * push);
        hero.hasImpulse = true;
        hero.resetFallDistance();
        airborneOwn();
        oneShot(MovementRules.CLING, HeroActions.LOCO_ONE_SHOT_SPECIAL);
        return null;
    }

    /** The wall face the hero touches (the direction toward it), or null. */
    public Direction touchingWall() {
        AABB box = hero.getBoundingBox().deflate(0, 0.05, 0);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!hero.level().noCollision(hero, box.move(d.getStepX() * 0.06, 0, d.getStepZ() * 0.06))) {
                return d;
            }
        }
        return null;
    }

    /**
     * The mantle (1.7, 1.8: acrobats III, climbers III): onto a ledge in front whose top is within 1 block of its hands,
     * by a short upward push and a forward step, never a position jump. Returns null, or why not.
     */
    public String mantle() {
        if (!profile.has(Feature.MANTLE)) {
            return "it has no mantle";
        }
        if (hero.onGround() && state == State.NONE || hero.isInWater()) {
            return "not in the air or on a wall";
        }
        Direction d = onWall() ? climbFace : touchingWall();
        if (d == null) {
            return "no ledge in front";
        }
        double top = ledgeTop(d);
        double hands = hero.getY() + hero.getBbHeight() * 0.8;
        if (Double.isNaN(top) || top > hands + 1.0 || top < hero.getY() - 0.5) {
            return "no ledge within reach of its hands";
        }
        if (onWall()) {
            hero.setNoGravity(false);
        }
        climbFace = d;
        mantleTop = top;
        double rise = top - hero.getY() + 0.15;
        hero.setDeltaMovement(0, rise > 0 ? LeapSolver.strengthFor(rise) : 0, 0);
        hero.hasImpulse = true;
        setState(State.MANTLE);
        oneShot(MovementRules.CLIMB, HeroActions.LOCO_ONE_SHOT_SPECIAL);
        return null;
    }

    /** The top of the wall in front (absolute y) with room for the hero above it, or NaN. */
    private double ledgeTop(Direction d) {
        Level level = hero.level();
        BlockPos front = BlockPos.containing(hero.getX() + d.getStepX() * 0.8, hero.getY(), hero.getZ() + d.getStepZ() * 0.8);
        for (int k = 0; k <= 3; k++) {
            BlockPos p = front.above(k);
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            if (s.isEmpty()) {
                if (k == 0) {
                    return Double.NaN;
                }
                BlockPos under = p.below();
                double top = under.getY() + level.getBlockState(under).getCollisionShape(level, under).max(Direction.Axis.Y);
                boolean room = level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
                return room ? top : Double.NaN;
            }
        }
        return Double.NaN;
    }

    private void mantleTick(boolean ground) {
        Vec3 v = hero.getDeltaMovement();
        if (hero.getY() >= mantleTop - 0.05 || stateTicks > 4) {
            hero.setDeltaMovement(climbFace.getStepX() * EDGE_PUSH, v.y, climbFace.getStepZ() * EDGE_PUSH);
        } else {
            hero.setDeltaMovement(0, v.y, 0);
        }
        if (ground && hero.getY() >= mantleTop - 0.05 || stateTicks > 20) {
            setState(State.NONE);
            landed(false, 0);
        }
    }

    // ------------------------------------------------------------------ climbing (1.8, 3.4)

    /**
     * Climbs the wall face toward {@code face} (vanilla's ladder physics: 2.35 b/s up), up to its climb height from where
     * it starts; at the top it steps over the edge (a climber III mantles). Returns null, or why not.
     */
    public String climb(Direction face) {
        if (!profile.has(Feature.CLIMB)) {
            return "it does not climb";
        }
        if (now() < gripAfter) {
            return "it cannot grip again yet";
        }
        if (face == null || face.getAxis().isVertical()) {
            return "a wall face is north, south, east or west";
        }
        if (state != State.NONE && !onWall() && state != State.JUMP) {
            return "busy";
        }
        BlockPos front = hero.blockPosition().relative(face);
        Level level = hero.level();
        if (level.getBlockState(front).getCollisionShape(level, front).isEmpty()
                && level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty()) {
            return "no wall there";
        }
        hero.setNoGravity(false);
        climbFace = face;
        if (!onWall()) {
            climbStartY = hero.getY();
            climbColumn = front;
        }
        climbLeftGround = !hero.onGround();
        settleTicks = 0;
        setState(State.CLIMB);
        return null;
    }

    /** Clings still to the wall it climbs (no gravity, no push: vanilla's sneak-cling is a player's only). */
    public String cling() {
        if (!onWall()) {
            return "not on a wall";
        }
        setState(State.CLING);
        hero.setNoGravity(true);
        hero.setDeltaMovement(Vec3.ZERO);
        return null;
    }

    /** Climbs down the wall it is on (vanilla clamps the descent to 3 b/s). */
    public String climbDown() {
        if (!onWall()) {
            return "not on a wall";
        }
        hero.setNoGravity(false);
        setState(State.CLIMB_DOWN);
        descending = false;
        return null;
    }

    /** Lets go of the wall: it falls (safe fall applies) and cannot grip again for 0.5 s. */
    public void letGo() {
        if (!onWall() && state != State.MANTLE) {
            return;
        }
        hero.setNoGravity(false);
        gripAfter = now() + GRIP_AFTER;
        setState(State.NONE);
        inAir = !hero.onGround();
        peakY = hero.getY();
    }

    private void climbTick(boolean ground) {
        if (!profile.has(Feature.CLIMB)) {
            letGo();
            return;
        }
        if (!ground) {
            climbLeftGround = true;
        }
        if (ground && climbLeftGround && hero.getY() > climbStartY + 0.5) {
            // over the edge, on top: it steps in until it stands well on the top, not on its very edge (A11)
            if (settle(climbColumn, climbFace)) {
                return;
            }
            setState(State.NONE);
            inAir = false;
            return;
        }
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(climbFace.getStepX() * CLIMB_PUSH, v.y, climbFace.getStepZ() * CLIMB_PUSH);
        face(climbFace.getStepX(), climbFace.getStepZ());
        hero.resetFallDistance();
        // an acrobat III or a climber III mantles once the face ends at its head (vanilla's horizontal collision stays
        // on until its feet clear the top, so it never mantled and the ladder physics carried it over: the live check of
        // 2026-11-19)
        if (climbLeftGround && profile.has(Feature.MANTLE) && stateTicks > 2 && faceEndsAtHead() && mantle() == null) {
            return;
        }
        int limit = profile.climbHeight();
        if (hero.getY() - climbStartY >= limit - 1e-3 && hero.horizontalCollision) {
            cling(); // its climb height ends here (A11 climbs only what it can top)
        } else if (stateTicks > ACTION_TIMEOUT * 2) {
            letGo();
        }
    }

    private void clingTick() {
        hero.setDeltaMovement(Vec3.ZERO);
        hero.setNoGravity(true);
        hero.resetFallDistance();
        face(climbFace.getStepX(), climbFace.getStepZ());
        if (!touchingFace()) {
            letGo();
        }
    }

    private void climbDownTick(boolean ground) {
        hero.setNoGravity(false);
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(0, v.y, 0);
        face(climbFace.getStepX(), climbFace.getStepZ());
        if (ground) {
            setState(State.NONE);
            inAir = false;
        } else if (descending && !touchingFace() && nearFace(DESCEND_REACH) && stateTicks <= ACTION_TIMEOUT * 2) {
            // a climb down from a ledge: it keeps to the face it backed onto (a nudge, never into it: pressing into a
            // climbable face makes vanilla lift it)
            hero.setDeltaMovement(climbFace.getStepX() * DESCEND_NUDGE, v.y, climbFace.getStepZ() * DESCEND_NUDGE);
        } else if (!touchingFace() && !(descending && nearFace(DESCEND_REACH)) || stateTicks > ACTION_TIMEOUT * 2) {
            letGo();
        }
    }

    /** How far from the face a hero climbing down from a ledge may be and still hold it, and its nudge toward it. */
    static final double DESCEND_REACH = 0.45, DESCEND_NUDGE = 0.03;

    private boolean nearFace(double reach) {
        AABB box = hero.getBoundingBox().deflate(0, 0.05, 0);
        return !hero.level().noCollision(hero, box.move(climbFace.getStepX() * reach, 0, climbFace.getStepZ() * reach));
    }

    /** Whether the face it climbs ends below the top of its head (its upper body is over the top). */
    private boolean faceEndsAtHead() {
        AABB b = hero.getBoundingBox();
        AABB head = new AABB(b.minX, b.maxY - MANTLE_HEAD, b.minZ, b.maxX, b.maxY, b.maxZ)
                .move(climbFace.getStepX() * 0.1, 0, climbFace.getStepZ() * 0.1);
        return hero.level().noCollision(hero, head);
    }

    /** The part of the hero above the top of the face when it mantles (blocks). */
    static final double MANTLE_HEAD = 0.4;

    private boolean touchingFace() {
        AABB box = hero.getBoundingBox().deflate(0, 0.05, 0);
        return !hero.level().noCollision(hero, box.move(climbFace.getStepX() * 0.1, 0, climbFace.getStepZ() * 0.1));
    }

    // ------------------------------------------------------------------ the navigation's links (task A11, 3.2-3.4)

    /**
     * A jump-up link (3.2): onto the ledge in front ({@code face}) whose top is at {@code topY}. The 0.3 s crouch (the
     * tell, held still facing the ledge), then a straight jump with its jump strength while it presses toward the ledge;
     * at the top of each rise under the ledge's top it spends an air jump (an acrobat's), and once its feet are over the
     * top it steps over the edge. Returns null when it starts, or why not.
     */
    public String jumpUp(Direction face, double topY) {
        if (state != State.NONE || !hero.onGround() || hero.isInWater()) {
            return "not standing on the ground";
        }
        if (face == null || face.getAxis().isVertical()) {
            return "a ledge is north, south, east or west";
        }
        if (topY - hero.getY() > profile.reachUp() + 1e-6) {
            return "the ledge is higher than its jumps reach";
        }
        linkFace = face;
        linkTop = topY;
        linkColumn = hero.blockPosition().relative(face);
        settleTicks = 0;
        face(face.getStepX(), face.getStepZ());
        setState(State.JUMP_UP);
        oneShot(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_START);
        return null;
    }

    private void jumpUpTick(boolean ground) {
        if (stateTicks < TELL_TICKS) {
            Vec3 v = hero.getDeltaMovement();
            hero.setDeltaMovement(0, Math.min(0, v.y), 0);
            face(linkFace.getStepX(), linkFace.getStepZ());
            return;
        }
        if (stateTicks == TELL_TICKS) {
            String no = LeapSolver.refusal(takeOffState(), false);
            if (no != null) {
                lastLeap = new LeapResult(null, false, false, 0, 0, 0, 0, no);
                setState(State.NONE);
                return;
            }
            impulse(LeapSolver.attributeFor(profile.jumpHeight()) * hero.blockJumpFactor(), linkFace, EDGE_PUSH);
            oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_START);
            return;
        }
        peakY = Math.max(peakY, hero.getY());
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(linkFace.getStepX() * EDGE_PUSH, v.y, linkFace.getStepZ() * EDGE_PUSH);
        if (hero.getY() < linkTop - 0.05 && v.y <= 0 && !ground) {
            spendAirJump();
        }
        if (ground && stateTicks > TELL_TICKS + 1) {
            if (hero.getY() >= linkTop - 0.05 && settle(linkColumn, linkFace)) {
                return; // on the ledge: a step in from its edge
            }
            setState(State.NONE);
            landed(false, peakY - hero.getY());
        } else if (hero.isInWater() || stateTicks > ACTION_TIMEOUT) {
            setState(State.NONE);
        }
    }

    /** How far the hero's centre is inside {@code column} along {@code d} (from the column's near side). */
    private double depthInto(BlockPos column, Direction d) {
        return switch (d) {
            case EAST -> hero.getX() - column.getX();
            case WEST -> column.getX() + 1 - hero.getX();
            case SOUTH -> hero.getZ() - column.getZ();
            case NORTH -> column.getZ() + 1 - hero.getZ();
            default -> 1;
        };
    }

    /**
     * After a climb or a jump-up that ended on the very edge of the top: a short step in (at most 10 ticks) until its
     * centre is 0.3 inside the column it climbed onto, so it stands on the cell its link was for. True while it steps.
     */
    private boolean settle(BlockPos column, Direction d) {
        if (column == null || d == null || settleTicks >= 10) {
            return false;
        }
        double in = depthInto(column, d);
        if (in >= 0.3) {
            return false;
        }
        settleTicks++;
        double push = Math.min(0.1, 0.35 - in);
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(d.getStepX() * push, v.y, d.getStepZ() * push);
        return true;
    }

    /** An impulse that leaves the ground this tick (airborne at once, as the solver's arcs are). */
    private void impulse(double vertical, Direction toward, double push) {
        hero.setDeltaMovement(toward == null ? 0 : toward.getStepX() * push, vertical,
                toward == null ? 0 : toward.getStepZ() * push);
        hero.setOnGround(false);
        hero.hasImpulse = true;
        ownAirtime = true;
        inAir = true;
        peakY = hero.getY();
    }

    /** An air jump inside a link (its ceiling's count per airtime); false when none is left. */
    private boolean spendAirJump() {
        if (!profile.has(Feature.AIR_JUMP)
                || !MovementRules.airJumpLeft(airJumpsUsed, profile.count(MobilityRules.Count.AIR_JUMPS))) {
            return false;
        }
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x, LeapSolver.strengthFor(profile.airJumpHeight()), v.z);
        hero.hasImpulse = true;
        hero.resetFallDistance();
        airJumpsUsed++;
        oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_SPECIAL);
        return true;
    }

    /**
     * A chimney link (3.2, acrobats II-III): up the 1-2 wide shaft it stands in to the standable cell {@code exit}
     * beside its top. The 0.3 s crouch, a jump, then at the top of each rise a wall kick off a side of the shaft (never
     * the same face twice in a row) or, with none, an air jump; once its feet are over the exit's floor it steps out.
     * Returns null when it starts, or why not.
     */
    public String chimney(BlockPos exit) {
        if (!profile.has(Feature.CHIMNEY)) {
            return "it climbs no chimneys";
        }
        if (state != State.NONE || !hero.onGround() || hero.isInWater()) {
            return "not standing on the ground";
        }
        linkExit = exit.immutable();
        setState(State.CHIMNEY);
        oneShot(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_START);
        return null;
    }

    private void chimneyTick(boolean ground) {
        if (stateTicks < TELL_TICKS) {
            Vec3 v = hero.getDeltaMovement();
            hero.setDeltaMovement(0, Math.min(0, v.y), 0);
            return;
        }
        if (stateTicks == TELL_TICKS) {
            String no = LeapSolver.refusal(takeOffState(), false);
            if (no != null) {
                setState(State.NONE);
                return;
            }
            impulse(LeapSolver.attributeFor(profile.jumpHeight()) * hero.blockJumpFactor(), null, 0);
            oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_START);
            return;
        }
        peakY = Math.max(peakY, hero.getY());
        Vec3 v = hero.getDeltaMovement();
        if (hero.getY() >= linkExit.getY() - 0.05) {
            double dx = linkExit.getX() + 0.5 - hero.getX(), dz = linkExit.getZ() + 0.5 - hero.getZ();
            double len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            hero.setDeltaMovement(dx / len * EDGE_PUSH, v.y, dz / len * EDGE_PUSH);
            face(dx, dz);
        } else if (v.y <= 0.05 && !ground) {
            if (wallKick() != null) {
                spendAirJump();
            }
        } else if (!ground && touchingWall() == null) {
            // a 1-2 wide shaft is wider than the hero: it leans toward a side so the next apex finds a wall to kick off
            // (the side opposite its last kick; found by the live check of 2026-11-19, where it only air-jumped, fell
            // back and the trip failed into DigOut and the stuck hop)
            Direction w = chimneyWall();
            if (w != null) {
                hero.setDeltaMovement(w.getStepX() * CHIMNEY_LEAN, v.y, w.getStepZ() * CHIMNEY_LEAN);
            }
        }
        if (ground && stateTicks > TELL_TICKS + 1) {
            setState(State.NONE);
            landed(false, peakY - hero.getY());
        } else if (hero.isInWater() || stateTicks > ACTION_TIMEOUT) {
            setState(State.NONE);
        }
    }

    /** How fast a hero in a chimney leans toward the side it kicks off next (blocks a tick). */
    static final double CHIMNEY_LEAN = 0.15;

    /**
     * The side of the shaft a chimney kicks off next: a solid neighbour at its chest that is not the face of its last
     * kick, a side of the narrow way (both sides solid) first. Null when the shaft has none.
     */
    private Direction chimneyWall() {
        Level level = hero.level();
        BlockPos at = BlockPos.containing(hero.getX(), hero.getY() + 1.0, hero.getZ());
        Direction best = null;
        boolean bestNarrow = false;
        for (Direction d : HORIZONTAL) {
            if (d.get2DDataValue() == lastKickFace) {
                continue;
            }
            BlockPos n = at.relative(d);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
                continue;
            }
            BlockPos o = at.relative(d.getOpposite());
            boolean narrow = !level.getBlockState(o).getCollisionShape(level, o).isEmpty();
            if (best == null || narrow && !bestNarrow) {
                best = d;
                bestNarrow = narrow;
            }
        }
        return best;
    }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * A climb-down link (A11): from the ledge it stands on, it backs over the edge toward {@code over} and climbs down
     * the face below the edge at vanilla's ladder rate (at most 3 b/s), to the ground at its foot. Returns null when it
     * starts, or why not.
     */
    public String descend(Direction over) {
        if (!profile.has(Feature.CLIMB)) {
            return "it does not climb";
        }
        if (now() < gripAfter) {
            return "it cannot grip again yet";
        }
        if (state != State.NONE || !hero.onGround()) {
            return "not standing on the ground";
        }
        if (over == null || over.getAxis().isVertical()) {
            return "an edge is north, south, east or west";
        }
        linkFace = over;
        climbFace = over.getOpposite();
        climbStartY = hero.getY();
        setState(State.DESCEND);
        return null;
    }

    private void descendTick(boolean ground) {
        Vec3 v = hero.getDeltaMovement();
        face(climbFace.getStepX(), climbFace.getStepZ());
        if (ground && hero.getY() > climbStartY - 0.5) {
            // still on the ledge: it backs over the edge
            hero.setDeltaMovement(linkFace.getStepX() * CLIMB_PUSH, v.y, linkFace.getStepZ() * CLIMB_PUSH);
            if (stateTicks > 40) {
                setState(State.NONE); // it could not get over the edge
            }
            return;
        }
        // over the edge: it grips the face below it and climbs down
        hero.setDeltaMovement(0, Math.max(v.y, -0.15), 0);
        boolean owned = navOwned;
        setState(State.CLIMB_DOWN);
        descending = true;
        navOwned = owned;
        hero.resetFallDistance();
    }

    // ------------------------------------------------------------------ gliding (1.9)

    /**
     * Opens its glide heading along {@code heading} (horizontal): a falling hero sinks no faster than its sink rate and
     * keeps its forward speed, takes no fall damage, for at most its glide time (then it sinks at 6 b/s). It ends on
     * landing, any projectile hit, touching water. Returns null, or why not.
     */
    public String glide(Vec3 heading) {
        if (!profile.glides()) {
            return "it does not glide";
        }
        if (hero.onGround() || hero.isInWater() || state == State.DOWNED_FALL || state == State.DOWNED) {
            return "not in the air";
        }
        double h = heading == null ? 0 : Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        if (h < 1e-6) {
            Vec3 look = hero.getLookAngle();
            heading = new Vec3(look.x, 0, look.z);
            h = Math.max(1e-6, Math.sqrt(look.x * look.x + look.z * look.z));
        }
        if (flying()) {
            hero.setNoGravity(false);
        }
        glideX = heading.x / h;
        glideZ = heading.z / h;
        gustsUsed = 0;
        glideTicks = 0;
        inAir = true;
        setState(State.GLIDE);
        oneShot(MovementRules.GLIDE, HeroActions.LOCO_ONE_SHOT_START);
        return null;
    }

    /** A gust (1.9): one upward push of 1.5 blocks while gliding, its ceiling's count per glide. */
    public String gust() {
        if (state != State.GLIDE) {
            return "not gliding";
        }
        if (gustsUsed >= profile.count(MobilityRules.Count.GUSTS)) {
            return "no gust left in this glide";
        }
        gustsUsed++;
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x, LeapSolver.strengthFor(MobilityRules.GUST_LIFT), v.z);
        hero.hasImpulse = true;
        oneShot(MovementRules.GLIDE, HeroActions.LOCO_ONE_SHOT_SPECIAL);
        return null;
    }

    private void glideTick(boolean ground) {
        if (ground) {
            setState(State.NONE);
            inAir = false;
            ownAirtime = false;
            noGlideUntilLanding = false;
            oneShot(MovementRules.GLIDE, HeroActions.LOCO_ONE_SHOT_END);
            if (profile.flies() && stamina.exhausted()) {
                windedPending = true; // the exhausted glide down ends in the winded rest (2.2)
            }
            return;
        }
        if (hero.isInWater()) {
            endGlide();
            return;
        }
        glideTicks++;
        double seconds = profile.raw(Stat.GLIDE_SECONDS);
        boolean timedOut = !Double.isNaN(seconds) && glideTicks > seconds * BossRules.TPS;
        double sink = profile.raw(Stat.GLIDE_SINK), fwd = MovementRules.glideForward(profile.raw(Stat.GLIDE_FORWARD), timedOut);
        Vec3 v = hero.getDeltaMovement();
        double vy = MovementRules.glideVertical(v.y, Double.isNaN(sink) ? MobilityRules.GLIDE_TIMED_OUT_SINK : sink, timedOut);
        // vanilla dragged last tick's motion by 0.91 after it moved: from that motion toward the glide's
        double mx = v.x / LeapSolver.HORIZONTAL_DRAG, mz = v.z / LeapSolver.HORIZONTAL_DRAG;
        double vx = mx + (glideX * fwd - mx) * FLIGHT_BLEND, vz = mz + (glideZ * fwd - mz) * FLIGHT_BLEND;
        if (timedOut) {
            vx = v.x;
            vz = v.z;
        }
        hero.setDeltaMovement(vx, vy, vz);
        hero.resetFallDistance();
        face(glideX, glideZ);
    }

    /** Ends a glide in the air (a projectile hit, water): it falls, its safe fall applies, no glide until it lands. */
    private void endGlide() {
        setState(State.NONE);
        noGlideUntilLanding = true;
        inAir = !hero.onGround();
        peakY = hero.getY();
    }

    // ------------------------------------------------------------------ flight (2.1-2.3)

    /**
     * Whether it could take off once it stands still somewhere open (A12: a raid's choice of its way in): it flies, its
     * stamina is at its threshold and it is not exhausted, and it was not downed in the last 12 s. Headroom, the ground
     * time and the weather are checked at the take-off itself.
     */
    public boolean mayTakeOffSoon() {
        return profile.flies() && !stamina.exhausted() && stamina.share() + 1e-9 >= profile.takeOffShare()
                && airPoise.mayTakeOff(now());
    }

    /** Why it may not take off now (null: it may), by {@link MobilityRules#takeOffRefusal} and its downing. */
    public String takeOffRefusal() {
        if (state != State.NONE && state != State.WINDED) { // (a winded flyer is exhausted: the refusal below says so)
            return "busy";
        }
        if (!hero.onGround() || hero.isInWater()) {
            return "not standing on the ground";
        }
        long now = now();
        MobilityRules.Situation s = situation();
        int phase = hero.combat().boss().phase();
        boolean groundTime = airBudget == null || airBudget.groundTimeMet(now, s, phase);
        return MobilityRules.takeOffRefusal(profile, new MobilityRules.TakeOffState(stamina.share(), stamina.exhausted(),
                headroom(), hero.level().isThundering(), s, !airPoise.mayTakeOff(now), groundTime, false));
    }

    /**
     * Takes off (2.2): the 0.6 s tell (a crouch, the carrier opens; {@code cues.takeoff} and the hero's own take-off
     * sound), one push of 1.5 blocks, then powered flight hovering where it is. Returns null, or why not.
     */
    public String takeOff() {
        String no = takeOffRefusal();
        if (no != null) {
            return no;
        }
        flyTarget = null;
        hovering = true;
        setState(State.TAKEOFF);
        int mode = rides() ? MovementRules.RIDE : MovementRules.FLY;
        oneShot(mode, HeroActions.LOCO_ONE_SHOT_START);
        HeroSounds.takeoff(hero);
        return null;
    }

    /** Flies toward {@code target} (absolute; its feet), within the altitude ceiling; it hovers there. */
    public String flyTo(Vec3 target) {
        if (state != State.FLY && state != State.RISE && state != State.TAKEOFF) {
            return "not flying";
        }
        flyTarget = target;
        hovering = false;
        return null;
    }

    /** Stops where it is and hovers (2.0 a second of stamina). */
    public String hover() {
        if (state != State.FLY) {
            return "not flying";
        }
        flyTarget = null;
        hovering = true;
        return null;
    }

    /** Lands (2.2): a powered descent straight down, then the 0.4 s landing; no fall damage. */
    public String land() {
        if (state != State.FLY && state != State.RISE) {
            return "not flying";
        }
        hero.setNoGravity(true);
        setState(State.LAND);
        return null;
    }

    /** Free blocks above its feet over its footprint, up to the 4 a take-off needs (3.5). */
    public int headroom() {
        AABB b = hero.getBoundingBox();
        for (int k = MobilityRules.TAKE_OFF_HEADROOM; k >= 1; k--) {
            AABB col = new AABB(b.minX, b.minY + 0.01, b.minZ, b.maxX, b.minY + k, b.maxZ);
            if (hero.level().noCollision(hero, col)) {
                return k;
            }
        }
        return 0;
    }

    private void takeOffTick() {
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(0, Math.min(0, v.y), 0);
        if (stateTicks >= clipTicks("move.takeoff")) {
            hero.setDeltaMovement(0, LeapSolver.strengthFor(MobilityRules.TAKE_OFF_PUSH), 0);
            hero.setOnGround(false);
            hero.hasImpulse = true;
            inAir = true;
            setState(State.RISE);
        }
    }

    private void riseTick() {
        hero.resetFallDistance();
        Vec3 v = hero.getDeltaMovement();
        if (v.y <= 0.02 || hero.verticalCollision && stateTicks > 1) {
            hero.setNoGravity(true);
            hero.setDeltaMovement(v.x, 0, v.z);
            setState(State.FLY);
        }
    }

    private void flyTick(long now) {
        hero.setNoGravity(true);
        hero.resetFallDistance();
        MobilityRules.Situation s = situation();
        if (stamina.empty()) {
            // 2.2: at 0 it can only glide down, exhausted until 50%
            Vec3 v = hero.getDeltaMovement();
            hero.setNoGravity(false);
            if (glide(new Vec3(v.x, 0, v.z)) != null) {
                setState(State.NONE);
            }
            return;
        }
        if (stamina.low() && s != MobilityRules.Situation.ESCAPE
                || s == MobilityRules.Situation.TRAVEL && hero.level().isThundering()
                || airBudget != null && airBudget.mustLand(now, s, FCConfig.MOBILITY_BOSS_AIR_SECONDS.get(), authoredMaxAir())) {
            land();
            return;
        }
        double ceiling = ceilingY(s);
        Vec3 pos = hero.position();
        Vec3 t = flyTarget == null ? null : flyTarget;
        double cruise = profile.raw(Stat.CRUISE_SPEED), climb = profile.raw(Stat.CLIMB_RATE)
                * MovementRules.climbRateFactor(slowness());
        if (t == null || MovementRules.arrived(t.x - pos.x, t.y - pos.y, t.z - pos.z)) {
            hovering = true;
            MovementRules.flightVelocity(0, Math.min(0, ceiling - pos.y), 0, cruise, climb, ceiling - pos.y, wanted);
        } else {
            hovering = false;
            MovementRules.flightVelocity(t.x - pos.x, t.y - pos.y, t.z - pos.z, cruise, climb, ceiling - pos.y, wanted);
            if (hero.horizontalCollision && wanted[1] < climb * MovementRules.TICK) {
                wanted[1] = Math.min(climb * MovementRules.TICK, ceiling - pos.y); // over what it ran into
            }
            face(wanted[0], wanted[2]);
        }
        // vanilla dragged last tick's motion after it moved (x 0.91, and x 0.98 up and down with no gravity): from that
        // motion toward the wanted one, so a knock-back still carries the flyer for a few ticks
        Vec3 v = hero.getDeltaMovement();
        double mx = v.x / LeapSolver.HORIZONTAL_DRAG, my = v.y / LeapSolver.VERTICAL_DRAG, mz = v.z / LeapSolver.HORIZONTAL_DRAG;
        hero.setDeltaMovement(mx + (wanted[0] - mx) * FLIGHT_BLEND, my + (wanted[1] - my) * FLIGHT_BLEND,
                mz + (wanted[2] - mz) * FLIGHT_BLEND);
    }

    private double authoredMaxAir() {
        HeroDef d = hero.definition();
        HeroDef.Mobility m = d == null || d.v2() == null ? null : d.v2().mobility();
        return m == null || m.aerial() == null ? 0 : m.aerial().maxAir();
    }

    /** The altitude ceiling of 2.3 now (absolute y): a fight's counts from the highest engaged enemy on a block. */
    private double ceilingY(MobilityRules.Situation s) {
        if (--groundCheck <= 0 || Double.isNaN(groundYCache)) {
            groundCheck = 4;
            double drop = dropBelow(64);
            groundYCache = Double.isInfinite(drop) ? hero.getY() - 64 : hero.getY() - drop;
        }
        if (s == MobilityRules.Situation.FIGHT) {
            double enemy = hero.combat().boss().highestEngagedFeet();
            return Math.max(groundYCache, Double.isNaN(enemy) ? groundYCache : enemy) + BossRules.FIGHT_CEILING;
        }
        if (s == MobilityRules.Situation.RAID) {
            return groundYCache + MobilityRules.RAID_CEILING;
        }
        return groundYCache + MobilityRules.travelAltitude(FCConfig.MOBILITY_TRAVEL_ALTITUDE.get());
    }

    private void landTick(boolean ground) {
        hero.resetFallDistance();
        if (ground || hero.isInWater()) {
            stopPowered();
            setState(State.LANDING);
            landedAt = now();
            inAir = false;
            ownAirtime = false;
            oneShot(rides() ? MovementRules.RIDE : MovementRules.FLY, HeroActions.LOCO_ONE_SHOT_END);
            HeroSounds.cue(hero, SoundIndex.LANDING); // a flight's landing is a heavy one (SOUND.md 7)
            return;
        }
        hero.setNoGravity(true);
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x * 0.5, -MovementRules.LANDING_DESCENT * MovementRules.TICK, v.z * 0.5);
        if (stateTicks > ACTION_TIMEOUT * 3) {
            stopPowered();
            setState(State.NONE); // it could not get down: it falls, as any NPC
        }
    }

    private void landingTick() {
        hero.setDeltaMovement(0, Math.min(0, hero.getDeltaMovement().y), 0);
        if (stateTicks >= clipTicks("move.fly_land")) {
            setState(State.NONE);
        }
    }

    private void stopPowered() {
        hero.setNoGravity(false);
        hovering = false;
        flyTarget = null;
    }

    /**
     * The drop under its whole footprint: the shallowest of the drops under its box's four corners, so a hero whose middle
     * is over a pit while its box still rests on the edge does not count the pit (the live check of 2026-11-19: a glide
     * opened for a tick on the lip of a ditch and landed at once).
     */
    public double footprintDrop(int max) {
        AABB b = hero.getBoundingBox();
        double e = 0.01;
        double d = Math.min(dropBelow(max, b.minX + e, b.minZ + e), dropBelow(max, b.maxX - e, b.minZ + e));
        return Math.min(d, Math.min(dropBelow(max, b.minX + e, b.maxZ - e), dropBelow(max, b.maxX - e, b.maxZ - e)));
    }

    /** The drop under its feet to the first block that holds it, within {@code max} blocks (infinity: none). */
    public double dropBelow(int max) {
        return dropBelow(max, hero.getX(), hero.getZ());
    }

    private double dropBelow(int max, double x, double z) {
        Level level = hero.level();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(hero.getY() + 0.01), Mth.floor(z));
        for (int i = 0; i <= max; i++) {
            if (!level.isLoaded(p)) {
                return Double.POSITIVE_INFINITY;
            }
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            if (!s.isEmpty()) {
                double top = p.getY() + s.max(Direction.Axis.Y);
                if (top <= hero.getY() + 0.01) {
                    return hero.getY() - top;
                }
            }
            p.move(Direction.DOWN);
        }
        return Double.POSITIVE_INFINITY;
    }

    // ------------------------------------------------------------------ the dive and the touch-and-go (A12, 7.3, 7.4)

    private Vec3 diveMarker;
    private boolean diveImpact;
    private final double[] diveV = new double[3];

    /**
     * A {@code dive} (7.3): from the air (a flight, a glide, its own jump's apex), pushed along a straight line to
     * {@code marker} at {@link FightMobility#DIVE_SPEED}, stopped by blocks, with no fall damage; it ends on the ground,
     * against a block, at the marker or after 2 s. Returns null, or why not (not in the air).
     */
    public String dive(Vec3 marker) {
        if (hero.onGround() || hero.isInWater() || state == State.DOWNED_FALL || state == State.DOWNED
                || state == State.TAKEOFF || state == State.LANDING) {
            return "not in the air";
        }
        if (flying()) {
            stopPowered();
        }
        hero.setNoGravity(true);
        diveMarker = marker;
        diveImpact = false;
        inAir = true;
        ownAirtime = true;
        navOwned = false;
        setState(State.DIVE);
        return null;
    }

    private void diveTick(boolean ground) {
        hero.resetFallDistance();
        Vec3 pos = hero.position();
        double dx = diveMarker.x - pos.x, dy = diveMarker.y - pos.y, dz = diveMarker.z - pos.z;
        boolean there = dx * dx + dy * dy + dz * dz <= FightMobility.DIVE_ARRIVED * FightMobility.DIVE_ARRIVED;
        boolean blocked = stateTicks > 1 && (hero.horizontalCollision || hero.verticalCollision);
        if (ground && stateTicks > 1 || there || blocked || hero.isInWater() || stateTicks > FightMobility.DIVE_TIMEOUT) {
            hero.setNoGravity(false);
            hero.setDeltaMovement(0, Math.min(0, hero.getDeltaMovement().y), 0);
            diveImpact = true;
            setState(State.NONE);
            if (ground || hero.onGround()) {
                landed(true, 0);
            } else {
                diveLanding = true; // it comes down the last bit under gravity: noneTick lands it as a dive
                inAir = true;
                peakY = hero.getY();
            }
            return;
        }
        FightMobility.diveVelocity(dx, dy, dz, diveV);
        hero.setDeltaMovement(diveV[0], diveV[1], diveV[2]);
        hero.hasImpulse = true;
        face(diveV[0], diveV[2]);
    }

    /** Whether a dive came down since the last ask (the move runner's impact; cleared by asking). */
    public boolean takeDiveImpact() {
        boolean i = diveImpact;
        diveImpact = false;
        return i;
    }

    /** Whether it dives now. */
    public boolean diving() {
        return state == State.DIVE;
    }

    /**
     * The touch-and-go between two dives of one aerial window (7.4): after its 0.5 s on the ground, one push up into
     * powered flight with no take-off tell (the window's take-off was the tell). Returns null, or why not.
     */
    public String relift() {
        if (state != State.NONE || !profile.flies() || !hero.onGround() || hero.isInWater()) {
            return "not standing on the ground";
        }
        if (stamina.exhausted() || !airPoise.mayTakeOff(now()) || headroom() < MobilityRules.TAKE_OFF_HEADROOM) {
            return "cannot take off";
        }
        flyTarget = null;
        hovering = true;
        hero.setDeltaMovement(0, LeapSolver.strengthFor(MobilityRules.TAKE_OFF_PUSH), 0);
        hero.setOnGround(false);
        hero.hasImpulse = true;
        inAir = true;
        setState(State.RISE);
        return null;
    }

    // ------------------------------------------------------------------ stamina and the air budget (2.1, 5.1.4)

    private void staminaTick(long now) {
        boolean powered = flying();
        if (profile.flies() || stamina.max() > 0) {
            if (powered) {
                if (hero.tickCount % 20 == 0) {
                    loaded = backpackHalfFull();
                }
                MobilityRules.Situation s = situation();
                MobilityRules.Activity a = state == State.RISE ? MobilityRules.Activity.POWERED
                        : MovementRules.flightActivity(hovering, horizontalSpeed() > 0.02 || state == State.LAND, s);
                stamina.air(MovementRules.TICK, MobilityRules.drain(a, loaded, s));
            } else if (state == State.GLIDE) {
                stamina.air(MovementRules.TICK, MobilityRules.DRAIN_GLIDE);
            } else if (hero.onGround() && state != State.TAKEOFF) {
                stamina.ground(MovementRules.TICK);
            }
        }
        if (profile.flies()) {
            if (airBudget == null) {
                airBudget = new MobilityRules.AirBudget();
            }
            airBudget.tick(now, powered || state == State.GLIDE || state == State.DIVE);
        }
    }

    private boolean backpackHalfFull() {
        var inv = hero.inventory();
        int used = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used * 2 > inv.getContainerSize();
    }

    /** The air budget of the fight rules (null for a hero that does not fly). */
    public MobilityRules.AirBudget airBudget() {
        return airBudget;
    }

    // ------------------------------------------------------------------ bursts and landings (1.4, 1.5, 1.6)

    private void burstTick(long now) {
        boolean sprint = profile.has(Feature.SPRINT_BURST), charge = profile.has(Feature.TRAVERSAL_CHARGE);
        if (!sprint && !charge) {
            return;
        }
        MobilityRules.Burst b = MobilityRules.burst(profile, charge ? Feature.TRAVERSAL_CHARGE : Feature.SPRINT_BURST);
        boolean travel = travelling() && hero.onGround();
        if (bursting && (!travel || !burst.active(now) || hero.tickCount % 5 == 0 && !straightAhead(2))) {
            bursting = false;
            burst.stop(now);
            setBurst(0);
            if (charge && travel) {
                oneShot(MovementRules.CHARGE_RUN, HeroActions.LOCO_ONE_SHOT_END); // the skid
            }
        } else if (!bursting && travel && b != null && burst.ready(now, b) && straightAhead(MobilityRules.BURST_STRAIGHT)) {
            if (burst.start(now, b)) {
                bursting = true;
                setBurst(b.speedBonus());
            }
        }
    }

    /** Whether its path runs straight and level for {@code blocks} more nodes with nothing alive 2 blocks ahead (1.4). */
    private boolean straightAhead(int blocks) {
        Path p = hero.getNavigation().getPath();
        if (p == null || p.isDone()) {
            return false;
        }
        int i = p.getNextNodeIndex();
        if (i < 1 || i + blocks > p.getNodeCount()) {
            return false;
        }
        Node a = p.getNode(i - 1), b = p.getNode(i);
        int dx = b.x - a.x, dz = b.z - a.z;
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            return false;
        }
        for (int k = i + 1; k < i + blocks; k++) {
            Node n = p.getNode(k), m = p.getNode(k - 1);
            if (n.x - m.x != dx || n.z - m.z != dz || n.y != m.y) {
                return false;
            }
        }
        AABB ahead = hero.getBoundingBox().move(dx * 1.5, 0, dz * 1.5).inflate(0.5, 0, 0.5);
        return hero.level().getEntitiesOfClass(LivingEntity.class, ahead, e -> e != hero && e.isAlive()).isEmpty();
    }

    private void setBurst(double bonus) {
        AttributeInstance speed = hero.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(BURST);
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(BURST, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Whether a burst runs now (the runner's sprint or the charger's traversal charge). */
    public boolean bursting() {
        return bursting;
    }

    private void landingSlowTick() {
        boolean slow = hero.tickCount < landingSlowUntil;
        if (slow == landingSlowOn) {
            return;
        }
        landingSlowOn = slow;
        AttributeInstance speed = hero.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(LANDING_SLOW);
            if (slow) {
                speed.addTransientModifier(new AttributeModifier(LANDING_SLOW, MovementRules.LANDING_SLOW_FACTOR - 1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    // ------------------------------------------------------------------ hits, air poise and the downing (2.4, 5.1.7-8, 5.3)

    /**
     * A hit from anyone (before the boss path): a climber lets go (1.8: any hit), a glider stops gliding when a
     * projectile hits it (1.9), and a melee blow on a low flyer adds air poise (25, a critical 40; 5.3).
     */
    public void struck(DamageSource src) {
        Entity direct = src.getDirectEntity(), by = src.getEntity();
        if (direct == null && by == null) {
            return;
        }
        if (onWall() || state == State.MANTLE) {
            letGo();
        }
        if (direct instanceof LivingEntity a && direct == by && a != hero && airborneForPoise()
                && MovementRules.lowFlyer(hero.getY(), a.getY())) {
            airHit(critical(a) ? MovementRules.AirHit.MELEE_CRIT : MovementRules.AirHit.MELEE);
        }
    }

    /** A falling blow (vanilla's critical hit, the same rule for a player and an NPC). */
    private static boolean critical(LivingEntity a) {
        return a.fallDistance > 0 && !a.onGround() && !a.onClimbable() && !a.isInWater() && !a.isPassenger();
    }

    /** A knock-back on it (1.8: one of 0.5 or more knocks a climber off), whoever dealt it. */
    public void knocked(double strength) {
        if (strength >= 0.5 && (onWall() || state == State.MANTLE)) {
            letGo();
        }
    }

    /** A projectile hit it (5.3): its air poise by kind, whoever shot it; any projectile ends a glide. */
    public void projectileHit(Entity p) {
        MovementRules.AirHit kind = kind(p);
        if (state == State.GLIDE) {
            endGlide();
        }
        if (kind != null) {
            airHit(kind);
        }
    }

    /** The air poise a projectile adds (null: none). */
    static MovementRules.AirHit kind(Entity p) {
        if (p instanceof ThrownTrident) {
            return MovementRules.AirHit.TRIDENT;
        }
        if (p instanceof AbstractArrow a) {
            if (a.shotFromCrossbow()) {
                return a.pickup == AbstractArrow.Pickup.CREATIVE_ONLY ? MovementRules.AirHit.MULTISHOT_BOLT
                        : MovementRules.AirHit.BOLT;
            }
            return MovementRules.AirHit.ARROW;
        }
        if (p instanceof AbstractWindCharge) {
            return MovementRules.AirHit.WIND_CHARGE;
        }
        if (p instanceof Snowball || p instanceof ThrownEgg) {
            return MovementRules.AirHit.SNOWBALL;
        }
        if (p instanceof FishingHook) {
            return MovementRules.AirHit.FISHING_HOOK;
        }
        if (p instanceof com.factionclash.hero.entity.HeroProjectile) {
            return MovementRules.AirHit.ARROW;
        }
        return null;
    }

    /** Air poise from a hit while airborne; a full bar downs it. */
    public void airHit(MovementRules.AirHit kind) {
        if (!airborneForPoise()) {
            return;
        }
        if (airPoise.hit(kind.poise, now())) {
            down(Cause.AIR_POISE);
        }
    }

    /** Lightning struck it: airborne, it is downed at once (2.4). */
    public void lightning() {
        if (airborneForPoise()) {
            airPoise.instant(now());
            down(Cause.LIGHTNING);
        }
    }

    /**
     * Downed (2.4): its flight, glide, leap or climb ends, the carrier folds, it falls with no control (its horizontal
     * speed cut to a fifth), takes fall damage above its safe fall distance (one hit for the per-hit cap), lies 2 s
     * ({@code move.downed}, +20% damage taken), cannot take off for 12 s and gains 30 poise; {@code cues.downed}.
     * Returns whether it was downed (only an airborne hero is).
     */
    public boolean down(Cause cause) {
        if (cause != Cause.OPERATOR && !airborneForPoise() || state == State.DOWNED_FALL || state == State.DOWNED) {
            return false;
        }
        long now = now();
        if (now - airPoise.downedAt() > 1) {
            airPoise.instant(now);
        }
        hero.setNoGravity(false);
        hovering = false;
        flyTarget = null;
        Vec3 v = hero.getDeltaMovement();
        hero.setDeltaMovement(v.x * BossRules.DOWNED_SPEED_FACTOR, Math.min(0, v.y), v.z * BossRules.DOWNED_SPEED_FACTOR);
        setState(State.DOWNED_FALL);
        navOwned = false; // a downing is no link or trip of its navigation: the hold goal holds it
        loco.cut();
        inAir = true;
        peakY = hero.getY();
        downs++;
        HeroSounds.cue(hero, SoundIndex.DOWNED);
        hero.combat().downed();
        hero.combat().poiseDamage(BossRules.DOWNING_POISE); // (a stagger's own bar is in its stagger: ignored then)
        if (HeroCombatLog.ON) {
            FactionClash.LOGGER.info("[Heroes] combat {} downed ({})", hero.heroId(), cause);
        }
        return true;
    }

    private void downedFallTick(boolean ground) {
        hero.setNoGravity(false);
        if (ground || hero.isInWater() || hero.onClimbable()) {
            inAir = false;
            ownAirtime = false;
            setState(State.DOWNED);
            oneShot(MovementRules.DOWNED, HeroActions.LOCO_ONE_SHOT_START);
        } else if (stateTicks > ACTION_TIMEOUT * 3) {
            setState(State.NONE);
        }
    }

    private void downedTick() {
        hero.setDeltaMovement(0, Math.min(0, hero.getDeltaMovement().y), 0);
        if (stateTicks >= BossRules.DOWNED_TICKS) {
            setState(State.NONE);
        }
    }

    /** Death, a revival or a release: every action ends and the hero stands under gravity again. */
    public void reset() {
        if (hero.isNoGravity() && (flying() || clinging() || state == State.DIVE)) {
            hero.setNoGravity(false);
        }
        setState(State.NONE);
        hovering = false;
        flyTarget = null;
        bursting = false;
        setBurst(0);
    }

    // ------------------------------------------------------------------ operator and tests

    /** Sets the stamina to a share of its maximum (an operator, a test). */
    public void setStaminaShare(double share) {
        stamina.set(share * stamina.max(), stamina.exhausted());
    }

    // ------------------------------------------------------------------ saving (MOBILITY_AND_WORK 16)

    /**
     * {@code FcHero.mobility}: stamina, exhaustion, air poise and its last downing, and the state it was in: a powered
     * flight (its take-off push and its landing included, and a flyer's dive) as {@code fly}, a hero on a wall (climbing
     * up or down, or clinging) as {@code wall} with its face.
     */
    public void save(CompoundTag tag) {
        if (!profile.any() && stamina.max() <= 0 && airPoise.value() <= 0) {
            return;
        }
        CompoundTag m = new CompoundTag();
        m.putDouble("stamina", stamina.value());
        m.putBoolean("exhausted", stamina.exhausted());
        m.putDouble("airPoise", airPoise.value());
        long since = now() - airPoise.downedAt();
        if (since >= 0 && since < BossRules.NO_TAKEOFF_TICKS) {
            m.putInt("downedAgo", (int) since);
        }
        if (flying() || state == State.DIVE && profile.flies()) {
            m.putString("state", "fly");
        } else if (onWall() && climbFace != null && !hero.onGround()) {
            m.putString("state", "wall");
            m.putString("face", climbFace.getSerializedName());
        }
        tag.put("mobility", m);
    }

    /**
     * Loads it back: the stamina once the profile is known (on the hero's first tick); a flight comes down where it is (a
     * powered landing: nothing drives a flight after a load, and a hover nobody steers would hang there until its stamina
     * ran low), a hero on a wall climbs down the same face (nothing drives a cling after a load either, and letting go
     * would drop it); anything else ends (it stands or falls as any NPC, on the motion vanilla saved). Vanilla's own
     * {@code NoGravity} flag was saved with the entity: it is cleared here unless the landing needs it, so a hero saved
     * in a dive (no gravity, not saved as a state) never hangs in the air (the review of A10-A12).
     */
    public void load(CompoundTag tag) {
        CompoundTag m = tag.getCompound("mobility");
        savedStamina = m.contains("stamina") ? m.getDouble("stamina") : -1;
        savedExhausted = m.getBoolean("exhausted");
        long now = now();
        airPoise.restore(m.getDouble("airPoise"), m.contains("downedAgo") ? now - m.getInt("downedAgo") : Long.MIN_VALUE / 4, now);
        String st = m.getString("state");
        hero.setNoGravity(false);
        state = State.NONE;
        stateTicks = 0;
        if ("fly".equals(st)) {
            state = State.LAND;
            hovering = false;
            flyTarget = null;
            inAir = true;
            hero.setNoGravity(true);
        } else if ("wall".equals(st) || "cling".equals(st)) {
            Direction d = Direction.byName(m.getString("face"));
            if (d != null && d.getAxis().isHorizontal()) {
                climbFace = d;
                climbStartY = hero.getY();
                descending = false;
                inAir = true;
                state = State.CLIMB_DOWN;
            }
        }
        staminaRestored = false;
    }

    private double savedStamina = -1;
    private boolean savedExhausted;

    /** After a load and the definition: the saved stamina goes back into the profile's bar. */
    public void afterDefinition() {
        refresh();
        if (!staminaRestored && savedStamina >= 0 && stamina.max() > 0) {
            stamina.set(savedStamina, savedExhausted);
        }
        staminaRestored = true;
        if ((state == State.LAND && !profile.flies()) || (state == State.CLIMB_DOWN && !profile.has(Feature.CLIMB))) {
            hero.setNoGravity(false);
            state = State.NONE;
        }
    }

    /** For the sheet: what it does now, in a few words. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        parts.add(state.name().toLowerCase(java.util.Locale.ROOT));
        if (stamina.max() > 0) {
            parts.add(String.format(java.util.Locale.ROOT, "stamina %.1f/%.1f%s", stamina.value(), stamina.max(),
                    stamina.exhausted() ? " exhausted" : ""));
        }
        if (airPoise.value() > 0) {
            parts.add(String.format(java.util.Locale.ROOT, "air poise %.0f", airPoise.value()));
        }
        return String.join(", ", parts);
    }
}
