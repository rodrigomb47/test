package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A server timing file ({@code data/factionclash/hero_timings/<file>.json}, format 2, HEROES_V2 4.8): for each animation
 * of the matching animation file its kind (base or action), loop mode, length, the controller transition it was
 * written for, its total ticks from the action's start, its events (markers, sounds, effects) by tick, and for an
 * attack its wind-up, active and recovery ticks. The server times actions by it (an action code is cleared when its
 * clip has played); the client's markers are checked against it; {@code HeroTimingsTest} checks that the controllers
 * use exactly the transition lengths the files were written for. Pure.
 */
public record HeroTimings(String file, int format, Map<String, Clip> clips) {

    /** One marker, sound or effect, at its tick from the action's start (the controller blend included). */
    public record Event(int tick, String type, String value) {
    }

    /**
     * One animation. {@code ticks} is the clip's own length, {@code totalTicks} the blend plus the clip;
     * {@code windup}, {@code active}, {@code recovery} are -1 for a clip without hits.
     */
    public record Clip(String name, String kind, String loop, int ticks, int transitionTicks, int totalTicks,
                       List<Event> events, int windup, int active, int recovery) {
        public boolean isAction() {
            return "action".equals(kind);
        }

        public boolean loops() {
            return "loop".equals(loop);
        }

        /** The tick of the first {@code hit} marker, or -1. */
        public int firstHit() {
            for (Event e : events) {
                if ("hit".equals(e.type())) {
                    return e.tick();
                }
            }
            return -1;
        }
    }

    /** Reads a timing file's text; fields it lacks read as -1 or empty (the generator writes them all). */
    public static HeroTimings parse(String file, String json) {
        return parse(file, JsonTree.object(JsonTree.parse(json)));
    }

    /** Reads a parsed timing file ({@link JsonTree}). */
    public static HeroTimings parse(String file, Map<String, Object> o) {
        Map<String, Clip> clips = new LinkedHashMap<>();
        for (Map.Entry<String, Object> a : JsonTree.object(o.get("animations")).entrySet()) {
            if (!(a.getValue() instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> c = JsonTree.object(a.getValue());
            List<Event> events = new ArrayList<>();
            for (Object e : JsonTree.array(c.get("events"))) {
                Map<String, Object> eo = JsonTree.object(e);
                events.add(new Event(JsonTree.integer(eo, "tick"), JsonTree.string(eo, "type"), JsonTree.string(eo, "value")));
            }
            Map<String, Object> ft = JsonTree.object(c.get("frame_ticks"));
            clips.put(a.getKey(), new Clip(a.getKey(), JsonTree.string(c, "kind"), JsonTree.string(c, "loop"),
                    JsonTree.integer(c, "ticks"), JsonTree.integer(c, "transition_ticks"), JsonTree.integer(c, "total_ticks"),
                    List.copyOf(events), JsonTree.integer(ft, "windup"), JsonTree.integer(ft, "active"), JsonTree.integer(ft, "recovery")));
        }
        return new HeroTimings(file, JsonTree.integer(o, "format"), java.util.Collections.unmodifiableMap(clips));
    }

    /**
     * How many game ticks an action lasts at playback speed {@code speed} (GeckoLib scales the whole controller clock,
     * the blend included, so every tick of the file divides by the speed).
     */
    public static int ticksAt(int totalTicks, double speed) {
        double s = Double.isFinite(speed) && speed > 0 ? speed : 1.0;
        return (int) Math.ceil(totalTicks / s - 1e-9);
    }
}
