package com.factionclash.npc;

import com.factionclash.logic.Affix;

/**
 * Roles implemented in this build. Other professions from the design (animal carer, butcher, fisherman,
 * enchanter, quartermaster, cave explorer, axe fighter, field medic, TNT expert, rail engineer) are not
 * implemented yet; see README "Status".
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
    SWORDSMAN("swordsman", "Swordsman", true, false, ToolKind.SWORD, Affix.Job.OTHER),
    ARCHER("archer", "Archer", true, true, ToolKind.BOW, Affix.Job.ARCHER);

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
