package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.hero.entity.HeroProjectile;
import com.factionclash.logic.hero.Conditions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.PassiveBook;
import com.factionclash.logic.hero.Sense;
import com.factionclash.logic.hero.UseLedger;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A hero's passives in the world (HERO_FORMAT.md 17; HEROES_V2 6.6; task A4): the triggers' hooks call {@link #fire}
 * with the event, its enemy (the one hit, the attacker, the marked one, the one its ally struck) and its ally (the
 * hurt or low ally, the ally who used an ultimate); the passive's {@code if} is checked with the event's enemy as the
 * target (v1 11: for a passive the target is the attacker), then {@link PassiveBook} decides (cooldown, chance, once
 * per fight, re-arm, the loop guard, at most 8 firings a second) and the effects land on its form's receivers with the
 * passive slot's caps. {@code always} passives hold their stat bonuses while their {@code if} holds (checked every
 * second); {@code every} passives fire on their period while the hero fights. The same for every receiver and every
 * cause: nothing here reads whether anyone is a player.
 */
final class HeroPassives {
    private final HeroEntity hero;
    private PassiveBook book;
    private HeroDef bookFor;
    private boolean[] alwaysOn = new boolean[0];
    private final Sense sense = new Sense();

    HeroPassives(HeroEntity hero) {
        this.hero = hero;
    }

    /** The passive book of the hero's current definition (rebuilt after a reload); null without a definition. */
    PassiveBook book() {
        HeroDef d = hero.combat().def();
        if (d == null) {
            return null;
        }
        if (book == null || bookFor != d) {
            if (book != null) {
                releaseAlways();
            }
            book = new PassiveBook(d.passives());
            bookFor = d;
            alwaysOn = new boolean[d.passives().size()];
        }
        return book;
    }

    /** Whether the hero has any passive with this trigger (cheap: the hooks ask before building an event). */
    boolean has(Trigger t) {
        PassiveBook b = book();
        return b != null && b.has(t);
    }

    /** An event: each passive with its trigger may fire. */
    void fire(PassiveBook.Event e, LivingEntity enemy, LivingEntity ally) {
        PassiveBook b = book();
        if (b == null || !hero.isAlive() || hero.isPreview()) {
            return;
        }
        int[] idx = b.withTrigger(e.trigger());
        for (int i : idx) {
            fireOne(b, i, e, enemy, ally);
        }
    }

    private void fireOne(PassiveBook b, int i, PassiveBook.Event e, LivingEntity enemy, LivingEntity ally) {
        Passive p = b.get(i);
        if (p.cond() != null && !holds(p.cond(), enemy)) {
            return;
        }
        long now = hero.level().getGameTime();
        PassiveBook.Result r = b.tryFire(i, e, now, hero.getRandom().nextInt(100));
        if (r == PassiveBook.Result.RATE && b.takeWarning()) {
            FactionClash.LOGGER.warn("[Heroes] {}: more than {} passive firings in a second; the rest are dropped "
                    + "(v1 17.3, the loop guard)", hero.heroId(), PassiveBook.MAX_FIRINGS_PER_SECOND);
        }
        if (r != PassiveBook.Result.FIRED) {
            return;
        }
        try {
            apply(i, p, enemy, ally);
            firings++;
            HeroSounds.passiveFired(hero, p.name());
        } finally {
            b.done(i);
        }
    }

    /** How many times its passives fired (tests, sim). */
    int firings;

    private boolean holds(HeroDef.Condition c, LivingEntity target) {
        HeroSenses.fill(hero, target != null && target.isAlive() ? target : hero.getTarget(), sense, hero.energy(),
                hero.tickCount - hero.lastTargetTick());
        return Conditions.holds(c, sense);
    }

    /** The form a passive's use counts as (its caps' form factor and its {@code to: allies} area). */
    private static Form form(HeroDef.PassiveForm f) {
        return switch (f == null ? HeroDef.PassiveForm.SELF : f) {
            case SELF -> Form.SELF;
            case TRIGGER_ENEMY -> Form.MELEE;
            case TRIGGER_ALLY, ALLY -> Form.ALLY;
            case ALLIES -> Form.ALLIES;
            case NOVA -> Form.NOVA;
            case BOLT -> Form.BOLT;
            case SHOT -> Form.SHOT;
        };
    }

    private void apply(int i, Passive p, LivingEntity enemy, LivingEntity ally) {
        HeroCombat c = hero.combat();
        HeroDef d = c.def();
        HeroDef.PassiveForm pf = p.form() == null ? HeroDef.PassiveForm.SELF : p.form();
        UseLedger ledger = UseLedger.of(Slot.PASSIVE, c.abilityPower(), HeroEffects.powerScale(), 1);
        HeroUse use = c.newUse(d, null, Slot.PASSIVE, form(pf), "passive." + i, ledger);
        double radius = p.radius() > 0 ? p.radius() : pf == HeroDef.PassiveForm.NOVA ? 4 : 8;
        LivingEntity foe = enemy != null && enemy.isAlive() ? enemy : hero.getTarget();
        List<LivingEntity> rs = new ArrayList<>(4);
        switch (pf) {
            case SELF -> rs.add(hero);
            case TRIGGER_ENEMY -> {
                if (enemy != null && enemy.isAlive()) {
                    rs.add(enemy);
                }
            }
            case TRIGGER_ALLY -> {
                if (ally != null && ally.isAlive()) {
                    rs.add(ally);
                }
            }
            case ALLY -> rs.add(HeroForms.mostHurtAlly(hero, radius));
            case ALLIES -> {
                use.area(hero.position(), radius);
                rs.addAll(HeroForms.alliesNear(hero, hero.position(), radius));
            }
            case NOVA -> {
                use.area(hero.position(), radius);
                rs.addAll(HeroForms.enemiesNear(hero, hero.position().add(0, 0.5, 0), radius));
                HeroEffects.particleRing(hero.level(), hero.position(), radius, HeroEffects.particle(p.look(), d.element(), d.color()));
            }
            case BOLT -> {
                if (foe != null && foe.isAlive() && HeroForms.enemy(hero, foe) && hero.distanceTo(foe) <= PassiveBook.BOLT_RANGE
                        && hero.getSensing().hasLineOfSight(foe)) {
                    rs.add(foe);
                    HeroEffects.particleLine(hero.level(), hero.getEyePosition(), foe.getEyePosition(),
                            HeroEffects.particle(p.look(), d.element(), d.color()));
                }
            }
            case SHOT -> {
                if (foe != null && foe.isAlive() && HeroForms.enemy(hero, foe) && hero.distanceTo(foe) <= PassiveBook.BOLT_RANGE) {
                    Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
                    HeroProjectile.launchPassive(hero, use, p.effects(), p.projectile(), to, PassiveBook.BOLT_RANGE);
                }
            }
        }
        if (!rs.isEmpty()) {
            HeroEffects.apply(use, p.effects(), 0, rs, hero);
        }
        if (p.line() != null && !p.line().isBlank()) {
            hero.speak(LineEvent.SKILL, p.line());
        }
        if (p.look() != null) {
            HeroEffects.particles(hero.level(), hero, HeroEffects.particle(p.look(), d.element(), d.color()), 6);
        }
    }

    // ------------------------------------------------------------------ the hero's tick

    /**
     * Every tick: {@code every} passives on their period while fighting; every second, {@code always} passives put
     * on or take off their held stat bonuses as their {@code if} holds.
     */
    void tick(boolean fighting) {
        PassiveBook b = book();
        if (b == null) {
            return;
        }
        long now = hero.level().getGameTime();
        int[] every = b.withTrigger(Trigger.EVERY);
        for (int i : every) {
            if (b.everyDue(i, now, fighting)) {
                fireOne(b, i, PassiveBook.Event.of(Trigger.EVERY), hero.getTarget(), null);
            }
        }
        int[] always = b.withTrigger(Trigger.ALWAYS);
        if (always.length > 0 && hero.tickCount % 20 == 0) {
            for (int i : always) {
                Passive p = b.get(i);
                boolean on = p.cond() == null || holds(p.cond(), hero.getTarget());
                if (on != alwaysOn[i]) {
                    alwaysOn[i] = on;
                    holdAlways(i, p, on, now);
                }
            }
        }
    }

    /** An {@code always} passive's stats: held on the hero while on, removed when off (v1 17.1). */
    private void holdAlways(int i, Passive p, boolean on, long now) {
        HeroStatus st = on ? HeroStatus.of(hero) : HeroStatus.peek(hero);
        if (st == null) {
            return;
        }
        boolean any = false;
        for (int k = 0; k < p.effects().size(); k++) {
            if (p.effects().get(k) instanceof HeroDef.StatMod s) {
                String key = "always." + i + "." + k;
                if (on) {
                    st.book().stat(HeroStates.SOURCE + ".always", key, s.stat(), s.percent(), 0, 1, now);
                    any = true;
                } else {
                    any |= st.book().removeStat(HeroStates.SOURCE + ".always", key);
                }
            }
        }
        if (any) {
            st.book().consumeStatsDirty();
            HeroStatus.applyStats(hero, now);
        }
    }

    private void releaseAlways() {
        if (book == null) {
            return;
        }
        long now = hero.level().getGameTime();
        for (int i : book.withTrigger(Trigger.ALWAYS)) {
            if (i < alwaysOn.length && alwaysOn[i]) {
                alwaysOn[i] = false;
                holdAlways(i, book.get(i), false, now);
            }
        }
    }

    /** A fight began: once-per-fight passives re-arm, {@code every} counts from now. */
    void fightStarted() {
        PassiveBook b = book();
        if (b != null) {
            b.fightStarted(hero.level().getGameTime());
        }
    }

    /** The hero's health share changed (a heal): low-health passives re-arm 20 points above their value. */
    void health(double share) {
        PassiveBook b = book();
        if (b != null) {
            b.health(share);
        }
    }

    /** Death: held stats go (the status book clears itself too). */
    void reset() {
        releaseAlways();
    }

    /** Whether a held always-stat is on (tests). */
    boolean alwaysOn(int i) {
        return i < alwaysOn.length && alwaysOn[i];
    }
}
