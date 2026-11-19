package com.factionclash.logic.hero;

import com.factionclash.logic.hero.KillCredit.DeathType;
import com.factionclash.logic.hero.KillCredit.Fall;
import com.factionclash.logic.hero.KillCredit.Kind;
import com.factionclash.logic.hero.KillCredit.Ledger;
import com.factionclash.logic.hero.KillCredit.Party;
import com.factionclash.logic.hero.KillCredit.Words;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HEROES_V2 6.17 and {@code docs/design/breaching.md} 11.1: the 15 s window, a push with no damage credited, the newest
 * pusher wins, and vanilla's message key for each terrain death.
 */
class KillCreditTest {
    private static final long T = 10_000;

    @Test
    void theWindowIsFifteenSecondsForTerrainAndVanillasFiveForTheRest() {
        Ledger<String> l = new Ledger<>();
        l.record("hero", T, Kind.PUSH);
        assertEquals("hero", l.credited(T + 299, "lava"), "14.95 s after the push");
        assertNull(l.credited(T + 300, "lava"), "15 s after: no credit");
        assertEquals("hero", l.credited(T + 200, "fall"));
        assertEquals("hero", l.credited(T + 200, "outOfWorld"));
        assertEquals("hero", l.credited(T + 99, "starve"), "a death that is no terrain keeps vanilla's 5 s");
        assertNull(l.credited(T + 100, "starve"));
        assertNull(l.credited(T - 1, "lava"), "nothing before the push");
        assertTrue(l.expired(T + 300) && !l.expired(T + 299));
        assertNull(new Ledger<String>().credited(T, "lava"), "no push, no credit");
    }

    @Test
    void aPushWithNoDamageCountsAndTheNewestEnemyWins() {
        Ledger<String> l = new Ledger<>();
        l.record("soldier", T, Kind.BLOW);
        l.record("hero", T + 40, Kind.PUSH); // no damage: a push alone
        assertEquals("hero", l.credited(T + 100, "lava"));
        assertEquals(Kind.PUSH, l.kind());
        l.record("player", T + 60, Kind.BLOW);
        assertEquals("player", l.credited(T + 100, "lava"), "the newest one's, whoever it is");
        l.record(null, T + 80, Kind.PUSH);
        assertEquals("player", l.credited(T + 100, "lava"), "nobody is no one to credit");
    }

    @Test
    void theKillerIsTheAttackerElseTheCredit() {
        assertEquals("archer", KillCredit.killer("archer", "hero"));
        assertEquals("hero", KillCredit.killer(null, "hero"));
        assertNull(KillCredit.killer(null, null));
    }

    @Test
    void terrainIsTheDesignsList() {
        for (String id : new String[]{"fall", "lava", "inFire", "onFire", "hotFloor", "outOfWorld", "drown", "freeze",
                "inWall", "cactus", "sweetBerryBush", "stalagmite"}) {
            assertTrue(KillCredit.terrain(id), id);
        }
        for (String id : new String[]{"mob", "player", "arrow", "starve", "magic", "wither", "generic", "explosion",
                "factionclash.hero_fire", null}) {
            assertFalse(KillCredit.terrain(id), String.valueOf(id));
        }
    }

    @Test
    void theRefreshRunsWhileFallingBurningOrSwimmingUpToFifteenSeconds() {
        assertTrue(KillCredit.refreshes(T, T + 20, true, false, false, false, false));
        assertTrue(KillCredit.refreshes(T, T + 120, false, true, false, false, false), "burning, after vanilla's 5 s");
        assertTrue(KillCredit.refreshes(T, T + 299, false, false, true, false, false));
        assertTrue(KillCredit.refreshes(T, T + 40, false, false, false, true, false));
        assertTrue(KillCredit.refreshes(T, T + 40, false, false, false, false, true));
        assertFalse(KillCredit.refreshes(T, T + 300, true, true, true, true, true), "15 s are over");
        assertFalse(KillCredit.refreshes(T, T + 40, false, false, false, false, false), "standing safe on the ground");
        assertFalse(KillCredit.refreshes(Long.MIN_VALUE, T, true, false, false, false, false));
    }

    @Test
    void aPusherIsWrittenIntoTheTrackerOnceASecond() {
        Ledger<String> l = new Ledger<>();
        assertTrue(l.track("hero", T));
        assertFalse(l.track("hero", T + 1), "a tether's next tick");
        assertFalse(l.track("hero", T + 19));
        assertTrue(l.track("hero", T + 20));
        assertTrue(l.track("other", T + 21), "another pusher at once");
        assertFalse(l.track(null, T + 22));
    }

    private static Words terrain(String msgId, boolean credited) {
        return KillCredit.words(msgId, DeathType.DEFAULT, false, false, null, credited, false);
    }

    /** breaching.md 8.5's words: vanilla's {@code .player} keys with the credited enemy. */
    @Test
    void eachTerrainDeathHasVanillasKeyWithItsKiller() {
        assertEquals(new Words("death.attack.lava.player", Party.CREDITED, false, false), terrain("lava", true));
        assertEquals("death.attack.inFire.player", terrain("inFire", true).key());
        assertEquals("death.attack.onFire.player", terrain("onFire", true).key());
        assertEquals("death.attack.hotFloor.player", terrain("hotFloor", true).key());
        assertEquals("death.attack.outOfWorld.player", terrain("outOfWorld", true).key());
        assertEquals("death.attack.drown.player", terrain("drown", true).key());
        assertEquals("death.attack.freeze.player", terrain("freeze", true).key());
        assertEquals("death.attack.inWall.player", terrain("inWall", true).key());
        assertEquals("death.attack.cactus.player", terrain("cactus", true).key());
        assertEquals("death.attack.sweetBerryBush.player", terrain("sweetBerryBush", true).key());
        assertEquals(new Words("death.attack.lava", Party.NONE, false, false), terrain("lava", false), "alone");
        assertEquals(2, terrain("lava", true).arguments());
        assertEquals(1, terrain("lava", false).arguments());
    }

    @Test
    void aFallReadsAsVanillasFallVariants() {
        // pushed (a hit of 0 by the pusher) and then the fall: "was doomed to fall by"
        Words pushed = KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false, Fall.byHit(true, false, false), true, false);
        assertEquals(new Words("death.fell.assist", Party.STARTER, false, false), pushed);
        Words named = KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false, Fall.byHit(true, false, true), true, false);
        assertEquals(new Words("death.fell.assist.item", Party.STARTER, true, false), named);
        assertEquals(3, named.arguments());
        // a fall with no hit before it, but an enemy's push within 15 s
        Words assisted = KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false,
                Fall.accident("death.fell.accident.generic"), true, false);
        assertEquals(new Words("death.fell.assist", Party.CREDITED, false, false), assisted);
        // nobody near: vanilla's accident key
        assertEquals("death.fell.accident.ladder", KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false,
                Fall.accident("death.fell.accident.ladder"), false, false).key());
        // a hit with no attacker began it (a cactus): "was doomed to fall"; with a credit, the credited enemy
        assertEquals("death.fell.killer", KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false,
                Fall.byHit(false, false, false), false, false).key());
        assertEquals(new Words("death.fell.assist", Party.CREDITED, false, false), KillCredit.words("fall",
                DeathType.FALL_VARIANTS, false, false, Fall.byHit(false, false, false), true, false));
        // the killing fall's own attacker began it too: "fell too far and was finished by"
        assertEquals(new Words("death.fell.finish", Party.ATTACKER, false, false), KillCredit.words("fall",
                DeathType.FALL_VARIANTS, true, false, Fall.byHit(true, true, false), true, false));
        // no fall longer than 5 blocks: vanilla's "hit the ground too hard" (with the credit's ".player")
        assertEquals("death.attack.fall.player", KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false, null,
                true, false).key());
        assertEquals("death.attack.fall", KillCredit.words("fall", DeathType.FALL_VARIANTS, false, false, null,
                false, false).key());
    }

    @Test
    void aBlowIsTheAttackersAsVanillaSaysIt() {
        assertEquals(new Words("death.attack.mob", Party.ATTACKER, false, false), KillCredit.words("mob",
                DeathType.DEFAULT, true, false, null, true, false), "the attacker, never the credit");
        assertEquals(new Words("death.attack.mob.item", Party.ATTACKER, true, false), KillCredit.words("mob",
                DeathType.DEFAULT, true, true, null, false, false));
        assertEquals("death.attack.factionclash.hero_fire", KillCredit.words("factionclash.hero_fire",
                DeathType.DEFAULT, true, false, null, false, false).key());
        assertEquals("death.attack.arrow", KillCredit.words("arrow", DeathType.DEFAULT, true, false,
                Fall.byHit(true, true, false), false, false).key(), "a blow kills: the fall does not matter");
        Words bed = KillCredit.words("badRespawnPoint", DeathType.INTENTIONAL_GAME_DESIGN, false, false, null, true,
                false);
        assertEquals(new Words("death.attack.badRespawnPoint.message", Party.NONE, false, true), bed);
        assertEquals(2, bed.arguments());
    }
}
