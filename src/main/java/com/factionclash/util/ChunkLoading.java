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
 * home carry a ticking 3x3 chunk ticket with them (a hero in flight a 5x5 one, MOBILITY_AND_WORK 3.5). Nothing else is force-loaded, so the map is not kept loaded as a
 * whole.
 */
public final class ChunkLoading {
    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "faction_areas"),
            (level, helper) -> {
                CampaignData data = CampaignData.get(level.getServer());
                // Drop traveller tickets of NPCs that are truly gone (dead or lost; not merely presumed dead by their
                // faction, who may be alive far away and walking home), and old non-ticking ones
                // (they kept chunks loaded without updating the NPCs in them); living NPCs away from home get a
                // ticking area again when the server has started (see restoreTravellers).
                for (Map.Entry<UUID, net.neoforged.neoforge.common.world.chunk.TicketSet> e : Map.copyOf(helper.getEntityTickets()).entrySet()) {
                    UUID owner = e.getKey();
                    NpcRecord r = data.record(owner);
                    boolean lairMob = data.bases.values().stream().anyMatch(b -> b.lairMobs.contains(owner));
                    if ((r == null || r.gone()) && !lairMob) {
                        helper.removeAllTickets(owner);
                    } else {
                        for (long chunk : e.getValue().nonTicking().toLongArray()) {
                            helper.removeTicket(owner, chunk, false);
                        }
                    }
                }
            });

    private static final Map<UUID, Long> TRAVELLER_CHUNK = new HashMap<>();
    /** The radius of each traveller's area when it is not 1 (a hero in flight: 2, a 5x5 area). */
    private static final Map<UUID, Integer> TRAVELLER_RADIUS = new HashMap<>();

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
        UUID id = mob.getUUID();
        Long prev = TRAVELLER_CHUNK.get(id);
        int prevRadius = TRAVELLER_RADIUS.getOrDefault(id, 1);
        int radius = mob instanceof FactionNpc npc ? Math.max(1, npc.travelTicketRadius()) : 1;
        if (!away || !enabled()) {
            if (prev != null) {
                setArea(level, id, prev, prevRadius, false);
                TRAVELLER_CHUNK.remove(id);
                TRAVELLER_RADIUS.remove(id);
            }
            return;
        }
        if (prev != null && prev == chunk && prevRadius == radius) {
            return;
        }
        setArea(level, id, chunk, radius, true);
        if (prev != null) {
            // release the old area after the new one is held, keeping overlap loaded
            ChunkPos o = new ChunkPos(prev), n = new ChunkPos(chunk);
            for (int dx = -prevRadius; dx <= prevRadius; dx++) {
                for (int dz = -prevRadius; dz <= prevRadius; dz++) {
                    int x = o.x + dx, z = o.z + dz;
                    if (Math.abs(x - n.x) > radius || Math.abs(z - n.z) > radius) {
                        CONTROLLER.forceChunk(level, id, x, z, false, true);
                    }
                }
            }
        }
        TRAVELLER_CHUNK.put(id, chunk);
        if (radius == 1) {
            TRAVELLER_RADIUS.remove(id);
        } else {
            TRAVELLER_RADIUS.put(id, radius);
        }
    }

    /** The chunk radius of a traveller's ticking area now (0: it holds none; tests). */
    public static int travellerRadius(UUID id) {
        return TRAVELLER_CHUNK.containsKey(id) ? TRAVELLER_RADIUS.getOrDefault(id, 1) : 0;
    }

    public static void releaseTraveler(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            Long prev = TRAVELLER_CHUNK.remove(mob.getUUID());
            int r = TRAVELLER_RADIUS.getOrDefault(mob.getUUID(), 1);
            TRAVELLER_RADIUS.remove(mob.getUUID());
            if (prev != null) {
                setArea(level, mob.getUUID(), prev, r, false);
            }
        }
    }

    private static void setArea(ServerLevel level, UUID owner, long chunk, boolean add) {
        setArea(level, owner, chunk, 1, add);
    }

    private static void setArea(ServerLevel level, UUID owner, long chunk, int radius, boolean add) {
        ChunkPos c = new ChunkPos(chunk);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // ticking: the NPC (and what it works on) must actually be updated, not just kept in memory
                CONTROLLER.forceChunk(level, owner, c.x + dx, c.z + dz, add, true);
            }
        }
    }

    /**
     * After a restart, living NPCs whose last known position is away from their base get their 3x3 ticking area
     * back, so they wake up and carry on (an unloaded or frozen NPC could never re-register it itself).
     */
    public static void restoreTravellers(ServerLevel level) {
        if (!enabled()) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        for (com.factionclash.campaign.FactionState f : data.factions.values()) {
            for (NpcRecord r : f.roster.values()) {
                if (r.gone() || r.lastPos == null) { // (a member presumed dead but alive away keeps its area)
                    continue;
                }
                BaseState home = data.base(r.baseId);
                if (home != null && !home.destroyed && home.deepInside(r.lastPos)) {
                    continue;
                }
                long chunk = ChunkPos.asLong(r.lastPos);
                setArea(level, r.uuid, chunk, true);
                TRAVELLER_CHUNK.put(r.uuid, chunk);
            }
        }
    }

    public static int travellerCount() {
        return TRAVELLER_CHUNK.size();
    }
}
