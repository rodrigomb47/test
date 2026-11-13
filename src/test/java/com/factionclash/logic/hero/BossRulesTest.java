package com.factionclash.logic.hero;

import com.factionclash.logic.hero.BossRules.Capped;
import com.factionclash.logic.hero.BossRules.Fight;
import com.factionclash.logic.hero.BossRules.FightEvent;
import com.factionclash.logic.hero.BossRules.Hurt;
import com.factionclash.logic.hero.BossRules.HurtLedger;
import com.factionclash.logic.hero.BossRules.HurtRecord;
import com.factionclash.logic.hero.BossRules.Landed;
import com.factionclash.logic.hero.BossRules.Poise;
import com.factionclash.logic.hero.BossRules.WeaponKind;
import com.factionclash.logic.hero.BossRules.WeaponTier;
import com.factionclash.logic.hero.BossRules.Window;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.WeakPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BossRules} (HEROES_V2 6 and 14.3): boss health the same whoever and however many attack, the per-hit cap for
 * every attacker with the excess to poise and none without an attacker, the hurt cooldown per attacker, poise by
 * weapon, the parry window, stagger and its growth, the damage-taken windows, enrage and its +25% ceiling, the fight
 * clock, phases, the intro, the receiver's stun immunity, air poise, the fight's ceiling and reach clock, the
 * reference duel of SLICE_PLAN criterion 15, and equal treatment (the API takes no "is a player" input).
 */
class BossRulesTest {
    private static final double EPS = 1e-9;
    private static final Set<WeakPoint> NONE = EnumSet.noneOf(WeakPoint.class);

    // ------------------------------------------------------------------ health

    @Test
    void bossHealthIsTheSameWhoeverAndHowManyAttack() {
        // 6.2's example: an epic vanguard leader at level 10, base 34 + 9 x 0.8 = 41.2: about 165 against anyone
        double base = 34 + 9 * 0.8;
        double scale = BossRules.healthScale(350, true, 100);
        assertEquals(400, scale, EPS, "epic 350, +50 leading");
        double max = BossRules.maxHealth(base, scale, BossRules.roleFactor(Role.VANGUARD));
        assertEquals(164.8, max, 1e-6);
        assertEquals(3.0, BossRules.healthModifier(scale, 1.0), EPS, "the attribute modifier x4 - 1");
        assertEquals(BossRules.maxHealth(base, scale, 1.0), base * (1 + BossRules.healthModifier(scale, 1.0)), 1e-9);
        // the role factors of 6.2
        assertEquals(1.0, BossRules.roleFactor(Role.VANGUARD), EPS);
        assertEquals(1.2, BossRules.roleFactor(Role.STRIKER), EPS);
        assertEquals(1.25, BossRules.roleFactor(Role.SKIRMISHER), EPS);
        assertEquals(1.25, BossRules.roleFactor(Role.SUPPORT), EPS);
        assertEquals(1.25, BossRules.roleFactor(Role.CONTROLLER), EPS);
        assertEquals(1.3, BossRules.roleFactor(Role.MARKSMAN), EPS);
        assertEquals(1.3, BossRules.roleFactor(Role.SUMMONER), EPS);
        // nothing else is an input: no attacker count, no player count (the signature says so)
        for (Method m : BossRules.class.getMethods()) {
            if (m.getName().equals("maxHealth")) {
                assertEquals(3, m.getParameterCount(), "maxHealth(baseMax, healthScale, roleFactor) only");
            }
        }
    }

    @Test
    void theHealthScaleClampsAndTheShareIsKeptWhenTheMaximumChanges() {
        assertEquals(200, BossRules.healthScale(50, false, 100), EPS, "the file's scale is at least 200");
        assertEquals(650, BossRules.healthScale(900, true, 100), EPS, "600 at most, +50 leading");
        assertEquals(350 * 0.25, BossRules.healthScale(350, false, 1), EPS, "boss.healthScale at least 25%");
        assertEquals(350 * 3, BossRules.healthScale(350, false, 1000), EPS, "at most 300%");
        assertEquals(50, BossRules.keepShare(25, 100, 200), EPS, "a level: the share is kept");
        assertEquals(200, BossRules.keepShare(500, 100, 200), EPS, "over full reads as full");
        assertEquals(0, BossRules.keepShare(0, 100, 200), EPS);
        assertEquals(200, BossRules.keepShare(10, 0, 200), EPS, "no old maximum: full");
        assertEquals(3, BossRules.maxHealth(0, 300, 1), EPS, "a base of at least 1 point to scale");
    }

    // ------------------------------------------------------------------ the per-hit cap

    @Test
    void thePerHitCapHoldsForEveryAttackerWithTheExcessToPoise() {
        Capped c = BossRules.capHit(30, 165, 12);
        assertEquals(19.8, c.landed(), 1e-9, "12% of 165");
        assertEquals(10.2, c.toPoise(), 1e-9);
        Capped under = BossRules.capHit(7, 165, 12);
        assertEquals(7, under.landed(), EPS, "an iron sword's blow is under the cap");
        assertEquals(0, under.toPoise(), EPS);
        assertEquals(Capped.NONE, BossRules.capHit(0, 165, 12));
        assertEquals(Capped.NONE, BossRules.capHit(-3, 165, 12));
        assertEquals(Capped.NONE, BossRules.capHit(Double.NaN, 165, 12));
        assertEquals(165 * 0.05, BossRules.capHit(100, 165, 1).landed(), 1e-9, "a share of at least 5");
        assertEquals(165 * 0.25, BossRules.capHit(100, 165, 90).landed(), 1e-9, "at most 25");
    }

    @Test
    void damageWithNoAttackerIsNeverCappedAndGivesNoPoise() {
        Landed lava = BossRules.land(60, false, 165, 12, Window.NONE, NONE, WeaponKind.OTHER);
        assertEquals(60, lava.health(), EPS, "lava, fire, the void: not capped");
        assertEquals(0, lava.poise(), EPS);
        Landed blow = BossRules.land(60, true, 165, 12, Window.NONE, NONE, WeaponKind.MACE);
        assertEquals(19.8, blow.health(), 1e-9);
        assertEquals(19.8 * 2.0 + 40.2, blow.poise(), 1e-9, "the mace's factor on what landed, the excess 1:1");
        assertEquals(40.2, blow.excess(), 1e-9);
    }

    // ------------------------------------------------------------------ the hurt cooldown per attacker

    @Test
    void theHurtCooldownIsKeptPerAttacker() {
        HurtLedger<String> l = new HurtLedger<>();
        // two attackers in one tick: both land in full
        assertEquals(6, l.hit("soldier_a", 100, 6).lands(), EPS);
        assertEquals(6, l.hit("soldier_b", 100, 6).lands(), EPS);
        assertEquals(7, l.hit("player", 100, 7).lands(), EPS, "a player's blow in the same tick too");
        // one attacker twice within 10 ticks: only the excess
        Hurt again = l.hit("soldier_a", 105, 9);
        assertEquals(3, again.lands(), EPS);
        assertFalse(again.full());
        assertEquals(0, l.hit("soldier_a", 106, 8).lands(), EPS, "not more than its last amount (9 now)");
        assertEquals(1, l.hit("soldier_a", 107, 10).lands(), EPS);
        assertEquals(10, l.hit("soldier_a", 110, 10).lands(), EPS, "10 ticks after its full hit: in full again");
        // every hit with no attacker shares one record, so lava ticks as in vanilla
        assertEquals(4, l.hit(null, 200, 4).lands(), EPS);
        assertEquals(0, l.hit(null, 205, 4).lands(), EPS);
        assertEquals(4, l.hit(null, 210, 4).lands(), EPS);
        // the record and its window
        assertEquals(15, BossRules.invulnerableTime(new HurtRecord(100, 6), 105));
        assertEquals(0, BossRules.invulnerableTime(new HurtRecord(100, 6), 110));
        assertEquals(0, BossRules.invulnerableTime(null, 110));
        assertEquals(6, BossRules.lastHurt(new HurtRecord(100, 6), 109), EPS);
        assertEquals(0, BossRules.lastHurt(new HurtRecord(100, 6), 110), EPS);
        assertEquals(3, BossRules.hurtCooldown(100, 6, 105, 9), EPS, "15.6's form");
        assertEquals(9, BossRules.hurtCooldown(-1, 0, 105, 9), EPS, "no record");
        // old records are dropped
        assertEquals(1, l.prune(215), "only the no-attacker record of tick 210 is inside its window");
        assertEquals(0, l.prune(220));
        assertEquals(null, l.record("soldier_a", 300));
    }

    @Test
    void sixSoldiersHurtAHeroSixTimesAsFastAsOne() {
        // the absurd case of 6.16: with a shared cooldown six soldiers land about half; per attacker all of it
        HurtLedger<Integer> per = new HurtLedger<>();
        HurtLedger<Integer> shared = new HurtLedger<>();
        double perTotal = 0, sharedTotal = 0;
        for (long t = 0; t < 200; t++) {
            for (int s = 0; s < 6; s++) {
                if ((t + s * 3) % 20 == 0) { // each swings once a second, staggered
                    perTotal += per.hit(s, t, 6).lands();
                    sharedTotal += shared.hit(0, t, 6).lands();
                }
            }
        }
        assertEquals(6 * 10 * 6, perTotal, EPS, "60 blows of 6");
        assertTrue(sharedTotal < perTotal * 0.6, "a shared cooldown would swallow most: " + sharedTotal);
    }

    // ------------------------------------------------------------------ poise and stagger

    @Test
    void poiseByWeaponAndTheParryWindow() {
        assertEquals(1.0, WeaponKind.SWORD.poise, EPS);
        assertEquals(1.6, WeaponKind.AXE.poise, EPS);
        assertEquals(2.0, WeaponKind.MACE.poise, EPS);
        assertEquals(1.2, WeaponKind.TRIDENT.poise, EPS);
        assertEquals(0.6, WeaponKind.ARROW.poise, EPS);
        assertEquals(0.8, WeaponKind.BOLT.poise, EPS);
        assertEquals(1.0, WeaponKind.OTHER.poise, EPS);
        assertEquals(9.6, BossRules.poiseFromHit(6, 0, WeaponKind.AXE), 1e-9);
        assertEquals(6 + 4, BossRules.poiseFromHit(6, 4, WeaponKind.SWORD), 1e-9);
        assertEquals(6, BossRules.poiseFromHit(6, 0, null), 1e-9, "no weapon: 1.0");
        assertEquals(0, BossRules.poiseFromHit(-6, -1, WeaponKind.MACE), EPS);
        // a parry: a heavy move blocked by a shield raised 5-11 ticks ago, whoever holds it
        assertFalse(BossRules.parry(true, 4, MoveClass.HEAVY), "vanilla does not block yet");
        assertTrue(BossRules.parry(true, 5, MoveClass.HEAVY));
        assertTrue(BossRules.parry(true, 11, MoveClass.AREA));
        assertTrue(BossRules.parry(true, 8, MoveClass.ULTIMATE));
        assertFalse(BossRules.parry(true, 12, MoveClass.HEAVY), "raised too long ago: a plain block");
        assertFalse(BossRules.parry(true, 8, MoveClass.LIGHT), "light moves are blocked like any hit");
        assertFalse(BossRules.parry(false, 8, MoveClass.HEAVY));
        assertFalse(BossRules.parry(true, 8, null));
        assertEquals(25, BossRules.PARRY_POISE, EPS);
        assertEquals(40, BossRules.WALL_POISE, EPS);
    }

    @Test
    void fullPoiseStaggersAndTheMaximumGrows() {
        Poise p = new Poise(100);
        assertFalse(p.add(60, 0, false));
        assertTrue(p.add(40, 1, false), "full: a stagger");
        assertTrue(p.staggered(1));
        assertTrue(p.staggered(50));
        assertFalse(p.staggered(51), "2.5 s");
        assertEquals(0, p.value(), EPS, "the bar empties");
        assertEquals(120, p.max(), 1e-9, "the maximum grows 20% for the rest of the fight");
        assertFalse(p.add(500, 20, false), "no poise damage during a stagger: no stun-lock");
        assertEquals(0, p.value(), EPS);
        assertFalse(p.add(119, 60, false));
        assertTrue(p.add(1, 61, false));
        assertEquals(144, p.max(), 1e-9);
        p.enraged(true);
        assertEquals(144 * 1.25, p.max(), 1e-9, "x1.25 while enraged");
        p.reset();
        assertEquals(100, p.max(), 1e-9, "a new fight");
        assertEquals(0, p.staggers());
    }

    @Test
    void aHeroWithPoiseZeroNeverStaggers() {
        Poise p = new Poise(0);
        assertTrue(p.none());
        assertFalse(p.add(1, 0, false), "one point of poise damage staggered a hero with no poise bar");
        assertFalse(p.add(10_000, 1, false), "no blow staggers it, however big (12.10: 0 = never staggers)");
        assertEquals(0, p.staggers());
        assertFalse(p.staggered(2));
        assertEquals(0, p.value(), EPS);
        p.enraged(true);
        assertFalse(p.add(500, 3, false), "nor while enraged");
        assertFalse(new Poise(50).none(), "50 is the smallest bar");
        assertFalse(new Poise(-5).add(100, 0, false), "a negative value is no bar either");
    }

    @Test
    void superArmourHoldsTheBarJustShortAndTheNextHitStaggers() {
        Poise p = new Poise(80);
        assertFalse(p.add(200, 0, true), "a transition: no stagger");
        assertTrue(p.value() < 80 && p.value() > 79.99);
        assertTrue(p.add(0.01, 2, false), "the first hit after it staggers");
    }

    @Test
    void poiseDecaysFifteenASecondAfterThreeSeconds() {
        Poise p = new Poise(100);
        p.add(50, 0, false);
        p.decay(60);
        assertEquals(50, p.value(), 1e-9, "3 s: no decay yet");
        p.decay(80);
        assertEquals(35, p.value(), 1e-9, "one second later: -15");
        p.decay(1000);
        assertEquals(0, p.value(), EPS);
        p.add(30, 1000, false);
        p.decay(1070);
        assertEquals(30 - 10 * 15 / 20.0, p.value(), 1e-9, "decay restarts 3 s after the last poise damage");
    }

    // ------------------------------------------------------------------ damage-taken windows and weak points

    @Test
    void theDamageTakenWindows() {
        assertEquals(1.0, BossRules.takenFactor(Window.NONE, NONE), EPS);
        assertEquals(1.25, BossRules.takenFactor(new Window(true, false, false, false, false, WeaponTier.NONE), NONE),
                EPS, "staggered +25%");
        assertEquals(1.15, BossRules.takenFactor(new Window(false, true, false, false, false, WeaponTier.NONE), NONE),
                EPS, "the punish window");
        assertEquals(1.30, BossRules.takenFactor(new Window(false, true, true, false, false, WeaponTier.NONE), NONE),
                1e-9, "and +15% more from behind");
        assertEquals(1.0, BossRules.takenFactor(new Window(false, false, true, false, false, WeaponTier.NONE), NONE),
                EPS, "behind outside a recovery: as from the front (kit-gaps 8)");
        assertEquals(1.45, BossRules.takenFactor(new Window(false, true, true, false, false, WeaponTier.NONE),
                EnumSet.of(WeakPoint.BACK)), 1e-9, "the back weak point doubles the backstab bonus");
        assertEquals(1.45, BossRules.takenFactor(new Window(false, true, true, false, false, WeaponTier.NONE),
                EnumSet.of(WeakPoint.RECOVERY)), 1e-9, "the recovery weak point doubles the recovery bonus");
        assertEquals(0.5, BossRules.takenFactor(new Window(false, false, false, true, false, WeaponTier.NONE), NONE),
                EPS, "super armour: 50% less, never invulnerable");
        assertEquals(1.2, BossRules.takenFactor(new Window(false, false, false, false, true, WeaponTier.NONE), NONE),
                1e-9, "downed");
        assertEquals(1.15, BossRules.takenFactor(new Window(false, false, false, false, false, WeaponTier.IRON),
                EnumSet.of(WeakPoint.IRON)), 1e-9, "an iron weapon on an iron weakness");
        assertEquals(1.0, BossRules.takenFactor(new Window(false, false, false, false, false, WeaponTier.GOLD),
                EnumSet.of(WeakPoint.IRON)), EPS);
        assertEquals(1.15, BossRules.takenFactor(new Window(false, false, false, false, false, WeaponTier.GOLD),
                EnumSet.of(WeakPoint.GOLD)), 1e-9);
        assertEquals((1 + 0.25 + 0.15 + 0.15) * 0.5, BossRules.takenFactor(new Window(true, true, true, true, false,
                WeaponTier.NONE), NONE), 1e-9, "bonuses add, super armour halves the sum");
    }

    @Test
    void behindIsMoreThan120DegreesFromTheFacing() {
        // yaw 0 faces +Z
        assertFalse(BossRules.behind(0, 0, 1), "in front");
        assertTrue(BossRules.behind(0, 0, -1), "straight behind");
        assertFalse(BossRules.behind(0, 1, 0), "to the side: 90 degrees");
        assertFalse(BossRules.behind(0, Math.sin(Math.toRadians(120)), Math.cos(Math.toRadians(120))),
                "exactly 120 is not behind");
        assertTrue(BossRules.behind(0, Math.sin(Math.toRadians(125)), Math.cos(Math.toRadians(125))));
        assertTrue(BossRules.behind(90, 1, 0), "yaw 90 faces -X, so +X is behind");
        assertFalse(BossRules.behind(0, 0, 0), "on top of it");
    }

    @Test
    void theOtherWeakPoints() {
        assertTrue(BossRules.channelBroken(24.75, 165, 0), "15% of 165 by default");
        assertFalse(BossRules.channelBroken(24.7, 165, 0));
        assertTrue(BossRules.channelBroken(16.5, 165, 10));
        Set<WeakPoint> bell = EnumSet.of(WeakPoint.BELL);
        assertTrue(BossRules.bellStuns(bell, 8, 1000, 0));
        assertFalse(BossRules.bellStuns(bell, 8.1, 1000, 0));
        assertFalse(BossRules.bellStuns(bell, 3, 1000, 500), "once per 30 s");
        assertTrue(BossRules.bellStuns(bell, 3, 1100, 500));
        assertFalse(BossRules.bellStuns(NONE, 3, 1100, 0));
        Set<WeakPoint> light = EnumSet.of(WeakPoint.LIGHT);
        assertTrue(BossRules.lightWeakens(light, 14, 4), "a torch is 14");
        assertFalse(BossRules.lightWeakens(light, 11, 2));
        assertFalse(BossRules.lightWeakens(light, 15, 4.5));
        Set<WeakPoint> crit = EnumSet.of(WeakPoint.CRIT_ABOVE);
        assertTrue(BossRules.critAboveStaggers(crit, true, true, 72.0, 71.6));
        assertFalse(BossRules.critAboveStaggers(crit, true, true, 71.0, 71.6), "from below its eyes");
        assertFalse(BossRules.critAboveStaggers(crit, false, true, 72.0, 71.6));
        assertFalse(BossRules.critAboveStaggers(NONE, true, true, 72.0, 71.6));
    }

    // ------------------------------------------------------------------ the fight, enrage, reset

    @Test
    void theFightEndsTenSecondsAfterTheLastEngagedEnemy() {
        assertTrue(BossRules.engaged(1000, 800, false, 10), "it hurt the hero 10 s ago");
        assertFalse(BossRules.engaged(1000, 700, false, 10), "15 s ago is too long");
        assertTrue(BossRules.engaged(1000, 0, true, 32), "its target, at 32 blocks");
        assertFalse(BossRules.engaged(1000, 990, true, 32.5), "beyond 32 blocks");
        assertFalse(BossRules.engaged(1000, 0, false, 1));
        Fight f = new Fight();
        assertEquals(FightEvent.NONE, f.update(0, false));
        assertEquals(FightEvent.STARTED, f.update(100, true));
        assertEquals(FightEvent.NONE, f.update(300, true));
        assertEquals(10.0, f.seconds(300), EPS);
        assertEquals(FightEvent.NONE, f.update(499, false), "199 ticks after the last");
        assertTrue(f.active());
        assertEquals(FightEvent.ENDED, f.update(500, false));
        assertFalse(f.active());
        assertEquals(0, f.seconds(500), EPS);
        assertFalse(f.mayHeal(1099), "healing starts 30 s after");
        assertTrue(f.mayHeal(1100));
        assertEquals(FightEvent.STARTED, f.update(1200, true), "a new fight");
        assertFalse(f.mayHeal(1200), "no regeneration while fighting");
        assertEquals(41.2 * 0.01 / 20, BossRules.healPerTick(41.2), 1e-12, "1% of the base maximum a second");
    }

    @Test
    void enrageTimingAndTheTwentyFivePercentCeiling() {
        assertEquals(180, BossRules.enrageAfter(0, false), EPS);
        assertEquals(240, BossRules.enrageAfter(0, true), EPS, "leaders 240 (kit-gaps 12)");
        assertEquals(120, BossRules.enrageAfter(30, false), EPS);
        assertEquals(300, BossRules.enrageAfter(999, true), EPS);
        assertEquals(200, BossRules.enrageAfter(200, true), EPS);
        assertFalse(BossRules.enraged(179.9, 180, false));
        assertTrue(BossRules.enraged(180, 180, false));
        assertTrue(BossRules.enraged(5, 180, true), "a hard trigger, by a player or an NPC alike");
        assertEquals(15, BossRules.bossDamageBonus(List.of(15.0)), EPS);
        assertEquals(25, BossRules.bossDamageBonus(List.of(15.0, 10.0, 20.0)), EPS, "never more than +25%");
        assertEquals(15, BossRules.bossDamageBonus(List.of(15.0, 10.0, 20.0, -10.0)), EPS, "a douse counts on top");
        assertEquals(0, BossRules.bossDamageBonus(List.of()), EPS);
        assertEquals(0.7, BossRules.ENRAGE_GAP_FACTOR, EPS);
        assertEquals(1.25, BossRules.ENRAGE_POISE_FACTOR, EPS);
    }

    // ------------------------------------------------------------------ phases

    @Test
    void phasesGoForwardAndReArmTenPercentAbove() {
        List<Integer> starts = List.of(100, 60, 25);
        assertEquals(1, BossRules.phaseFor(1, 1.0, starts));
        assertEquals(1, BossRules.phaseFor(1, 0.61, starts));
        assertEquals(2, BossRules.phaseFor(1, 0.60, starts), "a phase begins at its share");
        assertEquals(3, BossRules.phaseFor(1, 0.20, starts), "two thresholds at once: the deeper phase");
        assertEquals(3, BossRules.phaseFor(3, 0.90, starts), "never back in a fight");
        assertEquals(1, BossRules.phaseFor(1, 0.10, List.of(100)), "one phase");
        assertEquals(3, BossRules.rearm(3, 0.34, starts));
        assertEquals(2, BossRules.rearm(3, 0.35, starts), "25 + 10");
        assertEquals(2, BossRules.rearm(3, 0.69, starts));
        assertEquals(1, BossRules.rearm(3, 0.70, starts), "60 + 10 as well");
        assertEquals(1, BossRules.rearm(1, 1.0, starts));
        assertEquals(1.0, BossRules.transitionSeconds(0), EPS);
        assertEquals(0.8, BossRules.transitionSeconds(0.1), EPS);
        assertEquals(2.0, BossRules.transitionSeconds(5), EPS);
        assertTrue(BossRules.rebirths(true, true, false));
        assertFalse(BossRules.rebirths(true, true, true), "once per fight");
        assertFalse(BossRules.rebirths(true, false, false));
        assertFalse(BossRules.rebirths(false, true, false));
        assertEquals(4, BossRules.pips(3, true), "the rebirth shows as a pip from the start");
        assertEquals(2, BossRules.pips(2, false));
    }

    @Test
    void theIntroPlaysOnceADayAgainstPeople() {
        assertTrue(BossRules.introDue(-1, 0, true));
        assertFalse(BossRules.introDue(0, 23_999, true), "the same day");
        assertTrue(BossRules.introDue(0, 24_000, true), "the next day");
        assertFalse(BossRules.introDue(-1, 0, false), "it does not announce itself to a zombie");
        assertEquals(-1, BossRules.day(-1));
    }

    // ------------------------------------------------------------------ knockback and control

    @Test
    void hazardKnockbackAndStunsThatCannotChain() {
        assertEquals(2, BossRules.safeKnockback(2, false), EPS);
        assertEquals(0.5, BossRules.safeKnockback(2, true), EPS, "lava or a drop ahead: cut to 0.5");
        assertEquals(0.3, BossRules.safeKnockback(0.3, true), EPS);
        // a 2 s stun at tick 0: immune until 6 s, whoever stuns next
        long immune = BossRules.stunImmuneUntil(0, 2);
        assertEquals(120, immune);
        assertFalse(BossRules.mayStun(60, immune), "a second hero's stun inside the window does nothing");
        assertTrue(BossRules.mayStun(120, immune));
        // several heroes trying every tick: at most 2 s stopped in any 6 s
        long until = Long.MIN_VALUE, stunnedEnd = Long.MIN_VALUE;
        int[] stopped = new int[2000];
        for (int t = 0; t < 2000; t++) {
            if (BossRules.mayStun(t, until)) {
                until = BossRules.stunImmuneUntil(t, 2);
                stunnedEnd = t + 40;
            }
            stopped[t] = t < stunnedEnd ? 1 : 0;
        }
        for (int s = 0; s + 120 <= 2000; s++) {
            int n = 0;
            for (int t = s; t < s + 120; t++) {
                n += stopped[t];
            }
            assertTrue(n <= 40, "window at " + s + ": " + n);
        }
        assertEquals(180, BossRules.stunImmuneUntil(0, 3), "3 s in 9 s for an ultimate's");
    }

    // ------------------------------------------------------------------ air poise

    @Test
    void airPoiseDownsAFlyer() {
        BossRules.AirPoise a = new BossRules.AirPoise();
        assertFalse(a.hit(60, 0));
        a.decay(40);
        assertEquals(60, a.value(), EPS, "2 s: no decay yet");
        a.decay(60);
        assertEquals(50, a.value(), 1e-9, "10 a second");
        assertFalse(a.hit(40, 60));
        assertTrue(a.hit(10, 61), "full: downed");
        assertTrue(a.downed(61));
        assertTrue(a.downed(100));
        assertFalse(a.downed(101), "lies 2 s");
        assertFalse(a.mayTakeOff(300));
        assertTrue(a.mayTakeOff(301), "no take-off for 12 s");
        assertTrue(a.instant(500), "a stun, a freeze, Slowness II, lightning");
        assertTrue(a.downed(500));
    }

    // ------------------------------------------------------------------ the fight's ceiling and reach clock

    @Test
    void theFightCeilingCountsFromTheHighestEngagedEnemy() {
        assertEquals(70, BossRules.fightCeiling(64, List.of()), EPS);
        assertEquals(70, BossRules.fightCeiling(64, List.of(64.0, 63.0)), EPS);
        assertEquals(78, BossRules.fightCeiling(64, List.of(64.0, 72.0)), EPS, "an archer on a pillar is in reach");
        assertFalse(BossRules.aboveReach(67, List.of(64.0)), "3 blocks above: in reach");
        assertTrue(BossRules.aboveReach(67.5, List.of(64.0)));
        assertFalse(BossRules.aboveReach(67.5, List.of(64.0, 66.0)), "one enemy that can reach is enough");
        assertFalse(BossRules.aboveReach(80, List.of()), "nobody engaged: nothing to be out of reach of");
    }

    @Test
    void theReachClockBringsTheHeroBackAfterFourSeconds() {
        BossRules.ReachClock c = new BossRules.ReachClock();
        for (int t = 0; t < 79; t++) {
            assertFalse(c.tick(true, false, false));
        }
        assertTrue(c.tick(true, false, false), "4 s out of reach: come back");
        c.reset();
        for (int t = 0; t < 70; t++) {
            c.tick(true, false, false);
        }
        for (int t = 0; t < 100; t++) {
            assertFalse(c.tick(true, true, false), "a telegraphed wind-up holds the clock");
        }
        assertEquals(70, c.ticks());
        assertFalse(c.tick(false, false, false), "back in reach: from 0");
        assertEquals(0, c.ticks());
        for (int t = 0; t < 500; t++) {
            assertFalse(c.tick(true, false, true), "never while escaping");
        }
    }

    // ------------------------------------------------------------------ SLICE_PLAN criterion 15

    /** Vanilla armour points of the items the slice fixtures wear. */
    private static final Map<String, Integer> ARMOUR = Map.ofEntries(Map.entry("leather_helmet", 1),
            Map.entry("leather_chestplate", 3), Map.entry("leather_leggings", 2), Map.entry("leather_boots", 1),
            Map.entry("chainmail_helmet", 2), Map.entry("chainmail_chestplate", 5),
            Map.entry("chainmail_leggings", 4), Map.entry("chainmail_boots", 1), Map.entry("iron_helmet", 2),
            Map.entry("iron_chestplate", 6), Map.entry("iron_leggings", 5), Map.entry("iron_boots", 2),
            Map.entry("golden_helmet", 2), Map.entry("golden_chestplate", 5), Map.entry("golden_leggings", 3),
            Map.entry("golden_boots", 1), Map.entry("turtle_helmet", 2));

    /** Vanilla 1.21.1 {@code CombatRules.getDamageAfterAbsorb} with no toughness. */
    private static double afterArmour(double damage, double armour) {
        double g = Math.max(armour * 0.2, Math.min(20, armour - damage / 2));
        return damage * (1 - g / 25);
    }

    @Test
    void referenceDuel() throws IOException {
        // SLICE_PLAN criterion 15: each slice hero at level 10 against a reference player (iron sword 6, 1.6
        // swings a second, half landing, vanilla armour, an iron sword on an iron weakness): 20-60 s of landed
        // hitting, as a leader and not; the table's values within a second; it also prints the time one iron-sword
        // soldier needs and the war power of 8.1.
        Object[][] want = {
                {"natsuho_komezaki", 129, 147, 27, 31}, {"yugen_kagaribi", 130, 148, 24, 27},
                {"benika_akagane", 144, 165, 31, 36}, {"hinae_shirasuzu", 130, 148, 27, 31},
                {"kagenori_tsurumine", 129, 147, 34, 39}, {"tobimaru_usugumo", 130, 148, 27, 31},
                {"ilsabet_frostmere", 144, 165, 31, 36}, {"ambrose_nettlewick", 130, 148, 27, 31}};
        List<String> report = new ArrayList<>();
        for (Object[] w : want) {
            HeroDef d = SliceKitsTest.load((String) w[0]);
            HeroRules.LevelStats st = HeroRules.statsAt(d.stats(), 10);
            double armour = st.armor();
            for (HeroDef.GearItem g : d.gear()) {
                armour += ARMOUR.getOrDefault(g.item(), 0);
            }
            boolean ironWeak = d.v2().boss().weakPoints().stream().anyMatch(s -> s.point() == WeakPoint.IRON);
            Set<WeakPoint> weak = ironWeak ? EnumSet.of(WeakPoint.IRON) : NONE;
            double perHit = afterArmour(6, armour) * BossRules.takenFactor(new Window(false, false, false, false,
                    false, WeaponTier.IRON), weak);
            double player = perHit * 1.6 * 0.5;
            // an NPC soldier: one swing a second, six in ten jump criticals (x1.5), half landing
            double soldier = (0.4 * afterArmour(6, armour) + 0.6 * afterArmour(9, armour))
                    * BossRules.takenFactor(new Window(false, false, false, false, false, WeaponTier.IRON), weak) * 0.5;
            for (int lead = 0; lead < 2; lead++) {
                double h = BossRules.maxHealth(st.health(), BossRules.healthScale(d.v2().boss().healthScale(),
                        lead == 1, 100), BossRules.roleFactor(d.behaviour().role()));
                double seconds = h / player;
                assertEquals((int) w[1 + lead], h, 1.0, w[0] + (lead == 1 ? " leading" : "") + " health");
                assertEquals((int) w[3 + lead], seconds, 1.0, w[0] + (lead == 1 ? " leading" : "") + " seconds");
                assertTrue(seconds >= 20 && seconds <= 60, w[0] + ": " + seconds);
                double slower = (h / soldier) / seconds;
                assertTrue(slower > 1.1 && slower < 1.4, "a soldier needs about a fifth longer: " + slower);
                double power = HeroRules.heroPower(h, 10, 100);
                if (w[0].equals("benika_akagane") && lead == 1) {
                    assertEquals(5.3, HeroRules.heroSoldiers(h, 10), 0.05, "8.1: about 5.3 iron soldiers");
                    assertEquals(11.8, power, 0.05, "8.1: power 11.8");
                }
                report.add(String.format(Locale.ROOT, "%s%s: %.0f health, %.1f s for the reference player, %.1f s"
                        + " for an iron-sword soldier, war power %.1f (%.1f iron soldiers)", w[0],
                        lead == 1 ? " (leader)" : "", h, seconds, h / soldier, power, HeroRules.heroSoldiers(h, 10)));
            }
        }
        report.forEach(System.out::println);
    }

    // ------------------------------------------------------------------ equal treatment

    @Test
    void equalForPlayerAndNpc() {
        // the API has no input that says whether anyone is a player or an NPC, and no token, yield or spare
        List<Class<?>> classes = List.of(BossRules.class, BossRules.HurtLedger.class, BossRules.Poise.class,
                BossRules.Fight.class, BossRules.AirPoise.class, BossRules.ReachClock.class, Window.class,
                Landed.class, Capped.class, Hurt.class, HurtRecord.class, MoveSelector.class, Sense.class,
                Sense.TargetState.class, Conditions.class, Decals.class, EscapeRules.class,
                EscapeRules.Context.class);
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                checkName(c, m.getName());
                for (Parameter p : m.getParameters()) {
                    checkName(c, p.getName());
                    checkName(c, p.getType().getSimpleName());
                }
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                checkName(c, f.getName());
            }
            if (c.isRecord()) {
                for (RecordComponent rc : c.getRecordComponents()) {
                    checkName(c, rc.getName());
                }
            }
        }
        // the same hit from "a player" and from "an NPC soldier" (two attackers with the same numbers) lands the same
        HurtLedger<String> l = new HurtLedger<>();
        Hurt a = l.hit("a mock player", 10, 30);
        Hurt b = l.hit("an NPC soldier", 10, 30);
        assertEquals(a.lands(), b.lands(), EPS);
        Landed la = BossRules.land(a.lands(), true, 165, 12, Window.NONE, NONE, WeaponKind.SWORD);
        Landed lb = BossRules.land(b.lands(), true, 165, 12, Window.NONE, NONE, WeaponKind.SWORD);
        assertEquals(la, lb);
        assertEquals(19.8, la.health(), 1e-9, "capped for both");
    }

    // ------------------------------------------------------------------ task A6a: engaged enemies, phases, healing

    @Test
    void engagedEnemiesAreCountedFromBlowsAndTheTarget() {
        BossRules.Engagement e = new BossRules.Engagement();
        java.util.function.IntPredicate all = id -> true;
        assertEquals(0, e.count(0, -1, all), "nobody: no fight");
        assertEquals(1, e.count(0, 7, all), "its target alone is engaged");
        e.hurtBy(3, 100);
        e.hurtBy(4, 110);
        assertEquals(3, e.count(120, 7, all), "two attackers and the target");
        assertEquals(2, e.count(120, 3, all), "the target that also struck counts once");
        assertEquals(2, e.count(120, 7, id -> id != 4), "an attacker that left (dead, a pact, 32 blocks) is not engaged");
        // 15 s after its last blow an attacker is forgotten
        assertEquals(2, e.count(100 + BossRules.ENGAGE_MEMORY_TICKS - 1, -1, all), "both still inside 15 s");
        assertEquals(1, e.count(100 + BossRules.ENGAGE_MEMORY_TICKS, -1, all), "the first is forgotten at 15 s");
        assertEquals(1, e.remembered());
        e.hurtBy(4, 400);
        assertEquals(1, e.remembered(), "a new blow refreshes, it does not add");
        assertEquals(1, e.count(400 + BossRules.ENGAGE_MEMORY_TICKS - 1, -1, all));
        // the memory is bounded: the oldest blow goes first
        BossRules.Engagement f = new BossRules.Engagement();
        for (int i = 0; i < BossRules.Engagement.MAX_REMEMBERED + 5; i++) {
            f.hurtBy(1000 + i, i);
        }
        assertEquals(BossRules.Engagement.MAX_REMEMBERED, f.remembered());
        assertEquals(BossRules.Engagement.MAX_REMEMBERED, f.count(50, -1, all));
        assertEquals(0, f.count(50, -1, id -> id < 1005), "the five oldest were forgotten");
    }

    @Test
    void theLedgerKeepsWhatVanillaMadeOfEachAttackersHit() {
        // the game sets each attacker's own record on the entity before vanilla's check and stores the outcome after
        HurtLedger<Integer> l = new HurtLedger<>();
        assertEquals(0, BossRules.invulnerableTime(l.record(1, 100), 100), "a first blow: no cooldown");
        l.remember(1, new HurtRecord(100, 6));
        assertEquals(20, BossRules.invulnerableTime(l.record(1, 100), 100), "the same tick: vanilla's full 20");
        assertEquals(6, BossRules.lastHurt(l.record(1, 105), 105), 1e-9);
        assertEquals(15, BossRules.invulnerableTime(l.record(1, 105), 105));
        assertEquals(0, BossRules.invulnerableTime(l.record(2, 105), 105), "another attacker has its own, empty");
        l.remember(1, new HurtRecord(100, 9)); // an excess raised the amount, the window keeps its start
        assertEquals(9, BossRules.lastHurt(l.record(1, 109), 109), 1e-9);
        assertEquals(null, l.record(1, 110), "after 10 ticks a full blow again");
        l.remember(null, new HurtRecord(200, 4));
        assertEquals(4, BossRules.lastHurt(l.record(null, 201), 201), 1e-9, "every hit with no attacker shares one record");
        assertEquals(0, l.prune(300), "old records are dropped");
    }

    @Test
    void theFightRunsOnEngagedEnemiesOfAnyKind() {
        BossRules.Engagement e = new BossRules.Engagement();
        Fight f = new Fight();
        e.hurtBy(42, 0);
        assertEquals(FightEvent.STARTED, f.update(0, e.count(0, -1, id -> true) > 0), "a blow alone starts a fight");
        long last = BossRules.ENGAGE_MEMORY_TICKS - 1;
        for (long t = 1; t <= last; t++) {
            assertEquals(FightEvent.NONE, f.update(t, e.count(t, -1, id -> true) > 0));
        }
        long gone = BossRules.ENGAGE_MEMORY_TICKS;
        assertEquals(0, e.count(gone, -1, id -> true));
        for (long t = gone; t < last + BossRules.FIGHT_END_TICKS; t++) {
            assertEquals(FightEvent.NONE, f.update(t, false), "the fight lasts 10 s after the last engaged enemy");
        }
        assertEquals(FightEvent.ENDED, f.update(last + BossRules.FIGHT_END_TICKS, false));
        long ended = last + BossRules.FIGHT_END_TICKS;
        assertEquals(ended, f.endedAt());
        assertFalse(f.mayHeal(ended + BossRules.HEAL_DELAY_TICKS - 1, -1));
        assertTrue(f.mayHeal(ended + BossRules.HEAL_DELAY_TICKS, -1), "30 s after the fight");
        assertFalse(f.mayHeal(ended + BossRules.HEAL_DELAY_TICKS, ended + 100), "hurt (by lava) 25 s ago: not yet");
        assertTrue(f.mayHeal(ended + 100 + BossRules.HEAL_DELAY_TICKS, ended + 100));
        Fight fresh = new Fight();
        assertTrue(fresh.mayHeal(5, -1), "never in a fight and never hurt");
    }

    @Test
    void phasesWaitForTheHeroAndPlayOneTransition() {
        BossRules.Phases p = new BossRules.Phases(List.of(100, 60, 25));
        assertEquals(3, p.count());
        assertFalse(p.health(0.61));
        assertFalse(p.due());
        assertTrue(p.health(0.60), "a phase begins when the share falls to its start");
        assertTrue(p.due());
        assertEquals(1, p.shown(), "the look waits for the transition");
        assertEquals(2, p.begin(1000, 24));
        assertEquals(2, p.shown());
        assertFalse(p.due());
        assertTrue(p.transitioning(1023));
        assertFalse(p.ends(1023));
        assertTrue(p.ends(1024));
        assertFalse(p.ends(1025), "once");
        assertFalse(p.transitioning(1024));
        assertFalse(p.health(0.9), "never back while it lives");
        assertEquals(2, p.reached());
        // one blow through 25% plays one transition, to phase 3
        BossRules.Phases q = new BossRules.Phases(List.of(100, 60, 25));
        assertTrue(q.health(0.2));
        assertEquals(3, q.reached());
        assertEquals(3, q.begin(0, 20));
        assertFalse(q.due());
        // a phase reached during a transition waits for its end
        BossRules.Phases r = new BossRules.Phases(List.of(100, 60, 25));
        r.health(0.5);
        r.begin(0, 30);
        r.health(0.2);
        assertFalse(r.due(), "not while one plays");
        r.ends(30);
        assertTrue(r.due());
        assertEquals(3, r.begin(31, 30));
    }

    @Test
    void phasesRearmTenPercentAboveTheirStart() {
        BossRules.Phases p = new BossRules.Phases(List.of(100, 60, 25));
        p.health(0.2);
        p.begin(0, 20);
        p.ends(20);
        assertFalse(p.rearm(0.34), "under 25 + 10");
        assertTrue(p.rearm(0.35), "35%: back to phase 2");
        assertEquals(2, p.reached());
        assertEquals(2, p.shown());
        assertFalse(p.rearm(0.69));
        assertTrue(p.rearm(0.70));
        assertEquals(1, p.shown());
        assertTrue(p.health(0.6), "the next fight plays the transition again");
        // healing straight to full re-arms both at once
        BossRules.Phases q = new BossRules.Phases(List.of(100, 60, 25));
        q.health(0.1);
        q.begin(0, 20);
        assertTrue(q.rearm(1.0));
        assertEquals(1, q.reached());
        assertFalse(q.transitioning(5), "a re-arm ends a transition");
        BossRules.Phases one = new BossRules.Phases(List.of());
        assertEquals(1, one.count(), "no phases: one");
        assertFalse(one.health(0.01));
        BossRules.Phases loaded = new BossRules.Phases(List.of(100, 60, 25));
        loaded.set(9);
        assertEquals(3, loaded.shown(), "a saved phase is held to the hero's phases");
    }

    private static void checkName(Class<?> c, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String bad : List.of("player", "npc", "token", "yield", "spare", "kneel")) {
            assertFalse(n.contains(bad), c.getSimpleName() + "." + name + " names '" + bad + "'");
        }
    }
}
