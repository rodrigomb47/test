package com.factionclash.hero;

import com.factionclash.combat.Pursuit;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef.TargetKind;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.Sense;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fills a {@link Sense} from the world (HEROES_V2 6.6, 6.8; task A3): the hero itself and its target, the same way
 * whoever the target is. A raised shield is {@code isBlocking()} for a player and an NPC soldier alike, a drawn bow
 * is a bow or crossbow in use, eating is food in use or an NPC's meal; nothing here asks whether the target is a
 * player beyond the kind {@code person}, which a player, an NPC and a hero share.
 *
 * <p>{@code target_behind} has two readings the kits use (A3's decision, {@code notes/kit-gaps.md} "A3 decisions"): for
 * the move choice and reactions it is a target standing behind the hero (Yugen's and Ilsabet's "behind him/her" turn
 * on it), and for an effect's {@code if} it is the hero standing behind the receiver (Tobimaru's backstab, "60% stronger
 * when he is behind you").
 */
public final class HeroSenses {
    private HeroSenses() {
    }

    /** The bosses of v1 11: an Amount's {@code target_max_health_percent} counts at most 2% against them (and heroes). */
    public static boolean boss(LivingEntity e) {
        return e instanceof HeroEntity || e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden
                || e instanceof ElderGuardian;
    }

    /** Whether {@code other} stands behind {@code viewer} (more than 90 degrees off its facing). */
    static boolean behind(LivingEntity viewer, Entity other) {
        double dx = other.getX() - viewer.getX(), dz = other.getZ() - viewer.getZ();
        if (dx * dx + dz * dz < 1e-4) {
            return false;
        }
        float yaw = (viewer instanceof Player ? viewer.getYRot() : viewer.yBodyRot) * ((float) Math.PI / 180F);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return fx * dx + fz * dz < 0;
    }

    /** What the hero knows of {@code t}: {@code forEffect} reads {@code behind} as "the hero is behind it". */
    public static void target(HeroEntity hero, LivingEntity t, Sense.TargetState ts, boolean forEffect) {
        ts.clear();
        long now = hero.level().getGameTime();
        ts.present = true;
        ts.distance = hero.distanceTo(t);
        ts.visible = t == hero || hero.getSensing().hasLineOfSight(t);
        ts.healthShare = t.getHealth() / Math.max(1F, t.getMaxHealth());
        ts.maxHealth = t.getMaxHealth();
        if (t instanceof Player || t instanceof FactionNpc) {
            ts.kinds.add(TargetKind.PERSON);
        }
        if (t instanceof HeroEntity) {
            ts.kinds.add(TargetKind.HERO);
        }
        if (t instanceof Enemy) {
            ts.kinds.add(TargetKind.MONSTER);
        }
        if (t.getType().is(EntityTypeTags.UNDEAD)) {
            ts.kinds.add(TargetKind.UNDEAD);
        }
        if (t.getType().is(EntityTypeTags.ARTHROPOD)) {
            ts.kinds.add(TargetKind.ARTHROPOD);
        }
        if (t.getType().is(EntityTypeTags.ILLAGER)) {
            ts.kinds.add(TargetKind.ILLAGER);
        }
        if (boss(t) && !(t instanceof HeroEntity)) {
            ts.kinds.add(TargetKind.BOSS);
        }
        ItemStack using = t.isUsingItem() ? t.getUseItem() : ItemStack.EMPTY;
        ts.blocking = t.isBlocking();
        ts.drawingBow = using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem;
        ts.eating = using.has(DataComponents.FOOD) || t instanceof FactionNpc n && n.isEatingMeal();
        ts.airborne = !t.onGround() && !t.isInWater() && !t.onClimbable() && !t.isPassenger();
        ts.flying = t.isFallFlying();
        ts.inWater = t.isInWater();
        ts.behind = forEffect ? behind(t, hero) : behind(hero, t);
        ts.heightAbove = t.getY() - hero.getY();
        Pursuit p = hero.pursuit();
        ts.unreachable = p.target() == t && p.mode() != Pursuit.Mode.CHASE;
        ts.burning = t.isOnFire();
        ts.freezing = t.getTicksFrozen() > 0;
        ts.slowed = t.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
        ts.stunned = HeroStatus.stunned(t);
        HeroStatus st = HeroStatus.peek(t);
        if (st != null && !st.book().isEmpty()) {
            ts.hasDot = st.book().hasDot(now);
            ts.marks.putAll(st.book().marks(now));
        }
    }

    /** The hero's own part of a sense, with its target (or none) and what is around it. */
    public static void fill(HeroEntity hero, LivingEntity target, Sense s, int energy, int ticksWithoutEnemy) {
        s.clear();
        Level level = hero.level();
        long now = level.getGameTime();
        if (target != null && target.isAlive()) {
            target(hero, target, s.target, false);
            int forced = hero.combat().forcedTargetState(); // the operator's target-state hook (SLICE_PLAN 6.5)
            if (forced != 0) {
                s.target.blocking |= (forced & HeroCombat.FORCE_BLOCKING) != 0;
                s.target.drawingBow |= (forced & HeroCombat.FORCE_BOW) != 0;
                s.target.eating |= (forced & HeroCombat.FORCE_EATING) != 0;
            }
        }
        s.healthShare = hero.getHealth() / Math.max(1F, hero.getMaxHealth());
        s.phase = HeroActions.phase(hero.state());
        s.enraged = hero.stateFlag(HeroActions.STATE_ENRAGED);
        s.energy = energy;
        s.ticksWithoutEnemy = ticksWithoutEnemy;
        Vec3 at = hero.position();
        for (LivingEntity e : HeroForms.enemiesNear(hero, at, 16)) {
            if (e == target) {
                continue;
            }
            s.enemies.add(new Sense.Near(hero.distanceTo(e), target == null ? 99 : e.distanceTo(target)));
        }
        s.enemiesEngaged = hero.combat().boss().engaged(); // 6.1's engaged enemies, of every kind (A6a)
        for (LivingEntity a : HeroForms.alliesNear(hero, at, 16)) {
            if (a != hero) {
                s.allies.add(new Sense.Ally(hero.distanceTo(a), a.getHealth() / Math.max(1F, a.getMaxHealth()),
                        a instanceof HeroEntity));
            }
        }
        BlockPos feet = hero.blockPosition();
        var base = hero.base();
        s.inHomeBase = base != null && base.contains(feet, 0);
        s.night = HeroRules.isNight(level.getDayTime());
        s.raining = level.isRainingAt(feet.above());
        Boolean sampled = hero.combat().boss().sampledLowCeiling(); // 6.12: every 2 s while fighting (A6b)
        s.lowCeiling = sampled != null ? sampled : solid(level, feet.above(2)) || solid(level, feet.above(3));
        s.inWater = hero.isInWater();
        s.shielded = hero.getAbsorptionAmount() > 0;
        s.airborne = !hero.onGround() && !hero.isInWater() && !hero.onClimbable() && !hero.isPassenger();
        s.climbing = hero.onClimbable();
        HeroStatus st = HeroStatus.peek(hero);
        if (st != null && !st.book().isEmpty()) {
            s.marks.putAll(st.book().marks(now));
            s.stunned = st.book().stunned(now);
            s.silenced = st.book().silenced(now); // 6.6: a silenced hero starts no move
        }
        s.addsAlive = hero.combat().addsAlive();
        s.state = hero.combat().stateName();
        s.empowered = s.state != null;
        s.dashPathHazard = target != null && hazardBetween(hero, target);
        s.escaping = hero.combat().boss().escape().fleeing(); // 6.13: no move while it runs; a cornered hero asks again
    }

    private static boolean solid(Level level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** Water or lava at the feet along the way to the target (6.12: no dash or charge then). */
    static boolean hazardBetween(HeroEntity hero, LivingEntity t) {
        Level level = hero.level();
        for (int i = 1; i <= 3; i++) {
            double f = i / 4.0;
            BlockPos p = BlockPos.containing(hero.getX() + (t.getX() - hero.getX()) * f, hero.getY() + 0.2,
                    hero.getZ() + (t.getZ() - hero.getZ()) * f);
            var fluid = level.getFluidState(p);
            if (fluid.is(FluidTags.LAVA) || fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
