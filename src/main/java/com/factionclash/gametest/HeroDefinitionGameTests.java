package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.HeroState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroKit;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.Affix;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroSpeech;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * Heroes, task A1b: the entity uses its definition. The test rig and fixtures with a format-2 definition give their
 * stats, boss health (the same whoever engages the hero), role, kit (a fists hero holds nothing), name tag and lines; a
 * definition that breaks or goes leaves its hero dormant; a reload applies a changed definition at once; the revival
 * block and the {@code revival_rite} talent shorten the ten minutes, never under 30 s; the war council counts a hero by
 * its war power; {@code /fc hero check} reports a broken file. Every test that changes the registry or runs a hero has
 * a batch of its own (the registry is shared); fixtures go away with the test.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroDefinitionGameTests {
    private HeroDefinitionGameTests() {
    }

    private static final double EPS = 1e-3;

    private static String visual(String style) {
        return "{\"form\":\"adult\",\"build\":\"standard\",\"base\":\"adult_f\",\"style\":\"" + style + "\",\"height_px\":52,"
                + "\"head_y\":42,\"render_scale\":0.56,\"scale\":1.0,\"animation_chain\":[\"_fallback\",\"_style_fists\",\"_core\"],"
                + "\"texture\":{\"width\":256,\"height\":128}}";
    }

    /** A fixture with a minimal format-2 definition ({@code more}: extra top-level fields), removed by the cleanup. */
    private static HeroRegistry.Transient fixture(GameTestBases.Handle t, String id, String style, String more) {
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, visual(style),
                HeroRegistry.fixtureDefinition(id, com.factionclash.logic.hero.HeroIdentity.nameFromId(id), style, more));
        MinecraftServer server = t.helper.getLevel().getServer();
        t.onCleanup(() -> {
            HeroManager.forget(server, id);
            fx.close();
        });
        return fx;
    }

    private static HeroEntity give(GameTestBases.Handle t, String id, int x, int z) {
        HeroManager.Result r = HeroManager.give(t.helper.getLevel().getServer(), id, t.base.faction, t.base,
                t.helper.absolutePos(new BlockPos(x, 2, z)));
        if (!r.ok()) {
            t.fail("give failed: " + r.message());
            throw new GameTestAssertException(r.message());
        }
        return r.hero();
    }

    private static void near(GameTestBases.Handle t, double expected, double actual, String what) {
        t.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + ", got " + actual);
    }

    /** A rare striker at level 1 (the fixtures' definition): 28 x 300% x 1.2. */
    private static final double FIXTURE_MAX = 28 * 3.0 * 1.2;

    private static String tag(HeroEntity h) {
        return h.getCustomName() == null ? "" : h.getCustomName().getString();
    }

    /** Runs a command as an operator and returns what it printed. */
    private static List<String> command(GameTestHelper h, String cmd) {
        List<String> out = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component c) {
                out.add(c.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        ServerLevel level = h.getLevel();
        CommandSourceStack src = new CommandSourceStack(sink, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)), Vec2.ZERO, level, 4,
                "hero-test", Component.literal("hero-test"), level.getServer(), null);
        level.getServer().getCommands().performPrefixedCommand(src, cmd);
        return out;
    }

    // ------------------------------------------------------------------ the definition on the entity

    /**
     * The test rig's definition on its entity: boss health from spawn (28 x 250% x 1.2 at level 1: a common striker),
     * the attack, armour and knock-back of its file, its kit (the iron "Test Blade", bound), a swordsman, the name tag
     * {@code ★ Test Rig · the Test Model} and its {@code summoned} line with a speech bubble.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_def_rig")
    public static void heroTakesItsDefinition(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 100);
        MinecraftServer server = h.getLevel().getServer();
        t.onCleanup(() -> HeroManager.forget(server, "test_rig"));
        try {
            HeroEntity hero = give(t, "test_rig", 6, 6);
            hero.setNoAi(true);
            double max = 28 * 2.5 * 1.2;
            near(t, max, hero.getMaxHealth(), "boss health");
            near(t, max, hero.getHealth(), "full at the spawn");
            near(t, 28, hero.getAttribute(Attributes.MAX_HEALTH).getBaseValue(), "the attribute's base is the v1 health");
            var boss = hero.getAttribute(Attributes.MAX_HEALTH).getModifier(HeroEntity.BOSS_HEALTH);
            t.assertTrue(boss != null, "no boss health modifier");
            near(t, BossRules.healthModifier(250, 1.2), boss.amount(), "the boss modifier");
            near(t, 2, hero.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(HeroEntity.HERO_ATTACK).amount(), "a striker's attack");
            near(t, 0.10, hero.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getValue(), "a striker's knock-back resistance");
            t.assertTrue(hero.getMainHandItem().is(Items.IRON_SWORD) && HeroKit.isKit(hero.getMainHandItem())
                    && hero.getMainHandItem().getHoverName().getString().equals("Test Blade"), "kit " + hero.getMainHandItem());
            t.assertTrue(hero.getRole() == NpcRole.SWORDSMAN && !hero.dormant() && !hero.fightsBareHanded(), "role " + hero.getRole());
            t.assertTrue(tag(hero).startsWith(HeroSpeech.STAR + " Test Rig · the Test Model") && !tag(hero).contains("dormant"),
                    "name tag " + tag(hero));
            t.assertTrue(hero.lastLine().equals("Test Rig, reporting to the " + FactionType.STONEGUARD.displayName + "."),
                    "summoned line: " + hero.lastLine());
            t.assertTrue(hero.isTalking(), "the face talks while the line shows");
            Display.TextDisplay bubble = hero.bubble();
            t.assertTrue(bubble != null && bubble.getTags().contains(HeroEntity.BUBBLE_TAG), "no speech bubble");
            t.assertTrue(bubble.getY() > hero.getY() + hero.getBbHeight(), "the bubble is above the head");
            // the rate limit (v1 23): a line right after another waits; fallen, revived and greetings do not
            t.assertTrue(!hero.say(LineEvent.VICTORY), "a second line within 20 s");
            t.assertTrue(hero.say(LineEvent.GREETING, java.util.Map.of("player", "Tester"), null)
                    && hero.lastLine().equals("Hello, Tester. I am only a test."), "greeting: " + hero.lastLine());
            t.assertTrue(!hero.say(LineEvent.GREETING, java.util.Map.of("player", "Tester"), null), "two greetings within 5 s");
            h.runAtTickTime(HeroSpeech.BUBBLE_TICKS + 5, t.guard(() -> {
                t.assertTrue(hero.bubble() == null && bubble.isRemoved(), "the bubble outlived its 3 s");
                t.assertTrue(!hero.isTalking(), "still talking after the bubble");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A fists-style hero holds no weapon (the reader's stone-sword placeholder is left out), never goes for one, takes
     * no storehouse weapon, and fights with its bare hands: it hurts a husk.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_def_fists")
    public static void fistsHeroHoldsNothingAndFightsBareHanded(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 300);
        try {
            fixture(t, "fixture_fists", "fists", "");
            HeroEntity hero = give(t, "fixture_fists", 6, 6);
            t.assertTrue(hero.getMainHandItem().isEmpty(), "a fists hero holds " + hero.getMainHandItem());
            t.assertTrue(hero.fightsBareHanded() && !hero.needsRoleTool() && !hero.takesStoreWeapons() && !hero.takesShield(),
                    "a fists hero would fetch a weapon");
            t.assertTrue(hero.getRole() == NpcRole.SWORDSMAN, "role " + hero.getRole());
            Husk husk = t.track(h.spawn(EntityType.HUSK, new BlockPos(10, 2, 6)));
            husk.setNoAi(true);
            float start = husk.getHealth();
            t.onEachTick(() -> {
                t.assertTrue(hero.getMainHandItem().isEmpty(), "it picked up " + hero.getMainHandItem());
                if (husk.getLastHurtByMob() == hero && husk.getHealth() < start || husk.isDeadOrDying()) {
                    t.assertTrue(hero.energy() > 0, "a landed basic hit gives energy");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Equal treatment (HEROES_V2 6.1): a hero's health is its boss health from the spawn, and it is the same whether
     * an NPC soldier or a player engages it: the maximum does not change, and the same blow takes the same health.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_equal")
    public static void heroHealthIsTheSameForAPlayerOrAnNpc(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 60);
        try {
            fixture(t, "fixture_equal", "katana", "");
            HeroEntity hero = give(t, "fixture_equal", 6, 6);
            hero.setNoAi(true);
            near(t, FIXTURE_MAX, hero.getMaxHealth(), "boss health with nobody engaged");
            FactionNpc soldier = t.track(h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(8, 2, 6)));
            soldier.faction(FactionType.GROVE_COVENANT);
            soldier.role(NpcRole.SWORDSMAN);
            soldier.setNoAi(true);
            hero.setTarget(soldier);
            float before = hero.getHealth();
            hero.hurt(h.getLevel().damageSources().mobAttack(soldier), 4F);
            float byNpc = before - hero.getHealth();
            near(t, FIXTURE_MAX, hero.getMaxHealth(), "boss health with an NPC engaged");
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            hero.setTarget(player);
            hero.invulnerableTime = 0;
            before = hero.getHealth();
            hero.hurt(h.getLevel().damageSources().playerAttack(player), 4F);
            float byPlayer = before - hero.getHealth();
            near(t, FIXTURE_MAX, hero.getMaxHealth(), "boss health with a player engaged");
            t.assertTrue(byNpc > 0 && Math.abs(byNpc - byPlayer) < EPS, "the same blow took " + byNpc + " from an NPC and "
                    + byPlayer + " from a player");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ dormant and reload

    /**
     * A hero whose definition is removed, or breaks, becomes dormant (HEROES_V2 3.4): it keeps its entity, faction,
     * role and share of health, fights as a plain soldier (20 health) with the neutral model, and its tag says
     * {@code (dormant)}; it cannot be given while dormant; a good definition brings it back at once.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_dormant")
    public static void heroIsDormantWithoutAValidDefinition(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        String id = "fixture_dormant";
        List<HeroRegistry.Transient> handles = new ArrayList<>();
        t.onCleanup(() -> {
            HeroManager.forget(server, id);
            handles.forEach(HeroRegistry.Transient::close);
        });
        try {
            handles.add(HeroRegistry.registerTransient(id, visual("katana"), HeroRegistry.fixtureDefinition(id, "Fixture Dormant", "katana", "")));
            HeroEntity hero = give(t, id, 6, 6);
            hero.setNoAi(true);
            UUID uuid = hero.getUUID();
            hero.setHealth((float) (FIXTURE_MAX / 2));
            // the definition is removed
            handles.add(HeroRegistry.registerTransient(id, visual("katana"), null));
            HeroManager.refreshAll(server);
            t.assertTrue(hero.dormant() && HeroRegistry.get(id).dormant(), "not dormant without a definition");
            t.assertTrue(hero.entry().id().equals(com.factionclash.logic.hero.HeroIdentity.FALLBACK), "the neutral model: " + hero.entry().id());
            t.assertTrue(tag(hero).contains(HeroSpeech.DORMANT), "name tag " + tag(hero));
            near(t, 20, hero.getMaxHealth(), "a plain soldier's health");
            near(t, 10, hero.getHealth(), "its share of health kept");
            t.assertTrue(hero.isAlive() && !hero.isRemoved() && hero.getUUID().equals(uuid) && hero.getRole() == NpcRole.SWORDSMAN
                    && hero.getFaction() == FactionType.TIDE_ALLIANCE, "it kept its entity, role and faction");
            HeroState s = t.data.heroes.get(id);
            t.assertTrue(s != null && s.alive && s.owner == FactionType.TIDE_ALLIANCE, "its record changed");
            t.assertTrue(!HeroManager.give(server, id, FactionType.EMBER_LEGION, null, null).ok(), "a dormant hero was given");
            // a broken definition: still dormant, and the report says why
            handles.add(HeroRegistry.registerTransient(id, visual("katana"), "{\"format\": 2, \"id\": \"" + id + "\""));
            HeroManager.refreshAll(server);
            t.assertTrue(hero.dormant(), "a broken definition brought it back");
            t.assertTrue(HeroRegistry.report().problems().all().stream().anyMatch(p -> p.file().equals("fixture/" + id + ".json")
                    && p.severity() == com.factionclash.logic.hero.Problems.Severity.NOT_LOADED), "the broken file is not reported");
            // fixed: back at once, with its share of the boss health
            handles.add(HeroRegistry.registerTransient(id, visual("katana"), HeroRegistry.fixtureDefinition(id, "Fixture Dormant", "katana", "")));
            HeroManager.refreshAll(server);
            t.assertTrue(!hero.dormant() && !tag(hero).contains(HeroSpeech.DORMANT), "still dormant: " + tag(hero));
            near(t, FIXTURE_MAX, hero.getMaxHealth(), "boss health again");
            near(t, FIXTURE_MAX / 2, hero.getHealth(), "the share kept again");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code /fc hero reload}: an operator's override in {@code config/factionclash/heroes/<id>.json} and a changed
     * definition apply to the living hero at once (title, numbers with the share of health kept, the tag); removing the
     * override puts the hero back.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_reload")
    public static void reloadAppliesAChangedDefinitionAtOnce(GameTestHelper h) throws IOException {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        String id = "fixture_reload";
        Path dir = FMLPaths.CONFIGDIR.get().resolve(FactionClash.MODID).resolve("heroes");
        Path override = dir.resolve(id + ".json");
        List<HeroRegistry.Transient> handles = new ArrayList<>();
        t.onCleanup(() -> {
            try {
                Files.deleteIfExists(override);
            } catch (IOException ignored) {
                // nothing to undo
            }
            HeroManager.forget(server, id);
            handles.forEach(HeroRegistry.Transient::close);
            HeroRegistry.reload();
        });
        try {
            handles.add(HeroRegistry.registerTransient(id, visual("katana"), HeroRegistry.fixtureDefinition(id, "Fixture Reload", "katana", "")));
            HeroEntity hero = give(t, id, 6, 6);
            hero.setNoAi(true);
            hero.setHealth((float) (FIXTURE_MAX / 2));
            Files.createDirectories(dir);
            Files.writeString(override, "{\"title\": \"the Overridden\", \"stats\": {\"health\": 40}}");
            List<String> said = command(h, "fc hero reload");
            t.assertTrue(said.stream().anyMatch(l -> l.startsWith("Heroes:")), "reload said " + said);
            t.assertTrue(HeroRegistry.get(id).title().equals("the Overridden") && HeroRegistry.get(id).overrides().equals(List.of("title", "stats")),
                    "the override did not apply: " + HeroRegistry.get(id).overrides());
            t.assertTrue(tag(hero).contains("the Overridden"), "name tag " + tag(hero));
            near(t, 40 * 3 * 1.2, hero.getMaxHealth(), "the override's health, as boss health");
            near(t, 40 * 3 * 1.2 / 2, hero.getHealth(), "its share kept");
            Files.delete(override);
            // a changed definition: another name and a bow, applied on the next reload
            handles.add(HeroRegistry.registerTransient(id, visual("bow"), HeroRegistry.fixtureDefinition(id, "Reloaded Archer", "bow", "")));
            command(h, "fc hero reload");
            t.assertTrue(HeroRegistry.get(id).title().isEmpty() && hero.npcName().equals("Reloaded Archer"), "name " + hero.npcName());
            t.assertTrue(hero.getRole() == NpcRole.ARCHER && hero.getMainHandItem().is(Items.BOW) && HeroKit.isKit(hero.ammo()),
                    "the new kit and role: " + hero.getRole() + " " + hero.getMainHandItem());
            near(t, 24 * 3 * 1.3, hero.getMaxHealth(), "a marksman's boss health");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** The A1b row's "done when": {@code /fc hero check} reports a broken file, and the sheet of a good one. */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "hero_def_check")
    public static void heroCheckReportsABrokenFile(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 40);
        String id = "fixture_broken";
        HeroRegistry.Transient fx = HeroRegistry.registerTransient(id, visual("katana"),
                "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"Broken\", \"behaviour\": {\"combat_role\": \"wizard\"}}");
        t.onCleanup(fx::close);
        try {
            List<String> out = command(h, "fc hero check " + id);
            t.assertTrue(out.stream().anyMatch(l -> l.contains("fixture/" + id + ".json") && l.contains("NOT LOADED")),
                    "check did not report the broken file: " + out);
            List<String> rig = command(h, "fc hero check test_rig");
            t.assertTrue(rig.stream().anyMatch(l -> l.contains("nothing to report")), "the test rig: " + rig);
            t.assertTrue(rig.stream().anyMatch(l -> l.contains("level 1: 84 health")), "the sheet: " + rig);
            t.assertTrue(HeroRegistry.get(id) != null && HeroRegistry.get(id).dormant(), "a broken fixture is dormant");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ revival

    /**
     * A hero's own revival block (HEROES_V2 12.3, 3.4) sets its delay (120 s instead of 600) and where it rises (where
     * it fell, not at home).
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_revival")
    public static void revivalBlockShortensTheReturn(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        try {
            fixture(t, "fixture_phoenix", "katana", ", \"revival\": {\"seconds\": 120, \"where\": \"where_it_fell\"}");
            HeroEntity hero = give(t, "fixture_phoenix", 9, 6);
            hero.setNoAi(true);
            BlockPos fell = hero.blockPosition();
            long now = h.getLevel().getGameTime();
            hero.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            HeroState s = t.data.heroes.get("fixture_phoenix");
            t.assertTrue(!s.alive && Math.abs(s.reviveAt - (now + 120 * 20)) <= 1, "returns at " + s.reviveAt + ", expected "
                    + (now + 120 * 20));
            t.assertTrue(fell.equals(s.fellAt), "fell at " + s.fellAt);
            s.reviveAt = h.getLevel().getGameTime();
            HeroManager.tick(server);
            HeroEntity back = HeroManager.entity(server, "fixture_phoenix");
            t.assertTrue(back != null && back.blockPosition().equals(fell) && !fell.equals(t.base.point(BaseState.SPAWN)),
                    "it should rise where it fell: " + (back == null ? "not back" : back.blockPosition()));
            t.assertTrue(s.revivals == 1 && s.deaths == 1, "revivals " + s.revivals);
            back.setNoAi(true);
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * The {@code revival_rite} talent (HEROES_V2 7.2, 3.4) brings the faction's fallen heroes back sooner, by its
     * holder's tier, never under 30 s: a level-20 holder (III, 40%) makes another hero's 600 s 360 and a 40 s own
     * revival 30 (not 24); a faction without the talent keeps 600.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_rite")
    public static void revivalRiteShortensWithTheThirtySecondFloor(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 60);
        GameTestBases.Handle other = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        try {
            fixture(t, "fixture_rite", "katana", ", \"talents\": [{\"id\": \"revival_rite\"}]");
            fixture(t, "fixture_quick", "katana", ", \"revival\": {\"seconds\": 40}");
            fixture(t, "fixture_plain", "katana", "");
            fixture(other, "fixture_alone", "katana", "");
            HeroEntity rite = give(t, "fixture_rite", 6, 6);
            HeroEntity quick = give(t, "fixture_quick", 8, 6);
            HeroEntity plain = give(t, "fixture_plain", 10, 6);
            HeroEntity alone = give(other, "fixture_alone", 6, 10);
            for (HeroEntity e : List.of(rite, quick, plain, alone)) {
                e.setNoAi(true);
            }
            HeroState rs = t.data.heroes.get("fixture_rite");
            t.assertTrue(HeroManager.riteTier(t.data, FactionType.GROVE_COVENANT) == 1, "tier I at level 1");
            t.assertTrue(HeroManager.reviveSeconds(t.data, t.data.heroes.get("fixture_plain")) == 480, "600 x 0.8");
            rite.restoreProgress(20, 0, new EnumMap<>(Affix.class));
            HeroManager.tick(server); // the record follows its level
            t.assertTrue(rs.level == 20 && HeroManager.riteTier(t.data, FactionType.GROVE_COVENANT) == 3, "tier III at level 20");
            long now = h.getLevel().getGameTime();
            plain.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            quick.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            alone.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            t.assertTrue(Math.abs(t.data.heroes.get("fixture_plain").reviveAt - (now + 360 * 20)) <= 1, "600 s x 0.6 = 360 s");
            t.assertTrue(Math.abs(t.data.heroes.get("fixture_quick").reviveAt - (now + HeroRules.MIN_REVIVE_SECONDS * 20)) <= 1,
                    "its own 40 s x 0.6 = 24 s, raised to the 30 s floor: " + (t.data.heroes.get("fixture_quick").reviveAt - now) / 20);
            t.assertTrue(Math.abs(t.data.heroes.get("fixture_alone").reviveAt - (now + FCConfig.HERO_REVIVE_SECONDS.get() * 20L)) <= 1,
                    "another faction's rite does nothing");
            rite.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            t.assertTrue(Math.abs(rs.reviveAt - (now + 360 * 20)) <= 1, "the holder's own return is shortened too");
            other.cleanup();
            t.succeed();
        } catch (RuntimeException | Error e) {
            other.cleanup();
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the war council

    /**
     * HEROES_V2 8.1: a base's defence power is its soldiers at home times the faction's soldier power plus its home
     * heroes' war power (by heads a hero is no defender), the heroes at most 1.5 times the soldiers; half under half
     * health; nothing while it is away on a raid.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_def_war")
    public static void warCouncilCountsHeroPower(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(FactionType.EMBER_LEGION);
        float oldPower = f.soldierPower;
        t.onCleanup(() -> {
            f.soldierPower = oldPower;
            f.raiders.clear();
        });
        try {
            fixture(t, "fixture_war", "katana", "");
            for (int i = 0; i < 6; i++) {
                t.npc(10 + i % 3, 10 + i / 3, NpcRole.SWORDSMAN).setNoAi(true);
            }
            f.soldierPower = 2.25F;
            double soldiers = 6 * 2.25;
            near(t, soldiers, FactionDirector.defencePower(level, t.data, t.base), "six iron soldiers, no hero");
            HeroEntity hero = give(t, "fixture_war", 6, 6);
            hero.setNoAi(true);
            hero.restoreProgress(10, 0, new EnumMap<>(Affix.class));
            double power = HeroRules.heroPower(hero.getMaxHealth(), 10, FCConfig.HERO_POWER_PERCENT.get());
            t.assertTrue(power > 2.25 * 2, "a level-10 hero is worth several soldiers: " + power);
            near(t, power, FactionDirector.heroDefence(level, t.data, t.base), "the hero's war power at home");
            near(t, HeroRules.countedPower(soldiers, power), FactionDirector.defencePower(level, t.data, t.base),
                    "soldiers by heads x their power + the hero's war power (not one more head)");
            hero.setHealth(hero.getMaxHealth() * 0.4F);
            near(t, power / 2, FactionDirector.heroDefence(level, t.data, t.base), "half under half health");
            f.raiders.add(hero.getUUID());
            near(t, 0, FactionDirector.heroDefence(level, t.data, t.base), "away on a raid it defends nothing");
            near(t, soldiers, FactionDirector.defencePower(level, t.data, t.base), "and the soldiers alone remain");
            f.raiders.clear();
            // the soldier backbone: with one soldier left the heroes count at most 1.5 times its power
            t.assertTrue(HeroRules.countedPower(2.25, power) <= 2.25 * 2.5 + EPS, "the backbone cap");
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** The speech bubble a stopped server saved is dropped when its chunk loads again. */
    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void savedSpeechBubbleIsDropped(GameTestHelper h) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", "\"left over\"");
        tag.putString("alignment", "center");
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(HeroEntity.BUBBLE_TAG));
        tag.put("Tags", tags);
        var e = EntityType.loadEntityRecursive(tag, h.getLevel(), x -> x);
        h.assertTrue(e != null, "no text display");
        e.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 3, 3))));
        h.getLevel().addLegacyChunkEntities(java.util.stream.Stream.of(e));
        h.assertTrue(h.getLevel().getEntity(e.getUUID()) == null, "a saved speech bubble came back");
        var stray = h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(8),
                d -> d.getTags().contains(HeroEntity.BUBBLE_TAG));
        h.assertTrue(stray.isEmpty(), "stray bubbles " + stray.size());
        h.succeed();
    }
}
