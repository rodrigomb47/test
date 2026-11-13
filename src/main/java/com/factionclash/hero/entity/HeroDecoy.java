package com.factionclash.hero.entity;

import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hero's {@code decoy} add (HEROES_V2 6.11; the server side, task A5, for {@code spawn_add}): it stands for its hero
 * for 10 s, walks a short path away from where it appeared, has 1 health and pops in a puff of smoke at any hit,
 * whoever lands it, or when its time is up or its hero is gone. It is of its hero's side
 * ({@link com.factionclash.combat.CombatRules#factionOf}), so an enemy hero's moves strike it as they strike the hero;
 * it attacks nobody, drops nothing, gives no experience and is never saved. Its owner's entity id is synced so the
 * client can draw it.
 *
 * <p>TODO(A7, {@code HeroDecoy} row of HEROES_V2 15.2): draw the owner hero's own GeckoLib model with its action two
 * ticks late, no shadow and no footsteps, and let NPC soldiers' target goals take it as they take its hero. Until then
 * the client draws a plain humanoid placeholder.
 */
public class HeroDecoy extends PathfinderMob {
    /** A decoy lasts 10 s (6.11). */
    public static final int LIFE_TICKS = 200;
    /** It walks to a spot this many blocks from where it appeared. */
    public static final double WALK_MIN = 3, WALK_MAX = 5;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(HeroDecoy.class,
            EntityDataSerializers.INT);

    private HeroEntity owner;
    private FactionType faction;
    private int life = LIFE_TICKS;
    private Vec3 walkTo;
    private boolean popped;

    public HeroDecoy(EntityType<? extends HeroDecoy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Makes it its hero's decoy, walking toward {@code walkTo}. */
    public void bind(HeroEntity hero, Vec3 walkTo) {
        this.owner = hero;
        this.faction = hero.getFaction();
        this.walkTo = walkTo;
        entityData.set(DATA_OWNER, hero.getId());
        setCustomName(hero.getCustomName());
        setCustomNameVisible(false);
    }

    /** The hero it stands for (server; null once it is gone). */
    public HeroEntity owner() {
        return owner;
    }

    /** The owner's entity id (synced, for the client's model). */
    public int ownerId() {
        return entityData.get(DATA_OWNER);
    }

    /** The side it is of: its hero's faction. */
    public FactionType faction() {
        return faction;
    }

    /** Ticks it has left. */
    public int life() {
        return life;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || popped) {
            return;
        }
        if (--life <= 0 || owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()) {
            pop();
            return;
        }
        if (walkTo != null && tickCount % 10 == 1 && getNavigation().isDone() && distanceToSqr(walkTo) > 1) {
            getNavigation().moveTo(walkTo.x, walkTo.y, walkTo.z, 1.0);
        }
    }

    /** Any hit pops it, whoever lands it (6.11). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || popped || isInvulnerableTo(source)) {
            return false;
        }
        pop();
        return true;
    }

    /** Gone in a puff of smoke: its hero hears {@code on_add_died}. */
    public void pop() {
        if (popped) {
            return;
        }
        popped = true;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY(0.5), getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.6F);
        }
        HeroEntity o = owner;
        discard();
        if (o != null && o.isAlive()) {
            o.combat().addGone(this);
        }
    }

    public boolean popped() {
        return popped;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
