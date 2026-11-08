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
            case SWORDSMAN, ARCHER, CROSSBOWMAN, AXE_FIGHTER -> new GuardJob(npc);
            case ANIMAL_CARER -> new AnimalCarerJob(npc);
            case BUTCHER -> new ButcherJob(npc);
            case FISHERMAN -> new FishermanJob(npc);
            case CAVE_EXPLORER -> new CaveExplorerJob(npc);
            case HAULER -> new HaulerJob(npc);
            case ENCHANTER -> new EnchanterJob(npc);
            case TRADER -> new TraderJob(npc);
        };
    }
}
