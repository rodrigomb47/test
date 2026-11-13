package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Common;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.factionclash.logic.hero.V2Files.def;
import static com.factionclash.logic.hero.V2Files.hero2;
import static com.factionclash.logic.hero.V2Files.move;
import static com.factionclash.logic.hero.V2Files.of;
import static com.factionclash.logic.hero.V2Files.one;
import static com.factionclash.logic.hero.V2Files.read;
import static com.factionclash.logic.hero.V2Files.withMove;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HEROES_V2 14.3 {@code HeroBalanceV2Test}: the balance pass of format 2 (6.4, 6.5, 6.6): the class minimums, a stun
 * making a move heavy whatever its form, the lighter-class rule, the decals, the weights and cooldown floors of the
 * new forms, the slot caps by form (combo, rain, barrage per shot, waves), the new effects' weights, the decoy cap, a
 * state's share of the on-hit cap and a zone's delay inside its wind-up.
 */
class HeroBalanceV2Test {
    private static final double EPS = 1e-6;

    private static String dmg(double amount) {
        return "{\"type\": \"damage\", \"amount\": " + amount + "}";
    }

    private static double damage(Effect e) {
        return ((HeroDef.Damage) e).amount().base();
    }

    private static double weight(Move m) {
        return m.ability().numbers().weight();
    }

    // ------------------------------------------------------------------ the class (6.4)

    @Test
    void framesNeverGoUnderTheClassMinimums() {
        assertEquals(0.4, MoveClass.LIGHT.minWindup, EPS);
        assertEquals(0.3, MoveClass.LIGHT.minRecovery, EPS);
        assertEquals(0.8, MoveClass.HEAVY.minWindup, EPS);
        assertEquals(0.8, MoveClass.HEAVY.minRecovery, EPS);
        assertEquals(1.0, MoveClass.AREA.minWindup, EPS);
        assertEquals(0.8, MoveClass.AREA.minRecovery, EPS);
        assertEquals(1.5, MoveClass.ULTIMATE.minWindup, EPS);
        assertEquals(1.5, MoveClass.ULTIMATE.minRecovery, EPS);
        Object[][] forms = {{"melee", MoveClass.LIGHT}, {"dash", MoveClass.HEAVY}, {"nova", MoveClass.AREA}};
        for (Object[] f : forms) {
            MoveClass cls = (MoveClass) f[1];
            Loaded l = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"" + f[0] + "\", \"cooldown\": 60,"
                    + " \"frames\": {\"windup\": 0.1, \"active\": 0.2, \"recovery\": 0.1}, \"effects\": [" + dmg(2) + "]"));
            Move m = move(l.hero("test_hero"), "m1");
            assertEquals(new HeroDef.Frames(cls.minWindup, 0.2, cls.minRecovery), m.frames(), (String) f[0]);
            List<Problem> raised = of(l, Code.FRAMES_RAISED);
            assertEquals(2, raised.size(), l.problems().report());
            assertTrue(raised.get(0).message().startsWith("a wind-up of 0.1 seconds is shorter than "
                    + Problems.article(cls.id()) + " move's"), raised.get(0).message());
            assertTrue(raised.get(1).message().startsWith("a recovery of 0.1 seconds is shorter"), raised.get(1).message());
            for (Problem p : raised) {
                assertEquals(Severity.ADJUSTED, p.severity());
                assertEquals("move 2 \"M\" > frames", p.path(), "reported at the move's frames");
            }
            // no frames: the minimums, silently
            Loaded none = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"" + f[0] + "\", \"cooldown\": 60,"
                    + " \"effects\": [" + dmg(2) + "]"));
            assertTrue(none.problems().all().isEmpty(), none.problems().report());
            assertEquals(new HeroDef.Frames(cls.minWindup, HeroCatalogue.DEFAULT_ACTIVE, cls.minRecovery),
                    move(none.hero("test_hero"), "m1").frames());
        }
        // longer frames are kept as written
        HeroDef d = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"frames\": {\"windup\": 0.95,"
                + " \"active\": 0.3, \"recovery\": 1.2}, \"effects\": [" + dmg(2) + "]"));
        assertEquals(new HeroDef.Frames(0.95, 0.3, 1.2), move(d, "m1").frames());
    }

    @Test
    void aStunMakesAMoveHeavyWhateverItsForm() {
        String stun = "{\"type\": \"stun\", \"seconds\": 1}";
        Object[][] cases = {
                {"\"form\": \"melee\", \"effects\": [" + dmg(2) + ", " + stun + "]", MoveClass.HEAVY},
                {"\"form\": \"shot\", \"effects\": [" + dmg(2) + ", " + stun + "]", MoveClass.HEAVY},
                {"\"form\": \"counter\", \"effects\": [" + stun + "]", MoveClass.HEAVY},
                {"\"form\": \"combo\", \"strikes\": [{\"effects\": [" + dmg(2) + "]}, {\"effects\": [" + stun + "]}]",
                        MoveClass.HEAVY},
                {"\"form\": \"barrage\", \"count\": 3, \"effects\": [" + dmg(2) + "], \"per_shot\": [[], [" + stun
                        + "]]", MoveClass.HEAVY},
                {"\"form\": \"nova\", \"effects\": [" + dmg(2) + ", " + stun + "]", MoveClass.AREA},
                {"\"form\": \"dash\", \"effects\": [" + dmg(2) + ", " + stun + "]", MoveClass.HEAVY},
        };
        for (Object[] c : cases) {
            Loaded l = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"cooldown\": 120, " + c[0]));
            Move m = move(l.hero("test_hero"), "m1");
            assertEquals(c[1], m.cls(), (String) c[0]);
            boolean raised = HeroCatalogue.classOf(m.form()) == MoveClass.LIGHT;
            assertEquals(raised ? 1 : 0, of(l, Code.CLASS_RAISED).size(), c[0] + "\n" + l.problems().report());
            if (raised) {
                assertTrue(one(l, Code.CLASS_RAISED).message().startsWith("a move that stuns an enemy is at least"
                        + " \"heavy\""));
                assertEquals(MoveClass.HEAVY.minWindup, m.frames().windup(), EPS, "a heavy tell");
            }
        }
        // even an authored light class
        Loaded light = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"class\": \"light\","
                + " \"cooldown\": 60, \"effects\": [" + stun + "]"));
        assertEquals(MoveClass.HEAVY, move(light.hero("test_hero"), "m1").cls());
    }

    @Test
    void onlyAMoveThatDoesNotHitHardMayBeLighterThanItsForm() {
        // a dash that only moves the hero and hastes it may be light (a reposition); with damage it may not
        HeroDef hop = def(withMove("\"id\": \"hop\", \"name\": \"Hop\", \"form\": \"dash\", \"class\": \"light\","
                + " \"cooldown\": 60, \"effects\": [{\"type\": \"effect\", \"id\": \"speed\", \"seconds\": 2, \"to\":"
                + " \"self\"}]"));
        assertEquals(MoveClass.LIGHT, move(hop, "hop").cls());
        assertEquals(Decal.NONE, move(hop, "hop").telegraph().shape());
        String[] hard = {dmg(2), "{\"type\": \"dot\", \"amount\": 1, \"seconds\": 3}", "{\"type\": \"knockback\"}",
                "{\"type\": \"pull\"}", "{\"type\": \"freeze\", \"seconds\": 1}"};
        for (String h : hard) {
            Loaded l = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"nova\", \"class\": \"light\","
                    + " \"cooldown\": 120, \"effects\": [" + h + "]"));
            assertEquals(MoveClass.AREA, move(l.hero("test_hero"), "m1").cls(), h);
            assertEquals("class \"light\" is lighter than form \"nova\"'s \"area\", and only a move that neither"
                    + " damages nor stuns, freezes, knocks back or pulls an enemy may be lighter. Read as \"area\"",
                    one(l, Code.CLASS_RAISED).message(), h);
        }
        HeroDef heal = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"nova\", \"class\": \"light\","
                + " \"cooldown\": 60, \"effects\": [{\"type\": \"effect\", \"id\": \"slowness\", \"seconds\": 2}]"));
        assertEquals(MoveClass.LIGHT, move(heal, "m1").cls(), "a slow is not a hard hit");
        // heavier than the form is always allowed
        assertEquals(MoveClass.AREA, move(def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\","
                + " \"class\": \"area\", \"effects\": [" + dmg(2) + "]")), "m1").cls());
    }

    // ------------------------------------------------------------------ decals (6.4, kit-gaps 4)

    @Test
    void theDecalComesFromTheFormAndOnlyLightMovesDrawNone() {
        Object[][] cases = {{"melee", "", Decal.NONE}, {"melee", "\"class\": \"heavy\", ", Decal.DISC},
                {"dash", "", Decal.STRIP}, {"leap", "", Decal.LANDING}, {"cone", "\"range\": 4, ", Decal.FAN},
                {"nova", "", Decal.DISC}, {"zone", "\"zone_at\": \"self\", ", Decal.DISC}, {"line", "", Decal.STRIP},
                {"wave", "", Decal.RING}, {"rain", "", Decal.IMPACTS}, {"sweep", "", Decal.FAN},
                {"grab", "", Decal.STRIP}, {"charge", "", Decal.STRIP}, {"counter", "\"class\": \"heavy\", ",
                Decal.RING}, {"shot", "", Decal.NONE}, {"barrage", "", Decal.NONE}};
        for (Object[] c : cases) {
            HeroDef d = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"" + c[0] + "\", " + c[1]
                    + "\"cooldown\": 120, \"effects\": [" + dmg(2) + "]"));
            assertEquals(c[2], move(d, "m1").telegraph().shape(), c[0] + " " + c[1]);
        }
        Loaded none = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"nova\", \"telegraph\": \"none\","
                + " \"cooldown\": 60, \"effects\": [" + dmg(2) + "]"));
        assertEquals(Decal.DISC, move(none.hero("test_hero"), "m1").telegraph().shape());
        assertEquals("an area move draws its ground mark over its wind-up (\"none\" is for light moves only). Read as"
                + " \"disc\"", one(none, Code.TELEGRAPH_NONE).message());
        // an unblockable move always draws its mark, even a light one
        HeroDef unb = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"unblockable\": true,"
                + " \"cooldown\": 60, \"effects\": [" + dmg(2) + "]"));
        assertEquals(Decal.DISC, move(unb, "m1").telegraph().shape());
        Loaded unbNone = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"unblockable\": true,"
                + " \"telegraph\": \"none\", \"cooldown\": 60, \"effects\": [" + dmg(2) + "]"));
        assertTrue(one(unbNone, Code.TELEGRAPH_NONE).message().startsWith("an unblockable move always draws"));
        // an authored shape and colour are kept
        HeroDef own = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"nova\", \"telegraph\": {\"shape\":"
                + " \"ring\", \"color\": \"#FF0000\"}, \"cooldown\": 60, \"effects\": [" + dmg(2) + "]"));
        assertEquals(new HeroDef.Telegraph(Decal.RING, 0xFF0000), move(own, "m1").telegraph());
    }

    // ------------------------------------------------------------------ weights and floors (6.5)

    @Test
    void theNewFormsWeighAsSixFiveSays() {
        // damage 2 at the level-1 reference is 2: the weights below follow 6.5's column
        HeroDef d = def(hero2("\"moves\": [" + V2Files.LUNGE + ", "
                + "{\"id\": \"charge1\", \"name\": \"C\", \"form\": \"charge\", \"cooldown\": 60, \"effects\": [" + dmg(2)
                + "]}, {\"id\": \"wave1\", \"name\": \"W\", \"form\": \"wave\", \"waves\": 2, \"cooldown\": 60, \"effects\":"
                + " [" + dmg(2) + "]}, {\"id\": \"rain1\", \"name\": \"R\", \"form\": \"rain\", \"count\": 9, \"cooldown\":"
                + " 60, \"effects\": [" + dmg(2) + "]}, {\"id\": \"sweep1\", \"name\": \"S\", \"form\": \"sweep\", \"arc\":"
                + " 120, \"cooldown\": 60, \"effects\": [" + dmg(2) + "]}, {\"id\": \"grab1\", \"name\": \"G\", \"form\":"
                + " \"grab\", \"throw\": 4, \"cooldown\": 60, \"effects\": [" + dmg(2) + "]}, {\"id\": \"counter1\","
                + " \"name\": \"Co\", \"form\": \"counter\", \"cooldown\": 60, \"effects\": [" + dmg(2) + "]},"
                + " {\"id\": \"barrage1\", \"name\": \"B\", \"form\": \"barrage\", \"count\": 4, \"cooldown\": 60,"
                + " \"effects\": [" + dmg(2) + "], \"per_shot\": [[{\"type\": \"knockback\", \"strength\": 2}], []]},"
                + " {\"id\": \"combo1\", \"name\": \"Cb\", \"form\": \"combo\", \"cooldown\": 60, \"strikes\":"
                + " [{\"effects\": [" + dmg(2) + "]}, {\"radius\": 1, \"effects\": [" + dmg(2) + "]}]}]"));
        assertEquals(2 + 2 + 2, weight(move(d, "charge1")), EPS, "effects + 2 (moves the hero) + 2");
        assertEquals(2 * 1.5 * 2, weight(move(d, "wave1")), EPS, "1.5 x waves");
        assertEquals(2 * 1.5 * 9 / 3, weight(move(d, "rain1")), EPS, "1.5 x count / 3");
        assertEquals(2 * 1.5 * 120 / 60, weight(move(d, "sweep1")), EPS, "1.5 x arc / 60");
        assertEquals(2 + 4 / 2.0 + 2, weight(move(d, "grab1")), EPS, "effects + throw / 2 + 2");
        assertEquals(2 * 0.75, weight(move(d, "counter1")), EPS, "x 0.75: it fires only when struck");
        // 4 shots of damage 2, and the knockback on shots 1 and 3 (per_shot cycles)
        assertEquals(2 * 0.75 * 4 + 1 * 0.75 * 2, weight(move(d, "barrage1")), EPS, "0.75 x count");
        assertEquals(2 + 2 * 1.5, weight(move(d, "combo1")), EPS, "the strikes' sum, an area strike x 1.5");
        assertEquals(2 + 2, weight(move(d, "lunge")), EPS, "format 1's forms keep v1 15's weight");
        for (Move m : d.v2().moves()) {
            assertEquals(HeroBalance.cooldownFloor(weight(m), 1), m.ability().numbers().floor(), EPS, m.id());
        }
    }

    @Test
    void aMoveUnderItsCooldownFloorIsRaised() {
        Loaded l = read(withMove("\"id\": \"charge1\", \"name\": \"C\", \"form\": \"charge\", \"cooldown\": 3,"
                + " \"effects\": [" + dmg(2) + "]"));
        Move m = move(l.hero("test_hero"), "charge1");
        assertEquals(5, m.ability().cooldown(), EPS, "2 + 6 / 2");
        Problem p = one(l, Code.COOLDOWN_FLOOR);
        assertEquals("3 seconds is too short for a move this strong (weight 6). Raised to 5 seconds, the shortest allowed"
                + " for it",
                p.message());
        assertEquals(Severity.ADJUSTED, p.severity());
        // two charges: x 1.5
        HeroDef two = def(withMove("\"id\": \"charge1\", \"name\": \"C\", \"form\": \"charge\", \"cooldown\": 3,"
                + " \"charges\": 2, \"effects\": [" + dmg(2) + "]"));
        assertEquals(7.5, move(two, "charge1").ability().cooldown(), EPS);
        // an ultimate has no floor, only its gap
        HeroDef u = def(hero2("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"line\": \"Now!\","
                + " \"effects\": [" + dmg(10) + "]}"));
        assertEquals(20, u.v2().ultimate().ability().numbers().minGap(), EPS, "clamp(15, 20, 60)");
        assertEquals(15, weight(u.v2().ultimate()), EPS);
    }

    @Test
    void anEmpowerWeighsTheStateItEnters() {
        HeroDef d = def(hero2("\"states\": [{\"name\": \"frenzy\", \"stats\": [{\"stat\": \"attack_speed\", \"percent\":"
                + " 25}, {\"stat\": \"speed\", \"percent\": 20}]}], \"moves\": [" + V2Files.LUNGE + ", {\"id\": \"e1\","
                + " \"name\": \"E\", \"form\": \"self\", \"cooldown\": 60, \"effects\": [{\"type\": \"empower\","
                + " \"seconds\": 8, \"state\": \"frenzy\"}]}]"));
        assertEquals(8 / 4.0 * (1 + 45 / 20.0), weight(move(d, "e1")), EPS);
    }

    // ------------------------------------------------------------------ slot caps by form (6.5)

    @Test
    void aCombosStrikesTogetherStayWithinOneAndAQuarterTimesTheCap() {
        assertEquals(1.25, HeroCatalogue.COMBO_CAP_FACTOR, EPS);
        // 8 + 8 = 16: within 20
        HeroDef ok = def(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"combo\", \"cooldown\": 120, \"strikes\":"
                + " [{\"effects\": [" + dmg(8) + "]}, {\"effects\": [" + dmg(10) + "]}]"));
        assertEquals(10, damage(move(ok, "c1").strikes().get(1).effects().get(0)), EPS);
        // 8 + 8 + 8 = 24 over 20: every strike scaled by 20 / 24
        Loaded l = read(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"combo\", \"cooldown\": 120, \"strikes\":"
                + " [{\"effects\": [" + dmg(8) + "]}, {\"effects\": [" + dmg(8) + "]}, {\"effects\": [" + dmg(8) + "]}]"));
        Move m = move(l.hero("test_hero"), "c1");
        for (HeroDef.Strike s : m.strikes()) {
            assertEquals(8 * 20 / 24.0, damage(s.effects().get(0)), 1e-3);
        }
        Problem p = one(l, Code.SLOT_CAP);
        assertTrue(p.message().startsWith("a combo (its strikes together) deals up to 24 health points at level"
                + " 1; the cap is 20 (1.25 x the skill cap of 16)"),
                p.message());
        // a single move is held to the plain cap
        Loaded melee = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"cooldown\": 120,"
                + " \"effects\": [" + dmg(8) + ", " + dmg(10) + "]"));
        double sum = 0;
        for (Effect e : move(melee.hero("test_hero"), "m1").ability().effects()) {
            sum += damage(e);
        }
        assertEquals(16, sum, 1e-3);
    }

    @Test
    void aRainCountsAThirdOfItsImpactsOnOneReceiverWithinTheZoneCap() {
        // 9 impacts of 10: a receiver takes 3, 30, over 16 x 1.5 = 24
        Loaded l = read(withMove("\"id\": \"r1\", \"name\": \"R\", \"form\": \"rain\", \"count\": 9, \"cooldown\": 120,"
                + " \"effects\": [" + dmg(10) + "]"));
        assertEquals(8, damage(move(l.hero("test_hero"), "r1").ability().effects().get(0)), 1e-3);
        assertTrue(one(l, Code.SLOT_CAP).message().contains("a rain (a third of its 9 impacts, on one receiver)"));
        // 6 impacts of 10: 20, within 24
        HeroDef six = def(withMove("\"id\": \"r1\", \"name\": \"R\", \"form\": \"rain\", \"count\": 6, \"cooldown\":"
                + " 120, \"effects\": [" + dmg(10) + "]"));
        assertEquals(10, damage(move(six, "r1").ability().effects().get(0)), EPS);
    }

    @Test
    void everyBarrageShotLandsOnTheOneTarget() {
        assertEquals(2, HeroBalance.shotsOf(0, 3, 4));
        assertEquals(1, HeroBalance.shotsOf(1, 3, 4));
        assertEquals(1, HeroBalance.shotsOf(2, 3, 4));
        assertEquals(0, HeroBalance.shotsOf(3, 4, 3), "more lists than shots");
        // 4 x 3 + 2 x 4 = 20 over 16: everything scaled by 0.8
        Loaded l = read(withMove("\"id\": \"b1\", \"name\": \"B\", \"form\": \"barrage\", \"count\": 4, \"cooldown\":"
                + " 120, \"effects\": [" + dmg(3) + "], \"per_shot\": [[" + dmg(4) + "], [{\"type\": \"knockback\"}]]"));
        Move m = move(l.hero("test_hero"), "b1");
        assertEquals(2.4, damage(m.ability().effects().get(0)), 1e-3);
        assertEquals(3.2, damage(m.perShot().get(0).get(0)), 1e-3);
        assertTrue(one(l, Code.SLOT_CAP).message().startsWith("a barrage (its 4 shots on one target) deals up to 20"
                + " health points"));
        // a volley keeps v1's rule (one target is reached by one arrow)
        HeroDef volley = def(withMove("\"id\": \"v1\", \"name\": \"V\", \"form\": \"volley\", \"count\": 5,"
                + " \"cooldown\": 120, \"effects\": [" + dmg(6) + "]"));
        assertEquals(6, damage(move(volley, "v1").ability().effects().get(0)), EPS);
    }

    @Test
    void aWavesWavesAreItsHits() {
        Loaded l = read(withMove("\"id\": \"w1\", \"name\": \"W\", \"form\": \"wave\", \"waves\": 3, \"cooldown\": 120,"
                + " \"effects\": [" + dmg(6) + "]"));
        assertEquals(16 / 3.0, damage(move(l.hero("test_hero"), "w1").ability().effects().get(0)), 1e-3);
        assertEquals(1, of(l, Code.SLOT_CAP).size());
        HeroDef one = def(withMove("\"id\": \"w1\", \"name\": \"W\", \"form\": \"wave\", \"cooldown\": 120,"
                + " \"effects\": [" + dmg(6) + "]"));
        assertEquals(6, damage(move(one, "w1").ability().effects().get(0)), EPS);
    }

    @Test
    void theOtherNewFormsHoldOneUseToTheCap() {
        for (String form : List.of("charge", "grab", "counter", "sweep")) {
            Loaded l = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"" + form + "\", \"cooldown\": 120,"
                    + " \"effects\": [" + dmg(20) + "]"));
            assertEquals(16, damage(move(l.hero("test_hero"), "m1").ability().effects().get(0)), 1e-3, form);
            assertEquals(1, of(l, Code.SLOT_CAP).size(), form);
        }
        Loaded ult = read(hero2("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"charge\", \"line\":"
                + " \"Now!\", \"effects\": [" + dmg(40) + "]}"));
        assertEquals(30, damage(ult.hero("test_hero").v2().ultimate().ability().effects().get(0)), 1e-3, "the"
                + " ultimate's cap");
    }

    // ------------------------------------------------------------------ the new effects (6.6)

    @Test
    void theNewEffectsWeights() {
        HeroBalance.Ref r = new HeroBalance.Ref(10, 100, 0, Map.of());
        Common t = new Common(To.TARGETS, 8, 100, null), self = new Common(To.SELF, 8, 100, null);
        assertEquals(1.5 * 1.5, HeroBalance.effectWeight(new HeroDef.GuardBreak(t, 3), 1.5, 1, r, null), EPS,
                "seconds / 2 x A");
        assertEquals(2, HeroBalance.effectWeight(new HeroDef.Poise(t, 20), 1, 1, r, null), EPS, "amount / 10");
        assertEquals(4, HeroBalance.effectWeight(new HeroDef.Silence(t, 2), 1, 1, r, null), EPS, "seconds x 2");
        assertEquals(2.5, HeroBalance.effectWeight(new HeroDef.Tether(t, 5, 8), 1, 1, r, null), EPS, "seconds / 2");
        assertEquals(14, HeroBalance.effectWeight(new HeroDef.Barrier(self, 7, 8, null), 1.5, 3, r, null), EPS,
                "hits x 2, never times the form or the hits");
        assertEquals(2, HeroBalance.effectWeight(new HeroDef.SpawnAdd(self, HeroDef.AddKind.DECOY, 2), 1.5, 2, r,
                null), EPS, "count");
        assertEquals(3, HeroBalance.effectWeight(new HeroDef.CallGuards(self, 2), 1, 1, r, null), EPS, "count x 1.5");
        // chance and a condition apply as in v1 15
        Common half = new Common(To.TARGETS, 8, 50, null);
        assertEquals(2, HeroBalance.effectWeight(new HeroDef.Silence(half, 2), 1, 1, r, null), EPS);
        Common cond = new Common(To.TARGETS, 8, 100, new HeroDef.Condition(HeroDef.ConditionType.TARGET_BLOCKING, 0,
                null, null, null));
        assertEquals(4 * 0.75, HeroBalance.effectWeight(new HeroDef.Silence(cond, 2), 1, 1, r, null), EPS);
        // an enemy-only effect repeats with the hits
        assertEquals(2 * 3, HeroBalance.effectWeight(new HeroDef.Poise(t, 20), 1, 3, r, null), EPS);
    }

    @Test
    void decoysAreCappedAtThree() {
        Loaded l = read(withMove("\"id\": \"s1\", \"name\": \"S\", \"form\": \"self\", \"cooldown\": 60, \"effects\":"
                + " [{\"type\": \"spawn_add\", \"add\": \"decoy\", \"count\": 5}]"));
        HeroDef.SpawnAdd s = (HeroDef.SpawnAdd) move(l.hero("test_hero"), "s1").ability().effects().get(0);
        assertEquals(3, s.count());
        assertEquals(Severity.ADJUSTED, one(l, Code.SUMMON_COUNT).severity());
        HeroDef crystals = def(withMove("\"id\": \"s1\", \"name\": \"S\", \"form\": \"self\", \"cooldown\": 60,"
                + " \"effects\": [{\"type\": \"spawn_add\", \"add\": \"focus_crystal\", \"count\": 4}]"));
        assertEquals(4, ((HeroDef.SpawnAdd) move(crystals, "s1").ability().effects().get(0)).count(), "only decoys");
    }

    // ------------------------------------------------------------------ states and transitions

    @Test
    void aStateSharesTheOnHitCapWithTheBasicAttack() {
        Loaded l = read(hero2("\"states\": [{\"name\": \"frenzy\", \"on_hit\": [" + dmg(3) + "]}]").replace("\"melee\"}",
                "\"melee\", \"on_hit\": [" + dmg(3) + "]}"));
        HeroDef d = l.hero("test_hero");
        assertEquals(3, damage(d.basicAttack().onHit().get(0)), EPS);
        assertEquals(1, damage(d.v2().state("frenzy").onHit().get(0)), 1e-3, "room 4 - 3 = 1");
        Problem p = one(l, Code.SHARED_ON_HIT);
        assertTrue(p.message().startsWith("in the state \"frenzy\", the basic attack's on_hit and the state's on_hit add"
                + " up to 6 health points of damage"), p.message());
        HeroDef within = def(hero2("\"states\": [{\"name\": \"frenzy\", \"on_hit\": [" + dmg(1) + "]}]")
                .replace("\"melee\"}", "\"melee\", \"on_hit\": [" + dmg(3) + "]}"));
        assertEquals(1, damage(within.v2().state("frenzy").onHit().get(0)), EPS);
        Loaded stat = read(hero2("\"states\": [{\"name\": \"frenzy\", \"stats\": [{\"stat\": \"speed\", \"percent\":"
                + " 45}]}]"));
        assertEquals(30, stat.hero("test_hero").v2().state("frenzy").stats().get(0).percent());
        assertEquals(1, stat.problems().all().size(), stat.problems().report());
        assertEquals(Severity.ADJUSTED, stat.problems().all().get(0).severity(), "a state's stat is within +-30%");
    }

    @Test
    void transitionEffectsAreHeldToThePassiveCap() {
        Loaded l = read(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 50,"
                + " \"states\": [\"rage\"], \"transition\": {\"effects\": [{\"type\": \"heal\", \"amount\": 9}]}}]},"
                + " \"states\": [{\"name\": \"rage\"}]"));
        HeroDef.Phase two = l.hero("test_hero").v2().boss().phases().get(1);
        assertEquals(4, ((HeroDef.Heal) two.transition().effects().get(0)).amount().base(), 1e-3);
        assertEquals(1, of(l, Code.SLOT_CAP).size(), l.problems().report());
    }

    @Test
    void aZoneLandsAsItsWindUpEnds() {
        Loaded l = read(withMove("\"id\": \"z1\", \"name\": \"Z\", \"form\": \"zone\", \"zone_at\": \"target\","
                + " \"range\": 10, \"delay\": 1.4, \"cooldown\": 60, \"frames\": {\"windup\": 1.0}, \"effects\": ["
                + dmg(2) + "]"));
        Move m = move(l.hero("test_hero"), "z1");
        assertEquals(1.4, m.frames().windup(), EPS);
        assertEquals(0, m.ability().delay(), EPS, "the runtime reads one wind-up, never a separate delay");
        assertEquals("a format-2 zone lands as its wind-up ends, so the wind-up covers its delay of 1.4 seconds;"
                + " the wind-up was raised to 1.4 seconds", one(l, Code.FRAMES_RAISED).message());
        Loaded within = read(withMove("\"id\": \"z1\", \"name\": \"Z\", \"form\": \"zone\", \"zone_at\": \"target\","
                + " \"range\": 10, \"delay\": 0.5, \"cooldown\": 60, \"effects\": [" + dmg(2) + "]"));
        assertTrue(within.problems().all().isEmpty(), within.problems().report());
        Move w = move(within.hero("test_hero"), "z1");
        assertEquals(1.0, w.frames().windup(), EPS);
        assertEquals(0, w.ability().delay(), EPS);
    }

    @Test
    void theSliceKitsBalanceAsTheirSheetsSay() throws java.io.IOException {
        // the numbers SLICE_PLAN 3 gives that F2 kept (the cooldowns the fixtures raised are listed in SliceKitsTest)
        HeroDef kagenori = SliceKitsTest.load("kagenori_tsurumine");
        Move thunder = move(kagenori, "thunderclap_draw");
        assertEquals(MoveClass.HEAVY, thunder.cls(), "a stun makes the cleave heavy");
        HeroDef natsuho = SliceKitsTest.load("natsuho_komezaki");
        assertEquals(MoveClass.LIGHT, move(natsuho, "claw_string").cls());
        assertEquals(Decal.RING, move(natsuho, "cat_reflex").telegraph().shape());
        for (String id : SliceKitsTest.IDS) {
            HeroDef d = SliceKitsTest.load(id);
            for (Move m : d.v2().moves()) {
                assertTrue(m.ability().cooldown() >= m.ability().numbers().floor() - 1e-9, id + " " + m.id());
            }
        }
    }
}
