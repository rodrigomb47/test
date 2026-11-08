package com.factionclash.npc;

import com.factionclash.logic.Affix;

/**
 * Roles implemented in this build. Other professions from the design (field medic, TNT expert, rail engineer)
 * are not implemented yet; see README "Status".
 * New values are only ever appended: saves store the id, the network sync uses the ordinal.
 */
public enum NpcRole {
    BUILDER("builder", "Builder", false, false, ToolKind.SHOVEL, Affix.Job.BUILDER),
    MINER("miner", "Miner", false, false, ToolKind.PICKAXE, Affix.Job.MINER),
    FARMER("farmer", "Farmer", false, false, ToolKind.HOE, Affix.Job.FARMER),
    WOODCUTTER("woodcutter", "Woodcutter", false, false, ToolKind.AXE, Affix.Job.WOODCUTTER),
    BLACKSMITH("blacksmith", "Blacksmith", false, false, ToolKind.NONE, Affix.Job.OTHER),
    CHEF("chef", "Chef", false, false, ToolKind.NONE, Affix.Job.OTHER),
    SMELTER("smelter", "Smelter", false, false, ToolKind.NONE, Affix.Job.OTHER),
    SCOUT("scout", "Scout", false, false, ToolKind.SWORD, Affix.Job.SCOUT),
    SWORDSMAN("swordsman", "Swordsman", true, false, ToolKind.SWORD, Affix.Job.MELEE),
    ARCHER("archer", "Archer", true, true, ToolKind.BOW, Affix.Job.ARCHER),
    ANIMAL_CARER("animal_carer", "Animal Carer", false, false, ToolKind.NONE, Affix.Job.OTHER),
    BUTCHER("butcher", "Butcher", false, false, ToolKind.SWORD, Affix.Job.OTHER),
    FISHERMAN("fisherman", "Fisherman", false, false, ToolKind.ROD, Affix.Job.FISHER),
    CAVE_EXPLORER("cave_explorer", "Cave Explorer", false, false, ToolKind.SWORD, Affix.Job.SCOUT),
    HAULER("hauler", "Hauler", false, false, ToolKind.NONE, Affix.Job.OTHER),
    CROSSBOWMAN("crossbowman", "Crossbowman", true, true, ToolKind.CROSSBOW, Affix.Job.ARCHER),
    AXE_FIGHTER("axe_fighter", "Axe Fighter", true, false, ToolKind.AXE, Affix.Job.MELEE),
    ENCHANTER("enchanter", "Enchanter", false, false, ToolKind.NONE, Affix.Job.OTHER),
    TRADER("trader", "Trader", false, false, ToolKind.NONE, Affix.Job.TRADER);

    public final String id;
    public final String displayName;
    public final boolean combatant;
    public final boolean ranged;
    public final ToolKind tool;
    public final Affix.Job affixJob;

    NpcRole(String id, String displayName, boolean combatant, boolean ranged, ToolKind tool, Affix.Job affixJob) {
        this.id = id;
        this.displayName = displayName;
        this.combatant = combatant;
        this.ranged = ranged;
        this.tool = tool;
        this.affixJob = affixJob;
    }

    public boolean isCivilian() {
        return !combatant;
    }

    public static NpcRole byId(String id) {
        for (NpcRole r : values()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** The established starting roster: one of each essential worker plus three swordsmen (ten NPCs). */
    public static NpcRole[] startingRoster() {
        return new NpcRole[]{BUILDER, MINER, FARMER, WOODCUTTER, BLACKSMITH, CHEF, SCOUT, SWORDSMAN, SWORDSMAN, SWORDSMAN};
    }
}
