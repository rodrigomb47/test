package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.WeakPoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hero as a boss (HEROES_V2 6, as the owner's decisions of 2026-11-12 and 2026-11-13 left it): one mode in every
 * fight, the same against a player, an NPC soldier, another hero or a monster. Boss health with no scaling by the
 * number or the kind of attackers (6.2), the per-hit cap for a hit from any attacking entity with the excess turned
 * into poise, vanilla's hurt cooldown kept per attacker (6.2, the equal-treatment review of 6.16), poise and stagger
 * (6.9), the damage-taken windows and weak points (6.9), phases, transitions and the rebirth (6.7), the fight clock
 * on engaged enemies, enrage and reset (6.1, 6.10), the intro (6.13), hazard-aware knockback (6.12), the stun
 * immunity that belongs to the receiver (6.14), air poise (MOBILITY_AND_WORK 2.4) and the fight-reach clock and
 * altitude ceiling, counted from the engaged enemies (MOBILITY_AND_WORK 2.3, 5.1.6, 5.1.15).
 *
 * <p>There is no yield, no attack token, no damage multiplier against players, no protection from a one-hit kill and
 * no health per engaged player: none of them exists here, and no method takes an input that says whether anyone is a
 * player ({@code BossRulesTest.equalForPlayerAndNpc}). How a losing fight ends (escape or last stand) is
 * {@link EscapeRules}'. Pure Java, unit-tested ({@code BossRulesTest}); numbers are PROPOSED (HEROES_V2 6) until the
 * slice campaigns measure them.
 */
public final class BossRules {
    private BossRules() {
    }

    /** Game ticks per second. */
    public static final int TPS = 20;

    // ================================================================== health (6.2)

    /** The authored {@code health_scale} range (the rarity's default when the file gives none). */
    public static final int HEALTH_SCALE_MIN = 200, HEALTH_SCALE_MAX = 600;
    /** {@code boss.healthScale}: a config percent that multiplies every hero's scale. */
    public static final int CONFIG_SCALE_MIN = 25, CONFIG_SCALE_MAX = 300, CONFIG_SCALE_DEFAULT = 100;

    /** HEROES_V2 6.2's role factor: the fragile roles start from less base health, so their boss lasts as long. */
    public static double roleFactor(Role r) {
        return HeroCatalogue.ROLE_BOSS.get(r).healthFactor();
    }

    /** The role's default poise when the file gives none (vanguard 140 ... marksman and summoner 70). */
    public static int defaultPoise(Role r) {
        return HeroCatalogue.ROLE_BOSS.get(r).poise();
    }

    /**
     * The health scale in percent the game applies: the file's scale (200-600), +50 while the hero leads its faction,
     * times {@code boss.healthScale} (25-300%). Nothing else enters: not who fights it and not how many.
     */
    public static double healthScale(int authored, boolean leading, int configPercent) {
        int a = Math.max(HEALTH_SCALE_MIN, Math.min(HEALTH_SCALE_MAX, authored));
        int cfg = Math.max(CONFIG_SCALE_MIN, Math.min(CONFIG_SCALE_MAX, configPercent));
        return (a + (leading ? HeroCatalogue.LEADER_HEALTH_BONUS : 0)) * cfg / 100.0;
    }

    /**
     * Boss health: {@code baseMax x healthScale / 100 x roleFactor}, always (from spawn, revival and load). There is
     * no attacker count and no player count: a duel, a raid party and a crowd of monsters all fight the same maximum.
     */
    public static double maxHealth(double baseMax, double healthScalePercent, double roleFactor) {
        return Math.max(1, baseMax) * Math.max(0, healthScalePercent) / 100 * Math.max(0, roleFactor);
    }

    /**
     * The value of the one permanent attribute modifier {@code factionclash:boss_health} ({@code ADD_MULTIPLIED_TOTAL}):
     * {@code healthScale / 100 x roleFactor - 1}, so the attribute's base stays the v1 health.
     */
    public static double healthModifier(double healthScalePercent, double roleFactor) {
        return healthScalePercent / 100 * roleFactor - 1;
    }

    /** When the maximum changes (a level, a config change, leading or not) the health share is kept. */
    public static double keepShare(double health, double oldMax, double newMax) {
        if (oldMax <= 0 || !Double.isFinite(health)) {
            return newMax;
        }
        return Math.max(0, Math.min(1, health / oldMax)) * newMax;
    }

    // ================================================================== the per-hit cap (6.2)

    /** {@code boss.max_hit_share}: one hit takes at most this share of max health (the file may set 5-25). */
    public static final int MAX_HIT_SHARE_DEFAULT = 12, MAX_HIT_SHARE_MIN = 5, MAX_HIT_SHARE_MAX = 25;

    /** What one hit takes from the hero's health and what goes to its poise instead. */
    public record Capped(double landed, double toPoise) {
        public static final Capped NONE = new Capped(0, 0);
    }

    /**
     * The per-hit cap for a hit with an attacking entity, whoever it is (a player, an NPC, a hero, a monster, their
     * projectiles and summons; also a downing fall, MOBILITY_AND_WORK 2.4): at most {@code sharePercent} of max health
     * lands, the excess goes to poise 1:1. Damage with no attacker (lava, fire, drowning, the void) is not passed
     * through here and is never capped; {@code /kill} is never touched.
     */
    public static Capped capHit(double amount, double maxHealth, int sharePercent) {
        if (!(amount > 0)) {
            return Capped.NONE;
        }
        double cap = maxHealth * Math.max(MAX_HIT_SHARE_MIN, Math.min(MAX_HIT_SHARE_MAX, sharePercent)) / 100.0;
        return amount <= cap ? new Capped(amount, 0) : new Capped(cap, amount - cap);
    }

    // ================================================================== the hurt cooldown per attacker (6.2, 6.16)

    /** Vanilla: within this many ticks of a source's last full hit only the excess over that hit lands. */
    public static final int HURT_WINDOW = 10;
    /** What vanilla sets {@code invulnerableTime} to on a full hit (the window is its upper half). */
    public static final int INVULNERABLE_TICKS = 20;

    /** One source's last hit: the tick of its last full hit and the amount the window compares against. */
    public record HurtRecord(long tick, double amount) {
    }

    /** What lands of a hit after the cooldown, the source's new record and whether the hit landed in full. */
    public record Hurt(double lands, HurtRecord record, boolean full) {
    }

    /**
     * Vanilla's half-second hurt cooldown for ONE source: a hit within {@link #HURT_WINDOW} ticks of that source's
     * last full hit lands only what it adds above that hit's amount (nothing when it is not larger); after the window
     * a hit lands in full and starts a new window. The amounts are the incoming ones, before armour (as vanilla
     * compares them). The game keeps one record per attacking entity ({@link HurtLedger}), so several attackers'
     * blows all land in full while one attacker's spam clicks still land only their excess.
     */
    public static Hurt hurtCooldown(HurtRecord last, long now, double amount) {
        double a = Math.max(0, amount);
        if (last != null && now - last.tick() >= 0 && now - last.tick() < HURT_WINDOW) {
            if (a <= last.amount()) {
                return new Hurt(0, last, false);
            }
            return new Hurt(a - last.amount(), new HurtRecord(last.tick(), a), false);
        }
        return new Hurt(a, new HurtRecord(now, a), true);
    }

    /** HEROES_V2 15.6's form: what lands of {@code amount} from a source whose last full hit was at the given tick. */
    public static double hurtCooldown(long lastTick, double lastAmount, long now, double amount) {
        return hurtCooldown(lastTick < 0 ? null : new HurtRecord(lastTick, lastAmount), now, amount).lands();
    }

    /** The {@code invulnerableTime} to set before vanilla's own check for this source ({@code 20 - elapsed}, or 0). */
    public static int invulnerableTime(HurtRecord last, long now) {
        if (last == null || now - last.tick() < 0 || now - last.tick() >= HURT_WINDOW) {
            return 0;
        }
        return (int) (INVULNERABLE_TICKS - (now - last.tick()));
    }

    /** The {@code lastHurt} to set before vanilla's own check for this source (its amount inside the window, or 0). */
    public static double lastHurt(HurtRecord last, long now) {
        return invulnerableTime(last, now) > 0 ? last.amount() : 0;
    }

    /**
     * The hurt cooldown kept per source (HEROES_V2 15.6, {@code HeroEntity.hurt}): the key is the attacking entity
     * ({@code DamageSource.getEntity()}: for an arrow its shooter, so an archer's arrows and blows share one key);
     * every hit with no attacker shares {@link #NO_ATTACKER}, so lava and fire tick as in vanilla. Entries older than
     * the window are dropped; nothing is saved.
     */
    public static final class HurtLedger<K> {
        /** The one shared key for every hit with no attacking entity. */
        public static final Object NO_ATTACKER = new Object() {
            @Override
            public String toString() {
                return "no attacker";
            }
        };

        private final Map<Object, HurtRecord> records = new HashMap<>();

        /** Lands a hit from {@code source} (null: no attacker) and remembers it. */
        public Hurt hit(K source, long now, double amount) {
            Object key = source == null ? NO_ATTACKER : source;
            Hurt h = hurtCooldown(records.get(key), now, amount);
            records.put(key, h.record());
            return h;
        }

        /** The record of a source inside its window, or null. */
        public HurtRecord record(K source, long now) {
            HurtRecord r = records.get(source == null ? NO_ATTACKER : source);
            return r != null && invulnerableTime(r, now) > 0 ? r : null;
        }

        /** Drops every record whose window has passed; returns how many are left. */
        public int prune(long now) {
            for (Iterator<HurtRecord> it = records.values().iterator(); it.hasNext(); ) {
                if (invulnerableTime(it.next(), now) == 0) {
                    it.remove();
                }
            }
            return records.size();
        }

        public int size() {
            return records.size();
        }
    }

    // ================================================================== poise and stagger (6.9)

    /** What a hit was dealt with, for its poise factor; the same table whoever holds the weapon. */
    public enum WeaponKind {
        SWORD(1.0), AXE(1.6), MACE(2.0), TRIDENT(1.2), ARROW(0.6), BOLT(0.8), OTHER(1.0);

        public final double poise;

        WeaponKind(double poise) {
            this.poise = poise;
        }
    }

    /**
     * The allowed {@code boss.poise}: 50-200, or 0 for a hero that never staggers (HEROES_V2 12.10: only with another
     * weakness; the reader raises a 0 without one to the role's default).
     */
    public static final int POISE_MIN = 50, POISE_MAX = 200;
    /** A parry (a heavy move blocked by a shield raised 5-11 ticks before), a charge into a wall, a downing. */
    public static final double PARRY_POISE = 25, WALL_POISE = 40, DOWNING_POISE = 30;
    /** Vanilla blocks once a shield has been up 5 ticks; a parry is a block within the first 6 ticks after that. */
    public static final int PARRY_FIRST_TICK = 5, PARRY_LAST_TICK = 11;
    /** Poise decays 15 per second after 3 s without poise damage. */
    public static final double POISE_DECAY_PER_SECOND = 15;
    public static final int POISE_DECAY_DELAY = 3 * TPS;
    /** A stagger: 2.5 s kneeling dizzy, +25% damage taken; then the maximum grows 20% for the rest of the fight. */
    public static final int STAGGER_TICKS = 50;
    public static final double STAGGER_TAKEN_PERCENT = 25, STAGGER_GROWTH = 1.2;

    /**
     * Poise from one hit: what landed x the weapon's factor, plus the part over the per-hit cap 1:1. A mace's fall
     * bonus is part of the damage it dealt, so it counts at the mace's factor.
     */
    public static double poiseFromHit(double landed, double excess, WeaponKind kind) {
        return Math.max(0, landed) * (kind == null ? 1.0 : kind.poise) + Math.max(0, excess);
    }

    /**
     * A parry: the receiver of a heavy move (or heavier) blocks it with a shield it raised 5-11 ticks before the hit,
     * a player's or an NPC's alike ({@code isBlocking()} and {@code getTicksUsingItem()}).
     */
    public static boolean parry(boolean blocking, int ticksUsingItem, MoveClass moveClass) {
        return blocking && moveClass != null && moveClass.atLeast(MoveClass.HEAVY)
                && ticksUsingItem >= PARRY_FIRST_TICK && ticksUsingItem <= PARRY_LAST_TICK;
    }

    /**
     * A hero's poise over one fight (6.9). Hits fill it whoever lands them; at its maximum the hero staggers for
     * 2.5 s, the bar empties and the maximum grows 20% for the rest of the fight (no stun-lock), and x1.25 while
     * enraged (6.10). While the hero is protected from a stagger (a transition, an ultimate's wind-up, a
     * {@code super_armor} move) the bar fills to just short of its maximum, so the next hit after the protection
     * staggers. During a stagger poise damage is ignored. A new fight starts from an empty bar and the base maximum.
     * A {@code boss.poise} of 0 is a hero with no poise bar: it never staggers, whatever lands.
     */
    public static final class Poise {
        private final double base;
        private final boolean none;
        private double value;
        private int staggers;
        private boolean enraged;
        private long lastDamageTick = Long.MIN_VALUE / 4;
        private long staggerUntil = Long.MIN_VALUE / 4;
        private long lastTick = Long.MIN_VALUE / 4;

        public Poise(double base) {
            this.none = !(base > 0);
            this.base = none ? 0 : base;
        }

        /** A hero with {@code boss.poise} 0: no bar, no stagger. */
        public boolean none() {
            return none;
        }

        public double max() {
            return base * Math.pow(STAGGER_GROWTH, staggers) * (enraged ? ENRAGE_POISE_FACTOR : 1);
        }

        public double value() {
            return value;
        }

        public int staggers() {
            return staggers;
        }

        public boolean staggered(long now) {
            return now < staggerUntil;
        }

        public long staggerUntil() {
            return staggerUntil;
        }

        public void enraged(boolean on) {
            enraged = on;
        }

        /** Adds poise damage; returns true when this hit staggers the hero. */
        public boolean add(double amount, long now, boolean protectedFromStagger) {
            decay(now);
            if (none || !(amount > 0) || staggered(now)) {
                return false;
            }
            lastDamageTick = now;
            value += amount;
            double m = max();
            if (value < m) {
                return false;
            }
            if (protectedFromStagger) {
                value = m - 1e-6;
                return false;
            }
            value = 0;
            staggers++;
            staggerUntil = now + STAGGER_TICKS;
            return true;
        }

        /** Applies the decay up to {@code now}: 15 a second once 3 s have passed since the last poise damage. */
        public void decay(long now) {
            long from = Math.max(lastTick, lastDamageTick + POISE_DECAY_DELAY);
            if (now > from && value > 0) {
                value = Math.max(0, value - (now - from) * POISE_DECAY_PER_SECOND / TPS);
            }
            lastTick = Math.max(lastTick, now);
        }

        /** A new fight: an empty bar, the base maximum, no stagger. */
        public void reset() {
            value = 0;
            staggers = 0;
            enraged = false;
            staggerUntil = Long.MIN_VALUE / 4;
            lastDamageTick = Long.MIN_VALUE / 4;
        }
    }

    // ================================================================== damage taken: windows and weak points (6.9)

    /** Recovery (the punish window) +15%, from behind during it +15% more; the matching weak points double each. */
    public static final double RECOVERY_TAKEN_PERCENT = 15, BEHIND_TAKEN_PERCENT = 15;
    /** A downed hero lies 2 s taking +20% (MOBILITY_AND_WORK 2.4). */
    public static final double DOWNED_TAKEN_PERCENT = 20;
    /** Super armour (a transition, the intro): 50% less damage taken, never invulnerable. */
    public static final double SUPER_ARMOUR_TAKEN_FACTOR = 0.5;
    /** {@code iron} / {@code gold} weak points: +15% from an iron or golden main-hand weapon. */
    public static final double METAL_WEAKNESS_PERCENT = 15;
    /** "From behind": more than this many degrees from the hero's facing. */
    public static final double BEHIND_DEGREES = 120;

    /** The tier of the attacker's main-hand weapon, for the {@code iron} and {@code gold} weak points. */
    public enum WeaponTier { NONE, WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE, OTHER }

    /** The state a hit meets, from the hero's side; nothing in it says who the attacker is. */
    public record Window(boolean staggered, boolean recovery, boolean behind, boolean superArmour, boolean downed,
                         WeaponTier tier) {
        public static final Window NONE = new Window(false, false, false, false, false, WeaponTier.NONE);
    }

    /**
     * The hero's damage-taken factor for one hit: the bonuses add (stagger +25, recovery +15, from behind during the
     * recovery +15, downed +20, an iron or golden weapon +15 against that weak point; {@code recovery} and
     * {@code back} weak points double theirs), then super armour halves it. The back bonus is part of the punish
     * window, so outside a recovery a hit from behind lands as one from the front (kit-gaps 8, decided in F3).
     */
    public static double takenFactor(Window w, Set<WeakPoint> weak) {
        double bonus = 0;
        if (w.staggered()) {
            bonus += STAGGER_TAKEN_PERCENT;
        }
        if (w.recovery()) {
            bonus += RECOVERY_TAKEN_PERCENT * (weak.contains(WeakPoint.RECOVERY) ? 2 : 1);
            if (w.behind()) {
                bonus += BEHIND_TAKEN_PERCENT * (weak.contains(WeakPoint.BACK) ? 2 : 1);
            }
        }
        if (w.downed()) {
            bonus += DOWNED_TAKEN_PERCENT;
        }
        if ((w.tier() == WeaponTier.IRON && weak.contains(WeakPoint.IRON))
                || (w.tier() == WeaponTier.GOLD && weak.contains(WeakPoint.GOLD))) {
            bonus += METAL_WEAKNESS_PERCENT;
        }
        return (1 + bonus / 100) * (w.superArmour() ? SUPER_ARMOUR_TAKEN_FACTOR : 1);
    }

    /**
     * Whether an attacker stands behind the hero: more than 120 degrees from its facing. {@code yawDegrees} is the
     * hero's body yaw (Minecraft's: 0 faces +Z, 90 faces -X); {@code dx, dz} point from the hero to the attacker.
     */
    public static boolean behind(double yawDegrees, double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) {
            return false;
        }
        double r = Math.toRadians(yawDegrees);
        double fx = -Math.sin(r), fz = Math.cos(r);
        double cos = (fx * dx + fz * dz) / len;
        return cos < Math.cos(Math.toRadians(BEHIND_DEGREES)) - 1e-12;
    }

    /** One hit, through the window, the cap and poise: what the health loses and what the poise bar gains. */
    public record Landed(double health, double poise, double excess) {
    }

    /**
     * The whole incoming path of one hit on a hero (after armour and resistances, which vanilla and v1 apply): the
     * damage-taken factor, then the per-hit cap, then poise. {@code hasAttacker} false is damage with no attacking
     * entity: not capped and no poise. Whoever the attacker is, the same numbers give the same result.
     */
    public static Landed land(double amount, boolean hasAttacker, double maxHealth, int capShare, Window w,
                              Set<WeakPoint> weak, WeaponKind kind) {
        double a = Math.max(0, amount) * takenFactor(w, weak);
        if (!hasAttacker) {
            return new Landed(a, 0, 0);
        }
        Capped c = capHit(a, maxHealth, capShare);
        return new Landed(c.landed(), poiseFromHit(c.landed(), c.toPoise(), kind), c.toPoise());
    }

    // ------------------------------------------------------------------ the other weak points (6.9)

    /** {@code channel}: damage of 15% of max health (or the move's own share) during a channel breaks it. */
    public static final int CHANNEL_INTERRUPT_DEFAULT = 15;
    /** A broken channel gives a 2 s recovery. */
    public static final int CHANNEL_BROKEN_RECOVERY_TICKS = 2 * TPS;
    /** {@code bell}: a bell rung within 8 blocks, by anyone, stuns a spirit hero 1 s, once per 30 s. */
    public static final double BELL_RADIUS = 8;
    public static final int BELL_STUN_TICKS = TPS, BELL_EVERY_TICKS = 30 * TPS;
    /** {@code light}: a block of light 12 or more placed within 4 blocks by an enemy weakens a shadow hero 10% for 10 s. */
    public static final int LIGHT_EMISSION_MIN = 12;
    public static final double LIGHT_RADIUS = 4, LIGHT_WEAKEN_PERCENT = 10;
    public static final int LIGHT_WEAKEN_TICKS = 10 * TPS;
    /** {@code snowball} / {@code water}: a douse ends a burning state and the hero deals 10% less for 5 s. */
    public static final double DOUSE_DEALT_PERCENT = -10;
    public static final int DOUSE_TICKS = 5 * TPS;

    /** Whether the damage taken during a channel breaks it ({@code interruptPercent} of max health; 0: 15). */
    public static boolean channelBroken(double damageDuringChannel, double maxHealth, int interruptPercent) {
        int p = interruptPercent > 0 ? interruptPercent : CHANNEL_INTERRUPT_DEFAULT;
        return damageDuringChannel >= maxHealth * p / 100.0;
    }

    /** Whether a bell rung now at {@code distance} stuns the hero (its weak point, once per 30 s). */
    public static boolean bellStuns(Set<WeakPoint> weak, double distance, long now, long lastBellStunAt) {
        return weak.contains(WeakPoint.BELL) && distance <= BELL_RADIUS
                && (lastBellStunAt <= 0 || now - lastBellStunAt >= BELL_EVERY_TICKS);
    }

    /** Whether a block placed by an enemy weakens the hero (its {@code light} weak point). */
    public static boolean lightWeakens(Set<WeakPoint> weak, int lightEmission, double distance) {
        return weak.contains(WeakPoint.LIGHT) && lightEmission >= LIGHT_EMISSION_MIN && distance <= LIGHT_RADIUS;
    }

    /**
     * {@code crit_above}: a critical hit from an attacker falling from above the hero's eyes staggers at once (a
     * player's critical hit or an NPC's or hero's jump critical alike).
     */
    public static boolean critAboveStaggers(Set<WeakPoint> weak, boolean critical, boolean falling,
                                            double attackerFeetY, double heroEyeY) {
        return weak.contains(WeakPoint.CRIT_ABOVE) && critical && falling && attackerFeetY > heroEyeY;
    }

    // ================================================================== the fight (6.1), enrage and reset (6.10)

    /** An enemy is engaged when it hurt the hero in the last 15 s or is its target, within 32 blocks. */
    public static final int ENGAGE_MEMORY_TICKS = 15 * TPS;
    public static final double ENGAGE_RADIUS = 32;
    /** A fight ends 10 s after its last engaged enemy leaves, dies or stops being an enemy. */
    public static final int FIGHT_END_TICKS = 10 * TPS;
    /** 30 s out of a fight the hero heals 1% of its base (v1) max health a second. */
    public static final int HEAL_DELAY_TICKS = 30 * TPS;
    public static final double HEAL_PERCENT_PER_SECOND = 1;

    /**
     * Whether a living enemy is engaged: it hurt the hero in the last 15 s or is its target, and it is within 32
     * blocks. Whether it is an enemy at all (a hostile faction, a monster, a player in survival or adventure) is the
     * game's; nothing here reads which kind it is.
     */
    public static boolean engaged(long now, long lastHurtByIt, boolean isTarget, double distance) {
        boolean recent = lastHurtByIt > 0 && now - lastHurtByIt >= 0 && now - lastHurtByIt < ENGAGE_MEMORY_TICKS;
        return (recent || isTarget) && distance <= ENGAGE_RADIUS;
    }

    /** What an update of the fight clock changed. */
    public enum FightEvent { NONE, STARTED, ENDED }

    /**
     * The fight clock (6.1): from the first engaged enemy until 10 s after the last one. It drives the move gap, the
     * enrage, the arena samples and the bar; the count of engaged enemies only says whether there are any.
     */
    public static final class Fight {
        private boolean active;
        private long start;
        private long lastEngaged;
        private long endedAt = Long.MIN_VALUE / 4;

        /** Call once a tick (or at any rate) with whether any enemy is engaged now. */
        public FightEvent update(long now, boolean anyEngaged) {
            if (anyEngaged) {
                lastEngaged = now;
                if (!active) {
                    active = true;
                    start = now;
                    return FightEvent.STARTED;
                }
                return FightEvent.NONE;
            }
            if (active && now - lastEngaged >= FIGHT_END_TICKS) {
                active = false;
                endedAt = now;
                return FightEvent.ENDED;
            }
            return FightEvent.NONE;
        }

        public boolean active() {
            return active;
        }

        public long start() {
            return start;
        }

        /** Seconds since the fight began (0 out of a fight): the enrage clock. */
        public double seconds(long now) {
            return active ? Math.max(0, now - start) / (double) TPS : 0;
        }

        /** Out of a fight long enough to heal (30 s after it ended); never while fighting. */
        public boolean mayHeal(long now) {
            return !active && now - endedAt >= HEAL_DELAY_TICKS;
        }
    }

    /** Health regained per tick out of a fight: 1% of the base (v1) maximum a second. */
    public static double healPerTick(double baseMax) {
        return Math.max(0, baseMax) * HEAL_PERCENT_PER_SECOND / 100 / TPS;
    }

    /** Soft enrage (6.10): +15% damage, the move gap x0.7, the poise maximum x1.25, the bar red. */
    public static final double ENRAGE_DAMAGE_PERCENT = 15, ENRAGE_GAP_FACTOR = 0.7, ENRAGE_POISE_FACTOR = 1.25;
    /** The authored {@code boss.enrage.after} range; 180 s by default, 240 s while leading (kit-gaps 12). */
    public static final double ENRAGE_AFTER_MIN = 120, ENRAGE_AFTER_MAX = 300;
    /** All the boss layer's damage bonuses together never exceed +25%. */
    public static final double BOSS_DAMAGE_BONUS_MAX = 25;

    /** Seconds of fighting before the soft enrage: the file's (120-300), else 180, or 240 while leading. */
    public static double enrageAfter(double authored, boolean leading) {
        if (authored > 0) {
            return Math.max(ENRAGE_AFTER_MIN, Math.min(ENRAGE_AFTER_MAX, authored));
        }
        return leading ? HeroCatalogue.ENRAGE_SECONDS_LEADER : HeroCatalogue.ENRAGE_SECONDS;
    }

    /**
     * Enraged: the soft enrage once the fight has lasted {@code after} seconds, or at once by a hard trigger
     * ({@code ally_hero_died}, {@code heart_attacked}, {@code favoured_hurt}, by a player or an NPC alike).
     */
    public static boolean enraged(double fightSeconds, double after, boolean hardTriggered) {
        return hardTriggered || fightSeconds >= after;
    }

    /**
     * The boss layer's damage bonus in percent (the enrage's +15, a phase's or a state's {@code damage_dealt}):
     * the bonuses add up to at most +25; maluses (a douse, a light weakness) count in full on top.
     */
    public static double bossDamageBonus(Collection<Double> percents) {
        double up = 0, down = 0;
        for (double p : percents) {
            if (p > 0) {
                up += p;
            } else {
                down += p;
            }
        }
        return Math.min(BOSS_DAMAGE_BONUS_MAX, up) + down;
    }

    // ================================================================== phases, transitions, rebirth (6.7)

    /** A phase re-arms when health climbs 10% above its threshold out of a fight. */
    public static final int REARM_MARGIN = 10;
    /** A transition lasts 0.8-2.0 s: 50% less damage taken, no stagger, a shockwave of strength 2 within 4 blocks. */
    public static final double TRANSITION_MIN = 0.8, TRANSITION_MAX = 2.0, SHOCK_RADIUS = 4, SHOCK_STRENGTH = 2;
    /** {@code rebirth} (legendary only): once per fight, lethal damage instead starts a final phase at 30%. */
    public static final double REBIRTH_SHARE = 0.30;

    /**
     * The phase for a health share, from the phases' starts (100, then for example 60 and 25: a phase begins when the
     * share falls to its {@code at}). Phases only go forward in a fight: the result is never below {@code current},
     * and a share that crosses two thresholds at once lands in the deeper phase (its transition plays once).
     */
    public static int phaseFor(int current, double share, List<Integer> starts) {
        int p = Math.max(1, current);
        for (int i = p; i < starts.size(); i++) {
            if (share * 100 <= starts.get(i) + 1e-9) {
                p = i + 1;
            }
        }
        return Math.min(p, Math.max(1, starts.size()));
    }

    /**
     * Out of a fight the phases re-arm as the hero heals: while the share is 10% above the current phase's start, it
     * goes back a phase, so the next fight plays that transition again.
     */
    public static int rearm(int current, double share, List<Integer> starts) {
        int p = Math.min(current, Math.max(1, starts.size()));
        while (p > 1 && share * 100 >= starts.get(p - 1) + REARM_MARGIN - 1e-9) {
            p--;
        }
        return p;
    }

    /** A transition's length, clamped to 0.8-2.0 s. */
    public static double transitionSeconds(double authored) {
        return Math.max(TRANSITION_MIN, Math.min(TRANSITION_MAX, authored > 0 ? authored : 1.0));
    }

    /** Whether lethal damage starts the final phase instead: a hero with {@code rebirth} that has not used it. */
    public static boolean rebirths(boolean lethal, boolean hasRebirth, boolean usedThisFight) {
        return lethal && hasRebirth && !usedThisFight;
    }

    /** The pips the bar shows: the phases, plus one for an unspent or spent rebirth (never a surprise). */
    public static int pips(int phases, boolean hasRebirth) {
        return Math.max(1, phases) + (hasRebirth ? 1 : 0);
    }

    // ================================================================== intro (6.13)

    /** The intro (1.0-1.5 s, super armour, 50% less damage) plays in a hero's first fight against people each day. */
    public static final double INTRO_MIN = 1.0, INTRO_MAX = 1.5;
    public static final long DAY_TICKS = 24000;

    /** The in-game day of a game time. */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY_TICKS);
    }

    /**
     * Whether this fight opens with the intro: its first fight in this in-game day in which a person (a player, a
     * faction NPC or a hero) is engaged. A hero does not announce itself to a zombie.
     */
    public static boolean introDue(long lastIntroDay, long gameTime, boolean personEngaged) {
        return personEngaged && day(gameTime) != lastIntroDay;
    }

    // ================================================================== knockback and control (6.12, 6.14)

    /** Knockback toward lava, fire, a drop over 6 or the void within 4 blocks is cut to this, for every receiver. */
    public static final double HAZARD_KNOCKBACK = 0.5, HAZARD_LOOKAHEAD = 4;
    public static final int HAZARD_DROP = 6;

    public static double safeKnockback(double strength, boolean hazardAhead) {
        return hazardAhead ? Math.min(strength, HAZARD_KNOCKBACK) : strength;
    }

    /** Knock-ups lift at most 1 block; grabs hold at most 1 s. */
    public static final double KNOCK_UP_MAX = 1;
    public static final int GRAB_HOLD_TICKS = TPS;

    /**
     * After a stun the receiver cannot be stunned again for twice its length, by anyone: the immunity belongs to the
     * receiver, so several heroes cannot chain their stuns (at most 2 s stopped in any 6 s, 3 s in 9 s). Returns the
     * game time until which a new stun does nothing.
     */
    public static long stunImmuneUntil(long stunStart, double seconds) {
        return stunStart + 3 * HeroRules.ticks(Math.max(0, seconds));
    }

    public static boolean mayStun(long now, long immuneUntil) {
        return now >= immuneUntil;
    }

    // ================================================================== air poise (MOBILITY_AND_WORK 2.4)

    public static final double AIR_POISE_MAX = 100, AIR_POISE_DECAY_PER_SECOND = 10;
    public static final int AIR_POISE_DECAY_DELAY = 2 * TPS, DOWNED_TICKS = 2 * TPS, NO_TAKEOFF_TICKS = 12 * TPS;
    /** A downed hero's horizontal speed is cut to a fifth: it drops almost where it was shot. */
    public static final double DOWNED_SPEED_FACTOR = 0.2;

    /**
     * An airborne hero's air poise: a bar of 100 that decays 10 a second after 2 s without a hit; full, or at once on
     * a stun, a freeze, Slowness II or worse or a lightning strike, the hero is downed (it falls, lies 2 s taking
     * +20%, cannot take off for 12 s, and gains 30 poise). Arrows count the same whoever shot them, and it holds
     * while escaping.
     */
    public static final class AirPoise {
        private double value;
        private long lastHit = Long.MIN_VALUE / 4;
        private long lastTick = Long.MIN_VALUE / 4;
        private long downedAt = Long.MIN_VALUE / 4;

        /** Adds air poise from a hit; returns true when it downs the hero. */
        public boolean hit(double amount, long now) {
            decay(now);
            lastHit = now;
            value += Math.max(0, amount);
            if (value >= AIR_POISE_MAX) {
                return down(now);
            }
            return false;
        }

        /** A stun, a freeze, Slowness II or worse, or lightning: downed at once. */
        public boolean instant(long now) {
            return down(now);
        }

        private boolean down(long now) {
            value = 0;
            downedAt = now;
            return true;
        }

        public void decay(long now) {
            long from = Math.max(lastTick, lastHit + AIR_POISE_DECAY_DELAY);
            if (now > from && value > 0) {
                value = Math.max(0, value - (now - from) * AIR_POISE_DECAY_PER_SECOND / TPS);
            }
            lastTick = Math.max(lastTick, now);
        }

        public double value() {
            return value;
        }

        /** Lying downed (2 s after the fall begins, +20% damage taken). */
        public boolean downed(long now) {
            return now - downedAt >= 0 && now - downedAt < DOWNED_TICKS;
        }

        public boolean mayTakeOff(long now) {
            return now - downedAt >= NO_TAKEOFF_TICKS;
        }
    }

    // ================================================================== the fight's air and reach (MOBILITY_AND_WORK 2.3, 5.1)

    /** While fighting, the ceiling is 6 blocks above the ground under the hero or above the highest engaged enemy. */
    public static final double FIGHT_CEILING = 6;
    /** "Out of reach" in the air: more than 3 blocks above every engaged enemy's standing height. */
    public static final double REACH_ABOVE = 3;
    /** At most 4 s at a time where no engaged enemy can reach it, unless it is winding up a telegraphed move. */
    public static final int REACH_LIMIT_TICKS = 4 * TPS;

    /**
     * The fight's altitude ceiling (MOBILITY_AND_WORK 2.3): 6 blocks above the ground under the hero or above the feet
     * of the highest engaged enemy standing on a block, whichever is higher, so an enemy on a pillar or a wall is
     * always in reach. {@code enemyFeet} holds the engaged enemies that stand on a block; who they are does not count.
     */
    public static double fightCeiling(double groundY, Collection<Double> enemyFeet) {
        double top = groundY;
        for (double y : enemyFeet) {
            top = Math.max(top, y);
        }
        return top + FIGHT_CEILING;
    }

    /** Whether an airborne hero is above every engaged enemy's melee reach (empty: no enemy, nothing to reach). */
    public static boolean aboveReach(double heroFeetY, Collection<Double> enemyStandingY) {
        if (enemyStandingY.isEmpty()) {
            return false;
        }
        for (double y : enemyStandingY) {
            if (heroFeetY - y <= REACH_ABOVE) {
                return false;
            }
        }
        return true;
    }

    /**
     * The fight-reach clock (MOBILITY_AND_WORK 5.1.6 and 5.1.15): while fighting, the time in a row the hero spends
     * where no engaged enemy can reach it (above their reach, or on ground none of them can walk to). It holds still
     * while the hero winds up a telegraphed move, starts again from 0 once an enemy can reach it, and never runs while
     * the hero escapes (an escape may go where nobody follows). At 4 s the hero must come back toward its enemies.
     */
    public static final class ReachClock {
        private int ticks;

        /** One tick; returns true when the hero must now come back. */
        public boolean tick(boolean outOfReach, boolean windingUp, boolean escaping) {
            if (escaping || !outOfReach) {
                ticks = 0;
                return false;
            }
            if (!windingUp) {
                ticks++;
            }
            return ticks >= REACH_LIMIT_TICKS;
        }

        public int ticks() {
            return ticks;
        }

        public void reset() {
            ticks = 0;
        }
    }
}
