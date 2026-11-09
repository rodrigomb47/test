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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
        // a sword does not help on stone: like a player, the NPC digs it with an empty hand rather than spend the sword
        h.assertTrue(n.getMainHandItem().getDamageValue() == 10, "a sword should be spared on stone, wear " + n.getMainHandItem().getDamageValue());
        n.equip(new ItemStack(Items.IRON_AXE));
        BlockPos leaf = new BlockPos(8, 2, 8);
        h.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(leaf), x -> true);
        h.assertTrue(n.getMainHandItem().getDamageValue() == 0, "clearing leaves wore the axe: " + n.getMainHandItem().getDamageValue());
        h.setBlock(leaf, Blocks.OAK_LOG);
        com.factionclash.work.BlockWork.breakNow(n, h.absolutePos(leaf), x -> true);
        h.assertTrue(n.getMainHandItem().getDamageValue() == 1, "an axe should lose 1 per log, wear " + n.getMainHandItem().getDamageValue());
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
        n.setNoAi(true); // only the scripted hits below: its own AI would swing first and leave the target invulnerable
        n.equip(new ItemStack(Items.IRON_SWORD));
        Zombie a = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
        Zombie b = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        Cow cow = h.spawn(EntityType.COW, new BlockPos(6, 2, 6));
        for (var m : List.of(a, b, cow)) {
            m.setNoAi(true);
        }
        h.runAfterDelay(3, () -> {
            float before = a.getHealth();
            a.invulnerableTime = 0;
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

    /**
     * The pact: when Ember conquers Grove, Tide and Stoneguard swear a pact against Ember. They learn where Ember
     * lives, their NPCs stop fighting each other (no sweep, no friendly fire, not enemies) and Ember stays the enemy.
     * Its own batch: the pact is campaign-wide and would change the other tests' fights.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "diplomacy")
    public static void conquestRaisesAPactAgainstTheConqueror(GameTestHelper h) {
        var data = com.factionclash.campaign.CampaignData.get(h.getLevel().getServer());
        List<com.factionclash.campaign.BaseState> made = new java.util.ArrayList<>();
        int i = 0;
        for (FactionType t : List.of(FactionType.EMBER_LEGION, FactionType.GROVE_COVENANT, FactionType.TIDE_ALLIANCE, FactionType.STONEGUARD)) {
            com.factionclash.campaign.BaseState b = new com.factionclash.campaign.BaseState();
            b.faction = t;
            b.kind = com.factionclash.campaign.BaseState.Kind.SETTLEMENT;
            b.center = new BlockPos(900_000 + 400 * i++, 70, 900_000);
            b.name = t.displayName + " test keep";
            made.add(data.addBase(b));
        }
        Runnable cleanup = () -> {
            for (var b : made) {
                data.bases.remove(b.id);
            }
            data.pactAgainst = null;
            data.pactMembers.clear();
            data.faction(FactionType.GROVE_COVENANT).alive = true;
            for (FactionType t : List.of(FactionType.TIDE_ALLIANCE, FactionType.STONEGUARD)) {
                data.faction(t).knowledge.removeIf(r -> r.pos.getX() >= 900_000);
            }
        };
        try {
            made.get(1).destroyed = true;
            data.faction(FactionType.GROVE_COVENANT).alive = false;
            com.factionclash.campaign.Diplomacy.onElimination(h.getLevel(), data, FactionType.GROVE_COVENANT, FactionType.EMBER_LEGION);
            h.assertTrue(data.pactAgainst == FactionType.EMBER_LEGION, "no pact against the conqueror: " + data.pactAgainst);
            h.assertTrue(data.pactMembers.equals(java.util.EnumSet.of(FactionType.TIDE_ALLIANCE, FactionType.STONEGUARD)), "members " + data.pactMembers);
            h.assertTrue(data.faction(FactionType.STONEGUARD).knowledge.stream().anyMatch(r -> r.subject == FactionType.EMBER_LEGION
                    && r.pos.equals(made.get(0).center)), "the allies were not told where Ember lives");
            FactionNpc tide = npc(h, 5, 5, FactionType.TIDE_ALLIANCE, NpcRole.SWORDSMAN);
            FactionNpc stone = npc(h, 6, 6, FactionType.STONEGUARD, NpcRole.SWORDSMAN);
            FactionNpc ember = npc(h, 6, 5, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
            FactionNpc ember2 = npc(h, 7, 5, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
            for (FactionNpc n : List.of(tide, stone, ember, ember2)) {
                n.setNoAi(true);
            }
            h.assertFalse(tide.isEnemy(stone), "pact members are still enemies");
            h.assertTrue(tide.isEnemy(ember) && stone.isEnemy(ember), "the conqueror should stay the enemy");
            h.assertTrue(ember.isEnemy(tide), "the conqueror should still fight the pact");
            tide.equip(new ItemStack(Items.IRON_SWORD));
            tide.setOnGround(true);
            tide.fallDistance = 0;
            tide.doHurtTarget(ember);
            h.assertTrue(ember2.getHealth() < ember2.getMaxHealth(), "the sweep should hit the second Ember soldier");
            h.assertTrue(stone.getHealth() == stone.getMaxHealth(), "the sweep hit an ally");
            stone.invulnerableTime = 0;
            stone.hurt(h.getLevel().damageSources().mobAttack(tide), 5.0F);
            h.assertTrue(stone.getHealth() == stone.getMaxHealth(), "friendly fire between allies");
            // Ember falls: the pact is over and the two allies are rivals again
            made.get(0).destroyed = true;
            data.faction(FactionType.EMBER_LEGION).alive = false;
            com.factionclash.campaign.Diplomacy.onElimination(h.getLevel(), data, FactionType.EMBER_LEGION, FactionType.TIDE_ALLIANCE);
            data.faction(FactionType.EMBER_LEGION).alive = true;
            h.assertTrue(data.pactAgainst == null, "the pact should end with the conqueror");
            h.assertTrue(tide.isEnemy(stone), "without the pact they are rivals again");
        } finally {
            cleanup.run();
        }
        h.succeed();
    }

    /** The newer affixes: Hardy cuts exhaustion, Duelist strengthens critical hits, Thrifty spares gear some of the time. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void hardyDuelistAndThriftyAffixes(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN);
        n.setNoAi(true);
        n.hunger().set(20, 0, 0);
        n.affixes().put(com.factionclash.logic.Affix.HARDY, 4);
        n.exhaust(1.0F);
        h.assertTrue(Math.abs(n.hunger().exhaustion() - 0.68F) < 0.001F, "Hardy x4 should feel 68% of the exhaustion: " + n.hunger().exhaustion());

        n.equip(new ItemStack(Items.IRON_SWORD));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
        z.setNoAi(true);
        float[] crit = new float[2];
        for (int i = 0; i < 2; i++) {
            if (i == 1) {
                n.affixes().put(com.factionclash.logic.Affix.DUELIST, 5);
            }
            z.setHealth(z.getMaxHealth());
            z.invulnerableTime = 0;
            n.setOnGround(false);
            n.fallDistance = 1.0F;
            n.doHurtTarget(z);
            crit[i] = z.getMaxHealth() - z.getHealth();
        }
        h.assertTrue(crit[1] > crit[0] * 1.25F, "Duelist x5 should make a critical hit x2 instead of x1.5: " + crit[0] + " -> " + crit[1]);

        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        n.equip(pick);
        n.affixes().put(com.factionclash.logic.Affix.THRIFTY, 4);
        for (int i = 0; i < 200; i++) {
            com.factionclash.work.BlockWork.wear(n, n.getMainHandItem(), h.getLevel(), Blocks.STONE.defaultBlockState(), h.absolutePos(new BlockPos(9, 2, 9)));
        }
        int worn = n.getMainHandItem().getDamageValue();
        h.assertTrue(worn >= 110 && worn <= 160, "Thrifty x4 should spare about a third of 200 wear points, worn " + worn);
        h.succeed();
    }

    /**
     * A trader trades with a villager like a player: the villager's offers, prices and stock, paid from the backpack.
     * It sells its wheat for emeralds first, then buys the iron helmet once it can pay; the villager earns its
     * trading experience. Gear no better than the storehouse's is not bought.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void traderTradesWithAVillagerLikeAPlayer(GameTestHelper h) {
        FactionNpc n = npc(h, 5, 5, FactionType.STONEGUARD, NpcRole.TRADER);
        n.setNoAi(true);
        net.minecraft.world.entity.npc.Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 5));
        v.setNoAi(true);
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.ARMORER));
        net.minecraft.world.item.trading.MerchantOffers offers = new net.minecraft.world.item.trading.MerchantOffers();
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.WHEAT, 20),
                new ItemStack(Items.EMERALD), 16, 2, 0.05F));
        offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 5),
                new ItemStack(Items.IRON_HELMET), 12, 1, 0.2F));
        v.setOffers(offers);
        h.assertTrue(com.factionclash.work.TraderJob.trades(v), "an armorer should count as a villager with trades");
        n.inventory().setItem(0, new ItemStack(Items.WHEAT, 64));
        n.inventory().setItem(1, new ItemStack(Items.EMERALD, 2));
        List<String> done = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var d = com.factionclash.work.TraderJob.tradeOnce(n, v, x -> x.is(Items.WHEAT), x -> x.is(Items.IRON_HELMET));
            if (d == null) {
                break;
            }
            done.add(d.text());
        }
        h.assertTrue(done.size() == 4 && done.get(0).startsWith("sold 20x") && done.get(3).equals("bought Iron Helmet"),
                "expected three sales then the helmet, got " + done);
        h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.WHEAT)) == 4, "wheat left "
                + com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.WHEAT)));
        h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.EMERALD)) == 0, "the emeralds should be spent");
        h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.IRON_HELMET)) == 1, "no helmet in the backpack");
        h.assertTrue(offers.get(0).getUses() == 3 && offers.get(1).getUses() == 1, "the villager's stock was not used");
        h.assertTrue(v.getVillagerXp() == 7, "the villager should gain its trading experience, got " + v.getVillagerXp());
        // a diamond helmet in store: an iron one is no improvement
        h.assertFalse(com.factionclash.work.TraderJob.betterThanStock(new ItemStack(Items.IRON_HELMET), pred -> new ItemStack(Items.DIAMOND_HELMET), pred -> 0),
                "bought worse gear than the storehouse has");
        h.assertTrue(com.factionclash.work.TraderJob.betterThanStock(new ItemStack(Items.IRON_CHESTPLATE), pred -> ItemStack.EMPTY, pred -> 0),
                "an iron chestplate for a base with none is worth buying");
        h.assertFalse(com.factionclash.work.TraderJob.betterThanStock(new ItemStack(Items.STONE_SWORD), pred -> ItemStack.EMPTY, pred -> 0),
                "stone gear is not worth emeralds");
        h.succeed();
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

    /**
     * In a mine's stairwell with two steps gone (mined out from under it), where the stairs may not be dug: the NPC
     * climbs the spiral, putting each missing stair floor back with a block from its backpack.
     */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void npcClimbsABrokenMineStairwell(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 9, 4, 8)) {
            h.setBlock(p, Blocks.STONE);
        }
        com.factionclash.campaign.WorkplaceState mine = new com.factionclash.campaign.WorkplaceState();
        mine.id = 990_001;
        mine.type = com.factionclash.campaign.WorkplaceState.Type.MINE;
        mine.origin = h.absolutePos(new BlockPos(4, 5, 5));
        mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
        var data = com.factionclash.campaign.CampaignData.get(h.getLevel().getServer());
        data.workplaces.put(mine.id, mine);
        for (int k = 1; k <= 3; k++) {
            BlockPos st = com.factionclash.structure.MineLayout.stand(mine, k);
            for (int y = 0; y < 3; y++) {
                h.getLevel().setBlockAndUpdate(st.above(y), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos floor2 = com.factionclash.structure.MineLayout.stand(mine, 2).below();
        BlockPos floor1 = com.factionclash.structure.MineLayout.stand(mine, 1).below();
        h.getLevel().setBlockAndUpdate(floor2, Blocks.AIR.defaultBlockState()); // steps 1 and 2 have lost their floors
        h.getLevel().setBlockAndUpdate(floor1, Blocks.AIR.defaultBlockState());
        BlockPos start = com.factionclash.structure.MineLayout.stand(mine, 3);
        h.assertTrue(start.equals(h.absolutePos(new BlockPos(6, 2, 6))), "step 3 should be at (6, 2, 6)");
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(6, 2, 6));
        n.faction(FactionType.TIDE_ALLIANCE);
        n.role(NpcRole.SCOUT); // (a miner's own job would claim the test mine and walk back down)
        n.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        BlockPos goal = h.absolutePos(new BlockPos(14, 6, 5));
        int[] steps = {0};
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0 || !n.onGround()) {
                return;
            }
            if (n.getBlockY() >= mine.origin.getY()) {
                data.workplaces.remove(mine.id);
                h.assertTrue(steps[0] == 3, "expected 3 steps up the spiral, took " + steps[0]);
                h.assertTrue(h.getLevel().getBlockState(floor1).is(Blocks.COBBLESTONE) && h.getLevel().getBlockState(floor2).is(Blocks.COBBLESTONE),
                        "the missing stair floors were not put back");
                h.assertTrue(n.inventory().getItem(0).getCount() == 3, "two blocks should be used");
                h.succeed();
                return;
            }
            if (++steps[0] > 6) {
                data.workplaces.remove(mine.id);
                h.fail("too many steps, at " + n.blockPosition());
            }
            if (!com.factionclash.npc.DigOut.step(n, goal)) {
                data.workplaces.remove(mine.id);
                h.fail("no step at " + n.blockPosition());
            }
        });
    }

    /**
     * A miner digs the spiral down from the surface to the level it chose, step by step, and the stairs it dug are
     * open. Back at the storehouse it plans the next layer: in the flat test world no layer is deep enough, so the
     * mine is given up as worked out and handed back, and the miner, with no other mine or cave, looks for a new
     * site, finds the ground everywhere too shallow and is idle.
     */
    @GameTest(template = "platform", timeoutTicks = 1200, batch = "mine_plan", skyAccess = true)
    public static void minerDigsDownToItsLevelThenRetiresAShallowMine(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 1200, new BlockPos(12, 2, 16));
        FactionNpc miner;
        com.factionclash.campaign.WorkplaceState mine = new com.factionclash.campaign.WorkplaceState();
        try {
            for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 9, 4, 8)) {
                h.setBlock(p, Blocks.STONE);
            }
            mine.type = com.factionclash.campaign.WorkplaceState.Type.MINE;
            mine.origin = h.absolutePos(new BlockPos(4, 5, 5));
            mine.extent = mine.origin;
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.maxRounds = 4;
            // a finished coal level 3 steps down that the shaft does not reach yet: the miner digs down to it first
            mine.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(3, com.factionclash.logic.SpiralMine.planSize(4), 4,
                    com.factionclash.logic.MinePlanner.Band.COAL.ordinal()));
            mine.cur = 0;
            mine.bottom = 0;
            mine.upgradeStep = 0;
            t.addWorkplace(mine);
            miner = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(4, 5, 5));
            miner.faction(FactionType.STONEGUARD);
            miner.role(NpcRole.MINER);
            t.register(miner);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
            miner.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            mine.assigned = miner.getUUID();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        boolean[] reached = {false};
        FactionNpc m = miner;
        t.onEachTick(() -> {
            if (!reached[0] && mine.bottom == 3) {
                reached[0] = true;
                for (com.factionclash.logic.SpiralMine.Cell c : com.factionclash.logic.SpiralMine.spiralPlan(0, 3)) {
                    BlockPos p = com.factionclash.structure.MineLayout.spiralPos(mine, c);
                    net.minecraft.world.level.block.state.BlockState s = h.getLevel().getBlockState(p);
                    h.assertTrue(s.isAir() || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH), "stair cell " + p + " should be dug, is " + s);
                }
                h.assertTrue(mine.upgradeStep == -1 && !mine.workedOut, "the shaft is done and the mine still open");
            }
            if (mine.workedOut) {
                h.assertTrue(reached[0], "the mine was given up before the shaft reached its level");
                h.assertTrue(mine.assigned == null && mine.note.contains("every layer"), "a worked-out mine is handed back: " + mine.note);
                if (m.job() != null && m.job().idleReason().equals(com.factionclash.work.MinerJob.TOO_SHALLOW)) {
                    t.succeed();
                }
            }
        });
    }

    /**
     * A miner digs the shaft of its own new mine (no hut) from the surface: the stairs it digs are open, the ground
     * ring around the mouth is plugged where it was water or open (water by the careful plugging before each cell,
     * an open hole by the new ring rule), and a torch stands beside the mouth. The test ends once the shaft
     * reaches its level, before any planning or tunnelling.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "mine_shaft", skyAccess = true)
    public static void minerDigsItsOwnShaftAndSealsTheGroundRing(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 400, new BlockPos(12, 2, 16));
        com.factionclash.campaign.WorkplaceState mine = new com.factionclash.campaign.WorkplaceState();
        BlockPos water, air, hole, torch;
        try {
            for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 9, 4, 8)) {
                h.setBlock(p, Blocks.STONE);
            }
            mine.type = com.factionclash.campaign.WorkplaceState.Type.MINE;
            mine.origin = h.absolutePos(new BlockPos(4, 5, 5));
            mine.extent = mine.origin;
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.hut = false;
            mine.maxRounds = 4;
            mine.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(3, com.factionclash.logic.SpiralMine.planSize(4), 4,
                    com.factionclash.logic.MinePlanner.Band.COAL.ordinal()));
            mine.cur = 0;
            mine.bottom = 0;
            mine.upgradeStep = 0;
            t.addWorkplace(mine);
            water = com.factionclash.structure.MineLayout.local(mine, 2, -1, -1);
            air = com.factionclash.structure.MineLayout.local(mine, 1, -1, -1);
            hole = com.factionclash.structure.MineLayout.local(mine, 2, 2, -1); // beside step 3, away from the water
            torch = com.factionclash.structure.MineLayout.local(mine, 0, -1, 0);
            h.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            h.getLevel().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
            FactionNpc miner = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(4, 5, 5));
            miner.faction(FactionType.STONEGUARD);
            miner.role(NpcRole.MINER);
            t.register(miner);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
            miner.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            miner.inventory().setItem(1, new ItemStack(Items.TORCH, 4));
            mine.assigned = miner.getUUID();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            if (mine.bottom != 3) {
                return;
            }
            for (com.factionclash.logic.SpiralMine.Cell c : com.factionclash.logic.SpiralMine.spiralPlan(0, 3)) {
                BlockPos p = com.factionclash.structure.MineLayout.spiralPos(mine, c);
                net.minecraft.world.level.block.state.BlockState s = h.getLevel().getBlockState(p);
                t.assertTrue(s.isAir() || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH), "stair cell " + p + " should be dug, is " + s);
            }
            for (BlockPos p : new BlockPos[]{water, air, hole}) {
                t.assertTrue(h.getLevel().getBlockState(p).is(Blocks.COBBLESTONE), "the ground ring at " + p + " should be plugged, is "
                        + h.getLevel().getBlockState(p));
            }
            t.assertTrue(h.getLevel().getBlockState(torch).is(Blocks.TORCH), "a torch should stand beside the mouth");
            t.succeed();
        });
    }

    /**
     * In the flat test world no ground lies deep enough for a mine: a miner without one looks around its base,
     * finds every point too shallow and goes idle for that reason at once, without opening a mine, digging anything
     * or walking out to look further.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "mine_nosite", skyAccess = true)
    public static void minerFindsNoSiteOnShallowGround(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 200);
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> floor = new java.util.HashMap<>();
        FactionNpc miner;
        try {
            for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 23, 1, 23)) {
                floor.put(h.absolutePos(p), h.getBlockState(p));
            }
            miner = t.npc(12, 16, NpcRole.MINER);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0)), b = h.absolutePos(new BlockPos(23, 0, 23));
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        FactionNpc m = miner;
        var data = t.data;
        int baseId = t.base.id;
        t.onEachTick(() -> {
            BlockPos at = m.blockPosition();
            t.assertTrue(at.getX() >= minX && at.getX() <= maxX && at.getZ() >= minZ && at.getZ() <= maxZ,
                    "the miner left the platform at " + at);
            if (m.job() == null || !m.job().idleReason().equals(com.factionclash.work.MinerJob.TOO_SHALLOW)) {
                return;
            }
            for (com.factionclash.campaign.WorkplaceState w : data.workplaces.values()) {
                t.assertTrue(w.baseId != baseId || w.type != com.factionclash.campaign.WorkplaceState.Type.MINE, "a mine was opened: #" + w.id);
            }
            floor.forEach((p, s) -> t.assertTrue(h.getLevel().getBlockState(p) == s, "the platform was dug at " + p));
            t.succeed();
        });
    }

    /**
     * A new mine whose mouth the miner never reaches is given up as unreachable, but a mine whose first descent is
     * under way is not: its bottom stays at the surface until the whole descent is dug, so a miner on its way back
     * to the shaft (after a reload, say) only looks as if it had never got there. The miner starts far from the
     * mouth with its walking time to the mouth already used up; with part of the shaft dug the mine is kept, and once
     * the record says nothing is dug the same walk gives it up and remembers the site.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "mine_reach", skyAccess = true)
    public static void minerKeepsAHalfDugShaftWhileWalkingBack(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 300, new BlockPos(12, 2, 16));
        com.factionclash.campaign.WorkplaceState mine = new com.factionclash.campaign.WorkplaceState();
        FactionNpc miner;
        java.lang.reflect.Field walkMine, walkTicks, walkLast, state, bad;
        try {
            for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 9, 4, 8)) {
                h.setBlock(p, Blocks.STONE); // the mouth stands on a block the miner cannot climb
            }
            mine.type = com.factionclash.campaign.WorkplaceState.Type.MINE;
            mine.origin = h.absolutePos(new BlockPos(4, 5, 5));
            mine.extent = mine.origin;
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.hut = false;
            mine.maxRounds = 4;
            mine.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(3, 0, 0,
                    com.factionclash.logic.MinePlanner.Band.COAL.ordinal()));
            mine.cur = 0;
            mine.bottom = 0;
            mine.upgradeStep = 6; // steps 1 and 2 of the descent are dug
            t.addWorkplace(mine);
            miner = t.npc(19, 19, NpcRole.MINER);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
            mine.assigned = miner.getUUID();
            Class<?> jc = com.factionclash.work.MinerJob.class;
            walkMine = jc.getDeclaredField("siteWalkMine");
            walkTicks = jc.getDeclaredField("siteWalkTicks");
            walkLast = jc.getDeclaredField("siteWalkLast");
            state = jc.getDeclaredField("state");
            bad = jc.getDeclaredField("badSites");
            for (java.lang.reflect.Field f : new java.lang.reflect.Field[]{walkMine, walkTicks, walkLast, state, bad}) {
                f.setAccessible(true);
            }
        } catch (ReflectiveOperationException e) {
            t.cleanup();
            throw new IllegalStateException(e);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        FactionNpc m = miner;
        // the walk toward the mouth has already taken the whole allowance (as if counted before a reload)
        Runnable usedUp = () -> {
            try {
                walkMine.setInt(m.job(), mine.id);
                walkTicks.setInt(m.job(), com.factionclash.work.MinerJob.UNREACHABLE_SITE_TICKS);
                walkLast.setLong(m.job(), h.getLevel().getGameTime() - 50);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
        int[] ticks = {0};
        t.onEachTick(() -> {
            if (!(m.job() instanceof com.factionclash.work.MinerJob)) {
                return;
            }
            String st;
            try {
                st = String.valueOf(state.get(m.job()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            ticks[0]++;
            if (ticks[0] == 1) {
                usedUp.run();
            }
            if (ticks[0] <= 30) {
                t.assertTrue(!mine.workedOut, "a mine whose shaft is being dug was given up: " + mine.note);
                if (ticks[0] == 30) {
                    t.assertTrue(st.equals("TRAVEL"), "the miner should be walking back to its mine, is in " + st);
                    mine.upgradeStep = 0; // nothing dug after all: the mouth was never reached
                    usedUp.run();
                }
                return;
            }
            if (mine.workedOut) {
                t.assertTrue(mine.note.contains("cannot be reached"), "given up for the wrong reason: " + mine.note);
                try {
                    t.assertTrue(((List<?>) bad.get(m.job())).contains(mine.origin.asLong()), "the unreachable site is not remembered");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                t.succeed();
            }
        });
    }

    /**
     * A mine rests until an iron pickaxe; a miner with a stone one and nothing else to work fetches the iron pickaxe
     * waiting in the storehouse and takes the mine up again (in the flat test world its planning then finds nothing
     * to dig, so the mine is given up, but only after it was taken up).
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "mine_rest")
    public static void minerFetchesABetterPickaxeForARestingMine(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 400);
        com.factionclash.campaign.WorkplaceState mine;
        FactionNpc miner;
        try {
            mine = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.MINE, h.absolutePos(new BlockPos(18, 2, 18)));
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.restTier = 2;
            FactionStorage.insert(h.getLevel(), t.base, new ItemStack(Items.IRON_PICKAXE));
            miner = t.npc(6, 6, NpcRole.MINER);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        FactionNpc m = miner;
        com.factionclash.campaign.WorkplaceState w = mine;
        t.onEachTick(() -> {
            if (w.workedOut) {
                h.assertTrue(w.restTier == 0, "the resting mine was given up without being taken up again (rest tier " + w.restTier + ")");
                h.assertTrue(m.tool().is(Items.IRON_PICKAXE), "the miner should work with the iron pickaxe, has " + m.tool());
                t.succeed();
            }
        });
    }

    /**
     * A hutless mine on the 3-high stone block of the platform (mouth at relative (4, 5, 5), tunnels running east)
     * with one coal level at step 3, for the shaft and tunnel tests below.
     */
    private static com.factionclash.campaign.WorkplaceState stoneBlockMine(GameTestHelper h, GameTestBases.Handle t, int progress, int bottom,
                                                                           int upgradeStep) {
        for (BlockPos p : BlockPos.betweenClosed(3, 2, 3, 9, 4, 8)) {
            h.setBlock(p, Blocks.STONE);
        }
        com.factionclash.campaign.WorkplaceState mine = new com.factionclash.campaign.WorkplaceState();
        mine.type = com.factionclash.campaign.WorkplaceState.Type.MINE;
        mine.origin = h.absolutePos(new BlockPos(4, 5, 5));
        mine.extent = mine.origin;
        mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
        mine.hut = false;
        mine.maxRounds = 0;
        mine.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(3, progress, 0, com.factionclash.logic.MinePlanner.Band.COAL.ordinal()));
        mine.cur = 0;
        mine.bottom = bottom;
        mine.upgradeStep = upgradeStep;
        t.addWorkplace(mine);
        // the stairs the record says are dug
        for (com.factionclash.logic.SpiralMine.Cell c : com.factionclash.logic.SpiralMine.spiralPlan(0, 3)) {
            BlockPos p = com.factionclash.structure.MineLayout.spiralPos(mine, c);
            if (!c.torch() && com.factionclash.structure.MineLayout.shaftCellDug(mine, p)) {
                h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        return mine;
    }

    private static FactionNpc stoneBlockMiner(GameTestHelper h, GameTestBases.Handle t, com.factionclash.campaign.WorkplaceState mine, BlockPos at) {
        FactionNpc miner = h.spawn(ModEntities.FACTION_NPC.get(), at);
        miner.faction(FactionType.STONEGUARD);
        miner.role(NpcRole.MINER);
        t.register(miner);
        miner.equip(new ItemStack(Items.STONE_PICKAXE));
        miner.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        miner.inventory().setItem(1, new ItemStack(Items.TORCH, 8));
        mine.assigned = miner.getUUID();
        return miner;
    }

    /**
     * The shaft meets an aquifer: the cells of the step it digs down to, and the rock beside them, hold water. The
     * miner plugs the water around each cell, plugs the wet cells of the stairs it has not reached yet instead of
     * only emptying them (they would fill again at once), and digs on; a cell that floods all the same just before
     * its turn is cleared rather than "dug" for thousands of ticks. The descent reaches its level well before the
     * stuck-shaft watchdog, with no shaft limit set.
     */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "mine_aquifer", skyAccess = true)
    public static void minerDigsItsShaftThroughAnAquifer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 900, new BlockPos(12, 2, 16));
        com.factionclash.campaign.WorkplaceState mine;
        BlockPos lowest;
        try {
            mine = stoneBlockMine(h, t, com.factionclash.logic.SpiralMine.planSize(0), 0, 0);
            // step 3's cell (forward 2, side 1) at its two lower rows, and the rock beside it, is an aquifer
            for (int dy = -3; dy <= -2; dy++) {
                for (int[] fs : new int[][]{{2, 1}, {3, 1}, {2, 2}}) {
                    h.getLevel().setBlockAndUpdate(com.factionclash.structure.MineLayout.local(mine, fs[0], fs[1], dy), Blocks.WATER.defaultBlockState());
                }
            }
            lowest = com.factionclash.structure.MineLayout.local(mine, 2, 1, -3);
            stoneBlockMiner(h, t, mine, new BlockPos(4, 5, 5));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        com.factionclash.campaign.WorkplaceState w = mine;
        // the index of step 3's lowest cell in the descent's plan
        int last = com.factionclash.logic.SpiralMine.spiralIndex(0, 3, lowest.getY() - mine.origin.getY());
        boolean[] flooded = {false};
        t.onEachTick(() -> {
            if (!flooded[0] && w.upgradeStep == last) {
                // water seeps into the last cell just before its turn
                flooded[0] = true;
                h.getLevel().setBlockAndUpdate(lowest, Blocks.WATER.defaultBlockState());
            }
            t.assertTrue(w.shaftLimit == 0 && !w.workedOut, "the descent was given up at the aquifer: limit " + w.shaftLimit + ", " + w.note);
            if (w.bottom != 3) {
                return;
            }
            t.assertTrue(flooded[0], "the last cell was never reached by the plan");
            for (com.factionclash.logic.SpiralMine.Cell c : com.factionclash.logic.SpiralMine.spiralPlan(0, 3)) {
                BlockPos p = com.factionclash.structure.MineLayout.spiralPos(w, c);
                net.minecraft.world.level.block.state.BlockState st = h.getLevel().getBlockState(p);
                t.assertTrue(st.isAir() || st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH), "stair cell " + p + " should be open, is " + st);
            }
            t.succeed();
        });
    }

    /**
     * A miner partway down the first descent of a new mine (its bottom is still the surface: the bottom only moves
     * once the whole descent is dug), back on its walk (after a fight or a reload), goes on down from where it is
     * instead of climbing to the mouth and walking all the way down again.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "mine_descent", skyAccess = true)
    public static void minerGoesOnDownFromWhereItIsInAHalfDugShaft(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 400, new BlockPos(12, 2, 16));
        com.factionclash.campaign.WorkplaceState mine;
        FactionNpc miner;
        BlockPos stand2;
        try {
            // steps 1 and 2 dug, step 3 not yet
            mine = stoneBlockMine(h, t, com.factionclash.logic.SpiralMine.planSize(0), 0, com.factionclash.logic.SpiralMine.spiralIndex(0, 3, -1));
            stand2 = com.factionclash.structure.MineLayout.stand(mine, 2);
            h.assertTrue(stand2.equals(h.absolutePos(new BlockPos(6, 3, 5))), "step 2 should stand at (6, 3, 5)");
            miner = stoneBlockMiner(h, t, mine, new BlockPos(6, 3, 5));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        com.factionclash.campaign.WorkplaceState w = mine;
        FactionNpc m = miner;
        int top = mine.origin.getY();
        t.onEachTick(() -> {
            t.assertTrue(m.getBlockY() < top, "the miner climbed back out to the mouth (at " + m.blockPosition() + ")");
            t.assertTrue(w.shaftLimit == 0 && !w.workedOut, "the descent was given up: " + w.note);
            if (w.bottom == 3) {
                t.succeed();
            }
        });
    }

    /**
     * Branch mining, a tunnel cell ahead floods (water from a cave lake or an aquifer seeps in just before the miner
     * gets to it, and runs into the tunnel already dug): the miner plugs the rock around it, empties the water from
     * the cell and from the tunnel behind it (rather than walling its own way out shut) and goes on, instead of
     * "digging" the water until the face watchdog gives the whole level up.
     */
    @GameTest(template = "platform", timeoutTicks = 600, batch = "mine_tunnel", skyAccess = true)
    public static void minerClearsAFloodedTunnelCell(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 600, new BlockPos(12, 2, 16));
        com.factionclash.campaign.WorkplaceState mine;
        try {
            mine = stoneBlockMine(h, t, 0, 3, -1);
            h.assertTrue(com.factionclash.structure.MineLayout.stand(mine, 3).equals(h.absolutePos(new BlockPos(6, 2, 6))), "step 3 should stand at (6, 2, 6)");
            stoneBlockMiner(h, t, mine, new BlockPos(6, 2, 6));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        com.factionclash.campaign.WorkplaceState w = mine;
        com.factionclash.campaign.WorkplaceState.MineLevel lv = mine.levels.get(0);
        List<com.factionclash.logic.SpiralMine.Cell> plan = com.factionclash.structure.MineLayout.tunnelPlan(lv);
        // the second main-tunnel cell's upper half (forward 4), after the first one (forward 3) is dug
        int wet = 2;
        h.assertTrue(plan.get(wet).f() == 4 && plan.get(wet).dy() == 1 && plan.get(0).f() == 3, "unexpected plan " + plan.subList(0, 4));
        BlockPos cell = com.factionclash.structure.MineLayout.tunnelPos(mine, lv, plan.get(wet));
        BlockPos behind = com.factionclash.structure.MineLayout.tunnelPos(mine, lv, plan.get(0));
        boolean[] flooded = {false};
        t.onEachTick(() -> {
            if (!flooded[0] && lv.progress == wet) {
                flooded[0] = true;
                h.getLevel().setBlockAndUpdate(cell, Blocks.WATER.defaultBlockState());
                h.getLevel().setBlockAndUpdate(behind, Blocks.WATER.defaultBlockState()); // it has run into the tunnel
            }
            t.assertTrue(!lv.cut, "the level was given up at the flooded cell");
            if (lv.progress <= wet + 1 || !flooded[0]) {
                return;
            }
            t.assertTrue(h.getLevel().getBlockState(cell).isAir(), "the flooded cell should be open, is " + h.getLevel().getBlockState(cell));
            t.assertTrue(!h.getLevel().getBlockState(behind).is(Blocks.COBBLESTONE), "the tunnel behind was walled shut");
            t.succeed();
        });
    }

    /**
     * A mine rests until an iron pickaxe, and the only one in the storehouse has Silk Touch, which a miner never
     * takes over its own stone one. The miner does not go to fetch it (it would stand at the storehouse every tick,
     * never getting anywhere): it looks for a new mine site at once, from where it stands. On the flat test world it
     * finds the ground too shallow everywhere and is idle, the mine still resting and the pickaxe still in store.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "mine_restfetch", skyAccess = true)
    public static void minerLooksForANewSiteWhenTheRestingMinesPickaxeIsNoUse(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 200);
        com.factionclash.campaign.WorkplaceState mine;
        FactionNpc miner;
        try {
            mine = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.MINE, h.absolutePos(new BlockPos(4, 2, 18)));
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.restTier = 2;
            var reg = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
            silk.enchant(reg.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
            FactionStorage.insert(h.getLevel(), t.base, silk);
            miner = t.npc(20, 20, NpcRole.MINER);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        FactionNpc m = miner;
        com.factionclash.campaign.WorkplaceState w = mine;
        BlockPos store = h.absolutePos(new BlockPos(4, 2, 4));
        t.onEachTick(() -> {
            t.assertTrue(m.blockPosition().distManhattan(store) > 10, "the miner went to the storehouse for a pickaxe it would not take");
            if (m.job() == null || !m.job().idleReason().equals(com.factionclash.work.MinerJob.TOO_SHALLOW)) {
                return;
            }
            t.assertTrue(w.restTier == 2 && w.assigned == null, "the resting mine was taken up");
            t.assertTrue(FactionStorage.count(h.getLevel(), t.base, s -> s.is(Items.IRON_PICKAXE)) == 1, "the Silk Touch pickaxe left the storehouse");
            t.succeed();
        });
    }

    /**
     * An old save's miner holds the surveyed cave it works and also its old hut mine (the old version never handed a
     * worked-out hut mine back; the migration brings it back to life). It goes on with the cave and hands the mine
     * back, so another miner can take it up and the base sees it has no miner.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "mine_legacy")
    public static void minerWorkingACaveHandsBackItsOldMine(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 60);
        com.factionclash.campaign.WorkplaceState mine, cave;
        FactionNpc miner;
        try {
            miner = t.npc(8, 8, NpcRole.MINER);
            miner.equip(new ItemStack(Items.STONE_PICKAXE));
            mine = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.MINE, h.absolutePos(new BlockPos(4, 2, 18)));
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            mine.hut = true;
            mine.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(6, 0, 0, -1));
            mine.cur = 0;
            mine.bottom = 6;
            mine.assigned = miner.getUUID();
            cave = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.CAVE, h.absolutePos(new BlockPos(18, 2, 4)));
            cave.marks.add(h.absolutePos(new BlockPos(18, 1, 4)).asLong());
            cave.route = new long[]{cave.origin.asLong()};
            cave.parent = new int[]{-1};
            cave.assigned = miner.getUUID();
            h.assertTrue(mine.active(), "the migrated mine should be open");
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        FactionNpc m = miner;
        com.factionclash.campaign.WorkplaceState mw = mine, cw = cave;
        h.runAtTickTime(20, t.guard(() -> {
            t.assertTrue(m.getUUID().equals(cw.assigned), "the miner should still work its cave");
            t.assertTrue(mw.assigned == null, "the old mine should be handed back");
            t.assertTrue(com.factionclash.campaign.FactionDirector.needs(h.getLevel(), t.data, t.base, h.getLevel().getGameTime()).stream()
                    .anyMatch(n -> n.role() == NpcRole.MINER && n.reason().equals("an open mine has no miner")), "the base should want a miner for it");
            t.succeed();
        }));
    }

    /** At the bottom of a narrow well with walls it cannot dig: the NPC pillars up with blocks from its backpack. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void npcPillarsOutOfAWell(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(4, 2, 4, 6, 4, 6)) {
            h.setBlock(p, Blocks.BEDROCK);
        }
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(5, y, 5), Blocks.AIR);
        }
        FactionNpc n = h.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(5, 2, 5));
        n.faction(FactionType.STONEGUARD);
        n.role(NpcRole.SCOUT);
        n.inventory().setItem(0, new ItemStack(Items.DIRT, 5));
        BlockPos goal = h.absolutePos(new BlockPos(5, 7, 14));
        int top = h.absolutePos(new BlockPos(5, 5, 5)).getY();
        int[] steps = {0};
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0 || !n.onGround()) {
                return;
            }
            if (n.getBlockY() >= top) {
                // two blocks pillared, then a plain step up onto the rim
                h.assertTrue(steps[0] == 3, "expected 3 steps, took " + steps[0]);
                h.assertTrue(com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.DIRT)) == 3, "two blocks should be used, dirt left "
                        + com.factionclash.work.Inv.count(n.inventory(), x -> x.is(Items.DIRT)));
                h.assertTrue(h.getBlockState(new BlockPos(5, 2, 5)).is(Blocks.DIRT) && h.getBlockState(new BlockPos(5, 3, 5)).is(Blocks.DIRT), "no pillar under it");
                h.succeed();
                return;
            }
            h.assertTrue(++steps[0] <= 5, "too many steps");
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

    /**
     * Retraining frees the old job's workplace: a fisherman who becomes a miner no longer holds the fishing spot, and a
     * tree plot still assigned to a worker who is now a scout (as in saves from before this rule) is free, so a
     * woodcutter can claim it.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void retrainingReleasesTheWorkplace(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        var roster = t.data.faction(t.base.faction).roster;
        com.factionclash.campaign.NpcRecord scout = new com.factionclash.campaign.NpcRecord();
        scout.uuid = java.util.UUID.randomUUID();
        scout.name = "Old woodcutter";
        scout.role = NpcRole.SCOUT;
        scout.baseId = t.base.id;
        try {
            var fishery = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.FISHERY, h.absolutePos(new BlockPos(18, 1, 18)));
            FactionNpc fisher = t.npc(8, 8, NpcRole.FISHERMAN);
            h.assertTrue(com.factionclash.work.Workplaces.claim(fisher, com.factionclash.campaign.WorkplaceState.Type.FISHERY) == fishery,
                    "the fisherman should claim the free fishing spot");
            h.assertFalse(com.factionclash.work.Workplaces.unworked(t.data, fishery), "a fishing spot with its fisherman counts as worked");
            fisher.role(NpcRole.MINER);
            h.assertTrue(fishery.assigned == null, "retraining should hand back the fishing spot, held by " + fishery.assigned);

            roster.put(scout.uuid, scout);
            var plot = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT, h.absolutePos(new BlockPos(18, 2, 6)));
            plot.assigned = scout.uuid; // retrained before retraining released workplaces
            h.assertTrue(com.factionclash.work.Workplaces.unworked(t.data, plot), "a tree plot held by a living scout should count as free");
            FactionNpc cutter = t.npc(10, 8, NpcRole.WOODCUTTER);
            h.assertTrue(com.factionclash.work.Workplaces.claim(cutter, com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT) == plot
                    && cutter.getUUID().equals(plot.assigned), "the woodcutter should claim the plot, held by " + plot.assigned);
            h.assertFalse(com.factionclash.work.Workplaces.unworked(t.data, plot), "the plot with its woodcutter counts as worked");
        } finally {
            roster.remove(scout.uuid);
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * A claim's condition applies to the NPC's own workplace too: a miner still holding a worked-out mine gets the
     * free active one instead of its old one.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void claimSkipsOwnWorkedOutMine(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 40);
        try {
            FactionNpc miner = t.npc(8, 8, NpcRole.MINER);
            var type = com.factionclash.campaign.WorkplaceState.Type.MINE;
            var a = t.addWorkplace(type, h.absolutePos(new BlockPos(4, 2, 18)));
            a.assigned = miner.getUUID();
            a.workedOut = true;
            var b = t.addWorkplace(type, h.absolutePos(new BlockPos(18, 2, 4)));
            h.assertTrue(com.factionclash.work.Workplaces.claim(miner, type) == a, "without a condition the miner's own mine comes first");
            var got = com.factionclash.work.Workplaces.claim(miner, type, w -> !w.workedOut);
            h.assertTrue(got == b, "the claim should skip the worked-out mine #" + a.id + " and take #" + b.id + ", got "
                    + (got == null ? "none" : "#" + got.id));
            h.assertTrue(miner.getUUID().equals(b.assigned), "the active mine should now be the miner's");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * The base hires no miner for a surveyed cave or an open mine while one of its miners has nothing to work (no mine,
     * no cave): that miner takes the place up itself. Only once every miner holds a place is another one wanted.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void noMinerHiredWhileAMinerIsFree(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 40);
        try {
            var level = h.getLevel();
            FactionNpc a = t.npc(8, 8, NpcRole.MINER);
            var cave = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.CAVE, h.absolutePos(new BlockPos(18, 2, 4)));
            cave.marks.add(h.absolutePos(new BlockPos(18, 1, 4)).asLong());
            java.util.function.Predicate<String> wants = why -> com.factionclash.campaign.FactionDirector.needs(level, t.data, t.base, level.getGameTime())
                    .stream().anyMatch(n -> n.role() == NpcRole.MINER && n.reason().equals(why));
            h.assertFalse(wants.test("a surveyed cave with ore to mine"), "a free miner takes the cave itself: nobody should be hired for it");
            var mine = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.MINE, h.absolutePos(new BlockPos(4, 2, 18)));
            mine.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            h.assertFalse(wants.test("an open mine has no miner"), "a free miner takes the open mine itself");
            mine.assigned = a.getUUID();
            h.assertTrue(wants.test("a surveyed cave with ore to mine"), "with the only miner busy, the cave wants one");
            cave.assigned = a.getUUID();
            mine.assigned = null;
            h.assertTrue(wants.test("an open mine has no miner"), "with the only miner in the cave, the open mine wants one");
            mine.workedOut = true;
            h.assertFalse(wants.test("an open mine has no miner"), "a worked-out mine wants nobody");
            h.assertTrue(com.factionclash.campaign.FactionDirector.withArticle("Axe Fighter").equals("an Axe Fighter")
                    && com.factionclash.campaign.FactionDirector.withArticle("Swordsman").equals("a Swordsman"), "articles before a role");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * A mine set aside for good keeps only the ground it really dug from other mines' corridors: nothing when its
     * shaft was never begun, the ground around its stairs when no tunnel was started, its tunnels as far as they were
     * lengthened otherwise; an open or resting mine keeps its whole corridor. It is listed as given up, not worked
     * out, when it was given up, and that survives a save (an older save says so through its note).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void setAsideMinesKeepOnlyTheGroundTheyDug(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            var level = h.getLevel();
            var type = com.factionclash.campaign.WorkplaceState.Type.MINE;
            BlockPos bo = h.absolutePos(new BlockPos(30, 2, 12));
            var b = t.addWorkplace(type, bo);
            b.facing = net.minecraft.core.Direction.EAST;
            b.maxRounds = 4;
            // another mine 60 east and 60 south of it, its tunnels running north across b's second stretch
            var a = t.addWorkplace(type, bo.offset(60, 0, 60));
            a.facing = net.minecraft.core.Direction.NORTH;
            a.maxRounds = 4;
            java.util.function.BooleanSupplier clear = () -> com.factionclash.work.MineSites.corridorClear(level, t.data, b, 1);
            h.assertFalse(clear.getAsBoolean(), "a new mine keeps its whole corridor");
            com.factionclash.work.MineSites.giveUp(level, t.data, a, "its mouth cannot be reached");
            h.assertTrue(a.workedOut && a.givenUp, "a given-up mine is set aside for good");
            h.assertTrue(clear.getAsBoolean(), "a mine given up before its shaft was begun keeps no corridor");
            a.bottom = 10;
            h.assertTrue(clear.getAsBoolean(), "a shaft with no tunnels keeps only the ground by its stairs");
            a.bottom = 60;
            a.levels.add(new com.factionclash.campaign.WorkplaceState.MineLevel(54, 100, 0, com.factionclash.logic.MinePlanner.Band.IRON.ordinal()));
            h.assertTrue(clear.getAsBoolean(), "tunnels never lengthened keep only the first stretch");
            a.levels.get(0).rounds = 4;
            h.assertFalse(clear.getAsBoolean(), "tunnels lengthened four times keep the whole corridor");
            a.levels.get(0).rounds = 0;
            a.workedOut = false;
            a.restTier = 2;
            h.assertFalse(clear.getAsBoolean(), "a resting mine keeps its whole corridor");
            a.restTier = 0;
            a.workedOut = true;
            // the station lists it as given up; a mine retired with every layer dug as worked out
            var c = t.addWorkplace(type, bo.offset(0, 0, -60));
            c.facing = net.minecraft.core.Direction.EAST;
            com.factionclash.work.MineSites.retire(level, t.data, c, com.factionclash.work.MineSites.WORKED_OUT);
            h.assertTrue(c.workedOut && !c.givenUp, "a worked-out mine is not given up");
            String lines = String.join("\n", com.factionclash.work.MineSites.describe(t.data, t.base, level.getMinBuildHeight(), w -> "x"));
            h.assertTrue(lines.contains("Worked-out mines: #" + c.id + "; given up: #" + a.id), "station lines: " + lines);
            CompoundTag tag = a.save();
            h.assertTrue(com.factionclash.campaign.WorkplaceState.load(tag).givenUp, "the flag survives a save");
            tag.remove("givenUp");
            h.assertTrue(com.factionclash.campaign.WorkplaceState.load(tag).givenUp, "an older save's given-up mine is known by its note");
            CompoundTag old = c.save();
            old.remove("givenUp");
            h.assertFalse(com.factionclash.campaign.WorkplaceState.load(old).givenUp, "an older save's worked-out mine stays worked out");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * A barren role is not hired again while the mark lasts: with no woodcutter at the base the director wants one,
     * but not while the woodcutter role is barren there, and again once the mark has run out. The mark survives a
     * save and load, and a base saved before marks existed loads with none.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void barrenRoleIsNotReplaced(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 40);
        try {
            var level = h.getLevel();
            long now = level.getGameTime();
            java.util.function.LongPredicate wantsWoodcutter = at -> com.factionclash.campaign.FactionDirector.needs(level, t.data, t.base, at).stream()
                    .anyMatch(n -> n.role() == NpcRole.WOODCUTTER && n.reason().startsWith("replacing"));
            h.assertTrue(wantsWoodcutter.test(now), "a base without a woodcutter should want one");
            t.base.markBarren(NpcRole.WOODCUTTER, now + 100);
            t.base.markBarren(NpcRole.WOODCUTTER, now + 50); // never shortened
            h.assertTrue(t.base.barrenUntil(NpcRole.WOODCUTTER) == now + 100, "a later mark must not shorten an earlier one");
            h.assertFalse(wantsWoodcutter.test(now), "no woodcutter should be hired while the role is barren");
            h.assertFalse(wantsWoodcutter.test(now + 99), "still barren one tick before the mark runs out");
            h.assertTrue(wantsWoodcutter.test(now + 100), "the base should hire a woodcutter again once the mark ran out");

            var provider = level.registryAccess();
            net.minecraft.nbt.CompoundTag tag = t.base.save(provider);
            BaseState back = BaseState.load(tag, provider);
            h.assertTrue(back.barrenUntil(NpcRole.WOODCUTTER) == now + 100 && back.barrenUntil.size() == 1,
                    "the barren mark should survive a save and load: " + back.barrenUntil);
            h.assertTrue(back.testHalf == -1 && back.testRadius == -1, "test sizes must never be saved");
            tag.remove("barrenUntil");
            h.assertTrue(BaseState.load(tag, provider).barrenUntil.isEmpty(), "an old base without marks should load with none");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * Miners and cave miners leave alone what nobody may dig: a chest, a block of a base's plan (here outside its
     * walls), the rock under a base (here 3 below its floor; deeper in a real world), another mine's stairs, a sculk
     * shrieker and deepslate bricks. Plain stone may be dug, also just beyond the zone under the base, and so may the
     * miner's own mine's stairs (when it clears them).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void minerNeverDigsProtectedBlocks(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 40);
        var level = h.getLevel();
        var type = com.factionclash.campaign.WorkplaceState.Type.MINE;
        try {
            // the planned block lies well outside the walls box (Chebyshev 8 from the centre), so only the plan rule
            // can refuse it; it is plain stone and diggable until it becomes part of the plan
            BlockPos planned = h.absolutePos(new BlockPos(4, 2, 12));
            h.setBlock(new BlockPos(4, 2, 12), Blocks.STONE);
            h.assertTrue(com.factionclash.work.WorkSafety.mayDig(level, planned, t.base, null), "stone outside any plan should be diggable");
            t.base.structures.add(com.factionclash.campaign.StructureRecord.of("Test plan", java.util.Map.of(planned, Blocks.STONE.defaultBlockState())));
            t.base.invalidatePlanIndex();
            // the rock under the base: below its floor and within its walls box + 2 (Chebyshev 4 here), but not one
            // block further out (kept inside the world: the GameTest ground lies a few blocks above its bottom)
            BlockPos under = h.absolutePos(new BlockPos(14, -1, 8)), beside = h.absolutePos(new BlockPos(17, -1, 12));
            h.setBlock(new BlockPos(14, -1, 8), Blocks.STONE);
            h.setBlock(new BlockPos(17, -1, 12), Blocks.STONE);
            var own = t.addWorkplace(type, h.absolutePos(new BlockPos(4, 5, 20)));
            own.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            var other = t.addWorkplace(type, h.absolutePos(new BlockPos(18, 5, 4)));
            other.facing = own.facing;
            BlockPos ownStairs = com.factionclash.structure.MineLayout.local(own, 1, 0, -2);
            BlockPos otherStairs = com.factionclash.structure.MineLayout.local(other, 1, 0, -2);
            level.setBlockAndUpdate(ownStairs, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(otherStairs, Blocks.STONE.defaultBlockState());
            h.setBlock(new BlockPos(20, 2, 8), Blocks.CHEST);
            h.setBlock(new BlockPos(20, 2, 10), Blocks.SCULK_SHRIEKER);
            h.setBlock(new BlockPos(20, 2, 12), Blocks.DEEPSLATE_BRICKS);
            h.setBlock(new BlockPos(20, 2, 16), Blocks.STONE);
            java.util.function.BiPredicate<BlockPos, com.factionclash.campaign.WorkplaceState> may =
                    (p, mine) -> com.factionclash.work.WorkSafety.mayDig(level, p, t.base, mine);
            h.assertTrue(may.test(h.absolutePos(new BlockPos(20, 2, 16)), own), "plain stone should be diggable");
            h.assertFalse(may.test(h.absolutePos(new BlockPos(20, 2, 8)), own), "a chest must not be dug");
            h.assertFalse(may.test(planned, own), "a block of the base's plan must not be dug");
            h.assertFalse(may.test(under, own), "the rock under the base must not be dug");
            h.assertTrue(may.test(beside, own), "the rock just beyond the walls box + 2 may be dug");
            h.assertFalse(may.test(otherStairs, own), "another mine's stairs must not be dug");
            h.assertTrue(may.test(ownStairs, own), "the miner's own stairs may be cleared");
            h.assertFalse(may.test(ownStairs, null), "a cave miner must not dig any mine's stairs");
            h.assertFalse(may.test(h.absolutePos(new BlockPos(20, 2, 10)), own), "a sculk shrieker must not be dug");
            h.assertFalse(may.test(h.absolutePos(new BlockPos(20, 2, 12)), own), "deepslate bricks must not be dug");
            h.assertFalse(com.factionclash.work.WorkSafety.structural(Blocks.STONE.defaultBlockState())
                    || com.factionclash.work.WorkSafety.structural(Blocks.INFESTED_STONE.defaultBlockState())
                    || com.factionclash.work.WorkSafety.structural(Blocks.OAK_PLANKS.defaultBlockState()), "stone, infested stone and planks are not structure blocks");
            h.assertTrue(com.factionclash.work.WorkSafety.structural(Blocks.TUFF_BRICK_STAIRS.defaultBlockState())
                    && com.factionclash.work.WorkSafety.structural(Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState())
                    && com.factionclash.work.WorkSafety.structural(Blocks.REINFORCED_DEEPSLATE.defaultBlockState())
                    && com.factionclash.work.WorkSafety.structural(Blocks.CRACKED_STONE_BRICKS.defaultBlockState()), "structure blocks");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * A woodcutter fells only a natural tree it can take down whole: a trunk touching planks, one with only persistent
     * leaves, one on stone and one too tall to reach from the ground are left standing, and so is a wild tree right
     * next to the walls (the same tree counts as fine where it is not protected wood).
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void treeSurveyLeavesBuiltAndProtectedWood(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 40);
        var level = h.getLevel();
        try {
            BlockPos natural = testTree(h, 3, 19, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, false, Blocks.GRASS_BLOCK);
            BlockPos framed = testTree(h, 9, 19, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, false, Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(10, 3, 19), Blocks.OAK_PLANKS);
            BlockPos hedge = testTree(h, 15, 19, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, true, Blocks.GRASS_BLOCK);
            BlockPos tall = testTree(h, 21, 19, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 10, false, Blocks.GRASS_BLOCK);
            BlockPos onStone = testTree(h, 19, 6, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, false, Blocks.STONE);
            BlockPos byTheWalls = testTree(h, 12, 9, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, false, Blocks.GRASS_BLOCK);
            java.util.function.Function<BlockPos, com.factionclash.logic.TreeShape.Tree> survey =
                    p -> com.factionclash.work.ForestScan.survey(level, t.data, t.base, p, true, false);
            var ok = survey.apply(natural);
            h.assertTrue(ok.ok() && ok.logs().size() == 4 && ok.logs().get(0)[1] == natural.getY() + 3,
                    "a natural oak should be felled whole, top first: " + ok.verdict() + " " + ok.logs().size());
            h.assertTrue(survey.apply(framed).verdict() == com.factionclash.logic.TreeShape.Verdict.TOUCHES_STRUCTURE,
                    "a trunk touching planks is part of a building: " + survey.apply(framed).verdict());
            h.assertTrue(survey.apply(hedge).verdict() == com.factionclash.logic.TreeShape.Verdict.NOT_NATURAL,
                    "a trunk with only persistent leaves is not a natural tree: " + survey.apply(hedge).verdict());
            h.assertTrue(survey.apply(tall).verdict() == com.factionclash.logic.TreeShape.Verdict.TOO_TALL,
                    "a tree with a log 9 above the stump cannot be felled whole: " + survey.apply(tall).verdict());
            h.assertTrue(survey.apply(onStone).verdict() == com.factionclash.logic.TreeShape.Verdict.NOT_NATURAL,
                    "a trunk on stone is not a natural tree: " + survey.apply(onStone).verdict());
            h.assertTrue(com.factionclash.work.WorkSafety.protectedWood(level, t.data, t.base, byTheWalls)
                    && !com.factionclash.work.WorkSafety.protectedWood(level, t.data, t.base, natural), "wood by the walls is protected");
            h.assertTrue(survey.apply(byTheWalls).verdict() == com.factionclash.logic.TreeShape.Verdict.TOUCHES_STRUCTURE,
                    "a wild tree by the walls is left standing: " + survey.apply(byTheWalls).verdict());
            h.assertTrue(com.factionclash.work.ForestScan.survey(level, t.data, t.base, byTheWalls, false, false).ok(),
                    "the same tree is fine where the zone rules do not apply");
            h.assertTrue(level.getBlockState(natural).is(Blocks.OAK_LOG), "a survey never cuts anything");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * A test tree at relative (x, 2, z): {@code height} logs on {@code ground}, with a small canopy of leaves (natural
     * or persistent) around the top log and one block above it. Returns the stump (absolute).
     */
    private static BlockPos testTree(GameTestHelper h, int x, int z, net.minecraft.world.level.block.Block log,
                                     net.minecraft.world.level.block.Block leaves, int height, boolean persistent,
                                     net.minecraft.world.level.block.Block ground) {
        h.setBlock(new BlockPos(x, 1, z), ground);
        for (int y = 0; y < height; y++) {
            h.setBlock(new BlockPos(x, 2 + y, z), log);
        }
        int top = 2 + height - 1;
        var leaf = leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1)
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, persistent);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = top - 1; y <= top + 1; y++) {
                    if ((dx != 0 || dz != 0 || y > top)) {
                        h.setBlock(new BlockPos(x + dx, y, z + dz), leaf);
                    }
                }
            }
        }
        return h.absolutePos(new BlockPos(x, 2, z));
    }

    // ------------------------------------------------------------------ fishing on real water

    /** The farm's irrigation, as the base layout has it: a channel 1 deep and 5 long at relative x 5, z 9..13, farmland on both sides. Returns its water (absolute). */
    private static java.util.Set<BlockPos> farmChannel(GameTestHelper h) {
        java.util.Set<BlockPos> water = new java.util.HashSet<>();
        for (int z = 9; z <= 13; z++) {
            h.setBlock(new BlockPos(4, 1, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(6, 1, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(5, 0, z), Blocks.STONE);
            h.setBlock(new BlockPos(5, 1, z), Blocks.WATER);
            water.add(h.absolutePos(new BlockPos(5, 1, z)));
        }
        return water;
    }

    /** A pond standing on the platform: water at relative y 2..3 inside a stone rim (y 2..3) one block around it. Returns its water (absolute). */
    private static java.util.Set<BlockPos> raisedPond(GameTestHelper h, int x0, int z0, int x1, int z1) {
        java.util.Set<BlockPos> water = new java.util.HashSet<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                for (int y = 2; y <= 3; y++) {
                    h.setBlock(new BlockPos(x, y, z), inside ? Blocks.WATER : Blocks.STONE);
                    if (inside) {
                        water.add(h.absolutePos(new BlockPos(x, y, z)));
                    }
                }
            }
        }
        return water;
    }

    /**
     * A pond dug into the platform: water at relative y 0..1 (its surface level with the floor) over a stone bottom
     * and sides, so a fisherman can walk right up to the bank. Returns its water (absolute).
     */
    private static java.util.Set<BlockPos> sunkenPond(GameTestHelper h, int x0, int z0, int x1, int z1) {
        java.util.Set<BlockPos> water = new java.util.HashSet<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                h.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y <= 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                    water.add(h.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        return water;
    }

    /** Checks a found fishing spot: bobber in {@code pond}, stand on dry solid ground with head room, 2..5 away and 1..2 above. */
    private static void assertGoodSpot(GameTestHelper h, com.factionclash.logic.FishingWater.Spot s, java.util.Set<BlockPos> pond,
                                       java.util.Set<BlockPos> never) {
        var level = h.getLevel();
        h.assertTrue(s != null, "a fishing spot should be found in the pond");
        BlockPos bob = new BlockPos(s.bx(), s.by(), s.bz()), stand = new BlockPos(s.sx(), s.sy(), s.sz());
        h.assertTrue(pond.contains(bob), "the bobber should float on the pond, not at " + bob.toShortString());
        h.assertFalse(never.contains(bob) || never.contains(stand), "never the farm's irrigation: " + bob.toShortString());
        h.assertTrue(level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), net.minecraft.core.Direction.UP),
                "the stand " + stand.toShortString() + " should be on solid ground");
        for (BlockPos p : new BlockPos[]{stand, stand.above()}) {
            h.assertTrue(level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty(),
                    "the stand should be dry, with room for a body at " + p.toShortString());
        }
        int cheb = Math.max(Math.abs(s.sx() - s.bx()), Math.abs(s.sz() - s.bz()));
        h.assertTrue(cheb >= 2 && cheb <= 5 && s.sy() - s.by() >= 1 && s.sy() - s.by() <= 2,
                "the stand should be 2..5 from the bobber and 1..2 above it: " + stand.toShortString() + " / " + bob.toShortString());
    }

    /**
     * The fisherman never fishes the farm's water: next to the irrigation channel (1 deep, 5 blocks, farmland on both
     * sides) and a 7x7 pond, every search (all four grid parities) picks the pond, with a dry stand on its rim.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static", skyAccess = true)
    public static void fishermanNeverPicksFarmIrrigation(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            java.util.Set<BlockPos> channel = farmChannel(h);
            java.util.Set<BlockPos> pond = raisedPond(h, 15, 15, 21, 21);
            for (int i = 0; i < 4; i++) {
                var s = com.factionclash.work.FishingSpots.search(h.getLevel(), t.data, t.base, t.base.center, 11, 11, List.of());
                assertGoodSpot(h, s, pond, channel);
            }
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * An old save's fishing spot on the farm channel loads as not searched yet, keeps its id, worker and catch total,
     * waits at the base centre after the upkeep, and the director's search moves it onto the pond.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static", skyAccess = true)
    public static void legacyFarmFisheryIsRelocated(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            var level = h.getLevel();
            java.util.Set<BlockPos> channel = farmChannel(h);
            java.util.Set<BlockPos> pond = raisedPond(h, 15, 15, 21, 21);
            com.factionclash.campaign.WorkplaceState old = new com.factionclash.campaign.WorkplaceState();
            old.type = com.factionclash.campaign.WorkplaceState.Type.FISHERY;
            old.origin = h.absolutePos(new BlockPos(5, 1, 13)); // the channel's end, as older versions chose
            old.extent = old.origin.offset(0, 1, 1);
            old.assigned = java.util.UUID.randomUUID();
            old.totalOutput = 17;
            t.addWorkplace(old);
            CompoundTag tag = old.save();
            for (String k : new String[]{"fishV", "spot", "open", "searched", "bad"}) {
                tag.remove(k);
            }
            var legacy = com.factionclash.campaign.WorkplaceState.load(tag);
            h.assertTrue(legacy != null && legacy.spot == com.factionclash.logic.FishingWater.UNSEARCHED, "an old fishing spot loads as not searched");
            t.data.workplaces.put(legacy.id, legacy);
            com.factionclash.campaign.BaseBuilder.ensureWorkplaces(t.data, t.base);
            long fisheries = t.base.workplaces.stream().map(t.data::workplace)
                    .filter(w -> w != null && w.type == com.factionclash.campaign.WorkplaceState.Type.FISHERY).count();
            h.assertTrue(fisheries == 1, "the upkeep must not add a second fishing spot: " + fisheries);
            h.assertTrue(legacy.origin.equals(t.base.center) && legacy.extent.equals(t.base.center),
                    "the old spot should wait at the base centre, not on the channel: " + legacy.origin.toShortString());
            com.factionclash.work.FishingSpots.maintain(level, t.data, t.base, level.getGameTime(), true);
            h.assertTrue(legacy.spot == com.factionclash.logic.FishingWater.VALID && pond.contains(legacy.origin) && !channel.contains(legacy.origin),
                    "the director should move the spot onto the pond: " + legacy.origin.toShortString() + " spot " + legacy.spot);
            h.assertTrue(legacy.id == old.id && old.assigned.equals(legacy.assigned) && legacy.totalOutput == 17,
                    "id, worker and catch total are kept");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /** A Lure III rod: the bite comes sooner, so the fishing tests run in reasonable time. */
    private static ItemStack lureRod(GameTestHelper h) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        var reg = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        rod.enchant(reg.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.LURE), 3);
        return rod;
    }

    /** A fisherman with a Lure III rod and the base's fishing spot set on the pond it would find itself. */
    private static FactionNpc fisherAtPond(GameTestHelper h, GameTestBases.Handle t, java.util.Set<BlockPos> pond) {
        var s = com.factionclash.work.FishingSpots.search(h.getLevel(), t.data, t.base, t.base.center, 11, 11, List.of());
        assertGoodSpot(h, s, pond, java.util.Set.of());
        var w = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.FISHERY, t.base.center);
        com.factionclash.work.FishingSpots.apply(t.data, w, s);
        FactionNpc fisher = t.npc(6, 6, NpcRole.FISHERMAN);
        fisher.equip(lureRod(h));
        return fisher;
    }

    /** A fisherman walks to the bank of a pond outside the walls, casts and brings in a catch. */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "fish_catch", skyAccess = true)
    public static void fishermanCatchesAtAPond(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 900);
        java.util.Set<BlockPos> pond = sunkenPond(h, 15, 15, 21, 21);
        FactionNpc fisher;
        try {
            fisher = fisherAtPond(h, t, pond);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            var fishery = t.data.workplaces.values().stream()
                    .filter(x -> x.baseId == t.base.id && x.type == com.factionclash.campaign.WorkplaceState.Type.FISHERY).findFirst().orElseThrow();
            h.assertTrue(fishery.spot == com.factionclash.logic.FishingWater.VALID, "the pond should stay the fishing spot: " + fisher.status());
            if (fishery.totalOutput > 0 && !fisher.inventory().isEmpty()) {
                t.succeed();
            }
        });
    }

    /**
     * A fisherman that just found water at the end of a fruitless walk (a forage leg that could not reach its target,
     * or a spot given up as out of reach) still walks to the new spot and fishes: the failed routes of the earlier
     * walk do not count against it. Its saved FISH state is honoured, so it goes straight to the water.
     */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "fish_resume", skyAccess = true)
    public static void fishermanIsNotPutOffByEarlierFailedRoutes(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 900);
        java.util.Set<BlockPos> pond = sunkenPond(h, 15, 15, 21, 21);
        FactionNpc fisher;
        try {
            fisher = fisherAtPond(h, t, pond);
            com.factionclash.work.Job job = fisher.job();
            CompoundTag saved = new CompoundTag();
            saved.putString("state", "FISH");
            job.load(saved, h.getLevel().registryAccess());
            // the failed routes a forage leg leaves behind (Job keeps them until it arrives somewhere)
            java.lang.reflect.Field failures = com.factionclash.work.Job.class.getDeclaredField("routeFailures");
            failures.setAccessible(true);
            failures.setInt(job, 5);
        } catch (ReflectiveOperationException e) {
            t.cleanup();
            throw new IllegalStateException(e);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        var fishery = t.data.workplaces.values().stream()
                .filter(x -> x.baseId == t.base.id && x.type == com.factionclash.campaign.WorkplaceState.Type.FISHERY).findFirst().orElseThrow();
        t.onEachTick(() -> {
            h.assertTrue(fishery.spot == com.factionclash.logic.FishingWater.VALID && fishery.badSpots.isEmpty(),
                    "the reachable pond must not be given up: " + fisher.status());
            if (fishery.totalOutput > 0 && !fisher.inventory().isEmpty()) {
                t.succeed();
            }
        });
    }

    /**
     * When the water at the spot is gone (the bobber's block turned to stone after the first cast), the fisherman
     * gives the spot up within two casts and remembers it, instead of waiting there for ever. The 5x5 pond is then
     * too small, so its next search finds no other spot and the fishery is marked as having no water within reach.
     */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "fish_gone", skyAccess = true)
    public static void fishermanLeavesWhenTheWaterIsGone(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 900);
        java.util.Set<BlockPos> pond = sunkenPond(h, 16, 16, 20, 20);
        FactionNpc fisher;
        try {
            fisher = fisherAtPond(h, t, pond);
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        var fishery = t.data.workplaces.values().stream()
                .filter(x -> x.baseId == t.base.id && x.type == com.factionclash.campaign.WorkplaceState.Type.FISHERY).findFirst().orElseThrow();
        BlockPos[] gone = new BlockPos[1];
        int[] castsAtGone = new int[1];
        boolean[] givenUp = new boolean[1];
        t.onEachTick(() -> {
            h.assertFalse(fisher.status().contains("has no water"), "the fisherman must not wait at a dry spot: " + fisher.status());
            if (!(fisher.job() instanceof com.factionclash.work.FishermanJob job)) {
                return;
            }
            if (gone[0] == null) {
                if (job.casts() >= 1) {
                    gone[0] = fishery.origin;
                    castsAtGone[0] = job.casts();
                    h.getLevel().setBlockAndUpdate(gone[0], Blocks.STONE.defaultBlockState());
                }
                return;
            }
            if (fishery.spot != com.factionclash.logic.FishingWater.VALID) {
                h.assertTrue(job.casts() <= castsAtGone[0] + 2, "the spot should be given up within two casts, took " + (job.casts() - castsAtGone[0]));
                h.assertTrue(fishery.badSpots.contains(gone[0].asLong()), "the dry spot should be remembered as unfit");
                h.assertFalse(pond.contains(fishery.origin), "no other spot in the 24-block pond: " + fishery.origin.toShortString());
                givenUp[0] = true;
            } else {
                h.assertFalse(givenUp[0], "the 24-block pond must not be taken up again: " + fishery.origin.toShortString());
            }
            if (fishery.spot == com.factionclash.logic.FishingWater.NONE) {
                t.succeed(); // the search after it (no foraging on a test base) found no other water
            }
        });
    }

    /**
     * No fisherman is hired where there is no water to fish: not when the last search found none (until two days
     * later), and not while the role is barren; with known water and low food it is.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void noFishermanRecruitedWithoutWater(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            var level = h.getLevel();
            long now = level.getGameTime();
            FactionStorage.insert(level, t.base, new ItemStack(Items.FISHING_ROD));
            var w = t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.FISHERY, t.base.center);
            java.util.function.Predicate<Long> wantsFisherman = at -> com.factionclash.campaign.FactionDirector.needs(level, t.data, t.base, at)
                    .stream().anyMatch(n -> n.role() == NpcRole.FISHERMAN);
            w.spot = com.factionclash.logic.FishingWater.NONE;
            w.searched = now;
            h.assertFalse(wantsFisherman.test(now), "no fisherman where the last search found no water");
            h.assertTrue(wantsFisherman.test(now + com.factionclash.logic.FishingWater.NONE_MEMORY), "two days later the base may try again");
            w.spot = com.factionclash.logic.FishingWater.VALID;
            h.assertTrue(wantsFisherman.test(now), "with fishing water and little food the base wants a fisherman");
            t.base.markBarren(NpcRole.FISHERMAN, now + 100);
            h.assertFalse(wantsFisherman.test(now), "not while the fisherman's role is barren");
            h.assertTrue(wantsFisherman.test(now + 100), "again once the mark has run out");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * Catches in open water are rolled for a fishing hook (never added to the world), so the vanilla treasure entry
     * applies exactly as for a player; elsewhere they are rolled for the fisherman, which never gets treasure.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void openWaterUsesTheHookForLoot(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.TIDE_ALLIANCE, 2, 11, 40);
        try {
            var level = h.getLevel();
            FactionNpc fisher = t.npc(8, 8, NpcRole.FISHERMAN);
            var open = com.factionclash.work.FishermanJob.lootEntity(true, fisher);
            h.assertTrue(open instanceof net.minecraft.world.entity.projectile.FishingHook hook && hook.isOpenWaterFishing(),
                    "open water rolls the loot for a fishing hook in open water: " + open);
            h.assertTrue(level.getEntity(open.getId()) == null, "the hook is never added to the world");
            h.assertTrue(com.factionclash.work.FishermanJob.lootEntity(false, fisher) == fisher, "elsewhere the fisherman itself");
            var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING);
            java.util.function.Function<net.minecraft.world.entity.Entity, Integer> treasures = looter -> {
                int n = 0;
                for (int i = 0; i < 400; i++) {
                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, fisher.position())
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, looter)
                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.FISHING);
                    for (ItemStack s : table.getRandomItems(params)) {
                        if (s.is(Items.NAME_TAG) || s.is(Items.SADDLE) || s.is(Items.BOW) || s.is(Items.ENCHANTED_BOOK) || s.is(Items.NAUTILUS_SHELL)
                                || (s.is(Items.FISHING_ROD) && s.isEnchanted())) {
                            n++;
                        }
                    }
                }
                return n;
            };
            h.assertTrue(treasures.apply(open) > 0, "400 casts in open water should bring up some treasure (5% each)");
            h.assertTrue(treasures.apply(fisher) == 0, "no treasure outside open water");
        } finally {
            t.cleanup();
        }
        h.succeed();
    }

    /**
     * Workplace records saved by older versions load and save again: a fishing spot from before real-water fishing
     * (its spot on the farm channel) loads as not searched, keeping its id, worker, catch total and place until the
     * upkeep moves it; a hut mine dug in fixed tiers becomes its list of levels at the old depths, still digging down
     * to the next one; an old fenced tree plot becomes a woodland whose fence is to come down, its grid's saplings
     * counted as planted; an unknown workplace type is skipped.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static")
    public static void legacyWorkplacesLoad(GameTestHelper h) {
        java.util.UUID worker = java.util.UUID.randomUUID();
        CompoundTag fish = new CompoundTag();
        fish.putInt("id", 7);
        fish.putInt("base", 3);
        fish.putString("type", "FISHERY");
        fish.putInt("tier", 1);
        BlockPos channel = new BlockPos(115, 63, 8);
        fish.putLong("origin", channel.asLong());
        fish.putString("facing", "east");
        fish.putLong("extent", channel.offset(0, 1, 1).asLong());
        fish.putUUID("assigned", worker);
        fish.putInt("upgradeStep", -1);
        fish.putLong("output", 42);
        fish.putInt("subLevel", 0);
        fish.putInt("progress", 0);
        var w = com.factionclash.campaign.WorkplaceState.load(fish);
        for (int round = 0; round < 2; round++) {
            h.assertTrue(w != null && w.type == com.factionclash.campaign.WorkplaceState.Type.FISHERY && w.id == 7 && w.baseId == 3
                    && worker.equals(w.assigned) && w.totalOutput == 42 && w.origin.equals(channel)
                    && w.spot == com.factionclash.logic.FishingWater.UNSEARCHED && !w.open && w.searched == 0 && w.badSpots.isEmpty(),
                    "an old fishing spot loads as not searched, keeping id, worker, output and place (round " + round + ")");
            CompoundTag saved = w.save();
            h.assertTrue(saved.getInt("fishV") == 1, "a saved fishing spot carries its format marker");
            w = com.factionclash.campaign.WorkplaceState.load(saved);
        }
        // an old hut mine, dug in fixed tiers (tier 3 plus one extra floor, digging down: shaft step 5), becomes
        // its level list at the old landings, keeping the level being dug down to and the shaft step
        CompoundTag oldMine = new CompoundTag();
        oldMine.putInt("id", 8);
        oldMine.putInt("base", 3);
        oldMine.putString("type", "MINE");
        oldMine.putInt("tier", 3);
        BlockPos mineOrigin = new BlockPos(132, 66, 0);
        oldMine.putLong("origin", mineOrigin.asLong());
        oldMine.putString("facing", "east");
        oldMine.putLong("extent", 0L);
        oldMine.putUUID("assigned", worker);
        oldMine.putInt("upgradeStep", 5);
        oldMine.putLong("output", 640);
        oldMine.putInt("subLevel", 1);
        oldMine.putInt("progress", 120);
        oldMine.putInt("reach", 0);
        oldMine.putInt("revisit", 0);
        var m = com.factionclash.campaign.WorkplaceState.load(oldMine);
        for (int round = 0; round < 2; round++) {
            String r = " (round " + round + ")";
            h.assertTrue(m != null && m.type == com.factionclash.campaign.WorkplaceState.Type.MINE && m.id == 8 && m.baseId == 3
                    && worker.equals(m.assigned) && m.totalOutput == 640 && m.origin.equals(mineOrigin), "an old mine keeps id, worker and output" + r);
            java.util.List<Integer> steps = m.levels.stream().map(l -> l.step).toList();
            h.assertTrue(steps.equals(java.util.List.of(6, 34, 66, 114, 122)), "levels at the old landings, then the one dug down to: " + steps + r);
            h.assertTrue(m.hut && m.bottom == 114 && m.cur == 4 && m.upgradeStep == 5 && m.maxRounds == 4 && m.tier == 1 && !m.workedOut,
                    "hut " + m.hut + ", bottom " + m.bottom + ", cur " + m.cur + ", shaft step " + m.upgradeStep + r);
            h.assertTrue(m.levels.get(3).progress == 120 && m.levels.stream().filter(l -> l.progress > 0).count() == 1
                    && m.levels.stream().allMatch(l -> l.band == -1 && l.rounds == 0 && !l.cut), "only the old deepest level keeps its progress" + r);
            CompoundTag saved = m.save();
            h.assertTrue(saved.getInt("mineV") == 1 && !saved.contains("reach") && !saved.contains("revisit") && saved.getInt("progress") == 0,
                    "a saved mine carries its format marker and none of the old tier numbers" + r);
            m = com.factionclash.campaign.WorkplaceState.load(saved);
        }
        // a mine in the new format keeps every field; a level that cannot exist is dropped and the current index follows
        m.levels.add(1, new com.factionclash.campaign.WorkplaceState.MineLevel(0, 9, 0, 1));
        m.cur = 5;
        m.levels.get(2).cut = true;
        m.levels.get(2).band = com.factionclash.logic.MinePlanner.Band.LAPIS.ordinal();
        m.shaftLimit = 118;
        m.restTier = 2;
        m.workedOut = true;
        m.note = "every layer it can reach is dug";
        m.opened = 4321;
        var back2 = com.factionclash.campaign.WorkplaceState.load(m.save());
        h.assertTrue(back2.levels.size() == 5 && back2.cur == 4 && back2.levels.get(1).cut && back2.levels.get(1).band == 2
                && back2.shaftLimit == 118 && back2.restTier == 2 && back2.workedOut && back2.note.equals(m.note) && back2.opened == 4321
                && back2.upgradeStep == 5 && back2.bottom == 114, "mine fields survive a save; levels " + back2.levels.size() + ", cur " + back2.cur);
        // an old fenced tree plot (tier 2, part-way through an enlargement) becomes a woodland whose fence is to come
        // down, with no enlargement under way and the saplings of its old grid counted as planted
        CompoundTag oldPlot = new CompoundTag();
        oldPlot.putInt("id", 9);
        oldPlot.putInt("base", 3);
        oldPlot.putString("type", "TREE_PLOT");
        oldPlot.putInt("tier", 2);
        BlockPos plotOrigin = new BlockPos(100, 64, 0);
        oldPlot.putLong("origin", plotOrigin.asLong());
        oldPlot.putString("facing", "west");
        oldPlot.putLong("extent", 0L);
        oldPlot.putUUID("assigned", worker);
        oldPlot.putInt("upgradeStep", 3);
        oldPlot.putLong("output", 512);
        oldPlot.putInt("subLevel", 0);
        oldPlot.putInt("progress", 0);
        var wl = com.factionclash.campaign.WorkplaceState.load(oldPlot);
        com.factionclash.campaign.WorkplaceState grid = new com.factionclash.campaign.WorkplaceState();
        grid.origin = plotOrigin;
        grid.facing = net.minecraft.core.Direction.WEST;
        java.util.List<Long> oldGrid = com.factionclash.structure.TreePlotLayout.spots(grid, 2).stream().map(BlockPos::asLong).toList();
        for (int round = 0; round < 2; round++) {
            String r = " (round " + round + ")";
            h.assertTrue(wl != null && wl.type == com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT && wl.id == 9 && wl.baseId == 3
                    && worker.equals(wl.assigned) && wl.totalOutput == 512 && wl.origin.equals(plotOrigin)
                    && wl.facing == net.minecraft.core.Direction.WEST, "an old tree plot keeps id, worker, output and place" + r);
            h.assertTrue(wl.legacyFence && wl.upgradeStep == -1 && wl.tier == 1 && wl.felled == 0 && wl.replant.isEmpty(),
                    "an old plot's fence is to come down, with no enlargement: fence " + wl.legacyFence + ", step " + wl.upgradeStep + r);
            h.assertTrue(wl.planted.equals(oldGrid) && oldGrid.size() == 9, "the old grid's 9 spots count as planted: " + wl.planted.size() + r);
            CompoundTag saved = wl.save();
            h.assertTrue(saved.getInt("woodV") == 1 && saved.getInt("upgradeStep") == -1, "a saved woodland carries its format marker" + r);
            wl = com.factionclash.campaign.WorkplaceState.load(saved);
        }
        // a woodland in the new format keeps every field; the lists keep their newest entries
        wl.legacyFence = false;
        wl.felled = 17;
        for (int i = 0; i < 70; i++) {
            wl.addPlanted(plotOrigin.offset(i, 0, 5));
        }
        for (int i = 0; i < 40; i++) {
            wl.addReplant(new com.factionclash.campaign.WorkplaceState.Replant(plotOrigin.offset(i, 0, -5).asLong(),
                    i % 2 == 0 ? "DARK_OAK" : "BIRCH", i % 2 == 0, 1000 + i));
        }
        wl.addReplant(new com.factionclash.campaign.WorkplaceState.Replant(plotOrigin.offset(39, 0, -5).asLong(), "SPRUCE", false, 5000));
        var wl2 = com.factionclash.campaign.WorkplaceState.load(wl.save());
        h.assertTrue(!wl2.legacyFence && wl2.felled == 17 && wl2.planted.size() == com.factionclash.campaign.WorkplaceState.MAX_PLANTED
                && wl2.planted.get(0) == plotOrigin.offset(6, 0, 5).asLong(), "planted keeps the newest 64: " + wl2.planted.size());
        var lastEntry = wl2.replant.get(wl2.replant.size() - 1);
        h.assertTrue(wl2.replant.size() == com.factionclash.campaign.WorkplaceState.MAX_REPLANT
                && wl2.replant.get(0).pos() == plotOrigin.offset(8, 0, -5).asLong() && wl2.replant.get(0).species().equals("DARK_OAK")
                && wl2.replant.get(0).big() && wl2.replant.get(0).since() == 1008
                && lastEntry.species().equals("SPRUCE") && lastEntry.since() == 5000
                && wl2.replant.stream().filter(e -> e.pos() == plotOrigin.offset(39, 0, -5).asLong()).count() == 1,
                "the replant queue keeps the newest 32, one entry per place: " + wl2.replant.size());
        var v = com.factionclash.campaign.WorkplaceState.load(fish);
        v.spot = com.factionclash.logic.FishingWater.NONE;
        v.searched = 1234;
        v.open = true;
        for (int i = 0; i < 40; i++) {
            v.addBadSpot(channel.offset(i, 0, 0));
        }
        var back = com.factionclash.campaign.WorkplaceState.load(v.save());
        h.assertTrue(back.spot == com.factionclash.logic.FishingWater.NONE && back.searched == 1234 && back.open
                && back.badSpots.size() == com.factionclash.campaign.WorkplaceState.MAX_BAD_SPOTS
                && back.badSpots.get(0) == channel.offset(8, 0, 0).asLong(), "fishing spot fields survive a save, the oldest unfit spots dropped first");
        CompoundTag unknown = fish.copy();
        unknown.putString("type", "WINDMILL");
        h.assertTrue(com.factionclash.campaign.WorkplaceState.load(unknown) == null, "an unknown workplace type is skipped");
        h.succeed();
    }

    // ------------------------------------------------------------------ the fenceless woodland

    /** A woodcutter of a registered test base, with a fenceless woodland it holds. */
    private static FactionNpc testWoodcutter(GameTestBases.Handle t, int x, int z, ItemStack axe) {
        com.factionclash.campaign.WorkplaceState woodland = new com.factionclash.campaign.WorkplaceState();
        woodland.type = com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT;
        woodland.origin = t.base.center;
        woodland.extent = t.base.center;
        t.addWorkplace(woodland);
        FactionNpc n = t.npc(x, z, NpcRole.WOODCUTTER);
        n.equip(axe);
        woodland.assigned = n.getUUID();
        return n;
    }

    private static com.factionclash.campaign.WorkplaceState woodlandOf(GameTestBases.Handle t) {
        for (int id : t.base.workplaces) {
            var w = t.data.workplace(id);
            if (w != null && w.type == com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT) {
                return w;
            }
        }
        return null;
    }

    /** Items of this kind in the NPC's backpack and the test base's storehouse. */
    private static int heldOrStored(GameTestBases.Handle t, FactionNpc n, net.minecraft.world.item.Item item) {
        return com.factionclash.work.Inv.count(n.inventory(), x -> x.is(item))
                + com.factionclash.storage.FactionStorage.count(t.helper.getLevel(), t.base, x -> x.is(item));
    }

    /** True while any log of {@code block} stands in the box from (x0, 2, z0) to (x1, 9, z1) (relative). */
    private static boolean logsIn(GameTestHelper h, int x0, int z0, int x1, int z1, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, 2, z0, x1, 9, z1)) {
            if (h.getBlockState(p).is(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A woodcutter fells a natural birch outside the walls whole, top first, with its axe, clears its canopy, and
     * plants a birch sapling where it stood (it also carries oak saplings): the woodland counts the tree and the
     * sapling.
     */
    @GameTest(template = "platform", timeoutTicks = 1200, batch = "wood_fell", skyAccess = true)
    public static void woodcutterFellsATreeAndReplantsTheSameSpecies(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 1200);
        FactionNpc n;
        BlockPos stump;
        try {
            stump = testTree(h, 18, 18, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, false, Blocks.GRASS_BLOCK);
            n = testWoodcutter(t, 8, 12, new ItemStack(Items.STONE_AXE));
            n.inventory().setItem(0, new ItemStack(Items.BIRCH_SAPLING, 2));
            n.inventory().setItem(1, new ItemStack(Items.OAK_SAPLING, 2));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            var w = woodlandOf(t);
            if (w == null || w.felled < 1 || !h.getLevel().getBlockState(stump).is(Blocks.BIRCH_SAPLING)) {
                return;
            }
            t.assertTrue(!logsIn(h, 15, 15, 21, 21, Blocks.BIRCH_LOG), "no birch log should be left of the tree");
            t.assertTrue(heldOrStored(t, n, Items.BIRCH_LOG) >= 5, "the 5 birch logs should be in the backpack or the storehouse: "
                    + heldOrStored(t, n, Items.BIRCH_LOG));
            t.assertTrue(w.planted.contains(stump.asLong()), "the woodland should count the new sapling as planted");
            t.assertTrue(w.felled == 1 && w.replant.isEmpty(), "one tree felled (" + w.felled + "), no stump left waiting: " + w.replant.size());
            t.succeed();
        });
    }

    /**
     * A woodcutter leaves wood that is not a natural tree standing whole: a log pillar with persistent leaves (a
     * hedge or decoration), a birch on stone, and an oak whose branch touches planks (part of something built). It
     * fells the plain natural oak.
     */
    @GameTest(template = "platform", timeoutTicks = 1000, batch = "wood_protect", skyAccess = true)
    public static void woodcutterLeavesProtectedWoodAlone(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 1000);
        FactionNpc n;
        BlockPos framed;
        try {
            testTree(h, 19, 6, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, true, Blocks.GRASS_BLOCK);     // (a) persistent leaves
            testTree(h, 19, 18, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, false, Blocks.STONE);     // (b) on stone
            framed = testTree(h, 12, 19, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, false, Blocks.GRASS_BLOCK); // (c) touches planks
            h.setBlock(new BlockPos(13, 4, 19), Blocks.OAK_LOG.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X)); // a branch
            h.setBlock(new BlockPos(14, 4, 19), Blocks.OAK_PLANKS);
            testTree(h, 5, 18, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, false, Blocks.GRASS_BLOCK);    // (d) plain natural oak
            n = testWoodcutter(t, 8, 12, new ItemStack(Items.STONE_AXE));
            n.inventory().setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            t.assertTrue(logsIn(h, 19, 6, 19, 6, Blocks.OAK_LOG), "the log pillar with persistent leaves should stand");
            t.assertTrue(logsIn(h, 19, 18, 19, 18, Blocks.BIRCH_LOG), "the birch on stone should stand");
            t.assertTrue(logsIn(h, 12, 19, 13, 19, Blocks.OAK_LOG), "the oak touching planks should stand");
            for (int y = 2; y <= 5; y++) {
                t.assertTrue(h.getBlockState(new BlockPos(12, y, 19)).is(Blocks.OAK_LOG), "every log of the oak touching planks should stand");
            }
            var w = woodlandOf(t);
            boolean natural = logsIn(h, 5, 18, 5, 18, Blocks.OAK_LOG);
            if (!natural && w != null && w.felled == 1 && n.job() instanceof com.factionclash.work.WoodcutterJob job && job.gaveUp(framed)) {
                t.succeed();
            }
        });
    }

    /**
     * An old save's fenced tree plot: its woodcutter takes the whole fence down with its axe (the posts go into its
     * backpack, for the storehouse) and the woodland no longer has a fence.
     */
    @GameTest(template = "platform", timeoutTicks = 1500, batch = "wood_fence")
    public static void woodcutterDismantlesAnOldFence(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.STONEGUARD, 2, 11, 1500);
        FactionNpc n;
        java.util.List<BlockPos> ring;
        com.factionclash.campaign.WorkplaceState plot = new com.factionclash.campaign.WorkplaceState();
        try {
            plot.type = com.factionclash.campaign.WorkplaceState.Type.TREE_PLOT;
            plot.origin = h.absolutePos(new BlockPos(5, 2, 12));
            plot.extent = plot.origin;
            plot.facing = h.getTestRotation().rotate(net.minecraft.core.Direction.EAST);
            plot.legacyFence = true;
            t.addWorkplace(plot);
            ring = com.factionclash.structure.TreePlotLayout.fence(plot, 1);
            h.assertTrue(ring.size() == 47, "a tier-1 ring has 47 posts, not " + ring.size());
            for (BlockPos p : ring) {
                h.getLevel().setBlockAndUpdate(p, Blocks.OAK_FENCE.defaultBlockState());
            }
            n = t.npc(9, 12, NpcRole.WOODCUTTER);
            n.equip(new ItemStack(Items.IRON_AXE));
            plot.assigned = n.getUUID();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            if (plot.legacyFence) {
                return;
            }
            for (BlockPos p : ring) {
                t.assertTrue(!h.getLevel().getBlockState(p).is(Blocks.OAK_FENCE), "a fence post is left at " + p.toShortString());
            }
            t.assertTrue(heldOrStored(t, n, Items.OAK_FENCE) >= 47, "every post should be in the backpack or the storehouse: "
                    + heldOrStored(t, n, Items.OAK_FENCE));
            t.succeed();
        });
    }

    /**
     * A woodcutter's job, saved after the first log of a tree is cut, loads with the tree it was felling and goes on
     * chopping it; a job saved by the fenced-plot version (no format number, state UPGRADE) starts over without its spot.
     */
    @GameTest(template = "platform", timeoutTicks = 600, batch = "wood_resume", skyAccess = true)
    public static void woodcutterResumesAfterReload(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 600);
        FactionNpc n;
        BlockPos stump;
        try {
            stump = testTree(h, 18, 17, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 5, false, Blocks.GRASS_BLOCK);
            n = testWoodcutter(t, 15, 15, new ItemStack(Items.STONE_AXE));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        var provider = h.getLevel().registryAccess();
        t.onEachTick(() -> {
            if (!(n.job() instanceof com.factionclash.work.WoodcutterJob job) || job.felling() == null) {
                return;
            }
            t.assertTrue(job.felling().equals(stump), "it should be felling the oak: " + job.felling());
            CompoundTag saved = new CompoundTag();
            job.save(saved, provider);
            var again = new com.factionclash.work.WoodcutterJob(n);
            again.load(saved, provider);
            t.assertTrue(stump.equals(again.felling()) && stump.equals(again.spot()) && again.stateName().equals("CHOP"),
                    "the loaded job should go on felling the oak: " + again.stateName() + " " + again.spot() + " " + again.felling());
            t.assertTrue(saved.getInt("v") == 2, "the job is saved in the current format");
            CompoundTag old = new CompoundTag();
            old.putString("state", "UPGRADE");
            old.putLong("spot", stump.asLong());
            old.putInt("progress", 3);
            old.putBoolean("wild", true);
            var oldJob = new com.factionclash.work.WoodcutterJob(n);
            oldJob.load(old, provider);
            t.assertTrue(oldJob.stateName().equals("CHECK") && oldJob.spot() == null,
                    "an old job starts over without its spot: " + oldJob.stateName() + " " + oldJob.spot());
            CompoundTag oldChop = old.copy();
            oldChop.putString("state", "CHOP");
            oldJob.load(oldChop, provider);
            t.assertTrue(oldJob.stateName().equals("CHECK") && oldJob.spot() == null, "an old mid-cut tree is not resumed as a plot tree");
            t.succeed();
        });
    }

    /**
     * A woodcutter goes back to a tree it had started to fell even when nothing of it is left to cut (its last logs
     * went while it was away unloading, or its chunk was not loaded when it decided): it finishes the tree there,
     * picking up the drops around the stump, and does not count the tree a second time.
     */
    @GameTest(template = "platform", timeoutTicks = 600, batch = "wood_return", skyAccess = true)
    public static void woodcutterGoesBackToAStartedTree(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 600);
        FactionNpc n;
        BlockPos stump = h.absolutePos(new BlockPos(18, 2, 18));
        try {
            h.setBlock(new BlockPos(18, 1, 18), Blocks.GRASS_BLOCK);
            n = testWoodcutter(t, 6, 12, new ItemStack(Items.STONE_AXE));
            // what the felled tree dropped, lying around its stump, well away from the woodcutter
            h.spawnItem(Items.BIRCH_SAPLING, 16.5f, 2.2f, 18.5f);
            h.spawnItem(Items.STICK, 19.5f, 2.2f, 20.5f);
            h.spawnItem(Items.APPLE, 18.5f, 2.2f, 15.5f);
            // the job as saved while it was on its way to unload, half-way through that tree
            CompoundTag tag = new CompoundTag();
            tag.putInt("v", 2);
            tag.putString("state", "CHECK");
            tag.putLong("felling", stump.asLong());
            n.job().load(tag, h.getLevel().registryAccess());
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            var w = woodlandOf(t);
            if (w == null) {
                return; // cleaned up just before the timeout
            }
            t.assertTrue(w.felled == 0 && w.replant.isEmpty(), "a tree with no stump left is not counted again");
            if (!(n.job() instanceof com.factionclash.work.WoodcutterJob job) || job.felling() != null) {
                return;
            }
            var left = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(stump).inflate(4, 2, 4), net.minecraft.world.entity.Entity::isAlive);
            if (left.isEmpty()) {
                t.succeed(); // it went back, found nothing left to cut, and picked up what lay around the stump
            }
        });
    }

    /**
     * Grove spots follow the rules: on the 5-block lattice, inside the work belt, at least 4 beyond the walls, off the
     * road strip, on soil under open sky (never farmland), with nothing growing within 2 blocks, away from a mine, and
     * within the forest radius. Of six lattice spots given grass or farmland, only the one that passes every rule is
     * offered (and not while the forest radius stops short of it); once it holds a sapling nothing is.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "workers_static", skyAccess = true)
    public static void groveSpotsFollowTheRules(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 40);
        var level = h.getLevel();
        try {
            // a wider work belt than a test base has, so the grove lattice (walls + 4 = Chebyshev 6) fits on the platform
            t.base.points.put(BaseState.MIN, t.base.center.offset(-11, -1, -11));
            t.base.points.put(BaseState.MAX, t.base.center.offset(11, 8, 11));
            h.setBlock(new BlockPos(22, 1, 17), Blocks.GRASS_BLOCK); // dx 10, dz 5: every rule holds
            h.setBlock(new BlockPos(17, 1, 22), Blocks.FARMLAND);     // farmland
            h.setBlock(new BlockPos(17, 1, 17), Blocks.GRASS_BLOCK); // Chebyshev 5: too close to the walls
            h.setBlock(new BlockPos(22, 1, 12), Blocks.GRASS_BLOCK); // on the road strip
            h.setBlock(new BlockPos(22, 1, 7), Blocks.GRASS_BLOCK);  // a sapling grows next to it
            h.setBlock(new BlockPos(21, 2, 7), Blocks.OAK_SAPLING);
            h.setBlock(new BlockPos(2, 1, 22), Blocks.GRASS_BLOCK);  // a mine opens beside it
            t.addWorkplace(com.factionclash.campaign.WorkplaceState.Type.MINE, h.absolutePos(new BlockPos(4, 2, 19)));
            // a grove tree is felled like any other, so no grove spot lies beyond the forest radius (the fitting
            // spot is 11.2 from the centre)
            t.base.testRadius = 11;
            h.assertTrue(new com.factionclash.work.ForestScan.GroveSearch().next(level, t.data, t.base) == null,
                    "no grove spot beyond the forest radius");
            t.base.testRadius = 15;
            var search = new com.factionclash.work.ForestScan.GroveSearch();
            BlockPos found = search.next(level, t.data, t.base);
            h.assertTrue(h.absolutePos(new BlockPos(22, 2, 17)).equals(found), "the one fitting grove spot should be offered, not " + found);
            h.assertTrue(search.next(level, t.data, t.base).equals(found), "a found spot is kept while it fits");
            level.setBlockAndUpdate(found, Blocks.BIRCH_SAPLING.defaultBlockState());
            h.assertTrue(search.next(level, t.data, t.base) == null, "with the spot planted nothing else fits");
            h.assertTrue(com.factionclash.work.WorkSafety.reserved(t.data, t.base, h.absolutePos(new BlockPos(2, 2, 22)),
                    com.factionclash.work.WorkSafety.Use.GROVE), "a grove keeps 6 away from a mine");
            h.assertTrue(com.factionclash.work.WorkSafety.reserved(t.data, t.base, h.absolutePos(new BlockPos(22, 2, 12)),
                    com.factionclash.work.WorkSafety.Use.GROVE), "the road strip is reserved");
        } finally {
            t.cleanup();
        }
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

    // ------------------------------------------------------------------ scouts: monsters and structures

    /** Sends a scout out toward {@code target} (absolute), as if it had just left home: its saved form, loaded. */
    private static com.factionclash.work.ScoutJob sendScout(GameTestHelper h, FactionNpc n, BlockPos target) {
        var job = (com.factionclash.work.ScoutJob) n.job();
        CompoundTag tag = new CompoundTag();
        tag.putString("state", "OUTBOUND");
        tag.putLong("target", target.asLong());
        tag.putLong("departed", h.getLevel().getGameTime());
        job.load(tag, h.getLevel().registryAccess());
        return job;
    }

    /** A zombie that stands still (no AI) and wears a helmet against the sun, discarded when the test base is cleaned up. */
    private static Zombie stillZombie(GameTestBases.Handle t, int x, int z) {
        Zombie zb = t.track(t.helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z)));
        zb.setNoAi(true);
        zb.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zb;
    }

    private static double nearest(FactionNpc n, List<? extends net.minecraft.world.entity.Entity> others) {
        double d = Double.MAX_VALUE;
        for (var e : others) {
            d = Math.min(d, n.distanceTo(e));
        }
        return d;
    }

    /**
     * Zombies next to a scout on its way to far-off land do not send it home: it runs to a point clear of them, on
     * the side of where it is going, and goes on exploring - it never turns back, ends well away from them, and then
     * walks on toward its target.
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "scout_evade", skyAccess = true)
    public static void scoutStepsAsideFromZombiesAndKeepsExploring(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 400);
        FactionNpc n;
        List<Zombie> zombies;
        com.factionclash.work.ScoutJob job;
        try {
            n = t.npc(18, 6, NpcRole.SCOUT);
            n.equip(new ItemStack(Items.IRON_SWORD));
            zombies = List.of(stillZombie(t, 21, 3), stillZombie(t, 22, 5), stillZombie(t, 20, 2));
            job = sendScout(h, n, h.absolutePos(new BlockPos(18, 2, 400))); // far to the south
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        double start = nearest(n, zombies);
        double[] clearAt = {Double.NaN};
        t.onEachTick(() -> {
            t.assertTrue(!job.stateName().equals("RETURN") && !job.isRetreating(), "the scout turned back: " + job.workplaceInfo()
                    + " / " + n.status());
            t.assertTrue(n.getTarget() == null || !(n.getTarget() instanceof Zombie), "the scout took up a fight with a zombie");
            if (Double.isNaN(clearAt[0]) && job.evasions() >= 1 && !job.isEvading() && job.stateName().equals("OUTBOUND")) {
                double d = nearest(n, zombies);
                t.assertTrue(d > 12 && d > start + 6, "should have got clear of the zombies: " + String.format("%.1f", d)
                        + " blocks (started " + String.format("%.1f", start) + ")");
                FactionClash.LOGGER.info("GAMETEST scout stepped aside {} time(s) by tick {}: {} -> {} blocks from the zombies, now at {} ({})",
                        job.evasions(), h.getTick(), String.format("%.1f", start), String.format("%.1f", d), h.relativePos(n.blockPosition()), n.status());
                clearAt[0] = n.getZ();
            }
            // then on toward the target, 400 blocks to the south: exploring, not standing about or circling back
            if (!Double.isNaN(clearAt[0]) && n.getZ() >= clearAt[0] + 8) {
                t.assertTrue(job.stateName().equals("OUTBOUND") && !job.isEvading(), "should be exploring again: " + job.workplaceInfo());
                t.succeed();
            }
        });
    }

    /**
     * Line of sight matters: a skeleton behind a wall, 9 to 14 blocks away (inside an archer's 18-block keep-away
     * distance, but not within 6), cannot shoot the scout, so the scout walks on past it without stepping aside.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "scout_sight", skyAccess = true)
    public static void scoutWalksOnPastAMonsterThatCannotSeeIt(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 200);
        FactionNpc n;
        com.factionclash.work.ScoutJob job;
        try {
            for (int x = 0; x < 24; x++) {
                for (int y = 2; y <= 5; y++) {
                    h.setBlock(new BlockPos(x, y, 12), Blocks.STONE);
                }
            }
            n = t.npc(3, 17, NpcRole.SCOUT);
            n.equip(new ItemStack(Items.IRON_SWORD));
            Skeleton sk = t.track(h.spawn(EntityType.SKELETON, new BlockPos(12, 2, 8)));
            sk.setNoAi(true);
            sk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            job = sendScout(h, n, h.absolutePos(new BlockPos(400, 2, 17))); // far to the east, along the wall
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        int x0 = h.absolutePos(BlockPos.ZERO).getX();
        t.onEachTick(() -> {
            t.assertTrue(job.evasions() == 0, "stepped aside from a skeleton that cannot see it: " + n.status());
            t.assertTrue(job.stateName().equals("OUTBOUND"), "the scout should carry on exploring: " + job.workplaceInfo());
            if (n.getX() - x0 >= 18) {
                t.succeed(); // walked past the skeleton
            }
        });
    }

    /** Badly hurt (under 40%), a scout does not step aside from a monster: it retreats home. */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "scout_hurt", skyAccess = true)
    public static void badlyHurtScoutRetreatsFromAMonster(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 200);
        FactionNpc n;
        com.factionclash.work.ScoutJob job;
        try {
            n = t.npc(18, 6, NpcRole.SCOUT);
            n.equip(new ItemStack(Items.IRON_SWORD));
            n.setHealth(n.getMaxHealth() * 0.35F);
            stillZombie(t, 20, 6);
            job = sendScout(h, n, h.absolutePos(new BlockPos(18, 2, 400)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            t.assertTrue(!job.isEvading(), "a badly hurt scout should not step aside but go home");
            if (job.stateName().equals("RETURN")) {
                t.assertTrue(job.isRetreating(), "it should retreat (at speed, ignoring attacks), not just return: " + job.workplaceInfo());
                t.assertTrue(job.workplaceInfo().contains("badly hurt"), "the reason should say it was badly hurt: " + job.workplaceInfo());
                t.succeed();
            }
        });
    }

    /** Enemy soldiers still make a scout retreat home with its report, as before (monsters are stepped around). */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "scout_soldiers", skyAccess = true)
    public static void enemySoldiersStillSendAScoutHome(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.GROVE_COVENANT, 2, 11, 200);
        FactionNpc n;
        com.factionclash.work.ScoutJob job;
        try {
            n = t.npc(18, 6, NpcRole.SCOUT);
            n.equip(new ItemStack(Items.IRON_SWORD));
            for (int z : new int[]{5, 8}) {
                FactionNpc s = t.track(npc(h, 21, z, FactionType.EMBER_LEGION, NpcRole.SWORDSMAN));
                s.setNoAi(true);
            }
            job = sendScout(h, n, h.absolutePos(new BlockPos(18, 2, 400)));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
        t.onEachTick(() -> {
            if (job.stateName().equals("RETURN")) {
                t.assertTrue(job.isRetreating() && job.workplaceInfo().contains("hostile"), "expected a retreat from the soldiers: "
                        + job.workplaceInfo());
                t.succeed();
            }
        });
    }

    /**
     * Structures cannot be generated in the flat test world, so their rules are checked as they stand: every village
     * kind (by the village tag), pillager outposts and woodland mansions are recognised through the real structure
     * registry, other structures are not; two looks at one village merge into one report that keeps the traders seen
     * (a structure-only report of an abandoned village never counts as a trading village); outposts and mansions are
     * dangerous ground; the new kinds survive saving and have their own map markers.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void structureReportsClassifyAndMerge(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure>, com.factionclash.campaign.Report.Kind> expect =
                new java.util.LinkedHashMap<>();
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_PLAINS, com.factionclash.campaign.Report.Kind.VILLAGE);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_DESERT, com.factionclash.campaign.Report.Kind.VILLAGE);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_SAVANNA, com.factionclash.campaign.Report.Kind.VILLAGE);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_SNOWY, com.factionclash.campaign.Report.Kind.VILLAGE);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_TAIGA, com.factionclash.campaign.Report.Kind.VILLAGE);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.PILLAGER_OUTPOST, com.factionclash.campaign.Report.Kind.PILLAGER_OUTPOST);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.WOODLAND_MANSION, com.factionclash.campaign.Report.Kind.WOODLAND_MANSION);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.STRONGHOLD, null);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.MINESHAFT, null);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.ANCIENT_CITY, null);
        expect.put(net.minecraft.world.level.levelgen.structure.BuiltinStructures.TRIAL_CHAMBERS, null);
        expect.forEach((key, kind) -> {
            var structure = registry.getOrThrow(key);
            h.assertTrue(com.factionclash.work.StructureSightings.classify(registry, structure) == kind,
                    key.location() + " should be " + kind + ", got " + com.factionclash.work.StructureSightings.classify(registry, structure));
        });

        com.factionclash.campaign.Report.Kind V = com.factionclash.campaign.Report.Kind.VILLAGE;
        com.factionclash.campaign.FactionState f = new com.factionclash.campaign.FactionState(FactionType.STONEGUARD);
        // the scout's two looks on one trip: from afar (the structure, no traders seen), then among the traders
        var glance = new com.factionclash.campaign.Report(V, new BlockPos(1000, 64, 1000), 100, null, 0, "plains village, no villagers with trades seen");
        var among = new com.factionclash.campaign.Report(V, new BlockPos(1030, 64, 1040), 140, null, 4, "plains village, 4 villagers with trades (farmer)");
        h.assertTrue(glance.sameSubject(among), "two looks 50 blocks apart at one village should be one subject");
        var trip = com.factionclash.work.ScoutJob.mergeOnTrip(com.factionclash.work.ScoutJob.mergeOnTrip(glance, among), glance);
        h.assertTrue(trip.count == 4 && trip.tradingVillage(), "a later glance from afar should keep the 4 traders seen: " + trip.count);
        // delivered: an older trader count arriving after a newer structure-only look still counts, merged into one
        h.assertTrue(f.addReport(glance), "the first village report is new");
        h.assertTrue(!f.addReport(among.copy()), "the second look is the same village");
        var old = new com.factionclash.campaign.Report(V, new BlockPos(1010, 64, 990), 50, null, 3, "3 villagers with trades (mason)");
        h.assertTrue(!f.addReport(old), "an old trader count of the same village is not a new report");
        long villages = f.knowledge.stream().filter(r -> r.kind == V).count();
        h.assertTrue(villages == 1, "one village should be on the map, not " + villages);
        var known = f.knowledge.stream().filter(r -> r.kind == V).findFirst().orElseThrow();
        h.assertTrue(known.count == 4 && known.tradingVillage(), "the village should keep its 4 traders: " + known.count + " (" + known.note + ")");
        var later = new com.factionclash.campaign.Report(V, new BlockPos(1000, 64, 1000), 500, null, 0, "plains village, no villagers with trades seen");
        f.addReport(later);
        known = f.knowledge.stream().filter(r -> r.kind == V).findFirst().orElseThrow();
        h.assertTrue(known.count == 4 && known.observedAt == 500, "a newer look without traders refreshes the report but keeps the traders");
        // an abandoned village elsewhere: on the map, but no trader goes there
        f.addReport(new com.factionclash.campaign.Report(V, new BlockPos(2000, 64, 2000), 200, null, 0, "desert village, abandoned (2 zombie villagers), no villagers with trades"));
        long trading = f.knowledge.stream().filter(com.factionclash.campaign.Report::tradingVillage).count();
        h.assertTrue(f.knowledge.stream().filter(r -> r.kind == V).count() == 2 && trading == 1,
                "two villages, one with traders: " + f.knowledge.size() + " reports, " + trading + " trading");
        // outposts and mansions: one report per structure, dangerous ground around it, never stale, saved by name
        var outpost = new com.factionclash.campaign.Report(com.factionclash.campaign.Report.Kind.PILLAGER_OUTPOST, new BlockPos(3000, 70, 3000), 300, null, 3, "3 pillagers seen");
        h.assertTrue(f.addReport(outpost), "the outpost is new");
        h.assertTrue(!f.addReport(new com.factionclash.campaign.Report(com.factionclash.campaign.Report.Kind.PILLAGER_OUTPOST, new BlockPos(3040, 70, 3010), 320, null, 1, "1 pillager seen")),
                "a second look at the outpost is not a second outpost");
        h.assertTrue(com.factionclash.campaign.BaseZones.nearKnownDanger(f, new BlockPos(3040, 0, 3040), com.factionclash.campaign.BaseZones.DANGER_RADIUS),
                "48 blocks around the outpost are dangerous ground");
        h.assertTrue(!com.factionclash.campaign.BaseZones.nearKnownDanger(f, new BlockPos(3100, 0, 3000), com.factionclash.campaign.BaseZones.DANGER_RADIUS),
                "100 blocks away is not");
        h.assertTrue(!com.factionclash.campaign.BaseZones.nearKnownDanger(f, new BlockPos(1000, 0, 1000), com.factionclash.campaign.BaseZones.DANGER_RADIUS),
                "a village is not dangerous ground");
        f.addReport(new com.factionclash.campaign.Report(com.factionclash.campaign.Report.Kind.WOODLAND_MANSION, new BlockPos(5000, 70, 5000), 300, null, 0, "no illagers seen"));
        h.assertTrue(com.factionclash.campaign.BaseZones.nearKnownDanger(f, new BlockPos(4960, 0, 5030), com.factionclash.campaign.BaseZones.DANGER_RADIUS),
                "by the mansion is dangerous ground");
        // a long campaign's flood of reports forgets old ore and monsters first, never the places on the map
        for (int i = 0; i < com.factionclash.campaign.FactionState.MAX_REPORTS + 20; i++) {
            f.addReport(new com.factionclash.campaign.Report(com.factionclash.campaign.Report.Kind.ORE, new BlockPos(10_000 + i * 100, 40, 0),
                    600 + i, null, 1, "Iron Ore"));
        }
        h.assertTrue(f.knowledge.size() == com.factionclash.campaign.FactionState.MAX_REPORTS, "the report cap holds: " + f.knowledge.size());
        long places = f.knowledge.stream().filter(r -> r.kind.structure()).count();
        h.assertTrue(places == 4, "both villages, the outpost and the mansion should still be known, not " + places);
        h.assertTrue(com.factionclash.campaign.BaseZones.nearKnownDanger(f, new BlockPos(3040, 0, 3040), com.factionclash.campaign.BaseZones.DANGER_RADIUS),
                "the outpost is still dangerous ground after the flood");
        for (var kind : new com.factionclash.campaign.Report.Kind[]{com.factionclash.campaign.Report.Kind.PILLAGER_OUTPOST,
                com.factionclash.campaign.Report.Kind.WOODLAND_MANSION}) {
            h.assertTrue(!kind.canGoStale && kind.structure() && kind.dangerousGround(), kind + " should be a structure that never goes stale");
            var copy = com.factionclash.campaign.Report.load(new com.factionclash.campaign.Report(kind, BlockPos.ZERO, 1, null, 2, "x").save());
            h.assertTrue(copy != null && copy.kind == kind && copy.count == 2, kind + " did not survive saving");
        }
        int[] kinds = {com.factionclash.net.MapPayload.OWN_BASE, com.factionclash.net.MapPayload.ENEMY_BASE, com.factionclash.net.MapPayload.LAIR,
                com.factionclash.net.MapPayload.HOSTILES, com.factionclash.net.MapPayload.CAVE, com.factionclash.net.MapPayload.ORE,
                com.factionclash.net.MapPayload.SURVEYED_CAVE, com.factionclash.net.MapPayload.VILLAGE,
                com.factionclash.net.MapPayload.PILLAGER_OUTPOST, com.factionclash.net.MapPayload.WOODLAND_MANSION};
        h.assertTrue(java.util.Arrays.stream(kinds).distinct().count() == kinds.length
                        && java.util.Arrays.stream(kinds).allMatch(k -> k >= 0 && k < com.factionclash.net.MapPayload.STALE),
                "map marker kinds must be distinct and below the STALE flag");
        h.succeed();
    }

    /** A structure piece with these bounds and nothing to build, for structure starts written into a test's chunks. */
    private static net.minecraft.world.level.levelgen.structure.StructurePiece testPiece(BoundingBox box) {
        return new net.minecraft.world.level.levelgen.structure.StructurePiece(
                net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.JIGSAW, 0, box) {
            @Override
            protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                                                 CompoundTag tag) {
            }

            @Override
            public void postProcess(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.StructureManager structures,
                                    net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random,
                                    BoundingBox area, ChunkPos chunk, BlockPos pivot) {
            }
        };
    }

    /**
     * Structure detection as it runs in the world, on a desert village and a pillager outpost whose starts and
     * references are written into the test's own (loaded) chunks for one tick and then restored: the village is
     * found through the reference in the scout's chunk, reported at its town centre (its first piece) with the
     * villagers with trades counted (none: not a trading village; two: a trading one), and left out once seen on
     * this trip; the outpost is in sight only from 30 blocks further west (its built bounds within 48 blocks); a
     * reference to a start chunk that is not
     * loaded is passed over and that chunk is not loaded or generated by looking.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void scoutSeesStructuresThroughLoadedChunksOnly(GameTestHelper h) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        Structure village = registry.getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_DESERT);
        Structure outpost = registry.getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.PILLAGER_OUTPOST);
        BlockPos eye = h.absolutePos(new BlockPos(12, 2, 12));
        ChunkPos hc = new ChunkPos(eye);
        LevelChunk here = level.getChunkSource().getChunkNow(hc.x, hc.z);
        ChunkPos oc = new ChunkPos(hc.x - 1, hc.z);
        LevelChunk beside = level.getChunkSource().getChunkNow(oc.x, oc.z);
        h.assertTrue(here != null && beside != null, "the test's chunks should be loaded");
        ChunkPos far = new ChunkPos(hc.x + 400, hc.z + 400);
        h.assertTrue(!level.hasChunk(far.x, far.z), "the far chunk should not be loaded to begin with");
        java.util.Map<LevelChunk, java.util.Map<Structure, StructureStart>> starts = new java.util.HashMap<>();
        java.util.Map<LevelChunk, java.util.Map<Structure, it.unimi.dsi.fastutil.longs.LongSet>> refs = new java.util.HashMap<>();
        for (LevelChunk c : List.of(here, beside)) {
            starts.put(c, new java.util.HashMap<>(c.getAllStarts()));
            java.util.Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> copy = new java.util.HashMap<>();
            c.getAllReferences().forEach((k, v) -> copy.put(k, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
            refs.put(c, copy);
        }
        List<net.minecraft.world.entity.npc.Villager> villagers = new java.util.ArrayList<>();
        try {
            // the village: its town centre 30 blocks east, a street running back toward the scout
            BlockPos c = eye.offset(30, 0, 0);
            StructureStart vs = new StructureStart(village, hc, 0, new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(
                    testPiece(new BoundingBox(c.getX() - 4, c.getY() - 1, c.getZ() - 4, c.getX() + 4, c.getY() + 6, c.getZ() + 4)),
                    testPiece(new BoundingBox(eye.getX() + 14, c.getY() - 1, c.getZ() - 2, c.getX() + 40, c.getY() + 3, c.getZ() + 2)))));
            here.setStartForStructure(village, vs);
            here.addReferenceForStructure(village, hc.toLong());
            // the outpost: 75-85 blocks west (its start's own box reaches 12 further, which does not count as built),
            // its start in the next chunk, referenced from the scout's chunk
            StructureStart os = new StructureStart(outpost, oc, 0, new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(
                    testPiece(new BoundingBox(eye.getX() - 85, eye.getY() - 1, eye.getZ() - 5, eye.getX() - 75, eye.getY() + 20, eye.getZ() + 5)))));
            beside.setStartForStructure(outpost, os);
            here.addReferenceForStructure(outpost, oc.toLong());
            // and a reference to an outpost whose start chunk is far away and not loaded
            here.addReferenceForStructure(outpost, far.toLong());

            var seen = com.factionclash.work.StructureSightings.look(level, eye, java.util.Set.of());
            h.assertTrue(!level.hasChunk(far.x, far.z), "looking must not load the far start chunk");
            h.assertTrue(seen.size() == 1 && seen.get(0).kind() == com.factionclash.campaign.Report.Kind.VILLAGE,
                    "only the village should be in sight, saw " + seen);
            var v = seen.get(0);
            h.assertTrue(v.start() == hc.toLong() && v.name().equals("desert village"), "wrong village sighting: " + v);
            h.assertTrue(v.pos().getX() == c.getX() && v.pos().getZ() == c.getZ(), "a village is reported at its town centre "
                    + c.toShortString() + ", not " + v.pos().toShortString());
            var empty = com.factionclash.work.StructureSightings.report(level, eye, v, 100);
            h.assertTrue(empty.count == 0 && !empty.tradingVillage() && empty.note.contains("no villagers with trades"),
                    "no villagers seen: not a trading village (" + empty.count + ", " + empty.note + ")");
            for (var prof : List.of(net.minecraft.world.entity.npc.VillagerProfession.FARMER,
                    net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN)) {
                var vil = h.spawn(EntityType.VILLAGER, new BlockPos(24 + villagers.size(), 2, 12));
                vil.setVillagerData(vil.getVillagerData().setProfession(prof));
                villagers.add(vil);
            }
            var traders = com.factionclash.work.StructureSightings.report(level, eye, v, 140);
            h.assertTrue(traders.count == 2 && traders.tradingVillage() && traders.note.startsWith("desert village, 2 villagers with trades"),
                    "two villagers with trades should be counted: " + traders.count + ", " + traders.note);
            h.assertTrue(empty.sameSubject(traders), "two looks at one village are one subject");
            h.assertTrue(com.factionclash.work.StructureSightings.look(level, eye, java.util.Set.of(hc.toLong())).isEmpty(),
                    "a structure seen on this trip is not seen again");

            h.assertTrue(com.factionclash.work.StructureSightings.look(level, eye.offset(-15, 0, 0), java.util.Set.of(hc.toLong())).isEmpty(),
                    "the outpost's own bounding box reaches within 48 blocks from 15 west, but what is built does not");
            BlockPos west = eye.offset(-30, 0, 0);
            var fromWest = com.factionclash.work.StructureSightings.look(level, west, java.util.Set.of(hc.toLong()));
            h.assertTrue(fromWest.size() == 1 && fromWest.get(0).kind() == com.factionclash.campaign.Report.Kind.PILLAGER_OUTPOST
                    && fromWest.get(0).start() == oc.toLong(), "the outpost should be in sight 30 blocks further west: " + fromWest);
            BoundingBox ob = os.getBoundingBox();
            BlockPos op = fromWest.get(0).pos();
            h.assertTrue(op.getX() == (ob.minX() + ob.maxX()) >> 1 && op.getZ() == (ob.minZ() + ob.maxZ()) >> 1,
                    "an outpost is reported at the middle of its bounds, not " + op.toShortString());
            h.assertTrue(!level.hasChunk(far.x, far.z), "looking must not load the far start chunk");
        } finally {
            villagers.forEach(net.minecraft.world.entity.Entity::discard);
            for (LevelChunk ch : List.of(here, beside)) {
                ch.setAllStarts(starts.get(ch));
                ch.setAllReferences(refs.get(ch));
            }
        }
        h.succeed();
    }
}
