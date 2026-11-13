package com.factionclash.hero;

import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.UseLedger;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;

/**
 * One use of a move, of a basic hit's {@code on_hit}, or of a zone (task A3): the hero, what is used, its slot, its
 * runtime caps per receiver ({@link UseLedger}) and what it already did once ({@code to: self} effects, a hop of its
 * own), so every hit, pulse and projectile of the use counts against the same caps. Projectiles and zones carry the
 * use; one that outlives its hero simply fizzles.
 */
public final class HeroUse {
    public final HeroEntity hero;
    public final HeroDef def;
    /** The move (null for a basic hit's {@code on_hit} or {@code every_nth_hit}). */
    public final HeroDef.Move move;
    public final Slot slot;
    public final Form form;
    /** A key for its statuses: the move's id, or {@code basic}. */
    public final String key;
    public final UseLedger ledger;
    public final double abilityPower;
    /** Effects that land once per use already landed (list index x 8 + effect index). */
    private final BitSet once = new BitSet();
    /** Energy for heals and shields given to allies this use (v1 14: at most 8 per use). */
    double healEnergy;
    /** Whether anything of the use landed on an enemy (followups, A5's timing). */
    public boolean landed;
    /** Whether the hero already struck its taunting pose for this use (A4). */
    boolean taunted;
    /** Where the area of a nova, zone or leap is (its allies-in-area effects use it). */
    Vec3 areaCenter;
    double areaRadius;

    HeroUse(HeroEntity hero, HeroDef def, HeroDef.Move move, Slot slot, Form form, String key, UseLedger ledger,
            double abilityPower) {
        this.hero = hero;
        this.def = def;
        this.move = move;
        this.slot = slot;
        this.form = form;
        this.key = key;
        this.ledger = ledger;
        this.abilityPower = abilityPower;
    }

    /** Whether the once-per-use effect at (list, index) still may land; books it. */
    boolean firstTime(int list, int index) {
        int bit = list * 8 + Math.min(7, index);
        if (once.get(bit)) {
            return false;
        }
        once.set(bit);
        return true;
    }

    /** Sets where the use's area is (its {@code to: allies} effects reach the allies inside it). */
    public void area(Vec3 c, double r) {
        areaCenter = c;
        areaRadius = r;
    }
}
