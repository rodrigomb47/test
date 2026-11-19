package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.HeroState;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBlasts;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroStatus;
import com.factionclash.logic.combat.EdgeSafety;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.work.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.factionclash.gametest.HeroAbilityGameTests.MELEE;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;

/**
 * The review of the round's terrain work (HEROES_V2 6.17, {@code docs/design/breaching.md} 8; tasks T1 and T3,
 * 2026-11-19): what the adversarial pass found in the ordinary NPCs' defence, each held here by a test that failed
 * before its fix.
 * <ul>
 * <li>{@code stunnedNpcNeitherDrinksNorPoursLikeAStunnedPlayer}: equal treatment. A stunned player cannot start using an
 * item ({@code HeroControlEvents}), so a stunned soldier neither drinks its Fire Resistance while it burns nor empties its
 * water bucket over a 12-block fall; it drinks once the stun is over. Before the review it drank and poured through
 * the stun.</li>
 * <li>{@code waterLandingIsPickedUpAfterASaveMidFall}: a soldier saved and loaded between its water landing's pour and its
 * touch-down picks its water up again; before, the source block stayed in the world and its bucket stayed empty.</li>
 * <li>{@code edgeSenseReadsTheHeroAmongFivePushers}: among five pushers listed four Knockback soldiers first, the hero
 * among them still makes the reach 6 blocks (a lava edge 4.5 blocks behind a soldier makes its cell unsafe); with five
 * Knockback soldiers and no hero the same cell is safe (reach 4). Before, the first four listed were kept and the hero
 * was left out.</li>
 * <li>{@code aHeroBeyondAPortalIsNotAtHomeThere}: every base and every revival is in the overworld. A hero carried into the
 * Nether has no own side at its base's coordinates there (its explosive moves are not held back by a base it is not
 * near), and a {@code where_it_fell} hero that dies there rises at home, not on whatever stands at the same coordinates
 * in the overworld. Before, the own-side check read the overworld's bases and the revival rose on a block in the
 * overworld's sky.</li>
 * </ul>
 * Each test has its own batch; every block placed is put back by the cleanup.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroTerrainReviewGameTests {
    private HeroTerrainReviewGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    private static ItemStack fireResistance() {
        return PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE);
    }

    private static int count(FactionNpc n, net.minecraft.world.item.Item item) {
        return Inv.count(n.inventory(), s -> s.is(item));
    }

    /** Clears any water left in relative rows 1-3 of the square round (cx, cz), at the cleanup. */
    private static void clearWater(GameTestBases.Handle t, int cx, int cz) {
        GameTestHelper h = t.helper;
        t.onCleanup(() -> {
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    for (int y = 1; y <= 3; y++) {
                        BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                        if (!h.getLevel().getFluidState(p).isEmpty()) {
                            h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ a stun holds items for everyone

    /**
     * {@code stunnedNpcNeitherDrinksNorPoursLikeAStunnedPlayer}: a burning soldier stunned for 6 s with a potion of Fire
     * Resistance does not drink it during the stun (past its 3 s of burning and a whole drink's 1.6 s later it still has
     * the potion and no effect) and drinks it after; a soldier stunned for its 12-block fall pours no water and is hurt
     * by the fall, its water bucket still full, while one not stunned beside it makes its water landing; a mock player
     * stunned the same 6 s cannot start drinking its potion, and can once the stun is over. The same stun, the same
     * seconds, the same rule: a stun holds what anyone would use.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 260, batch = "terrain_rv_stun")
    public static void stunnedNpcNeitherDrinksNorPoursLikeAStunnedPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            FactionNpc burner = HeroTerrainGameTests.still(t, FOE, 4, 2, 4, 60);
            burner.inventory().addItem(fireResistance());
            FactionNpc faller = HeroTerrainGameTests.still(t, FOE, 16, 14, 16, 20);
            FactionNpc control = HeroTerrainGameTests.still(t, FOE, 24, 14, 24, 20);
            for (FactionNpc n : new FactionNpc[]{faller, control}) {
                n.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
                n.inventory().addItem(new ItemStack(Items.WATER_BUCKET));
            }
            clearWater(t, 16, 16);
            clearWater(t, 24, 24);
            ServerPlayer p = HeroTerrainGameTests.movingPlayer(t, 10.5, 2, 4.5, FOE, 20);
            p.setItemInHand(InteractionHand.MAIN_HAND, fireResistance());
            burner.igniteForSeconds(20F);
            t.assertTrue(HeroStatus.stun(burner, 6) && HeroStatus.stun(faller, 6) && HeroStatus.stun(p, 6), "a stun did not take");
            t.onEachTick(() -> {
                long tick = h.getTick();
                if (tick == 5) {
                    p.startUsingItem(InteractionHand.MAIN_HAND);
                    t.assertTrue(!p.isUsingItem(), "a stunned player started drinking");
                }
                if (tick == 112) {
                    t.assertTrue(HeroStatus.stunned(burner), "the stun ended early");
                    t.assertTrue(burner.isOnFire(), "the soldier stopped burning");
                    t.assertTrue(burner.terrainSurvival().drinks() == 0 && !burner.hasEffect(MobEffects.FIRE_RESISTANCE)
                            && count(burner, Items.POTION) == 1, "a stunned soldier drank its Fire Resistance");
                    t.assertTrue(!burner.terrainSurvival().drinking(), "a stunned soldier is drinking");
                    t.assertTrue(faller.onGround() && control.onGround(), "the fallers are not down yet");
                    t.assertTrue(faller.terrainSurvival().landings() == 0 && count(faller, Items.WATER_BUCKET) == 1,
                            "a stunned soldier poured its water bucket");
                    t.assertTrue(faller.getHealth() < 15, "the stunned soldier's 12-block fall did not hurt: " + faller.getHealth());
                    t.assertTrue(control.terrainSurvival().landings() == 1 && control.getHealth() >= 20 - 1e-3,
                            "the soldier that was not stunned made no water landing: " + control.getHealth());
                }
                if (tick == 135) {
                    t.assertTrue(!HeroStatus.stunned(p), "the player's stun did not end");
                    p.startUsingItem(InteractionHand.MAIN_HAND);
                    t.assertTrue(p.isUsingItem(), "the player cannot drink after the stun");
                    p.stopUsingItem();
                }
                if (tick == 210) {
                    t.assertTrue(burner.terrainSurvival().drinks() == 1 && burner.hasEffect(MobEffects.FIRE_RESISTANCE)
                            && count(burner, Items.POTION) == 0, "the soldier did not drink once the stun was over");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ a save between the pour and the touch-down

    /**
     * {@code waterLandingIsPickedUpAfterASaveMidFall}: a soldier with a water bucket dropped from 12 blocks is saved and
     * loaded (the entity goes, a copy is read from its tag, as at a chunk unload or a server stop) on the tick after it
     * poured its water; the copy lands unhurt in the water and picks it up again: no source block left on the ground,
     * its bucket full again.
     */
    @GameTest(template = HeroMobilityGameTests.FIELD, timeoutTicks = 120, batch = "terrain_rv_landing_save")
    public static void waterLandingIsPickedUpAfterASaveMidFall(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            FactionNpc wet = HeroTerrainGameTests.still(t, FOE, 8, 14, 16, 20);
            wet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
            wet.inventory().addItem(new ItemStack(Items.WATER_BUCKET));
            clearWater(t, 8, 16);
            FactionNpc[] now = {wet};
            BlockPos[] poured = {null};
            t.onEachTick(() -> {
                FactionNpc n = now[0];
                if (now[0] == wet && wet.terrainSurvival().landings() == 1 && !wet.onGround()) {
                    poured[0] = wet.terrainSurvival().poured();
                    t.assertTrue(poured[0] != null, "poured nowhere");
                    CompoundTag tag = wet.saveWithoutId(new CompoundTag());
                    ServerLevel level = h.getLevel();
                    wet.discard();
                    FactionNpc copy = ModEntities.FACTION_NPC.get().create(level);
                    copy.load(tag);
                    t.assertTrue(level.addFreshEntity(copy), "the loaded copy did not join the level");
                    now[0] = t.track(copy);
                    return;
                }
                if (h.getTick() == 90) {
                    t.assertTrue(n != wet, "it never poured");
                    t.assertTrue(!h.getLevel().getFluidState(poured[0]).isSource(), "the water was left on the ground at " + poured[0]);
                    t.assertTrue(count(n, Items.WATER_BUCKET) == 1 && count(n, Items.BUCKET) == 0, "its bucket is not full again");
                    t.assertTrue(n.onGround(), "the copy is not down");
                    t.assertTrue(n.getHealth() >= 20 - 1e-3, "the landing hurt: " + n.getHealth());
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ beyond a portal

    /**
     * {@code aHeroBeyondAPortalIsNotAtHomeThere}: a {@code where_it_fell} hero inside its base (its own side 0 blocks away)
     * is carried into the Nether at the same x and z (a portal's trip, {@code changeDimension}); there its own side is
     * nowhere near. It dies there; a stone block stands in the overworld's sky at those coordinates (a cell a revival could
     * rise on); it rises at home.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "terrain_rv_nether")
    public static void aHeroBeyondAPortalIsNotAtHomeThere(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 60);
        MinecraftServer server = h.getLevel().getServer();
        try {
            String id = "fx_terrain_rv_nether";
            HeroEntity hero = HeroAbilityGameTests.hero(t, id, "katana", HeroAbilityGameTests.def(id, MELEE, "striker", IDLE_MOVE,
                    ", \"revival\": {\"seconds\": 120, \"where\": \"where_it_fell\"}"), 12, 12);
            hero.setNoAi(true);
            t.assertTrue(HeroBlasts.ownSideDistance(hero, hero.getX(), hero.getZ()) < 1, "not at home in its own base: "
                    + HeroBlasts.ownSideDistance(hero, hero.getX(), hero.getZ()));
            ServerLevel nether = server.getLevel(Level.NETHER);
            t.assertTrue(nether != null, "no Nether on the test server");
            BlockPos at = BlockPos.containing(hero.getX(), 100, hero.getZ());
            nether.getChunk(at);
            // a cell in the overworld's sky at the same coordinates, which a revival read in the wrong world would rise on
            BlockPos sky = at.below();
            h.getLevel().setBlockAndUpdate(sky, Blocks.STONE.defaultBlockState());
            t.onCleanup(() -> h.getLevel().setBlockAndUpdate(sky, Blocks.AIR.defaultBlockState()));
            Entity moved = hero.changeDimension(new DimensionTransition(nether, Vec3.atBottomCenterOf(at), Vec3.ZERO, 0F, 0F,
                    DimensionTransition.DO_NOTHING));
            t.assertTrue(moved instanceof HeroEntity && moved.level() == nether, "not carried into the Nether: " + moved);
            HeroEntity there = (HeroEntity) moved;
            there.setNoAi(true);
            t.assertTrue(HeroBlasts.ownSideDistance(there, there.getX(), there.getZ()) == Double.POSITIVE_INFINITY,
                    "in the Nether its own side is still " + HeroBlasts.ownSideDistance(there, there.getX(), there.getZ()) + " blocks away");
            there.hurt(nether.damageSources().genericKill(), Float.MAX_VALUE);
            HeroState s = t.data.heroes.get(id);
            t.assertTrue(s != null && !s.alive, "it did not die in the Nether");
            s.reviveAt = h.getLevel().getGameTime();
            HeroManager.tick(server);
            HeroEntity back = HeroManager.entity(server, id);
            t.assertTrue(back != null, "not back");
            back.setNoAi(true);
            t.assertTrue(back.blockPosition().equals(t.base.point(BaseState.SPAWN)),
                    "after a death in the Nether it should rise at home, not at " + back.blockPosition());
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ many pushers, a hero among them

    /** A {@link #SIDE} soldier with a Knockback II sword at relative (x, 2, z), still. */
    private static FactionNpc knocker(GameTestBases.Handle t, int x, int z) {
        FactionNpc s = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        s.faction(SIDE);
        s.role(NpcRole.SWORDSMAN);
        s.setNoAi(true);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        Holder<Enchantment> knock = t.helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.KNOCKBACK);
        sword.enchant(knock, 2);
        s.setItemSlot(EquipmentSlot.MAINHAND, sword);
        return s;
    }

    /** A {@link #FOE} soldier at relative (x, 2, z), still, fighting {@code target}. */
    private static FactionNpc guarded(GameTestBases.Handle t, int x, int z, net.minecraft.world.entity.LivingEntity target) {
        FactionNpc n = t.track(t.helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z)));
        n.faction(FOE);
        n.role(NpcRole.SWORDSMAN);
        n.setNoAi(true);
        n.setTarget(target);
        return n;
    }

    /**
     * {@code edgeSenseReadsTheHeroAmongFivePushers}: two lanes, each a soldier with a lava pool 4.5 blocks behind its cell
     * on the line from its enemies 5 blocks in front. In the first, four Knockback soldiers and then a hero stand there,
     * listed in that order: the hero is among the pushers read, so the reach is 6 and the soldier's cell is unsafe. In the
     * second, five Knockback soldiers and no hero: the reach is 4 and the same cell is safe (the lava is beyond it).
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "terrain_rv_pushers")
    public static void edgeSenseReadsTheHeroAmongFivePushers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 60);
        try {
            for (int lane : new int[]{8, 18}) {
                HeroTerrainGameTests.rect(t, 14, 16, 0, lane - 1, lane + 1, Blocks.STONE);
                HeroTerrainGameTests.lava(t, 14, 16, lane - 1, lane + 1);
            }
            for (int i = 0; i < 4; i++) {
                knocker(t, 4, 8);
            }
            HeroEntity hero = HeroAbilityGameTests.hero(t, "fx_terrain_rv_pusher", "katana",
                    HeroAbilityGameTests.def("fx_terrain_rv_pusher", MELEE, "striker", IDLE_MOVE, ""), 4, 8);
            hero.setNoAi(true);
            FactionNpc withHero = guarded(t, 9, 8, hero);
            FactionNpc[] knockers = new FactionNpc[5];
            for (int i = 0; i < 5; i++) {
                knockers[i] = knocker(t, 4, 18);
            }
            FactionNpc withoutHero = guarded(t, 9, 18, knockers[4]);
            h.runAfterDelay(3, t.guard(() -> {
                t.assertTrue(withHero.edgeSense().active() && withoutHero.edgeSense().active(), "no pusher read");
                t.assertTrue(withHero.edgeSense().pushers() == EdgeSafety.MAX_PUSHERS, "pushers read: " + withHero.edgeSense().pushers());
                t.assertTrue(withHero.edgeSense().heroNear(), "the hero among five pushers was left out of the reading");
                t.assertTrue(!withoutHero.edgeSense().heroNear(), "a hero read where none stands");
                t.assertTrue(withHero.edgeSense().unsafeAt(withHero.blockPosition()),
                        "with a hero among the pushers the lava 4.5 blocks behind should make the cell unsafe (reach 6)");
                t.assertTrue(!withoutHero.edgeSense().unsafeAt(withoutHero.blockPosition()),
                        "with Knockback soldiers only the lava 4.5 blocks behind is beyond the reach of 4");
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
