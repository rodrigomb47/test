package com.factionclash.logic.hero;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hero definition as the mod uses it (HERO_FORMAT.md, "v1"; HEROES_V2 12, "format 2", builds on it): immutable and
 * fully defaulted. {@link HeroReader} builds it from a file, {@link HeroBalance} has already applied the caps and
 * floors, so runtime code never sees an out-of-range number. Pure Java, no Minecraft classes.
 *
 * <p>The first 22 components are format 1's. {@link #v2} holds what format 2 adds (moves, states, the boss block,
 * personality, leader, talents, voice, revival, mobility...). A format-1 hero gets a {@code v2} too, mapped from its
 * own fields as HEROES_V2 12.1 says (skills as moves, the empowered state as one state, base abilities as talents,
 * boss numbers from its role and rarity), so game code reads one shape; a format-2 hero leaves format 1's
 * {@code skills}, {@code ultimate}, {@code empowered} and {@code baseAbilities} empty and keeps them in {@code v2}.
 *
 * <p>Units follow v1 section 3: time in seconds, distance in blocks, health and damage in health points, chances,
 * shares and bonuses in whole percent. Vanilla ids (potions, particles, sounds, items, enchantments) are kept without
 * the {@code minecraft:} prefix.
 */
public record HeroDef(int format, String id, String name, String title, String description, Rarity rarity,
                      String starterFor, List<String> factions, String skinModel, int color, Element element,
                      Stats stats, Map<Element, Integer> resistances, List<GearItem> gear, Behaviour behaviour,
                      BasicAttack basicAttack, List<Ability> skills, Ability ultimate, List<Passive> passives,
                      Empowered empowered, List<BaseAbility> baseAbilities, Map<LineEvent, List<String>> lines,
                      V2 v2) {

    /** No colour given: the faction colour is used. */
    public static final int NO_COLOR = -1;

    /** Every list and map of a definition is copied when it is built, so nobody can change a loaded hero. */
    public HeroDef {
        factions = List.copyOf(factions);
        resistances = frozen(new EnumMap<>(Element.class), resistances);
        gear = List.copyOf(gear);
        skills = List.copyOf(skills);
        passives = List.copyOf(passives);
        baseAbilities = List.copyOf(baseAbilities);
        Map<LineEvent, List<String>> l = new EnumMap<>(LineEvent.class);
        lines.forEach((k, v) -> l.put(k, List.copyOf(v)));
        lines = Collections.unmodifiableMap(l);
    }

    /** The same hero with another {@link V2} part. */
    public HeroDef withV2(V2 n) {
        return new HeroDef(format, id, name, title, description, rarity, starterFor, factions, skinModel, color,
                element, stats, resistances, gear, behaviour, basicAttack, skills, ultimate, passives, empowered,
                baseAbilities, lines, n);
    }

    /** The moves of either format (a format-1 hero's skills as moves, HEROES_V2 12.1). */
    public List<Move> moves() {
        return v2 == null ? List.of() : v2.moves();
    }

    /** An unmodifiable copy of {@code from} that keeps its order. */
    private static <K, V> Map<K, V> frozen(Map<K, V> into, Map<K, V> from) {
        into.putAll(from);
        return Collections.unmodifiableMap(into);
    }

    /**
     * Every word of the format is an enum constant whose id is its lower-case name. {@link #since} is the first
     * format whose files may use it: format 1's word lists hold only the words of format 1, so a format-1 file reads
     * exactly as before (HEROES_V2 12.1), and format 2's hold all of them.
     */
    public interface Word {
        default String id() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }

        default int since() {
            return 1;
        }

        /** The constants a file of {@code format} may use, in declaration order. */
        static <E extends Enum<E> & Word> List<E> inFormat(Class<E> cls, int format) {
            java.util.ArrayList<E> l = new java.util.ArrayList<>();
            for (E e : cls.getEnumConstants()) {
                if (e.since() <= format) {
                    l.add(e);
                }
            }
            return List.copyOf(l);
        }
    }

    public enum Rarity implements Word { COMMON, RARE, EPIC, LEGENDARY }

    public enum Element implements Word { PHYSICAL, FIRE, FROST, SHOCK, VENOM, BLIGHT, ARCANE, RADIANT }

    /** Combat roles; {@code controller} and {@code summoner} are format 2's (HEROES_V2 12.4). */
    public enum Role implements Word {
        VANGUARD, STRIKER, SKIRMISHER, MARKSMAN, SUPPORT, CONTROLLER(2), SUMMONER(2);

        private final int since;

        Role() {
            this(1);
        }

        Role(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }
    }

    public enum Growth implements Word { BALANCED, TOUGH, FIERCE, SWIFT, MYSTIC }

    public enum Focus implements Word { NEAREST, WEAKEST, STRONGEST, RANGED_FIRST, THREAT_TO_ALLIES, HEROES_FIRST }

    public enum Aggression implements Word { CAUTIOUS, STEADY, BOLD }

    /**
     * Whom a hero prefers to defend. {@code ALLIES} is everyone of its side, players included: v1's {@code players}
     * is read as it (equal treatment, HEROES_V2 6.15; {@link HeroCatalogue#EQUAL_READINGS}).
     */
    public enum Protects implements Word { NONE, ALLIES, WORKERS, HEART }

    public enum Raids implements Word { LEADS, JOINS, STAYS_HOME }

    public enum Style implements Word { MELEE, BOW, CROSSBOW, TRIDENT, THROWN }

    /**
     * Ability forms; the last nine are format 2's new forms (HEROES_V2 6.5, and the {@code dive} of MOBILITY_AND_WORK
     * 7.3 for heroes with an airborne trait).
     */
    public enum Form implements Word {
        MELEE, SHOT, VOLLEY, BOLT, CHAIN, CONE, LINE, NOVA, ZONE, LEAP, DASH, SELF, ALLY, ALLIES,
        COMBO(2), CHARGE(2), WAVE(2), RAIN(2), SWEEP(2), GRAB(2), COUNTER(2), BARRAGE(2), DIVE(2);

        private final int since;

        Form() {
            this(1);
        }

        Form(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }

        /** {@code self}, {@code ally} and {@code allies}: the receivers are the hero's side (v1 10.1). */
        public boolean friendly() {
            return this == SELF || this == ALLY || this == ALLIES;
        }
    }

    public enum ZoneAt implements Word { TARGET, SELF, FOLLOW_SELF }

    public enum UseWhen implements Word {
        ALWAYS, SELF_HEALTH_BELOW, ALLY_HEALTH_BELOW, TARGET_HEALTH_BELOW, ENEMIES_NEAR, TARGET_FAR, TARGET_CLOSE,
        BIG_FIGHT, OUT_OF_COMBAT
    }

    /** Who receives an effect (v1 10.2). */
    public enum To implements Word { TARGETS, SELF, ALLIES, ALLY }

    /**
     * Conditions ({@code if}, {@code prefer_when}, reactions' {@code when}). Format 2 adds HEROES_V2 6.6's, the reach
     * conditions reactions answer ({@code target_far}, {@code target_close}, {@code ally_hero_health_below}, 6.8) and
     * MOBILITY_AND_WORK 7.5's. None of them reads whether anyone is a player (6.15).
     */
    public enum ConditionType implements Word {
        TARGET_HEALTH_BELOW, TARGET_HEALTH_ABOVE, SELF_HEALTH_BELOW, SELF_HEALTH_ABOVE, TARGET_HAS_MARK,
        SELF_HAS_MARK, TARGET_BURNING, TARGET_FREEZING, TARGET_SLOWED, TARGET_STUNNED, TARGET_HAS_DOT, TARGET_IS,
        SELF_EMPOWERED, SELF_SHIELDED,
        TARGET_BLOCKING(2), TARGET_AIRBORNE(2), TARGET_DRAWING_BOW(2), TARGET_EATING(2), TARGET_BEHIND(2),
        TARGET_ABOVE(2), TARGET_BELOW(2), TARGET_UNREACHABLE(2), TARGET_FAR(2), TARGET_CLOSE(2), TARGET_FLYING(2),
        TARGET_IN_WATER(2), PHASE(2), ENRAGED(2), ENEMIES_ENGAGED(2), ALLIES_NEAR(2), ADDS_ALIVE(2),
        ALLY_HERO_HEALTH_BELOW(2), IN_HOME_BASE(2), IS_NIGHT(2), IS_RAINING(2), LOW_CEILING(2), IN_WATER(2),
        SELF_STATE(2), SELF_AIRBORNE(2), SELF_FLYING(2), SELF_CLIMBING(2), SELF_STAMINA_BELOW(2);

        private final int since;

        ConditionType() {
            this(1);
        }

        ConditionType(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }
    }

    /**
     * What a {@code target_is} condition may ask. {@code PERSON} is a player, a faction NPC or a hero alike: v1's
     * kinds {@code player} and {@code npc} are read as it, so no ability prefers or avoids players (equal treatment,
     * HEROES_V2 6.15; {@link HeroCatalogue#EQUAL_READINGS}).
     */
    public enum TargetKind implements Word { MONSTER, UNDEAD, ARTHROPOD, ILLAGER, PERSON, HERO, BOSS }

    /** Effect types; the last seven are format 2's (HEROES_V2 6.6). */
    public enum EffectType implements Word {
        DAMAGE, DOT, HEAL, SHIELD, EFFECT, STAT, KNOCKBACK, PULL, IGNITE, FREEZE, STUN, TAUNT, CLEANSE, DISPEL,
        ENERGY, COOLDOWN_CUT, MOVE_SELF, MARK, CLEAR_MARKS, EMPOWER, SUMMON,
        GUARD_BREAK(2), POISE(2), SILENCE(2), TETHER(2), BARRIER(2), SPAWN_ADD(2), CALL_GUARDS(2);

        private final int since;

        EffectType() {
            this(1);
        }

        EffectType(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }
    }

    public enum Stat implements Word {
        ATTACK, DAMAGE_DEALT, DAMAGE_TAKEN, ARMOR, SPEED, ATTACK_SPEED, HEALING_RECEIVED, KNOCKBACK_RESISTANCE,
        CRIT_DAMAGE, ENERGY_GAIN;

        /** Whether a modifier of this stat with this sign helps the receiver (v1 13.3). */
        public boolean helpful(int percent) {
            return this == DAMAGE_TAKEN ? percent < 0 : percent > 0;
        }
    }

    /** Passive triggers; the last ten are format 2's (HEROES_V2 6.6; there is no {@code on_yield}). */
    public enum Trigger implements Word {
        ALWAYS, ON_HIT, ON_CRIT, ON_HURT, ON_KILL, ON_SKILL, ON_ULTIMATE, ON_FIGHT_START, ON_LOW_HEALTH,
        ON_ALLY_LOW_HEALTH, ON_ALLY_HURT, ON_ALLY_ATTACK, ON_ALLY_ULTIMATE, ON_MARK_FULL, EVERY,
        ON_PHASE(2), ON_ENRAGE(2), ON_STAGGER(2), ON_COUNTER(2), ON_GUARD_BROKEN(2), ON_TARGET_HEAL(2),
        ON_ADD_DIED(2), ON_INTRO(2), ON_ESCAPE(2), ON_LAST_STAND(2);

        private final int since;

        Trigger() {
            this(1);
        }

        Trigger(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }
    }

    public enum PassiveForm implements Word {
        SELF, TRIGGER_ENEMY, TRIGGER_ALLY, ALLY, ALLIES, NOVA, BOLT, SHOT;

        public boolean friendly() {
            return this == SELF || this == TRIGGER_ALLY || this == ALLY || this == ALLIES;
        }
    }

    public enum Pose implements Word { NONE, SWING, BLOCK, BOW_DRAW, SPEAR_THROW, CROSSBOW_CHARGE, HORN, SPYGLASS }

    public enum Direction implements Word { BACK, FORWARD, UP }

    public enum Creature implements Word { WOLF, SNOW_GOLEM, IRON_GOLEM }

    public enum BaseType implements Word { AURA, MEND, LOOKOUT, RALLY, DRILL }

    /**
     * Line events; the rest after {@code revived} are format 2's (HEROES_V2 12.15). {@code unlock} never fires for a
     * format-2 hero (nothing unlocks), and there are no {@code yield} or {@code spared} events.
     */
    public enum LineEvent implements Word {
        SUMMONED, GREETING, IDLE, FIGHT_START, SKILL, ULTIMATE, LOW_HEALTH, KILL, ALLY_FALLEN, VICTORY, RAID_START,
        BASE_ATTACKED, LEVEL_UP, UNLOCK, FALLEN, REVIVED,
        FOUNDING(2), INTRO(2), PHASE_2(2), PHASE_3(2), ENRAGE(2), STAGGER(2), COUNTER(2), ESCAPE(2), LAST_STAND(2),
        RAID_ANNOUNCE(2), BASE_LOST(2), HERO_FALLEN(2), WORK(2), NIGHT(2), SMALL_TALK(2), PACT_OFFER(2),
        PACT_ACCEPTED(2), PACT_REFUSED(2), PEACE_OFFER(2), DECLARE_WAR(2), BETRAYAL(2), BETRAYED(2),
        STRANGER_WARNING(2), WELCOME(2), GIFT_LIKED(2), GIFT_DISLIKED(2);

        private final int since;

        LineEvent() {
            this(1);
        }

        LineEvent(int since) {
            this.since = since;
        }

        public int since() {
            return since;
        }
    }

    /** Where an effect list sits: each slot has its own caps (v1 16.1). */
    public enum Slot implements Word { ON_HIT, NTH_HIT, SKILL, ULTIMATE, PASSIVE }

    public enum GearSlot implements Word { MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET }

    public record Stats(double health, double attack, double armor, int speed, int knockbackResistance, int size,
                        Growth growth) {
    }

    /**
     * v1 8; {@code duelStyle} is format 2's (null for format 1), and {@code retreatBelowAuthored} tells whether the
     * file wrote {@code retreat_below} (an authored 0 reads as a last stand, HEROES_V2 6.13).
     */
    public record Behaviour(Role role, Focus focus, double keepDistance, Aggression aggression, Protects protects,
                            int retreatBelow, Raids raids, String duty, DuelStyle duelStyle,
                            boolean retreatBelowAuthored) {
        public Behaviour(Role role, Focus focus, double keepDistance, Aggression aggression, Protects protects,
                         int retreatBelow, Raids raids, String duty) {
            this(role, focus, keepDistance, aggression, protects, retreatBelow, raids, duty, null, false);
        }
    }

    /** One kit item: an allowlisted vanilla id, an optional custom name and its enchantments (id to level). */
    public record GearItem(String item, String name, Map<String, Integer> enchantments, GearSlot slot) {
        public GearItem {
            enchantments = frozen(new LinkedHashMap<>(), enchantments);
        }
    }

    public record NthHit(int n, List<Effect> effects) {
        public NthHit {
            effects = List.copyOf(effects);
        }
    }

    /**
     * {@code projectile}, {@code heldItem}, {@code amount}, {@code interval} and {@code element} are for style thrown;
     * {@code combo} (format 2, 1-4, default 3) is how many {@code attack.N} animations the string uses.
     */
    public record BasicAttack(Style style, String projectile, String heldItem, double amount, double interval,
                              Element element, List<Effect> onHit, NthHit everyNthHit, int combo) {
        public BasicAttack {
            onHit = List.copyOf(onHit);
        }

        public BasicAttack(Style style, String projectile, String heldItem, double amount, double interval,
                           Element element, List<Effect> onHit, NthHit everyNthHit) {
            this(style, projectile, heldItem, amount, interval, element, onHit, everyNthHit, 3);
        }
    }

    /**
     * A sum of parts (v1 12): health points plus shares of the hero's attack power, max health and armour, of the
     * receiver's max and missing health, and a number per stack of a mark.
     */
    public record Amount(double base, double attackPercent, double maxHealthPercent, double armorPercent,
                         double targetMaxHealthPercent, double targetMissingHealthPercent, String mark,
                         double perMarkStack) {
        public static Amount of(double base) {
            return new Amount(base, 0, 0, 0, 0, 0, null, 0);
        }

        /** Every part multiplied by {@code f} (the slot caps scale an over-cap ability down in proportion). */
        public Amount scaled(double f) {
            return new Amount(base * f, attackPercent * f, maxHealthPercent * f, armorPercent * f,
                    targetMaxHealthPercent * f, targetMissingHealthPercent * f, mark, perMarkStack * f);
        }
    }

    /**
     * {@code value}: a health share in percent, the least stacks of {@code mark}, blocks ({@code target_above},
     * {@code target_below}, {@code target_far}, {@code target_close}), a count ({@code enemies_engaged},
     * {@code allies_near}, {@code adds_alive}), a phase or a stamina share; {@code state} is {@code self_state}'s.
     */
    public record Condition(ConditionType type, int value, String mark, TargetKind kind, String state) {
        public Condition(ConditionType type, int value, String mark, TargetKind kind) {
            this(type, value, mark, kind, null);
        }
    }

    /** What every effect may carry (v1 13): receiver, the ally radius, a chance and a condition. */
    public record Common(To to, double radius, int chance, Condition cond) {
        public Common withTo(To t) {
            return new Common(t, radius, chance, cond);
        }
    }

    /** One effect of the catalogue (v1 13, HEROES_V2 6.6); one record per type. */
    public sealed interface Effect permits Damage, Dot, Heal, Shield, PotionEffect, StatMod, Knockback, Pull, Ignite,
            Freeze, Stun, Taunt, Cleanse, Dispel, Energy, CooldownCut, MoveSelf, Mark, ClearMarks, Empower, Summon,
            GuardBreak, Poise, Silence, Tether, Barrier, SpawnAdd, CallGuards {
        Common c();

        EffectType type();

        /** The same effect with another {@link Common}. */
        Effect with(Common c);
    }

    public record Damage(Common c, Amount amount, Element element, int drain) implements Effect {
        public EffectType type() {
            return EffectType.DAMAGE;
        }

        public Effect with(Common n) {
            return new Damage(n, amount, element, drain);
        }

        public Damage withAmount(Amount a) {
            return new Damage(c, a, element, drain);
        }
    }

    public record Dot(Common c, Amount amount, double seconds, Element element, int maxStacks) implements Effect {
        public EffectType type() {
            return EffectType.DOT;
        }

        public Effect with(Common n) {
            return new Dot(n, amount, seconds, element, maxStacks);
        }

        public Dot withAmount(Amount a) {
            return new Dot(c, a, seconds, element, maxStacks);
        }
    }

    public record Heal(Common c, Amount amount) implements Effect {
        public EffectType type() {
            return EffectType.HEAL;
        }

        public Effect with(Common n) {
            return new Heal(n, amount);
        }

        public Heal withAmount(Amount a) {
            return new Heal(c, a);
        }
    }

    public record Shield(Common c, Amount amount, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.SHIELD;
        }

        public Effect with(Common n) {
            return new Shield(n, amount, seconds);
        }

        public Shield withAmount(Amount a) {
            return new Shield(c, a, seconds);
        }
    }

    /** The {@code effect} type: a vanilla potion effect; {@code helpful} decides who may receive it. */
    public record PotionEffect(Common c, String id, int level, double seconds, boolean helpful) implements Effect {
        public EffectType type() {
            return EffectType.EFFECT;
        }

        public Effect with(Common n) {
            return new PotionEffect(n, id, level, seconds, helpful);
        }
    }

    /** The {@code stat} type; {@code seconds} 0 means held (an {@code always} passive). */
    public record StatMod(Common c, Stat stat, int percent, double seconds, int maxStacks) implements Effect {
        public EffectType type() {
            return EffectType.STAT;
        }

        public Effect with(Common n) {
            return new StatMod(n, stat, percent, seconds, maxStacks);
        }

        public boolean helpful() {
            return stat.helpful(percent);
        }
    }

    public record Knockback(Common c, double strength, double up) implements Effect {
        public EffectType type() {
            return EffectType.KNOCKBACK;
        }

        public Effect with(Common n) {
            return new Knockback(n, strength, up);
        }
    }

    public record Pull(Common c, double strength) implements Effect {
        public EffectType type() {
            return EffectType.PULL;
        }

        public Effect with(Common n) {
            return new Pull(n, strength);
        }
    }

    public record Ignite(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.IGNITE;
        }

        public Effect with(Common n) {
            return new Ignite(n, seconds);
        }
    }

    public record Freeze(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.FREEZE;
        }

        public Effect with(Common n) {
            return new Freeze(n, seconds);
        }
    }

    public record Stun(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.STUN;
        }

        public Effect with(Common n) {
            return new Stun(n, seconds);
        }

        public Stun withSeconds(double s) {
            return new Stun(c, s);
        }
    }

    public record Taunt(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.TAUNT;
        }

        public Effect with(Common n) {
            return new Taunt(n, seconds);
        }
    }

    public record Cleanse(Common c) implements Effect {
        public EffectType type() {
            return EffectType.CLEANSE;
        }

        public Effect with(Common n) {
            return new Cleanse(n);
        }
    }

    public record Dispel(Common c) implements Effect {
        public EffectType type() {
            return EffectType.DISPEL;
        }

        public Effect with(Common n) {
            return new Dispel(n);
        }
    }

    public record Energy(Common c, int amount) implements Effect {
        public EffectType type() {
            return EffectType.ENERGY;
        }

        public Effect with(Common n) {
            return new Energy(n, amount);
        }
    }

    public record CooldownCut(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.COOLDOWN_CUT;
        }

        public Effect with(Common n) {
            return new CooldownCut(n, seconds);
        }
    }

    public record MoveSelf(Common c, Direction direction, double distance) implements Effect {
        public EffectType type() {
            return EffectType.MOVE_SELF;
        }

        public Effect with(Common n) {
            return new MoveSelf(n, direction, distance);
        }
    }

    public record Mark(Common c, String mark, int stacks, int maxStacks, double seconds, boolean glow)
            implements Effect {
        public EffectType type() {
            return EffectType.MARK;
        }

        public Effect with(Common n) {
            return new Mark(n, mark, stacks, maxStacks, seconds, glow);
        }
    }

    public record ClearMarks(Common c, String mark) implements Effect {
        public EffectType type() {
            return EffectType.CLEAR_MARKS;
        }

        public Effect with(Common n) {
            return new ClearMarks(n, mark);
        }
    }

    /** {@code state}: the format-2 state it enters (null: format 1's empowered state). */
    public record Empower(Common c, double seconds, String state) implements Effect {
        public Empower(Common c, double seconds) {
            this(c, seconds, null);
        }

        public EffectType type() {
            return EffectType.EMPOWER;
        }

        public Effect with(Common n) {
            return new Empower(n, seconds, state);
        }
    }

    public record Summon(Common c, Creature creature, int count, double seconds, String name) implements Effect {
        public EffectType type() {
            return EffectType.SUMMON;
        }

        public Effect with(Common n) {
            return new Summon(n, creature, count, seconds, name);
        }
    }

    /** HEROES_V2 6.6: a raised shield is kept down for {@code seconds}, the same for every receiver. */
    public record GuardBreak(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.GUARD_BREAK;
        }

        public Effect with(Common n) {
            return new GuardBreak(n, seconds);
        }
    }

    /** Poise damage to a hero (heroes fighting each other). */
    public record Poise(Common c, int amount) implements Effect {
        public EffectType type() {
            return EffectType.POISE;
        }

        public Effect with(Common n) {
            return new Poise(n, amount);
        }
    }

    /** The receiver cannot start moves (a player or an ordinary NPC has none, so nothing changes for either). */
    public record Silence(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.SILENCE;
        }

        public Effect with(Common n) {
            return new Silence(n, seconds);
        }
    }

    /** Pulled back when it goes farther than {@code max} blocks from the anchor, for {@code seconds}. */
    public record Tether(Common c, double seconds, double max) implements Effect {
        public EffectType type() {
            return EffectType.TETHER;
        }

        public Effect with(Common n) {
            return new Tether(n, seconds, max);
        }
    }

    /** Blocks all damage to the hero until it has taken {@code hits} hits; {@code focus} is an optional focus add. */
    public record Barrier(Common c, int hits, double seconds, AddKind focus) implements Effect {
        public EffectType type() {
            return EffectType.BARRIER;
        }

        public Effect with(Common n) {
            return new Barrier(n, hits, seconds, focus);
        }
    }

    /** Spawns adds of the catalogue (HEROES_V2 6.11); milestone 1 has only {@code decoy}. */
    public record SpawnAdd(Common c, AddKind add, int count) implements Effect {
        public EffectType type() {
            return EffectType.SPAWN_ADD;
        }

        public Effect with(Common n) {
            return new SpawnAdd(n, add, count);
        }
    }

    /** Real soldiers of its faction within 32 blocks take the hero's target; nothing is spawned. */
    public record CallGuards(Common c, int count) implements Effect {
        public EffectType type() {
            return EffectType.CALL_GUARDS;
        }

        public Effect with(Common n) {
            return new CallGuards(n, count);
        }
    }

    /** Particles, colour, sound and pose of an ability (v1 10.5); {@code particle} null = the element's. */
    public record Look(String particle, int color, String sound, Pose pose, boolean lightning, boolean fangs) {
    }

    /**
     * What {@link HeroBalance} computed for an ability or passive: its weight (v1 15), its cooldown floor (skills and
     * passives) and, for the ultimate, the least time between two uses (v1 14).
     */
    public record Numbers(double weight, double floor, double minGap) {
        public static final Numbers NONE = new Numbers(0, 0, 0);
    }

    /**
     * A skill or the ultimate (v1 10). Form fields a form does not use keep their defaults; {@code cooldown},
     * {@code charges} and {@code energyGain} are for skills, {@code chargeRate} for the ultimate.
     */
    public record Ability(Slot slot, String name, String description, Form form, double range, double radius,
                          int count, int angle, double width, int hits, double hitInterval, String projectile,
                          ZoneAt zoneAt, double delay, double duration, double interval, double windup,
                          double cooldown, int charges, int energyGain, int chargeRate, UseWhen useWhen,
                          double useValue, int unlockLevel, List<Effect> effects, Look look, String line,
                          Numbers numbers) {
        public Ability {
            effects = List.copyOf(effects);
        }

        /** Pulses of a lasting zone: 1 + floor(duration / interval); 1 for every other form. */
        public int pulses() {
            return form == Form.ZONE && duration > 0 ? 1 + (int) Math.floor(duration / interval + 1e-9) : 1;
        }

        /** How many times the effects land on one receiver: {@code hits} for the forms that use it. */
        public int effectiveHits() {
            return HeroCatalogue.formUsesHits(form) ? hits : 1;
        }

        public Ability withEffects(List<Effect> e) {
            return new Ability(slot, name, description, form, range, radius, count, angle, width, hits, hitInterval,
                    projectile, zoneAt, delay, duration, interval, windup, cooldown, charges, energyGain, chargeRate,
                    useWhen, useValue, unlockLevel, List.copyOf(e), look, line, numbers);
        }

        public Ability withBalance(double newCooldown, Numbers n) {
            return new Ability(slot, name, description, form, range, radius, count, angle, width, hits, hitInterval,
                    projectile, zoneAt, delay, duration, interval, windup, newCooldown, charges, energyGain,
                    chargeRate, useWhen, useValue, unlockLevel, effects, look, line, n);
        }
    }

    /** A passive (v1 17); {@code triggerValue} is the health share or, for {@code every}, the period in seconds. */
    public record Passive(String name, String description, Trigger trigger, double triggerValue, String mark,
                          int chance, double cooldown, PassiveForm form, double radius, String projectile,
                          Condition cond, List<Effect> effects, Look look, String line, int unlockLevel,
                          Numbers numbers, boolean oncePerFight) {
        public Passive {
            effects = List.copyOf(effects);
        }

        public Passive(String name, String description, Trigger trigger, double triggerValue, String mark, int chance,
                       double cooldown, PassiveForm form, double radius, String projectile, Condition cond,
                       List<Effect> effects, Look look, String line, int unlockLevel, Numbers numbers) {
            this(name, description, trigger, triggerValue, mark, chance, cooldown, form, radius, projectile, cond,
                    effects, look, line, unlockLevel, numbers, false);
        }

        public Passive withEffects(List<Effect> e) {
            return new Passive(name, description, trigger, triggerValue, mark, chance, cooldown, form, radius,
                    projectile, cond, List.copyOf(e), look, line, unlockLevel, numbers, oncePerFight);
        }

        public Passive withBalance(double newCooldown, double newTriggerValue, Numbers n) {
            return new Passive(name, description, trigger, newTriggerValue, mark, chance, newCooldown, form, radius,
                    projectile, cond, effects, look, line, unlockLevel, n, oncePerFight);
        }
    }

    public record StatBonus(Stat stat, int percent) {
    }

    /** The optional state the {@code empower} effect enters (v1 18). */
    public record Empowered(String name, String description, List<StatBonus> stats, List<Effect> onHit,
                            String particle, int color, int size, boolean skillsLocked, String line) {
        public Empowered {
            stats = List.copyOf(stats);
            onHit = List.copyOf(onHit);
        }

        public Empowered withOnHit(List<Effect> e) {
            return new Empowered(name, description, stats, List.copyOf(e), particle, color, size, skillsLocked, line);
        }
    }

    /** A non-combat ability (v1 21); HEROES_V2 7.2 reads these as talents. */
    public sealed interface BaseAbility permits Aura, Mend, Lookout, Rally, Drill {
        String name();

        int unlockLevel();

        BaseType type();
    }

    public record Aura(String name, int unlockLevel, String affix, int stacks, double radius, List<String> roles)
            implements BaseAbility {
        public Aura {
            roles = List.copyOf(roles);
        }

        public BaseType type() {
            return BaseType.AURA;
        }
    }

    public record Mend(String name, int unlockLevel, int percent, double every, double radius) implements BaseAbility {
        public BaseType type() {
            return BaseType.MEND;
        }
    }

    public record Lookout(String name, int unlockLevel, double radius) implements BaseAbility {
        public BaseType type() {
            return BaseType.LOOKOUT;
        }
    }

    public record Rally(String name, int unlockLevel, int percent) implements BaseAbility {
        public BaseType type() {
            return BaseType.RALLY;
        }
    }

    public record Drill(String name, int unlockLevel, int xp, double radius) implements BaseAbility {
        public BaseType type() {
            return BaseType.DRILL;
        }
    }

    /** The reference attack power of v1 6.2 (caps and floors): the style's power plus {@code stats.attack}. */
    public double referenceAttack() {
        return HeroCatalogue.referenceAttack(basicAttack.style(), basicAttack.amount(), stats.attack());
    }

    // ================================================================== format 2 (HEROES_V2 12)

    public enum Gender implements Word { FEMALE, MALE }

    /** HEROES_V2 12.3: how a one-on-one fight with the hero feels. */
    public enum DuelStyle implements Word { RUSHDOWN, ZONER, GRAPPLER, COUNTER, TRAPPER, ALL_ROUNDER, STANCE, SUMMONER }

    /** HEROES_V2 6.4: a move's class sets its least wind-up and recovery (seconds); the order is light to heavy. */
    public enum MoveClass implements Word {
        LIGHT(0.4, 0.3), HEAVY(0.8, 0.8), AREA(1.0, 0.8), ULTIMATE(1.5, 1.5);

        public final double minWindup, minRecovery;

        MoveClass(double minWindup, double minRecovery) {
            this.minWindup = minWindup;
            this.minRecovery = minRecovery;
        }

        public boolean atLeast(MoveClass o) {
            return ordinal() >= o.ordinal();
        }
    }

    /** HEROES_V2 6.3: close up to 4 blocks, mid 4-10, far 10-24. */
    public enum Band implements Word { CLOSE, MID, FAR }

    /** HEROES_V2 6.4 and 12.7: the ground decal shape of a move's telegraph. */
    public enum Decal implements Word { DISC, STRIP, FAN, RING, LANDING, IMPACTS, NONE }

    /** HEROES_V2 6.9. */
    public enum WeakPoint implements Word {
        BACK, RECOVERY, CHARGE_WALL, CHANNEL, IRON, GOLD, SNOWBALL, WATER, BELL, LIGHT, CRIT_ABOVE
    }

    /** HEROES_V2 6.10's hard enrage triggers, by a player or an NPC alike. */
    public enum EnrageTrigger implements Word { ALLY_HERO_DIED, HEART_ATTACKED, FAVOURED_HURT }

    public enum Arena implements Word { OPEN, TIGHT, HOME }

    /** HEROES_V2 Appendix A, in its order. */
    public enum Temperament implements Word {
        HOT_BLOODED, STOIC, COOL_HEADED, TSUNDERE, SHY, SWEET_NATURED, YANDERE, GENKI, LONE_WOLF, PROUD_NOBLE,
        DELINQUENT, CHUUNIBYOU, AIRHEAD, BIG_SIBLING, TRICKSTER, SLEEPY, BATTLE_MANIAC, GENTLE_GIANT, SCHEMER,
        RIGHTEOUS, MELANCHOLIC, GLUTTON, PERFECTIONIST, CHARMER, ANCIENT_SAGE, COWARD_WHO_RISES, GRUFF_VETERAN,
        ECCENTRIC_GENIUS, MERCHANT, TYRANT
    }

    /** HEROES_V2 5.4: the eight traits, 0-100. */
    public enum Trait implements Word { AGGRESSION, HONOUR, AMBITION, CAUTION, GREED, SOCIABILITY, LOYALTY, VOLATILITY }

    /** HEROES_V2 6.13: what a losing hero does. There is no yield. */
    public enum WhenLosing implements Word { ESCAPE, LAST_STAND }

    /** HEROES_V2 6.13: where an escaping hero fights to the death anyway. */
    public enum StandsFor implements Word { HOME, LEADER }

    public enum Culture implements Word { EAST, WEST, MIXED }

    /** HEROES_V2 Appendix B, with the family's seat noun and the milestone that builds it. */
    public enum BaseStyle implements Word {
        EMBER_FORTRESS("Keep", 0), GROVE_LODGE("Lodge", 0), TIDE_PORT("Port", 0), STONE_HOLD("Hold", 0),
        SHRINE_PAVILION("Shrine", 1), CHERRY_COURT("Court", 1), BAMBOO_VILLAGE("Village", 1), FROST_HALL("Hall", 1),
        DARK_MANOR("Manor", 1), DEEPSLATE_KEEP("Keep", 1), DESERT_COURT("Court", 2), IVORY_PALACE("Palace", 2),
        JUNGLE_HAVEN("Haven", 2), TUFF_WORKSHOP("Workshop", 2), MUD_TOWN("Town", 2), CORAL_PORT("Port", 2);

        public final String seat;
        /** 0: a legacy family; otherwise the milestone that builds it. */
        public final int milestone;

        BaseStyle(String seat, int milestone) {
            this.seat = seat;
            this.milestone = milestone;
        }
    }

    public enum EconomyFocus implements Word {
        MILITARY, MINING, FARMING, FOOD, TRADE, CRAFT, LORE, BUILDING, SCOUTING, DEFENCE, GROWTH, PEOPLE
    }

    /** HEROES_V2 7.2's catalogue of 35, with the milestone that makes each live (0: waits for its system). */
    public enum TalentId implements Word {
        AURA(1), MASTER_CHEF(2), FORGEMASTER(2), SURVEYOR(1), FORESTER(2), HARVEST_BLESSING(2), BEASTMASTER(2),
        ANGLER_LORD(1), EMBER_KEEPER(1), QUARTERMASTER(2), RALLY(1), MENTOR(1), RENOWN(2), DRILL(1),
        FESTIVAL_HOST(2), STEWARD(2), MEND(1), TRIAGE(2), REVIVAL_RITE(2), PURIFIER(1), LOOKOUT(1), CARTOGRAPHER(1),
        SPYMASTER(1), PATHFINDER(2), ENVOY(2), INTIMIDATION(2), MERCHANT_PRINCE(1), TRIBUTE_MASTER(3),
        SAGE_OF_LETTERS(1), ARCHITECT(2), ALCHEMIST(0), ENGINEER(0), WARDEN_OF_WALLS(1), TACTICIAN(2),
        WAR_DRUMMER(2);

        public final int milestone;

        TalentId(int milestone) {
            this.milestone = milestone;
        }
    }

    /** HEROES_V2 12.14: the note-block instruments of the fallback voice. */
    public enum Blip implements Word { HARP, BELL, CHIME, FLUTE, XYLOPHONE, BIT, HAT, BASS, GUITAR, PLING }

    public enum RecruitCondition implements Word { DUEL, PAYMENT, REPUTATION, GIFT, VISITS, CAUSE, RESCUE }

    public enum Bond implements Word { FRIEND, RIVAL, SIBLING, MENTOR, STUDENT, SWORN_ENEMY, ADMIRER }

    /** The hero's own revival (HEROES_V2 12.3): where it rises. */
    public enum RiseWhere implements Word { HOME, WHERE_IT_FELL, NEAREST_BASE }

    /** When it rises: after the delay, or at the first nightfall after it. */
    public enum RiseAt implements Word { ANY, NIGHT }

    /** HEROES_V2 6.11's adds; only {@code decoy} is spawned in milestone 1 ({@link #milestone}). */
    public enum AddKind implements Word {
        DECOY(1), WISP(2), PAPER_FAMILIAR(2), FLOWER_SPRITE(2), DRONE(2), FOCUS_CRYSTAL(2), IMP(2), HATCHLING(2),
        HELPER_ANIMAL(2);

        public final int milestone;

        AddKind(int milestone) {
            this.milestone = milestone;
        }
    }

    /** MOBILITY_AND_WORK 1.3: nine families and the two footings (secondary only). */
    public enum MobilityTrait implements Word {
        RUNNER, CHARGER, LEAPER, ACROBAT, CLIMBER, GLIDER, FLYER, HOVER, SWIMMER, SNOWSTRIDE, EMBER_STRIDE;

        public boolean footing() {
            return this == SNOWSTRIDE || this == EMBER_STRIDE;
        }

        /** Leaves the ground by its own power (MOBILITY_AND_WORK 7.3: the {@code dive}). */
        public boolean airborne() {
            return this == LEAPER || this == ACROBAT || this == GLIDER || this == FLYER;
        }
    }

    /** MOBILITY_AND_WORK 8.1: what carries a flyer, a glider or a hovering hero. */
    public enum Carrier implements Word {
        WINGS, BROOM, CLOUD, SWORD, CRANE, JETS, LIGHT, CAPE, FANS, KITE, CANOPY, LEVITATION
    }

    public enum Avoid implements Word { WATER, LAVA, SNOW, HEIGHTS }

    /** The 16 vanilla dyes. */
    public enum Dye implements Word {
        WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY, LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN,
        RED, BLACK
    }

    /** A move's wind-up (the tell), active part and recovery (the punish window), in seconds (HEROES_V2 6.4). */
    public record Frames(double windup, double active, double recovery) {
        public double total() {
            return windup + active + recovery;
        }
    }

    /** One followup (HEROES_V2 6.3); {@code max} caps how often a move that follows itself repeats (0: the string limit). */
    public record Followup(String move, int chance, int max) {
    }

    /** The ground decal: its shape and colour ({@link #NO_COLOR}: the hero's accent, or red for an ultimate). */
    public record Telegraph(Decal shape, int color) {
    }

    /** One strike of a {@code combo}: its own timing, splash radius and effects. */
    public record Strike(double windup, double active, double radius, List<Effect> effects) {
        public Strike {
            effects = List.copyOf(effects);
        }

        public Strike withEffects(List<Effect> e) {
            return new Strike(windup, active, radius, e);
        }
    }

    /**
     * The new forms' own numbers (HEROES_V2 6.5) that format 1's {@link Ability} has no field for: a charge's speed in
     * percent or a wave's in blocks per second, the charge's wall stun, the number of waves, a grab's throw and lift,
     * a counter's stance seconds, absorbed share and whether it counters arrows. Unused fields are 0 or false. Other
     * new-form numbers live in the ability's own fields: range, radius, {@code count} (rain impacts, barrage shots),
     * {@code angle} (a sweep's arc), {@code width}, {@code duration}, {@code delay} (rain impact delay) and
     * {@code interval} (between waves, or shots). {@code diveFrom}: where a dive starts (null on other forms).
     */
    public record FormExtra(double speed, boolean wallStun, int waves, double throwStrength, double up,
                            double stance, int absorb, boolean ranged, DiveFrom diveFrom) {
        public static final FormExtra NONE = new FormExtra(0, false, 0, 0, 0, 0, 0, false, null);

        public FormExtra(double speed, boolean wallStun, int waves, double throwStrength, double up, double stance,
                         int absorb, boolean ranged) {
            this(speed, wallStun, waves, throwStrength, up, stance, absorb, ranged, null);
        }

        public FormExtra withDiveFrom(DiveFrom d) {
            return new FormExtra(speed, wallStun, waves, throwStrength, up, stance, absorb, ranged, d);
        }
    }

    /**
     * Where a {@code dive} starts (MOBILITY_AND_WORK 7.3): from the air (flyers and gliders) or from the apex of the
     * hero's own jump (leapers and acrobats). Null on every other form.
     */
    public enum DiveFrom implements Word { AIR, APEX }

    /**
     * A format-2 move (HEROES_V2 12.7): a v1 ability ({@link #ability}: form, form fields, effects, look, line,
     * cooldown, charges...) plus the move fields. {@code phases} are the boss phases it belongs to; {@code anim} the
     * animation that plays it; {@code channel} the share of max health whose damage breaks it (0: not channelled).
     */
    public record Move(String id, Ability ability, MoveClass cls, Frames frames, List<Band> bands, int weight,
                       List<Integer> phases, Condition preferWhen, List<Followup> followups, boolean repeatable,
                       boolean breaksGuard, boolean unblockable, boolean superArmor, int channel, Telegraph telegraph,
                       FormExtra extra, List<Strike> strikes, List<List<Effect>> perShot, String anim) {
        public Move {
            bands = List.copyOf(bands);
            phases = List.copyOf(phases);
            followups = List.copyOf(followups);
            strikes = List.copyOf(strikes);
            List<List<Effect>> ps = new java.util.ArrayList<>();
            for (List<Effect> l : perShot) {
                ps.add(List.copyOf(l));
            }
            perShot = List.copyOf(ps);
        }

        public Form form() {
            return ability.form();
        }

        public Move withAbility(Ability a) {
            return new Move(id, a, cls, frames, bands, weight, phases, preferWhen, followups, repeatable, breaksGuard,
                    unblockable, superArmor, channel, telegraph, extra, strikes, perShot, anim);
        }

        public Move withClass(MoveClass c, Frames f, Telegraph t) {
            return new Move(id, ability, c, f, bands, weight, phases, preferWhen, followups, repeatable, breaksGuard,
                    unblockable, superArmor, channel, t, extra, strikes, perShot, anim);
        }

        public Move withParts(List<Strike> s, List<List<Effect>> p) {
            return new Move(id, ability, cls, frames, bands, weight, phases, preferWhen, followups, repeatable,
                    breaksGuard, unblockable, superArmor, channel, telegraph, extra, s, p, anim);
        }

        public Move withFollowups(List<Followup> f) {
            return new Move(id, ability, cls, frames, bands, weight, phases, preferWhen, f, repeatable, breaksGuard,
                    unblockable, superArmor, channel, telegraph, extra, strikes, perShot, anim);
        }

        public Move withPhases(List<Integer> p) {
            return new Move(id, ability, cls, frames, bands, weight, p, preferWhen, followups, repeatable, breaksGuard,
                    unblockable, superArmor, channel, telegraph, extra, strikes, perShot, anim);
        }

        /** Every effect list of the move: its effects, each strike's and each per-shot list. */
        public List<List<Effect>> effectLists() {
            List<List<Effect>> l = new java.util.ArrayList<>();
            l.add(ability.effects());
            for (Strike s : strikes) {
                l.add(s.effects());
            }
            l.addAll(perShot);
            return l;
        }
    }

    /** A named state (HEROES_V2 12.9), the generalised empowered state of v1 18. */
    public record State(String name, String description, List<StatBonus> stats, List<Effect> onHit, String particle,
                        int color, int size, boolean skillsLocked, List<String> moves, List<String> show,
                        List<String> hide, String face, String line) {
        public State {
            stats = List.copyOf(stats);
            onHit = List.copyOf(onHit);
            moves = List.copyOf(moves);
            show = List.copyOf(show);
            hide = List.copyOf(hide);
        }

        public State withStats(List<StatBonus> s, List<Effect> o) {
            return new State(name, description, s, o, particle, color, size, skillsLocked, moves, show, hide, face,
                    line);
        }
    }

    /** A phase transition: its length, line, shockwave (radius, strength), effects on the hero's side and face. */
    public record Transition(double seconds, String line, double shockRadius, double shockStrength,
                             List<Effect> effects, String face) {
        public Transition {
            effects = List.copyOf(effects);
        }

        public static final Transition DEFAULT = new Transition(1.0, null, 4, 2, List.of(), null);

        public Transition withEffects(List<Effect> e) {
            return new Transition(seconds, line, shockRadius, shockStrength, e, face);
        }
    }

    /** One add a move or a phase may spawn. */
    public record AddSpec(AddKind id, int count) {
    }

    /**
     * A boss phase (HEROES_V2 6.7): {@code at} the health share where it begins, the moves and states it has, its
     * adds, stat bonuses and transition.
     */
    public record Phase(int at, List<String> moves, List<String> states, List<AddSpec> adds, List<StatBonus> stats,
                        Transition transition) {
        public Phase {
            moves = List.copyOf(moves);
            states = List.copyOf(states);
            adds = List.copyOf(adds);
            stats = List.copyOf(stats);
        }

        public Phase withParts(List<String> m, List<StatBonus> s, Transition t) {
            return new Phase(at, m, states, adds, s, t);
        }
    }

    /** HEROES_V2 6.8: a reaction replaces the next pick when {@code when} holds and the chance passes. */
    public record Reaction(Condition when, String move, int chance, double cooldown) {
    }

    /** A weak point and the line or prop that hints at it ({@code hint} null: a visible prop). */
    public record WeakSpot(WeakPoint point, String hint) {
    }

    /** {@code after} 0: the game's default (180 s, 240 s while leading). */
    public record Enrage(double after, List<EnrageTrigger> triggers, String line) {
        public Enrage {
            triggers = List.copyOf(triggers);
        }

        public static final Enrage DEFAULT = new Enrage(0, List.of(), null);
    }

    public record Intro(String line, String title) {
    }

    /**
     * The boss block as it is after the owner's decisions of 2026-11-12 and 2026-11-13 (HEROES_V2 12.10): no
     * damage to players, no defeat or yield, no attack tokens, no health per player. {@code healthScale} is the
     * file's or the rarity's; a leading hero adds 50 in the game ({@link #healthScale(boolean)}).
     */
    public record Boss(int healthScale, int maxHitShare, int poise, List<Phase> phases, List<Reaction> reactions,
                       List<WeakSpot> weakPoints, Enrage enrage, List<AddSpec> adds, Intro intro, boolean rebirth,
                       Arena arena) {
        public Boss {
            phases = List.copyOf(phases);
            reactions = List.copyOf(reactions);
            weakPoints = List.copyOf(weakPoints);
            adds = List.copyOf(adds);
        }

        public int healthScale(boolean leading) {
            return healthScale + (leading ? HeroCatalogue.LEADER_HEALTH_BONUS : 0);
        }

        /** The health share at which the last phase begins (100 with one phase). */
        public int lastPhaseAt() {
            return phases.isEmpty() ? 100 : phases.get(phases.size() - 1).at();
        }

        public Boss withPhases(List<Phase> p) {
            return new Boss(healthScale, maxHitShare, poise, p, reactions, weakPoints, enrage, adds, intro, rebirth,
                    arena);
        }
    }

    /**
     * HEROES_V2 12.11. {@code traits} holds all eight (the temperament's defaults where the file gave none);
     * {@code whenLosing} and {@code standsFor} are what the file wrote (null: not written).
     */
    public record Personality(Temperament temperament, Map<Trait, Integer> traits, WhenLosing whenLosing,
                              List<StandsFor> standsFor, List<String> likes, List<String> dislikes,
                              List<String> quirks) {
        public Personality {
            traits = frozen(new EnumMap<>(Trait.class), traits);
            standsFor = standsFor == null ? null : List.copyOf(standsFor);
            likes = List.copyOf(likes);
            dislikes = List.copyOf(dislikes);
            quirks = List.copyOf(quirks);
        }

        public int trait(Trait t) {
            return traits.getOrDefault(t, 50);
        }
    }

    /**
     * How a losing fight ends for this hero (HEROES_V2 6.13), worked out when it loads: its kind, where it stands
     * anyway (an escaping hero only), its escape threshold {@code fleeAt} in percent of max health (the health of
     * its last-stand beat for a last-stand hero) and the {@code retreat_below} it came from.
     */
    public record EndOfFight(WhenLosing kind, List<StandsFor> standsFor, int fleeAt, int retreatBelow) {
        public EndOfFight {
            standsFor = List.copyOf(standsFor);
        }

        /** "escapes under 15%, never from its home" or "fights to the death": the sheet's line. */
        public String sheetLine() {
            return EscapeRules.sheetLine(this);
        }
    }

    public record BannerLayer(String pattern, Dye color) {
    }

    public record Banner(Dye base, List<BannerLayer> patterns) {
        public Banner {
            patterns = List.copyOf(patterns);
        }
    }

    /** HEROES_V2 12.12; colours are {@link #NO_COLOR} when not given (the identity layer fills them). */
    public record Leader(boolean eligible, String title, List<String> factionNames, String shortName, String motto,
                         int primary, int secondary, int accent, Banner banner, BaseStyle baseStyle, Culture culture,
                         List<EconomyFocus> economyFocus, String seat) {
        public Leader {
            factionNames = List.copyOf(factionNames);
            economyFocus = List.copyOf(economyFocus);
        }

        public static final Leader DEFAULT = new Leader(true, "", List.of(), null, "", NO_COLOR, NO_COLOR, NO_COLOR,
                null, null, null, List.of(), null);
    }

    /** One talent (HEROES_V2 7.2, 12.13); {@code affix} and {@code roles} only for {@code aura}. */
    public record Talent(TalentId id, boolean realm, String affix, List<String> roles) {
        public Talent {
            roles = List.copyOf(roles);
        }
    }

    public record Voice(Blip blip, double pitch, int rate, double hurtPitch) {
    }

    public record Recruit(RecruitCondition condition, int value) {
    }

    public record Relationship(String hero, Bond kind) {
    }

    /** The hero's own death ability (HEROES_V2 12.3, 3.4); its seconds still meet the floor of the revival rules. */
    public record Revival(int seconds, RiseWhere where, RiseAt at, String line) {
    }

    public record TraitTier(MobilityTrait trait, int tier) {
    }

    /** {@code part}: a part id, {@code wings} or {@code weapon} (the weapon prop is the mount), or null. */
    public record CarrierSpec(Carrier kind, String part) {
    }

    /** MOBILITY_AND_WORK 7.4: the boss phases with aerial windows and their limits. */
    public record Aerial(List<Integer> phases, double maxAir, int dives, int weight) {
        public Aerial {
            phases = List.copyOf(phases);
        }
    }

    /** MOBILITY_AND_WORK 8. {@code secondary}, {@code carrier} and {@code aerial} may be null. */
    public record Mobility(TraitTier primary, TraitTier secondary, CarrierSpec carrier, Aerial aerial, boolean travel,
                           List<Avoid> avoid) {
        public Mobility {
            avoid = List.copyOf(avoid);
        }
    }

    /**
     * Everything format 2 adds (HEROES_V2 12.3). {@code ultimate} and {@code awakened} are moves of class ultimate
     * (either may be null); {@code generatedHash} is the content hash herogen wrote ({@code null} without a generated
     * block). Optional parts with nothing authored are null ({@code recruit}, {@code revival}, {@code mobility}: a
     * format-1 hero has no mobility).
     */
    public record V2(Gender gender, String archetype, String hybrid, List<Move> moves, Move ultimate, Move awakened,
                     List<State> states, Boss boss, Personality personality, EndOfFight end, Leader leader,
                     List<Talent> talents, Voice voice, Recruit recruit, List<Relationship> relationships,
                     Revival revival, Mobility mobility, String generatedHash) {
        public V2 {
            moves = List.copyOf(moves);
            states = List.copyOf(states);
            talents = List.copyOf(talents);
            relationships = List.copyOf(relationships);
        }

        public Move move(String id) {
            for (Move m : moves) {
                if (m.id().equals(id)) {
                    return m;
                }
            }
            if (ultimate != null && ultimate.id().equals(id)) {
                return ultimate;
            }
            return awakened != null && awakened.id().equals(id) ? awakened : null;
        }

        public State state(String name) {
            for (State s : states) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return null;
        }

        /** The moves, then the ultimate and the awakened ultimate: every move the selector may run. */
        public List<Move> allMoves() {
            List<Move> l = new java.util.ArrayList<>(moves);
            if (ultimate != null) {
                l.add(ultimate);
            }
            if (awakened != null) {
                l.add(awakened);
            }
            return List.copyOf(l);
        }

        public V2 withParts(List<Move> m, Move u, Move a, List<State> s, Boss b) {
            return new V2(gender, archetype, hybrid, m, u, a, s, b, personality, end, leader, talents, voice, recruit,
                    relationships, revival, mobility, generatedHash);
        }

        public V2 withEnd(EndOfFight e) {
            return new V2(gender, archetype, hybrid, moves, ultimate, awakened, states, boss, personality, e, leader,
                    talents, voice, recruit, relationships, revival, mobility, generatedHash);
        }
    }
}
