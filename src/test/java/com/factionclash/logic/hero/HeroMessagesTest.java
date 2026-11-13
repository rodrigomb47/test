package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.factionclash.logic.hero.HeroReaderTest.MIN;
import static com.factionclash.logic.hero.HeroReaderTest.hero;
import static com.factionclash.logic.hero.HeroReaderTest.skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every message the hero checker can write has a case here that makes it (QUALITY rule: "every validation message
 * has a test"): one small file per {@link Problems.Code} and {@link HeroJson.Code}, its outcome, and a piece of its
 * text. {@link #everyCodeHasACase} fails when a code is added without one.
 */
class HeroMessagesTest {
    private record Case(Severity severity, String text, Supplier<Loaded> input) {
    }

    private static final Map<String, Case> CASES = new LinkedHashMap<>();

    private static void c(Problems.Code code, Severity s, String text, Supplier<Loaded> in) {
        CASES.put(code.id(), new Case(s, text, in));
    }

    private static void j(HeroJson.Code code, Severity s, String text, String file) {
        CASES.put("json." + code.name().toLowerCase(Locale.ROOT), new Case(s, text, () -> read(file)));
    }

    private static Loaded read(String text) {
        return HeroReader.read("test_hero.json", text);
    }

    private static Loaded readAs(String file, String text) {
        return HeroReader.read(file, text);
    }

    private static Loaded withBasic(String basic) {
        return read(MIN.replace("\"basic_attack\": {\"style\": \"melee\"}, ", "") + ", \"basic_attack\": " + basic
                + "}");
    }

    private static Loaded skillOf(String fields) {
        return read(hero(skill(fields)));
    }

    private static Loaded meleeFx(String effects) {
        return skillOf("\"form\": \"melee\", \"cooldown\": 60, \"effects\": [" + effects + "]");
    }

    private static Loaded passive(String fields) {
        return read(hero("\"passives\": [{\"name\": \"P\", " + fields + "}]"));
    }

    private static String manyUnknown(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(", \"zz_").append(i).append("\": 1");
        }
        return sb.toString();
    }

    /** A dash move {@code id} with a followup {@code to} (none when null), for strings of followups. */
    private static String chain(String id, String to) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"form\": \"dash\", \"effects\": [{\"type\":"
                + " \"damage\", \"amount\": 2}]" + (to == null ? "" : ", \"followups\": [\"" + to + "\"]") + "}";
    }

    private static Loaded gear(String items) {
        return read(hero("").replace("\"gear\": [\"iron_sword\"]", "\"gear\": [" + items + "]"));
    }

    static {
        Severity NL = Severity.NOT_LOADED, PL = Severity.PART_LEFT_OUT, AD = Severity.ADJUSTED, NO = Severity.NOTE;
        // files and the core
        c(Problems.Code.FILE_ID, AD, "the file name was read as the id \"test_hero\"",
                () -> readAs("Test Hero.json", hero("")));
        c(Problems.Code.FILE_BAD_NAME, NL, "the file name gives no hero id", () -> readAs("x!.json", hero("")));
        c(Problems.Code.NOT_A_HERO, NL, "item 1 of the list is a number", () -> readAs("pack.json", "[5]"));
        c(Problems.Code.LIST_HERO_NO_ID, NL, "a hero in a list needs its own \"id\"",
                () -> readAs("pack.json", "[" + hero("").replace("\"id\": \"test_hero\", ", "") + "]"));
        c(Problems.Code.FORMAT_MISSING, NL, "\"format\" is missing", () -> read(hero("").replace("\"format\": 1, ",
                "")));
        c(Problems.Code.FORMAT_NEWER, NL, "for a newer Faction Clash", () -> read(hero("").replace("\"format\": 1",
                "\"format\": 3")));
        c(Problems.Code.FORMAT_INVALID, NL, "is not a format", () -> read(hero("").replace("\"format\": 1",
                "\"format\": \"new\"")));
        c(Problems.Code.ID_MISSING, AD, "\"id\" is missing; the file name gives it",
                () -> read(hero("").replace("\"id\": \"test_hero\", ", "")));
        c(Problems.Code.ID_MISMATCH, AD, "the file name wins", () -> read(hero("").replace("\"id\": \"test_hero\"",
                "\"id\": \"other\"")));
        c(Problems.Code.ID_INVALID, NL, "is not a hero id", () -> readAs("pack.json", "[" + hero("").replace(
                "test_hero", "X") + "]"));
        c(Problems.Code.MISSING, NL, "\"name\" is missing", () -> read(hero("").replace("\"name\": \"Test Hero\", ",
                "")));
        c(Problems.Code.TOO_MANY_PROBLEMS, NO, "more problems of this hero are not listed (only the first 200 are)",
                () -> read(hero("\"zz_0\": 1" + manyUnknown(240))));
        // fields and values
        c(Problems.Code.UNKNOWN_FIELD, NO, "\"wibble\" is not a field here", () -> read(hero("\"wibble\": 1")));
        c(Problems.Code.MISSPELLED_FIELD, AD, "\"elemnt\" was read as \"element\"", () -> read(hero("\"elemnt\":"
                + " \"fire\"")));
        c(Problems.Code.FIELD_ELSEWHERE, NO, "is only used by the ultimate", () -> skillOf("\"form\": \"self\","
                + " \"charge_rate\": 120, \"effects\": [{\"type\": \"cleanse\"}]"));
        c(Problems.Code.FIELD_UNUSED, NO, "\"angle\" is not used by form \"bolt\"", () -> skillOf("\"form\": \"bolt\","
                + " \"angle\": 40, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"));
        c(Problems.Code.NULL_FIELD, NO, "\"title\" is null, which means nothing; it was read as left out",
                () -> read(hero("\"title\": null")));
        c(Problems.Code.WRONG_TYPE, AD, "should be a whole percent, not true", () -> read(hero("\"stats\": {\"speed\":"
                + " true}")));
        c(Problems.Code.TEXT_NUMBER, AD, "\"105%\" was read as 105", () -> read(hero("\"stats\": {\"speed\":"
                + " \"105%\"}")));
        c(Problems.Code.TEXT_BOOL, AD, "\"false\" was read as false", () -> read(hero("\"empowered\": {\"name\": \"E\","
                + " \"skills_locked\": \"false\"}")));
        c(Problems.Code.CLAMPED, AD, "is above its range", () -> read(hero("\"stats\": {\"speed\": 150}")));
        c(Problems.Code.ROUNDED, AD, "is not a whole number", () -> read(hero("\"stats\": {\"speed\": 100.4}")));
        c(Problems.Code.SYNONYM, AD, "\"tank\" was read as \"vanguard\"", () -> read(hero("").replace("\"striker\"",
                "\"tank\"")));
        c(Problems.Code.EQUAL_TREATMENT, AD, "\"players\" was read as \"allies\": heroes treat players and NPCs alike",
                () -> read(hero("").replace("\"striker\"}", "\"striker\", \"protects\": \"players\"}")));
        c(Problems.Code.WORD_DEFAULT, AD, "\"wind\" is not an element", () -> read(hero("\"element\": \"wind\"")));
        c(Problems.Code.WORD_PART, PL, "\"warp\" is not a form", () -> skillOf("\"form\": \"warp\", \"effects\":"
                + " [{\"type\": \"cleanse\"}]"));
        c(Problems.Code.WORD_CORE, NL, "\"wizard\" is not a combat role", () -> read(hero("").replace("\"striker\"",
                "\"wizard\"")));
        c(Problems.Code.TEXT_CLEANED, AD, "formatting codes", () -> read(hero("\"title\": \"§cRed\"")));
        c(Problems.Code.TEXT_CUT, AD, "at most 32. Cut to 32", () -> read(hero("\"title\": \"" + "x".repeat(40)
                + "\"")));
        c(Problems.Code.MARK_RENAMED, AD, "\"Pinned\" was read as \"pinned\"", () -> meleeFx("{\"type\": \"damage\","
                + " \"amount\": 2}, {\"type\": \"mark\", \"mark\": \"Pinned\"}"));
        c(Problems.Code.MARK_INVALID, PL, "is not a mark id", () -> meleeFx("{\"type\": \"damage\", \"amount\": 2},"
                + " {\"type\": \"mark\", \"mark\": \"9\"}"));
        c(Problems.Code.COLOR_INVALID, AD, "\"red\" is not a colour", () -> read(hero("\"color\": \"red\"")));
        c(Problems.Code.LIST_TOO_LONG, PL, "\"factions\" holds at most 4 items", () -> read(hero("\"factions\":"
                + " [\"stoneguard\", \"ember_legion\", \"tide_alliance\", \"grove_covenant\", \"stoneguard\"]")));
        c(Problems.Code.LIST_WRAPPED, AD, "read as a list of one", () -> skillOf("\"form\": \"melee\", \"effects\":"
                + " {\"type\": \"damage\", \"amount\": 2}"));
        c(Problems.Code.LIST_DUPLICATE, AD, "\"stoneguard\" is listed twice", () -> read(hero("\"factions\":"
                + " [\"stoneguard\", \"stoneguard\"]")));
        c(Problems.Code.CONDITION_INCOMPLETE, PL, "needs \"value\"", () -> meleeFx("{\"type\": \"damage\", \"amount\":"
                + " 2, \"if\": {\"condition\": \"self_health_below\"}}, {\"type\": \"knockback\"}"));
        c(Problems.Code.AMOUNT_MARK, AD, "go together", () -> meleeFx("{\"type\": \"damage\", \"amount\": {\"base\": 2,"
                + " \"mark\": \"pinned\"}}"));
        // sense rules
        c(Problems.Code.NO_EFFECTS, PL, "has no effect left that works", () -> skillOf("\"form\": \"self\","
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"));
        c(Problems.Code.ENEMY_ONLY, PL, "only reaches enemies", () -> skillOf("\"form\": \"allies\", \"effects\":"
                + " [{\"type\": \"heal\", \"amount\": 2}, {\"type\": \"stun\"}]"));
        c(Problems.Code.ALLY_TO_SELF, NO, "it goes to the hero", () -> meleeFx("{\"type\": \"damage\", \"amount\": 2},"
                + " {\"type\": \"heal\", \"amount\": 2}"));
        c(Problems.Code.SELF_ONLY_TO, NO, "always acts on the hero", () -> skillOf("\"form\": \"self\", \"effects\":"
                + " [{\"type\": \"move_self\", \"to\": \"allies\"}]"));
        c(Problems.Code.MARK_RECEIVER, PL, "marks go on enemies", () -> skillOf("\"form\": \"allies\", \"effects\":"
                + " [{\"type\": \"heal\", \"amount\": 2}, {\"type\": \"mark\", \"mark\": \"pinned\"}]"));
        c(Problems.Code.TWO_DAMAGE, PL, "at most 2 \"damage\" effects", () -> meleeFx("{\"type\": \"damage\","
                + " \"amount\": 1}, {\"type\": \"damage\", \"amount\": 1}, {\"type\": \"damage\", \"amount\": 1}"));
        c(Problems.Code.ONE_MOVE, PL, "at most 1 \"move_self\"", () -> skillOf("\"form\": \"self\", \"effects\":"
                + " [{\"type\": \"move_self\"}, {\"type\": \"move_self\", \"direction\": \"up\"}]"));
        c(Problems.Code.MOVE_IN_LEAP, PL, "cannot be used in a \"leap\" or a \"dash\"", () -> skillOf("\"form\":"
                + " \"dash\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}, {\"type\": \"move_self\"}]"));
        c(Problems.Code.EMPOWER_NO_STATE, PL, "needs an \"empowered\" state", () -> skillOf("\"form\": \"self\","
                + " \"effects\": [{\"type\": \"empower\", \"seconds\": 5}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.SUMMON_SLOT, PL, "only works in skills and the ultimate", () -> passive("\"trigger\":"
                + " \"on_kill\", \"effects\": [{\"type\": \"summon\", \"creature\": \"wolf\"}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.SUMMONS_OFF, PL, "summons are switched off", () -> HeroReader.read("test_hero.json",
                hero(skill("\"form\": \"self\", \"effects\": [{\"type\": \"summon\", \"creature\": \"wolf\"},"
                        + " {\"type\": \"cleanse\"}]")), new Options(false, 64)));
        c(Problems.Code.ENERGY_ON_ULTIMATE, PL, "cannot give energy", () -> passive("\"trigger\": \"on_ultimate\","
                + " \"effects\": [{\"type\": \"energy\", \"amount\": 10}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.SELF_POTION, PL, "is for the hero only", () -> skillOf("\"form\": \"allies\", \"effects\":"
                + " [{\"type\": \"effect\", \"id\": \"invisibility\", \"seconds\": 3}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.POTION_REFUSED, PL, "\"absorption\" is not available to heroes; use the \"shield\" effect",
                () -> skillOf("\"form\": \"self\", \"effects\": [{\"type\": \"effect\", \"id\": \"absorption\","
                        + " \"seconds\": 3}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.STAT_ZERO, PL, "a change of 0% does nothing", () -> skillOf("\"form\": \"self\", \"effects\":"
                + " [{\"type\": \"stat\", \"stat\": \"speed\", \"percent\": 0}, {\"type\": \"cleanse\"}]"));
        c(Problems.Code.STUN_ON_HIT, PL, "a stun is not allowed", () -> withBasic("{\"style\": \"melee\", \"on_hit\":"
                + " [{\"type\": \"stun\"}]}"));
        c(Problems.Code.ALWAYS_STAT, PL, "holds stat changes only", () -> passive("\"trigger\": \"always\","
                + " \"effects\": [{\"type\": \"heal\", \"amount\": 1}, {\"type\": \"stat\", \"stat\": \"armor\","
                + " \"percent\": 5}]"));
        c(Problems.Code.TRIGGER_FORM, PL, "does not fit trigger", () -> passive("\"trigger\": \"on_kill\", \"form\":"
                + " \"bolt\", \"effects\": [{\"type\": \"damage\", \"amount\": 1}]"));
        c(Problems.Code.MARK_REQUIRED, PL, "needs \"mark\"", () -> passive("\"trigger\": \"on_mark_full\","
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 1}]"));
        c(Problems.Code.MARK_NOT_OWN, NO, "no effect of this hero applies \"pinned\"", () -> passive("\"trigger\":"
                + " \"on_mark_full\", \"mark\": \"pinned\", \"effects\": [{\"type\": \"damage\", \"amount\": 1}]"));
        c(Problems.Code.OUT_OF_COMBAT, AD, "read as \"always\"", () -> skillOf("\"form\": \"bolt\", \"use_when\":"
                + " \"out_of_combat\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]"));
        c(Problems.Code.LIGHTNING_ULTIMATE, NO, "for ultimates only", () -> skillOf("\"form\": \"self\", \"effects\":"
                + " [{\"type\": \"cleanse\"}], \"look\": {\"lightning\": true}"));
        c(Problems.Code.FANGS_FORM, NO, "fangs rise only along forms line, nova and zone", () -> skillOf("\"form\":"
                + " \"bolt\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}], \"look\": {\"fangs\": true}"));
        c(Problems.Code.ROLE_STYLE, NO, "a marksman that fights in melee; is that intended?",
                () -> read(hero("").replace("\"striker\"", "\"marksman\"")));
        c(Problems.Code.SIZE_CAP, NO, "the hero grows to 110% at most", () -> read(hero("\"stats\": {\"size\": 108},"
                + " \"empowered\": {\"name\": \"E\", \"size\": 105}")));
        c(Problems.Code.AURA_AFFIX, PL, "use a passive or a stat instead", () -> read(hero("\"base_abilities\":"
                + " [{\"type\": \"aura\", \"affix\": \"vitality\"}]")));
        // gear
        c(Problems.Code.GEAR_REFUSED, PL, "is not a weapon or armour piece", () -> gear("\"iron_sword\", \"elytra\""));
        c(Problems.Code.GEAR_TOO_GOOD, PL, "heroes start with iron gear at most", () -> gear("\"iron_sword\","
                + " \"diamond_boots\""));
        c(Problems.Code.GEAR_SLOT, PL, "one item per slot", () -> gear("\"iron_sword\", \"iron_boots\","
                + " \"golden_boots\""));
        c(Problems.Code.GEAR_ADDED, AD, "\"stone_sword\" was added", () -> gear(""));
        c(Problems.Code.GEAR_STYLE, PL, "a melee hero holds a sword, an axe or a mace in its main hand, not \"bow\"",
                () -> gear("\"bow\""));
        c(Problems.Code.ENCHANT_REFUSED, PL, "\"looting\" is not allowed for heroes", () -> gear("{\"item\":"
                + " \"iron_sword\", \"enchantments\": {\"looting\": 1}}"));
        c(Problems.Code.ENCHANT_ITEM, PL, "\"respiration\" does not go on \"iron_boots\"", () -> gear("\"iron_sword\", {\"item\":"
                + " \"iron_boots\", \"enchantments\": {\"respiration\": 1}}"));
        c(Problems.Code.ENCHANT_EXCLUSIVE, PL, "cannot be combined with \"protection\"", () -> gear("\"iron_sword\","
                + " {\"item\": \"iron_helmet\", \"enchantments\": {\"protection\": 1, \"fire_protection\": 1}}"));
        c(Problems.Code.ENCHANT_KIT, AD, "the kit has 7 enchantment levels; at most 6", () -> gear("{\"item\":"
                + " \"iron_sword\", \"enchantments\": {\"sharpness\": 2, \"unbreaking\": 3}}, {\"item\": \"iron_boots\","
                + " \"enchantments\": {\"feather_falling\": 2}}"));
        // lines
        c(Problems.Code.LINE_EVENT, PL, "\"wibble\" is not a line event", () -> read(hero("\"lines\": {\"wibble\":"
                + " [\"x\"]}")));
        c(Problems.Code.LINE_PLACEHOLDER, PL, "{target} is not a placeholder", () -> read(hero("\"lines\":"
                + " {\"kill\": [\"{target}!\"]}")));
        c(Problems.Code.LINE_SOMEONE, NO, "it reads \"someone\"", () -> read(hero("\"lines\": {\"fallen\":"
                + " [\"{enemy}...\"]}")));
        c(Problems.Code.LINE_BREAK, AD, "they became spaces", () -> read(hero("\"lines\": {\"victory\": [\"a\\nb\"]}")));
        // balance
        c(Problems.Code.SLOT_CAP, AD, "the skill cap is 16. Scaled down to 16", () -> meleeFx("{\"type\": \"damage\","
                + " \"amount\": 25}"));
        c(Problems.Code.SHARED_ON_HIT, AD, "they share the on-hit cap of 4", () -> withBasic("{\"style\": \"melee\","
                + " \"on_hit\": [{\"type\": \"damage\", \"amount\": 3}]}, \"empowered\": {\"name\": \"E\", \"on_hit\":"
                + " [{\"type\": \"damage\", \"amount\": 3}]}"));
        c(Problems.Code.COOLDOWN_FLOOR, AD, "too short for a skill this strong", () -> skillOf("\"form\": \"melee\","
                + " \"cooldown\": 2, \"effects\": [{\"type\": \"damage\", \"amount\": 8}]"));
        c(Problems.Code.PASSIVE_FLOOR, AD, "too short for a passive this strong", () -> passive("\"trigger\":"
                + " \"on_hit\", \"cooldown\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 8}]"));
        c(Problems.Code.EVERY_FLOOR, AD, "too often for a passive this strong", () -> passive("\"trigger\":"
                + " \"every\", \"trigger_value\": 3, \"form\": \"nova\", \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 6}]"));
        c(Problems.Code.THROWN_RATE, AD, "is faster than a bow", () -> withBasic("{\"style\": \"thrown\", \"amount\": 8,"
                + " \"interval\": 1}"));
        c(Problems.Code.POTION_LEVEL, AD, "\"strength\" goes up to level 2", () -> skillOf("\"form\": \"self\","
                + " \"effects\": [{\"type\": \"effect\", \"id\": \"strength\", \"level\": 3, \"seconds\": 5}]"));
        c(Problems.Code.POTION_SECONDS, AD, "lasts at most 12 seconds", () -> skillOf("\"form\": \"self\","
                + " \"effects\": [{\"type\": \"effect\", \"id\": \"strength\", \"seconds\": 20}]"));
        c(Problems.Code.STAT_CAP, AD, "is outside -30% to +30%", () -> skillOf("\"form\": \"self\", \"effects\":"
                + " [{\"type\": \"stat\", \"stat\": \"attack_speed\", \"percent\": 50}]"));
        c(Problems.Code.SUMMON_COUNT, AD, "at most 1 iron_golem per cast", () -> skillOf("\"form\": \"self\","
                + " \"cooldown\": 60, \"effects\": [{\"type\": \"summon\", \"creature\": \"iron_golem\", \"count\": 2}]"));
        // across files
        c(Problems.Code.DUPLICATE_ID, NL, "is already used by a.json", () -> HeroReader.readAll(List.of(
                new Source("b.json", "[" + hero("") + "]"), new Source("a.json", "[" + hero("") + "]")), Options.DEFAULT));
        c(Problems.Code.STARTER_CONFLICT, NO, "already has a starter", () -> HeroReader.readAll(List.of(
                new Source("abc_hero.json", hero("\"starter_for\": \"stoneguard\"")),
                new Source("bcd_hero.json", hero("\"starter_for\": \"stoneguard\""))), Options.DEFAULT));
        c(Problems.Code.MARK_UNUSED, NO, "is never applied by any loaded hero", () -> meleeFx("{\"type\": \"damage\","
                + " \"amount\": 2}, {\"type\": \"clear_marks\", \"mark\": \"nobody\"}"));
        c(Problems.Code.TOO_MANY_HEROES, NL, "loads at most 1 heroes", () -> HeroReader.readAll(List.of(
                new Source("abc_hero.json", hero("")), new Source("bcd_hero.json", hero(""))), new Options(true, 1)));
        // format 2 (HEROES_V2 12 and 14.1)
        c(Problems.Code.V1_FIELD, NO, "its entries were read as moves", () -> read(V2Files.hero2("\"skills\":"
                + " [{\"name\": \"Jab\", \"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}]")));
        c(Problems.Code.SOURCE_ONLY, NO, "is a source field that herogen reads", () -> read(V2Files.hero2("\"art\":"
                + " {\"form\": \"adult\"}")));
        c(Problems.Code.UNLOCK_IGNORED, NO, "read and ignored: a hero has all of its abilities from level 1",
                () -> read(V2Files.withMove("\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"unlock_level\":"
                        + " 5, \"effects\": [{\"type\": \"damage\", \"amount\": 2}]")));
        c(Problems.Code.BUILTIN_MISSING, NO, "a built-in hero needs \"gender\"", () -> HeroReader.read("test_hero.json",
                V2Files.hero2(""), Options.BUILTIN));
        c(Problems.Code.COURT_DUTY, AD, "\"court\" is set by the game while the hero leads", () -> read(V2Files.hero2("")
                .replace("\"striker\"}", "\"striker\", \"duty\": \"court\"}")));
        c(Problems.Code.MOVE_ID, AD, "\"id\" is missing; the name gives it: \"jab\"", () -> read(V2Files.withMove(
                "\"name\": \"Jab\", \"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]")));
        c(Problems.Code.NAME_TAKEN, PL, "another move already has the id \"lunge\"", () -> read(V2Files.withMove(
                "\"id\": \"lunge\", \"name\": \"Again\", \"form\": \"melee\", \"effects\": [{\"type\": \"damage\","
                        + " \"amount\": 2}]")));
        c(Problems.Code.MOVE_ID_RESERVED, PL, "is a movement animation's name", () -> read(V2Files.withMove("\"id\":"
                + " \"leap\", \"name\": \"Leap\", \"form\": \"leap\", \"effects\": [{\"type\": \"damage\", \"amount\":"
                + " 2}]")));
        c(Problems.Code.UNKNOWN_REFERENCE, PL, "\"nope\" is not a move of this hero", () -> read(V2Files.hero2(
                "\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"reactions\": [{\"when\":"
                        + " \"target_blocking\", \"move\": \"nope\"}]}")));
        c(Problems.Code.ITEM_ID, PL, "is not an item id", () -> read(V2Files.hero2("\"personality\": {\"likes\":"
                + " [\"Not An Item!\"]}")));
        c(Problems.Code.SOUND_ID, AD, "is not a sound id", () -> read(V2Files.withMove("\"id\": \"jab\", \"name\":"
                + " \"Jab\", \"form\": \"melee\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}], \"look\":"
                + " {\"sound\": \"Not:A Sound\"}")));
        c(Problems.Code.CLASS_RAISED, AD, "class \"light\" is lighter than form \"cone\"'s \"area\"",
                () -> read(V2Files.withMove("\"id\": \"fan\", \"name\": \"Fan\", \"form\": \"cone\", \"class\":"
                        + " \"light\", \"effects\": [{\"type\": \"damage\", \"amount\": 2}]")));
        c(Problems.Code.FRAMES_RAISED, AD, "is shorter than a heavy move's 0.8 seconds", () -> read(V2Files.withMove(
                "\"id\": \"rush\", \"name\": \"Rush\", \"form\": \"dash\", \"frames\": {\"windup\": 0.3},"
                        + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}]")));
        c(Problems.Code.FRAMES_GENERATED, NO, "has no generated animation", () -> read(V2Files.hero2("\"moves\": ["
                + V2Files.LUNGE + "], \"generated\": {\"moves\": []}")));
        c(Problems.Code.TELEGRAPH_NONE, AD, "draws its ground mark over its wind-up", () -> read(V2Files.withMove(
                "\"id\": \"rush\", \"name\": \"Rush\", \"form\": \"dash\", \"telegraph\": \"none\", \"effects\":"
                        + " [{\"type\": \"damage\", \"amount\": 2}]")));
        c(Problems.Code.GRAB_RULE, AD, "a grab is always unblockable", () -> read(V2Files.withMove("\"id\": \"grip\","
                + " \"name\": \"Grip\", \"form\": \"grab\", \"unblockable\": false, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}]")));
        c(Problems.Code.ULTIMATE_LINE, NO, "an ultimate speaks a line as its wind-up starts", () -> read(V2Files.hero2(
                "\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"effects\": [{\"type\":"
                        + " \"damage\", \"amount\": 8}]}")));
        c(Problems.Code.FOLLOWUP_SELF, PL, "a move follows itself only when it is \"repeatable\": true",
                () -> read(V2Files.withMove("\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"effects\":"
                        + " [{\"type\": \"damage\", \"amount\": 2}], \"followups\": [\"jab\"]")));
        c(Problems.Code.FOLLOWUP_LOOP, PL, "followups may not form a loop", () -> read(V2Files.hero2("\"moves\": ["
                + "{\"id\": \"a1\", \"name\": \"A\", \"form\": \"dash\", \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}], \"followups\": [\"b1\"]}, {\"id\": \"b1\", \"name\": \"B\", \"form\": \"melee\","
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}], \"followups\": [\"a1\"]}]")));
        c(Problems.Code.FOLLOWUP_STRING, PL, "a string is at most 4 moves long", () -> read(V2Files.hero2("\"moves\":"
                + " [" + chain("m1", "m2") + ", " + chain("m2", "m3") + ", " + chain("m3", "m4") + ", " + chain("m4",
                "m5") + ", " + chain("m5", null) + "]")));
        c(Problems.Code.FOLLOWUP_ULTIMATE, PL, "is an ultimate, which fires on its energy", () -> read(V2Files.hero2(
                "\"moves\": [{\"id\": \"a1\", \"name\": \"A\", \"form\": \"dash\", \"effects\": [{\"type\": \"damage\","
                        + " \"amount\": 2}], \"followups\": [\"big\"]}], \"ultimate\": {\"id\": \"big\", \"name\": \"Big\","
                        + " \"form\": \"nova\", \"line\": \"Now!\", \"effects\": [{\"type\": \"damage\", \"amount\":"
                        + " 8}]}")));
        c(Problems.Code.NEVER_PICKED, NO, "weight 0 means it is never picked on its own", () -> read(V2Files.withMove(
                "\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"weight\": 0, \"effects\": [{\"type\":"
                        + " \"damage\", \"amount\": 2}]")));
        c(Problems.Code.PHASE_ORDER, AD, "the first phase begins at full health; 90 was read as 100",
                () -> read(V2Files.hero2("\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 90}]}")));
        c(Problems.Code.PHASE_REPEATED, PL, "another phase already begins at 50%", () -> read(V2Files.hero2("\"boss\":"
                + " {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 50}, {\"at\": 50}]}")));
        c(Problems.Code.PHASE_NO_CHANGE, NO, "phase 2 only changes numbers", () -> read(V2Files.hero2("\"boss\":"
                + " {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 50}]}")));
        c(Problems.Code.NO_WEAKNESS, NO, "the hero has no weakness", () -> read(V2Files.hero2("\"boss\": {\"phases\":"
                + " [{\"at\": 100}]}")));
        c(Problems.Code.NO_REACH, NO, "no far-band move and no gap closer", () -> read(V2Files.hero2("\"moves\":"
                + " [{\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\", \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}]}]")));
        c(Problems.Code.CHANNEL_NONE, NO, "but no move of this hero has \"channel\"", () -> read(V2Files.hero2(
                "\"boss\": {\"weak_points\": [\"channel\"], \"phases\": [{\"at\": 100}]}")));
        c(Problems.Code.REBIRTH_RARITY, AD, "is for legendary heroes only", () -> read(V2Files.hero2("\"boss\":"
                + " {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"rebirth\": true}")));
        c(Problems.Code.POISE_RANGE, AD, "poise is 0 (none) or 50 to 200", () -> read(V2Files.hero2("\"boss\":"
                + " {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"poise\": 30}")));
        c(Problems.Code.REMOVED_FIELD, NO, "a hero deals the same damage to players and NPCs", () -> read(V2Files.hero2(
                "\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}], \"damage_vs_players\": 150}")));
        c(Problems.Code.YIELD_WORD, PL, "heroes do not yield", () -> read(V2Files.hero2("\"personality\":"
                + " {\"when_losing\": \"yield\"}")));
        c(Problems.Code.YIELD_EVENT, NO, "heroes do not yield; use \"escape\", \"last_stand\" or \"fallen\"",
                () -> read(V2Files.hero2("\"lines\": {\"yield\": [\"I give up.\"]}")));
        c(Problems.Code.KIND_FLIP, NO, "a stoic usually fights to the death; with these traits this one escapes",
                () -> read(V2Files.hero2("\"personality\": {\"temperament\": \"stoic\", \"traits\": {\"caution\":"
                        + " 70}}")));
        c(Problems.Code.STANDS_IGNORED, NO, "a hero that fights to the death stands everywhere", () -> read(V2Files.hero2(
                "\"personality\": {\"temperament\": \"battle_maniac\", \"stands_for\": [\"home\"]}")));
        c(Problems.Code.LATE_LAST_PHASE, NO, "its last phase starts at 15%, under 18%", () -> read(V2Files.hero2(
                "\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 15, \"moves\":"
                        + " [\"lunge\"]}]}, \"moves\": [{\"id\": \"jab\", \"name\": \"Jab\", \"form\": \"melee\","
                        + " \"phases\": [1], \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}, " + V2Files.LUNGE
                        .replace("\"form\": \"dash\"", "\"form\": \"dash\", \"phases\": [2]") + "]")));
        c(Problems.Code.ESCAPE_SELF, PL, "an \"on_escape\" passive acts on the hero itself only",
                () -> read(V2Files.hero2("\"passives\": [{\"name\": \"Smoke\", \"trigger\": \"on_escape\","
                        + " \"effects\": [{\"type\": \"shield\", \"amount\": 2, \"to\": \"allies\"}]}]")));
        c(Problems.Code.TRAIT_DRIFT, AD, "aggression 100 is more than 20 from a tsundere's 65", () -> read(V2Files.hero2(
                "\"personality\": {\"temperament\": \"tsundere\", \"traits\": {\"aggression\": 100}}")));
        c(Problems.Code.LEADER_NAME_TAKEN, NO, "\"Shared Name\" is also a faction name of", () -> HeroReader.readAll(
                List.of(new Source("abc_hero.json", V2Files.hero2("\"leader\": {\"faction_names\": [\"Shared Name\","
                        + " \"Name A\"]}").replace("test_hero", "abc_hero")), new Source("bcd_hero.json",
                        V2Files.hero2("\"leader\": {\"faction_names\": [\"Shared Name\", \"Name B\"]}")
                                .replace("test_hero", "bcd_hero"))), Options.DEFAULT));
        c(Problems.Code.BANNER_MOJANG, PL, "the company-logo pattern is not for faction banners",
                () -> read(V2Files.hero2("\"leader\": {\"banner\": {\"base\": \"red\", \"patterns\": [{\"pattern\":"
                        + " \"mojang\", \"color\": \"black\"}, {\"pattern\": \"circle\", \"color\": \"white\"},"
                        + " {\"pattern\": \"border\", \"color\": \"white\"}]}}")));
        c(Problems.Code.LEADER_SHORT, NO, "a leader names 2 to 4 factions", () -> read(V2Files.hero2("\"leader\":"
                + " {\"faction_names\": [\"Only One\"]}")));
        c(Problems.Code.REALM_ONE, AD, "only one talent of a leader works in every base", () -> read(V2Files.hero2(
                "\"talents\": [{\"id\": \"drill\", \"realm\": true}, {\"id\": \"mentor\", \"realm\": true}]")));
        c(Problems.Code.RELATION_UNKNOWN, NO, "\"someone_else\" is not a loaded hero", () -> read(V2Files.hero2(
                "\"relationships\": [{\"hero\": \"someone_else\", \"kind\": \"rival\"}]")));
        c(Problems.Code.RELATION_SELF, PL, "a hero has no relationship with itself", () -> read(V2Files.hero2(
                "\"relationships\": [{\"hero\": \"test_hero\", \"kind\": \"rival\"}]")));
        c(Problems.Code.SIGNATURE_SHARED, NO, "share archetype, element, style, ultimate form and signature move",
                () -> HeroReader.readAll(List.of(new Source("abc_hero.json", V2Files.hero2("\"archetype\": \"cat\"")
                        .replace("test_hero", "abc_hero")), new Source("bcd_hero.json", V2Files.hero2("\"archetype\":"
                        + " \"cat\"").replace("test_hero", "bcd_hero"))), Options.DEFAULT));
        c(Problems.Code.MOBILITY_UNKNOWN, NO, "is not a mobility trait", () -> read(V2Files.hero2("\"mobility\":"
                + " {\"primary\": {\"trait\": \"teleporter\"}}")));
        c(Problems.Code.MOBILITY_ADJUSTED, AD, "tier 3 is for epic and legendary heroes", () -> read(V2Files.hero2(
                "\"mobility\": {\"primary\": {\"trait\": \"leaper\", \"tier\": 3}}")));
        c(Problems.Code.MOBILITY_CARRIER, NO, "needs a carrier its model can show", () -> read(V2Files.hero2(
                "\"mobility\": {\"primary\": {\"trait\": \"glider\"}}")));
        c(Problems.Code.DIVE_RULE, PL, "a dive needs an airborne mobility trait", () -> read(V2Files.withMove(
                "\"id\": \"swoop\", \"name\": \"Swoop\", \"form\": \"dive\", \"effects\": [{\"type\":"
                        + " \"damage\", \"amount\": 3}]")));
        c(Problems.Code.FLYERS_SHARE, NO, "8 of the 8 loaded heroes fly, more than 15%", () -> {
            List<Source> s = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                s.add(new Source("flyer_" + (char) ('a' + i) + ".json", V2Files.hero2("\"archetype\": \"angel\"")
                        .replace("test_hero", "flyer_" + (char) ('a' + i))));
            }
            return HeroReader.readAll(s, Options.BUILTIN);
        });
        // the JSON reader's own
        j(HeroJson.Code.BOM, NO, "byte-order mark", "\uFEFF" + hero(""));
        j(HeroJson.Code.CRLF, NO, "Windows line ends", hero("").replace(", ", ",\r\n"));
        j(HeroJson.Code.CODE_FENCE, NO, "code fence around the hero was removed", "```json\n" + hero("") + "\n```");
        j(HeroJson.Code.CODE_BLOCK, NO, "the text around it was ignored", "Here:\n```\n" + hero("") + "\n```\nBye");
        j(HeroJson.Code.COMMENTS, NO, "a comment was ignored", "// mine\n" + hero(""));
        j(HeroJson.Code.TRAILING_COMMA, NO, "a comma before } or ] was ignored", hero("\"factions\":"
                + " [\"stoneguard\",]"));
        j(HeroJson.Code.EMPTY, NL, "the file is empty", " ");
        j(HeroJson.Code.TOO_BIG, NL, "a hero file may be at most 128 KB", hero("\"_x\": \"" + "y".repeat(900)
                + "\"").repeat(1) + " ".repeat(140_000));
        j(HeroJson.Code.YAML, NL, "this looks like YAML", "format: 1\nname: Test");
        j(HeroJson.Code.NOT_JSON, NL, "a hero file starts with {", "Hero!");
        j(HeroJson.Code.MISSING_COMMA, NL, "a comma is missing before \"name\"", hero("").replace(", \"name\"",
                " \"name\""));
        j(HeroJson.Code.MISSING_COLON, NL, "a colon is missing after \"name\"", hero("").replace("\"name\":",
                "\"name\""));
        j(HeroJson.Code.KEY_QUOTES, NL, "must be in double quotes", hero("").replace("\"name\":", "name:"));
        j(HeroJson.Code.SINGLE_QUOTES, NL, "single quotes", hero("").replace("\"Test Hero\"", "'Test Hero'"));
        j(HeroJson.Code.CURLY_QUOTES, NL, "curly quotes", hero("").replace("\"Test Hero\"", "“Test Hero”"));
        j(HeroJson.Code.UNCLOSED_STRING, NL, "never closed: a closing \" is missing", "{\"name\": \"Test");
        j(HeroJson.Code.LONG_STRING, NL, "longer than 1000 characters", hero("\"description\": \"" + "z".repeat(1200)
                + "\""));
        j(HeroJson.Code.BAD_ESCAPE, NL, "is not a JSON escape", hero("\"title\": \"a\\qb\""));
        j(HeroJson.Code.UNQUOTED_TEXT, NL, "Text must be in double quotes", hero("\"element\": fire"));
        j(HeroJson.Code.BAD_NUMBER, NL, "is not a JSON number", hero("").replace("\"format\": 1", "\"format\": 01"));
        j(HeroJson.Code.UNCLOSED, NL, "is never closed", hero("").substring(0, hero("").length() - 1));
        j(HeroJson.Code.WRONG_CLOSE, NL, "closes the [", hero("\"factions\": [\"stoneguard\"}"));
        j(HeroJson.Code.TRAILING_TEXT, NL, "text after the end", hero("") + " {}");
        j(HeroJson.Code.DUPLICATE_KEY, NL, "\"name\" appears twice", hero("\"name\": \"Again\""));
        j(HeroJson.Code.TOO_DEEP, NL, "nested deeper than 8 levels", hero("\"_x\": [[[[[[[[1]]]]]]]]"));
        j(HeroJson.Code.UNCLOSED_COMMENT, NL, "never closed with */", "/* " + hero(""));
        j(HeroJson.Code.MISSING_VALUE, NL, "a value is missing before }", "{\"format\": }");
    }

    @Test
    void everyCodeHasACase() {
        Set<String> all = new TreeSet<>();
        for (Problems.Code c : Problems.Code.values()) {
            all.add(c.id());
        }
        for (HeroJson.Code c : HeroJson.Code.values()) {
            all.add("json." + c.name().toLowerCase(Locale.ROOT));
        }
        Set<String> missing = new TreeSet<>(all);
        missing.removeAll(CASES.keySet());
        assertTrue(missing.isEmpty(), "codes without a case: " + missing);
        assertEquals(all, new TreeSet<>(CASES.keySet()));
    }

    @TestFactory
    Stream<DynamicTest> eachCaseWritesItsMessage() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, Case> e : CASES.entrySet()) {
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                Loaded l = e.getValue().input.get();
                List<Problem> got = l.problems().all().stream().filter(p -> p.code().equals(e.getKey())).toList();
                assertTrue(!got.isEmpty(), e.getKey() + " was not reported:\n" + l.problems().report());
                Problem p = got.get(0);
                assertEquals(e.getValue().severity, p.severity(), p.format());
                assertTrue(p.message().contains(e.getValue().text), "\"" + e.getValue().text + "\" in: " + p.format());
                String f = p.format();
                assertTrue(f.endsWith(p.severity().label + "."), f);
                assertTrue(f.startsWith(p.file()), f);
                assertTrue(!p.message().isBlank(), f);
                assertTrue(!f.matches("(?s).*(?<!\\.)\\.\\.(?!\\.).*") && !f.contains("?.") && !f.contains(". ."),
                        "punctuation: " + f);
            }));
        }
        return tests.stream();
    }
}
