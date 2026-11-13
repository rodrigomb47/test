package com.factionclash.logic.hero;

import java.util.List;

/**
 * The bit layouts of a hero's synced fields (HEROES_V2 10.1, MOBILITY_AND_WORK 4.2) and the action code table
 * (HEROES_V2 appendix D). The server packs, the client unpacks; both use these. Pure, unit-tested
 * ({@code HeroActionsTest}).
 *
 * <pre>
 * DATA_ACTION (int)  bits 0-7 action code; 8-11 sequence (+1 per new action, so a repeat restarts);
 *                    12-17 speed (0.5 + n x 1.5 / 63); 18-23 variant; 24-31 reserved
 * DATA_STATE  (byte) bits 0-1 phase - 1; 2 enraged; 3 super armour; 4 weapon drawn; 5 talking;
 *                    6 in a wind-up or channel (no blinking); 7 escaping (HEROES_V2 6.13: there is no yield)
 * DATA_MOOD   (byte) bits 0-3 mood (12 values); 4-5 intensity; 6 blush; 7 eyes glow
 * DATA_LOCO   (byte) bits 0-3 locomotion mode; 4-5 one-shot; 6 one-shot sequence toggle; 7 tired
 * </pre>
 */
public final class HeroActions {
    private HeroActions() {
    }

    // ------------------------------------------------------------------ DATA_ACTION

    public static final int CODE_NONE = 0;
    public static final int SPEED_STEPS = 63;
    public static final double MIN_SPEED = 0.5;
    public static final double MAX_SPEED = 2.0;
    /** The speed step that plays at exactly 1.0. */
    public static final int NORMAL_SPEED_STEP = 21;

    /** Packs an action. The code and variant are cut to their bits, the sequence taken modulo 16, the speed clamped. */
    public static int packAction(int code, int sequence, double speed, int variant) {
        return (code & 0xFF) | (Math.floorMod(sequence, 16) << 8) | (speedStep(speed) << 12) | ((variant & 0x3F) << 18);
    }

    /** A new action after {@code previous}: its sequence number is one higher, so the client restarts even a repeat. */
    public static int nextAction(int previous, int code, double speed, int variant) {
        return packAction(code, sequence(previous) + 1, speed, variant);
    }

    public static int code(int action) {
        return action & 0xFF;
    }

    public static int sequence(int action) {
        return (action >>> 8) & 0xF;
    }

    public static int variant(int action) {
        return (action >>> 18) & 0x3F;
    }

    /** The playback speed, 0.5 to 2.0 in 63 steps (step 21 is exactly 1.0). */
    public static double speed(int action) {
        return MIN_SPEED + ((action >>> 12) & 0x3F) * (MAX_SPEED - MIN_SPEED) / SPEED_STEPS;
    }

    /** The nearest speed step, clamped to 0.5-2.0. */
    public static int speedStep(double speed) {
        if (!Double.isFinite(speed)) {
            return NORMAL_SPEED_STEP;
        }
        long n = Math.round((speed - MIN_SPEED) * SPEED_STEPS / (MAX_SPEED - MIN_SPEED));
        return (int) Math.max(0, Math.min(SPEED_STEPS, n));
    }

    // ------------------------------------------------------------------ DATA_STATE

    public static final int STATE_ENRAGED = 1 << 2;
    public static final int STATE_SUPER_ARMOUR = 1 << 3;
    public static final int STATE_WEAPON_DRAWN = 1 << 4;
    public static final int STATE_TALKING = 1 << 5;
    /** In a wind-up or a channel: the face does not blink. */
    public static final int STATE_WINDUP = 1 << 6;
    /** Escaping a lost fight (HEROES_V2 6.13, 15.6: the same bit the first draft's yield used; nobody yields). */
    public static final int STATE_ESCAPING = 1 << 7;

    /** The boss phase, 1 to 4 (bits 0-1 hold phase - 1). */
    public static int phase(int state) {
        return (state & 0b11) + 1;
    }

    public static byte withPhase(int state, int phase) {
        return (byte) ((state & ~0b11) | (Math.max(1, Math.min(4, phase)) - 1));
    }

    public static boolean has(int state, int flag) {
        return (state & flag) != 0;
    }

    public static byte with(int state, int flag, boolean on) {
        return (byte) (on ? state | flag : state & ~flag);
    }

    // ------------------------------------------------------------------ DATA_MOOD

    /** The 12 moods in their code order (HEROES_V2 4.6; the order {@code visual.json} lists as {@code mood_order}). */
    public static final List<String> MOODS = List.of("calm", "happy", "angry", "focused", "hurt", "tired", "sad", "smug",
            "surprised", "asleep", "flustered", "determined");

    public static byte packMood(int mood, int intensity, boolean blush, boolean eyesGlow) {
        return (byte) ((Math.floorMod(mood, MOODS.size()) & 0xF) | ((intensity & 0b11) << 4) | (blush ? 1 << 6 : 0)
                | (eyesGlow ? 1 << 7 : 0));
    }

    /** The mood code (0-11; a stray 12-15 reads as calm). */
    public static int mood(int moodByte) {
        int m = moodByte & 0xF;
        return m < MOODS.size() ? m : 0;
    }

    public static int moodIntensity(int moodByte) {
        return (moodByte >>> 4) & 0b11;
    }

    public static boolean blush(int moodByte) {
        return (moodByte & (1 << 6)) != 0;
    }

    public static boolean eyesGlow(int moodByte) {
        return (moodByte & (1 << 7)) != 0;
    }

    /** The code of a mood name, or -1. */
    public static int moodCode(String name) {
        return MOODS.indexOf(name);
    }

    // ------------------------------------------------------------------ DATA_LOCO (MOBILITY_AND_WORK 4.2)

    /** Locomotion modes in their code order; code 0 is the ground rules of today's walking. */
    public static final List<String> LOCO_MODES = List.of("ground", "airborne", "glide", "fly", "fly_hover", "hover", "climb",
            "cling", "swim", "swim_down", "charge_run", "sprint", "downed", "winded", "ride", "reserved");
    public static final int LOCO_ONE_SHOT_NONE = 0, LOCO_ONE_SHOT_START = 1, LOCO_ONE_SHOT_END = 2, LOCO_ONE_SHOT_SPECIAL = 3;

    public static byte packLoco(int mode, int oneShot, boolean toggle, boolean tired) {
        return (byte) ((mode & 0xF) | ((oneShot & 0b11) << 4) | (toggle ? 1 << 6 : 0) | (tired ? 1 << 7 : 0));
    }

    public static int locoMode(int loco) {
        return loco & 0xF;
    }

    public static int locoOneShot(int loco) {
        return (loco >>> 4) & 0b11;
    }

    public static boolean locoToggle(int loco) {
        return (loco & (1 << 6)) != 0;
    }

    public static boolean locoTired(int loco) {
        return (loco & (1 << 7)) != 0;
    }

    // ------------------------------------------------------------------ action codes (appendix D)

    private static final String[] WORK = {"work.mine", "work.chop", "work.till", "work.plant", "work.harvest", "work.fish_cast",
            "work.fish_wait", "work.fish_reel", "work.cook", "work.smith", "work.build", "work.read", "work.pray", "work.sweep",
            "work.tend", "work.eat"};
    private static final String[] EMOTES = {"emote.greet", "emote.bow", "emote.wave", "emote.laugh", "emote.angry", "emote.sad",
            "emote.think", "emote.cheer", "emote.talk", "emote.shrug", "personal.1", "personal.2", "personal.3"};
    private static final String[] FIXED = {null, "attack.1", "attack.2", "attack.3", "attack.ranged", "guard", "guard.hit", "dodge",
            "stagger", "stun", "interrupt", "hurt.heavy", "draw", "sheathe", "taunt", "eat", "intro", "phase.2", "phase.3", "enrage",
            "victory", "defeated", "spared", "founding"};

    public static final int FIRST_STATE = 24, FIRST_WORK = 32, FIRST_EMOTE = 48, FIRST_MOVE = 64, MOVE_SLOTS = 12;
    public static final int ULTIMATE = 76, ULTIMATE_END = 77, ULTIMATE_AWAKENED = 78;

    /**
     * The animation an action code plays, for codes whose name is fixed; {@code state.N} and {@code move.N} name a
     * slot (1-3, 0-11) that the hero's {@code visual.json} turns into a name. Null for code 0 and reserved codes.
     */
    public static String actionName(int code) {
        if (code > 0 && code < FIXED.length) {
            return FIXED[code];
        }
        if (code >= FIRST_STATE && code < FIRST_STATE + 3) {
            return "state." + (code - FIRST_STATE + 1);
        }
        if (code >= FIRST_WORK && code < FIRST_WORK + WORK.length) {
            return WORK[code - FIRST_WORK];
        }
        if (code >= FIRST_EMOTE && code < FIRST_EMOTE + EMOTES.length) {
            return EMOTES[code - FIRST_EMOTE];
        }
        if (code >= FIRST_MOVE && code < FIRST_MOVE + MOVE_SLOTS) {
            return "move." + (code - FIRST_MOVE);
        }
        return switch (code) {
            case ULTIMATE -> "ultimate";
            case ULTIMATE_END -> "ultimate.end";
            case ULTIMATE_AWAKENED -> "ultimate.awakened";
            default -> null;
        };
    }

    /**
     * The telegraph pose of a move's wind-up (v1 10.5 {@code look.pose}; task A4): the clip that holds the pose while
     * the hero gathers, or 0 for none ({@code none}, or no look). The decals are A6's; A5 plays a move's own
     * {@code move.<id>} clip by its timing file in place of these.
     */
    public static int telegraphPose(HeroDef.Pose pose) {
        if (pose == null) {
            return CODE_NONE;
        }
        String clip = switch (pose) {
            case NONE -> null;
            case SWING -> "attack.1";
            case BLOCK -> "guard";
            case BOW_DRAW, SPEAR_THROW, CROSSBOW_CHARGE -> "attack.ranged";
            case HORN -> "emote.cheer";
            case SPYGLASS -> "emote.think";
        };
        return clip == null ? CODE_NONE : actionCode(clip);
    }

    /** The code of a fixed animation name ({@code attack.1}, {@code work.mine}, ...), or -1. */
    public static int actionCode(String name) {
        for (int c = 1; c < 256; c++) {
            if (name.equals(actionName(c))) {
                return c;
            }
        }
        return -1;
    }
}
