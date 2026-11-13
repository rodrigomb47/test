package com.factionclash.logic.hero;

import com.factionclash.logic.hero.EffectRouting.Receivers;
import com.factionclash.logic.hero.HeroDef.Common;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.MoveSelector.Pick;
import com.factionclash.logic.hero.MoveSelector.Snapshot;
import com.factionclash.logic.hero.Sense.TargetState;
import com.factionclash.logic.hero.UseLedger.Kind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ability core's pure rules (task A3): the runtime slot caps per use and receiver ({@link UseLedger}), who
 * receives each effect ({@link EffectRouting}), which moves the engine runs, and what {@link MoveSelector} keeps across
 * a save (charges, the last ultimate) and does with {@code cooldown_cut}. None of it reads whether anyone is a player.
 */
class AbilityRuntimeTest {
    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------ the slot caps at runtime

    @Test
    void aUseIsHeldToItsSlotCapPerReceiver() {
        UseLedger l = UseLedger.of(Slot.SKILL, 1.0, 100, 1);
        assertEquals(16 * 1.5, l.cap(Kind.DAMAGE), EPS, "the skill cap with the 1.5x gear room of v1 6.2");
        assertEquals(10, l.take(Kind.DAMAGE, "a", 10), EPS);
        assertEquals(10, l.take(Kind.DAMAGE, "a", 10), EPS);
        assertEquals(4, l.take(Kind.DAMAGE, "a", 10), EPS, "the third hit lands only what is left");
        assertEquals(0, l.take(Kind.DAMAGE, "a", 10), EPS);
        assertEquals(10, l.take(Kind.DAMAGE, "b", 10), EPS, "per receiver");
        assertEquals(12, l.left(Kind.HEAL, "a"), EPS, "heal 8 x 1.5");
        assertEquals(0, l.take(Kind.DAMAGE, "c", -3), EPS);
    }

    @Test
    void theCapsGrowWithAbilityPowerAndPowerScaleAndTheForm() {
        UseLedger u = UseLedger.of(Slot.ULTIMATE, 1.2, 150, 1);
        assertEquals(30 * 1.2 * 1.5 * 1.5, u.cap(Kind.DAMAGE), EPS);
        assertEquals(24 * 1.2 * 1.5 * 1.5, u.cap(Kind.DOT), EPS);
        assertEquals(12 * 1.2 * 1.5 * 1.5, u.cap(Kind.SHIELD), EPS);
        UseLedger z = UseLedger.of(Slot.SKILL, 1, 100, HeroCatalogue.ZONE_CAP_FACTOR);
        assertEquals(16 * 1.5 * 1.5, z.cap(Kind.DAMAGE), EPS, "a lasting zone: 1.5 x the slot cap");
        UseLedger h = UseLedger.of(Slot.ON_HIT, 1, 500, 1);
        assertEquals(4 * 2.0 * 1.5, h.cap(Kind.DAMAGE), EPS, "powerScale is held to 25-200");
    }

    @Test
    void formFactors() {
        Move zone = MovePlanTest.move("pool", "zone", "\"duration\": 4, \"interval\": 1");
        Move burst = MovePlanTest.move("burst", "zone", "\"duration\": 0");
        Move combo = MovePlanTest.move("two", "combo", "\"strikes\": [{\"effects\": [{\"type\": \"damage\", \"amount\": 2}]},"
                + " {\"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]");
        Move melee = MovePlanTest.move("cut", "melee", "");
        assertEquals(1.5, UseLedger.formFactor(zone), EPS);
        assertEquals(1, UseLedger.formFactor(burst), EPS);
        assertEquals(1.25, UseLedger.formFactor(combo), EPS);
        assertEquals(1, UseLedger.formFactor(melee), EPS);
    }

    // ------------------------------------------------------------------ who receives what (v1 10.2, 13)

    private static Common to(To t) {
        return new Common(t, 8, 100, null);
    }

    @Test
    void effectsGoWhereTheirToSays() {
        Effect dmg = new HeroDef.Damage(to(To.TARGETS), HeroDef.Amount.of(2), HeroDef.Element.PHYSICAL, 0);
        Effect heal = new HeroDef.Heal(to(To.ALLIES), HeroDef.Amount.of(2));
        Effect healOne = new HeroDef.Heal(to(To.ALLY), HeroDef.Amount.of(2));
        Effect shieldSelf = new HeroDef.Shield(to(To.SELF), HeroDef.Amount.of(2), 5);
        Effect hop = new HeroDef.MoveSelf(to(To.TARGETS), HeroDef.Direction.BACK, 3);
        assertEquals(Receivers.FORM, EffectRouting.receivers(dmg, Form.CONE));
        assertEquals(Receivers.ALLIES_IN_AREA, EffectRouting.receivers(heal, Form.NOVA));
        assertEquals(Receivers.ALLIES_IN_AREA, EffectRouting.receivers(heal, Form.ZONE));
        assertEquals(Receivers.ALLIES_IN_AREA, EffectRouting.receivers(heal, Form.LEAP));
        assertEquals(Receivers.ALLIES_AROUND, EffectRouting.receivers(heal, Form.MELEE));
        assertEquals(Receivers.MOST_HURT_ALLY, EffectRouting.receivers(healOne, Form.LINE));
        assertEquals(Receivers.SELF, EffectRouting.receivers(shieldSelf, Form.CONE));
        assertEquals(Receivers.SELF, EffectRouting.receivers(hop, Form.NOVA), "a self-only effect ignores to");
        assertTrue(EffectRouting.oncePerUse(hop));
        assertTrue(EffectRouting.oncePerUse(shieldSelf));
        assertFalse(EffectRouting.oncePerUse(dmg));
        assertFalse(EffectRouting.oncePerUse(heal));
    }

    @Test
    void recipientClassesHoldForEveryReceiver() {
        Effect dmg = new HeroDef.Damage(to(To.TARGETS), HeroDef.Amount.of(2), HeroDef.Element.PHYSICAL, 0);
        Effect heal = new HeroDef.Heal(to(To.TARGETS), HeroDef.Amount.of(2));
        Effect slow = new HeroDef.PotionEffect(to(To.TARGETS), "slowness", 1, 3, false);
        Effect haste = new HeroDef.PotionEffect(to(To.TARGETS), "speed", 1, 3, true);
        Effect mark = new HeroDef.Mark(to(To.TARGETS), "toll", 1, 5, 10, false);
        Effect cut = new HeroDef.CooldownCut(to(To.TARGETS), 2);
        assertTrue(EffectRouting.mayReach(dmg, true, false));
        assertFalse(EffectRouting.mayReach(dmg, false, false), "never an ally");
        assertFalse(EffectRouting.mayReach(dmg, false, true), "never the hero");
        assertTrue(EffectRouting.mayReach(heal, false, false));
        assertTrue(EffectRouting.mayReach(heal, false, true));
        assertFalse(EffectRouting.mayReach(heal, true, false), "never heals an enemy");
        assertTrue(EffectRouting.mayReach(slow, true, false));
        assertFalse(EffectRouting.mayReach(slow, false, false));
        assertTrue(EffectRouting.mayReach(haste, false, false));
        assertFalse(EffectRouting.mayReach(haste, true, false));
        assertTrue(EffectRouting.mayReach(mark, true, false));
        assertTrue(EffectRouting.mayReach(mark, false, true), "a self mark");
        assertFalse(EffectRouting.mayReach(mark, false, false), "never an ally's mark");
        assertTrue(EffectRouting.mayReach(cut, false, true));
        assertFalse(EffectRouting.mayReach(cut, true, false));
    }

    // ------------------------------------------------------------------ what the engine runs

    @Test
    void theEngineRunsWholeMovesOnly() throws IOException {
        assertTrue(EffectRouting.runs(MovePlanTest.move("cut", "melee", "")));
        assertTrue(EffectRouting.runs(MovePlanTest.move("arc", "chain", "")));
        assertFalse(EffectRouting.runs(MovePlanTest.move("rush", "charge", "")), "a format-2 form A5 adds");
        assertFalse(EffectRouting.runs(MovePlanTest.move("bash", "melee", "\"class\": \"heavy\", \"effects\": "
                + "[{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"stun\", \"seconds\": 1}]")), "a stun is A4's");
        HeroDef natsuho = SliceFixtures.load("natsuho_komezaki");
        List<String> runs = new ArrayList<>();
        for (Move m : natsuho.v2().allMoves()) {
            if (EffectRouting.runs(m)) {
                runs.add(m.id());
            }
        }
        assertEquals(List.of("claw_string", "pounce", "hiss", "scratch_dash"), runs,
                "the counter, the charge and the empower ultimate wait for A4-A5");
    }

    @Test
    void theSelectorNeverPicksAMoveTheEngineCannotRun() {
        HeroDef.V2 kit = MoveSelectorTest.kit(null, MoveSelectorTest.move("rush", "charge", "\"range\": 10"),
                MoveSelectorTest.move("cut", "melee", ""));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 7).runnable(EffectRouting::runs);
        s.fightStarted(0);
        for (long t = 0; t < 2000; t += 5) {
            Pick p = s.check(t, MoveSelectorTest.at(3));
            if (p != null) {
                assertEquals("cut", p.move().id());
            }
        }
        MoveSelector far = new MoveSelector(kit, 50, 50, false, 7).runnable(EffectRouting::runs);
        far.fightStarted(0);
        Pick[] out = new Pick[1];
        assertEquals(-1, MoveSelectorTest.firstPick(far, 0, 400, MoveSelectorTest.at(9), out),
                "the charge would reach, but it is not runnable");
    }

    // ------------------------------------------------------------------ cooldowns across a save, cooldown_cut

    @Test
    void chargesAndTheLastUltimateSurviveASave() {
        HeroDef.V2 kit = MoveSelectorTest.kit(null, MoveSelectorTest.move("cut", "melee", "\"cooldown\": 10"));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 3);
        s.fightStarted(0);
        Pick[] out = new Pick[1];
        long at = MoveSelectorTest.firstPick(s, 0, 200, MoveSelectorTest.at(2), out);
        assertTrue(at > 0);
        Move cut = kit.move("cut");
        long ready = s.readyAt(cut, at + 1);
        assertEquals(at + 200, ready, "10 s");
        Snapshot snap = s.snapshot();
        MoveSelector back = new MoveSelector(kit, 50, 50, false, 3);
        back.restore(snap, at + 50);
        assertEquals(0, back.charges(cut, at + 50, 0), "still cooling down after the load");
        assertEquals(ready, back.readyAt(cut, at + 50));
        assertEquals(1, back.charges(cut, ready, 0));
        MoveSelector fresh = new MoveSelector(kit, 50, 50, false, 3);
        assertEquals(1, fresh.charges(cut, at + 50, 0), "without the snapshot the cooldown would be lost");
    }

    @Test
    void aRestoredChargeNeverWaitsLongerThanTheMovesCooldownNow() {
        HeroDef.V2 kit = MoveSelectorTest.kit(null, MoveSelectorTest.move("cut", "melee", "\"cooldown\": 10"));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 3);
        s.restore(new Snapshot(java.util.Map.of("cut", new MoveSelector.ChargeState(0, 99_999, 0),
                "gone", new MoveSelector.ChargeState(0, 5, 0)), 0), 1000);
        assertEquals(1200, s.readyAt(kit.move("cut"), 1000));
        assertNull(kit.move("gone"));
    }

    @Test
    void cooldownCutNeverHalvesBelowTheFloor() {
        HeroDef.V2 kit = MoveSelectorTest.kit(null, MoveSelectorTest.move("cut", "melee", "\"cooldown\": 10"));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 3);
        s.fightStarted(0);
        Pick[] out = new Pick[1];
        long at = MoveSelectorTest.firstPick(s, 0, 200, MoveSelectorTest.at(2), out);
        Move cut = kit.move("cut");
        s.cutCooldowns(at + 10, 3);
        assertEquals(at + 200 - 60, s.readyAt(cut, at + 10), "3 s sooner");
        s.cutCooldowns(at + 20, 10);
        assertEquals(at + 100, s.readyAt(cut, at + 20), "never under half the 10 s since its last use");
    }

    // ------------------------------------------------------------------ equal treatment

    @Test
    void nothingHereNamesAPlayer() {
        List<Class<?>> classes = List.of(UseLedger.class, EffectRouting.class, MovePlan.class, MovePlan.Plan.class,
                FormGeometry.class, FormGeometry.Impulse.class, StatusBook.class, StatusBook.Entry.class,
                StatusBook.MarkResult.class, StatusBook.DotDue.class, MoveSelector.Snapshot.class,
                MoveSelector.ChargeState.class);
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                check(c, m.getName());
                for (Parameter p : m.getParameters()) {
                    check(c, p.getName());
                    check(c, p.getType().getSimpleName());
                }
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                check(c, f.getName());
            }
            if (c.isRecord()) {
                for (RecordComponent rc : c.getRecordComponents()) {
                    check(c, rc.getName());
                }
            }
        }
    }

    private static void check(Class<?> c, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String bad : List.of("player", "npc", "token", "yield", "spare")) {
            assertFalse(n.contains(bad), c.getSimpleName() + "." + name + " names '" + bad + "'");
        }
    }

    @Test
    void aSenseIsClearedForReuse() {
        Sense s = new Sense().target(new TargetState().distance(3).blocking(true).mark("toll", 2)).health(0.4).energy(70);
        s.enemy(2, 3).ally(4, 0.5, true);
        s.clear();
        assertFalse(s.target.present);
        assertFalse(s.target.blocking);
        assertTrue(s.target.marks.isEmpty());
        assertEquals(1, s.healthShare, EPS);
        assertEquals(0, s.energy);
        assertTrue(s.enemies.isEmpty() && s.allies.isEmpty());
        assertNotNull(s.target);
    }
}
