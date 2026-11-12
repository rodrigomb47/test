package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoodRulesTest {
    @Test
    void thePriorityStack() {
        int smug = HeroActions.moodCode("smug");
        assertEquals(smug, MoodRules.pick(smug, true, true, true, true, 0.1), "an operator's mood wins");
        assertEquals(MoodRules.HURT, MoodRules.pick(-1, true, true, true, true, 0.1), "the hurt wince");
        assertEquals(MoodRules.ASLEEP, MoodRules.pick(-1, false, true, true, true, 0.1));
        assertEquals(MoodRules.FOCUSED, MoodRules.pick(-1, false, false, true, true, 0.1), "a wind-up focuses");
        assertEquals(MoodRules.DETERMINED, MoodRules.pick(-1, false, false, false, true, 0.1), "fighting");
        assertEquals(MoodRules.TIRED, MoodRules.pick(-1, false, false, false, false, 0.2), "badly hurt, out of the fight");
        assertEquals(MoodRules.CALM, MoodRules.pick(-1, false, false, false, false, 1.0));
        assertEquals(MoodRules.CALM, MoodRules.pick(12, false, false, false, false, 1.0), "a stray code is no forced mood");
        assertEquals(0, MoodRules.CALM);
    }
}
