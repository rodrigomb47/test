package com.factionclash.logic;

/**
 * A player's food bar, rule for rule (vanilla {@code FoodData} and the peaceful bonus of {@code Player.aiStep}):
 * food level 0-20, saturation up to the food level, exhaustion that costs a saturation point (or, without
 * saturation, a food point) for every 4 accumulated. Healing comes from food: fast while saturated at 20, slow at
 * 18+, and it costs exhaustion; at 0 food the NPC starves. Pure logic, so the rules are unit-tested.
 */
public final class NpcHunger {
    public static final int MAX_FOOD = 20;
    /** Difficulty ids as vanilla: 0 peaceful, 1 easy, 2 normal, 3 hard. */
    public static final int PEACEFUL = 0, EASY = 1, NORMAL = 2, HARD = 3;

    private int foodLevel = MAX_FOOD;
    private float saturation = 5.0F;
    private float exhaustion;
    private int tickTimer;
    private int peacefulTimer;

    /** What a tick asks of the entity: heal this much (positive) or take this much starvation damage (negative). */
    public float tick(float health, float maxHealth, int difficulty, boolean naturalRegen) {
        if (exhaustion > 4.0F) {
            exhaustion -= 4.0F;
            if (saturation > 0.0F) {
                saturation = Math.max(saturation - 1.0F, 0.0F);
            } else if (difficulty != PEACEFUL) {
                foodLevel = Math.max(foodLevel - 1, 0);
            }
        }
        float change = 0;
        boolean hurt = health > 0 && health < maxHealth;
        if (difficulty == PEACEFUL && naturalRegen) {
            // peaceful: health and food come back on their own, as for a player
            if (++peacefulTimer % 20 == 0 && hurt) {
                change += 1.0F;
            }
            if (peacefulTimer % 10 == 0 && foodLevel < MAX_FOOD) {
                foodLevel++;
            }
        }
        if (naturalRegen && saturation > 0.0F && hurt && foodLevel >= MAX_FOOD) {
            if (++tickTimer >= 10) {
                float f = Math.min(saturation, 6.0F);
                change += f / 6.0F;
                exhaust(f);
                tickTimer = 0;
            }
        } else if (naturalRegen && foodLevel >= 18 && hurt) {
            if (++tickTimer >= 80) {
                change += 1.0F;
                exhaust(6.0F);
                tickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            if (++tickTimer >= 80) {
                if (health > 10.0F || difficulty == HARD || health > 1.0F && difficulty == NORMAL) {
                    change -= 1.0F;
                }
                tickTimer = 0;
            }
        } else {
            tickTimer = 0;
        }
        return change;
    }

    /** Eating: nutrition fills the food bar, saturation fills up to the new food level (vanilla FoodData.add). */
    public void eat(int nutrition, float saturationGained) {
        if (nutrition > 0) {
            foodLevel = Math.max(0, Math.min(foodLevel + nutrition, MAX_FOOD));
            saturation = Math.max(0, Math.min(saturation + saturationGained, foodLevel));
        }
    }

    public void exhaust(float amount) {
        exhaustion = Math.min(exhaustion + amount, 40.0F);
    }

    public int foodLevel() {
        return foodLevel;
    }

    public float saturation() {
        return saturation;
    }

    public float exhaustion() {
        return exhaustion;
    }

    /** Room in the food bar for an item of this nutrition without wasting any of it. */
    public boolean hasRoomFor(int nutrition) {
        return foodLevel + nutrition <= MAX_FOOD;
    }

    public void set(int food, float sat, float exh) {
        foodLevel = Math.max(0, Math.min(food, MAX_FOOD));
        saturation = Math.max(0, Math.min(sat, foodLevel));
        exhaustion = Math.max(0, Math.min(exh, 40.0F));
    }
}
