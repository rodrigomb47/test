package com.factionclash.logic.hero;

import java.util.Locale;
import java.util.Set;

/**
 * Hero ids, display names and the combat role that fits a weapon style (HEROES_V2 3.2, 12.2). Pure, unit-tested
 * ({@code HeroIdentityTest}).
 */
public final class HeroIdentity {
    /** The neutral model an unknown or dormant hero is drawn with; never a hero itself. */
    public static final String FALLBACK = "_fallback";
    public static final int MAX_ID_LENGTH = 40;

    /** Styles that fight like an axe fighter: heavy chopping and crushing weapons. */
    private static final Set<String> AXE_STYLES = Set.of("axe", "hammer", "scythe");

    private HeroIdentity() {
    }

    /**
     * Whether {@code id} names a hero: 1-40 of {@code a-z 0-9 _}, not starting with {@code _} (files whose name starts
     * with {@code _} are shared libraries and the fallback model).
     */
    public static boolean isHeroId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH || id.charAt(0) == '_') {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** A readable name from an id until a definition gives the real one: {@code test_rig} reads "Test Rig". */
    public static String nameFromId(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.isEmpty() ? id : sb.toString();
    }

    /**
     * The id of the soldier role a hero of this weapon style counts as (its {@code NpcRole}): {@code bow} an archer,
     * {@code crossbow} a crossbowman, axes, hammers and scythes an axe fighter, every other style (swords, fists,
     * staves, focus and thrown styles, an unknown one) a swordsman.
     */
    public static String combatRoleId(String style) {
        String s = style == null ? "" : style.toLowerCase(Locale.ROOT);
        if (s.equals("bow")) {
            return "archer";
        }
        if (s.equals("crossbow")) {
            return "crossbowman";
        }
        return AXE_STYLES.contains(s) ? "axe_fighter" : "swordsman";
    }
}
