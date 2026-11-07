package com.factionclash.npc;

import net.minecraft.util.RandomSource;

/** Original generated names for ordinary NPCs. */
public final class NpcNames {
    private static final String[] FIRST = {
            "Aldo", "Bria", "Cato", "Dara", "Edrin", "Fenna", "Garo", "Hale", "Isla", "Joren", "Kesta", "Lio",
            "Mara", "Nils", "Orla", "Pell", "Quin", "Rhea", "Soren", "Tamsin", "Ulric", "Vela", "Wren", "Yara",
            "Zeno", "Arlo", "Beck", "Cira", "Doran", "Elsa", "Fitch", "Greer", "Hollis", "Ines", "Jory", "Kael"};
    private static final String[] LAST = {
            "Ashford", "Brook", "Cole", "Dunmore", "Ember", "Flint", "Grey", "Hart", "Ives", "Kerr", "Lowe",
            "Marsh", "North", "Oakes", "Pike", "Reed", "Stone", "Thorne", "Vale", "Wells"};

    private NpcNames() {
    }

    public static String random(RandomSource random) {
        return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
    }
}
