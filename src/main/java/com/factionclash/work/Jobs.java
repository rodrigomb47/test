package com.factionclash.work;

import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;

public final class Jobs {
    private Jobs() {
    }

    public static Job create(NpcRole role, FactionNpc npc) {
        return switch (role) {
            case MINER -> new MinerJob(npc);
            case WOODCUTTER -> new WoodcutterJob(npc);
            case FARMER -> new FarmerJob(npc);
            case CHEF -> new ChefJob(npc);
            case SMELTER -> new SmelterJob(npc);
            case BLACKSMITH -> new BlacksmithJob(npc);
            case BUILDER -> new BuilderJob(npc);
            case SCOUT -> new ScoutJob(npc);
            case SWORDSMAN, ARCHER -> new GuardJob(npc);
        };
    }
}
