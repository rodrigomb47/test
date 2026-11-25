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
     * ({@code losses}, the last at {@code lossAt}), the war power of the heroes seen there as it counts now
     * ({@link #heroPower}), the {@code prior} and the time now: the soldiers seen, plus the prior's share of the yard out
     * of view; as the look ages it drifts up toward the prior (never below what was seen), and the losses seen come off,
     * counting for less as they age as a look does (a base hires replacements; review of the knowledge review,
     * 2026-11-19: losses seen days ago kept a rebuilt garrison believed empty). The soldiers out of view and the drift
     * have the prior's power.
     */
    public static Estimate estimate(Look look, int losses, long lossAt, double heroPower, Prior prior, long now) {
        double priorSoldiers = Math.max(0, prior.soldiers());
        double priorPower = Math.max(1, prior.power());
        double lost = Math.max(0, losses) * weight(lossAt, now);
        double heroes = Math.max(0, heroPower);
        if (look == null) {
            return new Estimate(Math.max(0, priorSoldiers - lost), priorPower, heroes);
        }
        double unseen = priorSoldiers * (1 - look.visible());
        double seen = look.soldiers() + unseen;
        double drift = Math.max(0, priorSoldiers - seen) * (1 - weight(look.at(), now));
        double soldiers = seen + drift;
        double seenPower = look.soldiers() > 0 ? Math.max(1, look.power()) : priorPower;
        double power = soldiers <= 0 ? priorPower : (look.soldiers() * seenPower + (unseen + drift) * priorPower) / soldiers;
        return new Estimate(Math.max(0, soldiers - lost), power, heroes);
    }

    /**
     * How much a sighting made at {@code at} still counts at {@code now}: all of it when fresh, nothing after
     * {@link #FORGET_TICKS} (a look, a loss seen at our walls, a hero seen at a base). A sighting dated in the future
     * (a clock set back) counts fully.
     */
    public static double weight(long at, long now) {
        return 1 - Math.min(1, Math.max(0, now - at) / (double) FORGET_TICKS);
    }

    /** A hero seen at a base: its war power at full strength (its bar and level) and when it was last seen there. */
    public record Hero(double power, long at) {
    }

    /**
     * The heroes' war power the council counts at a base now (review of the knowledge review, 2026-11-19): each sighting
     * counts for less as it ages, nothing after {@link #FORGET_TICKS} (a hero goes raiding, falls, or was passing through;
     * before, a hero seen once was counted there for ever at full strength, and the council never struck that base).
     */
    public static double heroPower(java.util.Collection<Hero> heroes, long now) {
        double sum = 0;
        for (Hero h : heroes) {
            sum += Math.max(0, h.power()) * weight(h.at(), now);
        }
        return sum;
    }

    /**
     * The heroes of a report ("id=power@at" joined by commas) by id; an entry saved before the sightings kept their time
     * ("id=power") was seen at {@code seenAt} (its report's look). Unreadable parts are dropped.
     */
    public static java.util.Map<String, Hero> heroes(String s, long seenAt) {
        java.util.Map<String, Hero> out = new java.util.LinkedHashMap<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String rest = part.substring(eq + 1);
            int atSign = rest.indexOf('@');
            try {
                double power = Double.parseDouble(atSign < 0 ? rest : rest.substring(0, atSign));
                long at = atSign < 0 ? seenAt : Long.parseLong(rest.substring(atSign + 1));
                out.put(part.substring(0, eq), new Hero(power, at));
            } catch (NumberFormatException ignored) {
                // a damaged entry is forgotten
            }
        }
        return out;
    }

    /** The heroes as a report keeps them: "id=power@at" joined by commas. */
    public static String heroes(java.util.Map<String, Hero> heroes) {
        StringBuilder b = new StringBuilder();
        heroes.forEach((id, h) -> b.append(b.length() == 0 ? "" : ",").append(id).append('=').append((float) h.power()).append('@').append(h.at()));
        return b.toString();
    }

    /** Two reports' heroes: every hero seen on either, each with its newest sighting (its power then). */
    public static java.util.Map<String, Hero> mergeHeroes(java.util.Map<String, Hero> older, java.util.Map<String, Hero> newer) {
        java.util.Map<String, Hero> out = new java.util.LinkedHashMap<>(older);
        newer.forEach((id, h) -> out.merge(id, h, (a, b) -> b.at() >= a.at() ? b : a));
        return out;
    }

    /**
     * PROPOSED: a hundredth of a soldier. Strengths closer than this are equal: a float rounding of the gear's power, the
     * prior's power mixed into an estimate, a loss faded by a tick never decide a war (review of the knowledge review).
     */
    static final double TIE = 0.01;

    /**
     * PROPOSED (review of the knowledge review, 2026-11-19): whether a party of strength {@code party} is a match for a
     * defence of {@code defence}, its soldiers of mean power {@code soldierPower}: at least two thirds of the defence,
     * counted in whole soldiers, as a council counts its own garrison (the party is every soldier beyond a third kept at
     * home). Before, two thirds of a fractional head counted, so against a garrison like its own (the prior) a council
     * with 9 or 12 soldiers attacked, at exact equality and only when a float rounding allowed it, and one with 10 or 11
     * never did.
     */
    public static boolean twoThirds(double party, double defence, double soldierPower) {
        double unit = Math.max(TIE, soldierPower);
        double heads = Math.max(0, defence) / unit;
        double need = Math.floor(heads * 2 / 3 + TIE) * unit;
        return party + TIE * unit >= need;
    }

    /**
     * PROPOSED (review of the knowledge review, 2026-11-19): a counter-attack's odds inside the raid interval: the party
     * outnumbers the defence 3 to 2, the defence's half again counted to the nearest whole soldier. The estimate is
     * fractional (a loss seen at our walls fades from the first tick), so a sliver of a soldier must not decide a war.
     */
    public static boolean threeToTwo(double party, double defence, double soldierPower) {
        double unit = Math.max(TIE, soldierPower);
        double need = Math.round(Math.max(0, defence) / unit * 3 / 2) * unit;
        return party + TIE * unit >= need;
    }

    /**
     * The war council's party ({@code FactionDirector}): every soldier beyond a third of the {@code atHome} garrison
     * (heroes count by heads there), at least {@code minParty}, always leaving {@code homeDefenders} at home, no more than
     * the {@code draftable} soldiers (a hero is never drafted); 0 when it cannot send one.
     */
    public static int partySize(int atHome, int draftable, int homeDefenders, int minParty) {
        int keep = Math.max(homeDefenders, (atHome + 2) / 3);
        int party = Math.min(Math.max(minParty, atHome - keep), draftable);
        return atHome < homeDefenders + party || party < minParty ? 0 : party;
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

    // ---- Focused reconnaissance (docs/design/recon.md; task R1, additive: nothing reads these until the council's
    // switch, R5, which deletes the look, the prior and the losses above) ----

    /**
     * A focused report (recon.md 4): what a scout ordered to watch one base saw from its vantage over a whole watch. The
     * soldiers counted once each, by face ({@code faces}: their UUIDs, so a sighting of the same soldier elsewhere is not
     * counted again in the plan, {@link Reserve}); their mean visible power (0 when none was seen); the heroes seen, by hero
     * id, at their war power at full strength; the share of the yard's {@link ReconRules#POINTS} points that were in view at
     * least once over the watch (0 to 1); whether the yard's middle was; how long it watched, when its last sample was
     * taken ({@code at}), from where ({@code vantage}: "pillar 12", "high ground", "ground") and by whom ({@code by}).
     */
    public record Recon(int soldiers, double power, java.util.Map<String, Double> heroes, double coverage, boolean middle,
                        long watchTicks, long at, String vantage, String by, java.util.Set<java.util.UUID> faces) {
        public Recon {
            soldiers = Math.max(0, soldiers);
            power = Math.max(0, power);
            coverage = Math.max(0, Math.min(1, coverage));
            watchTicks = Math.max(0, watchTicks);
            heroes = heroes == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(heroes));
            faces = faces == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(faces));
            vantage = vantage == null ? "" : vantage;
            by = by == null ? "" : by;
        }

        /** The yard points that were in view, of {@link ReconRules#POINTS}. */
        public int points() {
            return (int) Math.round(coverage * ReconRules.POINTS);
        }

        /**
         * A good look (recon.md 3.4): at least {@link ReconRules#MIN_POINTS} of the yard's points and its middle in view,
         * watched at least {@link ReconRules#MIN_WATCH_TICKS}. Coverage alone is not enough: a look at 40% of the yard that
         * misses its middle misses the guards' routes. A report that is not good is blind ("could not see inside"): it is
         * never "about 0", and it is no estimate at all.
         */
        public boolean good() {
            return points() >= ReconRules.MIN_POINTS && middle && watchTicks >= ReconRules.MIN_WATCH_TICKS;
        }

        /** The heroes seen in this watch: their summed war power at full strength. */
        public double heroPower() {
            double sum = 0;
            for (double p : heroes.values()) {
                sum += Math.max(0, p);
            }
            return sum;
        }
    }

    /** The coverage of a look that had {@code points} of the yard's {@link ReconRules#POINTS} points in view. */
    public static double coverage(int points) {
        return Math.max(0, Math.min(ReconRules.POINTS, points)) / (double) ReconRules.POINTS;
    }

    /**
     * Whether a council may judge a base by this report now (recon.md 2.3): a good look no older than
     * {@link ReconRules#FRESH_TICKS} (12000 in, 12001 out). A stale report means "order a new recon", never "assume
     * something": it does not drift toward anything as it ages.
     */
    public static boolean usable(Recon r, long now) {
        return r != null && r.good() && now - r.at() <= ReconRules.FRESH_TICKS;
    }

    /**
     * The estimate from a focused report (recon.md 5.1), beside the old {@link #estimate}: none (null) with no report, a
     * blind one or a stale one; otherwise the faces counted with no extrapolation, their mean power (1, a bare soldier,
     * when none was seen) and the war power of the heroes seen in that watch only. No prior, no drift, no losses, no hero
     * carried over from an older report. The count is a floor: what the council prepares for beyond it is the plan's
     * reserve ({@link Reserve}), which never lowers the estimate and never stands in for it.
     */
    public static Estimate estimateRecon(Recon r, long now) {
        if (!usable(r, now)) {
            return null;
        }
        return new Estimate(r.soldiers(), Math.max(1, r.power()), r.heroPower());
    }

    /**
     * What a faction keeps of its focused reports on one base (recon.md 4): the newest good look ({@code recon}, null when
     * none), the back-off of the attempts that failed since ({@code failedAt}, {@code failures}), the sides tried since the
     * last good look ({@code sidesTried}, a mask of {@link ReconRules.Side} bits) and whether the base was seen in ruins.
     */
    public record ReconFile(Recon recon, long failedAt, int failures, int sidesTried, boolean fallen) {
        /** A base nobody has watched. */
        public static final ReconFile NONE = new ReconFile(null, 0, 0, 0, false);

        public ReconFile {
            failures = Math.max(0, failures);
            sidesTried &= ReconRules.Side.ALL;
        }
    }

    /**
     * A newer report of the base merged into the kept one (recon.md 4's table). {@code newer} null is a passing look (a
     * position only): the kept recon is never cleared, aged or replaced by it. A good recon newer than the kept one
     * replaces it, clears the failures and the sides tried; one older than (or as old as) the kept one, a late scout's, is
     * ignored. A blind recon keeps the kept one and is a failed attempt ({@link #reconFailed}) with the sides its trip
     * tried. Ruins stay ruins.
     */
    public static ReconFile mergeRecon(ReconFile kept, Recon newer, int sidesTried) {
        if (kept == null) {
            kept = ReconFile.NONE;
        }
        if (kept.fallen() || newer == null) {
            return kept;
        }
        if (!newer.good()) {
            return reconFailed(kept, newer.at(), sidesTried);
        }
        if (kept.recon() != null && newer.at() <= kept.recon().at()) {
            return kept;
        }
        return new ReconFile(newer, kept.failedAt(), 0, 0, false);
    }

    /**
     * A failed attempt on the base (recon.md 2.1 and 2.4): a blind recon delivered, a scout home with no recon of it, or a
     * scout lost on the way. The one step of the back-off, so each failure counts once: a blind recon counts here when it
     * is merged ({@link #mergeRecon}), the other two when the order ends ({@link ReconRules.Ending#countsFailure}). A
     * failure no newer than the kept good recon (a late scout's) is not a consecutive failure and changes nothing.
     */
    public static ReconFile reconFailed(ReconFile kept, long at, int sidesTried) {
        if (kept == null) {
            kept = ReconFile.NONE;
        }
        if (kept.fallen() || kept.recon() != null && at <= kept.recon().at()) {
            return kept;
        }
        return new ReconFile(kept.recon(), Math.max(kept.failedAt(), at), kept.failures() + 1, kept.sidesTried() | sidesTried, false);
    }

    /** The base seen in ruins (recon.md 4): ruins for good, the recon dropped. */
    public static ReconFile reconRuins() {
        return new ReconFile(null, 0, 0, 0, true);
    }
}
