package com.factionclash.util;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.config.FCConfig;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded background simulation (spec 20): base areas keep ticking chunk tickets, and NPCs travelling away from
 * home carry a 3x3 chunk ticket with them. Nothing else is force-loaded, so the map is not kept loaded as a whole.
 */
public final class ChunkLoading {
    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "faction_areas"),
            (level, helper) -> {
                CampaignData data = CampaignData.get(level.getServer());
                // Drop traveller tickets of NPCs that are no longer alive in the roster.
                for (UUID owner : Map.copyOf(helper.getEntityTickets()).keySet()) {
                    NpcRecord r = data.record(owner);
                    boolean lairMob = data.bases.values().stream().anyMatch(b -> b.lairMobs.contains(owner));
                    if ((r == null || !r.alive) && !lairMob) {
                        helper.removeAllTickets(owner);
                    }
                }
            });

    private static final Map<UUID, Long> TRAVELLER_CHUNK = new HashMap<>();

    private ChunkLoading() {
    }

    public static boolean enabled() {
        return FCConfig.KEEP_FACTION_AREAS_LOADED.get();
    }

    public static void forceBase(ServerLevel level, BaseState base, boolean add) {
        BlockPos owner = base.point(BaseState.HEART);
        BlockPos min = base.point(BaseState.MIN);
        BlockPos max = base.point(BaseState.MAX);
        if (owner == null || min == null || max == null) {
            return;
        }
        boolean on = add && enabled();
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                CONTROLLER.forceChunk(level, owner, cx, cz, on, true);
            }
        }
    }

    /** Keeps the 3x3 chunks around an NPC (or Hollow mob) ticking while it is away from its base area. */
    public static void updateTraveler(Mob mob) {
        boolean away = true;
        if (mob instanceof FactionNpc npc) {
            BaseState base = npc.base();
            // NPCs without a home base (e.g. spawned by tests or admins) never hold chunk tickets
            away = base != null && !base.destroyed && !base.deepInside(npc.blockPosition());
        }
        updateTraveler(mob, away);
    }

    public static void updateTraveler(Mob mob, boolean away) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        long chunk = ChunkPos.asLong(mob.blockPosition());
        Long prev = TRAVELLER_CHUNK.get(mob.getUUID());
        if (!away || !enabled()) {
            if (prev != null) {
                setArea(level, mob.getUUID(), prev, false);
                TRAVELLER_CHUNK.remove(mob.getUUID());
            }
            return;
        }
        if (prev != null && prev == chunk) {
            return;
        }
        setArea(level, mob.getUUID(), chunk, true);
        if (prev != null) {
            // release the old area after the new one is held, keeping overlap loaded
            ChunkPos o = new ChunkPos(prev), n = new ChunkPos(chunk);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = o.x + dx, z = o.z + dz;
                    if (Math.abs(x - n.x) > 1 || Math.abs(z - n.z) > 1) {
                        CONTROLLER.forceChunk(level, mob.getUUID(), x, z, false, false);
                    }
                }
            }
        }
        TRAVELLER_CHUNK.put(mob.getUUID(), chunk);
    }

    public static void releaseTraveler(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            Long prev = TRAVELLER_CHUNK.remove(mob.getUUID());
            if (prev != null) {
                setArea(level, mob.getUUID(), prev, false);
            }
        }
    }

    private static void setArea(ServerLevel level, UUID owner, long chunk, boolean add) {
        ChunkPos c = new ChunkPos(chunk);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                CONTROLLER.forceChunk(level, owner, c.x + dx, c.z + dz, add, false);
            }
        }
    }

    public static int travellerCount() {
        return TRAVELLER_CHUNK.size();
    }
}
