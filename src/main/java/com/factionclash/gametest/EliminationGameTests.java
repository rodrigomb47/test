package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseBuilder;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.CampaignManager;
import com.factionclash.campaign.Diplomacy;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.combat.CombatRules;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroKillFeed;
import com.factionclash.hero.HeroKit;
import com.factionclash.hero.HeroManager;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.util.ChunkLoading;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A faction's fall (the owner's rule of 2026-11-25: "if a faction gets eliminated, all of their members dies"; the pact
 * against a conqueror removed the same day): a member away in an unloaded place dies when it loads, its things dropping;
 * a member of a faction never eliminated lives; a hero of the faction dies with its last base without running and is
 * free for good, whether it was loaded, waiting to rise or away (one kill-feed line each, its stale copy discarded); a
 * member player dies and is factionless, at once or at its next login; after a conquest the survivors stay enemies; an
 * old save that holds a pact loads. The review of 2026-11-25 added: the Hollow's monsters die with its last lair wherever
 * they are (an unloaded one when it loads) and give up their chunk tickets; an away hero's things drop where its copy
 * stood; a hero presumed lost is told dead at the fall; a member of an ended campaign dies when it loads in a new one. Each test that eliminates a faction has its own batch, sets the faction's other
 * standing bases aside and puts its roster back afterwards (the test base's cleanup restores its fall stamp).
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class EliminationGameTests {
    private EliminationGameTests() {
    }

    /** The faction that falls in these tests, and the conqueror. */
    private static final FactionType FALLS = FactionType.STONEGUARD, CONQUEROR = FactionType.EMBER_LEGION;

    /**
     * Makes the test base the faction's last standing one (any other standing base of it, a test's leftover, is set aside)
     * and keeps its roster records as they are now; the cleanup puts both back.
     */
    private static void lastBase(GameTestBases.Handle t) {
        CampaignData data = t.data;
        FactionType f = t.base.faction;
        List<BaseState> aside = new ArrayList<>();
        for (BaseState b : data.bases.values()) {
            if (b.faction == f && !b.destroyed && b != t.base) {
                aside.add(b);
            }
        }
        aside.forEach(b -> b.destroyed = true);
        Map<UUID, CompoundTag> records = new HashMap<>();
        data.faction(f).roster.forEach((u, r) -> records.put(u, r.save()));
        int conquests = data.faction(CONQUEROR).basesDestroyed;
        t.onCleanup(() -> {
            aside.forEach(b -> b.destroyed = false);
            var roster = data.faction(f).roster;
            records.forEach((u, tag) -> roster.put(u, NpcRecord.load(tag)));
            data.faction(CONQUEROR).basesDestroyed = conquests;
        });
    }

    /** An NPC of {@code faction} at relative (x, 2, z), still (no AI), discarded by the cleanup. */
    private static FactionNpc still(GameTestBases.Handle t, int x, int z, FactionType faction, NpcRole role) {
        FactionNpc n = t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
        n.faction(faction);
        n.role(role);
        n.setNoAi(true);
        return t.track(n);
    }

    /** The entity as its chunk would save it, then gone from the world as at a chunk unload. */
    private static CompoundTag unload(Entity e) {
        CompoundTag tag = new CompoundTag();
        e.save(tag);
        e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        return tag;
    }

    /** Its chunk loads again: the saved entity joins the level as it would from disk (null when it was refused). */
    private static Entity reload(GameTestBases.Handle t, CompoundTag tag) {
        ServerLevel level = t.helper.getLevel();
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> x);
        if (e == null) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("the saved entity did not load");
        }
        if (!level.addFreshEntity(e)) {
            return null;
        }
        return t.track(e);
    }

    /** Items of {@code item} lying within 3 blocks of {@code at}. */
    private static int lying(GameTestHelper h, Vec3 at, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3), e -> e.getItem().is(item))) {
            n += e.getItem().getCount();
        }
        return n;
    }

    /** The kill feed's lines that name {@code name}, while the test records them (stopped by the cleanup). */
    private static List<HeroKillFeed.Line> feed(GameTestBases.Handle t) {
        List<HeroKillFeed.Line> lines = HeroKillFeed.record();
        t.onCleanup(HeroKillFeed::stopRecording);
        return lines;
    }

    private static List<HeroKillFeed.Line> naming(List<HeroKillFeed.Line> lines, String name) {
        List<HeroKillFeed.Line> out = new ArrayList<>();
        synchronized (lines) {
            for (HeroKillFeed.Line l : lines) {
                if (l.text().getString().contains(name)) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /** A hero of the test base's faction, an escaping kind (a schemer), given at relative (x, 2, z). */
    private static HeroEntity schemer(GameTestBases.Handle t, String id, int x, int z) {
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroEndGameTests.endDef(id, HeroEndGameTests.SCHEMER, "", ""), x, z);
        hero.setNoAi(true);
        return hero;
    }

    /**
     * A free hero for good ({@code what} names the case): no faction, dead, no revival pending, on no faction's roll, the
     * revival timer brings nothing back and an operator's revive finds no faction to bring it back for.
     */
    private static void freeForGood(GameTestBases.Handle t, String id, String what) {
        var server = t.helper.getLevel().getServer();
        HeroState s = t.data.heroes.get(id);
        t.assertTrue(s != null, what + ": no campaign record left");
        t.assertTrue(s.owner == null && !s.alive && s.reviveAt == 0 && s.baseId == -1,
                what + ": not free for good (owner " + s.owner + ", alive " + s.alive + ", reviveAt " + s.reviveAt + ")");
        HeroManager.tick(server); // the revival timer's pass: nothing to bring back
        t.assertTrue(s.owner == null && !s.alive, what + ": the timer brought it back");
        t.assertTrue(!HeroManager.revive(server, id).ok(), what + ": an operator could still revive it for its fallen faction");
        for (FactionState f : t.data.factions.values()) {
            t.assertTrue(s.uuid == null || !f.roster.containsKey(s.uuid), what + ": still on the " + f.type.displayName + " roll");
        }
    }

    // ------------------------------------------------------------------ ordinary members

    /**
     * (i) A member away in an unloaded place when its faction falls (saved with its chunk, gone from the world) is dead for
     * the roll call at once; when its chunk loads it dies on its first tick where it stands, and the iron it carried lies
     * on the ground.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_away_member")
    public static void elimAwayMemberDiesWhenItLoadsAndDropsItsThings(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            FactionNpc miner = t.npc(18, 18, NpcRole.MINER);
            miner.setNoAi(true);
            miner.inventory().addItem(new ItemStack(Items.IRON_INGOT, 7));
            UUID id = miner.getUUID();
            Vec3 at = miner.position();
            CompoundTag saved = unload(miner);
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            NpcRecord r = t.data.faction(FALLS).roster.get(id);
            t.assertTrue(r != null && r.dead && r.where == State.KNOWN_DEAD, "the away member is not dead for the roll call: "
                    + (r == null ? "no record" : r.where + ", dead " + r.dead));
            t.assertTrue(lying(h, at, Items.IRON_INGOT) == 0, "iron on the ground before the member loaded");
            Entity back = reload(t, saved);
            t.assertTrue(back instanceof FactionNpc, "the member did not load again");
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(!back.isAlive(), "a member of the fallen faction loaded and lives on");
                t.assertTrue(lying(h, at, Items.IRON_INGOT) == 7, "its iron did not drop where it died: " + lying(h, at, Items.IRON_INGOT));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (ii) A member of a faction that was never eliminated lives: one whose faction is not alive but has no elimination
     * time (an old save's unwritten flag, a campaign never started), and one whose faction has no campaign state at all.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_never_fallen")
    public static void elimNeverFallenFactionsMembersLive(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            CampaignData data = t.data;
            FactionState flagged = data.faction(FALLS);
            boolean wasAlive = flagged.alive;
            long wasAt = flagged.eliminatedAt;
            FactionState none = data.factions.remove(FactionType.TIDE_ALLIANCE);
            t.onCleanup(() -> {
                flagged.alive = wasAlive;
                flagged.eliminatedAt = wasAt;
                if (none != null) {
                    data.factions.put(FactionType.TIDE_ALLIANCE, none);
                }
            });
            flagged.alive = false;
            flagged.eliminatedAt = 0;
            FactionNpc a = still(t, 6, 6, FALLS, NpcRole.SWORDSMAN);
            FactionNpc b = still(t, 8, 6, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
            h.runAfterDelay(45, t.guard(() -> {
                t.assertTrue(a.isAlive(), "a member of a faction never eliminated (not alive, no elimination time) died");
                t.assertTrue(b.isAlive(), "a member of a faction with no campaign state died");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ heroes

    /**
     * (iii) The faction's last base falls with its hero at home: an escaping kind, it does not run (no escape starts for
     * the last base), it dies with its faction, the feed tells it once in vanilla's words with its faction, no return is
     * announced, and it is free for good with its level kept: no revival, nothing for an operator to revive.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_hero_loaded")
    public static void elimLoadedHeroDiesWithTheLastBaseAndIsFree(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            List<HeroKillFeed.Line> lines = feed(t);
            List<Messages.Said> said = Messages.record();
            t.onCleanup(Messages::stopRecording);
            String id = "fx_elim_home";
            HeroEntity hero = schemer(t, id, 12, 12);
            HeroState s = t.data.heroes.get(id);
            int level = s.level;
            FactionNpc guard = t.npc(10, 12, NpcRole.SWORDSMAN);
            guard.setNoAi(true);
            CampaignManager.destroyBase(h.getLevel(), t.base, CONQUEROR, "test");
            t.assertTrue(CampaignManager.fallen(t.data, FALLS), "the faction did not fall with its last base");
            t.assertTrue(!guard.isAlive(), "a soldier at home outlived its faction");
            t.assertTrue(hero.isDeadOrDying(), "the hero outlived its faction");
            t.assertTrue(s.escapes == 0, "the hero started an escape from the last base's fall: " + s.escapes);
            t.assertTrue(s.deaths == 1 && s.level == level, "deaths " + s.deaths + ", level " + s.level + " (was " + level + ")");
            synchronized (said) {
                t.assertTrue(said.stream().noneMatch(x -> x.line().contains("returns")), "a revival was announced: " + said);
            }
            freeForGood(t, id, "the hero at home");
            List<HeroKillFeed.Line> told = naming(lines, HeroManager.nameOf(id));
            t.assertTrue(told.size() == 1 && told.get(0).victim() == hero, "the feed told the hero's death " + told.size() + " times: " + told);
            var c = told.get(0).text();
            t.assertTrue(c.getContents() instanceof TranslatableContents tc && tc.getKey().equals("death.attack.genericKill")
                    && c.getString().contains("(" + FALLS.displayName + ")"), "not vanilla's words with its faction: " + c.getString());
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (iv) A hero that fell before its faction and waits for its revival is freed at the fall: it never rises (no "has no
     * standing base to return to" ten minutes later) and the feed tells nothing more than its first death.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_hero_waiting")
    public static void elimHeroWaitingToRiseIsFreedAndNeverRises(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            List<HeroKillFeed.Line> lines = feed(t);
            String id = "fx_elim_waiting";
            HeroEntity hero = schemer(t, id, 12, 12);
            HeroState s = t.data.heroes.get(id);
            hero.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            t.assertTrue(!s.alive && s.reviveAt > 0 && s.owner == FALLS, "the hero is not waiting to rise: alive " + s.alive
                    + ", reviveAt " + s.reviveAt);
            t.assertTrue(naming(lines, HeroManager.nameOf(id)).size() == 1, "its first death was not told once");
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            t.assertTrue(s.deaths == 1, "the fall counted a second death of a dead hero: " + s.deaths);
            freeForGood(t, id, "the hero waiting to rise");
            t.assertTrue(naming(lines, HeroManager.nameOf(id)).size() == 1, "a second line for a hero already dead: "
                    + naming(lines, HeroManager.nameOf(id)));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (v) A hero away in an unloaded place when its faction falls dies with it unseen: the feed tells it once, at the fall,
     * in vanilla's generic words with its faction beside its name; it is free for good; and its saved copy, when its chunk
     * loads, is discarded (no second line, no hero walking for a dead faction).
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_hero_away")
    public static void elimHeroAwayIsToldOnceAndItsCopyDiscarded(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            List<HeroKillFeed.Line> lines = feed(t);
            String id = "fx_elim_away";
            HeroEntity hero = schemer(t, id, 18, 18);
            UUID uuid = hero.getUUID();
            HeroState s = t.data.heroes.get(id);
            CompoundTag saved = unload(hero);
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            String name = HeroManager.nameOf(id);
            List<HeroKillFeed.Line> told = naming(lines, name);
            t.assertTrue(told.size() == 1 && told.get(0).victim() == null, "the away hero's death was told " + told.size() + " times: " + told);
            var c = told.get(0).text();
            t.assertTrue(c.getContents() instanceof TranslatableContents tc && tc.getKey().equals("death.attack.genericKill")
                    && c.getString().contains(name + " (" + FALLS.displayName + ")"), "not vanilla's words with its faction: " + c.getString());
            t.assertTrue(s.deaths == 1, "its death was not counted: " + s.deaths);
            freeForGood(t, id, "the away hero");
            Entity copy = reload(t, saved);
            t.assertTrue(copy == null && h.getLevel().getEntity(uuid) == null, "the away hero's stale copy joined the world");
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(naming(lines, name).size() == 1, "a second line for the away hero: " + naming(lines, name));
                t.assertTrue(s.owner == null && !s.alive, "the copy brought the hero back");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the survivors, the player, old saves

    /**
     * (vi) Ember conquers Stoneguard: there is no pact. The Tide Alliance and the Grove Covenant stay enemies (their
     * soldiers see each other as enemies, a sweep and a blow land between them, they may raid each other), and nothing
     * Stoneguard's scouts had seen is handed to anyone.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "elim_conquest")
    public static void elimConquestLeavesTheSurvivorsHostile(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 40);
        try {
            lastBase(t);
            for (FactionType f : List.of(CONQUEROR, FactionType.TIDE_ALLIANCE, FactionType.GROVE_COVENANT)) {
                GameTestBases.Handle other = GameTestBases.register(h, f, 2, 3, 0);
                t.onCleanup(other::cleanup);
            }
            BaseState ember = t.data.livingBasesOf(CONQUEROR).get(0);
            Report saw = new Report(Report.Kind.ENEMY_BASE, ember.center, h.getLevel().getGameTime() - 500, CONQUEROR, 2,
                    "Ember settlement, 2 soldiers seen");
            saw.by = "Stoneguard scout";
            t.data.faction(FALLS).knowledge.add(saw);
            t.onCleanup(() -> t.data.faction(FALLS).knowledge.remove(saw));
            int tideKnew = t.data.faction(FactionType.TIDE_ALLIANCE).knowledge.size();
            int groveKnew = t.data.faction(FactionType.GROVE_COVENANT).knowledge.size();
            CampaignManager.destroyBase(h.getLevel(), t.base, CONQUEROR, "test");
            t.assertTrue(CampaignManager.fallen(t.data, FALLS), "Stoneguard did not fall");
            t.assertTrue(t.data.faction(FactionType.TIDE_ALLIANCE).knowledge.size() == tideKnew
                    && t.data.faction(FactionType.GROVE_COVENANT).knowledge.size() == groveKnew,
                    "the fallen faction's reports were handed to the survivors");
            t.assertTrue(Diplomacy.hostile(FactionType.TIDE_ALLIANCE, FactionType.GROVE_COVENANT)
                    && Diplomacy.hostile(FactionType.GROVE_COVENANT, FactionType.TIDE_ALLIANCE), "the survivors may not raid each other");
            FactionNpc tide = still(t, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
            FactionNpc grove = still(t, 6, 5, FactionType.GROVE_COVENANT, NpcRole.SWORDSMAN);
            FactionNpc grove2 = still(t, 7, 5, FactionType.GROVE_COVENANT, NpcRole.SWORDSMAN);
            FactionNpc ember1 = still(t, 6, 7, CONQUEROR, NpcRole.SWORDSMAN);
            t.assertTrue(tide.isEnemy(grove) && grove.isEnemy(tide), "the survivors' soldiers are not enemies");
            t.assertTrue(tide.isEnemy(ember1) && grove.isEnemy(ember1), "the conqueror is not their enemy");
            t.assertTrue(!CombatRules.sameSide(tide, grove), "the survivors are on one side");
            tide.equip(new ItemStack(Items.IRON_SWORD));
            tide.setOnGround(true);
            tide.fallDistance = 0;
            tide.doHurtTarget(grove);
            t.assertTrue(grove2.getHealth() < grove2.getMaxHealth(), "the sweep spared the second Grove soldier");
            float before = grove.getHealth();
            grove.invulnerableTime = 0;
            grove.hurt(h.getLevel().damageSources().mobAttack(tide), 5.0F);
            t.assertTrue(grove.getHealth() < before, "a blow between the survivors was called off as friendly fire");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A mock player that dies as a player does ({@code HeroKillFeedGameTests.mortal}: NeoForge's FakePlayer never dies),
     * in the level at relative (x, 2, z), a member of {@code faction} (none: null); the cleanup takes it away.
     */
    private static ServerPlayer mortal(GameTestBases.Handle t, int x, int z, FactionType faction, boolean inLevel) {
        ServerLevel level = t.helper.getLevel();
        ServerPlayer p = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "elim-test-player")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public void die(net.minecraft.world.damagesource.DamageSource source) {
                net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source);
            }
        };
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        p.moveTo(at.x, at.y, at.z, 0F, 0F);
        if (faction != null) {
            t.data.players.put(p.getUUID(), faction);
        }
        if (inLevel) {
            level.addFreshEntity(p);
        }
        t.onCleanup(() -> {
            t.data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /**
     * (vii) A member player dies with its faction and is factionless after (it may join a surviving faction); a member
     * player who was offline at the fall dies as it logs in, and is factionless too. A player of another faction lives.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "elim_player")
    public static void elimMemberPlayerDiesAndIsFactionless(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 40);
        try {
            lastBase(t);
            ServerPlayer member = mortal(t, 6, 6, FALLS, true);
            ServerPlayer other = mortal(t, 8, 6, FactionType.TIDE_ALLIANCE, true);
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            t.assertTrue(member.isDeadOrDying(), "the member player outlived its faction");
            t.assertTrue(!t.data.players.containsKey(member.getUUID()) && CombatRules.factionOf(member) == null,
                    "the member player still belongs to the fallen faction");
            t.assertTrue(other.isAlive() && t.data.players.get(other.getUUID()) == FactionType.TIDE_ALLIANCE,
                    "a player of another faction died or lost its faction");
            // offline at the fall: its membership is still on the books when it logs in
            ServerPlayer away = mortal(t, 10, 6, null, true);
            t.data.players.put(away.getUUID(), FALLS);
            CampaignManager.loggedIn(away);
            t.assertTrue(away.isDeadOrDying(), "the member player offline at the fall lives on after logging in");
            t.assertTrue(!t.data.players.containsKey(away.getUUID()), "the offline member still belongs to the fallen faction");
            CampaignManager.loggedIn(other);
            t.assertTrue(other.isAlive(), "a living faction's player died at its login");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (viii) A save from before 2026-11-25 that holds a pact against a conqueror (and a faction's allies' offensives) loads
     * as any other, its factions and heroes intact, and the next save no longer writes the pact.
     */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void elimOldSaveWithPactKeysLoads(GameTestHelper h) {
        var provider = h.getLevel().registryAccess();
        CompoundTag old = new CompoundTag();
        old.putInt("format", 4);
        old.putBoolean("started", true);
        ListTag factions = new ListTag();
        for (FactionType f : List.of(FactionType.TIDE_ALLIANCE, FactionType.GROVE_COVENANT, CONQUEROR)) {
            CompoundTag ft = new FactionState(f).save();
            if (f != CONQUEROR) {
                ListTag po = new ListTag();
                CompoundTag o = new CompoundTag();
                o.putString("ally", (f == FactionType.TIDE_ALLIANCE ? FactionType.GROVE_COVENANT : FactionType.TIDE_ALLIANCE).id);
                o.putInt("target", 7);
                o.putDouble("power", 9.5);
                o.putLong("until", 48_000);
                po.add(o);
                ft.put("pactOffensives", po);
            }
            factions.add(ft);
        }
        old.put("factions", factions);
        old.putString("pactAgainst", CONQUEROR.id);
        ListTag members = new ListTag();
        members.add(StringTag.valueOf(FactionType.TIDE_ALLIANCE.id));
        members.add(StringTag.valueOf(FactionType.GROVE_COVENANT.id));
        old.put("pactMembers", members);
        old.putLong("pactSince", 24_000);
        CampaignData d = CampaignData.load(old, provider);
        h.assertTrue(d.started && d.factions.size() == 3 && d.factions.get(FactionType.TIDE_ALLIANCE).alive,
                "the old save did not load its factions: " + d.factions.keySet());
        CompoundTag again = d.save(new CompoundTag(), provider);
        h.assertTrue(!again.contains("pactAgainst") && !again.contains("pactMembers") && !again.contains("pactSince"),
                "the pact is still saved: " + again.getAllKeys());
        ListTag fl = again.getList("factions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            h.assertTrue(!fl.getCompound(i).contains("pactOffensives"), "a faction still saves its pact allies' offensives");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the review of 2026-11-25

    /**
     * A monster of the test base, made a Hollow lair, at relative (x, 2, z): still, persistent, in the lair's leather
     * helmet (no burning in daylight), with a sword in hand that always drops; on the lair's list when {@code tracked}.
     */
    private static Zombie lairMonster(GameTestBases.Handle t, int x, int z, boolean tracked) {
        Zombie m = t.track(t.helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z)));
        m.getPersistentData().putInt(BaseBuilder.LAIR_TAG, t.base.id);
        m.setNoAi(true);
        m.setPersistenceRequired();
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        m.setDropChance(EquipmentSlot.HEAD, 0.0F);
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        m.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
        if (tracked) {
            t.base.lairMobs.add(m.getUUID());
        }
        return m;
    }

    /** The test base as the Hollow's last lair. */
    private static GameTestBases.Handle lastLair(GameTestHelper h, int timeout) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.HOLLOW, 2, 3, timeout);
        t.base.kind = BaseState.Kind.LAIR;
        lastBase(t);
        return t;
    }

    /**
     * (ix) The Hollow's last lair falls: every monster of it dies, wherever it is. One loaded but on no lair's list any
     * more (it walked off, or the lair stopped tracking it) dies at once; one away in an unloaded place dies when its chunk
     * loads, where it stands, and its sword drops; a wild zombie lives.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_lair_away")
    public static void elimHollowMonstersDieWithTheLastLairWhereverTheyAre(GameTestHelper h) {
        GameTestBases.Handle t = lastLair(h, 60);
        try {
            Zombie away = lairMonster(t, 18, 18, true);
            Vec3 at = away.position();
            Zombie loose = lairMonster(t, 6, 18, false);
            Zombie wild = t.track(h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6)));
            wild.setNoAi(true);
            wild.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            CompoundTag saved = unload(away);
            CampaignManager.destroyBase(h.getLevel(), t.base, CONQUEROR, "test");
            t.assertTrue(CampaignManager.fallen(t.data, FactionType.HOLLOW), "the Hollow did not fall with its last lair");
            t.assertTrue(!loose.isAlive(), "a loaded Hollow monster on no lair's list outlived the Hollow");
            t.assertTrue(wild.isAlive(), "a wild zombie died with the Hollow");
            Entity back = reload(t, saved);
            t.assertTrue(back instanceof Zombie, "the away monster did not load again");
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(!back.isAlive(), "a Hollow monster away at the fall loaded and lives on");
                t.assertTrue(lying(h, at, Items.IRON_SWORD) == 1, "its sword did not drop where it died: " + lying(h, at, Items.IRON_SWORD));
                t.assertTrue(wild.isAlive(), "a wild zombie died after the Hollow's fall");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (x) A lair monster away from its lair holds a ticking 3x3 area (ChunkLoading); its death gives the area up, whether
     * it dies on a raid or with the Hollow at its fall (before, only a faction NPC's death did, and the areas stayed loaded
     * until the server restarted).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "elim_lair_ticket")
    public static void elimALairMonstersDeathGivesUpItsChunkTicket(GameTestHelper h) {
        GameTestBases.Handle t = lastLair(h, 40);
        try {
            Zombie raider = lairMonster(t, 18, 18, true);
            Zombie other = lairMonster(t, 6, 18, true);
            t.onCleanup(() -> {
                ChunkLoading.releaseTraveler(raider);
                ChunkLoading.releaseTraveler(other);
            });
            ChunkLoading.updateTraveler(raider, true);
            ChunkLoading.updateTraveler(other, true);
            t.assertTrue(ChunkLoading.travellerRadius(raider.getUUID()) > 0 && ChunkLoading.travellerRadius(other.getUUID()) > 0,
                    "precondition: the monsters hold no ticking area (keepFactionAreasLoaded off?)");
            other.kill(); // a death away from the lair, the Hollow standing
            t.assertTrue(!other.isAlive(), "precondition: the monster did not die");
            t.assertTrue(ChunkLoading.travellerRadius(other.getUUID()) == 0, "a lair monster that died away keeps its ticking area");
            CampaignManager.destroyBase(h.getLevel(), t.base, CONQUEROR, "test");
            t.assertTrue(!raider.isAlive(), "the raider outlived the Hollow");
            t.assertTrue(ChunkLoading.travellerRadius(raider.getUUID()) == 0, "a raider killed at the Hollow's fall keeps its ticking area");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** Experience lying in orbs within 3 blocks of {@code at}. */
    private static int xpNear(GameTestHelper h, Vec3 at) {
        int n = 0;
        for (ExperienceOrb o : h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(3), e -> true)) {
            n += o.getValue() * Math.max(1, o.saveWithoutId(new CompoundTag()).getInt("Count"));
        }
        return n;
    }

    /** Kit items lying within 3 blocks of {@code at}. */
    private static int kitLying(GameTestHelper h, Vec3 at) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3), e -> HeroKit.isKit(e.getItem())).size();
    }

    /** The saved entity moved to relative (x, 2, z) as another entity of the same hero (an older copy: another UUID). */
    private static CompoundTag olderCopy(GameTestHelper h, CompoundTag saved, int x, int z) {
        CompoundTag c = saved.copy();
        c.putUUID("UUID", UUID.randomUUID());
        Vec3 p = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z)));
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(p.x));
        pos.add(DoubleTag.valueOf(p.y));
        pos.add(DoubleTag.valueOf(p.z));
        c.put("Pos", pos);
        return c;
    }

    /**
     * (xi) A hero away when its faction falls dies with it there: when its chunk loads, its copy is discarded and what it
     * carried drops where it stood, as at any hero's death (its pack, its ammunition, the experience it carried; its kit
     * vanishes); no second feed line. An older stale copy of it (another entity) drops nothing, so nothing is doubled.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_hero_things")
    public static void elimAwayHerosThingsDropWhereItsCopyStood(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            List<HeroKillFeed.Line> lines = feed(t);
            String id = "fx_elim_things";
            HeroEntity hero = schemer(t, id, 18, 18);
            hero.inventory().addItem(new ItemStack(Items.IRON_INGOT, 5));
            hero.setAmmo(new ItemStack(Items.ARROW)); // (its ammunition is one arrow, FactionNpc.setAmmo)
            boolean kit = false;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                kit |= HeroKit.isKit(hero.getItemBySlot(slot));
            }
            t.assertTrue(kit, "precondition: the hero wears no kit item");
            Vec3 at = hero.position();
            CompoundTag saved = unload(hero);
            saved.putInt("FcCarriedXp", 30);
            CompoundTag older = olderCopy(h, saved, 6, 18);
            Vec3 olderAt = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 2, 18)));
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            String name = HeroManager.nameOf(id);
            t.assertTrue(naming(lines, name).size() == 1, "the away hero's death was not told once: " + naming(lines, name));
            Entity copy = reload(t, saved);
            Entity old = reload(t, older);
            t.assertTrue(copy == null && old == null, "a stale copy of the hero joined the world");
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(lying(h, at, Items.IRON_INGOT) == 5, "its pack did not drop where it stood: " + lying(h, at, Items.IRON_INGOT));
                t.assertTrue(lying(h, at, Items.ARROW) == 1, "its ammunition did not drop: " + lying(h, at, Items.ARROW));
                t.assertTrue(xpNear(h, at) == 30, "the experience it carried did not drop: " + xpNear(h, at));
                t.assertTrue(kitLying(h, at) == 0, "its kit dropped");
                t.assertTrue(lying(h, olderAt, Items.IRON_INGOT) == 0 && lying(h, olderAt, Items.ARROW) == 0,
                        "an older stale copy dropped its things too (a duplicate)");
                t.assertTrue(naming(lines, name).size() == 1, "a second line for the away hero: " + naming(lines, name));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (xii) A hero presumed lost (its entity went missing from a loaded place; it may be alive where it is) when its faction
     * falls dies with it: the feed tells it once at the fall (the presumption told nothing), no second death is counted,
     * it is free for good, and its copy's things drop where it stood when it loads.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "elim_hero_presumed")
    public static void elimHeroPresumedLostIsToldDeadAtTheFall(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FALLS, 2, 3, 60);
        try {
            lastBase(t);
            List<HeroKillFeed.Line> lines = feed(t);
            var server = h.getLevel().getServer();
            String id = "fx_elim_presumed";
            HeroEntity hero = schemer(t, id, 18, 18);
            hero.inventory().addItem(new ItemStack(Items.GOLD_INGOT, 3));
            UUID uuid = hero.getUUID();
            Vec3 at = hero.position();
            HeroState s = t.data.heroes.get(id);
            CompoundTag saved = unload(hero);
            NpcRecord r = t.data.faction(FALLS).roster.get(uuid);
            t.assertTrue(r != null, "precondition: the hero has no roster entry");
            r.vanished = true; // the engine lost it from a loaded place (RollCall.upkeep)
            HeroManager.tick(server);
            t.assertTrue(!s.alive && s.reviveAt > 0 && s.owner == FALLS && s.deaths == 1,
                    "precondition: not presumed lost (alive " + s.alive + ", reviveAt " + s.reviveAt + ", deaths " + s.deaths + ")");
            String name = HeroManager.nameOf(id);
            t.assertTrue(naming(lines, name).isEmpty(), "the presumption was told as a death: " + naming(lines, name));
            CampaignManager.eliminate(h.getLevel(), t.data, FALLS);
            List<HeroKillFeed.Line> told = naming(lines, name);
            t.assertTrue(told.size() == 1 && told.get(0).victim() == null,
                    "the presumed-lost hero's death with its faction was told " + told.size() + " times: " + told);
            var c = told.get(0).text();
            t.assertTrue(c.getContents() instanceof TranslatableContents tc && tc.getKey().equals("death.attack.genericKill")
                    && c.getString().contains(name + " (" + FALLS.displayName + ")"), "not vanilla's words with its faction: " + c.getString());
            t.assertTrue(s.deaths == 1, "the fall counted a second death: " + s.deaths);
            freeForGood(t, id, "the presumed-lost hero");
            Entity copy = reload(t, saved);
            t.assertTrue(copy == null && h.getLevel().getEntity(uuid) == null, "the presumed-lost hero's copy joined the world");
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(lying(h, at, Items.GOLD_INGOT) == 3, "its pack did not drop where it stood: " + lying(h, at, Items.GOLD_INGOT));
                t.assertTrue(naming(lines, name).size() == 1, "a second line: " + naming(lines, name));
                t.assertTrue(s.owner == null && !s.alive, "the copy brought the hero back");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * (xiii) A member of an ended campaign (an operator ran {@code /fc start} again; its faction may have fallen in the old
     * one while it was away) dies where it stands when it loads in the new one, its things dropping. A recruit of this
     * campaign carries its id and lives; an NPC with no campaign id (saved before ids, a test's) lives.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "elim_old_campaign")
    public static void elimAMemberOfAnEndedCampaignDiesWhenItLoads(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 3, 40);
        try {
            CampaignData data = t.data;
            long was = data.campaignId;
            t.onCleanup(() -> data.campaignId = was);
            data.campaignId = 0x5EED_0002L;
            FactionNpc member = t.npc(6, 6, NpcRole.SWORDSMAN);
            member.setNoAi(true);
            t.assertTrue(member.campaignId() == data.campaignId, "a recruit does not carry its campaign's id: " + member.campaignId());
            FactionNpc old = still(t, 8, 6, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
            old.setCampaignId(0x5EED_0001L);
            old.inventory().addItem(new ItemStack(Items.IRON_INGOT, 2));
            Vec3 oldAt = old.position();
            FactionNpc unknown = still(t, 10, 6, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
            h.runAfterDelay(25, t.guard(() -> {
                t.assertTrue(!old.isAlive(), "a member of an ended campaign lives on in the new one");
                t.assertTrue(lying(h, oldAt, Items.IRON_INGOT) == 2, "its things did not drop: " + lying(h, oldAt, Items.IRON_INGOT));
                t.assertTrue(member.isAlive(), "a recruit of this campaign died");
                t.assertTrue(unknown.isAlive(), "an NPC with no campaign id died");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
