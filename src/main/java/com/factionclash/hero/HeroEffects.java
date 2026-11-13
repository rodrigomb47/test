package com.factionclash.hero;

import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.Conditions;
import com.factionclash.logic.hero.EffectRouting;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroRules.HitContext;
import com.factionclash.logic.hero.Sense;
import com.factionclash.logic.hero.StatusBook;
import com.factionclash.logic.hero.UseLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The effects of HERO_FORMAT.md 13 that the engine runs (task A3: {@code damage}, {@code dot}, {@code heal},
 * {@code shield}, {@code effect}, {@code stat}, {@code knockback}, {@code pull}, {@code ignite}, {@code freeze},
 * {@code cleanse}, {@code energy}, {@code cooldown_cut}, {@code move_self}, {@code mark}, {@code clear_marks}; task A4:
 * {@code stun}, {@code taunt}, {@code dispel}, {@code empower}, {@code summon}; task A5: format 2's {@code guard_break},
 * {@code poise}, {@code silence}, {@code tether}, {@code barrier}, {@code spawn_add}, {@code call_guards}, a move's
 * {@code breaks_guard} and {@code unblockable}). Each effect goes to the receivers its {@code to} names
 * ({@link EffectRouting}), only to those its recipient class allows, rolled by its {@code chance} and checked by its
 * {@code if} for each receiver ({@link Conditions}). Amounts read the hero's attack power, health and armour and the
 * receiver's health and marks at hit time ({@link HeroRules#amountAtHit}), grow with ability power and
 * {@code heroes.powerScale}, and are held per use and receiver to the slot caps ({@link UseLedger}). One code path for
 * every receiver: nothing here branches on a player.
 */
public final class HeroEffects {
    private HeroEffects() {
    }

    /** A {@code stat} or {@code effect} with no {@code seconds} given by a passive lasts this long (v1 13.3 default). */
    static final double DEFAULT_STAT_SECONDS = 8;

    // ------------------------------------------------------------------ routing

    /**
     * Applies one effect list of a use: {@code listIndex} tells lists apart for once-per-use effects (a combo's
     * strikes), {@code formReceivers} are whoever the form reached, {@code direct} what struck (the hero or its
     * projectile).
     */
    public static void apply(HeroUse use, List<Effect> effects, int listIndex, List<LivingEntity> formReceivers,
                             Entity direct) {
        HeroEntity hero = use.hero;
        LivingEntity struck = null;
        for (int i = 0; i < effects.size(); i++) {
            Effect e = effects.get(i);
            if (!EffectRouting.EFFECTS.contains(e.type())) {
                continue;
            }
            EffectRouting.Receivers to = EffectRouting.receivers(e, use.form);
            if (EffectRouting.oncePerUse(e) && !use.firstTime(listIndex, i)) {
                continue;
            }
            List<LivingEntity> rs = switch (to) {
                case FORM -> formReceivers;
                case SELF -> List.of(hero);
                // v1 10.2: the allies inside the same area; an effect that names its own radius reaches that far from
                // the area's centre instead (review of A3-A6: Hinae's rite heals allies within 10 while it strikes
                // enemies within 6, Ilsabet's vow shields allies within 8, as their kit sheets say)
                case ALLIES_IN_AREA -> use.areaCenter == null ? List.of(hero)
                        : HeroForms.alliesNear(hero, use.areaCenter, e.c().radius() > 0 ? e.c().radius() : use.areaRadius);
                case ALLIES_AROUND -> HeroForms.alliesNear(hero, hero.position(), radius(e));
                case MOST_HURT_ALLY -> List.of(HeroForms.mostHurtAlly(hero, radius(e)));
            };
            for (int k = 0; k < rs.size(); k++) {
                LivingEntity r = rs.get(k);
                if (r == null || !r.isAlive()) {
                    continue;
                }
                boolean self = r == hero;
                boolean enemy = !self && HeroForms.enemy(hero, r);
                if (!EffectRouting.mayReach(e, enemy, self) || !enemy && !self && !HeroForms.ally(hero, r)) {
                    continue;
                }
                if (e.c().chance() < 100 && hero.getRandom().nextInt(100) >= e.c().chance()) {
                    continue;
                }
                if (e.c().cond() != null && !holds(hero, e.c().cond(), r)) {
                    continue;
                }
                one(use, e, listIndex, i, r, enemy, direct);
                if (enemy && struck == null) {
                    struck = r;
                }
            }
        }
        if (struck != null) {
            HeroSounds.impact(use, struck); // S2: the move's impact where it landed, whoever it struck (6.15)
        }
    }

    private static double radius(Effect e) {
        return e.c().radius() > 0 ? e.c().radius() : 8;
    }

    /** An effect's {@code if} for one receiver: the receiver is the sense's target (v1 11). */
    static boolean holds(HeroEntity hero, HeroDef.Condition c, LivingEntity r) {
        Sense s = hero.combat().effectSense();
        HeroSenses.fill(hero, null, s, hero.energy(), 0);
        HeroSenses.target(hero, r, s.target, true);
        return Conditions.holds(c, s);
    }

    // ------------------------------------------------------------------ one effect on one receiver

    static String key(Entity r) {
        return r.getStringUUID();
    }

    static void one(HeroUse use, Effect e, int list, int idx, LivingEntity r, boolean enemy, Entity direct) {
        HeroEntity hero = use.hero;
        long now = hero.level().getGameTime();
        String statusKey = use.key + "#" + list + "." + idx;
        switch (e) {
            case HeroDef.Damage d -> {
                double raw = raw(use, d.amount(), r);
                raw = use.ledger.take(UseLedger.Kind.DAMAGE, key(r), raw);
                Element el = element(use, d.element());
                // 6.9: a breaks_guard move knocks a raised shield aside (the axe rule) unless it has its own guard_break;
                // an unblockable move, every grab among them, goes through a shield (the same for every receiver)
                boolean guardUp = use.move != null && use.move.breaksGuard() && HeroStatus.shieldUp(r);
                // a zone bursts from the ground under its receivers, so no shield stops it (SLICE_PLAN 3.4: "zones
                // ignore shields"; review of A3-A6), the same for every receiver
                boolean unblockable = use.move != null && (use.move.unblockable() || use.move.form() == HeroDef.Form.GRAB)
                        || use.form == HeroDef.Form.ZONE;
                float lost = unblockable ? HeroDamage.dealUnblockable(hero, direct, r, el, raw)
                        : HeroDamage.deal(hero, direct, r, el, raw, false);
                if (guardUp && !hasGuardBreak(use.move) && HeroStatus.guardBreak(r, BREAKS_GUARD_SECONDS)) {
                    use.landed |= enemy;
                }
                if (lost > 0) {
                    use.landed |= enemy;
                    hero.markAttacked();
                    if (d.drain() > 0) {
                        hero.heal((float) (lost * d.drain() / 100.0));
                    }
                }
                elementParticles(hero.level(), r, el, 6);
            }
            case HeroDef.Dot d -> {
                double perSecond = raw(use, d.amount(), r);
                double total = use.ledger.take(UseLedger.Kind.DOT, key(r), perSecond * d.seconds());
                if (total <= 0) {
                    return;
                }
                Element el = element(use, d.element());
                HeroStatus.of(r).book().dot(hero.getStringUUID(), statusKey, el, total / d.seconds(), d.seconds(),
                        d.maxStacks(), now);
                use.landed |= enemy;
                elementParticles(hero.level(), r, el, 4);
            }
            case HeroDef.Heal h -> {
                double x = use.ledger.take(UseLedger.Kind.HEAL, key(r), raw(use, h.amount(), r));
                float before = r.getHealth();
                r.heal((float) x);
                restored(use, r, r.getHealth() - before);
                particles(hero.level(), r, ParticleTypes.HEART, 3);
            }
            case HeroDef.Shield s -> {
                double x = use.ledger.take(UseLedger.Kind.SHIELD, key(r), raw(use, s.amount(), r));
                if (x > 0) {
                    restored(use, r, HeroStatus.of(r).shield(r, x, s.seconds()));
                }
            }
            case HeroDef.PotionEffect p -> {
                Holder<MobEffect> h = potion(p.id());
                if (h != null) {
                    r.addEffect(new MobEffectInstance(h, Math.max(1, (int) HeroRules.ticks(p.seconds())),
                            Math.max(0, p.level() - 1)), hero);
                    use.landed |= enemy;
                }
            }
            case HeroDef.StatMod s -> {
                HeroStatus st = HeroStatus.of(r);
                st.book().stat(hero.getStringUUID(), statusKey, s.stat(), s.percent(),
                        s.seconds() > 0 ? s.seconds() : DEFAULT_STAT_SECONDS, s.maxStacks(), now);
                st.book().consumeStatsDirty();
                HeroStatus.applyStats(r, now);
                use.landed |= enemy;
            }
            case HeroDef.Knockback k -> {
                knockback(hero, r, k.strength(), k.up());
                use.landed |= enemy;
            }
            case HeroDef.Pull p -> {
                pull(hero, r, p.strength());
                use.landed |= enemy;
            }
            case HeroDef.Ignite g -> {
                r.igniteForSeconds((float) g.seconds());
                use.landed |= enemy;
            }
            case HeroDef.Freeze f -> {
                HeroStatus.of(r).book().freeze(f.seconds(), now);
                if (r.canFreeze()) {
                    r.setTicksFrozen(Math.max(r.getTicksFrozen(), r.getTicksRequiredToFreeze() + 2));
                }
                use.landed |= enemy;
            }
            case HeroDef.Cleanse c -> cleanse(r, now);
            case HeroDef.Energy en -> {
                if (r instanceof HeroEntity h) {
                    h.combat().gain(en.amount());
                }
            }
            case HeroDef.CooldownCut cc -> hero.combat().cutCooldowns(cc.seconds());
            case HeroDef.MoveSelf m -> hero.combat().hop(m.direction(), m.distance());
            case HeroDef.Mark m -> {
                HeroStatus st = HeroStatus.of(r);
                StatusBook.MarkResult res = st.book().mark(m.mark(), m.stacks(), m.maxStacks(), m.seconds(),
                        hero.getStringUUID(), m.glow(), !CombatRules.sameSide(hero, r) && r != hero, now);
                if (m.glow()) {
                    r.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) HeroRules.ticks(m.seconds()), 0, true, false));
                }
                use.landed |= enemy;
                if (res.full()) {
                    hero.combat().markFull(m.mark(), r); // the on_mark_full passives are A4's
                }
            }
            case HeroDef.ClearMarks m -> HeroStatus.of(r).book().clearMarks(m.mark(), now);
            case HeroDef.Stun st -> {
                // the slot's stun seconds per use and receiver (v1 16.1); the receiver's immunity decides the rest (6.14)
                double secs = use.ledger.take(UseLedger.Kind.STUN, key(r), st.seconds());
                if (secs > 0 && HeroStatus.stun(r, secs)) {
                    use.landed |= enemy;
                    particles(hero.level(), r, ParticleTypes.CRIT, 8);
                }
            }
            case HeroDef.Taunt t -> {
                if (HeroStatus.taunt(r, hero, t.seconds())) {
                    use.landed |= enemy;
                    particles(hero.level(), r, ParticleTypes.ANGRY_VILLAGER, 2);
                    if (!use.taunted) {
                        use.taunted = true;
                        hero.playAction(com.factionclash.logic.hero.HeroActions.actionCode("taunt"), 1.0, 0);
                    }
                }
            }
            case HeroDef.Dispel x -> {
                if (HeroStatus.dispel(r)) {
                    use.landed |= enemy;
                    particles(hero.level(), r, ParticleTypes.SMOKE, 6);
                }
            }
            case HeroDef.Empower em -> {
                String state = com.factionclash.logic.hero.MoveSelector.stateOf(em, use.def.v2());
                if (state != null) {
                    hero.combat().enterState(state, em.seconds());
                }
            }
            case HeroDef.Summon sm -> HeroSummons.summon(hero, sm.creature(), sm.count(), sm.seconds(), sm.name());
            // ---- format 2's effects (HEROES_V2 6.6; task A5), the same on every receiver
            case HeroDef.GuardBreak g -> {
                if (HeroStatus.guardBreak(r, g.seconds())) {
                    use.landed |= enemy;
                    particles(hero.level(), r, ParticleTypes.CRIT, 6);
                }
            }
            case HeroDef.Poise p -> {
                // poise damage is a hero's (heroes fighting each other); anyone else has no poise to fill
                if (r instanceof HeroEntity h && h != hero) {
                    h.combat().poiseDamage(p.amount());
                    use.landed |= enemy;
                }
            }
            case HeroDef.Silence si -> {
                HeroStatus.silence(r, si.seconds());
                use.landed |= enemy;
                particles(hero.level(), r, ParticleTypes.SMOKE, 4);
            }
            case HeroDef.Tether te -> {
                HeroStatus.tether(r, hero, te.seconds(), te.max());
                use.landed |= enemy;
            }
            case HeroDef.Barrier b -> hero.combat().barrier(b.hits(), b.seconds(), b.focus());
            case HeroDef.SpawnAdd a -> hero.combat().spawnAdds(a.add(), a.count());
            case HeroDef.CallGuards c -> com.factionclash.combat.Alerts.callGuards(hero, hero.getTarget(),
                    CALL_GUARDS_RADIUS, c.count());
            default -> {
            }
        }
    }

    /** A {@code breaks_guard} move knocks a raised shield aside for 5 s, as an axe does (6.9). */
    public static final double BREAKS_GUARD_SECONDS = 5;
    /** {@code call_guards} reaches soldiers of the hero's faction this far (6.6). */
    public static final double CALL_GUARDS_RADIUS = 32;

    private static boolean hasGuardBreak(HeroDef.Move m) {
        for (List<Effect> l : m.effectLists()) {
            for (Effect e : l) {
                if (e instanceof HeroDef.GuardBreak) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Energy for healing and shielding (v1 14: 1 per 2 points restored, at most 8 per use; the hero counts as an
     * ally, A3) and combat XP for what it restores to others (v1 22.1: 1 per 5 points; healing itself earns none).
     */
    private static void restored(HeroUse use, LivingEntity r, double points) {
        if (points <= 0) {
            return;
        }
        if (r != use.hero) {
            use.hero.addHealXp(points);
        }
        double give = Math.min(HeroRules.ENERGY_PER_EVENT_CAP - use.healEnergy, HeroRules.energyFromHealing(points));
        if (give > 0) {
            use.healEnergy += give;
            use.hero.combat().gain(give);
        }
    }

    // ------------------------------------------------------------------ amounts (v1 12)

    /** An Amount on a receiver at hit time, x ability power x {@code heroes.powerScale}: v1 6.3's {@code raw}. */
    static double raw(HeroUse use, Amount a, LivingEntity r) {
        HitContext c = context(use, a, r);
        double at = HeroRules.amountAtHit(a, c);
        return com.factionclash.logic.hero.HeroBalance.powerScaled(at * use.abilityPower, powerScale());
    }

    public static int powerScale() {
        return com.factionclash.logic.hero.HeroBalance.clampPowerScale(FCConfig.HERO_POWER_SCALE.get());
    }

    static HitContext context(HeroUse use, Amount a, LivingEntity r) {
        HeroEntity hero = use.hero;
        HeroDef d = use.def;
        int stacks = 0;
        if (a.mark() != null) {
            HeroStatus st = HeroStatus.peek(r);
            stacks = st == null ? 0 : st.book().marks(a.mark(), hero.level().getGameTime());
        }
        return new HitContext(attackPower(hero, d), hero.getMaxHealth(), hero.getAttributeValue(Attributes.ARMOR),
                r.getMaxHealth(), r.getMaxHealth() - r.getHealth(), stacks, HeroSenses.boss(r), d.referenceAttack(),
                d.stats().armor() + HeroCatalogue.REFERENCE_WORN_ARMOR);
    }

    /**
     * The hero's attack power (v1 6.2): a melee hero's {@code ATTACK_DAMAGE} (the number a player's hit with the held
     * weapon uses), a bow's 6, a crossbow's 7, a trident's 8, a thrown attack's {@code amount}, each ranged one plus the
     * bonuses ({@code ATTACK_DAMAGE} minus its base 1).
     */
    public static double attackPower(HeroEntity hero, HeroDef d) {
        double attr = hero.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return switch (d.basicAttack().style()) {
            case MELEE -> attr;
            case BOW -> 6 + Math.max(0, attr - 1);
            case CROSSBOW -> 7 + Math.max(0, attr - 1);
            case TRIDENT -> 8 + Math.max(0, attr - 1);
            case THROWN -> d.basicAttack().amount() + Math.max(0, attr - 1);
        };
    }

    static Element element(HeroUse use, Element e) {
        return e != null ? e : use.def.element() != null ? use.def.element() : Element.PHYSICAL;
    }

    // ------------------------------------------------------------------ control and movement (v1 13.4)

    /**
     * {@code knockback}: away from the hero with vanilla's knockback (knock-back resistance applies), plus a lift of
     * {@code up} blocks (at most 1, 6.14) for a receiver on the ground. Hazard-aware for every receiver (6.12): a push
     * that would land it in lava or over a drop of more than 3 blocks is cut to a third.
     */
    public static void knockback(HeroEntity hero, LivingEntity r, double strength, double up) {
        double dx = r.getX() - hero.getX(), dz = r.getZ() - hero.getZ();
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) {
            Vec3 f = HeroForms.facing(hero, r.position());
            dx = f.x;
            dz = f.z;
            l = 1;
        }
        double s = FormGeometry.knockbackStrength(strength);
        if (hazardAhead(r, dx / l, dz / l, 2.5 * s / FormGeometry.KNOCKBACK_PER_STRENGTH)) {
            s /= 3;
        }
        r.knockback(s, -dx / l, -dz / l);
        if (up > 0 && r.onGround()) {
            Vec3 v = r.getDeltaMovement();
            double lift = FormGeometry.liftImpulse(up) * (1 - r.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            r.setDeltaMovement(v.x, Math.max(v.y, lift), v.z);
        }
        r.hurtMarked = true;
    }

    /** {@code pull}: toward the hero, two blocks per point of strength, never onto it; a small hop carries it. */
    public static void pull(HeroEntity hero, LivingEntity r, double strength) {
        double dx = hero.getX() - r.getX(), dz = hero.getZ() - r.getZ();
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) {
            return;
        }
        double v = FormGeometry.pullSpeed(strength, l) * (1 - r.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (v <= 0) {
            return;
        }
        r.setDeltaMovement(dx / l * v, Math.max(r.getDeltaMovement().y, FormGeometry.PULL_LIFT), dz / l * v);
        r.hasImpulse = true;
        r.hurtMarked = true;
    }

    /**
     * Whether a throw of about {@code blocks} along (dx, dz) would land {@code r} in danger (HEROES_V2 6.12, task A5):
     * lava or fire where it lands or on the way, or no floor within 6 blocks below (a deep drop or the void). The same
     * test for every receiver.
     */
    public static boolean landingHazard(LivingEntity r, double dx, double dz, double blocks) {
        Level level = r.level();
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-6) {
            return false;
        }
        double ux = dx / l, uz = dz / l;
        int steps = (int) Math.ceil(Math.max(1, blocks + 1));
        for (int s = 1; s <= steps; s++) {
            double d = Math.min(s, blocks + 1);
            BlockPos land = BlockPos.containing(r.getX() + ux * d, r.getY() + 0.1, r.getZ() + uz * d);
            if (!level.getBlockState(land).getCollisionShape(level, land).isEmpty()
                    && !level.getBlockState(land.above()).getCollisionShape(level, land.above()).isEmpty()) {
                return false; // a wall stops the throw here, before anything further
            }
            boolean floor = false;
            for (int i = -1; i <= 6; i++) {
                BlockPos p = land.below(i);
                var st = level.getBlockState(p);
                if (level.getFluidState(p).is(FluidTags.LAVA) || st.is(net.minecraft.tags.BlockTags.FIRE)
                        || st.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        || st.is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
                    return true;
                }
                if (i >= 0 && !st.getCollisionShape(level, p).isEmpty()) {
                    floor = true;
                    break;
                }
            }
            if (!floor && s >= steps - 1) {
                return true;
            }
        }
        return false;
    }

    /** Lava where a push of about {@code blocks} along (dx, dz) would land it, or no floor within 3 blocks below. */
    static boolean hazardAhead(LivingEntity r, double dx, double dz, double blocks) {
        Level level = r.level();
        BlockPos land = BlockPos.containing(r.getX() + dx * blocks, r.getY() + 0.1, r.getZ() + dz * blocks);
        for (int i = 0; i <= 3; i++) {
            BlockPos p = land.below(i);
            if (level.getFluidState(p).is(FluidTags.LAVA)) {
                return true;
            }
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code cleanse} (v1 13.2): harmful potion effects, hero DoTs, marks an enemy put on it, harmful stat modifiers,
     * fire, freezing and a stun.
     */
    static void cleanse(LivingEntity r, long now) {
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance i : r.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                bad.add(i.getEffect());
            }
        }
        bad.forEach(r::removeEffect);
        r.clearFire();
        r.setTicksFrozen(0);
        HeroStatus st = HeroStatus.peek(r);
        if (st != null && st.book().cleanse(null)) {
            st.book().consumeStatsDirty();
            HeroStatus.applyStats(r, now);
        }
        HeroStatus.releaseIfFree(r); // a cleanse ends a stun (its immunity stays, A4)
        particles(r.level(), r, ParticleTypes.HAPPY_VILLAGER, 6);
    }

    static Holder<MobEffect> potion(String id) {
        Optional<Holder.Reference<MobEffect>> h = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace(id));
        return h.orElse(null);
    }

    // ------------------------------------------------------------------ looks (v1 10.5: fixed particle counts)

    public static ParticleOptions particle(Element e) {
        return switch (e == null ? Element.PHYSICAL : e) {
            case PHYSICAL -> ParticleTypes.CRIT;
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case SHOCK -> ParticleTypes.ELECTRIC_SPARK;
            case VENOM -> new DustParticleOptions(new Vector3f(0x5B / 255F, 0xAE / 255F, 0x3C / 255F), 1F);
            case BLIGHT -> new DustParticleOptions(new Vector3f(0x2E / 255F, 0x2A / 255F, 0x33 / 255F), 1F);
            case ARCANE -> ParticleTypes.ENCHANT;
            case RADIANT -> ParticleTypes.END_ROD;
        };
    }

    /** A look's particle: its {@code particle} (a {@code dust} in its colour) or the element's. */
    public static ParticleOptions particle(HeroDef.Look look, Element e, int heroColor) {
        if (look == null || look.particle() == null) {
            return particle(e);
        }
        if ("dust".equals(look.particle())) {
            int c = look.color() != HeroDef.NO_COLOR ? look.color() : heroColor != HeroDef.NO_COLOR ? heroColor : 0xFFFFFF;
            return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1F);
        }
        var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.withDefaultNamespace(look.particle()));
        return type instanceof SimpleParticleType s ? s : particle(e);
    }

    public static void elementParticles(Level level, Entity at, Element e, int count) {
        particles(level, at, particle(e), count);
    }

    public static void particles(Level level, Entity at, ParticleOptions p, int count) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(p, at.getX(), at.getY(0.5), at.getZ(), count, at.getBbWidth() / 2, at.getBbHeight() / 4,
                    at.getBbWidth() / 2, 0.02);
        }
    }

    /** A line of particles from a to b (a bolt, a chain's jump, a line), about two a block. */
    public static void particleLine(Level level, Vec3 a, Vec3 b, ParticleOptions p) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        double d = a.distanceTo(b);
        int n = (int) Math.min(48, Math.max(2, d * 2));
        for (int i = 0; i <= n; i++) {
            Vec3 c = a.lerp(b, i / (double) n);
            sl.sendParticles(p, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
    }

    /** A ring of particles on the ground (a zone or a nova). */
    public static void particleRing(Level level, Vec3 c, double r, ParticleOptions p) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        int n = (int) Math.min(40, Math.max(8, r * 6));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            sl.sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }
}
