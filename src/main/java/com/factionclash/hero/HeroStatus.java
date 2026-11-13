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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What heroes' moves leave on a living entity (HERO_FORMAT.md 13 and 19; tasks A3 and A4): its {@link StatusBook} of
 * marks, damage over time, stat modifiers, shield, freeze hold, stun and taunt, applied to the entity the same way
 * whoever carries it (a player, an NPC, a hero or a monster). A data attachment ({@link HeroAttachments#STATUS}), saved with its entity and
 * with every expiry as a game time, so a status keeps running across a save; stat modifiers are applied again on
 * load. Every entity carrying something is ticked once a server tick ({@link #tickAll}): expiry, the DoTs' seconds
 * ({@link HeroDamage#dotTick}), the freeze hold and the attribute modifiers of the stats.
 *
 * <p><b>Control</b> (A4, HEROES_V2 6.14-6.15): a stun stops every receiver for its seconds. Everyone stunned gets the
 * {@code factionclash:stun} modifier of -100% on movement speed and jump strength; a goal-based mob (an NPC, a hero, a
 * zombie) also gets {@link com.factionclash.combat.StunnedGoal}, which stops its attack and move goals (a hero's move in
 * its wind-up is interrupted); a player's attacks, item use, interactions, block breaking and placing are cancelled by
 * {@link HeroControlEvents}, and so is a stunned attacker's melee blow of any kind. The camera still turns and a
 * knock-back still moves it. After a stun nobody can stun it again for twice its length. Vanilla bosses are immune. A
 * taunt holds every receiver the same way: a mob or an NPC targets the taunting hero again every second (a
 * brain-based mob keeps its own mind), and every receiver, players included, deals 50% less damage to anyone but the
 * taunting hero for its seconds; the hero glows meanwhile. A dispel takes the helpful part away.
 *
 * <p><b>Format 2's effects</b> (A5, HEROES_V2 6.6), the same on every carrier: a silence (a hero starts no move), a
 * tether to the spot where it landed (pulled back each tick past its length), a broken guard (a raised shield goes down,
 * none goes up for its seconds, a player's shield also on vanilla's cooldown) and a hero's barrier (in the book; the hero
 * blocks with it).
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
            if (x.stun() && !book.stunned(now)) {
                releaseStun(e);
            }
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
        if (book.stunned(now)) {
            holdStun(e);
        }
        if ((now & 15) == 0 && e instanceof Mob m) {
            String by = book.tauntedBy(now);
            if (by != null) {
                retarget(m, by);
            }
        }
        StatusBook.Entry te = book.tether(now);
        if (te != null) {
            tetherTick(e, te, now);
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
        releaseStun(e);
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

    // ------------------------------------------------------------------ control (A4: stun, taunt, dispel)

    /** The modifier that stops a stunned entity's walking and jumping (-100%), on players and NPCs alike. */
    public static final ResourceLocation STUN_ID = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "stun");

    /** Vanilla's bosses ignore stuns and taunts (v1 13.4); a hero is not one of them here. */
    public static boolean immuneToControl(LivingEntity e) {
        return HeroSenses.boss(e) && !(e instanceof HeroEntity);
    }

    /** A brain-based mob (a villager, a piglin, a warden: no goals) keeps its own mind under a taunt (v1 13.4). */
    public static boolean brainBased(Mob m) {
        return m.goalSelector.getAvailableGoals().isEmpty();
    }

    /** Whether the entity is stunned now. */
    public static boolean stunned(LivingEntity e) {
        HeroStatus s = peek(e);
        return s != null && !s.book.isEmpty() && s.book.stunned(e.level().getGameTime());
    }

    /**
     * Stuns {@code r} for {@code seconds}, the same for every receiver (HEROES_V2 6.15): false, and nothing changes,
     * when it is a vanilla boss or an earlier stun's immunity still holds (6.14: it belongs to the receiver, whoever
     * stunned it). A held item is lowered (a drawn bow, a raised shield, food); a hero's move in its wind-up is
     * interrupted.
     */
    public static boolean stun(LivingEntity r, double seconds) {
        if (r.level().isClientSide || !r.isAlive() || immuneToControl(r)) {
            return false;
        }
        HeroStatus s = of(r);
        if (!s.book.stun(seconds, r.level().getGameTime())) {
            return false;
        }
        holdStun(r);
        if (r.isUsingItem()) {
            r.stopUsingItem();
        }
        if (r instanceof Mob m) {
            com.factionclash.combat.StunnedGoal.ensure(m);
            m.getNavigation().stop();
            m.setJumping(false);
        }
        if (r instanceof HeroEntity h) {
            h.combat().stunned();
        }
        r.hurtMarked = true;
        return true;
    }

    /** Keeps the stun's modifiers on (applied at the stun and again on load). */
    static void holdStun(LivingEntity e) {
        modifier(e, Attributes.MOVEMENT_SPEED, -1);
        modifier(e, Attributes.JUMP_STRENGTH, -1);
    }

    /** Takes the stun's modifiers off when it is no longer stunned (a cleanse removed the stun). */
    static void releaseIfFree(LivingEntity e) {
        if (!stunned(e)) {
            releaseStun(e);
        }
    }

    static void releaseStun(LivingEntity e) {
        modifier(e, Attributes.MOVEMENT_SPEED, 0);
        modifier(e, Attributes.JUMP_STRENGTH, 0);
        if (e instanceof HeroEntity h && com.factionclash.logic.hero.HeroActions.code(h.action())
                == com.factionclash.logic.hero.HeroActions.actionCode("stun")) {
            h.stopAction(); // the stun pose ends with the stun
        }
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attr, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier old = inst.getModifier(STUN_ID);
        if (amount == 0) {
            if (old != null) {
                inst.removeModifier(STUN_ID);
            }
        } else if (old == null) {
            inst.addTransientModifier(new AttributeModifier(STUN_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * Taunts {@code r} for {@code seconds} by {@code hero} (HEROES_V2 6.15): a goal-based mob or NPC targets the hero
     * now and again every 16 ticks; every receiver deals half damage to anyone but the hero meanwhile
     * ({@link #tauntFactor}); the hero glows for the taunt's seconds (presentation). False for a vanilla boss.
     */
    public static boolean taunt(LivingEntity r, HeroEntity hero, double seconds) {
        if (r.level().isClientSide || !r.isAlive() || immuneToControl(r)) {
            return false;
        }
        HeroStatus s = of(r);
        s.book.taunt(hero.getStringUUID(), seconds, r.level().getGameTime());
        if (r instanceof Mob m && !brainBased(m)) {
            m.setTarget(hero);
        }
        int ticks = (int) com.factionclash.logic.hero.HeroRules.ticks(seconds);
        MobEffectInstance glow = hero.getEffect(MobEffects.GLOWING);
        if (glow == null || glow.getDuration() < ticks) {
            hero.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false));
        }
        return true;
    }

    private static void retarget(Mob m, String heroUuid) {
        if (brainBased(m) || !(m.level() instanceof ServerLevel sl)) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(heroUuid);
        } catch (IllegalArgumentException ex) {
            return;
        }
        Entity h = sl.getEntity(id);
        if (h instanceof HeroEntity hero && hero.isAlive() && m.getTarget() != hero && m.distanceToSqr(hero) < 48 * 48) {
            m.setTarget(hero);
        }
    }

    /**
     * The share of its damage an attacker deals to {@code target}: 0.5 while a taunt binds it to another hero (to the
     * taunting hero itself, the full amount), else 1. The same for players, NPCs, heroes and monsters (6.15).
     */
    public static double tauntFactor(LivingEntity attacker, Entity target) {
        HeroStatus s = peek(attacker);
        if (s == null || s.book.isEmpty()) {
            return 1;
        }
        String by = s.book.tauntedBy(attacker.level().getGameTime());
        return by == null || by.equals(target.getStringUUID()) ? 1 : 0.5;
    }

    /**
     * {@code dispel} (v1 13.4): the receiver's helpful potion effects (absorption included), its helpful hero stat
     * modifiers and its hero shield. Returns whether anything went.
     */
    public static boolean dispel(LivingEntity r) {
        List<Holder<MobEffect>> good = new ArrayList<>();
        for (MobEffectInstance i : r.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                good.add(i.getEffect());
            }
        }
        good.forEach(r::removeEffect);
        boolean any = !good.isEmpty();
        HeroStatus st = peek(r);
        if (st != null && !st.book.isEmpty()) {
            StatusBook.Dispelled d = st.book.dispel();
            if (d.stats()) {
                st.book.consumeStatsDirty();
                applyStats(r, r.level().getGameTime());
            }
            if (d.shield() > 0) {
                removeShield(r, d.shield());
            }
            any |= d.any();
        }
        return any;
    }

    // ------------------------------------------------------------------ format 2's effects (A5: silence, tether, guard)

    /** A silence (HEROES_V2 6.6): a hero cannot start moves for its seconds; it changes nothing for anyone else. */
    public static void silence(LivingEntity r, double seconds) {
        if (!r.level().isClientSide && r.isAlive()) {
            of(r).book.silence(seconds, r.level().getGameTime());
        }
    }

    public static boolean silenced(LivingEntity e) {
        HeroStatus s = peek(e);
        return s != null && !s.book.isEmpty() && s.book.silenced(e.level().getGameTime());
    }

    /**
     * A tether (6.6) of {@code max} blocks to the spot where {@code r} stands now, for {@code seconds}, by {@code hero}:
     * past its length it is pulled back, every tick, the same for every receiver (a velocity, never a teleport).
     */
    public static void tether(LivingEntity r, HeroEntity hero, double seconds, double max) {
        if (!r.level().isClientSide && r.isAlive()) {
            of(r).book.tether(hero.getStringUUID(), seconds, max, r.getX(), r.getY(), r.getZ(), r.level().getGameTime());
        }
    }

    private static void tetherTick(LivingEntity e, StatusBook.Entry te, long now) {
        double dx = te.x - e.getX(), dy = te.y - e.getY(), dz = te.z - e.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v = com.factionclash.logic.hero.FormGeometry.tetherPull(d, te.amount);
        if (v > 0 && d > 1e-4) {
            Vec3 m = e.getDeltaMovement();
            e.setDeltaMovement(dx / d * v, Math.max(m.y, dy / d * v), dz / d * v);
            e.hasImpulse = true;
            e.hurtMarked = true;
        }
        if ((now & 7) == 0 && e.level() instanceof ServerLevel sl) {
            int n = (int) Math.min(12, Math.max(2, d * 2));
            for (int i = 0; i <= n; i++) {
                double f = i / (double) n;
                sl.sendParticles(ParticleTypes.CRIT, te.x + (e.getX() - te.x) * f, te.y + 0.5 + (e.getY(0.5) - te.y - 0.5) * f,
                        te.z + (e.getZ() - te.z) * f, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Whether {@code e} holds a shield up now (raised, not yet necessarily blocking): a player's or an NPC's. */
    public static boolean shieldUp(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK);
    }

    /**
     * {@code guard_break} (6.6, 6.15): a raised shield goes down and stays down for {@code seconds}, the same for
     * everyone: no shield goes up while its status lasts (the use is cancelled for a player and an NPC alike), and a
     * player's shield also gets vanilla's cooldown for those seconds (the axe rule, shown on the hotbar). False when no
     * shield was up.
     */
    public static boolean guardBreak(LivingEntity r, double seconds) {
        if (r.level().isClientSide || !r.isAlive() || !shieldUp(r)) {
            return false;
        }
        of(r).book.guardBreak(seconds, r.level().getGameTime());
        net.minecraft.world.item.ItemStack used = r.getUseItem();
        r.stopUsingItem();
        if (r instanceof net.minecraft.world.entity.player.Player p) {
            p.getCooldowns().addCooldown(used.getItem(), (int) Math.max(1, com.factionclash.logic.hero.HeroRules.ticks(seconds)));
            r.level().broadcastEntityEvent(p, (byte) 30); // the shield-break sound, as Player.disableShield sends it
        } else {
            r.level().playSound(null, r.getX(), r.getY(), r.getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                    r.getSoundSource(), 0.8F, 0.8F + r.getRandom().nextFloat() * 0.4F);
        }
        return true;
    }

    /** Whether {@code e}'s guard is broken now (no shield may go up). */
    public static boolean guardBroken(LivingEntity e) {
        HeroStatus s = peek(e);
        return s != null && !s.book.isEmpty() && s.book.guardBroken(e.level().getGameTime());
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
        if (e.kind == StatusBook.Kind.TETHER) {
            t.putDouble("x", e.x);
            t.putDouble("y", e.y);
            t.putDouble("z", e.z);
        }
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
        e.x = t.getDouble("x");
        e.y = t.getDouble("y");
        e.z = t.getDouble("z");
        return e;
    }
}
