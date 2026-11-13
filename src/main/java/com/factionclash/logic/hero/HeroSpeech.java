package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.LineEvent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a hero says and who hears it (v1 23, HEROES_V2 12.15; task A1b): the line for an event (the hero's own, or a
 * short generic one when its file has none and {@code heroes.defaultLines} is on), its placeholders filled in, the
 * audience of each event, the rate limit of v1 23 ({@link Clock}), the chat form {@code <★ Name> line}, and the name
 * tag's text ({@code ★ Name · title [Fighting 80%]}, {@code (dormant)}). Nothing here reads whether a listener or an
 * enemy is a player: {@code {enemy}} is a zombie's, an NPC's or a player's name alike. Pure, unit-tested
 * ({@code HeroSpeechTest}).
 */
public final class HeroSpeech {
    /** How long a speech bubble shows above the head, and the face talks (v1 23: 3 s). */
    public static final int BUBBLE_TICKS = 60;
    /** A star marks a hero in chat and on its name tag. */
    public static final String STAR = "★";
    public static final String DORMANT = "(dormant)";

    private HeroSpeech() {
    }

    /** Who hears an event's line (v1 23's table; format 2's events by what they are about). */
    public enum Audience { FACTION, NEARBY, PLAYER }

    public static Audience audience(LineEvent e) {
        return switch (e) {
            case SUMMONED, RAID_START, BASE_ATTACKED, LEVEL_UP, UNLOCK, FALLEN, REVIVED, FOUNDING, RAID_ANNOUNCE,
                 BASE_LOST, HERO_FALLEN, PACT_OFFER, PACT_ACCEPTED, PACT_REFUSED, PEACE_OFFER, DECLARE_WAR, BETRAYAL,
                 BETRAYED -> Audience.FACTION;
            case GREETING, WELCOME, GIFT_LIKED, GIFT_DISLIKED -> Audience.PLAYER;
            default -> Audience.NEARBY;
        };
    }

    /**
     * Short generic lines for the events a hero's file leaves out ({@code heroes.defaultLines}, v1 23). Each uses
     * only the placeholders its event allows; events about diplomacy and gifts have none (a hero without its own line
     * says nothing there).
     */
    public static final Map<LineEvent, List<String>> DEFAULT_LINES;

    static {
        Map<LineEvent, List<String>> m = new EnumMap<>(LineEvent.class);
        m.put(LineEvent.SUMMONED, List.of("{name} stands with the {faction}.", "The {faction} can count on me."));
        m.put(LineEvent.GREETING, List.of("Hello, {player}.", "Yes, {player}?"));
        m.put(LineEvent.IDLE, List.of("All quiet here.", "A calm day, {player}."));
        m.put(LineEvent.FIGHT_START, List.of("Here it comes!", "Face me, {enemy}!"));
        m.put(LineEvent.SKILL, List.of("Take this!"));
        m.put(LineEvent.ULTIMATE, List.of("Now!"));
        m.put(LineEvent.LOW_HEALTH, List.of("I'm hurt...", "Not like this..."));
        m.put(LineEvent.KILL, List.of("One less.", "{enemy} is down."));
        m.put(LineEvent.ALLY_FALLEN, List.of("{ally}... no!"));
        m.put(LineEvent.VICTORY, List.of("It's over.", "That's settled."));
        m.put(LineEvent.RAID_START, List.of("We march!"));
        m.put(LineEvent.BASE_ATTACKED, List.of("To the walls!"));
        m.put(LineEvent.LEVEL_UP, List.of("I grow stronger."));
        m.put(LineEvent.UNLOCK, List.of("Something new: {ability}."));
        m.put(LineEvent.FALLEN, List.of("Not... yet..."));
        m.put(LineEvent.REVIVED, List.of("I'm back.", "{name} returns."));
        m.put(LineEvent.INTRO, List.of("So you came, {enemy}."));
        m.put(LineEvent.PHASE_2, List.of("Enough playing."));
        m.put(LineEvent.PHASE_3, List.of("Now I'm serious!"));
        m.put(LineEvent.ENRAGE, List.of("You'll regret this!"));
        m.put(LineEvent.STAGGER, List.of("Ngh!"));
        m.put(LineEvent.COUNTER, List.of("Too slow!"));
        m.put(LineEvent.ESCAPE, List.of("Not today!"));
        m.put(LineEvent.LAST_STAND, List.of("I will not run."));
        DEFAULT_LINES = Collections.unmodifiableMap(m);
    }

    /**
     * The line for an event before its placeholders are filled: one of the hero's own (at {@code roll}, 0 to 1), or
     * a generic one with {@code defaults} on when its file has none for the event; null when there is none at all.
     * {@code def} null is a dormant hero (generic lines only).
     */
    public static String pick(HeroDef def, LineEvent e, boolean defaults, double roll) {
        List<String> own = def == null ? List.of() : def.lines().getOrDefault(e, List.of());
        List<String> lines = !own.isEmpty() ? own : defaults ? DEFAULT_LINES.getOrDefault(e, List.of()) : List.of();
        if (lines.isEmpty()) {
            return null;
        }
        int i = (int) Math.floor(Math.max(0, Math.min(0.999999, roll)) * lines.size());
        return lines.get(i);
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    /** Fills {@code {name}}-style placeholders; one with no value reads "someone" (as the reader does, v1 23). */
    public static String fill(String line, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null || v.isBlank() ? "someone" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** The chat form of a line: {@code <★ Bram Ostler> Eyes on me!}. */
    public static String chatLine(String name, String line) {
        return "<" + STAR + " " + name + "> " + line;
    }

    // ------------------------------------------------------------------ name tag (v1 25.8, 14)

    /** {@code ★ Bram Ostler · the Gatewarden} (no title: just the star and the name). */
    public static String tagName(String name, String title) {
        return STAR + " " + name + (title == null || title.isBlank() ? "" : " · " + title);
    }

    /** {@code [Fighting 80%]}: the energy while it fights (v1 14, {@code heroes.showEnergy}); {@code [Fighting]} without. */
    public static String fightTag(int energy, boolean showEnergy) {
        int e = Math.max(0, Math.min(HeroRules.ENERGY_MAX, energy));
        return showEnergy ? "[Fighting " + e + "%]" : "[Fighting]";
    }

    // ------------------------------------------------------------------ the rate limit (v1 23)

    /**
     * One hero's dialogue clock: the last line, greeting and idle line (game ticks; 0 never). {@code summoned},
     * {@code fallen}, {@code revived} and {@code ultimate} are said whatever the clock, {@code greeting} 5 s after the
     * last greeting, {@code idle} at most every 15 minutes, every other line one per {@code heroes.dialogueCooldown}.
     */
    public static final class Clock {
        private long lastLine, lastGreeting, lastIdle;

        public boolean allows(LineEvent e, long now, double cooldownSeconds) {
            return HeroRules.maySpeak(e, now, lastLine, lastGreeting, lastIdle, cooldownSeconds);
        }

        /** A line of {@code e} was said at {@code now} (a greeting only moves its own clock). */
        public void said(LineEvent e, long now) {
            long t = Math.max(1, now);
            if (e == LineEvent.GREETING) {
                lastGreeting = t;
                return;
            }
            if (e == LineEvent.IDLE) {
                lastIdle = t;
            }
            lastLine = t;
        }

        public long lastLine() {
            return lastLine;
        }
    }
}
