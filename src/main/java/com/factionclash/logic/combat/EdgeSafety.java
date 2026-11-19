package com.factionclash.logic.combat;

import com.factionclash.logic.hero.EdgePlay.Ground;

/**
 * The edge sense ({@code docs/design/breaching.md} 8.1; HEROES_V2 6.17 "How everyone defends"; tasks X5 and T3): one rule
 * set for faction NPCs and heroes, as a careful player keeps away from a cliff while a pusher is near. Players defend by
 * their own play; nothing here reads whether anyone is a player.
 *
 * <ul>
 * <li>A <b>lethal edge</b> is a cell that holds lava, fire, a magma block or a lit campfire, opens on the void, or drops
 * more than {@link #DROP_LETHAL} blocks (vanilla's mobs take fall damage after 3; 4 keeps steps and ledges usable).
 * Lava and fire are none for a fire-proof one.</li>
 * <li>A <b>pusher</b> ({@link #pusher}) is an enemy within {@link #PUSHER_RANGE} blocks that can push: any hero; any NPC
 * or player holding a weapon with Knockback or a bow with Punch; ravagers, hoglins, zoglins, iron golems and breezes;
 * anyone holding wind charges.</li>
 * <li>Its <b>reach</b> ({@link #reach}): 6 blocks while a hero is among the pushers (a hero's knockback 3, the strongest,
 * throws about 6 blocks on flat ground, HEROES_V2 6.19), else 4 (a Knockback II sword, a Punch bow, a ravager's roar or
 * a wind charge).</li>
 * <li>A cell is <b>unsafe</b> ({@link #unsafe}) when a lethal edge lies within that reach behind it, on the line from a
 * pusher through it: a path costs {@link #PATH_MALUS} more per unsafe cell, so a fighter walks round to the side of its
 * target that keeps the edge to its flank, and its waiting spots skip them. It never makes anyone refuse a fight: with no
 * safe cell in reach it fights where it is.</li>
 * </ul>
 *
 * <p>Pure Java, unit-tested ({@code EdgeSafetyTest}); the game reads the blocks ({@code combat/EdgeSense}).
 */
public final class EdgeSafety {
    private EdgeSafety() {
    }

    /** A drop deeper than this is a lethal edge (blocks of air under the edge's floor). */
    public static final int DROP_LETHAL = 4;
    /** How far behind a cell a lethal edge counts: with a hero among the pushers, and without. */
    public static final double HERO_REACH = 6, PUSH_REACH = 4;
    /** An enemy that can push counts within this many blocks. */
    public static final double PUSHER_RANGE = 8;
    /** A path's cost for each unsafe cell it crosses (PROPOSED). */
    public static final float PATH_MALUS = 4;
    /** At most this many pushers are read at once (the nearest). */
    public static final int MAX_PUSHERS = 4;

    /** What a pusher is (the game fills the flags from the entity and what it holds; the same for a player and an NPC). */
    public static boolean pusher(boolean hero, int knockbackLevel, int punchLevel, boolean pushingMob, boolean windCharges) {
        return hero || knockbackLevel > 0 || punchLevel > 0 || pushingMob || windCharges;
    }

    /** The reach behind a cell: {@link #HERO_REACH} with a hero among the pushers, else {@link #PUSH_REACH}. */
    public static double reach(boolean heroAmongPushers) {
        return heroAmongPushers ? HERO_REACH : PUSH_REACH;
    }

    /** Whether what lies at a column is a lethal edge for one that is {@code fireproof} or not. */
    public static boolean lethal(Ground g, int drop, boolean fireproof) {
        return switch (g) {
            case LAVA, FIRE -> !fireproof;
            case VOID -> true;
            case FLOOR, POWDER_SNOW, THORNS -> drop > DROP_LETHAL;
            case WATER, WALL -> false;
        };
    }

    /** What a column reads: nothing to fear, a lethal edge, or a wall (a push stops there: nothing beyond counts). */
    public static final int CLEAR = 0, LETHAL = 1, WALL = 2;

    /** Reads the column at (x, y, z) as {@link #CLEAR}, {@link #LETHAL} or {@link #WALL} (y: the feet row it is read from). */
    @FunctionalInterface
    public interface Columns {
        int at(int x, int y, int z);
    }

    /** {@link #lethal} as a column reading, a wall included. */
    public static int column(Ground g, int drop, boolean fireproof) {
        return g == Ground.WALL ? WALL : lethal(g, drop, fireproof) ? LETHAL : CLEAR;
    }

    /**
     * Whether a lethal edge lies within {@code reach} blocks behind the cell centred at (cx, cz), feet row {@code y}, on
     * the line from the pusher at (px, pz) through it, with no wall between. A pusher standing on the cell itself (under half a block away) has
     * every side as "behind": any edge within reach in the 8 directions counts.
     */
    public static boolean edgeBehind(double cx, int y, double cz, double px, double pz, double reach, Columns col) {
        double dx = cx - px, dz = cz - pz;
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 0.5) {
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                if (line(cx, y, cz, Math.cos(ang), Math.sin(ang), reach, col)) {
                    return true;
                }
            }
            return false;
        }
        return line(cx, y, cz, dx / l, dz / l, reach, col);
    }

    private static boolean line(double cx, int y, double cz, double ux, double uz, double reach, Columns col) {
        int lastX = (int) Math.floor(cx), lastZ = (int) Math.floor(cz);
        for (double k = 0.5; k <= reach + 1e-9; k += 0.5) {
            int x = (int) Math.floor(cx + ux * k), z = (int) Math.floor(cz + uz * k);
            if (x == lastX && z == lastZ) {
                continue;
            }
            lastX = x;
            lastZ = z;
            int c = col.at(x, y, z);
            if (c == WALL) {
                return false; // a wall stops the push before the edge
            }
            if (c == LETHAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the cell at (cx, y, cz) is unsafe while pushers stand at ({@code px[i]}, {@code pz[i]}), {@code n} of them, a
     * hero among them or not: a lethal edge within the reach behind it from any pusher within {@link #PUSHER_RANGE} plus
     * that reach of the cell.
     */
    public static boolean unsafe(double cx, int y, double cz, double[] px, double[] pz, int n, boolean hero, Columns col) {
        double reach = reach(hero);
        double near = PUSHER_RANGE + reach;
        for (int i = 0; i < n; i++) {
            double dx = cx - px[i], dz = cz - pz[i];
            if (dx * dx + dz * dz > near * near) {
                continue;
            }
            if (edgeBehind(cx, y, cz, px[i], pz[i], reach, col)) {
                return true;
            }
        }
        return false;
    }

    /** The extra path cost of a cell: {@link #PATH_MALUS} when unsafe, else 0. */
    public static float malus(boolean unsafe) {
        return unsafe ? PATH_MALUS : 0;
    }
}
