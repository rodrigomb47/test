package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBoss;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.entity.HeroDecoy;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.hero.entity.HeroZone;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.husk;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroAbilityGameTests.noRegen;
import static com.factionclash.gametest.HeroAbilityGameTests.player;
import static com.factionclash.gametest.HeroAbilityGameTests.slice;
import static com.factionclash.gametest.HeroMoveGameTests.archer;
import static com.factionclash.gametest.HeroMoveGameTests.blockingPlayer;
import static com.factionclash.gametest.HeroMoveGameTests.def;
import static com.factionclash.gametest.HeroMoveGameTests.hero;
import static com.factionclash.gametest.HeroMoveGameTests.shieldBearer;
import static com.factionclash.gametest.HeroMoveGameTests.visual;

/**
 * The review of A3-A6 and S2 (HEROES_V2 15.2 "Review of A3-A6 and S2"): one GameTest for each fault the review found
 * and fixed, and for the rules no test held yet. Each runs in a batch of its own; every rule a player can meet is
 * checked with an NPC soldier and a mock player alike (6.15).
 * <ul>
 * <li>{@code selfMovesFollowUp}: a move that reaches no enemy (a state, a vanish) starts its followups (the slice kits:
 * Midnight Frenzy then Pounce, Shadow Step then Backstab).</li>
 * <li>{@code heroNeverHealsFromFood}: a hero's food bar never heals it (6.2: no regeneration in a fight; 6.10: its reset
 * is the healing); a soldier beside it still heals from its own.</li>
 * <li>{@code staggerIsNeverChained}: during a stagger that did not come from the bar, blows from a soldier and a player
 * add no poise, so no second stagger extends the kneel (6.9).</li>
 * <li>{@code barrierFocusHoldsThenStaggers}: a barrier's living focus holds its last hit and is never the decoy the hero's
 * own {@code spawn_add} pops to keep its cap; popping the focus breaks the barrier and staggers the hero (6.6), whoever
 * strikes.</li>
 * <li>{@code zonesIgnoreShields}: a zone's burst goes through a raised shield, a soldier's and a player's alike (SLICE_PLAN
 * 3.4); a blockable blow from the front does not.</li>
 * <li>{@code allyEffectsReachTheirOwnRadius}: an area move's heal for its allies reaches the effect's own radius beyond the
 * area (Hinae's rite, Ilsabet's vow), a soldier and a player alike; a sweep that passes no enemy still gives its allies
 * what it gives them (Hinae's dance).</li>
 * <li>{@code creativePlayerIsNeverAnEnemy}: a creative player's blow engages nothing (6.1, 6.14 rule 6); a survival
 * player's blow starts the fight.</li>
 * <li>{@code heroMovesOnAHeroAreCapped}: a hero's move on another hero takes at most 12% of its health, the excess to its
 * poise (6.2: the cap holds for every attacker, heroes included), and the bar keeps that opening blow's poise when the
 * target's fight begins (6.9).</li>
 * <li>{@code punishWindowAndWeakPointsAlikeForNpcAndPlayer}: a blow in the hero's recovery, from behind, with an iron
 * weapon lands x1.75 with the {@code recovery}, {@code back} and {@code iron} weak points, the same for a soldier and a
 * player (6.9).</li>
 * <li>{@code enragedHeroWaitsShorterGaps}: an enraged hero's gap between moves is phase 1's x0.7 (6.3, 6.10).</li>
 * <li>{@code twoHeroesFightEachOther}: two slice heroes of hostile factions pick moves against each other, every blow of
 * either is capped at 12% of the other's health, and a save in the middle of the fight loads.</li>
 * <li>{@code saveAndDeathMidMove}: a save in a wind-up loads with no move running; a death in a wind-up clears its decal
 * and nothing lands after it.</li>
 * <li>{@code sixteenHeroesFightWithinBudget}: sixteen heroes fight 8 against 8; the server time per hero tick is logged
 * against section 11's budget, and zones and projectiles stay within their caps.</li>
 * </ul>
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroReviewGameTests {
    private HeroReviewGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;
    private static final String DMG4 = "\"effects\": [{\"type\": \"damage\", \"amount\": 4}]";

    // ------------------------------------------------------------------ followups of moves that reach no enemy

    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_rv_follow")
    public static void selfMovesFollowUp(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 160);
        try {
            HeroEntity cat = HeroAbilityGameTests.hero(t, "natsuho_komezaki", "fists", slice("natsuho_komezaki"), 4, 4);
            HeroEntity ninja = HeroAbilityGameTests.hero(t, "tobimaru_usugumo", "katana", slice("tobimaru_usugumo"), 4, 18);
            Husk far = husk(t, 10, 4, 300); // 6 blocks: inside Pounce's reach of 8, outside close range
            Husk near = husk(t, 6, 18, 300); // 2 blocks: inside Backstab's reach
            force(t, cat, far, "midnight_frenzy");
            force(t, ninja, near, "shadow_step");
            // each followup must start on the very tick its self move's active part ends (it replaces that recovery, 6.3);
            // a pick of the hero's own later (Natsuho picks Pounce by herself too) does not count
            long[] due = {cat.combat().lastMoveStart() + cat.combat().lastPlan().activeEnd(),
                    ninja.combat().lastMoveStart() + ninja.combat().lastPlan().activeEnd()};
            boolean[] seen = new boolean[2];
            t.onEachTick(() -> {
                seen[0] |= "pounce".equals(cat.combat().lastMove()) && cat.combat().lastMoveStart() == due[0];
                seen[1] |= "backstab".equals(ninja.combat().lastMove()) && ninja.combat().lastMoveStart() == due[1];
                if (seen[0] && seen[1]) {
                    t.assertTrue("frenzy".equals(cat.combat().stateName()), "Midnight Frenzy's state is not on");
                    t.succeed();
                }
            });
            h.runAtTickTime(140, t.guard(() -> t.fail("followups after a self move: Pounce " + seen[0] + ", Backstab "
                    + seen[1] + " (last moves " + cat.combat().lastMove() + " at " + cat.combat().lastMoveStart() + ", "
                    + ninja.combat().lastMove() + " at " + ninja.combat().lastMoveStart() + "; due at " + due[0] + ", " + due[1] + ")")));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ no natural regeneration (6.2, 6.10)

    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_rv_regen")
    public static void heroNeverHealsFromFood(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 200);
        try {
            String a = "fx_rv_regen", b = "fx_rv_regen2";
            HeroEntity fighting = HeroAbilityGameTests.hero(t, a, "katana", HeroAbilityGameTests.def(a, MELEE, "striker", "", ""), 4, 4);
            HeroEntity resting = HeroAbilityGameTests.hero(t, b, "katana", HeroAbilityGameTests.def(b, MELEE, "striker", "", ""), 4, 18);
            resting.setNoAi(true);
            FactionNpc soldier = t.track(h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(18, 2, 18)));
            soldier.faction(SIDE);
            soldier.role(NpcRole.SWORDSMAN);
            soldier.setNoAi(true);
            Husk husk = husk(t, 8, 4, 400); // the fighting hero's target: it fights (6.1) and is never hurt by it
            fighting.setTarget(husk);
            float[] start = new float[3];
            LivingEntity[] who = {fighting, resting, soldier};
            for (int i = 0; i < 3; i++) {
                FactionNpc n = (FactionNpc) who[i];
                n.hunger().set(20, 5F, 0F); // a full, saturated food bar: a player or a soldier heals from it
                n.setHealth(n.getMaxHealth() * 0.5F);
            }
            // a hurt with no attacker: the resting hero's reset heals only 30 s after it (6.10), so before then nothing may
            resting.hurt(h.getLevel().damageSources().generic(), 1F);
            for (int i = 0; i < 3; i++) {
                start[i] = who[i].getHealth();
            }
            h.runAtTickTime(10, t.guard(() -> t.assertTrue(fighting.combat().boss().fighting(), "the hero is not fighting its target")));
            h.runAtTickTime(160, t.guard(() -> {
                t.assertTrue(soldier.getHealth() > start[2], "the soldier did not heal from its food (the test sees nothing)");
                t.assertTrue(fighting.getHealth() <= start[0], "a fighting hero healed from its food: " + start[0] + " -> "
                        + fighting.getHealth());
                t.assertTrue(resting.getHealth() <= start[1], "a hero healed from its food before its reset's 30 s: "
                        + start[1] + " -> " + resting.getHealth());
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ no stun-lock (6.9)

    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_rv_chain")
    public static void staggerIsNeverChained(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_rv_chain";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", "", ""), 6, 6);
            hero.setNoAi(true);
            noRegen(hero);
            FactionNpc soldier = foe(t, 8, 6, 100);
            ServerPlayer pl = player(t, 8, 9, FOE, 100);
            long[] kneelEnds = new long[1];
            h.runAtTickTime(2, t.guard(() -> {
                BossRules.Poise p = hero.combat().poise();
                t.assertTrue(!hero.combat().poiseDamage(p.max() - 1), "one short of a full bar staggered");
                hero.combat().stagger(); // as a weak point staggers it (crit_above, charge_wall, a broken barrier)
                HeroBoss b = hero.combat().boss();
                t.assertTrue(b.staggerCount() == 1 && hero.combat().staggered(), "the weak point's stagger did not land");
                kneelEnds[0] = h.getLevel().getGameTime() + BossRules.STAGGER_TICKS;
                hero.hurt(soldier.damageSources().mobAttack(soldier), 8);
                t.assertTrue(b.staggerCount() == 1, "a soldier's blow in the kneel staggered it again (a stun-lock)");
                hero.hurt(pl.damageSources().playerAttack(pl), 8);
                t.assertTrue(b.staggerCount() == 1, "a player's blow in the kneel staggered it again (a stun-lock)");
            }));
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (kneelEnds[0] > 0 && now > kneelEnds[0] + 1) {
                    t.assertTrue(!hero.combat().staggered(), "the kneel outlasted its 2.5 s: a second stagger extended it");
                    // the bar works again once the kneel is over
                    t.assertTrue(hero.combat().poiseDamage(hero.combat().poise().max()), "a full bar after the kneel did not stagger");
                    t.assertTrue(hero.combat().boss().staggerCount() == 2, "staggers " + hero.combat().boss().staggerCount());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the barrier's focus (6.6)

    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_rv_focus")
    public static void barrierFocusHoldsThenStaggers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 100);
        try {
            String id = "fx_rv_focus";
            String ward = move("ward", "self", "\"weight\": 0, \"cooldown\": 20, \"effects\": [{\"type\": \"barrier\", \"hits\": 3,"
                    + " \"seconds\": 12, \"focus\": \"decoy\"}]");
            String clones = move("clones", "self", "\"weight\": 0, \"cooldown\": 20, \"effects\": [{\"type\": \"spawn_add\","
                    + " \"add\": \"decoy\", \"count\": 3}]");
            HeroEntity hero = hero(t, id, def(id, ward + ", " + clones), 6, 6);
            hero.setNoAi(true);
            noRegen(hero);
            FactionNpc soldier = foe(t, 8, 6, 100);
            ServerPlayer pl = player(t, 8, 9, FOE, 100);
            t.assertTrue(hero.combat().forceMove("ward"), "the ward did not start");
            UUID[] focusId = new UUID[1];
            long[] popped = new long[1];
            h.runAtTickTime(20, t.guard(() -> {
                t.assertTrue(hero.combat().barrierHits() == 3, "barrier hits " + hero.combat().barrierHits());
                List<HeroDecoy> focus = decoys(h, hero);
                t.assertTrue(focus.size() == 1, "the barrier's focus add: " + focus.size());
                focusId[0] = focus.get(0).getUUID();
                // three more decoys fill the cap of 3: the oldest go, but never the barrier's focus
                t.assertTrue(hero.combat().forceMove("clones"), "the clones did not start");
            }));
            h.runAtTickTime(40, t.guard(() -> {
                List<HeroDecoy> all = decoys(h, hero);
                HeroDecoy focus = null;
                for (HeroDecoy d : all) {
                    focus = d.getUUID().equals(focusId[0]) ? d : focus;
                }
                t.assertTrue(focus != null, "the hero's own spawn_add popped its barrier's focus");
                t.assertTrue(all.size() == 3, "decoys alive: " + all.size());
                t.assertTrue(hero.combat().barrierHits() == 3 && hero.combat().boss().staggerCount() == 0,
                        "its own decoys broke its barrier or staggered it");
                float health = hero.getHealth();
                hero.hurt(soldier.damageSources().mobAttack(soldier), 5);
                t.assertTrue(hero.combat().barrierHits() == 2, "a soldier's blow did not cost the barrier a hit");
                hero.hurt(pl.damageSources().playerAttack(pl), 5);
                t.assertTrue(hero.combat().barrierHits() == 1, "a player's blow did not cost the barrier a hit");
                hero.hurt(soldier.damageSources().mobAttack(soldier), 5);
                hero.hurt(pl.damageSources().playerAttack(pl), 5);
                t.assertTrue(hero.combat().barrierHits() == 1, "the last hit fell while its focus lives");
                t.assertTrue(hero.getHealth() == health, "a blow went through the barrier");
                t.assertTrue(hero.combat().boss().staggerCount() == 0, "staggered before the focus fell");
                focus.hurt(pl.damageSources().playerAttack(pl), 1);
                popped[0] = h.getLevel().getGameTime();
            }));
            t.onEachTick(() -> {
                if (popped[0] > 0 && h.getLevel().getGameTime() >= popped[0] + 2) {
                    t.assertTrue(hero.combat().barrierHits() == 0, "the barrier outlived its focus");
                    t.assertTrue(hero.combat().boss().staggerCount() == 1 && hero.combat().staggered(),
                            "killing the focus did not stagger the hero");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    private static List<HeroDecoy> decoys(GameTestHelper h, HeroEntity hero) {
        return h.getLevel().getEntitiesOfClass(HeroDecoy.class, hero.getBoundingBox().inflate(12),
                d -> d.owner() == hero && !d.popped() && d.isAlive());
    }

    // ------------------------------------------------------------------ zones ignore shields (SLICE_PLAN 3.4)

    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_rv_zone")
    public static void zonesIgnoreShields(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String circle = move("circle", "zone", "\"class\": \"area\", \"zone_at\": \"target\", \"range\": 10, \"radius\": 2,"
                    + " \"cooldown\": 10, " + DMG4);
            String jab = move("jab", "melee", "\"class\": \"light\", \"cooldown\": 6, " + DMG4);
            String a = "fx_rv_zone", b = "fx_rv_zone2", c = "fx_rv_zone3";
            HeroEntity hn = hero(t, a, def(a, "controller", circle, "", ""), 4, 4);
            HeroEntity hp = hero(t, b, def(b, "controller", circle, "", ""), 4, 16);
            HeroEntity hj = hero(t, c, def(c, "striker", jab, "", ""), 18, 10);
            for (HeroEntity x : List.of(hn, hp, hj)) {
                x.setNoAi(true);
            }
            FactionNpc arch = archer(t, 2, 22);
            FactionNpc npc = shieldBearer(t, 8, 4, 100, arch), blocker = shieldBearer(t, 20, 10, 100, arch);
            ServerPlayer pl = blockingPlayer(t, 8, 16, FOE, 100);
            pl.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            pl.startUsingItem(InteractionHand.OFF_HAND);
            for (LivingEntity e : List.<LivingEntity>of(npc, blocker, pl)) {
                // face the heroes (west), so a shield covers whatever comes from them
                e.setYRot(90F);
                e.setYHeadRot(90F);
                e.yBodyRot = 90F;
            }
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(npc.isBlocking() && pl.isBlocking() && blocker.isBlocking(), "a shield is not blocking");
                force(t, hn, npc, "circle");
                force(t, hp, pl, "circle");
                force(t, hj, blocker, "jab");
            }));
            h.runAtTickTime(80, t.guard(() -> {
                t.assertTrue(npc.isBlocking() && pl.isBlocking(), "a shield went down");
                t.assertTrue(npc.getHealth() < 100, "the zone's burst was blocked by the soldier's shield");
                t.assertTrue(pl.getHealth() < 100, "the zone's burst was blocked by the player's shield");
                HeroAbilityGameTests.near(t, 100 - npc.getHealth(), 100 - pl.getHealth(), "the same burst through both shields");
                t.assertTrue(blocker.getHealth() >= 100, "a blockable blow from the front went through a raised shield");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ what a move gives its side

    /** An ally soldier of the heroes' side at relative (x, 2, z), still, at half of 100 health, not healing on its own. */
    static FactionNpc friend(GameTestBases.Handle t, int x, int z) {
        FactionNpc n = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        n.faction(SIDE);
        n.role(NpcRole.SWORDSMAN);
        n.setNoAi(true);
        n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        n.setHealth(50);
        return noRegen(n);
    }

    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_rv_allies")
    public static void allyEffectsReachTheirOwnRadius(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String bless = move("bless", "nova", "\"class\": \"area\", \"radius\": 3, \"cooldown\": 10, \"effects\": ["
                    + "{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"heal\", \"amount\": 4, \"to\": \"allies\", \"radius\": 8}]");
            String dance = move("dance", "sweep", "\"class\": \"area\", \"range\": 4, \"arc\": 90, \"duration\": 1, \"cooldown\": 10,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"effect\", \"id\": \"regeneration\","
                    + " \"level\": 1, \"seconds\": 4, \"to\": \"allies\", \"radius\": 8}]");
            String a = "fx_rv_allies", b = "fx_rv_allies2";
            HeroEntity priest = hero(t, a, def(a, "support", bless, "", ""), 4, 4);
            HeroEntity dancer = hero(t, b, def(b, "support", dance, "", ""), 16, 16);
            priest.setNoAi(true);
            dancer.setNoAi(true);
            Husk near = husk(t, 6, 4, 100); // inside the nova's 3 blocks
            Husk ahead = husk(t, 16, 21, 100); // 5 blocks ahead of the dancer: outside its 4-block line
            FactionNpc soldierA = friend(t, 10, 4); // 6 blocks from the priest: outside the nova, inside the heal's 8
            ServerPlayer playerA = player(t, 4, 10, SIDE, 100);
            playerA.setHealth(50);
            FactionNpc soldierB = friend(t, 16, 11);
            ServerPlayer playerB = player(t, 21, 16, SIDE, 100);
            playerB.setHealth(50);
            force(t, priest, near, "bless");
            h.runAtTickTime(32, t.guard(() -> {
                t.assertTrue(near.getHealth() < 100, "the nova did not strike its target");
                t.assertTrue(soldierA.getHealth() > 50, "an ally soldier 6 blocks away was not healed by a heal of radius 8");
                t.assertTrue(playerA.getHealth() > 50, "an ally player 6 blocks away was not healed by a heal of radius 8");
                HeroAbilityGameTests.near(t, soldierA.getHealth(), playerA.getHealth(), "the same heal for a soldier and a player");
                force(t, dancer, ahead, "dance");
            }));
            h.runAtTickTime(70, t.guard(() -> {
                t.assertTrue(ahead.getHealth() >= 100, "the sweep reached past its 4 blocks");
                t.assertTrue(soldierB.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
                        "a sweep that passed no enemy gave its allies nothing (the soldier)");
                t.assertTrue(playerB.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
                        "a sweep that passed no enemy gave its allies nothing (the player)");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ creative players are never enemies (6.1)

    /** A creative mock player of {@code faction} at relative (x, 2, z); the cleanup removes it and its membership. */
    static ServerPlayer creativePlayer(GameTestBases.Handle t, int x, int z, FactionType faction) {
        ServerLevel level = t.helper.getLevel();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "hero-creative-player")) {
            @Override
            public void tick() {
                baseTick();
            }
        };
        Vec3 at = Vec3.atBottomCenterOf(t.helper.absolutePos(new BlockPos(x, 2, z)));
        p.moveTo(at.x, at.y, at.z, 0F, 0F);
        p.setGameMode(GameType.CREATIVE);
        CampaignData data = CampaignData.get(level.getServer());
        data.players.put(p.getUUID(), faction);
        level.addFreshEntity(p);
        t.onCleanup(() -> {
            data.players.remove(p.getUUID());
            p.discard();
        });
        return p;
    }

    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_rv_creative")
    public static void creativePlayerIsNeverAnEnemy(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            String id = "fx_rv_creative";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", "", ""), 6, 6);
            noRegen(hero);
            ServerPlayer creative = creativePlayer(t, 8, 6, FOE);
            t.assertTrue(creative.isCreative(), "the mock player is not creative");
            HeroBoss b = hero.combat().boss();
            h.runAtTickTime(2, t.guard(() -> hero.hurt(creative.damageSources().playerAttack(creative), 2)));
            ServerPlayer[] survival = new ServerPlayer[1];
            t.onEachTick(() -> {
                long tick = h.getTick();
                if (tick > 2 && tick < 50) {
                    t.assertTrue(!b.fighting() && b.engaged() == 0, "a creative player's blow engaged the hero (engaged "
                            + b.engaged() + ")");
                    t.assertTrue(hero.getTarget() != creative, "the hero took a creative player as its target");
                } else if (tick == 50) {
                    survival[0] = player(t, 8, 10, FOE, 100);
                    hero.hurt(survival[0].damageSources().playerAttack(survival[0]), 2);
                } else if (tick >= 52) {
                    t.assertTrue(b.fighting() && b.engaged() >= 1, "a survival player's blow did not start the fight");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a hero's move on a hero is capped (6.2)

    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_rv_cap")
    public static void heroMovesOnAHeroAreCapped(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String a = "fx_rv_cap", b = "fx_rv_cap2";
            String smash = move("smash", "melee", "\"class\": \"light\", \"cooldown\": 6,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 30}]");
            HeroEntity striker = hero(t, a, def(a, smash), 6, 6);
            HeroEntity target = hero(t, b, visual(b), def(b, ""), FOE, 8, 6);
            for (HeroEntity x : List.of(striker, target)) {
                x.setNoAi(true);
                noRegen(x);
            }
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                target.setItemSlot(slot, ItemStack.EMPTY); // no armour: the move's 16 reaches the cap whole
            }
            float max = target.getMaxHealth();
            force(t, striker, target, "smash");
            t.onEachTick(() -> {
                // two ticks after the hit: the target has seen its fight begin, and the opening blow's poise must stay
                if (striker.combat().lastRelease() < 0 || h.getLevel().getGameTime() < striker.combat().lastRelease() + 2) {
                    return;
                }
                t.assertTrue(target.combat().boss().fighting(), "the hit did not start the target's fight");
                BossRules.Landed l = target.combat().boss().lastLanded();
                t.assertTrue(l != null, "the move did not reach the hero's incoming path");
                double cap = max * BossRules.MAX_HIT_SHARE_DEFAULT / 100.0;
                t.assertTrue(max - target.getHealth() <= cap + 1e-3, "a hero's move took " + (max - target.getHealth())
                        + " from a hero in one hit, over 12% (" + cap + ")");
                t.assertTrue(l.excess() > 0, "the move was not over the cap (no excess): the test sees nothing");
                t.assertTrue(target.combat().poise().value() >= l.excess(), "the excess did not become poise, or the fight's"
                        + " start emptied the bar the opening blow filled: " + target.combat().poise().value());
                t.succeed();
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the punish window and the metal weak points (6.9)

    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_rv_punish")
    public static void punishWindowAndWeakPointsAlikeForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_rv_punish";
            String cut = move("cut", "melee", "\"class\": \"light\", \"weight\": 0, \"cooldown\": 10,"
                    + " \"frames\": {\"windup\": 0.4, \"active\": 0.1, \"recovery\": 1.0}, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            String def = "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"Punish Fixture\", \"gender\": \"female\","
                    + " \"archetype\": \"samurai\", \"rarity\": \"common\", \"behaviour\": {\"combat_role\": \"striker\"},"
                    + " \"basic_attack\": " + MELEE + ", \"moves\": [" + cut + "], \"boss\": {\"weak_points\": [\"recovery\", \"back\","
                    + " \"iron\"], \"phases\": [{\"at\": 100}]}, \"personality\": {\"temperament\": \"stoic\"},"
                    + " \"leader\": {\"eligible\": false}}";
            HeroEntity hero = hero(t, id, def, 6, 6);
            hero.setNoAi(true);
            noRegen(hero);
            hero.setYRot(0F); // facing +z, toward its target
            hero.setYHeadRot(0F);
            hero.yBodyRot = 0F;
            Husk husk = husk(t, 6, 8, 300);
            FactionNpc front = foe(t, 7, 7, 100); // in front, a stone sword: the plain hit
            front.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            FactionNpc back = foe(t, 6, 4, 100); // behind it, an iron sword
            back.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            ServerPlayer pl = player(t, 5, 4, FOE, 100); // behind it too, an iron sword
            pl.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            double[] plain = new double[1];
            h.runAtTickTime(2, t.guard(() -> {
                hero.hurt(front.damageSources().mobAttack(front), 4);
                plain[0] = hero.combat().boss().lastLanded().health();
                t.assertTrue(plain[0] > 0, "the plain hit did not land");
                force(t, hero, husk, "cut");
            }));
            h.runAtTickTime(18, t.guard(() -> {
                t.assertTrue(hero.combat().stage() == com.factionclash.logic.hero.MovePlan.MoveStage.RECOVERY, "not in the recovery: "
                        + hero.combat().stage());
                hero.hurt(back.damageSources().mobAttack(back), 4);
                double npc = hero.combat().boss().lastLanded().health();
                hero.hurt(pl.damageSources().playerAttack(pl), 4);
                double player = hero.combat().boss().lastLanded().health();
                // recovery +15 doubled, from behind in it +15 doubled, iron +15: x1.75 (BossRules.takenFactor)
                double factor = BossRules.takenFactor(new BossRules.Window(false, true, true, false, false,
                        BossRules.WeaponTier.IRON), java.util.EnumSet.of(com.factionclash.logic.hero.HeroDef.WeakPoint.RECOVERY,
                        com.factionclash.logic.hero.HeroDef.WeakPoint.BACK, com.factionclash.logic.hero.HeroDef.WeakPoint.IRON));
                HeroAbilityGameTests.near(t, plain[0] * factor, npc, "a soldier's iron blow from behind in the recovery");
                HeroAbilityGameTests.near(t, npc, player, "the same blow from a player and a soldier");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ enraged, it waits shorter gaps (6.3, 6.10)

    @GameTest(template = "platform", timeoutTicks = 240, batch = "hero_rv_rage")
    public static void enragedHeroWaitsShorterGaps(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 240);
        try {
            String id = "fx_rv_rage";
            String jab = move("jab", "melee", "\"class\": \"light\", \"weight\": 0, \"cooldown\": 1, " + DMG4);
            HeroEntity hero = hero(t, id, def(id, jab), 6, 6);
            hero.setNoAi(true);
            noRegen(hero);
            Husk husk = husk(t, 8, 6, 2000);
            hero.setTarget(husk);
            h.runAtTickTime(3, t.guard(() -> {
                t.assertTrue(hero.combat().boss().fighting(), "no fight with its target");
                hero.combat().boss().backdateFight(20 * 200); // 200 s of fighting: past the 180 s soft enrage
            }));
            int[] gaps = {0};
            boolean[] wasRunning = {false};
            t.onEachTick(() -> {
                if (h.getTick() < 6) {
                    return;
                }
                t.assertTrue(hero.combat().boss().enraged() && hero.stateFlag(HeroActions.STATE_ENRAGED), "not enraged");
                boolean running = hero.combat().running();
                long now = h.getLevel().getGameTime();
                if (wasRunning[0] && !running) {
                    long gap = hero.combat().selector().gapEndsAt() - now;
                    // phase 1's gap is 1.5-2.5 s; enraged x0.7: 1.05-1.75 s (21-35 ticks)
                    t.assertTrue(gap >= 21 && gap <= 35, "an enraged hero's gap was " + gap + " ticks (21-35 expected)");
                    if (++gaps[0] == 4) {
                        t.succeed();
                        return;
                    }
                }
                if (!running) {
                    t.assertTrue(hero.combat().forceMove("jab"), "the jab would not start");
                    running = true;
                }
                wasRunning[0] = running;
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a hero against a hero

    @GameTest(template = "platform", timeoutTicks = 500, batch = "hero_rv_duel")
    public static void twoHeroesFightEachOther(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 500);
        try {
            String na = "natsuho_komezaki", hi = "hinae_shirasuzu";
            HeroEntity cat = hero(t, na, visual(na), slice(na), SIDE, 4, 12);
            HeroEntity priest = hero(t, hi, visual(hi), slice(hi), FOE, 14, 12);
            noRegen(cat);
            noRegen(priest);
            HeroEntity[] both = {cat, priest};
            BossRules.Landed[] seen = new BossRules.Landed[2];
            boolean[] fought = new boolean[2];
            t.onEachTick(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = both[i];
                    if (!x.isAlive()) {
                        continue;
                    }
                    HeroBoss b = x.combat().boss();
                    fought[i] |= b.fighting();
                    BossRules.Landed l = b.lastLanded();
                    if (l != null && l != seen[i]) {
                        seen[i] = l;
                        t.assertTrue(l.health() <= x.getMaxHealth() * BossRules.MAX_HIT_SHARE_DEFAULT / 100.0 + 1e-3,
                                x.heroId() + " took " + l.health() + " in one hit, over 12% of " + x.getMaxHealth());
                    }
                }
                if (h.getTick() == 240 && cat.isAlive()) {
                    // a save in the middle of the fight loads, with its cooldowns and no move running
                    CompoundTag tag = cat.saveWithoutId(new CompoundTag());
                    HeroEntity copy = ModEntities.HERO.get().create(h.getLevel());
                    copy.load(tag);
                    t.assertTrue(!copy.combat().running() && !copy.stateFlag(HeroActions.STATE_WINDUP),
                            "a copy loaded mid-fight runs a move");
                    t.assertTrue(copy.energy() == cat.energy(), "energy " + copy.energy() + " after the load, " + cat.energy());
                    copy.discard();
                }
                if (h.getTick() >= 440) {
                    for (int i = 0; i < 2; i++) {
                        t.assertTrue(fought[i], both[i].heroId() + " never fought");
                        t.assertTrue(both[i].combat().lastMoveStart() >= 0, both[i].heroId() + " never picked a move");
                        t.assertTrue(both[i].getHealth() < both[i].getMaxHealth() || !both[i].isAlive(),
                                both[i].heroId() + " was never hurt");
                    }
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a save and a death in a wind-up

    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_rv_midmove")
    public static void saveAndDeathMidMove(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            String id = "fx_rv_mid";
            String cone = move("cleave", "cone", "\"class\": \"heavy\", \"range\": 5, \"angle\": 90, \"cooldown\": 30,"
                    + " \"frames\": {\"windup\": 1.2, \"active\": 0.2, \"recovery\": 0.8}, " + DMG4);
            HeroEntity hero = hero(t, id, def(id, cone), 4, 6);
            hero.setNoAi(true);
            FactionNpc soldier = foe(t, 7, 6, 100);
            player(t, 4, 14, SIDE, 100); // a player of its side near: the wind-up's decal is drawn (6.1)
            force(t, hero, soldier, "cleave");
            h.runAtTickTime(10, t.guard(() -> {
                t.assertTrue(hero.combat().windingUp() && hero.stateFlag(HeroActions.STATE_WINDUP), "not in the wind-up");
                t.assertTrue(hero.combat().telegraphs().liveCount() == 1, "no decal for the wind-up");
                CompoundTag tag = hero.saveWithoutId(new CompoundTag());
                HeroEntity copy = ModEntities.HERO.get().create(h.getLevel());
                copy.load(tag);
                t.assertTrue(!copy.combat().running() && !copy.stateFlag(HeroActions.STATE_WINDUP)
                        && HeroActions.code(copy.action()) <= 0, "a copy saved in a wind-up loads running it");
                copy.discard();
                hero.kill();
            }));
            h.runAtTickTime(12, t.guard(() -> {
                t.assertTrue(!hero.isAlive() && !hero.combat().running(), "a dead hero runs its move");
                t.assertTrue(hero.combat().telegraphs().liveCount() == 0, "a dead hero's decal stayed");
            }));
            h.runAtTickTime(50, t.guard(() -> {
                t.assertTrue(soldier.getHealth() >= 100, "the move of a hero that died in its wind-up landed");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ sixteen heroes (section 11)

    /** Server time per hero tick allowed by this test: generous (the budget of 11 is 0.05 ms in a fight). */
    static final double TICK_MS_LIMIT = 1.0, COMBAT_MS_LIMIT = 0.25;

    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_rv_sixteen")
    public static void sixteenHeroesFightWithinBudget(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String moves = move("cut", "melee", "\"class\": \"light\", \"cooldown\": 4, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]")
                    + ", " + move("burst", "nova", "\"class\": \"area\", \"radius\": 3, \"cooldown\": 8,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"knockback\", \"strength\": 1}]")
                    + ", " + move("bolt", "shot", "\"class\": \"light\", \"range\": 14, \"cooldown\": 6,"
                    + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}]")
                    + ", " + move("pool", "zone", "\"class\": \"area\", \"zone_at\": \"target\", \"range\": 12, \"radius\": 2,"
                    + " \"duration\": 3, \"interval\": 1, \"cooldown\": 10, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            List<HeroEntity> heroes = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String id = "fx_rv_16_" + i;
                boolean side = i < 8;
                HeroEntity x = hero(t, id, visual(id), def(id, "striker", moves, "", ""), side ? SIDE : FOE, 2 + 2 * (i % 8), side ? 6 : 16);
                noRegen(x);
                heroes.add(x);
            }
            long[] stats = new long[3];
            t.onEachTick(() -> {
                long tick = h.getTick();
                if (tick == 100) {
                    HeroEntity.resetTickStats();
                }
                if (tick > 100) {
                    AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(48);
                    for (HeroEntity x : heroes) {
                        int zones = h.getLevel().getEntitiesOfClass(HeroZone.class, box, z -> z.hero() == x).size();
                        t.assertTrue(zones <= com.factionclash.hero.HeroCombat.MAX_ZONES, x.heroId() + " has " + zones + " zones");
                    }
                    int shots = h.getLevel().getEntitiesOfClass(HeroProjectile.class, box).size();
                    t.assertTrue(shots <= 64, shots + " hero projectiles in flight for 16 heroes");
                }
                if (tick == 340) {
                    long[] s = HeroEntity.tickStats();
                    System.arraycopy(s, 0, stats, 0, 3);
                    double perTick = s[2] == 0 ? 0 : s[0] / 1e6 / s[2], combat = s[2] == 0 ? 0 : s[1] / 1e6 / s[2];
                    int moved = 0, fighting = 0;
                    for (HeroEntity x : heroes) {
                        moved += x.combat().lastMoveStart() >= 0 ? 1 : 0;
                        fighting += x.combat().boss().fighting() || !x.isAlive() ? 1 : 0;
                    }
                    FactionClash.LOGGER.info("[Heroes] review: 16 heroes fighting, {} hero ticks: {} ms a hero tick, {} ms of it in"
                            + " the fight runtime (budget 0.05); {} picked a move, {} fighting or fallen",
                            s[2], String.format("%.4f", perTick), String.format("%.4f", combat), moved, fighting);
                    t.assertTrue(s[2] >= 16 * 200, "too few hero ticks timed: " + s[2]);
                    t.assertTrue(moved >= 12, "only " + moved + " of 16 heroes picked a move");
                    t.assertTrue(perTick < TICK_MS_LIMIT, "a hero tick took " + perTick + " ms on average");
                    t.assertTrue(combat < COMBAT_MS_LIMIT, "the fight runtime took " + combat + " ms a hero tick on average");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
