package com.factionclash.npc;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.combat.CivilianFleeGoal;
import com.factionclash.combat.CombatRules;
import com.factionclash.combat.CreeperEvadeGoal;
import com.factionclash.combat.FactionHurtByTargetGoal;
import com.factionclash.combat.NpcMeleeAttackGoal;
import com.factionclash.combat.NpcRangedAttackGoal;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.Affix;
import com.factionclash.logic.AffixRoller;
import com.factionclash.registry.ModEntities;
import com.factionclash.util.ChunkLoading;
import com.factionclash.util.Messages;
import com.factionclash.work.Job;
import com.factionclash.work.Jobs;
import com.factionclash.work.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.Map;

/**
 * An ordinary faction NPC. NPCs have no hunger (spec 2), their equipment never loses durability, ranged NPCs
 * hold a single arrow that is never consumed, and movement uses player-equivalent physics.
 */
public class FactionNpc extends PathfinderMob implements RangedAttackMob {
    private static final EntityDataAccessor<Byte> DATA_FACTION = SynchedEntityData.defineId(FactionNpc.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(FactionNpc.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(FactionNpc.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(FactionNpc.class, EntityDataSerializers.BOOLEAN);

    /** Input magnitude a player holding "forward" produces; see {@link #setSpeed(float)}. */
    private static final float PLAYER_INPUT = 0.98F;
    public static final int VARIANTS = 4;

    private final SimpleContainer inventory = new SimpleContainer(18);
    private ItemStack ammo = ItemStack.EMPTY;
    private int baseId = -1;
    private String npcName = "";
    private int npcLevel;
    private int xp;
    private final EnumMap<Affix, Integer> affixes = new EnumMap<>(Affix.class);
    private Job job;
    private CompoundTag pendingJobTag;
    private String status = "Idle";
    private String activity = "";
    private final NpcNavigator navigator = new NpcNavigator(this);
    private int regenTimer;
    private int recordTimer;
    private long lastHurtTime;
    private long lastAttackTime;

    public FactionNpc(EntityType<? extends FactionNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        if (this.getNavigation() instanceof GroundPathNavigation g) {
            g.setCanOpenDoors(true);
            g.setCanFloat(true);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 2.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 0.0);
    }

    public static FactionNpc create(ServerLevel level, FactionType faction, NpcRole role, BaseState base, BlockPos at) {
        FactionNpc npc = new FactionNpc(ModEntities.FACTION_NPC.get(), level);
        npc.faction(faction);
        npc.role(role);
        npc.baseId = base == null ? -1 : base.id;
        npc.npcName = NpcNames.random(level.random);
        npc.entityData.set(DATA_VARIANT, (byte) level.random.nextInt(VARIANTS));
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        npc.refreshName();
        return npc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACTION, (byte) 0);
        builder.define(DATA_ROLE, (byte) NpcRole.SWORDSMAN.ordinal());
        builder.define(DATA_VARIANT, (byte) 0);
        builder.define(DATA_AIMING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CreeperEvadeGoal(this));
        this.goalSelector.addGoal(2, new CivilianFleeGoal(this));
        this.goalSelector.addGoal(3, new NpcRangedAttackGoal(this));
        this.goalSelector.addGoal(3, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new WorkGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FactionHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> isGuardDuty() && CombatRules.isHostileMonster(e)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, FactionNpc.class, 10, true, false,
                e -> isGuardDuty() && e instanceof FactionNpc o && o.getFaction() != getFaction()));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> isGuardDuty() && e instanceof ServerPlayer p && CombatRules.isEnemyPlayer(this, p)));
    }

    /** Combatants actively look for enemies; civilians only defend themselves. */
    public boolean isGuardDuty() {
        return getRole().combatant;
    }

    // ---------------------------------------------------------------- movement

    /**
     * Vanilla mobs feed their speed in as both the input vector and the acceleration scale, so their real speed
     * is roughly speed squared. Players feed a constant input of 0.98. Using the same input makes an NPC with the
     * player's 0.1 movement-speed attribute walk at player speed (about 4.3 blocks/s, sprinting 5.6 at 1.3x).
     */
    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed > 1.0E-4F ? PLAYER_INPUT : 0.0F);
    }

    /** Allies pass through each other so narrow doors and stairs do not become permanent jams. */
    @Override
    protected void doPush(Entity other) {
        if (other instanceof FactionNpc o && o.getFaction() == this.getFaction()) {
            return;
        }
        super.doPush(other);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ---------------------------------------------------------------- equipment

    /** NPC gear does not wear out from ordinary use (spec 9). */
    @Override
    protected void doHurtEquipment(DamageSource source, float amount, EquipmentSlot... slots) {
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ItemStack ammo() {
        return ammo;
    }

    /** Equips a single arrow; the previous arrow is removed (replaced equipment disappears). */
    public void setAmmo(ItemStack arrow) {
        this.ammo = arrow.isEmpty() ? ItemStack.EMPTY : arrow.copyWithCount(1);
    }

    /** Equips an item into its slot; whatever it replaces disappears (spec 9). */
    public void equip(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        EquipmentSlot slot = stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : EquipmentSlot.MAINHAND;
        this.setItemSlot(slot, stack.copyWithCount(1));
        this.setDropChance(slot, 2.0F);
    }

    public ItemStack tool() {
        return getMainHandItem();
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = getMainHandItem();
        if (!(bow.getItem() instanceof BowItem) || ammo.isEmpty()) {
            return;
        }
        ItemStack shot = ammo.copyWithCount(1);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, shot, velocity, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem w) {
            arrow = w.customArrow(arrow, shot, bow);
        }
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(arrow.getBaseDamage() * (1.0 + AffixRoller.total(affixes, Affix.MARKSMAN)));
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + d * 0.2F, dz, 1.6F, 4.0F);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
        lastAttackTime = tickCount;
    }

    public boolean isAiming() {
        return entityData.get(DATA_AIMING);
    }

    public void setAiming(boolean aiming) {
        entityData.set(DATA_AIMING, aiming);
    }

    // ---------------------------------------------------------------- identity

    public FactionType getFaction() {
        byte b = entityData.get(DATA_FACTION);
        FactionType[] v = FactionType.values();
        return v[Math.floorMod(b, v.length)];
    }

    public void faction(FactionType type) {
        entityData.set(DATA_FACTION, (byte) type.ordinal());
    }

    public NpcRole getRole() {
        byte b = entityData.get(DATA_ROLE);
        NpcRole[] v = NpcRole.values();
        return v[Math.floorMod(b, v.length)];
    }

    public void role(NpcRole role) {
        boolean changed = role != getRole();
        entityData.set(DATA_ROLE, (byte) role.ordinal());
        if (changed || job == null) {
            job = null;
        }
        reapplyAffixes();
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    public String npcName() {
        return npcName;
    }

    public int baseId() {
        return baseId;
    }

    public void setBaseId(int id) {
        this.baseId = id;
    }

    public BaseState base() {
        if (level().getServer() == null) {
            return null;
        }
        return CampaignData.get(level().getServer()).base(baseId);
    }

    public NpcNavigator navigator() {
        return navigator;
    }

    public Job job() {
        if (job == null && !level().isClientSide) {
            job = Jobs.create(getRole(), this);
            if (pendingJobTag != null) {
                job.load(pendingJobTag, registryAccess());
                pendingJobTag = null;
            }
        }
        return job;
    }

    public String status() {
        return status;
    }

    /** Sets the readable status; the short activity word is shown on the name tag. */
    public void setStatus(String status, String activity) {
        this.status = status;
        if (!activity.equals(this.activity)) {
            this.activity = activity;
            refreshName();
        }
    }

    public void refreshName() {
        Component name = Component.literal(npcName).withStyle(getFaction().color)
                .append(Component.literal(" · " + getRole().displayName + (activity.isEmpty() ? "" : " [" + activity + "]"))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
        setCustomName(name);
    }

    // ---------------------------------------------------------------- experience

    public int npcLevel() {
        return npcLevel;
    }

    public int xp() {
        return xp;
    }

    public Map<Affix, Integer> affixes() {
        return affixes;
    }

    public double affix(Affix a) {
        return AffixRoller.total(affixes, a);
    }

    /** Awards experience to this NPC for completed work and rolls one affix per level gained (spec 18). */
    public void awardXp(int amount) {
        if (amount <= 0 || level().isClientSide) {
            return;
        }
        AffixRoller.LevelResult r = AffixRoller.addXp(npcLevel, xp, amount);
        npcLevel = r.level();
        xp = r.xp();
        for (int i = 0; i < r.levelsGained(); i++) {
            Affix a = AffixRoller.roll(getRole().affixJob, affixes, java.util.concurrent.ThreadLocalRandom.current());
            if (a != null) {
                Map<Affix, Integer> next = AffixRoller.apply(affixes, a);
                affixes.clear();
                affixes.putAll(next);
            }
            Messages.toFaction(level().getServer(), getFaction(), Component.literal(npcName + " reached level " + npcLevel
                    + (a == null ? "" : " and gained " + a.displayName + " (" + a.description + ")")));
        }
        if (r.levelsGained() > 0) {
            reapplyAffixes();
            NpcRecord rec = record();
            if (rec != null) {
                rec.level = npcLevel;
            }
        }
    }

    /** Applies affix attribute bonuses with fixed ids, so reloads or role changes never stack them. */
    public void reapplyAffixes() {
        applyModifier(Attributes.MAX_HEALTH, "affix_vitality", affix(Affix.VITALITY), AttributeModifier.Operation.ADD_VALUE);
        applyModifier(Attributes.MOVEMENT_SPEED, "affix_swiftness", affix(Affix.SWIFTNESS), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyModifier(Attributes.ATTACK_DAMAGE, "affix_might", affix(Affix.MIGHT), AttributeModifier.Operation.ADD_VALUE);
        applyModifier(Attributes.ARMOR, "affix_toughness", affix(Affix.TOUGHNESS), AttributeModifier.Operation.ADD_VALUE);
    }

    private void applyModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id,
                               double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) {
            return;
        }
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, id);
        inst.removeModifier(rl);
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(rl, amount, op));
        }
    }

    public NpcRecord record() {
        if (level().getServer() == null) {
            return null;
        }
        FactionState f = CampaignData.get(level().getServer()).factions.get(getFaction());
        return f == null ? null : f.roster.get(getUUID());
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void aiStep() {
        this.updateSwingTime();
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        // Slow natural regeneration replaces the player's hunger-based healing (NPCs have no hunger).
        if (getHealth() < getMaxHealth() && tickCount - lastHurtTime > 200 && ++regenTimer >= 80) {
            regenTimer = 0;
            heal(1.0F);
        }
        // Stale targets must not freeze work: civilians only defend at close range, and anyone gives up on a
        // target after 10 s without trading blows (e.g. a visible but unreachable mob).
        LivingEntity t = getTarget();
        if (t != null) {
            boolean stale = tickCount - Math.max(lastHurtTime, lastAttackTime) > 200 && tickCount - targetSetAt > 200;
            boolean farForCivilian = getRole().isCivilian() && distanceToSqr(t) > 100;
            if (!t.isAlive() || stale || farForCivilian) {
                setTarget(null);
            }
        }
        if (++recordTimer >= 40) {
            recordTimer = 0;
            NpcRecord rec = record();
            if (rec != null) {
                rec.lastPos = blockPosition();
                rec.lastSeen = level().getGameTime();
                rec.alive = true;
            }
            ChunkLoading.updateTraveler(this);
        }
    }

    private long targetSetAt;

    @Override
    public void setTarget(LivingEntity target) {
        if (target != getTarget()) {
            targetSetAt = tickCount;
        }
        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean r = super.doHurtTarget(target);
        if (r) {
            lastAttackTime = tickCount;
        }
        return r;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r) {
            lastHurtTime = tickCount;
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            CampaignData data = CampaignData.get(sl.getServer());
            NpcRecord rec = record();
            if (rec != null) {
                rec.alive = false;
                rec.lastPos = blockPosition();
                rec.lastSeen = sl.getGameTime();
                data.setDirty();
            }
            ChunkLoading.releaseTraveler(this);
            Messages.toFaction(sl.getServer(), getFaction(), Component.literal(npcName + " (" + getRole().displayName + ") was killed")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            ChunkLoading.releaseTraveler(this);
        }
        super.remove(reason);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
                spawnAtLocation(s);
            }
        }
        if (!ammo.isEmpty()) {
            spawnAtLocation(ammo);
            ammo = ItemStack.EMPTY;
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            ItemStack held = player.getItemInHand(hand);
            if (player.isShiftKeyDown() && !held.isEmpty() && CombatRules.isMember(sp, getFaction())) {
                if (NpcInspector.tryGiveEquipment(this, sp, held)) {
                    return InteractionResult.SUCCESS;
                }
            }
            NpcInspector.inspect(this, sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("FcFaction", getFaction().id);
        tag.putString("FcRole", getRole().id);
        tag.putByte("FcVariant", (byte) variant());
        tag.putString("FcName", npcName);
        tag.putInt("FcBase", baseId);
        tag.put("FcInventory", inventory.createTag(registryAccess()));
        if (!ammo.isEmpty()) {
            tag.put("FcAmmo", ammo.save(registryAccess()));
        }
        tag.putInt("FcLevel", npcLevel);
        tag.putInt("FcXp", xp);
        CompoundTag af = new CompoundTag();
        affixes.forEach((k, v) -> af.putInt(k.name(), v));
        tag.put("FcAffixes", af);
        CompoundTag jt = new CompoundTag();
        if (job != null) {
            job.save(jt, registryAccess());
        } else if (pendingJobTag != null) {
            jt = pendingJobTag;
        }
        tag.put("FcJob", jt);
        tag.putString("FcStatus", status);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        FactionType f = FactionType.byId(tag.getString("FcFaction"));
        if (f != null) {
            faction(f);
        }
        NpcRole r = NpcRole.byId(tag.getString("FcRole"));
        if (r != null) {
            entityData.set(DATA_ROLE, (byte) r.ordinal());
        }
        entityData.set(DATA_VARIANT, tag.getByte("FcVariant"));
        npcName = tag.getString("FcName");
        baseId = tag.contains("FcBase") ? tag.getInt("FcBase") : -1;
        inventory.fromTag(tag.getList("FcInventory", Tag.TAG_COMPOUND), registryAccess());
        ammo = tag.contains("FcAmmo") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("FcAmmo")) : ItemStack.EMPTY;
        npcLevel = tag.getInt("FcLevel");
        xp = tag.getInt("FcXp");
        affixes.clear();
        CompoundTag af = tag.getCompound("FcAffixes");
        for (String k : af.getAllKeys()) {
            try {
                Affix a = Affix.valueOf(k);
                affixes.put(a, Math.min(a.maxStacks, af.getInt(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        job = null;
        pendingJobTag = tag.getCompound("FcJob");
        status = tag.getString("FcStatus");
        reapplyAffixes();
    }
}
