package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoodPaymentTest {
    private static int units(FoodPayment.Plan p, String key) {
        return p.uses().stream().filter(u -> u.key().equals(key)).mapToInt(FoodPayment.Use::count).sum();
    }

    @Test
    void valueFollowsHungerRestoration() {
        // 20 hunger points: 4 bread (5 each) or 3 cooked beef (8 each) but 7 raw beef (3 each)
        FoodPayment.Plan bread = FoodPayment.plan(List.of(new FoodPayment.Option("bread", 5, 10)), 20);
        assertEquals(4, units(bread, "bread"));
        FoodPayment.Plan steak = FoodPayment.plan(List.of(new FoodPayment.Option("cooked_beef", 8, 10)), 20);
        assertEquals(3, units(steak, "cooked_beef"));
        FoodPayment.Plan raw = FoodPayment.plan(List.of(new FoodPayment.Option("beef", 3, 10)), 20);
        assertEquals(7, units(raw, "beef"));
        assertTrue(units(steak, "cooked_beef") < units(raw, "beef"), "cooked food must count for more than raw");
    }

    @Test
    void paysAtLeastTheBillWithLittleWaste() {
        List<FoodPayment.Option> opts = List.of(new FoodPayment.Option("bread", 5, 3), new FoodPayment.Option("cooked_beef", 8, 2),
                new FoodPayment.Option("apple", 4, 1));
        FoodPayment.Plan p = FoodPayment.plan(opts, 21);
        assertNotNull(p);
        assertTrue(p.paidValue() >= 21);
        assertTrue(p.paidValue() <= 21 + 4, "waste should be at most one small unit, was " + p.paidValue());
        for (FoodPayment.Use u : p.uses()) {
            int avail = opts.stream().filter(o -> o.key().equals(u.key())).findFirst().orElseThrow().available();
            assertTrue(u.count() <= avail);
        }
    }

    @Test
    void insufficientFoodReturnsNull() {
        assertNull(FoodPayment.plan(List.of(new FoodPayment.Option("bread", 5, 3)), 20));
        assertNull(FoodPayment.plan(List.of(), 1));
    }

    @Test
    void ignoresZeroNutritionAndEmptyStacks() {
        FoodPayment.Plan p = FoodPayment.plan(List.of(new FoodPayment.Option("air", 0, 99), new FoodPayment.Option("none", 6, 0),
                new FoodPayment.Option("bread", 5, 5)), 10);
        assertNotNull(p);
        assertEquals(1, p.uses().size());
        assertEquals("bread", p.uses().get(0).key());
    }

    @Test
    void exactCoverageUsesWholeUnits() {
        for (int need = 1; need <= 60; need++) {
            List<FoodPayment.Option> opts = List.of(new FoodPayment.Option("a", 2, 10), new FoodPayment.Option("b", 5, 4),
                    new FoodPayment.Option("c", 8, 3));
            FoodPayment.Plan p = FoodPayment.plan(opts, need);
            int total = FoodPayment.totalValue(opts);
            if (need > total) {
                assertNull(p);
            } else {
                assertNotNull(p, "need " + need);
                assertTrue(p.paidValue() >= need);
                assertEquals(p.paidValue(), p.uses().stream().mapToInt(FoodPayment.Use::value).sum());
            }
        }
    }
}
