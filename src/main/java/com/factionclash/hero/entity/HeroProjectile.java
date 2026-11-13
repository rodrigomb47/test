package com.factionclash.hero.entity;

import com.factionclash.hero.HeroDamage;
import com.factionclash.hero.HeroEffects;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroForms;
import com.factionclash.hero.HeroUse;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.registry.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A hero's projectile (HERO_FORMAT.md 9 and 10.1, B.6; task A3): a {@code shot} or a {@code volley} of a move, or a
 * {@code thrown} basic attack. It looks like the vanilla item of its look (an arrow, a snowball, a fire charge...;
 * custom models are A9), flies straight, or with an arrow's gravity for the arrow looks, passes through the hero's
 * allies, and on its first enemy applies what it carries: the move's effects to that enemy and to enemies within its
 * splash radius (one use, so every projectile of a volley counts against the same slot caps), or the basic hit's
 * damage, energy and {@code on_hit}. It changes no block (no fire, no broken pot) and drops nothing; it is not saved,
 * and one that outlives its hero or the use it carries simply fizzles.
 */
public class HeroProjectile extends ThrowableItemProjectile {
    /** Arrow looks fly at a bow's speed with gravity; item looks straight and slower (v1 B.6). */
    public static final float ARROW_SPEED = 1.6F, ITEM_SPEED = 1.2F;
    /** A thrown basic attack reaches 14 blocks (v1 9). */
    public static final double THROWN_RANGE = 14;
    /** A projectile that hits nothing is gone after this many ticks, or past its range plus 4 blocks. */
    public static final int MAX_AGE = 100;

    private enum Kind { MOVE, BASIC }

    private Kind kind = Kind.MOVE;
    private HeroUse use;
    private List<HeroDef.Effect> effects = List.of();
    private int listIndex;
    private double splash;
    private double range = 24;
    private boolean gravity;
    private Element element = Element.PHYSICAL;
    private double basicRaw;
    private Vec3 start;

    public HeroProjectile(EntityType<? extends HeroProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected double getDefaultGravity() {
        return gravity ? 0.03 : 0.0;
    }

    /** The vanilla item a projectile look shows (B.6); a snowball when the look is unknown. */
    public static Item lookItem(String look) {
        if (look == null || look.isEmpty()) {
            return Items.SNOWBALL;
        }
        ResourceLocation rl = ResourceLocation.tryParse(look.contains(":") ? look : "minecraft:" + look);
        Item i = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        return i == Items.AIR ? Items.SNOWBALL : i;
    }

    private static HeroProjectile make(HeroEntity hero, String look) {
        HeroProjectile p = new HeroProjectile(ModEntities.HERO_PROJECTILE.get(), hero.level());
        p.setOwner(hero);
        p.setItem(new ItemStack(lookItem(look)));
        p.gravity = look != null && HeroCatalogue.ARROW_LOOKS.contains(look);
        Vec3 eye = hero.getEyePosition();
        p.setPos(eye.x, eye.y - 0.1, eye.z);
        p.start = p.position();
        return p;
    }

    private void aim(Vec3 to, double yawOffset, float speed) {
        Vec3 d = to.subtract(position());
        if (yawOffset != 0) {
            double r = Math.toRadians(yawOffset), c = Math.cos(r), s = Math.sin(r);
            d = new Vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
        }
        if (gravity) {
            double ticks = d.horizontalDistance() / speed;
            d = d.add(0, 0.5 * 0.03 * ticks * ticks, 0); // an archer's lead over the drop
        }
        shoot(d.x, d.y, d.z, speed, 0F);
    }

    /** A move's {@code shot} or one projectile of its {@code volley}, aimed at {@code to}, turned by {@code yawOffset}. */
    public static void launchMove(HeroEntity hero, HeroUse use, HeroDef.Ability a, Vec3 to, double yawOffset) {
        String look = a.projectile() == null ? "arrow" : a.projectile();
        HeroProjectile p = make(hero, look);
        p.kind = Kind.MOVE;
        p.use = use;
        p.effects = a.effects();
        p.splash = a.radius();
        p.range = a.range() + 4;
        p.aim(to, yawOffset, p.gravity ? ARROW_SPEED : ITEM_SPEED);
        hero.level().addFreshEntity(p);
    }

    /** A {@code thrown} basic attack: {@code raw} damage of {@code element}, aimed at {@code to}. */
    public static HeroProjectile launchBasic(HeroEntity hero, String look, Element element, double raw, Vec3 to) {
        HeroProjectile p = make(hero, look);
        p.kind = Kind.BASIC;
        p.element = element == null ? Element.PHYSICAL : element;
        p.basicRaw = raw;
        p.range = THROWN_RANGE + 4;
        p.aim(to, 0, p.gravity ? ARROW_SPEED : ITEM_SPEED);
        hero.level().addFreshEntity(p);
        return p;
    }

    private HeroEntity hero() {
        return getOwner() instanceof HeroEntity h && h.isAlive() && !h.isRemoved() ? h : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (start == null) {
            start = position();
        }
        if (tickCount > MAX_AGE || position().distanceTo(start) > range || hero() == null) {
            discard();
        } else if (tickCount % 2 == 0) {
            HeroEffects.particles(level(), this, HeroEffects.particle(element), 1);
        }
    }

    /** Only the hero's enemies stop it; it passes through its allies and bystanders. */
    @Override
    protected boolean canHitEntity(Entity e) {
        HeroEntity h = hero();
        return super.canHitEntity(e) && h != null && e instanceof LivingEntity l && HeroForms.enemy(h, l);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        HeroEntity h = hero();
        if (level().isClientSide || h == null || !(r.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (kind == Kind.BASIC) {
            if (HeroDamage.deal(h, this, target, element, basicRaw, false) > 0 || !target.isAlive()) {
                h.combat().onBasicHit(target, this);
            }
            return;
        }
        if (use == null) {
            return;
        }
        List<LivingEntity> rs = new ArrayList<>();
        rs.add(target);
        if (splash > 0) {
            for (LivingEntity e : HeroForms.enemiesNear(h, position(), splash)) {
                if (!rs.contains(e)) {
                    rs.add(e);
                }
            }
        }
        HeroEffects.apply(use, effects, listIndex, rs, this);
    }

    /** A block stops it and nothing happens to the block (v1 13.6: no block changes); a splash still lands. */
    @Override
    protected void onHitBlock(BlockHitResult r) {
        HeroEntity h = hero();
        if (level().isClientSide || h == null || kind != Kind.MOVE || use == null || splash <= 0) {
            return;
        }
        HeroEffects.apply(use, effects, listIndex, HeroForms.enemiesNear(h, r.getLocation(), splash), this);
    }
}
