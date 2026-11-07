package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;

/** Assigns personal workplaces: an NPC keeps its workplace; a free or orphaned one is claimed. */
public final class Workplaces {
    private Workplaces() {
    }

    public static WorkplaceState claim(FactionNpc npc, WorkplaceState.Type type) {
        BaseState base = npc.base();
        if (base == null || npc.level().getServer() == null) {
            return null;
        }
        CampaignData data = CampaignData.get(npc.level().getServer());
        WorkplaceState free = null;
        for (int id : base.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null || w.type != type) {
                continue;
            }
            if (npc.getUUID().equals(w.assigned)) {
                return w;
            }
            if (free == null) {
                if (w.assigned == null) {
                    free = w;
                } else {
                    NpcRecord holder = data.record(w.assigned);
                    if (holder == null || !holder.alive) {
                        free = w;
                    }
                }
            }
        }
        if (free != null) {
            free.assigned = npc.getUUID();
            data.setDirty();
        }
        return free;
    }
}
