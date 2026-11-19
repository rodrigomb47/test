package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.HeroState;
import com.factionclash.combat.KillCredits;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroKillFeed;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;

/**
 * Kill credit and the hero kill feed (HEROES_V2 6.17 "Kill credit and the kill feed", {@code docs/design/breaching.md}
 * 8.5, ROADMAP phase 4): a death by terrain within 15 s of a hero's push is the hero's kill, for an NPC soldier and a
 * mock player alike, worded as vanilla words it ("was doomed to fall by", "walked into the danger zone due to", "tried
 * to swim in lava to escape") with both factions beside the names; a hero killed by a player, by an NPC or by terrain
 * alone is told the same way; nothing is told while {@code showDeathMessages} is off. The player's line is vanilla's
 * own death message (the recorder takes it as vanilla builds it, after the credit and with the names); the NPC's is the
 * feed's own. Each test has its own batch; the pools are walled in glass and cleared by the cleanup.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroKillFeedGameTests {
    private HeroKillFeedGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    /** A still common striker of {@link #SIDE} (it never fights on its own here: its one move weighs 0). */
    private static HeroEntity pusher(GameTestBases.Handle t, String id, int x, int z) {
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", IDLE_MOVE, ""), x, z);
        hero.setNoAi(true);
        return hero;
    }

    /**
     * A mock player that dies as a player does: NeoForge's {@code FakePlayer} never dies (its {@code die} does nothing),
     * so this one runs the first steps of {@code ServerPlayer.die}, the death event (where the credit and the feed run)
     * before vanilla builds its death message from the combat tracker, which the feed's recorder takes as vanilla would;
     * and it counts its hurt cooldown down as {@code ServerPlayer.tick} does. Otherwise {@link HeroAbilityGameTests#player}.
     */
    private static ServerPlayer mortal(GameTestBases.Handle t, int x, int z, FactionType faction, double health) {
        net.minecraft.server.level.ServerLevel level = t.helper.getLevel();
        ServerPlayer p = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hero-test-player")) {
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
            public void tick() {
                baseTick();
                if (invulnerableTime > 0) {
                    invulnerableTime--;
                }
            }

            @Override
            public void die(net.minecraft.world.damagesource.DamageSource source) {
                net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source);
            }
        };
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        p.moveTo(at.x, at.y, at.z, 0F, 0F);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);
        p.setHealth((float) health);
        p.setOnGround(true);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("cannot clear the spawn invulnerability: " + e);
        }
        var data = com.factionclash.campaign.CampaignData.get(level.getServer());
        data.players.put(p.getUUID(), faction);
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /** One cell of {@code fluid} at relative (x, 2, z), walled in glass, cleared again by the cleanup. */
    private static void pool(GameTestBases.Handle t, int x, int z, Block fluid) {
        GameTestHelper h = t.helper;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(x + dx, 2, z + dz);
                h.setBlock(p, dx == 0 && dz == 0 ? fluid : Blocks.GLASS);
                h.setBlock(p.above(), dx == 0 && dz == 0 ? Blocks.AIR : Blocks.GLASS);
            }
        }
        t.onCleanup(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    h.setBlock(new BlockPos(x + dx, 2, z + dz), Blocks.AIR);
                    h.setBlock(new BlockPos(x + dx, 3, z + dz), Blocks.AIR);
                }
            }
        });
    }

    /** Moves an entity to the middle of relative cell (x, 2, z). */
    private static void put(GameTestBases.Handle t, LivingEntity e, int x, int z) {
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        e.moveTo(at.x, at.y, at.z, e.getYRot(), 0F);
        e.setDeltaMovement(Vec3.ZERO);
    }

    private static List<HeroKillFeed.Line> recording(GameTestBases.Handle t) {
        List<HeroKillFeed.Line> lines = HeroKillFeed.record();
        t.onCleanup(HeroKillFeed::stopRecording);
        return lines;
    }

    private static HeroKillFeed.Line lineOf(List<HeroKillFeed.Line> lines, LivingEntity victim) {
        synchronized (lines) {
            for (HeroKillFeed.Line l : lines) {
                if (l.victim() == victim) {
                    return l;
                }
            }
        }
        return null;
    }

    /**
     * The line told of {@code victim}: vanilla's key {@code key}, the victim's name with {@code victimFaction} and the
     * killer's with {@code killerFaction} (null: a one-name line), and {@code words} in the text.
     */
    private static void told(GameTestBases.Handle t, List<HeroKillFeed.Line> lines, LivingEntity victim, String key,
                             String killerName, FactionType victimFaction, FactionType killerFaction, String words) {
        HeroKillFeed.Line l = lineOf(lines, victim);
        t.assertTrue(l != null, "nothing told of " + victim.getName().getString() + "'s death; told: " + lines);
        Component c = l.text();
        t.assertTrue(c.getContents() instanceof TranslatableContents, "not a translated line: " + c);
        TranslatableContents tc = (TranslatableContents) c.getContents();
        t.assertTrue(tc.getKey().equals(key), "the key " + tc.getKey() + ", not " + key + " (" + c.getString() + ")");
        String text = c.getString();
        t.assertTrue(text.contains(words), "\"" + text + "\" does not say \"" + words + "\"");
        t.assertTrue(text.contains("(" + victimFaction.displayName + ")"), "no victim faction in \"" + text + "\"");
        if (killerName != null) {
            t.assertTrue(text.contains(killerName + " (" + killerFaction.displayName + ")"),
                    "no \"" + killerName + " (" + killerFaction.displayName + ")\" in \"" + text + "\"");
        } else {
            t.assertTrue(tc.getArgs().length == 1, "a one-name line names " + tc.getArgs().length);
        }
    }

    // ------------------------------------------------------------------ deaths by terrain after a push

    /**
     * A hero knocks an NPC soldier and a mock player back; 6 s later (past vanilla's 5 s) they stand in water, where
     * their last attacker is still the hero at 7 s (the refresh), then in lava, where they die: the hero's kills
     * ("tried to swim in lava to escape Lava Pusher (Ember Legion)"), its campaign count and energy, the soldier's line
     * the feed's own and the player's vanilla's own message with both factions.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "killfeed_lava")
    public static void pushedIntoLavaIsThePushersKillForAnNpcAndAPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_lava_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 6);
            soldier.setNpcName("Ash Rook");
            ServerPlayer p = mortal(t, 8, 12, FOE, 6);
            pool(t, 14, 6, Blocks.WATER);
            pool(t, 14, 12, Blocks.WATER);
            pool(t, 19, 6, Blocks.LAVA);
            pool(t, 19, 12, Blocks.LAVA);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0); // the moves' own push
                HeroEffects.knockback(hero, p, 1, 0);
                t.assertTrue(soldier.getLastHurtByMob() == hero && p.getLastHurtByMob() == hero, "the push set no last attacker");
            }));
            h.runAfterDelay(125, t.guard(() -> {
                put(t, soldier, 14, 6);
                put(t, p, 14, 12);
            }));
            h.runAfterDelay(145, t.guard(() -> {
                // vanilla forgets a last attacker after 100 ticks: in water it is set again every second
                t.assertTrue(soldier.getLastHurtByMob() == hero, "the soldier's last attacker was forgotten in the water: "
                        + soldier.getLastHurtByMob());
                t.assertTrue(p.getLastHurtByMob() == hero, "the player's last attacker was forgotten in the water: "
                        + p.getLastHurtByMob());
                put(t, soldier, 19, 6);
                put(t, p, 19, 12);
            }));
            h.runAfterDelay(260, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && p.isDeadOrDying(), "the lava did not kill: soldier "
                        + soldier.getHealth() + ", player " + p.getHealth());
                told(t, lines, soldier, "death.attack.lava.player", "Fixture Lava Pusher", FOE, SIDE, "tried to swim in lava to escape");
                told(t, lines, p, "death.attack.lava.player", "Fixture Lava Pusher", FOE, SIDE, "tried to swim in lava to escape");
                t.assertTrue(!lineOf(lines, soldier).vanilla() && lineOf(lines, p).vanilla(), "the player's line is vanilla's own");
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 2, "the hero's kills: " + (s == null ? "no record" : s.kills));
                t.assertTrue(lineOf(lines, soldier).killer() == hero && lineOf(lines, p).killer() == hero, "the killer is the hero");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Knocked back by a hero, then a fall of 12 blocks (vanilla's landing: the fall damage with the fall distance on
     * it): "was doomed to fall by" the hero, for an NPC soldier and a mock player alike, from the push's hit of 0 in
     * their combat trackers.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "killfeed_fall")
    public static void pushedToItsDeathByAFallIsDoomedToFallByThePusher(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_fall_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            ServerPlayer p = mortal(t, 8, 12, FOE, 4);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                HeroEffects.knockback(hero, p, 1, 0);
            }));
            h.runAfterDelay(40, t.guard(() -> {
                for (LivingEntity r : List.of(soldier, p)) {
                    r.fallDistance = 12;
                    r.causeFallDamage(12F, 1F, r.damageSources().fall());
                }
            }));
            h.runAfterDelay(60, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && p.isDeadOrDying(), "the fall did not kill");
                // (vanilla's ".item" key when the pusher's weapon has a name of its own)
                String key = hero.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? "death.fell.assist.item" : "death.fell.assist";
                told(t, lines, soldier, key, "Fixture Fall Pusher", FOE, SIDE, "was doomed to fall by");
                told(t, lines, p, key, "Fixture Fall Pusher", FOE, SIDE, "was doomed to fall by");
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 2, "the hero's kills: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Knocked back by a hero, then, 6 s later (vanilla has forgotten the hero), burnt by a magma block's hot floor:
     * "walked into the danger zone due to" the hero, an NPC soldier and a mock player alike.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "killfeed_magma")
    public static void pushedOntoMagmaWalkedIntoTheDangerZoneDueToThePusher(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_magma_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            ServerPlayer p = mortal(t, 8, 12, FOE, 4);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                HeroEffects.knockback(hero, p, 1, 0);
            }));
            h.runAfterDelay(130, t.guard(() -> {
                // 6.25 s after the push, standing (no refresh): past vanilla's 5 s, inside the credit's 15 s
                t.assertTrue(soldier.getLastHurtByMob() == null && p.getLastHurtByMob() == null, "vanilla still remembers");
                for (LivingEntity r : List.of(soldier, p)) {
                    r.invulnerableTime = 0;
                    r.hurt(r.damageSources().hotFloor(), 10F); // what MagmaBlock.stepOn deals, with a killing amount
                }
            }));
            h.runAfterDelay(150, t.guard(() -> {
                told(t, lines, soldier, "death.attack.hotFloor.player", "Fixture Magma Pusher", FOE, SIDE,
                        "walked into the danger zone due to");
                told(t, lines, p, "death.attack.hotFloor.player", "Fixture Magma Pusher", FOE, SIDE,
                        "walked into the danger zone due to");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A push 15 s or more before a terrain death is no credit: "tried to swim in lava", nobody named, no kill. */
    @GameTest(template = "platform", timeoutTicks = 420, batch = "killfeed_window")
    public static void aPushOverFifteenSecondsOldIsNoCredit(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 420);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_old_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            h.runAfterDelay(5, t.guard(() -> HeroEffects.knockback(hero, soldier, 1, 0)));
            h.runAfterDelay(320, t.guard(() -> {
                t.assertTrue(KillCredits.credited(soldier, "lava") == null, "a 15 s old push still counts");
                soldier.invulnerableTime = 0;
                soldier.hurt(soldier.damageSources().lava(), 20F);
            }));
            h.runAfterDelay(340, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying(), "the lava did not kill");
                t.assertTrue(lineOf(lines, soldier) == null, "an ordinary NPC's lone death is no hero's: " + lines);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 0, "the hero got a kill: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a hero's death

    /**
     * Three heroes fall, to a mock player's blow, to an NPC soldier's blow and to lava alone: each is told with its
     * faction ("was slain by hero-test-player (Grove Covenant)", "was slain by <soldier> (Grove Covenant)", "tried to
     * swim in lava" with no killer), and a player killed by a hero's blow reads with both factions.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "killfeed_hero")
    public static void heroDeathsAndAHerosKillAreTold(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity byPlayer = pusher(t, "fixture_feed_one", 4, 4);
            HeroEntity byNpc = pusher(t, "fixture_feed_two", 4, 10);
            HeroEntity byLava = pusher(t, "fixture_feed_three", 4, 16);
            ServerPlayer p = mortal(t, 7, 4, FOE, 20);
            FactionNpc soldier = foe(t, 7, 10, 20);
            soldier.setNpcName("Ash Rook");
            ServerPlayer victim = mortal(t, 7, 16, FOE, 4);
            h.runAfterDelay(5, t.guard(() -> {
                victim.hurt(byLava.damageSources().mobAttack(byLava), 20F); // a hero's blow on a player
                for (HeroEntity x : List.of(byPlayer, byNpc, byLava)) {
                    x.setHealth(1F); // one capped blow kills it
                }
                byPlayer.hurt(p.damageSources().playerAttack(p), 6F);
                byNpc.hurt(soldier.damageSources().mobAttack(soldier), 6F);
                byLava.hurt(byLava.damageSources().lava(), 6F);
            }));
            h.runAfterDelay(25, t.guard(() -> {
                t.assertTrue(byPlayer.isDeadOrDying() && byNpc.isDeadOrDying() && byLava.isDeadOrDying() && victim.isDeadOrDying(),
                        "not all fell");
                told(t, lines, byPlayer, "death.attack.player", "hero-test-player", SIDE, FOE, "was slain by");
                told(t, lines, byNpc, "death.attack.mob", soldier.npcName(), SIDE, FOE, "was slain by");
                told(t, lines, byLava, "death.attack.lava", null, SIDE, null, "tried to swim in lava");
                String key = byLava.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? "death.attack.mob.item" : "death.attack.mob";
                told(t, lines, victim, key, "Fixture Feed Three", FOE, SIDE, "was slain by");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** With {@code showDeathMessages} off nothing is told, of an NPC's death by a hero's push or of a hero's own. */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "killfeed_rule")
    public static void nothingIsToldWithDeathMessagesOff(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES);
        boolean before = rule.get();
        t.onCleanup(() -> rule.set(before, h.getLevel().getServer()));
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_quiet_pusher", 4, 9);
            HeroEntity fallen = pusher(t, "fixture_quiet_fallen", 4, 15);
            FactionNpc soldier = foe(t, 8, 6, 4);
            rule.set(false, h.getLevel().getServer());
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                soldier.invulnerableTime = 0;
                soldier.hurt(soldier.damageSources().lava(), 20F);
                fallen.setHealth(1F);
                fallen.hurt(fallen.damageSources().lava(), 6F);
            }));
            h.runAfterDelay(25, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && fallen.isDeadOrDying(), "they did not die");
                t.assertTrue(lines.isEmpty(), "told with showDeathMessages off: " + lines);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 1, "the credit still counts without the message: " + (s == null ? "-" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
