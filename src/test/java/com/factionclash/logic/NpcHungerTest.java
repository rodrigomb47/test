package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcHungerTest {
    @Test
    void exhaustionEatsSaturationFirstThenFood() {
        NpcHunger h = new NpcHunger(); // starts like a fresh player: 20 food, 5 saturation
        for (int i = 0; i < 5; i++) {
            h.exhaust(4.01F);
            h.tick(20, 20, NpcHunger.NORMAL, true);
        }
        assertEquals(20, h.foodLevel());
        assertEquals(0.0F, h.saturation(), 1e-4);
        h.exhaust(4.01F);
        h.tick(20, 20, NpcHunger.NORMAL, true);
        assertEquals(19, h.foodLevel());
    }

    @Test
    void eatingLikeFoodData() {
        NpcHunger h = new NpcHunger();
        h.set(10, 0, 0);
        h.eat(5, 6.0F); // bread
        assertEquals(15, h.foodLevel());
        assertEquals(6.0F, h.saturation(), 1e-4);
        h.eat(8, 12.8F); // steak: food caps at 20, saturation at the food level
        assertEquals(20, h.foodLevel());
        assertEquals(18.8F, h.saturation(), 1e-3);
        assertTrue(!h.hasRoomFor(1));
    }

    @Test
    void healsOnlyWhenFedAndPaysForIt() {
        NpcHunger h = new NpcHunger();
        h.set(18, 0, 0);
        float healed = 0;
        for (int i = 0; i < 80; i++) {
            healed += h.tick(10, 20, NpcHunger.NORMAL, true);
        }
        assertEquals(1.0F, healed, 1e-4); // slow regeneration at 18+
        assertEquals(6.0F, h.exhaustion(), 1e-4);
        NpcHunger hungry = new NpcHunger();
        hungry.set(17, 0, 0);
        float none = 0;
        for (int i = 0; i < 400; i++) {
            none += hungry.tick(10, 20, NpcHunger.NORMAL, true);
        }
        assertEquals(0.0F, none, 1e-4); // below 18 food nothing heals
    }

    @Test
    void starvationDependsOnDifficulty() {
        for (int diff : new int[]{NpcHunger.EASY, NpcHunger.NORMAL, NpcHunger.HARD}) {
            NpcHunger h = new NpcHunger();
            h.set(0, 0, 0);
            float health = 20;
            for (int i = 0; i < 80 * 30; i++) {
                health += h.tick(health, 20, diff, true);
            }
            float expected = diff == NpcHunger.EASY ? 10 : diff == NpcHunger.NORMAL ? 1 : -10;
            assertEquals(expected, health, 1e-4, "difficulty " + diff);
        }
    }
}
