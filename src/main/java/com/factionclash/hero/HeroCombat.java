package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.hero.entity.HeroDecoy;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.hero.entity.HeroZone;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.EffectRouting;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Direction;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MovePlan;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.logic.hero.MoveTiming;
import com.factionclash.logic.hero.MoveTiming.Interrupt;
import com.factionclash.logic.hero.PassiveBook;
import com.factionclash.logic.hero.Sense;
import com.factionclash.logic.hero.UseLedger;
import com.factionclash.logic.leader.Temperaments;
import com.factionclash.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A hero's fight runtime (HEROES_V2 3.3 and 6.3; HERO_FORMAT.md 14, 15, 32 phase 4; task A3): its energy, which it
 * gains and spends, its {@link MoveSelector} (cooldowns and charges with the floors its definition already holds, the
 * gap, reactions, followups), the ability power of its level, and the move it runs. One loop in every fight, whoever
 * the enemy is: nothing here reads whether a target is a player.
 *
 * <p><b>The move runner</b> (task A5; HEROES_V2 4.8, 6.3-6.5, SLICE_PLAN 2 "Move runner"). A move goes select ->
 * wind-up -> hits -> recovery ({@link MovePlan}). Its frames come from the hero's timing file through its animation
 * chain (the clip its {@code DATA_ACTION} code names; the clip's {@code hit} markers are its hits) or, without one, from
 * the move's own frames; the plan is then played at the hero's speed (Haste, Mining Fatigue, Slowness and the
 * {@code attack_speed} stat, never under the class minimums: {@link MoveTiming}). {@code DATA_ACTION} gets the move's
 * code ({@code move.<slot>} from its {@code visual.json} list, {@code ultimate}, {@code ultimate.awakened}) at that
 * speed on the tick the wind-up starts, so the clip's {@code hit} marker shows on the tick the server lands the hit; a
 * hero with no clip for the move holds its look's telegraph pose instead (A4). Interrupts ({@link #interrupt}): a stun,
 * a stagger (A6a calls {@link #stagger}), a knock-back over 0.5 in a wind-up, a broken channel, an escape (A6/A12),
 * death, and a target lost or out of reach in a wind-up (its charge kept). Format 2's forms run here: {@code charge},
 * {@code wave}, {@code rain}, {@code sweep}, {@code grab}, {@code counter}, {@code barrage} (and A3's {@code combo});
 * waves and rain impacts, once released, roll on as flights of their own; the {@code dive} is A12's.
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
 * {@code on_mark_full}...), combat XP for the ultimate (v1 22.1) and the wind-up's telegraph pose. <b>Task A5</b> adds
 * {@code on_counter}, {@code on_stagger} and {@code on_add_died}, the hero's poise (filled by the {@code poise} effect and
 * a charge into a wall; hits fill it from A6a), its barrier and its decoys.
 */
public final class HeroCombat {
    /** A leap that has not landed after this long lands where it is (v1 29.4: "a 40-tick timeout"). */
    public static final int LEAP_TIMEOUT = 40;
    /** At most this many zones of one hero; a new one ends the oldest (v1 10.1). */
    public static final int MAX_ZONES = 2;
    /** A grabbed receiver this far from the hero's hand has slipped away (a teleport, a wall): the hold ends. */
    public static final double GRAB_SLIP = 4.0;

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
    /** The DATA_ACTION code, speed and plan of the last move it started (tests, the marker log). */
    private int lastCode = -1;
    private double lastSpeed = 1;
    private MovePlan.Plan lastPlan;
    /** Its state (A4). */
    final HeroStates states;
    /** Its passives (A4). */
    final HeroPassives passives;
    private boolean restoreStates;
    /** A5: its poise (from its definition), the end of a stagger, waves and rain impacts in flight, its decoys. */
    private BossRules.Poise poise;
    private HeroDef poiseFor;
    private long staggerUntil = Long.MIN_VALUE / 4;
    private final List<Flight> flights = new ArrayList<>();
    private final ArrayDeque<HeroDecoy> decoys = new ArrayDeque<>();
    /** The add a barrier depends on (6.6: its focus), or null. */
    private UUID barrierFocus;
    /** A blow its counter stance caught this tick: the riposte answers it on the hero's next tick. */
    private LivingEntity riposteOn;
    /** A6a: the boss controller (the fight clock, the hurt cooldown per attacker, the cap, poise, phases). */
    final HeroBoss boss;
    /** A6a: the ground decals of its wind-ups, drawn by the players near. */
    final HeroTelegraphs telegraphs;

    HeroCombat(HeroEntity hero) {
        this.hero = hero;
        this.states = new HeroStates(hero);
        this.passives = new HeroPassives(hero);
        this.boss = new HeroBoss(hero, this);
        this.telegraphs = new HeroTelegraphs(hero);
    }

    /** Its boss controller (task A6a). */
    public HeroBoss boss() {
        return boss;
    }

    /** Its ground decals (task A6a). */
    public HeroTelegraphs telegraphs() {
        return telegraphs;
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
     * Every server tick, after the hero's AI and movement: the fight's opening gap starts when its fight starts, a
     * caught blow is answered, the running move advances one tick (so its timing never depends on when the goal
     * selector runs), and waves and rain impacts in flight roll on.
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
        if (riposteOn != null) {
            LivingEntity a = riposteOn;
            riposteOn = null;
            if (run != null) {
                run.riposte(a);
            }
        }
        // what is in flight first: a wave or an impact released this tick starts rolling on the next one, so a rain
        // impact lands exactly its delay after its call
        if (!flights.isEmpty()) {
            for (int i = 0; i < flights.size(); i++) {
                if (flights.get(i).step()) {
                    flights.remove(i--);
                }
            }
        }
        if (run != null) {
            run.step();
        }
        // the fight (6.1, A6a): from the first engaged enemy, player or NPC alike, until 10 s after the last
        boss.tickFight();
        boolean f = boss.fighting();
        if (f && !fighting) {
            MoveSelector s = selector();
            if (s != null) {
                s.fightStarted(now());
            }
            passives.fightStarted();
            BossRules.Poise p = poise();
            if (p != null) {
                p.reset(); // a new fight starts from an empty bar (6.9)
            }
        }
        fighting = f;
        states.tick();
        passives.tick(fighting);
        boss.tick();
        telegraphs.tick();
        if (!zones.isEmpty()) {
            zones.removeIf(Entity::isRemoved);
        }
        if (!decoys.isEmpty() && (hero.tickCount & 7) == 0) {
            decoys.removeIf(d -> d.isRemoved() || d.popped());
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

    /** The {@code DATA_ACTION} code the last move started with (-1: none, a telegraph pose or nothing played). */
    public int lastCode() {
        return lastCode;
    }

    /** The speed the last move played at (its clip and its plan). */
    public double lastSpeed() {
        return lastSpeed;
    }

    /** The plan the last move ran with (ticks from its start; null before any). */
    public MovePlan.Plan lastPlan() {
        return lastPlan;
    }

    /** The stage of the running move (DONE when none). */
    public MoveStage stage() {
        return run == null ? MoveStage.DONE : run.stage();
    }

    /** Whether it is in a move's wind-up now. */
    public boolean windingUp() {
        return run != null && run.stage() == MoveStage.WINDUP;
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
        if (t == null || !t.isAlive() || !fighting || HeroStatus.stunned(hero) || boss.held()) {
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

    // ------------------------------------------------------------------ timing (A5: frames, DATA_ACTION, speed)

    /** How a move runs: its plan at its speed, the {@code DATA_ACTION} code its clip plays (-1: none), that speed. */
    public record Timing(MovePlan.Plan plan, int code, double speed, boolean fromFile) {
    }

    /**
     * The hero's move speed now (4.8): Haste, Mining Fatigue, Slowness and its {@code attack_speed} stat, 0.5-2.0
     * ({@link MoveTiming#speed}). The same rule for every hero; nothing else changes it.
     */
    public double speed() {
        double stat = HeroStatus.clampStat(Stat.ATTACK_SPEED, HeroStatus.percent(hero, Stat.ATTACK_SPEED));
        return MoveTiming.speed(stat, level(MobEffects.DIG_SPEED), level(MobEffects.DIG_SLOWDOWN),
                level(MobEffects.MOVEMENT_SLOWDOWN));
    }

    private int level(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e) {
        MobEffectInstance i = hero.getEffect(e);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    /**
     * How {@code m} runs now: its code from the hero's {@code visual.json} move list (the ultimates' fixed names), its
     * clip through the hero's timing files (the same chain the client plays from), the clip's frames and {@code hit}
     * markers, or the move's own frames without one; the plan at the hero's speed, held to the class minimums.
     */
    public Timing timingFor(Move m) {
        HeroDef d = def();
        HeroEntry entry = hero.entry();
        HeroLook look = entry.visual().look();
        boolean awakened = d != null && d.v2() != null && d.v2().awakened() != null && d.v2().awakened().id().equals(m.id())
                && MoveSelector.isUltimate(m);
        boolean ultimate = MoveSelector.isUltimate(m) && !awakened;
        int code = HeroActions.moveCode(m.id(), ultimate, awakened, look.moves());
        String clipName = code < 0 ? null : HeroClips.actionClip(code, look.moves(), look.states());
        HeroTimings.Clip clip = clipName == null ? null : HeroTimingFiles.clip(entry, clipName);
        if ((ultimate || awakened) && clip == null) {
            code = -1; // the client finds an ultimate's clip by its fixed name in the chain: none here, nothing to play
        }
        Frames f = m.frames();
        int[] markers = null;
        boolean fromFile = clip != null && clip.windup() >= 0 && clip.active() >= 0 && clip.recovery() >= 0;
        if (fromFile) {
            f = new Frames(clip.windup() / 20.0, clip.active() / 20.0, clip.recovery() / 20.0);
            markers = hitMarkers(clip);
        }
        MovePlan.Plan base = MovePlan.of(m, f, markers);
        double s = MoveTiming.playback(speed(), base.windup(), base.recoveryEnd() - base.activeEnd(), m.cls());
        return new Timing(MoveTiming.scale(base, s, m.cls()), code, s, fromFile);
    }

    private static int[] hitMarkers(HeroTimings.Clip c) {
        int n = 0;
        for (HeroTimings.Event e : c.events()) {
            if ("hit".equals(e.type())) {
                n++;
            }
        }
        int[] out = new int[n];
        int i = 0;
        for (HeroTimings.Event e : c.events()) {
            if ("hit".equals(e.type())) {
                out[i++] = e.tick();
            }
        }
        return out;
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
        Timing timing = timingFor(m);
        run = new Run(m, p.source(), timing, use, hero.getTarget());
        lastMove = m.id();
        lastMoveStart = now();
        lastRelease = -1;
        lastCode = timing.code();
        lastSpeed = timing.speed();
        lastPlan = timing.plan();
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
            FactionClash.LOGGER.debug("[Heroes] {} starts {} ({}) at {}: code {} speed {} plan {}", hero.heroId(), m.id(),
                    p.source(), now(), timing.code(), timing.speed(), timing.plan());
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

    // ------------------------------------------------------------------ interrupts (A5)

    /**
     * The goal stopped: a move still in its wind-up is interrupted (v1 10.3: half the cooldown back), one past it simply
     * ends; its clip stops.
     */
    public void stopRun() {
        if (run == null) {
            return;
        }
        Run r = run;
        if (r.stage() == MoveStage.WINDUP) {
            MoveSelector s = selector();
            if (s != null) {
                s.interrupted(r.move, now());
            }
        }
        r.end(true);
    }

    /**
     * Stops the running move for {@code why} when {@link MoveTiming#stops} says it does (HEROES_V2 4.8, 6.9, 6.13): a
     * stun or a stagger at any stage, a knock-back over 0.5 in a wind-up (never a {@code super_armor} move's or an
     * ultimate's), a broken channel (a 2 s recovery follows), an escape (a wind-up is cancelled, an active part plays out
     * its tick), death. A wind-up stopped gives half the cooldown back where v1 says so; the interrupt's clip plays
     * ({@code stun}, {@code stagger}, {@code interrupt}). Returns whether it stopped a move.
     */
    public boolean interrupt(Interrupt why) {
        Run r = run;
        if (r == null) {
            return false;
        }
        MoveStage st = r.stage();
        Move m = r.move;
        boolean armour = m.superArmor() || MoveSelector.isUltimate(m);
        if (!MoveTiming.stops(why, st, armour, m.channel() > 0)) {
            return false;
        }
        if (why == Interrupt.CHANNEL) {
            r.breakChannel();
            return true;
        }
        if (why == Interrupt.ESCAPE && st == MoveStage.ACTIVE) {
            r.lastActiveTick = true; // 6.13: a move in its active part plays out that tick and stops
            return true;
        }
        if (MoveTiming.refunds(why, st)) {
            MoveSelector s = selector();
            if (s != null) {
                s.interrupted(m, now());
            }
        }
        r.end(true);
        String clip = MoveTiming.clip(why);
        if (clip != null && why != Interrupt.STUN && why != Interrupt.STAGGER) {
            hero.playAction(HeroActions.actionCode(clip), 1.0, 0);
        }
        return true;
    }

    /** Discards the running move, any pick and what is in flight (death, a reload); its state and held passive stats end. */
    void reset() {
        if (run != null) {
            run.end(true);
        }
        pending = null;
        riposteOn = null;
        flights.clear();
        popDecoys();
        states.exit();
        passives.reset();
        boss.reset();
        telegraphs.clearAll();
    }

    /**
     * It was stunned (HEROES_V2 6.15, task A4): a move in its wind-up is interrupted (half the cooldown back, v1 10.3;
     * the path an escape takes in A6), one past it simply ends, a pick waiting to start is dropped; it shows its
     * {@code stun} pose.
     */
    void stunned() {
        pending = null;
        if (run != null) {
            interrupt(Interrupt.STUN);
        }
        hero.playAction(HeroActions.actionCode("stun"), 1.0, 0);
    }

    // ------------------------------------------------------------------ poise and stagger (the hook A6a completes)

    /** Its poise bar ({@code boss.poise}; null while it has no definition; {@code none()} with poise 0). */
    public BossRules.Poise poise() {
        HeroDef d = def();
        if (d == null || d.v2() == null) {
            return null;
        }
        if (poise == null || poiseFor != d) {
            poise = new BossRules.Poise(d.v2().boss().poise());
            poiseFor = d;
        }
        return poise;
    }

    /**
     * Poise damage on the hero (the {@code poise} effect, a charge of its own into a wall; A6a adds every hit): at
     * full poise it staggers, unless a {@code super_armor} move or an ultimate's wind-up protects it (6.9). Returns
     * whether it staggered.
     */
    public boolean poiseDamage(double amount) {
        BossRules.Poise p = poise();
        if (p == null || !hero.isAlive()) {
            return false;
        }
        boolean protectedNow = boss.transitioning() || run != null && (run.move.superArmor()
                || MoveSelector.isUltimate(run.move) && run.stage() == MoveStage.WINDUP);
        if (p.add(amount, now(), protectedNow)) {
            stagger();
            return true;
        }
        return false;
    }

    /**
     * The stagger (6.9; the hook of task A5, completed by A6a): the running move is interrupted (half the cooldown back
     * in its wind-up, its decal gone), it plays {@code stagger} and kneels 2.5 s ({@code combat/HeroHoldGoal}: no
     * walking, no blow, no move) taking +25% damage ({@link HeroBoss#incoming}), stars circle over its head, it says its
     * {@code stagger} line and its {@code on_stagger} passives fire.
     */
    public void stagger() {
        pending = null;
        staggerUntil = now() + BossRules.STAGGER_TICKS;
        if (run != null) {
            interrupt(Interrupt.STAGGER);
        }
        hero.playAction(HeroActions.actionCode("stagger"), 1.0, 0);
        hero.getNavigation().stop();
        hero.setDeltaMovement(hero.getDeltaMovement().multiply(0, 1, 0));
        HeroEffects.particles(hero.level(), hero, ParticleTypes.CRIT, 10);
        if (hero.level() instanceof ServerLevel sl) {
            sl.playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 0.9F, 0.8F);
        }
        boss.staggered();
        hero.say(LineEvent.STAGGER);
        trigger(PassiveBook.Event.of(Trigger.ON_STAGGER), hero.getTarget(), null);
    }

    /** Whether it is staggered now (no move starts). */
    public boolean staggered() {
        return now() < staggerUntil;
    }

    // ------------------------------------------------------------------ counter, barrier, channel (A5)

    /**
     * A blow on the hero (from the damage event, after shields and armour): while a {@code counter} stance is up
     * (its wind-up) the first melee blow from an enemy (or an arrow, when the counter takes {@code ranged}) is caught:
     * the stance's {@code absorb} share of it is taken off and the riposte answers it on the hero's next tick
     * (6.5: "the riposte is instant"). Returns the share of the blow that lands (1: not caught). A hero's damage over
     * time is no blow. The same for a player's blow and an NPC's.
     */
    public double counterBlow(DamageSource src) {
        Run r = run;
        if (r == null || r.move.form() != Form.COUNTER || r.countered || r.stage() != MoveStage.WINDUP) {
            return 1;
        }
        LivingEntity a = src.getEntity() instanceof LivingEntity l && l != hero ? l : null;
        if (a == null || HeroForms.ally(hero, a) || HeroDamage.isDot(src)) {
            return 1;
        }
        boolean melee = src.getDirectEntity() == a;
        boolean ranged = !melee && src.getDirectEntity() instanceof Projectile;
        if (!melee && !(ranged && r.move.extra().ranged())) {
            return 1;
        }
        r.countered = true;
        riposteOn = a;
        int absorb = r.move.extra().absorb() > 0 ? r.move.extra().absorb() : 80;
        return Math.max(0, 1 - absorb / 100.0);
    }

    /** Whether a counter stance is up now (its wind-up, not yet answered). */
    public boolean countering() {
        return run != null && run.move.form() == Form.COUNTER && !run.countered && run.stage() == MoveStage.WINDUP;
    }

    /**
     * Damage the hero took ({@code amount} after reductions): a channelled move breaks after its {@code channel} share
     * of max health in its wind-up and active part (6.9: a 2 s recovery).
     */
    void damaged(double amount) {
        Run r = run;
        if (r == null || r.move.channel() <= 0 || !(amount > 0)) {
            return;
        }
        MoveStage st = r.stage();
        if (st != MoveStage.WINDUP && st != MoveStage.ACTIVE) {
            return;
        }
        r.channelDamage += amount;
        if (BossRules.channelBroken(r.channelDamage, hero.getMaxHealth(), r.move.channel())) {
            interrupt(Interrupt.CHANNEL);
        }
    }

    /** {@code barrier} (6.6): blocks all damage until it has taken {@code hits} blows or {@code seconds} pass. */
    void barrier(int hits, double seconds, HeroDef.AddKind focus) {
        HeroStatus.of(hero).book().barrier(hits, seconds, now());
        barrierFocus = null;
        if (focus != null) {
            List<HeroDecoy> f = spawnAdds(focus, 1);
            if (!f.isEmpty()) {
                barrierFocus = f.get(0).getUUID();
            }
        }
        HeroEffects.particleRing(hero.level(), hero.position().add(0, 1, 0), 1.2, ParticleTypes.END_ROD);
        hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    /** The blows its barrier still blocks (0: none). */
    public int barrierHits() {
        HeroStatus s = HeroStatus.peek(hero);
        return s == null || s.book().isEmpty() ? 0 : s.book().barrierHits(now());
    }

    /**
     * Whether its barrier stops this damage (from {@link HeroEntity#hurt}, before anything else): all of it is
     * blocked; a blow from anyone (any damage with an attacker but a hero's damage over time) costs the barrier one hit,
     * and its last hit holds while its focus add lives. {@code /kill} and the void go through.
     */
    boolean barrierBlocks(DamageSource src) {
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        HeroStatus s = HeroStatus.peek(hero);
        if (s == null || s.book().isEmpty() || s.book().barrierHits(now()) <= 0) {
            return false;
        }
        if (src.getEntity() == null || HeroDamage.isDot(src)) {
            return true; // blocked, but no blow: fire, a fall, a damage over time
        }
        var result = s.book().barrierHit(now(), focusAlive());
        if (hero.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, hero.getX(), hero.getY(0.6), hero.getZ(), 4, 0.4, 0.5, 0.4, 0.05);
            sl.playSound(null, hero.getX(), hero.getY(), hero.getZ(), result == com.factionclash.logic.hero.StatusBook.BarrierHit.BROKE
                    ? SoundEvents.GLASS_BREAK : SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.2F);
        }
        return true;
    }

    private boolean focusAlive() {
        if (barrierFocus == null) {
            return false;
        }
        for (HeroDecoy d : decoys) {
            if (d.getUUID().equals(barrierFocus) && d.isAlive() && !d.popped()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ adds (A5: spawn_add's decoys)

    /**
     * {@code spawn_add} (6.11): {@code count} adds of {@code kind} beside the hero. Milestone 1 has only the
     * {@code decoy} ({@link HeroDecoy}); at most {@link HeroCatalogue#MAX_DECOYS} live a hero (the oldest pop first).
     */
    List<HeroDecoy> spawnAdds(HeroDef.AddKind kind, int count) {
        List<HeroDecoy> made = new ArrayList<>();
        if (kind != HeroDef.AddKind.DECOY || !(hero.level() instanceof ServerLevel sl)) {
            return made;
        }
        for (int i = 0; i < Math.max(1, count); i++) {
            decoys.removeIf(d -> d.isRemoved() || d.popped());
            while (decoys.size() >= HeroCatalogue.MAX_DECOYS) {
                decoys.removeFirst().pop();
            }
            HeroDecoy d = ModEntities.HERO_DECOY.get().create(sl);
            if (d == null) {
                break;
            }
            double a = hero.getRandom().nextDouble() * Math.PI * 2;
            double r = HeroDecoy.WALK_MIN + hero.getRandom().nextDouble() * (HeroDecoy.WALK_MAX - HeroDecoy.WALK_MIN);
            Vec3 at = hero.position().add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6);
            d.moveTo(at.x, at.y, at.z, hero.getYRot(), 0F);
            d.bind(hero, hero.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            sl.addFreshEntity(d);
            sl.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 8, 0.3, 0.5, 0.3, 0.02);
            decoys.addLast(d);
            made.add(d);
        }
        return made;
    }

    /** Its adds alive now (the {@code adds_alive} condition). */
    public int addsAlive() {
        int n = 0;
        for (HeroDecoy d : decoys) {
            if (d.isAlive() && !d.popped()) {
                n++;
            }
        }
        return n;
    }

    /** One of its adds is gone (popped, timed out): {@code on_add_died}; its barrier's focus breaks the barrier. */
    public void addGone(HeroDecoy d) {
        decoys.remove(d);
        if (barrierFocus != null && barrierFocus.equals(d.getUUID())) {
            barrierFocus = null;
            HeroStatus s = HeroStatus.peek(hero);
            if (s != null && s.book().breakBarrier()) {
                stagger(); // 6.6: killing the focus breaks the barrier and staggers the hero
            }
        }
        trigger(PassiveBook.Event.of(Trigger.ON_ADD_DIED), null, null);
    }

    private void popDecoys() {
        while (!decoys.isEmpty()) {
            HeroDecoy d = decoys.removeFirst();
            if (!d.popped()) {
                d.pop();
            }
        }
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

    /** The mood its phase transition or its state holds (-1: none). */
    public int stateMood() {
        int m = boss.mood();
        return m >= 0 ? m : states.mood();
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
        boss.save(h);
    }

    void load(CompoundTag h) {
        basicHits = h.getInt("basicHits");
        states.load(h);
        boss.load(h);
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

    // ------------------------------------------------------------------ flights: waves and rain impacts (A5)

    /** Something a move released that rolls on by itself (a wave's ring, a rain impact); true when it is done. */
    private interface Flight {
        boolean step();
    }

    /**
     * A {@code wave} (6.5): a ring that grows from where the hero stood at its release at {@code speed} blocks a second
     * to its {@code radius}; each enemy on the ground within 0.75 blocks of its front is hit once (jump to dodge).
     */
    private final class WaveFlight implements Flight {
        final HeroUse use;
        final Vec3 centre;
        final double radius, speed;
        final List<HeroDef.Effect> effects;
        final Set<Integer> hit = new HashSet<>();
        final ParticleOptions look;
        int age;

        WaveFlight(HeroUse use, Vec3 centre, double radius, double speed, List<HeroDef.Effect> effects, ParticleOptions look) {
            this.use = use;
            this.centre = centre;
            this.radius = radius;
            this.speed = speed;
            this.effects = effects;
            this.look = look;
        }

        @Override
        public boolean step() {
            age++;
            double front = FormGeometry.waveFront(age, speed);
            if (front > radius + FormGeometry.WAVE_BAND || !hero.isAlive()) {
                return true;
            }
            List<LivingEntity> rs = null;
            for (LivingEntity e : HeroForms.enemiesNear(hero, centre, front + FormGeometry.WAVE_BAND + 1)) {
                double dx = e.getX() - centre.x, dz = e.getZ() - centre.z;
                if (FormGeometry.waveHits(Math.sqrt(dx * dx + dz * dz), e.getBbWidth() / 2, e.getY() - centre.y,
                        e.onGround(), front, radius) && hit.add(e.getId())) {
                    if (rs == null) {
                        rs = new ArrayList<>(2);
                    }
                    rs.add(e);
                }
            }
            if (rs != null) {
                HeroEffects.apply(use, effects, 0, rs, hero);
            }
            if ((age & 1) == 0) {
                HeroEffects.particleRing(hero.level(), centre, Math.max(0.5, front), look);
            }
            return false;
        }
    }

    /** One {@code rain} impact (6.5): called at a spot, it lands {@code delay} later on enemies within 1.5 blocks of it. */
    private final class RainFlight implements Flight {
        final HeroUse use;
        final Vec3 at;
        final int landsIn;
        final List<HeroDef.Effect> effects;
        final ParticleOptions look;
        int age;
        /** Its ground disc (A6a; -1: none). */
        int decal = -1;

        RainFlight(HeroUse use, Vec3 at, int landsIn, List<HeroDef.Effect> effects, ParticleOptions look) {
            this.use = use;
            this.at = at;
            this.landsIn = landsIn;
            this.effects = effects;
            this.look = look;
        }

        @Override
        public boolean step() {
            age++;
            if (!hero.isAlive()) {
                telegraphs.clear(decal);
                return true;
            }
            if (age < landsIn) {
                if ((age & 3) == 0 && hero.level() instanceof ServerLevel sl) {
                    // the tell: a falling column over the spot (its ground disc is A6's decal)
                    sl.sendParticles(look, at.x, at.y + 3 - 3.0 * age / landsIn, at.z, 2, 0.2, 0.1, 0.2, 0);
                }
                return false;
            }
            telegraphs.clear(decal);
            HeroEffects.particleRing(hero.level(), at, FormGeometry.RAIN_IMPACT_RADIUS, look);
            HeroEffects.apply(use, effects, 0, HeroForms.enemiesNear(hero, at.add(0, 0.5, 0), FormGeometry.RAIN_IMPACT_RADIUS), hero);
            return true;
        }
    }

    // ------------------------------------------------------------------ one running move

    private static final int M_NONE = 0, M_LEAP = 1, M_DASH = 2, M_CHARGE = 3, M_SWEEP = 4, M_GRAB = 5;

    /** A move from its start to the end of its recovery. */
    final class Run {
        final Move move;
        final MoveSelector.Source source;
        final MovePlan.Plan plan;
        final Timing timing;
        final HeroUse use;
        LivingEntity target;
        Vec3 aim;
        int t;
        /** The next hit of the plan to release (each is released once, even while the active part is held). */
        int nextHit;
        boolean done;
        /** Leap, dash, charge, sweep and grab motion. */
        int motion;
        int motionTicks;
        int motionLimit;
        boolean leftGround;
        Vec3 dashDir;
        double dashSpeed;
        int dashTicks;
        final Set<Integer> dashHit = new HashSet<>();
        /** A chain's links so far. */
        final List<LivingEntity> chain = new ArrayList<>();
        /** It holds a telegraph pose until its first release (A4: a hero with no clip for the move). */
        boolean posed;
        /** The {@code DATA_ACTION} sequence it started (its clip stops with the run unless something replaced it). */
        int actionSequence = -1;
        /** The recovery ends here instead (a grab's miss, a broken channel); -1: the plan's. */
        int recoveryEnd = -1;
        /** No followup after this run (a miss, a broken channel). */
        boolean noFollowup;
        /** A counter stance caught a blow. */
        boolean countered;
        /** Damage taken in its channel. */
        double channelDamage;
        /** An escape: this active tick is its last. */
        boolean lastActiveTick;
        /** A charge into a wall. */
        boolean wallHit;
        /** The sweep's facing at its start and its length in ticks. */
        Vec3 sweepFacing;
        int sweepTicks;
        /** The receiver a grab holds. */
        LivingEntity grabbed;
        /** Its wind-up's ground decal (A6a; -1: none drawn). */
        int decal = -1;

        Run(Move move, MoveSelector.Source source, Timing timing, HeroUse use, LivingEntity target) {
            this.move = move;
            this.source = source;
            this.timing = timing;
            this.plan = timing.plan();
            this.use = use;
            this.target = target;
            this.aim = target != null ? target.position() : hero.position();
        }

        MoveStage stage() {
            if (done) {
                return MoveStage.DONE;
            }
            MoveStage st = plan.stageAt(t);
            return st == MoveStage.DONE && t < recoveryEnd() ? MoveStage.RECOVERY : st;
        }

        int recoveryEnd() {
            return Math.max(plan.recoveryEnd(), recoveryEnd);
        }

        /**
         * The wind-up starts: the hero stops and gathers, and {@code DATA_ACTION} gets the move's code at its speed
         * (4.8), or, with no clip for the move, its look's telegraph pose (A4).
         */
        void begin() {
            hero.getNavigation().stop();
            hero.setStateFlag(HeroActions.STATE_WINDUP, plan.windup() > 0);
            hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, move.superArmor());
            if (timing.code() >= 0) {
                hero.playAction(timing.code(), timing.speed(), 0);
                actionSequence = HeroActions.sequence(hero.action());
            } else {
                int pose = HeroActions.telegraphPose(move.ability().look() == null ? null : move.ability().look().pose());
                if (pose > 0 && plan.windup() > 0) {
                    hero.playAction(pose, 1.0, 0); // no clip of its own: the wind-up's telegraph pose (A4)
                    posed = true;
                }
            }
            if (hero.level() instanceof ServerLevel sl) {
                HeroEffects.particles(sl, hero, HeroEffects.particle(move.ability().look(), element(), color()), 8);
            }
            if (plan.windup() > 0) {
                // 6.4: the ground decal grows over the whole wind-up, for the players near (presentation only)
                decal = telegraphs.show(move, plan.windup(), target, aim);
            }
            // the hero's tick steps it from this tick on
        }

        /** Its decal goes (the hit, an interrupt, the end). */
        void clearDecal() {
            if (decal > 0) {
                telegraphs.clear(decal);
                decal = -1;
            }
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

        /**
         * Whether a target that dies, goes or moves out of reach calls the wind-up off (v1 10.3): not a counter (its
         * stance answers whoever strikes) and not a grab (reaching for a target that stepped away is a miss, 6.5).
         */
        boolean callsOffOnTarget() {
            return MoveSelector.needsTarget(move) && move.form() != Form.COUNTER && move.form() != Form.GRAB;
        }

        void step() {
            if (done) {
                return;
            }
            MoveStage stage = stage();
            if (stage == MoveStage.WINDUP) {
                if (callsOffOnTarget() && (target == null || !target.isAlive() || target.isRemoved()
                        || hero.distanceTo(target) > MoveSelector.reach(move) + HeroForms.RANGE_SLACK)) {
                    // v1 10.3: the target died or left range during the wind-up; the charge is not spent
                    MoveSelector s = selector();
                    if (s != null) {
                        s.cancelled(move, now());
                    }
                    end(true);
                    return;
                }
                if (target != null && target.isAlive()) {
                    aim = target.position();
                    hero.getLookControl().setLookAt(target, 30F, 30F);
                }
                hero.getNavigation().stop();
                if (t % 5 == 0) {
                    tell();
                }
            } else if (stage == MoveStage.ACTIVE && target != null && target.isAlive() && motion != M_SWEEP) {
                hero.getLookControl().setLookAt(target, 30F, 30F);
            }
            while (!done && nextHit < plan.hitCount() && plan.hit(nextHit) <= t) {
                int hit = nextHit++;
                if (hit == 0) {
                    hero.setStateFlag(HeroActions.STATE_WINDUP, false);
                    lastRelease = now();
                    unpose();
                    clearDecal();
                }
                if (target != null && target.isAlive()) {
                    aim = target.position();
                }
                release(hit);
            }
            if (done) {
                return;
            }
            if (motion != M_NONE) {
                motionTick();
                if (done) {
                    return; // the motion's end interrupted the move (a charge into a wall that staggers)
                }
            }
            if (lastActiveTick) {
                end(true);
                return;
            }
            if (t == plan.activeEnd() - 1 && motion != M_NONE && motionTicks < motionLimit) {
                return; // the active part lasts until the motion ends
            }
            t++;
            if (t == plan.activeEnd() && !done) {
                hero.setStateFlag(HeroActions.STATE_WINDUP, false);
                motion = M_NONE;
                if (use.landed && !noFollowup) {
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
            if (t >= recoveryEnd()) {
                end(true);
            }
        }

        /**
         * The run is over. {@code gap}: the string ends and the selector's next gap starts now (false only when a
         * followup takes over the recovery, 6.3). Its clip stops unless another action replaced it.
         */
        void end(boolean gap) {
            if (done) {
                return;
            }
            done = true;
            motion = M_NONE;
            grabbed = null;
            hero.setStateFlag(HeroActions.STATE_WINDUP, false);
            if (!boss.transitioning()) {
                hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
            }
            unpose();
            clearDecal();
            if (actionSequence >= 0 && HeroActions.sequence(hero.action()) == actionSequence
                    && HeroActions.code(hero.action()) == timing.code()) {
                hero.stopAction();
            }
            if (run == this) {
                run = null;
            }
            MoveSelector s = gap ? selector() : null;
            if (s != null) {
                s.moveEnded(now(), HeroActions.phase(hero.state()), hero.stateFlag(HeroActions.STATE_ENRAGED));
            }
        }

        /** A broken channel (6.9): nothing more lands, the clip shows the interruption, a 2 s recovery follows. */
        void breakChannel() {
            nextHit = plan.hitCount();
            motion = M_NONE;
            noFollowup = true;
            hero.setStateFlag(HeroActions.STATE_WINDUP, false);
            unpose();
            clearDecal();
            int from = Math.max(t, plan.windup());
            t = Math.max(t, Math.min(plan.activeEnd(), from));
            if (t < plan.activeEnd()) {
                t = plan.activeEnd();
            }
            recoveryEnd = t + MoveTiming.CHANNEL_BREAK_RECOVERY;
            hero.playAction(HeroActions.actionCode("interrupt"), 1.0, 0);
            actionSequence = -1;
        }

        /**
         * The counter's riposte (6.5): the move's effects at the one whose blow the stance caught, at once, then its
         * active part and recovery; {@code on_counter} fires. The riposte's effects keep the skill's caps.
         */
        void riposte(LivingEntity a) {
            if (done || move.form() != Form.COUNTER) {
                return;
            }
            nextHit = plan.hitCount(); // the stance's end releases nothing more
            hero.setStateFlag(HeroActions.STATE_WINDUP, false);
            unpose();
            clearDecal();
            lastRelease = now();
            if (a.isAlive() && !HeroForms.ally(hero, a)) {
                hero.getLookControl().setLookAt(a, 90F, 90F);
                aim = a.position();
                target = a;
                hero.swing(InteractionHand.MAIN_HAND);
                HeroEffects.apply(use, move.ability().effects(), 0, List.of(a), hero);
                use.landed = true;
                HeroEffects.particles(hero.level(), a, HeroEffects.particle(move.ability().look(), element(), color()), 10);
            }
            trigger(PassiveBook.Event.of(Trigger.ON_COUNTER), a, null);
            hero.say(LineEvent.COUNTER, Map.of("enemy", a.getName().getString()), null);
            t = Math.max(t, plan.windup());
        }

        /**
         * The wind-up's tell (v1 10.3): smoke where a nova, zone, leap or wave will land and around a rain's target,
         * a line along a charge, a purple ring at a grab's reach (unblockable), every 5 ticks (the growing ground decals
         * are A6's; the wind-up is the same with or without them).
         */
        void tell() {
            Ability a = move.ability();
            Level level = hero.level();
            switch (move.form()) {
                case NOVA -> HeroEffects.particleRing(level, hero.position(), Math.max(1, a.radius()), ParticleTypes.SMOKE);
                case ZONE -> HeroEffects.particleRing(level, a.zoneAt() == HeroDef.ZoneAt.TARGET ? aim : hero.position(),
                        Math.max(1, a.radius()), ParticleTypes.SMOKE);
                case LEAP -> HeroEffects.particleRing(level, aim, Math.max(1, a.radius()), ParticleTypes.SMOKE);
                case WAVE -> HeroEffects.particleRing(level, hero.position(), Math.max(1, a.radius()), ParticleTypes.SMOKE);
                case RAIN -> HeroEffects.particleRing(level, aim, Math.max(1, a.radius()), ParticleTypes.SMOKE);
                case CHARGE -> {
                    Vec3 f = HeroForms.facing(hero, aim);
                    double range = a.range() > 0 ? a.range() : 10;
                    HeroEffects.particleLine(level, hero.position().add(0, 0.1, 0),
                            hero.position().add(f.x * range, 0.1, f.z * range), ParticleTypes.SMOKE);
                }
                case GRAB -> HeroEffects.particleRing(level, hero.position(), Math.max(1, a.range()), ParticleTypes.WITCH);
                default -> {
                }
            }
        }

        // ---------------------------------------------------------------- the forms (v1 10.1, HEROES_V2 6.5)

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
                case CHARGE -> charge(a);
                case WAVE -> {
                    double speed = move.extra().speed() > 0 ? move.extra().speed() : 8;
                    double radius = a.radius() > 0 ? a.radius() : 10;
                    flights.add(new WaveFlight(use, hero.position(), radius, speed, fx,
                            HeroEffects.particle(a.look(), element(), color())));
                    hero.swing(InteractionHand.MAIN_HAND);
                }
                case RAIN -> rainImpact(a);
                case SWEEP -> sweep(a);
                case GRAB -> grab(a);
                case COUNTER -> {
                    // the stance ended with no blow to answer: nothing lands, its recovery is the punish window
                }
                case BARRAGE -> barrageShot(a, hit);
                default -> {
                    // dive (A12) is never picked (EffectRouting.FORMS)
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

        /**
         * One shot of a {@code barrage} (6.5): aimed again at the target as it fires, carrying the move's effects and
         * its {@code per_shot} list for this shot (cycled); every shot is the same use, under the same caps.
         */
        void barrageShot(Ability a, int hit) {
            Vec3 to = target != null && target.isAlive() ? target.position().add(0, target.getBbHeight() * 0.6, 0) : aim;
            List<HeroDef.Effect> fx = a.effects();
            int list = 0;
            if (!move.perShot().isEmpty()) {
                int i = hit % move.perShot().size();
                List<HeroDef.Effect> both = new ArrayList<>(fx.size() + move.perShot().get(i).size());
                both.addAll(fx);
                both.addAll(move.perShot().get(i));
                fx = both;
                list = 1 + i;
            }
            HeroProjectile.launchShot(hero, use, a, fx, list, to);
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

        /**
         * One call of a {@code rain} (6.5): a spot within its radius of the target (where it stands now, or the aim),
         * on the ground there; it lands {@code delay} seconds later (0.75-1.0: never shortened, it is the tell).
         */
        void rainImpact(Ability a) {
            Vec3 c = target != null && target.isAlive() ? target.position() : aim;
            double radius = a.radius() > 0 ? a.radius() : 5;
            double[] off = FormGeometry.discPoint(hero.getRandom().nextDouble(), hero.getRandom().nextDouble(), radius);
            Vec3 spot = ground(c.x + off[0], c.y, c.z + off[1]);
            int delay = (int) Math.max(1, HeroRules.ticks(a.delay() > 0 ? a.delay() : 0.9));
            RainFlight f = new RainFlight(use, spot, delay, a.effects(), HeroEffects.particle(a.look(), element(), color()));
            f.decal = telegraphs.impact(move, spot, delay); // 6.4: one disc per impact, gone as it lands
            flights.add(f);
        }

        /** The top of the ground at (x, z) near height y: the first solid block from 2 above down to 4 below. */
        Vec3 ground(double x, double y, double z) {
            Level level = hero.level();
            BlockPos p = BlockPos.containing(x, y + 2, z);
            for (int i = 0; i <= 6; i++) {
                BlockPos q = p.below(i);
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                    return new Vec3(x, q.getY() + 1, z);
                }
            }
            return new Vec3(x, y, z);
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
            startMotion(M_LEAP, LEAP_TIMEOUT);
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
            startMotion(M_DASH, LEAP_TIMEOUT);
            dashHit.clear();
        }

        /**
         * A {@code charge} (6.5): a straight run along the aim at its {@code speed} for its {@code range}, through
         * everyone in the way (each hit once); it stops at walls, drops and water, and a run that ends against a wall
         * with {@code wall_stun} fills the hero's own poise by 40.
         */
        void charge(Ability a) {
            if (hero.isInWater()) {
                return;
            }
            double pct = move.extra().speed() > 0 ? move.extra().speed() : 160;
            double range = a.range() > 0 ? a.range() : 10;
            dashDir = HeroForms.facing(hero, aim);
            dashSpeed = FormGeometry.chargeSpeed(pct);
            dashTicks = FormGeometry.chargeTicks(range, pct);
            hero.getNavigation().stop();
            startMotion(M_CHARGE, dashTicks + 2);
            dashHit.clear();
        }

        /**
         * A {@code sweep} (6.5): a line from the hero's eyes turning across its {@code arc} over its {@code duration};
         * each enemy it passes within its {@code range} is hit once, unless a block stands between them.
         */
        void sweep(Ability a) {
            sweepFacing = HeroForms.facing(hero, aim);
            sweepTicks = MoveTiming.at((int) Math.max(1, HeroRules.ticks(a.duration() > 0 ? a.duration() : 1.5)), timing.speed());
            dashHit.clear();
            hero.swing(InteractionHand.MAIN_HAND);
            startMotion(M_SWEEP, sweepTicks);
        }

        /**
         * A {@code grab} (6.5): the target, if it is within reach, is held at the hero's hand for 1 s (its velocity set
         * toward the hand each tick; never a teleport: it keeps its own actions), then thrown, never toward lava or a
         * drop (6.12), and the move's effects land on it. A miss leaves the hero in a 1.2 s recovery.
         */
        void grab(Ability a) {
            double range = a.range() > 0 ? a.range() : 2.5;
            LivingEntity g = target;
            boolean inReach = g != null && g.isAlive() && HeroForms.enemy(hero, g)
                    && HeroForms.distance(g, hero.position().add(0, hero.getBbHeight() / 2, 0)) <= range;
            if (!inReach) {
                // 6.5: a miss costs a 1.2 s recovery
                noFollowup = true;
                t = Math.max(t, plan.activeEnd() - 1);
                recoveryEnd = plan.activeEnd() + MoveTiming.GRAB_MISS_RECOVERY;
                return;
            }
            grabbed = g;
            hero.swing(InteractionHand.MAIN_HAND);
            startMotion(M_GRAB, BossRules.GRAB_HOLD_TICKS + 1);
        }

        void startMotion(int kind, int limit) {
            motion = kind;
            motionTicks = 0;
            motionLimit = limit;
        }

        void motionTick() {
            motionTicks++;
            switch (motion) {
                case M_LEAP -> leapTick();
                case M_DASH -> pushTick(false);
                case M_CHARGE -> pushTick(true);
                case M_SWEEP -> sweepTick();
                case M_GRAB -> grabTick();
                default -> motion = M_NONE;
            }
        }

        void leapTick() {
            if (!hero.onGround()) {
                leftGround = true;
            }
            if (leftGround && hero.onGround() || motionTicks >= LEAP_TIMEOUT) {
                motion = M_NONE;
                Ability a = move.ability();
                use.area(hero.position(), a.radius());
                HeroEffects.particleRing(hero.level(), hero.position(), a.radius(), HeroEffects.particle(a.look(), element(), color()));
                HeroEffects.apply(use, a.effects(), 0, HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), a.radius()), hero);
            }
        }

        /** A dash's or a charge's tick: the push, the stops (a wall, a drop, water), each enemy in the way once. */
        void pushTick(boolean charge) {
            boolean wall = motionTicks > 1 && hero.horizontalCollision;
            if (dashTicks <= 0 || wall || dropAhead() || charge && waterAhead()) {
                motion = M_NONE;
                hero.setDeltaMovement(hero.getDeltaMovement().multiply(0.2, 1, 0.2));
                if (charge && wall) {
                    wallHit = true;
                    HeroEffects.particles(hero.level(), hero, ParticleTypes.CRIT, 12);
                    hero.level().playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.SHIELD_BLOCK,
                            SoundSource.HOSTILE, 1F, 0.6F);
                    if (move.extra().wallStun()) {
                        poiseDamage(BossRules.WALL_POISE); // 6.9: a charge ending against a wall
                    }
                }
                return;
            }
            dashTicks--;
            Vec3 v = hero.getDeltaMovement();
            hero.setDeltaMovement(dashDir.x * dashSpeed, v.y, dashDir.z * dashSpeed);
            hero.hasImpulse = true;
            List<LivingEntity> hits = null;
            for (LivingEntity e : HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), FormGeometry.DASH_REACH)) {
                if (dashHit.add(e.getId())) {
                    if (hits == null) {
                        hits = new ArrayList<>(2);
                    }
                    hits.add(e);
                }
            }
            if (hits != null) {
                HeroEffects.apply(use, move.ability().effects(), 0, hits, hero);
            }
        }

        /** No floor within a block and a half ahead (a dash or a charge stops at walls and drops, v1 10.1). */
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

        /** Water or lava just ahead at the feet (a charge stops at water, 6.5). */
        boolean waterAhead() {
            if (hero.isInWater() || hero.isInLava()) {
                return true;
            }
            BlockPos p = BlockPos.containing(hero.getX() + dashDir.x * (dashSpeed + 0.6), hero.getY() + 0.2,
                    hero.getZ() + dashDir.z * (dashSpeed + 0.6));
            var fluid = hero.level().getFluidState(p);
            return fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
        }

        void sweepTick() {
            if (motionTicks > sweepTicks) {
                motion = M_NONE;
                return;
            }
            Ability a = move.ability();
            double arc = a.angle() > 0 ? a.angle() : 120;
            double range = a.range() > 0 ? a.range() : 8;
            double width = a.width() > 0 ? a.width() : 1;
            double from = FormGeometry.sweepAngle(motionTicks - 1, sweepTicks, arc);
            double to = FormGeometry.sweepAngle(motionTicks, sweepTicks, arc);
            double[] dir = FormGeometry.turn(sweepFacing.x, sweepFacing.z, to);
            hero.setYRot((float) Math.toDegrees(Math.atan2(-dir[0], dir[1])));
            hero.setYBodyRot(hero.getYRot());
            Vec3 eye = hero.getEyePosition();
            Vec3 tip = eye.add(dir[0] * range, 0, dir[1] * range);
            var clip = hero.level().clip(new ClipContext(eye, tip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hero));
            Vec3 end = clip.getType() == HitResult.Type.MISS ? tip : clip.getLocation();
            if ((motionTicks & 1) == 0) {
                HeroEffects.particleLine(hero.level(), eye, end, HeroEffects.particle(a.look(), element(), color()));
            }
            List<LivingEntity> hits = null;
            for (LivingEntity e : HeroForms.enemiesNear(hero, hero.position(), range + 1)) {
                if (dashHit.contains(e.getId())) {
                    continue;
                }
                double dx = e.getX() - hero.getX(), dz = e.getZ() - hero.getZ();
                if (!FormGeometry.inSweep(dx, dz, sweepFacing.x, sweepFacing.z, from, to, range, width, e.getBbWidth() / 2)) {
                    continue;
                }
                Vec3 mid = e.position().add(0, e.getBbHeight() / 2, 0);
                var ray = hero.level().clip(new ClipContext(eye, mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hero));
                if (ray.getType() != HitResult.Type.MISS) {
                    continue; // 6.5: a block between them stops the line
                }
                dashHit.add(e.getId());
                if (hits == null) {
                    hits = new ArrayList<>(2);
                }
                hits.add(e);
            }
            if (hits != null) {
                HeroEffects.apply(use, a.effects(), 0, hits, hero);
            }
        }

        /** The hand a grab holds its receiver in. */
        Vec3 hand() {
            Vec3 f = HeroForms.facing(hero, grabbed != null ? grabbed.position() : aim);
            return hero.position().add(f.x * FormGeometry.GRAB_HAND_FORWARD, FormGeometry.GRAB_HAND_UP, f.z * FormGeometry.GRAB_HAND_FORWARD);
        }

        void grabTick() {
            LivingEntity g = grabbed;
            if (g == null || !g.isAlive() || g.isRemoved() || g.distanceToSqr(hand()) > GRAB_SLIP * GRAB_SLIP) {
                grabbed = null;
                motion = M_NONE;
                return;
            }
            hero.getNavigation().stop();
            hero.getLookControl().setLookAt(g, 30F, 30F);
            if (motionTicks <= BossRules.GRAB_HOLD_TICKS) {
                Vec3 h = hand();
                double[] v = FormGeometry.grabHold(h.x - g.getX(), h.y - g.getY(), h.z - g.getZ());
                g.setDeltaMovement(v[0], v[1], v[2]);
                g.hasImpulse = true;
                g.hurtMarked = true;
                g.resetFallDistance();
                return;
            }
            throwGrabbed(g);
            grabbed = null;
            motion = M_NONE;
        }

        /**
         * The throw: away along the first of {@link FormGeometry#THROW_TRIES} whose landing is safe (no lava, fire, deep
         * drop or void: 6.12, the same for every receiver); when none is, straight ahead and softened to 0.5. Then the
         * move's effects land on it (unblockable, as every grab is).
         */
        void throwGrabbed(LivingEntity g) {
            double strength = move.extra().throwStrength() > 0 ? move.extra().throwStrength() : 2;
            double up = move.extra().up();
            FormGeometry.Impulse i = FormGeometry.throwImpulse(strength, up);
            double dist = FormGeometry.throwDistance(strength, up);
            Vec3 f = HeroForms.facing(hero, g.position());
            double[] dir = null;
            for (double deg : FormGeometry.THROW_TRIES) {
                double[] d = FormGeometry.turn(f.x, f.z, deg);
                if (!HeroEffects.landingHazard(g, d[0], d[1], dist)) {
                    dir = d;
                    break;
                }
            }
            double h = i.horizontal();
            if (dir == null) {
                dir = new double[] {f.x, f.z};
                h = BossRules.safeKnockback(h, true);
            }
            hero.swing(InteractionHand.MAIN_HAND);
            // the effects first: a blow's own knock-back must not bend the throw toward what it avoided
            HeroEffects.apply(use, move.ability().effects(), 0, List.of(g), hero);
            use.landed = true;
            if (!g.isAlive()) {
                return;
            }
            double res = 1 - Math.max(0, Math.min(1, g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)));
            g.setDeltaMovement(dir[0] * h * res, i.vertical(), dir[1] * h * res);
            g.hasImpulse = true;
            g.hurtMarked = true;
            HeroEffects.particles(hero.level(), g, HeroEffects.particle(move.ability().look(), element(), color()), 10);
        }
    }
}
