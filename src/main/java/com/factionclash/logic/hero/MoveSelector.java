package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Band;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Followup;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Knockback;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Reaction;
import com.factionclash.logic.hero.HeroDef.Slot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The boss fight loop's choice of move (HEROES_V2 6.3, 6.8; 15.6 "MoveSelector"): one loop in every fight, whoever
 * the enemy is. The game calls {@link #check} every 5 ticks while the hero is NEUTRAL (between moves, spacing and
 * basic attacks), {@link #followup} when a move hits, and {@link #moveEnded} when a string's last recovery ends.
 *
 * <ul>
 * <li><b>The gap</b> between moves: phase 1 1.5-2.5 s, phase 2 1.0-2.0 s, phase 3 0.6-1.5 s, x0.7 enraged. A fight
 * opens with a full phase-1 gap from its first engaged enemy or from the end of the intro ({@link #fightStarted},
 * {@link #introEnded}), never shortened, so no move, reaction answer or ultimate starts sooner: with the class
 * minimums of 6.4 nothing lands before 1.9 s (2.3 s for a heavy move, 3.0 s for an ultimate). Each hero waits its own
 * gap. v1's 1 s global cooldown between the starts of two moves also holds (a followup is part of its string).</li>
 * <li><b>The pick</b>: the moves of the current phase with a ready charge (the ultimates: full energy and their least
 * gap), a target in reach and in sight, their v1 {@code use_when}, scored {@code weight x band fit x prefer fit x
 * novelty x posture} and drawn by weighted random from the hero's own seed.</li>
 * <li><b>Reactions</b> read whatever the hero fights through the same {@link Sense}: a true {@code when} whose move
 * is in the current phase and ready, with a passed chance, replaces the next pick (never a move in progress, never
 * before the gap). Each occurrence of the condition gets one roll; a condition held on gets a new roll each time the
 * reaction's cooldown passes; the cooldown starts when the answer starts. A reaction whose move is not in the current
 * phase waits for one that has it (kit-gaps 7).</li>
 * <li><b>Followups</b> form strings: when a move hits, each listed followup in order may start at once by its own
 * chance (it needs its own charge and reach, not the gap); at most {@link HeroCatalogue#STRING_MAX} moves in a string,
 * repeats included; a move follows itself only when {@code repeatable}, at most {@code max} times, and its repeats are
 * the same use (one charge for the run: zoomies' four bounds are one cooldown).</li>
 * </ul>
 *
 * <p>No token check: nothing here knows how many heroes press the target. It is not asked for a move while the hero
 * escapes ({@link Sense#escaping}; a cornered hero asks again), is stunned or silenced. Pure Java, seeded,
 * unit-tested ({@code MoveSelectorTest}).
 */
public final class MoveSelector {
    /** The pick runs every 5 ticks while NEUTRAL. */
    public static final int CHECK_TICKS = 5;
    /** v1's global cooldown: no move starts within 1 s of the start of the last one (a followup excepted). */
    public static final int GLOBAL_COOLDOWN_TICKS = 20;
    /** The gap range by phase (1-3), seconds. */
    public static final double[][] GAP_SECONDS = {{1.5, 2.5}, {1.0, 2.0}, {0.6, 1.5}};
    /** Score factors of 6.3. */
    public static final double ADJACENT_BAND = 0.25, PREFER_FIT = 2, NOVELTY_RECENT = 0.35, POSTURE_SLOPE = 0.6;
    public static final int RECENT_MOVES = 3;
    /** An ultimate whose balance gave no least gap waits at least this long after the last one. */
    public static final double DEFAULT_ULTIMATE_GAP = HeroCatalogue.ULTIMATE_GAP_MIN;
    /** A counter stance answers melee, so it is picked only with the target this close. */
    public static final double COUNTER_REACH = HeroCatalogue.CLOSE_MAX;

    /** How a move bends the posture factor: offence by aggression, zoning and defence by caution. */
    public enum Posture { OFFENSIVE, ZONING, DEFENSIVE }

    /** Why a move starts. */
    public enum Source { PICK, REACTION, FOLLOWUP, OPENER }

    /** A move to start now; {@code reaction} is the reaction it answers (or null). */
    public record Pick(Move move, Source source, Reaction reaction) {
    }

    private static final class Charges {
        int left;
        long nextAt;
    }

    private static final class ReactionState {
        final Reaction r;
        long readyAt = Long.MIN_VALUE / 4;
        boolean rolled;
        long lastRollAt;

        ReactionState(Reaction r) {
            this.r = r;
        }
    }

    private final HeroDef.V2 kit;
    private final double aggression, caution;
    private final boolean opensWithUltimate;
    private final SplittableRandom rng;
    private final Map<String, Charges> charges = new HashMap<>();
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private final List<ReactionState> reactions = new ArrayList<>();
    private ReactionState pending;
    private long gapEndsAt = Long.MAX_VALUE;
    private long lastStartAt = Long.MIN_VALUE / 4;
    private long lastUltimateAt;
    private boolean firstPick = true;
    private int stringLength;
    private int selfRepeats;
    private String stringLast;

    /**
     * A selector for one hero: its kit, its raw aggression and caution (0-100, the fight AI's own traits, not scaled
     * by {@code leaders.personalityStrength}), whether its temperament opens a fight with its ultimate when that is
     * ready (the chuunibyou's rule), and its seed.
     */
    public MoveSelector(HeroDef.V2 kit, int aggression, int caution, boolean opensWithUltimate, long seed) {
        this.kit = kit;
        this.aggression = Math.max(0, Math.min(100, aggression)) / 100.0;
        this.caution = Math.max(0, Math.min(100, caution)) / 100.0;
        this.opensWithUltimate = opensWithUltimate;
        this.rng = new SplittableRandom(seed);
        for (Reaction r : kit.boss().reactions()) {
            reactions.add(new ReactionState(r));
        }
    }

    // ------------------------------------------------------------------ the fight's clock

    /** The fight began (its first engaged enemy): a full phase-1 gap before the first move. */
    public void fightStarted(long now) {
        gapEndsAt = now + HeroRules.ticks(draw(1, false, true));
        firstPick = true;
        pending = null;
        stringLength = 0;
        stringLast = null;
        for (ReactionState rs : reactions) {
            rs.rolled = false;
        }
    }

    /** The intro ended: the opening gap counts from here. */
    public void introEnded(long now) {
        gapEndsAt = now + HeroRules.ticks(draw(1, false, true));
    }

    /** A move (or the last move of a string) has played its recovery: the next gap begins. */
    public void moveEnded(long now, int phase, boolean enraged) {
        gapEndsAt = now + HeroRules.ticks(draw(phase, enraged, false));
        stringLength = 0;
        stringLast = null;
    }

    /** The gap in seconds for a phase: drawn in its range, x0.7 enraged; the opening gap is never shortened. */
    private double draw(int phase, boolean enraged, boolean opening) {
        double[] r = GAP_SECONDS[Math.max(1, Math.min(3, phase)) - 1];
        double g = r[0] + rng.nextDouble() * (r[1] - r[0]);
        return opening || !enraged ? g : g * BossRules.ENRAGE_GAP_FACTOR;
    }

    public long gapEndsAt() {
        return gapEndsAt;
    }

    // ------------------------------------------------------------------ the 5-tick check

    /**
     * Every 5 ticks while NEUTRAL: updates the reactions and, once the gap and the global cooldown are over, returns
     * the move to start (and books it: its charge is spent), or null to keep spacing and basic attacks.
     */
    public Pick check(long now, Sense s) {
        if (s.stunned || s.silenced || s.escaping) {
            return null;
        }
        observeReactions(now, s);
        if (now < gapEndsAt || now - lastStartAt < GLOBAL_COOLDOWN_TICKS) {
            return null;
        }
        if (pending != null) {
            ReactionState rs = pending;
            pending = null;
            Move m = kit.move(rs.r.move());
            if (m != null && eligible(m, now, s)) {
                rs.readyAt = now + HeroRules.ticks(rs.r.cooldown());
                start(m, now, false);
                return new Pick(m, Source.REACTION, rs.r);
            }
        }
        if (firstPick && opensWithUltimate) {
            for (Move u : ultimates()) {
                if (eligible(u, now, s)) {
                    start(u, now, false);
                    return new Pick(u, Source.OPENER, null);
                }
            }
        }
        List<Move> cands = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        double total = 0;
        for (Move m : kit.allMoves()) {
            if (!eligible(m, now, s)) {
                continue;
            }
            double sc = score(m, s);
            if (sc > 0) {
                cands.add(m);
                scores.add(sc);
                total += sc;
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        double x = rng.nextDouble() * total;
        Move chosen = cands.get(cands.size() - 1);
        for (int i = 0; i < cands.size(); i++) {
            x -= scores.get(i);
            if (x < 0) {
                chosen = cands.get(i);
                break;
            }
        }
        start(chosen, now, false);
        return new Pick(chosen, Source.PICK, null);
    }

    private void observeReactions(long now, Sense s) {
        for (ReactionState rs : reactions) {
            if (!Conditions.holds(rs.r.when(), s)) {
                rs.rolled = false;
                continue;
            }
            if (pending != null || now < rs.readyAt) {
                continue;
            }
            Move m = kit.move(rs.r.move());
            if (m == null || !eligible(m, now, s)) {
                continue;
            }
            if (rs.rolled && now - rs.lastRollAt < HeroRules.ticks(rs.r.cooldown())) {
                continue;
            }
            rs.rolled = true;
            rs.lastRollAt = now;
            if (rng.nextInt(100) < rs.r.chance()) {
                pending = rs;
            }
        }
    }

    /** The reaction whose answer replaces the next pick, or null. */
    public Reaction pendingReaction() {
        return pending == null ? null : pending.r;
    }

    // ------------------------------------------------------------------ followups (6.3)

    /**
     * {@code played} hit: each of its followups in order may start at once by its own chance, if it has its charge
     * and reach and the string has room (at most 4 moves, repeats included; a move follows itself only when
     * {@code repeatable}, at most {@code max} times). Null: the string ends and {@code played} plays its full
     * recovery.
     */
    public Pick followup(long now, Move played, Sense s) {
        if (s.stunned || s.silenced || s.escaping || stringLength >= HeroCatalogue.STRING_MAX) {
            return null;
        }
        for (Followup fu : played.followups()) {
            Move m = kit.move(fu.move());
            if (m == null) {
                continue;
            }
            boolean repeat = m.id().equals(played.id());
            if (repeat) {
                int max = fu.max() > 0 ? fu.max() : HeroCatalogue.STRING_MAX - 1;
                if (!m.repeatable() || selfRepeats >= max) {
                    continue;
                }
            }
            if (!eligible(m, now, s, !repeat)) {
                continue;
            }
            if (rng.nextInt(100) < fu.chance()) {
                start(m, now, true, repeat);
                return new Pick(m, Source.FOLLOWUP, null);
            }
        }
        return null;
    }

    /** Moves played in the current string (0 between strings). */
    public int stringLength() {
        return stringLength;
    }

    // ------------------------------------------------------------------ booking, refunds

    private void start(Move m, long now, boolean followup) {
        start(m, now, followup, false);
    }

    /** Books a start; a move repeating itself in a string is the same use (one charge for the whole run). */
    private void start(Move m, long now, boolean followup, boolean repeat) {
        if (isUltimate(m)) {
            lastUltimateAt = now;
        } else if (!repeat) {
            Charges c = refill(m, now);
            if (c.left == maxCharges(m)) {
                c.nextAt = now + cooldownTicks(m);
            }
            c.left--;
        }
        lastStartAt = now;
        firstPick = false;
        recent.addFirst(m.id());
        while (recent.size() > RECENT_MOVES) {
            recent.removeLast();
        }
        if (followup) {
            stringLength++;
            selfRepeats = m.id().equals(stringLast) ? selfRepeats + 1 : 0;
        } else {
            stringLength = 1;
            selfRepeats = 0;
        }
        stringLast = m.id();
    }

    /**
     * The move did not start after all (v1 10.3: its target died or left reach during the wind-up): its charge is not
     * spent.
     */
    public void cancelled(Move m, long now) {
        if (isUltimate(m)) {
            lastUltimateAt = 0;
            return;
        }
        Charges c = refill(m, now);
        c.left = Math.min(maxCharges(m), c.left + 1);
    }

    /**
     * Its wind-up was interrupted (v1 10.3: a stun or a knockback above 0.5; an escape uses the same interrupt, 6.13):
     * half the cooldown is refunded.
     */
    public void interrupted(Move m, long now) {
        if (isUltimate(m)) {
            return;
        }
        Charges c = refill(m, now);
        if (c.left < maxCharges(m)) {
            c.nextAt = Math.max(now, c.nextAt - cooldownTicks(m) / 2);
        }
    }

    /** Charges ready now (an ultimate: 1 when ready by energy and gap, else 0; {@code energy} is the hero's). */
    public int charges(Move m, long now, int energy) {
        if (isUltimate(m)) {
            return ultimateReady(m, now, energy) ? 1 : 0;
        }
        return refill(m, now).left;
    }

    private Charges refill(Move m, long now) {
        Charges c = charges.computeIfAbsent(m.id(), k -> {
            Charges n = new Charges();
            n.left = maxCharges(m);
            return n;
        });
        int max = maxCharges(m);
        long cd = cooldownTicks(m);
        while (c.left < max && now >= c.nextAt) {
            c.left++;
            c.nextAt += Math.max(1, cd);
        }
        return c;
    }

    private static int maxCharges(Move m) {
        return Math.max(1, m.ability().charges());
    }

    private static long cooldownTicks(Move m) {
        return Math.max(0, HeroRules.ticks(m.ability().cooldown()));
    }

    private boolean ultimateReady(Move m, long now, int energy) {
        double gap = m.ability().numbers().minGap() > 0 ? m.ability().numbers().minGap() : DEFAULT_ULTIMATE_GAP;
        return HeroRules.ultimateReady(energy, now, lastUltimateAt, gap);
    }

    private List<Move> ultimates() {
        List<Move> l = new ArrayList<>();
        if (kit.ultimate() != null) {
            l.add(kit.ultimate());
        }
        if (kit.awakened() != null) {
            l.add(kit.awakened());
        }
        return l;
    }

    // ------------------------------------------------------------------ eligibility and score (6.3)

    /** Whether {@code m} may start now (the gap aside): phase, charge, reach, sight, footing, arena and use_when. */
    public boolean eligible(Move m, long now, Sense s) {
        return eligible(m, now, s, true);
    }

    private boolean eligible(Move m, long now, Sense s, boolean needCharge) {
        if (!m.phases().contains(s.phase)) {
            return false;
        }
        if (needCharge && (isUltimate(m) ? !ultimateReady(m, now, s.energy) : refill(m, now).left <= 0)) {
            return false;
        }
        Form f = m.form();
        if (s.climbing) {
            return false;
        }
        if (s.airborne && !(f == Form.SHOT || f == Form.VOLLEY || f == Form.BOLT || f == Form.BARRAGE
                || f == Form.RAIN)) {
            return false;
        }
        boolean mover = f == Form.LEAP || f == Form.DASH || f == Form.CHARGE;
        if ((s.inWater && mover) || (s.dashPathHazard && (f == Form.DASH || f == Form.CHARGE))) {
            return false;
        }
        if (s.lowCeiling && (f == Form.LEAP || knocksUp(m))) {
            return false;
        }
        if (needsTarget(m)) {
            Sense.TargetState t = s.target;
            if (!t.present || !t.visible || t.distance > reach(m) + 1e-9) {
                return false;
            }
        }
        return Conditions.useWhen(m, s);
    }

    /** {@code weight x band fit x prefer fit x novelty x posture} (6.3). */
    public double score(Move m, Sense s) {
        double w = Math.max(0, m.weight());
        if (w == 0) {
            return 0;
        }
        double band = s.target.present ? bandFit(m.bands(), bandOf(s.target.distance)) : 1;
        double prefer = m.preferWhen() != null && Conditions.holds(m.preferWhen(), s) ? PREFER_FIT : 1;
        double novelty = 1;
        if (!recent.isEmpty() && recent.peekFirst().equals(m.id()) && !m.repeatable()) {
            novelty = 0;
        } else if (recent.contains(m.id())) {
            novelty = NOVELTY_RECENT;
        }
        return w * band * prefer * novelty * postureFactor(m);
    }

    /** {@code 1 + 0.6 (a - 0.5)} for offence, {@code 1 + 0.6 (c - 0.5)} for zoning and defence. */
    public double postureFactor(Move m) {
        double t = posture(m) == Posture.OFFENSIVE ? aggression : caution;
        return 1 + POSTURE_SLOPE * (t - 0.5);
    }

    /**
     * A move's posture by its form: self, ally and allies moves and counter stances are defensive; zones, waves and
     * rain hold ground (zoning); every other form is offensive (F3's reading of 6.3's "offensive moves" and "zoning
     * and defensive ones").
     */
    public static Posture posture(Move m) {
        return switch (m.form()) {
            case SELF, ALLY, ALLIES, COUNTER -> Posture.DEFENSIVE;
            case ZONE, WAVE, RAIN -> Posture.ZONING;
            default -> Posture.OFFENSIVE;
        };
    }

    /** Close up to 4 blocks, mid up to 10, far beyond. */
    public static Band bandOf(double distance) {
        return distance <= HeroCatalogue.CLOSE_MAX ? Band.CLOSE : distance <= HeroCatalogue.MID_MAX ? Band.MID
                : Band.FAR;
    }

    /** 1 in one of its bands, 0.25 in a band next to one of them, else 0. */
    public static double bandFit(List<Band> bands, Band at) {
        if (bands.isEmpty() || bands.contains(at)) {
            return 1;
        }
        for (Band b : bands) {
            if (Math.abs(b.ordinal() - at.ordinal()) == 1) {
                return ADJACENT_BAND;
            }
        }
        return 0;
    }

    /** Self, ally and allies moves need no enemy target. */
    public static boolean needsTarget(Move m) {
        return !m.form().friendly();
    }

    /** How far from its target a move may start, by its form (its range, or its radius for self-centred areas). */
    public static double reach(Move m) {
        HeroDef.Ability a = m.ability();
        return switch (m.form()) {
            case SELF, ALLY, ALLIES -> Double.POSITIVE_INFINITY;
            case COUNTER -> COUNTER_REACH;
            case NOVA, WAVE -> positive(a.radius(), m.form(), false);
            case ZONE -> a.zoneAt() == HeroDef.ZoneAt.TARGET ? positive(a.range(), m.form(), true)
                    : positive(a.radius(), m.form(), false);
            default -> positive(a.range(), m.form(), true);
        };
    }

    private static double positive(double v, Form f, boolean range) {
        if (v > 0) {
            return v;
        }
        HeroCatalogue.FormSpec spec = HeroCatalogue.FORMS.get(f);
        double d = spec == null ? Double.NaN : range ? spec.rangeDefault() : spec.radiusDefault();
        return Double.isNaN(d) ? HeroCatalogue.CLOSE_MAX : d;
    }

    /** A move that lifts its receivers (a knockback with {@code up}): none under a ceiling of 3 blocks (6.12). */
    public static boolean knocksUp(Move m) {
        for (List<Effect> l : m.effectLists()) {
            for (Effect e : l) {
                if (e instanceof Knockback k && k.up() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isUltimate(Move m) {
        return m.ability().slot() == Slot.ULTIMATE || m.cls() == HeroDef.MoveClass.ULTIMATE;
    }

    // ------------------------------------------------------------------ timing (6.4)

    /**
     * A move's frames at a speed (the hero's attack-speed factor, 0.5-2.0): every part scales, but a wind-up and a
     * recovery never shorten below their class minimum (6.4: "telegraphs never shorten below these minimums, even in
     * phase 3 or enraged").
     */
    public static Frames frames(Move m, double speed) {
        double sp = Double.isFinite(speed) ? Math.max(HeroActions.MIN_SPEED, Math.min(HeroActions.MAX_SPEED, speed)) : 1;
        Frames f = m.frames();
        return new Frames(Math.max(m.cls().minWindup, f.windup() / sp), f.active() / sp,
                Math.max(m.cls().minRecovery, f.recovery() / sp));
    }
}
