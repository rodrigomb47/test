package com.factionclash.logic.hero;

import com.factionclash.logic.hero.EdgePlay.Ground;
import com.factionclash.logic.hero.EdgePlay.PushKind;
import com.factionclash.logic.hero.EdgePlay.Receiver;
import com.factionclash.logic.hero.EdgePlay.Ring;
import com.factionclash.logic.hero.EdgePlay.Step;
import com.factionclash.logic.hero.EdgePlay.Walk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EdgePlay} (HEROES_V2 6.17, 14.3; task T1): edges read with the receiver's immunities, the push distances of
 * 6.19 (knockback 1 about 2 blocks; 3 about 6, 4 with a vanguard's 30 resistance), the pick's factor by form, the
 * grab's throw toward the nearest lethal edge (the order of tries, edges first), the walk round's spot and its 3 s clock
 * with the 10 s give-up. Nothing here knows whether a receiver is a player.
 */
class EdgePlayTest {
    private static final double EPS = 1e-9;
    /** East (+x) is direction 0, south (+z) 4, west 8, north 12. */
    private static final int EAST = 0, SOUTH = 4, WEST = 8, NORTH = 12;

    private static Move3 kit(String... moves) {
        return new Move3(MoveSelectorTest.kit(null, moves));
    }

    private record Move3(HeroDef.V2 kit) {
        HeroDef.Move m(String id) {
            return kit.move(id);
        }
    }

    // ------------------------------------------------------------------ edges and immunities

    @Test
    void edgesAreReadWithTheReceiversImmunities() {
        Receiver plain = Receiver.plain(20);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.LAVA, 0, plain), EPS, "lava is lethal");
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.FIRE, 0, plain), EPS, "fire, magma, a campfire: harmful");
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.THORNS, 0, plain), EPS);
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.POWDER_SNOW, 0, plain), EPS);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.VOID, 0, plain), EPS);
        assertEquals(0, EdgePlay.value(Ground.WATER, 3, plain), EPS, "water takes a fall");
        assertEquals(0, EdgePlay.value(Ground.FLOOR, 0, plain), EPS);
        assertEquals(0, EdgePlay.value(Ground.FLOOR, 2, plain), EPS, "a step down within the safe fall");
        // a drop: harmful past the safe fall, lethal when it takes half the health left (a push lifts about a block)
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.FLOOR, 4, plain), EPS);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.FLOOR, 12, plain), EPS);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.FLOOR, 4, Receiver.plain(4)), EPS,
                "the same drop is lethal to one with little health left");
        assertEquals(10, EdgePlay.fallDamage(12, plain), EPS, "vanilla: ceil(fall - 3), the lift counted");
        // fire-proof: lava and fire are no edge (an ember_stride hero, Fire Resistance, a fire-immune mob)
        Receiver fireproof = new Receiver(20, 3, 1, true, false, true, false, 0);
        assertEquals(0, EdgePlay.value(Ground.LAVA, 0, fireproof), EPS);
        assertEquals(0, EdgePlay.value(Ground.FIRE, 0, fireproof), EPS);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.FLOOR, 20, fireproof), EPS, "but a cliff still is");
        // no fall damage, a soft landing (Slow Falling, a glider): drops are nothing, the void still harmful
        Receiver noFall = new Receiver(20, 3, 0, false, false, true, false, 0);
        Receiver glider = new Receiver(20, 3, 1, false, true, true, false, 0);
        assertEquals(0, EdgePlay.value(Ground.FLOOR, 30, noFall), EPS);
        assertEquals(0, EdgePlay.value(Ground.FLOOR, 30, glider), EPS);
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.VOID, 0, noFall), EPS);
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.VOID, 0, glider), EPS);
        assertEquals(EdgePlay.LETHAL, EdgePlay.value(Ground.LAVA, 5, glider), EPS, "a glider still burns");
        // a long safe fall (a hero's): a 6-block drop is nothing to one with a safe fall of 8
        assertEquals(0, EdgePlay.value(Ground.FLOOR, 6, new Receiver(200, 8, 1, false, false, true, false, 0)), EPS);
        // water: harmful only to one with a water weak point; powder snow nothing to one that never freezes
        assertEquals(EdgePlay.HARMFUL, EdgePlay.value(Ground.WATER, 0, new Receiver(20, 3, 1, false, false, true, true, 0)), EPS);
        assertEquals(0, EdgePlay.value(Ground.POWDER_SNOW, 0, new Receiver(20, 3, 1, false, false, false, false, 0)), EPS);
    }

    @Test
    void pushDistancesAreTheCriticsNumbers() {
        // 6.19: knockback 1 is vanilla's 0.4, about 2 blocks on flat ground; 3 about 6; 4 with 30 resistance
        double one = FormGeometry.knockbackDistance(1, 0);
        assertTrue(one > 1.8 && one < 2.2, "knockback 1: " + one);
        // vanilla's flight and skid (knockbackFullStrengthIntoLava measures a knockback 2 in game: 4.13 blocks)
        assertEquals(4.13, FormGeometry.knockbackDistance(2, 0), 0.05, "knockback 2 as measured in game");
        double three = FormGeometry.knockbackDistance(3, 0);
        assertEquals(3 * one, three, 1e-6, "linear in strength");
        assertTrue(three > 5.5 && three < 6.5, "knockback 3 (Shoulder Charge, Ram Strike): " + three);
        double vanguard = FormGeometry.knockbackDistance(3, 0.3);
        assertTrue(vanguard > 3.9 && vanguard < 4.5, "knockback 3 against a vanguard's 30: " + vanguard);
        assertEquals(0, FormGeometry.knockbackDistance(3, 1), EPS, "full resistance: no push");
    }

    @Test
    void sectorsAndDirections() {
        assertEquals(EAST, EdgePlay.sector(1, 0));
        assertEquals(SOUTH, EdgePlay.sector(0, 1));
        assertEquals(WEST, EdgePlay.sector(-1, 0));
        assertEquals(NORTH, EdgePlay.sector(0, -1));
        assertEquals(2, EdgePlay.sector(1, 1), "south-east");
        assertEquals(SOUTH, EdgePlay.turnedSector(1, 0, 90), "east turned 90 degrees as FormGeometry.turn turns it");
        double[] t = FormGeometry.turn(1, 0, 90);
        assertEquals(EdgePlay.sector(t[0], t[1]), EdgePlay.turnedSector(1, 0, 90));
        assertEquals(1, EdgePlay.dirX(EAST), EPS);
        assertEquals(1, EdgePlay.dirZ(SOUTH), EPS);
    }

    @Test
    void theRingKeepsTheNearestEdgeOfEachKind() {
        Ring r = new Ring();
        assertFalse(r.any());
        r.edge(EAST, 5, EdgePlay.HARMFUL).edge(EAST, 7, EdgePlay.LETHAL).edge(EAST, 6, EdgePlay.LETHAL);
        assertEquals(6, r.lethal(EAST), EPS);
        assertEquals(5, r.harmful(EAST), EPS);
        assertEquals(EdgePlay.HARMFUL, r.valueWithin(EAST, 5), EPS, "a push of 5 reaches the harmful edge at 5");
        assertEquals(EdgePlay.LETHAL, r.valueWithin(EAST, 5.5), EPS, "lethal first when both are in reach");
        assertEquals(0, r.valueWithin(EAST, 4), EPS);
        assertEquals(0, r.valueWithin(WEST, 8), EPS);
        // a pool from 2 to 3 blocks: a push landing in it counts, one that flies over it does not (a drop always does)
        r.edge(SOUTH, 2, 3, EdgePlay.LETHAL);
        assertEquals(EdgePlay.LETHAL, r.valueWithin(SOUTH, 2.5), EPS);
        assertEquals(EdgePlay.LETHAL, r.valueWithin(SOUTH, 3.4), EPS);
        assertEquals(0, r.valueWithin(SOUTH, 4.5), EPS, "flown over");
        assertEquals(2, r.landedEdge(SOUTH, 2.5), EPS);
        assertEquals(EdgePlay.LETHAL, r.valueWithin(EAST, 20), EPS, "past a drop is down");
        assertTrue(r.any());
        r.clear();
        assertFalse(r.any());
        // stale: another receiver, 2 s old, or moved away from its centre
        r.receiverId = 7;
        r.sampledAt = 100;
        r.centreX = 0;
        r.centreZ = 0;
        assertFalse(r.stale(120, 7, 0.5, 0.5));
        assertTrue(r.stale(140, 7, 0, 0));
        assertTrue(r.stale(120, 8, 0, 0));
        assertTrue(r.stale(120, 7, 2, 0));
    }

    // ------------------------------------------------------------------ the pick

    @Test
    void movesThatPushAndTheirKinds() {
        Move3 k = kit(MoveSelectorTest.move("bash", "melee", "\"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                        + " {\"type\": \"knockback\", \"strength\": 2}]"),
                MoveSelectorTest.move("seize", "grab", "\"class\": \"heavy\", \"throw\": 3, \"up\": 1"),
                MoveSelectorTest.move("reel", "bolt", "\"range\": 10, \"effects\": [{\"type\": \"pull\", \"strength\": 3}]"),
                MoveSelectorTest.move("ram", "charge", "\"class\": \"heavy\", \"range\": 10, \"effects\": [{\"type\": \"knockback\", \"strength\": 3}]"),
                MoveSelectorTest.move("cut", "melee", ""));
        assertEquals(PushKind.AWAY, EdgePlay.push(k.m("bash")).kind());
        assertEquals(2, EdgePlay.push(k.m("bash")).strength(), EPS);
        assertEquals(PushKind.ANY, EdgePlay.push(k.m("seize")).kind());
        assertEquals(3, EdgePlay.push(k.m("seize")).strength(), EPS);
        assertEquals(PushKind.TOWARD, EdgePlay.push(k.m("reel")).kind());
        assertEquals(PushKind.AWAY, EdgePlay.push(k.m("ram")).kind(), "a charge with a knockback pushes away");
        assertEquals(PushKind.NONE, EdgePlay.push(k.m("cut")).kind());
    }

    @Test
    void thePickDoublesAPushTowardALethalEdge() {
        EdgePlay.Push kb2 = new EdgePlay.Push(PushKind.AWAY, 2, 0, 3.5);
        Ring r = new Ring().edge(EAST, 3, EdgePlay.LETHAL).edge(SOUTH, 3, EdgePlay.HARMFUL);
        // the hero west of its target (its line points east): the push carries it 4 blocks east, into the lava at 3
        assertEquals(2, EdgePlay.pickFactor(kb2, r, 1, 0, 2, 0), EPS);
        assertEquals(1.5, EdgePlay.pickFactor(kb2, r, 0, 1, 2, 0), EPS, "a harmful edge: x1.5");
        assertEquals(1, EdgePlay.pickFactor(kb2, r, -1, 0, 2, 0), EPS, "pushed the other way: as usual");
        assertEquals(1, EdgePlay.pickFactor(kb2, null, 1, 0, 2, 0), EPS, "no ring (edgePlay off)");
        assertEquals(1, EdgePlay.pickFactor(EdgePlay.Push.NONE, r, 1, 0, 2, 0), EPS);
        // knockback 1 (2 blocks) does not reach an edge 3 away; knockback resistance shortens the push
        EdgePlay.Push kb1 = new EdgePlay.Push(PushKind.AWAY, 1, 0, 3.5);
        assertEquals(1, EdgePlay.pickFactor(kb1, r, 1, 0, 2, 0), EPS);
        assertEquals(1, EdgePlay.pickFactor(kb2, r, 1, 0, 2, 0.5), EPS, "half the push falls short");
        // a grab throws any way: an edge in any of its tries counts
        EdgePlay.Push grab = new EdgePlay.Push(PushKind.ANY, 3, 1, 2.5);
        assertEquals(2, EdgePlay.pickFactor(grab, new Ring().edge(NORTH, 4, EdgePlay.LETHAL), 1, 0, 2, 0), EPS);
        // a pull: the edge lies between the target and the hero (west of the target here), within the pull
        EdgePlay.Push pull = new EdgePlay.Push(PushKind.TOWARD, 3, 0, 10);
        assertEquals(2, EdgePlay.pickFactor(pull, new Ring().edge(WEST, 3, EdgePlay.LETHAL), 1, 0, 8, 0), EPS);
        assertEquals(1, EdgePlay.pickFactor(pull, new Ring().edge(EAST, 3, EdgePlay.LETHAL), 1, 0, 8, 0), EPS,
                "behind the target, away from the hero: a pull draws it away from that edge");
    }

    @Test
    void theThrowGoesToTheNearestLethalEdgeEdgesFirst() {
        // facing east; lava north at 4 and south at 3: the nearer, south (+90 degrees, the fourth try)
        Ring r = new Ring().edge(NORTH, 4, EdgePlay.LETHAL).edge(SOUTH, 3, EdgePlay.LETHAL);
        assertEquals(90, FormGeometry.THROW_TRIES[EdgePlay.throwTry(r, 1, 0, 5)], EPS);
        // a harmful edge straight ahead loses to a lethal one to the side
        r = new Ring().edge(EAST, 2, EdgePlay.HARMFUL).edge(NORTH, 4, EdgePlay.LETHAL);
        assertEquals(-90, FormGeometry.THROW_TRIES[EdgePlay.throwTry(r, 1, 0, 5)], EPS);
        // equal distance: the earlier try (45 before -45)
        r = new Ring().edge(2, 3, EdgePlay.LETHAL).edge(14, 3, EdgePlay.LETHAL);
        assertEquals(45, FormGeometry.THROW_TRIES[EdgePlay.throwTry(r, 1, 0, 5)], EPS);
        // only a harmful edge: thrown at it
        r = new Ring().edge(WEST, 2, EdgePlay.HARMFUL);
        assertEquals(180, FormGeometry.THROW_TRIES[EdgePlay.throwTry(r, 1, 0, 5)], EPS);
        // nothing in the throw's reach: straight ahead, at full strength
        assertEquals(0, EdgePlay.throwTry(new Ring().edge(NORTH, 7, EdgePlay.LETHAL), 1, 0, 5));
        assertEquals(0, EdgePlay.throwTry(null, 1, 0, 5));
    }

    // ------------------------------------------------------------------ the approach

    @Test
    void theWalkRoundStandsOnTheFarSide() {
        Ring r = new Ring().edge(EAST, 3, EdgePlay.LETHAL);
        // the hero is north of its target; to push it east into the lava it stands west of it, at the move's reach
        EdgePlay.Spot s = EdgePlay.approach(r, 4, 3.5, 0, -3, null);
        assertNotNull(s);
        assertEquals(EAST, s.dir());
        assertEquals(-3, s.dx(), 1e-9);
        assertEquals(0, s.dz(), 1e-9);
        assertFalse(EdgePlay.aligned(0, 1, EAST), "from the north its line points south: not yet");
        assertTrue(EdgePlay.aligned(1, 0, EAST), "from the west it points east: aimed");
        assertTrue(EdgePlay.aligned(Math.cos(Math.toRadians(10)), Math.sin(Math.toRadians(10)), EAST),
                "within the edge's sector: the pick reads the same direction");
        assertFalse(EdgePlay.aligned(Math.cos(Math.toRadians(15)), Math.sin(Math.toRadians(15)), EAST));
        // lethal before harmful, then the shortest walk; an edge out of the push's reach is none
        r = new Ring().edge(EAST, 3, EdgePlay.HARMFUL).edge(WEST, 3, EdgePlay.LETHAL);
        assertEquals(WEST, EdgePlay.approach(r, 4, 3.5, 3, 0, null).dir(), "lethal, though the walk is longer");
        r = new Ring().edge(EAST, 3, EdgePlay.LETHAL).edge(WEST, 3, EdgePlay.LETHAL);
        assertEquals(WEST, EdgePlay.approach(r, 4, 3.5, 3, 0.5, null).dir(), "the hero east of it: push it west");
        assertNull(EdgePlay.approach(new Ring().edge(EAST, 6, EdgePlay.LETHAL), 4, 3.5, 0, -3, null));
        assertNull(EdgePlay.approach(r, 4, 3.5, 3, 0, d -> true), "every direction skipped (no spot to stand on)");
        assertEquals(2, EdgePlay.standOff(2.5), EPS);
        assertEquals(4, EdgePlay.standOff(14), EPS, "a charge starts from 4 blocks, its line runs through");
    }

    @Test
    void theWalkRoundGivesUpAfterThreeSecondsAndLeavesThatTargetAloneForTen() {
        Walk w = new Walk();
        EdgePlay.Spot s = new EdgePlay.Spot(-3, 0, EAST, EdgePlay.LETHAL);
        assertEquals(Step.WALK, w.step(0, 1, s, false));
        assertTrue(w.walking());
        assertEquals(Step.WALK, w.step(55, 1, s, false));
        assertEquals(Step.GAVE_UP, w.step(60, 1, s, false), "3 s and still not round");
        assertEquals(Step.NONE, w.step(65, 1, s, false), "that target is left alone");
        assertTrue(w.blocked(259, 1));
        assertEquals(Step.WALK, w.step(70, 2, s, false), "another target is not");
        w.reset();
        assertEquals(Step.WALK, w.step(260, 1, s, false), "10 s later it may walk round again");
        assertEquals(Step.ALIGNED, w.step(270, 1, s, true), "round: the pick takes over");
        assertFalse(w.walking());
        assertEquals(Step.NONE, w.step(275, 1, null, false), "no edge in reach");
    }

    @Test
    void theSelectorScoresAPushAtAnEdgeAndOffersItForTheWalkRound() {
        HeroDef.V2 k = MoveSelectorTest.kit(null,
                MoveSelectorTest.move("bash", "melee", "\"weight\": 5, \"cooldown\": 8, \"effects\": [{\"type\": \"damage\","
                        + " \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 2}]"),
                MoveSelectorTest.move("cut", "melee", "\"weight\": 5"));
        MoveSelector sel = new MoveSelector(k, 50, 50, false, 1);
        Sense s = MoveSelectorTest.at(2);
        double plain = sel.score(k.move("bash"), s);
        s.edges = new Ring().edge(EAST, 3, EdgePlay.LETHAL);
        s.toTargetX = 1;
        s.toTargetZ = 0;
        assertEquals(2 * plain, sel.score(k.move("bash"), s), 1e-9, "x2 toward lava");
        assertEquals(sel.score(k.move("cut"), MoveSelectorTest.at(2)), sel.score(k.move("cut"), s), 1e-9,
                "a move that does not push: as usual");
        s.toTargetX = -1;
        assertEquals(plain, sel.score(k.move("bash"), s), 1e-9, "aimed away from the edge: as usual");
        // the push offered for the walk round: ready now, or within 2 s; not one 3 s off
        EdgePlay.Push p = sel.readyPush(0, s, EdgePlay.READY_TICKS);
        assertNotNull(p);
        assertEquals(2, p.strength(), EPS);
        sel.fightStarted(0);
        Sense near = MoveSelectorTest.at(2);
        long t = 0;
        MoveSelector.Pick pick = null;
        while (pick == null || !pick.move().id().equals("bash")) {
            pick = sel.check(t, near);
            t += 5;
            if (pick != null && !pick.move().id().equals("bash")) {
                sel.moveEnded(t, 1, false);
            }
        }
        long bashAt = t - 5;
        sel.moveEnded(t, 1, false);
        // another move between (the pick never plays a move that is not repeatable twice running: theWalkRoundIsOffered...)
        pick = null;
        while (pick == null || !pick.move().id().equals("cut")) {
            pick = sel.check(t, near);
            t += 5;
        }
        sel.moveEnded(t, 1, false);
        assertTrue(t < bashAt + 8 * 20 - EdgePlay.READY_TICKS, "cut came " + (t - bashAt) + " ticks after bash");
        assertNull(sel.readyPush(t, s, EdgePlay.READY_TICKS), "its 8 s cooldown runs");
        assertNotNull(sel.readyPush(bashAt + 8 * 20 - EdgePlay.READY_TICKS, s, EdgePlay.READY_TICKS), "ready within 2 s");
    }

    /**
     * Review of T1 (2026-11-19): a hero stands right for its walk round's spot when its own push from where it stands
     * already lands in an edge as bad as the spot's, not only inside the spot's sector. The live failure: the spot east-
     * southeast of a cliff (sector 2), the hero stopped a block short in sector 3, from where its knockback 3 (6.2 blocks)
     * still went over the same cliff 6.5 blocks off; it counted as not there, walked nowhere for 3 s and pushed unaimed.
     */
    @Test
    void aHeroStandsRightWhenItsPushAlreadyLandsInTheEdge() {
        Ring ring = new Ring().edge(2, 3.5, EdgePlay.LETHAL).edge(3, 6.5, EdgePlay.LETHAL);
        EdgePlay.Spot sp = new EdgePlay.Spot(-2, -2, 2, EdgePlay.LETHAL);
        double a = Math.toRadians(64.8), ux = Math.cos(a), uz = Math.sin(a);
        assertEquals(3, EdgePlay.sector(ux, uz));
        assertFalse(EdgePlay.aligned(ux, uz, 2), "not in the spot's own sector");
        assertTrue(EdgePlay.standsRight(ring, ux, uz, 6.2, sp), "its push from here goes over the same cliff");
        assertEquals(2, EdgePlay.pickFactor(new EdgePlay.Push(EdgePlay.PushKind.AWAY, 3, 0, 3.5), ring, ux, uz, 3, 0), EPS,
                "and the pick doubles it from here");
        Ring farther = new Ring().edge(2, 3.5, EdgePlay.LETHAL).edge(3, 7.5, EdgePlay.LETHAL);
        assertFalse(EdgePlay.standsRight(farther, ux, uz, 6.2, sp), "a push that falls short of the edge: walk on");
        Ring worse = new Ring().edge(2, 3.5, EdgePlay.LETHAL).edge(3, 6.5, EdgePlay.HARMFUL);
        assertFalse(EdgePlay.standsRight(worse, ux, uz, 6.2, sp), "only a harmful edge from here while the spot's is lethal: walk on");
        assertTrue(EdgePlay.standsRight(ring, Math.cos(Math.toRadians(45)), Math.sin(Math.toRadians(45)), 6.2, sp), "in the sector");
        assertFalse(EdgePlay.standsRight(ring, ux, uz, 6.2, null), "no spot");
    }

    /**
     * Review of T1 (2026-11-19): the walk round holds every move while it walks and then leaves its target alone for 10 s,
     * so it is offered only a push the pick could start next: not one whose {@code use_when} fails, and not the move just
     * played when it is not repeatable (its novelty is 0), even with a second charge ready.
     */
    @Test
    void theWalkRoundIsOfferedOnlyAPushThePickCouldStart() {
        HeroDef.V2 k = MoveSelectorTest.kit(null,
                MoveSelectorTest.move("desperate", "melee", "\"weight\": 5, \"use_when\": \"self_health_below\", \"use_value\": 40,"
                        + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 2}]"));
        MoveSelector sel = new MoveSelector(k, 50, 50, false, 1);
        Sense s = MoveSelectorTest.at(2);
        s.edges = new Ring().edge(EAST, 3, EdgePlay.LETHAL);
        s.toTargetX = 1;
        s.healthShare = 1;
        assertNull(sel.readyPush(0, s, EdgePlay.READY_TICKS), "its use_when fails at full health: no walk round for it");
        s.healthShare = 0.3;
        assertNotNull(sel.readyPush(0, s, EdgePlay.READY_TICKS), "under 40%: it may start, so the hero walks round for it");

        HeroDef.V2 k2 = MoveSelectorTest.kit(null,
                MoveSelectorTest.move("bash", "melee", "\"weight\": 5, \"charges\": 2, \"cooldown\": 8, \"effects\": [{\"type\":"
                        + " \"damage\", \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 2}]"),
                MoveSelectorTest.move("cut", "melee", "\"weight\": 5"));
        MoveSelector sel2 = new MoveSelector(k2, 50, 50, false, 1);
        sel2.fightStarted(0);
        Sense near = MoveSelectorTest.at(2);
        long t = 0;
        MoveSelector.Pick pick = null;
        while (pick == null || !pick.move().id().equals("bash")) {
            pick = sel2.check(t, near);
            t += 5;
            if (pick != null) {
                sel2.moveEnded(t, 1, false);
            }
        }
        assertEquals(1, sel2.charges(k2.move("bash"), t, 0), "its second charge is ready");
        assertEquals(0, sel2.score(k2.move("bash"), s), EPS, "just played and not repeatable: the pick scores it 0");
        assertNull(sel2.readyPush(t, s, EdgePlay.READY_TICKS), "so no walk round for it");
        while (pick == null || !pick.move().id().equals("cut")) {
            pick = sel2.check(t, near);
            t += 5;
            if (pick != null) {
                sel2.moveEnded(t, 1, false);
            }
        }
        assertNotNull(sel2.readyPush(t, s, EdgePlay.READY_TICKS), "once another move played, its second charge is offered");
    }
}
