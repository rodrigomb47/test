package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of telegraph ground decals (HEROES_V2 6.4): vanilla block displays laid flat, so every shape is built
 * from flat boxes. A disc is two squares, the second turned 45 degrees; a strip is one box stretched along a line; a
 * fan is 3-5 strips from the hero; a ring is up to 12 strips tangent to a circle; a landing disc sits where a leap
 * lands; rain draws one disc per impact. Each piece grows from 0 to full size over the wind-up (a readable charging
 * timer) about its grow point, which stays fixed on the ground. At most 12 pieces per hero (a disc counts 2).
 *
 * <p>The shape a move draws is its {@link Move#telegraph()} (authored, or the form's from {@link
 * HeroCatalogue#decalOf}); where it goes and how large it is comes from the form (F3's answer to kit-gaps 4, "in the
 * spirit of 6.4": the decal covers the ground the move can hit, never less, so whoever stands outside it is safe).
 * Decals are drawn only while a player is within 32 blocks (presentation, 6.1); a move's timing and hit test never
 * depend on them. Coordinates are blocks in the world's X/Z plane relative to an origin the caller picks (normally
 * the hero's feet). Pure, unit-tested ({@code DecalsTest}).
 */
public final class Decals {
    private Decals() {
    }

    /** At most this many pieces alive per hero. */
    public static final int MAX_PIECES = 12;
    /** A decal's height (scale y) and how far above the ground it is laid. */
    public static final double THICKNESS = 0.01, LIFT = 0.015;
    /** A disc's square side for a radius: the union's innermost edge (at 22.5 degrees) reaches the radius. */
    public static final double DISC_SIDE = 2 * Math.cos(Math.PI / 8);
    /** Strip widths: a ranged shot's track, a body-wide run or grab, a fan's rib and a ring's band. */
    public static final double SHOT_WIDTH = 0.6, BODY_WIDTH = 1.0, RIB_WIDTH = 0.5, RING_WIDTH = 0.5;
    /** A self move's disc is at least this large (the hero's own ground: "something happens here"). */
    public static final double SELF_RADIUS = 1.5;
    /** Rain: each impact hits within this of its point (6.5: "within 1-2 blocks", PROPOSED). */
    public static final double IMPACT_RADIUS = 1.5;
    /** A fan has one rib per 30 degrees, plus one, within 3-5. */
    public static final int FAN_MIN = 3, FAN_MAX = 5, RING_MIN = 6, RING_MAX = 12;
    /** A volley's fan is at least this wide (its arrows fly 10 degrees apart). */
    public static final double VOLLEY_MIN_SPREAD = 20;

    /**
     * One flat box: its centre on the ground, its rotation about +Y ({@code yaw}, radians, as JOML's
     * {@code rotationY}: the box's local +Z points along {@code (sin yaw, cos yaw)}), its width (local X) and length
     * (local Z), and the point it grows from as a fraction along its length (0.5: from its centre, 0: from its near
     * end, for strips that start at the hero).
     */
    public record Piece(double x, double z, double yaw, double width, double length, double growFrom) {
        /** The direction of its length axis in the world. */
        public double[] axis() {
            return new double[]{Math.sin(yaw), Math.cos(yaw)};
        }

        /** Its four corners (x, z), counter-clockwise from the near left. */
        public double[][] corners() {
            double[] l = axis();
            double[] w = {l[1], -l[0]};
            double hw = width / 2, hl = length / 2;
            return new double[][]{
                    {x - w[0] * hw - l[0] * hl, z - w[1] * hw - l[1] * hl},
                    {x + w[0] * hw - l[0] * hl, z + w[1] * hw - l[1] * hl},
                    {x + w[0] * hw + l[0] * hl, z + w[1] * hw + l[1] * hl},
                    {x - w[0] * hw + l[0] * hl, z - w[1] * hw + l[1] * hl}};
        }

        /** The display scale vector (width, thickness, length). */
        public double[] scale() {
            return new double[]{width, THICKNESS, length};
        }

        /**
         * The display translation at full size for a block display spawned at the piece's centre (the unit block
         * model spans 0..1, so its bottom centre is moved to the centre after the scale and the rotation).
         */
        public double[] translation() {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            double hx = width / 2, hz = length / 2;
            // JOML rotationY: x' = x cos + z sin, z' = -x sin + z cos
            return new double[]{-(hx * c + hz * s), LIFT, -(-hx * s + hz * c)};
        }

        /**
         * The translation at scale 0, the spawn tick's: the grow point relative to the centre. Display interpolation
         * lerps translation and scale, so the grow point stays fixed while the piece grows to full size.
         */
        public double[] growTranslation() {
            double[] l = axis();
            double off = (growFrom - 0.5) * length;
            return new double[]{l[0] * off, LIFT, l[1] * off};
        }
    }

    /** One drawn shape: its pieces, and whether it is a disc (2 pieces that can fall back to 1). */
    public record Shape(Decal kind, List<Piece> pieces) {
        public Shape {
            pieces = List.copyOf(pieces);
        }
    }

    // ------------------------------------------------------------------ the shapes

    /** A disc of radius {@code r}: a square and the same square turned 45 degrees, never smaller than the circle. */
    public static Shape disc(double cx, double cz, double r) {
        double a = Math.max(0.2, r) * DISC_SIDE;
        return new Shape(Decal.DISC, List.of(new Piece(cx, cz, 0, a, a, 0.5),
                new Piece(cx, cz, Math.PI / 4, a, a, 0.5)));
    }

    /** A strip from {@code (ox, oz)} along {@code (dx, dz)}, {@code length} long, growing from the origin end. */
    public static Shape strip(double ox, double oz, double dx, double dz, double length, double width) {
        double[] d = unit(dx, dz);
        double len = Math.max(0.2, length);
        return new Shape(Decal.STRIP, List.of(new Piece(ox + d[0] * len / 2, oz + d[1] * len / 2,
                Math.atan2(d[0], d[1]), Math.max(0.2, width), len, 0)));
    }

    /** A fan of ribs from the hero across {@code angleDegrees}, one per 30 degrees plus one, 3-5. */
    public static Shape fan(double ox, double oz, double dx, double dz, double range, double angleDegrees) {
        double ang = Math.max(0, Math.min(360, angleDegrees));
        int n = Math.max(FAN_MIN, Math.min(FAN_MAX, 1 + (int) Math.round(ang / 30)));
        double[] d = unit(dx, dz);
        double base = Math.atan2(d[0], d[1]);
        double half = Math.toRadians(ang) / 2;
        List<Piece> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = base - half + (n == 1 ? 0 : 2 * half * i / (n - 1));
            double len = Math.max(0.2, range);
            l.add(new Piece(ox + Math.sin(t) * len / 2, oz + Math.cos(t) * len / 2, t, RIB_WIDTH, len, 0));
        }
        return new Shape(Decal.FAN, l);
    }

    /**
     * A ring of radius {@code r}: strips tangent to the circle (a polygon drawn around it, so the band never falls
     * inside the radius), about one per 2 blocks of circumference, 6-12.
     */
    public static Shape ring(double cx, double cz, double r) {
        double rr = Math.max(0.5, r);
        int n = Math.max(RING_MIN, Math.min(RING_MAX, (int) Math.ceil(2 * Math.PI * rr / 2)));
        double side = 2 * rr * Math.tan(Math.PI / n);
        List<Piece> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            double px = cx + Math.sin(t) * (rr + RING_WIDTH / 2), pz = cz + Math.cos(t) * (rr + RING_WIDTH / 2);
            // tangent: perpendicular to the radius
            l.add(new Piece(px, pz, t + Math.PI / 2, RING_WIDTH, side + RING_WIDTH, 0.5));
        }
        return new Shape(Decal.RING, l);
    }

    // ------------------------------------------------------------------ a move's decal

    /**
     * Where the move is aimed when its wind-up starts: the hero at {@code (0, 0)} facing {@code (dirX, dirZ)}, its
     * target at {@code (targetX, targetZ)}, the landing point of a leap, and the impact points of a rain (one disc
     * each, spawned as each impact's own wind-up starts).
     */
    public record Aim(double dirX, double dirZ, double targetX, double targetZ, double landX, double landZ,
                      List<double[]> impacts) {
        public Aim {
            impacts = List.copyOf(impacts);
        }

        /** Aimed at a target straight ahead at {@code distance}; a leap lands there. */
        public static Aim ahead(double distance) {
            return new Aim(0, 1, 0, distance, 0, distance, List.of());
        }
    }

    /**
     * The shapes a move draws for its wind-up, from its telegraph shape and its form (none for {@code none}):
     * <ul>
     * <li>disc: a self, ally or allies move, a nova and a zone at the hero cover the hero's ground (its radius, at
     * least 1.5); a melee or combo move the ground in front of it out to its reach (its splash added); a chain the
     * ground its lightning jumps across around the target (its jump radius); a zone at the target its radius there;
     * any other form a disc of its radius (at least 1.5) where it is aimed;</li>
     * <li>strip: from the hero along its aim, its range long: a line its width, a dash, charge or grab a body's
     * width, a shot, bolt or barrage a narrow track;</li>
     * <li>fan: from the hero across a cone's angle, a sweep's arc or a volley's spread, its range long;</li>
     * <li>ring: a wave's furthest front, a counter stance's reach around the hero (the ground where a blow is
     * answered);</li>
     * <li>landing: a disc of the leap's radius where it lands;</li>
     * <li>impacts: one disc per rain impact point given.</li>
     * </ul>
     */
    public static List<Shape> forMove(Move m, Aim aim) {
        Decal kind = m.telegraph() == null ? HeroCatalogue.defaultDecal(m.cls(), m.form()) : m.telegraph().shape();
        HeroDef.Ability a = m.ability();
        Form f = m.form();
        double range = MoveSelector.reach(m);
        List<Shape> out = new ArrayList<>();
        switch (kind) {
            case NONE -> {
            }
            case DISC -> out.add(discFor(m, aim));
            case STRIP -> {
                double w = f == Form.LINE ? Math.max(BODY_WIDTH, a.width())
                        : f == Form.SHOT || f == Form.BOLT || f == Form.BARRAGE ? SHOT_WIDTH : BODY_WIDTH;
                out.add(strip(0, 0, aim.dirX(), aim.dirZ(), finite(range, HeroCatalogue.CLOSE_MAX), w));
            }
            case FAN -> {
                // a volley's arrows fly 10 degrees apart (v1 10.2); a cone's angle and a sweep's arc are their own
                double ang = f == Form.VOLLEY ? Math.max(VOLLEY_MIN_SPREAD, (Math.max(2, a.count()) - 1) * 10)
                        : f == Form.SWEEP || f == Form.CONE ? a.angle() : 90;
                out.add(fan(0, 0, aim.dirX(), aim.dirZ(), finite(range, HeroCatalogue.CLOSE_MAX), ang > 0 ? ang : 90));
            }
            case RING -> {
                double r = f == Form.WAVE ? a.radius() : f == Form.COUNTER ? MoveSelector.COUNTER_REACH
                        : Math.max(SELF_RADIUS, a.radius());
                out.add(ring(0, 0, r));
            }
            case LANDING -> out.add(retag(disc(aim.landX(), aim.landZ(), Math.max(1, a.radius())), Decal.LANDING));
            case IMPACTS -> {
                for (double[] p : aim.impacts()) {
                    out.add(retag(disc(p[0], p[1], IMPACT_RADIUS), Decal.IMPACTS));
                }
            }
        }
        return out;
    }

    private static Shape discFor(Move m, Aim aim) {
        HeroDef.Ability a = m.ability();
        return switch (m.form()) {
            case SELF, ALLY, ALLIES, NOVA -> disc(0, 0, Math.max(SELF_RADIUS, a.radius()));
            case ZONE -> a.zoneAt() == HeroDef.ZoneAt.TARGET ? disc(aim.targetX(), aim.targetZ(), Math.max(1,
                    a.radius())) : disc(0, 0, Math.max(SELF_RADIUS, a.radius()));
            case MELEE, COMBO, GRAB, COUNTER -> {
                double reach = MoveSelector.reach(m);
                double r = reach / 2 + Math.max(0, a.radius());
                double[] d = unit(aim.dirX(), aim.dirZ());
                yield disc(d[0] * reach / 2, d[1] * reach / 2, r);
            }
            case CHAIN -> disc(aim.targetX(), aim.targetZ(), Math.max(SELF_RADIUS, a.radius()));
            default -> disc(aim.targetX(), aim.targetZ(), Math.max(SELF_RADIUS, a.radius()));
        };
    }

    private static Shape retag(Shape s, Decal k) {
        return new Shape(k, s.pieces());
    }

    private static double finite(double v, double dflt) {
        return Double.isFinite(v) && v > 0 ? v : dflt;
    }

    private static double[] unit(double dx, double dz) {
        double len = Math.hypot(dx, dz);
        return len < 1e-9 ? new double[]{0, 1} : new double[]{dx / len, dz / len};
    }

    // ------------------------------------------------------------------ colour, growth, budget

    /**
     * The decal's dye: red for an ultimate, purple for an unblockable move (grabs are always unblockable), else the
     * move's own telegraph colour or the hero's accent, as the nearest dye the caller chose for it.
     */
    public static Dye dye(MoveClass cls, boolean unblockable, Dye normal) {
        if (cls == MoveClass.ULTIMATE) {
            return Dye.RED;
        }
        if (unblockable) {
            return Dye.PURPLE;
        }
        return normal == null ? Dye.WHITE : normal;
    }

    /**
     * The interpolation ticks for growing to full size: the decal spawns at scale 0 and one tick later gets its full
     * transformation with this duration (a change in the spawn tick is applied without interpolation), so it reaches
     * full size as the wind-up ends.
     */
    public static int growTicks(int windupTicks) {
        return Math.max(1, windupTicks - 1);
    }

    /**
     * Fits requested shapes into the hero's budget of 12 pieces, {@code inUse} already alive: shapes in order while
     * they fit; a disc with room for one piece only keeps one square (of the same side, still covering its radius);
     * a fan or a ring with room for at least 3 pieces is thinned evenly; anything else waits (left out).
     */
    public static List<Shape> fit(List<Shape> requested, int inUse) {
        int room = MAX_PIECES - Math.max(0, inUse);
        List<Shape> out = new ArrayList<>();
        for (Shape s : requested) {
            int n = s.pieces().size();
            if (n <= room) {
                out.add(s);
                room -= n;
                continue;
            }
            boolean discLike = s.kind() == Decal.DISC || s.kind() == Decal.LANDING || s.kind() == Decal.IMPACTS;
            if (discLike && room >= 1) {
                Piece p = s.pieces().get(0);
                double side = p.width() / DISC_SIDE * 2;
                out.add(new Shape(s.kind(), List.of(new Piece(p.x(), p.z(), 0, side, side, 0.5))));
                room -= 1;
            } else if ((s.kind() == Decal.FAN || s.kind() == Decal.RING) && room >= 3) {
                List<Piece> thin = new ArrayList<>();
                for (int i = 0; i < room; i++) {
                    thin.add(s.pieces().get((int) Math.round((double) i * n / room) % n));
                }
                out.add(new Shape(s.kind(), thin));
                room = 0;
            }
        }
        return out;
    }

    /** Pieces of a list of shapes. */
    public static int pieces(List<Shape> shapes) {
        int n = 0;
        for (Shape s : shapes) {
            n += s.pieces().size();
        }
        return n;
    }
}
