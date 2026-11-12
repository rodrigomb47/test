package com.factionclash.hero;

import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroSize;
import com.factionclash.npc.FactionNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A hero (HEROES_V2 3): a faction NPC with everything a faction NPC has (inventory, hunger, gear and wear, traits,
 * levels, its job, navigation, stuck recovery, roster record, alarms, war council) plus a hero id, the synced fields
 * the animated model is driven by (10.1) and a hitbox sized from its model (3.6). It counts as one soldier of the
 * role that fits its weapon style and fights with that role's basic attacks until the kit engine (A3) exists.
 *
 * <p>GeckoLib is on both sides, so implementing {@link GeoEntity} is safe on a dedicated server; nothing here
 * touches a client class. The controllers stay empty until the client model (A2) adds them.
 */
public class HeroEntity extends FactionNpc implements GeoEntity {
    /** The hero id ({@code ""} until set; set once at spawn). */
    private static final EntityDataAccessor<String> DATA_HERO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    /** The action the {@code action} controller plays: code, sequence, speed, variant ({@link HeroActions}). */
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    /** Phase, enraged, super armour, weapon drawn, talking, wind-up, yielding ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Mood, intensity, blush, glowing eyes ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Locomotion mode, one-shot and tired (MOBILITY_AND_WORK 4.2); reserved here, driven by the mobility engine (A10). */
    private static final EntityDataAccessor<Byte> DATA_LOCO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);

    /** Wings, tails and long weapons reach past the hitbox: the culling box is this much larger (3.1). */
    public static final double CULLING_MARGIN = 1.5;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public HeroEntity(EntityType<? extends HeroEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FactionNpc.createAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HERO, "");
        builder.define(DATA_ACTION, HeroActions.CODE_NONE);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_MOOD, (byte) 0);
        builder.define(DATA_LOCO, (byte) 0);
    }

    // ------------------------------------------------------------------ identity

    public String heroId() {
        return entityData.get(DATA_HERO);
    }

    /** The registry entry it is drawn and sized with (the fallback for an unknown, dormant hero). */
    public HeroEntry entry() {
        return HeroRegistry.getOrFallback(heroId());
    }

    /** Whether the registry knows its id (a hero whose id it does not know is dormant: plain soldier, fallback look). */
    public boolean dormant() {
        return HeroRegistry.get(heroId()) == null;
    }

    /**
     * Sets the hero id: its size and art scale follow ({@code generic.scale} starts at the model's {@code scale}).
     * Called once, before the entity joins the level, and on loading.
     */
    public void setHeroId(String id) {
        entityData.set(DATA_HERO, id == null ? "" : id);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(entry().visual().scale());
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HERO.equals(key)) {
            refreshDimensions();
        }
    }

    /** Every hero looks for enemies, whatever its duty (v1 25.6). */
    @Override
    public boolean isGuardDuty() {
        return true;
    }

    // ------------------------------------------------------------------ size

    /**
     * The hitbox of HEROES_V2 3.6 from the model: vanilla multiplies these dimensions by {@code generic.scale}, so they
     * are the clamped size divided by it, and the box never exceeds 1.98 blocks however large the scale grows.
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float s = getScale();
        if (!(s > 0)) {
            s = 1.0F;
        }
        HeroSize.Size size = entry().visual().size(s);
        return EntityDimensions.scalable((float) (size.width() / s), (float) (size.height() / s))
                .withEyeHeight((float) (size.eyeHeight() / s));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(CULLING_MARGIN);
    }

    // ------------------------------------------------------------------ synced fields

    public int action() {
        return entityData.get(DATA_ACTION);
    }

    public void setAction(int packed) {
        entityData.set(DATA_ACTION, packed);
    }

    /** Starts an action: a new sequence number, so even the same action again restarts on the client. */
    public void playAction(int code, double speed, int variant) {
        setAction(HeroActions.nextAction(action(), code, speed, variant));
    }

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public void setState(byte state) {
        entityData.set(DATA_STATE, state);
    }

    public boolean stateFlag(int flag) {
        return HeroActions.has(state(), flag);
    }

    public void setStateFlag(int flag, boolean on) {
        setState(HeroActions.with(state(), flag, on));
    }

    /** The talking flag (DATA_STATE bit 5): set while a line of dialogue shows; the face cycles its talking mouth. */
    public boolean isTalking() {
        return stateFlag(HeroActions.STATE_TALKING);
    }

    public void setTalking(boolean talking) {
        setStateFlag(HeroActions.STATE_TALKING, talking);
    }

    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    public void setMood(byte mood) {
        entityData.set(DATA_MOOD, mood);
    }

    public byte loco() {
        return entityData.get(DATA_LOCO);
    }

    public void setLoco(byte loco) {
        entityData.set(DATA_LOCO, loco);
    }

    // ------------------------------------------------------------------ kit

    /** Better gear replacing a kit item: the kit item vanishes instead of going to the backpack (and the storehouse). */
    @Override
    public void equip(ItemStack stack) {
        if (!stack.isEmpty()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR && HeroKit.isKit(getItemBySlot(slot)) && fits(stack, slot)) {
                    setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        super.equip(stack);
    }

    private static boolean fits(ItemStack stack, EquipmentSlot slot) {
        EquipmentSlot target = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot()
                : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        return target == slot;
    }

    /** Kit items never drop: they vanish here and the hero gets them back when it returns. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (HeroKit.isKit(getItemBySlot(slot))) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < inventory().getContainerSize(); i++) {
            if (HeroKit.isKit(inventory().getItem(i))) {
                inventory().setItem(i, ItemStack.EMPTY);
            }
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl && isDeadOrDying()) {
            HeroManager.onDeath(sl.getServer(), this);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    /** Empty until the client model (A2): the base, action and secondary controllers come with it. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag h = new CompoundTag();
        h.putString("id", heroId());
        tag.put("FcHero", h);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag h = tag.getCompound("FcHero");
        setHeroId(h.getString("id"));
    }
}
