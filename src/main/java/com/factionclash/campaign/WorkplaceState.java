package com.factionclash.campaign;

import com.factionclash.logic.FishingWater;
import com.factionclash.logic.LegacyMine;
import com.factionclash.logic.MinePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A personal, persistent workspace (spec 5): assigned worker, tier and upgrade progress. */
public final class WorkplaceState {
    public enum Type {
        /**
         * One mine site: origin = the surface cell in front of the spiral shaft (where the miner steps in), facing =
         * the way the tunnels run, {@link #levels} = the branch-mine levels opened so far. Old saves' mines, built
         * with the base, have a hut over the shaft ({@link #hut}).
         */
        MINE("Mine", 1),
        /**
         * A base's woodland (the old fenced tree plot): no fence and no grid. Its woodcutter fells natural trees around
         * the base and replants each stump with its own species ({@link #replant}), tends the saplings it planted
         * ({@link #planted}) and keeps a small grove growing in the base's work belt. origin and facing only matter for
         * an old save's fence ({@link #legacyFence}), which the woodcutter takes down.
         */
        TREE_PLOT("Woodland", 1),
        FARM("Farm", 1),
        /** origin = pen centre (inside), extent = the pen's fence gate. */
        PEN("Animal pen", 1),
        /**
         * One per settlement: origin = the water block the bobber floats on, extent = where the fisherman stands
         * (feet). While no fishing water is known ({@link #spot} not VALID) both are the base centre, as a
         * placeholder: a fishing spot is never derived from the farm's irrigation.
         */
        FISHERY("Fishing spot", 1),
        /**
         * A natural cave surveyed by a cave explorer: origin = the entrance, extent = the cavity the scouts reported,
         * {@link #route}/{@link #parent} = the explorer's walked waypoints, {@link #marks} = ores it saw.
         */
        CAVE("Surveyed cave", 1);

        public final String label;
        public final int maxTier;

        Type(String label, int maxTier) {
            this.label = label;
            this.maxTier = maxTier;
        }
    }

    public int id;
    public int baseId;
    public Type type;
    public int tier = 1;
    /** Mine: surface entrance anchor. Woodland: an old plot's fence entrance (only its old fence uses it). Farm: min corner. */
    public BlockPos origin;
    /** Direction pointing away from the base (mine tunnels run this way; an old tree plot's fence grew this way). */
    public Direction facing = Direction.EAST;
    /** Farm: max corner. */
    public BlockPos extent = BlockPos.ZERO;
    public UUID assigned;
    /**
     * Upgrade in progress: next build/dig step index; -1 when idle. Mines: the step of
     * {@code SpiralMine.spiralPlan(bottom, levels[cur].step)} while the shaft is dug down to the current level.
     */
    public int upgradeStep = -1;
    public long totalOutput;
    /** Caves: progress through the prospecting tunnels. (Mines keep their progress per level.) */
    public int progress;
    /** Mines: an old hut mine, built with the base: the way in and out is the hut's door. */
    public boolean hut;
    /** Mines: the deepest spiral step dug; steps 1 to bottom are all dug. */
    public int bottom;
    /** Mines: index in {@link #levels} of the level being worked, -1 when none is planned. */
    public int cur = -1;
    /** Mines: 0, or the deepest step the shaft may go to (below it lay something the miner may not dig). */
    public int shaftLimit;
    /** Mines: how many lengthening rounds fit this site's reserved corridor (0 to {@link MinePlanner#MAX_REACH}). */
    public int maxRounds;
    /** Mines: 0 when not resting, otherwise the pickaxe tier a miner needs to take the mine up again. */
    public int restTier;
    /** Mines: when the mine was opened (game time; 0 for old saves). */
    public long opened;
    /** Mines: every level opened, in the order they were opened. */
    public final List<MineLevel> levels = new ArrayList<>();
    /** Caves: waypoints the explorer walked (index 0 = entrance) and each waypoint's parent (-1 for the root). */
    public long[] route = new long[0];
    public int[] parent = new int[0];
    /** Caves: ore blocks seen and not yet mined (packed positions). */
    public final java.util.LinkedHashSet<Long> marks = new java.util.LinkedHashSet<>();
    /** Caves: when the last survey was delivered, a summary of it, and whether miners have worked it out. */
    public long surveyedAt;
    public String note = "";
    public boolean workedOut;
    /** Caves: where each prospecting tunnel meets rock (packed; Long.MIN_VALUE = no tunnel that way). */
    public long[] faces = new long[0];
    /**
     * Fishing spot: whether real fishing water is known ({@link FishingWater#UNSEARCHED}, {@link FishingWater#VALID}
     * or {@link FishingWater#NONE}), whether it is vanilla open water (treasure can be caught), and when a
     * fisherman's full search and forage last found nothing (the NONE memory).
     */
    public int spot = FishingWater.UNSEARCHED;
    public boolean open;
    public long searched;
    /** Fishing spot: bobber blocks that turned out unfit, oldest first (at most {@link #MAX_BAD_SPOTS}). */
    public final List<Long> badSpots = new ArrayList<>();
    /** PROPOSED: how many unfit fishing spots a fishery remembers. */
    public static final int MAX_BAD_SPOTS = 32;
    /** Woodland: the saplings this base's woodcutters planted (packed positions), oldest first. */
    public final List<Long> planted = new ArrayList<>();
    /** Woodland: stumps waiting to be replanted with their own species, oldest first. */
    public final List<Replant> replant = new ArrayList<>();
    /** Woodland: how many trees its woodcutters have felled (output counts every item, so it cannot tell). */
    public int felled;
    /** Woodland: an old save's tree-plot fence still stands (its rings follow from origin and facing). */
    public boolean legacyFence;
    /** PROPOSED: a woodland remembers at most this many planted saplings and this many stumps to replant. */
    public static final int MAX_PLANTED = 64, MAX_REPLANT = 32;

    /**
     * A stump to replant: where (the north-west corner of a 2x2 trunk), the species that stood there
     * ({@link com.factionclash.logic.ForestRules.Species} name), whether it was a 2x2 trunk, and since when it waits.
     */
    public record Replant(long pos, String species, boolean big, long since) {
    }

    /** Remembers a sapling planted at {@code p}, forgetting the oldest beyond {@link #MAX_PLANTED}. */
    public void addPlanted(BlockPos p) {
        long k = p.asLong();
        planted.remove(Long.valueOf(k));
        planted.add(k);
        while (planted.size() > MAX_PLANTED) {
            planted.remove(0);
        }
    }

    /** Queues a stump for replanting, replacing an older entry at the same place and forgetting the oldest beyond {@link #MAX_REPLANT}. */
    public void addReplant(Replant r) {
        replant.removeIf(e -> e.pos() == r.pos());
        replant.add(r);
        while (replant.size() > MAX_REPLANT) {
            replant.remove(0);
        }
    }

    /** Remembers an unfit fishing spot, forgetting the oldest beyond {@link #MAX_BAD_SPOTS}. */
    public void addBadSpot(BlockPos p) {
        long k = p.asLong();
        badSpots.remove(Long.valueOf(k));
        badSpots.add(k);
        while (badSpots.size() > MAX_BAD_SPOTS) {
            badSpots.remove(0);
        }
    }

    public boolean upgrading() {
        return upgradeStep >= 0;
    }

    /** Mines: still being worked, neither worked out nor resting until a better pickaxe comes. */
    public boolean active() {
        return !workedOut && restTier == 0;
    }

    /**
     * One level of a mine: the spiral step of its landing, progress through its branch-mine plan, how often the plan
     * was lengthened, the band it was opened for ({@link MinePlanner.Band} ordinal, -1 for a level of an old mine),
     * and whether its main tunnel was cut short (it met something nobody may dig): then it is done for good.
     */
    public static final class MineLevel {
        public int step;
        public int progress;
        public int rounds;
        public int band;
        public boolean cut;

        public MineLevel(int step, int progress, int rounds, int band) {
            this.step = step;
            this.progress = progress;
            this.rounds = rounds;
            this.band = band;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("base", baseId);
        t.putString("type", type.name());
        t.putInt("tier", tier);
        t.putLong("origin", origin.asLong());
        t.putString("facing", facing.getSerializedName());
        t.putLong("extent", extent.asLong());
        if (assigned != null) {
            t.putUUID("assigned", assigned);
        }
        t.putInt("upgradeStep", upgradeStep);
        t.putLong("output", totalOutput);
        t.putInt("progress", type == Type.MINE ? 0 : progress);
        if (type == Type.MINE) {
            t.putInt("mineV", 1);
            t.putBoolean("hut", hut);
            t.putInt("bottom", bottom);
            t.putInt("cur", cur);
            t.putInt("shaftLimit", shaftLimit);
            t.putInt("maxRounds", maxRounds);
            t.putInt("restTier", restTier);
            t.putLong("opened", opened);
            ListTag list = new ListTag();
            for (MineLevel lv : levels) {
                CompoundTag l = new CompoundTag();
                l.putInt("step", lv.step);
                l.putInt("prog", lv.progress);
                l.putInt("rounds", lv.rounds);
                l.putInt("band", lv.band);
                l.putBoolean("cut", lv.cut);
                list.add(l);
            }
            t.put("levels", list);
            t.putBoolean("workedOut", workedOut);
            t.putString("note", note);
        }
        if (type == Type.CAVE) {
            t.putLongArray("route", route);
            t.putIntArray("parent", parent);
            t.putLongArray("marks", marks.stream().mapToLong(Long::longValue).toArray());
            t.putLong("surveyedAt", surveyedAt);
            t.putString("note", note);
            t.putBoolean("workedOut", workedOut);
            t.putLongArray("faces", faces);
        }
        if (type == Type.TREE_PLOT) {
            t.putInt("woodV", 1);
            t.putLongArray("planted", planted.stream().mapToLong(Long::longValue).toArray());
            ListTag list = new ListTag();
            for (Replant r : replant) {
                CompoundTag e = new CompoundTag();
                e.putLong("p", r.pos());
                e.putString("s", r.species());
                e.putBoolean("big", r.big());
                e.putLong("at", r.since());
                list.add(e);
            }
            t.put("replant", list);
            t.putInt("felled", felled);
            t.putBoolean("legacyFence", legacyFence);
        }
        if (type == Type.FISHERY) {
            t.putInt("fishV", 1);
            t.putInt("spot", spot);
            t.putBoolean("open", open);
            t.putLong("searched", searched);
            t.putLongArray("bad", badSpots.stream().mapToLong(Long::longValue).toArray());
        }
        return t;
    }

    public static WorkplaceState load(CompoundTag t) {
        WorkplaceState w = new WorkplaceState();
        w.id = t.getInt("id");
        w.baseId = t.getInt("base");
        try {
            w.type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        w.tier = t.getInt("tier");
        w.origin = BlockPos.of(t.getLong("origin"));
        Direction d = Direction.byName(t.getString("facing"));
        w.facing = d == null ? Direction.EAST : d;
        w.extent = BlockPos.of(t.getLong("extent"));
        w.assigned = t.hasUUID("assigned") ? t.getUUID("assigned") : null;
        w.upgradeStep = t.contains("upgradeStep") ? t.getInt("upgradeStep") : -1;
        w.totalOutput = t.getLong("output");
        w.progress = t.getInt("progress");
        if (w.type == Type.MINE) {
            loadMine(w, t);
        }
        if (w.type == Type.TREE_PLOT) {
            loadWoodland(w, t);
        }
        if (w.type == Type.CAVE) {
            w.route = t.getLongArray("route");
            w.parent = t.getIntArray("parent");
            if (w.parent.length != w.route.length) {
                w.parent = new int[w.route.length];
                for (int i = 0; i < w.parent.length; i++) {
                    w.parent[i] = i - 1; // a broken tree degrades to a chain
                }
            }
            for (long m : t.getLongArray("marks")) {
                w.marks.add(m);
            }
            w.surveyedAt = t.getLong("surveyedAt");
            w.note = t.getString("note");
            w.workedOut = t.getBoolean("workedOut");
            w.faces = t.getLongArray("faces");
        }
        if (w.type == Type.FISHERY) {
            if (!t.contains("fishV")) {
                // an older save: its spot was the farm's water channel. It is looked for again on real water (the
                // next upkeep moves the placeholder to the base centre); id, worker and catch total are kept.
                w.spot = FishingWater.UNSEARCHED;
            } else {
                int spot = t.getInt("spot");
                w.spot = spot == FishingWater.VALID || spot == FishingWater.NONE ? spot : FishingWater.UNSEARCHED;
                w.open = t.getBoolean("open");
                w.searched = t.getLong("searched");
                for (long b : t.getLongArray("bad")) {
                    w.badSpots.add(b);
                }
                while (w.badSpots.size() > MAX_BAD_SPOTS) {
                    w.badSpots.remove(0);
                }
            }
        }
        return w;
    }

    /**
     * The woodland's own fields. A record without 'woodV' is an old fenced tree plot: its fence is to be taken down
     * ({@link #legacyFence}), the saplings of its grid count as planted (any that are not saplings any more are
     * dropped the first time they are looked at), nothing waits to be replanted and no tree was counted yet. An old
     * plot's tier and enlargement step are not used any more.
     */
    private static void loadWoodland(WorkplaceState w, CompoundTag t) {
        if (t.contains("woodV")) {
            for (long p : t.getLongArray("planted")) {
                w.planted.add(p);
            }
            ListTag list = t.getList("replant", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                w.replant.add(new Replant(e.getLong("p"), e.getString("s"), e.getBoolean("big"), e.getLong("at")));
            }
            w.felled = Math.max(0, t.getInt("felled"));
            w.legacyFence = t.getBoolean("legacyFence");
        } else {
            w.legacyFence = true;
            for (BlockPos p : com.factionclash.structure.TreePlotLayout.spots(w, Math.max(1, Math.min(3, w.tier)))) {
                w.planted.add(p.asLong());
            }
        }
        while (w.planted.size() > MAX_PLANTED) {
            w.planted.remove(0);
        }
        while (w.replant.size() > MAX_REPLANT) {
            w.replant.remove(0);
        }
        w.tier = 1;
        w.upgradeStep = -1;
    }

    /**
     * The mine's own fields. A record without 'mineV' comes from an older version: a hut mine built with the base,
     * dug in fixed tiers. Its levels are rebuilt from the old tier, floor, lengthening and progress numbers (see
     * {@link LegacyMine#migrate}), so they match the tunnels already dug; those old numbers are not saved again.
     */
    private static void loadMine(WorkplaceState w, CompoundTag t) {
        if (t.contains("mineV")) {
            w.hut = t.getBoolean("hut");
            w.bottom = t.getInt("bottom");
            w.cur = t.contains("cur") ? t.getInt("cur") : -1;
            w.shaftLimit = t.getInt("shaftLimit");
            w.maxRounds = t.getInt("maxRounds");
            w.restTier = t.getInt("restTier");
            w.opened = t.getLong("opened");
            w.workedOut = t.getBoolean("workedOut");
            w.note = t.getString("note");
            ListTag list = t.getList("levels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag l = list.getCompound(i);
                MineLevel lv = new MineLevel(l.getInt("step"), l.getInt("prog"), l.getInt("rounds"), l.contains("band") ? l.getInt("band") : -1);
                lv.cut = l.getBoolean("cut");
                w.levels.add(lv);
            }
        } else {
            LegacyMine.Migrated m = LegacyMine.migrate(w.origin.getY(), t.getInt("tier"), t.getInt("subLevel"), t.getInt("progress"),
                    t.getInt("reach"), t.getInt("revisit"), w.upgradeStep);
            for (LegacyMine.Level l : m.levels()) {
                w.levels.add(new MineLevel(l.step(), l.progress(), l.rounds(), -1));
            }
            w.bottom = m.bottom();
            w.cur = m.cur();
            w.upgradeStep = m.upgradeStep();
            w.hut = true;
            w.maxRounds = MinePlanner.MAX_REACH;
            w.workedOut = false;
        }
        w.tier = 1;
        w.progress = 0;
        // levels that cannot exist are dropped (the current one with them); cur must point at a real level
        int keptCur = -1;
        List<MineLevel> kept = new ArrayList<>();
        for (int i = 0; i < w.levels.size(); i++) {
            MineLevel lv = w.levels.get(i);
            if (lv.step <= 0) {
                continue;
            }
            if (i == w.cur) {
                keptCur = kept.size();
            }
            lv.progress = Math.max(0, lv.progress);
            lv.rounds = Math.max(0, Math.min(MinePlanner.MAX_REACH, lv.rounds));
            kept.add(lv);
        }
        w.levels.clear();
        w.levels.addAll(kept);
        w.cur = keptCur;
        w.bottom = Math.max(0, w.bottom);
        w.shaftLimit = Math.max(0, w.shaftLimit);
        w.restTier = Math.max(0, w.restTier);
        w.maxRounds = Math.max(0, Math.min(MinePlanner.MAX_REACH, w.maxRounds));
        if (w.cur < 0) {
            w.upgradeStep = -1;
        }
    }
}
