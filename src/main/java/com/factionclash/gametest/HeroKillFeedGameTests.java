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
 * 8.5, ROADMAP phase 4; the feed's scope the owner's of 2026-11-19): a death by terrain within 15 s of a hero's push is
 * the hero's kill, for an NPC soldier, a mock player and an enemy hero alike; only the hero's death is told in the feed,
 * worded as vanilla words it ("was doomed to fall by", "walked into the danger zone due to", "tried to swim in lava to
 * escape") with both factions beside the names. Every hero death is told, whatever killed it (a player, an NPC, a
 * monster, another hero, terrain alone), and nothing else is: no line when a hero kills an NPC, a player, a monster or an
 * animal (the player's own death is vanilla's message, to which the feed adds nothing). Nothing is told while
 * {@code showDeathMessages} is off. Each test has its own batch; the pools are walled in glass and cleared by the cleanup.
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

    /** A still hero of {@link #FOE}, given to a second test base of its own that the first one's cleanup removes. */
    private static HeroEntity foeHero(GameTestBases.Handle t, String id, int x, int z) {
        GameTestBases.Handle foe = GameTestBases.register(t.helper, FOE, 2, 11, 0);
        t.onCleanup(foe::cleanup);
        return pusher(foe, id, x, z);
    }

    /** One blow of {@code damage} on a hero brought down to 1 health first (its boss health would take a long fight). */
    private static void finish(HeroEntity victim, net.minecraft.world.damagesource.DamageSource source, float damage) {
        victim.setHealth(1F);
        victim.invulnerableTime = 0;
        victim.hurt(source, damage);
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
     * killer's with {@code killerFaction} (a killer with no faction: its name alone; no killer: a one-name line), and
     * {@code words} in the text.
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
        if (killerName != null && killerFaction != null) {
            t.assertTrue(text.contains(killerName + " (" + killerFaction.displayName + ")"),
                    "no \"" + killerName + " (" + killerFaction.displayName + ")\" in \"" + text + "\"");
        } else if (killerName != null) {
            t.assertTrue(text.endsWith(killerName), "\"" + text + "\" does not end with \"" + killerName + "\"");
        } else {
            t.assertTrue(tc.getArgs().length == 1, "a one-name line names " + tc.getArgs().length);
        }
    }

    // ------------------------------------------------------------------ deaths by terrain after a push

    /** Nothing told of the ordinary victims; exactly {@code count} lines in all. */
    private static void untold(GameTestBases.Handle t, List<HeroKillFeed.Line> lines, int count, LivingEntity... victims) {
        for (LivingEntity v : victims) {
            HeroKillFeed.Line l = lineOf(lines, v);
            t.assertTrue(l == null, "a death that is no hero's was told: " + (l == null ? "" : l.text().getString()));
        }
        t.assertTrue(lines.size() == count, count + " line(s) expected, told: " + lines);
    }

    /**
     * A hero knocks an NPC soldier, a mock player and an enemy hero back; 6 s later (past vanilla's 5 s) the soldier and
     * the player stand in water, where their last attacker is still the hero at 7 s (the refresh), then in lava, where
     * they die, and the enemy hero dies of lava: three kills for the hero, and only the enemy hero's death is told
     * ("tried to swim in lava to escape Fixture Lava Pusher (Ember Legion)"), nothing of the soldier's or the player's.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "killfeed_lava")
    public static void pushedIntoLavaIsThePushersKillAndOnlyTheHerosDeathIsTold(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_lava_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 6);
            soldier.setNpcName("Ash Rook");
            ServerPlayer p = mortal(t, 8, 12, FOE, 6);
            HeroEntity enemy = foeHero(t, "fixture_lava_hero", 8, 16);
            pool(t, 14, 6, Blocks.WATER);
            pool(t, 14, 12, Blocks.WATER);
            pool(t, 19, 6, Blocks.LAVA);
            pool(t, 19, 12, Blocks.LAVA);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0); // the moves' own push
                HeroEffects.knockback(hero, p, 1, 0);
                HeroEffects.knockback(hero, enemy, 1, 0);
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
                finish(enemy, enemy.damageSources().lava(), 4F);
            }));
            h.runAfterDelay(260, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && p.isDeadOrDying() && enemy.isDeadOrDying(), "the lava did not kill: soldier "
                        + soldier.getHealth() + ", player " + p.getHealth() + ", hero " + enemy.getHealth());
                told(t, lines, enemy, "death.attack.lava.player", "Fixture Lava Pusher", FOE, SIDE, "tried to swim in lava to escape");
                t.assertTrue(lineOf(lines, enemy).killer() == hero, "the killer is the hero");
                untold(t, lines, 1, soldier, p);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 3, "the hero's kills: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Knocked back by a hero, then a fall of 12 blocks (vanilla's landing: the fall damage with the fall distance on
     * it): "was doomed to fall by" the hero, credited for an NPC soldier, a mock player (vanilla's own death message says
     * it) and an enemy hero alike, from the push's hit of 0 in their combat trackers; only the hero's death is told.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "killfeed_fall")
    public static void pushedToItsDeathByAFallIsDoomedToFallByThePusher(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_fall_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            ServerPlayer p = mortal(t, 8, 12, FOE, 4);
            HeroEntity enemy = foeHero(t, "fixture_fall_hero", 8, 16);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                HeroEffects.knockback(hero, p, 1, 0);
                HeroEffects.knockback(hero, enemy, 1, 0);
            }));
            String[] playerMessage = new String[1];
            h.runAfterDelay(40, t.guard(() -> {
                enemy.setHealth(1F);
                for (LivingEntity r : List.of(soldier, p, enemy)) {
                    r.fallDistance = 12;
                    r.causeFallDamage(12F, 1F, r.damageSources().fall());
                }
                playerMessage[0] = p.getCombatTracker().getDeathMessage().getString(); // vanilla's own, as it tells it now
            }));
            h.runAfterDelay(60, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && p.isDeadOrDying() && enemy.isDeadOrDying(), "the fall did not kill");
                // (vanilla's ".item" key when the pusher's weapon has a name of its own)
                String key = hero.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? "death.fell.assist.item" : "death.fell.assist";
                told(t, lines, enemy, key, "Fixture Fall Pusher", FOE, SIDE, "was doomed to fall by");
                untold(t, lines, 1, soldier, p);
                t.assertTrue(playerMessage[0] != null && playerMessage[0].contains("was doomed to fall by Fixture Fall Pusher"),
                        "the player's own death message: " + playerMessage[0]);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 3, "the hero's kills: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * Knocked back by a hero, then, 6 s later (vanilla has forgotten the hero), burnt by a magma block's hot floor: the
     * hero's kill for an NPC soldier, a mock player and an enemy hero; the hero's death is told, "walked into the danger
     * zone due to" the pusher.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "killfeed_magma")
    public static void pushedOntoMagmaWalkedIntoTheDangerZoneDueToThePusher(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_magma_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            ServerPlayer p = mortal(t, 8, 12, FOE, 4);
            HeroEntity enemy = foeHero(t, "fixture_magma_hero", 8, 16);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                HeroEffects.knockback(hero, p, 1, 0);
                HeroEffects.knockback(hero, enemy, 1, 0);
            }));
            h.runAfterDelay(130, t.guard(() -> {
                // 6.25 s after the push, standing (no refresh): past vanilla's 5 s, inside the credit's 15 s
                t.assertTrue(soldier.getLastHurtByMob() == null && p.getLastHurtByMob() == null, "vanilla still remembers");
                for (LivingEntity r : List.of(soldier, p)) {
                    r.invulnerableTime = 0;
                    r.hurt(r.damageSources().hotFloor(), 10F); // what MagmaBlock.stepOn deals, with a killing amount
                }
                finish(enemy, enemy.damageSources().hotFloor(), 10F);
            }));
            h.runAfterDelay(150, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && p.isDeadOrDying() && enemy.isDeadOrDying(), "the hot floor did not kill: soldier "
                        + soldier.getHealth() + ", player " + p.getHealth() + ", hero " + enemy.getHealth());
                told(t, lines, enemy, "death.attack.hotFloor.player", "Fixture Magma Pusher", FOE, SIDE,
                        "walked into the danger zone due to");
                untold(t, lines, 1, soldier, p);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 3, "the hero's kills: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * A push 15 s or more before a terrain death is no credit: the soldier's death is no kill and untold, and the enemy
     * hero's is told with nobody named ("tried to swim in lava").
     */
    @GameTest(template = "platform", timeoutTicks = 420, batch = "killfeed_window")
    public static void aPushOverFifteenSecondsOldIsNoCredit(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 420);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity hero = pusher(t, "fixture_old_pusher", 4, 9);
            FactionNpc soldier = foe(t, 8, 6, 4);
            HeroEntity enemy = foeHero(t, "fixture_old_hero", 8, 12);
            h.runAfterDelay(5, t.guard(() -> {
                HeroEffects.knockback(hero, soldier, 1, 0);
                HeroEffects.knockback(hero, enemy, 1, 0);
            }));
            h.runAfterDelay(320, t.guard(() -> {
                t.assertTrue(KillCredits.credited(soldier, "lava") == null && KillCredits.credited(enemy, "lava") == null,
                        "a 15 s old push still counts");
                soldier.invulnerableTime = 0;
                soldier.hurt(soldier.damageSources().lava(), 20F);
                finish(enemy, enemy.damageSources().lava(), 4F);
            }));
            h.runAfterDelay(340, t.guard(() -> {
                t.assertTrue(soldier.isDeadOrDying() && enemy.isDeadOrDying(), "the lava did not kill");
                told(t, lines, enemy, "death.attack.lava", null, FOE, null, "tried to swim in lava");
                t.assertTrue(lineOf(lines, enemy).killer() == null, "a killer named: " + lineOf(lines, enemy).killer());
                untold(t, lines, 1, soldier);
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.kills == 0, "the hero got a kill: " + (s == null ? "no record" : s.kills));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a hero's death, and a hero's kills

    /**
     * Five heroes fall, to a mock player's blow, to an NPC soldier's, to a husk's, to an enemy hero's and to lava alone:
     * each is told with its faction ("was slain by hero-test-player (Grove Covenant)", "was slain by Ash Rook (Grove
     * Covenant)", "was slain by Husk", "was slain by Fixture Feed Foe (Grove Covenant)", "tried to swim in lava" with no
     * killer). The same moment a hero kills a mock player, an NPC soldier, a husk and a cow: none of those is told (the
     * player's own death is vanilla's message), so exactly five lines.
     */
    @GameTest(template = "platform", timeoutTicks = 120, batch = "killfeed_hero")
    public static void everyHeroDeathIsToldAndAHerosOwnKillsAreNot(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            List<HeroKillFeed.Line> lines = recording(t);
            HeroEntity byPlayer = pusher(t, "fixture_feed_one", 4, 3);
            HeroEntity byNpc = pusher(t, "fixture_feed_two", 4, 7);
            HeroEntity byLava = pusher(t, "fixture_feed_three", 4, 11);
            HeroEntity byMonster = pusher(t, "fixture_feed_four", 4, 15);
            HeroEntity byHero = pusher(t, "fixture_feed_five", 4, 19);
            ServerPlayer p = mortal(t, 7, 3, FOE, 20);
            FactionNpc soldier = foe(t, 7, 7, 20);
            soldier.setNpcName("Ash Rook");
            net.minecraft.world.entity.monster.Husk husk = HeroAbilityGameTests.husk(t, 7, 15, 20);
            HeroEntity enemy = foeHero(t, "fixture_feed_foe", 7, 19);
            // the hero's own kills, none of them told
            ServerPlayer victim = mortal(t, 10, 3, FOE, 4);
            FactionNpc slain = foe(t, 10, 7, 4);
            net.minecraft.world.entity.monster.Husk prey = HeroAbilityGameTests.husk(t, 10, 11, 4);
            net.minecraft.world.entity.animal.Cow cow = t.track(h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(10, 2, 15)));
            cow.setNoAi(true);
            h.runAfterDelay(5, t.guard(() -> {
                for (LivingEntity v : List.of(victim, slain, prey, cow)) {
                    v.hurt(byLava.damageSources().mobAttack(byLava), 40F); // a hero's blow
                }
                finish(byPlayer, p.damageSources().playerAttack(p), 6F);
                finish(byNpc, soldier.damageSources().mobAttack(soldier), 6F);
                finish(byLava, byLava.damageSources().lava(), 6F);
                finish(byMonster, husk.damageSources().mobAttack(husk), 6F);
                finish(byHero, enemy.damageSources().mobAttack(enemy), 6F);
            }));
            h.runAfterDelay(25, t.guard(() -> {
                for (LivingEntity v : List.of(byPlayer, byNpc, byLava, byMonster, byHero, victim, slain, prey, cow)) {
                    t.assertTrue(v.isDeadOrDying(), v.getName().getString() + " did not fall");
                }
                told(t, lines, byPlayer, "death.attack.player", "hero-test-player", SIDE, FOE, "was slain by");
                told(t, lines, byNpc, "death.attack.mob", soldier.npcName(), SIDE, FOE, "was slain by");
                told(t, lines, byLava, "death.attack.lava", null, SIDE, null, "tried to swim in lava");
                told(t, lines, byMonster, "death.attack.mob", "Husk", SIDE, null, "was slain by");
                String key = enemy.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? "death.attack.mob.item" : "death.attack.mob";
                told(t, lines, byHero, key, "Fixture Feed Foe", SIDE, FOE, "was slain by");
                untold(t, lines, 5, victim, slain, prey, cow);
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** With {@code showDeathMessages} off nothing is told, of a hero's death or of anyone else's. */
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
                finish(fallen, fallen.damageSources().lava(), 6F);
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
