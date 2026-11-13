package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.Banner;
import com.factionclash.logic.hero.HeroDef.BannerLayer;
import com.factionclash.logic.hero.HeroDef.Dye;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A faction's banner (HEROES_V2 5.1, 5.3, Appendix C): the leader's {@code banner} (a base dye and 2-4 vanilla pattern
 * layers, never the company logo), else its archetype's emblem in the faction's dyes. Real banners hang at the
 * station and the gates and members get one; banners are decoration and stay out of the repair plan. Pure,
 * unit-tested ({@code BannerDesignTest}).
 */
public final class BannerDesign {
    private BannerDesign() {
    }

    /** At most this many pattern layers (vanilla shows six; the format allows 2-4). */
    public static final int MIN_LAYERS = 2, MAX_LAYERS = 4;

    /** Appendix C's emblem groups and their two patterns. */
    public enum Emblem {
        BEAST_KIN("triangles_top", "circle"), SHRINE("straight_cross", "stripe_center"), KNIGHTS("cross", "border"),
        PLANTS("flower", "gradient_up"), DARK("skull", "curly_border"), STORM("stripe_downleft", "gradient"),
        SEA("flow", "border"), CRAFT("bricks", "square_top_left"), STARS("rhombus", "circle"),
        DRAGONS("triangles_bottom", "gradient");

        public final String first, second;

        Emblem(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Each archetype's emblem group (F4's assignment of ROSTER_PLAN 2.3's 54 archetypes to Appendix C's groups; the
     * archetype files of the art pipeline may override it). The sea group's own emblem is the single {@code flow}
     * pattern; a {@code border} is added as its second layer, since a banner has at least two.
     */
    public static final Map<String, Emblem> ARCHETYPE_EMBLEM;

    static {
        Map<String, Emblem> m = new LinkedHashMap<>();
        put(m, Emblem.BEAST_KIN, "cat", "fox", "wolf", "tanuki", "oni", "komainu", "beast_tamer", "berserker",
                "grappler", "martial_artist", "delinquent");
        put(m, Emblem.SHRINE, "shrine", "onmyoji", "warrior_monk", "battle_priest");
        put(m, Emblem.KNIGHTS, "knight", "paladin", "samurai", "dragoon", "arbalist", "noble_fencer", "maid_butler");
        put(m, Emblem.PLANTS, "plant_spirit", "elf", "fan_dancer", "bard");
        put(m, Emblem.DARK, "spider_weaver", "jiangshi", "witch", "vampire", "demon_lord", "reaper", "necromancer");
        put(m, Emblem.STORM, "tengu", "snow_spirit", "wanderer", "ninja", "storm_drummer", "sword_cultivator");
        put(m, Emblem.SEA, "kappa", "corsair", "siren");
        put(m, Emblem.CRAFT, "battle_chef", "alchemist", "android", "tactician", "smith");
        put(m, Emblem.STARS, "moon_rabbit", "angel", "idol", "magical");
        put(m, Emblem.DRAGONS, "east_dragon", "west_dragon", "phoenix");
        ARCHETYPE_EMBLEM = java.util.Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, Emblem> m, Emblem e, String... ids) {
        for (String id : ids) {
            m.put(id, e);
        }
    }

    public static Emblem emblem(String archetype) {
        return ARCHETYPE_EMBLEM.getOrDefault(archetype, Emblem.KNIGHTS);
    }

    /**
     * The archetype's banner in the faction's dyes: the base in {@code dyePrimary}, the first pattern in
     * {@code dyeSecondary}, the second in the accent's dye; a layer whose dye equals the base takes the colour that
     * contrasts most with it, so no emblem vanishes into its field.
     */
    public static Banner forArchetype(String archetype, Dye dyePrimary, Dye dyeSecondary, Dye accent) {
        Emblem e = emblem(archetype);
        List<BannerLayer> l = new ArrayList<>();
        l.add(new BannerLayer(e.first, contrasting(dyeSecondary, dyePrimary)));
        l.add(new BannerLayer(e.second, contrasting(accent, dyePrimary)));
        return new Banner(dyePrimary, l);
    }

    /**
     * {@code want}, or, when it is the base's own dye, white or black: whichever differs more in lightness from the
     * base (an emblem reads by its lightness against the field).
     */
    public static Dye contrasting(Dye want, Dye base) {
        if (want != null && want != base) {
            return want;
        }
        double l = Palette.lab(Palette.DYE_RGB.get(base))[0];
        double w = Math.abs(Palette.lab(Palette.DYE_RGB.get(Dye.WHITE))[0] - l);
        double b = Math.abs(Palette.lab(Palette.DYE_RGB.get(Dye.BLACK))[0] - l);
        if (base == Dye.WHITE) {
            return Dye.BLACK;
        }
        if (base == Dye.BLACK) {
            return Dye.WHITE;
        }
        return w >= b ? Dye.WHITE : Dye.BLACK;
    }

    /**
     * The faction's banner: the leader's when it gives a valid one, else the archetype's. A leader's banner is valid
     * with 2-4 layers of vanilla patterns, none the company logo (the reader already refuses it; this is the game's
     * last line).
     */
    public static Banner banner(Banner authored, String archetype, Dye dyePrimary, Dye dyeSecondary, Dye accent) {
        if (valid(authored)) {
            return authored;
        }
        return forArchetype(archetype, dyePrimary, dyeSecondary, accent);
    }

    public static boolean valid(Banner b) {
        if (b == null || b.base() == null || b.patterns().size() < MIN_LAYERS || b.patterns().size() > MAX_LAYERS) {
            return false;
        }
        for (BannerLayer l : b.patterns()) {
            if (l.color() == null || !HeroCatalogue.BANNER_PATTERNS.contains(l.pattern())
                    || HeroCatalogue.REFUSED_PATTERN.equals(l.pattern())) {
                return false;
            }
        }
        return true;
    }

    /** The banner as the {@code /give} component text of vanilla 1.21.1's {@code banner_patterns}. */
    public static String component(Banner b) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < b.patterns().size(); i++) {
            BannerLayer l = b.patterns().get(i);
            sb.append(i == 0 ? "" : ",").append("{pattern:\"minecraft:").append(l.pattern()).append("\",color:\"")
                    .append(l.color().id()).append("\"}");
        }
        return sb.append("]").toString();
    }
}
