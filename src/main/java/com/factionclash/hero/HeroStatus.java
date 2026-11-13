package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.StatusBook;
import com.factionclash.logic.hero.StatusBook.DotDue;
import com.factionclash.logic.hero.StatusBook.Entry;
import com.factionclash.logic.hero.StatusBook.Expired;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What heroes' moves leave on a living entity (HERO_FORMAT.md 13 and 19; task A3): its {@link StatusBook} of marks,
 * damage over time, stat modifiers, shield and freeze hold, applied to the entity the same way whoever carries it (a
 * player, an NPC, a hero or a monster). A data attachment ({@link HeroAttachments#STATUS}), saved with its entity and
 * with every expiry as a game time, so a status keeps running across a save; stat modifiers are applied again on
 * load. Every entity carrying something is ticked once a server tick ({@link #tickAll}): expiry, the DoTs' seconds
 * ({@link HeroDamage#dotTick}), the freeze hold and the attribute modifiers of the stats.
 */
public final class HeroStatus {
    private final StatusBook book = new StatusBook();

    public StatusBook book() {
        return book;
    }

    // ------------------------------------------------------------------ access

    /** The entity's statuses, created when it has none (and it is ticked from then on). */
    public static HeroStatus of(LivingEntity e) {
        HeroStatus s = e.getData(HeroAttachments.STATUS);
        track(e);
        return s;
    }

    /** The entity's statuses, or null when it never had any (nothing is created). */
    public static HeroStatus peek(LivingEntity e) {
        return e != null && e.hasData(HeroAttachments.STATUS) ? e.getData(HeroAttachments.STATUS) : null;
    }

    /** The sum of a stat's modifiers on the entity now, in percent (0 when it has none). */
    public static double percent(LivingEntity e, Stat stat) {
        HeroStatus s = peek(e);
        return s == null || s.book.isEmpty() ? 0 : s.book.statPercent(stat, e.level().getGameTime());
    }

    // ------------------------------------------------------------------ the carriers, ticked every server tick

    /** Entities that carry statuses (weak: an unloaded or removed entity drops out). */
    private static final Set<LivingEntity> CARRIERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<LivingEntity> SWEEP = new ArrayList<>();

    /** Makes sure the entity is ticked while it carries something (a status was added, or it joined the level). */
    public static void track(LivingEntity e) {
        if (!e.level().isClientSide) {
            CARRIERS.add(e);
        }
    }

    /** Once a server tick: every carrier's statuses. Allocation-free while nothing is due. */
    public static void tickAll(MinecraftServer server) {
        if (CARRIERS.isEmpty()) {
            return;
        }
        SWEEP.clear();
        SWEEP.addAll(CARRIERS);
        for (LivingEntity e : SWEEP) {
            if (e.isRemoved() || !(e.level() instanceof ServerLevel)) {
                CARRIERS.remove(e);
                continue;
            }
            HeroStatus s = peek(e);
            if (s == null || !s.tick(e)) {
                CARRIERS.remove(e);
            }
        }
        SWEEP.clear();
    }

    /** One tick of this entity's statuses; false when it carries nothing more. */
    boolean tick(LivingEntity e) {
        long now = e.level().getGameTime();
        if (!e.isAlive()) {
            clearAll(e);
            return false;
        }
        Expired x = book.expire(now);
        if (x != Expired.NONE) {
            if (x.shield() > 0) {
                removeShield(e, x.shield());
            }
            if (x.marks() && !book.glowing(now) && e.hasEffect(MobEffects.GLOWING)) {
                MobEffectInstance g = e.getEffect(MobEffects.GLOWING);
                if (g != null && g.getDuration() > 0 && g.isAmbient()) {
                    e.removeEffect(MobEffects.GLOWING);
                }
            }
        }
        if (book.frozen(now) && e.canFreeze()) {
            // held fully frozen, as powder snow holds it (v1 13.1); vanilla thaws 2 a tick, so the hold stays full
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 2));
        }
        List<DotDue> due = book.dueDots(now);
        for (int i = 0; i < due.size(); i++) {
            HeroDamage.dotTick(e, due.get(i));
            if (!e.isAlive()) {
                clearAll(e);
                return false;
            }
        }
        if (book.consumeStatsDirty()) {
            applyStats(e, now);
        }
        return !book.isEmpty();
    }

    /** Drops everything and undoes what it changed on the entity (death). */
    void clearAll(LivingEntity e) {
        boolean hadStats = book.entries().stream().anyMatch(x -> x.kind == StatusBook.Kind.STAT);
        book.clear();
        book.consumeStatsDirty();
        if (hadStats) {
            applyStats(e, e.level().getGameTime());
        }
    }

    // ------------------------------------------------------------------ stats as attributes (v1 13.3)

    /** The modifier id of a stat on any entity: one per stat, the sum of its statuses. */
    public static ResourceLocation statId(Stat stat) {
        return ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_stat_" + stat.id());
    }

    private static Holder<Attribute> attribute(Stat stat) {
        return switch (stat) {
            case ATTACK -> Attributes.ATTACK_DAMAGE;
            case ARMOR -> Attributes.ARMOR;
            case SPEED -> Attributes.MOVEMENT_SPEED;
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case KNOCKBACK_RESISTANCE -> Attributes.KNOCKBACK_RESISTANCE;
            default -> null; // damage_dealt, damage_taken, healing_received, crit_damage, energy_gain: read by the rules
        };
    }

    /**
     * Sets the attribute-backed stats (attack, armour, speed, attack speed, knock-back resistance) from the book: one
     * modifier per stat, the sum of its statuses held to the stat's range (v1 13.3), removed when none is left.
     */
    static void applyStats(LivingEntity e, long now) {
        Map<Stat, Double> sums = null;
        for (Stat stat : Stat.values()) {
            Holder<Attribute> attr = attribute(stat);
            if (attr == null) {
                continue;
            }
            AttributeInstance inst = e.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            if (sums == null) {
                HeroStatus s = peek(e);
                sums = s == null ? Map.of() : s.book.stats(now);
            }
            ResourceLocation id = statId(stat);
            double pct = clampStat(stat, sums.getOrDefault(stat, 0.0));
            AttributeModifier old = inst.getModifier(id);
            if (pct == 0) {
                if (old != null) {
                    inst.removeModifier(id);
                }
                continue;
            }
            boolean flat = stat == Stat.KNOCKBACK_RESISTANCE;
            double amount = pct / 100.0;
            if (old != null && Math.abs(old.amount() - amount) < 1e-9) {
                continue;
            }
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, amount, flat ? AttributeModifier.Operation.ADD_VALUE
                    : AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A stat's sum held to its range of v1 13.3 (several statuses never go past one status's limits). */
    public static double clampStat(Stat stat, double percent) {
        var r = HeroCatalogue.STAT_RANGES.get(stat);
        return r == null ? percent : Math.max(r.min(), Math.min(r.max(), percent));
    }

    // ------------------------------------------------------------------ shield (v1 13.2)

    private static final ResourceLocation SHIELD_ID = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_shield");

    /**
     * Gives a shield of {@code amount} absorption for {@code seconds}: at most 60% of the receiver's max health, a
     * stronger shield replaces a weaker one, they never add (v1 13.2). Returns the absorption it added.
     */
    public double shield(LivingEntity e, double amount, double seconds) {
        long now = e.level().getGameTime();
        Entry cur = book.shield(now);
        double remaining = cur == null ? 0 : Math.min(cur.amount, e.getAbsorptionAmount());
        double next = com.factionclash.logic.hero.HeroRules.shieldAfter(remaining, amount, e.getMaxHealth());
        if (next <= remaining + 1e-6) {
            return 0;
        }
        AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
        if (max != null) {
            max.removeModifier(SHIELD_ID);
            max.addTransientModifier(new AttributeModifier(SHIELD_ID, next, AttributeModifier.Operation.ADD_VALUE));
        }
        float others = (float) Math.max(0, e.getAbsorptionAmount() - remaining);
        e.setAbsorptionAmount((float) (others + next));
        book.shield(next, seconds, now);
        track(e);
        return next - remaining;
    }

    /** A shield ran out: what is left of it goes (other absorption, a golden apple's, stays). */
    private static void removeShield(LivingEntity e, double granted) {
        double left = Math.min(granted, e.getAbsorptionAmount());
        AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
        if (max != null) {
            max.removeModifier(SHIELD_ID);
        }
        e.setAbsorptionAmount((float) Math.max(0, e.getAbsorptionAmount() - left));
    }

    // ------------------------------------------------------------------ saving

    /** Written with the entity (nothing when it carries no status); every time is a game time. */
    public static final IAttachmentSerializer<Tag, HeroStatus> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public HeroStatus read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
            HeroStatus s = new HeroStatus();
            if (tag instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    Entry e = readEntry(list.getCompound(i));
                    if (e != null) {
                        s.book.restore(e);
                    }
                }
            }
            return s;
        }

        @Override
        public Tag write(HeroStatus s, HolderLookup.Provider provider) {
            if (s.book.isEmpty()) {
                return null;
            }
            ListTag list = new ListTag();
            for (Entry e : s.book.entries()) {
                list.add(writeEntry(e));
            }
            return list;
        }
    };

    static CompoundTag writeEntry(Entry e) {
        CompoundTag t = new CompoundTag();
        t.putString("kind", e.kind.name().toLowerCase(Locale.ROOT));
        t.putString("id", e.id);
        t.putString("source", e.source);
        if (e.element != null) {
            t.putString("element", e.element.id());
        }
        if (e.stat != null) {
            t.putString("stat", e.stat.id());
        }
        t.putDouble("amount", e.amount);
        t.putInt("stacks", e.stacks);
        t.putInt("max", e.maxStacks);
        t.putLong("until", e.expiresAt);
        t.putLong("next", e.nextTickAt);
        t.putBoolean("glow", e.glow);
        t.putBoolean("enemy", e.enemy);
        return t;
    }

    static Entry readEntry(CompoundTag t) {
        StatusBook.Kind kind;
        try {
            kind = StatusBook.Kind.valueOf(t.getString("kind").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Entry e = new Entry(kind, t.getString("id"), t.getString("source"));
        if (t.contains("element")) {
            e.element = HeroCatalogue.enumOf(Element.class, t.getString("element"));
        }
        if (t.contains("stat")) {
            e.stat = HeroCatalogue.enumOf(Stat.class, t.getString("stat"));
            if (e.stat == null && kind == StatusBook.Kind.STAT) {
                return null;
            }
        }
        if (kind == StatusBook.Kind.DOT && e.element == null) {
            e.element = Element.PHYSICAL;
        }
        e.amount = t.getDouble("amount");
        e.stacks = Math.max(1, t.getInt("stacks"));
        e.maxStacks = Math.max(1, t.getInt("max"));
        e.expiresAt = t.getLong("until");
        e.nextTickAt = t.getLong("next");
        e.glow = t.getBoolean("glow");
        e.enemy = t.getBoolean("enemy");
        return e;
    }
}
