package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.hero.entity.HeroZone;
import com.factionclash.logic.hero.EffectRouting;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Direction;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.MovePlan;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.logic.hero.PassiveBook;
import com.factionclash.logic.hero.Sense;
import com.factionclash.logic.hero.UseLedger;
import com.factionclash.logic.leader.Temperaments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hero's fight runtime (HEROES_V2 3.3 and 6.3; HERO_FORMAT.md 14, 15, 32 phase 4; task A3): its energy, which it
 * gains and spends, its {@link MoveSelector} (cooldowns and charges with the floors its definition already holds, the
 * gap, reactions, followups), the ability power of its level, and the move it runs. One loop in every fight, whoever
 * the enemy is: nothing here reads whether a target is a player.
 *
 * <p><b>A move</b> goes select -> wind-up -> resolve at its hit ticks -> recovery ({@link MovePlan}). The plan comes
 * from the move's authored frames today; A5 plugs in the timing files' frames at the hero's speed, the
 * {@code DATA_ACTION} code and interrupts at {@link #planFor} and {@link Run#begin} without changing the rest. The
 * forms are v1's fourteen and the combo ({@link EffectRouting#FORMS}); a move with a form or effect the engine does
 * not run yet is never picked.
 *
 * <p><b>Energy</b> (v1 14): basic hits land 5 (melee, thrown and arrows alike), a skill's use its {@code energy_gain},
 * damage taken 1 per 2 health (at most 8 a hit), a kill 10, heals and shields for allies 1 per 2 points (at most 8 a
 * use), an {@code energy} effect its amount; each times the ultimate's {@code charge_rate} and the {@code energy_gain}
 * stat. The ultimate needs 100 and spends all of it.
 *
 * <p><b>Task A4</b> adds the hero's state ({@link HeroStates}: entered by {@code empower}, its stats, on-hit effects,
 * size, aura, face, locked skills and move list), its passives ({@link HeroPassives} over {@code PassiveBook}, with the
 * loop guard), stuns (a stunned hero picks nothing and a move in its wind-up is interrupted, half its cooldown back),
 * the triggers it sets off ({@code on_skill}, {@code on_ultimate}, {@code on_ally_ultimate}, {@code on_hit},
 * {@code on_mark_full}...), combat XP for the ultimate (v1 22.1) and the wind-up's telegraph pose.
 */
public final class HeroCombat {
    /** A leap that has not landed after this long lands where it is (v1 29.4: "a 40-tick timeout"). */
    public static final int LEAP_TIMEOUT = 40;
    /** At most this many zones of one hero; a new one ends the oldest (v1 10.1). */
    public static final int MAX_ZONES = 2;

    private final HeroEntity hero;
    private MoveSelector selector;
    private HeroDef selectorFor;
    private MoveSelector.Snapshot saved;
    private final Sense sense = new Sense();
    private final Sense effectSense = new Sense();
    private Run run;
    private MoveSelector.Pick pending;
    private long nextCheckAt;
    private boolean fighting;
    private int basicHits;
    private int ownHopUntil = -1;
    private final ArrayDeque<HeroZone> zones = new ArrayDeque<>();
    /** The last move it started, when, and when it first released (game times; -1: not yet). */
    private String lastMove = "";
    private long lastMoveStart = -1, lastRelease = -1;
    /** Its state (A4). */
    final HeroStates states;
    /** Its passives (A4). */
    final HeroPassives passives;
    private boolean restoreStates;

    HeroCombat(HeroEntity hero) {
        this.hero = hero;
        this.states = new HeroStates(hero);
        this.passives = new HeroPassives(hero);
    }

    // ------------------------------------------------------------------ the definition and the selector

    /** The hero's checked definition while it has one and is not dormant (null otherwise: no moves). */
    HeroDef def() {
        return hero.dormant() ? null : hero.definition();
    }

    /**
     * The move selector for the hero's current definition (built on first use, rebuilt after a reload with its
     * charges carried over), seeded from the hero's UUID; null while it has no definition.
     */
    public MoveSelector selector() {
        HeroDef d = def();
        if (d == null || d.v2() == null) {
            return null;
        }
        if (selector == null || selectorFor != d) {
            MoveSelector.Snapshot keep = selector != null ? selector.snapshot() : saved;
            HeroDef.Personality p = d.v2().personality();
            int a = p == null ? 50 : p.trait(HeroDef.Trait.AGGRESSION);
            int c = p == null ? 50 : p.trait(HeroDef.Trait.CAUTION);
            boolean opener = p != null && p.temperament() != null
                    && Temperaments.has(p.temperament(), Temperaments.Flag.OPENS_WITH_ULTIMATE);
            selector = new MoveSelector(d.v2(), a, c, opener, hero.getUUID().getLeastSignificantBits())
                    .runnable(EffectRouting::runs);
            selector.restore(keep, now());
            selectorFor = d;
            saved = null;
            if (fighting) {
                selector.fightStarted(now());
            }
        }
        return selector;
    }

    long now() {
        return hero.level().getGameTime();
    }

    /** Ability power at the hero's level (v1 5.3: 1 + the growth profile's percent per level above 1). */
    public double abilityPower() {
        HeroDef d = def();
        return d == null ? 1 : HeroRules.abilityPower(d.stats().growth(), hero.npcLevel());
    }

    /** The sense an effect's {@code if} is checked with (reused, so a check allocates no sense). */
    Sense effectSense() {
        return effectSense;
    }

    // ------------------------------------------------------------------ energy (v1 14)

    /**
     * Adds energy for an event worth {@code base}: x the ultimate's {@code charge_rate} x (1 + the hero's
     * {@code energy_gain} stat).
     */
    public void gain(double base) {
        HeroDef d = def();
        int rate = d != null && d.v2() != null && d.v2().ultimate() != null ? d.v2().ultimate().ability().chargeRate() : 100;
        int stat = (int) Math.round(HeroStatus.clampStat(Stat.ENERGY_GAIN, HeroStatus.percent(hero, Stat.ENERGY_GAIN)));
        hero.setEnergy(HeroRules.addEnergy(hero.energy(), HeroRules.energyGain(base, rate, stat)));
    }

    // ------------------------------------------------------------------ the fight clock (6.3)

    /**
     * Every server tick, after the hero's AI and movement: the fight's opening gap starts when its fight starts, and
     * the running move advances one tick (so its timing never depends on when the goal selector runs).
     */
    void tick() {
        if (restoreStates) {
            restoreStates = false;
            states.restore();
        }
        if (!later.isEmpty()) {
            for (int i = 0; i < later.size(); i++) {
                Later l = later.get(i);
                trigger(l.e(), l.enemy(), l.ally());
            }
            later.clear();
        }
        if (run != null) {
            run.step();
        }
        boolean f = hero.inFight();
        if (f && !fighting) {
            MoveSelector s = selector();
            if (s != null) {
                s.fightStarted(now());
            }
            passives.fightStarted();
        }
        fighting = f;
        states.tick();
        passives.tick(fighting);
        if (!zones.isEmpty()) {
            zones.removeIf(Entity::isRemoved);
        }
    }

    // ------------------------------------------------------------------ the move (HeroAbilityGoal drives it)

    /** Whether a move is running (its wind-up, active part or recovery). */
    public boolean running() {
        return run != null;
    }

    /** The move being run (null: none). */
    public Move runningMove() {
        return run == null ? null : run.move;
    }

    /** The id of the last move it started ("" before any). */
    public String lastMove() {
        return lastMove;
    }

    /** The game time the last move started (-1: none yet). */
    public long lastMoveStart() {
        return lastMoveStart;
    }

    /** The game time the last move released its first hit (the end of its wind-up; -1: not yet). */
    public long lastRelease() {
        return lastRelease;
    }

    /** The stage of the running move (DONE when none). */
    public MoveStage stage() {
        return run == null ? MoveStage.DONE : run.plan.stageAt(run.t);
    }

    /**
     * Every 5 ticks while the hero fights and runs no move: asks the selector for a move against the current target
     * (HEROES_V2 6.3). True when one was picked; {@link #startPicked} starts it.
     */
    public boolean tryPick() {
        if (run != null || pending != null) {
            return pending != null;
        }
        long now = now();
        if (now < nextCheckAt || !hero.isAlive() || hero.isPreview()) {
            return false;
        }
        nextCheckAt = now + MoveSelector.CHECK_TICKS;
        LivingEntity t = hero.getTarget();
        if (t == null || !t.isAlive() || !fighting || HeroStatus.stunned(hero)) {
            return false;
        }
        MoveSelector s = selector();
        if (s == null) {
            return false;
        }
        HeroSenses.fill(hero, t, sense, hero.energy(), hero.tickCount - hero.lastTargetTick());
        pending = s.check(now, sense);
        return pending != null;
    }

    /** Starts the move {@link #tryPick} picked. */
    public void startPicked() {
        MoveSelector.Pick p = pending;
        pending = null;
        if (p != null) {
            begin(p);
        }
    }

    /** The frames a move runs with: its authored ones (A5: the timing file's at the hero's speed). */
    MovePlan.Plan planFor(Move m) {
        return MovePlan.of(m);
    }

    private void begin(MoveSelector.Pick p) {
        HeroDef d = def();
        if (d == null) {
            return;
        }
        Move m = p.move();
        Ability a = m.ability();
        boolean ultimate = MoveSelector.isUltimate(m);
        Slot slot = ultimate ? Slot.ULTIMATE : Slot.SKILL;
        UseLedger ledger = UseLedger.of(slot, abilityPower(), HeroEffects.powerScale(), UseLedger.formFactor(m));
        HeroUse use = newUse(d, m, slot, m.form(), m.id(), ledger);
        run = new Run(m, p.source(), planFor(m), use, hero.getTarget());
        lastMove = m.id();
        lastMoveStart = now();
        lastRelease = -1;
        if (ultimate) {
            hero.setEnergy(0); // the ultimate uses all of it (v1 14)
        } else {
            gain(a.energyGain());
        }
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            HeroState.count(rec.casts, m.id());
            CampaignData.get(hero.getServer()).setDirty();
        }
        if (ultimate) {
            hero.awardCombatXp(HeroRules.XP_ULTIMATE);
        }
        String line = a.line();
        if (line != null && !line.isBlank()) {
            hero.speak(ultimate ? LineEvent.ULTIMATE : LineEvent.SKILL, line);
        } else if (ultimate) {
            hero.say(LineEvent.ULTIMATE, Map.of("ability", a.name()), null);
        } else {
            hero.say(LineEvent.SKILL, Map.of("ability", a.name()), null);
        }
        if (a.look() != null && a.look().sound() != null) {
            String sid = a.look().sound();
            ResourceLocation rl = ResourceLocation.tryParse(sid);
            if (rl != null) {
                BuiltInRegistries.SOUND_EVENT.getOptional(rl).ifPresent(s ->
                        hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), s, SoundSource.HOSTILE, 1F, 1F));
            }
        }
        if (FactionClash.LOGGER.isDebugEnabled()) {
            FactionClash.LOGGER.debug("[Heroes] {} starts {} ({}) at {}", hero.heroId(), m.id(), p.source(), now());
        }
        run.begin();
        // v1 17.1: on_skill / on_ultimate, and every other hero of its side within 24 blocks hears on_ally_ultimate
        LivingEntity t = hero.getTarget();
        if (ultimate) {
            trigger(PassiveBook.Event.of(Trigger.ON_ULTIMATE), t, null);
            for (HeroEntity other : HeroEntity.live()) {
                if (other != hero && other.isAlive() && other.level() == hero.level()
                        && other.distanceToSqr(hero) <= PassiveBook.ALLY_ULTIMATE_RADIUS * PassiveBook.ALLY_ULTIMATE_RADIUS
                        && HeroForms.ally(other, hero)) {
                    other.combat().trigger(PassiveBook.Event.of(Trigger.ON_ALLY_ULTIMATE), other.getTarget(), hero);
                }
            }
        } else {
            trigger(PassiveBook.Event.of(Trigger.ON_SKILL), t, null);
        }
    }

    /** A new use (its projectiles and zones carry it until they are gone). */
    HeroUse newUse(HeroDef d, Move m, Slot slot, Form form, String key, UseLedger ledger) {
        return new HeroUse(hero, d, m, slot, form, key, ledger, abilityPower());
    }

    /**
     * Starts one of the hero's moves now against its current target, outside the selector's gap and without spending
     * a charge (operator hooks and tests; the selector books its own picks). False when the hero runs a move already,
     * has no such move, or the engine does not run it.
     */
    public boolean forceMove(String id) {
        HeroDef d = def();
        Move m = d == null || d.v2() == null ? null : d.v2().move(id);
        if (run != null || m == null || !EffectRouting.runs(m)) {
            return false;
        }
        begin(new MoveSelector.Pick(m, MoveSelector.Source.PICK, null));
        return run != null;
    }

    /**
     * The goal stopped: a move still in its wind-up is interrupted (v1 10.3: half the cooldown back; the same path a
     * stun takes in A4, and an escape in A6), one past it simply ends.
     */
    public void stopRun() {
        if (run == null) {
            return;
        }
        Run r = run;
        if (r.plan.stageAt(r.t) == MoveStage.WINDUP) {
            MoveSelector s = selector();
            if (s != null) {
                s.interrupted(r.move, now());
            }
        }
        r.end(true);
    }

    /** Discards the running move and any pick (death, a reload); its state and held passive stats end. */
    void reset() {
        if (run != null) {
            run.end(true);
        }
        pending = null;
        states.exit();
        passives.reset();
    }

    /**
     * It was stunned (HEROES_V2 6.15, task A4): a move in its wind-up is interrupted (half the cooldown back, v1 10.3;
     * the path an escape takes in A6), one past it simply ends, a pick waiting to start is dropped; it shows its
     * {@code stun} pose.
     */
    void stunned() {
        pending = null;
        if (run != null) {
            stopRun();
        }
        hero.playAction(HeroActions.actionCode("stun"), 1.0, 0);
    }

    // ------------------------------------------------------------------ states and passives (A4)

    /** Enters (or refreshes) one of its states for {@code seconds}: {@code empower}, a phase or a passive. */
    public boolean enterState(String name, double seconds) {
        return states.enter(name, seconds);
    }

    /** The state it is in now, or null. */
    public String stateName() {
        return states.name();
    }

    /** The game time its state ends (0: none). */
    public long stateUntil() {
        return states.name() == null ? 0 : states.until();
    }

    /** The mood its state holds (-1: none). */
    public int stateMood() {
        return states.mood();
    }

    /**
     * An event for its passives (v1 17.1, HEROES_V2 6.6): its {@code enemy} (the one hit, the attacker, the marked
     * one) and {@code ally} (the hurt or low ally), either may be null. A5, A6 and A12 send theirs here too
     * ({@code on_counter}, {@code on_phase}, {@code on_stagger}, {@code on_escape}...).
     */
    public void trigger(PassiveBook.Event e, LivingEntity enemy, LivingEntity ally) {
        if (passives.has(e.trigger())) {
            passives.fire(e, enemy, ally);
        }
    }

    /** An event the world's hooks saw inside another entity's hurt or heal: its passives answer it on the hero's tick. */
    private record Later(PassiveBook.Event e, LivingEntity enemy, LivingEntity ally) {
    }

    private final List<Later> later = new ArrayList<>();

    /**
     * {@link #trigger}, on the hero's next tick: hooks inside another entity's hurt, heal or shield block send theirs here,
     * so a passive never strikes into a hurt that is still being worked out.
     */
    public void triggerLater(PassiveBook.Event e, LivingEntity enemy, LivingEntity ally) {
        if (passives.has(e.trigger()) && later.size() < 32) {
            later.add(new Later(e, enemy, ally));
        }
    }

    /** Whether it has a passive with this trigger (the world's hooks ask first). */
    public boolean hasPassive(Trigger t) {
        return passives.has(t);
    }

    /** How many times its passives fired (tests, {@code sim.py}). */
    public int passiveFirings() {
        return passives.firings;
    }

    /** Its health share changed (a heal): low-health passives re-arm 20 points above their value. */
    void healthChanged(double share) {
        passives.health(share);
    }

    // ------------------------------------------------------------------ basic hits (v1 9, 14)

    /**
     * A basic attack landed on {@code target} (a melee blow, a thrown projectile, an arrow): 5 energy, the basic
     * attack's {@code on_hit} effects and, every n-th hit, its finisher. The same for every target.
     */
    public void onBasicHit(LivingEntity target, Entity direct) {
        gain(HeroRules.ENERGY_BASIC_HIT);
        HeroDef d = def();
        if (d == null || target == null) {
            return;
        }
        basicHits++;
        List<LivingEntity> to = List.of(target);
        HeroDef.BasicAttack b = d.basicAttack();
        List<HeroDef.Effect> stateHit = states.onHit();
        if (!b.onHit().isEmpty() || !stateHit.isEmpty()) {
            // the basic on_hit and the state's share one per-hit cap (v1 16.1)
            HeroUse use = newUse(d, null, Slot.ON_HIT, Form.MELEE, "basic",
                    UseLedger.of(Slot.ON_HIT, abilityPower(), HeroEffects.powerScale(), 1));
            if (!b.onHit().isEmpty()) {
                HeroEffects.apply(use, b.onHit(), 0, to, direct);
            }
            if (!stateHit.isEmpty() && target.isAlive()) {
                HeroEffects.apply(use, stateHit, 2, to, direct);
            }
        }
        if (b.everyNthHit() != null && b.everyNthHit().n() > 0 && basicHits % b.everyNthHit().n() == 0) {
            HeroUse use = newUse(d, null, Slot.NTH_HIT, Form.MELEE, "finisher",
                    UseLedger.of(Slot.NTH_HIT, abilityPower(), HeroEffects.powerScale(), 1));
            HeroEffects.apply(use, b.everyNthHit().effects(), 1, to, direct);
        }
        triggerLater(PassiveBook.Event.of(Trigger.ON_HIT), target, null); // an arrow's hit comes inside the target's hurt
    }

    // ------------------------------------------------------------------ effects that act on the hero

    /** {@code cooldown_cut}: every running recharge comes sooner, never under half its cooldown (v1 13.5). */
    public void cutCooldowns(double seconds) {
        MoveSelector s = selector();
        if (s != null) {
            s.cutCooldowns(now(), seconds);
        }
    }

    /** {@code move_self}: a physical hop of the hero's own, away from, toward or up (v1 13.4); no fall damage from it. */
    public void hop(Direction d, double distance) {
        FormGeometry.Impulse i = FormGeometry.hop(d, distance);
        Vec3 aim = run != null ? run.aim : hero.getTarget() != null ? hero.getTarget().position() : null;
        Vec3 f = aim == null ? HeroForms.facing(hero, hero.position()) : HeroForms.facing(hero, aim);
        double sign = d == Direction.FORWARD ? 1 : d == Direction.BACK ? -1 : 0;
        takeOff(f.x * i.horizontal() * sign, i.vertical(), f.z * i.horizontal() * sign);
        ownHopUntil = hero.tickCount + i.airTicks() + 20;
    }

    /**
     * A take-off of the hero's own: the impulse, and the hero counts as off the ground from this tick, so its arc is
     * the one {@link FormGeometry} worked out (air drag from the first tick, not the ground's friction).
     */
    private void takeOff(double vx, double vy, double vz) {
        hero.setDeltaMovement(vx, vy, vz);
        hero.setOnGround(false);
        hero.hasImpulse = true;
        hero.getNavigation().stop();
    }

    /** Whether the hero is in the air by its own leap or hop (it takes no fall damage from those, v1 13.4). */
    public boolean ownHop() {
        return hero.tickCount <= ownHopUntil;
    }

    /** A mark this hero applies reached its maximum: its {@code on_mark_full} passives for that mark (v1 17.1). */
    void markFull(String mark, LivingEntity on) {
        if (FactionClash.LOGGER.isDebugEnabled()) {
            FactionClash.LOGGER.debug("[Heroes] {}: mark {} full on {}", hero.heroId(), mark, on.getName().getString());
        }
        trigger(new PassiveBook.Event(Trigger.ON_MARK_FULL, mark, 1, null, 0, false), on == hero ? null : on, null);
    }

    void zoneStarted(HeroZone z) {
        zones.removeIf(Entity::isRemoved);
        zones.addLast(z);
        while (zones.size() > MAX_ZONES) {
            zones.removeFirst().discard();
        }
    }

    // ------------------------------------------------------------------ saving (3.3: cooldowns as ready-at game times)

    void save(CompoundTag h) {
        MoveSelector.Snapshot s = selector != null ? selector.snapshot() : saved;
        if (s != null) {
            CompoundTag c = new CompoundTag();
            ListTag list = new ListTag();
            for (Map.Entry<String, MoveSelector.ChargeState> e : s.charges().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putString("move", e.getKey());
                t.putInt("left", e.getValue().left());
                t.putLong("next", e.getValue().nextAt());
                t.putLong("used", e.getValue().lastUseAt());
                list.add(t);
            }
            c.put("charges", list);
            c.putLong("ultimate", s.lastUltimateAt());
            h.put("cooldowns", c);
        }
        h.putInt("basicHits", basicHits);
        states.save(h);
    }

    void load(CompoundTag h) {
        basicHits = h.getInt("basicHits");
        states.load(h);
        restoreStates = true;
        if (!h.contains("cooldowns")) {
            return;
        }
        CompoundTag c = h.getCompound("cooldowns");
        Map<String, MoveSelector.ChargeState> m = new LinkedHashMap<>();
        ListTag list = c.getList("charges", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            m.put(t.getString("move"), new MoveSelector.ChargeState(t.getInt("left"), t.getLong("next"), t.getLong("used")));
        }
        saved = new MoveSelector.Snapshot(m, c.getLong("ultimate"));
        selector = null;
        selectorFor = null;
    }

    // ------------------------------------------------------------------ one running move

    /** A move from its start to the end of its recovery. */
    final class Run {
        final Move move;
        final MoveSelector.Source source;
        final MovePlan.Plan plan;
        final HeroUse use;
        LivingEntity target;
        Vec3 aim;
        int t;
        /** The next hit of the plan to release (each is released once, even while the active part is held). */
        int nextHit;
        boolean done;
        /** Leap and dash motion. */
        int motion;
        int motionTicks;
        boolean leftGround;
        Vec3 dashDir;
        double dashSpeed;
        int dashTicks;
        final Set<Integer> dashHit = new HashSet<>();
        /** A chain's links so far. */
        final List<LivingEntity> chain = new ArrayList<>();
        /** It holds a telegraph pose until its first release (A4). */
        boolean posed;

        Run(Move move, MoveSelector.Source source, MovePlan.Plan plan, HeroUse use, LivingEntity target) {
            this.move = move;
            this.source = source;
            this.plan = plan;
            this.use = use;
            this.target = target;
            this.aim = target != null ? target.position() : hero.position();
        }

        /** The wind-up starts: the hero stops and gathers (A5 adds {@code DATA_ACTION} here). */
        void begin() {
            hero.getNavigation().stop();
            hero.setStateFlag(HeroActions.STATE_WINDUP, plan.windup() > 0);
            int pose = HeroActions.telegraphPose(move.ability().look() == null ? null : move.ability().look().pose());
            if (pose > 0 && plan.windup() > 0) {
                hero.playAction(pose, 1.0, 0); // the wind-up's telegraph pose (A5 plays the move's own clip)
                posed = true;
            }
            if (hero.level() instanceof ServerLevel sl) {
                HeroEffects.particles(sl, hero, HeroEffects.particle(move.ability().look(), element(), color()), 8);
            }
            // the hero's tick steps it from this tick on
        }

        /** The telegraph pose ends with the wind-up. */
        void unpose() {
            if (posed) {
                posed = false;
                hero.stopAction();
            }
        }

        HeroDef.Element element() {
            HeroDef.Element e = use.def.element();
            return e == null ? HeroDef.Element.PHYSICAL : e;
        }

        int color() {
            return use.def.color();
        }

        boolean enemyForm() {
            return MoveSelector.needsTarget(move);
        }

        void step() {
            if (done) {
                return;
            }
            MoveStage stage = plan.stageAt(t);
            if (stage == MoveStage.WINDUP) {
                if (enemyForm() && (target == null || !target.isAlive() || target.isRemoved()
                        || hero.distanceTo(target) > MoveSelector.reach(move) + HeroForms.RANGE_SLACK)) {
                    // v1 10.3: the target died or left range during the wind-up; the charge is not spent
                    MoveSelector s = selector();
                    if (s != null) {
                        s.cancelled(move, now());
                    }
                    end(true);
                    return;
                }
                if (target != null) {
                    aim = target.position();
                    hero.getLookControl().setLookAt(target, 30F, 30F);
                }
                hero.getNavigation().stop();
                if (t % 5 == 0) {
                    tell();
                }
            } else if (stage == MoveStage.ACTIVE && target != null && target.isAlive()) {
                hero.getLookControl().setLookAt(target, 30F, 30F);
            }
            while (!done && nextHit < plan.hitCount() && plan.hit(nextHit) <= t) {
                int hit = nextHit++;
                if (hit == 0) {
                    hero.setStateFlag(HeroActions.STATE_WINDUP, false);
                    lastRelease = now();
                    unpose();
                }
                if (target != null && target.isAlive()) {
                    aim = target.position();
                }
                release(hit);
            }
            if (motion != 0) {
                motionTick();
            }
            if (t == plan.activeEnd() - 1 && motion != 0 && motionTicks < LEAP_TIMEOUT) {
                return; // the active part lasts until the leap lands or the dash ends
            }
            t++;
            if (t == plan.activeEnd() && !done) {
                hero.setStateFlag(HeroActions.STATE_WINDUP, false);
                if (use.landed) {
                    MoveSelector s = selector();
                    if (s != null && target != null && target.isAlive()) {
                        HeroSenses.fill(hero, target, sense, hero.energy(), 0);
                        MoveSelector.Pick next = s.followup(now(), move, sense);
                        if (next != null) {
                            // a followup replaces the recovery of the move it follows (6.3)
                            end(false);
                            HeroCombat.this.begin(next);
                            return;
                        }
                    }
                }
            }
            if (t >= plan.recoveryEnd()) {
                end(true);
            }
        }

        /**
         * The run is over. {@code gap}: the string ends and the selector's next gap starts now (false only when a
         * followup takes over the recovery, 6.3).
         */
        void end(boolean gap) {
            if (done) {
                return;
            }
            done = true;
            motion = 0;
            hero.setStateFlag(HeroActions.STATE_WINDUP, false);
            unpose();
            if (run == this) {
                run = null;
            }
            MoveSelector s = gap ? selector() : null;
            if (s != null) {
                s.moveEnded(now(), HeroActions.phase(hero.state()), hero.stateFlag(HeroActions.STATE_ENRAGED));
            }
        }

        /**
         * The wind-up's tell for an area (v1 10.3): a ring of smoke where it will land, every 5 ticks (the growing
         * ground decals are A6's; the wind-up is the same with or without them).
         */
        void tell() {
            Ability a = move.ability();
            Vec3 at;
            switch (move.form()) {
                case NOVA -> at = hero.position();
                case ZONE -> at = a.zoneAt() == HeroDef.ZoneAt.TARGET ? aim : hero.position();
                case LEAP -> at = aim;
                default -> {
                    return;
                }
            }
            HeroEffects.particleRing(hero.level(), at, Math.max(1, a.radius()), net.minecraft.core.particles.ParticleTypes.SMOKE);
        }

        // ---------------------------------------------------------------- the forms (v1 10.1)

        void release(int hit) {
            Ability a = move.ability();
            List<HeroDef.Effect> fx = a.effects();
            switch (move.form()) {
                case MELEE -> {
                    hero.swing(InteractionHand.MAIN_HAND);
                    HeroEffects.apply(use, fx, 0, strike(a.radius()), hero);
                }
                case COMBO -> {
                    hero.swing(InteractionHand.MAIN_HAND);
                    HeroDef.Strike s = move.strikes().get(Math.min(hit, move.strikes().size() - 1));
                    HeroEffects.apply(use, s.effects(), hit + 1, strike(s.radius()), hero);
                }
                case BOLT -> {
                    List<LivingEntity> rs = new ArrayList<>();
                    if (target != null && target.isAlive() && hero.distanceTo(target) <= a.range() + HeroForms.RANGE_SLACK
                            && hero.getSensing().hasLineOfSight(target) && HeroForms.enemy(hero, target)) {
                        rs.add(target);
                        splash(rs, target.position(), a.radius());
                        HeroEffects.particleLine(hero.level(), hero.getEyePosition(), target.getEyePosition(),
                                HeroEffects.particle(a.look(), element(), color()));
                    }
                    HeroEffects.apply(use, fx, 0, rs, hero);
                }
                case CHAIN -> chainLink(hit);
                case CONE -> {
                    hero.swing(InteractionHand.MAIN_HAND);
                    HeroEffects.apply(use, fx, 0, HeroForms.cone(hero, aim, a.range(), a.angle()), hero);
                }
                case LINE -> {
                    Vec3 end = HeroForms.lineEnd(hero, aim.add(0, target != null ? target.getBbHeight() / 2 : 1, 0), a.range());
                    HeroEffects.particleLine(hero.level(), hero.getEyePosition(), end, HeroEffects.particle(a.look(), element(), color()));
                    HeroEffects.apply(use, fx, 0, HeroForms.line(hero, end, a.width()), hero);
                }
                case NOVA -> {
                    use.area(hero.position(), a.radius());
                    HeroEffects.particleRing(hero.level(), hero.position(), a.radius(), HeroEffects.particle(a.look(), element(), color()));
                    HeroEffects.apply(use, fx, 0, HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), a.radius()), hero);
                }
                case SELF -> HeroEffects.apply(use, fx, 0, List.of(hero), hero);
                case ALLY -> HeroEffects.apply(use, fx, 0, List.of(HeroForms.mostHurtAlly(hero, a.range())), hero);
                case ALLIES -> {
                    use.area(hero.position(), a.radius());
                    HeroEffects.apply(use, fx, 0, HeroForms.alliesNear(hero, hero.position(), a.radius()), hero);
                }
                case SHOT -> shoot(a, new double[] {0});
                case VOLLEY -> shoot(a, FormGeometry.fanOffsets(a.count()));
                case ZONE -> zone(a);
                case LEAP -> leap(a);
                case DASH -> dash(a);
                default -> {
                    // forms the engine does not run are never picked (EffectRouting.FORMS)
                }
            }
            if (a.look() != null && a.look().lightning() && MoveSelector.isUltimate(move) && hero.level() instanceof ServerLevel sl) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    Vec3 at = target != null && target.isAlive() ? target.position() : hero.position();
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    sl.addFreshEntity(bolt);
                }
            }
        }

        /** A melee strike's receivers: the target in reach, and enemies within {@code radius} of it. */
        List<LivingEntity> strike(double radius) {
            List<LivingEntity> rs = new ArrayList<>();
            if (target != null && target.isAlive() && HeroForms.inMeleeReach(hero, target) && HeroForms.enemy(hero, target)) {
                rs.add(target);
                splash(rs, target.position(), radius);
            }
            return rs;
        }

        void splash(List<LivingEntity> rs, Vec3 at, double radius) {
            if (radius <= 0) {
                return;
            }
            for (LivingEntity e : HeroForms.enemiesNear(hero, at.add(0, 0.5, 0), radius)) {
                if (!rs.contains(e)) {
                    rs.add(e);
                }
            }
        }

        /** One link of a chain: the target first, then the nearest new enemy within the radius of the last link. */
        void chainLink(int hit) {
            Ability a = move.ability();
            LivingEntity next = null;
            if (hit == 0) {
                if (target != null && target.isAlive() && hero.distanceTo(target) <= a.range() + HeroForms.RANGE_SLACK
                        && hero.getSensing().hasLineOfSight(target) && HeroForms.enemy(hero, target)) {
                    next = target;
                }
            } else if (!chain.isEmpty()) {
                LivingEntity last = chain.get(chain.size() - 1);
                double best = Double.MAX_VALUE;
                for (LivingEntity e : HeroForms.enemiesNear(hero, last.position().add(0, 0.5, 0), a.radius())) {
                    double dd = e.distanceToSqr(last);
                    if (!chain.contains(e) && dd < best) {
                        best = dd;
                        next = e;
                    }
                }
            }
            if (next == null) {
                return;
            }
            Vec3 from = chain.isEmpty() ? hero.getEyePosition() : chain.get(chain.size() - 1).getEyePosition();
            chain.add(next);
            HeroEffects.particleLine(hero.level(), from, next.getEyePosition(), HeroEffects.particle(a.look(), element(), color()));
            HeroEffects.apply(use, a.effects(), 0, List.of(next), hero);
        }

        /** A shot or a volley: one projectile per yaw offset, aimed at the target (v1 10.1). */
        void shoot(Ability a, double[] yawOffsets) {
            Vec3 to = target != null && target.isAlive() ? target.position().add(0, target.getBbHeight() * 0.6, 0) : aim;
            for (double off : yawOffsets) {
                HeroProjectile.launchMove(hero, use, a, to, off);
            }
            hero.swing(InteractionHand.MAIN_HAND);
        }

        /** A zone at the target, at the hero or following it (v1 10.1); at most two per hero. */
        void zone(Ability a) {
            boolean follow = a.zoneAt() == HeroDef.ZoneAt.FOLLOW_SELF;
            Vec3 at = a.zoneAt() == HeroDef.ZoneAt.TARGET ? aim : hero.position();
            UseLedger ledger = UseLedger.of(use.slot, use.abilityPower, HeroEffects.powerScale(), UseLedger.formFactor(move));
            HeroUse zoneUse = newUse(use.def, move, use.slot, Form.ZONE, move.id(), ledger);
            HeroZone z = HeroZone.spawn(hero, zoneUse, a, at, follow);
            if (z != null) {
                zoneStarted(z);
            }
        }

        /** A leap: a physical arc to beside the target; on landing, every enemy within the radius (v1 10.1). */
        void leap(Ability a) {
            if (hero.isInWater() || hero.onClimbable()) {
                return;
            }
            Vec3 o = hero.position();
            Vec3 f = HeroForms.facing(hero, aim);
            double d = Math.max(0, Math.sqrt((aim.x - o.x) * (aim.x - o.x) + (aim.z - o.z) * (aim.z - o.z)) - 1.0);
            FormGeometry.Impulse i = FormGeometry.leap(Math.min(d, a.range() + 1), aim.y - o.y);
            takeOff(f.x * i.horizontal(), i.vertical(), f.z * i.horizontal());
            ownHopUntil = hero.tickCount + i.airTicks() + LEAP_TIMEOUT;
            motion = 1;
            motionTicks = 0;
            leftGround = false;
        }

        /** A dash: a 0.5 s push toward the target along the ground, through enemies within 1.5 blocks (v1 10.1). */
        void dash(Ability a) {
            if (hero.isInWater()) {
                return;
            }
            dashDir = HeroForms.facing(hero, aim);
            double dist = Math.min(a.range(), Math.sqrt(hero.distanceToSqr(aim.x, hero.getY(), aim.z)) + 1.5);
            dashSpeed = FormGeometry.dashSpeed(dist);
            dashTicks = FormGeometry.DASH_TICKS;
            hero.getNavigation().stop();
            motion = 2;
            motionTicks = 0;
            dashHit.clear();
        }

        void motionTick() {
            motionTicks++;
            if (motion == 1) {
                if (!hero.onGround()) {
                    leftGround = true;
                }
                if (leftGround && hero.onGround() || motionTicks >= LEAP_TIMEOUT) {
                    motion = 0;
                    Ability a = move.ability();
                    use.area(hero.position(), a.radius());
                    HeroEffects.particleRing(hero.level(), hero.position(), a.radius(), HeroEffects.particle(a.look(), element(), color()));
                    HeroEffects.apply(use, a.effects(), 0, HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), a.radius()), hero);
                }
                return;
            }
            if (motion == 2) {
                if (dashTicks <= 0 || motionTicks > 1 && hero.horizontalCollision || dropAhead()) {
                    motion = 0;
                    hero.setDeltaMovement(hero.getDeltaMovement().multiply(0.2, 1, 0.2));
                    return;
                }
                dashTicks--;
                Vec3 v = hero.getDeltaMovement();
                hero.setDeltaMovement(dashDir.x * dashSpeed, v.y, dashDir.z * dashSpeed);
                hero.hasImpulse = true;
                List<LivingEntity> hits = new ArrayList<>();
                for (LivingEntity e : HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), FormGeometry.DASH_REACH)) {
                    if (dashHit.add(e.getId())) {
                        hits.add(e);
                    }
                }
                if (!hits.isEmpty()) {
                    HeroEffects.apply(use, move.ability().effects(), 0, hits, hero);
                }
            }
        }

        /** No floor within a block and a half ahead (a dash stops at walls and drops, v1 10.1). */
        boolean dropAhead() {
            BlockPos ahead = BlockPos.containing(hero.getX() + dashDir.x * (dashSpeed + 0.6), hero.getY() - 0.2,
                    hero.getZ() + dashDir.z * (dashSpeed + 0.6));
            for (int i = 0; i <= 1; i++) {
                BlockPos p = ahead.below(i);
                if (!hero.level().getBlockState(p).getCollisionShape(hero.level(), p).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
}
