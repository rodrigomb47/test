package com.factionclash.logic;

/**
 * Pure rules for the world's structures a scout reports (PROPOSED values): which structures count (every village in
 * the village tag - plains, desert, savanna, snowy and taiga, abandoned ones included, since a zombie village is a
 * village structure built from other pieces - pillager outposts and woodland mansions), when one is in sight (its
 * bounds within {@link #SIGHT} blocks of the scout, horizontally), what it is called, and how two observations of one
 * village combine.
 */
public final class StructureRules {
    private StructureRules() {
    }

    /** The structures a scout reports. The names match the report kinds. */
    public enum Sight { VILLAGE, PILLAGER_OUTPOST, WOODLAND_MANSION }

    /** A structure is in sight when its bounds come this close to the scout (blocks, horizontally). */
    public static final int SIGHT = 48;
    /** The loaded chunks looked at: this many chunks around the scout's own in each direction. */
    public static final int CHUNK_RADIUS = 3;
    /** Observations of one structure lie this close together; two structures of these kinds never do. */
    public static final int SAME_STRUCTURE = 64;

    /**
     * The kind of structure {@code id} ("minecraft:pillager_outpost") is, or null when scouts do not report it.
     * {@code villageTag} says whether the structure is in the village tag.
     */
    public static Sight classify(String id, boolean villageTag) {
        if (villageTag) {
            return Sight.VILLAGE;
        }
        return switch (id == null ? "" : id) {
            case "minecraft:pillager_outpost" -> Sight.PILLAGER_OUTPOST;
            case "minecraft:mansion" -> Sight.WOODLAND_MANSION;
            default -> null;
        };
    }

    /** The horizontal distance from (x, z) to the box [minX, maxX] x [minZ, maxZ] of blocks (0 inside it). */
    public static double gap(double x, double z, int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - (maxX + 1)));
        double dz = Math.max(0, Math.max(minZ - z, z - (maxZ + 1)));
        return Math.hypot(dx, dz);
    }

    /** True when a structure with these bounds is in sight of a scout at (x, z). */
    public static boolean inSight(double x, double z, int minX, int minZ, int maxX, int maxZ) {
        return gap(x, z, minX, minZ, maxX, maxZ) <= SIGHT;
    }

    /**
     * What a village is called from its structure id: "minecraft:village_snowy" is a "snowy village"; any other id in
     * the village tag (a data pack's village) is just a "village".
     */
    public static String villageName(String id) {
        String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
        if (path.startsWith("village_") && path.length() > "village_".length()) {
            return path.substring("village_".length()).replace('_', ' ') + " village";
        }
        return "village";
    }

    /**
     * Trading villagers believed to live in a village after a newer observation: the newer count, unless it saw none
     * (the scout looked from afar, or the villagers were out of sight) while an older one saw some.
     */
    public static int mergedTraders(int older, int newer) {
        return newer > 0 ? newer : Math.max(0, older);
    }
}
