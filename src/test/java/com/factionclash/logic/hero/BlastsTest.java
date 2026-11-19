package com.factionclash.logic.hero;

import com.factionclash.logic.hero.Blasts.Cell;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.MoveSelector.Pick;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Blasts} (HEROES_V2 6.18, task T2): a blast's reach, vanilla's ray rule (what the weakest and the strongest
 * rays break, obsidian kept, water soaking a blast up, nothing past the reach), where a move's blasts land, the pick's
 * own-side check (a score of 0 and no start from any source near its own side's blocks, as before elsewhere) and the
 * blast's decal disc.
 */
class BlastsTest {
    private static final double EPS = 1e-9;
    private static final String DMG = "{\"type\": \"damage\", \"amount\": 2}";

    /** A world of stone (6) below y 0, with the given cells of other blocks; air above. */
    private static Blasts.World ground(float below, Object... cells) {
        return (x, y, z) -> {
            for (int i = 0; i < cells.length; i += 2) {
                int[] c = (int[]) cells[i];
                if (c[0] == x && c[1] == y && c[2] == z) {
                    return (Float) cells[i + 1];
                }
            }
            return y < 0 ? below : -1;
        };
    }

    // ------------------------------------------------------------------ the reach and the ray rule

    @Test
    void aBlastReachesAboutOnePointSevenBlocksAPower() {
        assertEquals(1.3 * 0.3 / 0.225, Blasts.REACH_PER_POWER, 1e-4);
        assertEquals(1.733, Blasts.reach(1), 1e-3);
        assertEquals(3.467, Blasts.reach(2), 1e-3);
        assertEquals(6.933, Blasts.reach(4), 1e-3, "TNT's reach in the open");
        assertEquals(0, Blasts.reach(0), EPS);
    }

    @Test
    void theRayRuleBreaksWhatVanillaBreaksAndNoMore() {
        Blasts.World stone = ground(6F);
        // nothing to break in the open air
        assertTrue(Blasts.broken(0.5, 10.5, 0.5, 4F, Blasts.STRONGEST, (x, y, z) -> -1F).isEmpty());
        // TNT on a stone floor digs a crater; a stronger ray breaks a superset; nothing past the reach
        Set<Cell> weak = Blasts.broken(0.5, 0.0, 0.5, 4F, Blasts.WEAKEST, stone);
        Set<Cell> mid = Blasts.broken(0.5, 0.0, 0.5, 4F, 1.0F, stone);
        Set<Cell> strong = Blasts.broken(0.5, 0.0, 0.5, 4F, Blasts.STRONGEST, stone);
        assertFalse(weak.isEmpty(), "even its weakest rays break the stone under it");
        assertTrue(mid.containsAll(weak) && strong.containsAll(mid), "monotone in the ray strength");
        assertTrue(strong.size() > weak.size());
        assertTrue(weak.contains(new Cell(0, -1, 0)), "the block under the centre");
        for (Cell c : strong) {
            assertTrue(c.y() < 0, "only blocks break: " + c);
            double dx = Math.max(0, Math.max(c.x() - 0.5, 0.5 - (c.x() + 1)));
            double dy = Math.max(0, -c.y() - 1.0);
            double dz = Math.max(0, Math.max(c.z() - 0.5, 0.5 - (c.z() + 1)));
            assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= Blasts.reach(4) + 1e-6, "past the reach: " + c);
        }
        // a power-2 blast just off a stone-brick wall (6): its weakest rays break nothing, its strongest the face
        Blasts.World wall = (x, y, z) -> x == 1 && y >= 0 && y <= 3 && z >= -3 && z <= 3 ? 6F : -1F;
        assertTrue(Blasts.broken(0.95, 1.5, 0.5, 2F, Blasts.WEAKEST, wall).isEmpty());
        Set<Cell> face = Blasts.broken(0.95, 1.5, 0.5, 2F, Blasts.STRONGEST, wall);
        assertTrue(face.contains(new Cell(1, 1, 0)), "the wall block it struck: " + face);
        // obsidian (1200) and bedrock never break, whatever the power
        Blasts.World obsidian = ground(1200F);
        assertTrue(Blasts.broken(0.5, 0.0, 0.5, 4F, Blasts.STRONGEST, obsidian).isEmpty());
        // water (100) soaks a blast up: stone behind a water block survives a blast that would break it bare
        Blasts.World bare = (x, y, z) -> x == 2 && y == 0 && z == 0 ? 6F : -1F;
        Blasts.World soaked = (x, y, z) -> x == 2 && y == 0 && z == 0 ? 6F : x == 1 && y == 0 && z == 0 ? 100F : -1F;
        assertTrue(Blasts.broken(0.5, 0.5, 0.5, 4F, Blasts.STRONGEST, bare).contains(new Cell(2, 0, 0)));
        assertFalse(Blasts.broken(0.5, 0.5, 0.5, 4F, Blasts.STRONGEST, soaked).contains(new Cell(2, 0, 0)));
        // a ray stops at the world's edge
        Blasts.World edge = new Blasts.World() {
            @Override
            public float resistance(int x, int y, int z) {
                return 0.5F;
            }

            @Override
            public boolean inWorld(int x, int y, int z) {
                return y >= 0;
            }
        };
        for (Cell c : Blasts.broken(0.5, 0.5, 0.5, 2F, Blasts.STRONGEST, edge)) {
            assertTrue(c.y() >= 0, "below the world: " + c);
        }
    }

    // ------------------------------------------------------------------ where a move's blasts land

    private static Move bomb(String fields) {
        return V2Files.move(V2Files.def(V2Files.withMove("\"id\": \"bomb\", \"name\": \"Bomb\", " + fields
                + ", \"cooldown\": 60, \"effects\": [" + DMG + "], \"explosive\": 1")), "bomb");
    }

    @Test
    void aMoveBlastsAtItsTargetOrAtItself() {
        assertEquals(Blasts.At.TARGET, Blasts.at(bomb("\"form\": \"shot\"")));
        assertEquals(Blasts.At.SELF, Blasts.at(bomb("\"form\": \"nova\", \"radius\": 3")));
        assertEquals(Blasts.At.SELF, Blasts.at(bomb("\"form\": \"zone\", \"zone_at\": \"self\", \"radius\": 3")));
        assertEquals(Blasts.At.TARGET, Blasts.at(bomb("\"form\": \"zone\", \"zone_at\": \"target\", \"range\": 10")));
        assertEquals(Blasts.At.TARGET, Blasts.at(bomb("\"form\": \"leap\"")));
        assertEquals(0, Blasts.spread(bomb("\"form\": \"shot\""), 10), EPS);
        assertEquals(4.5, Blasts.spread(bomb("\"form\": \"rain\", \"radius\": 4, \"count\": 4, \"delay\": 0.9"), 10), EPS,
                "a rain's impacts land within its radius");
        assertEquals(10 * Math.sin(Math.toRadians(10)), Blasts.spread(bomb("\"form\": \"volley\", \"count\": 3"), 10), EPS);
        assertEquals(1.5, Blasts.spread(bomb("\"form\": \"leap\""), 6), EPS);
    }

    // ------------------------------------------------------------------ the own-side check of the pick

    @Test
    void thePickNeverBlastsNearItsOwnSide() {
        String shot = MoveSelectorTest.move("bomb", "shot", "\"range\": 16, \"cooldown\": 30, \"explosive\": 1");
        HeroDef.V2 kit = MoveSelectorTest.kit(null, V2Files.LUNGE, shot);
        Move m = kit.move("bomb");
        assertNotNull(m.explosive());
        double reach = Blasts.reach(1);
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 7).runnable(x -> true);
        assertTrue(s.explosive, "the kit has an explosive move");
        Sense far = MoveSelectorTest.at(5);
        assertEquals(1, s.ownSideFactor(m, far), EPS, "none of its side near: as before");
        Sense near = MoveSelectorTest.at(5);
        near.ownFromTarget = reach - 0.1;
        assertEquals(0, s.ownSideFactor(m, near), EPS);
        assertEquals(0, s.score(m, near), EPS, "a blast that reaches its own side's blocks scores 0");
        assertFalse(s.eligible(m, 0, near), "nor does a followup, a reaction or an opener start it");
        Sense outside = MoveSelectorTest.at(5);
        outside.ownFromTarget = reach + 0.1;
        assertTrue(s.score(m, outside) > 0);
        // only the explosive move is held back; the lunge beside it is picked as before
        Move lunge = kit.move("lunge");
        assertEquals(1, s.ownSideFactor(lunge, near), EPS);
        // a nova blasts where the hero stands: its own distance counts, not its target's
        String nova = MoveSelectorTest.move("burst", "nova", "\"radius\": 3, \"cooldown\": 30, \"explosive\": 2");
        HeroDef.V2 kn = MoveSelectorTest.kit(null, nova);
        MoveSelector sn = new MoveSelector(kn, 50, 50, false, 7).runnable(x -> true);
        Sense home = MoveSelectorTest.at(3);
        home.ownFromSelf = 2;
        assertEquals(0, sn.ownSideFactor(kn.move("burst"), home), EPS);
        home.ownFromSelf = Double.POSITIVE_INFINITY;
        home.ownFromTarget = 0;
        assertEquals(1, sn.ownSideFactor(kn.move("burst"), home), EPS);
        // a kit with no explosive move senses nothing of its side
        assertFalse(new MoveSelector(MoveSelectorTest.kit(null, V2Files.LUNGE), 50, 50, false, 7).explosive);
        // over many checks, a selector near its own side never picks the blast
        Pick[] out = new Pick[1];
        near.ownFromTarget = 0;
        s.fightStarted(0);
        for (long t = 0; t < 2000; t += MoveSelector.CHECK_TICKS) {
            Pick p = s.check(t, near);
            if (p != null) {
                out[0] = p;
                assertFalse(p.move().id().equals("bomb"), "picked the blast at tick " + t);
            }
        }
        assertNotNull(out[0], "the lunge was still picked");
        assertTrue(new Sense().clear().ownFromTarget == Double.POSITIVE_INFINITY, "a fresh sense knows no side near");
    }

    // ------------------------------------------------------------------ the decal

    @Test
    void aBlastDrawsADiscOfItsReachWhereItLands() {
        Move shot = bomb("\"form\": \"shot\", \"range\": 16");
        List<Decals.Shape> s = Decals.forMove(shot, Decals.Aim.ahead(10));
        assertEquals(2, s.size(), "its track and its blast disc");
        assertEquals(Decal.STRIP, s.get(0).kind());
        Decals.Shape disc = s.get(1);
        assertEquals(Decal.DISC, disc.kind());
        assertEquals(10, disc.pieces().get(0).z(), EPS, "at the target");
        assertEquals(Blasts.reach(1) * Decals.DISC_SIDE, disc.pieces().get(0).width(), 1e-9);
        // a nova's at the hero, a leap's where it lands
        Decals.Shape nova = Decals.forMove(bomb("\"form\": \"nova\", \"radius\": 3"), Decals.Aim.ahead(4)).get(1);
        assertEquals(0, nova.pieces().get(0).z(), EPS);
        Decals.Shape leap = Decals.forMove(bomb("\"form\": \"leap\""), new Decals.Aim(0, 1, 0, 6, 0, 5, List.of())).get(1);
        assertEquals(5, leap.pieces().get(0).z(), EPS);
        // a rain's impacts draw theirs, as wide as the blast; a move with no blast draws as before
        Move rain = bomb("\"form\": \"rain\", \"radius\": 4, \"count\": 4, \"delay\": 0.9");
        assertEquals(Math.max(Decals.IMPACT_RADIUS, Blasts.reach(1)), Decals.impactRadius(rain), EPS);
        assertTrue(Decals.forMove(rain, Decals.Aim.ahead(6)).isEmpty(), "its impacts draw theirs as each is called");
        Move plain = V2Files.move(V2Files.def(V2Files.withMove("\"id\": \"plain\", \"name\": \"Plain\", \"form\": \"shot\","
                + " \"class\": \"heavy\", \"cooldown\": 60, \"effects\": [" + DMG + "]")), "plain");
        assertNull(plain.explosive());
        assertEquals(1, Decals.forMove(plain, Decals.Aim.ahead(10)).size());
        assertEquals(Decals.IMPACT_RADIUS, Decals.impactRadius(plain), EPS);
    }
}
