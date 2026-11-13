package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.BaseStyle;
import com.factionclash.logic.hero.HeroDef.Culture;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The base style families (HEROES_V2 5.3, Appendix B): each family's wall, frame, floor, roof (and its stairs),
 * perimeter blocks and seat noun, the milestone that builds it, the stone-like blocks added to
 * {@code MaterialRules.STONE_BLOCKS} so builders repair with substitutes, the leader's default family by archetype
 * (ROSTER_PLAN.md 2.3) and, until a family exists, the nearest built one by material colour. Block ids are vanilla
 * ids without the namespace. Pure, unit-tested against the appendix's table ({@code BaseStylesTest}); the builders
 * read it from L4.
 */
public final class BaseStyles {
    private BaseStyles() {
    }

    /**
     * One family: its blocks, its seat noun ("Still Crane Keep") and the representative colours of its three large
     * surfaces (walls, roofs, the perimeter), the average colours of those blocks' textures.
     */
    public record Family(BaseStyle id, String wall, String frame, String floor, String roof, String stairs,
                         String perimeter, int wallRgb, int roofRgb, int perimeterRgb) {
        public String seat() {
            return id.seat;
        }

        public int milestone() {
            return id.milestone;
        }

        public boolean legacy() {
            return id.milestone == 0;
        }

        public List<String> blocks() {
            return List.of(wall, frame, floor, roof, stairs, perimeter);
        }

        public int[] colours() {
            return new int[]{wallRgb, roofRgb, perimeterRgb};
        }
    }

    private static final Map<BaseStyle, Family> FAMILIES = new EnumMap<>(BaseStyle.class);

    private static void f(BaseStyle s, String wall, String frame, String floor, String roof, String stairs,
                          String perimeter, int wallRgb, int roofRgb, int perimeterRgb) {
        FAMILIES.put(s, new Family(s, wall, frame, floor, roof, stairs, perimeter, wallRgb, roofRgb, perimeterRgb));
    }

    static {
        f(BaseStyle.EMBER_FORTRESS, "bricks", "dark_oak_log", "dark_oak_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "polished_blackstone_bricks", 0x966153, 0x373737, 0x302B32);
        f(BaseStyle.GROVE_LODGE, "oak_planks", "oak_log", "spruce_planks", "spruce_planks", "spruce_stairs",
                "mossy_stone_bricks", 0xA2834F, 0x735531, 0x737969);
        f(BaseStyle.TIDE_PORT, "prismarine_bricks", "birch_log", "birch_planks", "dark_prismarine",
                "dark_prismarine_stairs", "smooth_sandstone", 0x63AB9E, 0x335B4B, 0xE0D6AA);
        f(BaseStyle.STONE_HOLD, "stone_bricks", "spruce_log", "spruce_planks", "cobblestone", "stone_brick_stairs",
                "stone_bricks", 0x7A7A7A, 0x7F7F7F, 0x7A7A7A);
        f(BaseStyle.SHRINE_PAVILION, "birch_planks", "stripped_mangrove_log", "spruce_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "stone_bricks", 0xC0AF79, 0x373737, 0x7A7A7A);
        f(BaseStyle.CHERRY_COURT, "cherry_planks", "stripped_cherry_log", "birch_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "smooth_stone", 0xE3B3AD, 0x373737, 0x9E9E9E);
        f(BaseStyle.BAMBOO_VILLAGE, "bamboo_planks", "jungle_log", "bamboo_planks", "spruce_planks", "spruce_stairs",
                "cobblestone", 0xC2AD51, 0x735531, 0x7F7F7F);
        f(BaseStyle.FROST_HALL, "polished_diorite", "spruce_log", "spruce_planks", "dark_prismarine",
                "dark_prismarine_stairs", "stone_bricks", 0xC1C1C3, 0x335B4B, 0x7A7A7A);
        f(BaseStyle.DARK_MANOR, "polished_blackstone_bricks", "dark_oak_log", "dark_oak_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "blackstone", 0x302B32, 0x373737, 0x2A2328);
        f(BaseStyle.DEEPSLATE_KEEP, "deepslate_bricks", "dark_oak_log", "spruce_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "cobbled_deepslate", 0x464646, 0x373737, 0x4D4D51);
        f(BaseStyle.DESERT_COURT, "smooth_sandstone", "acacia_log", "cut_sandstone", "smooth_sandstone",
                "sandstone_stairs", "sandstone", 0xE0D6AA, 0xE0D6AA, 0xD8CB9B);
        f(BaseStyle.IVORY_PALACE, "quartz_bricks", "birch_log", "birch_planks", "smooth_stone", "stone_stairs",
                "polished_diorite", 0xEAE5DE, 0x9E9E9E, 0xC1C1C3);
        f(BaseStyle.JUNGLE_HAVEN, "jungle_planks", "jungle_log", "jungle_planks", "mossy_cobblestone",
                "mossy_cobblestone_stairs", "mossy_stone_bricks", 0xA07351, 0x6E7660, 0x737969);
        f(BaseStyle.TUFF_WORKSHOP, "tuff_bricks", "spruce_log", "spruce_planks", "deepslate_tiles",
                "deepslate_tile_stairs", "polished_tuff", 0x62675F, 0x373737, 0x61685F);
        f(BaseStyle.MUD_TOWN, "mud_bricks", "acacia_log", "acacia_planks", "mud_bricks", "mud_brick_stairs",
                "red_sandstone", 0x89684F, 0x89684F, 0xBA6620);
        f(BaseStyle.CORAL_PORT, "dark_prismarine", "mangrove_log", "mangrove_planks", "prismarine_bricks",
                "prismarine_brick_stairs", "sandstone", 0x335B4B, 0x63AB9E, 0xD8CB9B);
    }

    /** The blocks Appendix B marks *: added to {@code MaterialRules.STONE_BLOCKS} so repairs can substitute. */
    public static final List<String> STONE_ADDITIONS = List.of("cut_sandstone", "smooth_stone", "mud_bricks",
            "tuff_bricks", "polished_tuff", "red_sandstone", "polished_diorite", "quartz_bricks");

    /** The family and its blocks. */
    public static Family of(BaseStyle s) {
        return FAMILIES.get(s);
    }

    /** Whether a family is built by milestone {@code m} (the four legacy families always). */
    public static boolean built(BaseStyle s, int milestone) {
        return s.milestone <= milestone;
    }

    /**
     * The family a leader's base is built in at milestone {@code m}: its own when it is built, else the built family
     * nearest it by material colour (the validator prints which; HEROES_V2 Appendix B). The distance compares the
     * three large surfaces both ways, each to the nearest of the other's, walls counting double, so a coral port
     * waits as the tide port it mirrors and an ivory palace as the frost hall.
     */
    public static BaseStyle builtFor(BaseStyle wanted, int milestone) {
        if (built(wanted, milestone)) {
            return wanted;
        }
        BaseStyle best = BaseStyle.STONE_HOLD;
        double bd = Double.MAX_VALUE;
        for (BaseStyle s : BaseStyle.values()) {
            if (!built(s, milestone)) {
                continue;
            }
            double e = materialDistance(FAMILIES.get(wanted), FAMILIES.get(s));
            if (e < bd) {
                bd = e;
                best = s;
            }
        }
        return best;
    }

    /** How far apart two families look: walls, roofs and perimeters matched both ways, walls counting double. */
    public static double materialDistance(Family a, Family b) {
        return (oneWay(a.colours(), b.colours()) + oneWay(b.colours(), a.colours())) / 8;
    }

    private static double oneWay(int[] from, int[] to) {
        double s = 0;
        for (int i = 0; i < from.length; i++) {
            double m = Double.MAX_VALUE;
            for (int c : to) {
                m = Math.min(m, Palette.deltaE(from[i], c));
            }
            s += (i == 0 ? 2 : 1) * m;
        }
        return s;
    }

    /** ROSTER_PLAN.md 2.3: a leader's default family by archetype (stone_hold for an unknown one). */
    public static final Map<String, BaseStyle> ARCHETYPE_STYLE;

    static {
        Map<String, BaseStyle> m = new LinkedHashMap<>();
        String[][] rows = {{"cat", "tide_port"}, {"fox", "cherry_court"}, {"wolf", "grove_lodge"},
                {"tanuki", "mud_town"}, {"moon_rabbit", "ivory_palace"}, {"oni", "ember_fortress"},
                {"tengu", "stone_hold"}, {"snow_spirit", "frost_hall"}, {"kappa", "tide_port"},
                {"spider_weaver", "dark_manor"}, {"east_dragon", "coral_port"}, {"west_dragon", "deepslate_keep"},
                {"komainu", "shrine_pavilion"}, {"plant_spirit", "jungle_haven"}, {"shrine", "shrine_pavilion"},
                {"onmyoji", "shrine_pavilion"}, {"samurai", "deepslate_keep"}, {"wanderer", "bamboo_village"},
                {"ninja", "bamboo_village"}, {"warrior_monk", "stone_hold"}, {"fan_dancer", "cherry_court"},
                {"delinquent", "mud_town"}, {"battle_chef", "mud_town"}, {"storm_drummer", "stone_hold"},
                {"sword_cultivator", "ivory_palace"}, {"martial_artist", "mud_town"}, {"jiangshi", "dark_manor"},
                {"phoenix", "desert_court"}, {"knight", "stone_hold"}, {"paladin", "ivory_palace"},
                {"battle_priest", "ivory_palace"}, {"witch", "dark_manor"}, {"elf", "grove_lodge"},
                {"vampire", "dark_manor"}, {"demon_lord", "ember_fortress"}, {"angel", "ivory_palace"},
                {"reaper", "dark_manor"}, {"necromancer", "deepslate_keep"}, {"beast_tamer", "grove_lodge"},
                {"dragoon", "stone_hold"}, {"berserker", "grove_lodge"}, {"arbalist", "stone_hold"},
                {"corsair", "coral_port"}, {"bard", "cherry_court"}, {"alchemist", "tuff_workshop"},
                {"siren", "coral_port"}, {"idol", "cherry_court"}, {"magical", "ivory_palace"},
                {"maid_butler", "ivory_palace"}, {"noble_fencer", "ivory_palace"}, {"android", "tuff_workshop"},
                {"tactician", "tuff_workshop"}, {"smith", "tuff_workshop"}, {"grappler", "mud_town"}};
        for (String[] r : rows) {
            m.put(r[0], HeroCatalogue.enumOf(BaseStyle.class, r[1]));
        }
        ARCHETYPE_STYLE = java.util.Collections.unmodifiableMap(m);
    }

    /** The leader's family: its own {@code base_style}, else its archetype's (5.1). */
    public static BaseStyle styleFor(BaseStyle authored, String archetype) {
        if (authored != null) {
            return authored;
        }
        return ARCHETYPE_STYLE.getOrDefault(archetype, BaseStyle.STONE_HOLD);
    }

    /**
     * The culture recruits' names follow when the leader gives none (F4's default by ROSTER_PLAN 2.3's groups):
     * animal-kin and yokai, shrine and martial Japan, wuxia: east; western fantasy: west; modern, pop and science
     * fiction: mixed.
     */
    public static Culture defaultCulture(String archetype) {
        int i = HeroCatalogue.ARCHETYPES.indexOf(archetype);
        if (i < 0) {
            return Culture.MIXED;
        }
        // ROSTER_PLAN 2.3's order: groups A (0-13), B (14-23), C (24-27), D (28-45), E (46-53)
        return i < 28 ? Culture.EAST : i < 46 ? Culture.WEST : Culture.MIXED;
    }
}
