package com.factionclash.logic;

import com.factionclash.logic.ForestRules.Action;
import com.factionclash.logic.ForestRules.Ground;
import com.factionclash.logic.ForestRules.Replant;
import com.factionclash.logic.ForestRules.Species;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForestRulesTest {
    @Test
    void speciesFromLog() {
        for (Species s : Species.values()) {
            assertEquals(s, ForestRules.fromLog("minecraft:" + s.log()), s.log());
            assertEquals(s, ForestRules.fromLog(s.log()), "a bare id is a vanilla one");
            assertEquals(s, ForestRules.fromSapling("minecraft:" + s.sapling()), s.sapling());
        }
        assertEquals(Species.OAK, ForestRules.fromLog("minecraft:oak_log"));
        assertEquals(Species.DARK_OAK, ForestRules.fromLog("minecraft:dark_oak_log"));
        assertEquals(Species.CHERRY, ForestRules.fromLog("minecraft:cherry_log"));
        assertNull(ForestRules.fromLog("minecraft:mangrove_log"), "mangroves stand on roots");
        assertNull(ForestRules.fromLog("minecraft:stripped_oak_log"), "stripped logs are built");
        assertNull(ForestRules.fromLog("minecraft:stripped_birch_log"));
        assertNull(ForestRules.fromLog("minecraft:oak_wood"), "wood blocks are built");
        assertNull(ForestRules.fromLog("minecraft:dark_oak_wood"));
        assertNull(ForestRules.fromLog("minecraft:crimson_stem"), "nether stems are not trees here");
        assertNull(ForestRules.fromLog("minecraft:warped_stem"));
        assertNull(ForestRules.fromLog("othermod:oak_log"), "another mod's logs are never felled");
        assertNull(ForestRules.fromLog(null));
        assertNull(ForestRules.fromSapling("minecraft:mangrove_propagule"));
        assertNull(ForestRules.fromSapling("minecraft:azalea"));
        assertEquals(Species.DARK_OAK, Species.byName("dark_oak"));
        assertNull(Species.byName("MANGROVE"));
    }

    @Test
    void darkOakNeedsFourAndIsNeverPlantedAlone() {
        Replant three = ForestRules.replant(Species.DARK_OAK, true, 3, null, 0);
        assertEquals(Action.WAIT, three.action(), "three dark oak saplings cannot grow a dark oak");
        Replant four = ForestRules.replant(Species.DARK_OAK, true, 4, null, 0);
        assertEquals(new Replant(Action.SAME, Species.DARK_OAK, true, 4), four);
        Replant five = ForestRules.replant(Species.DARK_OAK, true, 9, Species.OAK, 0);
        assertEquals(4, five.count(), "a square is four saplings, however many are carried");
        // after the patience, any other carried sapling stands in, one of it, never dark oak
        Replant later = ForestRules.replant(Species.DARK_OAK, true, 3, Species.OAK, ForestRules.REPLANT_PATIENCE);
        assertEquals(new Replant(Action.SUBSTITUTE, Species.OAK, false, 1), later);
        Replant noSubstitute = ForestRules.replant(Species.DARK_OAK, true, 3, Species.DARK_OAK, ForestRules.REPLANT_PATIENCE);
        assertEquals(Action.WAIT, noSubstitute.action(), "dark oak is never planted alone, not even as a substitute");
        Map<Species, Integer> carried = new EnumMap<>(Species.class);
        carried.put(Species.DARK_OAK, 3);
        assertNull(ForestRules.substitute(s -> carried.getOrDefault(s, 0)), "only dark oak carried: no substitute");
        for (int n = 0; n <= 3; n++) {
            for (long w = 0; w < ForestRules.REPLANT_GIVE_UP; w += 997) {
                Replant r = ForestRules.replant(Species.DARK_OAK, true, n, ForestRules.substitute(s -> carried.getOrDefault(s, 0)), w);
                assertFalse(r.action() == Action.SAME || r.action() == Action.SUBSTITUTE, "with " + n + " dark oak saplings at " + w);
            }
        }
    }

    @Test
    void bigSpruceIsReplantedAsOneSapling() {
        Replant r = ForestRules.replant(Species.SPRUCE, true, 4, null, 0);
        assertEquals(new Replant(Action.SAME, Species.SPRUCE, false, 1), r, "never a 2x2 square: no huge spruce nobody can fell");
        Replant jungle = ForestRules.replant(Species.JUNGLE, true, 6, null, 0);
        assertEquals(new Replant(Action.SAME, Species.JUNGLE, false, 1), jungle);
        Replant plain = ForestRules.replant(Species.BIRCH, false, 1, null, 0);
        assertEquals(new Replant(Action.SAME, Species.BIRCH, false, 1), plain);
    }

    @Test
    void replantTimeline() {
        assertEquals(Action.SAME, ForestRules.replant(Species.BIRCH, false, 1, Species.OAK, 0).action());
        assertEquals(Action.SAME, ForestRules.replant(Species.BIRCH, false, 1, Species.OAK, 60000).action(),
                "the right sapling is always planted, however late");
        assertEquals(Action.WAIT, ForestRules.replant(Species.BIRCH, false, 0, Species.OAK, 0).action());
        assertEquals(Action.WAIT, ForestRules.replant(Species.BIRCH, false, 0, Species.OAK, 11999).action());
        Replant sub = ForestRules.replant(Species.BIRCH, false, 0, Species.OAK, 12000);
        assertEquals(new Replant(Action.SUBSTITUTE, Species.OAK, false, 1), sub);
        assertEquals(Action.WAIT, ForestRules.replant(Species.BIRCH, false, 0, null, 12000).action(), "nothing to stand in: keep waiting");
        assertEquals(Action.WAIT, ForestRules.replant(Species.BIRCH, false, 0, null, 47999).action());
        assertEquals(Action.DROP, ForestRules.replant(Species.BIRCH, false, 0, null, 48000).action());
        assertEquals(Action.DROP, ForestRules.replant(Species.BIRCH, false, 0, Species.OAK, 48000).action());
        assertEquals(Action.DROP, ForestRules.replant(null, false, 0, null, 48000).action(), "an unknown species is forgotten in time");
        // the substitute: the grove order first, then jungle, never dark oak
        Map<Species, Integer> carried = new EnumMap<>(Species.class);
        carried.put(Species.JUNGLE, 2);
        carried.put(Species.ACACIA, 1);
        carried.put(Species.DARK_OAK, 8);
        assertEquals(Species.ACACIA, ForestRules.substitute(s -> carried.getOrDefault(s, 0)));
        carried.put(Species.ACACIA, 0);
        assertEquals(Species.JUNGLE, ForestRules.substitute(s -> carried.getOrDefault(s, 0)));
        carried.put(Species.BIRCH, 1);
        assertEquals(Species.BIRCH, ForestRules.substitute(s -> carried.getOrDefault(s, 0)));
    }

    @Test
    void groveLatticeAndExclusions() {
        int half = 24;
        // the starter spots of a new base: on the lattice, in the belt, beside the west path
        for (int[] p : new int[][]{{-35, 5}, {-35, -5}, {-40, 5}, {-40, -5}}) {
            assertTrue(ForestRules.groveSpotAllowed(p[0], p[1], half, true, Ground.DIRT, false), p[0] + "," + p[1]);
        }
        assertTrue(ForestRules.onGroveLattice(-55, 25) && ForestRules.onGroveLattice(0, 0));
        assertFalse(ForestRules.groveSpotAllowed(-36, 5, half, true, Ground.DIRT, false), "off the lattice");
        assertFalse(ForestRules.groveSpotAllowed(-35, 6, half, true, Ground.DIRT, false), "off the lattice");
        // walls + 4: Chebyshev 28 is the first allowed ring; 25 (on the lattice) is too close
        assertFalse(ForestRules.groveSpotAllowed(-25, 10, half, true, Ground.DIRT, false), "within the walls + 4");
        assertFalse(ForestRules.groveSpotAllowed(25, -25, half, true, Ground.DIRT, false), "within the walls + 4");
        assertTrue(ForestRules.groveSpotAllowed(30, -25, half, true, Ground.DIRT, false));
        assertTrue(ForestRules.groveSpotAllowed(-10, -30, 5, true, Ground.DIRT, false), "a smaller base, its own walls");
        // the road strip keeps both gates and the west path clear
        assertFalse(ForestRules.groveSpotAllowed(-35, 0, half, true, Ground.DIRT, false), "on the west path");
        assertFalse(ForestRules.groveSpotAllowed(50, 0, half, true, Ground.DIRT, false), "in front of the east gate");
        assertTrue(ForestRules.onRoadStrip(-54, 3, half) && !ForestRules.onRoadStrip(-55, 0, half) && !ForestRules.onRoadStrip(-40, 4, half));
        assertTrue(ForestRules.groveSpotAllowed(-55, 0, half, true, Ground.DIRT, false), "beyond the strip's end");
        // outside the force-ticked belt nothing grows without players
        assertFalse(ForestRules.groveSpotAllowed(-35, 5, half, false, Ground.DIRT, false), "outside the belt");
        // farmland and paths are never planted; nor is a reserved place
        assertFalse(ForestRules.groveSpotAllowed(-35, 5, half, true, Ground.FARMLAND, false), "farmland");
        assertFalse(ForestRules.groveSpotAllowed(-35, 5, half, true, Ground.PATH, false), "a path");
        assertFalse(ForestRules.groveSpotAllowed(-35, 5, half, true, Ground.OTHER, false), "stone or sand");
        assertFalse(ForestRules.groveSpotAllowed(-35, 5, half, true, Ground.DIRT, true), "reserved for a mine or the fishery");
    }

    @Test
    void groveOrderNeverJungleOrDarkOak() {
        assertFalse(ForestRules.GROVE_ORDER.contains(Species.JUNGLE));
        assertFalse(ForestRules.GROVE_ORDER.contains(Species.DARK_OAK));
        assertEquals(Species.BIRCH, ForestRules.GROVE_ORDER.get(0), "birch grows the shortest trunks");
        Map<Species, Integer> carried = new EnumMap<>(Species.class);
        carried.put(Species.JUNGLE, 5);
        carried.put(Species.DARK_OAK, 8);
        assertNull(ForestRules.groveSpecies(s -> carried.getOrDefault(s, 0)), "jungle and dark oak never go into the grove");
        carried.put(Species.CHERRY, 1);
        carried.put(Species.OAK, 3);
        assertEquals(Species.OAK, ForestRules.groveSpecies(s -> carried.getOrDefault(s, 0)));
        carried.put(Species.SPRUCE, 1);
        assertEquals(Species.SPRUCE, ForestRules.groveSpecies(s -> carried.getOrDefault(s, 0)));
    }

    @Test
    void startersLieWithinTheSmallestForestRadius() {
        // a server may set forestRadius as low as the config allows: the tree search must still find every starter
        int half = 24;
        assertEquals(4, ForestRules.STARTER_OFFSETS.size());
        for (int[] o : ForestRules.STARTER_OFFSETS) {
            assertTrue(o[0] * o[0] + o[1] * o[1] <= ForestRules.MIN_FOREST_RADIUS * ForestRules.MIN_FOREST_RADIUS,
                    o[0] + "," + o[1] + " lies beyond the smallest forest radius " + ForestRules.MIN_FOREST_RADIUS);
            assertTrue(ForestRules.groveSpotAllowed(o[0], o[1], half, true, Ground.DIRT, false), "a starter stands on a grove spot");
        }
        // the old lowest setting (32) left all four outside, so the woodcutter had to forage for its own trees
        assertTrue(ForestRules.STARTER_OFFSETS.stream().allMatch(o -> o[0] * o[0] + o[1] * o[1] > 32 * 32));
    }

    @Test
    void groveGrowsTwiceAsBigWhileShortOfWood() {
        assertEquals(16, ForestRules.DEFAULT_GROVE_SIZE);
        assertEquals(16, ForestRules.groveTarget(16, false));
        assertEquals(32, ForestRules.groveTarget(16, true), "short of wood: twice the saplings");
        assertEquals(16, ForestRules.groveTarget(8, true));
        assertEquals(ForestRules.MAX_GROVE_SIZE, ForestRules.groveTarget(20, true), "never beyond the config's ceiling");
        assertEquals(0, ForestRules.groveTarget(0, true), "groveSize 0 keeps no grove, short of wood or not");
        // the default's arithmetic: 3 random ticks in 4096 blocks a tick, 1 in 7 grows a stage, two stages, lit about
        // half the day (raw sky light 9 or more): about a day and a half a sapling
        double ticksPerSapling = 2 / (3.0 / 4096 / 7) / 0.52;
        double treesPerDay = ForestRules.DEFAULT_GROVE_SIZE * 24000 / ticksPerSapling;
        assertTrue(treesPerDay >= 9 && treesPerDay <= 12, "about 10 trees a day: " + treesPerDay);
    }

    @Test
    void farWalksGetTheTimeTheyNeed() {
        assertEquals(ForestRules.WALK_BASE_TICKS, ForestRules.walkBudget(ForestRules.WALK_BASE_TICKS, 0));
        // a woodcutter 130 blocks out on a forage leg, walking back to a grove tree at about 4.3 blocks a second
        double walkTicks = 130 / 4.3 * 20;
        assertTrue(ForestRules.walkBudget(ForestRules.WALK_BASE_TICKS, 130) > 1.5 * walkTicks,
                "the way back is not a reason to give the tree up: " + ForestRules.walkBudget(ForestRules.WALK_BASE_TICKS, 130));
        assertTrue(ForestRules.walkBudget(ForestRules.WALK_BASE_TICKS, 130) > 400, "the old flat 400 ticks was 86 blocks at best");
        assertEquals(600 + 80, ForestRules.walkBudget(600, 10));
        assertEquals(600, ForestRules.walkBudget(600, -3), "no negative distance");
    }

    @Test
    void flowersArePickedOnlyForSpeciesThatGrowBeeNests() {
        assertTrue(ForestRules.beesNearFlowers(Species.OAK));
        assertTrue(ForestRules.beesNearFlowers(Species.BIRCH));
        assertTrue(ForestRules.beesNearFlowers(Species.CHERRY));
        assertFalse(ForestRules.beesNearFlowers(Species.SPRUCE));
        assertFalse(ForestRules.beesNearFlowers(Species.ACACIA));
        assertFalse(ForestRules.beesNearFlowers(Species.JUNGLE));
        assertFalse(ForestRules.beesNearFlowers(Species.DARK_OAK));
    }
}
