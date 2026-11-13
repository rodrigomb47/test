package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.StatusBook.DotDue;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A hero's damage (HERO_FORMAT.md 6.1 and 6.3; task A3): the formula of {@link HeroRules} with the element against
 * the target, the attacker's {@code damage_dealt} and the target's resistance and {@code damage_taken}, held to
 * {@code heroes.maxHit}, then {@code target.hurt} with the element's damage type ({@code factionclash:hero_<element>}
 * and {@code _dot}, tagged so vanilla's own rules apply: Fire Protection, Fire Resistance and fire-immune mobs for
 * fire, armour ignored by arcane, no knock-back from a DoT). The same path for every target, a player, an NPC, a hero
 * or a monster: no damage type scales with the difficulty, nothing reads who is hit.
 *
 * <p><b>Invulnerability frames</b> (v1 6.3): the target's {@code invulnerableTime} and {@code lastHurt} are saved,
 * cleared for the hero hit and restored after it, so hero hits are not swallowed by vanilla's half-second cooldown
 * and do not swallow a soldier's next sword hit either. The per-hit boss cap on heroes is A6's.
 */
public final class HeroDamage {
    private HeroDamage() {
    }

    /** Every hero damage type ({@code data/factionclash/tags/damage_type/hero.json}). */
    public static final TagKey<DamageType> HERO = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero"));

    private static final Map<Element, ResourceKey<DamageType>> HIT = new EnumMap<>(Element.class);
    private static final Map<Element, ResourceKey<DamageType>> DOT = new EnumMap<>(Element.class);

    static {
        for (Element e : Element.values()) {
            HIT.put(e, ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_" + e.id())));
            DOT.put(e, ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_" + e.id() + "_dot")));
        }
    }

    public static ResourceKey<DamageType> type(Element e, boolean dot) {
        return (dot ? DOT : HIT).get(e == null ? Element.PHYSICAL : e);
    }

    /** A hero damage source: the element's type, what struck ({@code direct}: the hero or its projectile) and who. */
    public static DamageSource source(Level level, Element e, boolean dot, Entity direct, Entity causing) {
        Holder<DamageType> h = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type(e, dot));
        return new DamageSource(h, direct, causing);
    }

    // ------------------------------------------------------------------ the element and the factors

    /** What the target is, as far as the elements care (v1 6.1). */
    public static Set<HeroRules.Nature> nature(LivingEntity t) {
        Set<HeroRules.Nature> n = EnumSet.noneOf(HeroRules.Nature.class);
        EntityType<?> type = t.getType();
        if (t.fireImmune() || t.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            n.add(HeroRules.Nature.FIRE_IMMUNE);
        }
        if (t instanceof SnowGolem) {
            n.add(HeroRules.Nature.SNOW_GOLEM);
        }
        if (type.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
            n.add(HeroRules.Nature.FREEZE_IMMUNE);
        }
        if (type.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
            n.add(HeroRules.Nature.FREEZE_HURTS_EXTRA);
        }
        if (t.isInWaterRainOrBubble()) {
            n.add(HeroRules.Nature.WET);
        }
        if (type.is(EntityTypeTags.UNDEAD)) {
            n.add(HeroRules.Nature.UNDEAD);
        }
        if (type == EntityType.WITHER || type == EntityType.WITHER_SKELETON) {
            n.add(HeroRules.Nature.WITHER_IMMUNE);
        }
        return n;
    }

    /** A hero target's own resistance to an element, in percent (v1 6.1); 0 for everyone else. */
    public static double resistance(LivingEntity t, Element e) {
        if (t instanceof HeroEntity h && !h.dormant()) {
            HeroDef d = h.definition();
            return d == null ? 0 : d.resistances().getOrDefault(e, 0);
        }
        return 0;
    }

    /**
     * The four factors of v1 6.3 for a hit of {@code e} by {@code attacker} (null: none) on {@code target},
     * multiplied and clamped to 0..2.
     */
    public static double factor(LivingEntity attacker, LivingEntity target, Element e) {
        double dealt = attacker == null ? 0 : HeroStatus.percent(attacker, Stat.DAMAGE_DEALT);
        double taken = HeroStatus.percent(target, Stat.DAMAGE_TAKEN);
        return HeroRules.damageFactor(dealt, HeroRules.elementFactor(e, nature(target)), resistance(target, e), taken);
    }

    public static double maxHit() {
        return HeroRules.clampMaxHit(FCConfig.HERO_MAX_HIT.get());
    }

    // ------------------------------------------------------------------ dealing it

    private static final Field LAST_HURT = lastHurtField();

    private static Field lastHurtField() {
        try {
            // NeoForge runs on Mojang's names in development and on servers alike
            Field f = LivingEntity.class.getDeclaredField("lastHurt");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionClash.LOGGER.warn("[Heroes] LivingEntity.lastHurt not found; hero hits restore only the invulnerable time");
            return null;
        }
    }

    private static float lastHurt(LivingEntity e) {
        try {
            return LAST_HURT == null ? 0 : LAST_HURT.getFloat(e);
        } catch (IllegalAccessException ex) {
            return 0;
        }
    }

    private static void lastHurt(LivingEntity e, float v) {
        try {
            if (LAST_HURT != null) {
                LAST_HURT.setFloat(e, v);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    /**
     * Hurts {@code target} with a hero source for {@code amount} (already through the formula), around vanilla's
     * hurt cooldown (saved, cleared, restored). Returns the health and absorption it really lost.
     */
    public static float hit(LivingEntity target, DamageSource src, float amount) {
        if (!(amount > 0) || !target.isAlive()) {
            return 0;
        }
        int inv = target.invulnerableTime;
        float last = lastHurt(target);
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        try {
            target.hurt(src, amount);
        } finally {
            if (target.isAlive()) {
                target.invulnerableTime = inv;
                lastHurt(target, last);
            }
        }
        float lost = Math.max(0, before - (target.isAlive() ? target.getHealth() + target.getAbsorptionAmount() : 0));
        if (HeroCombatLog.ON) {
            HeroCombatLog.dealt(target, src, amount, lost);
        }
        return lost;
    }

    /**
     * A hero's hit of {@code raw} damage (Amount x ability power x {@code powerScale}, after the use's slot cap) of
     * element {@code e}: x the factors, at most {@code heroes.maxHit}, dealt by {@code direct} (the hero or its
     * projectile) for {@code hero}. Returns what it really took.
     */
    public static float deal(LivingEntity hero, Entity direct, LivingEntity target, Element e, double raw, boolean dot) {
        double dealt = HeroRules.dealt(raw, factor(hero, target, e), maxHit());
        if (dealt <= 0) {
            return 0;
        }
        return hit(target, source(target.level(), e, dot, direct, hero), (float) dealt);
    }

    /** Whether a hit being dealt now is an unblockable move's (a shield does not stop it; A5). Server thread only. */
    private static int unblockableDepth;

    /** Whether the hero hit being dealt now goes through shields ({@link HeroMoveEvents} reads it in the shield event). */
    public static boolean dealingUnblockable() {
        return unblockableDepth > 0;
    }

    /** {@link #deal} for an {@code unblockable} move (a grab's throw among them): a raised shield does not stop it. */
    public static float dealUnblockable(LivingEntity hero, Entity direct, LivingEntity target, Element e, double raw) {
        unblockableDepth++;
        try {
            return deal(hero, direct, target, e, raw, false);
        } finally {
            unblockableDepth--;
        }
    }

    /** Whether {@code src} is a hero's damage over time (one of the {@code _dot} types). */
    public static boolean isDot(DamageSource src) {
        var key = src.typeHolder().unwrapKey().orElse(null);
        return key != null && DOT.containsValue(key);
    }

    /**
     * One second of a source's DoTs on {@code target} (v1 13.1): their sum held to {@code heroes.maxDotPerSecond}
     * (per hero and target), through the factors and {@code maxHit}, with the element's {@code _dot} type. The hero
     * that applied them need not be there any more; then the hit has no attacker.
     */
    static void dotTick(LivingEntity target, DotDue d) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity hero = null;
        try {
            Entity src = d.source().isEmpty() ? null : level.getEntity(UUID.fromString(d.source()));
            if (src instanceof LivingEntity l && l.isAlive()) {
                hero = l;
            }
        } catch (IllegalArgumentException ignored) {
        }
        double cap = Math.max(1, FCConfig.HERO_MAX_DOT_PER_SECOND.get());
        double perSecond = Math.min(cap, Math.max(0, d.perSecond())); // HeroRules.dotTick over one source's sum
        HeroEffects.elementParticles(level, target, d.element(), 3);
        if (hero == null) {
            double dealt = HeroRules.dealt(perSecond, factor(null, target, d.element()), maxHit());
            hit(target, source(level, d.element(), true, null, null), (float) dealt);
        } else {
            deal(hero, hero, target, d.element(), perSecond, true);
        }
    }
}
