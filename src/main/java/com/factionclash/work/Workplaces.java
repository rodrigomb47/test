package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Assigns personal workplaces: an NPC keeps its workplace while it still suits the job; a free or orphaned one is
 * claimed. A workplace counts as held only while its holder is alive and still in the role that works it, so a
 * worker who died or took up another job leaves it free for the next one.
 */
public final class Workplaces {
    private Workplaces() {
    }

    public static WorkplaceState claim(FactionNpc npc, WorkplaceState.Type type) {
        return claim(npc, type, w -> true);
    }

    /** The workplace of this type already assigned to the NPC, without claiming anything. */
    public static WorkplaceState held(FactionNpc npc, WorkplaceState.Type type) {
        BaseState base = npc.base();
        if (base == null || npc.level().getServer() == null) {
            return null;
        }
        CampaignData data = CampaignData.get(npc.level().getServer());
        for (int id : base.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w != null && w.type == type && npc.getUUID().equals(w.assigned)) {
                return w;
            }
        }
        return null;
    }

    /** The role that works a workplace of this type, or null when any living worker may hold it. */
    static NpcRole roleFor(WorkplaceState.Type type) {
        return switch (type) {
            case MINE, CAVE -> NpcRole.MINER;
            case TREE_PLOT -> NpcRole.WOODCUTTER;
            case FISHERY -> NpcRole.FISHERMAN;
            default -> null;
        };
    }

    /**
     * True if the workplace's holder is really there: its roster entry exists, it is alive, and it is still in the
     * role that works this kind of workplace. Old saves can hold records assigned to workers who were retrained
     * before retraining released them; those count as free.
     */
    public static boolean holderValid(CampaignData data, WorkplaceState w) {
        if (w.assigned == null) {
            return false;
        }
        NpcRecord holder = data.record(w.assigned);
        if (holder == null || !holder.alive) {
            return false;
        }
        NpcRole role = roleFor(w.type);
        return role == null || holder.role == role;
    }

    /** True if a workplace has no worker (nobody assigned, or its worker is dead or now does another job). */
    public static boolean unworked(CampaignData data, WorkplaceState w) {
        return !holderValid(data, w);
    }

    /**
     * Claims a workplace of this type that passes {@code ok}. The NPC's own one comes first, but only while it passes
     * too (a finished mine it still holds is skipped); otherwise the first one without a real holder is taken.
     */
    public static WorkplaceState claim(FactionNpc npc, WorkplaceState.Type type, Predicate<WorkplaceState> ok) {
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
                if (ok.test(w)) {
                    return w;
                }
                continue;
            }
            if (free == null && ok.test(w) && !holderValid(data, w)) {
                free = w;
            }
        }
        if (free != null) {
            free.assigned = npc.getUUID();
            data.setDirty();
        }
        return free;
    }

    /** Hands a workplace back: nobody works it until the next claim. */
    public static void release(CampaignData data, WorkplaceState w) {
        if (w.assigned != null) {
            w.assigned = null;
            data.setDirty();
        }
    }

    /** Hands back every workplace assigned to this NPC, for example when it takes up another job. */
    public static void releaseAll(CampaignData data, UUID uuid) {
        if (uuid == null) {
            return;
        }
        for (WorkplaceState w : data.workplaces.values()) {
            if (uuid.equals(w.assigned)) {
                release(data, w);
            }
        }
    }
}
