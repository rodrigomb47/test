package com.factionclash.logic.hero;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hero definition as the mod uses it (HERO_FORMAT.md, "v1"; HEROES_V2 12 builds on it): immutable and fully
 * defaulted. {@link HeroReader} builds it from a file, {@link HeroBalance} has already applied the caps and floors, so
 * runtime code never sees an out-of-range number. Pure Java, no Minecraft classes.
 *
 * <p>Units follow v1 section 3: time in seconds, distance in blocks, health and damage in health points, chances,
 * shares and bonuses in whole percent. Vanilla ids (potions, particles, sounds, items, enchantments) are kept without
 * the {@code minecraft:} prefix.
 */
public record HeroDef(int format, String id, String name, String title, String description, Rarity rarity,
                      String starterFor, List<String> factions, String skinModel, int color, Element element,
                      Stats stats, Map<Element, Integer> resistances, List<GearItem> gear, Behaviour behaviour,
                      BasicAttack basicAttack, List<Ability> skills, Ability ultimate, List<Passive> passives,
                      Empowered empowered, List<BaseAbility> baseAbilities, Map<LineEvent, List<String>> lines) {

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

    /** An unmodifiable copy of {@code from} that keeps its order. */
    private static <K, V> Map<K, V> frozen(Map<K, V> into, Map<K, V> from) {
        into.putAll(from);
        return Collections.unmodifiableMap(into);
    }

    /** Every word of the format is an enum constant whose id is its lower-case name. */
    public interface Word {
        default String id() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Rarity implements Word { COMMON, RARE, EPIC, LEGENDARY }

    public enum Element implements Word { PHYSICAL, FIRE, FROST, SHOCK, VENOM, BLIGHT, ARCANE, RADIANT }

    public enum Role implements Word { VANGUARD, STRIKER, SKIRMISHER, MARKSMAN, SUPPORT }

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

    public enum Form implements Word {
        MELEE, SHOT, VOLLEY, BOLT, CHAIN, CONE, LINE, NOVA, ZONE, LEAP, DASH, SELF, ALLY, ALLIES;

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

    public enum ConditionType implements Word {
        TARGET_HEALTH_BELOW, TARGET_HEALTH_ABOVE, SELF_HEALTH_BELOW, SELF_HEALTH_ABOVE, TARGET_HAS_MARK,
        SELF_HAS_MARK, TARGET_BURNING, TARGET_FREEZING, TARGET_SLOWED, TARGET_STUNNED, TARGET_HAS_DOT, TARGET_IS,
        SELF_EMPOWERED, SELF_SHIELDED
    }

    /**
     * What a {@code target_is} condition may ask. {@code PERSON} is a player, a faction NPC or a hero alike: v1's
     * kinds {@code player} and {@code npc} are read as it, so no ability prefers or avoids players (equal treatment,
     * HEROES_V2 6.15; {@link HeroCatalogue#EQUAL_READINGS}).
     */
    public enum TargetKind implements Word { MONSTER, UNDEAD, ARTHROPOD, ILLAGER, PERSON, HERO, BOSS }

    public enum EffectType implements Word {
        DAMAGE, DOT, HEAL, SHIELD, EFFECT, STAT, KNOCKBACK, PULL, IGNITE, FREEZE, STUN, TAUNT, CLEANSE, DISPEL,
        ENERGY, COOLDOWN_CUT, MOVE_SELF, MARK, CLEAR_MARKS, EMPOWER, SUMMON
    }

    public enum Stat implements Word {
        ATTACK, DAMAGE_DEALT, DAMAGE_TAKEN, ARMOR, SPEED, ATTACK_SPEED, HEALING_RECEIVED, KNOCKBACK_RESISTANCE,
        CRIT_DAMAGE, ENERGY_GAIN;

        /** Whether a modifier of this stat with this sign helps the receiver (v1 13.3). */
        public boolean helpful(int percent) {
            return this == DAMAGE_TAKEN ? percent < 0 : percent > 0;
        }
    }

    public enum Trigger implements Word {
        ALWAYS, ON_HIT, ON_CRIT, ON_HURT, ON_KILL, ON_SKILL, ON_ULTIMATE, ON_FIGHT_START, ON_LOW_HEALTH,
        ON_ALLY_LOW_HEALTH, ON_ALLY_HURT, ON_ALLY_ATTACK, ON_ALLY_ULTIMATE, ON_MARK_FULL, EVERY
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

    public enum LineEvent implements Word {
        SUMMONED, GREETING, IDLE, FIGHT_START, SKILL, ULTIMATE, LOW_HEALTH, KILL, ALLY_FALLEN, VICTORY, RAID_START,
        BASE_ATTACKED, LEVEL_UP, UNLOCK, FALLEN, REVIVED
    }

    /** Where an effect list sits: each slot has its own caps (v1 16.1). */
    public enum Slot implements Word { ON_HIT, NTH_HIT, SKILL, ULTIMATE, PASSIVE }

    public enum GearSlot implements Word { MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET }

    public record Stats(double health, double attack, double armor, int speed, int knockbackResistance, int size,
                        Growth growth) {
    }

    public record Behaviour(Role role, Focus focus, double keepDistance, Aggression aggression, Protects protects,
                            int retreatBelow, Raids raids, String duty) {
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

    /** {@code projectile}, {@code heldItem}, {@code amount}, {@code interval} and {@code element} are for style thrown. */
    public record BasicAttack(Style style, String projectile, String heldItem, double amount, double interval,
                              Element element, List<Effect> onHit, NthHit everyNthHit) {
        public BasicAttack {
            onHit = List.copyOf(onHit);
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

    /** {@code value}: a health share in percent, or the least stacks of {@code mark}. */
    public record Condition(ConditionType type, int value, String mark, TargetKind kind) {
    }

    /** What every effect may carry (v1 13): receiver, the ally radius, a chance and a condition. */
    public record Common(To to, double radius, int chance, Condition cond) {
        public Common withTo(To t) {
            return new Common(t, radius, chance, cond);
        }
    }

    /** One effect of the catalogue (v1 13); one record per type. */
    public sealed interface Effect permits Damage, Dot, Heal, Shield, PotionEffect, StatMod, Knockback, Pull, Ignite,
            Freeze, Stun, Taunt, Cleanse, Dispel, Energy, CooldownCut, MoveSelf, Mark, ClearMarks, Empower, Summon {
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

    public record Empower(Common c, double seconds) implements Effect {
        public EffectType type() {
            return EffectType.EMPOWER;
        }

        public Effect with(Common n) {
            return new Empower(n, seconds);
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
                          Numbers numbers) {
        public Passive {
            effects = List.copyOf(effects);
        }

        public Passive withEffects(List<Effect> e) {
            return new Passive(name, description, trigger, triggerValue, mark, chance, cooldown, form, radius,
                    projectile, cond, List.copyOf(e), look, line, unlockLevel, numbers);
        }

        public Passive withBalance(double newCooldown, double newTriggerValue, Numbers n) {
            return new Passive(name, description, trigger, newTriggerValue, mark, chance, newCooldown, form, radius,
                    projectile, cond, effects, look, line, unlockLevel, n);
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
}
