package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBossBars;
import com.factionclash.hero.HeroBossEvents;
import com.factionclash.hero.HeroCombat;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroSenses;
import com.factionclash.hero.HeroStatus;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.Sense;
import com.factionclash.npc.FactionNpc;
import com.factionclash.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.player;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;
import static com.factionclash.gametest.HeroBossGameTests.blow;
import static com.factionclash.gametest.HeroBossGameTests.bossHero;
import static com.factionclash.gametest.HeroBossGameTests.near;
import static com.factionclash.gametest.HeroBossGameTests.now;
import static com.factionclash.gametest.HeroBossGameTests.shieldPlayer;
import static com.factionclash.gametest.HeroBossGameTests.soldier;
import static com.factionclash.gametest.HeroBossGameTests.swordPlayer;

/**
 * Heroes, task A6b: the boss controller, part two (HEROES_V2 6.8-6.13, 14.5; SLICE_PLAN 5 criteria 9, 12-14 and 6.2).
 * Reactions read any target (an NPC soldier's raised shield draws the answer a mock player's does, with the same seed
 * the same picks); the end of a fight with no yield (an escaping-kind hero breaks off at its threshold, from NPC
 * soldiers and from a mock player at the same health, and runs; a last-stand hero says its line once and dies fighting;
 * Natsuho runs afield, stands at home, runs once the heart is gone); the boss bar and the intro title for the players
 * near, with {@code fled} and {@code fallen}; the soft and hard enrage and the reset; rewards only at a death, to the
 * side that won it, picked up by its soldiers; the event weak points, caused alike by an NPC and a mock player; and the
 * operator's hooks. <b>Equal treatment</b> (6.15): wherever a player could cause a rule, an NPC soldier causes it too
 * with the same numbers. Each test has its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroEndGameTests {
    private HeroEndGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;
    private static final double EPS = 1e-3;

    /** A schemer: aggression 45 + honour 20 - caution 70 = -5, an escaping kind; loyalty 30 stands nowhere. */
    static final String SCHEMER = "{\"temperament\": \"schemer\", \"traits\": {\"aggression\": 45, \"honour\": 20, \"caution\": 70,"
            + " \"loyalty\": 30}}";
    /** A battle maniac: 95 + 70 - 5 = 160, a last stand. */
    static final String MANIAC = "{\"temperament\": \"battle_maniac\", \"traits\": {\"aggression\": 95, \"honour\": 70, \"caution\": 5}}";
    static final String LINES = ", \"lines\": {\"escape\": [\"I am off!\"], \"last_stand\": [\"Not one step back!\"],"
            + " \"intro\": [\"So it begins, {enemy}.\"], \"enrage\": [\"Enough!\"], \"fallen\": [\"Ugh...\"]}";

    /** A common striker with one never-picked move, this personality, these boss fields and more top-level fields. */
    static String endDef(String id, String personality, String boss, String more) {
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + HeroIdentity.nameFromId(id) + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"striker\"},"
                + " \"basic_attack\": " + MELEE + ", \"moves\": [" + IDLE_MOVE + "], \"boss\": {\"phases\": [{\"at\": 100}]" + boss + "},"
                + " \"personality\": " + personality + ", \"leader\": {\"eligible\": false}" + LINES + more + "}";
    }

    static String weak(String... points) {
        return ", \"weak_points\": [\"" + String.join("\", \"", points) + "\"]";
    }

    static int fleeAt(HeroEntity h) {
        return h.definition().v2().end().fleeAt();
    }

    static double share(HeroEntity h) {
        return h.getHealth() / h.getMaxHealth();
    }

    static void noArmour(HeroEntity h) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            h.setItemSlot(s, ItemStack.EMPTY);
        }
    }

    /** XP orbs (their total value) and crests on the ground within {@code r} of {@code at}. */
    static int orbs(GameTestHelper h, Vec3 at, double r) {
        int total = 0;
        for (ExperienceOrb o : h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(r))) {
            net.minecraft.nbt.CompoundTag tag = o.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            total += o.getValue() * Math.max(1, tag.getInt("Count"));
        }
        return total;
    }

    static List<ItemEntity> crests(GameTestHelper h, Vec3 at, double r) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(r), e -> e.getItem().is(ModItems.HERO_CREST.get()));
    }

    // ------------------------------------------------------------------ reactions read any target (6.8)

    /**
     * {@code reactionFiresOnNpcShield}: two heroes with the same {@code target_blocking} answer (50%, a 2 s cooldown) and the
     * same seed face an NPC soldier who raises his shield against an archer and a mock player who raises hers: both senses
     * read "blocking", and both heroes answer with the same move at the same tick (one roll per raise, the same draws); the
     * reaction is counted and logged. Nothing else is ever picked (every move weighs 0).
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_end_react")
    public static void reactionFiresOnNpcShield(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String answer = move("answer", "melee", "\"weight\": 0, \"cooldown\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            String boss = ", \"reactions\": [{\"when\": \"target_blocking\", \"move\": \"answer\", \"chance\": 50, \"cooldown\": 2}]";
            String a = "fx_end_react_npc", b = "fx_end_react_pl";
            HeroEntity vsNpc = bossHero(t, a, endDef(a, MANIAC, boss, "").replace("\"moves\": [" + IDLE_MOVE + "]",
                    "\"moves\": [" + IDLE_MOVE + ", " + answer + "]"), 4, 4);
            HeroEntity vsPlayer = bossHero(t, b, endDef(b, MANIAC, boss, "").replace("\"moves\": [" + IDLE_MOVE + "]",
                    "\"moves\": [" + IDLE_MOVE + ", " + answer + "]"), 4, 16);
            vsNpc.setNoAi(true);
            vsPlayer.setNoAi(true);
            vsNpc.setOnGround(true); // a still mob does not fall: on the ground, or it reads as airborne
            vsPlayer.setOnGround(true);
            FactionNpc archer = HeroMoveGameTests.archer(t, 20, 4);
            FactionNpc shieldNpc = HeroAbilityGameTests.foe(t, 6, 4, 200);
            shieldNpc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            ServerPlayer shieldPl = shieldPlayer(t, 6, 16);
            long[] first = {-1, -1};
            String[] picked = {null, null};
            h.runAtTickTime(3, t.guard(() -> {
                // both shields go up on the same tick: the soldier's against an archer, as soldiers raise them
                shieldNpc.setTarget(archer);
                shieldNpc.startUsingItem(InteractionHand.OFF_HAND);
                shieldPl.startUsingItem(InteractionHand.OFF_HAND);
                vsNpc.setTarget(shieldNpc);
                vsPlayer.setTarget(shieldPl);
                vsNpc.combat().selector().reseed(20261113L);
                vsPlayer.combat().selector().reseed(20261113L);
            }));
            t.onEachTick(() -> {
                if (h.getTick() < 4) {
                    return;
                }
                HeroEntity[] heroes = {vsNpc, vsPlayer};
                for (int i = 0; i < 2; i++) {
                    HeroCombat c = heroes[i].combat();
                    if (!c.running() && c.tryPick()) {
                        c.startPicked();
                    }
                    if (first[i] < 0 && c.running()) {
                        first[i] = h.getTick();
                        picked[i] = c.lastMove();
                    }
                }
                if (h.getTick() == 12) {
                    Sense.TargetState sn = new Sense.TargetState(), sp = new Sense.TargetState();
                    HeroSenses.target(vsNpc, shieldNpc, sn, false);
                    HeroSenses.target(vsPlayer, shieldPl, sp, false);
                    t.assertTrue(sn.blocking && sp.blocking, "a raised shield reads as blocking: soldier " + sn.blocking + ", player " + sp.blocking);
                }
                if (first[0] >= 0 && first[1] >= 0) {
                    t.assertTrue("answer".equals(picked[0]) && "answer".equals(picked[1]), "picked " + picked[0] + " and " + picked[1]);
                    t.assertTrue(first[0] == first[1], "the soldier's shield was answered at " + first[0] + ", the player's at " + first[1]);
                    t.assertTrue(vsNpc.combat().boss().reactions() == 1 && vsPlayer.combat().boss().reactions() == 1,
                            "reactions counted " + vsNpc.combat().boss().reactions() + " and " + vsPlayer.combat().boss().reactions());
                    t.assertTrue(first[0] >= 3 + 30, "an answer before the opening gap: tick " + first[0]);
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ escape (6.13, MOBILITY_AND_WORK 7.7)

    /**
     * {@code escapingHeroFleesAtThresholdFromNpcsAndPlayer}: two escaping-kind heroes (schemers) are struck down a little at
     * a time, one by NPC soldiers and one by a mock player, with the same blows: each breaks off on the blow that takes it
     * under its {@code flee_at}, never before, at the same health; its move stops, it drops its target, says its escape
     * line, the campaign counts it, the bar its ally sees shows {@code fled} and then goes, nothing drops where it was, and
     * it runs off on foot.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_end_escape")
    public static void escapingHeroFleesAtThresholdFromNpcsAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String a = "fx_end_esc_npc", b = "fx_end_esc_pl";
            // (the test area is walled in by barriers: the heroes start on the far side from where they will run)
            HeroEntity byNpc = bossHero(t, a, endDef(a, SCHEMER, "", ""), 18, 6);
            HeroEntity byPlayer = bossHero(t, b, endDef(b, SCHEMER, "", ""), 18, 18);
            noArmour(byNpc);
            noArmour(byPlayer);
            FactionNpc s1 = soldier(t, 20, 6), s2 = soldier(t, 20, 8);
            ServerPlayer p = swordPlayer(t, 20, 18);
            ServerPlayer ally = player(t, 12, 12, SIDE, 100); // sees both bars
            Vec3[] spot = new Vec3[2];
            double[] escapedAt = {-1, -1};
            h.runAtTickTime(3, t.guard(() -> {
                t.assertTrue(fleeAt(byNpc) > 0 && fleeAt(byNpc) == fleeAt(byPlayer), "flee_at " + fleeAt(byNpc));
                byNpc.setTarget(s1);
                byPlayer.setTarget(p);
                blow(byNpc, s1, 1);
                blow(byPlayer, p, 1);
            }));
            h.runAtTickTime(30, t.guard(() -> {
                t.assertTrue(HeroBossBars.viewers(byNpc).contains(ally) && HeroBossBars.viewers(byPlayer).contains(ally),
                        "the ally near does not see both bars");
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    x.setHealth((float) (x.getMaxHealth() * (fleeAt(x) + 3) / 100.0));
                }
            }));
            // the same blows on both, one every 11 ticks (past each attacker's hurt window), until each one breaks off
            for (int i = 0; i < 12; i++) {
                int k = i;
                h.runAtTickTime(32 + 11 * i, t.guard(() -> {
                    HeroEntity[] hs = {byNpc, byPlayer};
                    for (int j = 0; j < 2; j++) {
                        HeroEntity x = hs[j];
                        if (escapedAt[j] >= 0 || !x.isAlive()) {
                            continue;
                        }
                        double before = share(x);
                        LivingEntity by = j == 0 ? (k % 2 == 0 ? s1 : s2) : p;
                        blow(x, by, 1.5F);
                        boolean esc = x.stateFlag(HeroActions.STATE_ESCAPING);
                        t.assertTrue(esc == share(x) * 100 < fleeAt(x), x.heroId() + " escaping " + esc + " at " + share(x) * 100 + "%");
                        if (esc) {
                            t.assertTrue(before * 100 >= fleeAt(x), "it should have gone on the blow before");
                            escapedAt[j] = share(x);
                            spot[j] = x.position();
                            t.assertTrue(x.getTarget() == null && x.combat().boss().escape().escapes() == 1, "target dropped, one escape");
                            t.assertTrue("I am off!".equals(x.lastLine()), "escape line: " + x.lastLine());
                            HeroState rec = x.campaignRecord();
                            t.assertTrue(rec != null && rec.escapes == 1 && rec.lastEscapeAt > 0, "the campaign counts the escape");
                            t.assertTrue(HeroBossBars.text(x).equals(BossRules.endName(x.npcName(), true)), "the bar: " + HeroBossBars.text(x));
                            t.assertTrue(!x.combat().boss().fighting(), "the fight goes on for an escaper");
                        }
                    }
                }));
            }
            h.runAtTickTime(200, t.guard(() -> {
                t.assertTrue(escapedAt[0] >= 0 && escapedAt[1] >= 0, "an escaper did not escape: " + escapedAt[0] + " " + escapedAt[1]);
                near(t, escapedAt[0], escapedAt[1], EPS, "the same health at the escape from soldiers and from a player");
                t.assertTrue(HeroBossBars.viewers(byNpc).isEmpty() && HeroBossBars.viewers(byPlayer).isEmpty(), "the fled bar stays");
            }));
            h.runAtTickTime(300, t.guard(() -> {
                for (int j = 0; j < 2; j++) {
                    HeroEntity x = j == 0 ? byNpc : byPlayer;
                    t.assertTrue(x.isAlive(), x.heroId() + " died");
                    t.assertTrue(x.position().distanceTo(spot[j]) > 5, x.heroId() + " did not run: " + x.position().distanceTo(spot[j]));
                    t.assertTrue(orbs(h, spot[j], 6) == 0 && crests(h, spot[j], 6).isEmpty(), "an escape dropped rewards");
                    t.assertTrue(x.getTarget() == null || !x.combat().boss().escape().fleeing(), "a fleeing hero took a target");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code lastStandHeroNeverFleesAndDiesFighting}: two last-stand heroes (battle maniacs) brought under the health where
     * they would have fled, one by NPC soldiers and one by a mock player: each says its last-stand line once (once in the
     * fight, whatever more blows land), keeps its target and never escapes; the killing blow lands (nothing cancels it),
     * it dies with no kneel, the bar shows {@code fallen} and empties, its revival timer starts.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_end_last")
    public static void lastStandHeroNeverFleesAndDiesFighting(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String a = "fx_end_last_npc", b = "fx_end_last_pl";
            HeroEntity byNpc = bossHero(t, a, endDef(a, MANIAC, "", ""), 6, 6);
            HeroEntity byPlayer = bossHero(t, b, endDef(b, MANIAC, "", ""), 6, 18);
            byNpc.setNoAi(true);
            byPlayer.setNoAi(true);
            noArmour(byNpc);
            noArmour(byPlayer);
            FactionNpc s1 = soldier(t, 8, 6), s2 = soldier(t, 8, 8);
            ServerPlayer p = swordPlayer(t, 8, 18);
            ServerPlayer ally = player(t, 12, 12, SIDE, 100);
            h.runAtTickTime(3, t.guard(() -> {
                byNpc.setTarget(s1);
                byPlayer.setTarget(p);
                blow(byNpc, s1, 1);
                blow(byPlayer, p, 1);
            }));
            h.runAtTickTime(30, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.definition().v2().end().kind() == HeroDef.WhenLosing.LAST_STAND, "not a last-stand kind");
                    t.assertTrue(HeroBossBars.viewers(x).contains(ally), "no bar for the ally near");
                    x.setHealth((float) (x.getMaxHealth() * 0.06));
                }
                blow(byNpc, s1, 1);
                blow(byPlayer, p, 1);
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().escape().lastStands() == 1, x.heroId() + " said no last stand");
                    t.assertTrue("Not one step back!".equals(x.lastLine()), "last-stand line: " + x.lastLine());
                    HeroState rec = x.campaignRecord();
                    t.assertTrue(rec != null && rec.lastStands == 1, "the campaign counts it");
                }
            }));
            h.runAtTickTime(42, t.guard(() -> {
                blow(byNpc, s2, 1);
                blow(byPlayer, p, 1);
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().escape().lastStands() == 1, "a second last-stand line in one fight");
                    t.assertTrue(!x.stateFlag(HeroActions.STATE_ESCAPING) && x.getTarget() != null && x.isAlive(),
                            x.heroId() + " left the fight");
                }
            }));
            h.runAtTickTime(54, t.guard(() -> {
                blow(byNpc, s1, 40);
                blow(byPlayer, p, 40);
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.isDeadOrDying(), x.heroId() + " survived the killing blow at " + x.getHealth());
                    t.assertTrue(HeroBossBars.text(x).equals(BossRules.endName(x.npcName(), false)), "the bar: " + HeroBossBars.text(x));
                    t.assertTrue(HeroBossBars.progress(x) == 0F, "a fallen bar is empty");
                    t.assertTrue(HeroBossBars.viewers(x).contains(ally), "the ally does not see it fall");
                    HeroState rec = x.campaignRecord() != null ? x.campaignRecord()
                            : CampaignData.get(h.getLevel().getServer()).heroes.get(x.heroId());
                    t.assertTrue(rec != null && !rec.alive && rec.reviveAt > h.getLevel().getGameTime(), "no revival timer");
                    t.assertTrue(x.combat().boss().escape().escapes() == 0, "a last-stand hero escaped");
                }
            }));
            h.runAtTickTime(54 + BossRules.END_SHOW_TICKS + 3, t.guard(() -> {
                t.assertTrue(HeroBossBars.viewers(byNpc).isEmpty() && HeroBossBars.viewers(byPlayer).isEmpty(), "the fallen bar stays");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code homeStandHeroFleesAfieldNotAtHome}: Natsuho (the slice fixture: an escaping kind by her authored
     * {@code when_losing}, loyalty 70, so she stands at home) brought under her 8% runs when the fight is away from her
     * home base, makes her last stand inside it (and keeps fighting there), and runs from it once its heart is destroyed.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_end_home")
    public static void homeStandHeroFleesAfieldNotAtHome(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String text = HeroAbilityGameTests.slice("natsuho_komezaki");
            HeroEntity afield = bossHero(t, "fx_nat_afield", text.replace("\"natsuho_komezaki\"", "\"fx_nat_afield\""), 4, 4);
            HeroEntity home = bossHero(t, "fx_nat_home", text.replace("\"natsuho_komezaki\"", "\"fx_nat_home\""), 4, 12);
            HeroEntity heartless = bossHero(t, "fx_nat_heart", text.replace("\"natsuho_komezaki\"", "\"fx_nat_heart\""), 4, 20);
            List<HeroEntity> all = List.of(afield, home, heartless);
            for (HeroEntity x : all) {
                x.setNoAi(true);
                noArmour(x);
            }
            FactionNpc s1 = soldier(t, 6, 4), s2 = soldier(t, 6, 12), s3 = soldier(t, 6, 20);
            BaseState base = t.base;
            BlockPos min = base.point(BaseState.MIN), max = base.point(BaseState.MAX);
            h.runAtTickTime(3, t.guard(() -> {
                HeroDef.EndOfFight e = afield.definition().v2().end();
                t.assertTrue(e.kind() == HeroDef.WhenLosing.ESCAPE && e.standsFor().contains(HeroDef.StandsFor.HOME) && e.fleeAt() == 8,
                        "Natsuho's end of a fight: " + e);
                afield.setTarget(s1);
                home.setTarget(s2);
                heartless.setTarget(s3);
                blow(afield, s1, 1);
                blow(home, s2, 1);
                blow(heartless, s3, 1);
                for (HeroEntity x : all) {
                    x.setHealth((float) (x.getMaxHealth() * 0.09));
                }
            }));
            // afield: the base's walls are moved 300 blocks off for this blow, so the fight is away from home
            h.runAtTickTime(15, t.guard(() -> {
                base.points.put(BaseState.MIN, min.offset(300, 0, 0));
                base.points.put(BaseState.MAX, max.offset(300, 0, 0));
                try {
                    blow(afield, s1, 3);
                } finally {
                    base.points.put(BaseState.MIN, min);
                    base.points.put(BaseState.MAX, max);
                }
                t.assertTrue(share(afield) * 100 < 8, "not under 8%: " + share(afield) * 100);
                t.assertTrue(afield.stateFlag(HeroActions.STATE_ESCAPING), "Natsuho did not run from a fight afield");
                // at home, with the heart standing
                blow(home, s2, 3);
                t.assertTrue(share(home) * 100 < 8 && home.isAlive(), "home: " + share(home) * 100);
                t.assertTrue(!home.stateFlag(HeroActions.STATE_ESCAPING), "Natsuho ran from her own harbour");
                t.assertTrue(home.combat().boss().escape().lastStands() == 1, "no last stand at home");
            }));
            h.runAtTickTime(27, t.guard(() -> {
                blow(home, s2, 1);
                t.assertTrue(!home.stateFlag(HeroActions.STATE_ESCAPING) && home.getTarget() == s2, "she left her home fight");
                // the heart falls: the stand ends with it
                base.destroyed = true;
                blow(heartless, s3, 3);
                t.assertTrue(heartless.stateFlag(HeroActions.STATE_ESCAPING), "Natsuho did not run once the heart was gone");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the bar and the title (6.13)

    /**
     * {@code bossBarShownInAnyFightNearAPlayer}: a hero fighting an NPC soldier shows its bar (name, title, pip) and its
     * title to a mock player 10 blocks off and to nobody 40 blocks off; a hero fighting with no player near shows none, and
     * the same forced move deals both soldiers the same; a player sees at most 3 bars, the nearest first. A hero whose day
     * has no intro yet opens its first fight against a soldier with it (super armour, half damage, its line) and not its
     * second fight that day; a monster gets none.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_end_bar")
    public static void bossBarShownInAnyFightNearAPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String hit = move("hit", "melee", "\"weight\": 0, \"cooldown\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]");
            List<HeroEntity> heroes = new ArrayList<>();
            int[][] at = {{4, 4}, {4, 8}, {4, 12}, {4, 16}};
            List<FactionNpc> foes = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String id = "fx_end_bar_" + i;
                HeroEntity x = bossHero(t, id, endDef(id, MANIAC, "", ", \"title\": \"the Test\"").replace("\"moves\": [" + IDLE_MOVE + "]",
                        "\"moves\": [" + IDLE_MOVE + ", " + hit + "]"), at[i][0], at[i][1]);
                x.setNoAi(true);
                heroes.add(x);
                foes.add(soldier(t, at[i][0] + 2, at[i][1]));
            }
            ServerPlayer near = player(t, 10, 4, SIDE, 100);
            ServerPlayer distant = player(t, 10, 4, SIDE, 100);
            String introId = "fx_end_bar_intro";
            HeroEntity intro = bossHero(t, introId, endDef(introId, MANIAC, "", ""), 18, 4);
            intro.setNoAi(true);
            FactionNpc introFoe = soldier(t, 20, 4);
            h.runAtTickTime(2, t.guard(() -> {
                Vec3 d = near.position().add(0, 0, 40);
                distant.moveTo(d.x, d.y, d.z);
                for (int i = 0; i < 3; i++) {
                    heroes.get(i).setTarget(foes.get(i));
                    blow(heroes.get(i), foes.get(i), 1);
                }
            }));
            h.runAtTickTime(25, t.guard(() -> {
                HeroEntity x = heroes.get(0);
                t.assertTrue(HeroBossBars.viewers(x).contains(near), "the player 10 blocks off sees no bar");
                t.assertTrue(!HeroBossBars.viewers(x).contains(distant), "a player 40 blocks off sees the bar");
                t.assertTrue(HeroBossBars.text(x).equals(BossRules.barName(x.npcName(), "the Test", 1, 1, false)), "bar text " + HeroBossBars.text(x));
                t.assertTrue(HeroBossBars.titled(x, near) && !HeroBossBars.titled(x, distant), "the title");
                // the move with a player near; the same move later with nobody near (the bar decides nothing)
                force(t, x, foes.get(0), "hit");
                // the fourth hero joins: the player sees the 3 nearest (x 4..16 at z 4..16 from the player at 10, 4)
                heroes.get(3).setTarget(foes.get(3));
                blow(heroes.get(3), foes.get(3), 1);
            }));
            h.runAtTickTime(60, t.guard(() -> {
                t.assertTrue(foes.get(0).getHealth() < 100, "the move did not land");
                int seen = 0;
                for (HeroEntity x : heroes) {
                    seen += HeroBossBars.viewers(x).contains(near) ? 1 : 0;
                }
                t.assertTrue(seen == 3, "the player sees " + seen + " bars");
                t.assertTrue(!HeroBossBars.viewers(heroes.get(3)).contains(near), "the farthest of four is shown");
                // the intro: its first fight against people this day
                intro.combat().boss().introPlayedToday(false);
                intro.setTarget(introFoe);
            }));
            h.runAtTickTime(63, t.guard(() -> {
                t.assertTrue(intro.combat().boss().introducing() && intro.combat().boss().intros() == 1, "no intro");
                t.assertTrue(intro.stateFlag(HeroActions.STATE_SUPER_ARMOUR), "no super armour in the intro");
                t.assertTrue(intro.lastLine().startsWith("So it begins"), "intro line: " + intro.lastLine());
                float lost = blow(intro, introFoe, 4);
                intro.setHealth(intro.getMaxHealth());
                HeroEntity x = heroes.get(1);
                float full = blow(x, foes.get(1), 4);
                near(t, full * 0.5, lost, EPS, "the intro takes half");
            }));
            h.runAtTickTime(100, t.guard(() -> {
                t.assertTrue(!intro.combat().boss().introducing() && !intro.stateFlag(HeroActions.STATE_SUPER_ARMOUR), "the intro lasts");
                introFoe.discard();
                intro.setTarget(null);
                // nobody near any more: the bars go, the fights go on
                Vec3 away = near.position().add(0, 0, 40);
                near.moveTo(away.x, away.y, away.z);
            }));
            h.runAtTickTime(125, t.guard(() -> {
                HeroEntity y = heroes.get(1);
                t.assertTrue(HeroBossBars.viewers(y).isEmpty() && y.combat().boss().fighting(), "a bar with nobody near");
                force(t, y, foes.get(1), "hit");
            }));
            h.runAtTickTime(160, t.guard(() -> near(t, foes.get(0).getHealth(), foes.get(1).getHealth(), EPS,
                    "the same move dealt the same with and without a player near")));
            h.runAtTickTime(330, t.guard(() -> {
                t.assertTrue(!intro.combat().boss().fighting(), "the fight did not end 10 s after its enemy");
                FactionNpc again = soldier(t, 20, 6);
                intro.setTarget(again);
            }));
            h.runAtTickTime(340, t.guard(() -> {
                t.assertTrue(intro.combat().boss().fighting() && intro.combat().boss().intros() == 1, "a second intro the same day");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ enrage and reset (6.10)

    /**
     * {@code enrageAndResetForNpcAndPlayer}: a hero fighting an NPC soldier and one fighting a mock player each enrage once
     * the fight has lasted {@code boss.enrage.after} (backdated by the operator's hook): +15% damage dealt, the poise maximum
     * x1.25, the enrage line, the bar red, {@code on_enrage}; the same numbers for both. The fight ends 10 s after the enemy
     * is gone and the enrage with it. A hard trigger ({@code heart_attacked}, a soldier starting on the heart) enrages at once.
     */
    @GameTest(template = "platform", timeoutTicks = 600, batch = "hero_end_enrage")
    public static void enrageAndResetForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 600);
        try {
            String boss = ", \"poise\": 100, \"enrage\": {\"after\": 120, \"triggers\": [\"heart_attacked\"]}";
            String passive = ", \"passives\": [{\"name\": \"Fury\", \"trigger\": \"on_enrage\", \"form\": \"self\", \"cooldown\": 1,"
                    + " \"effects\": [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10, \"seconds\": 30}]}]";
            String a = "fx_end_rage_npc", b = "fx_end_rage_pl";
            HeroEntity vsNpc = bossHero(t, a, endDef(a, MANIAC, boss, passive), 4, 4);
            HeroEntity vsPlayer = bossHero(t, b, endDef(b, MANIAC, boss, passive), 4, 16);
            vsNpc.setNoAi(true);
            vsPlayer.setNoAi(true);
            FactionNpc s = soldier(t, 6, 4);
            ServerPlayer p = swordPlayer(t, 6, 16);
            ServerPlayer ally = player(t, 12, 10, SIDE, 100);
            double[] poiseMax = new double[2];
            h.runAtTickTime(3, t.guard(() -> {
                vsNpc.setTarget(s);
                vsPlayer.setTarget(p);
                blow(vsNpc, s, 1);
                blow(vsPlayer, p, 1);
                poiseMax[0] = vsNpc.combat().poise().max();
                poiseMax[1] = vsPlayer.combat().poise().max();
            }));
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(!vsNpc.combat().boss().enraged(), "enraged at once");
                vsNpc.combat().boss().backdateFight(120 * 20);
                vsPlayer.combat().boss().backdateFight(120 * 20);
            }));
            h.runAtTickTime(25, t.guard(() -> {
                HeroEntity[] hs = {vsNpc, vsPlayer};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = hs[i];
                    t.assertTrue(x.combat().boss().enraged() && x.stateFlag(HeroActions.STATE_ENRAGED), x.heroId() + " not enraged");
                    near(t, 15, HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT), EPS, "the enrage's damage");
                    near(t, poiseMax[i] * BossRules.ENRAGE_POISE_FACTOR, x.combat().poise().max(), EPS, "the poise maximum");
                    t.assertTrue("Enough!".equals(x.lastLine()), "the enrage line: " + x.lastLine());
                    t.assertTrue(x.combat().passiveFirings() >= 1, "on_enrage did not fire");
                    t.assertTrue(HeroBossBars.barColour(x) == net.minecraft.world.BossEvent.BossBarColor.RED, "the bar is not red");
                    t.assertTrue(HeroBossBars.viewers(x).contains(ally), "the ally sees no bar");
                }
                // the enemies go: the fights end 10 s later, and the enrage with them
                s.discard();
                p.discard();
                vsNpc.setTarget(null);
                vsPlayer.setTarget(null);
            }));
            h.runAtTickTime(25 + BossRules.FIGHT_END_TICKS + 20, t.guard(() -> {
                HeroEntity[] hs = {vsNpc, vsPlayer};
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = hs[i];
                    t.assertTrue(!x.combat().boss().fighting() && !x.combat().boss().enraged() && !x.stateFlag(HeroActions.STATE_ENRAGED),
                            x.heroId() + " still enraged after its fight");
                    near(t, 0, HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT), EPS, "the enrage's damage went");
                    near(t, poiseMax[i], x.combat().poise().max(), EPS, "the poise maximum went back");
                }
                // a hard trigger: a soldier starts breaking the base's heart while the hero fights
                BlockPos heart = h.absolutePos(new BlockPos(12, 2, 12));
                t.base.points.put(BaseState.HEART, heart);
                FactionNpc raider = soldier(t, 8, 4);
                vsNpc.setTarget(raider);
                blow(vsNpc, raider, 1);
            }));
            h.runAtTickTime(25 + BossRules.FIGHT_END_TICKS + 30, t.guard(() -> {
                t.assertTrue(vsNpc.combat().boss().fighting() && !vsNpc.combat().boss().enraged(), "the new fight");
                FactionNpc raider = (FactionNpc) vsNpc.getTarget();
                HeroBossEvents.heartAttacked(h.getLevel(), t.base.point(BaseState.HEART), raider);
            }));
            h.runAtTickTime(25 + BossRules.FIGHT_END_TICKS + 32, t.guard(() -> {
                t.assertTrue(vsNpc.combat().boss().enraged(), "heart_attacked did not enrage at once");
                t.assertTrue(!vsPlayer.combat().boss().enraged(), "a hero out of a fight enraged");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ rewards (6.13)

    /**
     * {@code rewardsDropOnlyOnDeath}: a hero killed by an NPC soldier drops its XP orbs (7 x level), its crest and its
     * non-kit gear where it died, and a soldier of the winning side standing there picks the orbs up for his faction; a
     * hero killed by a mock player drops the same for the player; a hero that escapes drops nothing (the operator's
     * {@code escape} hook).
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_end_rewards")
    public static void rewardsDropOnlyOnDeath(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String a = "fx_end_rew_npc", b = "fx_end_rew_pl", c = "fx_end_rew_esc";
            HeroEntity byNpc = bossHero(t, a, endDef(a, MANIAC, "", ""), 4, 4);
            HeroEntity byPlayer = bossHero(t, b, endDef(b, MANIAC, "", ""), 4, 16);
            HeroEntity escaper = bossHero(t, c, endDef(c, SCHEMER, "", ""), 18, 10);
            for (HeroEntity x : List.of(byNpc, byPlayer, escaper)) {
                x.setNoAi(true);
                x.inventory().setItem(0, new ItemStack(Items.EMERALD, 3)); // its own gear, not its kit
            }
            FactionNpc s = soldier(t, 5, 4);
            ServerPlayer p = swordPlayer(t, 6, 16);
            Vec3[] spot = new Vec3[3];
            long[] bank = new long[1];
            h.runAtTickTime(3, t.guard(() -> {
                bank[0] = CampaignData.get(h.getLevel().getServer()).faction(FOE).xpBank;
                byNpc.setTarget(s);
                byPlayer.setTarget(p);
                spot[0] = byNpc.position();
                spot[1] = byPlayer.position();
                spot[2] = escaper.position();
                byNpc.setHealth(1);
                byPlayer.setHealth(1);
                blow(byNpc, s, 5);
                blow(byPlayer, p, 5);
                t.assertTrue(byNpc.isDeadOrDying() && byPlayer.isDeadOrDying(), "the killing blows did not kill");
                int xp = HeroRules.rewardXp(byPlayer.npcLevel());
                t.assertTrue(orbs(h, spot[1], 3) == xp, "the player's kill dropped " + orbs(h, spot[1], 3) + " XP, not " + xp);
                List<ItemEntity> cr = crests(h, spot[1], 3);
                t.assertTrue(cr.size() == 1 && cr.get(0).getItem().get(DataComponents.CUSTOM_NAME).getString().contains(byPlayer.npcName()),
                        "the crest for the player: " + cr.size());
                t.assertTrue(crests(h, spot[0], 3).size() == 1, "no crest for the soldier's side");
                t.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot[0], spot[0]).inflate(3),
                        e -> e.getItem().is(Items.EMERALD)).isEmpty(), "its gear did not drop");
                t.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot[0], spot[0]).inflate(3),
                        e -> com.factionclash.hero.HeroKit.isKit(e.getItem())).isEmpty(), "its kit dropped");
                // the escape
                t.assertTrue(escaper.combat().boss().escape().start(com.factionclash.logic.hero.EscapeRules.Trigger.LOW_HEALTH, true),
                        "the escape hook did not start an escape");
            }));
            h.runAtTickTime(40, t.guard(() -> {
                long gained = CampaignData.get(h.getLevel().getServer()).faction(FOE).xpBank - bank[0];
                t.assertTrue(gained == HeroRules.rewardXp(byNpc.npcLevel()), "the soldier's faction picked up " + gained + " XP");
                t.assertTrue(orbs(h, spot[0], 3) == 0, "orbs left by the soldier");
                t.assertTrue(escaper.isAlive() && orbs(h, spot[2], 6) == 0 && crests(h, spot[2], 6).isEmpty()
                        && h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot[2], spot[2]).inflate(6),
                        e -> e.getItem().is(Items.EMERALD)).isEmpty(), "an escape dropped something");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the event weak points (6.9)

    /** A mock player that can swing a full-strength blow and stand in the air (a falling critical hit). */
    static ServerPlayer critPlayer(GameTestBases.Handle t, int x, int z) {
        ServerPlayer p = player(t, x, z, FOE, 100);
        try {
            java.lang.reflect.Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 100);
        } catch (ReflectiveOperationException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("cannot charge the mock player's blow: " + e);
        }
        return p;
    }

    /**
     * {@code eventWeakPointsAnswerNpcAndPlayerAlike}: two heroes of fire with the weak points snowball, light, bell and
     * crit_above. On one an NPC soldier causes each, on the other a mock player: a thrown snowball douses (its burning
     * state ends, -10% damage dealt; a second within 20 s does nothing), a torch or glowstone placed within 4 blocks by an
     * enemy weakens it 10%, a bell rung within 8 blocks stuns it 1 s, and a falling critical hit from above its eyes
     * staggers it at once. The same counts and numbers for both.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "hero_end_weak")
    public static void eventWeakPointsAnswerNpcAndPlayerAlike(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 300);
        try {
            String boss = weak("snowball", "light", "bell", "crit_above") + ", \"poise\": 100";
            String more = ", \"element\": \"fire\", \"states\": [{\"name\": \"rage\", \"stats\": [{\"stat\": \"attack\", \"percent\": 20}]}]";
            String a = "fx_end_weak_npc", b = "fx_end_weak_pl";
            HeroEntity byNpc = bossHero(t, a, endDef(a, MANIAC, boss, more), 4, 4);
            HeroEntity byPlayer = bossHero(t, b, endDef(b, MANIAC, boss, more), 4, 19);
            byNpc.setNoAi(true);
            byPlayer.setNoAi(true);
            FactionNpc s = soldier(t, 8, 4);
            s.inventory().setItem(0, new ItemStack(Items.TORCH, 4));
            ServerPlayer p = critPlayer(t, 8, 19);
            BlockPos bellA = new BlockPos(9, 2, 6), bellB = new BlockPos(9, 2, 21);
            h.runAtTickTime(3, t.guard(() -> {
                byNpc.combat().enterState("rage", 60);
                byPlayer.combat().enterState("rage", 60);
                for (int i = 0; i < 2; i++) {
                    LivingEntity owner = i == 0 ? s : p;
                    HeroEntity x = i == 0 ? byNpc : byPlayer;
                    Snowball sb = new Snowball(h.getLevel(), owner);
                    sb.setPos(x.getX() + 2.5, x.getEyeY() - 0.4, x.getZ());
                    sb.shoot(-1, 0, 0, 1.2F, 0F);
                    h.getLevel().addFreshEntity(sb);
                }
            }));
            h.runAtTickTime(20, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().douses() == 1, x.heroId() + " not doused: " + x.combat().boss().douses());
                    t.assertTrue(x.combat().stateName() == null, "its burning state did not end");
                    near(t, -10, HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT), EPS, "the douse");
                    t.assertTrue(x.combat().boss().douse(BossRules.Douse.SNOWBALL) == false, "a second douse inside 20 s");
                }
                // light: the soldier sets a torch through the NPCs' own placing, the player places glowstone
                t.assertTrue(com.factionclash.work.CaveWork.placeTorch(s, byNpc.blockPosition().east(2)), "the soldier placed no torch");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOWSTONE));
                BlockPos below = byPlayer.blockPosition().east(2).below();
                p.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
                t.assertTrue(h.getLevel().getBlockState(below.above()).is(Blocks.GLOWSTONE), "the player placed no glowstone");
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().lightWeakenings() == 1, x.heroId() + " not weakened by light");
                    near(t, -20, HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT), EPS, "the douse and the light");
                }
                // the bell: rung by the soldier and by the player
                h.setBlock(bellA, Blocks.BELL.defaultBlockState());
                h.setBlock(bellB, Blocks.BELL.defaultBlockState());
                ((BellBlock) Blocks.BELL).attemptToRing(s, h.getLevel(), h.absolutePos(bellA), Direction.NORTH);
                ((BellBlock) Blocks.BELL).attemptToRing(p, h.getLevel(), h.absolutePos(bellB), Direction.NORTH);
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().bellStuns() == 1 && HeroStatus.stunned(x), x.heroId() + " not stunned by the bell");
                }
            }));
            // a falling critical hit from above the eyes, once the stun is over
            h.runAtTickTime(60, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = i == 0 ? byNpc : byPlayer;
                    LivingEntity by = i == 0 ? s : p;
                    t.assertTrue(!HeroStatus.stunned(x) && !x.combat().staggered(), "still stunned");
                    by.moveTo(x.getX() + 0.8, x.getEyeY() + 0.3, x.getZ(), 90F, 60F);
                    by.setOnGround(false);
                    by.fallDistance = 1.5F;
                    if (by instanceof ServerPlayer sp) {
                        sp.attack(x);
                    } else {
                        ((FactionNpc) by).doHurtTarget(x);
                    }
                }
            }));
            h.runAtTickTime(62, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().staggered(), x.heroId() + " not staggered by a critical hit from above");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code waterStartlesAWaterHatingHero}: two heroes with the {@code water} weak point (not of fire): a water bottle thrown
     * by an NPC soldier breaks beside one and a mock player's beside the other: each flinches (held 1 s, its wind-up cut)
     * and deals 10% less; stepping into water later inside the 20 s does nothing more.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_end_water")
    public static void waterStartlesAWaterHatingHero(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String cleave = move("cleave", "melee", "\"class\": \"heavy\", \"weight\": 0, \"cooldown\": 20,"
                    + " \"frames\": {\"windup\": 1.0, \"active\": 0.2, \"recovery\": 0.8}, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]");
            String a = "fx_end_wet_npc", b = "fx_end_wet_pl";
            HeroEntity byNpc = bossHero(t, a, endDef(a, MANIAC, weak("water"), "").replace("\"moves\": [" + IDLE_MOVE + "]",
                    "\"moves\": [" + IDLE_MOVE + ", " + cleave + "]"), 4, 4);
            HeroEntity byPlayer = bossHero(t, b, endDef(b, MANIAC, weak("water"), "").replace("\"moves\": [" + IDLE_MOVE + "]",
                    "\"moves\": [" + IDLE_MOVE + ", " + cleave + "]"), 4, 19);
            byNpc.setNoAi(true);
            byPlayer.setNoAi(true);
            FactionNpc s = soldier(t, 7, 4);
            ServerPlayer p = player(t, 7, 19, FOE, 100);
            h.runAtTickTime(3, t.guard(() -> {
                force(t, byNpc, s, "cleave");
                force(t, byPlayer, p, "cleave");
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = i == 0 ? byNpc : byPlayer;
                    ThrownPotion pot = new ThrownPotion(h.getLevel(), i == 0 ? s : p);
                    pot.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
                    pot.setPos(x.getX(), x.getY() + 2.6, x.getZ());
                    pot.setDeltaMovement(0, -0.6, 0);
                    h.getLevel().addFreshEntity(pot);
                }
            }));
            h.runAtTickTime(12, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().douses() == 1, x.heroId() + " not doused: " + x.combat().boss().douses());
                    t.assertTrue(!x.combat().running(), "its wind-up was not cut");
                    t.assertTrue(x.combat().boss().held(), "it does not flinch");
                    near(t, -10, HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT), EPS, "the douse");
                    t.assertTrue(x.combat().lastRelease() < 0, "the cleave landed");
                }
            }));
            h.runAtTickTime(40, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(!x.combat().boss().held(), "the flinch lasts");
                    h.setBlock(h.relativePos(x.blockPosition()), Blocks.WATER.defaultBlockState());
                }
            }));
            h.runAtTickTime(50, t.guard(() -> {
                for (HeroEntity x : List.of(byNpc, byPlayer)) {
                    t.assertTrue(x.combat().boss().douses() == 1, "a second douse inside 20 s");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the operator's hooks (SLICE_PLAN 6.5)

    /**
     * {@code operatorHooksDriveABossFight}: {@code /fc admin hero health} sets the health share, {@code poise ... full}
     * staggers it, {@code target-state ... blocking} makes a target that is not blocking read as blocking for its
     * reactions (and {@code clear} undoes it), and {@code escape} starts an escape on demand.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_end_ops")
    public static void operatorHooksDriveABossFight(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String id = "fx_end_ops";
            HeroEntity hero = bossHero(t, id, endDef(id, MANIAC, ", \"poise\": 100", ""), 6, 6);
            hero.setNoAi(true);
            FactionNpc s = soldier(t, 8, 6);
            MinecraftServer server = h.getLevel().getServer();
            h.runAtTickTime(3, t.guard(() -> {
                hero.setTarget(s);
                blow(hero, s, 1);
                run(server, "fc admin hero health " + id + " 40");
                near(t, 0.40, share(hero), 1e-4, "health");
                run(server, "fc admin hero poise " + id + " full");
                t.assertTrue(hero.combat().staggered(), "poise full did not stagger");
                Sense sense = new Sense();
                HeroSenses.fill(hero, s, sense, 0, 0);
                t.assertTrue(!sense.target.blocking, "the soldier is not blocking");
                run(server, "fc admin hero target-state " + id + " blocking");
                HeroSenses.fill(hero, s, sense, 0, 0);
                t.assertTrue(sense.target.blocking, "target-state blocking");
                run(server, "fc admin hero target-state " + id + " clear");
                HeroSenses.fill(hero, s, sense, 0, 0);
                t.assertTrue(!sense.target.blocking, "target-state clear");
                run(server, "fc admin hero escape " + id);
                t.assertTrue(hero.stateFlag(HeroActions.STATE_ESCAPING) && hero.combat().boss().escape().escapes() == 1,
                        "the escape hook (a last-stand hero, at 40%)");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
