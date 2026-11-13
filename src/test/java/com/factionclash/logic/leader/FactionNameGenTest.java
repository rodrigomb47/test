package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.leader.FactionNameGen.Names;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link FactionNameGen}: the leader's names first, seeded generation, uniqueness, key words, slugs, base names. */
class FactionNameGenTest {
    private static final Set<String> LEGACY = Set.of("ember_legion", "grove_covenant", "tide_alliance", "stoneguard",
            "hollow");

    @Test
    void theLeadersNamesComeFirst() {
        Set<String> taken = new HashSet<>(LEGACY);
        Names n = FactionNameGen.names(List.of("House of the Still Crane", "Still Crane Watch"), "Still Crane", 1,
                "samurai", Temperament.STOIC, Dye.BLUE, taken);
        assertEquals(new Names("House of the Still Crane", "Still Crane", "still_crane"), n);
        taken.addAll(List.of(n.name(), n.shortName(), n.slug()));
        Names second = FactionNameGen.names(List.of("House of the Still Crane", "Still Crane Watch"), "Still Crane", 1,
                "samurai", Temperament.STOIC, Dye.BLUE, taken);
        assertEquals("Still Crane Watch", second.name(), "the first unused name");
        assertEquals("Crane Watch", second.shortName(), "short names are unique too: the name's last words");
        Names noShort = FactionNameGen.names(List.of("Crimson Anvil Warband"), null, 1, "oni", Temperament.BATTLE_MANIAC,
                Dye.RED, new HashSet<>(LEGACY));
        assertEquals("Crimson Anvil", noShort.shortName(), "the name's key word");
        assertEquals("crimson_anvil", noShort.slug());
    }

    @Test
    void generatedNamesAreSeededUniqueAndWithinLimits() {
        Set<String> taken = new HashSet<>(LEGACY);
        Set<String> names = new HashSet<>(), shorts = new HashSet<>(), slugs = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String arch = HeroCatalogue.ARCHETYPES.get(i % 10);
            Temperament t = Temperament.values()[i % 30];
            Dye d = Dye.values()[i % 3];
            Names n = FactionNameGen.generate(1000 + i, arch, t, d, taken);
            assertEquals(n, FactionNameGen.generate(1000 + i, arch, t, d, new HashSet<>(taken)), "seeded");
            assertTrue(n.name().length() >= 3 && n.name().length() <= 32, n.name());
            assertTrue(n.shortName().length() >= 1 && n.shortName().length() <= 14, n.shortName());
            assertTrue(n.slug().matches("[a-z0-9_]{3,24}"), n.slug());
            assertTrue(names.add(n.name().toLowerCase(Locale.ROOT)), "unique name " + n.name());
            assertTrue(shorts.add(n.shortName().toLowerCase(Locale.ROOT)), "unique short " + n.shortName());
            assertTrue(slugs.add(n.slug()), "unique slug " + n.slug());
            assertFalse(LEGACY.contains(n.slug()), "slot ids stay commands");
            taken.addAll(List.of(n.name(), n.shortName(), n.slug()));
        }
        Names oni = FactionNameGen.generate(7, "oni", Temperament.BATTLE_MANIAC, Dye.RED, Set.of());
        assertTrue(oni.name().contains("Crimson") || oni.name().contains("Scarlet"), oni.name());
    }

    @Test
    void wordBanks() {
        for (String a : HeroCatalogue.ARCHETYPES) {
            List<String> m = FactionNameGen.MOTIFS.get(a);
            assertNotNull(m, a);
            assertEquals(3, m.size(), a);
        }
        for (Dye d : Dye.values()) {
            assertEquals(2, FactionNameGen.COLOURS.get(d).size());
        }
        for (Temperament t : Temperament.values()) {
            assertNotNull(FactionNameGen.group(t));
        }
        assertEquals(FactionNameGen.Group.MARTIAL, FactionNameGen.group(Temperament.BATTLE_MANIAC));
        assertEquals(FactionNameGen.Group.LONE, FactionNameGen.group(Temperament.LONE_WOLF));
    }

    @Test
    void keyWordsSlugsAndBaseNames() {
        assertEquals("Still Crane", FactionNameGen.keyWord("House of the Still Crane"));
        assertEquals("Crimson Anvil", FactionNameGen.keyWord("Crimson Anvil Warband"));
        assertEquals("Red Horn", FactionNameGen.keyWord("The Red Horn Host"));
        assertEquals("Azure Crane", FactionNameGen.keyWord("Order of the Azure Crane"));
        assertEquals("Extraordinarily", FactionNameGen.keyWord("Extraordinarilylongname").substring(0, 14)
                .equals("Extraordinaril") ? "Extraordinarily" : "x");
        assertTrue(FactionNameGen.keyWord("Snow").equals("Snow"));
        assertTrue(FactionNameGen.keyWord("The Long Winding Silver River Road").length() <= 14);
        assertEquals("still_crane", FactionNameGen.slug("Still Crane", Set.of()));
        assertEquals("still_crane_2", FactionNameGen.slug("Still Crane", Set.of("still_crane")));
        assertEquals("still_crane_3", FactionNameGen.slug("Still Crane", Set.of("STILL_CRANE", "still_crane_2")));
        assertEquals("ox_faction", FactionNameGen.slug("Ox", Set.of()), "at least 3 letters");
        String longSlug = FactionNameGen.slug("A Very Long Short Name Here Indeed", Set.of());
        assertTrue(longSlug.length() <= 24 && !longSlug.endsWith("_"), longSlug);
        assertEquals("o_neil_s_band", FactionNameGen.slug("O'Neil's Band!", Set.of()));
        assertEquals("Still Crane Keep", FactionNameGen.baseName("Still Crane", "Keep"));
    }
}
