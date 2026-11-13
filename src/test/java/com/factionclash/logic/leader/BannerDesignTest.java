package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.Banner;
import com.factionclash.logic.hero.HeroDef.BannerLayer;
import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.leader.BannerDesign.Emblem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link BannerDesign}: the leader's banner or the archetype's emblem, vanilla patterns only, never the logo. */
class BannerDesignTest {
    @Test
    void everyArchetypeHasAnEmblemOfVanillaPatterns() {
        assertEquals(HeroCatalogue.ARCHETYPES.size(), BannerDesign.ARCHETYPE_EMBLEM.size(), "all 54, once each");
        for (String a : HeroCatalogue.ARCHETYPES) {
            assertTrue(BannerDesign.ARCHETYPE_EMBLEM.containsKey(a), a);
        }
        for (Emblem e : Emblem.values()) {
            for (String p : List.of(e.first, e.second)) {
                assertTrue(HeroCatalogue.BANNER_PATTERNS.contains(p), p);
                assertNotEquals(HeroCatalogue.REFUSED_PATTERN, p);
            }
            assertTrue(BannerDesign.ARCHETYPE_EMBLEM.containsValue(e), e + " is used");
        }
        // Appendix C's pairs
        assertEquals(Emblem.BEAST_KIN, BannerDesign.emblem("cat"));
        assertEquals("triangles_top", Emblem.BEAST_KIN.first);
        assertEquals("straight_cross", BannerDesign.emblem("shrine").first);
        assertEquals("cross", BannerDesign.emblem("knight").first);
        assertEquals("flower", BannerDesign.emblem("plant_spirit").first);
        assertEquals("skull", BannerDesign.emblem("necromancer").first);
        assertEquals("stripe_downleft", BannerDesign.emblem("tengu").first);
        assertEquals("flow", BannerDesign.emblem("siren").first);
        assertEquals("bricks", BannerDesign.emblem("smith").first);
        assertEquals("rhombus", BannerDesign.emblem("idol").first);
        assertEquals("triangles_bottom", BannerDesign.emblem("east_dragon").first);
        assertEquals(Emblem.KNIGHTS, BannerDesign.emblem("nobody"), "an unknown archetype: the plainest");
    }

    @Test
    void theArchetypesBannerInTheFactionsDyes() {
        Banner b = BannerDesign.forArchetype("cat", Dye.CYAN, Dye.WHITE, Dye.ORANGE);
        assertEquals(Dye.CYAN, b.base());
        assertEquals(new BannerLayer("triangles_top", Dye.WHITE), b.patterns().get(0));
        assertEquals(new BannerLayer("circle", Dye.ORANGE), b.patterns().get(1));
        assertTrue(BannerDesign.valid(b));
        // a layer in the base's own dye would vanish: it takes the most contrasting dye instead
        Banner same = BannerDesign.forArchetype("knight", Dye.WHITE, Dye.WHITE, null);
        for (BannerLayer l : same.patterns()) {
            assertNotEquals(Dye.WHITE, l.color());
        }
        assertEquals(Dye.BLACK, BannerDesign.contrasting(Dye.WHITE, Dye.WHITE));
        assertEquals(Dye.RED, BannerDesign.contrasting(Dye.RED, Dye.WHITE));
    }

    @Test
    void theLeadersBannerWinsWhenValid() {
        Banner benika = new Banner(Dye.RED, List.of(new BannerLayer("triangles_bottom", Dye.BLACK),
                new BannerLayer("circle", Dye.YELLOW)));
        assertEquals(benika, BannerDesign.banner(benika, "oni", Dye.RED, Dye.BLACK, Dye.YELLOW));
        Banner logo = new Banner(Dye.RED, List.of(new BannerLayer("mojang", Dye.BLACK),
                new BannerLayer("circle", Dye.YELLOW)));
        assertFalse(BannerDesign.valid(logo), "never the company logo");
        assertEquals(BannerDesign.forArchetype("oni", Dye.RED, Dye.BLACK, Dye.YELLOW),
                BannerDesign.banner(logo, "oni", Dye.RED, Dye.BLACK, Dye.YELLOW));
        assertFalse(BannerDesign.valid(new Banner(Dye.RED, List.of(new BannerLayer("circle", Dye.YELLOW)))),
                "at least 2 layers");
        assertFalse(BannerDesign.valid(new Banner(Dye.RED, List.of(new BannerLayer("circle", Dye.YELLOW),
                new BannerLayer("swirl", Dye.BLACK)))), "vanilla patterns only");
        assertFalse(BannerDesign.valid(null));
        assertEquals("[{pattern:\"minecraft:triangles_bottom\",color:\"black\"},{pattern:\"minecraft:circle\","
                + "color:\"yellow\"}]", BannerDesign.component(benika));
    }
}
