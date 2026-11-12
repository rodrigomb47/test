package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Recruitment;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.hero.HeroRevival;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Heroes in the campaign (HEROES_V2 3.4, v1 25): giving a hero to a faction, releasing it, its death and its return
 * after {@code heroes.reviveSeconds}, and the rule that a hero id has one living entity. Its state lives in
 * {@link CampaignData#heroes}; the hero itself is an ordinary roster member (its {@link NpcRecord} carries the hero
 * id), so it counts as one soldier of its role for the director's needs, garrison and war council.
 */
public final class HeroManager {
    /** Checks once a second. */
    public static final int TICK_INTERVAL = 20;

    private HeroManager() {
    }

    /** The outcome of a command: whether it did what was asked, the hero standing in the world (if any), what to say. */
    public record Result(boolean ok, HeroEntity hero, String message) {
        static Result done(HeroEntity hero, String message) {
            return new Result(true, hero, message);
        }

        static Result failed(HeroEntity hero, String message) {
            return new Result(false, hero, message);
        }
    }

    // ------------------------------------------------------------------ give, release, revive

    /**
     * The hero joins {@code faction} now, at {@code at} (null: its base's spawn point beside the reporting station),
     * with its kit; a hero serving another faction (or this one) is released from it first. Its home is the
     * faction's first standing base, or {@code base} when given (a test base).
     */
    public static Result give(MinecraftServer server, String id, FactionType faction, BaseState base, BlockPos at) {
        HeroEntry entry = HeroRegistry.get(id);
        if (entry == null) {
            return Result.failed(null, "No hero \"" + id + "\". /fc hero list shows them.");
        }
        if (entry.previewOnly()) {
            return Result.failed(null, entry.name() + " is a preview fixture: it can be lined up and posed, never given to a faction.");
        }
        CampaignData data = CampaignData.get(server);
        if (base == null) {
            List<BaseState> bases = faction == null ? List.of() : data.livingBasesOf(faction);
            if (bases.isEmpty()) {
                return Result.failed(null, (faction == null ? "That faction" : faction.displayName) + " has no standing base for a hero to live at.");
            }
            base = bases.get(0);
        }
        HeroState s = data.heroes.computeIfAbsent(id, k -> {
            HeroState n = new HeroState();
            n.id = k;
            return n;
        });
        if (s.owner != null) {
            release(server, s, s.owner == faction ? "is called to the station" : "leaves to serve the " + faction.displayName);
        }
        HeroEntity hero = spawn(server.overworld(), data, s, entry, faction, base, at == null ? spawnPoint(base) : at);
        Messages.toFaction(server, faction, Component.literal(entry.name() + " joins the " + faction.displayName + "!")
                .withStyle(ChatFormatting.GOLD));
        return Result.done(hero, entry.name() + " joins the " + faction.displayName + " at " + base.name + " ("
                + hero.blockPosition().toShortString() + ")");
    }

    /** Frees the hero: its entity leaves the world, its roster entry goes, it keeps its level for the next owner. */
    public static Result release(MinecraftServer server, String id) {
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.get(id);
        if (s == null || s.owner == null) {
            return Result.failed(null, "\"" + id + "\" serves no faction.");
        }
        String name = nameOf(id);
        FactionType was = s.owner;
        release(server, s, "is released");
        return Result.done(null, name + " no longer serves the " + was.displayName + ".");
    }

    private static void release(MinecraftServer server, HeroState s, String why) {
        CampaignData data = CampaignData.get(server);
        FactionType was = s.owner;
        if (s.uuid != null) {
            Entity e = server.overworld().getEntity(s.uuid);
            if (e instanceof HeroEntity h) {
                snapshot(s, h);
                h.discard();
            }
            if (was != null) {
                data.faction(was).roster.remove(s.uuid);
            }
        }
        s.owner = null;
        s.baseId = -1;
        s.alive = false;
        s.reviveAt = 0;
        data.setDirty();
        if (was != null) {
            Messages.toFaction(server, was, Component.literal(nameOf(s.id) + " " + why + "."));
        }
    }

    /** A new campaign: every hero is released, and the loaded hero entities leave the world. */
    public static void releaseAll(MinecraftServer server, String why) {
        CampaignData data = CampaignData.get(server);
        for (HeroState s : data.heroes.values()) {
            if (s.owner != null) {
                release(server, s, why);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (HeroEntity h : level.getEntities(ModEntities.HERO.get(), e -> true)) {
                h.discard();
            }
        }
    }

    /** Brings a fallen hero back now (an operator's command; the timer does the same when it is due). */
    public static Result revive(MinecraftServer server, String id) {
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.get(id);
        if (s == null || s.owner == null) {
            return Result.failed(null, "\"" + id + "\" serves no faction; give it to one with /fc admin hero give.");
        }
        if (s.alive) {
            Entity e = s.uuid == null ? null : server.overworld().getEntity(s.uuid);
            return Result.failed(e instanceof HeroEntity h ? h : null, nameOf(id) + " is alive" + (e == null ? " (in an unloaded area)" : "") + ".");
        }
        FactionType owner = s.owner;
        HeroEntity h = returnHome(server, data, s);
        return h == null ? Result.failed(null, nameOf(id) + " could not return: the " + owner.displayName
                + " have no standing base, so the hero is free again.") : Result.done(h, nameOf(id) + " returns at " + h.blockPosition().toShortString() + ".");
    }

    /**
     * A fallen hero comes back at its home base's spawn point (or the faction's first standing base), at full health,
     * with its kit, level and traits: a respawn like a player's at a bed, never a teleport of a living NPC. A faction
     * with no standing base loses the hero (it is released).
     */
    private static HeroEntity returnHome(MinecraftServer server, CampaignData data, HeroState s) {
        BaseState base = data.base(s.baseId);
        if (base == null || base.destroyed || base.faction != s.owner) {
            List<BaseState> bases = data.livingBasesOf(s.owner);
            base = bases.isEmpty() ? null : bases.get(0);
        }
        HeroEntry entry = HeroRegistry.getOrFallback(s.id);
        if (base == null) {
            release(server, s, "has no standing base to return to and leaves");
            return null;
        }
        HeroEntity h = spawn(server.overworld(), data, s, entry, s.owner, base, spawnPoint(base));
        Messages.toFaction(server, s.owner, Component.literal(h.npcName() + " returns to " + base.name + ".").withStyle(ChatFormatting.GOLD));
        return h;
    }

    /**
     * Puts the hero in the world for {@code faction}: a fresh entity with a new UUID (so any stale copy of an older
     * one is told apart), its id, role, name, level and traits, its kit, a roster entry; the state records it before it
     * joins the level, which is what the one-entity-per-id check reads.
     */
    private static HeroEntity spawn(ServerLevel level, CampaignData data, HeroState s, HeroEntry entry, FactionType faction,
                                    BaseState base, BlockPos at) {
        if (s.uuid != null) {
            for (FactionState f : data.factions.values()) {
                f.roster.remove(s.uuid); // the fallen or released entity's entry
            }
            Entity old = level.getEntity(s.uuid);
            if (old != null) {
                old.discard(); // a corpse still in its death animation
            }
        }
        HeroEntity h = ModEntities.HERO.get().create(level);
        if (h == null) {
            throw new IllegalStateException("the hero entity type could not create an entity");
        }
        h.setHeroId(s.id);
        h.faction(faction);
        NpcRole role = entry.role();
        h.role(role);
        h.setBaseId(base.id);
        h.setNpcName(entry.name());
        h.restoreProgress(s.level, s.xp, s.affixes);
        ItemStack weapon = HeroKit.weapon(role, s.id);
        if (!weapon.isEmpty()) {
            h.equip(weapon);
        }
        if (role.ranged) {
            h.setAmmo(new ItemStack(Items.ARROW));
        }
        h.setHealth(h.getMaxHealth());
        h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        s.owner = faction;
        s.baseId = base.id;
        s.uuid = h.getUUID();
        s.alive = true;
        s.reviveAt = 0;
        data.setDirty();
        level.addFreshEntity(h);
        Recruitment.register(data, h, base);
        NpcRecord r = data.faction(faction).roster.get(h.getUUID());
        if (r != null) {
            r.heroId = s.id;
            r.level = s.level;
        }
        return h;
    }

    private static BlockPos spawnPoint(BaseState base) {
        BlockPos p = base.point(BaseState.SPAWN);
        return p != null ? p : base.center;
    }

    // ------------------------------------------------------------------ death, uniqueness, the timer

    /** The hero fell: it returns after {@code heroes.reviveSeconds}; its kit vanished with it (HeroEntity). */
    public static void onDeath(MinecraftServer server, HeroEntity h) {
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.get(h.heroId());
        if (s == null || !s.alive || !h.getUUID().equals(s.uuid)) {
            return;
        }
        fallen(server, data, s, h, "has fallen at " + h.blockPosition().getX() + ", " + h.blockPosition().getZ());
    }

    private static void fallen(MinecraftServer server, CampaignData data, HeroState s, HeroEntity h, String what) {
        if (h != null) {
            snapshot(s, h);
        }
        long now = server.overworld().getGameTime();
        s.alive = false;
        s.deaths++;
        s.reviveAt = HeroRevival.reviveAt(now, FCConfig.HERO_REVIVE_SECONDS.get());
        data.setDirty();
        if (s.owner != null) {
            Messages.toFaction(server, s.owner, Component.literal(nameOf(s.id) + " " + what + " and returns in "
                    + HeroRevival.countdown(s.reviveAt - now) + ".").withStyle(ChatFormatting.RED));
        }
    }

    /** Level, experience and traits outlive the entity. */
    private static void snapshot(HeroState s, HeroEntity h) {
        s.level = h.npcLevel();
        s.xp = h.xp();
        s.affixes.clear();
        s.affixes.putAll(h.affixes());
    }

    /**
     * Whether a hero entity may join the level (HEROES_V2 3.4, v1 25.1): the one the campaign records may; any other
     * entity with the same id is a stale copy (from an old chunk, a copied world, a released or revived hero) and is
     * discarded. A hero entity the campaign has no record of (a world copied without its data) is adopted.
     */
    public static boolean mayJoin(MinecraftServer server, HeroEntity h) {
        String id = h.heroId();
        if (id.isEmpty() || h.isPreview()) {
            return true; // a display copy is never the campaign's hero
        }
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.get(id);
        if (s == null) {
            s = new HeroState();
            s.id = id;
            s.owner = h.getFaction();
            s.baseId = h.baseId();
            s.uuid = h.getUUID();
            s.alive = true;
            snapshot(s, h);
            data.heroes.put(id, s);
            BaseState base = data.base(h.baseId());
            NpcRecord r = data.faction(s.owner).roster.get(h.getUUID());
            if (r == null && base != null && base.faction == s.owner) {
                Recruitment.register(data, h, base);
                r = data.faction(s.owner).roster.get(h.getUUID());
            }
            if (r != null) {
                r.heroId = id;
            }
            data.setDirty();
            FactionClash.LOGGER.info("Hero {} ({}) had no campaign record and is adopted by the {}", id, h.getUUID(), s.owner.displayName);
            return true;
        }
        if (s.owner != null && h.getUUID().equals(s.uuid)) {
            if (!s.alive) {
                // presumed lost while its chunk was away, and back before its timer ran out
                s.alive = true;
                s.reviveAt = 0;
                NpcRecord r = data.faction(s.owner).roster.get(s.uuid);
                if (r != null) {
                    r.alive = true;
                }
                data.setDirty();
            }
            return true;
        }
        FactionClash.LOGGER.info("Discarding a stale copy of hero {} ({}); the hero is {}", id, h.getUUID(),
                s.owner == null ? "free" : s.uuid + " of the " + s.owner.displayName);
        return false;
    }

    /**
     * Once a second: a fallen hero whose time has come returns; a hero whose roster entry the director found missing
     * (its entity gone from a loaded area for two minutes) counts as fallen; a living hero's level and traits are
     * copied into the campaign record.
     */
    public static void tick(MinecraftServer server) {
        CampaignData data = CampaignData.get(server);
        if (data.heroes.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (HeroState s : new ArrayList<>(data.heroes.values())) {
            if (s.owner == null) {
                continue;
            }
            if (s.alive) {
                NpcRecord r = s.uuid == null ? null : data.faction(s.owner).roster.get(s.uuid);
                Entity e = s.uuid == null ? null : server.overworld().getEntity(s.uuid);
                if (e instanceof HeroEntity h && h.isAlive()) {
                    if (h.npcLevel() != s.level || h.xp() != s.xp) {
                        snapshot(s, h);
                        data.setDirty();
                    }
                } else if (r != null && !r.alive) {
                    fallen(server, data, s, null, "is missing and presumed lost");
                }
            } else if (HeroRevival.due(now, s.reviveAt)) {
                returnHome(server, data, s);
            }
        }
    }

    // ------------------------------------------------------------------ previews (screenshots)

    /**
     * Stands a display copy of a hero at {@code at} facing {@code yaw}, sashed in {@code tint}'s colour (HEROES_V2 15.2
     * A2, {@code /fc admin hero preview|lineup}): any hero the registry knows, preview fixtures included. It is not the
     * campaign's hero: no roster entry, no job, no AI, cannot be hurt, never counted.
     */
    public static Result preview(ServerLevel level, String id, FactionType tint, BlockPos at, float yaw) {
        return preview(level, id, tint, net.minecraft.world.phys.Vec3.atBottomCenterOf(at), yaw);
    }

    /** {@link #preview(ServerLevel, String, FactionType, BlockPos, float)} at an exact position (a lineup's even spacing). */
    public static Result preview(ServerLevel level, String id, FactionType tint, net.minecraft.world.phys.Vec3 at, float yaw) {
        HeroEntry entry = HeroRegistry.get(id);
        if (entry == null) {
            return Result.failed(null, "No hero \"" + id + "\". /fc hero list shows them.");
        }
        HeroEntity h = ModEntities.HERO.get().create(level);
        if (h == null) {
            return Result.failed(null, "the hero entity type could not create an entity");
        }
        h.setHeroId(id);
        h.faction(tint);
        h.role(entry.role());
        h.setNpcName(entry.name());
        h.makePreview();
        ItemStack weapon = HeroKit.weapon(entry.role(), id);
        if (!weapon.isEmpty()) {
            h.equip(weapon);
        }
        h.moveTo(at.x, at.y, at.z, yaw, 0F);
        h.setYHeadRot(yaw);
        h.setYBodyRot(yaw);
        level.addFreshEntity(h);
        return Result.done(h, entry.name() + " (preview) at " + h.blockPosition().toShortString());
    }

    /** Every loaded entity of a hero id in the level: the campaign's hero and its previews ({@code id} null: every hero). */
    public static List<HeroEntity> loaded(ServerLevel level, String id) {
        List<HeroEntity> out = new ArrayList<>();
        for (HeroEntity h : level.getEntities(ModEntities.HERO.get(), e -> id == null || id.equals(e.heroId()))) {
            out.add(h);
        }
        return out;
    }

    /** Removes the preview copies of a hero ({@code id} null: of every hero); returns how many. */
    public static int clearPreviews(ServerLevel level, String id) {
        int n = 0;
        for (HeroEntity h : loaded(level, id)) {
            if (h.isPreview()) {
                h.discard();
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ reading

    /** The hero's name: its registry entry's, or one made from the id for a hero the registry no longer knows. */
    public static String nameOf(String id) {
        HeroEntry e = HeroRegistry.get(id);
        return e != null ? e.name() : com.factionclash.logic.hero.HeroIdentity.nameFromId(id);
    }

    /** The hero's loaded entity, or null. */
    public static HeroEntity entity(MinecraftServer server, String id) {
        HeroState s = CampaignData.get(server).heroes.get(id);
        if (s == null || s.uuid == null || !s.alive) {
            return null;
        }
        return server.overworld().getEntity(s.uuid) instanceof HeroEntity h ? h : null;
    }

    /** Removes every trace of a hero (tests): its entity, its roster entry and its campaign record. */
    public static void forget(MinecraftServer server, String id) {
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.remove(id);
        if (s != null && s.uuid != null) {
            Entity e = server.overworld().getEntity(s.uuid);
            if (e != null) {
                e.discard();
            }
            for (FactionState f : data.factions.values()) {
                f.roster.remove(s.uuid);
            }
        }
        data.setDirty();
    }
}
