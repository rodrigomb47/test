package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.EndOfFight;
import com.factionclash.logic.hero.HeroDef.StandsFor;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.hero.HeroDef.WhenLosing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a losing fight ends for a hero (HEROES_V2 6.13, the owner's decision of 2026-11-13: "a hero shouldnt yield.
 * they either try to escape or fight until die depending of what they want"): the parts a hero file decides, read
 * when it loads. Its kind (escape or last stand) from its aggression, honour and caution, a temperament rule and the
 * authored override; where an escaping hero stands anyway (its home, beside its leader) from its loyalty; and its
 * escape threshold {@code flee_at}. Nothing here takes the enemy's kind as an input: there is no rule for players.
 *
 * <p>The runtime decision (F3, HEROES_V2 6.13 and MOBILITY_AND_WORK 7.7) sits on top of these: under {@code flee_at}
 * in any fight ({@link #lowHealth}), a lost raid ({@link #lostRaid}) and a lost base ({@link #lostBase}); the
 * situation rules ({@link #heldBy}), the 10-minute window ({@link #inWindow}), an unspent {@code rebirth} and
 * {@code heroes.escape}; then the last-stand beat once per fight. Around the escape: cornered ({@link #cornered}),
 * its end ({@link #escapeOver}), the refuge ({@link #refuge}), the stay away ({@link #mayReturn}) and the pursuing
 * heroes that let it go ({@link #heroPursues}). The game calls {@link #decide} after every hit that lands on the
 * hero and once a tick ({@code HeroEscapeGoal}, A6/A12). F5 adds the rest of MOBILITY_AND_WORK 7.7: a scout's step
 * aside or retreat, which uses an escape's movement and nothing else ({@link Departure}), who pursues, the wild's
 * distance, no rewards for an escape, the morale changes, and the war council while it is away. The movement rules
 * an escape lifts and keeps are {@code MobilityRules.holds}. Pure, unit-tested ({@code EscapeRulesTest}).
 */
public final class EscapeRules {
    private EscapeRules() {
    }

    /** aggression + honour - caution at or above this: a last stand (raw 0-100 values, never scaled). */
    public static final int NERVE_LAST_STAND = 65;
    /** Loyalty at which an escaping hero stands at home, and beside its fighting leader. */
    public static final int HOME_LOYALTY = 70, LEADER_LOYALTY = 85;
    /** {@code flee_at}: a third of {@code retreat_below} within these, then under the last phase by the margin. */
    public static final int FLEE_MIN = 8, FLEE_MAX = 15, FLEE_FLOOR = 5, LAST_PHASE_MARGIN = 10;
    /** An escaping hero whose last phase starts under this leaves as the phase begins (a NOTE). */
    public static final int LATE_LAST_PHASE = 18;

    public static int nerve(int aggression, int honour, int caution) {
        return aggression + honour - caution;
    }

    /**
     * The kind its traits and temperament give, before any authored override: a last stand when the nerve is 65 or
     * more; {@code sleepy} always stands (its rule "bold under half health": once it is that awake it sees the fight
     * through).
     */
    public static WhenLosing byTraits(Temperament t, int aggression, int honour, int caution) {
        if (t == Temperament.SLEEPY) {
            return WhenLosing.LAST_STAND;
        }
        return nerve(aggression, honour, caution) >= NERVE_LAST_STAND ? WhenLosing.LAST_STAND : WhenLosing.ESCAPE;
    }

    public static WhenLosing byTraits(Temperament t, Map<Trait, Integer> traits) {
        return byTraits(t, traits.getOrDefault(Trait.AGGRESSION, 50), traits.getOrDefault(Trait.HONOUR, 50),
                traits.getOrDefault(Trait.CAUTION, 50));
    }

    /**
     * The hero's kind: the authored {@code personality.when_losing} first, then a v1 {@code retreat_below} the file
     * wrote as 0 ("never retreats"), then {@link #byTraits}.
     */
    public static WhenLosing kind(Temperament t, Map<Trait, Integer> traits, WhenLosing authored, int retreatBelow,
                                  boolean retreatAuthored) {
        if (authored != null) {
            return authored;
        }
        if (retreatAuthored && retreatBelow == 0) {
            return WhenLosing.LAST_STAND;
        }
        return byTraits(t, traits);
    }

    /** Where an escaping hero stands anyway: the authored list ({@code []} nowhere), else from its loyalty. */
    public static List<StandsFor> standsFor(int loyalty, List<StandsFor> authored) {
        if (authored != null) {
            return List.copyOf(authored);
        }
        List<StandsFor> l = new ArrayList<>();
        if (loyalty >= HOME_LOYALTY) {
            l.add(StandsFor.HOME);
        }
        if (loyalty >= LEADER_LOYALTY) {
            l.add(StandsFor.LEADER);
        }
        return List.copyOf(l);
    }

    /** A half rounds up (the escape threshold, and a {@code retreat_below} from the personality: kit-gaps 11). */
    public static int roundHalfUp(double v) {
        return (int) Math.floor(v + 0.5 + 1e-9);
    }

    /**
     * HEROES_V2 5.4: the {@code retreat_below} a hero's personality gives when the file writes none: the role's
     * default + 20 (c - 0.5) - 10 (a - 0.5), with the raw traits as shares, clamped to 0-60 and rounded half up to a
     * whole percent.
     */
    public static int retreatBelow(int roleDefault, int aggression, int caution) {
        double v = roleDefault + 20 * (caution / 100.0 - 0.5) - 10 * (aggression / 100.0 - 0.5);
        return roundHalfUp(Math.max(0, Math.min(60, v)));
    }

    /**
     * {@code flee_at} (percent of max health): a third of {@code retreat_below}, a half rounding up, within 8-15, and
     * never above the last phase's start less 10, with a floor of 5. A phase 3 that starts at 25 is fought down to
     * 15-8%.
     */
    public static int fleeAt(int retreatBelow, int lastPhaseAt) {
        int f = Math.max(FLEE_MIN, Math.min(FLEE_MAX, roundHalfUp(retreatBelow / 3.0)));
        f = Math.min(f, lastPhaseAt - LAST_PHASE_MARGIN);
        return Math.max(FLEE_FLOOR, f);
    }

    /** Everything at once, for the reader. */
    public static EndOfFight end(Temperament t, Map<Trait, Integer> traits, WhenLosing authored,
                                 List<StandsFor> authoredStands, int retreatBelow, boolean retreatAuthored,
                                 int lastPhaseAt) {
        WhenLosing k = kind(t, traits, authored, retreatBelow, retreatAuthored);
        List<StandsFor> s = k == WhenLosing.LAST_STAND ? List.of()
                : standsFor(traits.getOrDefault(Trait.LOYALTY, 50), authoredStands);
        return new EndOfFight(k, s, fleeAt(retreatBelow, lastPhaseAt), retreatBelow);
    }

    // ================================================================== the runtime decision (6.13, MOBILITY_AND_WORK 7.7)

    /** {@code heroes.escapeAwaySeconds} (60-1200) and {@code heroes.escapeCooldownSeconds} (120-1800). */
    public static final int AWAY_DEFAULT = 300, AWAY_MIN = 60, AWAY_MAX = 1200;
    public static final int WINDOW_DEFAULT = 600, WINDOW_MIN = 120, WINDOW_MAX = 1800;
    /** Cornered: no path or link away from its pursuers for 3 s; it fights back and runs again once a way opens. */
    public static final int CORNERED_TICKS = 60;
    /** An escape ends inside the refuge's walls, or when no pursuer has been within 48 blocks for 10 s. */
    public static final double CLEAR_RADIUS = 48;
    public static final int CLEAR_TICKS = 200;
    /** In the wild it keeps at least 64 blocks from the fight it left for its stay-away time. */
    public static final double WILD_KEEP_AWAY = 64;
    /** It returns to its duty once it has been away its time and is above half health. */
    public static final double RETURN_HEALTH_SHARE = 0.5;
    /** A hero with honour 60 or more lets an escaping hero go (5.4): it does not chase or strike it. */
    public static final int HONOUR_LETS_GO = 60;
    /** "Near" a raid's target: inside its walls box plus 16 blocks. */
    public static final double NEAR_TARGET_MARGIN = 16;

    public static int awaySeconds(int config) {
        return Math.max(AWAY_MIN, Math.min(AWAY_MAX, config));
    }

    public static int windowSeconds(int config) {
        return Math.max(WINDOW_MIN, Math.min(WINDOW_MAX, config));
    }

    /** What has gone wrong: the fight, the raid or the base. */
    public enum Trigger { LOW_HEALTH, LOST_RAID, LOST_BASE }

    /**
     * What the hero does: fights on; escapes (breaks off for good with any mobility, MOBILITY_AND_WORK 7.7); says its
     * last-stand line once and fights on; finishes the fight it is in and then goes (a lost raid or base for a hero
     * that does not escape); or goes on foot with the party or to the nearest standing base. There is no yield.
     */
    public enum Outcome { FIGHT_ON, ESCAPE, LAST_STAND_BEAT, FINISH_FIGHT, GO_ON_FOOT }

    /**
     * Where the hero is and what holds it, for one decision. {@code atHome}: inside its home base (the walls box plus
     * 16 blocks; a leader's home is its capital); {@code homeHeartStands}: that base's heart stands;
     * {@code leaderFightingNear}: its faction's leader (another hero) is fighting within 32 blocks;
     * {@code rebirthUnspent}: it has a {@code rebirth} it has not used in this fight; {@code escapeEnabled}:
     * {@code heroes.escape}; {@code lastEscapeAt}: the game time its last escape began (0: never). Nothing in it says
     * who the enemy is.
     */
    public record Context(boolean atHome, boolean homeHeartStands, boolean leaderFightingNear, boolean rebirthUnspent,
                          boolean escapeEnabled, long now, long lastEscapeAt, int windowSeconds) {
    }

    /** Inside the 10-minute window after an escape began: no trigger starts another one. */
    public static boolean inWindow(long now, long lastEscapeAt, int windowSeconds) {
        return lastEscapeAt > 0 && now - lastEscapeAt >= 0 && now - lastEscapeAt < windowSeconds(windowSeconds) * 20L;
    }

    /**
     * The situation rule that holds an escaping hero where it is, or null: {@code home} inside its home base while
     * that base's heart stands, {@code leader} while its leader fights within 32 blocks. A stand ends with its reason.
     */
    public static StandsFor heldBy(EndOfFight e, Context c) {
        if (e.kind() != WhenLosing.ESCAPE) {
            return null;
        }
        if (e.standsFor().contains(StandsFor.HOME) && c.atHome() && c.homeHeartStands()) {
            return StandsFor.HOME;
        }
        if (e.standsFor().contains(StandsFor.LEADER) && c.leaderFightingNear()) {
            return StandsFor.LEADER;
        }
        return null;
    }

    /**
     * Whether an escape may start now: an escaping-kind hero that no situation holds, outside the window of its last
     * escape, with {@code heroes.escape} on.
     */
    public static boolean mayEscape(EndOfFight e, Context c) {
        return e.kind() == WhenLosing.ESCAPE && c.escapeEnabled() && heldBy(e, c) == null
                && !inWindow(c.now(), c.lastEscapeAt(), c.windowSeconds());
    }

    /**
     * Under its threshold in any fight (read after every hit that lands and once a tick): an escaping hero that may
     * escape breaks off at once; one that may not (held by a situation, inside the window, with an unspent
     * {@code rebirth}, or with {@code heroes.escape} off) and every last-stand hero say their last-stand line the
     * first time ({@code beatDone} false) and fight on. "Under" is strictly below {@code flee_at} percent.
     */
    public static Outcome lowHealth(EndOfFight e, double healthShare, boolean beatDone, Context c) {
        if (healthShare * 100 >= e.fleeAt()) {
            return Outcome.FIGHT_ON;
        }
        if (mayEscape(e, c) && !c.rebirthUnspent()) {
            return Outcome.ESCAPE;
        }
        return beatDone ? Outcome.FIGHT_ON : Outcome.LAST_STAND_BEAT;
    }

    /**
     * Its raid falls back (beaten back, or its leader fell or escaped): an escaping hero in or near the target or
     * fighting escapes home; elsewhere, or inside the window, it goes home with the party on foot. A hero that does
     * not escape (a last-stand hero, or one held by a situation) finishes the fight it is in, then goes.
     */
    public static Outcome lostRaid(EndOfFight e, boolean inOrNearTarget, boolean fighting, Context c) {
        if (mayEscape(e, c) && (inOrNearTarget || fighting)) {
            return Outcome.ESCAPE;
        }
        return fighting && (e.kind() == WhenLosing.LAST_STAND || heldBy(e, c) != null) ? Outcome.FINISH_FIGHT
                : Outcome.GO_ON_FOOT;
    }

    /**
     * The heart of the base it defends is destroyed: a {@code home} stand ends with it, so an escaping hero escapes at
     * once whatever its health (to the nearest standing base, else the wild); inside the window it goes there on foot.
     * A last-stand hero finishes the fight it is in, then goes.
     */
    public static Outcome lostBase(EndOfFight e, boolean fighting, Context c) {
        Context lost = new Context(c.atHome(), false, c.leaderFightingNear(), c.rebirthUnspent(), c.escapeEnabled(),
                c.now(), c.lastEscapeAt(), c.windowSeconds());
        if (mayEscape(e, lost)) {
            return Outcome.ESCAPE;
        }
        return fighting && (e.kind() == WhenLosing.LAST_STAND || heldBy(e, lost) != null) ? Outcome.FINISH_FIGHT
                : Outcome.GO_ON_FOOT;
    }

    /** Cornered: no way away from its pursuers for 3 s. It fights back where it stands until one opens. */
    public static boolean cornered(int ticksWithoutWayAway) {
        return ticksWithoutWayAway >= CORNERED_TICKS;
    }

    /** The escape is over: inside its refuge's walls, or 10 s with no pursuer within 48 blocks. */
    public static boolean escapeOver(boolean insideRefugeWalls, int ticksWithNoPursuerNear) {
        return insideRefugeWalls || ticksWithNoPursuerNear >= CLEAR_TICKS;
    }

    /** It goes back to its duty once it has been away its time ({@code heroes.escapeAwaySeconds}) and is above half. */
    public static boolean mayReturn(long now, long escapedAt, int awaySeconds, double healthShare) {
        return now - escapedAt >= awaySeconds(awaySeconds) * 20L && healthShare > RETURN_HEALTH_SHARE;
    }

    /** Where an escape stands (task A6b). */
    public enum EscapeStage { RUNNING, CORNERED, OVER }

    /**
     * One escape from its start to its end (MOBILITY_AND_WORK 7.7; task A6b): the hero runs while it has a way away
     * from its pursuers; with none for 3 s it is cornered and fights back where it stands, and it runs again as soon as a
     * way opens; the escape is over inside its refuge's walls or once no pursuer has been within 48 blocks for 10 s.
     * The game reports what it sees every {@code dt} ticks; nothing here says who the pursuers are.
     */
    public static final class Escape {
        private final long startedAt;
        private final double originX, originZ;
        private final String refuge;
        private EscapeStage stage = EscapeStage.RUNNING;
        private int noWay, clear;

        public Escape(long startedAt, double originX, double originZ, String refuge) {
            this.startedAt = startedAt;
            this.originX = originX;
            this.originZ = originZ;
            this.refuge = refuge;
        }

        /**
         * {@code dt} ticks went by: {@code wayAway} it could move away from its pursuers, {@code pursuerNear} one was
         * within 48 blocks, {@code insideRefuge} it stands inside its refuge's walls. Returns the stage now.
         */
        public EscapeStage tick(int dt, boolean wayAway, boolean pursuerNear, boolean insideRefuge) {
            if (stage == EscapeStage.OVER) {
                return stage;
            }
            int d = Math.max(0, dt);
            clear = pursuerNear ? 0 : clear + d;
            if (escapeOver(insideRefuge, clear)) {
                stage = EscapeStage.OVER;
                return stage;
            }
            if (wayAway) {
                noWay = 0;
                stage = EscapeStage.RUNNING;
            } else {
                noWay += d;
                if (cornered(noWay)) {
                    stage = EscapeStage.CORNERED;
                }
            }
            return stage;
        }

        public EscapeStage stage() {
            return stage;
        }

        public long startedAt() {
            return startedAt;
        }

        /** Where the fight it left was. */
        public double originX() {
            return originX;
        }

        public double originZ() {
            return originZ;
        }

        /** The base it runs to (null: the wild). */
        public String refuge() {
            return refuge;
        }

        /** Ticks in a row with no pursuer within 48 blocks. */
        public int clearTicks() {
            return clear;
        }

        /** Ticks in a row with no way away. */
        public int noWayTicks() {
            return noWay;
        }
    }

    /**
     * Where a hero running into the wild heads: {@code distance} blocks from the fight it left ({@code fromX, fromZ}),
     * straight away from it through where the hero stands; from the fight's very spot, along {@code yawDegrees}
     * (Minecraft's: 0 faces +Z, 90 faces -X). Returns {x, z}.
     */
    public static double[] awayPoint(double heroX, double heroZ, double fromX, double fromZ, double yawDegrees,
                                     double distance) {
        double dx = heroX - fromX, dz = heroZ - fromZ, len = Math.hypot(dx, dz);
        if (len < 0.5) {
            double r = Math.toRadians(yawDegrees);
            dx = -Math.sin(r);
            dz = Math.cos(r);
            len = 1;
        }
        return new double[]{fromX + dx / len * distance, fromZ + dz / len * distance};
    }

    /** Whether an enemy hero chases an escaping hero: not with honour 60 or more (it lets it go). */
    public static boolean heroPursues(int honour) {
        return honour < HONOUR_LETS_GO;
    }

    /** A standing base of the hero's faction it could run to, with its distance from the hero. */
    public record BaseOption(String id, double distance) {
    }

    /**
     * Where an escape runs (MOBILITY_AND_WORK 7.7), never to the base it flees: low health or a lost raid, its home
     * base when that stands and the fight is not there; otherwise (a fight at its home, a lost base, a fallen home)
     * the nearest other standing base of its faction; with none, the wild (null).
     */
    public static String refuge(Trigger t, String homeId, String fleesId, List<BaseOption> standing) {
        boolean homeStands = homeId != null && standing.stream().anyMatch(b -> b.id().equals(homeId));
        if (t != Trigger.LOST_BASE && homeStands && !homeId.equals(fleesId)) {
            return homeId;
        }
        BaseOption best = null;
        for (BaseOption b : standing) {
            if (b.id().equals(fleesId)) {
                continue;
            }
            if (best == null || b.distance() < best.distance()) {
                best = b;
            }
        }
        return best == null ? null : best.id();
    }

    /**
     * One entry point for the game (HEROES_V2 15.6, called after every hit that lands on the hero and once a tick):
     * the trigger's own rule, {@link #lowHealth}, {@link #lostRaid} or {@link #lostBase}.
     */
    public static Outcome decide(EndOfFight e, Trigger t, double healthShare, boolean beatDone,
                                 boolean inOrNearTarget, boolean fighting, Context c) {
        return switch (t) {
            case LOW_HEALTH -> lowHealth(e, healthShare, beatDone, c);
            case LOST_RAID -> lostRaid(e, inOrNearTarget, fighting, c);
            case LOST_BASE -> lostBase(e, fighting, c);
        };
    }

    // ================================================================== escapes and what is not one (MOBILITY_AND_WORK 7.7)

    /**
     * A hero leaving where it is with an escape's movement (any mobility, MOBILITY_AND_WORK 7.7): an escape, or a
     * scout's step aside or retreat, which uses the movement and none of the rest: no boss bar, no stay away, no
     * window spent, no morale, no line, no leaving a raid.
     */
    public enum Departure {
        ESCAPE, SCOUT_STEP_ASIDE, SCOUT_RETREAT;

        /** Any mobility, even where no pursuer can follow (the rules of 5.1 a fight keeps are lifted). */
        public boolean usesEscapeMovement() {
            return true;
        }

        /** The escape's own consequences: the bar's {@code fled} for 3 s, the window, the stay away, morale, the line. */
        public boolean isEscape() {
            return this == ESCAPE;
        }

        /** A step aside goes out of sight and on with its round; a retreat goes home; an escape to its refuge. */
        public boolean goesOnWithItsRound() {
            return this == SCOUT_STEP_ASIDE;
        }
    }

    /** A scout retreats home from monsters when under this share of its health (ScoutJob's {@code BADLY_HURT}). */
    public static final double SCOUT_BADLY_HURT = 0.4;

    /**
     * A scouting hero's way off (ScoutJob's rules, with an escape's movement): enemy soldiers seen, or badly hurt
     * among monsters, send it home (a retreat); a monster about or in the way makes it step aside and go on. Once an
     * enemy has engaged it, it is in a fight like any other (null: the fight's rules decide, {@link #lowHealth}).
     */
    public static Departure scoutDeparture(boolean engaged, boolean enemySoldiersSeen, double healthShare,
                                           boolean monstersNear, boolean monsterInTheWay) {
        if (engaged) {
            return null;
        }
        if (enemySoldiersSeen || monstersNear && healthShare < SCOUT_BADLY_HURT) {
            return Departure.SCOUT_RETREAT;
        }
        return monstersNear || monsterInTheWay ? Departure.SCOUT_STEP_ASIDE : null;
    }

    /**
     * Who chases an escaping hero: the enemies engaged in the fight it fled, of every kind, and anything whose target
     * it is; an enemy hero only with honour under 60 (it lets the escaper go, HEROES_V2 5.4).
     */
    public static boolean pursues(boolean engagedInTheFightItFled, boolean targetsIt, boolean enemyHero,
                                  int enemyHeroHonour) {
        if (!engagedInTheFightItFled && !targetsIt) {
            return false;
        }
        return !enemyHero || heroPursues(enemyHeroHonour);
    }

    /**
     * The wild (no base to run to): during its stay-away time it keeps at least 64 blocks from the fight it left;
     * true when it must move farther off.
     */
    public static boolean keepFarther(double distanceFromTheFight, long now, long escapedAt, int awaySeconds) {
        return now - escapedAt < awaySeconds(awaySeconds) * 20L && distanceFromTheFight < WILD_KEEP_AWAY;
    }

    /** How a hero left its last fight: it died there (or on the way), or it escaped. */
    public enum End { DIED, ESCAPED }

    /**
     * An escape is neither a death nor a defeat: nothing drops, nobody gets XP orbs, a crest or a token fragment, and it
     * keeps its gear and what it carries (raid loot included). Killed during the escape, it has died: the usual rewards
     * and drops, to whoever killed it ({@code HeroRules.rewardXp}, {@code HeroRules.winningSide}).
     */
    public static boolean rewards(End end) {
        return end == End.DIED;
    }

    /** Morale (milestone 2): an escape -4 at its home base, a leader's -6 at every base of its faction. */
    public static final int MORALE_ESCAPE = -4, MORALE_LEADER_ESCAPE = -6;
    /** Against a hero killed (-8 at its home base) and a leader killed (-20 at every base of its faction). */
    public static final int MORALE_KILLED = -8, MORALE_LEADER_KILLED = -20;

    /**
     * The morale change at one base: {@code ofItsFaction} the base belongs to the hero's faction,
     * {@code itsHome} it is the hero's home base. Nothing for the side it fled (or that killed it).
     */
    public static int moraleDelta(End end, boolean leader, boolean ofItsFaction, boolean itsHome) {
        if (!ofItsFaction) {
            return 0;
        }
        if (leader) {
            return end == End.ESCAPED ? MORALE_LEADER_ESCAPE : MORALE_LEADER_KILLED;
        }
        if (!itsHome) {
            return 0;
        }
        return end == End.ESCAPED ? MORALE_ESCAPE : MORALE_KILLED;
    }

    /**
     * The war council while an escaped hero is away: its war power is in no raid party and in no base's defence but
     * its refuge's (at half until it is above half health: {@code HeroRules.defencePower}).
     */
    public static boolean countsInDefence(String baseId, String refugeId) {
        return baseId != null && baseId.equals(refugeId);
    }

    /** An escape leaves the raid: the hero is out of the party ({@code f.raiders}), now and for the stay away. */
    public static boolean inRaidParty(boolean escapingOrAway) {
        return !escapingOrAway;
    }

    /** The sheet's line: "fights to the death", "escapes under 15%" or "escapes under 8%, never from its home". */
    public static String sheetLine(EndOfFight e) {
        if (e.kind() == WhenLosing.LAST_STAND) {
            return "fights to the death";
        }
        String s = "escapes under " + e.fleeAt() + "%";
        boolean home = e.standsFor().contains(StandsFor.HOME), leader = e.standsFor().contains(StandsFor.LEADER);
        if (home && leader) {
            return s + ", never from its home or from beside its leader";
        }
        if (home) {
            return s + ", never from its home";
        }
        return leader ? s + ", never from beside its leader" : s;
    }
}
