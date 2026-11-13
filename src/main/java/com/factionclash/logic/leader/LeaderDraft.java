package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.Gender;
import com.factionclash.logic.hero.HeroDef.Rarity;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroRules;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The leader draft (HEROES_V2 5.2): a seeded greedy draw of one leader per faction slot from every eligible hero,
 * weighted by rarity (4, 4, 2, 1), with up to 200 tries per round and the rules relaxed from the last to the first
 * when they cannot all hold:
 * <ol>
 * <li>no two leaders share an archetype;</li>
 * <li>primary colours differ by at least 25 (CIE76 delta-E) and no two share a {@code dye_primary}; only the
 * delta-E part is ever relaxed (wool, carpets and beds show only the 16 dyes);</li>
 * <li>no two share a temperament;</li>
 * <li>with two or more slots both genders are present;</li>
 * <li>with three or more, not every leader has aggression 70 or more, and at least one has 55 or more (war
 * balance).</li>
 * </ol>
 * Fixed leaders ({@code leaders.fixed} or {@code /fc start ... <hero ids>}) fill slots first and are kept whatever the
 * rules say. The seed is {@code leaders.seed} when set, else the world seed XOR the campaign's start time. Pure,
 * unit-tested ({@code LeaderDraftTest}).
 */
public final class LeaderDraft {
    private LeaderDraft() {
    }

    public static final int TRIES = 200;
    public static final double MIN_DELTA_E = 25;
    public static final int AGGRESSIVE = 70, BOLD_ENOUGH = 55;

    /** What the draft knows of a hero. {@code aggression} is its raw trait (0-100). */
    public record Candidate(String id, Rarity rarity, String archetype, Gender gender, Temperament temperament,
                            int aggression, int primary, Dye dyePrimary, boolean eligible) {
    }

    /**
     * The cast: one hero id per slot (null where nobody could be drawn under the dye rule), how many rules had to be
     * relaxed (0: all held; 1: rule 5 dropped; 2: rules 4-5; 3: rules 3-5; 4: rule 2 down to "different dyes" too;
     * 5: rule 1 as well) and the seed, printed at the campaign's start.
     */
    public record Cast(List<String> leaders, int relaxed, long seed) {
        public Cast {
            leaders = java.util.Collections.unmodifiableList(new ArrayList<>(leaders));
        }
    }

    /** {@code leaders.seed} when set (non-zero), else the world seed XOR the campaign's start game time. */
    public static long seed(long configSeed, long worldSeed, long startTime) {
        return configSeed != 0 ? configSeed : worldSeed ^ startTime;
    }

    /** Draws {@code slots} leaders; {@code fixed} ids fill the first slots. */
    public static Cast draw(List<Candidate> pool, int slots, List<String> fixed, long seed) {
        List<Candidate> fixedC = new ArrayList<>();
        for (String id : fixed) {
            for (Candidate c : pool) {
                if (c.id().equals(id) && fixedC.size() < slots && !fixedC.contains(c)) {
                    fixedC.add(c);
                }
            }
        }
        List<Candidate> eligible = pool.stream().filter(Candidate::eligible).filter(c -> !fixedC.contains(c)).toList();
        for (int relax = 0; relax <= 5; relax++) {
            for (int attempt = 0; attempt < TRIES; attempt++) {
                SplittableRandom r = new SplittableRandom(seed * 31 + relax * 1_000_003L + attempt);
                List<Candidate> cast = new ArrayList<>(fixedC);
                boolean ok = true;
                while (cast.size() < slots) {
                    List<Candidate> fits = new ArrayList<>();
                    for (Candidate c : eligible) {
                        if (!cast.contains(c) && fits(c, cast, relax)) {
                            fits.add(c);
                        }
                    }
                    if (fits.isEmpty()) {
                        ok = false;
                        break;
                    }
                    int i = HeroRules.drawIndex(fits.stream().map(Candidate::rarity).toList(), HeroRules.LEADER_WEIGHTS,
                            r.nextDouble());
                    if (i < 0) {
                        ok = false;
                        break;
                    }
                    cast.add(fits.get(i));
                }
                if (ok && wholeCastHolds(cast, slots, relax)) {
                    return new Cast(cast.stream().map(Candidate::id).toList(), relax, seed);
                }
            }
        }
        // the dye rule is never relaxed: fill what it allows, leave the rest to the legacy identity
        List<String> out = new ArrayList<>();
        List<Candidate> cast = new ArrayList<>(fixedC);
        for (Candidate c : eligible) {
            if (cast.size() < slots && fits(c, cast, 5)) {
                cast.add(c);
            }
        }
        for (Candidate c : cast) {
            out.add(c.id());
        }
        while (out.size() < slots) {
            out.add(null);
        }
        return new Cast(out, 6, seed);
    }

    /** The pairwise rules 1-3 against those already drawn, with {@code relax} rules dropped from the end. */
    static boolean fits(Candidate c, List<Candidate> cast, int relax) {
        for (Candidate o : cast) {
            if (c.dyePrimary() == o.dyePrimary()) {
                return false; // never relaxed
            }
            if (relax < 5 && c.archetype() != null && c.archetype().equals(o.archetype())) {
                return false;
            }
            if (relax < 4 && Palette.deltaE(c.primary(), o.primary()) < MIN_DELTA_E) {
                return false;
            }
            if (relax < 3 && c.temperament() != null && c.temperament() == o.temperament()) {
                return false;
            }
        }
        return true;
    }

    /** Rules 4 and 5, which only a whole cast can meet. */
    static boolean wholeCastHolds(List<Candidate> cast, int slots, int relax) {
        if (relax < 2 && slots >= 2) {
            boolean f = cast.stream().anyMatch(c -> c.gender() == Gender.FEMALE);
            boolean m = cast.stream().anyMatch(c -> c.gender() == Gender.MALE);
            if (!f || !m) {
                return false;
            }
        }
        if (relax < 1 && slots >= 3) {
            boolean allHot = cast.stream().allMatch(c -> c.aggression() >= AGGRESSIVE);
            boolean oneBold = cast.stream().anyMatch(c -> c.aggression() >= BOLD_ENOUGH);
            return !allHot && oneBold;
        }
        return true;
    }
}
