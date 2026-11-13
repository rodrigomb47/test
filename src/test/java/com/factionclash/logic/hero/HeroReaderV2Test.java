package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Band;
import com.factionclash.logic.hero.HeroDef.ConditionType;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.EffectType;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroDef.WhenLosing;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.factionclash.logic.hero.V2Files.LUNGE;
import static com.factionclash.logic.hero.V2Files.def;
import static com.factionclash.logic.hero.V2Files.hero2;
import static com.factionclash.logic.hero.V2Files.move;
import static com.factionclash.logic.hero.V2Files.of;
import static com.factionclash.logic.hero.V2Files.one;
import static com.factionclash.logic.hero.V2Files.read;
import static com.factionclash.logic.hero.V2Files.withMove;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HEROES_V2 14.3 {@code HeroReaderV2Test}: format 2 of the hero file (HEROES_V2 12) and its sense rules (14.1):
 * every new field, form, effect, condition, trigger and line event, the boss block as it is after the owner's
 * decisions, personality and the end of a fight, leader, talents, voice, recruit, relationships, revival, mobility
 * (MOBILITY_AND_WORK 8), sound references (SOUND.md), the generated block, the format-1-to-2 mapping and the limits.
 */
class HeroReaderV2Test {
    private static final double EPS = 1e-9;

    private static String fx(String type) {
        return "{\"type\": \"" + type + "\"}";
    }

    private static final String DMG = "{\"type\": \"damage\", \"amount\": 2}";

    // ------------------------------------------------------------------ the minimal hero and the defaults

    @Test
    void theMinimalFormatTwoHeroLoadsCleanWithItsDefaults() {
        Loaded l = read(hero2(""));
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, l.problems().report());
        assertTrue(l.problems().all().isEmpty(), l.problems().report());
        V2Sanity.sane(d);
        assertEquals(2, d.format());
        HeroDef.V2 v = d.v2();
        assertTrue(d.skills().isEmpty() && d.ultimate() == null && d.empowered() == null);
        assertEquals(1, v.moves().size());
        Move lunge = v.moves().get(0);
        assertEquals("lunge", lunge.id());
        assertEquals(MoveClass.HEAVY, lunge.cls(), "a dash is heavy by its form (6.4)");
        assertEquals(new HeroDef.Frames(0.8, HeroCatalogue.DEFAULT_ACTIVE, 0.8), lunge.frames(), "no frames: the"
                + " class minimums, silently");
        assertEquals(List.of(Band.MID), lunge.bands(), "a 6-block dash likes mid range");
        assertEquals(5, lunge.weight());
        assertEquals(List.of(1), lunge.phases(), "one phase");
        assertEquals("move.lunge", lunge.anim());
        assertEquals(Decal.STRIP, lunge.telegraph().shape());
        assertEquals(300, v.boss().healthScale(), "a rare hero (the default rarity): 300");
        assertEquals(350, v.boss().healthScale(true), "a leader adds 50 (6.2)");
        assertEquals(100, v.boss().poise(), "a striker's poise (12.4)");
        assertEquals(12, v.boss().maxHitShare());
        assertNull(v.personality().temperament());
        assertEquals(50, v.personality().trait(HeroDef.Trait.LOYALTY));
        assertEquals(WhenLosing.ESCAPE, v.end().kind(), "neutral traits: nerve 50 + 50 - 50 = 50, under 65");
        assertEquals(8, v.end().fleeAt(), "a striker's retreat_below 25: a third is 8");
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.RUNNER, 1), v.mobility().primary(), "no archetype:"
                + " runner I");
        assertEquals(new HeroDef.Voice(HeroDef.Blip.HARP, 1.0, 3, 1.0), v.voice());
        assertEquals(HeroDef.Leader.DEFAULT, v.leader());
        assertTrue(v.talents().isEmpty() && v.relationships().isEmpty());
        assertNull(v.revival());
        assertNull(v.recruit());
        assertNull(v.generatedHash());
        assertEquals(3, d.basicAttack().combo());
        assertEquals(d.moves(), v.moves());
    }

    @Test
    void theRarityAndTheRoleSetTheBossDefaults() {
        int[] scale = {250, 300, 350, 400};
        int[] phases = {2, 2, 3, 3};
        HeroDef.Rarity[] rs = HeroDef.Rarity.values();
        for (int i = 0; i < rs.length; i++) {
            HeroDef d = def(hero2("\"rarity\": \"" + rs[i].id() + "\", \"boss\": {\"weak_points\": [\"back\"]}"));
            assertEquals(scale[i], d.v2().boss().healthScale(), rs[i].id());
            assertEquals(phases[i], d.v2().boss().phases().size(), rs[i].id());
        }
        HeroDef epic = def(hero2("\"rarity\": \"epic\", \"boss\": {\"weak_points\": [\"back\"]}"));
        assertEquals(List.of(100, 60, 25), epic.v2().boss().phases().stream().map(HeroDef.Phase::at).toList());
        HeroDef rare = def(hero2("\"boss\": {\"weak_points\": [\"back\"]}"));
        assertEquals(List.of(100, 50), rare.v2().boss().phases().stream().map(HeroDef.Phase::at).toList());
        int[] poise = {140, 100, 80, 70, 80, 80, 70};
        HeroDef.Role[] roles = HeroDef.Role.values();
        for (int i = 0; i < roles.length; i++) {
            HeroDef d = def(hero2("").replace("\"striker\"", "\"" + roles[i].id() + "\""));
            assertEquals(poise[i], d.v2().boss().poise(), roles[i].id());
            assertEquals(HeroCatalogue.ROLES.get(roles[i]).health(), d.stats().health(), roles[i].id());
        }
    }

    // ------------------------------------------------------------------ format 2 only, and format 1 untouched

    @Test
    void formatTwoWordsAreNotFormatOnes() {
        // in a format-1 file, the new forms, effects, triggers, roles and events are no words, and the moves block is
        // an unknown field; their synonyms read as before
        Loaded v1 = HeroReader.read("test_hero.json", HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"combo\","
                + " \"effects\": [" + DMG + "]") + ", \"moves\": []"));
        assertEquals(Severity.PART_LEFT_OUT, one(v1, Code.WORD_PART).severity());
        assertTrue(one(v1, Code.UNKNOWN_FIELD).message().startsWith("\"moves\" is not a field here"));
        HeroDef charge1 = HeroReaderTest.def(HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"charge\","
                + " \"effects\": [" + DMG + "]")));
        assertEquals(Form.DASH, charge1.skills().get(0).form(), "v1 reads \"charge\" as a dash");
        HeroDef charge2 = def(withMove("\"id\": \"bull_rush\", \"name\": \"Bull Rush\", \"form\": \"charge\","
                + " \"effects\": [" + DMG + "]"));
        assertEquals(Form.CHARGE, move(charge2, "bull_rush").form(), "format 2 has a charge form of its own");
        HeroDef barrier1 = HeroReaderTest.def(HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"self\","
                + " \"effects\": [{\"type\": \"barrier\", \"amount\": 4}]")));
        assertEquals(EffectType.SHIELD, barrier1.skills().get(0).effects().get(0).type());
        HeroDef barrier2 = def(withMove("\"id\": \"ward\", \"name\": \"Ward\", \"form\": \"self\", \"effects\":"
                + " [{\"type\": \"barrier\", \"hits\": 5}]"));
        assertEquals(new HeroDef.Barrier(new HeroDef.Common(HeroDef.To.SELF, 8, 100, null), 5, 8, null),
                move(barrier2, "ward").ability().effects().get(0));
        assertTrue(HeroReader.read("test_hero.json", HeroReaderTest.MIN.replace("striker", "controller") + "}")
                .heroes().isEmpty(), "a format-1 hero cannot be a controller");
        assertEquals(HeroDef.Role.CONTROLLER, def(hero2("").replace("striker", "crowd_control")).behaviour().role());
        Loaded trig = HeroReader.read("test_hero.json", HeroReaderTest.hero("\"passives\": [{\"name\": \"P\","
                + " \"trigger\": \"on_stagger\", \"effects\": [{\"type\": \"cleanse\"}]}]"));
        assertEquals(Severity.PART_LEFT_OUT, one(trig, Code.WORD_PART).severity());
    }

    @Test
    void formatOneHeroesReadAsBeforeAndMapToFormatTwo() throws IOException {
        for (String ex : List.of("bram_ostler", "sela_quickfern", "maudie_brine")) {
            String text = Files.readString(Path.of("docs/heroes/examples/" + ex + ".json"));
            Loaded l = HeroReader.read(ex + ".json", text);
            assertTrue(l.problems().all().isEmpty(), l.problems().report());
            HeroDef d = l.hero(ex);
            V2Sanity.sane(d);
            HeroDef.V2 v = d.v2();
            assertEquals(d.skills().size(), v.moves().size(), ex);
            for (int i = 0; i < d.skills().size(); i++) {
                HeroDef.Ability a = d.skills().get(i);
                Move m = v.moves().get(i);
                boolean zone = a.form() == Form.ZONE;
                assertEquals(zone ? HeroBalance.withDelay(a, 0) : a, m.ability(), "the skill itself, with its balanced"
                        + " numbers (a zone's delay moves into its wind-up)");
                assertEquals(HeroReader.normaliseId(a.name()), m.id());
                MoveClass cls = m.cls();
                assertTrue(cls.atLeast(HeroCatalogue.classOf(a.form())));
                double windup = Math.max(Math.max(a.windup(), zone ? HeroBalance.upToTick(a.delay()) : 0),
                        cls.minWindup);
                assertEquals(windup, m.frames().windup(), EPS, "a v1 wind-up is raised to its class's minimum (and a"
                        + " zone's to its delay), silently");
            }
            assertEquals(MoveClass.ULTIMATE, v.ultimate().cls());
            assertEquals(d.empowered() == null ? 0 : 1, v.states().size());
            assertEquals(d.baseAbilities().size(), v.talents().size());
            assertEquals(HeroCatalogue.HEALTH_SCALE.get(d.rarity()), v.boss().healthScale());
            assertNull(v.mobility(), "a format-1 hero gets no mobility (MOBILITY_AND_WORK 8.2)");
            assertNull(v.personality().temperament());
        }
        HeroDef bram = HeroReader.read("bram_ostler.json", Files.readString(Path.of(
                "docs/heroes/examples/bram_ostler.json"))).hero("bram_ostler");
        assertEquals(HeroDef.TalentId.AURA, bram.v2().talents().get(0).id());
        // a skill with a stun is at least heavy, as a move
        HeroDef stun = HeroReaderTest.def(HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"melee\", \"cooldown\":"
                + " 60, \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"stun\"}]")));
        assertEquals(MoveClass.HEAVY, stun.moves().get(0).cls());
        // a v1 retreat_below 0 reads as a last stand
        HeroDef never = HeroReaderTest.def(HeroReaderTest.MIN.replace("\"striker\"}", "\"striker\", \"retreat_below\":"
                + " 0}") + "}");
        assertEquals(WhenLosing.LAST_STAND, never.v2().end().kind());
    }

    @Test
    void formatOneFieldsInAFormatTwoFileAreReadAsTheirFormatTwoParts() {
        Loaded l = read(hero2("\"skills\": [{\"name\": \"Big Jab\", \"form\": \"melee\", \"effects\": [" + DMG + "]}],"
                + " \"empowered\": {\"name\": \"High Water\", \"stats\": [{\"stat\": \"speed\", \"percent\": 10}]},"
                + " \"base_abilities\": [{\"type\": \"mend\"}], \"starter_for\": \"stoneguard\", \"skin_model\":"
                + " \"slim\", \"stats\": {\"size\": 104}"));
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, l.problems().report());
        assertEquals(List.of("lunge", "big_jab"), d.v2().moves().stream().map(Move::id).toList(), "skills as moves,"
                + " their ids from their names");
        assertEquals("high_water", d.v2().states().get(0).name());
        assertEquals(HeroDef.TalentId.MEND, d.v2().talents().get(0).id());
        assertEquals(4, of(l, Code.V1_FIELD).size(), "skills, empowered, base_abilities, stats.size");
        assertEquals(2, of(l, Code.FIELD_ELSEWHERE).size(), "starter_for and skin_model: format-1 fields");
        assertTrue(one(l, Code.MARK_RENAMED).message().contains("\"high_water\""));
        assertEquals(100, d.stats().size(), "stats.size is replaced by art.scale");
        assertTrue(d.skills().isEmpty() && d.empowered() == null && d.baseAbilities().isEmpty());
        Loaded src = read(hero2("\"art\": {}, \"animations\": {\"style\": \"katana\"}"));
        assertEquals(2, of(src, Code.SOURCE_ONLY).size());
        assertNotNull(src.hero("test_hero"));
    }

    // ------------------------------------------------------------------ moves (12.7)

    @Test
    void moveFieldsReadAndDefault() {
        HeroDef d = def(withMove("\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"class\": \"heavy\","
                + " \"frames\": {\"windup\": 1.2, \"active\": 0.2, \"recovery\": 1.0}, \"band\": [\"close\", \"mid\"],"
                + " \"weight\": 7, \"repeatable\": true, \"breaks_guard\": true, \"super_armor\": true, \"channel\":"
                + " {\"interrupt\": 20}, \"telegraph\": {\"shape\": \"ring\", \"color\": \"#112233\"}, \"prefer_when\":"
                + " \"target_blocking\", \"effects\": [" + DMG + "]"));
        Move m = move(d, "jab");
        assertEquals(MoveClass.HEAVY, m.cls(), "heavier than the form's light: allowed");
        assertEquals(new HeroDef.Frames(1.2, 0.2, 1.0), m.frames());
        assertEquals(List.of(Band.CLOSE, Band.MID), m.bands());
        assertEquals(7, m.weight());
        assertTrue(m.repeatable() && m.breaksGuard() && m.superArmor() && !m.unblockable());
        assertEquals(20, m.channel());
        assertEquals(new HeroDef.Telegraph(Decal.RING, 0x112233), m.telegraph());
        assertEquals(ConditionType.TARGET_BLOCKING, m.preferWhen().type());
        // "any" is every band; a v1 windup is the frames' wind-up; channel true is 15
        HeroDef any = def(withMove("\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"band\": \"any\","
                + " \"windup\": 0.6, \"channel\": true, \"effects\": [" + DMG + "]"));
        assertEquals(List.of(Band.CLOSE, Band.MID, Band.FAR), move(any, "jab").bands());
        assertEquals(0.6, move(any, "jab").frames().windup(), EPS);
        assertEquals(15, move(any, "jab").channel());
        // default bands by form and reach
        Object[][] bands = {{"melee", "", List.of(Band.CLOSE)}, {"shot", "\"range\": 16, ", List.of(Band.MID, Band.FAR)},
                {"bolt", "\"range\": 8, ", List.of(Band.MID)}, {"self", "", List.of(Band.CLOSE, Band.MID, Band.FAR)},
                {"zone", "\"zone_at\": \"self\", ", List.of(Band.CLOSE)}, {"nova", "", List.of(Band.CLOSE)},
                {"cone", "\"range\": 3, ", List.of(Band.CLOSE)}};
        for (Object[] b : bands) {
            String effect = b[0].equals("self") ? "{\"type\": \"cleanse\"}" : DMG;
            HeroDef x = def(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"" + b[0] + "\", " + b[1]
                    + "\"effects\": [" + effect + "]"));
            assertEquals(b[2], move(x, "m1").bands(), (String) b[0]);
        }
        // the ultimate and its awakened twin
        HeroDef u = def(hero2("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"line\": \"Now!\","
                + " \"effects\": [" + DMG + "], \"awakened\": {\"id\": \"bigger\", \"name\": \"Bigger\", \"form\":"
                + " \"line\", \"line\": \"Now, truly!\", \"effects\": [" + DMG + "]}}"));
        assertEquals("ultimate", u.v2().ultimate().anim());
        assertEquals("ultimate.awakened", u.v2().awakened().anim());
        assertEquals(MoveClass.ULTIMATE, u.v2().awakened().cls());
        assertEquals(new HeroDef.Frames(1.5, HeroCatalogue.DEFAULT_ACTIVE, 1.5), u.v2().ultimate().frames());
        assertEquals(3, u.v2().allMoves().size());
        Loaded twin = read(hero2("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"line\":"
                + " \"Now!\", \"effects\": [" + DMG + "], \"awakened\": {\"id\": \"big\", \"name\": \"Big\", \"form\":"
                + " \"nova\", \"line\": \"Again!\", \"effects\": [" + DMG + "]}}"));
        assertEquals(Severity.PART_LEFT_OUT, one(twin, Code.NAME_TAKEN).severity());
        assertNull(twin.hero("test_hero").v2().awakened());
        Loaded onMove = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"effects\": [" + DMG
                + "], \"awakened\": {}, \"charge_rate\": 120"));
        assertEquals(2, of(onMove, Code.FIELD_ELSEWHERE).size());
        // an ultimate class on a move and the reverse
        Loaded cls = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"class\":"
                + " \"ultimate\"") + "], \"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\","
                + " \"class\": \"heavy\", \"line\": \"Now!\", \"effects\": [" + DMG + "]}"));
        assertEquals(2, of(cls, Code.CLASS_RAISED).size());
        assertEquals(MoveClass.HEAVY, move(cls.hero("test_hero"), "lunge").cls());
        assertEquals(MoveClass.ULTIMATE, cls.hero("test_hero").v2().ultimate().cls());
    }

    @Test
    void moveIdsAreUniqueReadableAndNeverALocomotionName() {
        Loaded l = read(withMove("\"name\": \"Falling Crane Strike\", \"form\": \"leap\", \"effects\": [" + DMG + "]"));
        assertEquals("\"id\" is missing; the name gives it: \"falling_crane_strike\" (its animation is"
                + " move.falling_crane_strike)", one(l, Code.MOVE_ID).message());
        for (String reserved : List.of("leap", "fly", "glide", "climb", "idle", "air_dive", "takeoff")) {
            Loaded r = read(withMove("\"id\": \"" + reserved + "\", \"name\": \"X\", \"form\": \"melee\", \"effects\":"
                    + " [" + DMG + "]"));
            assertTrue(one(r, Code.MOVE_ID_RESERVED).message().contains("move." + reserved), reserved);
            assertEquals(1, r.hero("test_hero").v2().moves().size(), reserved);
        }
        Loaded bad = read(withMove("\"id\": \"9x\", \"name\": \"X\", \"form\": \"melee\", \"effects\": [" + DMG + "]"));
        assertEquals(Severity.PART_LEFT_OUT, one(bad, Code.MARK_INVALID).severity());
        Loaded renamed = read(withMove("\"id\": \"Big Jab\", \"name\": \"X\", \"form\": \"melee\", \"effects\": [" + DMG
                + "]"));
        assertEquals("\"Big Jab\" was read as \"big_jab\"", one(renamed, Code.MARK_RENAMED).message());
        // 12 moves at most
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            sb.append(i == 0 ? "" : ", ").append(LUNGE.replace("lunge", "lunge_" + i));
        }
        Loaded many = read(hero2("\"moves\": [" + sb + "]"));
        assertEquals(12, many.hero("test_hero").v2().moves().size());
        assertTrue(one(many, Code.LIST_TOO_LONG).message().contains("at most 12 items"));
    }

    @Test
    void theEightNewFormsReadTheirOwnFields() {
        HeroDef d = def(hero2("\"moves\": [" + LUNGE + ", "
                + "{\"id\": \"combo1\", \"name\": \"C\", \"form\": \"combo\", \"cooldown\": 30, \"strikes\": [{\"radius\":"
                + " 1, \"windup\": 0.1, \"effects\": [" + DMG + "]}, {\"effects\": [" + DMG + ", {\"type\": \"knockback\"}]}]},"
                + "{\"id\": \"charge1\", \"name\": \"Ch\", \"form\": \"charge\", \"range\": 12, \"speed\": 190, \"wall_stun\":"
                + " true, \"effects\": [" + DMG + "]},"
                + "{\"id\": \"wave1\", \"name\": \"W\", \"form\": \"wave\", \"radius\": 9, \"speed\": 10, \"waves\": 2,"
                + " \"interval\": 0.8, \"cooldown\": 30, \"effects\": [" + DMG + "]},"
                + "{\"id\": \"rain1\", \"name\": \"R\", \"form\": \"rain\", \"count\": 9, \"radius\": 6, \"duration\": 3,"
                + " \"delay\": 0.8, \"range\": 14, \"cooldown\": 30, \"effects\": [" + DMG + "]},"
                + "{\"id\": \"sweep1\", \"name\": \"S\", \"form\": \"sweep\", \"range\": 10, \"arc\": 150, \"duration\": 2,"
                + " \"width\": 2, \"cooldown\": 30, \"effects\": [" + DMG + "]},"
                + "{\"id\": \"grab1\", \"name\": \"G\", \"form\": \"grab\", \"range\": 3, \"throw\": 4, \"up\": 1,"
                + " \"effects\": [" + DMG + "]},"
                + "{\"id\": \"counter1\", \"name\": \"Co\", \"form\": \"counter\", \"stance\": 1.5, \"absorb\": 60,"
                + " \"ranged\": true, \"effects\": [" + DMG + "]},"
                + "{\"id\": \"barrage1\", \"name\": \"B\", \"form\": \"barrage\", \"count\": 4, \"interval\": 0.5,"
                + " \"range\": 20, \"projectile\": \"snowball\", \"cooldown\": 30, \"effects\": [" + DMG + "],"
                + " \"per_shot\": [[" + DMG + "], [{\"type\": \"knockback\"}]]}]"));
        V2Sanity.sane(d);
        Move combo = move(d, "combo1");
        assertEquals(2, combo.strikes().size());
        assertEquals(1, combo.strikes().get(0).radius(), EPS);
        assertEquals(0.1, combo.strikes().get(0).windup(), EPS);
        assertTrue(combo.ability().effects().isEmpty(), "a combo's effects are its strikes'");
        assertEquals(MoveClass.LIGHT, combo.cls());
        Move charge = move(d, "charge1");
        assertEquals(12, charge.ability().range(), EPS);
        assertEquals(190, charge.extra().speed(), EPS);
        assertTrue(charge.extra().wallStun());
        assertEquals(MoveClass.HEAVY, charge.cls());
        Move wave = move(d, "wave1");
        assertEquals(9, wave.ability().radius(), EPS);
        assertEquals(10, wave.extra().speed(), EPS);
        assertEquals(2, wave.extra().waves());
        assertEquals(0.8, wave.ability().interval(), EPS);
        assertEquals(Decal.RING, wave.telegraph().shape());
        Move rain = move(d, "rain1");
        assertEquals(9, rain.ability().count());
        assertEquals(6, rain.ability().radius(), EPS);
        assertEquals(3, rain.ability().duration(), EPS);
        assertEquals(0.8, rain.ability().delay(), EPS, "a rain's impact delay");
        assertEquals(Decal.IMPACTS, rain.telegraph().shape());
        Move sweep = move(d, "sweep1");
        assertEquals(150, sweep.ability().angle(), "the arc");
        assertEquals(2, sweep.ability().width(), EPS);
        assertEquals(Decal.FAN, sweep.telegraph().shape());
        Move grab = move(d, "grab1");
        assertEquals(4, grab.extra().throwStrength(), EPS);
        assertEquals(1, grab.extra().up(), EPS);
        assertTrue(grab.unblockable(), "a grab is always unblockable (6.5)");
        assertEquals(MoveClass.HEAVY, grab.cls());
        Move counter = move(d, "counter1");
        assertEquals(1.5, counter.extra().stance(), EPS);
        assertEquals(60, counter.extra().absorb());
        assertTrue(counter.extra().ranged());
        assertEquals(Decal.NONE, counter.telegraph().shape(), "light by its form: no decal");
        Move barrage = move(d, "barrage1");
        assertEquals(4, barrage.ability().count());
        assertEquals(0.5, barrage.ability().interval(), EPS);
        assertEquals("snowball", barrage.ability().projectile());
        assertEquals(2, barrage.perShot().size());
        // defaults
        HeroDef dd = def(hero2("\"moves\": [" + LUNGE + ", {\"id\": \"wave1\", \"name\": \"W\", \"form\": \"wave\","
                + " \"cooldown\": 30, \"effects\": [" + DMG + "]}, {\"id\": \"rain1\", \"name\": \"R\", \"form\":"
                + " \"rain\", \"cooldown\": 30, \"effects\": [" + DMG + "]}, {\"id\": \"counter1\", \"name\": \"C\","
                + " \"form\": \"counter\", \"effects\": [" + DMG + "]}]"));
        assertEquals(10, move(dd, "wave1").ability().radius(), EPS);
        assertEquals(8, move(dd, "wave1").extra().speed(), EPS);
        assertEquals(1, move(dd, "wave1").extra().waves());
        assertEquals(6, move(dd, "rain1").ability().count());
        assertEquals(5, move(dd, "rain1").ability().radius(), EPS);
        assertEquals(0.9, move(dd, "rain1").ability().delay(), EPS);
        assertEquals(80, move(dd, "counter1").extra().absorb());
        assertEquals(1, move(dd, "counter1").extra().stance(), EPS);
    }

    @Test
    void newFormsClampTheirRangesAndNoteFieldsTheyDoNotUse() {
        Loaded l = read(withMove("\"id\": \"charge1\", \"name\": \"Ch\", \"form\": \"charge\", \"range\": 30, \"speed\":"
                + " 90, \"angle\": 40, \"hits\": 2, \"strikes\": [], \"effects\": [" + DMG + "]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(16, move(d, "charge1").ability().range(), EPS);
        assertEquals(120, move(d, "charge1").extra().speed(), EPS);
        assertEquals(2, of(l, Code.CLAMPED).size());
        assertEquals(3, of(l, Code.FIELD_UNUSED).size(), "angle, hits and strikes are not a charge's");
        Loaded v1form = read(withMove("\"id\": \"rush\", \"name\": \"R\", \"form\": \"dash\", \"speed\": 150,"
                + " \"effects\": [" + DMG + "]"));
        assertEquals("\"speed\" is not used by form \"dash\"; it was ignored", one(v1form, Code.FIELD_UNUSED).message());
        Loaded counts = read(withMove("\"id\": \"b1\", \"name\": \"B\", \"form\": \"barrage\", \"count\": 2,"
                + " \"cooldown\": 30, \"effects\": [" + DMG + "]"));
        assertEquals(3, move(counts.hero("test_hero"), "b1").ability().count(), "a barrage fires 3-10 shots");
        Loaded vcount = read(withMove("\"id\": \"v1\", \"name\": \"V\", \"form\": \"volley\", \"count\": 9,"
                + " \"cooldown\": 30, \"effects\": [" + DMG + "]"));
        assertEquals(5, move(vcount.hero("test_hero"), "v1").ability().count(), "a volley keeps format 1's 2-5");
        Loaded combo = read(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"combo\", \"strikes\": [{\"effects\":"
                + " [" + DMG + "]}]"));
        assertEquals(Severity.PART_LEFT_OUT, one(combo, Code.NO_EFFECTS).severity(), "a combo needs 2 strikes");
        Loaded perShot = read(withMove("\"id\": \"v1\", \"name\": \"V\", \"form\": \"volley\", \"per_shot\": [[" + DMG
                + "]], \"effects\": [" + DMG + "]"));
        assertEquals("\"per_shot\" is not used by form \"volley\"; it was ignored",
                one(perShot, Code.FIELD_UNUSED).message());
        Loaded comboFx = read(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"combo\", \"effects\": [" + DMG
                + "], \"strikes\": [{\"effects\": [" + DMG + "]}, {\"effects\": [" + DMG + "]}]"));
        assertEquals("a combo's effects are its strikes'; it was ignored", one(comboFx, Code.FIELD_UNUSED).message());
        Loaded noStrikes = read(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"combo\""));
        assertTrue(of(noStrikes, Code.MISSING).get(0).message().startsWith("\"strikes\" is missing"));
        Loaded moveInCharge = read(withMove("\"id\": \"c1\", \"name\": \"C\", \"form\": \"charge\", \"effects\": [" + DMG
                + ", {\"type\": \"move_self\"}]"));
        assertEquals(Severity.PART_LEFT_OUT, one(moveInCharge, Code.MOVE_IN_LEAP).severity(), "the hero is already"
                + " moving in a charge");
    }

    // ------------------------------------------------------------------ effects (6.6)

    @Test
    void theSevenNewEffectsReadTheirFieldsAndRecipients() {
        HeroDef d = def(withMove("\"id\": \"all1\", \"name\": \"All\", \"form\": \"melee\", \"cooldown\": 120,"
                + " \"effects\": [{\"type\": \"guard_break\", \"seconds\": 3}, {\"type\": \"poise\", \"amount\": 20},"
                + " {\"type\": \"silence\"}, {\"type\": \"tether\", \"seconds\": 5, \"max\": 8}, {\"type\":"
                + " \"call_guards\"}]"));
        List<HeroDef.Effect> e = move(d, "all1").ability().effects();
        HeroDef.Common t = new HeroDef.Common(HeroDef.To.TARGETS, 8, 100, null),
                self = new HeroDef.Common(HeroDef.To.SELF, 8, 100, null);
        assertEquals(new HeroDef.GuardBreak(t, 3), e.get(0));
        assertEquals(new HeroDef.Poise(t, 20), e.get(1));
        assertEquals(new HeroDef.Silence(t, 2), e.get(2));
        assertEquals(new HeroDef.Tether(t, 5, 8), e.get(3));
        assertEquals(new HeroDef.CallGuards(self, 2), e.get(4));
        HeroDef s = def(withMove("\"id\": \"s1\", \"name\": \"S\", \"form\": \"self\", \"cooldown\": 120, \"effects\":"
                + " [{\"type\": \"barrier\", \"hits\": 7, \"seconds\": 12, \"focus\": \"focus_crystal\"}, {\"type\":"
                + " \"spawn_add\", \"add\": \"decoy\", \"count\": 2}]"));
        assertEquals(new HeroDef.Barrier(self, 7, 12, HeroDef.AddKind.FOCUS_CRYSTAL), move(s, "s1").ability().effects()
                .get(0));
        assertEquals(new HeroDef.SpawnAdd(self, HeroDef.AddKind.DECOY, 2), move(s, "s1").ability().effects().get(1));
        // recipient classes: the four control effects reach enemies only; the other three act from the hero
        for (String type : List.of("guard_break", "silence", "tether")) {
            Loaded l = read(withMove("\"id\": \"a1\", \"name\": \"A\", \"form\": \"allies\", \"effects\": [{\"type\":"
                    + " \"heal\", \"amount\": 2}, {\"type\": \"" + type + "\"}]"));
            assertEquals(Severity.PART_LEFT_OUT, one(l, Code.ENEMY_ONLY).severity(), type);
        }
        Loaded selfTo = read(withMove("\"id\": \"a1\", \"name\": \"A\", \"form\": \"self\", \"effects\": [{\"type\":"
                + " \"call_guards\", \"to\": \"allies\"}]"));
        assertEquals(Severity.NOTE, one(selfTo, Code.SELF_ONLY_TO).severity());
        Loaded poiseMissing = read(withMove("\"id\": \"a1\", \"name\": \"A\", \"form\": \"melee\", \"effects\": [" + DMG
                + ", {\"type\": \"poise\"}]"));
        assertTrue(one(poiseMissing, Code.MISSING).message().startsWith("\"amount\" is missing"));
        Loaded addInPassive = read(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"on_kill\", \"effects\":"
                + " [{\"type\": \"spawn_add\", \"add\": \"decoy\"}, {\"type\": \"cleanse\"}]}]"));
        assertEquals("\"spawn_add\" only works in moves and the ultimate", one(addInPassive, Code.SUMMON_SLOT).message());
        Loaded decoys = read(withMove("\"id\": \"s1\", \"name\": \"S\", \"form\": \"self\", \"cooldown\": 60,"
                + " \"effects\": [{\"type\": \"spawn_add\", \"add\": \"decoy\", \"count\": 4}]"));
        assertEquals("at most 3 decoys per hero (HEROES_V2 6.11). Set to 3", one(decoys, Code.SUMMON_COUNT).message());
        Loaded unknownAdd = read(withMove("\"id\": \"s1\", \"name\": \"S\", \"form\": \"self\", \"effects\":"
                + " [{\"type\": \"spawn_add\", \"add\": \"dragon\"}, {\"type\": \"cleanse\"}]"));
        assertEquals(Severity.PART_LEFT_OUT, one(unknownAdd, Code.WORD_PART).severity());
        // every format-2 effect type is in this test's heroes
        Set<EffectType> seen = new HashSet<>();
        for (HeroDef h : List.of(d, s)) {
            HeroBalance.effectLists(h).forEach(l -> l.forEach(x -> seen.add(x.type())));
        }
        for (EffectType et : EffectType.values()) {
            if (et.since() == 2) {
                assertTrue(seen.contains(et), et.id());
            }
        }
    }

    @Test
    void empowerEntersANamedState() {
        String states = "\"states\": [{\"name\": \"frenzy\", \"stats\": [{\"stat\": \"speed\", \"percent\": 20}]},"
                + " {\"name\": \"calm\"}]";
        HeroDef d = def(hero2(states + ", \"moves\": [" + LUNGE + ", {\"id\": \"e1\", \"name\": \"E\", \"form\":"
                + " \"self\", \"effects\": [{\"type\": \"empower\", \"seconds\": 6, \"state\": \"frenzy\"}]}]"));
        assertEquals(new HeroDef.Empower(new HeroDef.Common(HeroDef.To.SELF, 8, 100, null), 6, "frenzy"),
                move(d, "e1").ability().effects().get(0));
        Loaded unknown = read(hero2(states + ", \"moves\": [" + LUNGE + ", {\"id\": \"e1\", \"name\": \"E\", \"form\":"
                + " \"self\", \"effects\": [{\"type\": \"empower\", \"seconds\": 6, \"state\": \"rage\"},"
                + " {\"type\": \"cleanse\"}]}]"));
        assertEquals("\"rage\" is not a state of this hero. Its states: frenzy, calm",
                one(unknown, Code.UNKNOWN_REFERENCE).message());
        Loaded which = read(hero2(states + ", \"moves\": [" + LUNGE + ", {\"id\": \"e1\", \"name\": \"E\", \"form\":"
                + " \"self\", \"effects\": [{\"type\": \"empower\", \"seconds\": 6}, {\"type\": \"cleanse\"}]}]"));
        assertTrue(one(which, Code.MISSING).message().startsWith("\"state\" is missing: which state it enters"));
        HeroDef single = def(hero2("\"states\": [{\"name\": \"frenzy\"}], \"moves\": [" + LUNGE + ", {\"id\": \"e1\","
                + " \"name\": \"E\", \"form\": \"self\", \"effects\": [{\"type\": \"empower\", \"seconds\": 6}]}]"));
        assertEquals("frenzy", ((HeroDef.Empower) move(single, "e1").ability().effects().get(0)).state());
        Loaded none = read(withMove("\"id\": \"e1\", \"name\": \"E\", \"form\": \"self\", \"effects\": [{\"type\":"
                + " \"empower\", \"seconds\": 6}, {\"type\": \"cleanse\"}]"));
        assertEquals("\"empower\" needs a state in \"states\" at the top level of the hero",
                one(none, Code.EMPOWER_NO_STATE).message());
    }

    @Test
    void statesReadEveryField() {
        Loaded l = read(hero2("\"states\": [{\"name\": \"twin_sky\", \"description\": \"Two blades.\", \"stats\":"
                + " [{\"stat\": \"attack_speed\", \"percent\": 20}], \"on_hit\": [{\"type\": \"damage\", \"amount\": 1}],"
                + " \"particle\": \"electric_spark\", \"color\": \"#AABBCC\", \"size\": 115, \"skills_locked\": true,"
                + " \"moves\": [\"lunge\"], \"show\": [\"p3_weapon_l\"], \"hide\": [\"fine_scarf\"], \"face\":"
                + " \"determined\", \"line\": \"No more restraint.\"}, {\"name\": \"twin_sky\"}, {\"name\": \"calm\"},"
                + " {\"name\": \"rage\"}]"));
        HeroDef d = l.hero("test_hero");
        HeroDef.State s = d.v2().state("twin_sky");
        assertEquals(List.of(new HeroDef.StatBonus(HeroDef.Stat.ATTACK_SPEED, 20)), s.stats());
        assertEquals(1, s.onHit().size());
        assertEquals("electric_spark", s.particle());
        assertEquals(0xAABBCC, s.color());
        assertEquals(110, s.size(), "at most 110% (2-high tunnels)");
        assertTrue(s.skillsLocked());
        assertEquals(List.of("lunge"), s.moves());
        assertEquals(List.of("p3_weapon_l"), s.show());
        assertEquals(List.of("fine_scarf"), s.hide());
        assertEquals("determined", s.face());
        assertEquals("No more restraint.", s.line());
        assertEquals(Severity.PART_LEFT_OUT, one(l, Code.NAME_TAKEN).severity(), "a repeated name");
        assertTrue(one(l, Code.LIST_TOO_LONG).message().contains("at most 3 items"));
        assertEquals(2, d.v2().states().size());
        Loaded badFace = read(hero2("\"states\": [{\"name\": \"x1\", \"face\": \"smirking\"}]"));
        assertEquals(Severity.ADJUSTED, one(badFace, Code.WORD_DEFAULT).severity());
    }

    // ------------------------------------------------------------------ conditions (6.6, 6.8, MOBILITY 7.5)

    @Test
    void everyNewConditionReadsWithItsDefault() {
        for (ConditionType t : HeroDef.Word.inFormat(ConditionType.class, 2)) {
            if (t.since() < 2) {
                continue;
            }
            String extra = t == ConditionType.SELF_STATE ? ", \"state\": \"calm\"" : "";
            HeroDef d = def(hero2("\"states\": [{\"name\": \"calm\"}], \"moves\": [" + LUNGE.replace("\"form\":"
                    + " \"dash\"", "\"form\": \"dash\", \"prefer_when\": {\"condition\": \"" + t.id() + "\"" + extra
                    + "}") + "]"));
            HeroDef.Condition c = move(d, "lunge").preferWhen();
            assertEquals(t, c.type(), t.id());
            HeroCatalogue.CondValue cv = HeroCatalogue.CONDITION_VALUES.get(t);
            assertEquals(cv == null ? 0 : (int) cv.dflt(), c.value(), t.id());
            if (t == ConditionType.SELF_STATE) {
                assertEquals("calm", c.state());
            }
        }
        HeroDef far = def(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"prefer_when\": {\"condition\": \"target_far\", \"value\": 30}") + "]"));
        assertEquals(24, move(far, "lunge").preferWhen().value(), "clamped to 4-24 blocks");
        Loaded noState = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"prefer_when\": {\"condition\": \"self_state\", \"state\": \"rage\"}") + "]"));
        assertEquals("\"rage\" is not a state of this hero (it has none)", one(noState, Code.UNKNOWN_REFERENCE)
                .message());
        assertNull(move(noState.hero("test_hero"), "lunge").preferWhen(), "the preference is left out, not the move");
        Loaded needs = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"prefer_when\": \"self_health_below\"") + "]"));
        assertEquals(Severity.PART_LEFT_OUT, one(needs, Code.CONDITION_INCOMPLETE).severity());
        assertTrue(one(needs, Code.CONDITION_INCOMPLETE).format().endsWith("Preference PART LEFT OUT."));
    }

    @Test
    void conditionsThatSingledOutPlayersReadAsEveryone() {
        Loaded l = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"prefer_when\": \"target_is_player\"") + ", {\"id\": \"n1\", \"name\": \"N\", \"form\": \"nova\","
                + " \"prefer_when\": {\"condition\": \"players_engaged\", \"value\": 2}, \"effects\": [" + DMG + "]},"
                + " {\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"prefer_when\": {\"condition\": \"target_is\","
                + " \"kind\": \"player\"}, \"effects\": [" + DMG + "]}]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(new HeroDef.Condition(ConditionType.TARGET_IS, 0, null, HeroDef.TargetKind.PERSON, null),
                move(d, "lunge").preferWhen());
        assertEquals(ConditionType.ENEMIES_ENGAGED, move(d, "n1").preferWhen().type());
        assertEquals(2, move(d, "n1").preferWhen().value());
        assertEquals(HeroDef.TargetKind.PERSON, move(d, "m1").preferWhen().kind());
        List<Problem> eq = of(l, Code.EQUAL_TREATMENT);
        assertEquals(3, eq.size(), l.problems().report());
        for (Problem p : eq) {
            assertEquals(Severity.ADJUSTED, p.severity());
            assertTrue(p.message().contains("heroes treat players and NPCs alike"), p.message());
        }
    }

    @Test
    void reactionsReadWhenMoveChanceAndCooldown() {
        Loaded l = read(hero2("\"moves\": [" + LUNGE + "], \"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\":"
                + " \"nova\", \"line\": \"Now!\", \"effects\": [" + DMG + "]}, \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}], \"reactions\": [{\"when\": \"target_eating\", \"move\": \"lunge\"},"
                + " {\"when\": {\"condition\": \"target_far\", \"value\": 12}, \"move\": \"lunge\", \"chance\": 70,"
                + " \"cooldown\": 6}, {\"when\": \"target_blocking\", \"move\": \"big\"}, {\"move\": \"lunge\"},"
                + " {\"when\": \"wobbling\", \"move\": \"lunge\"}]}"));
        HeroDef d = l.hero("test_hero");
        List<HeroDef.Reaction> rs = d.v2().boss().reactions();
        assertEquals(2, rs.size());
        assertEquals(new HeroDef.Reaction(new HeroDef.Condition(ConditionType.TARGET_EATING, 0, null, null, null),
                "lunge", 50, 10), rs.get(0), "chance 50 and cooldown 10 by default (kit-gaps 2)");
        assertEquals(12, rs.get(1).when().value());
        assertEquals(70, rs.get(1).chance());
        assertEquals(6, rs.get(1).cooldown(), EPS);
        assertTrue(one(l, Code.UNKNOWN_REFERENCE).message().contains("an ultimate fires on its energy"));
        assertTrue(one(l, Code.MISSING).message().startsWith("\"when\" is missing"));
        assertEquals(Severity.PART_LEFT_OUT, one(l, Code.WORD_PART).severity());
    }

    // ------------------------------------------------------------------ passives (6.6)

    @Test
    void theTenNewTriggersPairWithTheirForms() {
        for (Trigger t : HeroDef.Word.inFormat(Trigger.class, 2)) {
            if (t.since() < 2) {
                continue;
            }
            HeroCatalogue.TriggerSpec ts = HeroCatalogue.TRIGGERS.get(t);
            assertNotNull(ts, t.id());
            for (HeroDef.PassiveForm f : HeroDef.PassiveForm.values()) {
                String effect = f.friendly() ? "{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10}" : DMG;
                Loaded l = read(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"" + t.id() + "\", \"form\": \""
                        + f.id() + "\", \"once_per_fight\": true, \"cooldown\": 30, \"effects\": [" + effect + "]}]"));
                HeroDef d = l.hero("test_hero");
                if (ts.forms().contains(f)) {
                    assertEquals(1, d.passives().size(), t.id() + "/" + f.id() + "\n" + l.problems().report());
                    assertTrue(d.passives().get(0).oncePerFight());
                } else {
                    assertTrue(d.passives().isEmpty(), t.id() + "/" + f.id());
                    assertEquals(Severity.PART_LEFT_OUT, one(l, Code.TRIGGER_FORM).severity());
                }
            }
        }
        assertEquals(List.of(HeroDef.PassiveForm.SELF), HeroCatalogue.TRIGGERS.get(Trigger.ON_ESCAPE).forms(), "an"
                + " escaping hero starts no attack");
        HeroDef phase = def(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"on_phase\", \"trigger_value\": 3,"
                + " \"effects\": [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10}]}]"));
        assertEquals(3, phase.passives().get(0).triggerValue(), EPS);
        Loaded yield = read(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"on_yield\", \"effects\":"
                + " [{\"type\": \"cleanse\"}]}]"));
        Problem y = one(yield, Code.YIELD_WORD);
        assertTrue(y.message().contains("\"on_escape\"") && y.message().contains("\"on_last_stand\""), y.message());
        assertTrue(y.format().endsWith("Passive PART LEFT OUT."));
        Loaded esc = read(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"on_escape\", \"effects\": [{\"type\":"
                + " \"effect\", \"id\": \"speed\", \"seconds\": 4}, {\"type\": \"heal\", \"amount\": 2, \"to\":"
                + " \"ally\"}]}]"));
        assertEquals(1, esc.hero("test_hero").passives().get(0).effects().size());
        assertEquals(Severity.PART_LEFT_OUT, one(esc, Code.ESCAPE_SELF).severity());
        HeroDef once = def(hero2("\"passives\": [{\"name\": \"P\", \"trigger\": \"on_hit\", \"effects\": [" + DMG
                + "]}]"));
        assertFalse(once.passives().get(0).oncePerFight());
    }

    @Test
    void unlockLevelsAreReadAndIgnored() {
        Loaded l = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"unlock_level\": 10") + "], \"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\","
                + " \"unlock_level\": 25, \"line\": \"Now!\", \"effects\": [" + DMG + "]}, \"passives\": [{\"name\": \"P\","
                + " \"trigger\": \"on_hit\", \"unlock_level\": 5, \"effects\": [" + DMG + "]}], \"boss\": {\"weak_points\":"
                + " [\"back\"], \"phases\": [{\"at\": 100, \"unlock_level\": 15}]}"));
        assertEquals(4, of(l, Code.UNLOCK_IGNORED).size(), l.problems().report());
        HeroDef d = l.hero("test_hero");
        assertEquals(1, move(d, "lunge").ability().unlockLevel());
        assertEquals(1, d.v2().ultimate().ability().unlockLevel());
        assertEquals(1, d.passives().get(0).unlockLevel(), "everything from level 1 (HEROES_V2 8)");
        Loaded unlockLines = read(hero2("\"lines\": {\"unlock\": [\"{ability}!\"]}"));
        assertTrue(one(unlockLines, Code.UNLOCK_IGNORED).message().contains("never fires for a format-2 hero"));
        assertTrue(unlockLines.hero("test_hero").lines().isEmpty());
    }

    // ------------------------------------------------------------------ lines (12.15)

    @Test
    void theNewLineEventsAndTheirPlaceholders() {
        StringBuilder sb = new StringBuilder();
        for (LineEvent e : HeroDef.Word.inFormat(LineEvent.class, 2)) {
            if (e == LineEvent.UNLOCK) {
                continue;
            }
            List<String> ph = HeroCatalogue.PLACEHOLDERS_2.get(e);
            assertNotNull(ph, e.id());
            sb.append(sb.isEmpty() ? "" : ", ").append("\"").append(e.id()).append("\": [\"{")
                    .append(String.join("} {", ph)).append("}\"]");
        }
        Loaded l = read(hero2("\"lines\": {" + sb + "}"));
        assertTrue(l.problems().all().isEmpty(), l.problems().report());
        assertEquals(HeroDef.Word.inFormat(LineEvent.class, 2).size() - 1, l.hero("test_hero").lines().size());
        Loaded fight = read(hero2("\"lines\": {\"intro\": [\"{player}, draw.\"], \"escape\": [\"Later, {Player}.\"],"
                + " \"greeting\": [\"Hi {player}.\"], \"kill\": [\"Down, {player}.\"]}"));
        HeroDef d = fight.hero("test_hero");
        assertEquals(List.of("{enemy}, draw."), d.lines().get(LineEvent.INTRO));
        assertEquals(List.of("Later, {enemy}."), d.lines().get(LineEvent.ESCAPE));
        assertEquals(List.of("Down, {enemy}."), d.lines().get(LineEvent.KILL));
        assertEquals(List.of("Hi {player}."), d.lines().get(LineEvent.GREETING), "a greeting is not a fight line");
        assertEquals(3, of(fight, Code.EQUAL_TREATMENT).size());
        // fallen names its enemy in format 2 (in format 1 it reads "someone")
        assertEquals(List.of("{enemy} won."), def(hero2("\"lines\": {\"fallen\": [\"{enemy} won.\"]}")).lines()
                .get(LineEvent.FALLEN));
        Loaded yield = read(hero2("\"lines\": {\"yield\": [\"Mercy!\"], \"spared\": [\"Thank you.\"]}"));
        assertEquals(2, of(yield, Code.YIELD_EVENT).size());
        assertTrue(yield.hero("test_hero").lines().isEmpty());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            many.append(i == 0 ? "" : ", ").append("\"Line ").append(i).append("\"");
        }
        Loaded limit = read(hero2("\"lines\": {\"kill\": [" + many + "]}"));
        assertEquals(12, limit.hero("test_hero").lines().get(LineEvent.KILL).size());
        assertEquals("an event has at most 12 lines", one(limit, Code.LIST_TOO_LONG).message());
        assertEquals(List.of("Ah, {item}."), def(hero2("\"lines\": {\"gift_liked\": [\"Ah, {item}.\"]}")).lines()
                .get(LineEvent.GIFT_LIKED));
        // the boss block's lines follow the same rules
        HeroDef boss = def(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"intro\":"
                + " \"{player}. Draw.\", \"enrage\": {\"line\": \"Enough, {player}!\"}}"));
        assertEquals("{enemy}. Draw.", boss.v2().boss().intro().line());
        assertEquals("Enough, {enemy}!", boss.v2().boss().enrage().line());
    }

    // ------------------------------------------------------------------ followups (6.3, kit-gaps 1)

    @Test
    void followupStringsAreAtMostFourMovesAndNeverLoop() {
        // a repeatable move follows itself; max caps the repeats; the string limit counts every move
        HeroDef d = def(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"repeatable\":"
                + " true, \"followups\": [{\"move\": \"lunge\", \"chance\": 100, \"max\": 2}]") + "]"));
        assertEquals(List.of(new HeroDef.Followup("lunge", 100, 2)), move(d, "lunge").followups());
        HeroDef four = def(hero2("\"moves\": [" + chainMove("a1", "b1") + ", " + chainMove("b1", "c1") + ", "
                + chainMove("c1", "d1") + ", " + chainMove("d1", null) + "]"));
        assertEquals(1, move(four, "c1").followups().size(), "a string of exactly 4 is fine");
        Loaded five = read(hero2("\"moves\": [" + chainMove("a1", "b1") + ", " + chainMove("b1", "c1") + ", "
                + chainMove("c1", "d1") + ", " + chainMove("d1", "e1") + ", " + chainMove("e1", null) + "]"));
        assertTrue(move(five.hero("test_hero"), "d1").followups().isEmpty(), "the 5th move's link is left out");
        assertEquals(Severity.PART_LEFT_OUT, one(five, Code.FOLLOWUP_STRING).severity());
        Loaded loop = read(hero2("\"moves\": [" + chainMove("a1", "b1") + ", " + chainMove("b1", "c1") + ", "
                + chainMove("c1", "a1") + "]"));
        assertTrue(one(loop, Code.FOLLOWUP_LOOP).message().startsWith("followups may not form a loop (\"c1\" -> \"a1\""));
        assertTrue(move(loop.hero("test_hero"), "c1").followups().isEmpty());
        Loaded selfNot = read(hero2("\"moves\": [" + chainMove("a1", "a1") + "]"));
        assertEquals(Severity.PART_LEFT_OUT, one(selfNot, Code.FOLLOWUP_SELF).severity());
        Loaded unknown = read(hero2("\"moves\": [" + chainMove("a1", "zz") + "]"));
        assertEquals("\"zz\" is not a move of this hero", one(unknown, Code.UNKNOWN_REFERENCE).message());
        Loaded maxOther = read(hero2("\"moves\": [" + LUNGE + ", {\"id\": \"a1\", \"name\": \"A\", \"form\": \"dash\","
                + " \"effects\": [" + DMG + "], \"followups\": [{\"move\": \"lunge\", \"max\": 2}]}]"));
        assertTrue(one(maxOther, Code.FIELD_UNUSED).message().startsWith("\"max\" caps how often a move repeats"));
        assertEquals(0, move(maxOther.hero("test_hero"), "a1").followups().get(0).max());
        // a followup string may start from the ultimate
        HeroDef ult = def(hero2("\"moves\": [" + LUNGE + "], \"ultimate\": {\"id\": \"big\", \"name\": \"Big\","
                + " \"form\": \"self\", \"line\": \"Now!\", \"effects\": [{\"type\": \"cleanse\"}], \"followups\":"
                + " [\"lunge\"]}"));
        assertEquals("lunge", ult.v2().ultimate().followups().get(0).move());
    }

    private static String chainMove(String id, String to) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"form\": \"dash\", \"effects\": [" + DMG + "]"
                + (to == null ? "" : ", \"followups\": [\"" + to + "\"]") + "}";
    }

    @Test
    void weightZeroMovesNeedSomethingThatLeadsToThem() {
        HeroDef d = def(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"followups\":"
                + " [\"finish\"]") + ", {\"id\": \"finish\", \"name\": \"F\", \"form\": \"melee\", \"weight\": 0,"
                + " \"effects\": [" + DMG + "]}]"));
        assertEquals(0, move(d, "finish").weight());
        HeroDef byReaction = def(hero2("\"moves\": [" + LUNGE + ", {\"id\": \"finish\", \"name\": \"F\", \"form\":"
                + " \"melee\", \"weight\": 0, \"effects\": [" + DMG + "]}], \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}], \"reactions\": [{\"when\": \"target_eating\", \"move\": \"finish\"}]}"));
        assertNotNull(byReaction);
    }

    // ------------------------------------------------------------------ the boss block (12.10)

    @Test
    void theBossBlockReadsEveryField() {
        Loaded l = read(hero2("\"rarity\": \"legendary\", \"states\": [{\"name\": \"rage\"}], \"boss\": {\"health_scale\":"
                + " 500, \"max_hit_share\": 10, \"poise\": 150, \"weak_points\": [{\"point\": \"water\", \"hint\":"
                + " \"Not water!\"}, \"iron\", \"back\"], \"phases\": [{\"at\": 100}, {\"at\": 60, \"states\":"
                + " [\"rage\"], \"stats\": [{\"stat\": \"armor\", \"percent\": 20}], \"adds\": [\"decoy\"],"
                + " \"transition\": {\"seconds\": 1.6, \"line\": \"Phase two.\", \"shockwave\": {\"radius\": 6,"
                + " \"strength\": 3}, \"effects\": [{\"type\": \"heal\", \"amount\": 2}], \"face\": \"angry\"}},"
                + " {\"at\": 30}], \"enrage\": {\"after\": 200, \"triggers\": [\"ally_hero_died\", \"heart_attacked\"],"
                + " \"line\": \"Enough!\"}, \"adds\": [{\"id\": \"decoy\", \"count\": 2}], \"intro\": {\"line\":"
                + " \"Begin.\", \"title\": \"the Test\"}, \"rebirth\": true, \"arena\": {\"prefers\": \"tight\"}}"));
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, l.problems().report());
        HeroDef.Boss b = d.v2().boss();
        assertEquals(500, b.healthScale());
        assertEquals(10, b.maxHitShare());
        assertEquals(150, b.poise());
        assertEquals(List.of(new HeroDef.WeakSpot(HeroDef.WeakPoint.WATER, "Not water!"),
                new HeroDef.WeakSpot(HeroDef.WeakPoint.IRON, null), new HeroDef.WeakSpot(HeroDef.WeakPoint.BACK, null)),
                b.weakPoints());
        assertEquals(List.of(100, 60, 30), b.phases().stream().map(HeroDef.Phase::at).toList());
        HeroDef.Phase two = b.phases().get(1);
        assertEquals(List.of("rage"), two.states());
        assertEquals(List.of(new HeroDef.StatBonus(HeroDef.Stat.ARMOR, 20)), two.stats());
        assertEquals(List.of(new HeroDef.AddSpec(HeroDef.AddKind.DECOY, 1)), two.adds());
        HeroDef.Transition t = two.transition();
        assertEquals(1.6, t.seconds(), EPS);
        assertEquals("Phase two.", t.line());
        assertEquals(6, t.shockRadius(), EPS);
        assertEquals(3, t.shockStrength(), EPS);
        assertEquals(1, t.effects().size());
        assertEquals("angry", t.face());
        assertEquals(HeroDef.Transition.DEFAULT, b.phases().get(2).transition());
        assertEquals(new HeroDef.Enrage(200, List.of(HeroDef.EnrageTrigger.ALLY_HERO_DIED,
                HeroDef.EnrageTrigger.HEART_ATTACKED), "Enough!"), b.enrage());
        assertEquals(List.of(new HeroDef.AddSpec(HeroDef.AddKind.DECOY, 2)), b.adds());
        assertEquals(new HeroDef.Intro("Begin.", "the Test"), b.intro());
        assertTrue(b.rebirth(), "a legendary hero may have a rebirth");
        assertEquals(HeroDef.Arena.TIGHT, b.arena());
        assertEquals(30, b.lastPhaseAt());
    }

    @Test
    void phasesBeginAtFullHealthAndGoDown() {
        Loaded l = read(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 40}, {\"at\": 95}, {\"at\":"
                + " 70}]}, \"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"phases\": [1]")
                + ", " + chainMove("m2", null).replace("\"form\": \"dash\"", "\"form\": \"dash\", \"phases\": [2]")
                + ", " + chainMove("m3", null).replace("\"form\": \"dash\"", "\"form\": \"dash\", \"phases\": [3]")
                + "]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(List.of(100, 70, 40), d.v2().boss().phases().stream().map(HeroDef.Phase::at).toList());
        assertEquals(2, of(l, Code.PHASE_ORDER).size(), () -> "sorted, and the first read as 100\n"
                + l.problems().report());
        Loaded clamp = read(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 0}]}"));
        assertEquals(1, clamp.hero("test_hero").v2().boss().phases().get(1).at());
        Loaded unknown = read(hero2("\"moves\": [" + LUNGE + "], \"boss\": {\"weak_points\": [\"back\"], \"phases\":"
                + " [{\"at\": 100, \"moves\": [\"lunge\", \"ghost\"], \"states\": [\"none_here\"]}]}"));
        assertEquals(2, of(unknown, Code.UNKNOWN_REFERENCE).size(), () -> unknown.problems().report());
        assertEquals(List.of("lunge"), unknown.hero("test_hero").v2().boss().phases().get(0).moves());
        // a move's phases: its own, else the phases that list it, else all
        HeroDef listed = def(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100, \"moves\":"
                + " [\"lunge\"]}, {\"at\": 50, \"moves\": [\"lunge\", \"m2\"]}]}, \"moves\": [" + LUNGE + ", "
                + chainMove("m2", null) + "]"));
        assertEquals(List.of(1, 2), move(listed, "lunge").phases());
        assertEquals(List.of(2), move(listed, "m2").phases());
        Loaded none = read(hero2("\"moves\": [" + LUNGE + ", " + chainMove("m2", null).replace("\"form\": \"dash\"",
                "\"form\": \"dash\", \"phases\": [3]") + "]"));
        assertTrue(one(none, Code.NEVER_PICKED).message().startsWith("the move is in no phase this hero has"));
        Loaded badPhase = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\","
                + " \"phases\": [0, 2, \"x\"]") + "]"));
        assertEquals(1, of(badPhase, Code.CLAMPED).size());
        assertEquals(1, of(badPhase, Code.WRONG_TYPE).size());
    }

    @Test
    void theBossChecksOfFourteenOne() {
        // a weakness: a weak point or a resistance of -10 or lower
        assertTrue(read(hero2("\"resistances\": {\"fire\": -10}, \"boss\": {\"phases\": [{\"at\": 100}]}")).problems()
                .all().isEmpty());
        assertEquals(1, of(read(hero2("\"resistances\": {\"fire\": -9}, \"boss\": {\"phases\": [{\"at\": 100}]}")),
                Code.NO_WEAKNESS).size());
        // reach: a far-band move or a gap closer
        assertTrue(read(hero2("\"moves\": [{\"id\": \"s1\", \"name\": \"S\", \"form\": \"shot\", \"range\": 20,"
                + " \"effects\": [" + DMG + "]}]")).problems().all().isEmpty(), "a 20-block shot is far");
        // poise 0 needs another weakness; 1-49 is not a poise
        Loaded zero = read(hero2("\"boss\": {\"poise\": 0, \"phases\": [{\"at\": 100}]}"));
        assertEquals(100, zero.hero("test_hero").v2().boss().poise());
        assertTrue(one(zero, Code.POISE_RANGE).message().contains("needs another weakness"));
        assertEquals(0, def(hero2("\"boss\": {\"poise\": 0, \"weak_points\": [\"back\"], \"phases\": [{\"at\":"
                + " 100}]}")).v2().boss().poise());
        Loaded low = read(hero2("\"boss\": {\"poise\": 20, \"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}]}"));
        assertEquals(50, low.hero("test_hero").v2().boss().poise());
        // rebirth only for legendary heroes
        assertFalse(read(hero2("\"boss\": {\"rebirth\": true, \"weak_points\": [\"back\"], \"phases\": [{\"at\":"
                + " 100}]}")).hero("test_hero").v2().boss().rebirth());
        // ranges
        Loaded range = read(hero2("\"boss\": {\"health_scale\": 900, \"max_hit_share\": 40, \"weak_points\":"
                + " [\"back\", \"back\"], \"phases\": [{\"at\": 100}]}"));
        HeroDef.Boss b = range.hero("test_hero").v2().boss();
        assertEquals(600, b.healthScale());
        assertEquals(25, b.maxHitShare());
        assertEquals(1, b.weakPoints().size());
        assertEquals(1, of(range, Code.LIST_DUPLICATE).size());
        // a channel weak point needs a channelled move
        assertTrue(read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"channel\":"
                + " 15") + "], \"boss\": {\"weak_points\": [\"channel\"], \"phases\": [{\"at\": 100}]}")).problems()
                .all().isEmpty());
    }

    @Test
    void theRemovedFieldsAreReadAndIgnoredEachWithItsNote() {
        Map<String, String> removed = Map.of(
                "damage_vs_players", "read and ignored: a hero deals the same damage to players and NPCs",
                "defeat", "read and ignored: heroes do not yield; set \"personality.when_losing\" to choose between"
                        + " escaping and a last stand",
                "attack_tokens", "read and ignored: any number of heroes may press one target",
                "tokens", "read and ignored: any number of heroes may press one target",
                "health_per_player", "read and ignored: boss health does not change with the number of attackers");
        for (Map.Entry<String, String> e : removed.entrySet()) {
            Loaded l = read(hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"" + e.getKey()
                    + "\": {\"yield\": true, \"value\": 2}}"));
            Problem p = one(l, Code.REMOVED_FIELD);
            assertEquals(e.getValue(), p.message(), e.getKey());
            assertEquals(Severity.NOTE, p.severity());
            assertEquals("boss > " + e.getKey(), p.path());
            assertEquals(1, l.problems().all().size(), "nothing else is reported: " + l.problems().report());
        }
        Loaded hs = read(hero2("\"boss\": {\"health_scale\": {\"base\": 380, \"players\": 50}, \"weak_points\":"
                + " [\"back\"], \"phases\": [{\"at\": 100}]}"));
        assertEquals(380, hs.hero("test_hero").v2().boss().healthScale());
        assertEquals("read and ignored: boss health does not change with the number of attackers",
                one(hs, Code.REMOVED_FIELD).message());
    }

    // ------------------------------------------------------------------ the end of a fight (6.13), personality (12.11)

    @Test
    void escapeOrLastStandFromTheTraitsTheOverrideAndTheTemperament() {
        Object[][] cases = {
                // temperament, traits, when_losing, expected kind
                {"battle_maniac", "", null, WhenLosing.LAST_STAND},
                {"schemer", "", null, WhenLosing.ESCAPE},
                {"stoic", "", null, WhenLosing.LAST_STAND},
                {"sleepy", "", null, WhenLosing.LAST_STAND},
                {"shy", "", null, WhenLosing.ESCAPE},
                {"tsundere", "", "\"escape\"", WhenLosing.ESCAPE},
                {"schemer", "", "\"stand\"", WhenLosing.LAST_STAND},
                {"righteous", "", "\"flee\"", WhenLosing.ESCAPE},
        };
        for (Object[] c : cases) {
            HeroDef d = def(hero2("\"personality\": {\"temperament\": \"" + c[0] + "\"" + (c[2] == null ? ""
                    : ", \"when_losing\": " + c[2]) + "}"));
            assertEquals(c[3], d.v2().end().kind(), c[0] + " " + c[2]);
        }
        for (String y : List.of("yield", "kneel", "surrender")) {
            Loaded l = read(hero2("\"personality\": {\"temperament\": \"schemer\", \"when_losing\": \"" + y + "\"}"));
            assertTrue(one(l, Code.YIELD_WORD).format().endsWith("When losing PART LEFT OUT."), y);
            assertEquals(WhenLosing.ESCAPE, l.hero("test_hero").v2().end().kind(), "the default applies: " + y);
        }
        assertEquals(WhenLosing.LAST_STAND, def(hero2("").replace("\"striker\"}", "\"striker\", \"retreat_below\":"
                + " 0}")).v2().end().kind(), "an authored retreat_below of 0: never retreats");
        Loaded syn = read(hero2("\"personality\": {\"when_losing\": \"fight_to_death\"}"));
        assertEquals("\"fight_to_death\" was read as \"last_stand\"", one(syn, Code.SYNONYM).message());
    }

    @Test
    void whereAnEscapingHeroStandsAnyway() {
        HeroDef home = def(hero2("\"personality\": {\"temperament\": \"sweet_natured\"}"));
        assertEquals(List.of(HeroDef.StandsFor.HOME), home.v2().end().standsFor(), "loyalty 80: home");
        HeroDef both = def(hero2("\"personality\": {\"temperament\": \"gentle_giant\"}"));
        assertEquals(List.of(HeroDef.StandsFor.HOME, HeroDef.StandsFor.LEADER), both.v2().end().standsFor(), "85: both");
        HeroDef nowhere = def(hero2("\"personality\": {\"temperament\": \"sweet_natured\", \"stands_for\": []}"));
        assertTrue(nowhere.v2().end().standsFor().isEmpty(), "[] is nowhere");
        HeroDef leader = def(hero2("\"personality\": {\"temperament\": \"schemer\", \"stands_for\": [\"leader\","
                + " \"leader\"]}"));
        assertEquals(List.of(HeroDef.StandsFor.LEADER), leader.v2().end().standsFor());
        Loaded ignored = read(hero2("\"personality\": {\"temperament\": \"righteous\", \"stands_for\": [\"home\"]}"));
        assertEquals(Severity.NOTE, one(ignored, Code.STANDS_IGNORED).severity());
        assertTrue(ignored.hero("test_hero").v2().end().standsFor().isEmpty());
        // sweet-natured: aggression 25, caution 50 move a striker's 25 to 27.5, read as 28; a third is 9
        assertEquals("escapes under 9%, never from its home", home.v2().end().sheetLine());
        // gentle giant: aggression 20, caution 60: 25 + 2 + 3 = 30, a third is 10
        assertEquals("escapes under 10%, never from its home or from beside its leader", both.v2().end().sheetLine());
        assertEquals("fights to the death", def(hero2("\"personality\": {\"temperament\": \"stoic\"}")).v2().end()
                .sheetLine());
    }

    @Test
    void theEscapeThresholdComesFromRetreatBelowAndTheLastPhase() {
        // a marksman's 40 moved by caution 70 and aggression 45 (Yugen): 44.5 rounds to 45, a third is 15
        HeroDef yugen = def(hero2("\"personality\": {\"temperament\": \"schemer\"}").replace("\"striker\"",
                "\"marksman\""));
        assertEquals(45, yugen.behaviour().retreatBelow());
        assertEquals(15, yugen.v2().end().fleeAt());
        // a last phase at 20 caps it at 10
        HeroDef late = def(hero2("\"personality\": {\"temperament\": \"schemer\"}, \"boss\": {\"weak_points\":"
                + " [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 20, \"states\": [\"s1\"]}]}, \"states\": [{\"name\":"
                + " \"s1\"}]").replace("\"striker\"", "\"marksman\""));
        assertEquals(10, late.v2().end().fleeAt());
        Loaded tooLate = read(hero2("\"personality\": {\"temperament\": \"schemer\"}, \"boss\": {\"weak_points\":"
                + " [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 12, \"states\": [\"s1\"]}]}, \"states\": [{\"name\":"
                + " \"s1\"}]"));
        assertEquals(5, tooLate.hero("test_hero").v2().end().fleeAt(), "never under 5");
        assertEquals(Severity.NOTE, one(tooLate, Code.LATE_LAST_PHASE).severity());
        HeroDef authored = def(hero2("").replace("\"striker\"}", "\"striker\", \"retreat_below\": 45}"));
        assertEquals(15, authored.v2().end().fleeAt(), "an authored retreat_below wins");
    }

    @Test
    void traitsStayNearTheirTemperamentAndDriveTheFightDefaults() {
        Loaded l = read(hero2("\"personality\": {\"temperament\": \"tsundere\", \"traits\": {\"aggression\": 100,"
                + " \"honor\": 50, \"wit\": 3, \"caution\": 45}}"));
        HeroDef d = l.hero("test_hero");
        HeroDef.Personality p = d.v2().personality();
        assertEquals(85, p.trait(HeroDef.Trait.AGGRESSION), "65 + 20");
        assertEquals(50, p.trait(HeroDef.Trait.HONOUR), "\"honor\" read as \"honour\"");
        assertEquals(60, p.trait(HeroDef.Trait.AMBITION), "the temperament's");
        assertEquals("aggression 100 is more than 20 from a tsundere's 65 (so the temperament stays true). Set to 85",
                one(l, Code.TRAIT_DRIFT).message());
        assertEquals(Severity.PART_LEFT_OUT, one(l, Code.WORD_PART).severity(), "\"wit\" is no trait");
        assertEquals(HeroDef.Aggression.BOLD, d.behaviour().aggression(), "85: bold (5.4)");
        assertEquals(HeroDef.Focus.WEAKEST, d.behaviour().focus(), "a tsundere: the role's focus");
        assertEquals(HeroDef.Focus.STRONGEST, def(hero2("\"personality\": {\"temperament\": \"battle_maniac\"}"))
                .behaviour().focus());
        assertEquals(HeroDef.Focus.HEROES_FIRST, def(hero2("\"personality\": {\"temperament\": \"hot_blooded\"}"))
                .behaviour().focus());
        assertEquals(HeroDef.Focus.WEAKEST, def(hero2("\"personality\": {\"temperament\": \"trickster\"}").replace(
                "\"striker\"", "\"vanguard\"")).behaviour().focus());
        assertEquals(HeroDef.Aggression.CAUTIOUS, def(hero2("\"personality\": {\"temperament\": \"shy\"}")).behaviour()
                .aggression());
        HeroDef authored = def(hero2("\"personality\": {\"temperament\": \"shy\"}").replace("\"striker\"}",
                "\"striker\", \"aggression\": \"bold\", \"focus\": \"nearest\"}"));
        assertEquals(HeroDef.Aggression.BOLD, authored.behaviour().aggression(), "the file wins");
        assertEquals(HeroDef.Focus.NEAREST, authored.behaviour().focus());
        HeroDef pers = def(hero2("\"personality\": {\"likes\": [\"minecraft:bamboo\", \"cherry_sapling\"], \"dislikes\":"
                + " [\"rotten_flesh\"], \"quirks\": [\"hums\", \"" + "q".repeat(130) + "\"]}"));
        assertEquals(List.of("bamboo", "cherry_sapling"), pers.v2().personality().likes());
        assertEquals(120, pers.v2().personality().quirks().get(1).length());
    }

    @Test
    void theTraitsFlippingTheTemperamentsKindIsANote() {
        Loaded flip = read(hero2("\"personality\": {\"temperament\": \"stoic\", \"traits\": {\"caution\": 70}}"));
        assertEquals("a stoic usually fights to the death; with these traits this one escapes (aggression 50 + honour"
                + " 80 - caution 70 = 60; 65 or more is a last stand)", one(flip, Code.KIND_FLIP).message());
        assertEquals(WhenLosing.ESCAPE, flip.hero("test_hero").v2().end().kind());
        Loaded withOverride = read(hero2("\"personality\": {\"temperament\": \"stoic\", \"traits\": {\"caution\": 70},"
                + " \"when_losing\": \"last_stand\"}"));
        assertTrue(one(withOverride, Code.KIND_FLIP).message().endsWith("although its \"when_losing\" decides"));
        assertTrue(read(hero2("\"personality\": {\"temperament\": \"stoic\"}")).problems().all().isEmpty());
    }

    @Test
    void behaviourAddsTheDuelStyleAndRefusesTheCourtDuty() {
        HeroDef d = def(hero2("").replace("\"striker\"}", "\"striker\", \"duel_style\": \"all-rounder\", \"duty\":"
                + " \"miner\"}"));
        assertEquals(HeroDef.DuelStyle.ALL_ROUNDER, d.behaviour().duelStyle());
        assertEquals("miner", d.behaviour().duty());
        Loaded court = read(hero2("").replace("\"striker\"}", "\"striker\", \"duty\": \"Court\"}"));
        assertEquals("guard", court.hero("test_hero").behaviour().duty());
        assertEquals(Severity.ADJUSTED, one(court, Code.COURT_DUTY).severity());
        assertEquals(4, def(hero2("").replace("\"melee\"}", "\"melee\", \"combo\": 4}")).basicAttack().combo());
        assertEquals(4, def(hero2("").replace("\"melee\"}", "\"melee\", \"combo\": 7}")).basicAttack().combo());
    }

    // ------------------------------------------------------------------ leader, talents, voice, ... (12.12-12.16)

    @Test
    void theLeaderBlock() {
        Loaded l = read(hero2("\"leader\": {\"eligible\": false, \"title\": \"Lord of Tests\", \"faction_names\":"
                + " [\"House of Tests\", \"Test Watch\"], \"short_name\": \"Tests\", \"motto\": \"We test.\","
                + " \"colors\": {\"primary\": \"#2E3A6B\", \"secondary\": \"E8E4D8\", \"accent\": \"grey\"},"
                + " \"banner\": {\"base\": \"blue\", \"patterns\": [{\"pattern\": \"circle\", \"color\": \"white\"},"
                + " {\"pattern\": \"border\", \"color\": \"light grey\"}]}, \"base_style\": \"deepslate_keep\","
                + " \"culture\": \"east\", \"economy_focus\": [\"military\", \"defense\", \"mining\"], \"seat\": \"Hall\"}"));
        HeroDef.Leader ld = l.hero("test_hero").v2().leader();
        assertFalse(ld.eligible());
        assertEquals("Lord of Tests", ld.title());
        assertEquals(List.of("House of Tests", "Test Watch"), ld.factionNames());
        assertEquals("Tests", ld.shortName());
        assertEquals("We test.", ld.motto());
        assertEquals(0x2E3A6B, ld.primary());
        assertEquals(0xE8E4D8, ld.secondary());
        assertEquals(HeroDef.NO_COLOR, ld.accent());
        assertEquals(new HeroDef.Banner(HeroDef.Dye.BLUE, List.of(new HeroDef.BannerLayer("circle", HeroDef.Dye.WHITE),
                new HeroDef.BannerLayer("border", HeroDef.Dye.LIGHT_GRAY))), ld.banner());
        assertEquals(HeroDef.BaseStyle.DEEPSLATE_KEEP, ld.baseStyle());
        assertEquals("Keep", ld.baseStyle().seat);
        assertEquals(HeroDef.Culture.EAST, ld.culture());
        assertEquals(List.of(HeroDef.EconomyFocus.MILITARY, HeroDef.EconomyFocus.DEFENCE), ld.economyFocus());
        assertEquals("Hall", ld.seat());
        assertEquals(1, of(l, Code.COLOR_INVALID).size());
        assertEquals(2, of(l, Code.SYNONYM).size(), "light grey and defense");
        assertEquals(1, of(l, Code.LIST_TOO_LONG).size(), "economy_focus holds 2");
        Loaded patterns = read(hero2("\"leader\": {\"banner\": {\"base\": \"red\", \"patterns\": [{\"pattern\":"
                + " \"mojang\", \"color\": \"black\"}, {\"pattern\": \"globe\", \"color\": \"white\"}, {\"pattern\":"
                + " \"swirl\", \"color\": \"white\"}]}}"));
        assertEquals(1, patterns.hero("test_hero").v2().leader().banner().patterns().size());
        assertEquals(1, of(patterns, Code.BANNER_MOJANG).size());
        assertEquals(1, of(patterns, Code.LEADER_SHORT).size(), "fewer than 2 layers left");
        assertEquals(Severity.PART_LEFT_OUT, one(patterns, Code.WORD_PART).severity());
        assertEquals(42, HeroCatalogue.BANNER_PATTERNS.size());
    }

    @Test
    void talents() {
        Loaded l = read(hero2("\"talents\": [\"drill\", {\"id\": \"mentor\", \"realm\": true}, {\"id\": \"aura\","
                + " \"affix\": \"prospector\", \"roles\": [\"miner\"], \"realm\": true}, \"lookout\"]"));
        List<HeroDef.Talent> t = l.hero("test_hero").v2().talents();
        assertEquals(List.of(new HeroDef.Talent(HeroDef.TalentId.DRILL, false, null, List.of()),
                new HeroDef.Talent(HeroDef.TalentId.MENTOR, true, null, List.of()),
                new HeroDef.Talent(HeroDef.TalentId.AURA, false, "prospector", List.of("miner"))), t);
        assertEquals(1, of(l, Code.REALM_ONE).size());
        assertEquals(1, of(l, Code.LIST_TOO_LONG).size(), "3 talents");
        Loaded aura = read(hero2("\"talents\": [\"aura\", {\"id\": \"aura\", \"affix\": \"vitality\"}]"));
        assertEquals(1, of(aura, Code.MISSING).size(), "a bare aura has no affix");
        assertEquals(1, of(aura, Code.AURA_AFFIX).size());
        Loaded drill = read(hero2("\"talents\": [{\"id\": \"drill\", \"affix\": \"hardy\"}, \"drill\"]"));
        assertEquals(1, of(drill, Code.FIELD_UNUSED).size(), "affix on a drill");
        assertEquals(1, of(drill, Code.LIST_DUPLICATE).size());
        assertEquals(1, drill.hero("test_hero").v2().talents().size());
        assertEquals(35, HeroDef.TalentId.values().length);
        long live = java.util.Arrays.stream(HeroDef.TalentId.values()).filter(x -> x.milestone == 1).count();
        assertEquals(15, live, "HEROES_V2 7.2: the 15 milestone-1 talents");
        HeroDef aura2 = def(hero2("\"talents\": [{\"id\": \"aura\", \"affix\": \"angler\"}]"));
        assertEquals(HeroCatalogue.ROLE_IDS, aura2.v2().talents().get(0).roles(), "every role by default");
    }

    @Test
    void voiceRecruitRelationshipsAndRevival() {
        assertEquals(new HeroDef.Voice(HeroDef.Blip.HARP, 1.25, 3, 1.25), def(hero2("\"gender\": \"female\"")).v2()
                .voice());
        assertEquals(new HeroDef.Voice(HeroDef.Blip.BASS, 0.9, 3, 0.9), def(hero2("\"gender\": \"m\"")).v2().voice());
        Loaded voice = read(hero2("\"voice\": {\"blip\": \"alto\", \"pitch\": 3, \"rate\": 1, \"hurt_pitch\": 0.8}"));
        assertEquals(new HeroDef.Voice(HeroDef.Blip.HARP, 2, 2, 0.8), voice.hero("test_hero").v2().voice());
        assertEquals(2, of(voice, Code.CLAMPED).size());
        assertEquals(1, of(voice, Code.WORD_DEFAULT).size());
        assertEquals(new HeroDef.Recruit(HeroDef.RecruitCondition.DUEL, 0), def(hero2("\"recruit\": {\"condition\":"
                + " \"duel\"}")).v2().recruit());
        Loaded rel = read(hero2("\"relationships\": [{\"hero\": \"Other Hero\", \"kind\": \"rival\"}, {\"hero\":"
                + " \"other_hero\", \"kind\": \"friend\"}, {\"hero\": \"test_hero\", \"kind\": \"friend\"}, {\"hero\":"
                + " \"x\", \"kind\": \"friend\"}, {\"hero\": \"third_one\", \"kind\": \"lover\"}]"));
        assertEquals(List.of(new HeroDef.Relationship("other_hero", HeroDef.Bond.RIVAL)), rel.hero("test_hero").v2()
                .relationships());
        assertEquals(1, of(rel, Code.LIST_DUPLICATE).size());
        assertEquals(1, of(rel, Code.RELATION_SELF).size());
        assertEquals(1, of(rel, Code.WRONG_TYPE).size(), "\"x\" is no hero id");
        assertEquals(1, of(rel, Code.LIST_TOO_LONG).size(), "4 at most");
        assertEquals(1, of(rel, Code.RELATION_UNKNOWN).size(), "other_hero is not loaded");
        Loaded both = HeroReader.readAll(List.of(new Source("test_hero.json", hero2("\"relationships\": [{\"hero\":"
                + " \"abc_hero\", \"kind\": \"sibling\"}]")), new Source("abc_hero.json", hero2("").replace("test_hero",
                "abc_hero"))), Options.DEFAULT);
        assertTrue(both.problems().all().isEmpty(), both.problems().report());
        assertEquals(new HeroDef.Revival(120, HeroDef.RiseWhere.WHERE_IT_FELL, HeroDef.RiseAt.NIGHT, "Again."),
                def(hero2("\"revival\": {\"seconds\": 120, \"where\": \"where_it_fell\", \"at\": \"night\", \"line\":"
                        + " \"Again.\"}")).v2().revival());
        Loaded rev = read(hero2("\"revival\": {\"seconds\": 5}"));
        assertEquals(new HeroDef.Revival(30, HeroDef.RiseWhere.HOME, HeroDef.RiseAt.ANY, null), rev.hero("test_hero")
                .v2().revival(), "never under 30 s");
        assertEquals(600, def(hero2("\"revival\": {\"where\": \"nearest_base\"}")).v2().revival().seconds());
    }

    @Test
    void builtInHeroesNeedTheirIdentity() {
        Loaded l = HeroReader.read("test_hero.json", hero2(""), Options.BUILTIN);
        assertEquals(4, of(l, Code.BUILTIN_MISSING).size(), "gender, archetype, personality, leader");
        assertTrue(read(hero2("")).problems().all().isEmpty(), "an override need not repeat them");
        HeroDef d = def(hero2("\"gender\": \"female\", \"archetype\": \"cat\", \"hybrid\": \"fox\""));
        assertEquals(HeroDef.Gender.FEMALE, d.v2().gender());
        assertEquals("cat", d.v2().archetype());
        assertEquals("fox", d.v2().hybrid());
        Loaded same = read(hero2("\"archetype\": \"cat\", \"hybrid\": \"cat\""));
        assertNull(same.hero("test_hero").v2().hybrid());
        assertEquals(54, HeroCatalogue.ARCHETYPES.size());
    }

    // ------------------------------------------------------------------ mobility (MOBILITY_AND_WORK 8)

    @Test
    void theMobilityBlockAndItsDefaults() {
        HeroDef d = def(hero2("\"rarity\": \"epic\", \"element\": \"frost\", \"mobility\": {\"primary\": {\"trait\":"
                + " \"charger\", \"tier\": 3}, \"secondary\": {\"trait\": \"snowstride\", \"tier\": 1}, \"travel\": false,"
                + " \"avoid\": [\"lava\", \"heights\"]}"));
        HeroDef.Mobility m = d.v2().mobility();
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.CHARGER, 3), m.primary());
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.SNOWSTRIDE, 1), m.secondary());
        assertFalse(m.travel());
        assertEquals(List.of(HeroDef.Avoid.LAVA, HeroDef.Avoid.HEIGHTS), m.avoid());
        // archetype defaults (8.2): the first allowed primary at tier 1, the flight carrier and aerial phases
        HeroDef cat = def(hero2("\"archetype\": \"cat\""));
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.ACROBAT, 1), cat.v2().mobility().primary());
        HeroDef witch = def(hero2("\"archetype\": \"witch\""));
        assertEquals(HeroDef.MobilityTrait.FLYER, witch.v2().mobility().primary().trait());
        assertEquals(new HeroDef.CarrierSpec(HeroDef.Carrier.BROOM, null), witch.v2().mobility().carrier());
        assertEquals(new HeroDef.Aerial(List.of(2, 3), 8, 3, 4), witch.v2().mobility().aerial());
        HeroDef angel = def(hero2("\"archetype\": \"angel\", \"rarity\": \"legendary\""));
        assertEquals(List.of(1, 2, 3), angel.v2().mobility().aerial().phases(), "legendary flyers from phase 1");
        HeroDef spirit = def(hero2("\"archetype\": \"snow_spirit\""));
        assertEquals(new HeroDef.CarrierSpec(HeroDef.Carrier.LEVITATION, null), spirit.v2().mobility().carrier());
        // a water weak point adds water to avoid
        HeroDef wet = def(hero2("\"boss\": {\"weak_points\": [\"water\"], \"phases\": [{\"at\": 100}]}"));
        assertEquals(List.of(HeroDef.Avoid.WATER), wet.v2().mobility().avoid());
        // ARCH_PRIMARY covers every archetype
        assertEquals(HeroCatalogue.ARCHETYPES, List.copyOf(HeroCatalogue.ARCH_PRIMARY.keySet()));
    }

    @Test
    void theMobilityRulesOfEightThree() {
        Object[][] cases = {
                // block, code, expected primary, expected secondary
                {"{\"primary\": {\"trait\": \"teleporter\"}}", Code.MOBILITY_UNKNOWN, "runner:1", null},
                {"{\"primary\": {\"trait\": \"leaper\", \"tier\": 5}}", Code.MOBILITY_ADJUSTED, "leaper:2", null},
                {"{\"primary\": {\"trait\": \"leaper\", \"tier\": 3}}", Code.MOBILITY_ADJUSTED, "leaper:2", null},
                {"{\"primary\": {\"trait\": \"leaper\", \"tier\": 2}, \"secondary\": {\"trait\": \"climber\", \"tier\":"
                        + " 3}}", Code.MOBILITY_ADJUSTED, "leaper:2", "climber:2"},
                {"{\"primary\": {\"trait\": \"leaper\", \"tier\": 1}, \"secondary\": {\"trait\": \"climber\", \"tier\":"
                        + " 2}}", Code.MOBILITY_ADJUSTED, "leaper:1", "climber:1"},
                {"{\"primary\": {\"trait\": \"runner\"}, \"secondary\": {\"trait\": \"flyer\"}}", Code.MOBILITY_ADJUSTED,
                        "runner:1", null},
                {"{\"primary\": {\"trait\": \"glider\"}, \"secondary\": {\"trait\": \"hover\"}, \"carrier\": {\"kind\":"
                        + " \"cape\"}}", Code.MOBILITY_ADJUSTED, "glider:1", null},
                {"{\"primary\": {\"trait\": \"runner\"}, \"secondary\": {\"trait\": \"ember_stride\"}}",
                        Code.MOBILITY_ADJUSTED, "runner:1", null},
                {"{\"primary\": {\"trait\": \"runner\"}, \"aerial\": {\"phases\": [2]}}", Code.MOBILITY_ADJUSTED,
                        "runner:1", null},
                {"{\"primary\": {\"trait\": \"snowstride\"}}", Code.MOBILITY_UNKNOWN, "runner:1", null},
                {"{\"primary\": {\"trait\": \"flyer\"}, \"carrier\": {\"kind\": \"rocket\"}}", Code.MOBILITY_UNKNOWN,
                        "flyer:1", null},
        };
        for (Object[] c : cases) {
            Loaded l = read(hero2("\"mobility\": " + c[0]));
            HeroDef d = l.hero("test_hero");
            assertNotNull(d, l.problems().report());
            int expected = ((String) c[0]).contains("\"tier\": 5") ? 2 : 1; // out of range, then tier 3 for a rare hero
            assertEquals(expected, of(l, (Code) c[1]).size(), c[0] + "\n" + l.problems().report());
            HeroDef.Mobility m = d.v2().mobility();
            assertEquals(c[2], m.primary().trait().id() + ":" + m.primary().tier(), (String) c[0]);
            assertEquals(c[3], m.secondary() == null ? null : m.secondary().trait().id() + ":" + m.secondary().tier(),
                    (String) c[0]);
        }
        Loaded aerial = read(hero2("\"mobility\": {\"primary\": {\"trait\": \"flyer\"}, \"carrier\": {\"kind\":"
                + " \"wings\", \"part\": \"wings\"}, \"aerial\": {\"max_air\": 12, \"dives\": 0, \"weight\": 11}}"));
        assertEquals(new HeroDef.Aerial(List.of(2, 3), 8, 1, 10), aerial.hero("test_hero").v2().mobility().aerial());
        assertEquals(3, of(aerial, Code.MOBILITY_ADJUSTED).size());
        Loaded noCarrier = read(hero2("\"mobility\": {\"primary\": {\"trait\": \"glider\"}}"));
        assertEquals(Severity.NOTE, one(noCarrier, Code.MOBILITY_CARRIER).severity());
        Loaded part = read(hero2("\"mobility\": {\"primary\": {\"trait\": \"flyer\"}, \"carrier\": {\"kind\":"
                + " \"broom\", \"part\": \"Not A Part\"}}"));
        assertEquals(1, of(part, Code.WRONG_TYPE).size());
        assertNull(part.hero("test_hero").v2().mobility().carrier().part());
        HeroDef phoenix = def(hero2("\"element\": \"fire\", \"mobility\": {\"primary\": {\"trait\": \"runner\"},"
                + " \"secondary\": {\"trait\": \"ember_stride\"}}"));
        assertEquals(HeroDef.MobilityTrait.EMBER_STRIDE, phoenix.v2().mobility().secondary().trait());
    }

    @Test
    void theShareOfFlyersIsCheckedAcrossBuiltInHeroes() {
        List<Source> s = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "hero_" + (char) ('a' + i);
            s.add(new Source(id + ".json", hero2("\"archetype\": \"" + (i < 1 ? "angel" : "cat") + "\"")
                    .replace("test_hero", id)));
        }
        assertFalse(HeroReader.readAll(s, Options.BUILTIN).problems().has(Code.FLYERS_SHARE.id()), "1 of 10");
        s.set(1, new Source("hero_b.json", hero2("\"archetype\": \"angel\"").replace("test_hero", "hero_b")));
        assertTrue(HeroReader.readAll(s, Options.BUILTIN).problems().has(Code.FLYERS_SHARE.id()), "2 of 10");
        assertFalse(HeroReader.readAll(s, Options.DEFAULT).problems().has(Code.FLYERS_SHARE.id()), "built-ins only");
    }

    // ------------------------------------------------------------------ sound references (SOUND.md 3, 5)

    @Test
    void soundIdsAreCheckedForTheirFormOnly() {
        Object[][] cases = {{"item.shield.block", "item.shield.block"},
                {"minecraft:entity.cat.hiss", "entity.cat.hiss"},
                {"factionclash:hero.natsuho_komezaki.claw", "factionclash:hero.natsuho_komezaki.claw"},
                {"factionclash:hero.cue.telegraph.heavy", "factionclash:hero.cue.telegraph.heavy"},
                {"factionclash:hero.voice.natsuho_komezaki", "factionclash:hero.voice.natsuho_komezaki"},
                {"factionclash:hero.kit.fire_burst", "factionclash:hero.kit.fire_burst"},
                {"entity.warden.sonic_boom", "entity.warden.sonic_boom"},
                {"factionclash:sword_swing", null}, {"Not:A Sound", null}, {"hero.", null}, {"x y", null},
                {"factionclash:hero.voice.", null}, {"othermod:boom", null}};
        for (Object[] c : cases) {
            Loaded l = read(withMove("\"id\": \"m1\", \"name\": \"M\", \"form\": \"melee\", \"effects\": [" + DMG + "],"
                    + " \"look\": {\"sound\": \"" + c[0] + "\"}"));
            assertEquals(c[1], move(l.hero("test_hero"), "m1").ability().look().sound(), (String) c[0]);
            assertEquals(c[1] == null ? 1 : 0, of(l, Code.SOUND_ID).size(), (String) c[0]);
        }
        // format 1 keeps its allowlist
        Loaded v1 = HeroReader.read("test_hero.json", HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"melee\","
                + " \"cooldown\": 60, \"effects\": [" + DMG + "], \"look\": {\"sound\": \"entity.cat.hiss\"}")));
        assertEquals(Severity.ADJUSTED, one(v1, Code.WORD_DEFAULT).severity());
    }

    // ------------------------------------------------------------------ the generated block (12.1)

    @Test
    void theGeneratedBlockGivesFramesAndAnimations() {
        Loaded l = read(hero2("\"moves\": [" + LUNGE.replace("\"form\": \"dash\"", "\"form\": \"dash\", \"frames\":"
                + " {\"windup\": 1.0, \"active\": 0.2, \"recovery\": 0.9}") + ", " + chainMove("m2", null) + "],"
                + " \"generated\": {\"generator\": \"herogen 1\", \"hash\": \"abc123\", \"moves\": [{\"id\": \"lunge\","
                + " \"anim\": \"move.lunge\", \"frames\": {\"windup\": 1.3, \"active\": 0.05, \"recovery\": 0.85}},"
                + " {\"id\": \"m2\", \"anim\": \"move.m2_v2\", \"frames\": {\"windup\": 0.82}}, {\"id\": \"ghost\"}]}"));
        HeroDef d = l.hero("test_hero");
        assertEquals(new HeroDef.Frames(1.3, 0.05, 0.85), move(d, "lunge").frames(), "the markers win");
        assertEquals(0.82, move(d, "m2").frames().windup(), EPS);
        assertEquals("move.m2_v2", move(d, "m2").anim());
        assertEquals("abc123", d.v2().generatedHash());
        List<Problem> g = of(l, Code.FRAMES_GENERATED);
        assertEquals(2, g.size(), l.problems().report());
        assertTrue(g.stream().anyMatch(p -> p.message().contains("differ from its animation's markers")));
        assertTrue(g.stream().anyMatch(p -> p.message().contains("\"ghost\" is not a move of this hero")));
    }

    // ------------------------------------------------------------------ equal treatment, no yield, no tokens

    @Test
    void noLoadedDefinitionCanSingleOutPlayersYieldOrHoldTokens() {
        Set<String> forbidden = Set.of("player", "players", "npc", "yield", "yields", "spare", "spared", "kneel",
                "token", "tokens", "target_is_player", "players_engaged", "on_yield", "damage_vs_players", "defeat");
        for (HeroCatalogue.Vocab v : HeroCatalogue.vocabs().values()) {
            for (String w : v.words) {
                assertFalse(forbidden.contains(w), v.name + ": " + w);
            }
        }
        // no record of a definition has a component that could hold such a rule
        List<Class<?>> records = new ArrayList<>(List.of(HeroDef.class));
        for (Class<?> c : HeroDef.class.getDeclaredClasses()) {
            if (c.isRecord()) {
                records.add(c);
            }
        }
        for (Class<?> c : records) {
            for (RecordComponent rc : c.getRecordComponents()) {
                String n = rc.getName().toLowerCase(Locale.ROOT);
                assertFalse(n.contains("player") || n.contains("yield") || n.contains("spare") || n.contains("token")
                        || n.contains("npc"), c.getSimpleName() + "." + rc.getName());
            }
        }
        for (Class<?> c : HeroDef.class.getDeclaredClasses()) {
            if (c.isEnum()) {
                for (Object e : c.getEnumConstants()) {
                    String n = ((Enum<?>) e).name().toLowerCase(Locale.ROOT);
                    assertFalse(n.contains("player") || n.contains("yield") || n.contains("spare")
                            || n.contains("token"), c.getSimpleName() + "." + n);
                }
            }
        }
    }

    // ------------------------------------------------------------------ limits and damaged files

    @Test
    void theLimitsOfTwelveSeventeen() {
        assertEquals(12, HeroCatalogue.MAX_MOVES);
        assertEquals(3, HeroCatalogue.MAX_STATES);
        assertEquals(3, HeroCatalogue.MAX_PHASES);
        assertEquals(8, HeroCatalogue.MAX_REACTIONS);
        assertEquals(4, HeroCatalogue.MAX_WEAK_POINTS);
        assertEquals(3, HeroCatalogue.MAX_TALENTS);
        assertEquals(4, HeroCatalogue.MAX_RELATIONSHIPS);
        assertEquals(12, HeroCatalogue.MAX_LINES_PER_EVENT_2);
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            r.append(i == 0 ? "" : ", ").append("{\"when\": \"target_eating\", \"move\": \"lunge\"}");
        }
        Loaded l = read(hero2("\"boss\": {\"weak_points\": [\"back\", \"iron\", \"gold\", \"bell\", \"light\"], \"phases\":"
                + " [{\"at\": 100}, {\"at\": 80}, {\"at\": 60}, {\"at\": 40}], \"reactions\": [" + r + "]}"));
        HeroDef d = l.hero("test_hero");
        assertEquals(8, d.v2().boss().reactions().size());
        assertEquals(4, d.v2().boss().weakPoints().size());
        assertEquals(3, d.v2().boss().phases().size());
        assertEquals(3, of(l, Code.LIST_TOO_LONG).size());
    }

    @Test
    void noDamagedSliceKitBreaksTheReaderOrARule() throws IOException {
        int loaded = 0, refused = 0;
        for (Source s : SliceKitsTest.sources()) {
            Object clean = HeroReaderRobustnessTest.model(HeroJson.parse(s.text()).root());
            for (int seed = 0; seed < 250; seed++) {
                Random rnd = new Random(seed * 7919L + s.path().hashCode());
                Object m = HeroReaderRobustnessTest.copy(clean);
                int changes = 1 + rnd.nextInt(5);
                for (int i = 0; i < changes; i++) {
                    HeroReaderRobustnessTest.damage(m, rnd);
                }
                String text = HeroReaderRobustnessTest.write(m);
                Loaded l;
                try {
                    l = HeroReader.readAll(List.of(new Source(s.path(), text)), Options.BUILTIN);
                } catch (RuntimeException e) {
                    throw new AssertionError(s.path() + " seed " + seed + " threw on:\n" + text, e);
                }
                for (HeroDef d : l.heroes().values()) {
                    try {
                        V2Sanity.sane(d);
                    } catch (AssertionError e) {
                        throw new AssertionError(s.path() + " seed " + seed + ": " + e.getMessage() + "\n" + text, e);
                    }
                }
                for (Problem p : l.problems().all()) {
                    String f = p.format();
                    assertTrue(f.endsWith(p.severity().label + "."), f);
                    assertFalse(p.message().isBlank(), f);
                }
                if (l.heroes().isEmpty()) {
                    refused++;
                } else {
                    loaded++;
                }
            }
        }
        assertTrue(loaded > 1200 && refused > 20, "both outcomes are exercised: " + loaded + " loaded, " + refused
                + " refused");
    }
}
