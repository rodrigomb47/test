package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.FormSpec;
import com.factionclash.logic.hero.HeroCatalogue.RolePreset;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.PassiveForm;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HERO_FORMAT.md 31.1 {@code HeroReaderTest}: from the minimal hero to lists of heroes and duplicate ids. */
class HeroReaderTest {
    static final String MIN = "{\"format\": 1, \"id\": \"test_hero\", \"name\": \"Test Hero\", \"behaviour\":"
            + " {\"combat_role\": \"striker\"}, \"basic_attack\": {\"style\": \"melee\"}, \"gear\": [\"iron_sword\"]";

    /** The minimal hero plus {@code more} top-level fields. */
    static String hero(String more) {
        return MIN + (more.isEmpty() ? "" : ", " + more) + "}";
    }

    static Loaded read(String text) {
        return HeroReader.read("test_hero.json", text);
    }

    static HeroDef def(String text) {
        Loaded l = read(text);
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, () -> "should load:\n" + l.problems().report());
        return d;
    }

    static List<Problem> of(Loaded l, Problems.Code code) {
        return l.problems().all().stream().filter(p -> p.code().equals(code.id())).toList();
    }

    static Problem one(Loaded l, Problems.Code code) {
        List<Problem> p = of(l, code);
        assertEquals(1, p.size(), () -> "one " + code + " in:\n" + l.problems().report());
        return p.get(0);
    }

    static String skill(String fields) {
        return "\"skills\": [ { \"name\": \"S\", " + fields + " } ]";
    }

    // ------------------------------------------------------------------ the minimal hero and required fields

    @Test
    void theMinimalHeroLoadsWithTheRoleDefaults() {
        Loaded l = read("{\"format\": 1, \"id\": \"test_hero\", \"name\": \"Test Hero\", \"behaviour\": {\"combat_role\":"
                + " \"vanguard\"}, \"basic_attack\": {\"style\": \"melee\"}}");
        HeroDef d = l.hero("test_hero");
        assertNotNull(d);
        assertTrue(l.problems().all().isEmpty(), "five fields are a whole hero; every default is silent:\n"
                + l.problems().report());
        RolePreset v = HeroCatalogue.ROLES.get(Role.VANGUARD);
        assertEquals(v.health(), d.stats().health());
        assertEquals(v.attack(), d.stats().attack());
        assertEquals(v.armor(), d.stats().armor());
        assertEquals(v.speed(), d.stats().speed());
        assertEquals(v.knockbackResistance(), d.stats().knockbackResistance());
        assertEquals(100, d.stats().size());
        assertEquals(HeroDef.Growth.TOUGH, d.stats().growth());
        assertEquals(HeroDef.Focus.THREAT_TO_ALLIES, d.behaviour().focus());
        assertEquals(HeroDef.Aggression.STEADY, d.behaviour().aggression());
        assertEquals(HeroDef.Protects.ALLIES, d.behaviour().protects());
        assertEquals(20, d.behaviour().retreatBelow());
        assertEquals(HeroDef.Raids.LEADS, d.behaviour().raids());
        assertEquals("guard", d.behaviour().duty());
        assertEquals(HeroDef.Rarity.RARE, d.rarity());
        assertEquals(HeroDef.Element.PHYSICAL, d.element());
        assertEquals(HeroCatalogue.FACTIONS, d.factions());
        assertEquals(HeroDef.NO_COLOR, d.color());
        assertEquals("", d.title());
        assertEquals("stone_sword", d.gear().get(0).item());
        assertTrue(d.skills().isEmpty() && d.passives().isEmpty() && d.baseAbilities().isEmpty());
        assertNull(d.ultimate());
        assertNull(d.empowered());
        assertTrue(d.lines().isEmpty());
        assertEquals(7, d.referenceAttack(), "a melee hero's reference attack: 6 + a vanguard's stats.attack 1");
    }

    @Test
    void everyRoleGivesItsPreset() {
        for (Role r : HeroDef.Word.inFormat(Role.class, 1)) { // format 1's roles (format 2 adds two)
            HeroDef d = def(MIN.replace("striker", r.id()) + "}");
            RolePreset p = HeroCatalogue.ROLES.get(r);
            assertEquals(p.health(), d.stats().health(), r.id());
            assertEquals(p.growth(), d.stats().growth(), r.id());
            assertEquals(p.keepDistance(), d.behaviour().keepDistance(), r.id());
            assertEquals(p.raids(), d.behaviour().raids(), r.id());
        }
    }

    @Test
    void eachRequiredFieldHasItsMessage() {
        String[][] cases = {
                {"\"format\": 1, ", "", "\"format\" is missing. Write \"format\": 1 at the top of the hero"},
                {"\"name\": \"Test Hero\", ", "", "\"name\" is missing. Add the hero's display name, for example"
                        + " \"name\": \"Test Hero\""},
                {"\"behaviour\": {\"combat_role\": \"striker\"}, ", "", "\"behaviour\" is missing. Add at least"
                        + " \"behaviour\": { \"combat_role\": \"striker\" }"},
                {"\"combat_role\": \"striker\"", "\"aggression\": \"bold\"", "\"combat_role\" is missing. Allowed:"
                        + " vanguard, striker, skirmisher, marksman, support"},
                {"\"basic_attack\": {\"style\": \"melee\"}, ", "", "\"basic_attack\" is missing. Add at least"
                        + " \"basic_attack\": { \"style\": \"melee\" }"},
                {"\"style\": \"melee\"", "\"amount\": 4", "\"style\" is missing. Allowed: melee, bow, crossbow,"
                        + " trident, thrown"},
        };
        for (String[] c : cases) {
            String text = hero("").replace(c[0], c[1]);
            Loaded l = read(text);
            assertTrue(l.heroes().isEmpty(), c[0]);
            assertEquals(List.of("test_hero"), l.notLoaded());
            Problem p = l.problems().all().stream().filter(x -> x.severity() == Severity.NOT_LOADED).findFirst()
                    .orElseThrow();
            assertEquals(c[2], p.message(), c[0]);
        }
        Problem name = read(hero("").replace("\"name\": \"Test Hero\", ", "")).problems().all().stream()
                .filter(x -> x.severity() == Severity.NOT_LOADED).findFirst().orElseThrow();
        assertEquals("test_hero.json, line 1: \"name\" is missing. Add the hero's display name, for example \"name\":"
                + " \"Test Hero\". NOT LOADED.", name.format());
        Loaded both = read(hero("").replace("\"name\": \"Test Hero\", ", "").replace("\"striker\"", "\"wizard\""));
        assertEquals(2, both.problems().count(Severity.NOT_LOADED), "a broken core still reports everything");
    }

    @Test
    void theFormatMustBeOneThisBuildReads() {
        // format 2 (HEROES_V2 12) is read since F2; a newer one is refused
        assertEquals("this file is for a newer Faction Clash (format 3; this build reads formats 1 and 2)",
                one(read(hero("").replace("\"format\": 1", "\"format\": 3")), Problems.Code.FORMAT_NEWER).message());
        for (String bad : List.of("0", "-1", "1.5", "\"one\"", "true")) {
            Loaded l = read(hero("").replace("\"format\": 1", "\"format\": " + bad));
            assertTrue(l.heroes().isEmpty(), bad);
            assertEquals(Severity.NOT_LOADED, one(l, Problems.Code.FORMAT_INVALID).severity(), bad);
        }
        Loaded text = read(hero("").replace("\"format\": 1", "\"format\": \"1\""));
        assertNotNull(text.hero("test_hero"));
        assertEquals("\"1\" was read as 1", one(text, Problems.Code.TEXT_NUMBER).message());
    }

    // ------------------------------------------------------------------ fields

    @Test
    void unknownAndMisspelledFields() {
        Loaded l = read(hero("\"elemnt\": \"fire\", \"Title\": \"the Test\", \"wibble\": 3, \"_note\": \"x\","
                + " \"stats\": {\"helth\": 30, \"armr\": 2}, " + skill("\"form\": \"melee\", \"cooldwon\": 20,"
                + " \"effects\": [{\"type\": \"knockback\", \"uo\": 1}, {\"type\": \"stun\", \"amount\": 3}]")));
        HeroDef d = l.hero("test_hero");
        assertEquals(HeroDef.Element.FIRE, d.element(), "elemnt read as element");
        assertEquals("the Test", d.title(), "Title read as title");
        assertEquals(30, d.stats().health());
        assertEquals(2, d.stats().armor(), "armr is 1 from armor (4+ letters: up to 2)");
        assertEquals(20, d.skills().get(0).cooldown());
        List<String> misspelled = of(l, Problems.Code.MISSPELLED_FIELD).stream().map(Problem::message).toList();
        assertTrue(misspelled.contains("\"cooldwon\" was read as \"cooldown\""), misspelled.toString());
        assertTrue(misspelled.contains("\"Title\" was read as \"title\""));
        assertEquals(5, misspelled.size(), misspelled.toString());
        List<String> unknown = of(l, Problems.Code.UNKNOWN_FIELD).stream().map(Problem::message).toList();
        assertTrue(unknown.contains("\"wibble\" is not a field here; it was ignored."), unknown.toString());
        assertTrue(unknown.stream().anyMatch(m -> m.startsWith("\"uo\" is not a field here; it was ignored. Did you"
                + " mean \"to\" or \"up\"?")), "close to two fields: a did-you-mean, nothing read");
        Problem elsewhere = one(l, Problems.Code.FIELD_ELSEWHERE);
        assertEquals("\"amount\" is not used by the \"stun\" effect; it was ignored", elsewhere.message());
        assertEquals(Severity.NOTE, elsewhere.severity());
        assertTrue(l.problems().all().stream().noneMatch(p -> p.message().contains("_note")), "comments are silent");
        // a short field name: only edit distance 1 counts
        Loaded shortName = read(hero(skill("\"form\": \"self\", \"effects\": [{\"type\": \"cleanse\", \"tx\":"
                + " \"self\"}]")));
        assertTrue(of(shortName, Problems.Code.MISSPELLED_FIELD).get(0).message().contains("\"to\""));
        Loaded tooFar = read(hero(skill("\"form\": \"self\", \"effects\": [{\"type\": \"cleanse\", \"tzz\": 1}]")));
        assertTrue(of(tooFar, Problems.Code.MISSPELLED_FIELD).isEmpty(), "\"tzz\" is 2 from \"to\": unknown");
        // a field already present is not a candidate
        Loaded present = read(hero(skill("\"form\": \"self\", \"cooldown\": 9, \"cooldwon\": 20, \"effects\":"
                + " [{\"type\": \"cleanse\"}]")));
        assertEquals(9, present.hero("test_hero").skills().get(0).cooldown());
        assertEquals(1, of(present, Problems.Code.UNKNOWN_FIELD).size());
    }

    @Test
    void numbersInTextAndOutOfRange() {
        Loaded l = read(hero("\"stats\": {\"speed\": \"105%\", \"health\": \"30\", \"attack\": 9, \"size\": 99.6}, "
                + skill("\"form\": \"self\", \"cooldown\": \"12 seconds\", \"windup\": \"0.5s\", \"effects\":"
                + " [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": \"20%\", \"seconds\": \"4 blocks\"}]")));
        HeroDef d = l.hero("test_hero");
        assertEquals(105, d.stats().speed());
        assertEquals(30, d.stats().health());
        assertEquals(4, d.stats().attack(), "clamped to 4");
        assertEquals(100, d.stats().size(), "rounded");
        Ability s = d.skills().get(0);
        assertEquals(12, s.cooldown());
        assertEquals(0.5, s.windup());
        HeroDef.StatMod st = (HeroDef.StatMod) s.effects().get(0);
        assertEquals(20, st.percent());
        assertEquals(8, st.seconds(), "\"4 blocks\" is not seconds: the default");
        assertEquals("should be a number of seconds, not \"4 blocks\". Used the default, 8",
                one(l, Problems.Code.WRONG_TYPE).message());
        assertEquals(5, of(l, Problems.Code.TEXT_NUMBER).size());
        assertEquals("9 is above its range. Allowed 0 to 4. Set to 4", one(l, Problems.Code.CLAMPED).message());
        assertEquals("99.6 is not a whole number. Rounded to 100", one(l, Problems.Code.ROUNDED).message());
        Loaded b = read(hero("\"empowered\": {\"name\": \"E\", \"skills_locked\": \"true\"}"));
        assertTrue(b.hero("test_hero").empowered().skillsLocked());
        assertEquals("\"true\" was read as true", one(b, Problems.Code.TEXT_BOOL).message());
        Loaded below = read(hero("\"stats\": {\"health\": 5}"));
        assertEquals("5 is below its range. Allowed 20 to 40. Set to 20", one(below, Problems.Code.CLAMPED).message());
    }

    @Test
    void unknownWordsListWhatIsAllowed() {
        Loaded l = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2,"
                + " \"element\": \"wind\"}]")));
        Problem p = one(l, Problems.Code.WORD_DEFAULT);
        assertEquals("test_hero.json, line 1: skill 1 \"S\" > effect 1 > element: \"wind\" is not an element. Allowed:"
                + " physical, fire, frost, shock, venom, blight, arcane, radiant. Used the hero's element, \"physical\"."
                + " ADJUSTED.", p.format());
        Loaded role = read(hero("").replace("\"striker\"", "\"wizard\""));
        assertEquals("test_hero.json, line 1: behaviour > combat_role: \"wizard\" is not a combat role. Allowed:"
                + " vanguard, striker, skirmisher, marksman, support. NOT LOADED.", one(role, Problems.Code.WORD_CORE)
                .format());
        Loaded near = read(hero("\"element\": \"frots\""));
        assertTrue(one(near, Problems.Code.WORD_DEFAULT).message().contains("Did you mean \"frost\"?"));
        Loaded ranger = read(hero("").replace("\"striker\"", "\"ranger\""));
        assertEquals("behaviour > combat_role: \"ranger\" was read as \"marksman\". ADJUSTED.",
                one(ranger, Problems.Code.SYNONYM).format().substring("test_hero.json, line 1: ".length()));
        Loaded caseFree = read(hero("\"element\": \"  Fire \", " + skill("\"form\": \"Self\", \"effects\":"
                + " [{\"type\": \"effect\", \"id\": \"minecraft:Slow Falling\", \"seconds\": 5}]")));
        assertTrue(caseFree.problems().all().isEmpty(), "case, spaces and the minecraft: prefix do not matter: "
                + caseFree.problems().report());
        assertEquals("slow_falling", ((HeroDef.PotionEffect) caseFree.hero("test_hero").skills().get(0).effects()
                .get(0)).id());
        Loaded sound = read(hero(skill("\"form\": \"self\", \"effects\": [{\"type\": \"cleanse\"}], \"look\":"
                + " {\"sound\": \"boom\"}")));
        assertTrue(one(sound, Problems.Code.WORD_DEFAULT).message().contains("the list in HERO_FORMAT.md Appendix B"),
                "long lists point to the appendix");
    }

    @Test
    void everySynonymTableIsRead() {
        HeroDef d = def(hero("\"element\": \"holy\", \"skills\": ["
                + "{\"name\": \"A\", \"form\": \"aoe\", \"use_when\": \"crowd\", \"effects\": [{\"type\": \"burn\"},"
                + " {\"type\": \"slowness\", \"seconds\": 2}]}],"
                + " \"passives\": [{\"name\": \"P\", \"trigger\": \"on_damaged\", \"effects\": [{\"type\": \"push\"}]}],"
                + " \"base_abilities\": [{\"type\": \"sentry\"}], \"lines\": {\"hello\": [\"Hi {player}.\"]}")
                .replace("\"striker\"", "\"tank\""));
        assertEquals(Role.VANGUARD, d.behaviour().role());
        assertEquals(HeroDef.Element.RADIANT, d.element());
        Ability a = d.skills().get(0);
        assertEquals(Form.NOVA, a.form());
        assertEquals(HeroDef.UseWhen.ENEMIES_NEAR, a.useWhen());
        assertInstanceOf(HeroDef.Ignite.class, a.effects().get(0));
        assertEquals("slowness", ((HeroDef.PotionEffect) a.effects().get(1)).id(), "a potion name as a type");
        assertEquals(Trigger.ON_HURT, d.passives().get(0).trigger());
        assertInstanceOf(HeroDef.Knockback.class, d.passives().get(0).effects().get(0));
        assertInstanceOf(HeroDef.Lookout.class, d.baseAbilities().get(0));
        assertEquals(List.of("Hi {player}."), d.lines().get(LineEvent.GREETING));
        // every synonym of every table resolves to its word, and never shadows a word
        for (HeroCatalogue.Vocab v : HeroCatalogue.vocabs().values()) {
            for (Map.Entry<String, String> e : v.synonyms.entrySet()) {
                assertEquals(e.getValue(), v.resolve(e.getKey()), v.name + ": " + e.getKey());
                assertFalse(v.has(e.getKey()));
            }
        }
    }

    @Test
    void aNullIsReadAsAFieldLeftOut() {
        Loaded l = read(hero("\"title\": null, \"ultimate\": null, \"skills\": null, \"stats\": {\"health\": null,"
                + " \"armor\": 5}, \"resistances\": {\"fire\": null, \"frost\": 20}, \"lines\": {\"kill\": null,"
                + " \"victory\": [\"Done.\"]}").replace("[\"iron_sword\"]", "[{\"item\": \"iron_sword\","
                + " \"enchantments\": {\"sharpness\": null, \"knockback\": 1}}]"));
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, l.problems().report());
        assertEquals("", d.title());
        assertNull(d.ultimate());
        assertTrue(d.skills().isEmpty());
        assertEquals(28, d.stats().health(), "the role's health");
        assertEquals(5, d.stats().armor());
        assertEquals(Map.of(HeroDef.Element.FROST, 20), d.resistances());
        assertEquals(Map.of("knockback", 1), d.gear().get(0).enchantments());
        assertEquals(List.of(LineEvent.VICTORY), List.copyOf(d.lines().keySet()));
        assertEquals(7, of(l, Problems.Code.NULL_FIELD).size(), l.problems().report());
        assertEquals(7, l.problems().all().size(), "notes and nothing else: a null is nothing");
        assertEquals("test_hero.json, line 1: stats: \"health\" is null, which means nothing; it was read as left out."
                + " NOTE.", of(l, Problems.Code.NULL_FIELD).get(3).format());
        // a required field that is null is missing
        Loaded name = read(hero("").replace("\"name\": \"Test Hero\"", "\"name\": null"));
        assertTrue(name.heroes().isEmpty());
        assertEquals("\"name\" is missing. Add the hero's display name, for example \"name\": \"Test Hero\"",
                one(name, Problems.Code.MISSING).message());
        Loaded fx = read(hero(skill("\"form\": \"melee\", \"effects\": null")));
        assertEquals("skill 1 \"S\": \"effects\" is missing: what the skill does. Skill PART LEFT OUT.",
                one(fx, Problems.Code.MISSING).format().substring("test_hero.json, line 1: ".length()));
        Loaded base = read(hero("\"base_abilities\": [{\"type\": null}]"));
        assertEquals(Severity.PART_LEFT_OUT, one(base, Problems.Code.MISSING).severity());
        assertTrue(one(base, Problems.Code.MISSING).message().startsWith("\"type\" is missing. Allowed: aura"));
        // a null where a list holds its items is not a field: that item is left out, and said so
        Loaded item = read(hero(skill("\"form\": \"melee\", \"effects\": [null, {\"type\": \"knockback\"}]")));
        assertEquals("should be an object { \"type\": ... }, not null", one(item, Problems.Code.WRONG_TYPE).message());
        assertEquals(1, item.hero("test_hero").skills().get(0).effects().size());
        // a field that is null does not stand in the way of its misspelled twin
        Loaded twin = read(hero(skill("\"form\": \"self\", \"cooldown\": null, \"cooldwon\": 20, \"effects\":"
                + " [{\"type\": \"cleanse\"}]")));
        assertEquals(20, twin.hero("test_hero").skills().get(0).cooldown());
    }

    @Test
    void wordsThatToldPlayersApartAreReadAsWordsThatDoNot() {
        // the owner's rule (HEROES_V2 6, 6.15): no hero prefers, avoids or singles out players
        Loaded l = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2, \"if\":"
                + " {\"condition\": \"target_is\", \"kind\": \"player\"}}, {\"type\": \"knockback\", \"if\":"
                + " {\"condition\": \"target_is\", \"kind\": \"NPC\"}}, {\"type\": \"ignite\", \"if\": {\"condition\":"
                + " \"target_is\", \"kind\": \"person\"}}]")).replace("\"striker\"}", "\"striker\", \"protects\":"
                + " \"players\"}"));
        HeroDef d = l.hero("test_hero");
        assertEquals(HeroDef.Protects.ALLIES, d.behaviour().protects());
        for (Effect e : d.skills().get(0).effects()) {
            assertEquals(HeroDef.TargetKind.PERSON, e.c().cond().kind());
        }
        List<Problem> eq = of(l, Problems.Code.EQUAL_TREATMENT);
        assertEquals(3, eq.size(), "\"person\" itself is a word: " + l.problems().report());
        assertEquals("test_hero.json, line 1: behaviour > protects: \"players\" was read as \"allies\": heroes treat"
                + " players and NPCs alike, and \"allies\" is everyone of its side, players included. ADJUSTED.",
                eq.get(0).format());
        assertEquals("\"player\" was read as \"person\": heroes treat players and NPCs alike, and a person is a player,"
                + " an NPC or a hero alike", eq.get(1).message());
        assertEquals("skills[0].effects[1].if.kind", eq.get(2).machinePath());
        assertEquals(3, l.problems().all().size());
        Loaded bad = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                + " {\"type\": \"stun\", \"if\": {\"condition\": \"target_is\", \"kind\": \"villager\"}}]")));
        assertTrue(one(bad, Problems.Code.WORD_PART).message().contains("Allowed: monster, undead, arthropod, illager,"
                + " person, hero, boss"), "the allowed kinds name no players");
        for (Class<?> c : HeroDef.class.getDeclaredClasses()) {
            if (c.isEnum()) {
                for (Object k : c.getEnumConstants()) {
                    assertFalse(k.toString().contains("PLAYER"), c.getSimpleName() + "." + k);
                }
            }
        }
    }

    @Test
    void aBrokenFileCannotFloodTheReport() {
        StringBuilder junk = new StringBuilder();
        for (int i = 0; i < 320; i++) {
            junk.append("\"zz_unknown_").append(i).append("\": 1, ");
        }
        Loaded l = read(hero(junk + "\"stats\": {\"health\": 99}"));
        assertNotNull(l.hero("test_hero"));
        assertEquals(HeroCatalogue.MAX_PROBLEMS_PER_HERO + 1, l.problems().all().size());
        Problem more = one(l, Problems.Code.TOO_MANY_PROBLEMS);
        assertEquals("121 more problems of this hero are not listed (only the first 200 are); fix those and check the"
                + " file again", more.message());
        assertEquals(Severity.ADJUSTED, more.severity(), "the worst of what is not listed: the clamped health");
        assertEquals(Severity.ADJUSTED, l.problems().worst("test_hero"));
        // what stops a hero from loading is always listed
        Loaded dead = read(hero(junk + "\"stats\": {\"health\": 99}").replace("\"striker\"", "\"wizard\"")
                .replace("\"style\": \"melee\"", "\"style\": \"claws\""));
        assertTrue(dead.heroes().isEmpty());
        assertEquals(2, dead.problems().count(Severity.NOT_LOADED), dead.problems().report().substring(0, 400));
        // a long list is told once
        Loaded list = read(hero("\"factions\": [\"stoneguard\", \"ember_legion\", \"tide_alliance\","
                + " \"grove_covenant\", \"stoneguard\", \"stoneguard\", \"stoneguard\"]"));
        assertEquals("factions > faction 5: \"factions\" holds at most 4 items; this one and the 2 after it were left"
                + " out. Faction PART LEFT OUT.", one(list, Problems.Code.LIST_TOO_LONG).format()
                .substring("test_hero.json, line 1: ".length()));
    }

    @Test
    void theBalancePassReportsAtTheExactField() {
        String file = "{\n  \"format\": 1,\n  \"id\": \"test_hero\",\n  \"name\": \"Test Hero\",\n"
                + "  \"behaviour\": {\"combat_role\": \"support\"},\n"
                + "  \"basic_attack\": {\n    \"style\": \"thrown\",\n    \"amount\": 9,\n"
                + "    \"on_hit\": [{\"type\": \"damage\", \"amount\": 3}]\n  },\n"
                + "  \"skills\": [\n    {\n      \"name\": \"Mire\",\n      \"form\": \"bolt\",\n"
                + "      \"cooldown\": 2,\n      \"effects\": [\n"
                + "        {\"type\": \"damage\", \"amount\": 8},\n"
                + "        {\"type\": \"effect\", \"id\": \"weakness\",\n         \"level\": 3,\n"
                + "         \"seconds\": 20},\n"
                + "        {\"type\": \"stat\", \"stat\": \"attack_speed\", \"seconds\": 4,\n         \"percent\": -50}\n"
                + "      ]\n    },\n"
                + "    {\"name\": \"Pack\", \"form\": \"self\", \"cooldown\": 60, \"effects\": [\n"
                + "      {\"type\": \"summon\", \"creature\": \"snow_golem\",\n       \"count\": 3}]},\n"
                + "    {\"name\": \"Burst\", \"form\": \"nova\", \"effects\": [{\"type\": \"damage\", \"amount\": 10}]}\n"
                + "  ],\n"
                + "  \"passives\": [\n    {\"name\": \"Tick\", \"trigger\": \"every\", \"form\": \"nova\",\n"
                + "     \"trigger_value\": 3,\n     \"effects\": [{\"type\": \"damage\", \"amount\": 8}]},\n"
                + "    {\"name\": \"Jab\", \"trigger\": \"on_hit\", \"effects\": [{\"type\": \"damage\", \"amount\": 6}]}\n"
                + "  ],\n"
                + "  \"empowered\": {\n    \"name\": \"Tide\",\n    \"stats\": [{\"stat\": \"speed\", \"percent\": 10},\n"
                + "              {\"stat\": \"armor\",\n               \"percent\": 30}, {\"stat\": \"attack\","
                + " \"percent\": -50}],\n"
                + "    \"on_hit\": [{\"type\": \"damage\", \"amount\": 3}]\n  }\n}\n";
        Loaded l = read(file);
        assertNotNull(l.hero("test_hero"), l.problems().report());
        String[][] want = {
                // code, friendly path, machine path, line
                {"thrown_rate", "basic_attack > interval", "basic_attack.interval", "6"},
                {"potion_level", "skill 1 \"Mire\" > effect 2 > level", "skills[0].effects[1].level", "19"},
                {"potion_seconds", "skill 1 \"Mire\" > effect 2 > seconds", "skills[0].effects[1].seconds", "20"},
                {"stat_cap", "skill 1 \"Mire\" > effect 3 > percent", "skills[0].effects[2].percent", "22"},
                {"summon_count", "skill 2 \"Pack\" > effect 1 > count", "skills[1].effects[0].count", "27"},
                {"cooldown_floor", "skill 1 \"Mire\" > cooldown", "skills[0].cooldown", "15"},
                {"cooldown_floor", "skill 3 \"Burst\" > cooldown", "skills[2].cooldown", "28"},
                {"every_floor", "passive 1 \"Tick\" > trigger_value", "passives[0].trigger_value", "32"},
                {"passive_floor", "passive 2 \"Jab\" > cooldown", "passives[1].cooldown", "34"},
                {"shared_on_hit", "empowered > on_hit", "empowered.on_hit", "41"},
        };
        for (String[] w : want) {
            assertTrue(l.problems().all().stream().anyMatch(p -> p.code().equals(w[0]) && p.path().equals(w[1])
                    && p.machinePath().equals(w[2]) && p.line() == Integer.parseInt(w[3])
                    && p.severity() == Severity.ADJUSTED), String.join(" | ", w) + " in:\n" + l.problems().report());
        }
        Problem given = of(l, Problems.Code.COOLDOWN_FLOOR).get(0);
        assertEquals("test_hero.json, line 15: skill 1 \"Mire\" > cooldown: 2 seconds is too short for a skill this"
                + " strong (weight 15). Raised to 9.5 seconds, the shortest allowed for it. ADJUSTED.", given.format());
        assertEquals("test_hero.json, line 20: skill 1 \"Mire\" > effect 2 > seconds: \"weakness\" on enemies lasts at"
                + " most 8 seconds. Set to 8. ADJUSTED.", one(l, Problems.Code.POTION_SECONDS).format(),
                "v1 26.3's own example of a potion cap");
        assertTrue(of(l, Problems.Code.COOLDOWN_FLOOR).get(1).message().endsWith("(the file gives no \"cooldown\", so"
                + " its default was used)"), "a default that was raised says it is one");
        assertTrue(one(l, Problems.Code.THROWN_RATE).message().endsWith("(the file gives no \"interval\", so its"
                + " default was used)"));
        // an empowered stat is reported at that stat
        Loaded emp = read(hero("\"empowered\": {\"name\": \"E\", \"stats\": [{\"stat\": \"speed\", \"percent\": 10},"
                + " {\"stat\": \"knockback_resistance\", \"percent\": -20}]}"));
        Problem stat = one(emp, Problems.Code.STAT_CAP);
        assertEquals("empowered > stats > stat 2 > percent", stat.path());
        assertEquals("empowered.stats[1].percent", stat.machinePath());
        // slot caps are about the whole ability, or the whole on-hit list
        Loaded cap = read(hero(skill("\"form\": \"melee\", \"cooldown\": 60, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 25}]") + ", \"basic_attack\": {\"style\": \"melee\", \"on_hit\": [{\"type\": \"damage\","
                + " \"amount\": 9}], \"every_nth_hit\": {\"n\": 3, \"effects\": [{\"type\": \"damage\", \"amount\": 20}]}}")
                .replace("\"basic_attack\": {\"style\": \"melee\"}, ", ""));
        assertEquals(List.of("basic_attack.on_hit", "basic_attack.every_nth_hit", "skills[0]"),
                of(cap, Problems.Code.SLOT_CAP).stream().map(Problem::machinePath).toList());
    }

    // ------------------------------------------------------------------ forms (v1 10.1)

    @Test
    void eachFormHasItsDefaultsAndNotesWhatItDoesNotUse() {
        String all = "\"range\": 6, \"radius\": 2, \"count\": 4, \"angle\": 60, \"width\": 2, \"hits\": 2,"
                + " \"hit_interval\": 0.5, \"projectile\": \"snowball\", \"zone_at\": \"target\", \"delay\": 1,"
                + " \"duration\": 2, \"interval\": 1, ";
        for (Form f : HeroDef.Word.inFormat(Form.class, 1)) { // format 1's forms (format 2 adds eight)
            String fx = f.friendly() ? "{\"type\": \"cleanse\"}" : "{\"type\": \"damage\", \"amount\": 2}";
            Ability a = def(hero(skill("\"form\": \"" + f.id() + "\", \"effects\": [" + fx + "]"))).skills().get(0);
            FormSpec fs = HeroCatalogue.FORMS.get(f);
            assertEquals(fs.usesRange() ? fs.rangeDefault() : 0, a.range(), f.id());
            assertEquals(fs.usesRadius() ? fs.radiusDefault() : 0, a.radius(), f.id());
            assertEquals(3, a.count(), f.id());
            assertEquals(f == Form.LEAP ? 0.3 : 0, a.windup(), f.id());
            assertEquals(f.friendly() && f != Form.SELF ? HeroDef.UseWhen.ALLY_HEALTH_BELOW : HeroDef.UseWhen.ALWAYS,
                    a.useWhen(), f.id());
            assertEquals(f == Form.ZONE ? 1 : 0, a.delay(), f.id() + ": a zone at the target warns for 1 s");
            Loaded l = read(hero(skill("\"form\": \"" + f.id() + "\", " + all + "\"effects\": [" + fx + "]")));
            int unused = (fs.usesRange() && !fs.rangeFixed() ? 0 : 1) + (fs.usesRadius() ? 0 : 1)
                    + (fs.count() ? 0 : 1) + (fs.angle() ? 0 : 1) + (fs.width() ? 0 : 1) + (fs.hits() ? 0 : 2)
                    + (fs.projectile() ? 0 : 1) + (fs.zone() ? 0 : 4);
            assertEquals(unused, of(l, Problems.Code.FIELD_UNUSED).size(), f.id() + ":\n" + l.problems().report());
        }
        Loaded melee = read(hero(skill("\"form\": \"melee\", \"range\": 6, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}]")));
        assertEquals("a melee skill always reaches 3.5 blocks; \"range\" is fixed; it was ignored",
                one(melee, Problems.Code.FIELD_UNUSED).message());
        assertEquals(3.5, melee.hero("test_hero").skills().get(0).range());
        Ability selfZone = def(hero(skill("\"form\": \"zone\", \"zone_at\": \"follow_self\", \"effects\":"
                + " [{\"type\": \"damage\", \"amount\": 2}]"))).skills().get(0);
        assertEquals(0, selfZone.delay(), "a zone at the hero starts at once");
        Ability ult = def(hero("\"ultimate\": {\"name\": \"U\", \"form\": \"nova\", \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}]}")).ultimate();
        assertEquals(0.5, ult.windup());
        assertEquals(HeroDef.UseWhen.BIG_FIGHT, ult.useWhen());
        Loaded ultFields = read(hero("\"ultimate\": {\"name\": \"U\", \"form\": \"nova\", \"cooldown\": 5,"
                + " \"charges\": 2, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}, " + skill("\"form\":"
                + " \"self\", \"charge_rate\": 120, \"effects\": [{\"type\": \"cleanse\"}]")));
        assertEquals(3, of(ultFields, Problems.Code.FIELD_ELSEWHERE).size());
    }

    @Test
    void useWhenValuesAndOutOfCombat() {
        HeroDef d = def(hero("\"skills\": [{\"name\": \"A\", \"form\": \"self\", \"use_when\": \"self_health_below\","
                + " \"effects\": [{\"type\": \"cleanse\"}]}, {\"name\": \"B\", \"form\": \"bolt\", \"use_when\":"
                + " \"target_far\", \"use_value\": 30, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]"));
        assertEquals(50, d.skills().get(0).useValue());
        assertEquals(16, d.skills().get(1).useValue(), "clamped to 4-16 blocks");
        Loaded ooc = read(hero(skill("\"form\": \"bolt\", \"use_when\": \"out_of_combat\", \"effects\": [{\"type\":"
                + " \"damage\", \"amount\": 2}]")));
        assertEquals(HeroDef.UseWhen.ALWAYS, ooc.hero("test_hero").skills().get(0).useWhen());
        one(ooc, Problems.Code.OUT_OF_COMBAT);
        Loaded friendly = read(hero(skill("\"form\": \"self\", \"use_when\": \"out_of_combat\", \"use_value\": 3,"
                + " \"effects\": [{\"type\": \"cleanse\"}]")));
        assertEquals(HeroDef.UseWhen.OUT_OF_COMBAT, friendly.hero("test_hero").skills().get(0).useWhen());
        assertTrue(one(friendly, Problems.Code.FIELD_UNUSED).message().contains("use_value"));
    }

    // ------------------------------------------------------------------ recipients (v1 13, 10.2)

    @Test
    void enemyOnlyEffectsStayOffAllies() {
        Loaded l = read(hero("\"ultimate\": {\"name\": \"Hold the Gate\", \"form\": \"allies\", \"effects\": ["
                + "{\"type\": \"shield\", \"amount\": 4}, {\"type\": \"damage\", \"amount\": 3}]}"));
        assertEquals("ultimate \"Hold the Gate\" > effect 2: \"damage\" only reaches enemies, but this ultimate affects"
                + " allies (form \"allies\"). Put it in an ability that hits enemies. Effect PART LEFT OUT.",
                one(l, Problems.Code.ENEMY_ONLY).format().substring("test_hero.json, line 1: ".length()));
        assertEquals(1, l.hero("test_hero").ultimate().effects().size());
        for (String to : List.of("self", "allies", "ally")) {
            Loaded t = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                    + " {\"type\": \"stun\", \"to\": \"" + to + "\"}]")));
            assertTrue(one(t, Problems.Code.ENEMY_ONLY).message().contains("\"to\": \"" + to + "\""), to);
        }
        Loaded harmful = read(hero(skill("\"form\": \"allies\", \"effects\": [{\"type\": \"heal\", \"amount\": 2},"
                + " {\"type\": \"stat\", \"stat\": \"damage_taken\", \"percent\": 10, \"seconds\": 4}, {\"type\":"
                + " \"effect\", \"id\": \"weakness\", \"seconds\": 3}]")));
        assertEquals(2, of(harmful, Problems.Code.ENEMY_ONLY).size(), "+10% damage taken and weakness are harmful");
    }

    @Test
    void allyOnlyEffectsInAnEnemyFormGoToTheHero() {
        Loaded l = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                + " {\"type\": \"shield\", \"amount\": 3}, {\"type\": \"heal\", \"to\": \"allies\", \"amount\": 2},"
                + " {\"type\": \"stat\", \"stat\": \"damage_taken\", \"percent\": -10, \"seconds\": 4}]")));
        List<Effect> fx = l.hero("test_hero").skills().get(0).effects();
        assertEquals(To.SELF, fx.get(1).c().to(), "\"hit them and shield myself\"");
        assertEquals(To.ALLIES, fx.get(2).c().to());
        assertEquals(To.SELF, fx.get(3).c().to(), "-10% damage taken is helpful");
        assertEquals(2, of(l, Problems.Code.ALLY_TO_SELF).size());
        Loaded selfOnly = read(hero(skill("\"form\": \"bolt\", \"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                + " {\"type\": \"cooldown_cut\", \"seconds\": 2, \"to\": \"allies\"}, {\"type\": \"move_self\","
                + " \"to\": \"self\"}]")));
        assertEquals("\"cooldown_cut\" always acts on the hero; \"to\": \"allies\" was ignored",
                one(selfOnly, Problems.Code.SELF_ONLY_TO).message());
        assertEquals(To.SELF, selfOnly.hero("test_hero").skills().get(0).effects().get(1).c().to());
        Loaded marks = read(hero("\"skills\": [{\"name\": \"A\", \"form\": \"self\", \"effects\": [{\"type\": \"mark\","
                + " \"mark\": \"focus\"}]}, {\"name\": \"B\", \"form\": \"bolt\", \"effects\": [{\"type\": \"mark\","
                + " \"mark\": \"focus\"}, {\"type\": \"mark\", \"mark\": \"focus\", \"to\": \"self\"}, {\"type\":"
                + " \"clear_marks\", \"mark\": \"focus\", \"to\": \"allies\"}]}, {\"name\": \"C\", \"form\": \"ally\","
                + " \"effects\": [{\"type\": \"heal\", \"amount\": 1}, {\"type\": \"mark\", \"mark\": \"focus\"}]}]"));
        HeroDef d = marks.hero("test_hero");
        assertEquals(To.SELF, d.skills().get(0).effects().get(0).c().to(), "a mark in a self form is on the hero");
        assertEquals(To.TARGETS, d.skills().get(1).effects().get(0).c().to());
        assertEquals(To.SELF, d.skills().get(1).effects().get(1).c().to());
        assertEquals(2, of(marks, Problems.Code.MARK_RECEIVER).size(), "clear_marks to allies; a mark on an ally");
        Loaded invis = read(hero("\"skills\": [{\"name\": \"A\", \"form\": \"self\", \"effects\": [{\"type\": \"effect\","
                + " \"id\": \"invisibility\", \"seconds\": 4}]}, {\"name\": \"B\", \"form\": \"allies\", \"effects\":"
                + " [{\"type\": \"effect\", \"id\": \"invisibility\", \"seconds\": 4}, {\"type\": \"cleanse\"}]}]"));
        assertEquals(1, of(invis, Problems.Code.SELF_POTION).size(), "only the hero may vanish");
    }

    // ------------------------------------------------------------------ passives (v1 17)

    @Test
    void triggersAndFormsPairAsTheTableSays() {
        for (Trigger t : HeroDef.Word.inFormat(Trigger.class, 1)) { // format 1's triggers
            HeroCatalogue.TriggerSpec ts = HeroCatalogue.TRIGGERS.get(t);
            for (PassiveForm f : PassiveForm.values()) {
                String fx = t == Trigger.ALWAYS ? "{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 10}"
                        : f.friendly() ? "{\"type\": \"cleanse\"}" : "{\"type\": \"damage\", \"amount\": 1}";
                Loaded l = read(hero("\"passives\": [{\"name\": \"P\", \"trigger\": \"" + t.id() + "\", \"form\": \""
                        + f.id() + "\"" + (t == Trigger.ON_MARK_FULL ? ", \"mark\": \"mk\"" : "") + ", \"effects\": ["
                        + fx + "]}]"));
                boolean ok = ts.forms().contains(f);
                assertEquals(ok ? 1 : 0, l.hero("test_hero").passives().size(), t.id() + " / " + f.id() + ":\n"
                        + l.problems().report());
                assertEquals(ok ? 0 : 1, of(l, Problems.Code.TRIGGER_FORM).size(), t.id() + " / " + f.id());
            }
            HeroDef d = def(hero("\"passives\": [{\"name\": \"P\", \"trigger\": \"" + t.id() + "\""
                    + (t == Trigger.ON_MARK_FULL ? ", \"mark\": \"mk\"" : "") + ", \"effects\": [" + (t == Trigger.ALWAYS
                    ? "{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 10}" : "{\"type\": \"cleanse\"}") + "]}]"));
            if (ts.defaultForm().friendly() || t == Trigger.ALWAYS) {
                assertEquals(ts.defaultForm(), d.passives().get(0).form(), t.id());
            }
        }
    }

    @Test
    void passiveValuesMarksAndTheAlwaysRule() {
        HeroDef d = def(hero("\"passives\": [{\"name\": \"Low\", \"trigger\": \"on_low_health\", \"effects\":"
                + " [{\"type\": \"cleanse\"}]}, {\"name\": \"Tick\", \"trigger\": \"every\", \"trigger_value\": 8,"
                + " \"form\": \"nova\", \"effects\": [{\"type\": \"knockback\"}]}, {\"name\": \"Hold\", \"trigger\":"
                + " \"always\", \"if\": {\"condition\": \"self_health_below\", \"value\": 50}, \"effects\":"
                + " [{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 15}]}, {\"name\": \"Full\", \"trigger\":"
                + " \"on_mark_full\", \"mark\": \"Toll\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}],"
                + " \"basic_attack\": {\"style\": \"melee\", \"on_hit\": [{\"type\": \"mark\", \"mark\": \"toll\"}]}")
                .replace("\"basic_attack\": {\"style\": \"melee\"}, ", ""));
        assertEquals(35, d.passives().get(0).triggerValue());
        assertEquals(8, d.passives().get(1).triggerValue());
        assertEquals(4, d.passives().get(1).radius(), "nova default radius");
        assertEquals(0, ((HeroDef.StatMod) d.passives().get(2).effects().get(0)).seconds(), "held");
        assertEquals(HeroDef.ConditionType.SELF_HEALTH_BELOW, d.passives().get(2).cond().type());
        assertEquals("toll", d.passives().get(3).mark());
        assertEquals("my_mark", HeroReader.normaliseId(" My-Mark "), "mark ids are made regular like hero ids");
        assertEquals("burnchilljson", HeroReader.normaliseId("burn/chill.json"), "but they are not file names");
        Loaded notOwn = read(hero("\"passives\": [{\"name\": \"Full\", \"trigger\": \"on_mark_full\", \"mark\":"
                + " \"toll\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]"));
        one(notOwn, Problems.Code.MARK_NOT_OWN);
        one(notOwn, Problems.Code.MARK_UNUSED);
        Loaded always = read(hero("\"passives\": [{\"name\": \"A\", \"trigger\": \"always\", \"effects\":"
                + " [{\"type\": \"heal\", \"amount\": 2}, {\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 10}]}]"));
        one(always, Problems.Code.ALWAYS_STAT);
        assertEquals(1, always.hero("test_hero").passives().get(0).effects().size());
        Loaded unused = read(hero("\"passives\": [{\"name\": \"E\", \"trigger\": \"every\", \"cooldown\": 3, \"mark\":"
                + " \"x\", \"trigger_value\": 5, \"projectile\": \"arrow\", \"effects\": [{\"type\": \"cleanse\"}]},"
                + " {\"name\": \"H\", \"trigger\": \"on_hit\", \"trigger_value\": 5, \"radius\": 3, \"effects\":"
                + " [{\"type\": \"damage\", \"amount\": 1}]}]"));
        assertEquals(5, of(unused, Problems.Code.FIELD_UNUSED).size(), unused.problems().report());
    }

    @Test
    void conditionsNeedTheirValues() {
        HeroDef d = def(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2, \"if\":"
                + " {\"condition\": \"target_is\", \"kind\": \"undead\"}}, {\"type\": \"stun\", \"if\": {\"condition\":"
                + " \"target_has_mark\", \"mark\": \"mk\", \"value\": 30}}]")));
        HeroDef.Condition c = d.skills().get(0).effects().get(0).c().cond();
        assertEquals(HeroDef.TargetKind.UNDEAD, c.kind());
        assertEquals(10, d.skills().get(0).effects().get(1).c().cond().value(), "mark stacks clamp to 1-10");
        for (String bad : List.of("{\"condition\": \"target_health_below\"}", "{\"condition\": \"self_has_mark\"}",
                "{\"condition\": \"target_is\"}")) {
            Loaded l = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2},"
                    + " {\"type\": \"stun\", \"if\": " + bad + "}]")));
            assertEquals(Severity.PART_LEFT_OUT, one(l, Problems.Code.CONDITION_INCOMPLETE).severity(), bad);
            assertEquals(1, l.hero("test_hero").skills().get(0).effects().size(), "the effect is left out: " + bad);
        }
    }

    @Test
    void amountsReadEveryPart() {
        HeroDef d = def(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": {\"base\": 1,"
                + " \"attack_percent\": 50, \"max_health_percent\": 5, \"armor_percent\": 10,"
                + " \"target_max_health_percent\": 2, \"target_missing_health_percent\": 3, \"mark\": \"mk\","
                + " \"per_mark_stack\": 0.5}}, {\"type\": \"mark\", \"mark\": \"mk\"}]")));
        HeroDef.Amount a = ((HeroDef.Damage) d.skills().get(0).effects().get(0)).amount();
        assertEquals(new HeroDef.Amount(1, 50, 5, 10, 2, 3, "mk", 0.5), a);
        Loaded half = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\":"
                + " {\"base\": 2, \"per_mark_stack\": 1}}]")));
        assertEquals(Severity.ADJUSTED, one(half, Problems.Code.AMOUNT_MARK).severity());
        assertEquals(HeroDef.Amount.of(2), ((HeroDef.Damage) half.hero("test_hero").skills().get(0).effects().get(0))
                .amount());
        Loaded empty = read(hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": {}},"
                + " {\"type\": \"knockback\"}]")));
        assertEquals(Severity.PART_LEFT_OUT, one(empty, Problems.Code.MISSING).severity());
    }

    @Test
    void onlyAThrownBasicAttackHasAnElementOfItsOwn() {
        HeroDef melee = def(hero("\"element\": \"fire\", " + skill("\"form\": \"melee\", \"effects\": [{\"type\":"
                + " \"damage\", \"amount\": 2}]")));
        assertEquals(HeroDef.Element.FIRE, melee.element());
        assertEquals(HeroDef.Element.PHYSICAL, melee.basicAttack().element(), "melee and arrow hits stay physical");
        assertEquals(HeroDef.Element.FIRE, ((HeroDef.Damage) melee.skills().get(0).effects().get(0)).element(),
                "an effect takes the hero's element");
        String thrown = hero("\"element\": \"fire\"").replace("\"style\": \"melee\"", "\"style\": \"thrown\"");
        assertEquals(HeroDef.Element.FIRE, def(thrown).basicAttack().element(), "a thrown attack takes the hero's");
        assertEquals(HeroDef.Element.FROST, def(thrown.replace("\"style\": \"thrown\"", "\"style\": \"thrown\","
                + " \"element\": \"frost\"")).basicAttack().element(), "or its own");
        assertEquals("snowball", def(thrown).basicAttack().projectile());
        assertEquals(4, def(thrown).basicAttack().amount());
        assertEquals(1.5, def(thrown).basicAttack().interval());
    }

    // ------------------------------------------------------------------ placeholders (v1 23)

    @Test
    void linesKeepTheirPlaceholdersOrSaySomeone() {
        Loaded l = read(hero("\"lines\": {\"kill\": [\"Down, {enemy}.\", \"Got {target}!\", \"Bye {player}.\"],"
                + " \"level_up\": \"Level {level}!\", \"fallen\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\","
                + " \"h\", \"i\"], \"death\": [\"j\"]}"));
        Map<LineEvent, List<String>> lines = l.hero("test_hero").lines();
        assertEquals(List.of("Down, {enemy}.", "Bye someone."), lines.get(LineEvent.KILL));
        assertEquals(List.of("Level {level}!"), lines.get(LineEvent.LEVEL_UP));
        assertEquals(8, lines.get(LineEvent.FALLEN).size(), "at most 8 per event, synonyms included");
        assertEquals(2, of(l, Problems.Code.LIST_TOO_LONG).size());
        assertEquals("{target} is not a placeholder. Allowed for \"kill\": {name}, {enemy}",
                one(l, Problems.Code.LINE_PLACEHOLDER).message());
        one(l, Problems.Code.LINE_SOMEONE);
        one(l, Problems.Code.LIST_WRAPPED);
        Loaded cut = read(hero("\"lines\": {\"victory\": [\"" + "x".repeat(130) + "\"]}"));
        assertEquals(120, cut.hero("test_hero").lines().get(LineEvent.VICTORY).get(0).length());
        // placeholders are read in any case and kept in lower case
        Loaded caps = read(hero("\"lines\": {\"kill\": [\"{Enemy} is down, says {NAME}. {Target}?\", \"{Enemy}!\"]}"));
        assertEquals(List.of("{enemy}!"), caps.hero("test_hero").lines().get(LineEvent.KILL));
        assertEquals("{target} is not a placeholder. Allowed for \"kill\": {name}, {enemy}",
                one(caps, Problems.Code.LINE_PLACEHOLDER).message());
        // a cut never leaves half a character behind
        Loaded emoji = read(hero("\"title\": \"" + "a" + "\uD83D\uDE00".repeat(20) + "\""));
        String title = emoji.hero("test_hero").title();
        assertEquals(31, title.length(), "32 would end in the first half of a surrogate pair");
        assertFalse(Character.isHighSurrogate(title.charAt(title.length() - 1)));
    }

    // ------------------------------------------------------------------ gear (v1 7, Appendix B.4-B.5)

    @Test
    void gearFollowsTheAllowlistAndOneItemPerSlot() {
        Loaded l = read(hero("").replace("\"gear\": [\"iron_sword\"]", "\"gear\": [\"diamond_helmet\","
                + " \"netherite_sword\", \"stick\", \"iron_helmet\", \"golden_helmet\", \"minecraft:iron_axe\"]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(List.of("iron_helmet", "iron_axe"), d.gear().stream().map(HeroDef.GearItem::item).toList());
        assertEquals(2, of(l, Problems.Code.GEAR_TOO_GOOD).size());
        assertTrue(one(l, Problems.Code.GEAR_REFUSED).message().startsWith("\"stick\" is not a weapon or armour piece"));
        assertEquals("the head slot already holds \"iron_helmet\"; one item per slot",
                one(l, Problems.Code.GEAR_SLOT).message());
        assertEquals(HeroDef.GearSlot.MAINHAND, d.gear().get(1).slot());
        // the style decides the main hand
        Loaded bow = read(hero("").replace("\"style\": \"melee\"", "\"style\": \"bow\"").replace("\"iron_sword\"",
                "\"iron_sword\", \"shield\""));
        assertEquals(List.of("shield", "bow"), bow.hero("test_hero").gear().stream().map(HeroDef.GearItem::item)
                .toList());
        one(bow, Problems.Code.GEAR_STYLE);
        assertEquals("the kit has no bow for the bow style; \"bow\" was added", one(bow, Problems.Code.GEAR_ADDED)
                .message());
        Loaded two = read(hero("").replace("\"style\": \"melee\"", "\"style\": \"crossbow\"").replace("\"iron_sword\"",
                "\"iron_sword\", \"crossbow\""));
        assertEquals(List.of("crossbow"), two.hero("test_hero").gear().stream().map(HeroDef.GearItem::item).toList());
        assertTrue(one(two, Problems.Code.GEAR_SLOT).message().contains("\"crossbow\""), "the serving item wins");
        HeroDef thrown = def(hero("").replace("\"style\": \"melee\"", "\"style\": \"thrown\"").replace("[\"iron_sword\"]",
                "[]"));
        assertTrue(thrown.gear().isEmpty(), "a thrown style needs no weapon");
        Loaded gearless = read(hero("").replace("\"style\": \"melee\"", "\"style\": \"trident\""));
        assertEquals("trident", gearless.hero("test_hero").gear().get(0).item());
    }

    @Test
    void enchantmentsFollowVanillaRules() {
        Loaded l = read(hero("").replace("\"gear\": [\"iron_sword\"]", "\"gear\": [{\"item\": \"iron_sword\","
                + " \"name\": \"Edge\", \"enchantments\": {\"sharpness\": 2, \"smite\": 1, \"power\": 1, \"mending\": 1,"
                + " \"fortitude\": 1, \"knockback\": 1, \"fire_aspect\": 1, \"sweeping_edge\": 1}},"
                + " {\"item\": \"turtle_helmet\", \"enchantments\": {\"respiration\": 1, \"protection\": 3}}]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(Map.of("sharpness", 2, "knockback", 1, "fire_aspect", 1), d.gear().get(0).enchantments());
        assertEquals("Edge", d.gear().get(0).name());
        assertEquals("\"smite\" cannot be combined with \"sharpness\" (vanilla rules)",
                one(l, Problems.Code.ENCHANT_EXCLUSIVE).message());
        assertEquals("\"power\" does not go on \"iron_sword\" (vanilla rules)", one(l, Problems.Code.ENCHANT_ITEM)
                .message());
        assertTrue(one(l, Problems.Code.ENCHANT_REFUSED).message().contains("mending"));
        assertTrue(one(l, Problems.Code.WORD_PART).message().contains("\"fortitude\" is not an enchantment"));
        assertEquals("an item has at most 3 enchantments", one(l, Problems.Code.LIST_TOO_LONG).message());
        assertEquals(Map.of("respiration", 1, "protection", 1), d.gear().get(1).enchantments(), "3 clamped to 2, then the"
                + " kit's 7 levels lowered to 6 from the last");
        one(l, Problems.Code.ENCHANT_KIT);
        one(l, Problems.Code.CLAMPED);
        Loaded kit = read(hero("").replace("\"gear\": [\"iron_sword\"]", "\"gear\": [{\"item\": \"iron_sword\","
                + " \"enchantments\": {\"sharpness\": 2, \"unbreaking\": 3}}, {\"item\": \"iron_boots\","
                + " \"enchantments\": {\"feather_falling\": 2, \"depth_strider\": 2}}]"));
        HeroDef k = kit.hero("test_hero");
        int total = k.gear().stream().mapToInt(g -> g.enchantments().values().stream().mapToInt(i -> i).sum()).sum();
        assertEquals(6, total);
        assertEquals(Map.of("sharpness", 2, "unbreaking", 3), k.gear().get(0).enchantments(), "the last are lowered");
        assertEquals(Map.of("feather_falling", 1), k.gear().get(1).enchantments());
        one(kit, Problems.Code.ENCHANT_KIT);
        // every allowed enchantment goes on at least one allowed item kind, and unbreaking on all
        for (HeroCatalogue.Enchant e : HeroCatalogue.ENCHANTS.values()) {
            assertFalse(e.accepts().isEmpty());
        }
        assertEquals(HeroCatalogue.GearKind.values().length, HeroCatalogue.ENCHANTS.get("unbreaking").accepts().size());
    }

    // ------------------------------------------------------------------ files and lists

    @Test
    void theFileNameIsTheId() {
        assertEquals("bram_ostler", HeroReader.idFromFileName("heroes/my pack/Bram Ostler.json"));
        assertEquals("maudie_brine", HeroReader.idFromFileName("Maudie-Brine.JSON"));
        assertEquals("sla", HeroReader.idFromFileName("Séla!.json"), "other characters are dropped");
        Loaded l = HeroReader.read("Test Hero.json", hero(""));
        assertNotNull(l.hero("test_hero"));
        assertEquals("Test Hero.json: the file name was read as the id \"test_hero\". ADJUSTED.",
                one(l, Problems.Code.FILE_ID).format());
        Loaded other = HeroReader.read("other_hero.json", hero(""));
        assertNotNull(other.hero("other_hero"), "the file name wins over the id inside");
        assertTrue(one(other, Problems.Code.ID_MISMATCH).message().endsWith("the file name wins: \"other_hero\""));
        Loaded noId = read(hero("").replace("\"id\": \"test_hero\", ", ""));
        assertNotNull(noId.hero("test_hero"));
        one(noId, Problems.Code.ID_MISSING);
        Loaded bad = HeroReader.read("7.json", hero(""));
        assertTrue(bad.heroes().isEmpty());
        assertEquals(Severity.NOT_LOADED, one(bad, Problems.Code.FILE_BAD_NAME).severity());
    }

    @Test
    void aListOfHeroesInOneFile() {
        String a = hero("").replace("test_hero", "alpha_one");
        String b = hero("").replace("test_hero", "beta_two");
        Loaded l = HeroReader.read("pack.json", "[" + a + ", " + b + ", 5, " + hero("").replace("\"id\": \"test_hero\","
                + " ", "") + ", " + hero("").replace("test_hero", "Not Valid") + "]");
        assertEquals(List.of("alpha_one", "beta_two"), List.copyOf(l.heroes().keySet()));
        assertEquals("pack.json", l.files().get("beta_two"));
        one(l, Problems.Code.NOT_A_HERO);
        one(l, Problems.Code.LIST_HERO_NO_ID);
        one(l, Problems.Code.ID_INVALID);
        assertTrue(of(l, Problems.Code.FILE_ID).isEmpty(), "a list's file name is not an id");
        assertEquals(3, l.notLoaded().size());
    }

    @Test
    void duplicateIdsTheLimitAndStarters() {
        String h = hero("\"starter_for\": \"stoneguard\"");
        Loaded l = HeroReader.readAll(List.of(new Source("b/test_hero.json", h), new Source("a/test_hero.json", h),
                new Source("c/pack.json", "[" + h + "]")), Options.DEFAULT);
        assertEquals("a/test_hero.json", l.files().get("test_hero"), "the first in alphabetical order loads");
        List<Problem> dup = of(l, Problems.Code.DUPLICATE_ID);
        assertEquals(2, dup.size());
        assertEquals("b/test_hero.json: the id \"test_hero\" is already used by a/test_hero.json (the first file in"
                + " alphabetical order loads); this hero was not loaded. NOT LOADED.", dup.get(0).format());
        Loaded limit = HeroReader.readAll(List.of(new Source("aa_one.json", hero("")), new Source("bb_two.json",
                hero("")), new Source("cc_three.json", hero(""))), new Options(true, 2));
        assertEquals(List.of("aa_one", "bb_two"), List.copyOf(limit.heroes().keySet()));
        assertEquals(List.of("cc_three"), limit.notLoaded());
        one(limit, Problems.Code.TOO_MANY_HEROES);
        Loaded starters = HeroReader.readAll(List.of(new Source("zed_hero.json", h), new Source("amy_hero.json", h)),
                Options.DEFAULT);
        Problem s = one(starters, Problems.Code.STARTER_CONFLICT);
        assertEquals("zed_hero", s.hero());
        assertTrue(s.message().contains("\"amy_hero\" (the first by id)"));
        // a mark one hero checks and another applies is fine
        String checks = hero(skill("\"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2, \"if\":"
                + " {\"condition\": \"target_has_mark\", \"mark\": \"pinned\"}}]"));
        String applies = hero("\"basic_attack\": {\"style\": \"melee\", \"on_hit\": [{\"type\": \"mark\", \"mark\":"
                + " \"pinned\"}]}").replace("\"basic_attack\": {\"style\": \"melee\"}, ", "");
        assertEquals(1, of(HeroReader.read("checker_hero.json", checks), Problems.Code.MARK_UNUSED).size());
        Loaded both = HeroReader.readAll(List.of(new Source("checker_hero.json", checks), new Source("applier_hero.json",
                applies)), Options.DEFAULT);
        assertTrue(of(both, Problems.Code.MARK_UNUSED).isEmpty(), both.problems().report());
    }

    @Test
    void summonsCanBeSwitchedOff() {
        String h = hero("\"ultimate\": {\"name\": \"U\", \"form\": \"self\", \"effects\": [{\"type\": \"summon\","
                + " \"creature\": \"wolf\", \"count\": 2}, {\"type\": \"cleanse\"}]}");
        assertEquals(2, ((HeroDef.Summon) def(h).ultimate().effects().get(0)).count());
        Loaded off = HeroReader.read("test_hero.json", h, new Options(false, 64));
        one(off, Problems.Code.SUMMONS_OFF);
        assertEquals(1, off.hero("test_hero").ultimate().effects().size());
    }

    // ------------------------------------------------------------------ the examples (v1 31.1 HeroDocsTest's part)

    @Test
    void theThreeExamplesReadWithNoProblems() throws IOException {
        for (String id : List.of("bram_ostler", "sela_quickfern", "maudie_brine")) {
            Path p = Path.of("docs/heroes/examples/" + id + ".json");
            Loaded l = HeroReader.read(p.getFileName().toString(), Files.readString(p));
            assertTrue(l.problems().all().isEmpty(), id + ":\n" + l.problems().report());
            assertNotNull(l.hero(id));
        }
        HeroDef bram = HeroReader.read("bram_ostler.json", Files.readString(Path.of(
                "docs/heroes/examples/bram_ostler.json"))).hero("bram_ostler");
        assertEquals("Bram Ostler", bram.name());
        assertEquals("the Gatewarden", bram.title());
        assertEquals(0x8A7F70, bram.color());
        assertEquals("stoneguard", bram.starterFor());
        assertEquals(Map.of(HeroDef.Element.PHYSICAL, 10, HeroDef.Element.FROST, -10), bram.resistances());
        assertEquals(4, bram.basicAttack().everyNthHit().n());
        assertEquals(3, bram.skills().size());
        assertEquals("Iron Vigil", bram.empowered().name());
        assertEquals(2, bram.baseAbilities().size());
        assertEquals(14, bram.lines().size());
        assertEquals(HeroDef.Pose.BLOCK, bram.skills().get(0).look().pose());
        assertEquals(HeroDef.Pose.NONE, bram.skills().get(1).look().pose() == HeroDef.Pose.HORN ? HeroDef.Pose.NONE
                : bram.skills().get(1).look().pose());
    }

    @Test
    void aLoadedHeroCannotBeChanged() throws IOException {
        HeroDef d = HeroReader.read("bram_ostler.json", Files.readString(Path.of(
                "docs/heroes/examples/bram_ostler.json"))).hero("bram_ostler");
        List<Runnable> changes = List.of(
                () -> d.factions().add("x"), () -> d.gear().clear(), () -> d.skills().clear(),
                () -> d.passives().clear(), () -> d.baseAbilities().clear(),
                () -> d.resistances().put(HeroDef.Element.FIRE, 60), () -> d.lines().clear(),
                () -> d.lines().get(LineEvent.KILL).add("x"), () -> d.gear().get(0).enchantments().put("sharpness", 2),
                () -> d.basicAttack().onHit().clear(), () -> d.basicAttack().everyNthHit().effects().clear(),
                () -> d.skills().get(0).effects().clear(), () -> d.ultimate().effects().clear(),
                () -> d.passives().get(0).effects().clear(), () -> d.empowered().stats().clear(),
                () -> d.empowered().onHit().clear(),
                () -> ((HeroDef.Aura) d.baseAbilities().get(0)).roles().clear());
        for (int i = 0; i < changes.size(); i++) {
            Runnable r = changes.get(i);
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, r::run, "change " + i);
        }
        // and whoever builds one by hand gets copies too
        List<Effect> mine = new java.util.ArrayList<>(d.skills().get(0).effects());
        Ability copy = d.skills().get(0).withEffects(mine);
        mine.clear();
        assertEquals(3, copy.effects().size());
        java.util.Map<String, Integer> ench = new java.util.LinkedHashMap<>();
        ench.put("unbreaking", 1);
        ench.put("sharpness", 1);
        HeroDef.GearItem g = new HeroDef.GearItem("iron_sword", null, ench, HeroDef.GearSlot.MAINHAND);
        ench.clear();
        assertEquals(List.of("unbreaking", "sharpness"), List.copyOf(g.enchantments().keySet()), "in the file's order");
    }

    // ------------------------------------------------------------------ reports

    @Test
    void theReportGroupsByFileWorstFirst() {
        Loaded l = HeroReader.readAll(List.of(new Source("zz_hero.json", hero("\"stats\": {\"health\": 99}")),
                new Source("aa_hero.json", hero("").replace("\"name\": \"Test Hero\", ", ""))), Options.DEFAULT);
        String r = l.problems().report();
        assertTrue(r.startsWith("== aa_hero.json: 2 problems\n"), r);
        assertTrue(r.indexOf("NOT LOADED") < r.indexOf("ADJUSTED"), "NOT LOADED first");
        assertTrue(r.contains("== zz_hero.json: 2 problems\n"), r);
        assertEquals("Heroes: 1 loaded (1 adjusted), 1 not loaded", l.problems().summary(l.heroes().keySet(),
                l.notLoaded()));
        Problem p = l.problems().all().get(0);
        assertTrue(p.log().startsWith("[Heroes] "), p.log());
    }
}
