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

    /**
     * A run drops back to a walk only below this ground speed (PROPOSED): a band under {@link #RUNNING}, because NPCs
     * travel right about it (0.24-0.26 blocks a tick seen in game, with dips to 0.15 at corners and doors), and without
     * the band the hero flipped between walk and run several times a second, each flip restarting the clip.
     */
    public static final double RUN_TO_WALK = 0.20;
    /** A change between walking and running shows only once the new pace has held this many ticks (PROPOSED). */
    public static final int PACE_HOLD = 6;
    /** Paces: standing, walking, running. */
    public static final int STAND = 0, WALK = 1, RUN = 2;

    /**
     * The pace a ground speed asks for after {@code previous}: standing below {@link #MOVING}, running from
     * {@link #RUNNING}, and a run keeps running down to {@link #RUN_TO_WALK}.
     */
    public static int pace(double ground, int previous) {
        if (!(ground >= MOVING)) {
            return STAND;
        }
        if (ground >= RUNNING) {
            return RUN;
        }
        return previous == RUN && ground >= RUN_TO_WALK ? RUN : WALK;
    }

    /**
     * A ground speed that stands for {@code pace} in the clip choice ({@link #baseCandidates}, {@link #baseKey}): the
     * real speed moved just inside the pace's range. The clip's playback speed still follows the real speed.
     */
    public static double paceSpeed(int pace, double ground) {
        return switch (pace) {
            case RUN -> Math.max(ground, RUNNING);
            case WALK -> Math.max(MOVING, Math.min(ground, RUNNING - 1e-3));
            default -> 0.0;
        };
    }

    /**
     * The pace one hero shows (one per hero, fed once a tick with its smoothed ground speed): the band of {@link #pace},
     * and a change between walking and running waits until the new pace has held {@link #PACE_HOLD} ticks; starting and
     * stopping show at once. Pure ({@code HeroClipsTest}).
     */
    public static final class PaceGate {
        private int shown = STAND;
        private int wanted = STAND;
        private long since;

        public int update(double ground, long tick) {
            int w = pace(ground, shown);
            if (w != wanted) {
                wanted = w;
                since = tick;
            }
            if (w != shown && (w == STAND || shown == STAND || tick - since >= PACE_HOLD)) {
                shown = w;
            }
            return shown;
        }

        public int shown() {
            return shown;
        }
    }

    /** Whether a step of {@code blocks} across the ground in one tick is a teleport. */
    public static boolean isTeleport(double blocks) {
        return !(blocks <= TELEPORT_STEP);
    }

    /** How the hero moves this frame (client side; ground speed smoothed over a few ticks). */
    public record Motion(double groundSpeed, double verticalSpeed, boolean onGround, boolean inWater, boolean crouching,
                         boolean sleeping, boolean sitting, boolean weaponDrawn, int locoMode) {
    }

    /**
     * Clips for the locomotion modes of {@code DATA_LOCO} (MOBILITY_AND_WORK 4.2), by mode code; null: ground rules. The
     * names are G10's (MOBILITY_AND_WORK 4.1): an airborne hero on its own jump or leap plays {@code move.leap}, a
     * clinging one {@code move.climb_idle}, the charger's traversal charge {@code move.charge_run}, the runner's burst
     * {@code move.sprint}, a hero flying on a mount carrier (a broom, a cloud, a sword, a crane) {@code move.ride}.
     */
    private static final String[] LOCO_CLIPS = {null, "move.leap", "move.glide", "move.fly", "move.fly_hover", "move.hover",
            "move.climb", "move.climb_idle", "move.swim", "move.swim_down", "move.charge_run", "move.sprint", "move.downed",
            "move.winded", "move.ride", null};
    /** The second choice of a mode whose own clip a hero may lack (the core library has them all). */
    private static final String[] LOCO_FALLBACK = {null, "move.fall", null, null, "move.fly", null, null, "move.climb",
            null, "move.swim", "move.run", "move.run", null, "move.idle", "move.fly", null};
    /** A flyer at or above this ground speed (blocks a tick, 6 b/s) plays {@code move.fly_fast}. */
    public static final double FLY_FAST = 0.3;
    /** A climber going down faster than this (blocks a tick) plays {@code move.climb_down}. */
    public static final double CLIMBING_DOWN = 0.03;

    private static final int MODE_CLIMB = 6, MODE_FLY = 3;

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
            if (mode == MODE_CLIMB && m.verticalSpeed() < -CLIMBING_DOWN) {
                out.add("move.climb_down");
            } else if (mode == MODE_FLY && m.groundSpeed() >= FLY_FAST) {
                out.add("move.fly_fast");
            }
            out.add(LOCO_CLIPS[mode]);
            if (LOCO_FALLBACK[mode] != null) {
                out.add(LOCO_FALLBACK[mode]);
            }
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
     * Everything {@link #baseCandidates} decides on, as one number: two motions with the same key get the same
     * candidates, so the {@code base} controller resolves its clip again only when the key changes (or the hero's
     * animation chain does), not every frame. Forced poses ({@link #FORCED}) and one-shots ({@link #oneShotKey}) have
     * keys of their own.
     */
    public static int baseKey(Motion m) {
        int mode = m.locoMode() & 0xF;
        int locoClip = mode > 0 && mode < LOCO_CLIPS.length && LOCO_CLIPS[mode] != null ? mode : 0;
        int pace = m.groundSpeed() >= RUNNING ? 2 : m.groundSpeed() >= MOVING ? 1 : 0;
        boolean swimming = m.inWater() && !m.onGround();
        boolean falling = !swimming && !m.onGround() && m.verticalSpeed() < -FALLING;
        boolean climbingDown = locoClip == MODE_CLIMB && m.verticalSpeed() < -CLIMBING_DOWN;
        boolean flyingFast = locoClip == MODE_FLY && m.groundSpeed() >= FLY_FAST;
        return (m.sleeping() ? 1 : 0) | (m.sitting() ? 2 : 0) | locoClip << 2 | (swimming ? 1 << 6 : 0) | (falling ? 1 << 7 : 0)
                | (m.crouching() ? 1 << 8 : 0) | pace << 9 | (pace == 0 && m.weaponDrawn() ? 1 << 11 : 0)
                | (climbingDown ? 1 << 12 : 0) | (flyingFast ? 1 << 13 : 0);
    }

    // ------------------------------------------------------------------ locomotion one-shots (MOBILITY_AND_WORK 4.2)

    /**
     * The one-shot clip a {@code DATA_LOCO} mode and one-shot kind name (null: none). A one-shot keeps the mode that
     * names it for its whole length, so each (mode, kind) pair is one clip:
     * <pre>
     *             start          end           special
     * ground      crouch_jump    land          land_roll
     * airborne    jump           land_heavy    air_jump
     * glide       glide_start    land          gust
     * fly, hover  takeoff        fly_land      -
     * ride        takeoff        fly_land      -
     * climb       -              -             mantle
     * cling       -              -             wall_kick
     * charge_run  -              skid          -
     * sprint      -              skid          -
     * downed      downed         -             -
     * </pre>
     */
    public static String oneShotClip(int mode, int kind) {
        String m = mode >= 0 && mode < HeroActions.LOCO_MODES.size() ? HeroActions.LOCO_MODES.get(mode) : "";
        return switch (kind) {
            case HeroActions.LOCO_ONE_SHOT_START -> switch (m) {
                case "ground" -> "move.crouch_jump";
                case "airborne" -> "move.jump";
                case "glide" -> "move.glide_start";
                case "fly", "fly_hover", "ride" -> "move.takeoff";
                case "downed" -> "move.downed";
                default -> null;
            };
            case HeroActions.LOCO_ONE_SHOT_END -> switch (m) {
                case "ground", "glide" -> "move.land";
                case "airborne" -> "move.land_heavy";
                case "fly", "fly_hover", "ride" -> "move.fly_land";
                case "charge_run", "sprint" -> "move.skid";
                default -> null;
            };
            case HeroActions.LOCO_ONE_SHOT_SPECIAL -> switch (m) {
                case "ground" -> "move.land_roll";
                case "airborne" -> "move.air_jump";
                case "glide" -> "move.gust";
                case "climb" -> "move.mantle";
                case "cling" -> "move.wall_kick";
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * The loop a one-shot settles into (G10: each ends on the first frame of the loop that follows it): a jump into the
     * leap, a glide's start or gust into the glide, a take-off into the flight (on a mount carrier, the ride), a
     * landing, the mantle, the skid and the downing into the idle.
     */
    public static String oneShotSettle(int mode, int kind) {
        String clip = oneShotClip(mode, kind);
        if (clip == null) {
            return null;
        }
        return switch (clip) {
            case "move.jump", "move.crouch_jump", "move.air_jump", "move.wall_kick" -> "move.leap";
            case "move.glide_start", "move.gust" -> "move.glide";
            case "move.takeoff" -> mode == 14 ? "move.ride" : "move.fly";
            default -> "move.idle";
        };
    }

    /** The lengths of the one-shots in ticks (MOBILITY_AND_WORK 4.1), when a hero's timing files do not say. */
    public static int oneShotTicks(String clip) {
        if (clip == null) {
            return 0;
        }
        return switch (clip) {
            case "move.jump", "move.crouch_jump", "move.land", "move.wall_kick" -> 6;
            case "move.air_jump" -> 7;
            case "move.glide_start", "move.gust", "move.fly_land", "move.skid" -> 8;
            case "move.mantle", "move.land_heavy", "move.land_roll" -> 10;
            case "move.takeoff" -> 12;
            case "move.downed" -> 40;
            default -> 10;
        };
    }

    /**
     * The key of a playing one-shot: its mode, kind and sequence toggle (a new one-shot of the same clip has another
     * key, so the controller restarts it); never equal to a motion's or a forced pose's.
     */
    public static int oneShotKey(int mode, int kind, boolean toggle) {
        return 1 << 21 | (mode & 0xF) | (kind & 0b11) << 4 | (toggle ? 1 << 6 : 0);
    }

    // ------------------------------------------------------------------ wings (MOBILITY_AND_WORK 4.3)

    /**
     * The wing loop of a winged hero's {@code wings} controller by its {@code DATA_LOCO} mode: spread to glide, beating
     * in powered flight (its take-off and landing too), folded otherwise (walking, riding a mount carrier).
     */
    public static String wingLoop(int mode) {
        String m = mode >= 0 && mode < HeroActions.LOCO_MODES.size() ? HeroActions.LOCO_MODES.get(mode) : "";
        return switch (m) {
            case "glide" -> "sec.wings_glide";
            case "fly", "fly_hover" -> "sec.wings_flap";
            default -> "sec.wings_fold";
        };
    }

    /** The wing beat's playback speed: 1, slower when the hero is tired (the {@code tired} bit, 2.2). */
    public static double wingSpeed(String loop, boolean tired) {
        return "sec.wings_flap".equals(loop) && tired ? 0.7 : 1.0;
    }

    /** The key of a forced pose (never equal to a motion's {@link #baseKey}). */
    public static int forcedKey(int code) {
        return 1 << 20 | code & 0xFF;
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
