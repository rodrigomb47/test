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
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroRevival;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Heroes in the campaign (HEROES_V2 3.4, v1 25): giving a hero to a faction, releasing it, its death and its return
 * after {@code heroes.reviveSeconds} (or its own revival block, shortened by the {@code revival_rite} talent, never
 * under {@code heroes.minReviveSeconds}), and the rule that a hero id has one living entity. Its state lives in
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
        if (entry.dormant()) {
            return Result.failed(null, entry.name() + " has no valid definition (/fc hero check " + id + " says why), so it cannot join a faction.");
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
        hero.say(LineEvent.SUMMONED);
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
        }
        free(data, s);
        if (was != null) {
            Messages.toFaction(server, was, Component.literal(nameOf(s.id) + " " + why + "."));
        }
    }

    /**
     * The hero serves no faction from now (released, or its faction fell): its roster entry goes, it keeps its level,
     * experience and traits for whoever takes it next (a hero of a fallen faction may be recruited again by a later
     * milestone), and it has no revival pending. Any entity copy of it that loads later is a stale one ({@link #mayJoin}).
     */
    private static void free(CampaignData data, HeroState s) {
        if (s.owner != null && s.uuid != null) {
            data.faction(s.owner).roster.remove(s.uuid);
        }
        s.owner = null;
        s.baseId = -1;
        s.alive = false;
        s.reviveAt = 0;
        s.presumedLost = false;
        data.setDirty();
    }

    // ------------------------------------------------------------------ a faction's fall

    /**
     * Its faction has fallen ({@code logic/campaign/Elimination}): every hero of it dies with it and never comes back
     * (no base to rise at); each is a free hero from then on, its level kept. A hero alive in a loaded place is killed
     * there (its own death is the kill feed's line and frees it, {@link #onDeath}); one alive away in an unloaded place
     * dies unseen, with one kill-feed line now (what it carried drops where it stood when its chunk loads, {@link #mayJoin});
     * one already dead and waiting to rise is freed with nothing more told. A hero presumed lost (its entity went missing:
     * no death was told, and it may be alive where it is) counts as alive here: it dies with its faction, told once.
     * Called at the elimination, and from {@link #tick} for a save in which a faction fell before this rule.
     */
    public static void factionFell(MinecraftServer server, CampaignData data, FactionType faction) {
        for (HeroState s : new ArrayList<>(data.heroes.values())) {
            if (s.owner == faction) {
                fellWithFaction(server, data, s);
            }
        }
    }

    /** One hero of a fallen faction (see {@link #factionFell}); does nothing for a hero that serves no faction. */
    private static void fellWithFaction(MinecraftServer server, CampaignData data, HeroState s) {
        if (s.owner == null) {
            return;
        }
        HeroEntity h = living(s);
        switch (com.factionclash.logic.campaign.Elimination.heroFate(s.alive, s.presumedLost, h != null)) {
            case DIES_THERE -> {
                h.kill(); // its death (onDeath) frees it, and the feed tells it
                if (h.isAlive()) {
                    h.setHealth(0);
                    h.die(h.damageSources().genericKill());
                }
                if (s.owner != null) {
                    settleFallen(data, s, h); // (a death that did not reach onDeath)
                }
            }
            case TOLD_AND_FREED -> {
                // it dies where it is, unseen: the feed tells every hero's death, so this one is told now, in vanilla's
                // generic words with its faction beside its name (owner, 2026-11-25)
                HeroKillFeed.fellAway(server, nameOf(s.id), s.owner);
                settleFallen(data, s, null);
            }
            case FREED -> settleFallen(data, s, null);
        }
    }

    /** A hero of a fallen faction is dead for good: counted as a death if it was alive, then free. */
    private static void settleFallen(CampaignData data, HeroState s, HeroEntity h) {
        if (h != null) {
            snapshot(s, h);
        }
        if (s.alive) {
            s.deaths++;
        }
        FactionType was = s.owner;
        free(data, s);
        FactionClash.LOGGER.info("[Heroes] {} fell with the {} and is free (level {})", s.id, was == null ? "?" : was.displayName, s.level);
    }

    /** The campaign's own living entity of this hero in any loaded place (any dimension), or null. */
    private static HeroEntity living(HeroState s) {
        if (s.uuid == null) {
            return null;
        }
        for (HeroEntity h : HeroEntity.live()) {
            if (s.uuid.equals(h.getUUID()) && h.isAlive() && !h.isPreview()) {
                return h;
            }
        }
        return null;
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
     * A fallen hero comes back at full health, with its kit, level and traits: a respawn like a player's at a bed,
     * never a teleport of a living NPC (HEROES_V2 3.4). Where: its home base's spawn point (or the faction's first
     * standing base); a hero whose own {@code revival} block says {@code where_it_fell} rises where it fell, and
     * {@code nearest_base} at the faction's standing base nearest to that place. It says its {@code revived} line (its
     * revival block's own first). A faction with no standing base loses the hero (it is released).
     */
    private static HeroEntity returnHome(MinecraftServer server, CampaignData data, HeroState s) {
        BaseState home = data.base(s.baseId);
        if (home == null || home.destroyed || home.faction != s.owner) {
            List<BaseState> bases = data.livingBasesOf(s.owner);
            home = bases.isEmpty() ? null : bases.get(0);
        }
        HeroEntry entry = HeroRegistry.getOrFallback(s.id);
        if (home == null) {
            release(server, s, "has no standing base to return to and leaves");
            return null;
        }
        HeroDef.Revival own = entry.def() == null ? null : entry.def().v2().revival();
        BlockPos at = spawnPoint(home);
        BaseState base = home;
        if (own != null && s.fellAt != null) {
            if (own.where() == HeroDef.RiseWhere.WHERE_IT_FELL) {
                // 6.17 (task T1): on the nearest safe cell within 16 blocks of where it fell (out of the lava it died in),
                // at home after a death in the void or with no safe cell near
                BlockPos safe = safeCellNear(server.overworld(), s.fellAt, SAFE_RISE_RADIUS);
                if (safe != null) {
                    at = safe;
                }
            } else if (own.where() == HeroDef.RiseWhere.NEAREST_BASE) {
                BlockPos fell = s.fellAt;
                base = data.livingBasesOf(s.owner).stream()
                        .min(java.util.Comparator.comparingDouble(b -> b.center.distSqr(fell))).orElse(home);
                at = spawnPoint(base);
            }
        }
        // its faction learns of a fall nobody of it saw when the hero rises (docs/design/missing_npcs.md 7)
        NpcRecord old = com.factionclash.campaign.RollCall.heroRises(data, s.owner, s.uuid);
        boolean unseen = com.factionclash.campaign.RollCall.fallUnknown(old);
        HeroEntity h = spawn(server.overworld(), data, s, entry, s.owner, home, at);
        s.revivals++;
        data.setDirty();
        // risen where none of its faction is (where it fell, far from home) from a fall none of it saw: nobody of it
        // hears of either now; it keeps its faction's view of it and tells its fall itself when it comes home (review of
        // 2026-11-19: the faction was told at once)
        boolean heard = !unseen || com.factionclash.campaign.RollCall.inHomeArea(data, s.owner, server.overworld(), h.blockPosition());
        if (heard) {
            boolean fell = own != null && own.where() == HeroDef.RiseWhere.WHERE_IT_FELL && s.fellAt != null && !at.equals(spawnPoint(home));
            String where = fell ? " where it fell" : " to " + base.name;
            String back = unseen ? h.npcName() + " fell where none of the faction saw it and returns" + where + "."
                    : h.npcName() + " returns" + where + ".";
            Messages.toFaction(server, s.owner, Component.literal(back).withStyle(ChatFormatting.GOLD));
            com.factionclash.campaign.RollCall.heard(s.owner, h.getUUID(), unseen
                    ? com.factionclash.logic.campaign.Whereabouts.Event.KILLED_REPORTED : com.factionclash.logic.campaign.Whereabouts.Event.BACK, back);
        } else {
            com.factionclash.campaign.RollCall.heroRisesAway(data, old, h);
        }
        server.overworld().sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, h.getX(), h.getY() + 1, h.getZ(),
                40, 0.4, 0.8, 0.4, 0.3);
        HeroSounds.cue(h, com.factionclash.logic.hero.SoundIndex.REVIVED); // the old spare's warm chime, now the return's
        if (own != null && own.line() != null && !own.line().isBlank()) {
            if (heard) {
                Messages.toFaction(server, s.owner, Component.literal(com.factionclash.logic.hero.HeroSpeech.chatLine(h.npcName(), own.line())));
                h.clock().said(LineEvent.REVIVED, server.overworld().getGameTime());
            } else {
                h.speak(LineEvent.REVIVED, own.line()); // (away from home: those near hear it)
            }
        } else {
            h.say(LineEvent.REVIVED);
        }
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
        // its role: its definition's; a dormant hero keeps the one it last had
        NpcRole role = entry.dormant() && s.role != null && NpcRole.byId(s.role) != null ? NpcRole.byId(s.role) : entry.role();
        h.role(role);
        h.setBaseId(base.id);
        h.setNpcName(entry.dormant() ? nameOf(s.id) : entry.name());
        h.restoreProgress(s.level, s.xp, s.affixes);
        h.applyDefinition(true); // numbers with boss health, role, name and the kit of its file
        h.setHealth(h.getMaxHealth());
        s.role = h.getRole().id;
        h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        s.owner = faction;
        s.baseId = base.id;
        s.uuid = h.getUUID();
        s.alive = true;
        s.reviveAt = 0;
        s.presumedLost = false;
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

    /** A {@code where_it_fell} revival rises within this many blocks of where the hero fell (6.17). */
    public static final int SAFE_RISE_RADIUS = 16;

    /**
     * The nearest cell to {@code fell} (horizontally, then by height) within {@code radius} blocks where a hero stands
     * unhurt: a floor, room for it, no lava, fire, magma, powder snow or thorns there or beside it (HEROES_V2 6.17).
     * Null after a death in the void (below the world's floor) or when there is none.
     */
    public static BlockPos safeCellNear(ServerLevel level, BlockPos fell, int radius) {
        if (fell == null || fell.getY() < level.getMinBuildHeight()) {
            return null;
        }
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        int[] drop = new int[1];
        List<int[]> cells = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    cells.add(new int[] {dx, dz});
                }
            }
        }
        cells.sort(java.util.Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        int top = Math.min(level.getMaxBuildHeight() - 2, fell.getY() + 4);
        for (int[] o : cells) {
            int x = fell.getX() + o[0], z = fell.getZ() + o[1];
            for (int dy = 0; dy <= 8; dy++) {
                for (int sign = dy == 0 ? 1 : -1; sign <= 1; sign += 2) {
                    int y = fell.getY() + sign * dy;
                    if (y > top || y <= level.getMinBuildHeight()) {
                        continue;
                    }
                    if (com.factionclash.combat.TerrainEdges.safeStand(level, x, y, z, c, drop) && clearAround(level, x, y, z, c, drop)) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    /** No lava or fire in the four cells beside a revival cell (it does not rise with a foot in the pool). */
    private static boolean clearAround(ServerLevel level, int x, int y, int z, BlockPos.MutableBlockPos c, int[] drop) {
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : side) {
            var g = com.factionclash.combat.TerrainEdges.ground(level, x + o[0], y, z + o[1], c, drop);
            if (g == com.factionclash.logic.hero.EdgePlay.Ground.LAVA || g == com.factionclash.logic.hero.EdgePlay.Ground.FIRE) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos spawnPoint(BaseState base) {
        BlockPos p = base.point(BaseState.SPAWN);
        return p != null ? p : base.center;
    }

    // ------------------------------------------------------------------ death, uniqueness, the timer

    /**
     * The hero fell: its {@code fallen} line, and it returns after its revival delay ({@link #reviveSeconds}); its kit
     * vanished with it (HeroEntity). The killer's faction hears of it too (v1 25.4).
     */
    public static void onDeath(MinecraftServer server, HeroEntity h, net.minecraft.world.damagesource.DamageSource source) {
        CampaignData data = CampaignData.get(server);
        HeroState s = data.heroes.get(h.heroId());
        if (s == null || !h.getUUID().equals(s.uuid)) {
            return;
        }
        if (!s.alive) {
            if (s.presumedLost) {
                // presumed lost, but its entity lived on where the presumption could not see it (another dimension):
                // this is its real death, told by the feed now and counted at the presumption, so no presumption is left
                // for a faction's fall to tell; a fallen faction's hero is settled for good (no second death)
                s.presumedLost = false;
                data.setDirty();
                if (s.owner != null && com.factionclash.campaign.CampaignManager.fallen(data, s.owner)) {
                    settleFallen(data, s, h);
                }
            }
            return;
        }
        if (s.owner != null && com.factionclash.campaign.CampaignManager.fallen(data, s.owner)) {
            // it dies with its fallen faction (or after it, a straggler): no base to rise at, so it never returns and is
            // free from now; the kill feed has told its death; nobody of its faction is left to hear of it
            h.say(LineEvent.FALLEN);
            settleFallen(data, s, h);
            return;
        }
        // the killer: the blow's, or the enemy whose push or blow came within 15 s of a death by terrain (6.17)
        Entity killer = source == null ? null : com.factionclash.combat.KillCredits.killer(h, source); // truth: the killer's name in the hero's last words and the fall's line (presentation); the witnesses carry the line
        h.say(LineEvent.FALLEN, killer == null ? java.util.Map.of() : java.util.Map.of("enemy", HeroEntity.nameOf(killer)), null);
        // where it fell, for a where_it_fell or nearest_base revival: only in the overworld, where every base and every
        // revival is (review of T1, 2026-11-19: a hero that died beyond a portal rose at the same coordinates in the
        // overworld, on whatever stood there); a death in another dimension rises at home
        boolean overworld = h.level().dimension() == net.minecraft.world.level.Level.OVERWORLD;
        s.fellAt = overworld ? h.blockPosition() : null;
        // its faction hears of it now only when it knows (a fall near home, or seen from home; RollCall decided in
        // FactionNpc.die): else a witness away brings the news, or its rising at home tells it (docs/design/missing_npcs.md)
        boolean known = com.factionclash.campaign.RollCall.known(data.record(h.getUUID()));
        fallen(server, data, s, h, "has fallen at " + h.blockPosition().getX() + ", " + h.blockPosition().getZ()
                + (overworld ? "" : " in " + h.level().dimension().location().getPath().replace('_', ' ')), known);
        if (h.level() instanceof ServerLevel seenAt) {
            // its fall is a sighting of every hostile faction whose people saw it (docs/design/recon.md 5.5, task R11): at
            // once at home, carried home by a scout away; it counts in their plans until half a day after its return
            com.factionclash.campaign.EnemySightings.fell(seenAt, data, h, seenAt.getGameTime());
        }
        com.factionclash.faction.FactionType theirs = com.factionclash.combat.CombatRules.factionOf(killer);
        if (theirs != null && theirs != s.owner) {
            String line = h.npcName() + " of the " + s.owner.displayName + " has fallen to " + HeroEntity.nameOf(killer) + ".";
            if (killer instanceof net.minecraft.server.level.ServerPlayer) {
                Messages.toFaction(server, theirs, Component.literal(line).withStyle(ChatFormatting.GOLD)); // the player saw it
            } else if (h.level() instanceof ServerLevel sl) {
                // the members of the killer's side who saw it fall (the roll call's witness rule: 32 blocks, a line of
                // sight) carry it home; told at once when one of them stands at home (knowledge review of 2026-11-19: a
                // summon's or a decoy's kill was told at once from anywhere, and a pusher that had walked off carried it)
                List<java.util.UUID> saw = new java.util.ArrayList<>();
                for (com.factionclash.campaign.RollCall.Witness w : com.factionclash.campaign.RollCall.witnesses(sl, data, h, theirs)) {
                    saw.add(w.uuid()); // (a member player carries it to a station, as its other news)
                }
                com.factionclash.campaign.RollCall.tellOrCarry(sl, data, theirs, saw, line, ChatFormatting.GOLD);
            }
        }
    }

    /**
     * Seconds from a death to the return (HEROES_V2 3.4): {@code heroes.reviveSeconds} (600), or the hero's own
     * {@code revival} block's seconds, shortened by the best {@code revival_rite} talent among the heroes its faction
     * has, never under {@code heroes.minReviveSeconds} (30). A dormant hero has no abilities: the plain 600.
     */
    public static int reviveSeconds(CampaignData data, HeroState s) {
        HeroDef d = HeroRegistry.definition(s.id);
        int own = d != null && d.v2().revival() != null ? d.v2().revival().seconds() : 0;
        return HeroRules.reviveDelaySeconds(FCConfig.HERO_REVIVE_SECONDS.get(), own, riteTier(data, s.owner),
                FCConfig.HERO_MIN_REVIVE_SECONDS.get());
    }

    /** The faction's {@code revival_rite} tier: the best of its heroes that have the talent, by each one's level (7.2). */
    public static int riteTier(CampaignData data, com.factionclash.faction.FactionType faction) {
        if (faction == null) {
            return 0;
        }
        List<Integer> levels = new ArrayList<>();
        for (HeroState o : data.heroes.values()) {
            HeroDef d = o.owner == faction ? HeroRegistry.definition(o.id) : null;
            if (d != null && d.v2().talents().stream().anyMatch(t -> t.id() == HeroDef.TalentId.REVIVAL_RITE)) {
                levels.add(o.level);
            }
        }
        return HeroRules.riteTier(levels);
    }

    private static void fallen(MinecraftServer server, CampaignData data, HeroState s, HeroEntity h, String what, boolean tell) {
        if (h != null) {
            snapshot(s, h);
        }
        long now = server.overworld().getGameTime();
        s.alive = false;
        s.deaths++;
        s.reviveAt = HeroRevival.reviveAt(now, reviveSeconds(data, s));
        data.setDirty();
        if (s.owner != null && tell) {
            HeroDef d = HeroRegistry.definition(s.id);
            boolean night = d != null && d.v2().revival() != null && d.v2().revival().at() == HeroDef.RiseAt.NIGHT;
            String line = nameOf(s.id) + " " + what + " and returns in " + HeroRevival.countdown(s.reviveAt - now)
                    + (night ? ", at the first nightfall after" : "") + ".";
            Messages.toFaction(server, s.owner, Component.literal(line).withStyle(ChatFormatting.RED));
            com.factionclash.campaign.RollCall.heard(s.owner, s.uuid, com.factionclash.logic.campaign.Whereabouts.Event.KILLED, line);
        }
    }

    /** Level, experience and traits outlive the entity; its role and health are kept for a dormant return and the war councils. */
    private static void snapshot(HeroState s, HeroEntity h) {
        s.level = h.npcLevel();
        s.xp = h.xp();
        s.affixes.clear();
        s.affixes.putAll(h.affixes());
        s.role = h.getRole().id;
        s.health = Math.max(0, h.getHealth());
        s.maxHealth = h.getMaxHealth();
    }

    /**
     * Every loaded hero takes its definition as the registry has it now ({@code /reload}, {@code /fc hero reload}): a
     * changed file applies at once (v1 2.3), a broken or removed one leaves the hero dormant.
     */
    public static int refreshAll(MinecraftServer server) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (HeroEntity h : level.getEntities(ModEntities.HERO.get(), e -> true)) {
                if (!h.isPreview()) {
                    h.applyDefinition(false);
                    n++;
                }
            }
        }
        return n;
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
        if (h.isDeadOrDying()) {
            // saved during its death animation (the server stopped, or its chunk unloaded, within the second after it
            // fell): the fall was already counted and its return is timed; a dead copy joining would bring the record
            // back to life ("presumed lost ... back" below) for an entity that vanishes a second later, and the hero
            // would never return (alive with no entity) and could not be revived
            FactionClash.LOGGER.info("Discarding a dead copy of hero {} ({})", id, h.getUUID());
            return false;
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
                s.presumedLost = false;
                NpcRecord r = data.faction(s.owner).roster.get(s.uuid);
                if (r != null) {
                    r.vanished = false; // found again; its faction sees it when it is home (RollCall.sighting)
                }
                data.setDirty();
            }
            return true;
        }
        FactionClash.LOGGER.info("Discarding a stale copy of hero {} ({}); the hero is {}", id, h.getUUID(),
                s.owner == null ? "free" : s.uuid + " of the " + s.owner.displayName);
        if (s.owner == null && h.getUUID().equals(s.uuid) && com.factionclash.campaign.CampaignManager.fallen(data, h.getFaction())) {
            // the hero's own last entity, away in an unloaded place when its faction fell: it died with it there (its death
            // was told and counted at the fall), so what it carried drops where it stood, as at any hero's death (its kit
            // vanishes). Not now: a copy joining with its chunk must not touch the level yet (the next tick does it). Only
            // this entity: any older copy is another one, whose things would be a duplicate.
            com.factionclash.campaign.CampaignManager.afterJoin(h, e -> {
                if (e instanceof HeroEntity copy) {
                    copy.dropCarried();
                }
            });
        }
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
            if (com.factionclash.campaign.CampaignManager.fallen(data, s.owner)) {
                // its faction fell (a save from before every member died with its faction, or a hero that slipped through):
                // dead for good and free, never "has no standing base to return to and leaves" ten minutes later
                fellWithFaction(server, data, s);
                continue;
            }
            if (s.alive) {
                NpcRecord r = s.uuid == null ? null : data.faction(s.owner).roster.get(s.uuid);
                Entity e = s.uuid == null ? null : server.overworld().getEntity(s.uuid);
                if (e instanceof HeroEntity h && h.isAlive()) {
                    if (h.npcLevel() != s.level || h.xp() != s.xp || Math.abs(h.getHealth() - s.health) >= 1
                            || Math.abs(h.getMaxHealth() - s.maxHealth) >= 0.01) {
                        snapshot(s, h);
                        data.setDirty();
                    }
                } else if (r != null && r.vanished) {
                    // the engine lost it from a loaded place: it falls and returns on time (whatever its faction believes:
                    // a hero presumed dead far away but alive walks on); its faction hears now only if it saw it go
                    fallen(server, data, s, null, "is missing and presumed lost", !r.alive);
                    s.presumedLost = true; // (no death was told: if its faction falls meanwhile, the fall tells it)
                }
            } else {
                HeroDef d = HeroRegistry.definition(s.id);
                boolean night = d != null && d.v2().revival() != null && d.v2().revival().at() == HeroDef.RiseAt.NIGHT;
                if (HeroRules.revivalDue(now, s.reviveAt, night, server.overworld().getDayTime())) {
                    returnHome(server, data, s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ combat XP from the war (v1 22.1, task A4)

    /**
     * A raid ended: each hero still alive among the raiders of a won raid earns {@code XP_RAID_WON} (100); when the
     * target stood, each living hero of the defending faction at that base earns {@code XP_DEFENCE} (30). The same for
     * every hero, whoever the raiders and defenders were.
     */
    /**
     * A raid fell back (beaten back by the fall-back share; MOBILITY_AND_WORK 7.7, task A12): every hero of its party
     * still alive takes the lost-raid trigger: an escaping hero in or near the target or fighting escapes home with any
     * mobility, the others go home with the party. Returns how many escaped.
     */
    public static int raidLost(ServerLevel level, FactionState raiders, BaseState target) {
        int n = 0;
        for (java.util.UUID u : List.copyOf(raiders.raiders)) {
            if (level.getEntity(u) instanceof HeroEntity h && h.isAlive() && h.combat().boss().escape().lostRaid(target)) {
                n++;
            }
        }
        return n;
    }

    public static void raidEnded(ServerLevel level, FactionState raiders, BaseState target, boolean won) {
        if (won) {
            for (java.util.UUID u : raiders.raiders) {
                if (level.getEntity(u) instanceof HeroEntity h && h.isAlive()) {
                    h.awardCombatXp(HeroRules.XP_RAID_WON);
                }
            }
            return;
        }
        if (target == null || target.destroyed) {
            return;
        }
        for (HeroEntity h : HeroEntity.live()) {
            if (h.isAlive() && h.level() == level && h.getFaction() == target.faction
                    && target.contains(h.blockPosition(), 16)) {
                h.awardCombatXp(HeroRules.XP_DEFENCE);
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
        h.applyDefinition(true);
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
