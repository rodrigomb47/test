package com.factionclash.logic.hero;

/**
 * The mood the server shows on a hero's face (HEROES_V2 4.6 "Moods"), from the first part of the design's priority
 * stack that exists before the kit engine: an operator's forced mood (screenshots), the 0.5 s hurt wince, asleep while
 * sleeping, the wind-up's focus, determined while fighting, tired when badly hurt, otherwise calm. Phase and enrage
 * moods (A6), personality reactions and situations (L3/L4) and the temperament default slot in later between these.
 * Pure, unit-tested ({@code MoodRulesTest}).
 */
public final class MoodRules {
    public static final int CALM = HeroActions.moodCode("calm"), HURT = HeroActions.moodCode("hurt"),
            ASLEEP = HeroActions.moodCode("asleep"), FOCUSED = HeroActions.moodCode("focused"),
            DETERMINED = HeroActions.moodCode("determined"), TIRED = HeroActions.moodCode("tired");
    /** Below this share of its health a hero not fighting looks tired. */
    public static final double TIRED_HEALTH = 0.3;

    private MoodRules() {
    }

    /**
     * @param forced  an operator's mood code, or -1
     * @param hurt    hurt in the last 0.5 s
     * @param windup  in a wind-up or a channel ({@code DATA_STATE} bit 6)
     * @param health  health share, 0-1
     */
    public static int pick(int forced, boolean hurt, boolean asleep, boolean windup, boolean fighting, double health) {
        if (forced >= 0 && forced < HeroActions.MOODS.size()) {
            return forced;
        }
        if (hurt) {
            return HURT;
        }
        if (asleep) {
            return ASLEEP;
        }
        if (windup) {
            return FOCUSED;
        }
        if (fighting) {
            return DETERMINED;
        }
        if (health < TIRED_HEALTH) {
            return TIRED;
        }
        return CALM;
    }
}
