package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.HeroState;
import com.factionclash.combat.HeroHoldGoal;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBoss;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroStatus;
import com.factionclash.hero.HeroTelegraphs;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.Decals;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.npc.FactionNpc;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.heroHit;
import static com.factionclash.gametest.HeroAbilityGameTests.husk;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.noRegen;
import static com.factionclash.gametest.HeroAbilityGameTests.player;

/**
 * Heroes, task A6a: the boss controller, part one (HEROES_V2 6.1-6.4, 6.7, 6.9, 6.10, 14.5; SLICE_PLAN 5 criteria 9-11
 * and 14). The per-hit cap with its excess to poise, the hurt cooldown kept per attacker, poise and the stagger (which
 * interrupts a wind-up and holds the hero 2.5 s taking +25%), phases with their transitions, the fight clock counted
 * from engaged enemies, the out-of-fight healing and re-arm, and the telegraph decals sent to the players near.
 * <b>Equal treatment</b> (6.15): every rule that a blow can set off is checked with an NPC soldier's blow and a mock
 * player's with the same numbers, and both land the same. Each test that runs a hero has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroBossGameTests {
    private HeroBossGameTests() {
    }

    private static final FactionType FOE = FactionType.GROVE_COVENANT;
    private static final double EPS = 1e-3;

    // ------------------------------------------------------------------ fixtures

    /** A common hero (84 boss health at level 1 as a striker) with these moves, this boss block and more fields. */
    static String bossDef(String id, String role, String moves, String boss, String more) {
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + HeroIdentity.nameFromId(id) + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"" + role + "\"},"
                + " \"basic_attack\": " + MELEE + ", \"moves\": [" + moves + "], \"boss\": {\"weak_points\": [\"back\"]" + boss + "},"
                + " \"personality\": {\"temperament\": \"stoic\"}, \"leader\": {\"eligible\": false}" + more + "}";
    }

    /** A move that is never picked (weight 0), so a fixture has one. */
    static final String IDLE_MOVE = move("idle_cut", "melee", "\"weight\": 0, \"cooldown\": 30, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");

    static HeroEntity bossHero(GameTestBases.Handle t, String id, String def, int x, int z) {
        HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", def, x, z);
        noRegen(hero); // what it loses is what it was dealt
        return hero;
    }

    /** An enemy soldier holding an iron sword (still, no AI). */
    static FactionNpc soldier(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = foe(t, x, z, 100);
        n.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return n;
    }

    /** An enemy mock player holding an iron sword. */
    static ServerPlayer swordPlayer(GameTestBases.Handle t, int x, int z) {
        ServerPlayer p = player(t, x, z, FOE, 100);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        return p;
    }

    /** {@code attacker}'s blow of {@code amount} on the hero (a mob's or a player's attack source); what the hero lost. */
    static float blow(HeroEntity hero, LivingEntity attacker, float amount) {
        float before = hero.getHealth();
        hero.hurt(attacker instanceof net.minecraft.world.entity.player.Player p ? hero.damageSources().playerAttack(p)
                : hero.damageSources().mobAttack(attacker), amount);
        return before - hero.getHealth();
    }

    static double poise(HeroEntity hero) {
        BossRules.Poise p = hero.combat().poise();
        p.decay(hero.level().getGameTime());
        return p.value();
    }

    static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    static void near(GameTestBases.Handle t, double expected, double actual, double eps, String what) {
        t.assertTrue(Math.abs(expected - actual) <= eps, what + ": expected " + expected + ", got " + actual);
    }

    /** The phase a save of the hero keeps (1: none saved). */
    static int savedPhase(HeroEntity hero) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        hero.addAdditionalSaveData(tag);
        return Math.max(1, tag.getCompound("FcHero").getInt("phase"));
    }

    static boolean holding(HeroEntity hero) {
        return hero.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof HeroHoldGoal);
    }

    // ------------------------------------------------------------------ the per-hit cap and the hurt cooldown (6.2)

    /**
     * {@code hitCapHoldsForNpcAndPlayerHits}: an oversized blow from an NPC soldier and the same blow from a mock player
     * (iron swords, 40 damage) each take exactly 12% of the hero's maximum, and the excess plus the sword's factor goes
     * to poise, the same for both; lava is not capped and fills no poise. Then two NPC soldiers and a mock player strike
     * in the same tick and all three land in full (one hurt cooldown per attacker, 6.2), while one attacker's second
     * blow in the window lands only its excess; two lava hits in one tick share one cooldown as in vanilla.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_boss_cap")
    public static void hitCapHoldsForNpcAndPlayerHits(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 100);
        try {
            String id = "fx_boss_cap";
            HeroEntity hero = bossHero(t, id, bossDef(id, "striker", IDLE_MOVE, ", \"poise\": 200, \"phases\": [{\"at\": 100}]", ""), 8, 8);
            hero.setNoAi(true);
            FactionNpc a = soldier(t, 10, 8), b = soldier(t, 6, 8);
            ServerPlayer p = swordPlayer(t, 8, 10);
            h.runAtTickTime(3, t.guard(() -> {
                double max = hero.getMaxHealth(), cap = max * 0.12;
                // an NPC's oversized blow
                float lostNpc = blow(hero, a, 40);
                BossRules.Landed npc = hero.combat().boss().lastLanded();
                double poiseNpc = poise(hero);
                near(t, cap, lostNpc, EPS, "the NPC's blow is capped at 12% of " + max);
                t.assertTrue(npc.excess() > 10, "the excess of the NPC's blow " + npc.excess());
                near(t, npc.health() * BossRules.WeaponKind.SWORD.poise + npc.excess(), poiseNpc, EPS, "the NPC's blow's poise");
                // the same blow from a mock player, from the same health and an empty bar
                hero.setHealth((float) max);
                hero.combat().poise().reset();
                float lostPlayer = blow(hero, p, 40);
                BossRules.Landed pl = hero.combat().boss().lastLanded();
                near(t, lostNpc, lostPlayer, EPS, "the mock player's blow lands as the NPC's");
                near(t, npc.excess(), pl.excess(), EPS, "the same excess");
                near(t, poiseNpc, poise(hero), EPS, "the same poise");
                // lava: no attacker, not capped, no poise
                hero.setHealth((float) max);
                double before = poise(hero);
                float lava = blow0(hero, hero.damageSources().lava(), 30);
                t.assertTrue(lava > cap + 1, "lava was capped: " + lava + " (cap " + cap + ")");
                near(t, before, poise(hero), EPS, "lava filled poise");
                t.assertTrue(blow0(hero, hero.damageSources().lava(), 30) == 0, "a second lava hit in the window landed (one shared cooldown)");
            }));
            h.runAtTickTime(20, t.guard(() -> {
                hero.setHealth(hero.getMaxHealth());
                // three attackers in one tick, each under the cap: all land in full
                float one = blow(hero, a, 6);
                float two = blow(hero, b, 6);
                float three = blow(hero, p, 6);
                t.assertTrue(one > 0, "the first blow did not land");
                near(t, one, two, EPS, "a second soldier's blow in the same tick");
                near(t, one, three, EPS, "a mock player's blow in the same tick");
                // one attacker's spam: nothing more for the same blow, only the excess for a bigger one
                t.assertTrue(blow(hero, a, 6) == 0, "the same soldier's same blow landed twice in its window");
                float excess = blow(hero, a, 9);
                t.assertTrue(excess > 0 && excess < one, "a bigger blow in the window lands only its excess: " + excess);
            }));
            h.runAtTickTime(32, t.guard(() -> {
                t.assertTrue(blow(hero, a, 6) > 0, "after its window the soldier lands again");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    static float blow0(HeroEntity hero, net.minecraft.world.damagesource.DamageSource src, float amount) {
        float before = hero.getHealth();
        hero.hurt(src, amount);
        return before - hero.getHealth();
    }

    /**
     * {@code sixSoldiersEachLandInOneWindow}: six NPC soldiers swing their iron swords at a hero in the same tick, by the
     * soldiers' own attack ({@code doHurtTarget}); every blow lands the same (vanilla's one shared cooldown would let
     * only the first through, 6.16). A soldier swinging again inside its own 10 ticks lands nothing; after them it lands.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_boss_six")
    public static void sixSoldiersEachLandInOneWindow(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 100);
        try {
            String id = "fx_boss_six";
            HeroEntity hero = bossHero(t, id, bossDef(id, "vanguard", IDLE_MOVE, ", \"poise\": 200, \"phases\": [{\"at\": 100}]", ""), 10, 10);
            hero.setNoAi(true);
            FactionNpc[] six = {soldier(t, 8, 10), soldier(t, 12, 10), soldier(t, 10, 8), soldier(t, 10, 12), soldier(t, 8, 8),
                    soldier(t, 12, 12)};
            float[] first = new float[1];
            // a tick for the swords' attack damage to apply
            h.runAtTickTime(4, t.guard(() -> {
                float[] lost = new float[six.length];
                for (int i = 0; i < six.length; i++) {
                    float before = hero.getHealth();
                    t.assertTrue(six[i].doHurtTarget(hero), "soldier " + i + "'s swing did not land");
                    lost[i] = before - hero.getHealth();
                }
                first[0] = lost[0];
                t.assertTrue(lost[0] > 1, "a soldier's iron sword took " + lost[0]);
                for (int i = 1; i < six.length; i++) {
                    near(t, lost[0], lost[i], EPS, "soldier " + i + " in the same window as the others");
                }
            }));
            h.runAtTickTime(8, t.guard(() -> {
                float before = hero.getHealth();
                six[0].doHurtTarget(hero);
                near(t, 0, before - hero.getHealth(), EPS, "the first soldier's second swing inside its own window");
            }));
            h.runAtTickTime(16, t.guard(() -> {
                float before = hero.getHealth();
                six[0].doHurtTarget(hero);
                near(t, first[0], before - hero.getHealth(), EPS, "the first soldier's swing after its window");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ poise and the stagger (6.9)

    /**
     * {@code staggerAtFullPoise}: a hero (poise 50) in the wind-up of a heavy move takes two oversized blows from NPC
     * soldiers; the second fills the bar: it staggers, the move is interrupted (nothing lands on its target), it plays
     * {@code stagger}, the bar empties and its maximum grows to 60, and for 2.5 s it is held (no move starts, the hold
     * goal runs) and takes 25% more. The second stagger comes from a mock player's blows, whose poise is the NPC's. A
     * hero with {@code poise: 0} has no bar and never staggers.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_boss_stagger")
    public static void staggerAtFullPoise(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 200);
        try {
            String id = "fx_boss_stag", nb = "fx_boss_nobar";
            String smash = move("smash", "melee", "\"class\": \"heavy\", \"weight\": 0, \"cooldown\": 20,"
                    + " \"frames\": {\"windup\": 1.0, \"active\": 0.2, \"recovery\": 0.8}, \"effects\": [{\"type\": \"damage\", \"amount\": 4}]");
            HeroEntity hero = bossHero(t, id, bossDef(id, "striker", smash, ", \"poise\": 50, \"phases\": [{\"at\": 100}]", ""), 6, 6);
            HeroEntity noBar = bossHero(t, nb, bossDef(nb, "striker", IDLE_MOVE, ", \"poise\": 0, \"phases\": [{\"at\": 100}]", ""), 16, 16);
            noBar.setNoAi(true);
            Husk husk = husk(t, 7, 6, 200);
            FactionNpc s1 = soldier(t, 2, 2), s2 = soldier(t, 2, 4), s3 = soldier(t, 2, 6);
            ServerPlayer pl = swordPlayer(t, 2, 10);
            double[] npcPoise = {0};
            long[] staggeredAt = {-1};
            long[] moveStart = {-1};
            float[] staggeredLoss = {0};
            h.runAtTickTime(2, t.guard(() -> force(t, hero, husk, "smash")));
            h.runAtTickTime(5, t.guard(() -> {
                t.assertTrue(hero.combat().stage() == MoveStage.WINDUP, "not in its wind-up: " + hero.combat().stage());
                blow(hero, s1, 40);
                npcPoise[0] = poise(hero);
                t.assertTrue(!hero.combat().staggered() && npcPoise[0] < 50, "one blow staggered it: " + npcPoise[0]);
                blow(hero, s2, 40);
                t.assertTrue(hero.combat().staggered(), "two oversized blows (" + npcPoise[0] + " each) did not fill 50");
                staggeredAt[0] = now(h);
                moveStart[0] = hero.combat().lastMoveStart();
                t.assertTrue(!hero.combat().running(), "the stagger did not interrupt the wind-up");
                t.assertTrue(HeroActions.code(hero.action()) == HeroActions.actionCode("stagger"), "no stagger clip");
                near(t, 0, hero.combat().poise().value(), EPS, "the bar empties");
                near(t, 60, hero.combat().poise().max(), EPS, "its maximum grows 20%");
                t.assertTrue(hero.combat().boss().staggerCount() == 1, "staggers " + hero.combat().boss().staggerCount());
                // the no-bar hero takes the same blows and never staggers
                blow(noBar, s1, 40);
                blow(noBar, s2, 40);
                t.assertTrue(noBar.combat().poise().none() && !noBar.combat().staggered(), "a hero with poise 0 staggered");
            }));
            h.runAtTickTime(8, t.guard(() -> staggeredLoss[0] = blow(hero, s3, 5)));
            t.onEachTick(() -> {
                long now = now(h);
                if (staggeredAt[0] < 0) {
                    return;
                }
                t.assertTrue(!heroHit(husk, hero), "the interrupted move landed");
                if (now > staggeredAt[0] + 2 && now < staggeredAt[0] + BossRules.STAGGER_TICKS) {
                    t.assertTrue(hero.combat().staggered() && hero.combat().boss().held(), "it is not held for 2.5 s");
                    t.assertTrue(holding(hero), "the hold goal does not run while it kneels");
                    t.assertTrue(hero.combat().lastMoveStart() == moveStart[0], "a staggered hero started a move");
                }
                if (now == staggeredAt[0] + BossRules.STAGGER_TICKS + 2) {
                    t.assertTrue(!hero.combat().staggered(), "still staggered after 2.5 s");
                    float after = blow(hero, s3, 5);
                    near(t, after * 1.25, staggeredLoss[0], 0.01, "a staggered hero takes +25%");
                }
                if (now == staggeredAt[0] + BossRules.STAGGER_TICKS + 4) {
                    // the second stagger, from a mock player's blows: the same poise a blow as the NPC's
                    double before = poise(hero);
                    blow(hero, pl, 40);
                    near(t, npcPoise[0], poise(hero) - before, EPS, "a mock player's blow fills the bar as the NPC's did");
                }
                if (now == staggeredAt[0] + BossRules.STAGGER_TICKS + 16) {
                    blow(hero, pl, 40); // its own window has passed: a full blow again
                    t.assertTrue(hero.combat().staggered(), "the player's blows did not stagger it (max 60)");
                    t.assertTrue(hero.combat().boss().staggerCount() == 2, "staggers " + hero.combat().boss().staggerCount());
                    near(t, 72, hero.combat().poise().max(), EPS, "the maximum grew again");
                    HeroState rec = hero.campaignRecord();
                    t.assertTrue(rec == null || rec.staggers == 2, "the campaign counted " + (rec == null ? -1 : rec.staggers));
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** A mock player whose raised shield ages tick by tick as a living entity's does (so it parries like an NPC). */
    static ServerPlayer shieldPlayer(GameTestBases.Handle t, int x, int z) {
        net.minecraft.server.level.ServerLevel level = t.helper.getLevel();
        net.neoforged.neoforge.common.util.FakePlayer p = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hero-parry-player")) {
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
                if (isUsingItem()) {
                    updateUsingItem(getUseItem()); // the shield's use ages one tick, as a living entity's tick ages it
                }
            }
        };
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new net.minecraft.core.BlockPos(x, 2, z)));
        p.moveTo(at.x, at.y, at.z, 90F, 0F);
        p.setYHeadRot(90F);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        p.setHealth(100F);
        p.setOnGround(true);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("cannot clear spawn invulnerability: " + e);
        }
        com.factionclash.campaign.CampaignData data = com.factionclash.campaign.CampaignData.get(level.getServer());
        data.players.put(p.getUUID(), FOE);
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    /**
     * {@code parryAddsPoiseForNpcAndPlayer}: a heavy move blocked by a shield raised 5-11 ticks before it lands is a
     * parry, 25 poise on the hero that struck, whether an NPC soldier's shield or a mock player's caught it; a shield up
     * for 20 ticks blocks the same move with no parry.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_boss_parry")
    public static void parryAddsPoiseForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 100);
        try {
            String cleave = move("cleave", "melee", "\"class\": \"heavy\", \"weight\": 0, \"cooldown\": 20,"
                    + " \"frames\": {\"windup\": 0.8, \"active\": 0.2, \"recovery\": 0.8}, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]");
            String a = "fx_boss_parry_a", b = "fx_boss_parry_b", c = "fx_boss_parry_c";
            String boss = ", \"poise\": 100, \"phases\": [{\"at\": 100}]";
            HeroEntity vsNpc = bossHero(t, a, bossDef(a, "striker", cleave, boss, ""), 4, 4);
            HeroEntity vsPlayer = bossHero(t, b, bossDef(b, "striker", cleave, boss, ""), 4, 12);
            HeroEntity late = bossHero(t, c, bossDef(c, "striker", cleave, boss, ""), 4, 20);
            for (HeroEntity x : List.of(vsNpc, vsPlayer, late)) {
                x.setNoAi(true);
            }
            FactionNpc npc = soldier(t, 6, 4), npc2 = soldier(t, 6, 20);
            for (FactionNpc n : List.of(npc, npc2)) {
                n.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                n.setYRot(90F);
                n.setYBodyRot(90F);
                n.setYHeadRot(90F); // facing the hero, west
            }
            ServerPlayer pl = shieldPlayer(t, 6, 12);
            // a soldier raises its shield on its own against an archer more than 3.5 blocks away (and lowers it without
            // one): the archer is named its target on the tick the shield should go up
            FactionNpc archer = HeroMoveGameTests.archer(t, 16, 12);
            h.runAtTickTime(4, t.guard(() -> npc2.setTarget(archer))); // up about 20 ticks at the hit
            h.runAtTickTime(7, t.guard(() -> {
                force(t, vsNpc, npc, "cleave");
                force(t, vsPlayer, pl, "cleave");
                force(t, late, npc2, "cleave");
            }));
            // the cleave lands 17 ticks after it is forced (16 of wind-up); the shields go up 7 ticks before
            h.runAtTickTime(17, t.guard(() -> {
                npc.setTarget(archer);
                pl.startUsingItem(InteractionHand.OFF_HAND);
            }));
            t.onEachTick(() -> {
                if (vsNpc.combat().lastRelease() > 0 && vsPlayer.combat().lastRelease() > 0 && late.combat().lastRelease() > 0
                        && now(h) == vsNpc.combat().lastRelease() + 2) {
                    t.assertTrue(npc.isBlocking() && pl.isBlocking() && npc2.isBlocking(), "a shield was not up: soldier "
                            + npc.getTicksUsingItem() + " ticks, player " + pl.getTicksUsingItem() + ", late soldier "
                            + npc2.getTicksUsingItem());
                    t.assertTrue(npc.getHealth() == 100 && pl.getHealth() == 100 && npc2.getHealth() == 100, "a shield did not block: "
                            + npc.getHealth() + " " + pl.getHealth() + " " + npc2.getHealth() + "; poise " + poise(vsNpc) + " "
                            + poise(vsPlayer) + " " + poise(late) + "; head " + npc.getYHeadRot() + " " + pl.getYHeadRot() + " "
                            + npc2.getYHeadRot());
                    near(t, BossRules.PARRY_POISE, poise(vsNpc), EPS, "the soldier's parry");
                    near(t, BossRules.PARRY_POISE, poise(vsPlayer), EPS, "the mock player's parry, as the soldier's");
                    near(t, 0, poise(late), EPS, "a shield up for 20 ticks parried");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ phases (6.7)

    /**
     * {@code phaseTransitionAtThreshold}: a hero at 61% is struck under 60% by an NPC soldier: the transition to phase 2
     * begins (the phase the renderer reads, super armour, the {@code phase.2} clip, its face), its shockwave pushes an
     * NPC soldier and a mock player standing alike exactly alike, a blow in it takes half, poise fills to just short of
     * a stagger; when it ends the phase's state and stats hold, {@code on_phase} fired and the next poise staggers. A
     * mock player's blow under 25% then reaches phase 3, whose transition waits for the stagger to end.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_boss_phase")
    public static void phaseTransitionAtThreshold(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 260);
        try {
            String id = "fx_boss_phase";
            String boss = ", \"poise\": 100, \"phases\": [{\"at\": 100}, {\"at\": 60, \"states\": [\"rage\"], \"stats\": [{\"stat\": \"armor\","
                    + " \"percent\": 20}], \"transition\": {\"seconds\": 1.5, \"line\": \"Now it begins!\", \"face\": \"angry\"}},"
                    + " {\"at\": 25, \"transition\": {\"seconds\": 1.2}}]";
            String more = ", \"states\": [{\"name\": \"rage\", \"stats\": [{\"stat\": \"attack\", \"percent\": 20}], \"face\": \"angry\"}],"
                    + " \"passives\": [{\"name\": \"Phase Up\", \"trigger\": \"on_phase\", \"form\": \"self\", \"cooldown\": 1,"
                    + " \"effects\": [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10, \"seconds\": 20}]}]";
            // the two the shockwave pushes join the level before the hero, so they tick before it: what the test reads in
            // the transition's tick is the push itself (a still mob's own tick slows its velocity by 2%)
            FactionNpc pushedNpc = soldier(t, 12, 10);
            ServerPlayer pushedPlayer = swordPlayer(t, 8, 10);
            HeroEntity hero = bossHero(t, id, bossDef(id, "striker", IDLE_MOVE, boss, more), 10, 10);
            hero.setNoAi(true);
            FactionNpc s1 = soldier(t, 2, 2), s2 = soldier(t, 2, 4), s3 = soldier(t, 2, 6);
            float[] baseLoss = {0};
            long[] began = {-1};
            long[] phase3Asked = {-1};
            long[] crossedAt = {-1};
            h.runAtTickTime(2, t.guard(() -> {
                baseLoss[0] = blow(hero, s1, 4);
                hero.setHealth(hero.getMaxHealth() * 0.61F);
            }));
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(hero.combat().boss().phase() == 1 && !hero.combat().boss().transitioning(), "a phase began at 61%");
                blow(hero, s2, 3); // under 60%
                crossedAt[0] = now(h);
            }));
            t.onEachTick(() -> {
                long now = now(h);
                if (began[0] < 0 && hero.combat().boss().transitioning()) {
                    began[0] = now;
                    t.assertTrue(HeroActions.phase(hero.state()) == 2, "the renderer's phase is " + HeroActions.phase(hero.state()));
                    t.assertTrue(hero.stateFlag(HeroActions.STATE_SUPER_ARMOUR), "no super armour in the transition");
                    t.assertTrue(HeroActions.code(hero.action()) == HeroActions.actionCode("phase.2"), "no phase.2 clip");
                    Vec3 vn = pushedNpc.getDeltaMovement(), vp = pushedPlayer.getDeltaMovement();
                    double hn = Math.hypot(vn.x, vn.z), hp = Math.hypot(vp.x, vp.z);
                    t.assertTrue(hn > 0.2, "the shockwave did not push the soldier: " + vn);
                    near(t, hn, hp, EPS, "the shockwave pushes a mock player as it pushes a soldier");
                    t.assertTrue(vn.x > 0 && vp.x < 0, "not pushed away from the hero: " + vn + " / " + vp);
                    return;
                }
                if (began[0] < 0) {
                    t.assertTrue(crossedAt[0] < 0 || now - crossedAt[0] <= 2, "no transition after the blow under 60%");
                    return;
                }
                if (now == began[0] + 3) {
                    t.assertTrue(savedPhase(hero) == 1, "a save in the transition keeps phase " + savedPhase(hero)
                            + " (the transition plays again after a load)");
                    float lost = blow(hero, s3, 4);
                    near(t, baseLoss[0] * 0.5, lost, 0.01, "a blow in the transition takes half");
                    t.assertTrue(!hero.combat().poiseDamage(1000), "a transition staggered");
                    t.assertTrue(hero.combat().poise().value() > hero.combat().poise().max() - 0.01, "the bar is not just short of full");
                }
                if (now == began[0] + 20) {
                    t.assertTrue(HeroActions.mood(hero.mood()) == HeroActions.moodCode("angry"), "its face is "
                            + HeroActions.MOODS.get(HeroActions.mood(hero.mood())));
                    t.assertTrue(hero.combat().boss().held(), "a transition does not hold it");
                }
                if (now == began[0] + 32) {
                    t.assertTrue(!hero.combat().boss().transitioning(), "the transition outlasted its 1.5 s");
                    t.assertTrue(!hero.stateFlag(HeroActions.STATE_SUPER_ARMOUR), "super armour stayed");
                    t.assertTrue("rage".equals(hero.combat().stateName()), "the phase's state: " + hero.combat().stateName());
                    near(t, 20, HeroStatus.percent(hero, HeroDef.Stat.ARMOR), EPS, "the phase's armour");
                    t.assertTrue(hero.combat().passiveFirings() >= 1, "on_phase did not fire");
                    HeroState rec = hero.campaignRecord();
                    t.assertTrue(rec == null || rec.phaseReached == 2, "phase reached " + (rec == null ? -1 : rec.phaseReached));
                    t.assertTrue(hero.combat().poiseDamage(1), "the first poise after the protection did not stagger");
                    // phase 3 from a mock player's blow, while it kneels: the transition waits for the stagger
                    hero.setHealth(hero.getMaxHealth() * 0.26F);
                    blow(hero, pushedPlayer, 3);
                    phase3Asked[0] = now;
                }
                if (phase3Asked[0] > 0 && now > phase3Asked[0] && hero.combat().staggered()) {
                    t.assertTrue(!hero.combat().boss().transitioning() && hero.combat().boss().phase() == 2,
                            "a transition began inside a stagger");
                }
                if (phase3Asked[0] > 0 && now == phase3Asked[0] + BossRules.STAGGER_TICKS + 3) {
                    t.assertTrue(hero.combat().boss().transitioning() && HeroActions.phase(hero.state()) == 3,
                            "phase 3 did not begin after the stagger");
                    t.assertTrue(HeroActions.code(hero.action()) == HeroActions.actionCode("phase.3"), "no phase.3 clip");
                }
                if (phase3Asked[0] > 0 && now == phase3Asked[0] + BossRules.STAGGER_TICKS + 30) {
                    t.assertTrue(!hero.combat().boss().transitioning(), "phase 3's transition outlasted 1.2 s");
                    t.assertTrue("rage".equals(hero.combat().stateName()), "phase 2's state ended in phase 3");
                    near(t, 20, HeroStatus.percent(hero, HeroDef.Stat.ARMOR), EPS, "phase 2's armour in phase 3");
                    t.assertTrue(hero.combat().boss().transitionCount() == 2, "transitions " + hero.combat().boss().transitionCount());
                    t.assertTrue(savedPhase(hero) == 3, "the saved phase is " + savedPhase(hero));
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the fight clock and the reset (6.1, 6.10)

    /**
     * {@code fightEndsTenSecondsAfterLastEnemy}: a fight begins with an NPC soldier as the hero's target and ends exactly
     * 10 s after the soldier is gone; a mock player's blow (not its target) begins the next, which ends 10 s after the
     * player is gone. 30 s after the fight and the last blow the hero heals 1% of its base health a second, not sooner,
     * and its phase re-arms 10% above its start: the look, the state and the stats of phase 2 go.
     */
    @GameTest(template = "platform", timeoutTicks = 1300, batch = "hero_boss_fight")
    public static void fightEndsTenSecondsAfterLastEnemy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 1300);
        try {
            String id = "fx_boss_fight";
            String boss = ", \"poise\": 100, \"phases\": [{\"at\": 100}, {\"at\": 60, \"states\": [\"rage\"], \"stats\": [{\"stat\": \"armor\","
                    + " \"percent\": 20}], \"transition\": {\"seconds\": 1.0}}]";
            String more = ", \"states\": [{\"name\": \"rage\", \"stats\": [{\"stat\": \"attack\", \"percent\": 20}]}]";
            HeroEntity hero = bossHero(t, id, bossDef(id, "striker", IDLE_MOVE, boss, more), 10, 10);
            hero.setNoAi(true);
            FactionNpc npc = soldier(t, 13, 10);
            ServerPlayer pl = swordPlayer(t, 7, 10);
            HeroBoss b = hero.combat().boss();
            long[] npcGone = {-1}, playerStruck = {-1}, playerGone = {-1}, fightEnded = {-1};
            float[] held = {-1};
            h.runAtTickTime(2, t.guard(() -> {
                hero.setHealth(hero.getMaxHealth() * 0.5F); // phase 2 (its transition plays while it fights)
                hero.setTarget(npc);
            }));
            h.runAtTickTime(40, t.guard(() -> {
                t.assertTrue(b.fighting(), "its target did not begin a fight");
                t.assertTrue(b.engaged() == 1, "engaged " + b.engaged());
                t.assertTrue(b.phase() == 2 && "rage".equals(hero.combat().stateName()), "phase 2 did not apply");
                npc.discard();
                npcGone[0] = now(h);
            }));
            t.onEachTick(() -> {
                long now = now(h);
                if (npcGone[0] > 0 && playerStruck[0] < 0) {
                    long since = now - npcGone[0];
                    if (since > 0 && since < BossRules.FIGHT_END_TICKS) {
                        t.assertTrue(b.fighting(), "the fight ended " + since + " ticks after its enemy was gone");
                    }
                    if (since == BossRules.FIGHT_END_TICKS) {
                        t.assertTrue(!b.fighting(), "the fight lasted past 10 s after its last enemy");
                        hero.setTarget(null);
                    }
                    if (since == BossRules.FIGHT_END_TICKS + 20) {
                        t.assertTrue(!b.fighting(), "a fight with nobody");
                        blow(hero, pl, 2); // a mock player strikes: not its target, still an engaged enemy
                        playerStruck[0] = now;
                    }
                    return;
                }
                if (playerStruck[0] > 0 && playerGone[0] < 0) {
                    if (now == playerStruck[0] + 2) {
                        t.assertTrue(b.fighting() && b.engaged() == 1, "the player's blow did not begin a fight (engaged "
                                + b.engaged() + ")");
                    }
                    if (now == playerStruck[0] + 40) {
                        pl.discard();
                        playerGone[0] = now;
                    }
                    return;
                }
                if (playerGone[0] > 0 && fightEnded[0] < 0) {
                    long since = now - playerGone[0];
                    if (since > 0 && since < BossRules.FIGHT_END_TICKS) {
                        t.assertTrue(b.fighting(), "the fight ended " + since + " ticks after the player was gone");
                    }
                    if (since == BossRules.FIGHT_END_TICKS) {
                        t.assertTrue(!b.fighting(), "the fight lasted past 10 s after the player was gone");
                        fightEnded[0] = now;
                        // just under the re-arm point of phase 2 (60 + 10)
                        hero.setHealth(hero.getMaxHealth() * 0.695F);
                        held[0] = hero.getHealth();
                    }
                    return;
                }
                if (fightEnded[0] > 0) {
                    long healFrom = Math.max(fightEnded[0], playerStruck[0]) + BossRules.HEAL_DELAY_TICKS;
                    if (now < healFrom - 1) {
                        near(t, held[0], hero.getHealth(), EPS, "it healed " + (healFrom - now) + " ticks before 30 s out of the fight");
                        t.assertTrue(b.phase() == 2, "the phase re-armed under 70%");
                    }
                    if (now == healFrom + 80) {
                        double base = hero.getMaxHealth() / 2.5 / BossRules.roleFactor(HeroDef.Role.STRIKER);
                        t.assertTrue(hero.getHealth() > held[0], "no healing 30 s out of the fight");
                        t.assertTrue(hero.getHealth() - held[0] <= base * 0.01 * 5 + EPS, "it healed more than 1% of its base a second: "
                                + (hero.getHealth() - held[0]) + " in 4 s (base " + base + ")");
                        t.assertTrue(b.phase() == 1 && HeroActions.phase(hero.state()) == 1, "phase 2 did not re-arm at 70%");
                        t.assertTrue(hero.combat().stateName() == null, "phase 2's state outlived its re-arm");
                        near(t, 0, HeroStatus.percent(hero, HeroDef.Stat.ARMOR), EPS, "phase 2's armour outlived its re-arm");
                        t.succeed();
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ telegraph decals (6.4)

    /**
     * {@code telegraphDecalsGrowAndClean}: a heavy cone's wind-up sends its decal (a fan, the move's own shape from
     * {@link Decals#forMove}, its colour, growing over exactly the wind-up) to the mock player standing near, and it is
     * cleared on the tick the cone lands. With no player within 32 blocks nothing is sent and the cone lands on the same
     * tick of its wind-up. A wind-up a stun interrupts clears its decal. A rain draws one disc per impact as it is
     * called, growing over the impact's delay and gone as it lands; the hero's death clears those still up.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_boss_decal")
    public static void telegraphDecalsGrowAndClean(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 260);
        try {
            String id = "fx_boss_decal";
            String fan = move("fan", "cone", "\"class\": \"area\", \"range\": 6, \"angle\": 90, \"weight\": 0, \"cooldown\": 1,"
                    + " \"frames\": {\"windup\": 1.0, \"active\": 0.2, \"recovery\": 0.6}, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]");
            String rain = move("downpour", "rain", "\"class\": \"area\", \"range\": 16, \"count\": 4, \"radius\": 3, \"duration\": 2,"
                    + " \"delay\": 1.0, \"weight\": 0, \"cooldown\": 1, \"frames\": {\"windup\": 1.0, \"active\": 2.0, \"recovery\": 0.6},"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            HeroEntity hero = bossHero(t, id, bossDef(id, "striker", fan + ", " + rain, ", \"phases\": [{\"at\": 100}]", ""), 8, 8);
            hero.setNoAi(true);
            Husk husk = husk(t, 11, 8, 500);
            ServerPlayer watcher = player(t, 5, 8, null, 100);
            int[] stage = {0};
            long[] started = {-1};
            // forced from the test, after the hero's tick: its first step is the next tick, so it lands the wind-up + 1 later
            final long lands = 21;
            HeroTelegraphs.Sent[] shown = {null};
            h.runAtTickTime(3, t.guard(() -> {
                force(t, hero, husk, "fan");
                started[0] = now(h);
                HeroTelegraphs.Sent s = last(hero, true);
                t.assertTrue(s != null && s.at() == started[0], "no decal sent at the wind-up's start");
                shown[0] = s;
                HeroDef.Move m = hero.definition().v2().move("fan");
                Vec3 o = hero.position(), aim = husk.position();
                Vec3 f = new Vec3(aim.x - o.x, 0, aim.z - o.z).normalize();
                List<Decals.Shape> want = Decals.forMove(m, new Decals.Aim(f.x, f.z, aim.x - o.x, aim.z - o.z, aim.x - o.x,
                        aim.z - o.z, List.of()));
                t.assertTrue(s.kind() == HeroDef.Decal.FAN, "a cone draws " + s.kind());
                t.assertTrue(s.pieces() == Decals.pieces(want), "pieces " + s.pieces() + ", the shape has " + Decals.pieces(want));
                float[] packed = Decals.pack(want, null);
                for (int i = 0; i < packed.length; i++) {
                    if (i % Decals.PIECE_FLOATS != 1) {
                        near(t, packed[i], s.packed()[i], 1e-3, "piece float " + i);
                    }
                }
                t.assertTrue(s.growTicks() == hero.combat().lastPlan().windup() && s.growTicks() == 20,
                        "it grows over " + s.growTicks() + " ticks; the wind-up is " + hero.combat().lastPlan().windup());
                t.assertTrue(s.to().contains(watcher.getUUID()), "the player within 32 blocks was not sent it");
                t.assertTrue(hero.combat().telegraphs().liveCount() == 1, "live " + hero.combat().telegraphs().liveCount());
                stage[0] = 1;
            }));
            t.onEachTick(() -> {
                long now = now(h);
                if (stage[0] == 1 && hero.combat().lastRelease() > 0) {
                    t.assertTrue(hero.combat().lastRelease() - started[0] == lands, "the cone landed " + (hero.combat().lastRelease()
                            - started[0]) + " ticks after its start");
                    HeroTelegraphs.Sent c = last(hero, false);
                    t.assertTrue(c != null && c.id() == shown[0].id() && c.at() == hero.combat().lastRelease(),
                            "the decal was not cleared on the tick the cone landed");
                    t.assertTrue(hero.combat().telegraphs().liveCount() == 0, "a decal outlived its wind-up");
                    stage[0] = 2;
                }
                if (stage[0] == 2 && !hero.combat().running()) {
                    // nobody near: nothing sent, the same timing
                    watcher.moveTo(watcher.getX() + 40, watcher.getY(), watcher.getZ());
                    force(t, hero, husk, "fan");
                    started[0] = now;
                    HeroTelegraphs.Sent s = last(hero, true);
                    t.assertTrue(s == null || s.at() < now, "a decal was sent with no player within 32 blocks");
                    stage[0] = 3;
                }
                if (stage[0] == 3 && hero.combat().lastMoveStart() == started[0] && hero.combat().lastRelease() > 0) {
                    t.assertTrue(hero.combat().lastRelease() - started[0] == lands, "without a decal the cone landed after "
                            + (hero.combat().lastRelease() - started[0]));
                    stage[0] = 4;
                }
                if (stage[0] == 4 && !hero.combat().running()) {
                    watcher.moveTo(watcher.getX() - 40, watcher.getY(), watcher.getZ());
                    force(t, hero, husk, "fan");
                    started[0] = now;
                    stage[0] = 5;
                }
                if (stage[0] == 5 && now == started[0] + 5) {
                    HeroTelegraphs.Sent s = last(hero, true);
                    t.assertTrue(s != null && s.at() == started[0], "no decal for the third wind-up");
                    t.assertTrue(HeroStatus.stun(hero, 1), "the stun did not land");
                    HeroTelegraphs.Sent c = last(hero, false);
                    t.assertTrue(c != null && c.id() == s.id() && c.at() == now, "a stun's interrupt did not clear the decal");
                    t.assertTrue(hero.combat().telegraphs().liveCount() == 0, "live " + hero.combat().telegraphs().liveCount());
                    stage[0] = 6;
                }
                if (stage[0] == 6 && !HeroStatus.stunned(hero)) {
                    force(t, hero, husk, "downpour");
                    started[0] = now;
                    t.assertTrue(last(hero, true).at() < now, "a rain's wind-up drew a decal of its own");
                    stage[0] = 7;
                }
                if (stage[0] == 7) {
                    HeroTelegraphs.Sent s = last(hero, true);
                    if (s != null && s.at() > started[0]) {
                        t.assertTrue(s.kind() == HeroDef.Decal.IMPACTS && s.pieces() == 2, "an impact draws " + s.kind() + " of "
                                + s.pieces());
                        t.assertTrue(s.growTicks() == 20, "an impact's disc grows over " + s.growTicks() + " ticks, its delay is 20");
                        shown[0] = s;
                        started[0] = now;
                        stage[0] = 8;
                    }
                }
                if (stage[0] == 8 && now == started[0] + 21) {
                    HeroTelegraphs.Sent landed = null;
                    for (HeroTelegraphs.Sent x : HeroTelegraphs.recent()) {
                        if (x.hero() == hero.getId() && !x.shown() && x.id() == shown[0].id()) {
                            landed = x;
                        }
                    }
                    t.assertTrue(landed != null && landed.at() - shown[0].at() >= 19 && landed.at() - shown[0].at() <= 21,
                            "the first impact's disc was not cleared as it landed: " + (landed == null ? "never" : landed.at() - shown[0].at()));
                    t.assertTrue(hero.combat().telegraphs().liveCount() >= 1, "no later impact's disc is up");
                    int heroId = hero.getId();
                    java.util.Set<Integer> up = new java.util.HashSet<>();
                    for (HeroTelegraphs.Sent x : HeroTelegraphs.recent()) {
                        if (x.hero() == heroId && x.shown() && x.kind() == HeroDef.Decal.IMPACTS) {
                            up.add(x.id());
                        } else if (x.hero() == heroId && !x.shown()) {
                            up.remove(x.id());
                        }
                    }
                    hero.hurt(hero.damageSources().genericKill(), Float.MAX_VALUE);
                    t.assertTrue(!hero.isAlive(), "the hero did not die");
                    for (HeroTelegraphs.Sent x : HeroTelegraphs.recent()) {
                        if (x.hero() == heroId && !x.shown() && x.at() == now) {
                            up.remove(x.id());
                        }
                    }
                    t.assertTrue(up.isEmpty(), "its death left impact discs up: " + up);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    static HeroTelegraphs.Sent last(HeroEntity hero, boolean shown) {
        return lastOf(hero.getId(), shown);
    }

    static HeroTelegraphs.Sent lastOf(int heroId, boolean shown) {
        HeroTelegraphs.Sent out = null;
        for (HeroTelegraphs.Sent s : HeroTelegraphs.recent()) {
            if (s.hero() == heroId && s.shown() == shown) {
                out = s;
            }
        }
        return out;
    }
}
