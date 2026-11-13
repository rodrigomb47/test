package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MovePlan.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MovePlan} (HEROES_V2 6.3-6.4, v1 10.3; task A3): the wind-up ends on the first hit, hits come every
 * {@code hit_interval}, a combo's strikes and a chain's jumps are spaced, and the recovery follows the active part.
 */
class MovePlanTest {
    static Move move(String id, String form, String extra) {
        return MoveSelectorTest.kit(null, MoveSelectorTest.move(id, form, extra)).move(id);
    }

    @Test
    void aSingleHitLandsAtTheEndOfTheWindup() {
        Move m = move("slash", "melee", "\"class\": \"heavy\", \"frames\": {\"windup\": 0.8, \"active\": 0.2,"
                + " \"recovery\": 1.0}");
        Plan p = MovePlan.of(m);
        assertEquals(16, p.windup());
        assertArrayEquals(new int[] {16}, p.hits());
        assertEquals(20, p.activeEnd());
        assertEquals(40, p.recoveryEnd());
        assertEquals(MoveStage.WINDUP, p.stageAt(15));
        assertEquals(MoveStage.ACTIVE, p.stageAt(16));
        assertEquals(MoveStage.RECOVERY, p.stageAt(20));
        assertEquals(MoveStage.DONE, p.stageAt(40));
        assertEquals(0, p.hitAt(16));
        assertEquals(-1, p.hitAt(17));
    }

    @Test
    void hitsComeEveryHitInterval() {
        Move m = move("flurry", "nova", "\"radius\": 4, \"hits\": 3, \"hit_interval\": 0.3, \"frames\": {\"windup\": 1.0,"
                + " \"active\": 0.1, \"recovery\": 0.8}");
        Plan p = MovePlan.of(m);
        assertArrayEquals(new int[] {20, 26, 32}, p.hits());
        assertTrue(p.activeEnd() > 32, "the active part covers every hit");
        assertEquals(p.activeEnd() + 16, p.recoveryEnd());
    }

    @Test
    void aComboSpreadsItsStrikesOverItsActivePart() {
        Move m = move("claws", "combo", "\"class\": \"light\", \"frames\": {\"windup\": 0.45, \"active\": 0.5,"
                + " \"recovery\": 0.35}, \"strikes\": [{\"effects\": [{\"type\": \"damage\", \"amount\": 2}]},"
                + " {\"effects\": [{\"type\": \"damage\", \"amount\": 2}]}, {\"effects\": [{\"type\": \"damage\","
                + " \"amount\": 3}]}]");
        Plan p = MovePlan.of(m);
        assertArrayEquals(new int[] {9, 14, 19}, p.hits(), "0.45 s, then the 0.5 s split in two");
        assertEquals(20, p.activeEnd());
        assertEquals(27, p.recoveryEnd());
    }

    @Test
    void aStrikeWithItsOwnWindupWaitsForIt() {
        Move m = move("two", "combo", "\"class\": \"light\", \"frames\": {\"windup\": 0.5, \"active\": 0.4,"
                + " \"recovery\": 0.4}, \"strikes\": [{\"active\": 0.2, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]},"
                + " {\"windup\": 0.3, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]");
        Plan p = MovePlan.of(m);
        assertArrayEquals(new int[] {10, 20}, p.hits(), "the first strike's 0.2 s and the second's 0.3 s wind-up");
        assertEquals(21, p.activeEnd(), "past the last strike");
    }

    @Test
    void aChainJumpsEveryThreeTicks() {
        Move m = move("arc", "chain", "\"range\": 10, \"radius\": 4, \"count\": 4");
        Plan p = MovePlan.of(m);
        int w = p.windup();
        assertArrayEquals(new int[] {w, w + 3, w + 6, w + 9}, p.hits());
    }

    @Test
    void releaseFormsHaveOneHit() {
        for (String f : new String[] {"shot", "volley", "zone", "leap", "dash", "self", "ally", "allies"}) {
            Move m = move("m_" + f, f, f.equals("ally") || f.equals("allies") || f.equals("self")
                    ? "\"effects\": [{\"type\": \"heal\", \"amount\": 2}]" : "");
            Plan p = MovePlan.of(m);
            assertEquals(1, p.hitCount(), f);
            assertEquals(p.windup(), p.hit(0), f);
        }
    }

    @Test
    void otherFramesReplaceTheAuthoredOnes() {
        Move m = move("slash", "melee", "\"class\": \"heavy\", \"frames\": {\"windup\": 0.8, \"active\": 0.2,"
                + " \"recovery\": 1.0}");
        Plan p = MovePlan.of(m, new Frames(1.0, 0.25, 0.8));
        assertEquals(20, p.windup());
        assertEquals(25, p.activeEnd());
        assertEquals(41, p.recoveryEnd());
    }

    @Test
    void aPlanNeverRunsPastThirtySeconds() {
        Move m = move("slash", "melee", "");
        Plan p = MovePlan.of(m, new Frames(100, 100, 100));
        assertTrue(p.recoveryEnd() <= MovePlan.MAX_TICKS);
        assertTrue(p.windup() <= MovePlan.MAX_TICKS);
    }
}
