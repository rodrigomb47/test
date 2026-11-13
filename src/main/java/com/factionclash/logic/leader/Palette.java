package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Dye;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Colour science for faction identities (HEROES_V2 5.1, 5.2, 6.4, 6.13): RGB to CIE Lab (D65), CIE76 delta-E, the
 * nearest of vanilla's 16 dyes, of the 16 chat colours and of the 7 boss bar colours, and hex text. A faction's wool,
 * carpets and beds can only show dyes, so its {@code dye_primary} is the banner's base dye when the leader gives a
 * banner (the nearest dye by Lab distance is often wrong for dark colours: indigo and charcoal both land on gray,
 * plum on black) and the nearest dye only when it gives none. Pure, unit-tested ({@code PaletteTest}).
 */
public final class Palette {
    private Palette() {
    }

    /** Vanilla 1.21.1's {@code DyeColor} texture colours (the colours wool, banners and beds are drawn in). */
    public static final Map<Dye, Integer> DYE_RGB = new EnumMap<>(Dye.class);

    static {
        int[] rgb = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52, 0x9D9D97, 0x169C9C,
                0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
        Dye[] all = Dye.values();
        for (int i = 0; i < all.length; i++) {
            DYE_RGB.put(all[i], rgb[i]);
        }
    }

    /** The 16 chat colours ({@code ChatFormatting}), by name and colour. */
    public enum ChatColor {
        BLACK(0x000000), DARK_BLUE(0x0000AA), DARK_GREEN(0x00AA00), DARK_AQUA(0x00AAAA), DARK_RED(0xAA0000),
        DARK_PURPLE(0xAA00AA), GOLD(0xFFAA00), GRAY(0xAAAAAA), DARK_GRAY(0x555555), BLUE(0x5555FF), GREEN(0x55FF55),
        AQUA(0x55FFFF), RED(0xFF5555), LIGHT_PURPLE(0xFF55FF), YELLOW(0xFFFF55), WHITE(0xFFFFFF);

        public final int rgb;

        ChatColor(int rgb) {
            this.rgb = rgb;
        }
    }

    /**
     * The 7 boss bar colours ({@code BossEvent.BossBarColor}), each with the colour its bar texture shows (the
     * middle of the filled bar, approximately).
     */
    public enum BarColor {
        PINK(0xEC00B8), BLUE(0x00B7EC), RED(0xEC3500), GREEN(0x1DEC00), YELLOW(0xE9EC00), PURPLE(0x7B00EC),
        WHITE(0xECECEC);

        public final int rgb;

        BarColor(int rgb) {
            this.rgb = rgb;
        }
    }

    // ------------------------------------------------------------------ Lab

    /** CIE Lab (D65) of an sRGB colour. */
    public static double[] lab(int rgb) {
        double r = lin(((rgb >> 16) & 0xFF) / 255.0), g = lin(((rgb >> 8) & 0xFF) / 255.0), b = lin((rgb & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** CIE76 delta-E between two colours. */
    public static double deltaE(int a, int b) {
        double[] x = lab(a), y = lab(b);
        return Math.sqrt(sq(x[0] - y[0]) + sq(x[1] - y[1]) + sq(x[2] - y[2]));
    }

    private static double sq(double v) {
        return v * v;
    }

    // ------------------------------------------------------------------ nearest

    /** The dye nearest a colour by Lab distance (ties: the first dye in vanilla's order). */
    public static Dye nearestDye(int rgb) {
        Dye best = Dye.WHITE;
        double bd = Double.MAX_VALUE;
        for (Dye d : Dye.values()) {
            double e = deltaE(rgb, DYE_RGB.get(d));
            if (e < bd) {
                bd = e;
                best = d;
            }
        }
        return best;
    }

    /** The chat colour nearest a colour (the fallback where only the 16 chat colours can show it). */
    public static ChatColor nearestChat(int rgb) {
        ChatColor best = ChatColor.WHITE;
        double bd = Double.MAX_VALUE;
        for (ChatColor c : ChatColor.values()) {
            double e = deltaE(rgb, c.rgb);
            if (e < bd) {
                bd = e;
                best = c;
            }
        }
        return best;
    }

    /** The boss bar colour nearest a hero's primary (6.13); the bar turns red when it is enraged. */
    public static BarColor nearestBar(int rgb) {
        BarColor best = BarColor.WHITE;
        double bd = Double.MAX_VALUE;
        for (BarColor c : BarColor.values()) {
            double e = deltaE(rgb, c.rgb);
            if (e < bd) {
                bd = e;
                best = c;
            }
        }
        return best;
    }

    /**
     * A faction's two dyes (5.1): {@code dye_primary} is the banner's base dye and {@code dye_secondary} its first
     * pattern's dye when the leader gives a banner; otherwise the nearest dyes of the primary and secondary colours,
     * the secondary moved to the next-nearest dye when both land on the same one.
     */
    public static Dye[] identityDyes(int primary, int secondary, Dye bannerBase, Dye firstPatternDye) {
        if (bannerBase != null) {
            Dye second = firstPatternDye != null ? firstPatternDye : otherThan(secondary, bannerBase);
            return new Dye[]{bannerBase, second};
        }
        Dye p = nearestDye(primary);
        return new Dye[]{p, otherThan(secondary, p)};
    }

    /** The dye nearest {@code rgb} that is not {@code not}. */
    public static Dye otherThan(int rgb, Dye not) {
        Dye best = null;
        double bd = Double.MAX_VALUE;
        for (Dye d : Dye.values()) {
            if (d == not) {
                continue;
            }
            double e = deltaE(rgb, DYE_RGB.get(d));
            if (e < bd) {
                bd = e;
                best = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ text

    /** "#RRGGBB" for a colour. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** A colour from "#RRGGBB" or "RRGGBB"; -1 when it is not one. */
    public static int parse(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (!t.matches("[0-9a-fA-F]{6}")) {
            return -1;
        }
        return Integer.parseInt(t, 16);
    }

    /** The 16 dyes in vanilla's order. */
    public static List<Dye> dyes() {
        return List.of(Dye.values());
    }
}
