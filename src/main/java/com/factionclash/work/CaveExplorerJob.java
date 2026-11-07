package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.combat.CombatRules;
import com.factionclash.logic.CaveSurvey;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cave explorer: walks to a cave the scouts reported, finds the natural way in, explores it on foot lighting it
 * with torches, and notes every ore it can actually see. It never mines. Back at the reporting station it hands
 * in the survey - the route it walked and the ores it saw - and miners then go there to mine them.
 */
public class CaveExplorerJob extends Job {
    enum State { HOME, TRAVEL, ENTER, EXPLORE, RETURN, REPORT }

    /** PROPOSED: an expedition is cut short after this many ticks (half a day). */
    public static final int MAX_TRIP_TICKS = 12000;
    /** PROPOSED: a worked-out cave is surveyed again after two days (unexplored passages may hold more). */
    public static final long RESURVEY_TICKS = 48000;
    private static final int MAX_ORES = 256;
    /** PROPOSED: at most this many step blocks are placed on one survey. */
    public static final int MAX_STEPS_BUILT = 32;

    private State state = State.HOME;
    private BlockPos cavity;
    private BlockPos entrance;
    private final List<Long> waypoints = new ArrayList<>();
    private final List<Integer> parents = new ArrayList<>();
    private final LinkedHashSet<Long> ores = new LinkedHashSet<>();
    private final List<Long> frontier = new ArrayList<>();
    private final List<Integer> frontierParent = new ArrayList<>();
    private final List<Long> dead = new ArrayList<>();
    private final Deque<Integer> via = new ArrayDeque<>();
    private final Set<Long> noEntrance = new HashSet<>();
    private int current = -1;
    private Long goal;
    private int goalParent;
    private long departedAt;
    private int torches;
    private String summary = "";
    private String endReason = "";
    private boolean retreating;
    private int timer;
    private int dangerTimer;
    /** A step being built down a drop, and how many were built this trip. */
    private CaveSurvey.Step building;
    private int stepsBuilt;

    public CaveExplorerJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    public boolean ignoresCombat() {
        return state == State.RETURN && retreating;
    }

    @Override
    public String workplaceInfo() {
        String cave = cavity == null ? "none" : cavity.toShortString();
        return switch (state) {
            case HOME -> "At home between surveys";
            case TRAVEL -> "Going to the cave reported at " + cave;
            case ENTER -> "Entering the cave at " + (entrance == null ? cave : entrance.toShortString());
            case EXPLORE -> "Exploring the cave at " + cave + ": " + waypoints.size() + " spots, " + torches + " torches, "
                    + stepsBuilt + " steps built, " + ores.size() + " ores seen";
            case RETURN -> (retreating ? "RETREATING (" + endReason + ")" : "Returning (" + endReason + ")") + " with " + ores.size() + " ores noted";
            case REPORT -> "Handing in the cave survey";
        };
    }

    @Override
    protected int keep(ItemStack s) {
        if (s.is(Items.TORCH)) {
            return 64;
        }
        if (s.is(Items.STICK) || s.is(Items.COAL) || s.is(Items.CHARCOAL)) {
            return 8;
        }
        if (MinerJob.SEAL.test(s)) {
            return 48; // blocks to build steps down drops
        }
        return 0;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case HOME -> home(b);
            case TRAVEL -> travel();
            case ENTER -> enter();
            case EXPLORE -> explore();
            case RETURN -> returnHome(b);
            case REPORT -> report(b);
        }
    }

    // ------------------------------------------------------------------ at home

    private void home(BaseState b) {
        if (!atStorage()) {
            return;
        }
        unloadNow();
        upgradeEquipment();
        if (npc.getHealth() < npc.getMaxHealth() * 0.75F) {
            status("Recovering before the next survey", "Resting");
            waitFor(200);
            return;
        }
        int have = Inv.count(npc.inventory(), s -> s.is(Items.TORCH));
        if (have < 24) {
            takeFromStorage(s -> s.is(Items.TORCH), 32 - have);
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.TORCH)) < 12) {
            // torches are a 2x2 recipe: a player crafts them in hand from coal and sticks
            takeFromStorage(Crafting.FUEL_COAL, 4);
            takeFromStorage(s -> s.is(Items.STICK), 4);
            CaveWork.craftTorchesInHand(npc, 16);
        }
        if (Inv.count(npc.inventory(), MinerJob.SEAL) < 24) {
            takeFromStorage(MinerJob.SEAL, 32);
        }
        cavity = pickCave(b);
        if (cavity == null) {
            status("No unexplored caves reported - waiting for the scouts", "Idle");
            waitFor(1200);
            return;
        }
        resetSurvey();
        departedAt = now();
        state = State.TRAVEL;
    }

    private void resetSurvey() {
        entrance = null;
        waypoints.clear();
        parents.clear();
        ores.clear();
        frontier.clear();
        frontierParent.clear();
        dead.clear();
        via.clear();
        current = -1;
        goal = null;
        building = null;
        stepsBuilt = 0;
        torches = 0;
        summary = "";
        endReason = "";
        retreating = false;
    }

    /** The nearest cave the scouts reported that this base has not surveyed (or not for a long while). */
    private BlockPos pickCave(BaseState b) {
        FactionState f = data().faction(npc.getFaction());
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Report r : f.knowledge) {
            if (r.kind != Report.Kind.CAVE || noEntrance.contains(r.pos.asLong())) {
                continue;
            }
            double dx = r.pos.getX() - b.center.getX(), dz = r.pos.getZ() - b.center.getZ();
            double d = dx * dx + dz * dz;
            if (d > (double) CaveWork.MAX_CAVE_DISTANCE * CaveWork.MAX_CAVE_DISTANCE || d >= bestD
                    || CaveWork.ownWorkings(data(), b, r.pos) || surveyed(b, r.pos)) {
                continue;
            }
            best = r.pos;
            bestD = d;
        }
        return best;
    }

    private boolean surveyed(BaseState b, BlockPos pos) {
        WorkplaceState w = caveAt(b, pos);
        return w != null && !(w.workedOut && now() - w.surveyedAt > RESURVEY_TICKS);
    }

    /** The base's cave workplace for the cave around {@code pos}, if it has one. */
    private WorkplaceState caveAt(BaseState b, BlockPos pos) {
        for (int id : b.workplaces) {
            WorkplaceState w = data().workplace(id);
            if (w != null && w.type == WorkplaceState.Type.CAVE && (w.extent.closerThan(pos, 32) || w.origin.closerThan(pos, 24))) {
                return w;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ getting there

    private void travel() {
        if (dangerCheck()) {
            return;
        }
        if (now() - departedAt > MAX_TRIP_TICKS) {
            finishSurvey("took too long to get there");
            return;
        }
        BlockPos column = new BlockPos(cavity.getX(), npc.getBlockY(), cavity.getZ());
        if (level().hasChunk(cavity.getX() >> 4, cavity.getZ() >> 4)) {
            column = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        }
        double dx = npc.getX() - (cavity.getX() + 0.5), dz = npc.getZ() - (cavity.getZ() + 0.5);
        if (dx * dx + dz * dz > 12 * 12) {
            goTo(column, 6.0, "the cave reported at " + cavity.toShortString());
            return;
        }
        npc.navigator().reset();
        entrance = CaveWork.findEntrance(level(), cavity, npc.blockPosition());
        if (entrance == null) {
            noEntrance.add(cavity.asLong());
            Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Cave explorer " + npc.npcName()
                    + " found no way into the cave at " + cavity.toShortString() + " (explorers do not dig)"));
            cavity = null;
            finishSurvey("no way in");
            return;
        }
        state = State.ENTER;
    }

    private void enter() {
        if (dangerCheck()) {
            return;
        }
        NpcNavigator.Result r = walk(entrance, 1.2, "the cave entrance");
        if (r == NpcNavigator.Result.ARRIVED) {
            waypoints.add(entrance.asLong());
            parents.add(-1);
            current = 0;
            atWaypoint(0);
            state = State.EXPLORE;
        } else if (r == NpcNavigator.Result.FAILED) {
            BlockPos here = npc.blockPosition();
            double dx = here.getX() - entrance.getX(), dz = here.getZ() - entrance.getZ();
            if (dx * dx + dz * dz <= 16 * 16 && here.getY() > entrance.getY() && CaveWork.standable(level(), here)) {
                // the way in is down a pit or drop: start the survey at its rim and build steps down
                entrance = here;
                waypoints.add(here.asLong());
                parents.add(-1);
                current = 0;
                atWaypoint(0);
                state = State.EXPLORE;
                return;
            }
            noEntrance.add(cavity.asLong());
            cavity = null;
            finishSurvey("could not reach the entrance");
        }
    }

    // ------------------------------------------------------------------ exploring

    private void explore() {
        if (dangerCheck()) {
            return;
        }
        if (now() - departedAt > MAX_TRIP_TICKS) {
            finishSurvey("time to head back");
            return;
        }
        if (!via.isEmpty()) {
            int w = via.peek();
            NpcNavigator.Result r = walk(BlockPos.of(waypoints.get(w)), 1.5, "an explored passage");
            if (r == NpcNavigator.Result.ARRIVED) {
                current = w;
                via.poll();
            } else if (r == NpcNavigator.Result.FAILED) {
                via.clear(); // the way back to that branch is blocked: forget it
                goal = null;
            }
            return;
        }
        if (building != null) {
            buildStep();
            return;
        }
        if (goal == null) {
            chooseNext();
            return;
        }
        NpcNavigator.Result r = walk(BlockPos.of(goal), 1.2, "unexplored passages");
        if (r == NpcNavigator.Result.ARRIVED) {
            waypoints.add(goal);
            parents.add(goalParent);
            current = waypoints.size() - 1;
            goal = null;
            atWaypoint(current);
        } else if (r == NpcNavigator.Result.FAILED) {
            dead.add(goal);
            goal = null;
        }
    }

    private List<Long> visitedAll() {
        List<Long> v = new ArrayList<>(waypoints);
        v.addAll(dead);
        return v;
    }

    private void chooseNext() {
        if (waypoints.size() >= CaveSurvey.MAX_WAYPOINTS) {
            finishSurvey("surveyed as far as one trip allows");
            return;
        }
        if (Inv.count(npc.inventory(), s -> s.is(Items.TORCH)) == 0 && torches > 0) {
            finishSurvey("out of torches");
            return;
        }
        long here = waypoints.get(current);
        BlockPos e = entrance;
        Map<Long, Integer> walk = CaveSurvey.walk(here, CaveWork.cells(level(), e, CaveSurvey.MAX_RANGE), CaveSurvey.MAX_STEPS, 3000);
        List<Long> visited = visitedAll();
        Long next = CaveSurvey.next(walk, visited, CaveSurvey.MIN_STEPS, CaveSurvey.SPACING);
        for (long f : CaveSurvey.frontier(walk, visited, CaveSurvey.MIN_STEPS, CaveSurvey.SPACING)) {
            if ((next == null || f != next) && CaveSurvey.unexplored(f, frontier, CaveSurvey.SPACING)) {
                frontier.add(f);
                frontierParent.add(current);
            }
        }
        if (next != null) {
            goal = next;
            goalParent = current;
            return;
        }
        // nothing new from here: go back to the closest passage seen earlier but not explored yet
        for (int i = frontier.size() - 1; i >= 0; i--) {
            if (!CaveSurvey.unexplored(frontier.get(i), visited, CaveSurvey.SPACING)) {
                frontier.remove(i);
                frontierParent.remove(i);
            }
        }
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < frontier.size(); i++) {
            double d = CaveSurvey.dist2(frontier.get(i), here);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0) {
            // nothing walkable left: is there a drop leading deeper that a few placed blocks would make walkable?
            CaveSurvey.Step step = stepsBuilt < MAX_STEPS_BUILT && Inv.count(npc.inventory(), MinerJob.SEAL) > 0
                    ? CaveSurvey.dropStep(walk, CaveWork.cells(level(), e, CaveSurvey.MAX_RANGE), visited, CaveSurvey.SPACING, 8) : null;
            if (step != null) {
                building = step;
                return;
            }
            finishSurvey("the cave is explored");
            return;
        }
        goal = frontier.remove(best);
        goalParent = frontierParent.remove(best);
        via.clear();
        via.addAll(CaveSurvey.treePath(parentArray(), current, goalParent));
    }

    /** Walks to the ledge and places one block as a step down the drop; the cell on top becomes the next spot. */
    private void buildStep() {
        BlockPos ledge = BlockPos.of(building.ledge());
        NpcNavigator.Result r = walk(ledge, 0.6, "a drop to build steps down");
        if (r == NpcNavigator.Result.MOVING) {
            return;
        }
        if (r == NpcNavigator.Result.FAILED) {
            dead.add(building.onto());
            building = null;
            return;
        }
        BlockPos block = BlockPos.of(building.block());
        lookAt(block);
        boolean solid = !CaveWork.open(level(), block);
        if (!solid && !CaveWork.placeBlock(npc, block)) {
            building = null;
            finishSurvey("no blocks left to build steps");
            return;
        }
        stepsBuilt++;
        status("Building steps down into the cave (" + stepsBuilt + " placed)", "Exploring");
        goal = building.onto();
        goalParent = current;
        building = null;
        waitFor(5);
    }

    /** At a new spot: light it if it is dark, and look around for ore. */
    private void atWaypoint(int i) {
        BlockPos p = BlockPos.of(waypoints.get(i));
        if (CaveWork.dark(level(), p) && CaveWork.placeTorch(npc, p)) {
            torches++;
        }
        for (BlockPos ore : CaveWork.visibleOres(level(), npc)) {
            if (ores.size() < MAX_ORES) {
                ores.add(ore.asLong());
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        status("Exploring a cave: " + waypoints.size() + " spots, " + ores.size() + " ores seen, " + torches + " torches placed", "Exploring");
        waitFor(10);
    }

    private int[] parentArray() {
        return parents.stream().mapToInt(Integer::intValue).toArray();
    }

    private NpcNavigator.Result walk(BlockPos p, double reach, String label) {
        NpcNavigator.Result r = npc.navigator().travel(p, reach, 1.0);
        if (r == NpcNavigator.Result.MOVING) {
            status("Exploring a cave: walking to " + label, "Exploring");
        } else if (r == NpcNavigator.Result.FAILED) {
            npc.navigator().reset();
        }
        return r;
    }

    // ------------------------------------------------------------------ coming back

    private void finishSurvey(String reason) {
        if (!ores.isEmpty()) {
            summary = CaveWork.oreSummary(level(), ores);
        }
        npc.navigator().reset();
        headBack(reason);
    }

    /** Sets the way back out along the walked route (no world access: also used while loading). */
    private void headBack(String reason) {
        endReason = reason;
        via.clear();
        goal = null;
        if (current > 0) {
            via.addAll(CaveSurvey.treePath(parentArray(), current, 0));
        }
        state = State.RETURN;
    }

    private void returnHome(BaseState b) {
        if (!via.isEmpty()) {
            int w = via.peek();
            NpcNavigator.Result r = walk(BlockPos.of(waypoints.get(w)), 1.5, "the way out");
            if (r != NpcNavigator.Result.MOVING) {
                current = w;
                via.poll(); // arrived, or that stretch is blocked: carry on toward the entrance
            }
            return;
        }
        double speed = retreating ? 1.3 : 1.0;
        if (goTo(b.point(BaseState.STATION_STAND), 1.8, "the reporting station", speed)) {
            state = State.REPORT;
            timer = 0;
        } else if (retreating) {
            status("Retreating home: " + endReason, "Retreating");
        }
    }

    /** Hands the survey in at the station: the faction now knows the cave's route and ores. */
    private void report(BaseState b) {
        BlockPos station = b.point(BaseState.STATION);
        lookAt(station);
        if (!level().getBlockState(station).is(com.factionclash.registry.ModBlocks.REPORTING_STATION.get())) {
            status("The reporting station is destroyed - holding the survey", "Waiting");
            waitFor(400);
            return;
        }
        if (++timer < 40) {
            status("Handing in the cave survey", "Reporting");
            return;
        }
        npc.swing(InteractionHand.MAIN_HAND);
        if (cavity != null && entrance != null && !waypoints.isEmpty()) {
            deliver(b);
        }
        resetSurvey();
        cavity = null;
        state = State.HOME;
        waitFor(200);
    }

    private void deliver(BaseState b) {
        WorkplaceState w = caveAt(b, cavity);
        boolean fresh = w == null;
        if (fresh) {
            w = new WorkplaceState();
            w.type = WorkplaceState.Type.CAVE;
            w.origin = entrance;
            w.extent = cavity;
            data().addWorkplace(b, w);
        }
        w.origin = entrance;
        w.route = waypoints.stream().mapToLong(Long::longValue).toArray();
        w.parent = parentArray();
        int before = w.marks.size();
        w.marks.addAll(ores);
        int added = w.marks.size() - before;
        if (added > 0 || fresh) {
            w.workedOut = w.marks.isEmpty();
            if (added > 0) {
                w.progress = 0; // new ore: prospecting can start again from the (possibly new) deepest spot
                w.faces = new long[0];
            }
        }
        w.surveyedAt = now();
        w.tier = Math.min(w.tier + (fresh ? 0 : 1), 99);
        w.note = waypoints.size() + " spots explored, " + torches + " torches placed" + (stepsBuilt > 0 ? ", " + stepsBuilt + " steps built" : "")
                + "; ore seen: " + (summary.isEmpty() ? "none" : summary);
        data().setDirty();
        npc.awardXp(6 + Math.min(30, ores.size()));
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal("Cave explorer " + npc.npcName()
                + " surveyed the cave at " + entrance.toShortString() + " (" + endReason + "): " + w.note
                + (ores.isEmpty() ? "" : ". Miners can go and mine it.")));
    }

    /** Monsters close by or wounded: abandon the survey and run home (what was seen so far is still handed in). */
    private boolean dangerCheck() {
        if (++dangerTimer % 10 != 0) {
            return false;
        }
        int threats = level().getEntitiesOfClass(Monster.class, npc.getBoundingBox().inflate(8), m -> m.isAlive()
                && CombatRules.isHostileMonster(m)).size();
        boolean hurt = npc.getHealth() < npc.getMaxHealth() * 0.6F;
        if (threats >= 2 || (threats > 0 && hurt) || (hurt && state == State.EXPLORE)) {
            retreating = true;
            npc.setTarget(null);
            finishSurvey(threats > 0 ? threats + " monster(s) in the cave" : "wounded");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        if (cavity != null) {
            tag.putLong("cavity", cavity.asLong());
        }
        if (entrance != null) {
            tag.putLong("entrance", entrance.asLong());
        }
        tag.putLongArray("waypoints", waypoints.stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("parents", parentArray());
        tag.putLongArray("ores", ores.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("noEntrance", noEntrance.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("current", current);
        tag.putLong("departed", departedAt);
        tag.putInt("torches", torches);
        tag.putString("summary", summary);
        tag.putString("reason", endReason);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            state = State.HOME;
        }
        resetSurvey();
        cavity = readPos(tag, "cavity");
        entrance = readPos(tag, "entrance");
        for (long l : tag.getLongArray("waypoints")) {
            waypoints.add(l);
        }
        for (int i : tag.getIntArray("parents")) {
            parents.add(i);
        }
        for (long l : tag.getLongArray("ores")) {
            ores.add(l);
        }
        for (long l : tag.getLongArray("noEntrance")) {
            noEntrance.add(l);
        }
        current = Math.min(tag.getInt("current"), waypoints.size() - 1);
        departedAt = tag.getLong("departed");
        torches = tag.getInt("torches");
        summary = tag.getString("summary");
        endReason = tag.getString("reason");
        if (parents.size() != waypoints.size()) {
            waypoints.clear();
            parents.clear();
            current = -1;
        }
        switch (state) {
            case EXPLORE -> headBack("interrupted");
            case ENTER, TRAVEL -> state = cavity == null ? State.HOME : State.TRAVEL;
            case REPORT -> state = State.RETURN;
            default -> {
            }
        }
    }
}
