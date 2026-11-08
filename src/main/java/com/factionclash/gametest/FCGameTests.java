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
    /**
     * Gear wears like a player's: a sword loses 1 per hit, armour 1 per piece per hit (damage / 4, at least 1), a
     * pickaxe 1 per block mined and a sword 2 per block, a bow 1 per shot; what an equip replaces goes to the backpack.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void npcGearWearsLikeAPlayers(GameTestHelper h) {
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
        h.assertTrue(n.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 10, "chestplate wear " + n.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() + " (player: 10)");
        h.assertTrue(n.getMainHandItem().getDamageValue() == 10, "sword wear " + n.getMainHandItem().getDamageValue() + " (player: 10)");
        BlockPos stone = new BlockPos(7, 2, 7);
        h.setBlock(stone, Blocks.STONE);
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(stone), x -> true);
        h.assertTrue(n.getMainHandItem().getDamageValue() == 12, "a sword mining a block should lose 2, wear " + n.getMainHandItem().getDamageValue());
        n.equip(new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.IRON_SWORD)) == 1, "the replaced sword went missing");
        h.setBlock(stone, Blocks.STONE);
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(stone), x -> true);
        h.assertTrue(n.getMainHandItem().getDamageValue() == 1, "a pickaxe should lose 1 per block, wear " + n.getMainHandItem().getDamageValue());
        ItemStack almost = new ItemStack(Items.STONE_PICKAXE);
        almost.setDamageValue(almost.getMaxDamage() - 1);
        n.equip(almost);
        h.setBlock(stone, Blocks.STONE);
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(stone), x -> true);
        h.assertTrue(n.getMainHandItem().isEmpty(), "a used-up pickaxe should break");
        h.succeed();
    }

    /**
     * Anvil repair with the vanilla rules: each unit of material restores a quarter of the durability; only gear worth
     * it is repaired (enchanted, or iron/diamond gear needing less material than a new one).
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void anvilRepairFollowsVanillaRules(GameTestHelper h) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        int quarter = pick.getMaxDamage() / 4;
        pick.setDamageValue(quarter * 2 + 10); // a bit over half used up: three units of diamond
        net.minecraft.world.SimpleContainer inv = new net.minecraft.world.SimpleContainer(4);
        inv.setItem(0, new ItemStack(Items.DIAMOND, 5));
        int[] used = {0};
        com.factionclash.work.BlacksmithJob.repairWithMaterial(pick, inv, used);
        h.assertTrue(used[0] == 3, "expected 3 diamonds, used " + used[0]);
        h.assertTrue(pick.getDamageValue() == 0, "not fully repaired: " + pick.getDamageValue());
        h.assertTrue(inv.getItem(0).getCount() == 2, "diamonds left " + inv.getItem(0).getCount());
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.setDamageValue(chest.getMaxDamage() / 2);
        h.assertTrue(com.factionclash.work.BlacksmithJob.worthRepairing(chest), "a half-worn iron chestplate (2 ingots vs 8) is worth it");
        ItemStack plainPick = new ItemStack(Items.IRON_PICKAXE);
        plainPick.setDamageValue(plainPick.getMaxDamage() - 5);
        h.assertFalse(com.factionclash.work.BlacksmithJob.worthRepairing(plainPick), "4 ingots to repair a plain iron pickaxe that costs 3");
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        stone.setDamageValue(stone.getMaxDamage() / 2);
        h.assertFalse(com.factionclash.work.BlacksmithJob.worthRepairing(stone), "stone tools are not worth the anvil");
        ItemStack tooExpensive = new ItemStack(Items.IRON_CHESTPLATE);
        tooExpensive.setDamageValue(tooExpensive.getMaxDamage() / 2);
        tooExpensive.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 39);
        h.assertFalse(com.factionclash.work.BlacksmithJob.worthRepairing(tooExpensive), "past 40 levels it is Too Expensive");
        h.succeed();
    }

    /**
     * Faction advancements follow the game's own advancements: one criterion is enough for "Stone Age", any one iron
     * piece for "Suit Up", but "A Balanced Diet" needs every food; a diamond mined by an NPC makes "Diamonds!".
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void factionAdvancementsFollowVanillaRequirements(GameTestHelper h) {
        var server = h.getLevel().getServer();
        com.factionclash.campaign.CampaignData data = com.factionclash.campaign.CampaignData.get(server);
        boolean wasStarted = data.started;
        data.started = true;
        try {
            com.factionclash.campaign.FactionState f = data.faction(FactionType.TIDE_ALLIANCE);
            f.achievements.clear();
            f.advancementProgress.clear();
            com.factionclash.achieve.FactionAchievements.obtained(server, FactionType.TIDE_ALLIANCE, new ItemStack(Items.COBBLESTONE), "Tester");
            h.assertTrue(f.achievements.containsKey("minecraft:story/mine_stone"), "Stone Age not made");
            h.assertTrue(f.achievements.get("minecraft:story/mine_stone").by().equals("Tester"), "who made it is not recorded");
            com.factionclash.achieve.FactionAchievements.obtained(server, FactionType.TIDE_ALLIANCE, new ItemStack(Items.IRON_BOOTS), "Tester");
            h.assertTrue(f.achievements.containsKey("minecraft:story/obtain_armor"), "one iron piece should make Suit Up");
            com.factionclash.achieve.FactionAchievements.ate(server, FactionType.TIDE_ALLIANCE, new ItemStack(Items.BREAD), "Tester");
            h.assertTrue(f.achievements.containsKey("minecraft:husbandry/root"), "eating should make Husbandry");
            h.assertFalse(f.achievements.containsKey("minecraft:husbandry/balanced_diet"), "one bread is not a balanced diet");
            h.assertTrue(f.advancementProgress.get("minecraft:husbandry/balanced_diet").contains("bread"), "diet progress not kept");
            FactionNpc miner = npc(h, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.MINER);
            miner.equip(new ItemStack(Items.IRON_PICKAXE));
            h.setBlock(new BlockPos(6, 2, 5), Blocks.DIAMOND_ORE);
            com.factionclash.work.BlockWork.breakNow(miner, h.absolutePos(new BlockPos(6, 2, 5)), x -> true);
            h.assertTrue(f.achievements.containsKey("minecraft:story/mine_diamond"), "an NPC's diamond should make Diamonds!");
            h.assertTrue(f.achievements.get("minecraft:story/mine_diamond").by().equals(miner.npcName()), "the miner should be credited");
            f.achievements.clear();
            f.advancementProgress.clear();
        } finally {
            data.started = wasStarted;
        }
        h.succeed();
    }

    /**
     * Logistics: the quartermaster samples the storehouse, keeps the trend, and puts a base on rationing when its food
     * falls below a day's meals for its people; recruitment keeps that day's meals in reserve.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void logisticsRationsFoodWhenShort(GameTestHelper h) {
        CampaignData data = CampaignData.get(h.getLevel().getServer());
        BaseState b = fakeBase(h, FactionType.GROVE_COVENANT);
        data.bases.put(b.id, b);
        var roster = data.faction(FactionType.GROVE_COVENANT).roster;
        List<java.util.UUID> people = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            com.factionclash.campaign.NpcRecord r = new com.factionclash.campaign.NpcRecord();
            r.uuid = java.util.UUID.randomUUID();
            r.role = NpcRole.FARMER;
            r.baseId = b.id;
            roster.put(r.uuid, r);
            people.add(r.uuid);
        }
        try {
            net.minecraft.world.Container food = FactionStorage.container(h.getLevel(), b.storage(StorageCategory.FOOD).get(0));
            food.setItem(0, new ItemStack(Items.BREAD, 20)); // 100 hunger points for 5 people needing 40 a day
            com.factionclash.campaign.Logistics.tick(h.getLevel(), data, b, 100_000);
            h.assertFalse(com.factionclash.campaign.Logistics.rationing(b), "rationing with 2.5 days of food in store");
            h.assertTrue(com.factionclash.campaign.Logistics.foodReserve(data, b) == 40, "reserve should be a day's meals for 5");
            food.setItem(0, new ItemStack(Items.BREAD, 6)); // 30 points: less than a day
            com.factionclash.campaign.Logistics.tick(h.getLevel(), data, b, 103_000);
            h.assertTrue(com.factionclash.campaign.Logistics.rationing(b), "no rationing below a day's food");
            var l = com.factionclash.campaign.Logistics.ledger(b, com.factionclash.campaign.Logistics.Resource.FOOD);
            h.assertTrue(l.latest() == 30 && l.perDay() < 0, "the ledger should show the fall: " + l.latest() + " " + l.perDay());
            food.setItem(0, new ItemStack(Items.BREAD, 40));
            com.factionclash.campaign.Logistics.tick(h.getLevel(), data, b, 106_000);
            h.assertFalse(com.factionclash.campaign.Logistics.rationing(b), "rationing should end when food is back");
        } finally {
            data.bases.remove(b.id);
            people.forEach(roster::remove);
        }
        h.succeed();
    }

    /** Melee like a player's: a hit while falling is a critical (x1.5), a sword hit on the ground sweeps enemies but not animals. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void criticalHitsAndSwordSweeps(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        n.equip(new ItemStack(Items.IRON_SWORD));
        Zombie a = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
        Zombie b = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        Cow cow = h.spawn(EntityType.COW, new BlockPos(6, 2, 6));
        for (var m : List.of(a, b, cow)) {
            m.setNoAi(true);
        }
        h.runAfterDelay(3, () -> {
            float before = a.getHealth();
            n.setOnGround(true);
            n.fallDistance = 0;
            n.doHurtTarget(a);
            float ground = before - a.getHealth();
            h.assertTrue(b.getHealth() < b.getMaxHealth(), "the sweep missed the zombie next to the target");
            h.assertTrue(cow.getHealth() == cow.getMaxHealth(), "the sweep hit the cow");
            a.setHealth(a.getMaxHealth());
            a.invulnerableTime = 0;
            n.setOnGround(false);
            n.fallDistance = 1.0F;
            n.doHurtTarget(a);
            float crit = a.getMaxHealth() - a.getHealth();
            h.assertTrue(crit > ground * 1.4F, "a falling hit should be a critical: ground " + ground + ", falling " + crit);
            h.succeed();
        });
    }

    /** A swordsman raises its shield against an archer; an axe knocks it aside for a while. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void swordsmanBlocksWithShieldAndAxeDisablesIt(GameTestHelper h) {
        FactionNpc n = npc(h, 4, 10, FactionType.STONEGUARD, NpcRole.SWORDSMAN);
        n.equip(new ItemStack(Items.IRON_SWORD));
        n.equip(new ItemStack(Items.SHIELD));
        h.assertTrue(n.hasShield() && n.getMainHandItem().is(Items.IRON_SWORD), "sword and shield not both held");
        Skeleton archer = h.spawn(EntityType.SKELETON, new BlockPos(16, 2, 10));
        archer.setNoAi(true);
        n.setTarget(archer);
        h.runAfterDelay(15, () -> {
            h.assertTrue(n.isBlocking(), "the shield should be up against an archer");
            Zombie axeman = h.spawn(EntityType.ZOMBIE, h.absolutePos(new BlockPos(4, 2, 10)).offset(0, 0, 0).east(1).east(0).subtract(h.absolutePos(BlockPos.ZERO)));
            axeman.setNoAi(true);
            axeman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            n.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, axeman.position());
            n.setYBodyRot(n.getYHeadRot());
            float hp = n.getHealth();
            axeman.doHurtTarget(n);
            h.assertTrue(n.getHealth() == hp, "the shield did not stop the blow");
            h.assertFalse(n.isUsingItem(), "an axe should knock the shield aside");
            h.succeed();
        });
    }

    /** A badly hurt soldier with no golden apple falls back from its attacker; an archer backs away from a melee enemy. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void woundedSoldiersFallBackAndArchersKite(GameTestHelper h) {
        FactionNpc wounded = npc(h, 8, 4, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
        Zombie z1 = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 2));
        z1.setNoAi(true);
        wounded.setHealth(4.0F);
        wounded.setTarget(z1);
        FactionNpc archer = npc(h, 16, 16, FactionType.TIDE_ALLIANCE, NpcRole.ARCHER);
        archer.equip(new ItemStack(Items.BOW));
        archer.setAmmo(new ItemStack(Items.ARROW));
        Zombie z2 = h.spawn(EntityType.ZOMBIE, new BlockPos(16, 2, 13));
        z2.setNoAi(true);
        archer.setTarget(z2);
        double w0 = wounded.distanceTo(z1), a0 = archer.distanceTo(z2);
        h.runAfterDelay(100, () -> {
            h.assertTrue(wounded.distanceTo(z1) > w0 + 3, "the wounded soldier did not fall back: " + w0 + " -> " + wounded.distanceTo(z1));
            h.assertTrue(archer.distanceTo(z2) > a0 + 2, "the archer did not back away: " + a0 + " -> " + archer.distanceTo(z2));
            h.succeed();
        });
    }

    /** A hungry NPC eats the best-fitting safe food it carries, like a player; rotten flesh only when starving. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void hungryNpcEatsFromItsBackpack(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        n.hunger().set(10, 0, 0);
        n.inventory().setItem(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        n.inventory().setItem(1, new ItemStack(Items.BREAD, 2));
        FactionNpc starving = npc(h, 9, 9, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        starving.hunger().set(3, 0, 0);
        starving.inventory().setItem(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        FactionNpc picky = npc(h, 12, 12, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        picky.hunger().set(10, 0, 0);
        picky.inventory().setItem(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        FactionNpc wounded = npc(h, 16, 5, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        wounded.hunger().set(16, 0, 0);
        wounded.setHealth(2);
        wounded.inventory().setItem(0, new ItemStack(Items.COOKED_BEEF, 2));
        FactionNpc unhurt = npc(h, 16, 12, FactionType.GROVE_COVENANT, NpcRole.FARMER);
        unhurt.hunger().set(16, 0, 0);
        unhurt.inventory().setItem(0, new ItemStack(Items.COOKED_BEEF, 2));
        h.runAfterDelay(80, () -> {
            h.assertTrue(wounded.hunger().foodLevel() == 20, "a badly hurt NPC eats to heal again, food " + wounded.hunger().foodLevel());
            h.assertTrue(unhurt.hunger().foodLevel() == 16, "a healthy NPC wasted a steak at 16 food");
            h.assertTrue(n.hunger().foodLevel() == 15, "bread should bring 10 to 15, food " + n.hunger().foodLevel());
            h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.BREAD)) == 1, "one bread eaten");
            h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.ROTTEN_FLESH)) == 3, "rotten flesh eaten while bread was there");
            h.assertTrue(starving.hunger().foodLevel() > 3, "a starving NPC eats rotten flesh when it is all it has");
            h.assertTrue(picky.hunger().foodLevel() == 10, "rotten flesh eaten without need");
            h.succeed();
        });
    }

    /** Healing comes from food: a fed NPC regenerates, a hungry one (below 18) does not. */
    @GameTest(template = "platform", timeoutTicks = 260)
    public static void onlyFedNpcsHeal(GameTestHelper h) {
        FactionNpc fed = npc(h, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.BUILDER);
        FactionNpc hungry = npc(h, 9, 5, FactionType.TIDE_ALLIANCE, NpcRole.BUILDER);
        fed.hunger().set(20, 10, 0);
        hungry.hunger().set(12, 0, 0);
        fed.setHealth(10);
        hungry.setHealth(10);
        h.runAfterDelay(220, () -> {
            h.assertTrue(fed.getHealth() > 14, "a fed NPC should heal, health " + fed.getHealth());
            h.assertTrue(hungry.getHealth() <= 10.01F, "a hungry NPC healed, health " + hungry.getHealth());
            h.succeed();
        });
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

    /**
     * A crossbowman charges and fires on its own at a target, with its one arrow never used up; bolts fly faster
     * than a bow's arrows.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void crossbowmanChargesAndFires(GameTestHelper h) {
        FactionNpc xbow = npc(h, 3, 3, FactionType.TIDE_ALLIANCE, NpcRole.CROSSBOWMAN);
        xbow.equip(new ItemStack(Items.CROSSBOW));
        xbow.setAmmo(new ItemStack(Items.ARROW));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(12, 2, 12));
        z.setNoAi(true);
        xbow.setTarget(z);
        h.succeedWhen(() -> {
            AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(30);
            List<AbstractArrow> arrows = h.getLevel().getEntitiesOfClass(AbstractArrow.class, box);
            h.assertTrue(!arrows.isEmpty(), "no bolt fired yet");
            h.assertTrue(xbow.ammo().getCount() == 1, "the arrow was used up");
            h.assertTrue(arrows.get(0).getOwner() == xbow, "bolt not fired by the crossbowman");
        });
    }

    /** A badly hurt NPC in a fight eats its golden apple like a player (32 ticks) and gets the apple's effects. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void hurtSoldierEatsGoldenApple(GameTestHelper h) {
        FactionNpc s = npc(h, 3, 3, FactionType.GROVE_COVENANT, NpcRole.SWORDSMAN);
        s.inventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 1));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(13, 2, 13));
        z.setNoAi(true);
        s.setTarget(z);
        s.setHealth(6.0F);
        h.runAfterDelay(20, () -> h.assertTrue(com.factionclash.work.Inv.count(s.inventory(), x -> x.is(Items.GOLDEN_APPLE)) == 1,
                "the apple must take a player's eating time, not be instant"));
        h.succeedWhen(() -> {
            h.assertTrue(s.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION), "no absorption");
            h.assertTrue(s.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "no regeneration");
            h.assertTrue(com.factionclash.work.Inv.count(s.inventory(), x -> x.is(Items.GOLDEN_APPLE)) == 0, "apple not used up");
        });
    }

    /** Weapons set the attack speed exactly as for a player: sword 1.6, iron axe 0.9, bare hands 4.0. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void weaponsSetPlayerAttackSpeed(GameTestHelper h) {
        FactionNpc sword = npc(h, 3, 3, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        FactionNpc axe = npc(h, 6, 3, FactionType.EMBER_LEGION, NpcRole.AXE_FIGHTER);
        FactionNpc bare = npc(h, 9, 3, FactionType.EMBER_LEGION, NpcRole.CHEF);
        sword.equip(new ItemStack(Items.IRON_SWORD));
        axe.equip(new ItemStack(Items.IRON_AXE));
        h.runAfterDelay(2, () -> {
            double s = sword.getAttributeValue(Attributes.ATTACK_SPEED), a = axe.getAttributeValue(Attributes.ATTACK_SPEED);
            double b = bare.getAttributeValue(Attributes.ATTACK_SPEED);
            h.assertTrue(Math.abs(s - 1.6) < 0.01, "sword attack speed " + s);
            h.assertTrue(Math.abs(a - 0.9) < 0.01, "iron axe attack speed " + a);
            h.assertTrue(Math.abs(b - 4.0) < 0.01, "bare-hand attack speed " + b);
            h.assertTrue(axe.getAttributeValue(Attributes.ATTACK_DAMAGE) > sword.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    "an axe should hit harder than a sword");
            h.succeed();
        });
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
                int maxH = com.factionclash.config.FCConfig.UNSTUCK_HORIZONTAL.get(), maxV = com.factionclash.config.FCConfig.UNSTUCK_VERTICAL.get();
                h.assertTrue(Math.abs(Math.floor(to.x) - Math.floor(from.x)) <= maxH && Math.abs(Math.floor(to.z) - Math.floor(from.z)) <= maxH
                        && Math.abs(Math.floor(to.y) - Math.floor(from.y)) <= maxV, "recovery hop too large: " + from + " -> " + to);
                h.assertTrue(com.factionclash.npc.NpcNavigator.isSafeStandingSpot(h.getLevel(), n, n.blockPosition()),
                        "recovery landed on an unsafe spot");
            }
            if (r == com.factionclash.npc.NpcNavigator.Result.ARRIVED) {
                h.assertTrue(n.navigator().recoveries >= 1, "expected a recovery teleport");
                h.succeed();
            }
        });
    }

    /** Shut in rock with no route at all, an NPC digs a staircase up, step by step, until it is out on top. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void sealedNpcDigsItselfOut(GameTestHelper h) {
        // (the test area has a barrier ceiling, so "out in the open" here means standing on top of the rock)
        for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 8, 4, 8)) {
            h.setBlock(p, Blocks.STONE);
        }
        h.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
        h.setBlock(new BlockPos(5, 3, 5), Blocks.AIR);
        FactionNpc n = npc(h, 5, 5, FactionType.GROVE_COVENANT, NpcRole.SCOUT);
        BlockPos goal = h.absolutePos(new BlockPos(16, 7, 5)); // somewhere up the hill
        int top = h.absolutePos(new BlockPos(5, 5, 5)).getY();
        int[] steps = {0};
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0 || !n.onGround()) {
                return;
            }
            if (n.getBlockY() >= top) {
                h.assertTrue(steps[0] >= 3 && steps[0] <= 8, "unexpected number of dig steps: " + steps[0]);
                h.assertTrue(com.factionclash.npc.NpcNavigator.isSafeStandingSpot(h.getLevel(), n, n.blockPosition()), "unsafe spot");
                h.succeed();
                return;
            }
            h.assertFalse(com.factionclash.npc.DigOut.free(n), "inside the rock should count as shut in");
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "no step could be dug at " + n.blockPosition());
            steps[0]++;
        });
    }

    /** Stranded on a tree canopy, an NPC breaks the leaves underfoot and drops down, as a player would. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void npcOnCanopyDropsDown(GameTestHelper h) {
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (BlockPos p : BlockPos.betweenClosed(11, 4, 11, 13, 4, 13)) {
            h.setBlock(p, leaves);
        }
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(12, 5, 12));
        n.faction(FactionType.GROVE_COVENANT);
        n.role(NpcRole.SCOUT);
        h.runAfterDelay(5, () -> {
            h.assertFalse(com.factionclash.npc.DigOut.free(n), "standing on leaves should count as stranded");
            h.assertTrue(com.factionclash.npc.DigOut.step(n, h.absolutePos(new BlockPos(2, 2, 2))), "no way down was dug");
            h.assertTrue(h.getBlockState(new BlockPos(12, 4, 12)).isAir(), "the leaves underfoot are still there");
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(n.getY() < h.absolutePos(new BlockPos(12, 4, 12)).getY(), "it did not drop down: y=" + n.getY());
            h.succeed();
        });
    }

    /**
     * The enchanter at a real table with bookshelves: it pays levels and lapis the vanilla way and gets vanilla
     * enchantments on the sword; without enough levels nothing happens.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void enchanterUsesTheTableWithLevelsAndLapis(GameTestHelper h) {
        BlockPos table = new BlockPos(8, 2, 8);
        h.setBlock(table, Blocks.ENCHANTING_TABLE);
        for (int x = 6; x <= 10; x++) {
            h.setBlock(new BlockPos(x, 2, 6), Blocks.BOOKSHELF);
            h.setBlock(new BlockPos(x, 3, 6), Blocks.BOOKSHELF);
        }
        BlockPos abs = h.absolutePos(table);
        h.assertTrue(com.factionclash.work.EnchanterJob.bookshelves(h.getLevel(), abs) == 10, "expected 10 bookshelves of power");
        FactionNpc n = npc(h, 8, 10, FactionType.EMBER_LEGION, NpcRole.ENCHANTER);
        n.inventory().setItem(0, new ItemStack(Items.LAPIS_LAZULI, 10));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        long[] xp = {com.factionclash.logic.XpLevels.pointsFor(2)};
        // level 2 is below every offer a 10-bookshelf table makes for the third slot, but it may afford the first
        int poorSlot = com.factionclash.work.EnchanterJob.enchantOnce(h.getLevel(), abs, sword.copy(), n.inventory(), new long[]{0}, n.getRandom());
        h.assertTrue(poorSlot == -1, "enchanted with no experience at all");
        xp[0] = com.factionclash.logic.XpLevels.pointsFor(30);
        int used = com.factionclash.work.EnchanterJob.enchantOnce(h.getLevel(), abs, sword, n.inventory(), xp, n.getRandom());
        h.assertTrue(used >= 0, "no offer was taken at level 30");
        h.assertTrue(sword.isEnchanted(), "the sword is not enchanted");
        h.assertTrue(n.inventory().getItem(0).getCount() == 10 - (used + 1), "lapis paid: " + (10 - n.inventory().getItem(0).getCount()));
        h.assertTrue(com.factionclash.logic.XpLevels.levelFor(xp[0]) == 30 - (used + 1), "levels paid wrong: now " + com.factionclash.logic.XpLevels.levelFor(xp[0]));
        h.assertFalse(com.factionclash.work.EnchanterJob.candidate(sword), "an enchanted sword is not enchanted again");
        h.succeed();
    }

    /** Gear choice: enchanted beats plain at the same material, better material beats enchantments, Silk Touch loses. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void enchantedGearIsPreferredExceptSilkTouch(GameTestHelper h) {
        var reg = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE), eff = new ItemStack(Items.IRON_PICKAXE), silk = new ItemStack(Items.IRON_PICKAXE);
        eff.enchant(reg.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 3);
        silk.enchant(reg.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
        int t = com.factionclash.npc.ToolKind.tierOf(plain);
        h.assertTrue(com.factionclash.work.Job.gearScore(eff, t) > com.factionclash.work.Job.gearScore(plain, t), "Efficiency should beat plain");
        h.assertTrue(com.factionclash.work.Job.gearScore(silk, t) < com.factionclash.work.Job.gearScore(plain, t), "Silk Touch should lose to plain");
        h.assertTrue(com.factionclash.work.Job.gearScore(diamond, com.factionclash.npc.ToolKind.tierOf(diamond)) > com.factionclash.work.Job.gearScore(eff, t),
                "a plain diamond pickaxe should beat an enchanted iron one");
        h.succeed();
    }

    /** Ores an NPC mines give real experience orbs, and NPCs pick them up into their faction's experience. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void minedOreExperienceGoesToTheFaction(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.MINER);
        n.equip(new ItemStack(Items.IRON_PICKAXE));
        com.factionclash.campaign.FactionState f = com.factionclash.campaign.CampaignData.get(h.getLevel().getServer()).faction(FactionType.TIDE_ALLIANCE);
        long before = f.xpBank;
        BlockPos ore = new BlockPos(6, 2, 5);
        h.setBlock(ore, Blocks.DIAMOND_ORE);
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(ore), s -> true);
        h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.DIAMOND)) == 1, "no diamond in the backpack");
        h.runAfterDelay(40, () -> {
            h.assertTrue(f.xpBank >= before + 3, "diamond ore gives 3-7 experience; the faction got " + (f.xpBank - before));
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(ore)).inflate(4)).isEmpty(), "orbs were left lying around");
            h.succeed();
        });
    }

    /**
     * Cooking stations: one furnace out of fuel with ore waiting is enough to send the worker for fuel, even when the
     * other furnace is empty (it used to stand at the workshop forever).
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void oneColdFurnaceNeedsFuel(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 2, 3), Blocks.FURNACE);
        h.setBlock(new BlockPos(5, 2, 3), Blocks.FURNACE);
        var a = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 3));
        var b = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) h.getBlockEntity(new BlockPos(5, 2, 3));
        List<net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity> both = List.of(a, b);
        h.assertFalse(com.factionclash.work.CookingJob.needsFuel(both, false), "two empty furnaces need no fuel");
        a.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        h.assertTrue(com.factionclash.work.CookingJob.needsFuel(both, false), "ore waiting in a cold furnace needs fuel");
        h.assertFalse(com.factionclash.work.CookingJob.needsFuel(both, true), "the worker can refuel it from its backpack");
        a.setItem(1, new ItemStack(Items.COAL, 2));
        h.assertFalse(com.factionclash.work.CookingJob.needsFuel(both, false), "a furnace with fuel in its slot is fine");
        h.succeed();
    }

    /** A gap between an NPC and its goal: it bridges across with blocks from its backpack, one block per step. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void npcBridgesAGap(GameTestHelper h) {
        for (int x = 2; x <= 4; x++) {
            h.setBlock(new BlockPos(x, 2, 5), Blocks.STONE);
        }
        for (int x = 9; x <= 11; x++) {
            h.setBlock(new BlockPos(x, 2, 5), Blocks.STONE);
        }
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(4, 3, 5));
        n.faction(FactionType.GROVE_COVENANT);
        n.role(NpcRole.SCOUT);
        n.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        BlockPos goal = h.absolutePos(new BlockPos(10, 3, 5));
        int ledgeY = h.absolutePos(new BlockPos(4, 3, 5)).getY();
        int[] steps = {0};
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0 || !n.onGround()) {
                return;
            }
            if (n.getBlockX() >= h.absolutePos(new BlockPos(9, 3, 5)).getX()) {
                for (int x = 5; x <= 8; x++) {
                    h.assertTrue(h.getBlockState(new BlockPos(x, 2, 5)).is(Blocks.COBBLESTONE), "no bridge block at x=" + x);
                }
                h.assertTrue(n.inventory().getItem(0).getCount() == 6, "expected 4 blocks used, left " + n.inventory().getItem(0).getCount());
                h.assertTrue(n.getBlockY() == ledgeY, "the bridge should stay level, y=" + n.getBlockY());
                h.succeed();
                return;
            }
            h.assertTrue(++steps[0] <= 8, "too many steps");
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "no step at " + n.blockPosition());
        });
    }

    /**
     * Out on the tip of a staircase it bridged into the open and out of blocks: the NPC walks back down its own
     * steps (never back up to the tip) and digs its way up through the cliff instead.
     */
    @GameTest(template = "platform", timeoutTicks = 160)
    public static void npcOutOfBlocksWalksBackOffItsBridge(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(1, 2, 1, 4, 5, 10)) {
            h.setBlock(p, Blocks.STONE); // the cliff
        }
        for (BlockPos p : BlockPos.betweenClosed(5, 1, 1, 12, 1, 12)) {
            h.setBlock(p, Blocks.MAGMA_BLOCK); // the pit below: nowhere to stand
        }
        h.setBlock(new BlockPos(5, 1, 5), Blocks.STONE); // the cliff's foot, where the steps start
        h.setBlock(new BlockPos(6, 2, 5), Blocks.GRANITE); // the steps it built out into the air
        h.setBlock(new BlockPos(7, 3, 5), Blocks.GRANITE);
        h.setBlock(new BlockPos(8, 4, 5), Blocks.GRANITE);
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(8, 5, 5));
        n.faction(FactionType.GROVE_COVENANT);
        n.role(NpcRole.WOODCUTTER);
        n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        BlockPos goal = h.absolutePos(new BlockPos(8, 7, 20)); // home, above and beyond
        int cliffX = h.absolutePos(new BlockPos(4, 0, 0)).getX();
        BlockPos tip = h.absolutePos(new BlockPos(8, 5, 5));
        int[] steps = {0};
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0 || !n.onGround()) {
                return;
            }
            if (steps[0] > 0) {
                h.assertFalse(n.blockPosition().equals(tip), "it went back up to the tip of its bridge");
            }
            if (n.getBlockX() <= cliffX) {
                h.assertTrue(steps[0] >= 4 && steps[0] <= 6, "unexpected number of steps: " + steps[0]);
                h.assertTrue(h.getBlockState(new BlockPos(6, 2, 5)).is(Blocks.GRANITE), "its steps were dug away");
                h.succeed();
                return;
            }
            h.assertTrue(++steps[0] <= 8, "too many steps");
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "no step at " + n.blockPosition());
        });
    }

    /** On top of a tall trunk (too far to jump down), an NPC chops the log underfoot and comes down with the wood. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void npcOnTallTrunkChopsItsWayDown(GameTestHelper h) {
        for (int y = 2; y <= 5; y++) {
            h.setBlock(new BlockPos(12, y, 12), Blocks.OAK_LOG);
        }
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(12, 6, 12));
        n.faction(FactionType.STONEGUARD);
        n.role(NpcRole.WOODCUTTER);
        BlockPos goal = h.absolutePos(new BlockPos(2, 2, 2));
        h.runAfterDelay(5, () -> {
            h.assertTrue(com.factionclash.npc.DigOut.perched(h.getLevel(), n.blockPosition()), "on top of a 4-log trunk should count as stranded");
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "nothing was chopped");
            h.assertTrue(h.getBlockState(new BlockPos(12, 5, 12)).isAir(), "the log underfoot is still there");
            h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.OAK_LOG)) == 1, "the log did not go into the backpack");
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "nothing was chopped the second time");
        });
        h.runAfterDelay(50, () -> {
            // (the test area's barrier ceiling makes everything here count as "under cover", so check the perch)
            h.assertFalse(com.factionclash.npc.DigOut.perched(h.getLevel(), n.blockPosition()), "still stranded at y=" + n.getY());
            h.assertTrue(n.getBlockY() == h.absolutePos(new BlockPos(12, 4, 12)).getY(), "it should stand two logs lower, y=" + n.getY());
            h.succeed();
        });
    }

    /** Shut in just above where it needs to be (a pocket over a mine tunnel), an NPC digs down toward it, not up. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void sealedNpcDigsDownTowardALowerGoal(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 8, 6, 8)) {
            h.setBlock(p, Blocks.STONE);
        }
        h.setBlock(new BlockPos(5, 5, 5), Blocks.AIR);
        h.setBlock(new BlockPos(5, 6, 5), Blocks.AIR);
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(5, 5, 5));
        n.faction(FactionType.TIDE_ALLIANCE);
        n.role(NpcRole.MINER);
        BlockPos goal = h.absolutePos(new BlockPos(8, 2, 5));
        int startY = h.absolutePos(new BlockPos(5, 5, 5)).getY();
        h.runAfterDelay(5, () -> {
            h.assertTrue(com.factionclash.npc.DigOut.step(n, goal), "no step could be dug");
            h.assertTrue(n.getBlockY() == startY - 1, "it should step down toward the goal, y=" + n.getBlockY());
            h.succeed();
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

    /**
     * Real mining: the block's own drop goes straight into the miner's backpack, as a player's does - and only with
     * a pickaxe that can harvest it (a wooden pickaxe gets nothing from iron ore). Nothing is left on the ground.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void realMiningDropsIntoBackpack(GameTestHelper h) {
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
        h.onEachTick(() -> {
            if (!done[0] && b1.tick(stone, g)) {
                done[0] = true;
                h.assertTrue(b1.lastGained() >= 1, "the breaker should report the raw iron gained");
            }
            if (!done[1] && b2.tick(wood, w)) {
                done[1] = true;
            }
            if (done[0] && done[1]) {
                h.assertTrue(com.factionclash.work.Inv.count(stone.inventory(), x -> x.is(Items.RAW_IRON)) >= 1,
                        "stone-pickaxe miner did not get raw iron in its backpack");
                h.assertTrue(com.factionclash.work.Inv.count(wood.inventory(), x -> x.is(Items.RAW_IRON)) == 0,
                        "wooden-pickaxe miner got raw iron");
                h.assertTrue(h.getLevel().getBlockState(g).isAir() && h.getLevel().getBlockState(w).isAir(), "ore not removed");
                h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(g).inflate(4)).isEmpty(), "drops were left on the ground");
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

    /**
     * The hauler tidies the storehouse like a player: bread left in the overflow chest is carried to the food chest,
     * by walking to the chest, taking it out and putting it away.
     */
    @GameTest(template = "platform", timeoutTicks = 400)
    public static void haulerSortsMisplacedStacks(GameTestHelper h) {
        CampaignData data = CampaignData.get(h.getLevel().getServer());
        BaseState b = fakeBase(h, FactionType.STONEGUARD);
        b.points.put(BaseState.STORAGE_STAND, h.absolutePos(new BlockPos(4, 2, 4)));
        b.points.put(BaseState.MIN, h.absolutePos(new BlockPos(0, 1, 0)));
        b.points.put(BaseState.MAX, h.absolutePos(new BlockPos(15, 4, 15)));
        data.bases.put(b.id, b);
        BlockPos food = b.storage(StorageCategory.FOOD).get(0), general = b.storage(StorageCategory.GENERAL).get(1);
        net.minecraft.world.Container g = FactionStorage.container(h.getLevel(), general);
        g.setItem(5, new ItemStack(Items.BREAD, 7));
        FactionNpc hauler = npc(h, 8, 8, FactionType.STONEGUARD, NpcRole.HAULER);
        hauler.inventory().setItem(8, new ItemStack(Items.COOKED_BEEF, 4)); // its own meals, so it does not pack the bread
        hauler.setBaseId(b.id);
        Recruitment.register(data, hauler, b);
        h.onEachTick(() -> {
            net.minecraft.world.Container f = FactionStorage.container(h.getLevel(), food);
            int inFood = com.factionclash.work.Inv.count(f, x -> x.is(Items.BREAD));
            if (inFood == 7 && com.factionclash.work.Inv.count(g, x -> x.is(Items.BREAD)) == 0) {
                data.bases.remove(b.id);
                data.faction(b.faction).roster.remove(hauler.getUUID());
                h.succeed();
            }
        });
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
        FactionStorage.insert(level, b, new ItemStack(Items.BREAD, 6)); // the bill, plus a day's meals for the first recruit (reserve)
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
