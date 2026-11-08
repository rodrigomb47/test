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
import net.minecraft.util.Mth;
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
    private final com.factionclash.logic.NpcHunger hunger = new com.factionclash.logic.NpcHunger();
    /** Ticks left of the meal being eaten (-1 none), and what is being eaten. */
    private int eatingFood = -1;
    private ItemStack meal = ItemStack.EMPTY;
    private net.minecraft.world.phys.Vec3 lastSwimPos;
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
                // the player's attack speed (4.0 bare-handed): held weapons change it exactly as for players
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.SWEEPING_DAMAGE_RATIO) // Sweeping Edge on a sword works for NPCs as for players
                .add(Attributes.ARMOR, 0.0)
                // player mining attributes, so tools, Efficiency and Haste behave exactly as for players
                .add(Attributes.BLOCK_BREAK_SPEED)
                .add(Attributes.MINING_EFFICIENCY)
                .add(Attributes.SUBMERGED_MINING_SPEED);
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
        this.goalSelector.addGoal(2, new com.factionclash.combat.WithdrawGoal(this));
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
                e -> isGuardDuty() && e instanceof FactionNpc o && com.factionclash.campaign.Diplomacy.hostile(getServer(), getFaction(), o.getFaction())));
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

    /**
     * Armour wears when the NPC is hit, as a player's does (a mob's armour does not in vanilla). With tool wear off
     * in the config, gear never wears (the original rule).
     */
    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get()) {
            doHurtEquipment(source, amount, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
        }
    }

    @Override
    protected void doHurtEquipment(DamageSource source, float amount, EquipmentSlot... slots) {
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get() && !sparesGear()) {
            super.doHurtEquipment(source, amount, slots);
        }
    }

    /** The Thrifty affix: this time the gear takes no wear (rolled once per wearing event). */
    public boolean sparesGear() {
        double c = affix(Affix.THRIFTY);
        return c > 0 && random.nextDouble() < c;
    }

    /** One durability for a bow or crossbow shot, as for a player. */
    private void wearWeapon(int amount) {
        if (com.factionclash.config.FCConfig.TOOL_WEAR.get() && !getMainHandItem().isEmpty() && !sparesGear()) {
            getMainHandItem().hurtAndBreak(amount, this, EquipmentSlot.MAINHAND);
        }
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

    /** Equips an item into its slot; what it replaces goes into the backpack (and from there to the storehouse). */
    public void equip(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        EquipmentSlot slot = stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot()
                : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack old = getItemBySlot(slot);
        if (!old.isEmpty() && !level().isClientSide) {
            ItemStack rest = com.factionclash.work.Crafting.addAll(inventory, old.copy());
            if (!rest.isEmpty()) {
                spawnAtLocation(rest);
            }
        }
        this.setItemSlot(slot, stack.copyWithCount(1));
        this.setDropChance(slot, 2.0F);
    }

    public ItemStack tool() {
        return getMainHandItem();
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem || weapon instanceof net.minecraft.world.item.CrossbowItem;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = getMainHandItem();
        boolean crossbow = bow.getItem() instanceof net.minecraft.world.item.CrossbowItem;
        if (!(bow.getItem() instanceof BowItem) && !crossbow || ammo.isEmpty()) {
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
        if (crossbow) {
            com.factionclash.achieve.FactionAchievements.criterion(getServer(), getFaction(), "adventure/ol_betsy", "shot_crossbow", npcName);
            // a crossbow bolt flies faster and straighter, so it hits harder (arrow damage scales with speed)
            arrow.shoot(dx, dy + d * 0.15F, dz, 2.2F, 2.0F);
            playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        } else {
            arrow.shoot(dx, dy + d * 0.2F, dz, 1.6F, 4.0F);
            playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        }
        level().addFreshEntity(arrow);
        lastAttackTime = tickCount;
        wearWeapon(1);
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

    /** Ticks left of eating a golden apple (-1: not eating). */
    private int eatingApple = -1;
    private int appleCooldown;

    /**
     * Golden apples (spec 9): badly hurt in a fight, an NPC carrying one eats it like a player - 32 ticks of
     * eating, then the apple's own effects (regeneration, absorption) - and the apple is used up.
     */
    private void goldenApple() {
        if (appleCooldown > 0) {
            appleCooldown--;
        }
        if (eatingApple > 0) {
            if (--eatingApple % 4 == 0) {
                playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.0F + (getRandom().nextFloat() - getRandom().nextFloat()) * 0.4F);
            }
            if (eatingApple == 0) {
                eatingApple = -1;
                ItemStack apple = com.factionclash.work.Inv.first(inventory, x -> x.is(net.minecraft.world.item.Items.GOLDEN_APPLE));
                if (!apple.isEmpty()) {
                    eat(level(), apple.split(1), apple.getItem().getFoodProperties(new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE), this));
                    inventory.setChanged();
                    playSound(SoundEvents.PLAYER_BURP, 0.5F, 1.0F);
                }
                appleCooldown = 200;
            }
            return;
        }
        if (appleCooldown == 0 && getTarget() != null && getHealth() < getMaxHealth() * 0.4F
                && com.factionclash.work.Inv.count(inventory, x -> x.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) > 0) {
            eatingApple = 32;
            setStatus("Eating a golden apple", "Eating");
        }
    }

    public boolean eatingGoldenApple() {
        return eatingApple > 0;
    }

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
        goldenApple();
        shieldTick();
        if (tickCount % 10 == 0) {
            pickUpExperience();
        }
        if (tickCount % 200 == 0) {
            // Adventuring Time: every biome the faction's NPCs set foot in
            level().getBiome(blockPosition()).unwrapKey().ifPresent(k ->
                    com.factionclash.achieve.FactionAchievements.criterion(getServer(), getFaction(), "adventure/adventuring_time", k.location().toString(), npcName));
        }
        hungerTick();
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

    /**
     * Experience orbs within reach drift to an NPC as they would to a player (ores it mined, a furnace it emptied,
     * animals it bred). What it picks up goes to its faction's experience, which the enchanter spends.
     */
    private void pickUpExperience() {
        java.util.List<net.minecraft.world.entity.ExperienceOrb> orbs = level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                getBoundingBox().inflate(4.0, 2.0, 4.0), net.minecraft.world.entity.Entity::isAlive);
        if (orbs.isEmpty() || level().getServer() == null) {
            return;
        }
        com.factionclash.campaign.CampaignData data = CampaignData.get(level().getServer());
        com.factionclash.campaign.FactionState f = data.faction(getFaction());
        int total = 0;
        for (net.minecraft.world.entity.ExperienceOrb orb : orbs) {
            CompoundTag t = orb.saveWithoutId(new CompoundTag()); // merged orbs carry a count
            total += orb.getValue() * Math.max(1, t.getInt("Count"));
            orb.discard();
        }
        f.xpBank += total;
        data.setDirty();
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.1F, 0.55F + random.nextFloat() * 0.7F);
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != getTarget()) {
            targetSetAt = tickCount;
        }
        super.setTarget(target);
    }

    private static final net.minecraft.resources.ResourceLocation CRIT_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("factionclash", "critical_hit");

    /** A player's critical hit: attacking while falling (not on the ground, a ladder or in water). */
    public boolean critical() {
        return fallDistance > 0.0F && !onGround() && !onClimbable() && !isInWater() && !isPassenger();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        ItemStack weapon = getMainHandItem();
        if (isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) {
            stopUsingItem(); // the shield comes down for the swing
        }
        boolean crit = critical() && target instanceof LivingEntity;
        var dmg = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (crit && dmg != null) {
            dmg.removeModifier(CRIT_ID);
            dmg.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CRIT_ID, 0.5 + affix(Affix.DUELIST),
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean r;
        try {
            r = super.doHurtTarget(target);
        } finally {
            if (dmg != null) {
                dmg.removeModifier(CRIT_ID);
            }
        }
        if (r && crit && level() instanceof ServerLevel sl) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, getSoundSource(), 1.0F, 1.0F);
            sl.getChunkSource().broadcastAndSend(this, new net.minecraft.network.protocol.game.ClientboundAnimatePacket(target,
                    net.minecraft.network.protocol.game.ClientboundAnimatePacket.CRITICAL_HIT));
        } else if (r && !crit && onGround() && weapon.is(net.minecraft.tags.ItemTags.SWORDS) && target instanceof LivingEntity victim) {
            sweep(victim);
        }
        if (r) {
            lastAttackTime = tickCount;
            exhaust(0.1F); // a player's attack costs 0.1 exhaustion
            // the weapon wears like a player's: a sword 1 per hit, a tool used as a weapon 2
            if (com.factionclash.config.FCConfig.TOOL_WEAR.get() && target instanceof LivingEntity victim && !weapon.isEmpty()
                    && weapon == getMainHandItem() && weapon.getItem().hurtEnemy(weapon, victim, this) && !sparesGear()) {
                weapon.getItem().postHurtEnemy(weapon, victim, this);
            }
        }
        return r;
    }

    /**
     * A player's sword sweep: a hit on the ground also catches enemies right next to the target for 1 damage plus the
     * Sweeping Edge share of the hit. Only enemies: never allies, animals or bystanders.
     */
    private void sweep(LivingEntity target) {
        float dmg = (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float sweep = 1.0F + (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SWEEPING_DAMAGE_RATIO) * dmg;
        boolean any = false;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
            if (e == this || e == target || !e.isAlive() || distanceToSqr(e) >= 9.0 || !isEnemy(e)) {
                continue;
            }
            e.knockback(0.4, Mth.sin(getYRot() * Mth.DEG_TO_RAD), -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            e.hurt(damageSources().mobAttack(this), sweep);
            any = true;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 1.0F, 1.0F);
        if (any && level() instanceof ServerLevel sl) {
            double dx = -Mth.sin(getYRot() * Mth.DEG_TO_RAD), dz = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, getX() + dx, getY(0.5), getZ() + dz, 0, dx, 0.0, dz, 0.0);
        }
    }

    /** Hostile monsters, other factions' NPCs and enemy players: what a sweep may hit. */
    public boolean isEnemy(LivingEntity e) {
        if (e instanceof FactionNpc o) {
            return com.factionclash.campaign.Diplomacy.hostile(getServer(), getFaction(), o.getFaction());
        }
        if (e instanceof net.minecraft.server.level.ServerPlayer p) {
            return com.factionclash.combat.CombatRules.isEnemyPlayer(this, p);
        }
        return e instanceof net.minecraft.world.entity.monster.Enemy || e == getTarget();
    }

    // ---------------------------------------------------------------- shield

    private int shieldCooldown;

    public boolean hasShield() {
        return getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    /** Shields wear when they stop a hit of 3 or more, as a player's do. */
    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        if (!com.factionclash.config.FCConfig.TOOL_WEAR.get() || !getUseItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)
                || amount < 3.0F || sparesGear()) {
            return;
        }
        InteractionHand hand = getUsedItemHand();
        getUseItem().hurtAndBreak(1 + Mth.floor(amount), this, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (getUseItem().isEmpty()) {
            stopUsingItem();
        }
    }

    /** An axe knocks the shield aside for 5 seconds, as it does a player's. */
    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        if (attacker.canDisableShield()) {
            shieldCooldown = 100;
            stopUsingItem();
            playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + level().random.nextFloat() * 0.4F);
        }
    }

    /**
     * Shield work for a soldier: up while closing on an archer or between its own swings, down to strike, to shoot
     * or when there is no fight.
     */
    private void shieldTick() {
        if (shieldCooldown > 0) {
            shieldCooldown--;
        }
        if (!hasShield() || isEatingMeal() || eatingApple > 0) {
            return;
        }
        LivingEntity t = getTarget();
        boolean raise = false;
        if (shieldCooldown == 0 && t != null && t.isAlive() && getRole().combatant && !getRole().ranged) {
            double d = distanceTo(t);
            boolean shooter = t instanceof net.minecraft.world.entity.monster.RangedAttackMob
                    || t instanceof FactionNpc o && o.getRole().ranged;
            raise = shooter && d > 3.5 || d < 3.5 && tickCount - lastAttackTime < 8;
        }
        boolean up = isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND;
        if (raise && !up && !isUsingItem()) {
            startUsingItem(InteractionHand.OFF_HAND);
        } else if (!raise && up) {
            stopUsingItem();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r) {
            lastHurtTime = tickCount;
            exhaust(source.getFoodExhaustion());
        }
        return r;
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        exhaust(0.05F); // a player's jump
    }

    // ---------------------------------------------------------------- hunger

    public com.factionclash.logic.NpcHunger hunger() {
        return hunger;
    }

    public void exhaust(float amount) {
        if (!level().isClientSide && com.factionclash.config.FCConfig.HUNGER.get()) {
            hunger.exhaust(amount * hardy());
        }
    }

    /** The Hardy affix: a share of all exhaustion is never felt. */
    private float hardy() {
        return (float) Math.max(0.0, 1.0 - affix(Affix.HARDY));
    }

    /** Eating anything (a meal, a golden apple) fills the food bar as for a player. */
    @Override
    public ItemStack eat(net.minecraft.world.level.Level level, ItemStack stack, net.minecraft.world.food.FoodProperties food) {
        hunger.eat(food.nutrition(), food.saturation());
        if (!level.isClientSide) {
            com.factionclash.achieve.FactionAchievements.ate(getServer(), getFaction(), stack, npcName); // Husbandry, A Balanced Diet
        }
        return super.eat(level, stack, food);
    }

    public boolean isEatingMeal() {
        return eatingFood > 0;
    }

    /** Food a worker eats from its backpack: anything edible except golden apples (kept for fights). */
    public static boolean isMeal(ItemStack s, LivingEntity eater) {
        return !s.isEmpty() && s.getFoodProperties(eater) != null && !s.is(net.minecraft.world.item.Items.GOLDEN_APPLE)
                && !s.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE);
    }

    /** Food with a harmful effect (rotten flesh, spider eye, poisonous potato, raw chicken, pufferfish...). */
    public static boolean isHarmfulFood(ItemStack s, LivingEntity eater) {
        net.minecraft.world.food.FoodProperties f = s.getFoodProperties(eater);
        return f == null || f.effects().stream().anyMatch(e -> !e.effect().getEffect().value().isBeneficial())
                || s.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW);
    }

    /**
     * Eats like a player: when the food bar has dropped below 18 (where healing stops) and there is room for the
     * meal, or any time it is low; the best-fitting safe food first, harmful food only when starving. A meal takes
     * 1.6 s, shown in the off hand; work goes on meanwhile.
     */
    private void hungerTick() {
        if (!com.factionclash.config.FCConfig.HUNGER.get()) {
            if (getHealth() < getMaxHealth() && tickCount - lastHurtTime > 200 && ++regenTimer >= 80) {
                regenTimer = 0;
                heal(1.0F); // without hunger: the original slow regeneration
            }
            return;
        }
        hunger.exhaust((float) (com.factionclash.config.FCConfig.WORK_EXHAUSTION_PER_DAY.get() / 24000.0) * hardy());
        if (isInWater()) {
            if (lastSwimPos != null) {
                hunger.exhaust((float) (0.01 * position().distanceTo(lastSwimPos)) * hardy()); // swimming: 0.01 per metre
            }
            lastSwimPos = position();
        } else {
            lastSwimPos = null;
        }
        float change = hunger.tick(getHealth(), getMaxHealth(), level().getDifficulty().getId(),
                level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION));
        if (change > 0) {
            heal(change);
        } else if (change < 0) {
            hurt(damageSources().starve(), -change);
        }
        if (eatingFood > 0) {
            if (--eatingFood % 4 == 0 && level() instanceof ServerLevel sl) {
                playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.0F + (getRandom().nextFloat() - getRandom().nextFloat()) * 0.4F);
                sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, meal),
                        getX(), getEyeY() - 0.2, getZ(), 4, 0.15, 0.1, 0.15, 0.05);
            }
            if (eatingFood == 0) {
                eatingFood = -1;
                ItemStack food = com.factionclash.work.Inv.first(inventory, x -> ItemStack.isSameItemSameComponents(x, meal));
                if (!food.isEmpty()) {
                    net.minecraft.world.food.FoodProperties props = food.getFoodProperties(this);
                    ItemStack one = food.split(1);
                    inventory.setChanged();
                    if (props != null) {
                        eat(level(), one, props);
                    }
                }
                if (ItemStack.isSameItemSameComponents(getOffhandItem(), meal)) {
                    setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); // the meal shown in hand (never a shield)
                }
                meal = ItemStack.EMPTY;
            }
            return;
        }
        if (tickCount % 20 != 0 || eatingApple > 0) {
            return;
        }
        int food = hunger.foodLevel();
        boolean starving = food <= 6;
        // badly hurt below 18 food: no natural healing, so it eats even if part of the meal is wasted, as a player would
        boolean hurt = getHealth() <= getMaxHealth() * 0.75F;
        if (food >= 18 || getTarget() != null && !starving) {
            return;
        }
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!isMeal(s, this)) {
                continue;
            }
            net.minecraft.world.food.FoodProperties f = s.getFoodProperties(this);
            boolean harmful = isHarmfulFood(s, this);
            if (harmful && !starving) {
                continue;
            }
            boolean fits = hunger.hasRoomFor(f.nutrition());
            if (!fits && food > 12 && !hurt) {
                continue; // would waste most of it: wait a little
            }
            // fitting food first, then the most filling; harmful food last
            double score = (fits ? 100 : 0) + f.saturation() + f.nutrition() - (harmful ? 1000 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (!best.isEmpty()) {
            meal = best.copyWithCount(1);
            eatingFood = best.getUseDuration(this) > 0 ? best.getUseDuration(this) : 32;
            if (getOffhandItem().isEmpty()) {
                setItemSlot(EquipmentSlot.OFFHAND, meal.copy());
            }
        }
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
        CompoundTag hg = new CompoundTag();
        hg.putInt("food", hunger.foodLevel());
        hg.putFloat("saturation", hunger.saturation());
        hg.putFloat("exhaustion", hunger.exhaustion());
        tag.put("FcHunger", hg);
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
        if (tag.contains("FcHunger")) {
            CompoundTag hg = tag.getCompound("FcHunger");
            hunger.set(hg.getInt("food"), hg.getFloat("saturation"), hg.getFloat("exhaustion"));
        }
        reapplyAffixes();
    }
}
