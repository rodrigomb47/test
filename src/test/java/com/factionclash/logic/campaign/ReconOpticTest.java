package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Recon;
import com.factionclash.logic.campaign.ReconRules.Band;
import com.factionclash.logic.campaign.ReconRules.Column;
import com.factionclash.logic.campaign.ReconRules.Ground;
import com.factionclash.logic.campaign.ReconRules.Side;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The recon scout's instruments ({@code docs/design/recon.md} 5.6, task R12): the rows of data (the eyes and the vanilla
 * spyglass, never behind a tech level), the band a spyglass opens (61 to 96, a lair's unchanged), the ranking that keeps
 * the eyes' columns first, a GameTest base's scaled reach, the report's tag and the share by instrument.
 */
class ReconOpticTest {
    @Test
    void theFirstRowsAreTheEyesAndTheVanillaSpyglass() {
        assertEquals(List.of(ReconOptic.NONE, ReconOptic.SPYGLASS), ReconOptic.ROWS);
        assertEquals(ReconRules.SIGHT, ReconOptic.NONE.sight(), "the eyes reach 72");
        assertTrue(ReconOptic.NONE.none() && ReconOptic.NONE.id().isEmpty() && ReconOptic.NONE.item().isEmpty());
        assertEquals(96, ReconOptic.SPYGLASS.sight(), "the spyglass reaches 96");
        assertEquals("spyglass", ReconOptic.SPYGLASS.id());
        assertEquals("minecraft:spyglass", ReconOptic.SPYGLASS.item());
        assertEquals(Reserve.SHARE, ReconOptic.NONE.share());
        assertEquals(Reserve.SHARE, ReconOptic.SPYGLASS.share(), "the walls set the coverage: the same share");
        // the line of sight ends at 128 for everyone: from 96 the yard's far side (a half of 24) lies within it
        assertTrue(ReconOptic.SPYGLASS.sight() + 24 < 128);
    }

    @Test
    void theVanillaSpyglassIsNeverBehindATechLevel() {
        assertEquals(0, ReconOptic.SPYGLASS.techLevel());
        assertEquals(0, ReconOptic.NONE.techLevel());
        assertTrue(ReconOptic.SPYGLASS.usable(ReconOptic.NO_TECH), "usable with no tech at all");
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.best(List.of("minecraft:spyglass"), ReconOptic.NO_TECH));
        // a later instrument of the mod's own behind a tech level slots in as a row and waits for its level
        ReconOptic binoculars = new ReconOptic("binoculars", "factionclash:binoculars", "binoculars", 100, 0.5, 3);
        List<ReconOptic> rows = List.of(ReconOptic.NONE, ReconOptic.SPYGLASS, binoculars);
        List<String> both = List.of("minecraft:spyglass", "factionclash:binoculars");
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.best(both, ReconOptic.NO_TECH, rows), "below its level: the spyglass");
        assertEquals(binoculars, ReconOptic.best(both, 3, rows), "at its level: the one that reaches farther");
        assertEquals(ReconOptic.NONE, ReconOptic.best(List.of("factionclash:binoculars"), 2, rows));
    }

    @Test
    void anItemIsAnInstrumentOnlyWhenItsRowSaysSo() {
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.byItem("minecraft:spyglass"));
        assertNull(ReconOptic.byItem("minecraft:dirt"));
        assertNull(ReconOptic.byItem(""), "the eyes are no item");
        assertNull(ReconOptic.byItem(null));
        assertEquals(ReconOptic.NONE, ReconOptic.best(List.of(), ReconOptic.NO_TECH));
        assertEquals(ReconOptic.NONE, ReconOptic.best(null, ReconOptic.NO_TECH));
        assertEquals(ReconOptic.NONE, ReconOptic.best(List.of("minecraft:dirt", "minecraft:iron_sword"), ReconOptic.NO_TECH));
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.best(List.of("minecraft:dirt", "minecraft:spyglass"), ReconOptic.NO_TECH));
    }

    @Test
    void aReportsTagNamesItsRowAndAnUnknownOneIsTheEyes() {
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.byId("spyglass"));
        assertEquals(ReconOptic.NONE, ReconOptic.byId(""));
        assertEquals(ReconOptic.NONE, ReconOptic.byId(null), "an older save: no tag");
        assertEquals(ReconOptic.NONE, ReconOptic.byId("drone"), "a row since removed: the eyes, never null");
        Recon eyes = new Recon(3, 1, Map.of(), 0.4, true, 1200, 100, "pillar 12", "Edrin Cole", Set.of());
        assertEquals("", eyes.optic(), "a recon made with the eyes has no tag");
        assertEquals(ReconOptic.NONE, ReconOptic.of(eyes));
        Recon glass = new Recon(3, 1, Map.of(), 0.4, true, 1200, 100, "pillar 12", "Edrin Cole", Set.of(), "spyglass");
        assertEquals(ReconOptic.SPYGLASS, ReconOptic.of(glass));
        assertEquals("", new Recon(3, 1, Map.of(), 0.4, true, 1200, 100, "", "", Set.of(), null).optic());
        assertEquals(ReconOptic.NONE, ReconOptic.of(null));
        ReconWatch w = new ReconWatch();
        w.add(new ReconWatch.Sample(1 << ReconWatch.MIDDLE, Map.of(), Map.of(), 10));
        assertEquals("spyglass", w.recon("pillar 3", "Edrin Cole", "spyglass").optic());
        assertEquals("", w.recon("pillar 3", "Edrin Cole").optic());
    }

    @Test
    void aSpyglassOpensTheBandOutTo96AndLeavesALairsAsItIs() {
        Band glass = Band.of(false, ReconOptic.SPYGLASS);
        assertEquals(new Band(61, 96, 8), glass);
        assertEquals(Band.of(false), Band.of(false, ReconOptic.NONE));
        assertEquals(Band.of(true), Band.of(true, ReconOptic.SPYGLASS), "a lair is watched from the ground: its band stays");
        assertTrue(ReconRules.inBand(0, -90, glass));
        assertFalse(ReconRules.inBand(0, -90, Band.of(false)), "beyond the eyes");
        assertTrue(ReconRules.inBand(96, 0, glass));
        assertFalse(ReconRules.inBand(97, 0, glass));
        assertFalse(ReconRules.inBand(0, 60, glass), "the standoff is the guards', not the eye's");
        List<Column> eyes = ReconRules.columns(Side.NORTH, Band.of(false));
        List<Column> deep = ReconRules.columns(Side.NORTH, glass);
        assertTrue(deep.containsAll(eyes), "every column the eyes have, the spyglass has");
        assertTrue(deep.size() > 2 * eyes.size(), eyes.size() + " and " + deep.size());
        assertTrue(deep.contains(new Column(0, -96, 0, 96)));
    }

    @Test
    void aTestBasesReachIsScaledByTheSameRatio() {
        assertEquals(13, ReconOptic.scaled(13, ReconOptic.NONE));
        assertEquals(17, ReconOptic.scaled(13, ReconOptic.SPYGLASS), "13 x 96 / 72 = 17.3");
        assertEquals(21, ReconOptic.scaled(16, ReconOptic.SPYGLASS), "16 x 96 / 72 = 21.3");
        assertEquals(96, ReconOptic.scaled(72, ReconOptic.SPYGLASS));
        assertEquals(0, ReconOptic.scaled(-1, ReconOptic.SPYGLASS));
    }

    @Test
    void theEyesColumnsComeFirstSoTheSpyglassNeverMakesASideWorse() {
        Column axis61 = new Column(0, -61, 0, 61), axis70 = new Column(0, -70, 0, 70), off3 = new Column(3, -65, 3, 65),
                hill90 = new Column(0, -90, 0, 90), off2deep = new Column(-2, -80, -2, 80);
        List<Ground> found = List.of(new Ground(hill90, 9), new Ground(off2deep, 5), new Ground(off3, 0), new Ground(axis70, 1),
                new Ground(axis61, 0));
        // the old order alone takes the hill deep in the band first: its pillar would need about 17, the standoff's 12
        assertEquals(hill90, ReconRules.rank(found).get(0).column());
        List<Column> ranked = ReconRules.rank(found, ReconRules.SIGHT).stream().map(Ground::column).toList();
        assertEquals(List.of(axis70, axis61, off3, hill90, off2deep), ranked,
                "the eyes' columns in their own order (the axis, the higher ground along it), then the deeper ones");
        // with only the deeper ones (trees over the eyes' band), the spyglass's best is the hill on the axis
        List<Ground> blinded = List.of(new Ground(off2deep, 5), new Ground(hill90, 9));
        assertEquals(hill90, ReconRules.rank(blinded, ReconRules.SIGHT).get(0).column());
        // the eyes' own band: nothing deeper, so the same as the old order
        List<Ground> near = List.of(new Ground(axis61, 0), new Ground(axis70, 1), new Ground(off3, 0));
        assertEquals(ReconRules.rank(near), ReconRules.rank(near, ReconRules.SIGHT));
    }

    @Test
    void theShareIsTheInstrumentsAtTheIntervalAndNoneForALairOrACounterAttack() {
        assertEquals(0.5, Reserve.share(false, false, ReconOptic.SPYGLASS));
        assertEquals(Reserve.share(false, false), Reserve.share(false, false, ReconOptic.NONE));
        assertEquals(0, Reserve.share(true, false, ReconOptic.SPYGLASS));
        assertEquals(0, Reserve.share(false, true, ReconOptic.SPYGLASS));
        ReconOptic drone = new ReconOptic("drone", "factionclash:drone", "a drone", 96, 0.25, 4);
        assertEquals(0.25, Reserve.share(false, false, drone), "a later row brings its own share");
    }
}
