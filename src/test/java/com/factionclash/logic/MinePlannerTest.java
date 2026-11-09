package com.factionclash.logic;

import com.factionclash.logic.MinePlanner.Band;
import com.factionclash.logic.MinePlanner.Decision;
import com.factionclash.logic.MinePlanner.Kind;
import com.factionclash.logic.MinePlanner.Level;
import com.factionclash.logic.MinePlanner.Stock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MinePlannerTest {
    private static final int SURFACE = 70, MIN_Y = -64;
    /** Nothing is short: only the default order applies. */
    private static final Stock PLENTY = new Stock(100, 100, 100, 100, 100, false, false);

    private static Level level(int step, int progress, int rounds, boolean cut, Band band) {
        return new Level(step, progress, rounds, cut, band);
    }

    private static Level done(int step, int rounds, Band band) {
        return level(step, SpiralMine.planSize(rounds), rounds, false, band);
    }

    private static Decision next(List<Level> levels, int current, int bottom, int shaftLimit, int tier, Stock stock) {
        return MinePlanner.next(SURFACE, MIN_Y, levels, current, bottom, shaftLimit, tier, MinePlanner.wanted(stock), MinePlanner.MAX_REACH);
    }

    @Test
    void stepsFollowOreBands() {
        assertEquals(6, MinePlanner.stepFor(Band.COAL, SURFACE, MIN_Y));
        assertEquals(54, MinePlanner.stepFor(Band.IRON, SURFACE, MIN_Y));
        assertEquals(70, MinePlanner.stepFor(Band.LAPIS, SURFACE, MIN_Y));
        assertEquals(86, MinePlanner.stepFor(Band.GOLD, SURFACE, MIN_Y));
        assertEquals(126, MinePlanner.stepFor(Band.DEEP, SURFACE, MIN_Y));
        // the same heights from other surfaces: y 64/16/0/-16/-56 at surface 70
        assertEquals(50, MinePlanner.stepFor(Band.IRON, 66, MIN_Y));
        assertEquals(66, MinePlanner.stepFor(Band.LAPIS, 66, MIN_Y));
        assertEquals(82, MinePlanner.stepFor(Band.GOLD, 66, MIN_Y));
        assertEquals(122, MinePlanner.stepFor(Band.DEEP, 66, MIN_Y));
        assertEquals(123, MinePlanner.stepFor(Band.DEEP, 68, MIN_Y), "the deep layer falls back to the landing above the floor");
    }

    @Test
    void everyStepFacesTheTunnels() {
        for (int minY : new int[]{-64, 0}) {
            for (int surface = -40; surface <= 320; surface++) {
                for (Band b : Band.values()) {
                    int s = MinePlanner.stepFor(b, surface, minY);
                    if (s == -1) {
                        assertFalse(MinePlanner.deepEnough(surface, minY), "no layer only when too shallow: " + surface);
                        continue;
                    }
                    assertTrue(s >= MinePlanner.MIN_DEPTH && s <= surface - (minY + 8), b + " at surface " + surface + ": " + s);
                    assertTrue(Math.floorMod(s - 1, 4) == 1 || Math.floorMod(s - 1, 4) == 2, b + " step " + s + " does not face the tunnels");
                }
            }
        }
    }

    @Test
    void deepBandFeasibleForEverySurface() {
        for (int minY : new int[]{-64, 0, -128}) {
            for (int surface = minY + 14; surface <= 320; surface++) {
                int s = MinePlanner.stepFor(Band.DEEP, surface, minY);
                int y = surface - s;
                assertTrue(y >= minY + 8 && y <= minY + 10, "deep layer at y=" + y + " for surface " + surface + ", bottom " + minY);
            }
        }
    }

    @Test
    void deepBandFollowsMinBuildHeight() {
        assertEquals(9, 120 - MinePlanner.stepFor(Band.DEEP, 120, 0));
    }

    @Test
    void flatGameTestWorldHasNoLayer() {
        for (Band b : Band.values()) {
            assertEquals(-1, MinePlanner.stepFor(b, -60, -64), b.name());
        }
        assertFalse(MinePlanner.deepEnough(-60, -64));
        for (int tier = 0; tier <= 3; tier++) {
            Decision d = MinePlanner.next(-60, -64, List.of(), -1, 0, 0, tier, MinePlanner.wanted(new Stock(0, 0, 0, 0, 0, true, true)), 4);
            assertEquals(Kind.WORKED_OUT, d.kind(), "a flat world has nothing to dig, not even with a better pickaxe (tier " + tier + ")");
        }
    }

    /** Random level lists in a mine at the usual surface, some of them finished, cut or lengthened. */
    private static List<Level> randomLevels(Random r) {
        List<Level> out = new ArrayList<>();
        int n = r.nextInt(6);
        for (int i = 0; i < n; i++) {
            int step = SpiralMine.landingAtOrBelow(6 + r.nextInt(120));
            int rounds = r.nextInt(5);
            int progress = r.nextBoolean() ? SpiralMine.planSize(rounds) : r.nextInt(SpiralMine.planSize(rounds));
            Band band = MinePlanner.bandAt(SURFACE - step, SURFACE, MIN_Y);
            out.add(level(step, progress, rounds, r.nextInt(8) == 0, band));
        }
        return out;
    }

    private static Stock randomStock(Random r) {
        return new Stock(r.nextInt(40), r.nextInt(12), r.nextInt(20), r.nextInt(40), r.nextInt(40), r.nextBoolean(), r.nextBoolean());
    }

    @Test
    void woodenPickOnlyCoal() {
        Random r = new Random(1);
        for (int i = 0; i < 3000; i++) {
            List<Level> levels = randomLevels(r);
            Decision d = next(levels, levels.isEmpty() ? -1 : r.nextInt(levels.size()), 126, 0, 0, randomStock(r));
            if (d.kind() == Kind.WORK || d.kind() == Kind.LENGTHEN || d.kind() == Kind.NEW_LAYER) {
                assertEquals(Band.COAL, d.band(), "a wooden pickaxe digs only for coal: " + d);
            }
        }
    }

    @Test
    void stonePickNoGoldOrDeep() {
        Random r = new Random(2);
        for (int i = 0; i < 3000; i++) {
            List<Level> levels = randomLevels(r);
            Decision d = next(levels, levels.isEmpty() ? -1 : r.nextInt(levels.size()), 126, 0, 1, randomStock(r));
            if (d.kind() == Kind.WORK || d.kind() == Kind.LENGTHEN || d.kind() == Kind.NEW_LAYER) {
                assertTrue(d.band() != Band.GOLD && d.band() != Band.DEEP, "a stone pickaxe does not dig for gold or diamonds: " + d);
            }
        }
    }

    @Test
    void criticalIronFirst() {
        Decision d = next(List.of(), -1, 0, 0, 2, new Stock(3, 0, 0, 0, 0, true, true));
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.IRON, d.band());
        assertEquals(54, d.step());
    }

    @Test
    void diamondsBeforeIronWhenIronIsNotCritical() {
        Decision d = next(List.of(), -1, 0, 0, 2, new Stock(20, 0, 0, 0, 0, false, false));
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.DEEP, d.band());
        assertEquals(126, d.step());
    }

    @Test
    void lapisOnlyWithTable() {
        Stock noTable = new Stock(100, 100, 100, 0, 100, false, false);
        Stock table = new Stock(100, 100, 100, 0, 100, true, false);
        assertFalse(MinePlanner.wanted(noTable).contains(Band.LAPIS));
        assertEquals(List.of(Band.LAPIS), MinePlanner.wanted(table));
        assertEquals(Band.IRON, next(List.of(), -1, 0, 0, 2, noTable).band(), "with nothing wanted the default order starts with iron");
        Decision d = next(List.of(), -1, 0, 0, 2, table);
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.LAPIS, d.band());
        assertEquals(70, d.step());
    }

    @Test
    void coalWhenFuelOrWoodShort() {
        assertEquals(List.of(Band.COAL), MinePlanner.wanted(new Stock(100, 100, 100, 100, 10, false, false)));
        assertEquals(List.of(Band.COAL), MinePlanner.wanted(new Stock(100, 100, 100, 100, 100, false, true)));
        assertEquals(List.of(), MinePlanner.wanted(PLENTY));
        Decision d = next(List.of(), -1, 0, 0, 2, new Stock(100, 100, 100, 100, 100, false, true));
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.COAL, d.band());
        assertEquals(6, d.step());
    }

    @Test
    void staysOnUnfinishedLevel() {
        List<Level> levels = List.of(level(6, 10, 0, false, Band.COAL));
        Decision d = next(levels, 0, 6, 0, 2, new Stock(0, 0, 0, 0, 100, true, false));
        assertEquals(Kind.WORK, d.kind(), "an unfinished level is worked on even when iron is critical now");
        assertEquals(0, d.level());
    }

    @Test
    void switchesWhenThePickIsTooLow() {
        List<Level> levels = List.of(level(126, 10, 0, false, Band.DEEP));
        Decision d = next(levels, 0, 126, 0, 1, PLENTY);
        assertFalse(d.kind() == Kind.WORK && d.level() == 0, "a stone pickaxe must leave the deep level: " + d);
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.IRON, d.band());
    }

    @Test
    void lengthensFinishedLevelBeforeTheNextBand() {
        List<Level> levels = List.of(done(54, 0, Band.IRON));
        Decision d = MinePlanner.next(SURFACE, MIN_Y, levels, 0, 54, 0, 2, List.of(Band.IRON), 4);
        assertEquals(Kind.LENGTHEN, d.kind());
        assertEquals(0, d.level());
        Decision maxed = MinePlanner.next(SURFACE, MIN_Y, levels, 0, 54, 0, 2, List.of(Band.IRON), 0);
        assertNotEquals(Kind.LENGTHEN, maxed.kind(), "no lengthening beyond the rounds the corridor allows");
    }

    @Test
    void reusesALevelWithinFiveSteps() {
        Band legacy = MinePlanner.bandAt(SURFACE - 52, SURFACE, MIN_Y);
        assertEquals(Band.IRON, legacy, "an old level at y=18 counts as an iron level");
        for (int progress : new int[]{0, 100, SpiralMine.planSize(0)}) {
            List<Level> levels = List.of(level(52, progress, 0, false, legacy));
            Decision d = next(levels, -1, 52, 0, 2, new Stock(3, 100, 100, 100, 100, false, false));
            assertTrue((d.kind() == Kind.WORK || d.kind() == Kind.LENGTHEN) && d.level() == 0, "the level 2 steps off is reused: " + d);
        }
    }

    @Test
    void newLayersKeepSixStepsApart() {
        Random r = new Random(3);
        for (int i = 0; i < 5000; i++) {
            List<Level> levels = randomLevels(r);
            Decision d = next(levels, levels.isEmpty() ? -1 : r.nextInt(levels.size()), r.nextInt(127), 0, r.nextInt(4), randomStock(r));
            if (d.kind() == Kind.NEW_LAYER) {
                for (Level l : levels) {
                    assertTrue(Math.abs(l.step() - d.step()) >= MinePlanner.MIN_SPACING, "new layer " + d.step() + " too close to " + l.step());
                }
            }
        }
    }

    @Test
    void shallowerLayerNeedsNoDescent() {
        List<Level> levels = List.of(done(126, 4, Band.DEEP));
        Decision d = next(levels, 0, 126, 0, 2, new Stock(3, 100, 100, 100, 100, false, false));
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.IRON, d.band());
        assertEquals(54, d.step());
        assertTrue(d.step() <= 126, "the shaft already passes it");
    }

    @Test
    void respectsShaftLimit() {
        Random r = new Random(4);
        for (int i = 0; i < 2000; i++) {
            List<Level> levels = new ArrayList<>(randomLevels(r));
            levels.removeIf(l -> l.step() > 60);
            Decision d = next(levels, levels.isEmpty() ? -1 : r.nextInt(levels.size()), 60, 60, 2, randomStock(r));
            assertNotEquals(Band.DEEP, d.band(), "nothing below the shaft limit: " + d);
            if (d.kind() == Kind.NEW_LAYER) {
                assertTrue(d.step() <= 60);
            }
        }
        Decision d = next(List.of(), -1, 60, 60, 2, new Stock(20, 0, 0, 0, 100, false, false));
        assertEquals(Kind.NEW_LAYER, d.kind());
        assertEquals(Band.IRON, d.band(), "iron at step 54 is still above the limit");
    }

    @Test
    void cutLevelIsNeverWorkedAgain() {
        Random r = new Random(5);
        List<Level> levels = List.of(level(54, 100, 0, true, Band.IRON));
        for (int i = 0; i < 500; i++) {
            Decision d = next(levels, 0, 54, 0, r.nextInt(4), randomStock(r));
            assertNotEquals(0, d.level(), "a cut level is never worked or lengthened again: " + d);
            assertFalse(d.kind() == Kind.NEW_LAYER && Math.abs(d.step() - 54) < MinePlanner.MIN_SPACING, "nor replaced next to it");
        }
    }

    @Test
    void restWhenOnlyABetterPickHelps() {
        List<Level> stone = List.of(done(6, 4, Band.COAL), done(54, 4, Band.IRON), done(70, 4, Band.LAPIS));
        Decision d = next(stone, 2, 70, 0, 1, PLENTY);
        assertEquals(Kind.REST, d.kind());
        assertEquals(2, d.needTier(), "only an iron pickaxe opens the gold and deep layers");
        List<Level> all = new ArrayList<>(stone);
        all.add(done(86, 4, Band.GOLD));
        all.add(done(126, 4, Band.DEEP));
        assertEquals(Kind.WORKED_OUT, next(all, 4, 126, 0, 2, PLENTY).kind());
        assertEquals(Kind.WORKED_OUT, next(all, 4, 126, 0, 1, PLENTY).kind(), "a better pickaxe would not help either");
    }

    @Test
    void shortCorridorDigsMoreLayersOfTheOre() {
        // a site with no room to lengthen: the iron layer at y=16 is done, iron is nearly gone, the pickaxe is stone
        Stock noIron = new Stock(3, 0, 0, 0, 100, false, false);
        List<Level> levels = new ArrayList<>(List.of(done(6, 0, Band.COAL), done(54, 0, Band.IRON)));
        Decision d = MinePlanner.next(SURFACE, MIN_Y, levels, 1, 54, 0, 1, MinePlanner.wanted(noIron), 0);
        assertEquals(Kind.NEW_LAYER, d.kind(), "another iron layer, not lapis, coal or a rest for an iron pickaxe: " + d);
        assertEquals(Band.IRON, d.band());
        assertTrue(Math.abs(d.step() - 54) >= MinePlanner.MIN_SPACING && Math.abs(d.step() - 54) <= MinePlanner.BAND_SPREAD, "step " + d.step());
        assertEquals(Band.IRON, MinePlanner.bandAt(SURFACE - d.step(), SURFACE, MIN_Y), "still nearer iron's height than any other ore's");
        // the iron layers together hold no more tunnel than one layer lengthened four times would
        int layers = 1;
        for (int guard = 0; guard < 20; guard++) {
            d = MinePlanner.next(SURFACE, MIN_Y, levels, -1, 126, 0, 1, MinePlanner.wanted(noIron), 0);
            if (d.kind() != Kind.NEW_LAYER || d.band() != Band.IRON) {
                break;
            }
            for (Level l : levels) {
                assertTrue(Math.abs(l.step() - d.step()) >= MinePlanner.MIN_SPACING, "layer " + d.step() + " too close to " + l.step());
            }
            levels.add(done(d.step(), 0, Band.IRON));
            layers++;
        }
        assertTrue(layers >= 2 && layers <= MinePlanner.MAX_REACH + 1, "iron layers: " + layers);
        assertNotEquals(Band.IRON, d.band(), "no iron layer once the band's share is dug: " + d);
        // a full corridor keeps one layer per ore, lengthened instead
        Decision full = MinePlanner.next(SURFACE, MIN_Y, List.of(done(54, 4, Band.IRON)), 0, 54, 0, 1, MinePlanner.wanted(noIron), 4);
        assertNotEquals(Band.IRON, full.band(), "a fully lengthened iron layer gets no second one: " + full);
        // with one round of room, two layers' worth (each lengthened once)
        Decision one = MinePlanner.next(SURFACE, MIN_Y, List.of(done(54, 1, Band.IRON)), 0, 54, 0, 1, MinePlanner.wanted(noIron), 1);
        assertEquals(Kind.NEW_LAYER, one.kind());
        assertEquals(Band.IRON, one.band());
        Decision two = MinePlanner.next(SURFACE, MIN_Y, List.of(done(54, 1, Band.IRON), done(one.step(), 1, Band.IRON)), -1, 126, 0, 1,
                MinePlanner.wanted(noIron), 1);
        assertNotEquals(Band.IRON, two.band(), "a third layer would hold more than one fully lengthened one: " + two);
    }

    @Test
    void shaftStoppedAboveAnOreTakesItsNearestHeight() {
        // the shaft could not go below step 50 (y=20): iron's own height (step 54, y=16) is out of reach
        Stock noIron = new Stock(3, 100, 100, 100, 100, false, false);
        Decision d = MinePlanner.next(SURFACE, MIN_Y, List.of(done(6, 4, Band.COAL)), 0, 50, 50, 1, MinePlanner.wanted(noIron), 4);
        assertEquals(Kind.NEW_LAYER, d.kind(), "" + d);
        assertEquals(Band.IRON, d.band());
        assertEquals(50, d.step(), "the deepest landing the shaft reaches, still in iron's band");
        // too far above it: no iron layer at all
        Decision far = MinePlanner.next(SURFACE, MIN_Y, List.of(done(6, 4, Band.COAL)), 0, 30, 30, 1, MinePlanner.wanted(noIron), 4);
        assertNotEquals(Band.IRON, far.band(), "y=40 is coal's ground, not iron's: " + far);
    }

    @Test
    void moreLayersNeverBreakTheRules() {
        Random r = new Random(6);
        for (int i = 0; i < 5000; i++) {
            List<Level> levels = randomLevels(r);
            int tier = r.nextInt(4), limit = r.nextBoolean() ? 0 : 6 + r.nextInt(120), maxRounds = r.nextInt(5);
            Decision d = MinePlanner.next(SURFACE, MIN_Y, levels, levels.isEmpty() ? -1 : r.nextInt(levels.size()), 126, limit, tier,
                    MinePlanner.wanted(randomStock(r)), maxRounds);
            if (d.kind() == Kind.NEW_LAYER) {
                assertTrue(d.band().pickTier <= tier, "" + d);
                assertTrue(limit == 0 || d.step() <= limit, "below the shaft's limit: " + d);
                assertTrue(SpiralMine.isLanding(d.step()) && d.step() >= MinePlanner.MIN_DEPTH, "" + d);
                for (Level l : levels) {
                    assertTrue(Math.abs(l.step() - d.step()) >= MinePlanner.MIN_SPACING, "new layer " + d.step() + " too close to " + l.step());
                }
            }
            if (d.kind() == Kind.LENGTHEN) {
                assertTrue(levels.get(d.level()).rounds() < maxRounds, "lengthened beyond the corridor: " + d);
            }
        }
    }

    @Test
    void wantedOrder() {
        assertEquals(List.of(Band.IRON, Band.DEEP, Band.COAL, Band.GOLD, Band.LAPIS),
                MinePlanner.wanted(new Stock(3, 0, 0, 0, 0, true, true)));
        assertEquals(List.of(Band.DEEP, Band.IRON, Band.COAL, Band.GOLD, Band.LAPIS),
                MinePlanner.wanted(new Stock(20, 0, 0, 0, 0, true, false)));
        assertEquals(List.of(Band.GOLD), MinePlanner.wanted(new Stock(32, 8, 15, 32, 32, true, false)));
        assertEquals(List.of(), MinePlanner.wanted(new Stock(32, 8, 16, 32, 32, true, false)));
    }

    @Test
    void bandAtPicksTheNearestTarget() {
        assertEquals(Band.COAL, MinePlanner.bandAt(64, SURFACE, MIN_Y));
        assertEquals(Band.IRON, MinePlanner.bandAt(16, SURFACE, MIN_Y));
        assertEquals(Band.LAPIS, MinePlanner.bandAt(4, SURFACE, MIN_Y));
        assertEquals(Band.GOLD, MinePlanner.bandAt(-20, SURFACE, MIN_Y));
        assertEquals(Band.DEEP, MinePlanner.bandAt(-56, SURFACE, MIN_Y));
        assertEquals(Band.LAPIS, MinePlanner.bandAt(-8, SURFACE, MIN_Y), "a tie goes to the shallower band");
    }
}
