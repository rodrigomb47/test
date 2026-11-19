package com.factionclash.logic.hero;

import java.util.Set;

/**
 * Kill credit and the words of a death (HEROES_V2 6.17 "Kill credit and the kill feed", {@code docs/design/breaching.md}
 * 8.5 and X1; pure, unit-tested by {@code KillCreditTest}). One rule for every receiver, a player, a faction NPC, a hero
 * or a monster, and for every enemy that pushed or struck it, whoever that was:
 * <ul>
 * <li><b>The credit.</b> A death by terrain ({@link #TERRAIN}: a fall, lava, fire, a magma block, the void, drowning,
 * freezing, suffocation, a cactus or a berry bush) within {@link #WINDOW_TICKS} (15 s) of an enemy's push, throw, pull
 * or blow is that enemy's kill, the newest one's. A push counts even with no damage: the game writes it into the
 * receiver's combat tracker as a hit of 0 by the pusher, so vanilla's own fall message reads "was doomed to fall by".
 * A death with an attacker of its own is that attacker's, as vanilla's is; any other death with no attacker (starving,
 * a wither effect, magic) keeps vanilla's 5 s ({@link #VANILLA_TICKS}).</li>
 * <li><b>The refresh.</b> Vanilla forgets a last attacker after 5 s, so while the receiver falls, burns or is in lava,
 * below the world or in water, its last attacker is set to the credited enemy again every second
 * ({@link #REFRESH_TICKS}), up to the 15 s: vanilla's own death message for a player then names the pusher too.</li>
 * <li><b>The words</b> ({@link #words}): vanilla's death message keys chosen as vanilla's combat tracker chooses them
 * ({@code CombatTracker.getDeathMessage} with NeoForge's default provider: the fall variants, the intentional game
 * design, the {@code .item} and {@code .player} variants), with the credited enemy standing in where vanilla would
 * name nobody or an older attacker ("was doomed to fall by", "tried to swim in lava to escape", "walked into the danger
 * zone due to", "walked into fire while fighting", "was burned to a crisp while fighting", "didn't want to live in the
 * same world as", "drowned while trying to escape").</li>
 * </ul>
 * Nothing here reads whether anyone is a player.
 */
public final class KillCredit {
    private KillCredit() {
    }

    /** 6.17: a terrain death within this many ticks of an enemy's push or blow is that enemy's kill (15 s). */
    public static final int WINDOW_TICKS = 15 * 20;
    /** Vanilla keeps a last attacker this long ({@code LivingEntity.baseTick}: 100 ticks). */
    public static final int VANILLA_TICKS = 100;
    /** The last attacker of a falling, burning or drowning receiver is set again this often (every second). */
    public static final int REFRESH_TICKS = 20;
    /** A pusher's push is written into the receiver's combat tracker at most this often (a tether pulls every tick). */
    public static final int TRACKER_TICKS = 20;
    /** Vanilla's combat tracker names a fall only when it is longer than this ({@code getMostSignificantFall}). */
    public static final float SIGNIFICANT_FALL = 5.0F;

    /** What the enemy did to the receiver. */
    public enum Kind {
        BLOW, PUSH, THROW, PULL
    }

    /**
     * The terrain deaths, by vanilla message id (the damage types {@code fall}, {@code lava}, {@code in_fire} and
     * {@code campfire} ({@code inFire}), {@code on_fire}, {@code hot_floor} (a magma block), {@code out_of_world} (the
     * void), {@code drown}, {@code freeze}, {@code in_wall} (suffocation), {@code cactus}, {@code sweet_berry_bush}, and a
     * fall onto a {@code stalagmite}).
     */
    public static final Set<String> TERRAIN = Set.of("fall", "lava", "inFire", "onFire", "hotFloor", "outOfWorld",
            "drown", "freeze", "inWall", "cactus", "sweetBerryBush", "stalagmite");

    public static boolean terrain(String msgId) {
        return msgId != null && TERRAIN.contains(msgId);
    }

    /** Whether a credit from {@code tick} still counts at {@code now} for a death of this kind. */
    public static boolean counts(long tick, long now, String msgId) {
        long age = now - tick;
        return tick != Long.MIN_VALUE && age >= 0 && age < (terrain(msgId) ? WINDOW_TICKS : VANILLA_TICKS);
    }

    /**
     * Whether the receiver's last attacker is set to its credited enemy now: once a second ({@code now} on the second
     * the sweep runs), while the credit is younger than 15 s and the receiver falls, burns, is in lava, below the world
     * or in water.
     */
    public static boolean refreshes(long creditTick, long now, boolean falling, boolean burning, boolean inLava,
                                    boolean belowWorld, boolean inWater) {
        long age = now - creditTick;
        return creditTick != Long.MIN_VALUE && age >= 0 && age < WINDOW_TICKS
                && (falling || burning || inLava || belowWorld || inWater);
    }

    /**
     * One receiver's newest enemy push or blow (what the credit needs: who, when, what). {@code T} is whatever the game
     * names an enemy by. Recording is a few field writes: no allocation.
     */
    public static final class Ledger<T> {
        private T who;
        private long tick = Long.MIN_VALUE;
        private Kind kind = Kind.BLOW;
        private T tracked;
        private long trackedTick = Long.MIN_VALUE;

        /** {@code who} pushed or struck it at {@code now}: the newest one is kept. */
        public void record(T who, long now, Kind kind) {
            if (who == null) {
                return;
            }
            this.who = who;
            this.tick = now;
            this.kind = kind;
        }

        /**
         * Whether a push by {@code who} at {@code now} is written into the receiver's combat tracker as a hit of 0: the
         * first push of a pusher, then at most once a second ({@link #TRACKER_TICKS}); a blow is in the tracker already.
         */
        public boolean track(T who, long now) {
            if (who == null) {
                return false;
            }
            if (who.equals(tracked) && now - trackedTick < TRACKER_TICKS && now >= trackedTick) {
                return false;
            }
            tracked = who;
            trackedTick = now;
            return true;
        }

        /** The newest enemy, if its push or blow counts for a death of this kind at {@code now}; else null. */
        public T credited(long now, String msgId) {
            return who != null && counts(tick, now, msgId) ? who : null;
        }

        public T who() {
            return who;
        }

        public long tick() {
            return tick;
        }

        public Kind kind() {
            return kind;
        }

        /** Nothing left to credit after {@code now} (older than 15 s). */
        public boolean expired(long now) {
            return who == null || now - tick >= WINDOW_TICKS;
        }
    }

    /**
     * The killer: the killing damage's own attacker (its causing entity, else its direct one) when it has one, as
     * vanilla's message names it; otherwise the credited enemy ({@code credited}, already checked against the window
     * of this death by {@link Ledger#credited}); otherwise nobody.
     */
    public static <T> T killer(T attacker, T credited) {
        return attacker != null ? attacker : credited;
    }

    // ------------------------------------------------------------------ the words (vanilla's death message keys)

    /** The damage type's {@code death_message_type}. */
    public enum DeathType {
        DEFAULT, FALL_VARIANTS, INTENTIONAL_GAME_DESIGN
    }

    /** Who the message names beside the victim. */
    public enum Party {
        /** nobody: a one-name message */
        NONE,
        /** the killing damage's attacker */
        ATTACKER,
        /** the one whose hit began the most significant fall */
        STARTER,
        /** the credited enemy (6.17) */
        CREDITED
    }

    /**
     * The receiver's most significant fall as vanilla's combat tracker finds it ({@code getMostSignificantFall}, a fall
     * longer than 5 blocks): {@code accident} when the entry that began it is itself a fall (then {@code accidentKey}
     * is the fall location's key, {@code death.fell.accident.ladder} ...); otherwise a hit began it, whose attacker
     * ({@code starter}) is present or not, is or is not the killing damage's attacker, and holds a named item or not.
     */
    public record Fall(boolean accident, String accidentKey, boolean starter, boolean starterIsAttacker,
                       boolean starterItem) {
        public static Fall accident(String key) {
            return new Fall(true, key, false, false, false);
        }

        public static Fall byHit(boolean starter, boolean starterIsAttacker, boolean starterItem) {
            return new Fall(false, null, starter, starterIsAttacker, starterItem);
        }
    }

    /**
     * The message: its translation key, whom it names second, whether a third argument (the named item) follows, and
     * whether the second argument is the intentional game design's link instead of a name.
     */
    public record Words(String key, Party killer, boolean item, boolean link) {
        /** How many names and things it takes after the victim. */
        public int arguments() {
            return 1 + (link || killer != Party.NONE ? 1 : 0) + (item ? 1 : 0);
        }
    }

    /**
     * Vanilla's choice of death message for a death by damage {@code msgId} of {@code type} (NeoForge's
     * {@code IDeathMessageProvider.DEFAULT} and {@code DamageSource.getLocalizedDeathMessage}), with the credited enemy
     * filling the places where vanilla names nobody.
     *
     * @param attacker     the killing damage has a causing or direct entity
     * @param attackerItem that causing entity holds an item with a custom name in its main hand
     * @param fall         the most significant fall (null: none longer than 5 blocks)
     * @param credited     a credited enemy within this death's window ({@link Ledger#credited})
     * @param creditedItem the credited enemy holds a named item (only the fall's assist message names it)
     */
    public static Words words(String msgId, DeathType type, boolean attacker, boolean attackerItem, Fall fall,
                              boolean credited, boolean creditedItem) {
        String attack = "death.attack." + msgId;
        if (type == DeathType.FALL_VARIANTS && fall != null) {
            if (fall.accident()) {
                // vanilla: "fell from a high place" (or off a ladder...): no hit began it, but an enemy's push did
                return credited ? new Words(creditedItem ? "death.fell.assist.item" : "death.fell.assist", Party.CREDITED,
                        creditedItem, false) : new Words(fall.accidentKey(), Party.NONE, false, false);
            }
            if (fall.starter() && !(attacker && fall.starterIsAttacker())) {
                return new Words(fall.starterItem() ? "death.fell.assist.item" : "death.fell.assist", Party.STARTER,
                        fall.starterItem(), false);
            }
            if (attacker) {
                return new Words(attackerItem ? "death.fell.finish.item" : "death.fell.finish", Party.ATTACKER,
                        attackerItem, false);
            }
            if (credited) {
                return new Words(creditedItem ? "death.fell.assist.item" : "death.fell.assist", Party.CREDITED,
                        creditedItem, false);
            }
            return new Words("death.fell.killer", Party.NONE, false, false);
        }
        if (type == DeathType.INTENTIONAL_GAME_DESIGN) {
            return new Words(attack + ".message", Party.NONE, false, true);
        }
        if (attacker) {
            return new Words(attackerItem ? attack + ".item" : attack, Party.ATTACKER, attackerItem, false);
        }
        return credited ? new Words(attack + ".player", Party.CREDITED, false, false)
                : new Words(attack, Party.NONE, false, false);
    }
}
