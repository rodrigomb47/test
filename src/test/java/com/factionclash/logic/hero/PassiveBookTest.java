package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.PassiveForm;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.PassiveBook.Event;
import com.factionclash.logic.hero.PassiveBook.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PassiveBook} (HERO_FORMAT.md 17; task A4): cooldowns, chance, once per fight, the low-health re-arm, the
 * once-per-ally memory, {@code every}'s period, marks, phases, and the loop guard of v1 17.3 (passive and summon
 * damage never set off {@code on_hit}, {@code on_crit} or {@code on_ally_attack}; a passive's own effects never set it
 * off; at most 8 firings a second with one warning).
 */
class PassiveBookTest {
    static Passive p(Trigger t, double value, int chance, double cooldown, boolean once, String mark) {
        return new Passive("p", "", t, value, mark, chance, cooldown, PassiveForm.SELF, 0, null, null, List.of(), null,
                null, 1, HeroDef.Numbers.NONE, once);
    }

    static Passive p(Trigger t, double cooldown) {
        return p(t, 0, 100, cooldown, false, null);
    }

    @Test
    void aPassiveFiresOnItsTriggerAndWaitsForItsCooldown() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_HIT, 2), p(Trigger.ON_HURT, 1)));
        assertArrayEquals(new int[]{0}, b.withTrigger(Trigger.ON_HIT));
        assertArrayEquals(new int[0], b.withTrigger(Trigger.ON_KILL), "nothing allocated for a trigger it lacks");
        assertEquals(Result.NOT_THIS_TRIGGER, b.tryFire(0, Event.of(Trigger.ON_HURT), 0, 0));
        assertEquals(Result.FIRED, b.tryFire(0, Event.of(Trigger.ON_HIT), 0, 0));
        b.done(0);
        assertEquals(Result.COOLDOWN, b.tryFire(0, Event.of(Trigger.ON_HIT), 39, 0));
        assertEquals(Result.FIRED, b.tryFire(0, Event.of(Trigger.ON_HIT), 40, 0), "2 s later");
    }

    @Test
    void chanceIsRolledEachTime() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_HURT, 0, 25, 1, false, null)));
        assertEquals(Result.CHANCE, b.tryFire(0, Event.of(Trigger.ON_HURT), 0, 25));
        assertEquals(Result.FIRED, b.tryFire(0, Event.of(Trigger.ON_HURT), 0, 24));
    }

    @Test
    void theLoopGuardStopsPassiveAndSummonDamageFromSettingOffHitPassives() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_HIT, 1), p(Trigger.ON_CRIT, 1), p(Trigger.ON_ALLY_ATTACK, 2),
                p(Trigger.ON_HURT, 1)));
        assertEquals(Result.LOOP_GUARD, b.tryFire(0, Event.of(Trigger.ON_HIT).fromPassive(true), 0, 0));
        assertEquals(Result.LOOP_GUARD, b.tryFire(1, Event.of(Trigger.ON_CRIT).fromPassive(true), 0, 0));
        assertEquals(Result.LOOP_GUARD, b.tryFire(2, Event.of(Trigger.ON_ALLY_ATTACK).fromPassive(true), 0, 0));
        assertEquals(Result.FIRED, b.tryFire(3, Event.of(Trigger.ON_HURT).fromPassive(true), 0, 0),
                "on_hurt is not guarded: its cooldown and the rate limit hold it");
        assertEquals(Result.FIRED, b.tryFire(2, Event.of(Trigger.ON_ALLY_ATTACK), 0, 0), "an ally's own blow");
    }

    @Test
    void aPassivesOwnEffectsNeverSetItOffAgain() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_MARK_FULL, 0, 100, 1, false, "hex"),
                p(Trigger.ON_MARK_FULL, 0, 100, 1, false, "hex")));
        Event full = new Event(Trigger.ON_MARK_FULL, "hex", 1, null, 0, false);
        assertEquals(Result.FIRED, b.tryFire(0, full, 0, 0));
        assertTrue(b.firing(0));
        assertEquals(Result.OWN_EFFECTS, b.tryFire(0, full, 100, 0), "while its effects land, even past its cooldown");
        assertEquals(Result.FIRED, b.tryFire(1, full, 0, 0), "another passive still answers");
        b.done(0);
        assertFalse(b.firing(0));
        assertEquals(Result.FIRED, b.tryFire(0, full, 100, 0));
        assertEquals(Result.NOT_THIS_TRIGGER, b.tryFire(0, new Event(Trigger.ON_MARK_FULL, "seal", 1, null, 0, false), 500, 0),
                "only its own mark");
    }

    @Test
    void atMostEightFiringsASecondWithOneWarning() {
        List<Passive> ps = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ps.add(p(Trigger.ON_HURT, 1));
        }
        PassiveBook b = new PassiveBook(ps);
        int fired = 0;
        for (int i = 0; i < 12; i++) {
            if (b.tryFire(i, Event.of(Trigger.ON_HURT), 100, 0) == Result.FIRED) {
                fired++;
            }
            b.done(i);
        }
        assertEquals(PassiveBook.MAX_FIRINGS_PER_SECOND, fired);
        assertEquals(4, b.dropped());
        assertTrue(b.takeWarning());
        assertFalse(b.takeWarning(), "one warning only");
        assertEquals(Result.FIRED, b.tryFire(8, Event.of(Trigger.ON_HURT), 120, 0), "a new second");
        assertFalse(b.takeWarning());
    }

    @Test
    void oncePerFightRearmsWithTheNextFight() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_LOW_HEALTH, 30, 100, 1, true, null)));
        Event low = new Event(Trigger.ON_LOW_HEALTH, null, 0.2, null, 0, false);
        assertEquals(Result.FIRED, b.tryFire(0, low, 0, 0));
        b.done(0);
        b.health(0.9);
        assertEquals(Result.ONCE_PER_FIGHT, b.tryFire(0, low, 1000, 0));
        b.fightStarted(2000);
        assertEquals(Result.FIRED, b.tryFire(0, low, 2000, 0));
    }

    @Test
    void lowHealthFiresUnderItsValueAndRearmsTwentyAbove() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_LOW_HEALTH, 30, 100, 1, false, null)));
        assertEquals(Result.NOT_ARMED, b.tryFire(0, new Event(Trigger.ON_LOW_HEALTH, null, 0.31, null, 0, false), 0, 0));
        assertEquals(Result.FIRED, b.tryFire(0, new Event(Trigger.ON_LOW_HEALTH, null, 0.29, null, 0, false), 0, 0));
        b.done(0);
        assertEquals(Result.NOT_ARMED, b.tryFire(0, new Event(Trigger.ON_LOW_HEALTH, null, 0.10, null, 0, false), 100, 0));
        b.health(0.49);
        assertEquals(Result.NOT_ARMED, b.tryFire(0, new Event(Trigger.ON_LOW_HEALTH, null, 0.10, null, 0, false), 200, 0),
                "49% is not 20 points above 30");
        b.health(0.50);
        assertEquals(Result.FIRED, b.tryFire(0, new Event(Trigger.ON_LOW_HEALTH, null, 0.10, null, 0, false), 300, 0));
    }

    @Test
    void allyLowHealthOncePerAllyPerTwentySeconds() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_ALLY_LOW_HEALTH, 35, 100, 1, false, null)));
        Event a = new Event(Trigger.ON_ALLY_LOW_HEALTH, null, 0.2, "ally-a", 0, false);
        Event other = new Event(Trigger.ON_ALLY_LOW_HEALTH, null, 0.2, "ally-b", 0, false);
        assertEquals(Result.FIRED, b.tryFire(0, a, 0, 0));
        b.done(0);
        assertEquals(Result.ALLY_REPEAT, b.tryFire(0, a, 100, 0));
        assertEquals(Result.FIRED, b.tryFire(0, other, 100, 0), "another ally");
        b.done(0);
        assertEquals(Result.FIRED, b.tryFire(0, a, PassiveBook.ALLY_LOW_HEALTH_REPEAT, 0));
        b.done(0);
        assertEquals(Result.NOT_ARMED, b.tryFire(0, new Event(Trigger.ON_ALLY_LOW_HEALTH, null, 0.5, "c", 0, false), 9000, 0));
    }

    @Test
    void everyFiresOnItsPeriodOnlyWhileFighting() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.EVERY, 6, 100, 1, false, null)));
        b.fightStarted(0);
        assertFalse(b.everyDue(0, 119, true));
        assertTrue(b.everyDue(0, 120, true));
        assertFalse(b.everyDue(0, 200, true));
        assertTrue(b.everyDue(0, 240, true));
        assertFalse(b.everyDue(0, 400, false), "out of a fight");
        assertFalse(b.everyDue(0, 401, true), "a fight starts its count again");
        assertTrue(b.everyDue(0, 521, true));
    }

    @Test
    void phasePassivesFireForTheirPhase() {
        PassiveBook b = new PassiveBook(List.of(p(Trigger.ON_PHASE, 2, 100, 1, false, null)));
        assertEquals(Result.NOT_THIS_TRIGGER, b.tryFire(0, new Event(Trigger.ON_PHASE, null, 1, null, 3, false), 0, 0));
        assertEquals(Result.FIRED, b.tryFire(0, new Event(Trigger.ON_PHASE, null, 1, null, 2, false), 0, 0));
    }

    @Test
    void theSliceKitsPassivesLoadIntoABook() throws Exception {
        HeroDef natsuho = SliceFixtures.load("natsuho_komezaki");
        PassiveBook b = new PassiveBook(natsuho.passives());
        assertEquals(1, b.withTrigger(Trigger.ON_LOW_HEALTH).length, "nine lives");
        assertEquals(1, b.withTrigger(Trigger.ON_CRIT).length, "cat crit");
        int i = b.withTrigger(Trigger.ON_LOW_HEALTH)[0];
        assertTrue(b.get(i).oncePerFight());
        assertEquals(Result.FIRED, b.tryFire(i, new Event(Trigger.ON_LOW_HEALTH, null, 0.25, null, 0, false), 0, 0));
        b.done(i);
        assertEquals(Result.ONCE_PER_FIGHT, b.tryFire(i, new Event(Trigger.ON_LOW_HEALTH, null, 0.25, null, 0, false), 9999, 0));
    }
}
