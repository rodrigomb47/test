package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Move;

import java.util.HashSet;
import java.util.Set;

/**
 * Explosive moves (HEROES_V2 6.18, task T2), the pure half: how far a blast of a power reaches, vanilla's ray rule for
 * which blocks one breaks, where a move's blasts land, and the pick's own-side check. The game half is
 * {@code hero/HeroBlasts} (vanilla's {@code Level.explode} with interaction {@code MOB} and a damage calculator that hurts
 * and pushes nobody). Unit-tested in {@code BlastsTest}.
 *
 * <p>Vanilla's explosion (1.21.1 {@code Explosion.explode}) casts 16 x 16 x 16 shell rays from its centre; each ray
 * starts with a strength of {@code power x (0.7 to 1.3)}, chosen at random per ray, and walks in steps of 0.3 blocks,
 * losing 0.225 a step and {@code (resistance + 0.3) x 0.3} in each solid block or fluid it samples; every block a ray
 * samples while its strength is above 0 breaks. So a blast reaches at most {@code 1.3 x power / 0.225 x 0.3}, about
 * 1.73 blocks a power in the open, which is 6.18's "about 1.7 x its power"; obsidian (1200), bedrock and their like
 * stop every ray, and water and lava (100) soak it up.
 */
public final class Blasts {
    private Blasts() {
    }

    /** Vanilla's ray steps: the distance a step covers and the strength it costs in the open. */
    public static final float STEP = 0.3F, STEP_COST = 0.22500001F;
    /** The weakest and strongest ray, as factors of the power. */
    public static final float WEAKEST = 0.7F, STRONGEST = 1.3F;
    /** The furthest a blast of power 1 reaches, in blocks (in the open, its strongest ray). */
    public static final double REACH_PER_POWER = STRONGEST * STEP / STEP_COST;

    /** The furthest a blast of {@code power} breaks a block: its strongest ray in the open. */
    public static double reach(int power) {
        return REACH_PER_POWER * Math.max(0, power);
    }

    // ------------------------------------------------------------------ vanilla's ray rule

    /** One block cell. */
    public record Cell(int x, int y, int z) {
    }

    /** What the world holds at a cell, as vanilla's explosion reads it. */
    public interface World {
        /**
         * The cell's blast resistance (the greater of its block's and its fluid's), or a negative number for air with no
         * fluid (vanilla's empty {@code getBlockExplosionResistance}).
         */
        float resistance(int x, int y, int z);

        /** Whether the cell is inside the world's height (a ray stops at the edge). */
        default boolean inWorld(int x, int y, int z) {
            return true;
        }
    }

    /**
     * The cells a blast of {@code power} at {@code (cx, cy, cz)} breaks when every one of its rays has the strength
     * {@code power x factor} ({@link #WEAKEST} to {@link #STRONGEST}), by vanilla's own float steps. A larger factor
     * breaks a superset, so what a real blast breaks lies between the cells at {@link #WEAKEST} (it always breaks those)
     * and those at {@link #STRONGEST} (it never breaks more).
     */
    public static Set<Cell> broken(double cx, double cy, double cz, float power, float factor, World w) {
        Set<Cell> out = new HashSet<>();
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) {
                        continue;
                    }
                    double d0 = (double) ((float) j / 15.0F * 2.0F - 1.0F);
                    double d1 = (double) ((float) k / 15.0F * 2.0F - 1.0F);
                    double d2 = (double) ((float) l / 15.0F * 2.0F - 1.0F);
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    d0 /= d3;
                    d1 /= d3;
                    d2 /= d3;
                    float f = power * factor;
                    double x = cx, y = cy, z = cz;
                    for (; f > 0.0F; f -= STEP_COST) {
                        int bx = floor(x), by = floor(y), bz = floor(z);
                        if (!w.inWorld(bx, by, bz)) {
                            break;
                        }
                        float r = w.resistance(bx, by, bz);
                        if (r >= 0) {
                            f -= (r + 0.3F) * 0.3F;
                        }
                        if (f > 0.0F && r >= 0) {
                            out.add(new Cell(bx, by, bz));
                        }
                        x += d0 * STEP;
                        y += d1 * STEP;
                        z += d2 * STEP;
                    }
                }
            }
        }
        return out;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    // ------------------------------------------------------------------ where a move's blasts land

    /** Where a move's blast lands: at the hero (a nova, a zone at or following the hero) or at its target. */
    public enum At { SELF, TARGET }

    public static At at(Move m) {
        return switch (m.form()) {
            case NOVA -> At.SELF;
            case ZONE -> m.ability().zoneAt() == HeroDef.ZoneAt.TARGET ? At.TARGET : At.SELF;
            default -> At.TARGET;
        };
    }

    /**
     * How far from its centre a move's blasts may land, beyond the blast's own reach: a rain's impacts within its
     * radius of the target, a volley's side projectiles fanned out over {@code targetDistance}, a leap's or a dive's
     * landing beside its target; 0 for the rest.
     */
    public static double spread(Move m, double targetDistance) {
        HeroDef.Ability a = m.ability();
        return switch (m.form()) {
            case RAIN -> (a.radius() > 0 ? a.radius() : 5) + 0.5;
            case VOLLEY -> {
                double half = (Math.max(1, a.count()) - 1) / 2.0 * FormGeometry.VOLLEY_SPACING;
                yield Math.max(0, targetDistance) * Math.sin(Math.toRadians(half));
            }
            case LEAP, DIVE -> 1.5;
            default -> 0;
        };
    }

    /**
     * The pick's own-side check (6.18: a player does not blow up their own base on purpose): an explosive move whose
     * blast where it lands would reach a block of its own side ({@code ownFromTarget} and {@code ownFromSelf}: the
     * distance from its target and from the hero to the nearest one, infinite when none is near) scores 0. Any other
     * move scores as before (1).
     */
    public static double ownSideFactor(Move m, double ownFromTarget, double ownFromSelf, double targetDistance) {
        HeroDef.Explosive e = m.explosive();
        if (e == null) {
            return 1;
        }
        double own = at(m) == At.SELF ? ownFromSelf : ownFromTarget;
        return own > reach(e.power()) + spread(m, targetDistance) ? 1 : 0;
    }

    /** The radius of the ground decal at each blast point (6.18): the blast's reach. */
    public static double decalRadius(HeroDef.Explosive e) {
        return e == null ? 0 : reach(e.power());
    }
}
