package com.factionclash.logic.campaign;

import java.util.Collection;
import java.util.List;

/**
 * An instrument a recon scout watches through ({@code docs/design/recon.md} 5.6, task R12): one row of data each, so the
 * instruments of later phases slot in as rows. Under the recon rules an instrument at the eye changes only how far the
 * focused look reaches ({@link #sight}: so how deep the vantage band runs, from the standoff out), and the reserve share a
 * council adds to what was counted through it ({@link #share}); the vantage (a pillar or high ground outside the walls)
 * and the coverage it gives (the walls set it) stay the eyes'. A row names the item that is the instrument (a registry id,
 * so this class needs no Minecraft classes), and the tech level a faction needs to use it ({@link #techLevel}).
 *
 * <p>The first rows (the owner's answer of 2026-11-26: "we can use the spyglass leaving potential for more advanced recon
 * items made for this mod in the future that will require tech level, vanilla spyglass requires no tech level"):
 * {@link #NONE}, the eyes (72 blocks), and {@link #SPYGLASS}, the vanilla spyglass (96 blocks), which is never behind a
 * tech level. The mod's own instruments of ROADMAP Phase 6 (binoculars, drones) come as further rows with a tech level;
 * their values are that phase's design pass to set (recon.md 5.6's table holds placeholders only).</p>
 *
 * <p>Pure, no Minecraft classes. The game side maps an item to its row by the item's id ({@code work/ReconOptics}) and
 * passes the faction's tech level ({@link #NO_TECH} until the tech tree exists).</p>
 */
public record ReconOptic(String id, String item, String name, int sight, double share, int techLevel) {
    /** No tech tree yet (ROADMAP Phase 6): every faction is at this level, so only rows that need none are usable. */
    public static final int NO_TECH = 0;

    /** The eyes: no instrument. The focused look reaches {@link ReconRules#SIGHT} (72); the report's tag is empty. */
    public static final ReconOptic NONE = new ReconOptic("", "", "the eyes", ReconRules.SIGHT, Reserve.SHARE, NO_TECH);
    /**
     * PROPOSED: the vanilla spyglass (two copper ingots and an amethyst shard). Its zoom lets the focused look reach 96
     * blocks, so the band runs 61 to 96: more columns clear of trees and more natural high ground in range, not a shorter
     * pillar (recon.md 5.6). The reach stops at 96 because every line of sight ends at 128 blocks and the yard's far side
     * then lies within about 120. Vanilla, so never behind a tech level.
     */
    public static final ReconOptic SPYGLASS = new ReconOptic("spyglass", "minecraft:spyglass", "a spyglass", 96, Reserve.SHARE, NO_TECH);

    /** Every instrument, the eyes first. A later instrument is one more row here. */
    public static final List<ReconOptic> ROWS = List.of(NONE, SPYGLASS);

    public ReconOptic {
        id = id == null ? "" : id;
        item = item == null ? "" : item;
        name = name == null ? "" : name;
        sight = Math.max(1, sight);
        share = Math.max(0, share);
        techLevel = Math.max(0, techLevel);
    }

    /** Whether a faction at {@code techLevel} can use it. The eyes and the vanilla spyglass need none. */
    public boolean usable(int techLevel) {
        return this.techLevel <= techLevel;
    }

    /** Whether this is the eyes (no instrument). */
    public boolean none() {
        return id.isEmpty();
    }

    /**
     * The row a report's tag names: the eyes for an empty, unknown or missing tag (an older save, or a row since removed),
     * never null.
     */
    public static ReconOptic byId(String id) {
        if (id != null && !id.isEmpty()) {
            for (ReconOptic o : ROWS) {
                if (o.id.equals(id)) {
                    return o;
                }
            }
        }
        return NONE;
    }

    /** The instrument an item is ({@code "minecraft:spyglass"}), or null when it is none. */
    public static ReconOptic byItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        for (ReconOptic o : ROWS) {
            if (!o.none() && o.item.equals(itemId)) {
                return o;
            }
        }
        return null;
    }

    /** The instrument a recon was watched through: its {@code optic} tag's row. */
    public static ReconOptic of(Intel.Recon r) {
        return r == null ? NONE : byId(r.optic());
    }

    /**
     * The best instrument among the items a scout carries ({@code itemIds}, registry ids) that a faction at
     * {@code techLevel} can use: the one that reaches farthest; the eyes when it carries none.
     */
    public static ReconOptic best(Collection<String> itemIds, int techLevel) {
        return best(itemIds, techLevel, ROWS);
    }

    /** {@link #best(Collection, int)} over the instruments {@code rows}. */
    public static ReconOptic best(Collection<String> itemIds, int techLevel, List<ReconOptic> rows) {
        ReconOptic best = NONE;
        if (itemIds == null) {
            return best;
        }
        for (ReconOptic o : rows) {
            if (!o.none() && o.usable(techLevel) && o.sight > best.sight && itemIds.contains(o.item)) {
                best = o;
            }
        }
        return best;
    }

    /**
     * A GameTest base's scaled reach ({@code testSight}, its scaled eyes' reach) through {@code o}: scaled by the same
     * ratio as the real one ({@code o.sight / 72}), rounded down, so the scaled band never reaches farther than in
     * proportion (13 with the eyes is 17 with a spyglass, 16 is 21).
     */
    public static int scaled(int testSight, ReconOptic o) {
        return Math.floorDiv(Math.max(0, testSight) * o.sight, ReconRules.SIGHT);
    }
}
