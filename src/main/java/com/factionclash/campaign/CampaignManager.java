package com.factionclash.campaign;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import com.factionclash.util.ChunkLoading;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Campaign lifecycle and the PROPOSED victory rules: a base falls when its faction heart is destroyed; a faction
 * (human or The Hollow) is eliminated when it has no bases left; the last surviving faction wins.
 */
public final class CampaignManager {
    private static CampaignStarter starter;
    private static boolean destroying;

    /** Something for an entity that joined a level, done at the next server tick ({@link #afterJoin}). */
    private record AfterJoin(Entity entity, java.util.function.Consumer<Entity> then) {
    }

    private static final List<AfterJoin> AFTER_JOIN = new ArrayList<>();

    private CampaignManager() {
    }

    public static boolean starting() {
        return starter != null && !starter.done();
    }

    public static void start(MinecraftServer server, int radius) {
        starter = new CampaignStarter(server, radius);
    }

    public static void tick(MinecraftServer server) {
        runAfterJoin(server); // (first: with or without a campaign, and while one is being prepared)
        if (starter != null) {
            if (starter.step()) {
                starter = null;
            }
            return;
        }
        CampaignData data = CampaignData.get(server);
        if (!data.started) {
            return;
        }
        long t = server.overworld().getGameTime();
        FactionDirector.tick(server, data, t);
        if (t % 100 == 0 && data.winner == null) {
            checkHearts(server.overworld(), data);
            checkVictory(server, data);
        }
    }

    private static void checkHearts(ServerLevel level, CampaignData data) {
        for (BaseState b : List.copyOf(data.bases.values())) {
            BlockPos heart = b.point(BaseState.HEART);
            if (!b.destroyed && heart != null && level.isLoaded(heart)
                    && !level.getBlockState(heart).is(com.factionclash.registry.ModBlocks.FACTION_HEART.get())) {
                destroyBase(level, b, null, "unknown");
            }
        }
    }

    public static void onHeartRemoved(ServerLevel level, BlockPos pos) {
        if (destroying || level.getServer() == null) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        for (BaseState b : List.copyOf(data.bases.values())) {
            if (!b.destroyed && pos.equals(b.point(BaseState.HEART))) {
                destroyBase(level, b, null, "unknown");
            }
        }
    }

    public static void destroyBase(ServerLevel level, BaseState base, FactionType by, String byName) {
        if (base.destroyed) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        base.destroyed = true;
        base.destroyedAt = level.getGameTime();
        base.destroyedBy = by;
        // the faction's last base: everyone of it dies with it (eliminate, below), so no hero of it runs from this fall
        boolean last = data.livingBasesOf(base.faction).isEmpty();
        if (!last) {
            // its heroes: an escaping hero goes at once, a last-stand one finishes its fight (HEROES_V2 6.13, task A6b)
            com.factionclash.hero.HeroBossEvents.baseLost(level, base);
        }
        BlockPos heart = base.point(BaseState.HEART);
        destroying = true;
        try {
            if (heart != null && level.getBlockState(heart).is(com.factionclash.registry.ModBlocks.FACTION_HEART.get())) {
                level.destroyBlock(heart, false);
                level.setBlock(heart, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            destroying = false;
        }
        ChunkLoading.forceBase(level, base, false);
        if (by != null) {
            data.faction(by).basesDestroyed++;
        }
        data.setDirty();
        Messages.toAll(level.getServer(), Component.literal(base.faction.displayName + (base.isSettlement() ? " settlement" : " lair")
                + " at " + base.center.toShortString() + " has fallen" + (by == null ? "" : " to " + by.displayName + " (" + byName + ")"))
                .withStyle(ChatFormatting.GOLD));
        if (last) {
            eliminate(level, data, base.faction);
        }
    }

    /**
     * The faction has no base left: it is eliminated, and every member of it dies with it (the owner's rule of 2026-11-25,
     * {@code logic/campaign/Elimination}). Members in loaded places die now, where they stand, their things dropping as on
     * any death (an NPC's death is no chat line; a hero's is the kill feed's); a member away in an unloaded place dies when
     * it next loads ({@link #diesWithFaction}, from {@code FactionNpc.tick}; a Hollow monster, {@link #lairMonsterJoined});
     * its heroes are freed for good
     * ({@code HeroManager.factionFell}); a member player dies now, or at its next login ({@link #loggedIn}), and is
     * factionless after. Nothing of the faction acts again: its bases are all fallen, so the director runs no recruitment,
     * council, raid or work for it.
     */
    public static void eliminate(ServerLevel level, CampaignData data, FactionType faction) {
        FactionState f = data.faction(faction);
        if (!f.alive) {
            return;
        }
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        // stamped first: every death below (and any later one) already reads the faction as fallen
        f.alive = false;
        f.eliminatedAt = com.factionclash.logic.campaign.Elimination.stamp(now);
        f.raidTarget = -1;
        f.raiders.clear();
        f.raidEndNews = null;
        f.news.clear(); // nobody is left at home to be told
        data.setDirty();
        Messages.toAll(server, Component.literal(faction.displayName + " has been eliminated! All of its people die with it.")
                .withStyle(ChatFormatting.RED));
        List<Entity> remove = new ArrayList<>();
        for (ServerLevel lv : server.getAllLevels()) { // (a member beyond a portal too)
            for (Entity e : lv.getAllEntities()) {
                if (e instanceof FactionNpc n && n.getFaction() == faction && n.isAlive() && !n.isDisplayOnly()) {
                    remove.add(e);
                } else if (faction == FactionType.HOLLOW && lairMonster(e) && e.isAlive()) {
                    // every monster of the Hollow, by its lair's mark: one on no lair's list any more too (it walked off
                    // on a raid, or its lair stopped tracking it)
                    remove.add(e);
                }
            }
        }
        for (BaseState b : data.basesOf(faction)) {
            b.lairMobs.clear();
        }
        for (Entity e : remove) {
            killOutright(e);
        }
        com.factionclash.hero.HeroManager.factionFell(server, data, faction); // its heroes away, or waiting to rise
        for (net.minecraft.server.level.ServerPlayer p : membersOnline(server, data, faction)) {
            data.players.remove(p.getUUID()); // factionless from now: it respawns by vanilla's rules and may join another
            killOutright(p);
        }
        RollCall.factionFell(data, f, now); // every member known dead, the ones still away in unloaded places too
        data.setDirty();
        checkVictory(server, data);
    }

    /** The online players who are members of {@code faction}, in every dimension (a GameTest's mock player too). */
    private static List<net.minecraft.server.level.ServerPlayer> membersOnline(MinecraftServer server, CampaignData data, FactionType faction) {
        List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>();
        for (ServerLevel lv : server.getAllLevels()) {
            for (net.minecraft.server.level.ServerPlayer p : lv.players()) {
                if (data.players.get(p.getUUID()) == faction && !out.contains(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /**
     * A death with its faction: vanilla's {@code kill} (a generic kill that no armour, shield, barrier or invulnerability
     * stops), and if something still held it up, its health to nothing and its death all the same.
     */
    private static void killOutright(Entity e) {
        e.kill();
        if (e instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive()) {
            l.setHealth(0);
            l.die(l.damageSources().genericKill());
        }
    }

    /** A monster of a Hollow lair (marked by its lair, {@code BaseBuilder.LAIR_TAG}). */
    public static boolean lairMonster(Entity e) {
        return e instanceof net.minecraft.world.entity.Mob && !(e instanceof FactionNpc)
                && e.getPersistentData().contains(BaseBuilder.LAIR_TAG);
    }

    /**
     * A Hollow monster joined a level (its chunk loaded) after the Hollow fell: it was away in an unloaded place at the
     * fall, and dies where it stands at the next tick, its things dropping as at any death (vanilla monsters have no
     * {@code FactionNpc.tick} of their own to read the fall).
     */
    public static void lairMonsterJoined(net.minecraft.world.entity.Mob mob, CampaignData data) {
        if (!fallen(data, FactionType.HOLLOW)) {
            return;
        }
        afterJoin(mob, e -> {
            if (e.isAlive() && !e.isRemoved() && e.getServer() != null && fallen(CampaignData.get(e.getServer()), FactionType.HOLLOW)) {
                FactionClash.LOGGER.info("[Faction Clash] A monster of the fallen Hollow dies where it stands at {}", e.blockPosition().toShortString());
                killOutright(e);
            }
        });
    }

    /**
     * Runs {@code then} for an entity that is joining a level, at the next server tick: an entity that joins with its
     * chunk must not touch the level from its join event (the chunk may not be ready, and loading it from there can
     * deadlock), so a death or a drop it is due waits a tick. Server thread only.
     */
    public static void afterJoin(Entity e, java.util.function.Consumer<Entity> then) {
        AFTER_JOIN.add(new AfterJoin(e, then));
    }

    private static void runAfterJoin(MinecraftServer server) {
        if (AFTER_JOIN.isEmpty()) {
            return;
        }
        List<AfterJoin> due = List.copyOf(AFTER_JOIN);
        AFTER_JOIN.clear();
        for (AfterJoin a : due) {
            if (a.entity().getServer() == server) { // (not one left over from a world closed before this one opened)
                a.then().accept(a.entity());
            }
        }
    }

    /** Whether {@code faction} has fallen in this campaign ({@link com.factionclash.logic.campaign.Elimination#fallen}). */
    public static boolean fallen(CampaignData data, FactionType faction) {
        FactionState f = faction == null ? null : data.factions.get(faction); // (never creates a faction's state)
        return f != null && com.factionclash.logic.campaign.Elimination.fallen(f.alive, f.eliminatedAt);
    }

    /**
     * A member that was in an unloaded place when its faction fell, now loaded and ticking (an NPC or hero away on a
     * trip, a raider, a far miner): it dies where it stands, as its faction's people did, its things dropping. Returns
     * whether it died. Read by {@code FactionNpc.tick} on its first tick and once a second after.
     */
    public static boolean diesWithFaction(FactionNpc npc) {
        if (npc.isDisplayOnly() || !npc.isAlive() || npc.getServer() == null
                || !fallen(CampaignData.get(npc.getServer()), npc.getFaction())) {
            return false;
        }
        FactionClash.LOGGER.info("[Faction Clash] {} of the fallen {} dies where it stands at {}",
                npc.npcName().isEmpty() ? npc.getUUID() : npc.npcName(), npc.getFaction().displayName, npc.blockPosition().toShortString());
        killOutright(npc);
        return true;
    }

    /**
     * A player logs in (single-player: the world loads): a member of a faction that fell while it was away dies now, as
     * its faction's people did, and is factionless from then on.
     */
    public static void loggedIn(net.minecraft.server.level.ServerPlayer p) {
        CampaignData data = CampaignData.get(p.server);
        FactionType f = data.players.get(p.getUUID());
        if (f == null || !fallen(data, f)) {
            return;
        }
        data.players.remove(p.getUUID());
        data.setDirty();
        p.sendSystemMessage(Component.literal(f.displayName + " fell while you were away. You die with it.").withStyle(ChatFormatting.RED));
        killOutright(p);
    }

    public static List<FactionType> survivors(CampaignData data) {
        List<FactionType> out = new ArrayList<>();
        for (FactionType t : FactionType.values()) {
            if (!data.basesOf(t).isEmpty() && !data.livingBasesOf(t).isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static void checkVictory(MinecraftServer server, CampaignData data) {
        if (!data.started || data.winner != null) {
            return;
        }
        List<FactionType> alive = survivors(data);
        if (alive.size() == 1) {
            data.winner = alive.get(0);
            data.finishedAt = server.overworld().getGameTime();
            data.setDirty();
            Messages.toAll(server, Component.literal("*** " + data.winner.displayName + " is the last faction standing and wins the campaign! ***")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            FactionClash.LOGGER.info("Campaign winner: {}", data.winner.displayName);
        }
    }
}
