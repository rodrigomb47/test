package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.HeroState;
import com.factionclash.campaign.Recruitment;
import com.factionclash.combat.Alerts;
import com.factionclash.combat.StunnedGoal;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroDamage;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroForms;
import com.factionclash.hero.HeroKit;
import com.factionclash.hero.HeroStatus;
import com.factionclash.hero.HeroSummons;
import com.factionclash.hero.HeroTalents;
import com.factionclash.logic.Affix;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.MoveSelector;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.def;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.hero;
import static com.factionclash.gametest.HeroAbilityGameTests.heroHit;
import static com.factionclash.gametest.HeroAbilityGameTests.husk;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.near;
import static com.factionclash.gametest.HeroAbilityGameTests.noRegen;
import static com.factionclash.gametest.HeroAbilityGameTests.player;
import static com.factionclash.gametest.HeroAbilityGameTests.slice;

/**
 * Heroes, task A4 (HERO_FORMAT.md 32 phase 5 adapted to format 2): control (stun, taunt, dispel), states, summons,
 * passives with the loop guard, the target goal with no attack tokens, spacing, duties and alarms, the base abilities
 * read as talents, combat XP and growth, and the ground leap and dash. <b>Equal treatment</b> (HEROES_V2 6.15): every
 * control is checked on an NPC soldier and on a mock player (a survival {@code FakePlayer} of an enemy faction) with
 * the same seconds and numbers. Each test that runs a hero has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroControlGameTests {
    private HeroControlGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;
    private static final String W0 = "\"weight\": 0";

    private static boolean stunGoalRunning(Mob m) {
        return m.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof StunnedGoal);
    }

    // ------------------------------------------------------------------ stun (6.14, 6.15)

    /**
     * {@code stunStopsPlayerLikeNpc}: one hero's stun nova stops an enemy NPC soldier and an enemy mock player for the
     * same second: both lose their walking and jumping, neither's blow lands, neither can raise a shield, the player
     * cannot use an item, the soldier's goals are held by {@code StunnedGoal}. Both are free again on time, and a second
     * hero's stun inside the immunity window does nothing to either (6.14: the immunity is the receiver's).
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_ct_stun")
    public static void stunStopsPlayerLikeNpc(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String slam = move("slam", "nova", W0 + ", \"radius\": 6, \"effects\": [{\"type\": \"stun\", \"seconds\": 1}]");
            HeroEntity a = hero(t, "fx_ct_stun_a", "katana", def("fx_ct_stun_a", MELEE, "striker", slam, ""), 6, 6);
            HeroEntity b = hero(t, "fx_ct_stun_b", "katana", def("fx_ct_stun_b", MELEE, "striker", slam, ""), 6, 10);
            a.setNoAi(true);
            b.setNoAi(true);
            FactionNpc npc = foe(t, 8, 7, 100);
            npc.setNoAi(false); // its goals run, so StunnedGoal must hold them
            ServerPlayer p = player(t, 7, 8, FOE, 100);
            Husk dummy = husk(t, 9, 9, 200);
            long[] at = {-1};
            net.minecraft.world.phys.Vec3[] held = {null};
            force(t, a, npc, "slam");
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (at[0] < 0) {
                    if (HeroStatus.stunned(npc)) {
                        at[0] = now;
                        t.assertTrue(HeroStatus.stunned(p), "the same nova stuns the player in the same tick");
                    }
                    return;
                }
                long dt = now - at[0];
                if (dt == 6) {
                    held[0] = npc.position(); // what way it had on when stunned is spent by now
                }
                if (dt == 2) {
                    for (LivingEntity e : List.of(npc, p)) {
                        near(t, 0, e.getAttributeValue(Attributes.MOVEMENT_SPEED), e.getName().getString() + "'s speed");
                        near(t, 0, e.getAttributeValue(Attributes.JUMP_STRENGTH), e.getName().getString() + "'s jump");
                    }
                    t.assertTrue(stunGoalRunning(npc), "the soldier's StunnedGoal does not run");
                    float before = dummy.getHealth();
                    dummy.invulnerableTime = 0; // (the soldier may have struck it just before: no hurt cooldown hides a blow)
                    p.attack(dummy);
                    near(t, before, dummy.getHealth(), "the stunned player's blow landed");
                    dummy.invulnerableTime = 0;
                    npc.doHurtTarget(dummy);
                    near(t, before, dummy.getHealth(), "the stunned soldier's blow landed");
                    ItemStack balls = new ItemStack(Items.SNOWBALL, 4);
                    p.setItemInHand(InteractionHand.MAIN_HAND, balls);
                    InteractionResult r = p.gameMode.useItem(p, h.getLevel(), balls, InteractionHand.MAIN_HAND);
                    t.assertTrue(!r.consumesAction() && p.getMainHandItem().getCount() == 4, "the stunned player threw: " + r);
                    for (LivingEntity e : List.of(npc, p)) {
                        e.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                        e.startUsingItem(InteractionHand.OFF_HAND);
                        t.assertTrue(!e.isUsingItem(), e.getName().getString() + " raised a shield while stunned");
                    }
                    force(t, b, npc, "slam"); // its nova lands about 20 ticks later, inside the immunity
                }
                if (dt == 19) {
                    t.assertTrue(HeroStatus.stunned(npc) && HeroStatus.stunned(p), "both stunned for the whole second");
                    double moved = Math.hypot(npc.getX() - held[0].x, npc.getZ() - held[0].z);
                    t.assertTrue(moved < 0.05, "the stunned soldier walked " + moved + " blocks");
                }
                if (dt == 24) {
                    t.assertTrue(!HeroStatus.stun(npc, 1) && !HeroStatus.stun(p, 1),
                            "a stun from any source lands inside the immunity window");
                }
                if (dt == 30) {
                    t.assertTrue(b.combat().lastRelease() > at[0], "the second hero's nova has not landed");
                    for (LivingEntity e : List.of(npc, p)) {
                        t.assertTrue(!HeroStatus.stunned(e), e.getName().getString() + " is stunned again inside the immunity");
                        t.assertTrue(e.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, e.getName().getString() + " cannot walk");
                    }
                    t.assertTrue(!stunGoalRunning(npc), "the soldier's StunnedGoal still runs");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroStunInterruptsWindup}: a hero stunned in its wind-up (here by any source) loses the move: nothing lands,
     * the wind-up flag goes, half the cooldown comes back (v1 10.3) and it shows its stun pose.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_ct_interrupt")
    public static void heroStunInterruptsWindup(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            String id = "fx_ct_interrupt";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("cleave", "melee", "\"class\": \"heavy\", \"cooldown\": 10, \"effects\": [{\"type\": \"damage\", \"amount\": 4}]"), ""), 6, 6);
            Husk husk = husk(t, 7, 6, 200);
            hero.setTarget(husk);
            long[] stunnedAt = {-1};
            long[] readyBefore = {0};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                HeroDef.Move cleave = hero.definition().v2().move("cleave");
                MoveSelector s = hero.combat().selector();
                if (stunnedAt[0] < 0 && hero.combat().stage() == com.factionclash.logic.hero.MovePlan.MoveStage.WINDUP
                        && now - hero.combat().lastMoveStart() >= 4) {
                    readyBefore[0] = s.readyAt(cleave, now);
                    t.assertTrue(HeroStatus.stun(hero, 1), "the stun did not land");
                    stunnedAt[0] = now;
                    t.assertTrue(!hero.combat().running(), "the move runs on through a stun");
                    t.assertTrue(!hero.stateFlag(HeroActions.STATE_WINDUP), "the wind-up flag stayed");
                    t.assertTrue(HeroActions.code(hero.action()) == HeroActions.actionCode("stun"), "no stun pose");
                    long cd = HeroRules.ticks(cleave.ability().cooldown());
                    near(t, Math.max(now, readyBefore[0] - cd / 2), s.readyAt(cleave, now), "half the cooldown back");
                    return;
                }
                if (stunnedAt[0] > 0) {
                    t.assertTrue(!heroHit(husk, hero), "the interrupted move still landed");
                    if (now - stunnedAt[0] >= 30) {
                        t.succeed();
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ taunt and dispel (6.15)

    /**
     * {@code heroTauntRetargets}: a taunt makes an enemy soldier target the hero and keeps it there (set back within a
     * second), the hero glows, and for its seconds the soldier and an enemy mock player alike deal half their damage
     * to anyone but the hero (6.15), compared with the same blow before the taunt.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_ct_taunt")
    public static void heroTauntRetargets(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            String id = "fx_ct_taunt";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "vanguard",
                    move("provoke", "nova", W0 + ", \"radius\": 6, \"effects\": [{\"type\": \"taunt\", \"seconds\": 4}]"), ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc foeN = foe(t, 9, 6, 100);
            ServerPlayer p = player(t, 8, 8, FOE, 100);
            FactionNpc ally = ally(t, 12, 3);
            FactionNpc b0 = ally(t, 13, 6), c0 = ally(t, 13, 9), b1 = ally(t, 14, 6), c1 = ally(t, 14, 9);
            foeN.setTarget(ally);
            b0.hurt(foeN.damageSources().mobAttack(foeN), 8);
            c0.hurt(p.damageSources().playerAttack(p), 8);
            float lossN0 = 100 - b0.getHealth(), lossP0 = 100 - c0.getHealth();
            t.assertTrue(lossN0 > 1 && Math.abs(lossN0 - lossP0) < 1e-3, "the baselines differ: " + lossN0 + " / " + lossP0);
            force(t, hero, foeN, "provoke");
            long[] at = {-1};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (at[0] < 0) {
                    if (HeroStatus.peek(foeN) != null && HeroStatus.peek(foeN).book().tauntedBy(now) != null) {
                        at[0] = now;
                        t.assertTrue(foeN.getTarget() == hero, "the taunted soldier targets " + foeN.getTarget());
                        t.assertTrue(hero.hasEffect(MobEffects.GLOWING), "the taunting hero does not glow");
                        t.assertTrue(HeroStatus.peek(p) != null && hero.getStringUUID().equals(HeroStatus.peek(p).book().tauntedBy(now)),
                                "the player is not taunted");
                        b1.hurt(foeN.damageSources().mobAttack(foeN), 8);
                        c1.hurt(p.damageSources().playerAttack(p), 8);
                        float lossN1 = 100 - b1.getHealth(), lossP1 = 100 - c1.getHealth();
                        near(t, lossN0 * 0.5, lossN1, "the taunted soldier's blow on someone else");
                        near(t, lossP0 * 0.5, lossP1, "the taunted player's blow on someone else");
                        near(t, 1, HeroStatus.tauntFactor(foeN, hero), "a blow on the taunting hero itself");
                        near(t, 1, HeroStatus.tauntFactor(p, hero), "a player's blow on the taunting hero itself");
                        foeN.setTarget(ally);
                    }
                    return;
                }
                if (now - at[0] == 20) {
                    t.assertTrue(foeN.getTarget() == hero, "the taunt did not hold the soldier: " + foeN.getTarget());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /** An allied soldier of the test base standing still with 100 health and no regeneration. */
    static FactionNpc ally(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = noRegen(t.npc(x, z, NpcRole.SWORDSMAN));
        n.setNoAi(true);
        n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        n.setHealth(100);
        return n;
    }

    /**
     * {@code dispelTakesTheHelpfulPart}: a dispel strips an NPC soldier and a mock player alike of their helpful potion
     * effects, absorption, hero shield and helpful hero stat, and leaves the harmful ones (poison, a slow).
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ct_dispel")
    public static void dispelTakesTheHelpfulPart(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ct_dispel";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support",
                    move("strip", "nova", W0 + ", \"radius\": 5, \"effects\": [{\"type\": \"dispel\"}]"), ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc npc = foe(t, 8, 6, 100);
            ServerPlayer p = player(t, 6, 8, FOE, 100);
            long now = h.getLevel().getGameTime();
            for (LivingEntity e : List.of(npc, p)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
                e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0));
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
                HeroStatus st = HeroStatus.of(e);
                st.shield(e, 4, 20);
                st.book().stat("x", "up", Stat.ATTACK, 20, 20, 1, now);
                st.book().stat("x", "down", Stat.SPEED, -20, 20, 1, now);
            }
            h.runAtTickTime(3, t.guard(() -> {
                for (LivingEntity e : List.of(npc, p)) {
                    t.assertTrue(e.getAbsorptionAmount() > 4, e.getName().getString() + " had no absorption to lose");
                }
                force(t, hero, npc, "strip");
            }));
            h.runAtTickTime(40, t.guard(() -> {
                long n = h.getLevel().getGameTime();
                for (LivingEntity e : List.of(npc, p)) {
                    String who = e.getName().getString();
                    t.assertTrue(!e.hasEffect(MobEffects.MOVEMENT_SPEED) && !e.hasEffect(MobEffects.ABSORPTION), who + " kept a helpful effect");
                    near(t, 0, e.getAbsorptionAmount(), who + "'s absorption");
                    t.assertTrue(e.hasEffect(MobEffects.POISON), who + " lost its poison");
                    near(t, 0, HeroStatus.percent(e, Stat.ATTACK), who + "'s attack bonus");
                    near(t, -20, HeroStatus.percent(e, Stat.SPEED), who + "'s slow");
                    t.assertTrue(e.getAttribute(Attributes.ATTACK_DAMAGE) == null
                            || e.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(HeroStatus.statId(Stat.ATTACK)) == null, who + " kept the attack modifier");
                    t.assertTrue(HeroStatus.peek(e).book().shield(n) == null, who + " kept the hero shield");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ states (12.9)

    /**
     * {@code heroEmpowerAppliesAndEnds}: Natsuho's Midnight Frenzy (the slice fixture) enters her {@code frenzy}
     * state: attack speed +25%, speed +20%, her angry face, the state's bleed on her basic hits; the state survives a
     * save; it ends after its 8 s and every bonus goes.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ct_empower")
    public static void heroEmpowerAppliesAndEnds(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            HeroEntity hero = hero(t, "natsuho_komezaki", "fists", slice("natsuho_komezaki"), 6, 6);
            hero.setNoAi(true);
            Husk husk = husk(t, 7, 6, 200);
            force(t, hero, husk, "midnight_frenzy");
            long[] at = {-1};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (at[0] < 0) {
                    if ("frenzy".equals(hero.combat().stateName())) {
                        at[0] = now;
                        near(t, 0.25, hero.getAttribute(Attributes.ATTACK_SPEED).getModifier(HeroStatus.statId(Stat.ATTACK_SPEED)).amount(), "attack speed");
                        near(t, 0.20, hero.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HeroStatus.statId(Stat.SPEED)).amount(), "speed");
                        near(t, now + 160, hero.combat().stateUntil(), "8 s");
                    }
                    return;
                }
                long dt = now - at[0];
                if (dt == 2) {
                    t.assertTrue(HeroActions.mood(hero.mood()) == HeroActions.moodCode("angry"), "the frenzy's face");
                    hero.combat().onBasicHit(husk, hero);
                    t.assertTrue(HeroStatus.peek(husk) != null && HeroStatus.peek(husk).book().hasDot(now), "the frenzy's bleed");
                    CompoundTag tag = hero.saveWithoutId(new CompoundTag());
                    HeroEntity loaded = ModEntities.HERO.get().create(h.getLevel());
                    loaded.load(tag);
                    t.assertTrue("frenzy".equals(loaded.combat().stateName()), "the state did not survive a save");
                    loaded.discard();
                }
                if (dt == 161) {
                    t.assertTrue(hero.combat().stateName() == null, "the frenzy lasts past 8 s");
                    t.assertTrue(hero.getAttribute(Attributes.ATTACK_SPEED).getModifier(HeroStatus.statId(Stat.ATTACK_SPEED)) == null,
                            "the attack speed stayed");
                    near(t, 0, HeroStatus.percent(hero, Stat.SPEED), "the speed bonus");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ summons (v1 20)

    /**
     * {@code heroSummonFightsAndVanishes}: a hero's howl calls two wolves that take its target (they bite the husk, never
     * the allied soldier beside it); a second howl keeps the hero at 3 (its oldest goes); when their 10 s are up every
     * one vanishes, and none drops anything.
     */
    @GameTest(template = "platform", timeoutTicks = 320, batch = "hero_ct_summon")
    public static void heroSummonFightsAndVanishes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 320);
        try {
            String id = "fx_ct_summon";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("howl", "self", W0 + ", \"effects\": [{\"type\": \"summon\", \"creature\": \"wolf\", \"count\": 2,"
                            + " \"seconds\": 10, \"name\": \"Pack\"}]"), ""), 5, 6);
            hero.setNoAi(true);
            Husk husk = husk(t, 10, 6, 400);
            FactionNpc ally = ally(t, 11, 7);
            hero.setTarget(husk);
            h.runAtTickTime(2, t.guard(() -> force(t, hero, husk, "howl")));
            h.runAtTickTime(60, t.guard(() -> force(t, hero, husk, "howl")));
            boolean[] bitten = {false};
            int[] most = {0};
            t.onEachTick(() -> {
                List<Mob> mine = HeroSummons.of(hero);
                most[0] = Math.max(most[0], mine.size());
                t.assertTrue(mine.size() <= 3, "more than 3 summons: " + mine.size());
                if (husk.getLastDamageSource() != null && husk.getLastDamageSource().getEntity() instanceof Wolf w && HeroSummons.isSummon(w)) {
                    bitten[0] = true;
                }
                near(t, 100, ally.getHealth(), "a summon bit the allied soldier");
                for (Mob m : mine) {
                    t.assertTrue(m.getTarget() == null || m.getTarget() == husk, "a summon targets " + m.getTarget());
                }
            });
            h.runAtTickTime(300, t.guard(() -> {
                t.assertTrue(bitten[0], "no wolf ever bit the husk");
                t.assertTrue(most[0] == 3, "the second howl should leave exactly 3, saw at most " + most[0]);
                AABB box = new AABB(hero.blockPosition()).inflate(32);
                t.assertTrue(h.getLevel().getEntitiesOfClass(Wolf.class, box, HeroSummons::isSummon).isEmpty(), "a wolf outlived its seconds");
                t.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).isEmpty(), "a summon dropped loot");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ passives and the loop guard (v1 17)

    /** {@code heroPassiveOnHitFires}: an {@code on_hit} passive brands the one its basic attack hits, after its cooldown again. */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ct_onhit")
    public static void heroPassiveOnHitFires(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ct_onhit";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker", "",
                    ", \"passives\": [{\"name\": \"Brand\", \"trigger\": \"on_hit\", \"form\": \"trigger_enemy\", \"cooldown\": 1,"
                            + " \"effects\": [{\"type\": \"mark\", \"mark\": \"brand\", \"stacks\": 1, \"max_stacks\": 5, \"seconds\": 20}]}]"), 6, 6);
            hero.setNoAi(true);
            Husk husk = husk(t, 7, 6, 200);
            h.runAtTickTime(2, t.guard(() -> t.assertTrue(hero.doHurtTarget(husk), "the blow missed")));
            h.runAtTickTime(4, t.guard(() -> {
                t.assertTrue(HeroStatus.peek(husk).book().marks("brand", h.getLevel().getGameTime()) == 1, "no brand after the hit");
                t.assertTrue(hero.combat().passiveFirings() == 1, "firings " + hero.combat().passiveFirings());
            }));
            h.runAtTickTime(40, t.guard(() -> t.assertTrue(hero.doHurtTarget(husk), "the second blow missed")));
            h.runAtTickTime(42, t.guard(() -> {
                t.assertTrue(HeroStatus.peek(husk).book().marks("brand", h.getLevel().getGameTime()) == 2, "the second hit's brand");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroFollowUpDoesNotLoop} (the loop guard, v1 17.3): two heroes whose {@code on_ally_attack} passives join
     * an ally's attack each answer an allied soldier's one blow once; their own passive damage sets off neither's
     * passive again, and a summoned wolf's bites set off none at all.
     */
    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_ct_loop")
    public static void heroFollowUpDoesNotLoop(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            String join = ", \"passives\": [{\"name\": \"Join In\", \"trigger\": \"on_ally_attack\", \"form\": \"trigger_enemy\","
                    + " \"cooldown\": 2, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]";
            String howl = move("howl", "self", W0 + ", \"effects\": [{\"type\": \"summon\", \"creature\": \"wolf\", \"count\": 1, \"seconds\": 10}]");
            HeroEntity a = hero(t, "fx_ct_loop_a", "katana", def("fx_ct_loop_a", MELEE, "striker", howl, join), 5, 5);
            HeroEntity b = hero(t, "fx_ct_loop_b", "katana", def("fx_ct_loop_b", MELEE, "striker", "", join), 5, 9);
            a.setNoAi(true);
            b.setNoAi(true);
            FactionNpc soldier = ally(t, 7, 7);
            Husk husk = husk(t, 9, 7, 400);
            h.runAtTickTime(2, t.guard(() -> husk.hurt(soldier.damageSources().mobAttack(soldier), 3)));
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(a.combat().passiveFirings() == 1 && b.combat().passiveFirings() == 1,
                        "each hero joins once: " + a.combat().passiveFirings() + ", " + b.combat().passiveFirings());
                t.assertTrue(heroHit(husk, a) || heroHit(husk, b), "the passives did not strike");
                a.setTarget(husk);
                force(t, a, husk, "howl");
            }));
            boolean[] bitten = {false};
            t.onEachTick(() -> {
                if (husk.getLastDamageSource() != null && husk.getLastDamageSource().getEntity() instanceof Wolf w && HeroSummons.isSummon(w)) {
                    bitten[0] = true;
                }
                t.assertTrue(a.combat().passiveFirings() <= 1 && b.combat().passiveFirings() <= 1,
                        "a passive fired again: " + a.combat().passiveFirings() + ", " + b.combat().passiveFirings());
            });
            h.runAtTickTime(240, t.guard(() -> {
                t.assertTrue(bitten[0], "the wolf never bit");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroMarkFullFires}: a mark this hero fills sets off its {@code on_mark_full} passive once; the passive's own
     * effects fill the mark again and do not set it off again (v1 17.3).
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_ct_markfull")
    public static void heroMarkFullFires(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_ct_markfull";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker",
                    move("hex", "melee", W0 + ", \"effects\": [{\"type\": \"mark\", \"mark\": \"hex\", \"stacks\": 3, \"max_stacks\": 3}]"),
                    ", \"passives\": [{\"name\": \"Hex Burst\", \"trigger\": \"on_mark_full\", \"mark\": \"hex\", \"form\": \"trigger_enemy\","
                            + " \"cooldown\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 3}, {\"type\": \"clear_marks\", \"mark\": \"hex\"},"
                            + " {\"type\": \"mark\", \"mark\": \"hex\", \"stacks\": 3, \"max_stacks\": 3}]}]"), 6, 6);
            hero.setNoAi(true);
            Husk husk = husk(t, 7, 6, 200);
            force(t, hero, husk, "hex");
            t.onEachTick(() -> {
                if (hero.combat().passiveFirings() > 0) {
                    t.assertTrue(hero.combat().passiveFirings() == 1, "the passive set itself off");
                    t.assertTrue(heroHit(husk, hero), "the burst did not strike");
                    t.assertTrue(HeroStatus.peek(husk).book().marks("hex", h.getLevel().getGameTime()) == 3, "the hex was not put back");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code heroPassivesAnswerNpcAndPlayerAlike}: an {@code on_hurt} passive marks whoever hurt the hero, an NPC soldier
     * and a mock player alike; an {@code on_low_health} passive shields it once it drops under its value; an
     * {@code always} passive holds its stat bonus while its {@code if} holds and lets it go when it no longer does.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_ct_passives")
    public static void heroPassivesAnswerNpcAndPlayerAlike(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String id = "fx_ct_passives";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "vanguard", "",
                    ", \"passives\": [{\"name\": \"Spite\", \"trigger\": \"on_hurt\", \"form\": \"trigger_enemy\", \"cooldown\": 1,"
                            + " \"effects\": [{\"type\": \"mark\", \"mark\": \"spite\", \"stacks\": 1, \"max_stacks\": 3, \"seconds\": 30}]},"
                            + " {\"name\": \"Last Wall\", \"trigger\": \"on_low_health\", \"trigger_value\": 40, \"form\": \"self\","
                            + " \"effects\": [{\"type\": \"shield\", \"amount\": 3}]},"
                            + " {\"name\": \"Grit\", \"trigger\": \"always\", \"if\": {\"condition\": \"self_health_below\", \"value\": 50},"
                            + " \"effects\": [{\"type\": \"stat\", \"stat\": \"attack\", \"percent\": 10}]}]"), 6, 6);
            hero.setNoAi(true);
            FactionNpc npc = foe(t, 8, 6, 100);
            ServerPlayer p = player(t, 4, 6, FOE, 100);
            h.runAtTickTime(2, t.guard(() -> hero.hurt(npc.damageSources().mobAttack(npc), 2)));
            h.runAtTickTime(60, t.guard(() -> hero.hurt(p.damageSources().playerAttack(p), 2)));
            h.runAtTickTime(62, t.guard(() -> {
                long now = h.getLevel().getGameTime();
                t.assertTrue(HeroStatus.peek(npc) != null && HeroStatus.peek(npc).book().marks("spite", now) == 1, "the soldier was not marked");
                t.assertTrue(HeroStatus.peek(p) != null && HeroStatus.peek(p).book().marks("spite", now) == 1, "the player was not marked");
                near(t, 0, HeroStatus.percent(hero, Stat.ATTACK), "grit before its condition holds");
                hero.setHealth(hero.getMaxHealth() * 0.4F + 1);
                hero.hurt(npc.damageSources().mobAttack(npc), 4);
                t.assertTrue(hero.getHealth() < hero.getMaxHealth() * 0.4F, "the hero is not under 40%");
                t.assertTrue(hero.getAbsorptionAmount() > 0, "no low-health shield");
            }));
            h.runAtTickTime(90, t.guard(() -> {
                near(t, 10, HeroStatus.percent(hero, Stat.ATTACK), "grit under half health");
                hero.setHealth(hero.getMaxHealth());
                hero.heal(0.01F);
            }));
            h.runAtTickTime(120, t.guard(() -> {
                near(t, 0, HeroStatus.percent(hero, Stat.ATTACK), "grit stays at full health");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the target goal (no attack tokens)

    private static String presser(String id) {
        return def(id, MELEE, "striker", move("cut", "melee", "\"cooldown\": 6, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"), "");
    }

    /**
     * {@code threeHeroesPressOneTarget}: three hostile heroes all take one enemy soldier and stand in melee on it at once
     * (no attack tokens: none circles or waits for a turn), and no move of theirs lands before the opening gap plus its
     * wind-up (6.3). {@link #threeHeroesPressOnePlayer} is the same on a mock player.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ct_three")
    public static void threeHeroesPressOneTarget(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            FactionNpc target = foe(t, 8, 8, 1000);
            pressThree(h, t, "fx_ct_three", target);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ct_three_p")
    public static void threeHeroesPressOnePlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            ServerPlayer target = player(t, 8, 8, FOE, 1000);
            pressThree(h, t, "fx_ct_threep", target);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    private static void pressThree(GameTestHelper h, GameTestBases.Handle t, String prefix, LivingEntity target) {
        List<HeroEntity> heroes = new ArrayList<>();
        int[][] at = {{3, 3}, {3, 13}, {13, 3}};
        for (int i = 0; i < 3; i++) {
            String id = prefix + "_" + i;
            heroes.add(hero(t, id, "katana", presser(id), at[i][0], at[i][1]));
        }
        Map<HeroEntity, Long> engaged = new HashMap<>();
        t.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            int inReach = 0;
            for (HeroEntity hero : heroes) {
                LivingEntity tg = hero.getTarget();
                t.assertTrue(tg == null || tg == target, hero.heroId() + " took " + tg);
                if (tg == target) {
                    engaged.putIfAbsent(hero, now);
                }
                if (hero.combat().lastRelease() > 0) {
                    long after = hero.combat().lastRelease() - engaged.getOrDefault(hero, now);
                    t.assertTrue(after >= 36, hero.heroId() + "'s move landed " + after + " ticks after it engaged");
                }
                if (tg == target && HeroForms.inMeleeReach(hero, target)) {
                    inReach++;
                }
            }
            if (inReach == 3) {
                t.succeed(); // all three press the one target together
            }
        });
    }

    // ------------------------------------------------------------------ spacing (v1 5.2, 8)

    /** {@code skirmisherStepsBackAfterAHit}: a skirmisher steps back after a landed blow, then comes in again. */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_ct_skirmish")
    public static void skirmisherStepsBackAfterAHit(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String id = "fx_ct_skirmish";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "skirmisher", "", ""), 4, 6);
            Husk husk = husk(t, 10, 6, 400);
            long[] hitAt = {-1};
            double[] atHit = {0}, most = {0};
            int[] hits = {0};
            float[] last = {400};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                boolean basic = husk.getHealth() < last[0] - 1e-4 && husk.getLastDamageSource() != null
                        && husk.getLastDamageSource().getEntity() == hero && !husk.getLastDamageSource().is(HeroDamage.HERO);
                last[0] = husk.getHealth();
                if (basic) {
                    hits[0]++;
                    if (hitAt[0] < 0) {
                        hitAt[0] = now;
                        atHit[0] = hero.distanceTo(husk);
                    }
                }
                if (hitAt[0] > 0 && now - hitAt[0] <= 16) {
                    most[0] = Math.max(most[0], hero.distanceTo(husk));
                }
                if (hitAt[0] > 0 && now - hitAt[0] == 17) {
                    t.assertTrue(most[0] >= atHit[0] + 1.5, "it did not step back: " + atHit[0] + " -> at most " + most[0]);
                }
                if (hits[0] >= 2 && now - hitAt[0] > 17) {
                    t.succeed(); // and it came back in for the next blow
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code meleeSupportHoldsItsDistance}: a melee support hero (keep distance 8) with a ready move that reaches that far
     * comes in to about 8 blocks of its target and holds there instead of closing to melee (v1 8).
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_ct_hold")
    public static void meleeSupportHoldsItsDistance(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String id = "fx_ct_hold";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support",
                    move("spark", "bolt", W0 + ", \"range\": 10, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"), ""), 1, 6);
            Husk husk = husk(t, 14, 6, 200);
            t.onEachTick(() -> {
                t.assertTrue(husk.getHealth() >= 200, "the holding hero closed in and struck");
                if (h.getTick() >= 150) {
                    double d = hero.distanceTo(husk);
                    t.assertTrue(hero.getTarget() == husk, "it never took the husk");
                    t.assertTrue(d >= 4 && d <= 9.5, "it should hold about 8 blocks off, it stands " + d);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ duties and alarms (v1 8, 25.6; HEROES_V2 7.1)

    /**
     * {@code heroWorksItsDuty}: a fisherman-duty hero counts as a fisherman (its role and roster entry), takes a rod (its
     * kit weapon goes to its backpack, never lost) and catches fish at the base's pond as a fisherman does.
     */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "hero_ct_duty", skyAccess = true)
    public static void heroWorksItsDuty(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 900);
        try {
            java.util.Set<BlockPos> pond = FCGameTests.sunkenPond(h, 15, 15, 21, 21);
            var s = com.factionclash.work.FishingSpots.search(h.getLevel(), t.data, t.base, t.base.center, 11, 11, List.of());
            FCGameTests.assertGoodSpot(h, s, pond, java.util.Set.of());
            var w = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.FISHERY, t.base.center);
            com.factionclash.work.FishingSpots.apply(t.data, w, s);
            String id = "fx_ct_duty";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker\", \"duty\": \"fisherman", "", ""), 6, 6);
            t.assertTrue(hero.getRole() == NpcRole.FISHERMAN && hero.onWorkerDuty(), "role " + hero.getRole());
            t.assertTrue(t.data.faction(t.base.faction).roster.get(hero.getUUID()).role == NpcRole.FISHERMAN, "the roster's role");
            t.assertTrue(hero.fightsLikeSoldier() && hero.isGuardDuty(), "a fisherman-duty hero still fights");
            hero.equip(FCGameTests.lureRod(h));
            t.assertTrue(hero.getMainHandItem().is(Items.FISHING_ROD), "no rod in hand");
            t.assertTrue(kitInBackpack(hero), "the kit weapon was lost when the rod came");
            t.onEachTick(() -> {
                if (w.totalOutput > 0) {
                    t.assertTrue(kitInBackpack(hero) || HeroKit.isKit(hero.getMainHandItem()), "the kit weapon is gone");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    private static boolean kitInBackpack(HeroEntity hero) {
        for (int i = 0; i < hero.inventory().getContainerSize(); i++) {
            if (HeroKit.isKit(hero.inventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code heroAnswersAlarmsAndDrawsItsWeapon}: an alarm calls a worker-duty hero like a soldier (v1 29.3), and the
     * hero puts its rod away and draws its kit weapon for the fight.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "hero_ct_alarm")
    public static void heroAnswersAlarmsAndDrawsItsWeapon(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 60);
        try {
            String id = "fx_ct_alarm";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker\", \"duty\": \"fisherman", "", ""), 6, 6);
            hero.setNoAi(true);
            hero.equip(new ItemStack(Items.FISHING_ROD));
            t.assertTrue(!HeroKit.isKit(hero.getMainHandItem()) && kitInBackpack(hero), "the rod is not in hand");
            FactionNpc caller = ally(t, 7, 8);
            FactionNpc intruder = foe(t, 12, 6, 100);
            int called = Alerts.callGuards(caller, intruder, 16);
            t.assertTrue(called >= 1 && hero.getTarget() == intruder, "the alarm did not call the hero: " + hero.getTarget());
            h.runAtTickTime(12, t.guard(() -> {
                t.assertTrue(HeroKit.isKit(hero.getMainHandItem()), "the hero fights with " + hero.getMainHandItem());
                boolean rod = false;
                for (int i = 0; i < hero.inventory().getContainerSize(); i++) {
                    rod |= hero.inventory().getItem(i).is(Items.FISHING_ROD);
                }
                t.assertTrue(rod, "the rod was lost");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ base abilities as talents (v1 21, HEROES_V2 7.2)

    /**
     * {@code heroBaseAbilitiesWork}: at its home base a hero's {@code aura} gives the listed workers an extra affix stack
     * (a miner one Prospector stack, a swordsman none), its {@code rally} makes a recruit's food 10% cheaper, and its
     * {@code mend} heals a hurt allied soldier and a hurt member mock player alike, out of a fight.
     */
    @GameTest(template = "platform", timeoutTicks = 280, batch = "hero_ct_talents")
    public static void heroBaseAbilitiesWork(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 280);
        try {
            String id = "fx_ct_talents";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "support", "",
                    ", \"talents\": [{\"id\": \"aura\", \"affix\": \"prospector\", \"roles\": [\"miner\"]}, \"mend\", \"rally\"]"), 6, 6);
            hero.setNoAi(true);
            FactionNpc miner = t.npc(8, 6, NpcRole.MINER);
            miner.setNoAi(true);
            FactionNpc sword = ally(t, 8, 8);
            double minerBefore = miner.affix(Affix.PROSPECTOR), swordBefore = sword.affix(Affix.PROSPECTOR);
            int foodBefore = FCConfig.RECRUIT_FOOD_VALUE.get();
            FactionNpc hurt = ally(t, 4, 6);
            hurt.setHealth(50);
            ServerPlayer p = player(t, 6, 4, SIDE, 100);
            p.setHealth(50);
            HeroTalents.refresh();
            near(t, Math.min(miner.affixes().getOrDefault(Affix.PROSPECTOR, 0) + 1, Affix.PROSPECTOR.maxStacks) * Affix.PROSPECTOR.perStack,
                    miner.affix(Affix.PROSPECTOR), "the miner's aura stack");
            t.assertTrue(miner.affix(Affix.PROSPECTOR) > minerBefore, "no aura on the miner");
            near(t, swordBefore, sword.affix(Affix.PROSPECTOR), "a swordsman got the miners' aura");
            t.assertTrue(Recruitment.foodCost(t.base) == (int) Math.round(foodBefore * 0.9), "rally: " + Recruitment.foodCost(t.base));
            t.onEachTick(() -> {
                if (hurt.getHealth() > 50 || p.getHealth() > 50) {
                    near(t, 55, hurt.getHealth(), "the soldier's mend");
                    near(t, 55, p.getHealth(), "the player's mend");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ combat XP and growth (v1 22, HEROES_V2 8)

    /**
     * {@code heroCombatXpAndGrowth}: a kill gives 10 + half the victim's max health (a 200-health husk: 110, enough for
     * level 2), healing another gives 1 per 5 points, and the level grows the hero's numbers (health and ability power
     * by its growth profile); the campaign's record follows.
     */
    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_ct_xp")
    public static void heroCombatXpAndGrowth(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_ct_xp";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker", "", ""), 6, 6);
            hero.setNoAi(true);
            Husk husk = husk(t, 7, 6, 200);
            int level0 = hero.npcLevel(), xp0 = hero.xp();
            double max1 = hero.getMaxHealth(), power1 = hero.combat().abilityPower();
            husk.hurt(hero.damageSources().mobAttack(hero), 10000);
            t.assertTrue(!husk.isAlive(), "the husk lived");
            var expect = com.factionclash.logic.AffixRoller.addXp(level0, xp0, 110);
            t.assertTrue(expect.levelsGained() >= 1, "110 XP should be worth a level from " + level0);
            t.assertTrue(hero.npcLevel() == expect.level() && hero.xp() == expect.xp(), "110 XP: level " + expect.level() + " with "
                    + expect.xp() + ", got level " + hero.npcLevel() + " xp " + hero.xp());
            t.assertTrue(hero.getMaxHealth() > max1, "no health growth: " + max1 + " -> " + hero.getMaxHealth());
            t.assertTrue(hero.combat().abilityPower() > power1, "no ability power growth");
            int xp1 = hero.xp();
            hero.addHealXp(10);
            t.assertTrue(hero.xp() == xp1 + 2, "10 points healed: 2 XP, xp " + xp1 + " -> " + hero.xp());
            h.runAtTickTime(45, t.guard(() -> {
                HeroState s = hero.campaignRecord();
                t.assertTrue(s != null && s.level == expect.level(), "the record's level " + (s == null ? null : s.level));
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ ground leap and dash (v1 10.1)

    /**
     * {@code dashStopsAtDropsAndWalls}: a ground dash stops at the edge of a 3-deep trench instead of running into it,
     * and at a wall instead of climbing it; the hero ends on its feet, unhurt, on its own side.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_ct_dash")
    public static void dashStopsAtDropsAndWalls(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            Map<BlockPos, BlockState> saved = new HashMap<>();
            for (int x = 7; x <= 8; x++) {
                for (int z = 1; z <= 5; z++) {
                    for (int y = -1; y <= 1; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        saved.put(p, h.getBlockState(p));
                        h.setBlock(p, Blocks.AIR);
                    }
                }
            }
            for (int z = 9; z <= 13; z++) {
                for (int y = 2; y <= 4; y++) {
                    BlockPos p = new BlockPos(7, y, z);
                    saved.put(p, h.getBlockState(p));
                    h.setBlock(p, Blocks.STONE);
                }
            }
            t.onCleanup(() -> saved.forEach(h::setBlock));
            String lunge = move("lunge", "dash", W0 + ", \"range\": 8, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]");
            HeroEntity a = hero(t, "fx_ct_dash_a", "katana", def("fx_ct_dash_a", MELEE, "striker", lunge, ""), 3, 3);
            HeroEntity b = hero(t, "fx_ct_dash_b", "katana", def("fx_ct_dash_b", MELEE, "striker", lunge, ""), 3, 11);
            Husk ha = husk(t, 11, 3, 100), hb = husk(t, 11, 11, 100);
            double y0 = a.getY();
            float hpA = a.getHealth(), hpB = b.getHealth();
            double x0 = a.getX();
            h.runAtTickTime(2, t.guard(() -> {
                force(t, a, ha, "lunge");
                force(t, b, hb, "lunge");
            }));
            boolean[] done = {false, false};
            t.onEachTick(() -> {
                // each dash is judged the tick its move ends (its recovery holds the hero still); later the hero's
                // own fighting may walk it round the trench or the wall
                if (!done[0] && a.combat().lastRelease() > 0 && !a.combat().running()) {
                    done[0] = true;
                    BlockPos edge = h.absolutePos(new BlockPos(7, 2, 3));
                    t.assertTrue(a.getX() - x0 >= 1.5, "the dash did not carry the hero: " + (a.getX() - x0));
                    t.assertTrue(a.getX() < edge.getX() + 0.2, "the dash ran into the trench: x " + a.getX() + ", edge " + edge.getX());
                    near(t, y0, a.getY(), "the dashing hero fell");
                    t.assertTrue(a.onGround(), "not on its feet after the dash");
                    near(t, hpA, a.getHealth(), "the trench hurt the hero");
                }
                if (!done[1] && b.combat().lastRelease() > 0 && !b.combat().running()) {
                    done[1] = true;
                    BlockPos wall = h.absolutePos(new BlockPos(7, 2, 11));
                    t.assertTrue(b.getX() < wall.getX(), "the dash went through the wall: x " + b.getX());
                    near(t, y0, b.getY(), "the dashing hero climbed the wall");
                    t.assertTrue(b.onGround(), "not on its feet after the dash at the wall");
                    near(t, hpB, b.getHealth(), "the wall hurt the hero");
                }
                if (done[0] && done[1]) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ blindness (6.15)

    /**
     * {@code blindedNpcSeesFiveBlocks}: an NPC soldier and a hero under Blindness do not take an enemy 8 blocks away
     * (their search is cut to 5 blocks, about what a blinded player sees); once it wears off the soldier takes it.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_ct_blind")
    public static void blindedNpcSeesFiveBlocks(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String id = "fx_ct_blind";
            HeroEntity hero = hero(t, id, "katana", def(id, MELEE, "striker", "", ""), 4, 10);
            FactionNpc soldier = t.npc(4, 6, NpcRole.SWORDSMAN);
            FactionNpc enemy = foe(t, 12, 8, 100);
            hero.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            soldier.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            // both stay where they are (their goals run, their feet do not), so only the range decides
            hero.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            t.onEachTick(() -> {
                long tick = h.getTick();
                if (tick < 90) {
                    t.assertTrue(soldier.getTarget() != enemy, "the blinded soldier took an enemy 8 blocks away");
                    t.assertTrue(hero.getTarget() != enemy, "the blinded hero took an enemy 8 blocks away");
                    if (tick == 30) {
                        t.assertTrue(soldier.getAttributeValue(Attributes.FOLLOW_RANGE) <= 5.001, "follow range "
                                + soldier.getAttributeValue(Attributes.FOLLOW_RANGE));
                    }
                } else if (soldier.getTarget() == enemy && hero.getTarget() == enemy) {
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
