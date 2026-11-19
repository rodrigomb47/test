package com.factionclash.combat;

import com.factionclash.config.FCConfig;
import com.factionclash.npc.FactionNpc;
import com.factionclash.work.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * What a careful player does with what it carries against lethal terrain ({@code docs/design/breaching.md} 8.3 and 8.4,
 * HEROES_V2 6.17; task T3), for every faction NPC and hero, ticked from {@link FactionNpc#tick}:
 *
 * <ul>
 * <li><b>Fire Resistance</b>: one that carries a potion of Fire Resistance drinks it when it is in lava, or when it has
 * burnt for 3 s with no water within 6 blocks: the 1.6 s drink of a player (32 ticks, the drinking sound), then the
 * potion's effects and an empty glass bottle back in its pack.</li>
 * <li><b>The water landing</b> ({@code war.waterLanding}): one that carries a water bucket and falls more than 5 blocks
 * toward a solid floor empties the bucket on the floor when it is 2 to 3 blocks above it, lands in the water unhurt
 * (vanilla's water takes a fall), and picks the water up again once down. Not over lava, not in a dimension where water
 * boils (the Nether), not over the void. The player's well-known trick, with a real bucket at the moment a player
 * would.</li>
 * </ul>
 */
public final class TerrainSurvival {
    /** A potion takes this long to drink (a player's 1.6 s). */
    public static final int DRINK_TICKS = 32;
    /** It drinks once it has burnt this long with no water near (3 s). */
    public static final int BURN_TICKS = 60;
    /** Water within this many blocks is the better answer to burning. */
    public static final int WATER_NEAR = 6;
    /** A fall longer than this is broken with water (blocks), poured 2 to 3 blocks above the floor. */
    public static final double LANDING_FALL = 5, POUR_HIGH = 3, POUR_LOW = 2;
    /** The deepest it looks down for the floor of a fall. */
    public static final int FLOOR_SCAN = 64;

    private final FactionNpc npc;
    private int burnt, drinking = -1, drinks;
    private BlockPos poured;
    private int pouredFor, landings;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public TerrainSurvival(FactionNpc npc) {
        this.npc = npc;
    }

    /** Each server tick of a living NPC or hero. */
    public void tick() {
        fireResistance();
        waterLanding();
    }

    /** Whether it is drinking a potion now. */
    public boolean drinking() {
        return drinking > 0;
    }

    /** Potions of Fire Resistance drunk, water landings made (tests, the dump). */
    public int drinks() {
        return drinks;
    }

    public int landings() {
        return landings;
    }

    // ------------------------------------------------------------------ Fire Resistance (8.3)

    /** A drinkable potion whose effects include Fire Resistance. */
    public static boolean fireResistance(ItemStack s) {
        if (!s.is(Items.POTION)) {
            return false;
        }
        PotionContents c = s.get(DataComponents.POTION_CONTENTS);
        if (c == null) {
            return false;
        }
        for (MobEffectInstance e : c.getAllEffects()) {
            if (e.is(MobEffects.FIRE_RESISTANCE)) {
                return true;
            }
        }
        return false;
    }

    private void fireResistance() {
        burnt = npc.isOnFire() && !npc.isInWaterRainOrBubble() ? burnt + 1 : 0;
        if (drinking > 0) {
            if (--drinking % 4 == 0) {
                npc.playSound(SoundEvents.GENERIC_DRINK, 0.5F, 0.9F + npc.getRandom().nextFloat() * 0.1F);
            }
            if (drinking == 0) {
                drinking = -1;
                drink();
            }
            return;
        }
        if (npc.fireImmune() || npc.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return;
        }
        boolean need = npc.isInLava() || burnt >= BURN_TICKS && npc.tickCount % 10 == 0 && !waterNear();
        if (need && Inv.count(npc.inventory(), TerrainSurvival::fireResistance) > 0) {
            drinking = DRINK_TICKS;
            npc.setStatus("Drinking Fire Resistance", "Drinking");
        }
    }

    private void drink() {
        SimpleContainer inv = npc.inventory();
        ItemStack potion = Inv.first(inv, TerrainSurvival::fireResistance);
        if (potion.isEmpty()) {
            return;
        }
        PotionContents c = potion.get(DataComponents.POTION_CONTENTS);
        potion.shrink(1);
        if (c != null) {
            c.forEachEffect(npc::addEffect);
        }
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        ItemStack rest = inv.addItem(bottle);
        if (!rest.isEmpty()) {
            npc.spawnAtLocation(rest);
        }
        inv.setChanged();
        drinks++;
    }

    private boolean waterNear() {
        Level level = npc.level();
        BlockPos at = npc.blockPosition();
        for (int dx = -WATER_NEAR; dx <= WATER_NEAR; dx++) {
            for (int dz = -WATER_NEAR; dz <= WATER_NEAR; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (level.getFluidState(cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the water landing (8.4)

    private void waterLanding() {
        Level level = npc.level();
        if (poured != null) {
            pouredFor++;
            boolean down = npc.onGround() || npc.isInWater();
            if (down && pouredFor >= 2 || pouredFor > 100) {
                if (level.getFluidState(poured).isSource() && level.getFluidState(poured).is(FluidTags.WATER)) {
                    level.setBlockAndUpdate(poured, Blocks.AIR.defaultBlockState());
                    npc.playSound(SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
                    ItemStack empty = Inv.first(npc.inventory(), s -> s.is(Items.BUCKET));
                    if (!empty.isEmpty()) {
                        empty.shrink(1);
                        ItemStack rest = npc.inventory().addItem(new ItemStack(Items.WATER_BUCKET));
                        if (!rest.isEmpty()) {
                            npc.spawnAtLocation(rest);
                        }
                        npc.inventory().setChanged();
                    }
                }
                poured = null;
            }
            return;
        }
        double vy = npc.getDeltaMovement().y;
        if (npc.onGround() || npc.isInWater() || npc.isInLava() || vy > -0.3 || npc.isFallFlying() || npc.isPassenger()
                || !FCConfig.WAR_WATER_LANDING.get() || level.dimensionType().ultraWarm()) {
            return;
        }
        if (npc.softLanding()) {
            return; // a glider or a flyer glides down instead
        }
        if (npc.hasEffect(MobEffects.SLOW_FALLING) || Inv.count(npc.inventory(), s -> s.is(Items.WATER_BUCKET)) == 0) {
            return;
        }
        int fx = (int) Math.floor(npc.getX()), fz = (int) Math.floor(npc.getZ());
        int top = (int) Math.floor(npc.getY());
        for (int i = 0; i <= FLOOR_SCAN; i++) {
            int y = top - i;
            if (y < level.getMinBuildHeight()) {
                return; // the void: no floor to pour on
            }
            cursor.set(fx, y, fz);
            var fluid = level.getFluidState(cursor);
            if (fluid.is(FluidTags.LAVA) || fluid.is(FluidTags.WATER)) {
                return; // lava sets water to stone; water needs no help
            }
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                double h = npc.getY() - (y + 1);
                double fall = npc.fallDistance + h;
                // poured 2 to 3 blocks above the floor; a faster fall pours when the next tick would carry it past 3
                boolean due = h <= POUR_HIGH || h <= -vy * 1.5 + POUR_LOW;
                if (fall > LANDING_FALL && h >= 0.5 && due) {
                    pour(cursor.above());
                }
                return;
            }
        }
    }

    private void pour(BlockPos at) {
        Level level = npc.level();
        if (!level.getBlockState(at).canBeReplaced(Fluids.WATER) || !level.getFluidState(at).isEmpty()) {
            return;
        }
        ItemStack bucket = Inv.first(npc.inventory(), s -> s.is(Items.WATER_BUCKET));
        if (bucket.isEmpty()) {
            return;
        }
        bucket.shrink(1);
        ItemStack rest = npc.inventory().addItem(new ItemStack(Items.BUCKET));
        if (!rest.isEmpty()) {
            npc.spawnAtLocation(rest);
        }
        npc.inventory().setChanged();
        level.setBlockAndUpdate(at, Blocks.WATER.defaultBlockState());
        npc.playSound(SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
        poured = at.immutable();
        pouredFor = 0;
        landings++;
    }
}
