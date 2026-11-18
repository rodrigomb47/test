package com.factionclash.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/**
 * What a hero adds to {@link NpcNavigator}'s trips (MOBILITY_AND_WORK 3.5 and 3.8; task A11): the links its own
 * navigation takes, and the flight mode with its ground fallback. Workers and soldiers have none
 * ({@link FactionNpc#travelHelper} is null), so their trips run exactly as before.
 */
public interface TravelHelper {
    /**
     * Before the ground navigation's own step: a result here is the trip's for this tick (a link or a flight is under
     * way, or a flight has arrived); null lets the ground navigation go on.
     */
    NpcNavigator.Result travel(NpcNavigator nav, BlockPos target, double reach, double speed);

    /**
     * A ground path was just searched toward {@code goal} (a waypoint of a long trip, or the destination): true when
     * the hero takes this leg in the air instead (it has started its take-off).
     */
    boolean chooseFlight(NpcNavigator nav, BlockPos goal, Path ground, BlockPos target);

    /** The ground navigation is about to give up the trip: true when the hero has another way (it takes off). */
    boolean rescue(NpcNavigator nav, BlockPos target);
}
