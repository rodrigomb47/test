package com.factionclash.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Output table for constructed mines. An entry is only eligible when BOTH the mine tier (depth) and the
 * miner's pickaxe tier are high enough (spec 6: "a deep mine does not allow an inadequate pickaxe to obtain
 * every material"). Ineligible weight falls back to cobblestone so the cycle still yields an item.
 *
 * <p>PROPOSED balance values: the original project's numbers were not available, so these are new,
 * documented defaults (see BALANCE.md).
 */
public final class MineLootTable {
    /** Pickaxe tiers: 0 wood/gold, 1 stone, 2 iron, 3 diamond, 4 netherite. */
    public static final int PICK_WOOD = 0, PICK_STONE = 1, PICK_IRON = 2, PICK_DIAMOND = 3, PICK_NETHERITE = 4;
    public static final int MAX_MINE_TIER = 4;

    public record Entry(MineResource resource, double baseWeight, int minMineTier, int minPickTier) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(MineResource.COBBLESTONE, 55.0, 1, PICK_WOOD),
            new Entry(MineResource.COAL, 18.0, 1, PICK_WOOD),
            new Entry(MineResource.RAW_COPPER, 10.0, 1, PICK_STONE),
            new Entry(MineResource.RAW_IRON, 9.0, 1, PICK_STONE),
            new Entry(MineResource.LAPIS, 3.0, 2, PICK_STONE),
            new Entry(MineResource.REDSTONE, 4.0, 2, PICK_IRON),
            new Entry(MineResource.RAW_GOLD, 3.0, 2, PICK_IRON),
            new Entry(MineResource.DIAMOND, 1.5, 3, PICK_IRON),
            new Entry(MineResource.EMERALD, 0.5, 4, PICK_IRON));

    private MineLootTable() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** Ore weights grow with depth: +25% per tier above 1. Cobblestone weight is constant. */
    public static double depthMultiplier(int mineTier) {
        return 1.0 + 0.25 * (Math.max(1, mineTier) - 1);
    }

    public static boolean isEligible(Entry e, int mineTier, int pickTier) {
        return mineTier >= e.minMineTier() && pickTier >= e.minPickTier();
    }

    /** Effective weights for a given mine tier and pickaxe tier (ineligible weight folded into cobblestone). */
    public static Map<MineResource, Double> weights(int mineTier, int pickTier) {
        Map<MineResource, Double> out = new EnumMap<>(MineResource.class);
        double folded = 0;
        for (Entry e : ENTRIES) {
            double w = e.resource().isOre() ? e.baseWeight() * depthMultiplier(mineTier) : e.baseWeight();
            if (isEligible(e, mineTier, pickTier)) {
                out.merge(e.resource(), w, Double::sum);
            } else {
                folded += w;
            }
        }
        out.merge(MineResource.COBBLESTONE, folded, Double::sum);
        return out;
    }

    public static Map<MineResource, Double> probabilities(int mineTier, int pickTier) {
        Map<MineResource, Double> w = weights(mineTier, pickTier);
        double total = w.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<MineResource, Double> out = new EnumMap<>(MineResource.class);
        w.forEach((k, v) -> out.put(k, v / total));
        return out;
    }

    public static MineResource roll(int mineTier, int pickTier, RandomGenerator random) {
        Map<MineResource, Double> w = weights(mineTier, pickTier);
        double total = w.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = random.nextDouble() * total;
        for (Map.Entry<MineResource, Double> e : w.entrySet()) {
            r -= e.getValue();
            if (r < 0) {
                return e.getKey();
            }
        }
        return MineResource.COBBLESTONE;
    }

    /** Resources the given combination can ever produce. */
    public static List<MineResource> obtainable(int mineTier, int pickTier) {
        List<MineResource> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (isEligible(e, mineTier, pickTier)) {
                out.add(e.resource());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Pickaxe tiers make each cycle faster (PROPOSED: wood 1.0x .. netherite 1.6x). */
    public static double pickaxeSpeed(int pickTier) {
        return switch (pickTier) {
            case PICK_STONE -> 1.15;
            case PICK_IRON -> 1.3;
            case PICK_DIAMOND -> 1.5;
            case PICK_NETHERITE -> 1.6;
            default -> 1.0;
        };
    }

    public static int cycleTicks(int baseTicks, int pickTier, double workSpeedBonus) {
        double t = baseTicks / (pickaxeSpeed(pickTier) * (1.0 + workSpeedBonus));
        return Math.max(20, (int) Math.round(t));
    }
}
