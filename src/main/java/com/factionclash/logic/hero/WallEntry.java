package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.DuelStyle;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.MobilityRules.Activity;
import com.factionclash.logic.hero.MobilityRules.Feature;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Situation;
import com.factionclash.logic.hero.MobilityRules.Stat;

import java.util.List;

/**
 * A raiding hero and the walls of the base it raids (MOBILITY_AND_WORK 3.7 and 5.2; the owner's answer of
 * 2026-11-12: heroes cross hostile walls, soldiers still need the gate or a breach). Chosen after the muster, when the
 * party is 24 blocks from the walls: the <b>way in</b> (over the wall ring by flight, a glide, a climb or a jump onto
 * the top, else the gate or the breach with the soldiers), taken only when a <b>way out</b> is open from inside (the
 * gate, or the hero's own mobility), so a hero never ends up shut inside; and the one <b>goal inside</b> (open the way
 * for the party, hunt the home hero, the storehouse, the heart, the archers), each of which ends: on its own
 * condition, when the raid ends, or after 60 s without progress, and then the hero joins the party at the heart.
 *
 * <p>The way in reads the hero's whole mobility at its level (its {@link Profile}: a climber secondary climbs a wall
 * as a climber primary does), so the 5-high wall of today's bases is a climb for a ceiling-II climber from level 1
 * and a ceiling-I climber from level 13, and never a jump-up link (those reach 3 blocks at most). Nothing here reads
 * who defends the base; the blocks decide. Ordinary NPCs ({@link MobilityRules#NONE}) always take the gate. Pure,
 * unit-tested ({@code WallEntryTest}); A12's {@code HeroRaid} reads it.
 */
public final class WallEntry {
    private WallEntry() {
    }

    private static final double EPS = 1e-9;

    /** The way in is chosen after the muster, when the party is this close to the walls (PROPOSED). */
    public static final double CHOOSE_AT = 24;
    /** Every goal ends after this long without progress (PROPOSED: 60 s). */
    public static final int NO_PROGRESS_TICKS = 60 * BossRules.TPS;
    /** A hunted hero not seen inside within this long ends the hunt. */
    public static final int HUNT_UNSEEN_TICKS = 60 * BossRules.TPS;
    /** A greedy hero (greed at least 65) goes for the storehouse first. */
    public static final int GREEDY = 65;
    /** A flyer's way out: its stamina regains its take-off threshold within this long on the ground. */
    public static final double FLYER_REST_SECONDS = 20;
    /** A glider launching from its own jump apex beside the wall: the far face of a 1-thick wall is this far. */
    public static final double APEX_LAUNCH_DISTANCE = 1.5;
    /** The campaign check: a hero still inside a hostile base this long after its raid ended (must be 0). */
    public static final int STILL_INSIDE_TICKS = 60 * BossRules.TPS;

    // ================================================================== the wall and its gate

    /**
     * How the gate opens from inside (the blocks decide, not the layout): wooden doors or fence gates any NPC opens; an
     * iron door with a button or lever beside it inside; a gateway blocked only by blocks the hero may break under
     * {@code Reach}'s rule (at player speed); or shut for good from inside (nothing to press, nothing it may break).
     */
    public enum Gate {
        WOODEN, BUTTON_OR_LEVER, BREAKABLE, SHUT;

        /** A way out from inside. */
        public boolean opensFromInside() {
            return this != SHUT;
        }

        /** Soldiers open it from outside without help: only wooden doors and fence gates. */
        public boolean soldiersOpen() {
            return this == WOODEN;
        }
    }

    /**
     * One stretch of a base's wall ring as the raid meets it. {@code outside} and {@code inside}: the wall top's height
     * above the ground on each side (5 and 5 for today's walls); {@code launchHeight}: the feet height above the outside
     * ground of the best launch point outside (a ridge, a tree, a tower; 0 when there is none), {@code launchDistance}
     * its horizontal distance to the wall's far face; {@code ledgeHeight}: higher ground outside (a ledge, a roof) from
     * which a gap link reaches the top, its height above the outside ground (0: none), and {@code ledgeGap} the gap
     * between it and the wall top; {@code gate}: how the nearest gate opens from inside.
     */
    public record Wall(int outside, int inside, Gate gate, double launchHeight, double launchDistance,
                       double ledgeHeight, int ledgeGap) {
        /** Today's base wall: 5 high on both sides, wooden double doors, nothing to launch from. */
        public static Wall basic(Gate gate) {
            return new Wall(5, 5, gate, 0, 0, 0, 0);
        }
    }

    /** What a flyer brings to the wall: its stamina share, the flight's length over the wall, and a loaded pack. */
    public record Approach(double staminaShare, double flightBlocks, boolean loaded, boolean takeOffAllowed) {
        /** Full stamina, a 24-block flight, an empty pack, nothing against a take-off. */
        public static final Approach FRESH = new Approach(1, CHOOSE_AT, false, true);
    }

    // ================================================================== the way in

    /** How the hero gets in. The last two are the soldiers' ways: the gate, or the breach at their head. */
    public enum Way { FLY_OVER, CLIMB_OVER, LEAP_ON, GLIDE_OVER, GATE, LEAD_THE_BREACH }

    /** How the hero gets out from inside, in the order it tries them. */
    public enum Exit { FLIGHT, CLIMB, JUMP_UP, GATE, NONE }

    /** A chosen way in with the way out that allowed it. */
    public record Entry(Way way, Exit exit) {
        public boolean overTheWall() {
            return way != Way.GATE && way != Way.LEAD_THE_BREACH;
        }
    }

    /** Whether the hero's mobility is a charger's (it leads the breach at the head of the soldiers). */
    static boolean charger(Profile p) {
        return !Double.isNaN(p.raw(Stat.KNOCKBACK));
    }

    /**
     * Whether a flight over the wall fits: a take-off is allowed (headroom, its threshold, no storm: the caller asks
     * {@link MobilityRules#takeOffRefusal}), the flight's seconds fit the raid's air budget of 10 s per take-off, and its
     * stamina covers them at the powered drain (x1.5 loaded).
     */
    static boolean flightFits(Profile p, Approach a) {
        if (!p.flies() || !a.takeOffAllowed()) {
            return false;
        }
        double seconds = flightSeconds(p, a.flightBlocks());
        double drain = MobilityRules.drain(Activity.POWERED, a.loaded(), Situation.RAID);
        return seconds <= MobilityRules.RAID_AIR_SECONDS + EPS
                && a.staminaShare() * p.raw(Stat.STAMINA) + EPS >= seconds * drain;
    }

    /** A flight's seconds: the take-off, the blocks at cruise speed, the landing. */
    public static double flightSeconds(Profile p, double blocks) {
        return MobilityRules.TAKE_OFF_SECONDS + blocks / p.raw(Stat.CRUISE_SPEED) + MobilityRules.LANDING_SECONDS;
    }

    /** Down the inside face: a climb down, a drop within its safe fall (a worker's 3 included), or a glide. */
    static boolean downInside(Profile p, Wall w) {
        return p.climbHeight() >= w.inside() || w.inside() <= p.safeFall() + EPS || p.glides();
    }

    /** Up the outside face: within its current climb height. */
    static boolean climbsOver(Profile p, Wall w) {
        return p.climbHeight() >= w.outside() && downInside(p, w);
    }

    /**
     * Onto the top by a jump-up link (at most 3 blocks: not today's 5-high walls) or by a gap link from higher ground
     * outside (a landing at most 1 block higher or lower than the ledge, across at most its gap width), then down.
     */
    static boolean leapsOn(Profile p, Wall w) {
        boolean jumpUp = p.jumpUp() > 0 && w.outside() <= p.jumpUp() + EPS;
        boolean fromLedge = w.ledgeHeight() > 0 && p.gap() >= HeroLinks.GAP_MIN && w.ledgeGap() <= p.gap()
                && Math.abs(w.ledgeHeight() - w.outside()) <= HeroLinks.GAP_LANDING_RISE + EPS;
        return (jumpUp || fromLedge) && downInside(p, w);
    }

    /**
     * Over the wall on a glide: from the launch point outside, or (ceiling II-III) from its own jump apex beside the
     * wall, when the descent line still clears the wall top at its far face.
     */
    static boolean glidesOver(Profile p, Wall w) {
        if (!p.glides() || p.glideRatio() <= 0) {
            return false;
        }
        boolean fromLaunch = w.launchHeight() > 0
                && w.launchHeight() - w.launchDistance() / p.glideRatio() >= w.outside() - EPS;
        boolean fromApex = p.has(Feature.GLIDE_FROM_APEX)
                && p.jumpHeight() - APEX_LAUNCH_DISTANCE / p.glideRatio() >= w.outside() - EPS;
        return fromLaunch || fromApex;
    }

    /**
     * The way out from inside (5.2), the first that is open: flight (a flyer whose stamina, after the way in, regains
     * its take-off threshold within 20 s on the ground), a climb up the inside face, a jump-up link from inside, the
     * gate; {@link Exit#NONE} when none is. A glider has only the gate (a glide only goes down).
     */
    public static Exit wayOut(Profile p, Wall w, Approach a, boolean flewIn) {
        if (p.flies() && a.takeOffAllowed() && regainsAfter(p, a, flewIn)) {
            return Exit.FLIGHT;
        }
        if (p.climbHeight() >= w.inside()) {
            return Exit.CLIMB;
        }
        if (p.jumpUp() > 0 && w.inside() <= p.jumpUp() + EPS) {
            return Exit.JUMP_UP;
        }
        return w.gate().opensFromInside() ? Exit.GATE : Exit.NONE;
    }

    /** A flyer's stamina after the way in (when it flew in) and 20 s on the ground: at its take-off threshold. */
    static boolean regainsAfter(Profile p, Approach a, boolean flewIn) {
        MobilityRules.Stamina s = new MobilityRules.Stamina(p);
        double max = s.max();
        if (max <= 0) {
            return false;
        }
        s.air(max * (1 - a.staminaShare()), 1);
        if (flewIn) {
            s.air(flightSeconds(p, a.flightBlocks()), MobilityRules.drain(Activity.POWERED, a.loaded(),
                    Situation.RAID));
        }
        s.ground(FLYER_REST_SECONDS);
        return !s.exhausted() && s.share() + EPS >= p.takeOffShare();
    }

    /**
     * The way in (5.2), chosen after the muster: over the wall when the hero's mobility carries it up, over and down
     * inside, and a way out is open from inside; in the order flight, climb, a jump onto the top, a glide. Otherwise
     * the soldiers' way: at the head of the breach for a charger when the gate does not open for the soldiers, else
     * the gate (or the breach) with them. A worker, a soldier and a hero with {@code mobility.enabled} off have
     * {@link MobilityRules#NONE} and always take the gate.
     */
    public static Entry choose(Profile p, Wall w, Approach a) {
        Way over = null;
        if (flightFits(p, a)) {
            over = Way.FLY_OVER;
        } else if (climbsOver(p, w)) {
            over = Way.CLIMB_OVER;
        } else if (leapsOn(p, w)) {
            over = Way.LEAP_ON;
        } else if (glidesOver(p, w)) {
            over = Way.GLIDE_OVER;
        }
        if (over != null) {
            Exit out = wayOut(p, w, a, over == Way.FLY_OVER);
            if (out != Exit.NONE) {
                return new Entry(over, out);
            }
        }
        Way ground = charger(p) && !w.gate().soldiersOpen() ? Way.LEAD_THE_BREACH : Way.GATE;
        return new Entry(ground, w.gate().opensFromInside() ? Exit.GATE : Exit.NONE);
    }

    /** The way in is chosen once the party has mustered and is within 24 blocks of the walls. */
    public static boolean chooseNow(boolean mustered, double partyDistanceToWalls) {
        return mustered && partyDistanceToWalls <= CHOOSE_AT + EPS;
    }

    /**
     * If the way out closes while it is inside (a player bricks up the gate, a downing grounds a flyer): its links,
     * the gate, breaking at player speed what {@code Reach} allows, then {@code NpcNavigator}'s stall watchdog and
     * stuck recovery, as any NPC. The order is fixed.
     */
    public static final List<String> SHUT_IN_STEPS = List.of("links", "gate", "break at player speed",
            "stall watchdog and stuck recovery");

    /** The campaign check's counter (must stay 0): still inside a hostile base's walls 60 s after its raid ended. */
    public static boolean stillInsideTooLong(boolean insideHostileWalls, int ticksSinceRaidEnded) {
        return insideHostileWalls && ticksSinceRaidEnded >= STILL_INSIDE_TICKS;
    }

    // ================================================================== the goal inside

    /** One goal per raid (PROPOSED), by the party's state, then the hero's personality and role. */
    public enum Goal { OPEN_THE_WAY, HUNT_THE_HERO, CLEAR_THE_ARCHERS, STOREHOUSE, HEART }

    /**
     * What the goal is chosen from. {@code partyHeldOutside}: soldiers of its party are held outside (a door they
     * cannot open, an iron door worked from inside, a blocked gateway, defenders holding the doorway; an unchanged
     * gate of wooden doors holds nobody); {@code homeHeroKnown}: a defending hero is known to be home (a scout saw it,
     * or the hero sees it inside); {@code homeHeroSeenInside}: one is seen inside now; {@code archersDefending}: the
     * base has archers or crossbowmen at their posts; {@code storehouseStanding}: its storehouse stands.
     */
    public record GoalInput(boolean partyHeldOutside, Focus focus, Role role, DuelStyle duelStyle, int greed,
                            boolean homeHeroKnown, boolean homeHeroSeenInside, boolean archersDefending,
                            boolean storehouseStanding) {
    }

    /**
     * The goal inside (5.2): open the way while its party is held outside; hunt the home hero when its focus is
     * {@code heroes_first} and one is known to be home, or when one is seen inside; clear the archers for a marksman
     * or a zoner when there are archers; the storehouse for a greedy hero (greed 65 or more) while it stands; else
     * the raid's goal, the heart.
     */
    public static Goal goal(GoalInput in) {
        if (in.partyHeldOutside()) {
            return Goal.OPEN_THE_WAY;
        }
        if (in.homeHeroSeenInside() || in.focus() == Focus.HEROES_FIRST && in.homeHeroKnown()) {
            return Goal.HUNT_THE_HERO;
        }
        if ((in.role() == Role.MARKSMAN || in.duelStyle() == DuelStyle.ZONER) && in.archersDefending()) {
            return Goal.CLEAR_THE_ARCHERS;
        }
        if (in.greed() >= GREEDY && in.storehouseStanding()) {
            return Goal.STOREHOUSE;
        }
        return Goal.HEART;
    }

    /**
     * Where a goal stands now. {@code everyRaiderInside}: every living raider of its party is inside the walls box;
     * {@code huntedGone}: the hunted hero is dead or has escaped; {@code ticksHuntedUnseen}: since it was last seen
     * inside (or since the hunt began); {@code archersInRange}: defending archers within its range;
     * {@code backpackOfLoot}: it has taken one backpack of loot; {@code storehouseStanding}; {@code heartDestroyed}.
     */
    public record GoalState(boolean raidOver, int ticksWithoutProgress, boolean everyRaiderInside, boolean huntedGone,
                            int ticksHuntedUnseen, boolean archersInRange, boolean backpackOfLoot,
                            boolean storehouseStanding, boolean heartDestroyed) {
    }

    /** How a goal ended, or {@link #ONGOING}. */
    public enum GoalEnd { ONGOING, DONE, RAID_OVER, NO_PROGRESS }

    /**
     * Every goal ends, so a hero never holds up its raid (5.2): its own condition (every raider inside; the hunted hero
     * dead, escaped or unseen for 60 s; no archer in range; a backpack of loot, or the storehouse gone; the heart
     * destroyed), the raid's end, or 60 s without progress.
     */
    public static GoalEnd end(Goal g, GoalState s) {
        if (s.raidOver()) {
            return GoalEnd.RAID_OVER;
        }
        boolean done = switch (g) {
            case OPEN_THE_WAY -> s.everyRaiderInside();
            case HUNT_THE_HERO -> s.huntedGone() || s.ticksHuntedUnseen() >= HUNT_UNSEEN_TICKS;
            case CLEAR_THE_ARCHERS -> !s.archersInRange();
            case STOREHOUSE -> s.backpackOfLoot() || !s.storehouseStanding();
            case HEART -> s.heartDestroyed();
        };
        if (done) {
            return GoalEnd.DONE;
        }
        return s.ticksWithoutProgress() >= NO_PROGRESS_TICKS ? GoalEnd.NO_PROGRESS : GoalEnd.ONGOING;
    }

    /** What the hero does once its goal ended. */
    public enum After { JOIN_AT_THE_HEART, CARRY_LOOT_HOME, RAID_ENDS }

    /**
     * After its goal: the raid's end takes it home as the raid's end says (an escape, or home with the party); a
     * storehouse goal that took its backpack of loot carries it home (counted in the party until it unloads, as
     * looting raiders are today); every other end joins the party at the heart, which it may strike like any raider.
     */
    public static After after(Goal g, GoalEnd e, boolean backpackOfLoot) {
        if (e == GoalEnd.RAID_OVER) {
            return After.RAID_ENDS;
        }
        if (g == Goal.STOREHOUSE && backpackOfLoot) {
            return After.CARRY_LOOT_HOME;
        }
        return After.JOIN_AT_THE_HEART;
    }
}
