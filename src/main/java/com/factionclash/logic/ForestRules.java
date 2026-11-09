package com.factionclash.logic;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Pure rules of the woodland (no Minecraft classes, unit-tested): which trees a woodcutter fells and replants (by the
 * log they are made of), what it plants where a tree stood when it lacks the right sapling, and where it may plant
 * the grove that keeps a base in wood where no trees grow.
 */
public final class ForestRules {
    private ForestRules() {
    }

    /**
     * The kinds of tree a woodcutter fells and replants, with their vanilla log and sapling ids. Azalea trees grow oak
     * logs, so they are felled as oaks and come back as oaks. Mangroves (their trunks stand on roots), huge mushrooms
     * (no logs), nether stems and logs of other mods are never felled. Dark oak only grows from four saplings in a
     * square, so it is only ever replanted that way; spruce and jungle grow a huge 2x2 tree from four in a square,
     * which is how a felled mega spruce or giant jungle tree comes back.
     */
    public enum Species {
        OAK, BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, CHERRY;

        /** The vanilla id of its log, for example "dark_oak_log". */
        public String log() {
            return name().toLowerCase(Locale.ROOT) + "_log";
        }

        /** The vanilla id of its sapling, for example "dark_oak_sapling". */
        public String sapling() {
            return name().toLowerCase(Locale.ROOT) + "_sapling";
        }

        /** The name shown to players, for example "dark oak". */
        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        /** Saplings planted at once where such a tree stood: four in a square for dark oak, one otherwise. */
        public int saplingsPerTree() {
            return this == DARK_OAK ? 4 : 1;
        }

        /** True for the species that grow a huge 2x2 tree from four saplings in a square (spruce and jungle). */
        public boolean growsHuge() {
            return this == SPRUCE || this == JUNGLE;
        }

        /** The species with this name ({@link #name()}, any case), or null. */
        public static Species byName(String name) {
            if (name == null) {
                return null;
            }
            for (Species s : values()) {
                if (s.name().equalsIgnoreCase(name)) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * Saplings for the grove, best first: birch (5 to 7 logs), spruce (5 to 8), oak (a tenth grow into fancy oaks,
     * which can be too tall to fell from the ground and need a scaffold), cherry, acacia. Never jungle (up to 12 tall)
     * or dark oak (four at once). Grove saplings are planted one to a spot, so no mega tree grows in the grove.
     */
    public static final List<Species> GROVE_ORDER = List.of(Species.BIRCH, Species.SPRUCE, Species.OAK, Species.CHERRY, Species.ACACIA);

    /** PROPOSED: a stump waits this long for its own species' sapling before any other is planted there instead. */
    public static final long REPLANT_PATIENCE = 12000;
    /** PROPOSED: a stump still not replanted after this long is forgotten. */
    public static final long REPLANT_GIVE_UP = 48000;
    /**
     * PROPOSED: the stump of a mega spruce or giant jungle tree waits this long for four saplings of its species
     * (picked up from its canopy, or fetched from the storehouse) before a single one is planted at its north-west
     * corner instead.
     */
    public static final long HUGE_PATIENCE = 2400;
    /** PROPOSED: grove saplings stand on a lattice this many blocks apart, so grown trees do not touch. */
    public static final int GROVE_SPACING = 5;
    /** PROPOSED: the grove keeps this far (Chebyshev) beyond the walls. */
    public static final int GROVE_WALLS_MARGIN = 4;
    /** PROPOSED: the road strip kept clear of the grove: |dz| up to this, for |dx| up to the walls plus {@link #ROAD_LENGTH}. */
    public static final int ROAD_HALF_WIDTH = 3, ROAD_LENGTH = 30;

    /**
     * Where a new base's four starter oak saplings stand, (dx, dz) from its centre: on the grove lattice of the
     * levelled west pad, beside the west path. The first two are grown at once.
     */
    public static final List<int[]> STARTER_OFFSETS = List.of(new int[]{-35, -5}, new int[]{-35, 5}, new int[]{-40, -5}, new int[]{-40, 5});
    /**
     * The smallest forest radius the config allows: every starter tree lies within it (the farthest is 40.3 from the
     * centre), so the woodcutter's tree search always finds them.
     */
    public static final int MIN_FOREST_RADIUS = 41;

    /**
     * PROPOSED: the default number of saplings kept growing in the grove. A sapling in the always-ticking belt gets a
     * random tick 3 times in 4096 per game tick and grows one stage on 1 in 7 of those while lit (about half the day):
     * two stages take about 36,000 ticks, a day and a half. Sixteen saplings therefore give about 10 trees a day, about
     * 50 logs, which is what a base's builder, smiths and furnaces use in a day; eight gave half that.
     */
    public static final int DEFAULT_GROVE_SIZE = 16;
    /** PROPOSED: the most saplings the grove ever keeps growing (the config's upper bound). */
    public static final int MAX_GROVE_SIZE = 32;

    /**
     * How many saplings the grove keeps growing: {@code size} (the config's groveSize), or twice that, up to
     * {@link #MAX_GROVE_SIZE}, while the base is short of wood. 0 keeps no grove at all.
     */
    public static int groveTarget(int size, boolean woodShort) {
        if (size <= 0) {
            return 0;
        }
        return woodShort ? Math.min(MAX_GROVE_SIZE, size * 2) : size;
    }

    /** PROPOSED: a walk to a tree, stump or sapling may take this long, plus {@link #WALK_TICKS_PER_BLOCK} per block away. */
    public static final int WALK_BASE_TICKS = 400;
    /** A woodcutter walks about 4.3 blocks a second (4.7 ticks a block) on open ground; paths wind, so twice that. */
    public static final int WALK_TICKS_PER_BLOCK = 8;

    /**
     * How long a walk to something {@code distance} blocks away (horizontally) may take before it is given up, starting
     * from {@code baseTicks} ({@link #WALK_BASE_TICKS} for a tree): a tree near the base chosen while the woodcutter
     * was far out foraging is walked back to, not given up for the length of the way.
     */
    public static int walkBudget(int baseTicks, double distance) {
        return baseTicks + (int) Math.ceil(WALK_TICKS_PER_BLOCK * Math.max(0, distance));
    }

    /**
     * True for the species whose sapling grows a tree with a bee nest when flowers stand within 2 blocks of it (oak,
     * birch, cherry). A nest is a block entity, so such a tree would be left standing for good: the woodcutter picks
     * those flowers before planting, as a player keeping bees out of a tree farm would.
     */
    public static boolean beesNearFlowers(Species s) {
        return s == Species.OAK || s == Species.BIRCH || s == Species.CHERRY;
    }

    /**
     * The species of a log block id ("minecraft:oak_log" or "oak_log"), or null when a woodcutter does not fell such
     * logs: stripped logs, wood blocks, mangrove, nether stems and anything from another mod.
     */
    public static Species fromLog(String blockId) {
        if (blockId == null) {
            return null;
        }
        String id = blockId;
        int colon = id.indexOf(':');
        if (colon >= 0) {
            if (!id.substring(0, colon).equals("minecraft")) {
                return null;
            }
            id = id.substring(colon + 1);
        }
        for (Species s : Species.values()) {
            if (s.log().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The species of a sapling item or block id ("minecraft:birch_sapling" or "birch_sapling"), or null. */
    public static Species fromSapling(String id) {
        if (id == null) {
            return null;
        }
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        if (path.contains(":")) {
            return null;
        }
        for (Species s : Species.values()) {
            if (s.sapling().equals(path)) {
                return s;
            }
        }
        return null;
    }

    /** What a woodcutter does about a stump waiting to be replanted. */
    public enum Action {
        /** Plant the species that stood there. */
        SAME,
        /** Plant another species (one sapling): the right one did not come in time. */
        SUBSTITUTE,
        /** Not yet: wait for the right sapling (fetch it from the storehouse if it has one). */
        WAIT,
        /** Waited too long: forget this stump. */
        DROP
    }

    /** The decision: the action, the species to plant, whether as a 2x2 square, and how many saplings. */
    public record Replant(Action action, Species species, boolean twoByTwo, int count) {
    }

    /**
     * Saplings a stump of {@code s} waits for ({@code big}: a 2x2 trunk): four for a dark oak, and for a 2x2 spruce
     * or jungle tree (planted as a square, it grows back huge); one otherwise.
     */
    public static int saplingsFor(Species s, boolean big) {
        return s == Species.DARK_OAK || big && s != null && s.growsHuge() ? 4 : 1;
    }

    /**
     * What to plant where a tree of {@code want} stood ({@code big}: a 2x2 trunk), carrying {@code sameCarried} of
     * its saplings, with {@code substitute} the best other sapling carried (see {@link #substitute}, null for none),
     * after the stump has waited {@code waited} ticks.
     *
     * <ul>
     *   <li>Dark oak: four saplings in a square, only with four carried. A lone dark oak sapling never grows.</li>
     *   <li>A 2x2 spruce or jungle tree (now felled from a scaffold): four saplings in a square with four carried,
     *       so a mega spruce or giant jungle tree grows back; with fewer, after {@link #HUGE_PATIENCE}, one at the
     *       north-west corner.</li>
     *   <li>Any other tree: one sapling.</li>
     *   <li>Without it: wait; after {@link #REPLANT_PATIENCE} plant the substitute (one, never dark oak); after
     *       {@link #REPLANT_GIVE_UP} forget the stump.</li>
     * </ul>
     */
    public static Replant replant(Species want, boolean big, int sameCarried, Species substitute, long waited) {
        if (want == Species.DARK_OAK) {
            if (sameCarried >= 4) {
                return new Replant(Action.SAME, Species.DARK_OAK, true, 4);
            }
        } else if (want != null && big && want.growsHuge()) {
            if (sameCarried >= 4) {
                return new Replant(Action.SAME, want, true, 4);
            }
            if (sameCarried >= 1 && waited >= HUGE_PATIENCE) {
                return new Replant(Action.SAME, want, false, 1);
            }
        } else if (want != null && sameCarried >= 1) {
            return new Replant(Action.SAME, want, false, 1);
        }
        if (waited >= REPLANT_GIVE_UP) {
            return new Replant(Action.DROP, null, false, 0);
        }
        if (waited >= REPLANT_PATIENCE && substitute != null && substitute != Species.DARK_OAK) {
            return new Replant(Action.SUBSTITUTE, substitute, false, 1);
        }
        int count = want == null ? 0 : saplingsFor(want, big);
        return new Replant(Action.WAIT, want, count == 4, count);
    }

    /**
     * The best sapling to plant instead of a missing one, given how many of each the woodcutter can spare: the first
     * in {@link #GROVE_ORDER}, then jungle; never dark oak (it needs four). Null when it carries none of them.
     */
    public static Species substitute(ToIntFunction<Species> spare) {
        for (Species s : GROVE_ORDER) {
            if (spare.applyAsInt(s) > 0) {
                return s;
            }
        }
        return spare.applyAsInt(Species.JUNGLE) > 0 ? Species.JUNGLE : null;
    }

    /** The first species in {@link #GROVE_ORDER} the woodcutter can spare a sapling of, or null. */
    public static Species groveSpecies(ToIntFunction<Species> spare) {
        for (Species s : GROVE_ORDER) {
            if (spare.applyAsInt(s) > 0) {
                return s;
            }
        }
        return null;
    }

    /** What the ground under a grove spot is, as far as planting goes. */
    public enum Ground { DIRT, FARMLAND, PATH, OTHER }

    /** True when (dx, dz) from the base centre lies on the grove lattice ({@link #GROVE_SPACING} apart). */
    public static boolean onGroveLattice(int dx, int dz) {
        return Math.floorMod(dx, GROVE_SPACING) == 0 && Math.floorMod(dz, GROVE_SPACING) == 0;
    }

    /** True when (dx, dz) from the base centre lies on the base's road strip, which keeps the gates and paths clear. */
    public static boolean onRoadStrip(int dx, int dz, int half) {
        return Math.abs(dz) <= ROAD_HALF_WIDTH && Math.abs(dx) <= half + ROAD_LENGTH;
    }

    /**
     * True if a grove sapling may stand at (dx, dz) from the base centre: on the lattice, {@link #GROVE_WALLS_MARGIN}
     * or more beyond the walls (half-size {@code half}), off the road strip, inside the base's work belt
     * ({@code inBelt}, the force-ticked MIN/MAX box, where saplings grow without players), on dirt (never farmland or
     * a path), and not reserved for other work.
     */
    public static boolean groveSpotAllowed(int dx, int dz, int half, boolean inBelt, Ground ground, boolean reserved) {
        return onGroveLattice(dx, dz) && Math.max(Math.abs(dx), Math.abs(dz)) >= half + GROVE_WALLS_MARGIN
                && !onRoadStrip(dx, dz, half) && inBelt && ground == Ground.DIRT && !reserved;
    }
}
