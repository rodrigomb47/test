package com.factionclash.logic.hero;

import com.factionclash.logic.hero.Decals.Aim;
import com.factionclash.logic.hero.Decals.Piece;
import com.factionclash.logic.hero.Decals.Shape;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Decals}: the shapes of 6.4 built from flat boxes (a disc never smaller than its circle, strips along their
 * aim, fans and rings), the display transformation that puts a unit block where a piece lies and keeps its grow point
 * fixed, each form's decal (kit-gaps 4 as F3 decided it), the colours, the growth timing and the budget of 12.
 */
class DecalsTest {
    private static final double EPS = 1e-9;

    /** Whether (px, pz) lies inside a piece (on its ground rectangle). */
    private static boolean inside(Piece p, double px, double pz) {
        double[] l = p.axis();
        double dx = px - p.x(), dz = pz - p.z();
        double along = dx * l[0] + dz * l[1], across = dx * l[1] - dz * l[0];
        return Math.abs(along) <= p.length() / 2 + 1e-9 && Math.abs(across) <= p.width() / 2 + 1e-9;
    }

    private static boolean covered(Shape s, double px, double pz) {
        for (Piece p : s.pieces()) {
            if (inside(p, px, pz)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aDiscIsTwoSquaresThatNeverUnderstateTheCircle() {
        Shape d = Decals.disc(2, 3, 4);
        assertEquals(2, d.pieces().size());
        assertEquals(0, d.pieces().get(0).yaw(), EPS);
        assertEquals(Math.PI / 4, d.pieces().get(1).yaw(), EPS);
        double maxOut = 0;
        for (int a = 0; a < 360; a++) {
            double t = Math.toRadians(a);
            assertTrue(covered(d, 2 + Math.sin(t) * 3.999, 3 + Math.cos(t) * 3.999), "every point of the circle, " + a);
            // how far the star reaches in this direction
            double r = 4;
            while (covered(d, 2 + Math.sin(t) * r, 3 + Math.cos(t) * r)) {
                r += 0.01;
            }
            maxOut = Math.max(maxOut, r);
        }
        assertTrue(maxOut < 4 * 1.31, "the points reach about 31% beyond: " + maxOut);
    }

    @Test
    void aStripRunsFromItsOriginAlongItsAim() {
        Shape s = Decals.strip(0, 0, 1, 1, 10, 1);
        Piece p = s.pieces().get(0);
        double k = 10 / Math.sqrt(2) / 2;
        assertEquals(k, p.x(), 1e-9);
        assertEquals(k, p.z(), 1e-9);
        assertTrue(inside(p, 0.01, 0.01));
        assertTrue(inside(p, 7.0, 7.0));
        assertFalse(inside(p, 7.2, 7.2), "10 blocks long");
        assertFalse(inside(p, 1, -1), "1 wide");
        assertEquals(0, p.growFrom(), EPS, "it grows from the hero outward");
        double[] g = p.growTranslation();
        assertEquals(-k, g[0], 1e-9, "the grow point is the strip's near end");
        assertEquals(-k, g[2], 1e-9);
        assertEquals(Decals.LIFT, g[1], EPS);
    }

    @Test
    void theDisplayTranslationPutsTheUnitBlockOnThePiece() {
        // the block model spans 0..1; vertex = T + R(S v). Its bottom centre must land on the piece's centre.
        for (double yaw : new double[]{0, 0.3, Math.PI / 4, 2, -1.1}) {
            Piece p = new Piece(5, -2, yaw, 1.5, 6, 0.5);
            double[] t = p.translation();
            double[] sc = p.scale();
            double c = Math.cos(yaw), s = Math.sin(yaw);
            double vx = 0.5 * sc[0], vz = 0.5 * sc[2];
            double wx = t[0] + vx * c + vz * s, wz = t[2] - vx * s + vz * c;
            assertEquals(0, wx, 1e-9, "relative to the centre the display is spawned at");
            assertEquals(0, wz, 1e-9);
            assertEquals(Decals.LIFT, t[1], EPS);
            // and the far end of the local z axis lies along axis()
            double fx = t[0] + 0.5 * sc[0] * c + sc[2] * s, fz = t[2] - 0.5 * sc[0] * s + sc[2] * c;
            assertEquals(p.axis()[0] * 3, fx, 1e-9);
            assertEquals(p.axis()[1] * 3, fz, 1e-9);
            assertEquals(Decals.THICKNESS, sc[1], EPS);
        }
    }

    @Test
    void aFanHasOneRibPerThirtyDegreesAndARingIsTangent() {
        assertEquals(3, Decals.fan(0, 0, 0, 1, 5, 30).pieces().size(), "at least 3");
        assertEquals(4, Decals.fan(0, 0, 0, 1, 5, 90).pieces().size());
        assertEquals(5, Decals.fan(0, 0, 0, 1, 5, 120).pieces().size());
        assertEquals(5, Decals.fan(0, 0, 0, 1, 5, 180).pieces().size(), "at most 5");
        Shape fan = Decals.fan(0, 0, 0, 1, 8, 120);
        assertTrue(covered(fan, Math.sin(Math.toRadians(60)) * 7.9, Math.cos(Math.toRadians(60)) * 7.9),
                "the outer ribs mark the arc's edges");
        Shape ring = Decals.ring(0, 0, 10);
        assertEquals(12, ring.pieces().size(), "at most 12");
        assertEquals(6, Decals.ring(0, 0, 1).pieces().size(), "at least 6");
        for (int a = 0; a < 360; a += 3) {
            double t = Math.toRadians(a);
            assertTrue(covered(ring, Math.sin(t) * 10.4, Math.cos(t) * 10.4), "the band just outside the radius " + a);
            assertFalse(covered(ring, Math.sin(t) * 9.5, Math.cos(t) * 9.5), "nothing inside it " + a);
        }
    }

    @Test
    void eachFormDrawsTheGroundItCanHit() {
        String dmg = "\"effects\": [{\"type\": \"damage\", \"amount\": 2}]";
        HeroDef.V2 k = V2Files.def(V2Files.hero2("\"moves\": ["
                + "{\"id\": \"smash\", \"name\": \"A\", \"form\": \"melee\", \"class\": \"heavy\", \"radius\": 1, "
                + dmg + "},"
                + "{\"id\": \"burst\", \"name\": \"B\", \"form\": \"nova\", \"radius\": 5, " + dmg + "},"
                + "{\"id\": \"pool\", \"name\": \"C\", \"form\": \"zone\", \"zone_at\": \"target\", \"range\": 12,"
                + " \"radius\": 3, " + dmg + "},"
                + "{\"id\": \"beam\", \"name\": \"D\", \"form\": \"line\", \"range\": 8, \"width\": 2, " + dmg + "},"
                + "{\"id\": \"rush\", \"name\": \"E\", \"form\": \"dash\", \"range\": 6, " + dmg + "},"
                + "{\"id\": \"fan\", \"name\": \"F\", \"form\": \"cone\", \"range\": 5, \"angle\": 90, " + dmg + "},"
                + "{\"id\": \"pounce\", \"name\": \"G\", \"form\": \"leap\", \"range\": 8, \"radius\": 2, " + dmg + "},"
                + "{\"id\": \"quake\", \"name\": \"H\", \"form\": \"wave\", \"radius\": 10, " + dmg + "}]")).v2();
        // a hero holds at most 12 moves: the rest in a second kit
        HeroDef.V2 k2 = V2Files.def(V2Files.hero2("\"moves\": ["
                + "{\"id\": \"stance\", \"name\": \"I\", \"form\": \"counter\", \"class\": \"heavy\", " + dmg + "},"
                + "{\"id\": \"storm\", \"name\": \"J\", \"form\": \"rain\", \"count\": 4, " + dmg + "},"
                + "{\"id\": \"arc\", \"name\": \"K\", \"form\": \"chain\", \"class\": \"heavy\", \"radius\": 4, "
                + dmg + "},"
                + "{\"id\": \"turn\", \"name\": \"L\", \"form\": \"sweep\", \"range\": 8, \"arc\": 180, " + dmg + "},"
                + "{\"id\": \"focus\", \"name\": \"M\", \"form\": \"self\", \"class\": \"heavy\", \"effects\":"
                + " [{\"type\": \"cleanse\"}]},"
                + "{\"id\": \"jab\", \"name\": \"N\", \"form\": \"melee\", " + dmg + "},"
                + "{\"id\": \"grab\", \"name\": \"O\", \"form\": \"grab\", " + dmg + "}]")).v2();
        Aim aim = new Aim(0, 1, 0, 6, 0, 7, List.of(new double[]{1, 9}, new double[]{-2, 8}));
        // heavy melee: a disc in front out to its reach of 3.5, plus its splash
        Shape smash = Decals.forMove(k.move("smash"), aim).get(0);
        assertEquals(Decal.DISC, smash.kind());
        assertTrue(covered(smash, 0, 3.5 + 0.99), "reach 3.5 + splash 1");
        assertTrue(covered(smash, 0, 0.2));
        assertTrue(covered(Decals.forMove(k.move("burst"), aim).get(0), 0, 4.99), "a nova's radius around it");
        Shape pool = Decals.forMove(k.move("pool"), aim).get(0);
        assertTrue(covered(pool, 0, 6 + 2.99) && !covered(pool, 0, 0), "a zone at the target");
        Shape beam = Decals.forMove(k.move("beam"), aim).get(0);
        assertEquals(Decal.STRIP, beam.kind());
        assertEquals(2, beam.pieces().get(0).width(), EPS, "a line its width");
        assertEquals(8, beam.pieces().get(0).length(), EPS);
        assertEquals(Decals.BODY_WIDTH, Decals.forMove(k.move("rush"), aim).get(0).pieces().get(0).width(), EPS);
        assertEquals(6, Decals.forMove(k.move("rush"), aim).get(0).pieces().get(0).length(), EPS);
        assertEquals(4, Decals.forMove(k.move("fan"), aim).get(0).pieces().size(), "90 degrees: 4 ribs");
        Shape jump = Decals.forMove(k.move("pounce"), aim).get(0);
        assertEquals(Decal.LANDING, jump.kind());
        assertTrue(covered(jump, 0, 7 + 1.99), "where it lands");
        Shape quake = Decals.forMove(k.move("quake"), aim).get(0);
        assertEquals(Decal.RING, quake.kind());
        assertTrue(covered(quake, 0, 10.2));
        Shape stance = Decals.forMove(k2.move("stance"), aim).get(0);
        assertEquals(Decal.RING, stance.kind(), "a counter stance: a ring at the reach it answers");
        assertTrue(covered(stance, 0, MoveSelector.COUNTER_REACH + 0.2));
        List<Shape> storm = Decals.forMove(k2.move("storm"), aim);
        assertEquals(2, storm.size(), "one disc per impact given");
        assertTrue(covered(storm.get(0), 1, 9 + 1.49));
        Shape arc = Decals.forMove(k2.move("arc"), aim).get(0);
        assertTrue(covered(arc, 3.99, 6), "a chain: the ground its lightning jumps across around the target");
        Shape turn = Decals.forMove(k2.move("turn"), aim).get(0);
        assertEquals(Decal.FAN, turn.kind());
        assertEquals(5, turn.pieces().size(), "a 180-degree sweep");
        Shape focus = Decals.forMove(k2.move("focus"), aim).get(0);
        assertTrue(covered(focus, 0, Decals.SELF_RADIUS - 0.01), "a heavy self move marks the hero's ground");
        assertTrue(Decals.forMove(k2.move("jab"), aim).isEmpty(), "a light move shows only its animation");
        Shape grab = Decals.forMove(k2.move("grab"), aim).get(0);
        assertEquals(Decal.STRIP, grab.kind());
        assertTrue(k2.move("grab").unblockable(), "a grab is unblockable");
        assertEquals(Dye.PURPLE, Decals.dye(k2.move("grab").cls(), k2.move("grab").unblockable(), Dye.CYAN));
    }

    @Test
    void coloursAndGrowth() {
        assertEquals(Dye.RED, Decals.dye(MoveClass.ULTIMATE, false, Dye.CYAN), "an ultimate");
        assertEquals(Dye.RED, Decals.dye(MoveClass.ULTIMATE, true, Dye.CYAN));
        assertEquals(Dye.PURPLE, Decals.dye(MoveClass.HEAVY, true, Dye.CYAN), "unblockable");
        assertEquals(Dye.CYAN, Decals.dye(MoveClass.AREA, false, Dye.CYAN), "the accent's dye");
        assertEquals(Dye.WHITE, Decals.dye(MoveClass.AREA, false, null));
        assertEquals(15, Decals.growTicks(16), "spawned at 0, full one tick later over the rest of the wind-up");
        assertEquals(1, Decals.growTicks(1));
        assertEquals(1, Decals.growTicks(0));
    }

    @Test
    void theBudgetIsTwelvePieces() {
        List<Shape> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(Decals.disc(i, 0, 1.5));
        }
        assertEquals(12, Decals.pieces(Decals.fit(six, 0)), "six discs fill it");
        List<Shape> one = Decals.fit(List.of(Decals.disc(0, 0, 2)), 11);
        assertEquals(1, Decals.pieces(one), "room for one: one square");
        Piece sq = one.get(0).pieces().get(0);
        assertEquals(4, sq.width(), 1e-9, "of side 2r, still covering the circle");
        assertTrue(Decals.fit(List.of(Decals.disc(0, 0, 2)), 12).isEmpty(), "no room: it waits");
        List<Shape> ring = Decals.fit(List.of(Decals.ring(0, 0, 10)), 7);
        assertEquals(5, Decals.pieces(ring), "a ring thinned to the room left");
        assertTrue(Decals.fit(List.of(Decals.ring(0, 0, 10)), 10).isEmpty(), "fewer than 3: left out");
        assertTrue(Decals.fit(List.of(Decals.strip(0, 0, 0, 1, 5, 1)), 12).isEmpty());
        assertEquals(1, Decals.pieces(Decals.fit(List.of(Decals.strip(0, 0, 0, 1, 5, 1)), 11)));
    }

    // ------------------------------------------------------------------ task A6a: what the client draws

    @Test
    void piecesGrowFromTheirGrowPoint() {
        Shape strip = Decals.strip(0, 0, 1, 0, 6, 1);
        Piece p = strip.pieces().get(0);
        double[] gp = p.growPoint();
        assertEquals(0, gp[0], 1e-9, "a strip grows from the hero");
        assertEquals(0, gp[1], 1e-9);
        for (double[] c : p.cornersAt(0)) {
            assertEquals(0, c[0], 1e-9, "at 0 every corner is the grow point");
            assertEquals(0, c[1], 1e-9);
        }
        double[][] full = p.corners(), at1 = p.cornersAt(1), half = p.cornersAt(0.5);
        for (int i = 0; i < 4; i++) {
            assertEquals(full[i][0], at1[i][0], 1e-9);
            assertEquals(full[i][1], at1[i][1], 1e-9);
            assertEquals(full[i][0] / 2, half[i][0], 1e-9, "halfway: half the way from the grow point");
        }
        Piece disc = Decals.disc(3, 4, 2).pieces().get(1);
        double[] dg = disc.growPoint();
        assertEquals(3, dg[0], 1e-9, "a disc grows from its centre");
        assertEquals(4, dg[1], 1e-9);
    }

    @Test
    void packedPiecesDrawTheSameCorners() {
        List<Shape> shapes = List.of(Decals.fan(0, 0, 0.3, 1, 8, 120), Decals.disc(2, 5, 1.5), Decals.ring(0, 0, 6));
        float[] packed = Decals.pack(shapes, new double[]{0, 1.5, -0.5});
        assertEquals(Decals.pieces(shapes) * Decals.PIECE_FLOATS, packed.length);
        double[] out = new double[8];
        int k = 0;
        for (int s = 0; s < shapes.size(); s++) {
            for (Piece p : shapes.get(s).pieces()) {
                assertEquals(new double[]{0, 1.5, -0.5}[s], packed[k * Decals.PIECE_FLOATS + 1], 1e-6, "its shape's ground");
                for (double g : new double[]{0, 0.3, 1}) {
                    Decals.corners(packed, k, g, out);
                    double[][] want = p.cornersAt(g);
                    for (int c = 0; c < 4; c++) {
                        assertEquals(want[c][0], out[c * 2], 1e-4, "corner " + c + " x at " + g);
                        assertEquals(want[c][1], out[c * 2 + 1], 1e-4, "corner " + c + " z at " + g);
                    }
                }
                k++;
            }
        }
        assertEquals(0, Decals.pack(List.of(), null).length);
    }

    @Test
    void growthFollowsTheWindup() {
        assertEquals(0, Decals.growth(0, 20), 1e-9);
        assertEquals(0.5, Decals.growth(10, 20), 1e-9);
        assertEquals(1, Decals.growth(20, 20), 1e-9, "full size as the wind-up ends");
        assertEquals(1, Decals.growth(35, 20), 1e-9);
        assertEquals(1, Decals.growth(0, 0), 1e-9, "no wind-up: drawn at once");
    }
}
