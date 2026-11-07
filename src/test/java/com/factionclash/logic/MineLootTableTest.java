package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class MineLootTableTest {
    @Test
    void woodenPickaxeInShallowMineOnlyGetsStoneAndCoal() {
        assertEquals(List.of(MineResource.COBBLESTONE, MineResource.COAL), MineLootTable.obtainable(1, MineLootTable.PICK_WOOD));
    }

    @Test
    void deepMineDoesNotLetWeakPickaxeObtainEverything() {
        List<MineResource> deepStone = MineLootTable.obtainable(4, MineLootTable.PICK_STONE);
        assertFalse(deepStone.contains(MineResource.DIAMOND));
        assertFalse(deepStone.contains(MineResource.EMERALD));
        assertFalse(deepStone.contains(MineResource.RAW_GOLD));
        assertFalse(deepStone.contains(MineResource.REDSTONE));
        assertTrue(deepStone.contains(MineResource.RAW_IRON));
    }

    @Test
    void strongPickaxeInShallowMineIsLimitedByDepth() {
        List<MineResource> shallowDiamond = MineLootTable.obtainable(1, MineLootTable.PICK_DIAMOND);
        assertFalse(shallowDiamond.contains(MineResource.DIAMOND));
        assertFalse(shallowDiamond.contains(MineResource.LAPIS));
        assertTrue(MineLootTable.obtainable(3, MineLootTable.PICK_IRON).contains(MineResource.DIAMOND));
        assertTrue(MineLootTable.obtainable(4, MineLootTable.PICK_IRON).contains(MineResource.EMERALD));
    }

    @Test
    void probabilitiesSumToOneAndDepthImprovesOreOdds() {
        for (int tier = 1; tier <= 4; tier++) {
            for (int pick = 0; pick <= 4; pick++) {
                double sum = MineLootTable.probabilities(tier, pick).values().stream().mapToDouble(Double::doubleValue).sum();
                assertEquals(1.0, sum, 1e-9);
            }
        }
        double iron1 = MineLootTable.probabilities(1, MineLootTable.PICK_IRON).get(MineResource.RAW_IRON);
        double iron4 = MineLootTable.probabilities(4, MineLootTable.PICK_IRON).get(MineResource.RAW_IRON);
        assertTrue(iron4 > iron1, "deeper mines should improve ore odds");
        double cobble1 = MineLootTable.probabilities(1, MineLootTable.PICK_IRON).get(MineResource.COBBLESTONE);
        double cobble4 = MineLootTable.probabilities(4, MineLootTable.PICK_IRON).get(MineResource.COBBLESTONE);
        assertTrue(cobble4 < cobble1);
    }

    @Test
    void rollsNeverProduceIneligibleResourcesAndMatchOdds() {
        SplittableRandom r = new SplittableRandom(42);
        for (int tier = 1; tier <= 4; tier++) {
            for (int pick = 0; pick <= 4; pick++) {
                List<MineResource> ok = MineLootTable.obtainable(tier, pick);
                Map<MineResource, Integer> counts = new EnumMap<>(MineResource.class);
                int n = 40000;
                for (int i = 0; i < n; i++) {
                    MineResource res = MineLootTable.roll(tier, pick, r);
                    assertTrue(ok.contains(res), res + " not obtainable at tier " + tier + " pick " + pick);
                    counts.merge(res, 1, Integer::sum);
                }
                Map<MineResource, Double> p = MineLootTable.probabilities(tier, pick);
                for (MineResource res : ok) {
                    double observed = counts.getOrDefault(res, 0) / (double) n;
                    assertEquals(p.get(res), observed, 0.012, res + " frequency");
                }
            }
        }
    }

    @Test
    void cobblestoneGrantsNoExperience() {
        assertEquals(0, MineResource.COBBLESTONE.xp);
        assertTrue(MineResource.DIAMOND.xp > MineResource.COAL.xp);
    }

    @Test
    void betterPickaxesAreFaster() {
        int wood = MineLootTable.cycleTicks(140, MineLootTable.PICK_WOOD, 0);
        int iron = MineLootTable.cycleTicks(140, MineLootTable.PICK_IRON, 0);
        int ironDiligent = MineLootTable.cycleTicks(140, MineLootTable.PICK_IRON, 0.3);
        assertTrue(iron < wood);
        assertTrue(ironDiligent < iron);
        assertTrue(MineLootTable.cycleTicks(20, MineLootTable.PICK_NETHERITE, 5) >= 20);
    }
}
