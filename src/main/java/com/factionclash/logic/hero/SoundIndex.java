package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The hero sound index (SOUND.md section 15, task S2): {@code assets/factionclash/sounds/hero_sfx.json}, written by
 * {@code tools/sfxgen} and never by hand. Pure (no Minecraft class): the game reads it to register every event with
 * its range, to play each with its pitch and volume ranges and companions, and to find which sound belongs to which
 * moment of a hero's fight (the hero map of section 4.7) and of the shared cue set.
 *
 * <p>Also the timing rules of section 7 that depend on nothing but the index: which class cue a move's wind-up plays
 * ({@link #telegraphCue}) and when ({@link #cueDelay}, so the cue's {@code hit_at} falls on the release), which impact a
 * shot plays ({@link MoveSounds#impact(int)}), and which passive a map key names ({@link #passiveKey}). Nothing here
 * reads who a hero fights: only listeners are players (6.15).
 */
public final class SoundIndex {
    /** Who plays an event (section 4.1): the server at its moment, or the client from an animation or local state. */
    public enum Plays { SERVER, CLIENT }

    /** A companion: another event started with it at the same place, {@code delay} ticks later (section 4.6). */
    public record Companion(String sound, int delay) {
    }

    /**
     * One event: its full id, set, kind, who plays it, vanilla category, range (blocks: what the game really gives it),
     * play-time pitch and volume ranges, source ({@code generated}, {@code vanilla} or {@code alias}), variants, length
     * (seconds, 0 when unknown), whether it loops, companions, and {@code hitAt} (telegraph cues: seconds into the cue
     * where the attack lands; NaN for none).
     */
    public record Event(String id, String set, String kind, Plays plays, String category, double range,
                        double pitchLo, double pitchHi, double volumeLo, double volumeHi, String source, int variants,
                        double length, boolean loop, List<Companion> with, double hitAt, String subtitle) {
        public Event {
            with = List.copyOf(with);
        }

        public boolean hasHitAt() {
            return !Double.isNaN(hitAt);
        }

        /** A pitch from the play range for a uniform draw {@code u} in [0, 1). */
        public double pitch(double u) {
            return pick(pitchLo, pitchHi, u);
        }

        public double volume(double u) {
            return pick(volumeLo, volumeHi, u);
        }
    }

    /** A vanilla sound with a pitch (the voice's hurt and death). */
    public record Pitched(String event, double pitch) {
    }

    /**
     * The sounds of one move (section 4.7): its telegraph {@code cls} (light, heavy, area, ultimate), {@code windup}
     * (server, at the wind-up's start), {@code release} (client keyframe, or the server at the release tick when the
     * event is {@code plays: server}), {@code impact} (server, each time damage lands; a list cycles per shot),
     * {@code loop} (client, while a channel lasts), {@code end} (client keyframe) and {@code broken} (server). Null where
     * the map names nothing.
     */
    public record MoveSounds(String cls, String windup, String release, List<String> impact, String loop, String end,
                             String broken) {
        public MoveSounds {
            impact = List.copyOf(impact);
        }

        /** The impact of shot {@code shot} (0-based; a list cycles), or null. */
        public String impact(int shot) {
            return impact.isEmpty() ? null : impact.get(Math.floorMod(shot, impact.size()));
        }
    }

    /**
     * A hero's voice (section 6): its blip event ({@code hero.voice.<id>}), the syllable rate in ticks, the melody
     * (semitone steps), the jitter, the note-block fallback, hurt and death (vanilla, pitched), emotes.
     */
    public record Voice(String talk, int rate, List<Double> melody, double jitter, String fallbackBlip, Pitched hurt,
                        Pitched death, Map<String, String> emotes) {
        public Voice {
            melody = List.copyOf(melody);
            emotes = Collections.unmodifiableMap(new LinkedHashMap<>(emotes));
        }
    }

    /** A hero's map (section 4.7): which sound belongs to which moment. Missing groups are empty maps. */
    public record HeroMap(String id, Voice voice, Map<String, MoveSounds> moves, Map<String, String> states,
                          Map<String, String> phases, Map<String, String> passives, Map<String, String> weakPoints,
                          Map<String, String> mobility, String intro, Map<String, String> quirks, double stepsVolume,
                          String stepsWith) {
        /** Every event id the map names (voice, hurt and death included), in map order. */
        public List<String> ids() {
            Set<String> out = new LinkedHashSet<>();
            if (voice != null) {
                add(out, voice.talk());
                if (voice.hurt() != null) {
                    add(out, voice.hurt().event());
                }
                if (voice.death() != null) {
                    add(out, voice.death().event());
                }
                voice.emotes().values().forEach(v -> add(out, v));
            }
            for (MoveSounds m : moves.values()) {
                add(out, m.windup());
                add(out, m.release());
                m.impact().forEach(v -> add(out, v));
                add(out, m.loop());
                add(out, m.end());
                add(out, m.broken());
            }
            for (Map<String, String> g : List.of(states, phases, passives, weakPoints, mobility, quirks)) {
                g.values().forEach(v -> add(out, v));
            }
            add(out, intro);
            add(out, stepsWith);
            return List.copyOf(out);
        }

        private static void add(Set<String> s, String v) {
            if (v != null && !v.isEmpty()) {
                s.add(v);
            }
        }
    }

    // ------------------------------------------------------------------ the shared cue keys (the cue map, flattened)

    public static final String TELEGRAPH = "telegraph.", UNBLOCKABLE = "telegraph.unblockable", DIVE = "telegraph.dive";
    public static final String PARRY = "parry", COUNTER = "counter", POISE_CRACK = "poise.crack", POISE_BREAK = "poise.break",
            STAGGER = "stagger", PHASE = "phase", SHOCKWAVE = "shockwave", SUPER_ARMOR = "super_armor", ENRAGE = "enrage",
            INTRO = "intro", FLED = "fled", FALLEN = "fallen", LAST_STAND = "last_stand", REVIVED = "revived",
            ULTIMATE_READY = "ultimate_ready", WEAK_POINT = "weak_point", CHANNEL_BREAK = "channel_break",
            CHARGE_WALL = "charge_wall", DECOY_POP = "decoy_pop", BARRIER_HIT = "barrier.hit", BARRIER_BREAK = "barrier.break",
            TAKEOFF = "takeoff", LANDING = "landing", GLIDE = "glide", DOWNED = "downed", STEP_HEAVY = "step_heavy";

    /** Every cue key the game plays (S2's hooks); a unit test checks the committed map has them all. */
    public static final List<String> CUES = List.of("telegraph.light", "telegraph.heavy", "telegraph.area", "telegraph.ultimate",
            UNBLOCKABLE, DIVE, PARRY, COUNTER, POISE_CRACK, POISE_BREAK, STAGGER, PHASE, SHOCKWAVE, SUPER_ARMOR, ENRAGE,
            INTRO, FLED, FALLEN, LAST_STAND, REVIVED, ULTIMATE_READY, WEAK_POINT, CHANNEL_BREAK, CHARGE_WALL, DECOY_POP,
            BARRIER_HIT, BARRIER_BREAK, TAKEOFF, LANDING, GLIDE, DOWNED, STEP_HEAVY);

    /** Poise passes this share of its maximum: the crack (once per cycle, section 7). */
    public static final double POISE_CRACK_SHARE = 0.75;
    /** Ticks after {@code poise.break} that the stagger's dizzy stars start (the break's impact sits at 0.25 s). */
    public static final int STAGGER_AFTER_BREAK = 6;
    /** Companions nest at most this deep (section 4.1); deeper ones are not played. */
    public static final int MAX_COMPANION_DEPTH = 2;

    public static final SoundIndex EMPTY = new SoundIndex(Map.of(), Map.of(), Map.of());

    private final Map<String, Event> events;
    private final Map<String, String> cues;
    private final Map<String, HeroMap> heroes;

    private SoundIndex(Map<String, Event> events, Map<String, String> cues, Map<String, HeroMap> heroes) {
        this.events = Collections.unmodifiableMap(events);
        this.cues = Collections.unmodifiableMap(cues);
        this.heroes = Collections.unmodifiableMap(heroes);
    }

    /** Every event by full id ({@code factionclash:hero.<set>.<name>}), in file order. */
    public Map<String, Event> events() {
        return events;
    }

    /** The event with this full id, or null. */
    public Event event(String id) {
        return id == null ? null : events.get(id);
    }

    /** The cue map flattened ({@code telegraph.heavy}, {@code poise.break}, {@code fled}...) to full ids. */
    public Map<String, String> cues() {
        return cues;
    }

    /** The full id of cue {@code key}, or null. */
    public String cue(String key) {
        return cues.get(key);
    }

    public Map<String, HeroMap> heroes() {
        return heroes;
    }

    /** The map of hero {@code id}, or null (a hero with no sound set: the test rig, a batch hero before its sounds). */
    public HeroMap hero(String id) {
        return id == null ? null : heroes.get(id);
    }

    // ------------------------------------------------------------------ reading

    /** Reads the index; a syntax error throws {@link IllegalArgumentException}. */
    public static SoundIndex parse(String json) {
        Map<String, Object> root = JsonTree.object(JsonTree.parse(json));
        Map<String, Event> events = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonTree.object(root.get("events")).entrySet()) {
            events.put(e.getKey(), event(e.getKey(), JsonTree.object(e.getValue())));
        }
        Map<String, String> cues = new LinkedHashMap<>();
        flatten(JsonTree.object(root.get("cues")), "", cues);
        Map<String, HeroMap> heroes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonTree.object(root.get("heroes")).entrySet()) {
            heroes.put(e.getKey(), hero(e.getKey(), JsonTree.object(e.getValue())));
        }
        return new SoundIndex(events, cues, heroes);
    }

    /** A copy with hero {@code id}'s map replaced (a GameTest's own map for its fixture). */
    public SoundIndex withHero(String id, HeroMap map) {
        Map<String, HeroMap> h = new LinkedHashMap<>(heroes);
        if (map == null) {
            h.remove(id);
        } else {
            h.put(id, map);
        }
        return new SoundIndex(new LinkedHashMap<>(events), new LinkedHashMap<>(cues), h);
    }

    private static void flatten(Map<String, Object> m, String prefix, Map<String, String> out) {
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() instanceof Map<?, ?>) {
                flatten(JsonTree.object(e.getValue()), prefix + e.getKey() + ".", out);
            } else if (e.getValue() instanceof String s) {
                out.put(prefix + e.getKey(), s);
            }
        }
    }

    private static Event event(String id, Map<String, Object> o) {
        double[] pitch = range(o.get("pitch"), 1);
        double[] volume = range(o.get("volume"), 1);
        List<Companion> with = new ArrayList<>();
        for (Object c : JsonTree.array(o.get("with"))) {
            Map<String, Object> m = JsonTree.object(c);
            String s = JsonTree.string(m, m.containsKey("event") ? "event" : "sound");
            if (!s.isEmpty()) {
                with.add(new Companion(s, Math.max(0, JsonTree.integer(m, "delay"))));
            }
        }
        Plays plays = "server".equals(JsonTree.string(o, "plays")) ? Plays.SERVER : Plays.CLIENT;
        String cat = JsonTree.string(o, "category");
        return new Event(id, JsonTree.string(o, "set"), JsonTree.string(o, "kind"), plays, cat.isEmpty() ? "hostile" : cat,
                number(o, "range", 16), pitch[0], pitch[1], volume[0], volume[1], JsonTree.string(o, "source"),
                Math.max(1, JsonTree.integer(o, "variants")), number(o, "length", 0), Boolean.TRUE.equals(o.get("loop")), with,
                o.get("hit_at") instanceof Number n ? n.doubleValue() : Double.NaN, JsonTree.string(o, "subtitle"));
    }

    private static double number(Map<String, Object> o, String key, double dflt) {
        return o.get(key) instanceof Number n ? n.doubleValue() : dflt;
    }

    private static double[] range(Object v, double dflt) {
        List<Object> l = JsonTree.array(v);
        if (l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b) {
            double lo = a.doubleValue(), hi = b.doubleValue();
            return lo <= hi ? new double[] {lo, hi} : new double[] {hi, lo};
        }
        if (v instanceof Number n) {
            return new double[] {n.doubleValue(), n.doubleValue()};
        }
        return new double[] {dflt, dflt};
    }

    /** One hero map, as the index writes it under {@code heroes.<id>} (full ids). */
    public static HeroMap hero(String id, Map<String, Object> o) {
        Voice voice = null;
        Map<String, Object> v = JsonTree.object(o.get("voice"));
        if (!v.isEmpty()) {
            List<Double> melody = new ArrayList<>();
            for (Object m : JsonTree.array(v.get("melody"))) {
                if (m instanceof Number n) {
                    melody.add(n.doubleValue());
                }
            }
            voice = new Voice(nullIfEmpty(JsonTree.string(v, "talk")), Math.max(1, JsonTree.integer(v, "rate")),
                    melody.isEmpty() ? List.of(0.0) : melody, number(v, "jitter", 0.04),
                    nullIfEmpty(JsonTree.string(v, "fallback_blip")), pitched(v.get("hurt")), pitched(v.get("death")),
                    strings(v.get("emotes")));
        }
        Map<String, MoveSounds> moves = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonTree.object(o.get("moves")).entrySet()) {
            Map<String, Object> m = JsonTree.object(e.getValue());
            List<String> impact = new ArrayList<>();
            Object im = m.get("impact");
            if (im instanceof String s && !s.isEmpty()) {
                impact.add(s);
            } else {
                for (Object x : JsonTree.array(im)) {
                    if (x instanceof String s && !s.isEmpty()) {
                        impact.add(s);
                    }
                }
            }
            moves.put(e.getKey(), new MoveSounds(nullIfEmpty(JsonTree.string(m, "class")), str(m, "windup"), str(m, "release"),
                    impact, str(m, "loop"), str(m, "end"), str(m, "broken")));
        }
        Map<String, Object> steps = JsonTree.object(o.get("steps"));
        return new HeroMap(id, voice, Collections.unmodifiableMap(moves), strings(o.get("states")), strings(o.get("phases")),
                strings(o.get("passives")), strings(o.get("weak_points")), strings(o.get("mobility")), str(o, "intro"),
                strings(o.get("quirks")), number(steps, "volume", 1.0), str(steps, "with"));
    }

    private static String str(Map<String, Object> o, String key) {
        return o.get(key) instanceof String s && !s.isEmpty() ? s : null;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static Map<String, String> strings(Object v) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : JsonTree.object(v).entrySet()) {
            if (e.getValue() instanceof String s && !s.isEmpty()) {
                out.put(e.getKey(), s);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Pitched pitched(Object v) {
        Map<String, Object> m = JsonTree.object(v);
        String e = JsonTree.string(m, "event");
        return e.isEmpty() ? null : new Pitched(e, number(m, "pitch", 1.0));
    }

    // ------------------------------------------------------------------ the rules of section 7

    /**
     * The class cue a move's wind-up plays (section 7): {@code telegraph.<class>}; a grab's (always unblockable) is
     * {@link #UNBLOCKABLE}, the {@code dive} form's {@link #DIVE}. Null for an unknown class.
     */
    public static String telegraphCue(String cls, boolean grab, boolean dive) {
        if (grab) {
            return UNBLOCKABLE;
        }
        if (dive) {
            return DIVE;
        }
        if (cls == null) {
            return null;
        }
        return switch (cls.toLowerCase(Locale.ROOT)) {
            case "light", "heavy", "area", "ultimate" -> TELEGRAPH + cls.toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    /**
     * Ticks after a wind-up's start that its class cue starts (section 7), so the cue's {@code hit_at} falls on the
     * release: {@code max(0, windup - round(hit_at x 20))}. A cue without {@code hit_at} (NaN: light, unblockable) plays
     * at the start. A 1.3 s heavy wind-up (26 ticks) with the 0.78 s heavy cue: tick 10.
     */
    public static int cueDelay(int windupTicks, double hitAt) {
        if (Double.isNaN(hitAt) || hitAt <= 0) {
            return 0;
        }
        return Math.max(0, windupTicks - (int) Math.round(hitAt * 20));
    }

    /** A uniform pick in [lo, hi] for a draw {@code u} in [0, 1). */
    public static double pick(double lo, double hi, double u) {
        return hi <= lo ? lo : lo + (hi - lo) * Math.max(0, Math.min(1, u));
    }

    /**
     * The map key a passive named {@code name} has among {@code keys} (section 4.7's {@code passives}): its name in
     * lower case with every run of other characters as {@code _} ("Nine Lives": {@code nine_lives}), or a key that
     * starts with that and an underscore (a passive's moment: "Bewitched" at full marks, {@code bewitched_full}).
     * Null when none.
     */
    public static String passiveKey(Set<String> keys, String name) {
        if (name == null || keys.isEmpty()) {
            return null;
        }
        String n = slug(name);
        if (keys.contains(n)) {
            return n;
        }
        for (String k : keys) {
            if (k.startsWith(n + "_")) {
                return k;
            }
        }
        return null;
    }

    /** Lower case, every run of characters other than a-z and 0-9 one {@code _}, trimmed of underscores. */
    public static String slug(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean gap = false;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                if (gap && b.length() > 0) {
                    b.append('_');
                }
                b.append(c);
                gap = false;
            } else {
                gap = true;
            }
        }
        return b.toString();
    }

    /**
     * Whether poise rising from {@code before} to {@code after} of {@code max} passes the crack line (75%) this time:
     * the crack sounds once per cycle (the bar empties at a stagger or a new fight).
     */
    public static boolean poiseCracks(double before, double after, double max) {
        if (!(max > 0)) {
            return false;
        }
        double line = POISE_CRACK_SHARE * max;
        return before < line && after >= line;
    }

    /**
     * The move an action code plays (section 7's {@code loop} slot runs while it does): {@code move.<slot>} names the
     * {@code slot}th move of the hero's visual move list; the ultimate codes the map's move of class {@code ultimate}.
     * Null for any other code or an unknown move.
     */
    public static MoveSounds moveOfCode(HeroMap map, List<String> visualMoves, int code) {
        if (map == null) {
            return null;
        }
        if (code >= HeroActions.FIRST_MOVE && code < HeroActions.FIRST_MOVE + HeroActions.MOVE_SLOTS) {
            int slot = code - HeroActions.FIRST_MOVE;
            return visualMoves != null && slot < visualMoves.size() ? map.moves().get(visualMoves.get(slot)) : null;
        }
        if (code == HeroActions.ULTIMATE || code == HeroActions.ULTIMATE_AWAKENED) {
            for (MoveSounds m : map.moves().values()) {
                if ("ultimate".equals(m.cls())) {
                    return m;
                }
            }
        }
        return null;
    }

    /** The vanilla note block event of a v1 voice blip ({@code xylophone}: {@code minecraft:block.note_block.xylophone}). */
    public static String noteBlock(String blip) {
        return "minecraft:block.note_block." + (blip == null || blip.isBlank() ? "harp" : blip.toLowerCase(Locale.ROOT));
    }
}
