package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.DiveFrom;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroReader.Loaded;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code dive} form in the catalogue and the validator (MOBILITY_AND_WORK 7.3 and 8.3): a heavy move onto a
 * landing decal that weighs as a leap + 1 and lands with at least 1.0 s of recovery; refused for a hero without an
 * airborne trait, {@code from: air} without flight or gliding, an unblockable dive, and a dive that leads into a
 * grab; and the move selector's footing rule for it.
 */
class DiveFormTest {
    private static final String DMG = "{\"type\": \"damage\", \"amount\": 3}";
    private static final String LEAPER = "\"mobility\": {\"primary\": {\"trait\": \"leaper\", \"tier\": 2}}";
    private static final String FLYER = "\"mobility\": {\"primary\": {\"trait\": \"flyer\", \"tier\": 2},"
            + " \"carrier\": {\"kind\": \"broom\", \"part\": \"weapon\"}}";
    private static final String RUNNER = "\"mobility\": {\"primary\": {\"trait\": \"runner\", \"tier\": 2}}";

    private static String dive(String id, String extra) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"form\": \"dive\"" + (extra.isEmpty() ? ""
                : ", " + extra) + ", \"effects\": [" + DMG + "]}";
    }

    private static String hero(String mobility, String... moves) {
        return V2Files.hero2("\"moves\": [" + V2Files.LUNGE + ", " + String.join(", ", moves) + "], " + mobility);
    }

    @Test
    void aDiveIsAHeavyMoveOntoALandingDecalThatWeighsAsALeapPlusOne() {
        HeroDef d = V2Files.def(hero(LEAPER, dive("crane_drop", ""), "{\"id\": \"hop\", \"name\": \"Hop\", \"form\":"
                + " \"leap\", \"range\": 8, \"radius\": 2, \"effects\": [" + DMG + "]}"));
        Move m = V2Files.move(d, "crane_drop");
        assertEquals(Form.DIVE, m.form());
        assertEquals(HeroDef.MoveClass.HEAVY, m.cls());
        assertEquals(HeroDef.Decal.LANDING, m.telegraph().shape(), "the landing decal grows over the wind-up");
        assertEquals(8, m.ability().range(), 1e-9, "range 4-12, 8 by default");
        assertEquals(2, m.ability().radius(), 1e-9, "radius 1-3, 2 by default");
        assertEquals(DiveFrom.APEX, m.extra().diveFrom(), "a leaper dives from its jump's apex");
        assertTrue(m.frames().recovery() >= HeroCatalogue.DIVE_MIN_RECOVERY - 1e-9, "at least 1.0 s on the ground");
        assertTrue(m.frames().windup() >= HeroDef.MoveClass.HEAVY.minWindup - 1e-9);
        Move hop = V2Files.move(d, "hop");
        assertEquals(hop.ability().numbers().weight() + HeroCatalogue.DIVE_EXTRA_WEIGHT,
                m.ability().numbers().weight(), 1e-9, "as leap + 1");
        assertTrue(m.ability().numbers().floor() >= hop.ability().numbers().floor());
        assertTrue(V2Files.read(hero(LEAPER, dive("crane_drop", ""))).problems().all().isEmpty(),
                V2Files.read(hero(LEAPER, dive("crane_drop", ""))).problems().report());
    }

    /** A dive lands on its target as a leap does, so it closes the gap for 6.12's reach NOTE. */
    @Test
    void aDiveIsAGapCloserForTheReachNote() {
        String cut = "{\"id\": \"cut\", \"name\": \"Cut\", \"form\": \"melee\", \"effects\": [" + DMG + "]}";
        HeroReader.Loaded with = V2Files.read(V2Files.hero2("\"moves\": [" + cut + ", " + dive("crane_drop", "")
                + "], " + LEAPER));
        assertTrue(V2Files.of(with, Problems.Code.NO_REACH).isEmpty(), with.problems().report());
        HeroReader.Loaded without = V2Files.read(V2Files.hero2("\"moves\": [" + cut + "], " + LEAPER));
        assertEquals(1, V2Files.of(without, Problems.Code.NO_REACH).size(), "the same hero without its dive");
        assertTrue(V2Files.one(without, Problems.Code.NO_REACH).message().contains("leap, dash, charge or dive"));
    }

    @Test
    void whereADiveStartsFollowsTheHerosMobility() {
        HeroDef fly = V2Files.def(hero(FLYER, dive("broom_dive", "")));
        assertEquals(DiveFrom.AIR, V2Files.move(fly, "broom_dive").extra().diveFrom(), "a flyer dives from the air");
        HeroDef apexFlyer = V2Files.def(hero(FLYER, dive("drop", "\"from\": \"apex\"")));
        assertEquals(DiveFrom.APEX, V2Files.move(apexFlyer, "drop").extra().diveFrom(), "as written");
        HeroDef glider = V2Files.def(hero("\"mobility\": {\"primary\": {\"trait\": \"glider\", \"tier\": 1},"
                + " \"carrier\": {\"kind\": \"cape\"}}", dive("drop", "")));
        assertEquals(DiveFrom.AIR, V2Files.move(glider, "drop").extra().diveFrom());
        // a secondary airborne trait is enough
        HeroDef sec = V2Files.def(hero("\"mobility\": {\"primary\": {\"trait\": \"charger\", \"tier\": 2},"
                + " \"secondary\": {\"trait\": \"leaper\", \"tier\": 1}}", dive("drop", "")));
        assertEquals(DiveFrom.APEX, V2Files.move(sec, "drop").extra().diveFrom());
        // synonyms
        Loaded syn = V2Files.read(hero(LEAPER, "{\"id\": \"drop\", \"name\": \"Drop\", \"form\": \"swoop\", \"from\":"
                + " \"jump\", \"effects\": [" + DMG + "]}"));
        HeroDef sd = syn.hero("test_hero");
        assertEquals(Form.DIVE, V2Files.move(sd, "drop").form());
        assertEquals(DiveFrom.APEX, V2Files.move(sd, "drop").extra().diveFrom());
        assertEquals(2, V2Files.of(syn, Problems.Code.SYNONYM).size(), syn.problems().report());
        // its range is clamped like any form's
        Loaded far = V2Files.read(hero(LEAPER, dive("drop", "\"range\": 30")));
        assertEquals(12, V2Files.move(far.hero("test_hero"), "drop").ability().range(), 1e-9);
        assertFalse(V2Files.of(far, Problems.Code.CLAMPED).isEmpty());
        // "from" means nothing to another form
        Loaded dash = V2Files.read(V2Files.withMove("\"id\": \"rush\", \"name\": \"Rush\", \"form\": \"dash\","
                + " \"from\": \"air\", \"effects\": [" + DMG + "]"));
        assertFalse(V2Files.of(dash, Problems.Code.FIELD_UNUSED).isEmpty(), dash.problems().report());
        // format 1 has no dive
        assertNull(HeroCatalogue.vocab("form").resolve("dive"));
        assertEquals("dive", HeroCatalogue.vocab("form2").resolve("dive"));
    }

    @Test
    void theValidatorRefusesADiveItsHeroCannotMake() {
        // no airborne trait (a runner; a hero with no mobility block gets its archetype's default, a runner here)
        for (String mob : new String[]{RUNNER, "\"_x\": 0"}) {
            Loaded l = V2Files.read(hero(mob, dive("drop", "")));
            assertTrue(V2Files.one(l, Problems.Code.DIVE_RULE).message().contains("airborne mobility trait"));
            assertNull(l.hero("test_hero").v2().move("drop"), "NOT LOADED for that move");
            assertTrue(l.hero("test_hero").v2().move("lunge") != null, "the rest of the hero loads");
        }
        // from the air without flight or gliding
        Loaded air = V2Files.read(hero(LEAPER, dive("drop", "\"from\": \"air\"")));
        assertTrue(V2Files.one(air, Problems.Code.DIVE_RULE).message().contains("needs flight or gliding"));
        assertNull(air.hero("test_hero").v2().move("drop"));
        // never unblockable
        Loaded unb = V2Files.read(hero(FLYER, dive("drop", "\"unblockable\": true")));
        assertTrue(V2Files.one(unb, Problems.Code.DIVE_RULE).message().contains("never unblockable"));
        // never into a grab: a grab in an aerial window would carry its victim through the air
        Loaded grab = V2Files.read(hero(FLYER, dive("drop", "\"followups\": [{\"move\": \"seize\", \"chance\": 50}]"),
                "{\"id\": \"seize\", \"name\": \"Seize\", \"form\": \"grab\", \"effects\": [" + DMG + "]}"));
        assertTrue(V2Files.one(grab, Problems.Code.DIVE_RULE).message().contains("may not lead into a grab"));
        assertNull(grab.hero("test_hero").v2().move("drop"));
        assertTrue(grab.hero("test_hero").v2().move("seize") != null, "the grab stays for the ground");
        // a dive may follow itself in a touch-and-go string
        HeroDef chain = V2Files.def(hero(FLYER, dive("drop", "\"repeatable\": true, \"followups\": [{\"move\":"
                + " \"drop\", \"chance\": 60, \"max\": 2}]")));
        assertEquals(1, V2Files.move(chain, "drop").followups().size());
        // a dive as the ultimate follows the same rules, and its awakened twin goes with it
        Loaded ult = V2Files.read(V2Files.hero2(RUNNER + ", \"ultimate\": {\"id\": \"meteor\", \"name\": \"Meteor\","
                + " \"form\": \"dive\", \"line\": \"Down!\", \"effects\": [" + DMG + "]}"));
        assertEquals(1, V2Files.of(ult, Problems.Code.DIVE_RULE).size(), ult.problems().report());
        assertNull(ult.hero("test_hero").v2().ultimate());
    }

    @Test
    void theSelectorStartsADiveFromTheAirOnlyInTheAirAndFromTheApexOnTheGround() {
        HeroDef.V2 k = V2Files.def(hero(FLYER, dive("air_drop", "\"from\": \"air\""), dive("apex_drop",
                "\"from\": \"apex\""))).v2();
        Move air = k.move("air_drop"), apex = k.move("apex_drop");
        MoveSelector m = new MoveSelector(k, 50, 50, false, 1);
        Sense ground = MoveSelectorTest.at(5);
        assertFalse(m.eligible(air, 0, ground), "from the air: not from the ground");
        assertTrue(m.eligible(apex, 0, ground), "the apex dive starts with its jump");
        Sense up = MoveSelectorTest.at(5);
        up.airborne = true;
        assertTrue(m.eligible(air, 0, up), "a dive is a move that starts in the air (5.1.5)");
        assertTrue(m.eligible(apex, 0, up));
        Sense low = MoveSelectorTest.at(5);
        low.lowCeiling = true;
        assertFalse(m.eligible(apex, 0, low), "no room to jump");
        Sense wet = MoveSelectorTest.at(5);
        wet.inWater = true;
        assertFalse(m.eligible(apex, 0, wet));
        Sense cling = MoveSelectorTest.at(5);
        cling.climbing = true;
        assertFalse(m.eligible(apex, 0, cling), "nothing starts while clinging");
    }
}
