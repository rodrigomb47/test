package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.SlotCaps;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Slot;

import java.util.HashMap;
import java.util.Map;

/**
 * The slot caps of v1 16.1 at runtime, per use and per receiver, the same for every receiver (HEROES_V2 6.2 and 6.14
 * rule 3: "a hero's hit is held to the v1 slot caps and {@code heroes.maxHit} whoever it lands on"). The definition's
 * Amounts were already held to the caps at the level-1 reference when it loaded ({@link HeroBalance}); at hit time an
 * Amount can read more than its reference (the hero's boss health in a {@code max_health_percent}, a big target's
 * missing health, a mark with more stacks, better gear), so each use of a move, a basic hit's {@code on_hit} or a
 * zone keeps one ledger, and what it lands on one receiver over all its hits, pulses and projectiles stays within
 * <pre>
 *   slot cap x ability power at the hero's level x heroes.powerScale / 100 x 1.5 (gear and buffs, v1 6.2) x form factor
 * </pre>
 * where the form factor is 1.5 for a lasting zone or a rain (v1 16.1) and 1.25 for a combo (HEROES_V2 6.5). Damage is held as
 * the raw amount, before the element and the damage factors (clamped 0..2) and {@code heroes.maxHit} per hit; DoTs as
 * one application's total ({@code per second x seconds}), heals and shields as points restored, stuns as seconds at
 * the slot's own cap (2 s a skill, 3 an ultimate, 1 a passive or finisher; v1 16.1). Nothing here knows
 * who the receiver is beyond the key the game gives it. Pure, unit-tested ({@code AbilityRuntimeTest}).
 */
public final class UseLedger {
    /** What a use lands, each held to its slot's cap; {@code STUN} in seconds, which no power or gear scales (A4). */
    public enum Kind { DAMAGE, DOT, HEAL, SHIELD, STUN }

    private final double[] caps = new double[Kind.values().length];
    private final Map<String, double[]> spent = new HashMap<>();

    private UseLedger(SlotCaps c, double scale) {
        caps[Kind.DAMAGE.ordinal()] = c.damage() * scale;
        caps[Kind.DOT.ordinal()] = c.dot() * scale;
        caps[Kind.HEAL.ordinal()] = c.heal() * scale;
        caps[Kind.SHIELD.ordinal()] = c.shield() * scale;
        caps[Kind.STUN.ordinal()] = c.stun();
    }

    /**
     * A ledger for one use of {@code slot} by a hero with {@code abilityPower} ({@link HeroRules#abilityPower}),
     * {@code powerScale} ({@code heroes.powerScale}, percent) and the form's factor ({@link #formFactor}).
     */
    public static UseLedger of(Slot slot, double abilityPower, int powerScale, double formFactor) {
        SlotCaps c = HeroCatalogue.CAPS.get(slot == null ? Slot.SKILL : slot);
        return new UseLedger(c, scale(abilityPower, powerScale, formFactor));
    }

    /** The multiplier on the level-1 caps: ability power x power scale x the gear cap x the form's factor. */
    static double scale(double abilityPower, int powerScale, double formFactor) {
        return Math.max(0, abilityPower) * HeroBalance.clampPowerScale(powerScale) / 100.0 * HeroCatalogue.GEAR_CAP
                * Math.max(0, formFactor);
    }

    /**
     * 1.5 for a zone that pulses more than once (v1 16.1) and for a rain (a lasting zone of impacts, HEROES_V2 6.5: "zone
     * caps per receiver"), 1.25 for a combo (6.5), else 1.
     */
    public static double formFactor(Move m) {
        if (m.form() == Form.COMBO) {
            return HeroCatalogue.COMBO_CAP_FACTOR;
        }
        if (m.form() == Form.RAIN) {
            return HeroCatalogue.ZONE_CAP_FACTOR;
        }
        return m.form() == Form.ZONE && m.ability().pulses() > 1 ? HeroCatalogue.ZONE_CAP_FACTOR : 1;
    }

    /** The most this use may land of {@code kind} on one receiver. */
    public double cap(Kind kind) {
        return caps[kind.ordinal()];
    }

    /** What is left of {@code kind} for {@code receiver}. */
    public double left(Kind kind, String receiver) {
        double[] s = spent.get(receiver);
        return Math.max(0, cap(kind) - (s == null ? 0 : s[kind.ordinal()]));
    }

    /** Takes up to {@code amount} of {@code kind} for {@code receiver}: returns what may land and books it. */
    public double take(Kind kind, String receiver, double amount) {
        double want = Math.max(0, amount);
        if (want == 0) {
            return 0;
        }
        double[] s = spent.computeIfAbsent(receiver, k -> new double[Kind.values().length]);
        double allowed = Math.min(want, Math.max(0, cap(kind) - s[kind.ordinal()]));
        s[kind.ordinal()] += allowed;
        return allowed;
    }
}
