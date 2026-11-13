package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.TraitTier;
import com.factionclash.logic.hero.MobilityRules.AirBudget;
import com.factionclash.logic.hero.MobilityRules.Attr;
import com.factionclash.logic.hero.MobilityRules.Config;
import com.factionclash.logic.hero.MobilityRules.Count;
import com.factionclash.logic.hero.MobilityRules.Curve;
import com.factionclash.logic.hero.MobilityRules.Feature;
import com.factionclash.logic.hero.MobilityRules.Modifier;
import com.factionclash.logic.hero.MobilityRules.Op;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Rule;
import com.factionclash.logic.hero.MobilityRules.Scaling;
import com.factionclash.logic.hero.MobilityRules.Situation;
import com.factionclash.logic.hero.MobilityRules.Stamina;
import com.factionclash.logic.hero.MobilityRules.Stat;
import com.factionclash.logic.hero.MobilityRules.TakeOffState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 1, 2, 5.1, 7 and 15.9: ceilings, the level power, stamina, the air budget, ceilings, rules. */
class MobilityRulesTest {

    private static TraitTier tt(MobilityTrait t, int tier) {
        return new TraitTier(t, tier);
    }

    private static Profile at(MobilityTrait t, int tier, int level) {
        return MobilityRules.profile(tt(t, tier), null, level);
    }

    private static Profile slice(String id, int level) throws IOException {
        HeroDef d = SliceFixtures.load(id);
        return MobilityRules.profile(d.v2().mobility(), level, Config.DEFAULT);
    }

    // ------------------------------------------------------------------ the power curve (1.1)

    @Test
    void thePowerCurveIsSixtySeventyNineAndAHundred() {
        assertEquals(0.60, MobilityRules.power(1), 1e-9);
        assertEquals(0.6 + 0.4 * 4 / 19, MobilityRules.power(5), 1e-9);
        assertEquals(0.79, MobilityRules.power(10), 0.005);
        assertEquals(0.89, MobilityRules.power(15), 0.005);
        assertEquals(1.0, MobilityRules.power(20), 1e-9);
        assertEquals(1.0, MobilityRules.power(30), 1e-9, "levels 21-30 add nothing");
        assertEquals(0.60, MobilityRules.power(0), 1e-9, "never under level 1");
        for (int l = 2; l <= 20; l++) {
            assertTrue(MobilityRules.power(l) > MobilityRules.power(l - 1), "grows every level");
        }
        // the two config keys, clamped (30-100 and 10-30)
        assertEquals(1.0, MobilityRules.power(1, new Curve(100, 20)), 1e-9, "100: the full ceiling from the start");
        assertEquals(0.3, MobilityRules.power(1, new Curve(5, 20)), 1e-9);
        assertEquals(1.0, MobilityRules.power(10, new Curve(60, 10)), 1e-9);
        assertEquals(10, new Curve(60, 2).fullPowerLevel());
        assertEquals(30, new Curve(60, 99).fullPowerLevel());
        assertEquals(0.6 + 0.4 * 19 / 29, MobilityRules.power(20, new Curve(60, 30)), 1e-9);
    }

    @Test
    void theThreeKindsOfNumberAndTheRoundingOfLinks() {
        assertEquals(1.25 + 0.6 * (3.5 - 1.25), MobilityRules.scale(Scaling.BONUS, 1.25, 3.5, 0.6), 1e-9);
        assertEquals(0.6 * 45, MobilityRules.scale(Scaling.REACH, 0, 45, 0.6), 1e-9);
        assertEquals(25 / 0.6, MobilityRules.scale(Scaling.WAIT, 0, 25, 0.6), 1e-9);
        assertEquals(4, MobilityRules.linkBlocks(6 * 0.79), "a 6-block gap at 79% is 4.7, so 4");
        assertEquals(3, MobilityRules.linkBlocks(3.0));
        assertEquals(3, MobilityRules.linkBlocks(2.9999999), "float noise does not lose a block");
        // every stat's kind is the one 1.1's table gives
        for (Stat s : List.of(Stat.JUMP_HEIGHT, Stat.SAFE_FALL, Stat.HOVER_STEP, Stat.TRAVEL_SPEED, Stat.SPRINT_BOOST,
                Stat.CHARGE_BOOST, Stat.MOVEMENT_EFFICIENCY, Stat.KNOCKBACK, Stat.TRAVEL_HUNGER_CUT, Stat.SWIM_SPEED,
                Stat.WATER_EFFICIENCY, Stat.OXYGEN, Stat.CRUISE_SPEED, Stat.GLIDE_FORWARD, Stat.HOVER_HEIGHT,
                Stat.SNOW_SPEED)) {
            assertEquals(Scaling.BONUS, s.scaling, s.name());
        }
        for (Stat s : List.of(Stat.AIR_JUMP_HEIGHT, Stat.GAP, Stat.CLIMB_HEIGHT, Stat.GLIDE_SECONDS, Stat.STAMINA,
                Stat.CLIMB_RATE, Stat.REGAIN, Stat.HOVER_GAP)) {
            assertEquals(Scaling.REACH, s.scaling, s.name());
        }
        for (Stat s : List.of(Stat.SPRINT_COOLDOWN, Stat.CHARGE_COOLDOWN, Stat.TAKE_OFF, Stat.GLIDE_SINK)) {
            assertEquals(Scaling.WAIT, s.scaling, s.name());
        }
        assertEquals(Set.of(Stat.GAP, Stat.CLIMB_HEIGHT, Stat.HOVER_GAP),
                Set.of(java.util.Arrays.stream(Stat.values()).filter(s -> s.link).toArray()));
        assertEquals(1.25, Stat.JUMP_HEIGHT.worker);
        assertEquals(3, Stat.SAFE_FALL.worker);
        assertEquals(4.3, Stat.CRUISE_SPEED.worker);
        assertEquals(4.3, Stat.GLIDE_FORWARD.worker);
        assertEquals(1.0, Stat.HOVER_STEP.worker, "1.0 itself is a feature");
    }

    @Test
    void theFloorsKeepEveryFeatureUsableAtLevelOne() {
        // a leaper I would jump 1.58 at level 1: the floor makes it 1.8, so fences are jump-up links from level 1
        assertEquals(1.8, at(MobilityTrait.LEAPER, 1, 1).jumpHeight(), 1e-9);
        assertEquals(1.5, at(MobilityTrait.LEAPER, 1, 1).jumpUp(), 1e-9);
        assertEquals(1.8, at(MobilityTrait.LEAPER, 1, 20).jumpHeight(), 1e-9, "its ceiling is 1.8 anyway");
        // a hover I drifts 1.2 at level 1: the floor makes it 2
        assertEquals(2, at(MobilityTrait.HOVER, 1, 1).hoverGap());
        assertEquals(2, at(MobilityTrait.HOVER, 1, 20).hoverGap());
        assertEquals(3, at(MobilityTrait.HOVER, 2, 20).hoverGap());
        // with a lower curve the gap floor holds too
        Config low = new Config(true, true, new Curve(30, 20), 100);
        assertEquals(2, MobilityRules.profile(tt(MobilityTrait.ACROBAT, 1), null, 1, low).gap());
        // every family and ceiling has something usable at level 1
        for (MobilityTrait t : MobilityTrait.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                Profile p = at(t, tier, 1);
                assertTrue(p.any(), t + " " + tier);
                String what = t + " " + tier;
                switch (t) {
                    case LEAPER, ACROBAT -> {
                        assertTrue(p.jumpUp() >= MobilityRules.JUMP_UP_MIN - 1e-9, what + " jumps up a fence");
                        assertTrue(p.gap() >= MobilityRules.GAP_FLOOR, what);
                    }
                    case CLIMBER -> assertTrue(p.climbHeight() >= 3, what + " climbs 3 blocks");
                    case FLYER -> {
                        assertTrue(p.raw(Stat.STAMINA) >= 18 - 1e-9, what + " 18 s of stamina");
                        assertTrue(p.takeOffShare() <= 0.5 + 1e-9, what + " takes off at 50% or less");
                    }
                    case GLIDER -> assertTrue(p.glideRatio() >= 1.18, what + " glides 1.2 per block of drop");
                    case HOVER -> assertTrue(p.hoverGap() >= 2, what);
                    case RUNNER -> assertTrue(p.raw(Stat.TRAVEL_SPEED) > 0, what);
                    case CHARGER -> assertEquals(1.0, p.stepHeight(), 1e-9, what);
                    case SWIMMER -> assertTrue(p.raw(Stat.SWIM_SPEED) > 1, what);
                    case SNOWSTRIDE -> assertTrue(p.has(Feature.NEVER_FREEZES), what);
                    case EMBER_STRIDE -> assertTrue(p.has(Feature.FIREPROOF), what);
                }
            }
        }
        // 1.1's examples: a climber I climbs 3, a flyer I has 18 s and takes off at 50%, an acrobat I jumps up 1.5
        assertEquals(3, at(MobilityTrait.CLIMBER, 1, 1).climbHeight());
        assertEquals(18, at(MobilityTrait.FLYER, 1, 1).raw(Stat.STAMINA), 1e-9);
        assertEquals(0.5, at(MobilityTrait.FLYER, 1, 1).takeOffShare(), 1e-9);
        assertEquals(1.7, at(MobilityTrait.ACROBAT, 1, 1).jumpUp(), 1e-9);
        assertEquals(1.18, at(MobilityTrait.GLIDER, 1, 1).glideRatio(), 0.01);
    }

    @Test
    void everyFeatureOfTheCeilingIsThereAtLevelOneAndNoneAbove() {
        for (MobilityTrait t : MobilityTrait.values()) {
            MobilityRules.Family f = MobilityRules.table(t);
            for (int tier = 1; tier <= 3; tier++) {
                Profile one = at(t, tier, 1), twenty = at(t, tier, 20);
                assertEquals(one.features(), twenty.features(), t + " " + tier + ": no unlock with level");
                for (Feature x : Feature.values()) {
                    assertEquals(f.has(x, tier), one.has(x), t + " " + tier + " " + x);
                }
                for (Count c : Count.values()) {
                    assertEquals(f.count(c, tier), one.count(c), t + " " + tier + " " + c);
                }
            }
        }
        // the catalogue's feature tiers (1.3)
        assertFalse(at(MobilityTrait.RUNNER, 1, 1).has(Feature.STEP_ONE));
        assertTrue(at(MobilityTrait.RUNNER, 2, 1).has(Feature.STEP_ONE));
        assertTrue(at(MobilityTrait.RUNNER, 3, 1).has(Feature.SPRINT_BURST));
        assertFalse(at(MobilityTrait.RUNNER, 2, 1).has(Feature.SPRINT_BURST));
        assertTrue(at(MobilityTrait.CHARGER, 1, 1).has(Feature.STEP_ONE));
        assertFalse(at(MobilityTrait.CHARGER, 1, 1).has(Feature.TRAVERSAL_CHARGE));
        assertTrue(at(MobilityTrait.CHARGER, 2, 1).has(Feature.WEB_IMMUNE));
        assertTrue(at(MobilityTrait.LEAPER, 2, 1).has(Feature.LANDING_ROLL));
        assertFalse(at(MobilityTrait.LEAPER, 1, 1).has(Feature.LANDING_ROLL));
        assertEquals(2, at(MobilityTrait.ACROBAT, 3, 1).count(Count.AIR_JUMPS), "two air jumps at level 1");
        assertTrue(at(MobilityTrait.ACROBAT, 3, 1).has(Feature.NO_FALL_DAMAGE));
        assertTrue(at(MobilityTrait.ACROBAT, 3, 1).has(Feature.MANTLE));
        assertTrue(at(MobilityTrait.ACROBAT, 2, 1).has(Feature.WALL_KICK));
        assertTrue(at(MobilityTrait.ACROBAT, 2, 1).has(Feature.CHIMNEY));
        assertFalse(at(MobilityTrait.ACROBAT, 1, 1).has(Feature.CHIMNEY));
        assertTrue(at(MobilityTrait.CLIMBER, 2, 1).has(Feature.WORK_CLINGING));
        assertEquals(MobilityRules.ANY_HEIGHT, at(MobilityTrait.CLIMBER, 3, 1).climbHeight(), "\"any\" is a feature");
        assertTrue(at(MobilityTrait.CLIMBER, 3, 1).has(Feature.OVERHANG));
        assertEquals(0, at(MobilityTrait.GLIDER, 1, 1).count(Count.GUSTS));
        assertEquals(2, at(MobilityTrait.GLIDER, 3, 1).count(Count.GUSTS));
        assertTrue(at(MobilityTrait.GLIDER, 2, 1).has(Feature.GLIDE_FROM_APEX));
        assertTrue(at(MobilityTrait.SWIMMER, 2, 1).has(Feature.UNDERWATER_MINING));
        assertTrue(at(MobilityTrait.SWIMMER, 3, 1).has(Feature.WATER_BREATHING));
        assertTrue(at(MobilityTrait.EMBER_STRIDE, 2, 1).has(Feature.LAVA_WALKING));
        // the dive from level 1 for every airborne trait, whatever its ceiling (7.3); from the air only for flight
        for (MobilityTrait t : MobilityTrait.values()) {
            assertEquals(t.airborne(), at(t, 1, 1).airborne(), t.name());
            assertEquals(t == MobilityTrait.GLIDER || t == MobilityTrait.FLYER, at(t, 1, 1).has(Feature.DIVE_FROM_AIR));
        }
    }

    @Test
    void theTableOfFifteenNineAtLevelsOneTenAndTwenty() throws IOException {
        // Natsuho (acrobat III + climber I)
        double[][] natsuho = {{1.40, 1.45, 1.50}, {0.75, 0.99, 1.25}, {2.9, 3.4, 4.0}, {3, 4, 6}, {3, 4, 6}};
        int[] levels = {1, 10, 20};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("natsuho_komezaki", levels[i]);
            assertEquals(natsuho[0][i], p.jumpHeight(), 0.005, "jump " + levels[i]);
            assertEquals(natsuho[1][i], p.airJumpHeight(), 0.005, "air jump " + levels[i]);
            assertEquals(natsuho[2][i], p.reachUp(), 0.05, "reach " + levels[i]);
            assertEquals((int) natsuho[3][i], p.gap(), "gap " + levels[i]);
            assertEquals((int) natsuho[4][i], p.climbHeight(), "climb " + levels[i]);
            assertTrue(p.has(Feature.NO_FALL_DAMAGE) && p.has(Feature.CLING) && p.count(Count.AIR_JUMPS) == 2);
        }
        // Yugen (runner III + leaper I)
        double[][] yugen = {{12, 16, 20}, {0.60, 0.79, 1.00}, {30, 39, 50}, {24, 32, 40}, {25, 19, 15},
                {1.8, 1.8, 1.8}, {3, 3, 5}, {4.8, 5.4, 6.0}};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("yugen_kagaribi", levels[i]);
            assertEquals(yugen[0][i], p.raw(Stat.TRAVEL_SPEED), 0.5);
            assertEquals(yugen[1][i], p.raw(Stat.MOVEMENT_EFFICIENCY), 0.005);
            assertEquals(yugen[2][i], p.raw(Stat.TRAVEL_HUNGER_CUT), 0.5);
            assertEquals(yugen[3][i], p.raw(Stat.SPRINT_BOOST), 0.5);
            assertEquals(yugen[4][i], p.raw(Stat.SPRINT_COOLDOWN), 0.5);
            assertEquals(yugen[5][i], p.jumpHeight(), 1e-9, "the floor of 1.1: fences from level 1");
            assertEquals((int) yugen[6][i], p.gap());
            assertEquals(yugen[7][i], p.safeFall(), 0.05);
            assertTrue(p.has(Feature.STEP_ONE) && p.has(Feature.SPRINT_BURST));
        }
        // Benika (charger III + leaper I), Ilsabet (charger III + snowstride I)
        double[][] charger = {{0.18, 0.24, 0.30}, {6.6, 7.7, 9.0}, {0.60, 0.79, 1.00}, {36, 47, 60}, {20, 15, 12}};
        for (String id : List.of("benika_akagane", "ilsabet_frostmere")) {
            for (int i = 0; i < 3; i++) {
                Profile p = slice(id, levels[i]);
                assertEquals(charger[0][i], p.raw(Stat.KNOCKBACK), 0.005, id);
                assertEquals(charger[1][i], p.safeFall(), 0.05, id);
                assertEquals(charger[2][i], p.raw(Stat.MOVEMENT_EFFICIENCY), 0.005, id);
                assertEquals(charger[3][i], p.raw(Stat.CHARGE_BOOST), 0.5, id);
                assertEquals(charger[4][i], p.raw(Stat.CHARGE_COOLDOWN), 0.5, id);
                assertTrue(p.has(Feature.WEB_IMMUNE) && p.has(Feature.TRAVERSAL_CHARGE) && p.has(Feature.STEP_ONE));
            }
        }
        assertEquals(1.8, slice("benika_akagane", 1).jumpHeight(), 1e-9, "leaper I as Yugen's");
        assertTrue(slice("ilsabet_frostmere", 1).has(Feature.NEVER_FREEZES));
        assertTrue(slice("ilsabet_frostmere", 1).has(Feature.POWDER_SNOW_FREE));
        // Hinae (runner II + swimmer I)
        double[][] hinae = {{9, 12, 15}, {0.30, 0.39, 0.50}, {21, 28, 35}, {1.30, 1.39, 1.50}, {0.20, 0.26, 0.33},
                {1.2, 1.6, 2.0}};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("hinae_shirasuzu", levels[i]);
            assertEquals(hinae[0][i], p.raw(Stat.TRAVEL_SPEED), 0.5);
            assertEquals(hinae[1][i], p.raw(Stat.MOVEMENT_EFFICIENCY), 0.005);
            assertEquals(hinae[2][i], p.raw(Stat.TRAVEL_HUNGER_CUT), 0.5);
            assertEquals(hinae[3][i], p.raw(Stat.SWIM_SPEED), 0.005);
            assertEquals(hinae[4][i], p.raw(Stat.WATER_EFFICIENCY), 0.005);
            assertEquals(hinae[5][i], p.raw(Stat.OXYGEN), 0.05);
            assertEquals(1.0, p.stepHeight(), 1e-9);
        }
        // Kagenori (leaper II + runner I): 2-block ledges need 2.3, from level 13
        double[][] kagenori = {{2.00, 2.24, 2.50}, {3, 4, 6}, {7.2, 8.5, 10.0}, {6, 8, 10}, {12, 16, 20}};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("kagenori_tsurumine", levels[i]);
            assertEquals(kagenori[0][i], p.jumpHeight(), 0.005);
            assertEquals((int) kagenori[1][i], p.gap());
            assertEquals(kagenori[2][i], p.safeFall(), 0.05);
            assertEquals(kagenori[3][i], p.raw(Stat.TRAVEL_SPEED), 0.5);
            assertEquals(kagenori[4][i], p.raw(Stat.TRAVEL_HUNGER_CUT), 0.5);
            assertTrue(p.has(Feature.LANDING_ROLL));
        }
        assertTrue(slice("kagenori_tsurumine", 12).jumpUp() < 2);
        assertTrue(slice("kagenori_tsurumine", 13).jumpUp() >= 2);
        // Tobimaru (acrobat III + climber II): climbs 9 / 12 / 16 (a base wall is 5) and works while clinging
        int[] climb = {9, 12, 16};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("tobimaru_usugumo", levels[i]);
            assertEquals(climb[i], p.climbHeight());
            assertEquals(natsuho[0][i], p.jumpHeight(), 0.005, "as Natsuho's");
            assertTrue(p.has(Feature.WORK_CLINGING));
        }
        // Ambrose (flyer II)
        double[][] ambrose = {{27, 35.5, 45}, {5.9, 6.4, 7.0}, {1.8, 2.4, 3.0}, {4.0, 3.0, 2.4}, {6.5, 7.2, 8.0},
                {1.6, 2.4, 3.3}, {9, 12, 15}, {42, 32, 25}, {1.5, 2.0, 2.5}};
        for (int i = 0; i < 3; i++) {
            Profile p = slice("ambrose_nettlewick", levels[i]);
            assertEquals(ambrose[0][i], p.raw(Stat.STAMINA), 0.05);
            assertEquals(ambrose[1][i], p.raw(Stat.CRUISE_SPEED), 0.05);
            assertEquals(ambrose[2][i], p.raw(Stat.CLIMB_RATE), 0.05);
            assertEquals(ambrose[3][i], p.raw(Stat.GLIDE_SINK), 0.05);
            assertEquals(ambrose[4][i], p.raw(Stat.GLIDE_FORWARD), 0.05);
            assertEquals(ambrose[5][i], p.glideRatio(), 0.05);
            assertEquals(ambrose[6][i], p.raw(Stat.GLIDE_SECONDS), 0.5);
            assertEquals(ambrose[7][i], p.raw(Stat.TAKE_OFF), 0.5);
            assertEquals(ambrose[8][i], p.raw(Stat.REGAIN), 0.05);
            assertTrue(p.flies() && p.glides() && p.airborne());
        }
        // 1.1's examples: a ceiling-I climber scales a 5-high base wall from level 13
        assertEquals(4, at(MobilityTrait.CLIMBER, 1, 12).climbHeight());
        assertEquals(5, at(MobilityTrait.CLIMBER, 1, 13).climbHeight());
    }

    @Test
    void theSheetPrintsLevelsOneTenAndTwenty() {
        List<String> ambrose = MobilityRules.sheet(tt(MobilityTrait.FLYER, 2), null, Config.DEFAULT);
        assertTrue(ambrose.get(0).startsWith("features from level 1: "), ambrose.toString());
        assertTrue(ambrose.contains("stamina (s) 27.0 / 35.5 / 45.0"), ambrose.toString());
        assertTrue(ambrose.contains("cruise speed (b/s) 5.9 / 6.4 / 7.0"), ambrose.toString());
        assertTrue(ambrose.contains("take-off needs (% stamina) 42 / 32 / 25"), ambrose.toString());
        assertTrue(ambrose.contains("glide ratio (blocks per block of drop) 1.6 / 2.4 / 3.3"), ambrose.toString());
        List<String> natsuho = MobilityRules.sheet(tt(MobilityTrait.ACROBAT, 3), tt(MobilityTrait.CLIMBER, 1),
                Config.DEFAULT);
        assertTrue(natsuho.contains("gap (blocks) 3 / 4 / 6"), natsuho.toString());
        assertTrue(natsuho.contains("climb height (blocks) 3 / 4 / 6"), natsuho.toString());
        assertTrue(natsuho.contains("reach up with every air jump (blocks) 2.9 / 3.4 / 4.0"), natsuho.toString());
        assertTrue(natsuho.get(0).contains("2 air jumps"), natsuho.get(0));
        assertTrue(natsuho.stream().noneMatch(l -> l.startsWith("safe fall")), "no fall damage: no safe fall line");
        assertTrue(MobilityRules.sheet(null, null, Config.DEFAULT).isEmpty());
        assertTrue(MobilityRules.sheet(tt(MobilityTrait.RUNNER, 1), null, new Config(false, true, Curve.DEFAULT, 100))
                .isEmpty());
    }

    @Test
    void aPrimaryAndASecondaryCombineAndTheBetterNumberCounts() throws IOException {
        Profile benika = slice("benika_akagane", 1);
        assertEquals(6.6, benika.safeFall(), 0.01, "the charger's 6.6 over the leaper's 4.8");
        assertEquals(MobilityTrait.CHARGER, benika.source(Stat.SAFE_FALL));
        assertEquals(MobilityTrait.LEAPER, benika.source(Stat.JUMP_HEIGHT));
        assertEquals(MobilityTrait.CHARGER, benika.source(Feature.STEP_ONE));
        // the shorter wait: a flyer II and a glider-like wait from no one else; the sink rate of two traits
        Profile two = MobilityRules.profile(tt(MobilityTrait.ACROBAT, 3), tt(MobilityTrait.LEAPER, 2), 20);
        assertEquals(2.5, two.jumpHeight(), 1e-9, "the leaper's 2.5 over the acrobat's 1.5");
        assertEquals(6, two.gap(), "both 6");
        assertTrue(two.has(Feature.NO_FALL_DAMAGE) && two.has(Feature.LANDING_ROLL));
        assertEquals(Double.POSITIVE_INFINITY, two.safeFall());
        assertEquals(2.5 + 2 * 1.25, two.reachUp(), 1e-9, "the leaper's jump with the acrobat's air jumps");
        // hover's step over the runner's
        Profile hover = MobilityRules.profile(tt(MobilityTrait.HOVER, 3), tt(MobilityTrait.RUNNER, 2), 20);
        assertEquals(2.0, hover.stepHeight(), 1e-9);
        // a worker, a format-1 hero, a hero with mobility off: nothing
        assertSame(MobilityRules.NONE, MobilityRules.profile((HeroDef.Mobility) null, 20, Config.DEFAULT));
        assertSame(MobilityRules.NONE, MobilityRules.profile(tt(MobilityTrait.FLYER, 3), null, 20,
                new Config(false, true, Curve.DEFAULT, 100)));
        assertFalse(MobilityRules.NONE.any());
        assertEquals(MobilityRules.WORKER_JUMP, MobilityRules.NONE.jumpHeight());
        assertEquals(MobilityRules.WORKER_STEP, MobilityRules.NONE.stepHeight());
        assertEquals(MobilityRules.WORKER_SAFE_FALL, MobilityRules.NONE.safeFall());
        assertEquals(0, MobilityRules.NONE.jumpUp());
        assertEquals(0, MobilityRules.NONE.gap());
        assertEquals(0, MobilityRules.NONE.climbHeight());
        assertEquals(0.42, MobilityRules.NONE.jumpStrength(), 1e-9);
    }

    @Test
    void attributeModifiersAreNamedAfterTheirTraitAndTravelBonusesSwitch() throws IOException {
        assertTrue(MobilityRules.modifiers(MobilityRules.NONE, true, true).isEmpty(), "workers: none");
        Profile kagenori = slice("kagenori_tsurumine", 1);
        List<Modifier> fight = MobilityRules.modifiers(kagenori, false, false);
        assertEquals(0.55, MobilityRules.attributeValue(Attr.JUMP_STRENGTH, fight), 1e-9, "2.0 blocks: 0.55, which"
                + " jumps 2.04");
        assertEquals("factionclash:mobility_leaper", find(fight, Attr.JUMP_STRENGTH).id());
        assertEquals(7.2, MobilityRules.attributeValue(Attr.SAFE_FALL_DISTANCE, fight), 1e-9);
        assertNull(find(fight, Attr.MOVEMENT_SPEED), "the runner's speed is off in a fight");
        List<Modifier> trip = MobilityRules.modifiers(kagenori, true, true);
        Modifier speed = find(trip, Attr.MOVEMENT_SPEED);
        assertEquals("factionclash:mobility_runner", speed.id());
        assertEquals(Op.ADD_MULTIPLIED_BASE, speed.op());
        assertEquals(0.06, speed.amount(), 1e-9);
        assertEquals(0.25 * 1.06, MobilityRules.attributeValue(Attr.MOVEMENT_SPEED, trip), 1e-9);
        // the charger's knockback only while travelling faster than walking
        Profile benika = slice("benika_akagane", 20);
        assertNull(find(MobilityRules.modifiers(benika, true, false), Attr.KNOCKBACK_RESISTANCE));
        assertNull(find(MobilityRules.modifiers(benika, false, true), Attr.KNOCKBACK_RESISTANCE));
        assertEquals(0.3, find(MobilityRules.modifiers(benika, true, true), Attr.KNOCKBACK_RESISTANCE).amount(), 1e-9);
        assertEquals(1.0, MobilityRules.attributeValue(Attr.STEP_HEIGHT, MobilityRules.modifiers(benika, false, false)),
                1e-9);
        assertEquals(1.0, MobilityRules.attributeValue(Attr.MOVEMENT_EFFICIENCY, MobilityRules.modifiers(benika, false,
                false)), 1e-9, "movement efficiency holds in a fight");
        // no fall damage, fire-proof, under-water mining, swimming
        Profile natsuho = slice("natsuho_komezaki", 1);
        List<Modifier> nm = MobilityRules.modifiers(natsuho, false, false);
        assertEquals(0, MobilityRules.attributeValue(Attr.FALL_DAMAGE_MULTIPLIER, nm), 1e-9);
        assertEquals("factionclash:mobility_acrobat", find(nm, Attr.FALL_DAMAGE_MULTIPLIER).id());
        assertNull(find(nm, Attr.SAFE_FALL_DISTANCE));
        Profile ember = MobilityRules.profile(tt(MobilityTrait.RUNNER, 1), tt(MobilityTrait.EMBER_STRIDE, 1), 1);
        assertEquals(0, MobilityRules.attributeValue(Attr.BURNING_TIME, MobilityRules.modifiers(ember, false, false)),
                1e-9);
        assertEquals("factionclash:mobility_ember_stride", find(MobilityRules.modifiers(ember, false, false),
                Attr.BURNING_TIME).id());
        Profile diver = at(MobilityTrait.SWIMMER, 2, 1);
        List<Modifier> dm = MobilityRules.modifiers(diver, false, false);
        assertEquals(1.0, MobilityRules.attributeValue(Attr.SUBMERGED_MINING_SPEED, dm), 1e-9);
        assertEquals(1.6, MobilityRules.attributeValue(Attr.SWIM_SPEED, dm), 1e-9);
        assertEquals(3, MobilityRules.attributeValue(Attr.OXYGEN_BONUS, dm), 1e-9);
        // one modifier per attribute: the better of primary and secondary, never stacked
        for (Profile p : List.of(kagenori, benika, natsuho, ember, diver, slice("yugen_kagaribi", 20))) {
            List<Modifier> all = MobilityRules.modifiers(p, true, true);
            assertEquals(all.size(), all.stream().map(Modifier::attr).distinct().count(), all.toString());
        }
    }

    private static Modifier find(List<Modifier> mods, Attr a) {
        return mods.stream().filter(m -> m.attr() == a).findFirst().orElse(null);
    }

    @Test
    void burstsAreTravelOnlyAndGrowWithLevel() throws IOException {
        MobilityRules.Burst sprint = MobilityRules.burst(slice("yugen_kagaribi", 1), Feature.SPRINT_BURST);
        assertEquals(0.24, sprint.speedBonus(), 1e-9);
        assertEquals(25, sprint.cooldown(), 1e-9);
        assertEquals(3, sprint.seconds(), 1e-9);
        MobilityRules.Burst charge = MobilityRules.burst(slice("benika_akagane", 10), Feature.TRAVERSAL_CHARGE);
        assertEquals(0.47, charge.speedBonus(), 0.005);
        assertEquals(15.2, charge.cooldown(), 0.05);
        assertNull(MobilityRules.burst(slice("hinae_shirasuzu", 20), Feature.SPRINT_BURST), "runner II has none");
        assertNull(MobilityRules.burst(slice("yugen_kagaribi", 20), Feature.CLIMB));
        assertTrue(MobilityRules.travelBonusesOn(Situation.TRAVEL));
        assertTrue(MobilityRules.travelBonusesOn(Situation.ESCAPE), "an escape is travel");
        assertTrue(MobilityRules.travelBonusesOn(Situation.RAID));
        assertFalse(MobilityRules.travelBonusesOn(Situation.FIGHT), "off in every fight");
    }

    // ------------------------------------------------------------------ flight (2.1, 2.2)

    @Test
    void staminaDrainsInTheAirAndComesBackOnTheGround() {
        assertEquals(1.0, MobilityRules.drain(MobilityRules.Activity.POWERED, false, Situation.TRAVEL));
        assertEquals(0.5, MobilityRules.drain(MobilityRules.Activity.CRUISE, false, Situation.TRAVEL));
        assertEquals(2.0, MobilityRules.drain(MobilityRules.Activity.HOVER, false, Situation.TRAVEL));
        assertEquals(0, MobilityRules.drain(MobilityRules.Activity.GLIDE, true, Situation.FIGHT));
        assertEquals(0.75, MobilityRules.drain(MobilityRules.Activity.CRUISE, true, Situation.TRAVEL), 1e-9, "loaded");
        assertEquals(1.5, MobilityRules.drain(MobilityRules.Activity.POWERED, false, Situation.FIGHT), 1e-9);
        assertEquals(1.5, MobilityRules.drain(MobilityRules.Activity.CRUISE, false, Situation.FIGHT), 1e-9,
                "nobody cruises in a fight");
        assertEquals(0.5, MobilityRules.drain(MobilityRules.Activity.CRUISE, false, Situation.ESCAPE), 1e-9);
        Stamina s = new Stamina(at(MobilityTrait.FLYER, 2, 1));
        assertEquals(27, s.max(), 1e-9);
        assertEquals(1, s.share(), 1e-9);
        s.air(20, 1.0);
        assertEquals(7, s.value(), 1e-9);
        assertFalse(s.low(), "7 of 27 is 26%");
        s.air(1, 1.0);
        assertTrue(s.low(), "under 25%: it heads for a landing, its wings droop");
        assertFalse(s.exhausted());
        s.ground(1);
        assertEquals(6, s.value(), 1e-9, "nothing in the first second");
        s.ground(2);
        assertEquals(9, s.value(), 1e-9, "1.5 a second after it");
        s.air(30, 1.0);
        assertTrue(s.empty());
        assertTrue(s.exhausted(), "at 0 it is exhausted");
        s.ground(1 + 8);
        assertEquals(12, s.value(), 1e-9);
        assertTrue(s.exhausted(), "until 50%");
        s.ground(1.0);
        assertEquals(13.5, s.value(), 1e-9);
        assertFalse(s.exhausted(), "50% of 27 is 13.5");
        s.ground(100);
        assertEquals(27, s.value(), 1e-9, "never over its max");
        // stamina percent scales it (50-200)
        Profile doubled = MobilityRules.profile(tt(MobilityTrait.FLYER, 1), null, 20, new Config(true, true,
                Curve.DEFAULT, 200));
        assertEquals(60, doubled.raw(Stat.STAMINA), 1e-9);
        assertEquals(15, MobilityRules.profile(tt(MobilityTrait.FLYER, 1), null, 20, new Config(true, true,
                Curve.DEFAULT, 10)).raw(Stat.STAMINA), 1e-9, "at least 50%");
        // a worker has no stamina
        assertEquals(0, new Stamina(MobilityRules.NONE).max());
        assertEquals(0, new Stamina(MobilityRules.NONE).share());
    }

    @Test
    void theAverageFlightSpeedsOfTwoOne() {
        assertEquals(4.7, MobilityRules.averageFlightSpeed(at(MobilityTrait.FLYER, 1, 20)), 0.05);
        assertEquals(3.7, MobilityRules.averageFlightSpeed(at(MobilityTrait.FLYER, 1, 1)), 0.05);
        assertEquals(4.4, MobilityRules.averageFlightSpeed(at(MobilityTrait.FLYER, 2, 1)), 0.05);
        assertEquals(5.8, MobilityRules.averageFlightSpeed(at(MobilityTrait.FLYER, 2, 20)), 0.05);
        assertEquals(0, MobilityRules.averageFlightSpeed(at(MobilityTrait.GLIDER, 3, 20)));
        // Ambrose rises about 16 blocks in his 8 s at level 1 (3.7)
        assertEquals(15.9, MobilityRules.riseInAir(at(MobilityTrait.FLYER, 2, 1), 8), 0.05);
        assertEquals(0, MobilityRules.riseInAir(at(MobilityTrait.LEAPER, 3, 20), 8));
    }

    @Test
    void aTakeOffNeedsHeadroomStaminaGroundTimeAndNoStorm() {
        Profile p = at(MobilityTrait.FLYER, 2, 1);
        TakeOffState ok = new TakeOffState(0.5, false, 4, false, Situation.TRAVEL, false, true, false);
        assertNull(MobilityRules.takeOffRefusal(p, ok));
        assertEquals("it does not fly", MobilityRules.takeOffRefusal(at(MobilityTrait.GLIDER, 3, 20), ok));
        assertEquals("less than 4 blocks of headroom", MobilityRules.takeOffRefusal(p, new TakeOffState(0.5, false, 3,
                false, Situation.TRAVEL, false, true, false)));
        assertEquals("less than 4 blocks of headroom", MobilityRules.takeOffRefusal(p, new TakeOffState(0.5, false, 9,
                false, Situation.FIGHT, false, true, true)), "a low_ceiling arena");
        assertEquals("exhausted until its stamina is back at 50%", MobilityRules.takeOffRefusal(p,
                new TakeOffState(0.9, true, 4, false, Situation.TRAVEL, false, true, false)));
        assertEquals("stamina under its take-off threshold of 42%", MobilityRules.takeOffRefusal(p,
                new TakeOffState(0.41, false, 4, false, Situation.TRAVEL, false, true, false)));
        assertNull(MobilityRules.takeOffRefusal(p, new TakeOffState(0.4167, false, 4, false, Situation.TRAVEL, false,
                true, false)), "25 / 0.6 is 41.67%");
        assertEquals("downed in the last 12 s", MobilityRules.takeOffRefusal(p, new TakeOffState(0.9, false, 4, false,
                Situation.FIGHT, true, true, false)));
        assertEquals("not on the ground long enough since its last landing", MobilityRules.takeOffRefusal(p,
                new TakeOffState(0.9, false, 4, false, Situation.FIGHT, false, false, false)));
        String storm = "a thunderstorm: it travels on foot";
        assertEquals(storm, MobilityRules.takeOffRefusal(p, new TakeOffState(0.9, false, 4, true, Situation.TRAVEL,
                false, true, false)));
        assertEquals(storm, MobilityRules.takeOffRefusal(p, new TakeOffState(0.9, false, 4, true, Situation.RAID,
                false, true, false)));
        assertNull(MobilityRules.takeOffRefusal(p, new TakeOffState(0.9, false, 4, true, Situation.ESCAPE, false, true,
                false)), "an escape flies in any weather");
        assertNull(MobilityRules.takeOffRefusal(p, new TakeOffState(0.9, false, 4, true, Situation.FIGHT, false, true,
                false)));
        // mobility.flight off: it glides at its ceiling instead
        Profile off = MobilityRules.profile(tt(MobilityTrait.FLYER, 2), null, 20, new Config(true, false,
                Curve.DEFAULT, 100));
        assertFalse(off.flies());
        assertTrue(off.glides());
        assertTrue(off.airborne());
        assertTrue(off.flightSwitchedOff());
        assertTrue(Double.isNaN(off.raw(Stat.STAMINA)));
        assertEquals(3.33, off.glideRatio(), 0.01);
        assertEquals("flight is switched off (mobility.flight): it glides", MobilityRules.takeOffRefusal(off, ok));
        // the stamina reserve of 3.5: the best landing seen plus 25%
        assertTrue(MobilityRules.mustLandForReserve(12.4, 10, 1.0));
        assertFalse(MobilityRules.mustLandForReserve(12.5, 10, 1.0));
    }

    // ------------------------------------------------------------------ the air budget (5.1.4)

    @Test
    void theAirBudgetPerTakeOffAndOverAnyThirtySeconds() {
        assertEquals(8, MobilityRules.airLimit(Situation.FIGHT, 8, 0), 1e-9);
        assertEquals(6, MobilityRules.airLimit(Situation.FIGHT, 8, 6), 1e-9, "the file's max_air within it");
        assertEquals(4, MobilityRules.airLimit(Situation.FIGHT, 1, 0), 1e-9, "bossAirSeconds at least 4");
        assertEquals(10, MobilityRules.airLimit(Situation.FIGHT, 99, 0), 1e-9, "at most 10");
        assertEquals(10, MobilityRules.airLimit(Situation.RAID, 8, 0), 1e-9, "a raid's approach");
        assertTrue(Double.isNaN(MobilityRules.airLimit(Situation.TRAVEL, 8, 0)), "travel: stamina only");
        assertTrue(Double.isNaN(MobilityRules.airLimit(Situation.ESCAPE, 8, 0)), "escape: stamina only");
        assertEquals(6, MobilityRules.groundLimit(Situation.FIGHT, 1), 1e-9);
        assertEquals(4, MobilityRules.groundLimit(Situation.FIGHT, 3), 1e-9, "4 s in phase 3");
        assertEquals(6, MobilityRules.groundLimit(Situation.RAID, 3), 1e-9);
        assertEquals(0, MobilityRules.groundLimit(Situation.ESCAPE, 1), 1e-9);

        AirBudget b = new AirBudget();
        long t = 1000;
        assertTrue(b.groundTimeMet(t, Situation.FIGHT, 1), "long on the ground before the first take-off");
        for (int i = 0; i < 160; i++) {
            b.tick(t + i, true);
            assertEquals(i >= 160, b.mustLand(t + i, Situation.FIGHT, 8, 0));
        }
        b.tick(t + 160, true);
        assertTrue(b.mustLand(t + 160, Situation.FIGHT, 8, 0), "8 s per take-off");
        assertFalse(b.mustLand(t + 160, Situation.TRAVEL, 8, 0));
        assertFalse(b.mustLand(t + 160, Situation.RAID, 8, 0), "10 s on a raid's approach");
        assertEquals(161, b.windowAirTicks());
        // landing: 6 s on the ground before the next take-off (4 in phase 3)
        long land = t + 161;
        for (int i = 0; i < 120; i++) {
            b.tick(land + i, false);
        }
        assertFalse(b.groundTimeMet(land + 79, Situation.FIGHT, 3));
        assertTrue(b.groundTimeMet(land + 80, Situation.FIGHT, 3));
        assertFalse(b.groundTimeMet(land + 119, Situation.FIGHT, 1));
        assertTrue(b.groundTimeMet(land + 120, Situation.FIGHT, 1));
        // the 30 s window: 8 + 4 airborne within 30 s reaches 40% (12 s) whatever the take-offs
        long up2 = land + 120;
        for (int i = 0; i < 78; i++) {
            b.tick(up2 + i, true);
            assertFalse(b.mustLand(up2 + i, Situation.FIGHT, 8, 0), "tick " + i);
        }
        b.tick(up2 + 78, true);
        assertEquals(240, b.windowAirTicks());
        assertTrue(b.mustLand(up2 + 78, Situation.FIGHT, 8, 0), "12 s of the last 30 in the air, 3.95 s up");
        assertFalse(b.mustLand(up2 + 78, Situation.RAID, 8, 0), "the 30 s window is a fight's rule");
        // after 6 s on the ground the window still holds 12 s of air: no take-off until the first flight leaves it
        long down2 = up2 + 79;
        for (int i = 0; i < 120; i++) {
            b.tick(down2 + i, false);
        }
        assertEquals(240, b.windowAirTicks());
        assertFalse(b.groundTimeMet(down2 + 119, Situation.FIGHT, 1), "40% of the window is air");
        assertTrue(b.groundTimeMet(down2 + 120, Situation.RAID, 1), "a raid's approach has no window");
        assertFalse(b.groundTimeMet(down2 + 120, Situation.FIGHT, 1));
        for (int i = 120; i < 300; i++) {
            b.tick(down2 + i, false);
        }
        assertTrue(b.windowAirTicks() < 240);
        assertTrue(b.groundTimeMet(down2 + 299, Situation.FIGHT, 1));
        // skipped ticks count as ground
        AirBudget gap = new AirBudget();
        gap.tick(0, true);
        gap.tick(700, false);
        assertEquals(0, gap.windowAirTicks(), "the air tick left the 30 s window");
    }

    // ------------------------------------------------------------------ altitude (2.3) and the rules of 5.1

    @Test
    void theCeilingsOfTwoThreeCountEngagedEnemiesOfAnyKind() {
        assertEquals(64 + 24, MobilityRules.ceiling(Situation.TRAVEL, 64, List.of(), 24), 1e-9);
        assertEquals(64 + 24, MobilityRules.ceiling(Situation.ESCAPE, 64, List.of(), 24), 1e-9);
        assertEquals(64 + 48, MobilityRules.ceiling(Situation.TRAVEL, 64, List.of(), 99), 1e-9, "at most 48");
        assertEquals(64 + 8, MobilityRules.ceiling(Situation.TRAVEL, 64, List.of(), 1), 1e-9, "at least 8");
        assertEquals(74, MobilityRules.ceiling(Situation.RAID, 64, List.of(), 24), 1e-9, "a wall top counts as ground");
        assertEquals(70, MobilityRules.ceiling(Situation.FIGHT, 64, List.of(), 24), 1e-9);
        // an enemy on a 20-block pillar: the ceiling follows it, a player or an NPC archer alike
        assertEquals(90, MobilityRules.ceiling(Situation.FIGHT, 64, List.of(84.0, 64.0), 24), 1e-9);
    }

    @Test
    void whatHoldsWhileFightingAndWhatIsLiftedWhileEscaping() {
        for (Rule r : Rule.values()) {
            assertTrue(MobilityRules.holds(r, Situation.FIGHT), "every rule in a fight: " + r);
        }
        Set<Rule> lifted = Set.of(Rule.AIR_BUDGET, Rule.REACH_IN_THE_AIR, Rule.UNFOLLOWABLE_PLACES, Rule.COME_BACK,
                Rule.TRAVEL_BONUSES_OFF, Rule.STRIKES);
        for (Rule r : Rule.values()) {
            assertEquals(!lifted.contains(r), MobilityRules.holds(r, Situation.ESCAPE), "escaping: " + r);
        }
        // kept while escaping: no teleporting, air poise, the climber's grip, no carrying, hazards, the move caps
        for (Rule r : List.of(Rule.NO_TELEPORT, Rule.AIR_POISE, Rule.CLIMBER_KNOCKED_OFF, Rule.NO_CARRYING,
                Rule.HAZARDS, Rule.MOVES_UNCHANGED, Rule.CREATIVE_IGNORED)) {
            assertTrue(MobilityRules.holds(r, Situation.ESCAPE), r.name());
        }
        assertTrue(MobilityRules.holds(Rule.AIR_BUDGET, Situation.RAID), "a raid's approach has its own budget");
        assertFalse(MobilityRules.holds(Rule.AIR_BUDGET, Situation.TRAVEL));
        for (Situation s : Situation.values()) {
            assertTrue(MobilityRules.bossRulesHold(s), "it stays a boss: cap, poise, weak points");
        }
        assertFalse(MobilityRules.mayAttack(Situation.ESCAPE, false), "an escaping hero runs");
        assertTrue(MobilityRules.mayAttack(Situation.ESCAPE, true), "cornered, it fights back");
        assertTrue(MobilityRules.mayAttack(Situation.FIGHT, false));
        assertEquals(15, Rule.COME_BACK.number);
        assertEquals(4, Rule.AIR_BUDGET.number);
    }

    @Test
    void theReachClocksInputCountsEngagedEnemiesWhoeverTheyAre() {
        // no engaged enemy: nothing is out of reach
        assertFalse(MobilityRules.outOfReach(true, 100, List.of(), false, 999, 999));
        // airborne: more than 3 above every engaged enemy's standing height
        assertTrue(MobilityRules.outOfReach(true, 68.5, List.of(64.0, 65.0), true, 0, 0));
        assertFalse(MobilityRules.outOfReach(true, 68.0, List.of(64.0, 65.0), false, 0, 0), "3 above the higher one");
        // on the ground: none of them can get to it
        assertTrue(MobilityRules.outOfReach(false, 64, List.of(64.0), false, 0, 0));
        assertFalse(MobilityRules.outOfReach(false, 64, List.of(64.0), true, 0, 0));
        // deep water 4 s, lava 2 s
        assertFalse(MobilityRules.outOfReach(false, 60, List.of(64.0), true, 79, 0));
        assertTrue(MobilityRules.outOfReach(false, 60, List.of(64.0), true, 80, 0));
        assertTrue(MobilityRules.outOfReach(false, 60, List.of(64.0), true, 0, 40));
        assertFalse(MobilityRules.outOfReach(false, 60, List.of(64.0), true, 0, 39));
        // the clock itself (BossRules): 4 s, held while winding up, never while escaping
        BossRules.ReachClock c = new BossRules.ReachClock();
        for (int i = 0; i < 79; i++) {
            assertFalse(c.tick(true, false, false));
        }
        assertFalse(c.tick(true, true, false), "winding up a telegraphed move holds it");
        assertTrue(c.tick(true, false, false), "4 s: it comes back");
        assertFalse(c.tick(true, false, true), "never while escaping");
        assertEquals(0, c.ticks());
    }

    @Test
    void theValidatorsAdjustedTiersFeedTheProfile() {
        // a rare hero's tier 3 is read as 2 (8.3), so its numbers are a ceiling-II leaper's
        HeroDef d = V2Files.def(V2Files.hero2("\"mobility\": {\"primary\": {\"trait\": \"leaper\", \"tier\": 3}}"));
        Profile p = MobilityRules.profile(d.v2().mobility(), 20, Config.DEFAULT);
        assertEquals(2.5, p.jumpHeight(), 1e-9);
        assertEquals(6, p.gap());
        // a secondary above its primary is lowered to it
        HeroDef two = V2Files.def(V2Files.hero2("\"mobility\": {\"primary\": {\"trait\": \"leaper\", \"tier\": 1},"
                + " \"secondary\": {\"trait\": \"climber\", \"tier\": 2}}"));
        assertEquals(6, MobilityRules.profile(two.v2().mobility(), 20, Config.DEFAULT).climbHeight());
        // a flyer secondary is dropped: no flight
        HeroDef fly = V2Files.def(V2Files.hero2("\"mobility\": {\"primary\": {\"trait\": \"runner\"}, \"secondary\":"
                + " {\"trait\": \"flyer\"}}"));
        assertFalse(MobilityRules.profile(fly.v2().mobility(), 20, Config.DEFAULT).flies());
        // a format-1 hero has no mobility
        HeroDef v1 = HeroReaderTest.def(HeroReaderTest.hero(""));
        assertSame(MobilityRules.NONE, MobilityRules.profile(v1.v2().mobility(), 20, Config.DEFAULT));
    }
}
