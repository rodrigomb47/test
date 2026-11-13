package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.EconomyFocus;
import com.factionclash.logic.hero.HeroDef.RecruitCondition;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * HEROES_V2 Appendix A: the 30 temperaments with their default traits (kept once, in {@link HeroCatalogue}), their
 * special rule, default economy focus, default recruit condition and the temperaments they admire and clash with.
 * The special rules a game system of milestone 1 can act on carry a {@link Flag}; the others (pacts, morale, duels:
 * milestones 2 and 3) are text only until their systems exist. Pure, unit-tested against the appendix's table
 * ({@code TemperamentsTest}).
 */
public final class Temperaments {
    private Temperaments() {
    }

    /** The special rules milestone 1 acts on (HEROES_V2 5.5, 6.13, Appendix A). */
    public enum Flag {
        /** hot_blooded: a vengeance raid after its leader dies even when its aggression is under 70 (5.6). */
        VENGEANCE_ANY_AGGRESSION,
        /** genki: builds ahead: +1 to the free-beds threshold for a new house. */
        BUILDS_AHEAD,
        /** lone_wolf, delinquent: territorial: distance counts 5x in the raid target cost (5.5). */
        TERRITORIAL,
        /** chuunibyou: opens boss fights with its ultimate when it is ready (the move selector's opener). */
        OPENS_WITH_ULTIMATE,
        /** airhead: doubled noise on every decision. */
        DOUBLED_NOISE,
        /** trickster: the ambush bonus doubled. */
        AMBUSH_DOUBLED,
        /** sleepy: bold under half health: once that awake it sees the fight through (6.13: always a last stand). */
        BOLD_UNDER_HALF,
        /** battle_maniac: targets the strongest rival it can beat. */
        STRONGEST_BEATABLE,
        /** gentle_giant: always spares beaten factions (the mercy rule whatever its honour). */
        ALWAYS_SPARES,
        /** ancient_sage: no raids before day 3. */
        NO_RAIDS_BEFORE_DAY_3
    }

    /** One row of Appendix A (the traits come from {@link HeroCatalogue#TEMPERAMENT_TRAITS}). */
    public record Row(Temperament id, String rule, EconomyFocus focus, RecruitCondition recruit, Temperament admires,
                      Temperament clashes, Set<Flag> flags) {
        public Row {
            flags = flags.isEmpty() ? Set.of() : Set.copyOf(flags);
        }

        public Map<Trait, Integer> traits() {
            return HeroCatalogue.defaultTraits(id);
        }

        public boolean has(Flag f) {
            return flags.contains(f);
        }
    }

    private static final Map<Temperament, Row> ROWS = new EnumMap<>(Temperament.class);

    private static void row(Temperament t, String rule, EconomyFocus f, RecruitCondition r, Temperament admires,
                            Temperament clashes, Flag... flags) {
        Set<Flag> s = EnumSet.noneOf(Flag.class);
        s.addAll(java.util.List.of(flags));
        ROWS.put(t, new Row(t, rule, f, r, admires, clashes, s));
    }

    static {
        row(Temperament.HOT_BLOODED, "always accepts duels; vengeance raids even when a < 70", EconomyFocus.MILITARY,
                RecruitCondition.DUEL, Temperament.STOIC, Temperament.SCHEMER, Flag.VENGEANCE_ANY_AGGRESSION);
        row(Temperament.STOIC, "never surprise-breaks (as h >= 75)", EconomyFocus.MINING, RecruitCondition.REPUTATION,
                Temperament.GRUFF_VETERAN, Temperament.AIRHEAD);
        row(Temperament.COOL_HEADED, "opinion weighs half in pact maths", EconomyFocus.CRAFT,
                RecruitCondition.REPUTATION, Temperament.ANCIENT_SAGE, Temperament.GENKI);
        row(Temperament.TSUNDERE, "refuses the first pact offer from each faction", EconomyFocus.MILITARY,
                RecruitCondition.DUEL, Temperament.HOT_BLOODED, Temperament.CHARMER);
        row(Temperament.SHY, "seeks a pact with the strongest known neighbour", EconomyFocus.FARMING,
                RecruitCondition.VISITS, Temperament.BIG_SIBLING, Temperament.DELINQUENT);
        row(Temperament.SWEET_NATURED, "offers peace to every faction at first contact", EconomyFocus.FOOD,
                RecruitCondition.GIFT, Temperament.GENTLE_GIANT, Temperament.TYRANT);
        row(Temperament.YANDERE, "hard enrage when its favoured is hurt; never forgives its top grudge",
                EconomyFocus.MILITARY, RecruitCondition.CAUSE, Temperament.BIG_SIBLING, Temperament.CHARMER);
        row(Temperament.GENKI, "builds ahead (+1 bed threshold); impulsive raids", EconomyFocus.GROWTH,
                RecruitCondition.GIFT, Temperament.CHARMER, Temperament.SLEEPY, Flag.BUILDS_AHEAD);
        row(Temperament.LONE_WOLF, "never proposes pacts; accepts only when T >= 2", EconomyFocus.SCOUTING,
                RecruitCondition.DUEL, Temperament.STOIC, Temperament.SWEET_NATURED, Flag.TERRITORIAL);
        row(Temperament.PROUD_NOBLE, "opinion losses x1.5; builds monuments", EconomyFocus.TRADE,
                RecruitCondition.REPUTATION, Temperament.RIGHTEOUS, Temperament.DELINQUENT);
        row(Temperament.DELINQUENT, "territorial; respects whoever beats its leader in a duel", EconomyFocus.MILITARY,
                RecruitCondition.DUEL, Temperament.HOT_BLOODED, Temperament.PROUD_NOBLE, Flag.TERRITORIAL);
        row(Temperament.CHUUNIBYOU, "opens boss fights with its ultimate when ready; grand raid names",
                EconomyFocus.MILITARY, RecruitCondition.DUEL, Temperament.ECCENTRIC_GENIUS, Temperament.COOL_HEADED,
                Flag.OPENS_WITH_ULTIMATE);
        row(Temperament.AIRHEAD, "doubled noise; the luck whim", EconomyFocus.FOOD, RecruitCondition.GIFT,
                Temperament.SWEET_NATURED, Temperament.PERFECTIONIST, Flag.DOUBLED_NOISE);
        row(Temperament.BIG_SIBLING, "protective pacts with weaker factions; drill-like XP at home",
                EconomyFocus.PEOPLE, RecruitCondition.REPUTATION, Temperament.SHY, Temperament.TYRANT);
        row(Temperament.TRICKSTER, "ambush bonus doubled; surprise breaks", EconomyFocus.TRADE,
                RecruitCondition.PAYMENT, Temperament.CHARMER, Temperament.RIGHTEOUS, Flag.AMBUSH_DOUBLED);
        row(Temperament.SLEEPY, "few raids; +10% work at home; bold under half health", EconomyFocus.CRAFT,
                RecruitCondition.VISITS, Temperament.ANCIENT_SAGE, Temperament.GENKI, Flag.BOLD_UNDER_HALF);
        row(Temperament.BATTLE_MANIAC, "targets the strongest beatable rival; accepts every duel",
                EconomyFocus.MILITARY, RecruitCondition.DUEL, Temperament.HOT_BLOODED, Temperament.COWARD_WHO_RISES,
                Flag.STRONGEST_BEATABLE);
        row(Temperament.GENTLE_GIANT, "always spares beaten factions; helps allies defend", EconomyFocus.BUILDING,
                RecruitCondition.REPUTATION, Temperament.SWEET_NATURED, Temperament.TYRANT, Flag.ALWAYS_SPARES);
        row(Temperament.SCHEMER, "pacts with weaker neighbours, broken when strong", EconomyFocus.TRADE,
                RecruitCondition.PAYMENT, Temperament.TRICKSTER, Temperament.RIGHTEOUS);
        row(Temperament.RIGHTEOUS, "grudge against any conqueror; always joins coalitions", EconomyFocus.DEFENCE,
                RecruitCondition.REPUTATION, Temperament.GENTLE_GIANT, Temperament.SCHEMER);
        row(Temperament.MELANCHOLIC, "its top grudge never drifts", EconomyFocus.CRAFT, RecruitCondition.CAUSE,
                Temperament.STOIC, Temperament.GENKI);
        row(Temperament.GLUTTON, "food focus; raids for food while rationing", EconomyFocus.FOOD,
                RecruitCondition.PAYMENT, Temperament.SWEET_NATURED, Temperament.PERFECTIONIST);
        row(Temperament.PERFECTIONIST, "morale baseline -10; Diligence +1 at home", EconomyFocus.CRAFT,
                RecruitCondition.REPUTATION, Temperament.COOL_HEADED, Temperament.AIRHEAD);
        row(Temperament.CHARMER, "acceptance of its pact offers +0.15", EconomyFocus.TRADE, RecruitCondition.GIFT,
                Temperament.GENKI, Temperament.TSUNDERE);
        row(Temperament.ANCIENT_SAGE, "no raids before day 3; coalition builder", EconomyFocus.LORE,
                RecruitCondition.REPUTATION, Temperament.COOL_HEADED, Temperament.CHUUNIBYOU,
                Flag.NO_RAIDS_BEFORE_DAY_3);
        row(Temperament.COWARD_WHO_RISES, "avoids war; bold and +15 morale when its base is attacked",
                EconomyFocus.FARMING, RecruitCondition.VISITS, Temperament.BIG_SIBLING, Temperament.BATTLE_MANIAC);
        row(Temperament.GRUFF_VETERAN, "defence and drills", EconomyFocus.DEFENCE, RecruitCondition.DUEL,
                Temperament.STOIC, Temperament.CHUUNIBYOU);
        row(Temperament.ECCENTRIC_GENIUS, "crafting and lore projects first", EconomyFocus.LORE, RecruitCondition.GIFT,
                Temperament.CHUUNIBYOU, Temperament.GRUFF_VETERAN);
        row(Temperament.MERCHANT, "peace for tribute; sells hard", EconomyFocus.TRADE, RecruitCondition.PAYMENT,
                Temperament.GLUTTON, Temperament.RIGHTEOUS);
        row(Temperament.TYRANT, "tribute demands; expands first", EconomyFocus.GROWTH, RecruitCondition.PAYMENT,
                Temperament.BATTLE_MANIAC, Temperament.GENTLE_GIANT);
    }

    public static Row of(Temperament t) {
        return ROWS.get(t);
    }

    /** Whether a temperament carries a flag (false with no temperament). */
    public static boolean has(Temperament t, Flag f) {
        return t != null && ROWS.get(t).has(f);
    }

    /** Appendix A's opinion and compatibility affinity. */
    public static final double AFFINITY = 0.2;

    /**
     * The affinity between two temperaments (opinion and compatibility, 8): +0.2 when either admires the other, -0.2
     * when either clashes with the other, 0 when both or neither (and with no temperament).
     */
    public static double affinity(Temperament a, Temperament b) {
        if (a == null || b == null) {
            return 0;
        }
        Row x = ROWS.get(a), y = ROWS.get(b);
        boolean admire = x.admires() == b || y.admires() == a;
        boolean clash = x.clashes() == b || y.clashes() == a;
        return admire == clash ? 0 : admire ? AFFINITY : -AFFINITY;
    }

    /** The temperament's default economy focus (the leader block's {@code economy_focus} when it gives none). */
    public static EconomyFocus focus(Temperament t) {
        return t == null ? null : ROWS.get(t).focus();
    }
}
