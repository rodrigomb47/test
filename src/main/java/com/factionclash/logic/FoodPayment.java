package com.factionclash.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans which food to spend on a recruitment. Each food unit is worth the hunger points it normally
 * restores (spec 9), so better food covers more of the bill. The plan prefers to spend the least
 * valuable food that still covers the bill and minimises waste.
 */
public final class FoodPayment {
    private FoodPayment() {
    }

    /** One kind of food in storage. {@code key} identifies it to the caller (e.g. an item id). */
    public record Option(String key, int nutrition, int available) {
    }

    public record Use(String key, int count, int nutritionEach) {
        public int value() {
            return count * nutritionEach;
        }
    }

    public record Plan(List<Use> uses, int paidValue) {
        public boolean isEmpty() {
            return uses.isEmpty();
        }
    }

    public static int totalValue(List<Option> options) {
        int sum = 0;
        for (Option o : options) {
            if (o.nutrition() > 0 && o.available() > 0) {
                sum += o.nutrition() * o.available();
            }
        }
        return sum;
    }

    /**
     * @return a plan whose paid value is at least {@code required} with the smallest possible overpayment, or null
     * if storage cannot cover it. Among equally sized payments it spends lower-value food first, so cooked meals
     * are kept when raw food can pay (a bounded knapsack over the small bill size).
     */
    public static Plan plan(List<Option> options, int required) {
        if (required <= 0) {
            return new Plan(List.of(), 0);
        }
        List<Option> usable = new ArrayList<>();
        int maxN = 0;
        for (Option o : options) {
            if (o.nutrition() > 0 && o.available() > 0) {
                usable.add(o);
                maxN = Math.max(maxN, o.nutrition());
            }
        }
        if (totalValue(usable) < required) {
            return null;
        }
        usable.sort(Comparator.comparingInt(Option::nutrition));
        int cap = required + maxN; // the minimal sufficient payment is always below this
        long inf = Long.MAX_VALUE / 4;
        int t = usable.size();
        long[] dp = new long[cap + 1];
        java.util.Arrays.fill(dp, inf);
        dp[0] = 0;
        int[][] pick = new int[t][cap + 1];
        for (int i = 0; i < t; i++) {
            Option o = usable.get(i);
            int n = o.nutrition();
            int maxUnits = Math.min(o.available(), cap / n + 1);
            long[] next = new long[cap + 1];
            java.util.Arrays.fill(next, inf);
            for (int sum = 0; sum <= cap; sum++) {
                for (int k = 0; k <= maxUnits && k * n <= sum; k++) {
                    long prev = dp[sum - k * n];
                    if (prev >= inf) {
                        continue;
                    }
                    // secondary cost: prefer many low-value units over few high-value ones
                    long cost = prev + (long) k * n * n;
                    if (cost < next[sum]) {
                        next[sum] = cost;
                        pick[i][sum] = k;
                    }
                }
            }
            dp = next;
        }
        int best = -1;
        for (int sum = required; sum <= cap; sum++) {
            if (dp[sum] < inf) {
                best = sum;
                break;
            }
        }
        if (best < 0) {
            return null;
        }
        List<Use> uses = new ArrayList<>();
        int sum = best;
        for (int i = t - 1; i >= 0; i--) {
            int k = pick[i][sum];
            Option o = usable.get(i);
            if (k > 0) {
                uses.add(0, new Use(o.key(), k, o.nutrition()));
            }
            sum -= k * o.nutrition();
        }
        return new Plan(uses, best);
    }
}
