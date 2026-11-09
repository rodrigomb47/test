package com.factionclash.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses which layer of a mine a miner works, the way a survival player would (pure, unit-tested; no Minecraft
 * classes). The choice follows what the storehouse lacks and the pickaxe in hand, and puts each layer where vanilla
 * 1.21.1 ore generation is richest for it:
 * <ul>
 *   <li>coal just under the surface (6 below it);</li>
 *   <li>iron at y=16 and lapis at y=0, which need a stone pickaxe;</li>
 *   <li>gold at y=-16, and the deep layer for diamonds and redstone at 8 above the bottom of the world (y=-56),
 *       which need an iron pickaxe.</li>
 * </ul>
 * Iron, lapis and gold use the same absolute heights as the vanilla ore placements; only the deep layer follows the
 * world's bottom, as diamonds and redstone do. A miner finishes the level it is on before it switches; then, for the
 * most wanted ore it can harvest, it works or lengthens a level already near that height, or opens a new one at
 * least {@link #MIN_SPACING} steps from every other. When only a better pickaxe would help, the mine rests; when
 * nothing is left at all, it is worked out.
 *
 * <p>Depths are counted in spiral steps below the mine's surface (step k stands k blocks down); a level's landing is
 * always a step whose cell faces the tunnels ({@link SpiralMine#isLanding}).
 */
public final class MinePlanner {
    /**
     * What a layer is dug for, with the pickaxe tier its ore needs to drop (wood or gold 0, stone 1, iron 2). The
     * order is saved in mine records, so new bands may only be added at the end.
     */
    public enum Band {
        COAL(0, "coal"),
        IRON(1, "iron"),
        LAPIS(1, "lapis"),
        GOLD(2, "gold"),
        /** Diamonds and redstone, close above the bottom of the world. */
        DEEP(2, "diamonds");

        public final int pickTier;
        public final String label;

        Band(int pickTier, String label) {
            this.pickTier = pickTier;
            this.label = label;
        }

        /** The band saved as {@code ordinal}, or null for an unknown or legacy (-1) value. */
        public static Band byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    /** PROPOSED: a mine needs at least this many steps between its surface and the mining floor. */
    public static final int MIN_DEPTH = 6;
    /** PROPOSED: a new level lies at least this many steps from every other level of the mine. */
    public static final int MIN_SPACING = 6;
    /** PROPOSED: a level within this many steps of a band's height is used for that band instead of a new one. */
    public static final int REUSE_WITHIN = MIN_SPACING - 1;
    /** PROPOSED: how many times a level's branch mine may be lengthened (another 40 blocks out each time). */
    public static final int MAX_REACH = 4;
    /** PROPOSED: coal is mined this many blocks below the surface. */
    public static final int COAL_DEPTH = 6;
    /** Vanilla ore-band heights (absolute Y): iron, lapis and gold. */
    public static final int IRON_Y = 16, LAPIS_Y = 0, GOLD_Y = -16;
    /** PROPOSED: the deepest layer lies this far above the bottom of the world (y=-56 in a normal overworld). */
    public static final int FLOOR_ABOVE_BOTTOM = 8;

    /** PROPOSED storehouse amounts below which an ore is wanted (see {@link #wanted}). */
    public static final int IRON_CRITICAL = 8, DIAMONDS_WANTED = 8, IRON_WANTED = 32, FUEL_WANTED = 32, GOLD_WANTED = 16,
            LAPIS_WANTED = 32;

    /** The bands worth digging even when nothing is short, in this order. */
    static final List<Band> DEFAULT = List.of(Band.IRON, Band.DEEP, Band.GOLD, Band.LAPIS, Band.COAL);

    private MinePlanner() {
    }

    /** The deepest height a mine goes down to: {@link #FLOOR_ABOVE_BOTTOM} above the world's bottom. */
    public static int floorY(int minY) {
        return minY + FLOOR_ABOVE_BOTTOM;
    }

    /** The height (absolute Y) a band's layer aims for. */
    public static int targetY(Band b, int surfaceY, int minY) {
        return switch (b) {
            case COAL -> surfaceY - COAL_DEPTH;
            case IRON -> IRON_Y;
            case LAPIS -> LAPIS_Y;
            case GOLD -> GOLD_Y;
            case DEEP -> floorY(minY);
        };
    }

    /** True if the ground at {@code surfaceY} is deep enough for a mine at all: {@link #MIN_DEPTH} steps above the floor. */
    public static boolean deepEnough(int surfaceY, int minY) {
        return surfaceY - floorY(minY) >= MIN_DEPTH;
    }

    /**
     * The landing step of a band's layer in a mine whose surface is {@code surfaceY}, or -1 when the mine is too
     * shallow. The aim is clamped between {@link #MIN_DEPTH} steps down and the floor; the nearest landing at or
     * below it is used, or, when that would lie under the floor, the nearest one above it. The result is always a
     * landing between {@link #MIN_DEPTH} and the floor.
     */
    public static int stepFor(Band b, int surfaceY, int minY) {
        int maxN = surfaceY - floorY(minY);
        if (maxN < MIN_DEPTH) {
            return -1;
        }
        int n = Math.max(MIN_DEPTH, Math.min(maxN, surfaceY - targetY(b, surfaceY, minY)));
        int k = SpiralMine.landingAtOrBelow(n);
        if (k > maxN) {
            k = SpiralMine.landingAtOrAbove(n);
        }
        return k;
    }

    /** The band whose target height is nearest to {@code y}; a tie goes to the shallower band (used for old levels). */
    public static Band bandAt(int y, int surfaceY, int minY) {
        Band best = null;
        int bestD = Integer.MAX_VALUE, bestY = Integer.MIN_VALUE;
        for (Band b : Band.values()) {
            int t = targetY(b, surfaceY, minY);
            int d = Math.abs(y - t);
            if (d < bestD || (d == bestD && t > bestY)) {
                best = b;
                bestD = d;
                bestY = t;
            }
        }
        return best;
    }

    /**
     * What the storehouse holds, as far as the mine is concerned: iron (ingots and raw), diamonds, gold (raw and
     * ingots), lapis, fuel (coal and charcoal), whether the base has an enchanting table, and whether it is short of
     * fuel or wood.
     */
    public record Stock(int iron, int diamonds, int gold, int lapis, int fuel, boolean enchantingTable, boolean fuelOrWoodShort) {
    }

    /**
     * The bands the base wants, most urgent first: iron when it is nearly out, then diamonds, then iron again up to a
     * comfortable stock, coal while fuel or wood runs short, gold, and lapis once there is an enchanting table.
     */
    public static List<Band> wanted(Stock s) {
        Set<Band> out = new LinkedHashSet<>();
        if (s.iron() < IRON_CRITICAL) {
            out.add(Band.IRON);
        }
        if (s.diamonds() < DIAMONDS_WANTED) {
            out.add(Band.DEEP);
        }
        if (s.iron() < IRON_WANTED) {
            out.add(Band.IRON);
        }
        if (s.fuel() < FUEL_WANTED || s.fuelOrWoodShort()) {
            out.add(Band.COAL);
        }
        if (s.gold() < GOLD_WANTED) {
            out.add(Band.GOLD);
        }
        if (s.enchantingTable() && s.lapis() < LAPIS_WANTED) {
            out.add(Band.LAPIS);
        }
        return List.copyOf(out);
    }

    /** A level of the mine as the planner sees it. A cut level (its main tunnel met something nobody may dig) is done. */
    public record Level(int step, int progress, int rounds, boolean cut, Band band) {
        /** Cells in the level's branch-mine plan at its current length. */
        public int planSize() {
            return SpiralMine.planSize(rounds);
        }

        /** True while there is still plan to dig. */
        public boolean unfinished() {
            return !cut && progress < planSize();
        }
    }

    public enum Kind {
        /** Dig on in level {@code level}. */
        WORK,
        /** Level {@code level} is done: lengthen its branch mine by one round, then dig on. */
        LENGTHEN,
        /** Open a new level at step {@code step} for {@code band} (dig the shaft down first when it is deeper). */
        NEW_LAYER,
        /** Nothing to do with this pickaxe: the mine waits for a pickaxe of tier {@code needTier}. */
        REST,
        /** Every layer the mine can reach is dug. */
        WORKED_OUT
    }

    /** The planner's choice. {@code level} is an index into the level list (-1 when none); {@code band} may be null. */
    public record Decision(Kind kind, int level, int step, Band band, int needTier) {
        static Decision work(Kind kind, int level, Level l, Band band) {
            return new Decision(kind, level, l.step(), band, band == null ? 0 : band.pickTier);
        }
    }

    /**
     * The next thing to do in a mine.
     *
     * @param surfaceY   the mine's surface height (its origin)
     * @param minY       the world's bottom
     * @param levels     the mine's levels (bands of old levels already derived with {@link #bandAt})
     * @param current    index of the level being worked, or -1
     * @param bottom     the deepest spiral step dug (a NEW_LAYER below it needs the shaft dug down first)
     * @param shaftLimit 0, or the deepest step the shaft may reach (it met something it could not dig)
     * @param pickTier   tier of the pickaxe in hand (wood or gold 0, stone 1, iron 2, diamond 3)
     * @param wanted     {@link #wanted} for the storehouse
     * @param maxRounds  how many lengthening rounds fit this mine's corridor
     */
    public static Decision next(int surfaceY, int minY, List<Level> levels, int current, int bottom, int shaftLimit,
                                int pickTier, List<Band> wanted, int maxRounds) {
        int maxN = surfaceY - floorY(minY);
        int maxStep = shaftLimit > 0 ? Math.min(shaftLimit, maxN) : maxN;
        // (1) a level is finished before the miner switches, so changing stock never makes it go back and forth
        if (current >= 0 && current < levels.size()) {
            Level c = levels.get(current);
            if (c.unfinished() && c.band().pickTier <= pickTier && c.step() <= maxStep) {
                return Decision.work(Kind.WORK, current, c, c.band());
            }
        }
        // (2) the most wanted band the pickaxe can harvest: a level near its height, or a new one
        Set<Band> order = new LinkedHashSet<>(wanted);
        order.addAll(DEFAULT);
        for (Band b : order) {
            if (b.pickTier > pickTier) {
                continue;
            }
            int s = stepFor(b, surfaceY, minY);
            if (s < 0 || s > maxStep) {
                continue;
            }
            int li = nearest(levels, s);
            if (li < 0) {
                return new Decision(Kind.NEW_LAYER, -1, s, b, b.pickTier);
            }
            Level l = levels.get(li);
            if (l.cut()) {
                continue;
            }
            if (l.unfinished()) {
                return Decision.work(Kind.WORK, li, l, b);
            }
            if (l.rounds() < maxRounds) {
                return Decision.work(Kind.LENGTHEN, li, l, b);
            }
        }
        // (3) nothing for this pickaxe, but a better one would have work: rest until it comes
        for (int t = Math.max(1, pickTier + 1); t <= 2; t++) {
            Kind k = next(surfaceY, minY, levels, current, bottom, shaftLimit, t, wanted, maxRounds).kind();
            if (k != Kind.WORKED_OUT && k != Kind.REST) {
                return new Decision(Kind.REST, -1, -1, null, t);
            }
        }
        return new Decision(Kind.WORKED_OUT, -1, -1, null, 0);
    }

    /** Index of the level nearest to step {@code s} within {@link #REUSE_WITHIN} (the shallower one on a tie), or -1. */
    static int nearest(List<Level> levels, int s) {
        int best = -1, bestD = REUSE_WITHIN + 1;
        for (int i = 0; i < levels.size(); i++) {
            int d = Math.abs(levels.get(i).step() - s);
            if (d < bestD || (d == bestD && best >= 0 && levels.get(i).step() < levels.get(best).step())) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }
}
