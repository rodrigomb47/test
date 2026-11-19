package com.factionclash.logic.campaign;

/**
 * What a faction's war council believes of an enemy settlement's or lair's defence (knowledge review of 2026-11-19,
 * known leak 1: the council read the enemy's own roster, raid party and heroes, and punished every garrison that left on
 * a raid within five seconds). The council knows only what its own people saw ({@code docs/design/knowledge.md}): the
 * newest look its scouts (or its raiders) brought home of that base, older looks counting for less, a prior for what
 * nobody saw, and the enemy soldiers its people saw fall since. Pure rules; the game side is {@code campaign/BaseWatch}
 * and {@code FactionDirector}'s council.
 *
 * <p>A look sees only part of a walled yard: the soldiers in view are counted, the share of the yard out of view holds
 * what the prior says. A garrison that marched out unseen is still believed at home; one a scout saw gone is not.
 */
public final class Intel {
    private Intel() {
    }

    /**
     * PROPOSED: a look at a defence counts for less as it ages: the estimate drifts from what was seen to the prior over
     * this long (two days; a base hires a soldier about every quarter day while it has losses to make up).
     */
    public static final long FORGET_TICKS = 48000;
    /** Looks at one base on one trip whose shares in view differ by less than this are as good as each other: the newer wins. */
    static final double SAME_VIEW = 0.1;

    /**
     * One look at a defence: the soldiers seen (lair: its monsters), the share of the yard in view (0 to 1), the mean
     * fighting power of the soldiers seen (their visible armour and weapons; 0 when none was seen), the summed war power of
     * the heroes seen, and when it was made.
     */
    public record Look(int soldiers, double visible, double power, double heroPower, long at) {
        public Look {
            visible = Math.max(0, Math.min(1, visible));
            soldiers = Math.max(0, soldiers);
            heroPower = Math.max(0, heroPower);
        }
    }

    /** What the council assumes of a defence where nobody looked: so many soldiers of that mean power. */
    public record Prior(double soldiers, double power) {
    }

    /** The council's estimate: soldiers by heads (fractional), their mean power, the heroes' war power. */
    public record Estimate(double soldiers, double power, double heroPower) {
        /** The soldiers' summed power in the director's units. */
        public double soldierPower() {
            return soldiers * power;
        }

        /** "about N defenders": the soldiers rounded. */
        public int heads() {
            return (int) Math.round(soldiers);
        }
    }

    /**
     * The estimate from the newest {@code look} (null: nobody looked), the enemy soldiers seen to fall since it
     * ({@code losses}), the {@code prior} and the time now: the soldiers seen, plus the prior's share of the yard out of
     * view; as the look ages it drifts up toward the prior (never below what was seen), and the losses seen come off.
     * The soldiers out of view and the drift have the prior's power; the heroes seen are counted as seen.
     */
    public static Estimate estimate(Look look, int losses, Prior prior, long now) {
        double priorSoldiers = Math.max(0, prior.soldiers());
        double priorPower = Math.max(1, prior.power());
        if (look == null) {
            return new Estimate(Math.max(0, priorSoldiers - Math.max(0, losses)), priorPower, 0);
        }
        double unseen = priorSoldiers * (1 - look.visible());
        double seen = look.soldiers() + unseen;
        double drift = Math.max(0, priorSoldiers - seen) * Math.min(1, Math.max(0, now - look.at()) / (double) FORGET_TICKS);
        double soldiers = seen + drift;
        double seenPower = look.soldiers() > 0 ? Math.max(1, look.power()) : priorPower;
        double power = soldiers <= 0 ? priorPower : (look.soldiers() * seenPower + (unseen + drift) * priorPower) / soldiers;
        return new Estimate(Math.max(0, soldiers - Math.max(0, losses)), power, look.heroPower());
    }

    /**
     * Of two looks at one base (one trip's, or a trip's and the raiders'), the one that says more: the larger share of
     * the yard in view, else the newer. Either may be null.
     */
    public static Look better(Look a, Look b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (Math.abs(a.visible() - b.visible()) >= SAME_VIEW) {
            return a.visible() > b.visible() ? a : b;
        }
        return b.at() >= a.at() ? b : a;
    }

    /**
     * What anyone knows of a Hollow lair it never looked at: lairs start with {@code baseCap} monsters and grow by one a
     * day to {@code maxCap} (the campaign's rules, which every faction knows), so a lair is assumed as grown as the days
     * since the campaign began allow.
     */
    public static double lairPrior(int baseCap, int maxCap, long sinceStart) {
        return Math.min(Math.max(baseCap, maxCap), baseCap + Math.max(0, sinceStart) / (double) Whereabouts.TICKS_PER_DAY);
    }
}
