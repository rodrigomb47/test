package com.factionclash.logic.campaign;

/**
 * What a faction's war council believes of an enemy settlement's or lair's defence ({@code docs/design/recon.md}, the
 * owner's rule of 2026-11-25: "Only estimate enemy defence when the scout or other related role makes a report of what he
 * saw while focused on this particilar task"). The council knows only what a scout ordered to watch that one base counted
 * over a minute's watch and brought home ({@link Recon}): the faces counted, their visible gear and the heroes seen, while
 * the report is fresh. Nothing else makes an estimate: no prior, no losses seen at our walls, no raid party's or passing
 * look. What the council plans against beyond the count is {@link Reserve}'s. Pure rules; the game side is
 * {@code campaign/BaseWatch} (the samples), {@code work/ScoutJob} (the watch) and {@code FactionDirector}'s council.
 */
public final class Intel {
    private Intel() {
    }

    /** The council's estimate: the soldiers counted, their mean power, the heroes' war power. */
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
     * PROPOSED: a hundredth of a soldier. Strengths closer than this are equal: a float rounding of the gear's power never
     * decides a war (review of the knowledge review).
     */
    static final double TIE = 0.01;

    /**
     * PROPOSED (review of the knowledge review, 2026-11-19): whether a party of strength {@code party} is a match for a
     * defence of {@code defence}, its soldiers of mean power {@code soldierPower}: at least two thirds of the defence,
     * counted in whole soldiers, as a council counts its own garrison (the party is every soldier beyond a third kept at
     * home). Before, two thirds of a fractional head counted, so a council with 9 or 12 soldiers attacked a garrison like
     * its own at exact equality and only when a float rounding allowed it, and one with 10 or 11 never did.
     */
    public static boolean twoThirds(double party, double defence, double soldierPower) {
        double unit = Math.max(TIE, soldierPower);
        double heads = Math.max(0, defence) / unit;
        double need = Math.floor(heads * 2 / 3 + TIE) * unit;
        return party + TIE * unit >= need;
    }

    /**
     * PROPOSED (review of the knowledge review, 2026-11-19): a counter-attack's odds inside the raid interval: the party
     * outnumbers the defence 3 to 2, the defence's half again counted to the nearest whole soldier: a sliver of a soldier
     * (the gear's power, a hero weighed under the backbone) must not decide a war.
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

    // ---- Focused reconnaissance (docs/design/recon.md; task R1; the council's only input since task R5) ----

    /**
     * A focused report (recon.md 4): what a scout ordered to watch one base saw from its vantage over a whole watch. The
     * soldiers counted once each, by face ({@code faces}: their UUIDs, so a sighting of the same soldier elsewhere is not
     * counted again in the plan, {@link Reserve}); their mean visible power (0 when none was seen); the heroes seen, by hero
     * id, at their war power at full strength; the share of the yard's {@link ReconRules#POINTS} points that were in view at
     * least once over the watch (0 to 1); whether the yard's middle was; how long it watched, when its last sample was
     * taken ({@code at}), from where ({@code vantage}: "pillar 12", "high ground", "ground"), by whom ({@code by}) and
     * through what ({@code optic}: a {@link ReconOptic} row's tag, "spyglass"; empty for the eyes, task R12).
     */
    public record Recon(int soldiers, double power, java.util.Map<String, Double> heroes, double coverage, boolean middle,
                        long watchTicks, long at, String vantage, String by, java.util.Set<java.util.UUID> faces, String optic) {
        public Recon {
            soldiers = Math.max(0, soldiers);
            power = Math.max(0, power);
            coverage = Math.max(0, Math.min(1, coverage));
            watchTicks = Math.max(0, watchTicks);
            heroes = heroes == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(heroes));
            faces = faces == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(faces));
            vantage = vantage == null ? "" : vantage;
            by = by == null ? "" : by;
            optic = optic == null ? "" : optic;
        }

        /** A recon watched with the eyes (no instrument: an empty {@code optic} tag). */
        public Recon(int soldiers, double power, java.util.Map<String, Double> heroes, double coverage, boolean middle,
                     long watchTicks, long at, String vantage, String by, java.util.Set<java.util.UUID> faces) {
            this(soldiers, power, heroes, coverage, middle, watchTicks, at, vantage, by, faces, "");
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
     * The estimate from a focused report (recon.md 5.1): none (null) with no report, a blind one or a stale one; otherwise
     * the faces counted with no extrapolation, their mean power (1, a bare soldier, when none was seen) and the war power of
     * the heroes seen in that watch only. No prior, no drift, no losses, no hero
     * carried over from an older report. The count is a floor: what the council prepares for beyond it is the plan's
     * reserve ({@link Reserve}), which never lowers the estimate and never stands in for it.
     */
    public static Estimate estimate(Recon r, long now) {
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
        return mergeRecon(kept, newer, sidesTried, false);
    }

    /**
     * As {@link #mergeRecon(ReconFile, Recon, int)}; {@code attemptCounted}: the attempt the recon ends was counted already
     * (its order ended as lost before its scout came home: the roll call gave the scout up, then it walked in). A blind
     * recon then adds the sides its trip tried and no failure ({@link #reconSides}): one trip, one failed attempt (the
     * review of the focused reconnaissance round, findings 4 and 8: the back-off doubled for one attempt). A good one
     * replaces the kept recon as ever.
     */
    public static ReconFile mergeRecon(ReconFile kept, Recon newer, int sidesTried, boolean attemptCounted) {
        if (kept == null) {
            kept = ReconFile.NONE;
        }
        if (kept.fallen() || newer == null) {
            return kept;
        }
        if (!newer.good()) {
            return attemptCounted ? reconSides(kept, newer.at(), sidesTried) : reconFailed(kept, newer.at(), sidesTried);
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

    /**
     * The sides a trip tried, added with no failure counted (a blind recon whose attempt was counted when its order ended,
     * {@link #mergeRecon(ReconFile, Recon, int, boolean)}). As for a failure, a trip no newer than the kept good recon (a
     * late scout's) changes nothing: its sides were tried before that look.
     */
    public static ReconFile reconSides(ReconFile kept, long at, int sidesTried) {
        if (kept == null) {
            kept = ReconFile.NONE;
        }
        if (kept.fallen() || kept.recon() != null && at <= kept.recon().at()) {
            return kept;
        }
        return new ReconFile(kept.recon(), kept.failedAt(), kept.failures(), kept.sidesTried() | sidesTried, false);
    }

    /** The base seen in ruins (recon.md 4): ruins for good, the recon dropped. */
    public static ReconFile reconRuins() {
        return new ReconFile(null, 0, 0, 0, true);
    }
}
