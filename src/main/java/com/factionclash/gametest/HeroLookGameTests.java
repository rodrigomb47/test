package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroLook;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.hero.HeroTimingFiles;
import com.factionclash.logic.hero.BlinkSchedule;
import com.factionclash.logic.hero.FaceRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MoodRules;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Heroes, task A2 (HEROES_V2 4.4-4.9, 15.2): what the server gives the client model. The test rig's look (bone groups,
 * face table, blink) and its timing files load on the server; a preview fixture is never given to a faction; a
 * preview copy is a display copy beside the campaign's hero; an operator's pose drives the synced fields and a played
 * action is cleared once its clip has played (at its speed); fighting draws the weapon and sets the mood, a hit shows
 * the wince. The client side (frames, blinking, controllers, layers) is checked in the Xvfb viewer (HANDOFF 4).
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroLookGameTests {
    private HeroLookGameTests() {
    }

    private static void fail(GameTestBases.Handle t, String message) {
        t.fail(message);
        throw new GameTestAssertException(message);
    }

    /**
     * The test rig's look and timings, as the server reads them: tint, weapon, phase and fine bone groups; a 12-mood
     * face table with its blush; the normal blink; the katana swing's hit at tick 10 written for the action
     * controller's 2-tick blend, the walk for the base controller's 4. A preview fixture is refused by give.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void heroLookAndTimingsLoad(GameTestHelper h) {
        HeroEntry rig = HeroRegistry.get("test_rig");
        h.assertTrue(rig != null, "the test rig is not loaded");
        HeroLook look = rig.visual().look();
        h.assertTrue(look.tint().containsAll(List.of("sash", "fine_sash")), "tint bones " + look.tint());
        h.assertTrue(look.weaponDrawn().equals(List.of("weapon_r")) && look.weaponSheathed().equals(List.of("weapon_back")),
                "weapon bones " + look.weaponDrawn() + " / " + look.weaponSheathed());
        h.assertTrue(Integer.valueOf(2).equals(look.phaseBones().get("p2_halo")) && look.phaseOf("p2_halo") == 2, "the halo is a phase-2 bone");
        h.assertTrue(!look.fine().isEmpty() && look.fine().stream().allMatch(b -> b.startsWith("fine_")), "fine bones " + look.fine());
        h.assertTrue(look.face().moods().size() == 12, "12 moods");
        FaceRules.Frames happy = look.face().moods().get(HeroActions.moodCode("happy"));
        h.assertTrue(happy.equals(new FaceRules.Frames(FaceRules.EYES_HAPPY, FaceRules.MOUTH_SMILE, FaceRules.CHEEKS_BLUSH)),
                "the rig's happy face blushes: " + happy);
        h.assertTrue(look.face().blink().equals(BlinkSchedule.Profile.NORMAL), "blink " + look.face().blink());
        h.assertTrue(look.face().eyes().equals(FaceRules.Region.EYES) && look.face().mouth().equals(FaceRules.Region.MOUTH),
                "face regions " + look.face().eyes() + " " + look.face().mouth());
        h.assertTrue(look.phases().size() == 1 && look.phases().get(0).eyesGlow(), "the phase-2 look glows the eyes");
        HeroTimings.Clip swing = HeroTimingFiles.clip(rig, "attack.1");
        h.assertTrue(swing != null && swing.firstHit() == 10 && swing.transitionTicks() == HeroClips.ACTION_TRANSITION
                && swing.totalTicks() == 20, "attack.1 timing " + swing);
        HeroTimings.Clip walk = HeroTimingFiles.clip(rig, "move.walk");
        h.assertTrue(walk != null && walk.loops() && walk.transitionTicks() == HeroClips.BASE_TRANSITION, "move.walk timing " + walk);
        h.assertTrue(HeroTimingFiles.clip(rig, "attack.9") == null, "an unknown clip has no timing");
        // a preview fixture (a resource pack's visual on a client, the config folder on a server) is never given
        String json = "{\"style\":\"fists\",\"height_px\":45,\"head_y\":35,\"render_scale\":0.56,\"scale\":1,"
                + "\"animation_chain\":[\"fixture_look_only\",\"_style_fists\",\"_core\"]}";
        HeroRegistry.setClientEntries(Map.of("fixture_look_only",
                HeroRegistry.fixtureEntry("fixture_look_only", JsonParser.parseString(json).getAsJsonObject(), null, "test")));
        try {
            HeroEntry fx = HeroRegistry.get("fixture_look_only");
            h.assertTrue(fx != null && fx.previewOnly(), "the fixture is not registered as preview-only");
            HeroManager.Result r = HeroManager.give(h.getLevel().getServer(), "fixture_look_only", FactionType.EMBER_LEGION, null, null);
            h.assertTrue(!r.ok() && r.message().contains("preview fixture"), "a preview fixture was given: " + r.message());
        } finally {
            HeroRegistry.setClientEntries(Map.of());
        }
        h.succeed();
    }

    /**
     * A preview is a display copy beside the campaign's hero: both stand, the copy has no AI, cannot be hurt or
     * targeted, has no roster entry and is never counted, keeps still, and clearing the previews leaves the hero.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_preview")
    public static void heroPreviewIsADisplayCopy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 80);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        t.onCleanup(() -> {
            HeroManager.clearPreviews(level, "test_rig");
            HeroManager.forget(server, "test_rig");
        });
        try {
            HeroManager.Result given = HeroManager.give(server, "test_rig", FactionType.EMBER_LEGION, t.base, h.absolutePos(new BlockPos(6, 2, 6)));
            if (!given.ok()) {
                fail(t, "give failed: " + given.message());
            }
            HeroEntity hero = given.hero();
            hero.setNoAi(true);
            BlockPos at = h.absolutePos(new BlockPos(10, 2, 10));
            HeroManager.Result r = HeroManager.preview(level, "test_rig", FactionType.GROVE_COVENANT, at, 90F);
            t.assertTrue(r.ok(), "preview failed: " + r.message());
            HeroEntity p = r.hero();
            t.assertTrue(p.isAlive() && p.isPreview() && p.isNoAi() && p.isInvulnerable() && p.isDisplayOnly() && !p.canBeSeenAsEnemy(),
                    "the preview is not a display copy");
            t.assertTrue(hero.isAlive() && !hero.isRemoved() && !hero.isPreview(), "the preview turned the campaign's hero away");
            HeroState s = t.data.heroes.get("test_rig");
            t.assertTrue(s != null && hero.getUUID().equals(s.uuid), "the campaign record moved to the preview");
            t.assertTrue(!t.data.faction(FactionType.GROVE_COVENANT).roster.containsKey(p.getUUID())
                    && !t.data.faction(FactionType.EMBER_LEGION).roster.containsKey(p.getUUID()), "the preview has a roster entry");
            t.assertTrue(p.getCustomName() != null && p.getCustomName().getString().endsWith("(preview)"), "name " + p.getCustomName());
            t.assertTrue(p.getFaction() == FactionType.GROVE_COVENANT, "the preview's sash colour comes from its faction");
            t.assertTrue(HeroManager.loaded(level, "test_rig").size() == 2, "two test rig entities expected");
            double x = p.getX(), z = p.getZ();
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(p.isAlive() && Math.abs(p.getX() - x) < 1e-6 && Math.abs(p.getZ() - z) < 1e-6, "the preview moved");
                t.assertTrue(p.hunger().foodLevel() == 20, "a preview does not get hungry: " + p.hunger().foodLevel());
                int n = HeroManager.clearPreviews(level, "test_rig");
                t.assertTrue(n == 1 && p.isRemoved() && hero.isAlive() && !hero.isRemoved(), "clearing removed " + n + " and the hero alive: " + hero.isAlive());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * An operator's pose drives the synced fields the model reads: a forced mood, the weapon flag and a base pose show
     * at once and stay; a played swing is cleared once its clip has played (20 ticks at 1x, 10 at 2x) with a new
     * sequence number; {@code clearPose} puts everything back.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_pose")
    public static void heroPoseDrivesTheSyncedFields(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        HeroManager.Result r = HeroManager.preview(level, "test_rig", FactionType.TIDE_ALLIANCE, h.absolutePos(new BlockPos(8, 2, 8)), 0F);
        h.assertTrue(r.ok(), "preview failed: " + r.message());
        HeroEntity p = r.hero();
        Runnable cleanup = () -> HeroManager.clearPreviews(level, "test_rig");
        h.runAtTickTime(98, cleanup); // a timeout never leaves the preview behind
        int smug = HeroActions.moodCode("smug");
        p.setForcedMood(smug);
        p.setForcedWeapon(1);
        p.setForcedPose(HeroClips.FORCED.indexOf("walk"));
        p.playAction(HeroActions.actionCode("attack.1"), 1.0, 0);
        int seq = HeroActions.sequence(p.action());
        int[] seqAt2x = new int[1];
        h.runAtTickTime(2, () -> {
            try {
                h.assertTrue(HeroActions.mood(p.mood()) == smug, "forced mood " + HeroActions.mood(p.mood()));
                h.assertTrue(p.stateFlag(HeroActions.STATE_WEAPON_DRAWN), "forced weapon");
                h.assertTrue(p.forcedPose() == HeroClips.FORCED.indexOf("walk"), "forced pose");
                h.assertTrue(HeroActions.code(p.action()) == 1, "the swing is playing");
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
        h.runAtTickTime(23, () -> {
            try {
                h.assertTrue(HeroActions.code(p.action()) == HeroActions.CODE_NONE, "the swing was not cleared after its 20 ticks");
                h.assertTrue(HeroActions.sequence(p.action()) == (seq + 1) % 16, "clearing takes a new sequence number");
                h.assertTrue(HeroActions.mood(p.mood()) == smug && p.stateFlag(HeroActions.STATE_WEAPON_DRAWN), "the pose stays");
                p.playAction(HeroActions.actionCode("attack.1"), 2.0, 0);
                seqAt2x[0] = HeroActions.sequence(p.action());
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
        h.runAtTickTime(30, () -> {
            try {
                h.assertTrue(HeroActions.code(p.action()) == 1, "the 2x swing ended early");
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
        h.runAtTickTime(36, () -> {
            try {
                h.assertTrue(HeroActions.code(p.action()) == HeroActions.CODE_NONE && HeroActions.sequence(p.action()) == (seqAt2x[0] + 1) % 16,
                        "the 2x swing was not cleared after 10 ticks");
                p.clearPose();
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
        h.runAtTickTime(40, () -> {
            try {
                h.assertTrue(HeroActions.mood(p.mood()) == MoodRules.CALM && !p.stateFlag(HeroActions.STATE_WEAPON_DRAWN) && p.forcedPose() == 0,
                        "clearPose left mood " + HeroActions.mood(p.mood()) + ", weapon " + p.stateFlag(HeroActions.STATE_WEAPON_DRAWN)
                                + ", pose " + p.forcedPose());
                cleanup.run();
                h.succeed();
            } catch (RuntimeException | Error e) {
                cleanup.run();
                throw e;
            }
        });
    }

    /**
     * The fight drives the face and the weapon (HEROES_V2 4.6 moods, 4.3 weapon bones): with a target the hero draws its
     * weapon and looks determined; a hit shows the hurt wince; without a target the face is calm again at once and the
     * weapon goes back 5 s after its last blow (still out at 2.6 s, away at 6.1 s).
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_flags")
    public static void heroWeaponAndMoodFollowTheFight(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 200);
        MinecraftServer server = h.getLevel().getServer();
        HeroRegistry.Transient fx = HeroRegistry.registerTransient("fixture_flags", "{\"style\":\"katana\",\"height_px\":52,\"head_y\":42,"
                + "\"render_scale\":0.56,\"scale\":1,\"animation_chain\":[\"_fallback\",\"_style_katana\",\"_core\"]}");
        t.onCleanup(() -> {
            HeroManager.forget(server, "fixture_flags");
            fx.close();
        });
        try {
            HeroManager.Result given = HeroManager.give(server, "fixture_flags", FactionType.STONEGUARD, t.base, h.absolutePos(new BlockPos(6, 2, 6)));
            if (!given.ok()) {
                fail(t, "give failed: " + given.message());
            }
            HeroEntity hero = given.hero();
            hero.setNoAi(true);
            Husk husk = t.track(h.spawn(EntityType.HUSK, new BlockPos(9, 2, 6)));
            husk.setNoAi(true);
            h.runAtTickTime(3, t.guard(() -> {
                t.assertTrue(!hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN) && HeroActions.mood(hero.mood()) == MoodRules.CALM,
                        "at peace: weapon sheathed and calm");
                hero.setTarget(husk);
            }));
            h.runAtTickTime(6, t.guard(() -> {
                t.assertTrue(hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN), "the weapon is not drawn in a fight");
                t.assertTrue(HeroActions.mood(hero.mood()) == MoodRules.DETERMINED, "fighting mood " + HeroActions.mood(hero.mood()));
                hero.hurt(h.getLevel().damageSources().generic(), 1F);
            }));
            h.runAtTickTime(8, t.guard(() -> {
                t.assertTrue(HeroActions.mood(hero.mood()) == MoodRules.HURT, "no hurt wince: " + HeroActions.mood(hero.mood()));
                t.assertTrue(hero.doHurtTarget(husk), "the hero's blow did not land");
                hero.setTarget(null);
                husk.discard();
            }));
            h.runAtTickTime(30, t.guard(() -> t.assertTrue(HeroActions.mood(hero.mood()) == MoodRules.CALM,
                    "calm again after the fight: " + HeroActions.mood(hero.mood()))));
            // no target since tick 8, but its last blow was at tick 8: the weapon stays out for 5 s after it
            h.runAtTickTime(60, t.guard(() -> t.assertTrue(hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN),
                    "the weapon went back before 5 s had passed since the last blow")));
            h.runAtTickTime(130, t.guard(() -> {
                t.assertTrue(!hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN), "the weapon stays drawn long after the fight");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
