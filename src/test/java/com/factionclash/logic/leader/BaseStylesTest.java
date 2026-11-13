package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.BaseStyle;
import com.factionclash.logic.hero.HeroDef.Culture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link BaseStyles} against HEROES_V2 Appendix B and ROSTER_PLAN 2.3; the nearest built family; cultures. */
class BaseStylesTest {
    private static String block(String cell) {
        return cell.trim().replace("*", "").trim().replace(' ', '_');
    }

    @Test
    void everyFamilyMatchesAppendixB() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/HEROES_V2.md"));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Family | Wall | Frame | Floor | Roof (stairs) | Perimeter | Seat |")) {
                start = i + 2;
            }
        }
        assertTrue(start > 0);
        Set<BaseStyle> seen = EnumSet.noneOf(BaseStyle.class);
        List<String> starred = new ArrayList<>();
        for (int i = start; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String[] c = lines.get(i).split("\\|", -1);
            String id = c[1].trim().split("\\s+")[0].replace("`", "");
            BaseStyle s = HeroCatalogue.enumOf(BaseStyle.class, id);
            BaseStyles.Family f = BaseStyles.of(s);
            assertEquals(block(c[2]), f.wall(), id);
            assertEquals(block(c[3]), f.frame(), id);
            assertEquals(block(c[4]), f.floor(), id);
            String roof = c[5].trim();
            if (roof.contains("(")) {
                assertEquals(block(roof.substring(roof.indexOf('(') + 1, roof.indexOf(')'))), f.stairs(), id);
                roof = roof.substring(0, roof.indexOf('(')).trim();
            }
            assertEquals(block(roof), f.roof(), id);
            assertEquals(block(c[6]), f.perimeter(), id);
            assertEquals(c[7].trim(), f.seat(), id);
            String ms = c[8].trim();
            assertEquals(ms.equals("legacy") ? 0 : Integer.parseInt(ms.substring(1)), f.milestone(), id);
            for (int k = 2; k <= 6; k++) {
                for (String part : c[k].split("\\(")) {
                    if (part.contains("*")) {
                        starred.add(block(part.replace(")", "")));
                    }
                }
            }
            seen.add(s);
        }
        assertEquals(EnumSet.allOf(BaseStyle.class), seen);
        assertEquals(Set.copyOf(starred), Set.copyOf(BaseStyles.STONE_ADDITIONS), "the * blocks of Appendix B");
        for (BaseStyle s : BaseStyle.values()) {
            for (String b : BaseStyles.of(s).blocks()) {
                assertTrue(b.matches("[a-z_]+"), b);
            }
        }
    }

    @Test
    void theArchetypesFamilyMatchesRosterPlan() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/ROSTER_PLAN.md"));
        int rows = 0;
        boolean in = false;
        for (String l : lines) {
            if (l.startsWith("| Group | Archetype | F | M | Default base style (leader) |")) {
                in = true;
                continue;
            }
            if (in && l.startsWith("|---")) {
                continue;
            }
            if (in && !l.startsWith("|")) {
                break;
            }
            if (in) {
                String[] c = l.split("\\|", -1);
                assertEquals(HeroCatalogue.enumOf(BaseStyle.class, c[5].trim()),
                        BaseStyles.ARCHETYPE_STYLE.get(c[2].trim()), c[2]);
                rows++;
            }
        }
        assertEquals(54, rows);
        assertEquals(54, BaseStyles.ARCHETYPE_STYLE.size());
        assertEquals(BaseStyle.DEEPSLATE_KEEP, BaseStyles.styleFor(null, "samurai"));
        assertEquals(BaseStyle.FROST_HALL, BaseStyles.styleFor(BaseStyle.FROST_HALL, "samurai"), "the leader's own");
        assertEquals(BaseStyle.STONE_HOLD, BaseStyles.styleFor(null, "nobody"));
    }

    @Test
    void anUnbuiltFamilyWaitsAsTheNearestBuiltOne() {
        for (BaseStyle s : BaseStyle.values()) {
            BaseStyle b = BaseStyles.builtFor(s, 1);
            assertTrue(BaseStyles.built(b, 1), s + " -> " + b);
            if (s.milestone <= 1) {
                assertEquals(s, b, "built: itself");
            }
            assertEquals(s, BaseStyles.builtFor(s, 2), "every family is built by milestone 2");
        }
        assertEquals(BaseStyle.TIDE_PORT, BaseStyles.builtFor(BaseStyle.CORAL_PORT, 1), "the same prismarines");
        assertEquals(BaseStyle.FROST_HALL, BaseStyles.builtFor(BaseStyle.IVORY_PALACE, 1), "white walls");
        assertEquals(BaseStyle.GROVE_LODGE, BaseStyles.builtFor(BaseStyle.JUNGLE_HAVEN, 1), "planks and moss");
        assertEquals(BaseStyle.DEEPSLATE_KEEP, BaseStyles.builtFor(BaseStyle.TUFF_WORKSHOP, 1), "grey stone");
        assertEquals(BaseStyle.GROVE_LODGE, BaseStyles.builtFor(BaseStyle.MUD_TOWN, 1), "brown walls");
        assertEquals(BaseStyle.TIDE_PORT, BaseStyles.builtFor(BaseStyle.DESERT_COURT, 1), "sandstone");
        assertEquals(0, BaseStyles.materialDistance(BaseStyles.of(BaseStyle.STONE_HOLD),
                BaseStyles.of(BaseStyle.STONE_HOLD)), 1e-9);
        assertEquals(BaseStyles.materialDistance(BaseStyles.of(BaseStyle.MUD_TOWN), BaseStyles.of(BaseStyle.DARK_MANOR)),
                BaseStyles.materialDistance(BaseStyles.of(BaseStyle.DARK_MANOR), BaseStyles.of(BaseStyle.MUD_TOWN)),
                1e-9, "symmetric");
        assertTrue(BaseStyles.of(BaseStyle.EMBER_FORTRESS).legacy());
        assertEquals("Keep", BaseStyles.of(BaseStyle.DEEPSLATE_KEEP).seat());
    }

    @Test
    void cultureByArchetypeGroup() {
        assertEquals(Culture.EAST, BaseStyles.defaultCulture("cat"));
        assertEquals(Culture.EAST, BaseStyles.defaultCulture("samurai"));
        assertEquals(Culture.EAST, BaseStyles.defaultCulture("phoenix"));
        assertEquals(Culture.WEST, BaseStyles.defaultCulture("knight"));
        assertEquals(Culture.WEST, BaseStyles.defaultCulture("siren"));
        assertEquals(Culture.MIXED, BaseStyles.defaultCulture("idol"));
        assertEquals(Culture.MIXED, BaseStyles.defaultCulture("grappler"));
        assertEquals(Culture.MIXED, BaseStyles.defaultCulture("nobody"));
    }
}
