package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroClipsTest {
    /** What the test rig's chain has today (G3): idle, walk, run in the core, combat.idle and attack.1 in katana. */
    private static final Predicate<String> RIG = Set.of("move.idle", "move.walk", "move.run", "combat.idle", "attack.1")::contains;

    private static HeroClips.Motion stand(boolean drawn) {
        return new HeroClips.Motion(0, 0, true, false, false, false, false, drawn, 0);
    }

    private static HeroClips.Motion moving(double speed) {
        return new HeroClips.Motion(speed, 0, true, false, false, false, false, false, 0);
    }

    @Test
    void standingWalkingRunning() {
        assertEquals("move.idle", HeroClips.first(HeroClips.baseCandidates(stand(false)), RIG));
        assertEquals("combat.idle", HeroClips.first(HeroClips.baseCandidates(stand(true)), RIG), "weapon drawn");
        assertEquals("move.walk", HeroClips.first(HeroClips.baseCandidates(moving(0.216)), RIG), "an NPC's walk");
        assertEquals("move.run", HeroClips.first(HeroClips.baseCandidates(moving(0.28)), RIG), "a sprint");
        assertEquals("move.idle", HeroClips.first(HeroClips.baseCandidates(moving(0.01)), RIG), "a drift is standing");
        assertEquals("move.walk", HeroClips.first(HeroClips.baseCandidates(moving(0.3)), Set.of("move.idle", "move.walk")::contains),
                "no run clip: walk");
    }

    @Test
    void clipsTheLibrariesLackFallBack() {
        HeroClips.Motion swim = new HeroClips.Motion(0.1, 0, false, true, false, false, false, false, 0);
        assertEquals("move.swim", HeroClips.baseCandidates(swim).get(0));
        assertEquals("move.walk", HeroClips.first(HeroClips.baseCandidates(swim), RIG));
        HeroClips.Motion fall = new HeroClips.Motion(0, -0.5, false, false, false, false, false, false, 0);
        assertEquals("move.fall", HeroClips.baseCandidates(fall).get(0));
        assertEquals("move.idle", HeroClips.first(HeroClips.baseCandidates(fall), RIG));
        HeroClips.Motion hop = new HeroClips.Motion(0.2, -0.1, false, false, false, false, false, false, 0);
        assertEquals("move.walk", HeroClips.baseCandidates(hop).get(0), "a hop is not a fall");
        HeroClips.Motion sleep = new HeroClips.Motion(0, 0, true, false, false, true, false, false, 0);
        assertEquals(List.of("rest.sleep", "move.idle"), HeroClips.baseCandidates(sleep));
        HeroClips.Motion crouch = new HeroClips.Motion(0.05, 0, true, false, true, false, false, false, 0);
        assertEquals(List.of("move.crouch", "move.walk", "move.idle"), HeroClips.baseCandidates(crouch));
    }

    @Test
    void locomotionModesComeFirstWhenTheMobilityEngineSetsThem() {
        int fly = HeroActions.LOCO_MODES.indexOf("fly");
        HeroClips.Motion flying = new HeroClips.Motion(0.4, 0, false, false, false, false, false, false, fly);
        assertEquals("move.fly", HeroClips.baseCandidates(flying).get(0));
        assertEquals("move.run", HeroClips.first(HeroClips.baseCandidates(flying), RIG), "no fly clip yet: the ground rules");
        int climb = HeroActions.LOCO_MODES.indexOf("climb");
        assertEquals("move.climb", HeroClips.baseCandidates(new HeroClips.Motion(0, 0, false, false, false, false, false, false, climb)).get(0));
        assertEquals("move.idle", HeroClips.baseCandidates(new HeroClips.Motion(0, 0, true, false, false, false, false, false, 15)).get(0),
                "the reserved mode is ground");
    }

    @Test
    void walkAndRunPlayAtTheirGroundSpeedInStepsOfATenth() {
        assertEquals(2.0, HeroClips.playback("move.walk", 0.216), 1e-9);
        assertEquals(1.4, HeroClips.playback("move.run", 0.28), 1e-9);
        assertEquals(HeroClips.MIN_PLAYBACK, HeroClips.playback("move.walk", 0.02), 1e-9);
        assertEquals(HeroClips.MAX_PLAYBACK, HeroClips.playback("move.run", 2.0), 1e-9);
        assertEquals(1.0, HeroClips.playback("move.idle", 0.3), 1e-9);
        assertTrue(HeroClips.speedChanged(1.4, 1.7));
        assertTrue(!HeroClips.speedChanged(1.4, 1.6), "small changes keep the phase");
    }

    @Test
    void forcedPoses() {
        assertEquals("auto", HeroClips.FORCED.get(0));
        assertEquals(List.of(), HeroClips.forced(0));
        assertEquals(List.of("move.walk", "move.idle"), HeroClips.forced(HeroClips.FORCED.indexOf("walk")));
        assertEquals(List.of("move.idle"), HeroClips.forced(HeroClips.FORCED.indexOf("idle")));
        assertEquals("combat.idle", HeroClips.forced(HeroClips.FORCED.indexOf("combat")).get(0));
        assertEquals(HeroClips.FORCED_WALK_SPEED, HeroClips.forcedSpeed(HeroClips.FORCED.indexOf("walk")));
        assertEquals(List.of(), HeroClips.forced(99));
    }

    @Test
    void actionCodesNameTheirClipsAndLoops() {
        Predicate<String> has = Set.of("attack.1", "guard", "guard.loop", "move.lantern_volley", "state.burning", "ultimate", "ultimate.loop")::contains;
        assertEquals(List.of("attack.1"), HeroClips.actionStages(1, List.of(), List.of(), has));
        assertEquals(List.of("guard", "guard.loop"), HeroClips.actionStages(5, List.of(), List.of(), has), "a channel holds on its loop");
        assertEquals(List.of("move.lantern_volley"), HeroClips.actionStages(HeroActions.FIRST_MOVE, List.of("lantern_volley"), List.of(), has));
        assertEquals(List.of("move.lantern_volley"), HeroClips.actionStages(HeroActions.FIRST_MOVE, List.of("move.lantern_volley"), List.of(), has));
        assertEquals(List.of(), HeroClips.actionStages(HeroActions.FIRST_MOVE + 1, List.of("lantern_volley"), List.of(), has), "no slot 1");
        assertEquals(List.of("state.burning"), HeroClips.actionStages(HeroActions.FIRST_STATE, List.of(), List.of("burning"), has));
        assertEquals(List.of("ultimate", "ultimate.loop"), HeroClips.actionStages(HeroActions.ULTIMATE, List.of(), List.of(), has));
        assertEquals(List.of(), HeroClips.actionStages(2, List.of(), List.of(), has), "attack.2 is not in the chain");
        assertEquals(List.of(), HeroClips.actionStages(0, List.of(), List.of(), has));
        assertEquals(List.of(), HeroClips.actionStages(200, List.of(), List.of(), has));
        assertNull(HeroClips.actionClip(HeroActions.FIRST_STATE + 2, List.of(), List.of("a")));
    }

    @Test
    void aSwingPlaysTheWorkOfTheHeldTool() {
        assertEquals("attack.1", HeroClips.first(HeroClips.swingCandidates(HeroClips.Held.PICKAXE, false), RIG),
                "no work.mine yet: the swing");
        assertEquals("work.mine", HeroClips.swingCandidates(HeroClips.Held.PICKAXE, false).get(0));
        assertEquals("work.mine", HeroClips.swingCandidates(HeroClips.Held.SHOVEL, false).get(0));
        assertEquals("work.chop", HeroClips.swingCandidates(HeroClips.Held.AXE, false).get(0));
        assertEquals("attack.1", HeroClips.swingCandidates(HeroClips.Held.AXE, true).get(0), "an axe in a fight is a weapon");
        assertEquals("work.till", HeroClips.swingCandidates(HeroClips.Held.HOE, false).get(0));
        assertEquals(List.of("attack.1"), HeroClips.swingCandidates(HeroClips.Held.WEAPON, true));
        assertNull(HeroClips.first(HeroClips.swingCandidates(HeroClips.Held.WEAPON, true), Set.of("move.idle")::contains),
                "a fists hero with an empty style library plays nothing");
    }

    @Test
    void aTeleportIsNotRunning() {
        assertFalse(HeroClips.isTeleport(0.28), "a sprint");
        assertFalse(HeroClips.isTeleport(HeroClips.TELEPORT_STEP), "the limit itself still counts as motion");
        assertTrue(HeroClips.isTeleport(8.0), "a /tp or a stuck recovery jump");
        assertTrue(HeroClips.isTeleport(Double.NaN));
    }
}
