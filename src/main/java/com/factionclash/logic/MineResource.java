package com.factionclash.logic;

/**
 * Materials a constructed mine can yield. Kept free of Minecraft classes so loot rules are unit-testable;
 * {@code com.factionclash.work.MineItems} maps each entry to a real item.
 */
public enum MineResource {
    COBBLESTONE(0),
    COAL(2),
    RAW_COPPER(2),
    RAW_IRON(4),
    LAPIS(4),
    REDSTONE(3),
    RAW_GOLD(6),
    DIAMOND(10),
    EMERALD(12);

    /** Experience granted to the miner for producing one unit. Cobblestone grants none (spec 18). */
    public final int xp;

    MineResource(int xp) {
        this.xp = xp;
    }

    public boolean isOre() {
        return this != COBBLESTONE;
    }
}
