package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the hero checker reports (v1 26.2-26.4): each {@link Problem} has an outcome, a machine code, the file,
 * line and column, the field path spelled out for non-programmers ({@code skill 1 "Shield Slam" > effect 2 > amount})
 * and for the log ({@code skills[0].effects[1].amount}), and a message that says what is wrong, what is allowed and
 * what the mod did. Pure Java, unit-tested.
 */
public final class Problems {

    /** The four outcomes, worst first (v1 26.2). */
    public enum Severity {
        NOT_LOADED("NOT LOADED"), PART_LEFT_OUT("PART LEFT OUT"), ADJUSTED("ADJUSTED"), NOTE("NOTE");

        public final String label;

        Severity(String label) {
            this.label = label;
        }
    }

    /**
     * Every kind of message the reader and the balance pass write (the JSON reader's own are {@link HeroJson.Code}).
     * Each has at least one unit test that produces it ({@code HeroMessagesTest} checks the list).
     */
    public enum Code {
        // files and the hero's core
        FILE_ID, FILE_BAD_NAME, NOT_A_HERO, LIST_HERO_NO_ID, FORMAT_MISSING, FORMAT_NEWER, FORMAT_INVALID, ID_MISSING,
        ID_MISMATCH, ID_INVALID, MISSING, TOO_MANY_PROBLEMS,
        // fields and values
        UNKNOWN_FIELD, MISSPELLED_FIELD, FIELD_ELSEWHERE, FIELD_UNUSED, NULL_FIELD, WRONG_TYPE, TEXT_NUMBER, TEXT_BOOL,
        CLAMPED, ROUNDED, SYNONYM, EQUAL_TREATMENT, WORD_DEFAULT, WORD_PART, WORD_CORE, TEXT_CLEANED, TEXT_CUT,
        MARK_RENAMED,
        MARK_INVALID, COLOR_INVALID, LIST_TOO_LONG, LIST_WRAPPED, LIST_DUPLICATE, CONDITION_INCOMPLETE, AMOUNT_MARK,
        // sense rules (v1 26.1 step 3)
        NO_EFFECTS, ENEMY_ONLY, ALLY_TO_SELF, SELF_ONLY_TO, MARK_RECEIVER, TWO_DAMAGE, ONE_MOVE, MOVE_IN_LEAP,
        EMPOWER_NO_STATE, SUMMON_SLOT, SUMMONS_OFF, ENERGY_ON_ULTIMATE, SELF_POTION, POTION_REFUSED, STAT_ZERO,
        STUN_ON_HIT, ALWAYS_STAT, TRIGGER_FORM, MARK_REQUIRED, MARK_NOT_OWN, OUT_OF_COMBAT, LIGHTNING_ULTIMATE,
        FANGS_FORM, ROLE_STYLE, SIZE_CAP, AURA_AFFIX,
        // gear
        GEAR_REFUSED, GEAR_TOO_GOOD, GEAR_SLOT, GEAR_ADDED, GEAR_STYLE, ENCHANT_REFUSED, ENCHANT_ITEM,
        ENCHANT_EXCLUSIVE, ENCHANT_KIT,
        // lines
        LINE_EVENT, LINE_PLACEHOLDER, LINE_SOMEONE, LINE_BREAK,
        // balance (v1 26.1 step 4)
        SLOT_CAP, SHARED_ON_HIT, COOLDOWN_FLOOR, PASSIVE_FLOOR, EVERY_FLOOR, THROWN_RATE, POTION_LEVEL,
        POTION_SECONDS, STAT_CAP, SUMMON_COUNT,
        // across files (v1 26.1 step 6)
        DUPLICATE_ID, STARTER_CONFLICT, MARK_UNUSED, TOO_MANY_HEROES;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One reported point. {@code part} names what PART LEFT OUT skipped ("Effect", "Skill", "Line"...), null
     * otherwise; {@code hero} is the hero id when known.
     */
    public record Problem(Severity severity, String code, String hero, String file, int line, int column,
                          String path, String machinePath, String message, String part) {
        /** The chat and report line of v1 26.3: {@code <file>, line <L>: <path>: <message>. <OUTCOME>.} */
        public String format() {
            StringBuilder sb = new StringBuilder(file);
            if (line > 0) {
                sb.append(", line ").append(line);
                if (column > 0) {
                    sb.append(", column ").append(column);
                }
            }
            sb.append(": ");
            if (path != null && !path.isEmpty()) {
                sb.append(path).append(": ");
            }
            sb.append(message);
            if (!message.endsWith(".") && !message.endsWith("?") && !message.endsWith("!")) {
                sb.append('.');
            }
            sb.append(' ');
            if (part != null && severity == Severity.PART_LEFT_OUT) {
                sb.append(part).append(' ');
            }
            return sb.append(severity.label).append('.').toString();
        }

        /** The log line: the machine path and the code in front. */
        public String log() {
            return "[Heroes] " + severity.label + " " + code + " " + file + (line > 0 ? ":" + line : "")
                    + (machinePath == null || machinePath.isEmpty() ? "" : " " + machinePath) + " - " + message;
        }
    }

    /**
     * A position in a hero file: the friendly spelling ({@code skill 1 "Shield Slam" > effect 2}) and the machine one
     * ({@code skills[0].effects[1]}), immutable.
     */
    public record Path(String friendly, String machine) {
        public static final Path ROOT = new Path("", "");

        /** An object field. */
        public Path field(String name) {
            return new Path(join(friendly, name), machine.isEmpty() ? name : machine + "." + name);
        }

        /**
         * A list item: {@code noun} and 1-based number, with a quoted name when known; {@code listShown} false leaves
         * the list's own name out of the friendly path (effects, skills, passives, gear).
         */
        public Path item(String list, int index, String noun, String name, boolean listShown) {
            String f = noun + " " + (index + 1) + (name == null || name.isEmpty() ? "" : " \"" + name + "\"");
            String base = listShown ? join(friendly, list) : friendly;
            String m = (machine.isEmpty() ? list : machine + "." + list) + "[" + index + "]";
            return new Path(join(base, f), m);
        }

        /** A single named object such as the ultimate: {@code ultimate "Hold the Gate"}. */
        public Path named(String field, String name) {
            String f = field + (name == null || name.isEmpty() ? "" : " \"" + name + "\"");
            return new Path(join(friendly, f), machine.isEmpty() ? field : machine + "." + field);
        }

        private static String join(String a, String b) {
            return a.isEmpty() ? b : a + " > " + b;
        }
    }

    private final List<Problem> list = new ArrayList<>();

    public void add(Problem p) {
        list.add(p);
    }

    public void addAll(Problems other) {
        list.addAll(other.list);
    }

    public List<Problem> all() {
        return Collections.unmodifiableList(list);
    }

    public int count(Severity s) {
        int n = 0;
        for (Problem p : list) {
            if (p.severity == s) {
                n++;
            }
        }
        return n;
    }

    public boolean has(String code) {
        for (Problem p : list) {
            if (p.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** The problems of one hero (or one file when the hero is unknown). */
    public List<Problem> of(String hero) {
        List<Problem> r = new ArrayList<>();
        for (Problem p : list) {
            if (hero.equals(p.hero)) {
                r.add(p);
            }
        }
        return r;
    }

    /** The worst outcome reported for a hero, or null when it has none. */
    public Severity worst(String hero) {
        Severity w = null;
        for (Problem p : of(hero)) {
            if (w == null || p.severity.ordinal() < w.ordinal()) {
                w = p.severity;
            }
        }
        return w;
    }

    /**
     * The report of v1 26.4 and 30 ({@code _report.txt}, {@code /fc hero check}): per file a header, then its lines,
     * NOT LOADED first; files in alphabetical order.
     */
    public String report() {
        Map<String, List<Problem>> byFile = new LinkedHashMap<>();
        List<Problem> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing((Problem p) -> p.file).thenComparing(p -> p.severity.ordinal())
                .thenComparingInt(p -> p.line));
        for (Problem p : sorted) {
            byFile.computeIfAbsent(p.file, k -> new ArrayList<>()).add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Problem>> e : byFile.entrySet()) {
            sb.append("== ").append(e.getKey()).append(": ").append(e.getValue().size())
                    .append(e.getValue().size() == 1 ? " problem" : " problems").append('\n');
            for (Problem p : e.getValue()) {
                sb.append(p.format()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * The chat summary after a load (v1 26.4): "Heroes: 6 loaded (2 adjusted), 1 with parts left out, 1 not loaded".
     * A hero with parts left out is still loaded and counted in both.
     */
    public String summary(Collection<String> loaded, Collection<String> notLoaded) {
        int adjusted = 0, partial = 0;
        for (String id : loaded) {
            Severity w = worst(id);
            if (w == Severity.PART_LEFT_OUT) {
                partial++;
            } else if (w == Severity.ADJUSTED) {
                adjusted++;
            }
        }
        StringBuilder sb = new StringBuilder("Heroes: ").append(loaded.size()).append(" loaded");
        if (adjusted > 0) {
            sb.append(" (").append(adjusted).append(" adjusted)");
        }
        if (partial > 0) {
            sb.append(", ").append(partial).append(" with parts left out");
        }
        if (!notLoaded.isEmpty()) {
            sb.append(", ").append(notLoaded.size()).append(" not loaded");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ did you mean (v1 26.3)

    /**
     * The closest of {@code options} to {@code word}: edit distance at most 2 (3 for words longer than 10), ties to
     * the first; null when none is that close.
     */
    public static String closest(String word, Collection<String> options) {
        int limit = word.length() > 10 ? 3 : 2;
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String o : options) {
            int d = distance(word, o);
            if (d <= limit && d < bestD) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** {@code " Did you mean "fire"?"} or an empty string. */
    public static String didYouMean(String word, Collection<String> options) {
        String c = closest(word, options);
        return c == null ? "" : " Did you mean \"" + c + "\"?";
    }

    /** Edit distance with adjacent transpositions (optimal string alignment): "cooldwon" to "cooldown" is 1. */
    public static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }

    /** A number for messages: whole numbers without a point, others with at most two decimals ("6.4", "2.5"). */
    public static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** "1 second", "2.5 seconds". */
    public static String seconds(double v) {
        return num(v) + (Math.abs(v - 1) < 1e-9 ? " second" : " seconds");
    }

    /**
     * One decimal, for computed numbers such as weights and floors ("6.4"), rounded half to even on the exact binary
     * value as the reference checker (Python) prints them: a 3.25 s floor reads "3.2".
     */
    public static String num1(double v) {
        return num(new java.math.BigDecimal(v).setScale(1, java.math.RoundingMode.HALF_EVEN).doubleValue());
    }
}
