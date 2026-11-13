package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.TraitTier;
import com.factionclash.logic.hero.HeroLinks.Cell;
import com.factionclash.logic.hero.HeroLinks.Enemy;
import com.factionclash.logic.hero.HeroLinks.Kind;
import com.factionclash.logic.hero.HeroLinks.Link;
import com.factionclash.logic.hero.HeroLinks.Pos;
import com.factionclash.logic.hero.HeroLinks.StrikeFrom;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Situation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 3.2-3.4, 3.7 and 5.1.15: the hero's links on a pure grid, and who can reach whom. */
class HeroLinksTest {

    /** Flat ground with its top at {@code ground(x, z)} (cells below are solid), changed cell by cell. */
    static final class G implements HeroLinks.Grid {
        final Map<Pos, Cell> cells = new HashMap<>();
        final java.util.function.ToIntBiFunction<Integer, Integer> ground;

        G(java.util.function.ToIntBiFunction<Integer, Integer> ground) {
            this.ground = ground;
        }

        static G flat() {
            return new G((x, z) -> 0);
        }

        @Override
        public Cell at(int x, int y, int z) {
            Cell c = cells.get(new Pos(x, y, z));
            return c != null ? c : y < ground.applyAsInt(x, z) ? Cell.SOLID : Cell.AIR;
        }

        @Override
        public int minY() {
            return -20;
        }

        G set(int x, int y, int z, Cell c) {
            cells.put(new Pos(x, y, z), c);
            return this;
        }

        G fill(int x0, int y0, int z0, int x1, int y1, int z1, Cell c) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, c);
                    }
                }
            }
            return this;
        }
    }

    private static final Pos O = new Pos(0, 0, 0);

    private static Profile p(MobilityTrait t, int tier, int level) {
        return MobilityRules.profile(new TraitTier(t, tier), null, level);
    }

    private static Profile p(MobilityTrait t, int tier, MobilityTrait s, int sTier, int level) {
        return MobilityRules.profile(new TraitTier(t, tier), new TraitTier(s, sTier), level);
    }

    private static final Profile KAGENORI_1 = p(MobilityTrait.LEAPER, 2, MobilityTrait.RUNNER, 1, 1);
    private static final Profile KAGENORI_13 = p(MobilityTrait.LEAPER, 2, MobilityTrait.RUNNER, 1, 13);
    private static final Profile LEAPER_I_1 = p(MobilityTrait.LEAPER, 1, 1);
    private static final Profile LEAPER_I_20 = p(MobilityTrait.LEAPER, 1, 20);
    private static final Profile LEAPER_III = p(MobilityTrait.LEAPER, 3, 20);
    private static final Profile NATSUHO_1 = p(MobilityTrait.ACROBAT, 3, MobilityTrait.CLIMBER, 1, 1);
    private static final Profile TOBIMARU_1 = p(MobilityTrait.ACROBAT, 3, MobilityTrait.CLIMBER, 2, 1);
    private static final Profile AMBROSE_1 = p(MobilityTrait.FLYER, 2, 1);

    private static List<Link> of(List<Link> links, Kind k) {
        return links.stream().filter(l -> l.kind() == k).toList();
    }

    private static Link one(List<Link> links, Kind k) {
        List<Link> l = of(links, k);
        assertEquals(1, l.size(), links.toString());
        return l.get(0);
    }

    @Test
    void theGridModelStandsFallsAndSeesTheVanillaNeighbour() {
        G g = G.flat().fill(1, 0, 0, 1, 0, 0, Cell.SOLID).set(-1, 0, 0, Cell.FENCE).set(0, 0, 1, Cell.WATER);
        assertEquals(0, HeroLinks.standY(g, O));
        assertEquals(1, HeroLinks.standY(g, new Pos(1, 1, 0)));
        assertEquals(1.5, HeroLinks.standY(g, new Pos(-1, 1, 0)), "a fence top is 1.5 high");
        assertTrue(Double.isNaN(HeroLinks.standY(g, new Pos(0, 1, 0))), "in the air");
        assertTrue(HeroLinks.vanillaNeighbour(g, O, 1, 0), "a 1-block step");
        assertFalse(HeroLinks.vanillaNeighbour(g, O, -1, 0), "a fence: no mob jumps it");
        assertFalse(HeroLinks.vanillaNeighbour(g, O, 0, 1), "a fluid");
        assertTrue(HeroLinks.vanillaNeighbour(g, O, 0, -1), "level ground");
        G cliff = new G((x, z) -> x >= 1 ? -3 : 0);
        assertTrue(HeroLinks.vanillaNeighbour(cliff, O, 1, 0), "a 3-block drop is a worker's");
        G deep = new G((x, z) -> x >= 1 ? -4 : 0);
        assertFalse(HeroLinks.vanillaNeighbour(deep, O, 1, 0));
        HeroLinks.Floor v = HeroLinks.floor(new G((x, z) -> -100), 0, 0, 0);
        assertTrue(v.isVoid());
        assertEquals(Double.POSITIVE_INFINITY, v.depthBelow(0));
    }

    @Test
    void workersAndSoldiersHaveNoLinks() {
        G pit = G.flat().fill(-1, 0, -1, 1, 2, 1, Cell.SOLID).set(0, 0, 0, Cell.AIR).set(0, 1, 0, Cell.AIR)
                .set(0, 2, 0, Cell.AIR);
        assertTrue(HeroLinks.links(pit, O, MobilityRules.NONE, Situation.TRAVEL).isEmpty());
        assertTrue(HeroLinks.links(new G((x, z) -> x >= 1 ? -8 : 0), O, MobilityRules.NONE, Situation.ESCAPE)
                .isEmpty());
    }

    @Test
    void jumpUpLinksFollowTheJumpHeightMinusZeroPointThree() {
        G ledge = G.flat().fill(1, 0, 0, 1, 1, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(ledge, O, KAGENORI_1, Situation.TRAVEL), Kind.JUMP_UP).isEmpty(),
                "2.0 - 0.3 is short of a 2-block ledge");
        Link l = one(HeroLinks.links(ledge, O, KAGENORI_13, Situation.TRAVEL), Kind.JUMP_UP);
        assertEquals(new Pos(1, 2, 0), l.to());
        assertEquals(4, l.cost(), 1e-9, "2 + rise");
        // a field fence (1.5): a leaper I from level 1, thanks to the floor of 1.1
        G fence = G.flat().set(1, 0, 0, Cell.FENCE);
        Link f = one(HeroLinks.links(fence, O, LEAPER_I_1, Situation.FIGHT), Kind.JUMP_UP);
        assertEquals(3.5, f.cost(), 1e-9);
        // 3 blocks at most, however high the hero jumps
        G three = G.flat().fill(1, 0, 0, 1, 2, 0, Cell.SOLID);
        assertEquals(1, of(HeroLinks.links(three, O, LEAPER_III, Situation.TRAVEL), Kind.JUMP_UP).size());
        G four = G.flat().fill(1, 0, 0, 1, 3, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(four, O, LEAPER_III, Situation.TRAVEL), Kind.JUMP_UP).isEmpty());
        // an acrobat's air jumps count: Natsuho reaches 2.9 at level 1 (jump-ups to 2.6)
        assertEquals(1, of(HeroLinks.links(ledge, O, NATSUHO_1, Situation.TRAVEL), Kind.JUMP_UP).size());
        // headroom over the start: a block over its head stops the jump
        ledge.set(0, 3, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(ledge, O, KAGENORI_13, Situation.TRAVEL), Kind.JUMP_UP).isEmpty());
    }

    @Test
    void gapLinksComeFromTheLeapSolverWithinTheHerosWidth() {
        // a 5-wide ravine down to the void, then level ground
        G ravine = new G((x, z) -> x >= 1 && x <= 5 ? -100 : 0);
        Link gap = one(HeroLinks.links(ravine, O, LEAPER_I_20, Situation.TRAVEL), Kind.GAP);
        assertEquals(new Pos(6, 0, 0), gap.to());
        assertEquals(3.5, gap.cost(), 1e-9, "1 + width / 2");
        assertTrue(gap.risky(), "the void under the arc");
        assertEquals(0.50, gap.leap().vertical(), 1e-9);
        assertEquals(0.72, gap.leap().horizontal(), 1e-9);
        assertTrue(of(HeroLinks.links(ravine, O, LEAPER_I_1, Situation.TRAVEL), Kind.GAP).isEmpty(),
                "3 blocks at level 1");
        assertTrue(of(HeroLinks.links(ravine, O, LEAPER_I_20, Situation.FIGHT), Kind.GAP).isEmpty(),
                "a risky gap is refused in a fight");
        assertEquals(1, of(HeroLinks.links(ravine, O, LEAPER_I_20, Situation.ESCAPE), Kind.GAP).size(),
                "escaping: a wide landing is enough");
        // a shallow trench (5 deep, within the leaper III's safe fall of 16) is not risky: fine in a fight
        G trench = new G((x, z) -> x >= 1 && x <= 5 ? -5 : 0);
        Link safe = one(HeroLinks.links(trench, O, LEAPER_III, Situation.FIGHT), Kind.GAP);
        assertFalse(safe.risky());
        // a landing a block higher: within the limits
        G up = new G((x, z) -> x >= 1 && x <= 5 ? -100 : x > 5 ? 1 : 0);
        Link rise = one(HeroLinks.links(up, O, LEAPER_III, Situation.TRAVEL), Kind.GAP);
        assertEquals(new Pos(6, 1, 0), rise.to());
        // a 1-wide gap is not a link (2 is the least), a 9-wide gap is beyond every ceiling
        G narrow = new G((x, z) -> x == 1 ? -100 : 0);
        assertTrue(of(HeroLinks.links(narrow, O, LEAPER_III, Situation.TRAVEL), Kind.GAP).isEmpty());
        G wide = new G((x, z) -> x >= 1 && x <= 9 ? -100 : 0);
        assertTrue(of(HeroLinks.links(wide, O, LEAPER_III, Situation.TRAVEL), Kind.GAP).isEmpty());
        // a ceiling over the gap: no headroom along the arc
        G roofed = new G((x, z) -> x >= 1 && x <= 4 ? -100 : 0).fill(0, 2, 0, 5, 2, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(roofed, O, LEAPER_III, Situation.TRAVEL), Kind.GAP).isEmpty());
    }

    @Test
    void riskyGapsNeedAWideLandingAndNoLeapLandsNextToADeepDrop() {
        // a one-cell landing with the void beyond: never, not even escaping (1.6, 5.1.13)
        G perch = new G((x, z) -> x == 6 ? 0 : x <= 0 ? 0 : -100);
        for (Situation s : Situation.values()) {
            assertTrue(of(HeroLinks.links(perch, O, LEAPER_I_20, s), Kind.GAP).isEmpty(), s.name());
        }
        // a wall beyond the landing stops an overshoot: a wide landing
        G walled = new G((x, z) -> x >= 1 && x <= 5 ? -100 : x == 7 ? 4 : x > 7 ? -100 : 0);
        assertEquals(1, of(HeroLinks.links(walled, O, LEAPER_I_20, Situation.TRAVEL), Kind.GAP).size());
        assertTrue(of(HeroLinks.links(walled, O, LEAPER_I_20, Situation.FIGHT), Kind.GAP).isEmpty());
        // lava under the gap: risky, except for a fire-proof hero, for whom it is a plain 2-block drop
        G lava = new G((x, z) -> x >= 1 && x <= 3 ? -2 : 0).fill(1, -2, 0, 3, -2, 0, Cell.LAVA);
        Profile leaper = LEAPER_III;
        Profile fireproof = p(MobilityTrait.LEAPER, 3, MobilityTrait.EMBER_STRIDE, 1, 20);
        assertTrue(of(HeroLinks.links(lava, O, leaper, Situation.FIGHT), Kind.GAP).isEmpty(),
                "leaperRefusesLavaGapInFight");
        assertTrue(one(HeroLinks.links(lava, O, leaper, Situation.TRAVEL), Kind.GAP).risky(),
                "out of a fight, with a wide landing, it leaps");
        assertFalse(one(HeroLinks.links(lava, O, fireproof, Situation.FIGHT), Kind.GAP).risky());
        // powder snow under the arc is a hazard too
        G snow = new G((x, z) -> x >= 1 && x <= 3 ? -2 : 0).fill(1, -2, 0, 3, -2, 0, Cell.POWDER_SNOW);
        assertTrue(of(HeroLinks.links(snow, O, leaper, Situation.FIGHT), Kind.GAP).isEmpty());
        // in combat: a combat goal, a hostile within 16 blocks or a hurt in the last 5 s
        assertTrue(HeroLinks.inCombat(true, 99, 999));
        assertTrue(HeroLinks.inCombat(false, 16, 999));
        assertTrue(HeroLinks.inCombat(false, 99, 99));
        assertFalse(HeroLinks.inCombat(false, 16.5, 100));
        assertEquals(Situation.FIGHT, HeroLinks.linkSituation(true, false));
        assertEquals(Situation.ESCAPE, HeroLinks.linkSituation(true, true), "escaping is out of a fight for gaps");
        assertEquals(Situation.TRAVEL, HeroLinks.linkSituation(false, false));
    }

    @Test
    void dropLinksGoDownToTheSafeFallDistance() {
        G cliff = new G((x, z) -> x >= 1 ? -8 : 0);
        Link d = one(HeroLinks.links(cliff, O, p(MobilityTrait.LEAPER, 2, 20), Situation.TRAVEL), Kind.DROP);
        assertEquals(new Pos(1, -8, 0), d.to());
        assertEquals(3, d.cost(), 1e-9, "1 + drop / 4");
        assertTrue(of(HeroLinks.links(cliff, O, LEAPER_I_1, Situation.TRAVEL), Kind.DROP).isEmpty(), "4.8 safe");
        G four = new G((x, z) -> x >= 1 ? -4 : 0);
        assertEquals(1, of(HeroLinks.links(four, O, LEAPER_I_1, Situation.TRAVEL), Kind.DROP).size());
        G canyon = new G((x, z) -> x >= 1 ? -19 : 0);
        assertEquals(1, of(HeroLinks.links(canyon, O, NATSUHO_1, Situation.FIGHT), Kind.DROP).size(),
                "no fall damage: any drop");
        G lava = new G((x, z) -> x >= 1 ? -6 : 0).set(1, -6, 0, Cell.LAVA);
        assertTrue(of(HeroLinks.links(lava, O, NATSUHO_1, Situation.TRAVEL), Kind.DROP).isEmpty(), "never into lava");
    }

    @Test
    void climbLinksAndAHostileWallClimbedLikeAnyWall() {
        // a base wall 5 high and 1 thick
        G wall = G.flat().fill(1, 0, -3, 1, 4, 3, Cell.SOLID);
        Link c = one(HeroLinks.links(wall, O, TOBIMARU_1, Situation.RAID), Kind.CLIMB);
        assertEquals(new Pos(1, 5, 0), c.to());
        assertEquals(7.5, c.cost(), 1e-9, "1.5 a block");
        assertTrue(of(HeroLinks.links(wall, O, NATSUHO_1, Situation.RAID), Kind.CLIMB).isEmpty(), "climber I: 3");
        assertTrue(of(HeroLinks.links(wall, O, p(MobilityTrait.CLIMBER, 1, 12), Situation.RAID), Kind.CLIMB)
                .isEmpty());
        assertEquals(1, of(HeroLinks.links(wall, O, p(MobilityTrait.CLIMBER, 1, 13), Situation.RAID), Kind.CLIMB)
                .size(), "a ceiling-I climber scales a base wall from level 13");
        // the grid has no owner: a hostile base's wall gives the same links in every situation; only the route's
        // cost gives a hostile base a berth, and only outside a raid or a fight
        for (Situation s : Situation.values()) {
            assertEquals(List.of(c), of(HeroLinks.links(wall, O, TOBIMARU_1, s), Kind.CLIMB), s.name());
        }
        assertEquals(3, HeroLinks.berth(Situation.TRAVEL, true));
        assertEquals(3, HeroLinks.berth(Situation.ESCAPE, true));
        assertEquals(1, HeroLinks.berth(Situation.RAID, true), "a raid crosses it");
        assertEquals(1, HeroLinks.berth(Situation.FIGHT, true));
        assertEquals(1, HeroLinks.berth(Situation.TRAVEL, false));
        // a 3-high wall is leapt by a jump-up link as well
        G low = G.flat().fill(1, 0, -3, 1, 2, 3, Cell.SOLID);
        assertEquals(1, of(HeroLinks.links(low, O, LEAPER_III, Situation.RAID), Kind.JUMP_UP).size());
        // a crenellated top: merlons every second block; the hero stands between them
        G crenel = G.flat().fill(1, 0, -3, 1, 4, 3, Cell.SOLID).set(1, 5, 1, Cell.SOLID).set(1, 5, -1, Cell.SOLID);
        assertEquals(new Pos(1, 5, 0), one(HeroLinks.links(crenel, O, TOBIMARU_1, Situation.RAID), Kind.CLIMB).to());
        // a block in the climbing column stops it, unless a climber III climbs round the one-block overhang
        G lip = G.flat().fill(1, 0, 0, 1, 5, 0, Cell.SOLID).set(0, 4, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(lip, O, TOBIMARU_1, Situation.TRAVEL), Kind.CLIMB).isEmpty());
        Link round = one(HeroLinks.links(lip, O, p(MobilityTrait.CLIMBER, 3, 1), Situation.TRAVEL), Kind.CLIMB);
        assertEquals(new Pos(0, 5, 0), round.to());
        lip.set(-1, 5, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(lip, O, p(MobilityTrait.CLIMBER, 3, 1), Situation.TRAVEL), Kind.CLIMB).isEmpty(),
                "no room to climb round it");
    }

    @Test
    void chimneysGoUpOneOrTwoWideShafts() {
        // a 1-wide shaft 8 high between two walls, open at the top
        G shaft = G.flat().fill(-1, 0, 0, -1, 7, 0, Cell.SOLID).fill(1, 0, 0, 1, 7, 0, Cell.SOLID);
        Link c = one(HeroLinks.links(shaft, O, TOBIMARU_1, Situation.TRAVEL), Kind.CHIMNEY);
        assertEquals(8, HeroLinks.standY(shaft, c.to()), 1e-9);
        assertEquals(16, c.cost(), 1e-9, "2 a block");
        assertTrue(of(HeroLinks.links(shaft, O, p(MobilityTrait.ACROBAT, 1, 20), Situation.TRAVEL), Kind.CHIMNEY)
                .isEmpty(), "acrobat II-III only");
        // 2 wide
        G two = G.flat().fill(-1, 0, 0, -1, 7, 0, Cell.SOLID).fill(2, 0, 0, 2, 7, 0, Cell.SOLID);
        assertEquals(1, of(HeroLinks.links(two, O, TOBIMARU_1, Situation.TRAVEL), Kind.CHIMNEY).size());
        // 3 wide is no chimney; a capped shaft is no way up
        G three = G.flat().fill(-1, 0, 0, -1, 7, 0, Cell.SOLID).fill(3, 0, 0, 3, 7, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(three, O, TOBIMARU_1, Situation.TRAVEL), Kind.CHIMNEY).isEmpty());
        shaft.set(0, 8, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(shaft, O, TOBIMARU_1, Situation.TRAVEL), Kind.CHIMNEY).isEmpty());
    }

    @Test
    void glideLinksFollowTheGlideRatioAndAClearLine() {
        // a cliff 10 high: a glider I at full power glides 2.33 blocks per block of drop
        G cliff = new G((x, z) -> x >= 1 ? 0 : 10);
        Pos top = new Pos(0, 10, 0);
        Link gl = one(HeroLinks.links(cliff, top, p(MobilityTrait.GLIDER, 1, 20), Situation.TRAVEL), Kind.GLIDE);
        assertEquals(new Pos(23, 0, 0), gl.to(), "where the descent line meets the ground");
        assertEquals(11.5, gl.cost(), 1e-9, "1 a 2 blocks");
        // Ambrose at level 1 glides 1.6 per block of drop: a shorter glide
        Link amb = one(HeroLinks.links(cliff, top, AMBROSE_1, Situation.TRAVEL), Kind.GLIDE);
        assertEquals(16, amb.to().x());
        // a tower in the way
        cliff.fill(10, 0, 0, 10, 7, 0, Cell.SOLID);
        assertTrue(of(HeroLinks.links(cliff, top, p(MobilityTrait.GLIDER, 1, 20), Situation.TRAVEL), Kind.GLIDE)
                .isEmpty());
        // no launch from less than 4 blocks
        G step = new G((x, z) -> x >= 1 ? 0 : 3);
        assertTrue(of(HeroLinks.links(step, new Pos(0, 3, 0), p(MobilityTrait.GLIDER, 3, 20), Situation.TRAVEL),
                Kind.GLIDE).isEmpty());
        // past its glide time it does not glide there (a short-lived glide on the lowest curve)
        G high = new G((x, z) -> x >= 1 ? 0 : 40);
        MobilityRules.Config low = new MobilityRules.Config(true, true, new MobilityRules.Curve(30, 20), 100);
        Profile weak = MobilityRules.profile(new TraitTier(MobilityTrait.GLIDER, 1), null, 1, low);
        assertTrue(of(HeroLinks.links(high, new Pos(0, 40, 0), weak, Situation.TRAVEL), Kind.GLIDE).isEmpty());
        // into water: no landing
        G lake = new G((x, z) -> x >= 1 ? 0 : 10).fill(1, -1, 0, 40, -1, 0, Cell.WATER);
        assertTrue(of(HeroLinks.links(lake, top, p(MobilityTrait.GLIDER, 1, 20), Situation.TRAVEL), Kind.GLIDE)
                .isEmpty());
    }

    @Test
    void atMostEightLinksTheCheapestFirst() {
        // a 2-wide, 5-deep trench on every side, the ground beyond a block lower: a gap, a drop and a glide each way
        G g = new G((x, z) -> {
            int d = Math.max(Math.abs(x), Math.abs(z));
            return d == 0 ? 0 : d <= 2 ? -5 : -1;
        });
        Profile glider = p(MobilityTrait.GLIDER, 3, MobilityTrait.LEAPER, 2, 20);
        assertTrue(HeroLinks.gap(g, O, 0, 1, 0, glider, Situation.TRAVEL) != null);
        assertTrue(HeroLinks.drop(g, O, 0, 1, 0, glider) != null);
        assertTrue(HeroLinks.glide(g, O, 0, 1, 0, glider) != null);
        List<Link> l = HeroLinks.links(g, O, glider, Situation.TRAVEL);
        assertEquals(HeroLinks.MAX_LINKS, l.size(), "12 candidates, 8 kept");
        assertEquals(4, of(l, Kind.GAP).size());
        assertEquals(4, of(l, Kind.DROP).size());
        for (int i = 1; i < l.size(); i++) {
            assertTrue(l.get(i - 1).cost() <= l.get(i).cost());
        }
    }

    // ------------------------------------------------------------------ the fight-reach clock (5.1.15)

    @Test
    void aLinkThatLeavesEveryEngagedEnemyBehindStartsTheClockForAPlayerAndASoldierAlike() {
        G ravine = new G((x, z) -> x >= 1 && x <= 5 ? -100 : 0);
        Enemy player = new Enemy(new Pos(-2, 0, 0), MobilityRules.NONE);
        Enemy soldier = new Enemy(new Pos(-2, 0, 1), MobilityRules.NONE);
        Profile hero = LEAPER_III;
        Link across = one(HeroLinks.links(ravine, O, hero, Situation.ESCAPE), Kind.GAP);
        for (List<Enemy> e : List.of(List.of(player), List.of(soldier), List.of(player, soldier))) {
            assertTrue(HeroLinks.leavesEnemiesBehind(ravine, across, e), "nobody sprint-jumps 5 blocks");
        }
        // the clock: back within 4 s, every time
        BossRules.ReachClock clock = new BossRules.ReachClock();
        Pos at = across.to();
        int ticks = 0;
        boolean back = false;
        while (!back) {
            ticks++;
            back = clock.tick(!HeroLinks.anyEnemyReaches(ravine, at, List.of(player)), false, false);
        }
        assertEquals(80, ticks, "4 s");
        // the way back is a link too, and on the near side the enemies reach it: the clock starts over
        Link home = one(HeroLinks.links(ravine, at, hero, Situation.TRAVEL), Kind.GAP);
        assertEquals(O, home.to());
        assertTrue(HeroLinks.anyEnemyReaches(ravine, home.to(), List.of(player)));
        assertFalse(clock.tick(false, false, false));
        assertEquals(0, clock.ticks());
        // escaping, the clock never runs
        assertFalse(clock.tick(true, false, true));
        // a 3-block gap is no refuge: an ordinary person sprint-jumps it
        G narrow = new G((x, z) -> x >= 1 && x <= 3 ? -100 : 0);
        Link short3 = one(HeroLinks.links(narrow, O, hero, Situation.TRAVEL), Kind.GAP);
        assertFalse(HeroLinks.leavesEnemiesBehind(narrow, short3, List.of(player)));
        // an enemy hero follows on its own links (in a fight: no risky gap): ground it can reach is not out of reach
        G trench = new G((x, z) -> x >= 1 && x <= 5 ? -5 : 0);
        Link over = one(HeroLinks.links(trench, O, hero, Situation.FIGHT), Kind.GAP);
        assertTrue(HeroLinks.leavesEnemiesBehind(trench, over, List.of(player)), "5 deep: a person cannot climb out");
        Enemy rival = new Enemy(new Pos(-2, 0, 0), LEAPER_III);
        assertFalse(HeroLinks.leavesEnemiesBehind(trench, over, List.of(rival)));
        assertFalse(HeroLinks.leavesEnemiesBehind(trench, over, List.of(player, rival)), "one is enough");
        assertTrue(HeroLinks.leavesEnemiesBehind(ravine, across, List.of(rival)), "a void gap is risky: no hero"
                + " leaps it in a fight");
        // no engaged enemy: never out of reach
        assertTrue(HeroLinks.anyEnemyReaches(ravine, at, List.of()));
    }

    @Test
    void ordinaryPeopleTowerTwoBlocksAndSwimAcrossWater() {
        Enemy person = new Enemy(new Pos(-3, 0, 0), MobilityRules.NONE);
        // a hero on a 1x1 pillar: 3 high is in reach of a person who towers 2; 5 high is not
        G three = G.flat().fill(0, 0, 0, 0, 2, 0, Cell.SOLID);
        assertTrue(HeroLinks.anyEnemyReaches(three, new Pos(0, 3, 0), List.of(person)));
        G five = G.flat().fill(0, 0, 0, 0, 4, 0, Cell.SOLID);
        assertFalse(HeroLinks.anyEnemyReaches(five, new Pos(0, 5, 0), List.of(person)));
        // across a 6-wide river: a person swims
        G river = new G((x, z) -> x >= -1 && x <= 4 ? -2 : 0).fill(-1, -2, -5, 4, -1, 5, Cell.WATER);
        assertTrue(HeroLinks.anyEnemyReaches(river, new Pos(6, 0, 0), List.of(person)));
    }

    // ------------------------------------------------------------------ the strike from a link (3.7)

    @Test
    void aBasicAttackLandsFromALinkOnlyWithNoStandableCellInReach() {
        // a player or an NPC archer on a 1x1 pillar 5 high: no standable cell beside it
        G pillar = G.flat().fill(0, 0, 0, 0, 4, 0, Cell.SOLID);
        Pos target = new Pos(0, 5, 0), hero = new Pos(-1, 0, 0);
        assertFalse(HeroLinks.standableInReach(pillar, hero, TOBIMARU_1, target));
        assertTrue(HeroLinks.mayStrikeFromLink(true, false));
        assertFalse(HeroLinks.mayStrikeFromLink(false, false), "never a move");
        assertFalse(HeroLinks.mayStrikeFromLink(true, true), "with room beside the target it stands and fights");
        assertEquals(StrikeFrom.CLING, HeroLinks.strikeFrom(TOBIMARU_1, 5), "clinging just below the feet");
        assertEquals(StrikeFrom.AIR, HeroLinks.strikeFrom(AMBROSE_1, 5));
        assertEquals(StrikeFrom.APEX, HeroLinks.strikeFrom(NATSUHO_1, 5), "climb 3, but her apex is 2.9");
        assertNull(HeroLinks.strikeFrom(KAGENORI_1, 5), "a 2-block jump leaves 3 between");
        assertEquals(StrikeFrom.APEX, HeroLinks.strikeFrom(LEAPER_III, 5));
        assertNull(HeroLinks.strikeFrom(p(MobilityTrait.CHARGER, 3, 20), 5));
        assertNull(HeroLinks.strikeFrom(MobilityRules.NONE, 5));
        assertTrue(HeroLinks.mayUndermineFromCling(TOBIMARU_1), "a climber II works while clinging");
        assertFalse(HeroLinks.mayUndermineFromCling(NATSUHO_1));
        // a ledge with room beside the target: a climber climbs up and stands there
        G ledge = G.flat().fill(0, 0, -2, 0, 4, 2, Cell.SOLID);
        assertTrue(HeroLinks.standableInReach(ledge, hero, TOBIMARU_1, target));
        assertFalse(HeroLinks.standableInReach(ledge, hero, KAGENORI_1, target), "a leaper cannot get up there");
        assertNotNull(HeroLinks.strikeFrom(TOBIMARU_1, 5));
    }
}
