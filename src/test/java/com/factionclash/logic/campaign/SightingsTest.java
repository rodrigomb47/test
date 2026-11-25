package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Sightings.Base;
import com.factionclash.logic.campaign.Sightings.Sighting;
import com.factionclash.logic.campaign.Sightings.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sightings ledger ({@code docs/design/recon.md} 5.5 and 10.1 item 11): the newest sighting per entity, a soldier's
 * fall that sticks (a hero's does not), the cap of 64, ageing, and the base a sighting counts for.
 */
class SightingsTest {
    static final long REVIVE = 12000;

    static Sighting soldier(UUID id, long at, int x, int z) {
        return new Sighting(id, "stoneguard", "", 1.5, at, x, z, Source.HOME, "", Sightings.NO_BASE, false);
    }

    static Sighting hero(UUID id, String hero, long at) {
        return new Sighting(id, "stoneguard", hero, 6.8, at, 0, 0, Source.HOME, "", Sightings.NO_BASE, false);
    }

    @Test
    void theNewestSightingOfEachEntityIsKept() {
        Sightings l = new Sightings();
        UUID a = UUID.randomUUID();
        l.file(soldier(a, 100, 0, 0));
        l.file(soldier(a, 200, 5, 5));
        assertEquals(1, l.size());
        assertEquals(200, l.get(a).at());
        l.file(soldier(a, 150, 9, 9));
        assertEquals(200, l.get(a).at(), "an older sighting told late does not replace a newer one");
        assertEquals(5, l.get(a).x());
        l.file(null);
        assertEquals(1, l.size());
    }

    @Test
    void aSoldierSeenToFallStaysFallenButAHeroRisesAgain() {
        Sightings l = new Sightings();
        UUID s = UUID.randomUUID();
        l.file(soldier(s, 100, 0, 0));
        l.file(soldier(s, 150, 0, 0).fell(150));
        assertTrue(l.get(s).fallen());
        l.file(soldier(s, 300, 0, 0));
        assertTrue(l.get(s).fallen(), "a later sighting does not undo a soldier's fall");
        assertFalse(Sightings.counts(l.get(s), 300, REVIVE), "a soldier seen to fall counts nothing");
        UUID h = UUID.randomUUID();
        l.file(hero(h, "hinae", 100));
        l.file(hero(h, "hinae", 150).fell(150));
        assertTrue(l.get(h).fallen());
        l.file(hero(h, "hinae", 300));
        assertFalse(l.get(h).fallen(), "a hero seen again after its fall has risen");
        l.file(hero(h, "hinae", 200).fell(200));
        assertFalse(l.get(h).fallen(), "a fall carried home late, older than the hero seen alive, changes nothing");
        // a fall of a soldier nobody had seen before is kept, so a later sighting cannot count it
        UUID t = UUID.randomUUID();
        l.file(soldier(t, 400, 0, 0).fell(400));
        l.file(soldier(t, 500, 0, 0));
        assertTrue(l.get(t).fallen());
    }

    @Test
    void aSightingCountsHalfADayAHeroSeenFallingFromItsFallToHalfADayAfterItsReturn() {
        Sighting s = soldier(UUID.randomUUID(), 1000, 0, 0);
        assertTrue(Sightings.counts(s, 1000 + 12000, REVIVE), "12000 ticks ago: in");
        assertFalse(Sightings.counts(s, 1000 + 12001, REVIVE), "12001: out, no fading in between");
        assertTrue(Sightings.counts(s, 500, REVIVE), "dated after now (a clock set back): in");
        Sighting fell = hero(UUID.randomUUID(), "hinae", 1000).fell(1000);
        assertTrue(Sightings.counts(fell, 1001, REVIVE), "from its fall");
        assertTrue(Sightings.counts(fell, 1000 + REVIVE + 12000, REVIVE), "to half a day after its expected return");
        assertFalse(Sightings.counts(fell, 1000 + REVIVE + 12001, REVIVE));
        assertEquals(1000 + REVIVE + 12000, Sightings.countsUntil(fell, REVIVE));
        Sightings l = new Sightings();
        l.file(s);
        l.file(fell);
        l.prune(1000 + 12001, REVIVE);
        assertEquals(1, l.size(), "the soldier's sighting aged out; the fallen hero's stays until its return plus half a day");
        assertNotNull(l.get(fell.id()));
        l.prune(1000 + REVIVE + 12001, REVIVE);
        assertEquals(0, l.size());
    }

    @Test
    void theLedgerKeepsSixtyFourTheOldestDroppedFirst() {
        Sightings l = new Sightings();
        UUID first = UUID.randomUUID();
        l.file(soldier(first, 1000, 0, 0));
        for (int i = 1; i < Sightings.MAX; i++) {
            l.file(soldier(UUID.randomUUID(), 1000 + i, 0, 0));
        }
        assertEquals(64, l.size());
        assertNotNull(l.get(first));
        UUID newest = UUID.randomUUID();
        l.file(soldier(newest, 5000, 0, 0));
        assertEquals(64, l.size());
        assertNull(l.get(first), "the oldest dropped");
        assertNotNull(l.get(newest));
        UUID ancient = UUID.randomUUID();
        l.file(soldier(ancient, 1, 0, 0));
        assertEquals(64, l.size());
        assertNull(l.get(ancient), "an older sighting told late into a full ledger is the one dropped");
        assertEquals(64, l.all().size());
    }

    /** A sighting counts for its faction's reported base nearest to where it was seen; a watch's for the base watched. */
    @Test
    void aSightingCountsForTheNearestReportedBaseOfItsFaction() {
        List<Base> reported = List.of(new Base(1, "stoneguard", 400, 0), new Base(2, "stoneguard", -300, 200),
                new Base(3, "ember", 10, 10), new Base(4, "tide", 0, 500));
        Sighting nearOurs = soldier(UUID.randomUUID(), 100, 0, 0);
        assertEquals(2, Sightings.attribute(nearOurs, reported), "Stoneguard's nearest, not Ember's base right there");
        assertEquals(1, Sightings.attribute(soldier(UUID.randomUUID(), 100, 300, 50), reported));
        Sighting watched = new Sighting(UUID.randomUUID(), "stoneguard", "", 1.5, 100, 0, 0, Source.WATCH, "Edrin Cole", 1, false);
        assertEquals(1, Sightings.attribute(watched, reported), "a watch's sighting counts for the base watched");
        Sighting grove = new Sighting(UUID.randomUUID(), "grove", "", 1, 100, 0, 0, Source.FIELD, "Edrin Cole", Sightings.NO_BASE, false);
        assertEquals(Sightings.NO_BASE, Sightings.attribute(grove, reported), "none of its bases is known");
        Sightings l = new Sightings();
        l.file(nearOurs);
        l.file(watched);
        l.file(grove);
        assertEquals(List.of(nearOurs), l.forBase(2, reported));
        assertEquals(List.of(watched), l.forBase(1, reported));
        assertTrue(l.forBase(3, reported).isEmpty());
    }
}
