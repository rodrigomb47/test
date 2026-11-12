package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Report;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroKit;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.Affix;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.HeroSize;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heroes, task A1a (HEROES_V2 14.5): the registry reads the shipped visual files, a hero's synced fields start at
 * their defaults, its hitbox comes from its model and is capped at 1.98, it is never drafted into a raid party by the
 * soldier sort, it is unique per id, fights with its basic attacks, saves and reloads, and comes back after a death.
 * Static checks share {@code workers_static}; every test that lets a hero act has a batch of its own. Tests that need
 * no real art use fixture heroes registered for the test ({@link HeroRegistry#registerTransient}); the others use the
 * shipped {@code test_rig}, each in its own batch so two never hold the one hero at once.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroGameTests {
    private HeroGameTests() {
    }

    /** A fixture's visual file: drawn with the neutral model, sized by these numbers. */
    private static String fixture(String style, double heightPx, double headY, double scale) {
        return "{\"format\":1,\"form\":\"adult\",\"build\":\"standard\",\"base\":\"adult_f\",\"style\":\"" + style + "\","
                + "\"height_px\":" + heightPx + ",\"head_y\":" + headY + ",\"render_scale\":0.56,\"scale\":" + scale + ","
                + "\"animation_chain\":[\"_fallback\",\"_style_fists\",\"_core\"],\"texture\":{\"width\":256,\"height\":128}}";
    }

    /** Registers a fixture for the test; the handle's cleanup removes the hero, its record and the fixture. */
    private static void fixture(GameTestBases.Handle t, String id, String style, double heightPx, double headY, double scale) {
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, fixture(style, heightPx, headY, scale));
        MinecraftServer server = t.helper.getLevel().getServer();
        t.onCleanup(() -> {
            HeroManager.forget(server, id);
            fx.close();
        });
    }

    /** Gives a hero to the handle's base at relative (x, 2, z). */
    private static HeroEntity give(GameTestBases.Handle t, String id, int x, int z) {
        HeroManager.Result r = HeroManager.give(t.helper.getLevel().getServer(), id, t.base.faction, t.base, t.helper.absolutePos(new BlockPos(x, 2, z)));
        if (!r.ok()) {
            t.fail("give failed: " + r.message());
            throw new GameTestAssertException(r.message());
        }
        return r.hero();
    }

    private static void near(GameTestBases.Handle t, double expected, double actual, String what) {
        t.assertTrue(Math.abs(expected - actual) < 0.002, what + ": expected " + expected + ", got " + actual);
    }

    // ------------------------------------------------------------------ static checks

    /**
     * The shipped hero files load: the test rig with its numbers, model, texture and animation chain, the neutral
     * fallback model, no library listed as a hero, and no problem in any file.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void builtinHeroesLoad(GameTestHelper h) {
        HeroEntry rig = HeroRegistry.get("test_rig");
        h.assertTrue(rig != null, "the test rig is not loaded; problems: " + HeroRegistry.problems());
        h.assertTrue(rig.source() == HeroEntry.Source.BUILTIN && rig.ownModel(), "the test rig should be built in with its own model");
        h.assertTrue(rig.visual().heightPx() == 52 && rig.visual().headY() == 42 && rig.visual().renderScale() == 0.56,
                "test rig numbers " + rig.visual());
        h.assertTrue(rig.visual().style().equals("katana") && rig.role() == NpcRole.SWORDSMAN, "a katana hero is a swordsman");
        h.assertTrue(rig.visual().animationChain().equals(List.of("test_rig", "_style_katana", "_core")), "chain " + rig.visual().animationChain());
        h.assertTrue(rig.model().equals(ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "geo/hero/test_rig.geo.json")), "model " + rig.model());
        h.assertTrue(rig.name().equals("Test Rig"), "name " + rig.name());
        h.assertTrue(HeroRegistry.get(HeroIdentity.FALLBACK) == null, "the fallback model is not a hero");
        h.assertTrue(HeroRegistry.fallback().id().equals(HeroIdentity.FALLBACK) && HeroRegistry.fallback().ownModel(), "no fallback model");
        for (HeroEntry e : HeroRegistry.heroes()) {
            h.assertTrue(HeroIdentity.isHeroId(e.id()), "listed as a hero: " + e.id());
            if (e.source() != HeroEntry.Source.BUILTIN) {
                continue;
            }
            List<ResourceLocation> files = new ArrayList<>(List.of(e.model(), e.texture(), e.animation()));
            files.addAll(List.of(e.animationFallbacks()));
            for (ResourceLocation f : files) {
                var p = HeroRegistry.modPath(("assets/" + f.getNamespace() + "/" + f.getPath()).split("/"));
                h.assertTrue(p != null && Files.isRegularFile(p), e.id() + ": " + f + " is missing");
            }
        }
        h.assertTrue(HeroRegistry.problems().isEmpty(), "problems in the hero files: " + HeroRegistry.problems());
        h.succeed();
    }

    /**
     * A new hero's synced fields (HEROES_V2 10.1): its id, no action, phase 1 with no flags, calm, on the ground; the
     * talking flag is bit 5 of DATA_STATE alone; a new action advances the sequence. It is a swordsman of its faction on
     * the roster, on guard duty, holding its bound kit weapon.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void heroSyncedFieldsDefaults(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            fixture(t, "fixture_sync", "fists", 52, 42, 1.0);
            HeroEntity hero = give(t, "fixture_sync", 6, 6);
            hero.setNoAi(true);
            t.assertTrue(hero.heroId().equals("fixture_sync"), "hero id " + hero.heroId());
            t.assertTrue(hero.action() == 0 && hero.state() == 0 && hero.mood() == 0 && hero.loco() == 0,
                    "fresh fields " + hero.action() + "/" + hero.state() + "/" + hero.mood() + "/" + hero.loco());
            t.assertTrue(HeroActions.phase(hero.state()) == 1, "phase 1");
            t.assertTrue(HeroActions.MOODS.get(HeroActions.mood(hero.mood())).equals("calm"), "calm");
            t.assertTrue(HeroActions.LOCO_MODES.get(HeroActions.locoMode(hero.loco())).equals("ground"), "on the ground");
            hero.setTalking(true);
            t.assertTrue(hero.isTalking() && hero.state() == (byte) (1 << 5), "talking is bit 5 alone: " + hero.state());
            hero.setTalking(false);
            t.assertTrue(!hero.isTalking() && hero.state() == 0, "not talking");
            hero.playAction(1, 1.0, 0);
            int first = hero.action();
            hero.playAction(1, 1.0, 0);
            t.assertTrue(HeroActions.code(hero.action()) == 1 && HeroActions.sequence(hero.action()) == HeroActions.sequence(first) + 1,
                    "a repeated attack gets a new sequence number");
            hero.setAction(0);
            t.assertTrue(hero.getRole() == NpcRole.SWORDSMAN && hero.isGuardDuty() && hero.getFaction() == FactionType.TIDE_ALLIANCE,
                    "role " + hero.getRole());
            NpcRecord r = t.data.faction(FactionType.TIDE_ALLIANCE).roster.get(hero.getUUID());
            t.assertTrue(r != null && r.isHero() && r.heroId.equals("fixture_sync") && r.role == NpcRole.SWORDSMAN && r.baseId == t.base.id,
                    "roster record " + (r == null ? "missing" : r.heroId + " " + r.role));
            t.assertTrue(hero.getMainHandItem().is(Items.STONE_SWORD) && HeroKit.isKit(hero.getMainHandItem()), "kit weapon " + hero.getMainHandItem());
            HeroState s = t.data.heroes.get("fixture_sync");
            t.assertTrue(s != null && s.alive && s.owner == FactionType.TIDE_ALLIANCE && hero.getUUID().equals(s.uuid) && s.baseId == t.base.id,
                    "campaign record");
            t.assertTrue(hero.npcName().equals("Fixture Sync"), "name " + hero.npcName());
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The hitbox comes from the model (HEROES_V2 3.6): the adult rig is 1.82 blocks with its eyes at 1.575; a 110%
     * hero is capped at 1.98 (eyes in proportion) while its scale, and so its drawn model, stays 1.1, and a size state
     * on top keeps the cap; an 85% chibi is raised to 1.2. The culling box is 1.5 blocks larger all round.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void heroSizeFromVisualClampedTo198(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 40);
        try {
            fixture(t, "fixture_size_adult", "katana", 52, 42, 1.0);
            fixture(t, "fixture_size_big", "hammer", 52, 42, 1.1);
            fixture(t, "fixture_size_chibi", "bow", 35, 25, 0.85);
            HeroEntity adult = give(t, "fixture_size_adult", 6, 6);
            HeroEntity big = give(t, "fixture_size_big", 8, 6);
            HeroEntity chibi = give(t, "fixture_size_chibi", 10, 6);
            for (HeroEntity e : List.of(adult, big, chibi)) {
                e.setNoAi(true);
            }
            near(t, 1.82, adult.getBbHeight(), "adult height");
            near(t, 45 * 0.56 / 16, adult.getEyeHeight(), "adult eyes");
            near(t, 0.6, adult.getBbWidth(), "adult width");
            near(t, 1.82, adult.getBoundingBox().getYsize(), "adult box");
            near(t, 1.1, big.getScale(), "the big hero's scale");
            near(t, HeroSize.MAX_HEIGHT, big.getBbHeight(), "capped height");
            double model = 52 * 0.56 * 1.1 / 16;
            near(t, 45 * 0.56 * 1.1 / 16 * HeroSize.MAX_HEIGHT / model, big.getEyeHeight(), "capped eyes");
            t.assertTrue(big.getRole() == NpcRole.AXE_FIGHTER, "a hammer hero is an axe fighter: " + big.getRole());
            // a size state (A4) on top of the art scale: the box keeps the cap, the model would draw larger still
            var scale = big.getAttribute(Attributes.SCALE);
            ResourceLocation stateId = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "test_size_state");
            scale.addTransientModifier(new AttributeModifier(stateId, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            big.refreshDimensions();
            near(t, 1.1 * 1.05, big.getScale(), "scale with the state");
            near(t, HeroSize.MAX_HEIGHT, big.getBbHeight(), "capped height with the state");
            scale.removeModifier(stateId);
            near(t, HeroSize.MIN_HEIGHT, chibi.getBbHeight(), "the chibi floor");
            near(t, 28 * 0.56 * 0.85 / 16, chibi.getEyeHeight(), "the chibi's eyes stay on its model");
            t.assertTrue(chibi.getRole() == NpcRole.ARCHER && chibi.getMainHandItem().is(Items.BOW) && !chibi.ammo().isEmpty(),
                    "a bow hero is an archer with a bow and its arrow");
            AABB box = adult.getBoundingBox(), cull = adult.getBoundingBoxForCulling();
            near(t, box.getXsize() + 2 * HeroEntity.CULLING_MARGIN, cull.getXsize(), "culling box width");
            near(t, box.getYsize() + 2 * HeroEntity.CULLING_MARGIN, cull.getYsize(), "culling box height");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the director

    /**
     * HEROES_V2 3.2: a hero counts as one soldier of its role for the garrison and the party size, but the soldier
     * sort never drafts it, even as the strongest soldier there. Seven swordsmen and a level-10 hero in iron: the party
     * is five (a third of eight stays home) and the hero is not in it.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_raid")
    public static void heroNotDraftedIntoRaidByDefault(GameTestHelper h) throws ReflectiveOperationException {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 100);
        ServerLevel level = h.getLevel();
        CampaignData data = t.data;
        FactionState f = data.faction(FactionType.EMBER_LEGION);
        long now = level.getGameTime();
        int interval = FCConfig.RAID_INTERVAL_TICKS.get();
        long oldStarted = data.startedAt, oldLastRaid = f.lastRaidAt, oldRaidStarted = f.raidStartedAt, oldRally = f.raidRally;
        int oldLaunched = f.raidsLaunched, oldParty = f.raidParty;
        float oldPower = f.soldierPower;
        BaseState target = new BaseState();
        target.faction = FactionType.GROVE_COVENANT;
        target.kind = BaseState.Kind.SETTLEMENT;
        target.center = new BlockPos(930_000, 70, 930_000);
        target.name = "Hero raid test target";
        data.addBase(target);
        Report seen = new Report(Report.Kind.ENEMY_BASE, target.center, now, FactionType.GROVE_COVENANT, 0, "");
        f.knowledge.add(seen);
        t.onCleanup(() -> {
            data.bases.remove(target.id);
            f.knowledge.remove(seen);
            f.raidTarget = -1;
            f.raiders.clear();
            data.startedAt = oldStarted;
            f.lastRaidAt = oldLastRaid;
            f.raidStartedAt = oldRaidStarted;
            f.raidRally = oldRally;
            f.raidsLaunched = oldLaunched;
            f.raidParty = oldParty;
            f.soldierPower = oldPower;
        });
        try {
            t.assertTrue(f.raidTarget < 0, "Ember already has a raid under way");
            fixture(t, "fixture_raid", "katana", 52, 42, 1.0);
            List<FactionNpc> soldiers = new ArrayList<>();
            int[][] spots = {{10, 10}, {11, 10}, {12, 10}, {13, 10}, {10, 12}, {11, 12}, {13, 12}};
            for (int[] p : spots) {
                FactionNpc n = t.npc(p[0], p[1], NpcRole.SWORDSMAN);
                n.setNoAi(true);
                soldiers.add(n);
            }
            HeroEntity hero = give(t, "fixture_raid", 12, 13);
            hero.setNoAi(true);
            hero.restoreProgress(10, 0, new EnumMap<>(Affix.class));
            hero.equip(new ItemStack(Items.IRON_CHESTPLATE));
            long swordsmen = f.roster.values().stream().filter(r -> r.alive && r.baseId == t.base.id && r.role == NpcRole.SWORDSMAN).count();
            t.assertTrue(swordsmen == 8, "the hero counts as one of 8 swordsmen at the base, counted " + swordsmen);
            data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            var raids = FactionDirector.class.getDeclaredMethod("raids", ServerLevel.class, CampaignData.class, FactionState.class,
                    BaseState.class, long.class);
            raids.setAccessible(true);
            raids.invoke(null, level, data, f, t.base, now);
            t.assertTrue(f.raidTarget == target.id, "no raid was launched (target " + f.raidTarget + ")");
            t.assertTrue(f.raidParty == 5 && f.raiders.size() == 5, "party of 5 of 8 expected, got " + f.raidParty + " / " + f.raiders.size());
            t.assertTrue(!f.raiders.contains(hero.getUUID()), "the hero was drafted into the raid party");
            for (UUID u : f.raiders) {
                t.assertTrue(soldiers.stream().anyMatch(n -> n.getUUID().equals(u)), "a raider that is not one of the swordsmen: " + u);
            }
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the test rig in the world

    /**
     * One living entity per hero id: giving the test rig to another faction takes it from the first; a stale copy
     * with another UUID never joins the level; after a release, even a copy with the released hero's UUID is turned away.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_unique")
    public static void heroUniquePerId(GameTestHelper h) {
        GameTestBases.Handle ember = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 60);
        GameTestBases.Handle grove = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        ember.onCleanup(() -> HeroManager.forget(server, "test_rig"));
        try {
            ember.assertTrue(HeroRegistry.get("test_rig") != null, "the test rig is not loaded");
            HeroEntity first = give(ember, "test_rig", 6, 6);
            first.setNoAi(true);
            ember.assertTrue(first.isAlive() && first.getFaction() == FactionType.EMBER_LEGION, "the test rig did not join Ember");
            HeroEntity second = give(grove, "test_rig", 8, 8);
            second.setNoAi(true);
            ember.assertTrue(first.isRemoved(), "the first entity is still in the world");
            ember.assertTrue(!ember.data.faction(FactionType.EMBER_LEGION).roster.containsKey(first.getUUID()), "Ember kept its roster entry");
            HeroState s = ember.data.heroes.get("test_rig");
            ember.assertTrue(s.owner == FactionType.GROVE_COVENANT && second.getUUID().equals(s.uuid) && s.alive, "the record does not follow the gift");
            // a stale copy (an old chunk, a copied world) with another UUID
            HeroEntity copy = ModEntities.HERO.get().create(h.getLevel());
            copy.setHeroId("test_rig");
            copy.faction(FactionType.GROVE_COVENANT);
            copy.moveTo(h.absolutePos(new BlockPos(10, 2, 10)).getCenter());
            boolean joined = h.getLevel().addFreshEntity(copy);
            ember.assertTrue(!joined && h.getLevel().getEntity(copy.getUUID()) == null, "a second test rig joined the level");
            ember.assertTrue(countRigs(h) == 1, "living test rigs: " + countRigs(h));
            // released: its entity leaves; a copy of exactly that entity loading later is turned away too
            CompoundTag saved = second.saveWithoutId(new CompoundTag());
            HeroManager.Result r = HeroManager.release(server, "test_rig");
            ember.assertTrue(second.isRemoved() && s.owner == null && !s.alive, "release: " + r.message());
            ember.assertTrue(!grove.data.faction(FactionType.GROVE_COVENANT).roster.containsKey(second.getUUID()), "Grove kept its roster entry");
            HeroEntity again = ModEntities.HERO.get().create(h.getLevel());
            again.load(saved);
            ember.assertTrue(again.getUUID().equals(second.getUUID()) && again.heroId().equals("test_rig"), "the saved copy did not load");
            ember.assertTrue(!h.getLevel().addFreshEntity(again), "a copy of the released hero joined the level");
            ember.assertTrue(countRigs(h) == 0, "living test rigs after the release: " + countRigs(h));
            grove.cleanup();
            ember.succeed();
        } catch (RuntimeException | Error e) {
            grove.cleanup();
            ember.cleanup();
            throw e;
        }
    }

    private static long countRigs(GameTestHelper h) {
        return h.getLevel().getEntities(ModEntities.HERO.get(), e -> e.isAlive() && e.heroId().equals("test_rig")).size();
    }

    /** The test rig, given to a faction, fights with its basic attacks: it finds a husk on its own and hits it. */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_fight")
    public static void heroFightsWithBasicAttacks(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 300);
        MinecraftServer server = h.getLevel().getServer();
        t.onCleanup(() -> HeroManager.forget(server, "test_rig"));
        try {
            HeroEntity hero = give(t, "test_rig", 6, 6);
            Husk husk = t.track(h.spawn(EntityType.HUSK, new BlockPos(11, 2, 6)));
            husk.setNoAi(true);
            float start = husk.getHealth();
            t.onEachTick(() -> {
                if (husk.getLastHurtByMob() == hero && husk.getHealth() < start || husk.isDeadOrDying() && husk.getKillCredit() == hero) {
                    t.assertTrue(hero.getTarget() == husk || !husk.isAlive(), "it hit the husk without targeting it");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The test rig saves and loads: the entity's NBT (FcHero id, faction, role, level and traits, size, kit) and the
     * campaign record (owner, base, UUID, alive, level, the roster's hero id) come back the same.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "hero_save")
    public static void heroSavesAndReloads(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 40);
        MinecraftServer server = h.getLevel().getServer();
        t.onCleanup(() -> HeroManager.forget(server, "test_rig"));
        try {
            HeroEntity hero = give(t, "test_rig", 6, 6);
            hero.setNoAi(true);
            Map<Affix, Integer> traits = new EnumMap<>(Affix.class);
            traits.put(Affix.VITALITY, 1);
            hero.restoreProgress(3, 40, traits);
            HeroManager.tick(server); // the campaign record follows the living hero's level
            CompoundTag tag = hero.saveWithoutId(new CompoundTag());
            t.assertTrue(tag.getCompound("FcHero").getString("id").equals("test_rig"), "FcHero " + tag.getCompound("FcHero"));
            HeroEntity loaded = ModEntities.HERO.get().create(h.getLevel());
            loaded.load(tag);
            t.assertTrue(loaded.heroId().equals("test_rig") && loaded.getUUID().equals(hero.getUUID()), "id or UUID lost");
            t.assertTrue(loaded.getFaction() == FactionType.STONEGUARD && loaded.getRole() == NpcRole.SWORDSMAN && loaded.baseId() == t.base.id,
                    "faction, role or base lost");
            t.assertTrue(loaded.npcLevel() == 3 && loaded.xp() == 40 && loaded.affixes().getOrDefault(Affix.VITALITY, 0) == 1, "progress lost");
            t.assertTrue(loaded.npcName().equals("Test Rig"), "name " + loaded.npcName());
            near(t, 1.82, loaded.getBbHeight(), "size after loading");
            t.assertTrue(HeroKit.isKit(loaded.getMainHandItem()), "the kit weapon lost its binding");
            CompoundTag campaign = t.data.save(new CompoundTag(), h.getLevel().registryAccess());
            t.assertTrue(campaign.getInt("format") == CampaignData.FORMAT && CampaignData.FORMAT == 3, "format " + campaign.getInt("format"));
            CampaignData back = CampaignData.load(campaign, h.getLevel().registryAccess());
            HeroState s = back.heroes.get("test_rig");
            t.assertTrue(s != null && s.owner == FactionType.STONEGUARD && s.baseId == t.base.id && hero.getUUID().equals(s.uuid) && s.alive
                    && s.level == 3 && s.xp == 40 && s.affixes.getOrDefault(Affix.VITALITY, 0) == 1, "the campaign record did not survive");
            NpcRecord r = back.faction(FactionType.STONEGUARD).roster.get(hero.getUUID());
            t.assertTrue(r != null && r.heroId.equals("test_rig"), "the roster's hero id did not survive");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A hero that falls drops nothing of its kit and returns after heroes.reviveSeconds at its base's spawn point, as a
     * new entity (a new UUID, its old roster entry replaced) with its kit, level and traits; a released hero cannot be
     * revived.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_revive")
    public static void heroDiesAndRevives(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 100);
        MinecraftServer server = h.getLevel().getServer();
        try {
            fixture(t, "fixture_revive", "katana", 52, 42, 1.0);
            HeroEntity hero = give(t, "fixture_revive", 6, 6);
            hero.setNoAi(true);
            hero.restoreProgress(2, 10, new EnumMap<>(Affix.class));
            UUID firstId = hero.getUUID();
            long now = h.getLevel().getGameTime();
            hero.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            HeroState s = t.data.heroes.get("fixture_revive");
            t.assertTrue(hero.isDeadOrDying() && !s.alive && s.deaths == 1, "the hero did not fall");
            long expected = now + FCConfig.HERO_REVIVE_SECONDS.get() * 20L;
            t.assertTrue(Math.abs(s.reviveAt - expected) <= 1, "returns at " + s.reviveAt + ", expected " + expected);
            NpcRecord old = t.data.faction(FactionType.TIDE_ALLIANCE).roster.get(firstId);
            t.assertTrue(old != null && !old.alive, "the roster entry should be dead");
            AABB around = new AABB(hero.blockPosition()).inflate(4);
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
                t.assertTrue(!HeroKit.isKit(item.getItem()) && !item.getItem().is(Items.STONE_SWORD), "the kit dropped: " + item.getItem());
            }
            HeroManager.tick(server);
            t.assertTrue(HeroManager.entity(server, "fixture_revive") == null, "it came back before its time");
            s.reviveAt = h.getLevel().getGameTime(); // its ten minutes are over
            HeroManager.tick(server);
            HeroEntity back = HeroManager.entity(server, "fixture_revive");
            t.assertTrue(back != null && back.isAlive() && s.alive && s.reviveAt == 0, "the hero did not return");
            back.setNoAi(true);
            t.assertTrue(!back.getUUID().equals(firstId), "a returned hero is a new entity");
            t.assertTrue(back.blockPosition().equals(t.base.point(BaseState.SPAWN)), "returned at " + back.blockPosition() + ", not the spawn point");
            t.assertTrue(back.getHealth() == back.getMaxHealth() && HeroKit.isKit(back.getMainHandItem()), "health or kit missing");
            t.assertTrue(back.npcLevel() == 2 && back.xp() == 10, "progress lost: level " + back.npcLevel());
            var roster = t.data.faction(FactionType.TIDE_ALLIANCE).roster;
            t.assertTrue(!roster.containsKey(firstId) && roster.get(back.getUUID()) != null && roster.get(back.getUUID()).alive
                    && roster.get(back.getUUID()).isHero(), "roster not updated");
            t.assertTrue(!HeroManager.revive(server, "fixture_revive").message().isEmpty() && HeroManager.revive(server, "fixture_revive").hero() == back,
                    "reviving a living hero changed it");
            HeroManager.release(server, "fixture_revive");
            t.assertTrue(back.isRemoved() && !HeroManager.revive(server, "fixture_revive").ok(), "a released hero was revived");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
