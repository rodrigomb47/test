package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which animation clip each controller plays (HEROES_V2 4.4, 4.8; MOBILITY_AND_WORK 4.2): the {@code base} clip from how
 * the hero moves, with the playback speed that keeps its feet near the ground speed; the {@code action} clips of an
 * action code; the work clip a fresh swing plays by the held tool; the poses an operator can force. Every choice is a
 * list of candidates, best first: the controller plays the first one the hero's animation chain has, so a clip the
 * libraries do not have yet (G4's swim, fall, crouch, sit, sleep, work clips) falls back to one they have. Pure,
 * unit-tested ({@code HeroClipsTest}).
 */
public final class HeroClips {
    private HeroClips() {
    }

    /**
     * The controllers' transition lengths in ticks (HEROES_V2 4.4): the timing files state the blend every clip was
     * written for (events at clip time + blend), and {@code HeroTimingsTest} checks each file against these.
     */
    public static final int BASE_TRANSITION = 4, ACTION_TRANSITION = 2, SECONDARY_TRANSITION = 6;

    // ------------------------------------------------------------------ base

    /** Below this ground speed (blocks per tick) the hero stands. */
    public static final double MOVING = 0.015;
    /**
     * At or above this it runs (PROPOSED): between an NPC's walking speed (0.216, a player's walk) and its sprint
     * (0.28), so ordinary travel plays the walk and a sprint the run.
     */
    public static final double RUNNING = 0.245;
    /**
     * The ground speeds the walk and run clips play at 1x (PROPOSED, chosen by eye in game). G3's walk is a stroll
     * (legs +-21 degrees, 1.0 s), so an NPC at a player's 4.3 blocks/s plays it about twice as fast; the run (+-36, 0.6 s)
     * fits a sprint at about 1.4x.
     */
    public static final double WALK_AT_1X = 0.11, RUN_AT_1X = 0.20;
    public static final double MIN_PLAYBACK = 0.6, MAX_PLAYBACK = 2.2;
    /** Falling faster than this (blocks per tick, downward) off the ground plays the fall clip. */
    public static final double FALLING = 0.25;
    /**
     * A move of more than this many blocks across the ground in one tick is a teleport (a command, stuck recovery, a
     * revival), not running: it is left out of the ground speed, or the base clip would flash to the run, then the walk,
     * and restart the idle (seen in game at checkpoint 1). Far above any walk, sprint or dash.
     */
    public static final double TELEPORT_STEP = 3.0;

    /** Whether a step of {@code blocks} across the ground in one tick is a teleport. */
    public static boolean isTeleport(double blocks) {
        return !(blocks <= TELEPORT_STEP);
    }

    /** How the hero moves this frame (client side; ground speed smoothed over a few ticks). */
    public record Motion(double groundSpeed, double verticalSpeed, boolean onGround, boolean inWater, boolean crouching,
                         boolean sleeping, boolean sitting, boolean weaponDrawn, int locoMode) {
    }

    /** Clips for the locomotion modes of {@code DATA_LOCO} (MOBILITY_AND_WORK 4.2), by mode code; null: ground rules. */
    private static final String[] LOCO_CLIPS = {null, "move.fall", "move.glide", "move.fly", "move.fly_hover", "move.hover",
            "move.climb", "move.cling", "move.swim", "move.swim_down", "move.charge", "move.run", "move.downed", "move.winded",
            "rest.sit", null};

    /** The base clips to try, best first. */
    public static List<String> baseCandidates(Motion m) {
        List<String> out = new ArrayList<>(4);
        if (m.sleeping()) {
            out.add("rest.sleep");
        } else if (m.sitting()) {
            out.add("rest.sit");
        }
        int mode = m.locoMode() & 0xF;
        if (mode > 0 && mode < LOCO_CLIPS.length && LOCO_CLIPS[mode] != null) {
            out.add(LOCO_CLIPS[mode]);
        }
        if (m.inWater() && !m.onGround()) {
            out.add("move.swim");
        } else if (!m.onGround() && m.verticalSpeed() < -FALLING) {
            out.add("move.fall");
        }
        if (m.crouching()) {
            out.add("move.crouch");
        }
        if (m.groundSpeed() >= RUNNING) {
            out.add("move.run");
            out.add("move.walk");
        } else if (m.groundSpeed() >= MOVING) {
            out.add("move.walk");
        } else if (m.weaponDrawn()) {
            out.add("combat.idle");
        }
        out.add("move.idle");
        return out;
    }

    /**
     * The playback speed of a base clip at a ground speed: walk and run scale with it (in steps of 0.1, so the speed
     * changes rarely: GeckoLib rescales the whole clip time, which jumps the phase), everything else plays at 1.
     */
    public static double playback(String clip, double groundSpeed) {
        double at1;
        if ("move.walk".equals(clip)) {
            at1 = WALK_AT_1X;
        } else if ("move.run".equals(clip)) {
            at1 = RUN_AT_1X;
        } else {
            return 1.0;
        }
        double s = Math.max(MIN_PLAYBACK, Math.min(MAX_PLAYBACK, groundSpeed / at1));
        return Math.round(s * 10) / 10.0;
    }

    /** Whether the base controller should take a new playback speed (it changes only by 0.3 or more). */
    public static boolean speedChanged(double playing, double wanted) {
        return Math.abs(playing - wanted) >= 0.3 - 1e-9;
    }

    /** The first candidate the hero has, or null. */
    public static String first(List<String> candidates, Predicate<String> has) {
        for (String c : candidates) {
            if (c != null && has.test(c)) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ forced poses (operator, screenshots)

    /** Base poses an operator can force with {@code /fc admin hero pose <id> base <name>} (index = the synced code). */
    public static final List<String> FORCED = List.of("auto", "idle", "combat", "walk", "run", "swim", "fall", "crouch",
            "sit", "sleep", "climb", "fly", "glide");
    private static final String[] FORCED_CLIPS = {null, "move.idle", "combat.idle", "move.walk", "move.run", "move.swim",
            "move.fall", "move.crouch", "rest.sit", "rest.sleep", "move.climb", "move.fly", "move.glide"};
    /** The ground speeds a forced walk and run play at (an NPC's walk and sprint). */
    public static final double FORCED_WALK_SPEED = 0.216, FORCED_RUN_SPEED = 0.28;

    /** The candidates of a forced pose code (empty for "auto" or an unknown code). */
    public static List<String> forced(int code) {
        if (code <= 0 || code >= FORCED_CLIPS.length) {
            return List.of();
        }
        String clip = FORCED_CLIPS[code];
        return "move.idle".equals(clip) ? List.of(clip) : List.of(clip, "move.idle");
    }

    /** The ground speed a forced pose plays at. */
    public static double forcedSpeed(int code) {
        return code == 3 ? FORCED_WALK_SPEED : code == 4 ? FORCED_RUN_SPEED : 0.0;
    }

    // ------------------------------------------------------------------ actions

    /**
     * The clips an action code plays, in order: the code's animation, then its {@code .loop} when the chain has one
     * (guard, stun, defeated, work.fish_wait, the ultimate's loop: channels hold until the next code). {@code move.N} and
     * {@code state.N} take their names from the hero's visual file. Empty for code 0, a reserved code or a slot the
     * hero does not have.
     */
    public static List<String> actionStages(int code, List<String> moves, List<String> states, Predicate<String> has) {
        String name = actionClip(code, moves, states);
        if (name == null || !has.test(name)) {
            return List.of();
        }
        String loop = name + ".loop";
        return has.test(loop) ? List.of(name, loop) : List.of(name);
    }

    /** The animation name an action code plays for this hero, or null. */
    public static String actionClip(int code, List<String> moves, List<String> states) {
        String fixed = HeroActions.actionName(code);
        if (fixed == null) {
            return null;
        }
        if (code >= HeroActions.FIRST_MOVE && code < HeroActions.FIRST_MOVE + HeroActions.MOVE_SLOTS) {
            int slot = code - HeroActions.FIRST_MOVE;
            return moves != null && slot < moves.size() && moves.get(slot) != null ? prefixed("move.", moves.get(slot)) : null;
        }
        if (code >= HeroActions.FIRST_STATE && code < HeroActions.FIRST_STATE + 3) {
            int slot = code - HeroActions.FIRST_STATE;
            return states != null && slot < states.size() && states.get(slot) != null ? prefixed("state.", states.get(slot)) : null;
        }
        return fixed;
    }

    private static String prefixed(String prefix, String name) {
        return name.startsWith(prefix) ? name : prefix + name;
    }

    /** What a hero holds, for the work clip a swing plays (HEROES_V2 4.8 "Work"). */
    public enum Held { NOTHING, PICKAXE, SHOVEL, AXE, HOE, WEAPON, OTHER }

    /**
     * The clips a fresh swing plays while no action runs: mining with a pickaxe or shovel, chopping with an axe while
     * not fighting, tilling with a hoe; a weapon, an axe in a fight or anything else swings {@code attack.1}.
     */
    public static List<String> swingCandidates(Held held, boolean fighting) {
        return switch (held) {
            case PICKAXE, SHOVEL -> List.of("work.mine", "attack.1");
            case AXE -> fighting ? List.of("attack.1") : List.of("work.chop", "attack.1");
            case HOE -> List.of("work.till", "attack.1");
            default -> List.of("attack.1");
        };
    }
}
