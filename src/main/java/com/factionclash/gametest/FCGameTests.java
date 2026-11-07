package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.Recruitment;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.storage.FactionStorage;
import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * In-world checks run on a real headless server (./gradlew runGameTestServer). They exercise the actual entity,
 * physics, damage pipeline, storage containers and recruitment payment code paths.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class FCGameTests {
    private FCGameTests() {
    }

    private static FactionNpc npc(GameTestHelper h, int x, int z, FactionType f, NpcRole role) {
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
        n.faction(f);
        n.role(role);
        return n;
    }

    /** Acceptance 11 / spec 13: NPCs walk at player-equivalent speed (~4.3 blocks/s). */
    @GameTest(template = "runway", timeoutTicks = 300)
    public static void npcWalksAtPlayerSpeed(GameTestHelper h) {
        FactionNpc n = npc(h, 2, 4, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        BlockPos goal = h.absolutePos(new BlockPos(38, 2, 4));
        long[] t = {-1, -1};
        h.onEachTick(() -> {
            if (t[1] >= 0) {
                return;
            }
            if (n.getNavigation().isDone()) {
                n.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
            double rx = n.getX() - origin.getX();
            if (t[0] < 0 && rx >= 8) {
                t[0] = h.getTick();
            }
            if (t[0] >= 0 && rx >= 32) {
                t[1] = h.getTick();
                double speed = 24.0 / ((t[1] - t[0]) / 20.0);
                FactionClash.LOGGER.info("GAMETEST npc walk speed = {} blocks/s", String.format("%.2f", speed));
                if (speed >= 3.7 && speed <= 5.0) {
                    h.succeed();
                } else {
                    h.fail("walk speed " + speed + " b/s is not player-equivalent (expected ~4.3)");
                }
            }
        });
    }

    /** Acceptance 7: equipped NPC gear never loses durability, while a vanilla mob's armour does. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void npcGearDoesNotWear(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        n.equip(new ItemStack(Items.IRON_CHESTPLATE));
        n.equip(new ItemStack(Items.IRON_SWORD));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
        z.setNoAi(true);
        for (int i = 0; i < 10; i++) {
            n.invulnerableTime = 0;
            n.hurt(h.getLevel().damageSources().mobAttack(z), 6.0F);
            n.setHealth(n.getMaxHealth());
            z.invulnerableTime = 0;
            n.doHurtTarget(z);
            z.setHealth(z.getMaxHealth());
        }
        h.assertTrue(n.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0, "NPC chestplate lost durability");
        h.assertTrue(n.getMainHandItem().getDamageValue() == 0, "NPC sword lost durability");
        h.succeed();
    }

    /** Acceptance 12: zombies, skeletons and variants deal half damage to NPCs; other sources do not. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void undeadDealHalfDamageOnly(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(12, 2, 3));
        Skeleton skel = h.spawn(EntityType.SKELETON, new BlockPos(12, 2, 6));
        Husk husk = h.spawn(EntityType.HUSK, new BlockPos(12, 2, 9));
        Cow cow = h.spawn(EntityType.COW, new BlockPos(12, 2, 12));
        for (var m : List.of(zombie, skel, husk, cow)) {
            m.setNoAi(true);
        }
        float[] loss = new float[4];
        var sources = List.of(zombie, skel, husk, cow);
        for (int i = 0; i < 4; i++) {
            n.setHealth(n.getMaxHealth());
            n.invulnerableTime = 0;
            float before = n.getHealth();
            n.hurt(h.getLevel().damageSources().mobAttack(sources.get(i)), 4.0F);
            loss[i] = before - n.getHealth();
        }
        h.assertTrue(Math.abs(loss[0] - 2.0F) < 0.01F, "zombie should deal 2, dealt " + loss[0]);
        h.assertTrue(Math.abs(loss[1] - 2.0F) < 0.01F, "skeleton should deal 2, dealt " + loss[1]);
        h.assertTrue(Math.abs(loss[2] - 2.0F) < 0.01F, "husk (zombie variant) should deal 2, dealt " + loss[2]);
        h.assertTrue(Math.abs(loss[3] - 4.0F) < 0.01F, "non-undead source should deal full 4, dealt " + loss[3]);
        h.succeed();
    }

    /** No friendly fire between same-faction NPCs; enemies still hurt. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void noFriendlyFire(GameTestHelper h) {
        FactionNpc a = npc(h, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
        FactionNpc ally = npc(h, 8, 5, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
        FactionNpc enemy = npc(h, 11, 5, FactionType.STONEGUARD, NpcRole.SWORDSMAN);
        float before = a.getHealth();
        a.hurt(h.getLevel().damageSources().mobAttack(ally), 5.0F);
        h.assertTrue(a.getHealth() == before, "ally damaged an ally");
        a.invulnerableTime = 0;
        a.hurt(h.getLevel().damageSources().mobAttack(enemy), 5.0F);
        h.assertTrue(a.getHealth() < before, "enemy damage was blocked");
        h.succeed();
    }

    /** Acceptance 9: one equipped arrow, never consumed, its special effect carried by every shot. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void oneArrowIsNeverConsumed(GameTestHelper h) {
        FactionNpc archer = npc(h, 3, 3, FactionType.EMBER_LEGION, NpcRole.ARCHER);
        archer.equip(new ItemStack(Items.BOW));
        archer.setAmmo(PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(12, 2, 12));
        pig.setNoAi(true);
        for (int i = 0; i < 5; i++) {
            archer.performRangedAttack(pig, 1.0F);
        }
        h.assertTrue(archer.ammo().getCount() == 1 && archer.ammo().is(Items.TIPPED_ARROW), "ammo changed: " + archer.ammo());
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(30);
        List<AbstractArrow> arrows = h.getLevel().getEntitiesOfClass(AbstractArrow.class, box);
        h.assertTrue(arrows.size() == 5, "expected 5 arrows, got " + arrows.size());
        for (AbstractArrow a : arrows) {
            h.assertTrue(a.pickup == AbstractArrow.Pickup.DISALLOWED, "arrow can be picked up (duplication)");
            PotionContents pc = a.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS);
            h.assertTrue(pc != null && pc.is(Potions.POISON), "shot lost the arrow's special effect");
        }
        h.succeed();
    }

    /** Reloads restore identity, inventory, ammo and affixes without stacking affix bonuses (spec 18, 20). */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void saveReloadKeepsIdentityWithoutStacking(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.STONEGUARD, NpcRole.ARCHER);
        n.affixes().put(Affix.VITALITY, 2);
        n.reapplyAffixes();
        n.reapplyAffixes();
        n.setAmmo(new ItemStack(Items.SPECTRAL_ARROW));
        n.inventory().setItem(0, new ItemStack(Items.RAW_IRON, 7));
        double hp = n.getAttributeValue(Attributes.MAX_HEALTH);
        h.assertTrue(Math.abs(hp - 24.0) < 1e-6, "vitality x2 should give 24 max health, got " + hp);
        CompoundTag tag = n.saveWithoutId(new CompoundTag());
        FactionNpc copy = new FactionNpc(ModEntities.FACTION_NPC.get(), h.getLevel());
        copy.load(tag);
        copy.load(tag);
        h.assertTrue(copy.getUUID().equals(n.getUUID()), "uuid changed");
        h.assertTrue(copy.getFaction() == FactionType.STONEGUARD && copy.getRole() == NpcRole.ARCHER, "identity changed");
        h.assertTrue(Math.abs(copy.getAttributeValue(Attributes.MAX_HEALTH) - 24.0) < 1e-6, "affix stacked on reload");
        h.assertTrue(copy.ammo().is(Items.SPECTRAL_ARROW) && copy.ammo().getCount() == 1, "ammo not restored");
        h.assertTrue(copy.inventory().getItem(0).is(Items.RAW_IRON) && copy.inventory().getItem(0).getCount() == 7, "inventory not restored");
        h.succeed();
    }

    /** Acceptance 11: a boxed-in NPC recovers with a small validated hop (<=2 across, <=1 up) and reaches its goal. */
    @GameTest(template = "platform", timeoutTicks = 400)
    public static void stuckNpcRecoversLocally(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(new BlockPos(5 + dx, 2, 5 + dz), Blocks.STONE);
                    h.setBlock(new BlockPos(5 + dx, 3, 5 + dz), Blocks.STONE);
                }
            }
        }
        FactionNpc n = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.BUILDER);
        BlockPos goal = h.absolutePos(new BlockPos(16, 2, 5));
        h.onEachTick(() -> {
            int before = n.navigator().recoveries;
            net.minecraft.world.phys.Vec3 from = n.position();
            var r = n.navigator().travel(goal, 1.5, 1.0);
            if (n.navigator().recoveries > before) {
                net.minecraft.world.phys.Vec3 to = n.position();
                h.assertTrue(Math.abs(Math.floor(to.x) - Math.floor(from.x)) <= 2 && Math.abs(Math.floor(to.z) - Math.floor(from.z)) <= 2
                        && Math.abs(Math.floor(to.y) - Math.floor(from.y)) <= 1, "recovery hop too large: " + from + " -> " + to);
                h.assertTrue(com.factionclash.npc.NpcNavigator.isSafeStandingSpot(h.getLevel(), n, n.blockPosition()),
                        "recovery landed on an unsafe spot");
            }
            if (r == com.factionclash.npc.NpcNavigator.Result.ARRIVED) {
                h.assertTrue(n.navigator().recoveries >= 1, "expected a recovery teleport");
                h.succeed();
            }
        });
    }

    /** Spec 14: NPCs jump backward from a priming creeper onto a safe spot. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void npcJumpsBackFromCreeper(GameTestHelper h) {
        FactionNpc n = npc(h, 10, 10, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        Creeper c = h.spawn(EntityType.CREEPER, new BlockPos(12, 2, 10));
        c.setNoAi(true);
        double start = n.distanceTo(c);
        h.runAfterDelay(2, c::ignite);
        h.runAfterDelay(20, () -> {
            double d = n.distanceTo(c);
            c.discard();
            h.assertTrue(d > start + 1.0, "NPC did not evade the creeper: " + start + " -> " + d);
            h.succeed();
        });
    }

    /** Acceptance 12: hostile vanilla mobs treat NPCs as valid targets. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void zombiesTargetNpcs(GameTestHelper h) {
        FactionNpc n = npc(h, 4, 4, FactionType.TIDE_ALLIANCE, NpcRole.CHEF);
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(12, 2, 12));
        h.succeedWhen(() -> h.assertTrue(z.getTarget() == n, "zombie has not targeted the NPC"));
    }

    /** Guards engage enemy faction NPCs on sight. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void guardsEngageEnemyNpcs(GameTestHelper h) {
        FactionNpc guard = npc(h, 3, 3, FactionType.GROVE_COVENANT, NpcRole.SWORDSMAN);
        guard.equip(new ItemStack(Items.IRON_SWORD));
        FactionNpc raider = npc(h, 14, 14, FactionType.TIDE_ALLIANCE, NpcRole.FARMER);
        h.succeedWhen(() -> h.assertTrue(guard.getTarget() == raider || !raider.isAlive(), "guard ignored an enemy NPC"));
    }

    /** The defence alarm sends guards at intruders they cannot see (e.g. raiders inside the heart's hall). */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void defenceAlarmAssignsGuardsWithoutSight(GameTestHelper h) {
        for (int x = 8; x <= 16; x++) {
            for (int y = 2; y <= 5; y++) {
                h.setBlock(new BlockPos(x, y, 8), Blocks.STONE); // wall blocking line of sight
            }
        }
        FactionNpc guard = npc(h, 12, 4, FactionType.STONEGUARD, NpcRole.SWORDSMAN);
        FactionNpc raider = npc(h, 12, 14, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        raider.setNoAi(true);
        BaseState b = new BaseState();
        b.faction = FactionType.STONEGUARD;
        b.center = h.absolutePos(new BlockPos(12, 2, 12));
        b.points.put(BaseState.HEART, h.absolutePos(new BlockPos(12, 2, 14)));
        int n = com.factionclash.campaign.FactionDirector.defenceAlarm(h.getLevel(), b);
        h.assertTrue(n >= 1 && guard.getTarget() == raider, "alarm did not assign the guard (assigned " + n + ")");
        h.succeed();
    }

    /** Player-equivalent break times and harvest rules. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void breakTimesMatchPlayerFormula(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.MINER);
        BlockPos p = h.absolutePos(new BlockPos(6, 2, 5));
        h.runAfterDelay(5, () -> {
            var stone = Blocks.STONE.defaultBlockState();
            int stonePick = com.factionclash.work.BlockWork.ticksToBreak(n, stone, p, new ItemStack(Items.STONE_PICKAXE));
            int bareHand = com.factionclash.work.BlockWork.ticksToBreak(n, stone, p, ItemStack.EMPTY);
            int ironPick = com.factionclash.work.BlockWork.ticksToBreak(n, stone, p, new ItemStack(Items.IRON_PICKAXE));
            // player (Minecraft wiki): stone takes 0.6 s with a stone pickaxe, 0.4 s with iron, 7.5 s by hand
            h.assertTrue(stonePick == 12, "stone pickaxe on stone took " + stonePick + " ticks (player: 12)");
            h.assertTrue(ironPick == 8, "iron pickaxe on stone took " + ironPick + " ticks (player: 8)");
            h.assertTrue(bareHand == 150, "bare hand on stone took " + bareHand + " ticks (player: 150)");
            h.assertTrue(!com.factionclash.work.BlockWork.canHarvest(Blocks.IRON_ORE.defaultBlockState(), new ItemStack(Items.WOODEN_PICKAXE)),
                    "a wooden pickaxe must not harvest iron ore");
            h.assertTrue(com.factionclash.work.BlockWork.canHarvest(Blocks.IRON_ORE.defaultBlockState(), new ItemStack(Items.STONE_PICKAXE)),
                    "a stone pickaxe harvests iron ore");
            h.succeed();
        });
    }

    /** Mining an ore over time drops its real loot only with the right pickaxe, and the NPC picks it up. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void realMiningDropsAndPickup(GameTestHelper h) {
        BlockPos good = new BlockPos(6, 2, 5), bad = new BlockPos(6, 2, 12);
        h.setBlock(good, Blocks.IRON_ORE);
        h.setBlock(bad, Blocks.IRON_ORE);
        FactionNpc stone = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.MINER);
        stone.equip(new ItemStack(Items.STONE_PICKAXE));
        FactionNpc wood = npc(h, 5, 12, FactionType.EMBER_LEGION, NpcRole.MINER);
        wood.equip(new ItemStack(Items.WOODEN_PICKAXE));
        var b1 = new com.factionclash.work.BlockWork.Breaker();
        var b2 = new com.factionclash.work.BlockWork.Breaker();
        BlockPos g = h.absolutePos(good), w = h.absolutePos(bad);
        boolean[] done = {false, false};
        long[] brokeAt = {-1};
        h.onEachTick(() -> {
            if (!done[0] && b1.tick(stone, g)) {
                done[0] = true;
                brokeAt[0] = h.getTick();
            }
            if (!done[1] && b2.tick(wood, w)) {
                done[1] = true;
            }
            com.factionclash.work.BlockWork.pickUp(stone, x -> true);
            com.factionclash.work.BlockWork.pickUp(wood, x -> true);
            if (done[0]) {
                // the drop pops out at random like a player's: walk over to it, as the jobs do
                var item = com.factionclash.work.BlockWork.nearestItem(stone, new net.minecraft.world.phys.AABB(g).inflate(3), x -> true);
                if (item != null) {
                    stone.getNavigation().moveTo(item, 1.0);
                }
            }
            if (done[0] && done[1] && h.getTick() > brokeAt[0] + 60) {
                h.assertTrue(com.factionclash.work.Inv.count(stone.inventory(), x -> x.is(Items.RAW_IRON)) >= 1,
                        "stone-pickaxe miner did not get raw iron");
                h.assertTrue(com.factionclash.work.Inv.count(wood.inventory(), x -> x.is(Items.RAW_IRON)) == 0,
                        "wooden-pickaxe miner got raw iron");
                h.assertTrue(h.getLevel().getBlockState(g).isAir() && h.getLevel().getBlockState(w).isAir(), "ore not removed");
                h.succeed();
            }
        });
    }

    /**
     * Cave explorers note only ore they can really see: an ore in the corridor ceiling counts; an ore buried in the
     * rock, and an ore exposed in a sealed pocket behind a wall, do not.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void explorerSeesOnlyVisibleOre(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(1, 2, 1, 10, 5, 5)) {
            h.setBlock(p, Blocks.STONE);
        }
        for (int x = 2; x <= 9; x++) {
            h.setBlock(new BlockPos(x, 2, 2), Blocks.AIR);
            h.setBlock(new BlockPos(x, 3, 2), Blocks.AIR);
        }
        BlockPos visible = new BlockPos(6, 4, 2), buried = new BlockPos(6, 2, 4), sealed = new BlockPos(8, 2, 4);
        h.setBlock(visible, Blocks.IRON_ORE);
        h.setBlock(buried, Blocks.COAL_ORE);
        h.setBlock(sealed, Blocks.DIAMOND_ORE);
        h.setBlock(new BlockPos(8, 3, 4), Blocks.AIR); // a pocket above the diamond, walled off from the corridor
        FactionNpc explorer = npc(h, 2, 2, FactionType.STONEGUARD, NpcRole.CAVE_EXPLORER);
        h.runAfterDelay(5, () -> {
            List<BlockPos> seen = com.factionclash.work.CaveWork.visibleOres(h.getLevel(), explorer);
            h.assertTrue(seen.contains(h.absolutePos(visible)), "the ore in plain sight was not noticed: " + seen);
            h.assertFalse(seen.contains(h.absolutePos(buried)), "an ore buried in rock was noticed");
            h.assertFalse(seen.contains(h.absolutePos(sealed)), "an ore behind a wall was noticed");
            h.succeed();
        });
    }

    /** Explorers light caves with torches from their backpack: one torch per placement, only where it can stand. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void explorerPlacesRealTorches(GameTestHelper h) {
        FactionNpc explorer = npc(h, 3, 3, FactionType.TIDE_ALLIANCE, NpcRole.CAVE_EXPLORER);
        explorer.inventory().setItem(0, new ItemStack(Items.TORCH, 2));
        BlockPos floor = h.absolutePos(new BlockPos(5, 2, 5));
        h.assertTrue(com.factionclash.work.CaveWork.placeTorch(explorer, floor), "torch not placed on solid floor");
        h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.TORCH), "no torch block in the world");
        h.assertFalse(com.factionclash.work.CaveWork.placeTorch(explorer, floor.above()), "a torch was placed in mid-air");
        h.assertTrue(com.factionclash.work.Inv.count(explorer.inventory(), x -> x.is(Items.TORCH)) == 1,
                "exactly one torch should have been used");
        h.succeed();
    }

    /** Feeding two adult cows wheat like a player makes them breed a real calf; babies cannot be fed to breed. */
    @GameTest(template = "platform", timeoutTicks = 400)
    public static void carerBreedsAnimalsWithFood(GameTestHelper h) {
        FactionNpc carer = npc(h, 3, 3, FactionType.GROVE_COVENANT, NpcRole.ANIMAL_CARER);
        carer.inventory().setItem(0, new ItemStack(Items.WHEAT, 4));
        net.minecraft.world.entity.animal.Cow a = h.spawn(EntityType.COW, new BlockPos(10, 2, 10));
        net.minecraft.world.entity.animal.Cow b = h.spawn(EntityType.COW, new BlockPos(11, 2, 10));
        net.minecraft.world.entity.animal.Cow calf = h.spawn(EntityType.COW, new BlockPos(13, 2, 10));
        calf.setAge(-24000);
        h.assertTrue(!com.factionclash.work.AnimalCarerJob.feedLikePlayer(carer, calf), "fed a calf to breed");
        h.assertTrue(com.factionclash.work.AnimalCarerJob.feedLikePlayer(carer, a), "could not feed adult cow");
        h.assertTrue(com.factionclash.work.AnimalCarerJob.feedLikePlayer(carer, b), "could not feed second cow");
        h.assertTrue(com.factionclash.work.Inv.count(carer.inventory(), x -> x.is(Items.WHEAT)) == 2, "wheat not consumed");
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(30);
        h.succeedWhen(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, box,
                c -> c.isBaby() && c != calf).size() >= 1, "no calf was born"));
    }

    /** The butcher never culls babies, breeding animals, or below the breeding stock. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void butcherSparesBabiesAndBreedingStock(GameTestHelper h) {
        net.minecraft.world.entity.animal.Cow adult = h.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        net.minecraft.world.entity.animal.Cow baby = h.spawn(EntityType.COW, new BlockPos(7, 2, 5));
        net.minecraft.world.entity.animal.Cow inLove = h.spawn(EntityType.COW, new BlockPos(9, 2, 5));
        baby.setAge(-24000);
        inLove.setInLove(null);
        int stock = com.factionclash.work.ButcherJob.BREEDING_STOCK;
        h.assertTrue(com.factionclash.work.ButcherJob.canCull(adult, stock + 1), "surplus adult should be cullable");
        h.assertTrue(!com.factionclash.work.ButcherJob.canCull(adult, stock), "culled into the breeding stock");
        h.assertTrue(!com.factionclash.work.ButcherJob.canCull(baby, stock + 5), "a baby was cullable");
        h.assertTrue(!com.factionclash.work.ButcherJob.canCull(inLove, stock + 5), "a breeding animal was cullable");
        h.succeed();
    }

    private static BaseState fakeBase(GameTestHelper h, FactionType f) {
        BaseState b = new BaseState();
        b.id = 900000 + h.getLevel().random.nextInt(10000);
        b.faction = f;
        b.center = h.absolutePos(new BlockPos(12, 2, 12));
        b.points.put(BaseState.SPAWN, h.absolutePos(new BlockPos(12, 2, 12)));
        for (int i = 0; i < 4; i++) {
            BlockPos p = new BlockPos(2 + i, 2, 2);
            h.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.SOUTH));
            b.storage(i == 0 ? StorageCategory.FOOD : StorageCategory.GENERAL).add(h.absolutePos(p));
        }
        return b;
    }

    /** Spec 5/10: a full warehouse never deletes cargo; the remainder is returned to the carrier. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void fullStorageNeverDeletesItems(GameTestHelper h) {
        BaseState b = fakeBase(h, FactionType.EMBER_LEGION);
        for (int i = 0; i < 4 * 27; i++) {
            FactionStorage.insert(h.getLevel(), b, new ItemStack(Items.DIRT, 64));
        }
        int dirt = FactionStorage.count(h.getLevel(), b, s -> s.is(Items.DIRT));
        h.assertTrue(dirt == 4 * 27 * 64, "storage not full as expected: " + dirt);
        ItemStack rest = FactionStorage.insert(h.getLevel(), b, new ItemStack(Items.BREAD, 10));
        h.assertTrue(rest.getCount() == 10 && rest.is(Items.BREAD), "bread was lost: remainder " + rest);
        FactionStorage.extract(h.getLevel(), b, s -> s.is(Items.DIRT), 64);
        ItemStack rest2 = FactionStorage.insert(h.getLevel(), b, rest);
        h.assertTrue(rest2.isEmpty(), "bread did not fit after space was freed");
        h.assertTrue(FactionStorage.count(h.getLevel(), b, s -> s.is(Items.BREAD)) == 10, "bread count wrong");
        h.succeed();
    }

    /** Acceptance 8: recruitment charges nutrition-weighted food and exactly one equipment payment route. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void recruitmentChargesFoodAndOneEquipmentRoute(GameTestHelper h) {
        var level = h.getLevel();
        CampaignData data = CampaignData.get(level.getServer());
        BaseState b = fakeBase(h, FactionType.GROVE_COVENANT);
        // 1) no food -> fails and charges nothing
        FactionStorage.insert(level, b, new ItemStack(Items.COBBLESTONE, 3));
        FactionStorage.insert(level, b, new ItemStack(Items.STICK, 2));
        Recruitment.Result r0 = Recruitment.recruit(level, data, b, NpcRole.MINER, "test");
        h.assertTrue(!r0.ok(), "recruited without food");
        h.assertTrue(FactionStorage.count(level, b, s -> s.is(Items.COBBLESTONE)) == 3, "failed recruit charged materials");
        // 2) food + materials only -> materials charged, basic pickaxe created
        FactionStorage.insert(level, b, new ItemStack(Items.COOKED_BEEF, 2)); // 16
        FactionStorage.insert(level, b, new ItemStack(Items.BREAD, 2));       // 10
        int foodBefore = Recruitment.storedFoodValue(level, b);
        Recruitment.Result r1 = Recruitment.recruit(level, data, b, NpcRole.MINER, "test");
        h.assertTrue(r1.ok(), "recruit failed: " + r1.message());
        int paid = foodBefore - Recruitment.storedFoodValue(level, b);
        h.assertTrue(paid >= 20 && paid <= 21, "food paid " + paid + " (bill 20, nutrition-weighted)");
        h.assertTrue(FactionStorage.count(level, b, s -> s.is(Items.COBBLESTONE)) == 0
                && FactionStorage.count(level, b, s -> s.is(Items.STICK)) == 0, "pickaxe materials not charged");
        h.assertTrue(r1.npc().getMainHandItem().is(Items.STONE_PICKAXE), "recruit lacks its pickaxe");
        // 3) finished item stored -> the item is charged and the materials are left alone
        FactionStorage.insert(level, b, new ItemStack(Items.IRON_PICKAXE));
        FactionStorage.insert(level, b, new ItemStack(Items.COBBLESTONE, 3));
        FactionStorage.insert(level, b, new ItemStack(Items.STICK, 2));
        FactionStorage.insert(level, b, new ItemStack(Items.BREAD, 4));
        Recruitment.Result r2 = Recruitment.recruit(level, data, b, NpcRole.MINER, "test");
        h.assertTrue(r2.ok(), "second recruit failed: " + r2.message());
        h.assertTrue(r2.npc().getMainHandItem().is(Items.IRON_PICKAXE), "better stored gear not supplied");
        h.assertTrue(FactionStorage.count(level, b, s -> s.is(Items.IRON_PICKAXE)) == 0, "finished pickaxe not charged");
        h.assertTrue(FactionStorage.count(level, b, s -> s.is(Items.COBBLESTONE)) == 3
                && FactionStorage.count(level, b, s -> s.is(Items.STICK)) == 2, "double-charged materials as well as the item");
        r1.npc().discard();
        r2.npc().discard();
        data.faction(FactionType.GROVE_COVENANT).roster.remove(r1.npc().getUUID());
        data.faction(FactionType.GROVE_COVENANT).roster.remove(r2.npc().getUUID());
        h.succeed();
    }
}
