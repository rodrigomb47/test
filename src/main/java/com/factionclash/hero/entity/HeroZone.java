package com.factionclash.hero.entity;

import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroForms;
import com.factionclash.hero.HeroUse;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.registry.ModEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hero's {@code zone} (HERO_FORMAT.md 10.1; task A3): an area of {@code radius} at the target, at the hero, or
 * following the hero, that warns with a ring of particles for its {@code delay} (anyone may step out) and then pulses
 * {@code 1 + duration / interval} times, applying the move's effects to every enemy inside at each pulse (and its
 * {@code to: allies} effects to the allies inside). Its pulses share one use, held to 1.5 times the slot cap per
 * receiver over the whole zone (v1 16.1). An invisible entity with server-side particles (vanilla looks; the ground
 * decals are A6's), not saved, and gone with its hero; a hero has at most two at once.
 */
public class HeroZone extends Entity {
    private HeroUse use;
    private HeroDef.Ability ability;
    private boolean follow;
    private double radius = 3;
    private int delay;
    private int pulses = 1;
    private int interval = 20;
    private int pulsesDone;
    private int age;
    private ParticleOptions particle;

    public HeroZone(EntityType<? extends HeroZone> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Lays a zone of the move {@code a} for {@code use} at {@code at} (or following the hero). */
    public static HeroZone spawn(HeroEntity hero, HeroUse use, HeroDef.Ability a, Vec3 at, boolean follow) {
        HeroZone z = new HeroZone(ModEntities.HERO_ZONE.get(), hero.level());
        z.use = use;
        z.ability = a;
        z.follow = follow;
        z.radius = a.radius() > 0 ? a.radius() : 3;
        z.delay = (int) Math.max(0, HeroRules.ticks(a.delay()));
        z.pulses = Math.max(1, a.pulses());
        z.interval = (int) Math.max(1, HeroRules.ticks(a.interval()));
        z.particle = HeroEffects.particle(a.look(), use.def.element(), use.def.color());
        z.setPos(at.x, at.y, at.z);
        return hero.level().addFreshEntity(z) ? z : null;
    }

    public double radius() {
        return radius;
    }

    public int pulsesDone() {
        return pulsesDone;
    }

    public HeroEntity hero() {
        return use == null ? null : use.hero;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        HeroEntity h = hero();
        if (h == null || !h.isAlive() || h.isRemoved() || ability == null) {
            discard();
            return;
        }
        if (follow) {
            setPos(h.getX(), h.getY(), h.getZ());
        }
        ParticleOptions p = particle;
        int next = delay + pulsesDone * interval;
        if (age < delay && age % 5 == 0) {
            HeroEffects.particleRing(level(), position(), radius, ParticleTypes.SMOKE);
        }
        if (age >= next) {
            pulse(h, p);
            pulsesDone++;
            if (pulsesDone >= pulses) {
                discard();
                return;
            }
        }
        age++;
    }

    private void pulse(HeroEntity h, ParticleOptions p) {
        use.area(position(), radius);
        HeroEffects.particleRing(level(), position(), radius, p);
        HeroEffects.particleRing(level(), position(), radius * 0.5, p);
        HeroEffects.apply(use, ability.effects(), 0, HeroForms.enemiesNear(h, position().add(0, 0.5, 0), radius), h);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
