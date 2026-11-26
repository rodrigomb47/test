package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Estimate;
import com.factionclash.logic.campaign.Intel.Recon;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The war council's arithmetic (review of the knowledge review, 2026-11-19, kept by the focused reconnaissance of
 * {@code docs/design/recon.md}, task R5): two thirds in whole soldiers, the counter-attack's 3 to 2, the party. The
 * estimate itself is a focused recon's count ({@link Intel#estimate}; {@code ReconTest}); the plan beyond it is
 * {@code Reserve}'s ({@code ReserveTest}). The prior, the looks' drift, the losses seen at our walls, the heroes' fading,
 * the better of two looks and the lair's prior went with task R5.
 */
class IntelTest {
    private static final double EPS = 1e-9;

    static Recon counted(int soldiers, double power, long at) {
        return new Recon(soldiers, power, Map.of(), Intel.coverage(12), true, 1200, at, "pillar 12", "Edrin Cole", Set.of());
    }

    /**
     * Review finding 5, under the reserve (recon.md 5.5, 10.2 item 13): a council's party (every soldier beyond a third)
     * goes against a recon counting as many soldiers as it sends, in the same gear, and waits against one more, whatever
     * the gear and whether its power is a float or not. Two thirds of a plan of half again is the count head for head.
     */
    @Test
    void aPartyGoesAgainstAsManyCountedAsItSendsWhateverItsGear() {
        double[] gear = {1.0, 1.15, 1.35, 1.6, 1.85, 2.25, 2.8, 3.0};
        for (double p : gear) {
            double pf = (float) p; // FactionState.soldierPower is a float
            for (int s = 6; s <= 12; s++) {
                int party = Intel.partySize(s, s, 3, 3);
                assertTrue(party >= 3, "a party from " + s);
                Reserve.Plan as = Reserve.plan(counted(party, pf, 1000), 1000, Reserve.SHARE, java.util.List.of(), 12000);
                Reserve.Plan more = Reserve.plan(counted(party + 1, pf, 1000), 1000, Reserve.SHARE, java.util.List.of(), 12000);
                assertTrue(Intel.twoThirds(party * p, as.power(), as.unit()), s + " soldiers in gear " + p + ": party " + party
                        + " against as many counted");
                assertFalse(Intel.twoThirds(party * p, more.power(), more.unit()), s + " soldiers in gear " + p + ": party " + party
                        + " against one more counted");
            }
        }
    }

    /** Two thirds in whole soldiers, and the old rule where the defence is a whole number of thirds. */
    @Test
    void twoThirdsCountsWholeSoldiers() {
        assertTrue(Intel.twoThirds(4, 6, 1), "four against six");
        assertFalse(Intel.twoThirds(3.9, 6, 1), "a tenth of a soldier short");
        assertTrue(Intel.twoThirds(6, 10, 1), "six against ten: two thirds is six whole soldiers");
        assertFalse(Intel.twoThirds(5, 10, 1));
        assertTrue(Intel.twoThirds(3, 4.5, 1), "three against four and a half (an estimate)");
        assertTrue(Intel.twoThirds(0, 0, 1), "an empty yard");
        assertTrue(Intel.twoThirds(1, 1.4, 1), "less than one and a half soldiers: any party");
        assertTrue(Intel.twoThirds(8 * 2.0, 12 * 2.0, 2.0), "eight iron soldiers against twelve, in power units");
        assertFalse(Intel.twoThirds(7.9 * 2.0, 12 * 2.0, 2.0));
        assertTrue(Intel.twoThirds(6 * 1.35, 9 * (double) (float) 1.35, (float) 1.35), "a float rounding never decides it");
    }

    /** A counter-attack outnumbers the defence 3 to 2, its half again counted to the nearest whole soldier. */
    @Test
    void aCounterAttackOutnumbersTheDefenceThreeToTwo() {
        Estimate two = Intel.estimate(counted(2, 1.0, 100), 101);
        assertEquals(2, two.soldiers(), EPS);
        assertTrue(Intel.threeToTwo(3, two.soldierPower(), two.power()), "three against two counted");
        assertFalse(Intel.threeToTwo(2.9, two.soldierPower(), two.power()));
        assertFalse(Intel.threeToTwo(3, 2.4, 1), "three against two and a half (four would be needed)");
        assertTrue(Intel.threeToTwo(4, 2.4, 1));
        assertTrue(Intel.threeToTwo(3 * 2.0, 2 * 2.0, 2.0), "in power units");
        assertFalse(Intel.threeToTwo(2.9 * 2.0, 2 * 2.0, 2.0));
        assertTrue(Intel.threeToTwo(0, 0, 1));
    }

    /** The council's party: every soldier beyond a third, at least three, three always at home, never a hero. */
    @Test
    void thePartyIsEverySoldierBeyondAThird() {
        assertEquals(0, Intel.partySize(5, 5, 3, 3), "five: three must stay, two cannot go");
        assertEquals(3, Intel.partySize(6, 6, 3, 3));
        assertEquals(4, Intel.partySize(7, 7, 3, 3));
        assertEquals(6, Intel.partySize(9, 9, 3, 3));
        assertEquals(6, Intel.partySize(10, 10, 3, 3));
        assertEquals(8, Intel.partySize(12, 12, 3, 3), "the config's example: twelve raid with eight");
        assertEquals(5, Intel.partySize(10, 5, 3, 3), "a hero (or five) at home never goes");
        assertEquals(0, Intel.partySize(10, 2, 3, 3), "too few to draft");
    }
}
