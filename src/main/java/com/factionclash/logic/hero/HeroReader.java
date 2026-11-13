package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.Field;
import com.factionclash.logic.hero.HeroCatalogue.FormSpec;
import com.factionclash.logic.hero.HeroCatalogue.GearKind;
import com.factionclash.logic.hero.HeroCatalogue.Reach;
import com.factionclash.logic.hero.HeroCatalogue.RolePreset;
import com.factionclash.logic.hero.HeroCatalogue.TriggerSpec;
import com.factionclash.logic.hero.HeroCatalogue.Unit;
import com.factionclash.logic.hero.HeroCatalogue.ValueRange;
import com.factionclash.logic.hero.HeroCatalogue.Vocab;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.BaseAbility;
import com.factionclash.logic.hero.HeroDef.BaseType;
import com.factionclash.logic.hero.HeroDef.BasicAttack;
import com.factionclash.logic.hero.HeroDef.Behaviour;
import com.factionclash.logic.hero.HeroDef.Common;
import com.factionclash.logic.hero.HeroDef.Condition;
import com.factionclash.logic.hero.HeroDef.ConditionType;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.EffectType;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Empowered;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.GearItem;
import com.factionclash.logic.hero.HeroDef.GearSlot;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Look;
import com.factionclash.logic.hero.HeroDef.NthHit;
import com.factionclash.logic.hero.HeroDef.Numbers;
import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.PassiveForm;
import com.factionclash.logic.hero.HeroDef.Pose;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroDef.StatBonus;
import com.factionclash.logic.hero.HeroDef.Stats;
import com.factionclash.logic.hero.HeroDef.Style;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroDef.UseWhen;
import com.factionclash.logic.hero.HeroDef.ZoneAt;
import com.factionclash.logic.hero.HeroJson.Arr;
import com.factionclash.logic.hero.HeroJson.Member;
import com.factionclash.logic.hero.HeroJson.Node;
import com.factionclash.logic.hero.HeroJson.Num;
import com.factionclash.logic.hero.HeroJson.Obj;
import com.factionclash.logic.hero.HeroJson.Str;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Path;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.factionclash.logic.hero.Problems.num;

/**
 * Hero files to {@link HeroDef}s (v1 26.1 steps 2, 3, 4 and 6): reads each field as {@link HeroCatalogue} describes it
 * (forgiving: text for numbers, synonyms, misspelled field names, clamped ranges; v1 3.1), applies the sense rules of
 * v1 26.1 step 3 (recipient classes, forms and their fields, trigger and form pairs, placeholders, gear slots and
 * enchantments...), runs {@link HeroBalance}, and finally checks across files (duplicate ids, the hero limit, starter
 * conflicts, marks nobody applies). Every problem carries the file, line, field path and a plain message
 * ({@link Problems}). Pure Java, unit-tested ({@code HeroReaderTest}, {@code HeroMessagesTest}).
 */
public final class HeroReader {
    private HeroReader() {
    }

    /**
     * Server settings the checker needs: {@code heroes.summons} and {@code heroes.maxLoaded}; {@code builtin} when the
     * files are the mod's own heroes (format 2 then asks for the fields only built-ins must have: gender, archetype,
     * personality and leader, HEROES_V2 12.3; an operator's override need not repeat them).
     */
    public record Options(boolean summons, int maxLoaded, boolean builtin) {
        public static final Options DEFAULT = new Options(true, HeroCatalogue.MAX_LOADED, false);
        public static final Options BUILTIN = new Options(true, HeroCatalogue.MAX_LOADED, true);

        public Options(boolean summons, int maxLoaded) {
            this(summons, maxLoaded, false);
        }
    }

    /**
     * A file: its path (relative, used for messages and the alphabetical order) and its text. An operator's override
     * merged over a built-in hero ({@link HeroOverride}) comes as a ready {@code tree} instead, whose top-level members
     * came from two files: {@code memberFiles} names the file of each member the override replaced, so a problem in
     * one of them names the override file and its own line (the others name {@code path}).
     */
    public record Source(String path, String text, Obj tree, Map<String, String> memberFiles) {
        public Source {
            memberFiles = memberFiles == null ? Map.of() : Map.copyOf(memberFiles);
        }

        public Source(String path, String text) {
            this(path, text, null, Map.of());
        }

        /** The file a problem at {@code machinePath} belongs to: the override's for a member it replaced. */
        public String fileOf(String machinePath) {
            if (memberFiles.isEmpty() || machinePath == null || machinePath.isEmpty()) {
                return path;
            }
            int end = machinePath.length();
            for (int i = 0; i < machinePath.length(); i++) {
                char c = machinePath.charAt(i);
                if (c == '.' || c == '[') {
                    end = i;
                    break;
                }
            }
            return memberFiles.getOrDefault(machinePath.substring(0, end), path);
        }
    }

    /** The heroes that loaded (by id, sorted), the file each came from, the ids or files that did not, and every problem. */
    public record Loaded(Map<String, HeroDef> heroes, Map<String, String> files, List<String> notLoaded,
                         Problems problems) {
        public HeroDef hero(String id) {
            return heroes.get(id);
        }
    }

    /** One file on its own (the across-files checks still run on its heroes). */
    public static Loaded read(String path, String text) {
        return readAll(List.of(new Source(path, text)), Options.DEFAULT);
    }

    public static Loaded read(String path, String text, Options o) {
        return readAll(List.of(new Source(path, text)), o);
    }

    // ------------------------------------------------------------------ files

    record MarkRef(String mark, Path path, int line) {
    }

    /** One hero read from a file, with what the checks across files need ({@code across}: format 2's). */
    record Read(HeroDef def, String file, Set<String> applied, List<MarkRef> refs, HeroReader2.Across across) {
    }

    /** Every file, in alphabetical path order, then the checks across files (v1 26.1 step 6). */
    public static Loaded readAll(List<Source> sources, Options opt) {
        Problems problems = new Problems();
        List<Source> sorted = new ArrayList<>(sources);
        sorted.sort(Comparator.comparing(Source::path));
        List<Read> reads = new ArrayList<>();
        List<String> notLoaded = new ArrayList<>();
        for (Source s : sorted) {
            if (s.memberFiles().isEmpty()) {
                readFile(s, opt, problems, reads, notLoaded);
                continue;
            }
            // an override merged over a built-in: each problem names the file its member came from
            Problems own = new Problems();
            readFile(s, opt, own, reads, notLoaded);
            for (Problem p : own.all()) {
                String file = s.fileOf(p.machinePath());
                problems.add(file.equals(p.file()) ? p : new Problem(p.severity(), p.code(), p.hero(), file, p.line(),
                        p.column(), p.path(), p.machinePath(), p.message(), p.part()));
            }
        }
        Map<String, Read> loaded = new LinkedHashMap<>();
        for (Read r : reads) {
            Read first = loaded.get(r.def.id());
            if (first != null) {
                problems.add(new Problem(Severity.NOT_LOADED, Code.DUPLICATE_ID.id(), r.def.id(), r.file, 0, 0, "",
                        "", "the id \"" + r.def.id() + "\" is already used by " + first.file
                        + " (the first file in alphabetical order loads); this hero was not loaded", null));
                notLoaded.add(r.def.id());
            } else if (loaded.size() >= opt.maxLoaded) {
                problems.add(new Problem(Severity.NOT_LOADED, Code.TOO_MANY_HEROES.id(), r.def.id(), r.file, 0, 0,
                        "", "", "the server loads at most " + opt.maxLoaded + " heroes (heroes.maxLoaded); \""
                        + r.def.id() + "\" comes after them in alphabetical order and was not loaded", null));
                notLoaded.add(r.def.id());
            } else {
                loaded.put(r.def.id(), r);
            }
        }
        Map<String, List<Read>> starters = new TreeMap<>();
        Set<String> applied = new LinkedHashSet<>();
        for (Read r : loaded.values()) {
            if (r.def.starterFor() != null) {
                starters.computeIfAbsent(r.def.starterFor(), k -> new ArrayList<>()).add(r);
            }
            applied.addAll(r.applied);
        }
        for (Map.Entry<String, List<Read>> e : starters.entrySet()) {
            List<Read> l = e.getValue();
            l.sort(Comparator.comparing(r -> r.def.id()));
            for (int i = 1; i < l.size(); i++) {
                Read r = l.get(i);
                problems.add(new Problem(Severity.NOTE, Code.STARTER_CONFLICT.id(), r.def.id(), r.file, 0, 0,
                        "starter_for", "starter_for", "\"" + e.getKey() + "\" already has a starter, \""
                        + l.get(0).def.id() + "\" (the first by id); this hero is not its starter", null));
            }
        }
        for (Read r : loaded.values()) {
            Set<String> told = new LinkedHashSet<>();
            for (MarkRef m : r.refs) {
                if (!applied.contains(m.mark) && told.add(m.mark)) {
                    problems.add(new Problem(Severity.NOTE, Code.MARK_UNUSED.id(), r.def.id(), r.file, m.line, 0,
                            m.path.friendly(), m.path.machine(), "the mark \"" + m.mark + "\" is never applied by"
                            + " any loaded hero, so this never finds a stack", null));
                }
            }
        }
        HeroReader2.acrossFiles(loaded.values(), problems, opt);
        Map<String, HeroDef> heroes = new TreeMap<>();
        Map<String, String> files = new TreeMap<>();
        for (Read r : loaded.values()) {
            heroes.put(r.def.id(), r.def);
            files.put(r.def.id(), r.file);
        }
        return new Loaded(Collections.unmodifiableMap(heroes), Collections.unmodifiableMap(files),
                Collections.unmodifiableList(notLoaded), problems);
    }

    /**
     * The hero id a file name gives (v1 2.1): the name without folders and {@code .json}, lower case, spaces and
     * hyphens as {@code _}, other characters dropped.
     */
    public static String idFromFileName(String path) {
        return normaliseId(stem(path));
    }

    /** An id as written, made regular: lower case, spaces and hyphens as {@code _}, other characters dropped. */
    static String normaliseId(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** {@code t} cut to at most {@code max} characters, never through the middle of a surrogate pair. */
    static String cut(String t, int max) {
        if (t.length() <= max) {
            return t;
        }
        int end = max > 0 && Character.isHighSurrogate(t.charAt(max - 1)) ? max - 1 : max;
        return t.substring(0, end).trim();
    }

    /** A hero id (v1 3): 3-32 of {@code a-z 0-9 _}, starting with a letter. */
    public static boolean validId(String id) {
        return id != null && id.length() >= HeroCatalogue.ID_MIN && id.length() <= HeroCatalogue.ID_MAX
                && id.matches("[a-z][a-z0-9_]*");
    }

    private static String stem(String path) {
        String n = path.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.toLowerCase(Locale.ROOT).endsWith(".json") ? n.substring(0, n.length() - 5) : n;
    }

    private static void readFile(Source s, Options opt, Problems problems, List<Read> reads, List<String> notLoaded) {
        String file = s.path;
        String fileId = idFromFileName(file);
        HeroJson.Result res = s.tree != null ? new HeroJson.Result(s.tree, List.of(), null) : HeroJson.parse(s.text);
        for (HeroJson.Issue n : res.notes()) {
            problems.add(new Problem(Severity.NOTE, "json." + n.code().name().toLowerCase(Locale.ROOT), null, file,
                    n.line(), n.col(), "", "", n.message(), null));
        }
        if (!res.ok()) {
            HeroJson.Issue e = res.error();
            problems.add(new Problem(Severity.NOT_LOADED, "json." + e.code().name().toLowerCase(Locale.ROOT), fileId,
                    file, e.line(), e.col(), "", "", e.message(), null));
            notLoaded.add(fileId.isEmpty() ? file : fileId);
            return;
        }
        if (res.root() instanceof Obj o) {
            if (!validId(fileId)) {
                problems.add(new Problem(Severity.NOT_LOADED, Code.FILE_BAD_NAME.id(), null, file, 0, 0, "", "",
                        "the file name gives no hero id (\"" + fileId + "\"). A hero id is 3 to 32 lowercase letters,"
                        + " digits or _, starting with a letter: rename the file, for example bram_ostler.json", null));
                notLoaded.add(file);
                return;
            }
            if (!fileId.equals(stem(file))) {
                problems.add(new Problem(Severity.ADJUSTED, Code.FILE_ID.id(), fileId, file, 0, 0, "", "",
                        "the file name was read as the id \"" + fileId + "\"", null));
            }
            Ctx c = new Ctx(file, opt, problems);
            Read r = c.hero(o, fileId);
            if (r != null) {
                reads.add(r);
            } else {
                notLoaded.add(c.hero == null ? fileId : c.hero);
            }
        } else if (res.root() instanceof Arr a) {
            for (int i = 0; i < a.items().size(); i++) {
                Node n = a.items().get(i);
                if (!(n instanceof Obj o)) {
                    problems.add(new Problem(Severity.NOT_LOADED, Code.NOT_A_HERO.id(), null, file, n.line(), 0,
                            "hero " + (i + 1), "[" + i + "]", "item " + (i + 1) + " of the list is " + n.what()
                            + ", not a hero object { ... }", null));
                    notLoaded.add(file + "[" + i + "]");
                    continue;
                }
                Ctx c = new Ctx(file, opt, problems);
                Read r = c.hero(o, null);
                if (r != null) {
                    reads.add(r);
                } else {
                    notLoaded.add(c.hero == null ? file + "[" + i + "]" : c.hero);
                }
            }
        }
    }

    // ------------------------------------------------------------------ one hero

    /** Where a record came from, for the balance pass's messages; {@code fields} is null for a plain value. */
    record Pos(Path path, int line, Ctx.Fields fields) {
    }

    /** What the reader must do when a required value is missing or unreadable. */
    record Need(Severity severity, String part) {
    }

    enum Mode { OPTIONAL, PART, CORE }

    /**
     * The context an effect list is read in: its slot, owner wording, form (null for the basic attack's lists, which
     * have none) and the rules that follow from them.
     */
    record FxCtx(Slot slot, String owner, String formId, boolean friendly, boolean receiverIsHero,
                 boolean moveForm, boolean always, Trigger trigger) {
    }

    private static final Map<EffectType, Map<String, Field>> EFFECT_SHAPES = new EnumMap<>(EffectType.class);
    private static final Map<String, Field> ALL_EFFECT_FIELDS = new LinkedHashMap<>();
    /** Format 2's: the new types, and the fields format 2 adds to format 1's ({@code empower}'s {@code state}). */
    private static final Map<EffectType, Map<String, Field>> EFFECT_SHAPES_2 = new EnumMap<>(EffectType.class);
    private static final Map<String, Field> ALL_EFFECT_FIELDS_2 = new LinkedHashMap<>();

    static {
        for (EffectType t : EffectType.values()) {
            Map<String, Field> m = new LinkedHashMap<>(HeroCatalogue.EFFECT_COMMON);
            m.putAll(HeroCatalogue.EFFECTS.get(t));
            if (t.since() == 1) {
                EFFECT_SHAPES.put(t, m);
                ALL_EFFECT_FIELDS.putAll(m);
            }
            Map<String, Field> m2 = new LinkedHashMap<>(m);
            m2.putAll(HeroCatalogue.EFFECTS_2.getOrDefault(t, Map.of()));
            m2.put("if", new Field("if", HeroCatalogue.Kind.CONDITION, Unit.NONE, 0, 0, Double.NaN, null, false));
            EFFECT_SHAPES_2.put(t, m2);
            ALL_EFFECT_FIELDS_2.putAll(m2);
        }
    }

    /** A placeholder in a line; letters in any case ({@code {Enemy}} is read as {@code {enemy}}). */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_]+)}");
    private static final Pattern NUMBER_TEXT = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([a-z%°_ ]*)");

    static final class Ctx {
        final String file;
        final Options opt;
        final Problems problems;
        final IdentityHashMap<Object, Pos> where = new IdentityHashMap<>();
        final Set<String> applied = new LinkedHashSet<>();
        final List<MarkRef> refs = new ArrayList<>();
        String hero;
        boolean dead;
        /** The hero's format as peeked before it is checked (1 or 2): it picks the field tables and word lists. */
        int format = 1;
        /** Format 2's reader for this hero (null for format 1). */
        HeroReader2 v2Reader;
        Element element = Element.PHYSICAL;
        boolean hasEmpowered;
        /** Problems of this hero listed so far, and those past the limit with the worst outcome among them. */
        int listed, unlisted;
        Severity worstUnlisted;

        Ctx(String file, Options opt, Problems problems) {
            this.file = file;
            this.opt = opt;
            this.problems = problems;
        }

        /**
         * Adds a problem of this hero. A file cannot flood the log or the chat (v1 26.5): past
         * {@link HeroCatalogue#MAX_PROBLEMS_PER_HERO} only NOT LOADED problems are still listed; the others are counted
         * and told in one line when the hero has been read ({@link #tellUnlisted}).
         */
        void report(Severity s, Code code, int line, Path p, String msg, String part) {
            if (s == Severity.NOT_LOADED) {
                dead = true;
            } else if (listed >= HeroCatalogue.MAX_PROBLEMS_PER_HERO) {
                unlisted++;
                if (worstUnlisted == null || s.ordinal() < worstUnlisted.ordinal()) {
                    worstUnlisted = s;
                }
                return;
            }
            listed++;
            problems.add(new Problem(s, code.id(), hero, file, line, 0, p == null ? "" : p.friendly(),
                    p == null ? "" : p.machine(), msg, part));
        }

        /** One line for the problems past the limit, with the worst outcome among them. */
        void tellUnlisted() {
            if (unlisted > 0) {
                problems.add(new Problem(worstUnlisted, Code.TOO_MANY_PROBLEMS.id(), hero, file, 0, 0, "", "",
                        unlisted + " more " + (unlisted == 1 ? "problem of this hero is" : "problems of this hero are")
                                + " not listed (only the first " + HeroCatalogue.MAX_PROBLEMS_PER_HERO
                                + " are); fix those and check the file again", null));
                unlisted = 0;
            }
        }

        void need(Need n, Code code, int line, Path p, String msg) {
            report(n.severity, code, line, p, msg, n.part);
        }

        // -------------------------------------------------------------- objects and their fields

        /** An object's members resolved against a field table: comments skipped, misspellings read, the rest noted. */
        final class Fields {
            final Obj obj;
            final Path path;
            final Map<String, Member> got = new LinkedHashMap<>();

            Fields(Obj obj, Path path, Map<String, Field> shape, Map<String, String> elsewhere) {
                this.obj = obj;
                this.path = path;
                Set<String> keys = new LinkedHashSet<>();
                for (Member m : obj.members()) {
                    if (!(m.value() instanceof HeroJson.Null)) {
                        keys.add(m.key());
                    }
                }
                for (Member m : obj.members()) {
                    String k = m.key();
                    if (k.startsWith("_") || got.containsKey(k) && k.equals(got.get(k).key())) {
                        continue;
                    }
                    if (nothing(m, path)) {
                        continue;
                    }
                    if (shape.containsKey(k)) {
                        got.put(k, m);
                        continue;
                    }
                    String nk = HeroCatalogue.norm(k);
                    if (shape.containsKey(nk) && !keys.contains(nk) && !got.containsKey(nk)) {
                        report(Severity.ADJUSTED, Code.MISSPELLED_FIELD, m.line(), path, "\"" + k + "\" was read as \""
                                + nk + "\"", null);
                        got.put(nk, m);
                        continue;
                    }
                    if (elsewhere.containsKey(nk)) {
                        report(Severity.NOTE, Code.FIELD_ELSEWHERE, m.line(), path, "\"" + k + "\" "
                                + elsewhere.get(nk) + "; it was ignored", null);
                        continue;
                    }
                    List<String> close = new ArrayList<>();
                    for (String f : shape.keySet()) {
                        if (!keys.contains(f) && !got.containsKey(f)
                                && Problems.distance(nk, f) <= (f.length() <= 4 ? 1 : 2)) {
                            close.add(f);
                        }
                    }
                    if (close.size() == 1) {
                        report(Severity.ADJUSTED, Code.MISSPELLED_FIELD, m.line(), path, "\"" + k + "\" was read as \""
                                + close.get(0) + "\"", null);
                        got.put(close.get(0), m);
                        continue;
                    }
                    String hint = close.size() > 1 ? " Did you mean \"" + String.join("\" or \"", close) + "\"?"
                            : Problems.didYouMean(nk, shape.keySet());
                    report(Severity.NOTE, Code.UNKNOWN_FIELD, m.line(), path, "\"" + k + "\" is not a field here; it"
                            + " was ignored." + hint, null);
                }
            }

            Node node(String name) {
                Member m = got.get(name);
                return m == null ? null : m.value();
            }

            boolean has(String name) {
                return got.containsKey(name);
            }

            int line(String name) {
                Member m = got.get(name);
                return m == null ? obj.line() : m.value().line();
            }

            Path at(String name) {
                return path.field(name);
            }

            /** A NOTE for a field this context does not use; true when the field was there. */
            boolean unused(String name, String why) {
                if (has(name)) {
                    report(Severity.NOTE, Code.FIELD_UNUSED, line(name), at(name), why + "; it was ignored", null);
                    return true;
                }
                return false;
            }
        }

        Fields fields(Obj o, Path p, Map<String, Field> shape) {
            return new Fields(o, p, shape, Map.of());
        }

        /**
         * A field whose value is JSON's {@code null}: "nothing". It is read as if the field were left out (a NOTE), so
         * its default applies, or the usual message when the field is required.
         */
        boolean nothing(Member m, Path owner) {
            if (!(m.value() instanceof HeroJson.Null)) {
                return false;
            }
            report(Severity.NOTE, Code.NULL_FIELD, m.line(), owner, "\"" + m.key() + "\" is null, which means nothing;"
                    + " it was read as left out", null);
            return true;
        }

        // -------------------------------------------------------------- values

        String describe(boolean whole, Unit unit) {
            return switch (unit) {
                case SECONDS -> "a number of seconds";
                case BLOCKS -> "a number of blocks";
                case PERCENT -> "a whole percent";
                case HEALTH -> "a number of health points";
                case DEGREES -> "a number of degrees";
                default -> whole ? "a whole number" : "a number";
            };
        }

        Double parseUnit(String text, Unit unit) {
            Matcher m = NUMBER_TEXT.matcher(text.trim().toLowerCase(Locale.ROOT));
            if (!m.matches()) {
                return null;
            }
            String u = m.group(2).trim();
            boolean ok = u.isEmpty() || switch (unit) {
                case SECONDS -> Set.of("s", "sec", "secs", "second", "seconds").contains(u);
                case PERCENT -> Set.of("%", "percent", "pct").contains(u);
                case BLOCKS -> Set.of("b", "block", "blocks").contains(u);
                case HEALTH -> Set.of("hp", "health", "health points", "points").contains(u);
                case DEGREES -> Set.of("°", "deg", "degree", "degrees").contains(u);
                default -> false;
            };
            return ok ? Double.parseDouble(m.group(1)) : null;
        }

        String shown(Node n) {
            return switch (n) {
                case Str s -> "\"" + s.value() + "\"";
                case Num x -> x.text();
                case HeroJson.Bool b -> String.valueOf(b.value());
                default -> n.what();
            };
        }

        /** A number field: absent gives {@code dflt}; text, decimals and out-of-range values are fixed and reported. */
        double number(Fields f, String name, double min, double max, double dflt, boolean whole, Unit unit, Need need) {
            Node n = f.node(name);
            if (n == null) {
                return dflt;
            }
            Path p = f.at(name);
            double v;
            if (n instanceof Num x) {
                v = x.value();
            } else if (n instanceof Str s && parseUnit(s.value(), unit) != null) {
                v = parseUnit(s.value(), unit);
                report(Severity.ADJUSTED, Code.TEXT_NUMBER, n.line(), p, "\"" + s.value() + "\" was read as " + num(v),
                        null);
            } else {
                String msg = "should be " + describe(whole, unit) + ", not " + shown(n);
                if (need == null && !Double.isNaN(dflt)) {
                    report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, msg + ". Used the default, " + num(dflt),
                            null);
                    return dflt;
                }
                need(need == null ? new Need(Severity.ADJUSTED, null) : need, Code.WRONG_TYPE, n.line(), p,
                        need == null ? msg + ". It was ignored" : msg);
                return Double.NaN;
            }
            if (whole && Math.abs(v - Math.rint(v)) > 1e-9) {
                double r = Math.round(v);
                report(Severity.ADJUSTED, Code.ROUNDED, n.line(), p, num(v) + " is not a whole number. Rounded to "
                        + num(r), null);
                v = r;
            }
            if (v < min - 1e-9 || v > max + 1e-9) {
                double c = Math.max(min, Math.min(max, v));
                report(Severity.ADJUSTED, Code.CLAMPED, n.line(), p, num(v) + " is " + (v > max ? "above" : "below")
                        + " its range. Allowed " + (min == max ? "only " + num(min) : num(min) + " to " + num(max))
                        + ". Set to " + num(c), null);
                v = c;
            }
            return v;
        }

        /** A number field read with its table entry's range and default. */
        double number(Fields f, Map<String, Field> shape, String name, Need need) {
            Field d = shape.get(name);
            return number(f, name, d.min(), d.max(), d.dflt(), d.whole(), d.unit(), need);
        }

        double number(Fields f, Map<String, Field> shape, String name, double dflt) {
            Field d = shape.get(name);
            return number(f, name, d.min(), d.max(), dflt, d.whole(), d.unit(), null);
        }

        String allowed(Vocab v) {
            return v.words.size() <= 30 ? "Allowed: " + v.allowed() : "Allowed: the list in " + v.listedIn();
        }

        /** A word: a synonym is read as its word (ADJUSTED); an unknown word goes by {@code mode}. */
        String word(Fields f, String name, String vocab, String dflt, String dfltLabel, Mode mode, String part) {
            Node n = f.node(name);
            if (n == null) {
                return dflt;
            }
            Path p = f.at(name);
            if (!(n instanceof Str s)) {
                Vocab v = HeroCatalogue.vocab(vocab);
                String msg = "should be " + v.noun + " (a word in quotes), not " + shown(n) + ". " + allowed(v);
                return unknown(mode, n.line(), p, msg, Code.WRONG_TYPE, dflt, dfltLabel, part);
            }
            return resolve(s.value(), vocab, dflt, dfltLabel, mode, part, n.line(), p);
        }

        String resolve(String raw, String vocab, String dflt, String dfltLabel, Mode mode, String part, int line,
                       Path p) {
            Vocab v = HeroCatalogue.vocab(vocab);
            String w = HeroCatalogue.norm(raw);
            String r = v.resolve(w);
            if (r != null) {
                if (!r.equals(w)) {
                    report(Severity.ADJUSTED, Code.SYNONYM, line, p, "\"" + raw + "\" was read as \"" + r + "\"", null);
                }
                return r;
            }
            HeroCatalogue.EqualReading eq = HeroCatalogue.EQUAL_READINGS.getOrDefault(vocab, Map.of()).get(w);
            if (eq != null) {
                report(Severity.ADJUSTED, Code.EQUAL_TREATMENT, line, p, "\"" + raw + "\" was read as \"" + eq.readAs()
                        + "\": heroes treat players and NPCs alike, and " + eq.why(), null);
                return eq.readAs();
            }
            String msg = "\"" + raw + "\" is not " + v.noun + ". " + allowed(v) + "." + Problems.didYouMean(w, v.words);
            Code code = switch (mode) {
                case OPTIONAL -> Code.WORD_DEFAULT;
                case PART -> Code.WORD_PART;
                case CORE -> Code.WORD_CORE;
            };
            return unknown(mode, line, p, msg, code, dflt, dfltLabel, part);
        }

        String unknown(Mode mode, int line, Path p, String msg, Code code, String dflt, String dfltLabel,
                       String part) {
            switch (mode) {
                case OPTIONAL -> {
                    String tail = dflt == null ? " It was ignored" : " Used " + (dfltLabel == null ? "the default"
                            : dfltLabel) + ", \"" + dflt + "\"";
                    report(Severity.ADJUSTED, code, line, p, msg.endsWith(".") || msg.endsWith("?") ? msg + tail
                            : msg + "." + tail, null);
                    return dflt;
                }
                case PART -> report(Severity.PART_LEFT_OUT, code, line, p, msg, part);
                case CORE -> report(Severity.NOT_LOADED, code, line, p, msg, null);
            }
            return null;
        }

        boolean bool(Fields f, String name, boolean dflt) {
            Node n = f.node(name);
            if (n == null) {
                return dflt;
            }
            if (n instanceof HeroJson.Bool b) {
                return b.value();
            }
            if (n instanceof Str s && Set.of("true", "false").contains(HeroCatalogue.norm(s.value()))) {
                boolean v = HeroCatalogue.norm(s.value()).equals("true");
                report(Severity.ADJUSTED, Code.TEXT_BOOL, n.line(), f.at(name), "\"" + s.value() + "\" was read as "
                        + v, null);
                return v;
            }
            report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at(name), "should be true or false, not " + shown(n)
                    + ". Used the default, " + dflt, null);
            return dflt;
        }

        /** Plain text (v1 3): formatting codes and control characters removed, cut to {@code max}; null when absent. */
        String text(Fields f, String name, int max, Need need) {
            Node n = f.node(name);
            if (n == null) {
                return null;
            }
            Path p = f.at(name);
            if (!(n instanceof Str s)) {
                String msg = "should be text in quotes, not " + shown(n);
                if (need == null) {
                    report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, msg + ". It was ignored", null);
                } else {
                    need(need, Code.WRONG_TYPE, n.line(), p, msg);
                }
                return null;
            }
            String t = clean(s.value(), n.line(), p);
            if (t.length() > max) {
                report(Severity.ADJUSTED, Code.TEXT_CUT, n.line(), p, "is " + t.length() + " characters long; at most "
                        + max + ". Cut to " + max, null);
                t = cut(t, max);
            }
            return t.isEmpty() ? null : t;
        }

        String clean(String raw, int line, Path p) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '§') {
                    i++;
                } else if (c >= 0x20 && c != 0x7F) {
                    sb.append(c);
                } else if (c == '\n' || c == '\t' || c == '\r') {
                    sb.append(' ');
                }
            }
            String t = sb.toString().trim();
            if (!sb.toString().equals(raw)) {
                report(Severity.ADJUSTED, Code.TEXT_CLEANED, line, p, "formatting codes, line breaks and control"
                        + " characters were removed", null);
            }
            return t;
        }

        int color(Fields f, String name, String fallback) {
            Node n = f.node(name);
            if (n == null) {
                return HeroDef.NO_COLOR;
            }
            if (n instanceof Str s) {
                String t = s.value().trim();
                if (t.startsWith("#")) {
                    t = t.substring(1);
                }
                if (t.matches("[0-9A-Fa-f]{6}")) {
                    return Integer.parseInt(t, 16);
                }
            }
            report(Severity.ADJUSTED, Code.COLOR_INVALID, n.line(), f.at(name), shown(n) + " is not a colour. Write"
                    + " \"#RRGGBB\", for example \"#8A7F70\". Used " + fallback, null);
            return HeroDef.NO_COLOR;
        }

        /** A mark id (v1 3): normalised; null when absent or unusable (reported with {@code need}). */
        String mark(Fields f, String name, Need need) {
            Node n = f.node(name);
            if (n == null) {
                return null;
            }
            Path p = f.at(name);
            if (!(n instanceof Str s)) {
                need(need, Code.WRONG_TYPE, n.line(), p, "should be a mark id in quotes, not " + shown(n));
                return null;
            }
            String id = normaliseId(s.value());
            if (id.length() < HeroCatalogue.MARK_ID_MIN || id.length() > HeroCatalogue.MARK_ID_MAX
                    || !id.matches("[a-z][a-z0-9_]*")) {
                need(need, Code.MARK_INVALID, n.line(), p, "\"" + s.value() + "\" is not a mark id: 2 to 24"
                        + " lowercase letters, digits or _, starting with a letter");
                return null;
            }
            if (!id.equals(s.value())) {
                report(Severity.ADJUSTED, Code.MARK_RENAMED, n.line(), p, "\"" + s.value() + "\" was read as \"" + id
                        + "\"", null);
            }
            return id;
        }

        /**
         * A list field: a single value is read as a list of one; items over {@code max} are left out (each reported
         * with {@code noun}).
         */
        List<Node> list(Fields f, String name, int max, String noun, String part, boolean listShown) {
            Node n = f.node(name);
            if (n == null) {
                return List.of();
            }
            List<Node> items;
            if (n instanceof Arr a) {
                items = a.items();
            } else {
                report(Severity.ADJUSTED, Code.LIST_WRAPPED, n.line(), f.at(name), "should be a list [ ... ]; read as"
                        + " a list of one", null);
                items = List.of(n);
            }
            if (items.size() > max) {
                int more = items.size() - max - 1;
                report(Severity.PART_LEFT_OUT, Code.LIST_TOO_LONG, items.get(max).line(),
                        f.path.item(name, max, noun, null, listShown), "\"" + name + "\" holds at most " + max
                                + (max == 1 ? " item" : " items") + (more == 0 ? "" : "; this one and the " + more
                                + " after it were left out"), part);
                items = items.subList(0, max);
            }
            return items;
        }

        // -------------------------------------------------------------- the hero

        Read hero(Obj o, String fileId) {
            Read r = readHero(o, fileId);
            tellUnlisted();
            return r;
        }

        Read readHero(Obj o, String fileId) {
            hero = fileId;
            if (fileId == null) {
                Node idNode = o.get("id");
                if (!(idNode instanceof Str s)) {
                    report(Severity.NOT_LOADED, Code.LIST_HERO_NO_ID, o.line(), Path.ROOT, "a hero in a list needs its"
                            + " own \"id\", for example \"id\": \"bram_ostler\"", null);
                    return null;
                }
                if (!validId(s.value())) {
                    report(Severity.NOT_LOADED, Code.ID_INVALID, idNode.line(), Path.ROOT.field("id"), "\"" + s.value()
                            + "\" is not a hero id: 3 to 32 lowercase letters, digits or _, starting with a letter",
                            null);
                    return null;
                }
                hero = s.value();
            }
            // the format decides the field tables, so peek at it first (the message about it comes below)
            format = peekFormat(o);
            boolean v2 = format >= 2;
            Fields top = v2 ? new Fields(o, Path.ROOT, HeroCatalogue.TOP_2, HeroCatalogue.V1_ONLY_TOP)
                    : fields(o, Path.ROOT, HeroCatalogue.TOP);
            // format
            int declared = 0;
            Node fn = top.node("format");
            if (fn == null) {
                report(Severity.NOT_LOADED, Code.FORMAT_MISSING, o.line(), Path.ROOT, "\"format\" is missing. Write"
                        + " \"format\": " + likelyFormat(o) + " at the top of the hero", null);
            } else {
                double f = Double.NaN;
                if (fn instanceof Num x) {
                    f = x.value();
                } else if (fn instanceof Str s && parseUnit(s.value(), Unit.NONE) != null) {
                    f = parseUnit(s.value(), Unit.NONE);
                    report(Severity.ADJUSTED, Code.TEXT_NUMBER, fn.line(), Path.ROOT.field("format"), "\"" + s.value()
                            + "\" was read as " + num(f), null);
                }
                if (Double.isNaN(f) || f < 1 || f != Math.rint(f)) {
                    report(Severity.NOT_LOADED, Code.FORMAT_INVALID, fn.line(), Path.ROOT.field("format"), shown(fn)
                            + " is not a format. Write \"format\": " + likelyFormat(o), null);
                } else if (f > HeroCatalogue.MAX_FORMAT) {
                    report(Severity.NOT_LOADED, Code.FORMAT_NEWER, fn.line(), Path.ROOT.field("format"), "this file is"
                            + " for a newer Faction Clash (format " + num(f) + "; this build reads formats 1 and "
                            + HeroCatalogue.MAX_FORMAT + ")", null);
                } else {
                    declared = (int) f;
                }
            }
            // id
            Node idNode = top.node("id");
            if (fileId != null) {
                if (idNode == null) {
                    report(Severity.ADJUSTED, Code.ID_MISSING, o.line(), Path.ROOT, "\"id\" is missing; the file"
                            + " name gives it: \"" + fileId + "\"", null);
                } else if (!(idNode instanceof Str s) || !s.value().equals(fileId)) {
                    report(Severity.ADJUSTED, Code.ID_MISMATCH, idNode.line(), Path.ROOT.field("id"), "the id inside"
                            + " is " + shown(idNode) + ", but the file is named " + stem(file) + ".json; the file name"
                            + " wins: \"" + fileId + "\"", null);
                }
            }
            // name
            String name = text(top, "name", 24, new Need(Severity.NOT_LOADED, null));
            if (name == null && !top.has("name") || name == null && top.node("name") instanceof Str) {
                report(Severity.NOT_LOADED, Code.MISSING, o.line(), Path.ROOT, "\"name\" is missing. Add the hero's"
                        + " display name, for example \"name\": \"" + HeroIdentity.nameFromId(hero) + "\"", null);
            }
            // format 2: the personality comes first, because the fight AI's defaults follow from it (HEROES_V2 5.4)
            HeroReader2 r2 = v2 ? new HeroReader2(this, top) : null;
            if (v2) {
                r2.readEarly();
            }
            // behaviour (the role decides the defaults) and the basic attack's style
            Role role = null;
            Fields beh = null;
            Node bn = top.node("behaviour");
            if (bn instanceof Obj bo) {
                beh = fields(bo, Path.ROOT.field("behaviour"), v2 ? HeroCatalogue.BEHAVIOUR_2 : HeroCatalogue.BEHAVIOUR);
                if (!beh.has("combat_role")) {
                    report(Severity.NOT_LOADED, Code.MISSING, bo.line(), beh.path, "\"combat_role\" is missing. "
                            + allowed(HeroCatalogue.vocab(vocab("combat_role"))), null);
                } else {
                    String r = word(beh, "combat_role", vocab("combat_role"), null, null, Mode.CORE, null);
                    role = r == null ? null : HeroCatalogue.enumOf(Role.class, r);
                }
            } else {
                report(Severity.NOT_LOADED, bn == null ? Code.MISSING : Code.WRONG_TYPE, bn == null ? o.line()
                        : bn.line(), Path.ROOT, bn == null ? "\"behaviour\" is missing. Add at least \"behaviour\": {"
                        + " \"combat_role\": \"striker\" }" : "\"behaviour\" should be an object { ... }, not "
                        + shown(bn), null);
            }
            Role r0 = role == null ? Role.STRIKER : role;
            RolePreset preset = HeroCatalogue.ROLES.get(r0);
            String el = word(top, "element", "element", "physical", null, Mode.OPTIONAL, null);
            element = HeroCatalogue.enumOf(Element.class, el);
            // stats
            Stats stats = stats(top, preset);
            // basic attack (style first)
            Node an = top.node("basic_attack");
            Style style = null;
            Fields ba = null;
            if (an instanceof Obj ao) {
                ba = fields(ao, Path.ROOT.field("basic_attack"), v2 ? HeroCatalogue.BASIC_ATTACK_2
                        : HeroCatalogue.BASIC_ATTACK);
                if (!ba.has("style")) {
                    report(Severity.NOT_LOADED, Code.MISSING, ao.line(), ba.path, "\"style\" is missing. "
                            + allowed(HeroCatalogue.vocab("style")), null);
                } else {
                    String s = word(ba, "style", "style", null, null, Mode.CORE, null);
                    style = s == null ? null : HeroCatalogue.enumOf(Style.class, s);
                }
            } else {
                report(Severity.NOT_LOADED, an == null ? Code.MISSING : Code.WRONG_TYPE, an == null ? o.line()
                        : an.line(), Path.ROOT, an == null ? "\"basic_attack\" is missing. Add at least"
                        + " \"basic_attack\": { \"style\": \"melee\" }" : "\"basic_attack\" should be an object"
                        + " { ... }, not " + shown(an), null);
            }
            Style s0 = style == null ? Style.MELEE : style;
            Behaviour behaviour = v2 ? r2.behaviour(beh, r0, preset) : behaviour(beh, r0, preset);
            if (role != null && style != null && (style == Style.THROWN && HeroCatalogue.meleeRole(role)
                    || style == Style.MELEE && role == Role.MARKSMAN)) {
                report(Severity.NOTE, Code.ROLE_STYLE, ba.line("style"), ba.at("style"), "a " + role.id() + " that "
                        + (style == Style.MELEE ? "fights in melee" : "throws from range") + "; is that intended?",
                        null);
            }
            // the rest of the top level
            String title = orEmpty(text(top, "title", 32, null));
            String description = orEmpty(text(top, "description", 300, null));
            String rar = word(top, "rarity", "rarity", "rare", null, Mode.OPTIONAL, null);
            HeroDef.Rarity rarity = HeroCatalogue.enumOf(HeroDef.Rarity.class, rar);
            String starter = v2 ? null : word(top, "starter_for", "faction", null, null, Mode.OPTIONAL, null);
            List<String> factions = v2 ? HeroCatalogue.FACTIONS : factions(top);
            String skin = v2 ? "classic" : word(top, "skin_model", "skin_model", "classic", null, Mode.OPTIONAL, null);
            int color = v2 ? HeroDef.NO_COLOR : color(top, "color", "the faction colour");
            Map<Element, Integer> resist = resistances(top);
            Empowered empowered = null;
            if (v2) {
                r2.readStates(stats);
            } else {
                empowered = empowered(top, stats);
                hasEmpowered = empowered != null;
            }
            BasicAttack basic = basicAttack(ba, s0);
            List<GearItem> gear = gear(top, s0);
            List<Ability> skills = new ArrayList<>();
            Ability ultimate = null;
            List<Passive> passives = new ArrayList<>();
            List<BaseAbility> bases = new ArrayList<>();
            if (v2) {
                r2.readMoves(rarity);
            } else {
                List<Node> sk = list(top, "skills", 4, "skill", "Skill", false);
                for (int i = 0; i < sk.size(); i++) {
                    Ability a = ability(sk.get(i), Path.ROOT, Slot.SKILL, i);
                    if (a != null) {
                        skills.add(a);
                    }
                }
                Node un = top.node("ultimate");
                if (un != null) {
                    ultimate = ability(un, Path.ROOT, Slot.ULTIMATE, -1);
                }
            }
            List<Node> ps = list(top, "passives", 4, "passive", "Passive", false);
            for (int i = 0; i < ps.size(); i++) {
                Passive p = passive(ps.get(i), i);
                if (p != null) {
                    passives.add(p);
                }
            }
            if (!v2) {
                List<Node> bs = list(top, "base_abilities", 3, "base ability", "Base ability", false);
                for (int i = 0; i < bs.size(); i++) {
                    BaseAbility b = baseAbility(bs.get(i), i);
                    if (b != null) {
                        bases.add(b);
                    }
                }
            }
            Map<LineEvent, List<String>> lines = lines(top);
            finishOwnMarks();
            HeroDef.V2 part2 = v2 ? r2.finish(name, rarity, r0, s0, element, resist, stats, behaviour, passives, lines)
                    : null;
            if (dead || role == null || style == null || name == null || declared < 1
                    || declared > HeroCatalogue.MAX_FORMAT) {
                return null;
            }
            HeroDef draft = new HeroDef(declared, hero, name, title, description, rarity, starter, factions, skin, color,
                    element, stats, resist, gear, behaviour, basic, List.copyOf(skills), ultimate, List.copyOf(passives),
                    empowered, List.copyOf(bases), lines, part2);
            HeroDef def = HeroBalance.apply(draft, this::balanced);
            if (!v2) {
                def = def.withV2(HeroReader2.mapV1(def));
            }
            return new Read(def, file, Set.copyOf(applied), List.copyOf(refs), v2 ? r2.across(def) : null);
        }

        /** The format a hero says, before it is checked: 2 for format 2, else 1 (format 1's field tables). */
        int peekFormat(Obj o) {
            Node fn = o.get("format");
            for (Member m : o.members()) {
                // a misspelled "format" is read as one by the field table below, so it is peeked at the same way
                String k = m.key(), nk = HeroCatalogue.norm(k);
                if (fn != null) {
                    break;
                }
                if (!k.startsWith("_") && !HeroCatalogue.TOP.containsKey(k) && !HeroCatalogue.TOP_2.containsKey(k)
                        && (nk.equals("format") || Problems.distance(nk, "format") <= 2)) {
                    fn = m.value();
                }
            }
            double f = fn instanceof Num x ? x.value() : fn instanceof Str s && parseUnit(s.value(), Unit.NONE) != null
                    ? parseUnit(s.value(), Unit.NONE) : Double.NaN;
            return f == 2 ? 2 : 1;
        }

        /** For a missing or broken {@code format}: 2 when the hero uses a field only format 2 has, else 1. */
        int likelyFormat(Obj o) {
            for (Member m : o.members()) {
                if (!HeroCatalogue.TOP.containsKey(m.key()) && HeroCatalogue.TOP_2.containsKey(m.key())) {
                    return 2;
                }
            }
            return 1;
        }

        /** The word list a field reads in this hero's format ({@link HeroCatalogue#vocabName}). */
        String vocab(String name) {
            return HeroCatalogue.vocabName(name, format);
        }

        /**
         * A change the balance pass made, reported at the exact field: its line when the file has it, and a word that
         * the default was used when it has not.
         */
        void balanced(Object w, String field, Code code, String msg) {
            Pos pos = where.get(w);
            if (pos == null) {
                report(Severity.ADJUSTED, code, 0, Path.ROOT, msg, null);
                return;
            }
            Path p = pos.path;
            int line = pos.line;
            String m = msg;
            if (field != null) {
                p = p.field(field);
                if (pos.fields != null && pos.fields.has(field)) {
                    line = pos.fields.line(field);
                } else if (pos.fields != null) {
                    m = msg + " (the file gives no \"" + field + "\", so its default was used)";
                }
            }
            report(Severity.ADJUSTED, code, line, p, m, null);
        }

        String orEmpty(String s) {
            return s == null ? "" : s;
        }

        Stats stats(Fields top, RolePreset rp) {
            Node n = top.node("stats");
            Map<String, Field> sh = HeroCatalogue.STATS;
            if (!(n instanceof Obj o)) {
                if (n != null) {
                    report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), top.at("stats"), "should be an object { ... },"
                            + " not " + shown(n) + ". Used the role's stats", null);
                }
                return new Stats(rp.health(), rp.attack(), rp.armor(), rp.speed(), rp.knockbackResistance(), 100,
                        rp.growth());
            }
            Fields f = fields(o, top.at("stats"), sh);
            String g = word(f, "growth", "growth", rp.growth().id(), "the role's growth", Mode.OPTIONAL, null);
            int size = 100;
            if (format >= 2) {
                if (f.has("size")) {
                    report(Severity.NOTE, Code.V1_FIELD, f.line("size"), f.at("size"), "\"stats.size\" is replaced by"
                            + " \"art.scale\" in format 2 (the size comes from the hero's model); it was ignored", null);
                }
            } else {
                size = (int) number(f, sh, "size", 100);
            }
            return new Stats(number(f, sh, "health", rp.health()), number(f, sh, "attack", rp.attack()),
                    number(f, sh, "armor", rp.armor()), (int) number(f, sh, "speed", rp.speed()),
                    (int) number(f, sh, "knockback_resistance", rp.knockbackResistance()),
                    size, HeroCatalogue.enumOf(HeroDef.Growth.class, g));
        }

        Behaviour behaviour(Fields f, Role role, RolePreset rp) {
            if (f == null) {
                return new Behaviour(role, rp.focus(), rp.keepDistance(), rp.aggression(), rp.protects(),
                        rp.retreatBelow(), rp.raids(), "guard");
            }
            Map<String, Field> sh = HeroCatalogue.BEHAVIOUR;
            String focus = word(f, "focus", "focus", rp.focus().id(), "the role's focus", Mode.OPTIONAL, null);
            String agg = word(f, "aggression", "aggression", rp.aggression().id(), "the role's aggression",
                    Mode.OPTIONAL, null);
            String prot = word(f, "protects", "protects", rp.protects().id(), "the role's", Mode.OPTIONAL, null);
            String raids = word(f, "raids", "raids", rp.raids().id(), "the role's", Mode.OPTIONAL, null);
            String duty = word(f, "duty", "duty", "guard", null, Mode.OPTIONAL, null);
            return new Behaviour(role, HeroCatalogue.enumOf(HeroDef.Focus.class, focus),
                    number(f, sh, "keep_distance", rp.keepDistance()),
                    HeroCatalogue.enumOf(HeroDef.Aggression.class, agg),
                    HeroCatalogue.enumOf(HeroDef.Protects.class, prot), (int) number(f, sh, "retreat_below",
                    rp.retreatBelow()), HeroCatalogue.enumOf(HeroDef.Raids.class, raids), duty);
        }

        List<String> factions(Fields top) {
            List<String> out = new ArrayList<>();
            List<Node> items = list(top, "factions", 4, "faction", "Faction", true);
            for (int i = 0; i < items.size(); i++) {
                Node n = items.get(i);
                Path p = top.path.item("factions", i, "faction", null, true);
                String w = n instanceof Str s ? resolve(s.value(), "faction", null, null, Mode.PART, "Faction",
                        n.line(), p) : null;
                if (!(n instanceof Str)) {
                    report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be a faction id in quotes, not "
                            + shown(n), "Faction");
                }
                if (w != null && out.contains(w)) {
                    report(Severity.ADJUSTED, Code.LIST_DUPLICATE, n.line(), p, "\"" + w + "\" is listed twice; once"
                            + " is enough", null);
                } else if (w != null) {
                    out.add(w);
                }
            }
            return out.isEmpty() ? HeroCatalogue.FACTIONS : List.copyOf(out);
        }

        Map<Element, Integer> resistances(Fields top) {
            Map<Element, Integer> m = new EnumMap<>(Element.class);
            Node n = top.node("resistances");
            if (n == null) {
                return Collections.unmodifiableMap(m);
            }
            if (!(n instanceof Obj o)) {
                report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), top.at("resistances"), "should be an object"
                        + " { \"fire\": 20 }, not " + shown(n) + ". It was ignored", null);
                return Collections.unmodifiableMap(m);
            }
            Map<String, Field> sh = new LinkedHashMap<>();
            for (Member mem : o.members()) {
                if (!mem.key().startsWith("_") && !(mem.value() instanceof HeroJson.Null)) {
                    sh.put(mem.key(), new Field(mem.key(), HeroCatalogue.Kind.INT, Unit.PERCENT,
                            HeroCatalogue.RESIST_MIN, HeroCatalogue.RESIST_MAX, 0, null, false));
                }
            }
            Fields f = fields(o, top.at("resistances"), sh);
            for (String k : sh.keySet()) {
                String e = resolve(k, "element", null, null, Mode.PART, "Resistance", f.got.get(k).line(), f.at(k));
                if (e == null) {
                    continue;
                }
                double v = number(f, sh, k, new Need(Severity.PART_LEFT_OUT, "Resistance"));
                if (!Double.isNaN(v)) {
                    m.put(HeroCatalogue.enumOf(Element.class, e), (int) v);
                }
            }
            return Collections.unmodifiableMap(m);
        }

        // -------------------------------------------------------------- gear (v1 7)

        List<GearItem> gear(Fields top, Style style) {
            List<GearItem> items = new ArrayList<>();
            List<Integer> lines = new ArrayList<>();
            List<Path> paths = new ArrayList<>();
            List<Node> nodes = list(top, "gear", 6, "gear", "Gear item", false);
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                Path p = top.path.item("gear", i, "gear", null, false);
                GearItem g = gearItem(n, p);
                if (g != null) {
                    items.add(g);
                    lines.add(n.line());
                    paths.add(p);
                }
            }
            // one item per slot; the main hand holds what the style needs
            List<GearItem> out = new ArrayList<>();
            Map<GearSlot, GearItem> bySlot = new EnumMap<>(GearSlot.class);
            GearItem serving = null;
            for (GearItem g : items) {
                if (g.slot() == GearSlot.MAINHAND && serving == null
                        && HeroCatalogue.servesStyle(HeroCatalogue.GEAR.get(g.item()), style)) {
                    serving = g;
                }
            }
            for (int i = 0; i < items.size(); i++) {
                GearItem g = items.get(i);
                if (g.slot() == GearSlot.MAINHAND && serving != null && g != serving) {
                    report(Severity.PART_LEFT_OUT, Code.GEAR_SLOT, lines.get(i), paths.get(i), "the main hand already"
                            + " holds \"" + serving.item() + "\"; one item per slot", "Gear item");
                    continue;
                }
                if (g.slot() == GearSlot.MAINHAND && serving == null && style != Style.THROWN) {
                    report(Severity.PART_LEFT_OUT, Code.GEAR_STYLE, lines.get(i), paths.get(i), "a " + style.id()
                            + " hero holds " + (style == Style.MELEE ? "a sword, an axe or a mace" : "a "
                            + HeroCatalogue.styleItem(style)) + " in its main hand, not \"" + g.item() + "\"",
                            "Gear item");
                    continue;
                }
                GearItem old = bySlot.get(g.slot());
                if (old != null) {
                    report(Severity.PART_LEFT_OUT, Code.GEAR_SLOT, lines.get(i), paths.get(i), "the " + g.slot().id()
                            + " slot already holds \"" + old.item() + "\"; one item per slot", "Gear item");
                    continue;
                }
                bySlot.put(g.slot(), g);
                out.add(g);
            }
            if (style != Style.THROWN && !bySlot.containsKey(GearSlot.MAINHAND)) {
                // the style's basic item: the default of a hero without "gear" (v1 4), an adjustment of a kit that
                // lacks it (v1 7)
                String item = HeroCatalogue.styleItem(style);
                if (top.has("gear")) {
                    report(Severity.ADJUSTED, Code.GEAR_ADDED, top.line("gear"), top.at("gear"), "the kit has no"
                            + (style == Style.MELEE ? " melee weapon" : " " + item) + " for the " + style.id()
                            + " style; \"" + item + "\" was added", null);
                }
                out.add(new GearItem(item, null, Map.of(), GearSlot.MAINHAND, true));
            }
            // at most 6 enchantment levels in the kit: lower from the last
            int total = 0;
            for (GearItem g : out) {
                for (int lv : g.enchantments().values()) {
                    total += lv;
                }
            }
            if (total > HeroCatalogue.MAX_KIT_ENCHANT_LEVELS) {
                report(Severity.ADJUSTED, Code.ENCHANT_KIT, top.line("gear"), top.at("gear"), "the kit has " + total
                        + " enchantment levels; at most " + HeroCatalogue.MAX_KIT_ENCHANT_LEVELS
                        + ". The last ones were lowered", null);
                int over = total - HeroCatalogue.MAX_KIT_ENCHANT_LEVELS;
                for (int i = out.size() - 1; i >= 0 && over > 0; i--) {
                    GearItem g = out.get(i);
                    List<Map.Entry<String, Integer>> es = new ArrayList<>(g.enchantments().entrySet());
                    for (int j = es.size() - 1; j >= 0 && over > 0; j--) {
                        int cut = Math.min(over, es.get(j).getValue());
                        es.set(j, Map.entry(es.get(j).getKey(), es.get(j).getValue() - cut));
                        over -= cut;
                    }
                    Map<String, Integer> m = new LinkedHashMap<>();
                    for (Map.Entry<String, Integer> e : es) {
                        if (e.getValue() > 0) {
                            m.put(e.getKey(), e.getValue());
                        }
                    }
                    out.set(i, new GearItem(g.item(), g.name(), Collections.unmodifiableMap(m), g.slot(), g.added()));
                }
            }
            return List.copyOf(out);
        }

        GearItem gearItem(Node n, Path p) {
            Node itemNode;
            Fields f = null;
            if (n instanceof Str) {
                itemNode = n;
            } else if (n instanceof Obj o) {
                f = fields(o, p, HeroCatalogue.GEAR_ITEM);
                itemNode = f.node("item");
                if (itemNode == null) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"item\" is missing, for example"
                            + " \"item\": \"iron_sword\"", "Gear item");
                    return null;
                }
            } else {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an item id such as"
                        + " \"iron_sword\" or an object { \"item\": ... }, not " + shown(n), "Gear item");
                return null;
            }
            if (!(itemNode instanceof Str s)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, itemNode.line(), p, "the item should be an id in"
                        + " quotes, not " + shown(itemNode), "Gear item");
                return null;
            }
            String id = HeroCatalogue.norm(s.value());
            if (HeroCatalogue.tooGood(id)) {
                report(Severity.PART_LEFT_OUT, Code.GEAR_TOO_GOOD, itemNode.line(), p, "\"" + id + "\": heroes start"
                        + " with iron gear at most (they can still take better gear from the storehouse)", "Gear item");
                return null;
            }
            GearKind kind = HeroCatalogue.GEAR.get(id);
            if (kind == null) {
                report(Severity.PART_LEFT_OUT, Code.GEAR_REFUSED, itemNode.line(), p, "\"" + s.value() + "\" is not a"
                        + " weapon or armour piece a hero starts with. " + allowed(HeroCatalogue.vocab("gear")) + "."
                        + Problems.didYouMean(id, HeroCatalogue.GEAR.keySet()), "Gear item");
                return null;
            }
            String name = f == null ? null : text(f, "name", 24, null);
            Map<String, Integer> ench = f == null ? Map.of() : enchantments(f, kind, id);
            return new GearItem(id, name, ench, kind.slot);
        }

        Map<String, Integer> enchantments(Fields f, GearKind kind, String item) {
            Map<String, Integer> out = new LinkedHashMap<>();
            Node n = f.node("enchantments");
            if (n == null) {
                return out;
            }
            Path p = f.at("enchantments");
            if (!(n instanceof Obj o)) {
                report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"sharpness\": 1 },"
                        + " not " + shown(n) + ". It was ignored", null);
                return out;
            }
            Map<String, String> exclusive = new HashMap<>();
            int count = 0;
            for (Member m : o.members()) {
                if (m.key().startsWith("_") || nothing(m, p)) {
                    continue;
                }
                Path ep = p.field(m.key());
                String id = HeroCatalogue.norm(m.key());
                if (HeroCatalogue.REFUSED_ENCHANTS.containsKey(id)) {
                    report(Severity.PART_LEFT_OUT, Code.ENCHANT_REFUSED, m.line(), ep, "\"" + id + "\" is not allowed"
                            + " for heroes: " + HeroCatalogue.REFUSED_ENCHANTS.get(id), "Enchantment");
                    continue;
                }
                HeroCatalogue.Enchant e = HeroCatalogue.ENCHANTS.get(id);
                if (e == null) {
                    resolve(m.key(), "enchantment", null, null, Mode.PART, "Enchantment", m.line(), ep);
                    continue;
                }
                if (!e.accepts().contains(kind)) {
                    report(Severity.PART_LEFT_OUT, Code.ENCHANT_ITEM, m.line(), ep, "\"" + id + "\" does not go on \""
                            + item + "\" (vanilla rules)", "Enchantment");
                    continue;
                }
                if (e.exclusive() != null && exclusive.containsKey(e.exclusive())) {
                    report(Severity.PART_LEFT_OUT, Code.ENCHANT_EXCLUSIVE, m.line(), ep, "\"" + id + "\" cannot be"
                            + " combined with \"" + exclusive.get(e.exclusive()) + "\" (vanilla rules)", "Enchantment");
                    continue;
                }
                if (++count > HeroCatalogue.MAX_ENCHANTS_PER_ITEM) {
                    report(Severity.PART_LEFT_OUT, Code.LIST_TOO_LONG, m.line(), ep, "an item has at most "
                            + HeroCatalogue.MAX_ENCHANTS_PER_ITEM + " enchantments", "Enchantment");
                    continue;
                }
                Map<String, Field> sh = Map.of(m.key(), new Field(m.key(), HeroCatalogue.Kind.INT, Unit.LEVEL, 1,
                        e.maxLevel(), Double.NaN, null, true));
                Fields lf = new Fields(new Obj(o.line(), o.col(), List.of(m)), p, sh, Map.of());
                double lv = number(lf, sh, m.key(), new Need(Severity.PART_LEFT_OUT, "Enchantment"));
                if (Double.isNaN(lv)) {
                    continue;
                }
                if (e.exclusive() != null) {
                    exclusive.put(e.exclusive(), id);
                }
                out.put(id, (int) lv);
            }
            return Collections.unmodifiableMap(out);
        }

        // -------------------------------------------------------------- basic attack (v1 9) and empowered (18)

        BasicAttack basicAttack(Fields f, Style style) {
            if (f == null) {
                return new BasicAttack(style, "snowball", null, 4, 1.5, Element.PHYSICAL, List.of(), null);
            }
            Map<String, Field> sh = HeroCatalogue.BASIC_ATTACK;
            String proj = "snowball", held = null;
            double amount = 4, interval = 1.5;
            // melee, arrow and trident hits stay physical (v1 9); only a thrown attack has an element of its own
            Element el = Element.PHYSICAL;
            if (style == Style.THROWN) {
                proj = word(f, "projectile", "item_look", "snowball", null, Mode.OPTIONAL, null);
                held = word(f, "held_item", "held_prop", null, null, Mode.OPTIONAL, null);
                amount = number(f, sh, "amount", 4);
                interval = number(f, sh, "interval", 1.5);
                el = HeroCatalogue.enumOf(Element.class, word(f, "element", "element", element.id(),
                        "the hero's element", Mode.OPTIONAL, null));
            } else {
                for (String k : HeroCatalogue.THROWN_ONLY) {
                    f.unused(k, "\"" + k + "\" is only used by style \"thrown\"");
                }
            }
            FxCtx onHitCtx = new FxCtx(Slot.ON_HIT, "the basic attack's on_hit", null, false, false, false, false,
                    null);
            List<Effect> onHit = effects(f, "on_hit", 3, onHitCtx);
            NthHit nth = null;
            Node nn = f.node("every_nth_hit");
            if (nn instanceof Obj no) {
                Fields nf = fields(no, f.at("every_nth_hit"), HeroCatalogue.NTH_HIT);
                Need need = new Need(Severity.PART_LEFT_OUT, "Finisher");
                if (!nf.has("n")) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, no.line(), nf.path, "\"n\" is missing: every how"
                            + " many landed hits (2 to 6)", "Finisher");
                } else if (!nf.has("effects")) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, no.line(), nf.path, "\"effects\" is missing",
                            "Finisher");
                } else {
                    double n = number(nf, HeroCatalogue.NTH_HIT, "n", need);
                    List<Effect> fx = effects(nf, "effects", 3, new FxCtx(Slot.NTH_HIT, "the basic attack's"
                            + " finisher", null, false, false, false, false, null));
                    if (Double.isNaN(n)) {
                        n = 0;
                    } else if (fx.isEmpty()) {
                        report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, no.line(), nf.path, "the finisher has no"
                                + " effect left that works", "Finisher");
                    } else {
                        nth = new NthHit((int) n, fx);
                    }
                }
            } else if (nn != null) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, nn.line(), f.at("every_nth_hit"), "should be an object"
                        + " { \"n\": 3, \"effects\": [...] }, not " + shown(nn), "Finisher");
            }
            int combo = format >= 2 ? (int) number(f, HeroCatalogue.BASIC_ATTACK_2, "combo", 3) : 3;
            BasicAttack b = new BasicAttack(style, proj, held, amount, interval, el, onHit, nth, combo);
            where.put(b, new Pos(f.path, f.obj.line(), f));
            return b;
        }

        Empowered empowered(Fields top, Stats stats) {
            Node n = top.node("empowered");
            if (n == null) {
                return null;
            }
            Path p = top.at("empowered");
            if (!(n instanceof Obj o)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"name\": ... },"
                        + " not " + shown(n), "Empowered state");
                return null;
            }
            Fields f = fields(o, p, HeroCatalogue.EMPOWERED);
            String name = text(f, "name", 24, new Need(Severity.PART_LEFT_OUT, "Empowered state"));
            if (name == null) {
                if (!f.has("name")) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"name\" is missing", "Empowered state");
                }
                return null;
            }
            List<StatBonus> stats2 = new ArrayList<>();
            List<Node> sl = list(f, "stats", 4, "stat", "Stat", true);
            for (int i = 0; i < sl.size(); i++) {
                Path sp = p.item("stats", i, "stat", null, true);
                Node sn = sl.get(i);
                if (!(sn instanceof Obj so)) {
                    report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, sn.line(), sp, "should be an object { \"stat\":"
                            + " \"armor\", \"percent\": 20 }, not " + shown(sn), "Stat");
                    continue;
                }
                Fields sf = fields(so, sp, HeroCatalogue.STAT_BONUS);
                Need need = new Need(Severity.PART_LEFT_OUT, "Stat");
                if (!sf.has("stat") || !sf.has("percent")) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, so.line(), sp, "\"" + (sf.has("stat") ? "percent"
                            : "stat") + "\" is missing", "Stat");
                    continue;
                }
                String st = word(sf, "stat", "stat", null, null, Mode.PART, "Stat");
                double pc = number(sf, HeroCatalogue.STAT_BONUS, "percent", need);
                if (st != null && !Double.isNaN(pc)) {
                    StatBonus bonus = new StatBonus(HeroCatalogue.enumOf(Stat.class, st), (int) pc);
                    where.put(bonus, new Pos(sp, so.line(), sf));
                    stats2.add(bonus);
                }
            }
            FxCtx ctx = new FxCtx(Slot.ON_HIT, "the empowered on_hit", null, false, false, false, false, null);
            hasEmpowered = true;
            List<Effect> onHit = effects(f, "on_hit", 2, ctx);
            int size = (int) number(f, HeroCatalogue.EMPOWERED, "size", 100);
            if (stats.size() * size / 100.0 > HeroCatalogue.MAX_SIZE + 1e-9) {
                report(Severity.NOTE, Code.SIZE_CAP, f.line("size"), f.at("size"), "stats.size " + stats.size()
                        + "% x empowered.size " + size + "% is " + num(stats.size() * size / 100.0) + "%; the hero"
                        + " grows to " + HeroCatalogue.MAX_SIZE + "% at most, so it still fits 2-high tunnels", null);
            }
            String particle = word(f, "particle", "particle", null, "the element's particle", Mode.OPTIONAL, null);
            Empowered e = new Empowered(name, orEmpty(text(f, "description", 200, null)), List.copyOf(stats2), onHit,
                    particle, color(f, "color", "the hero colour"), size, bool(f, "skills_locked", false),
                    text(f, "line", HeroCatalogue.LINE_MAX, null));
            where.put(e, new Pos(p, o.line(), f));
            return e;
        }

        // -------------------------------------------------------------- abilities (v1 10)

        String peekName(Node n) {
            if (n instanceof Obj o && o.get("name") instanceof Str s) {
                StringBuilder sb = new StringBuilder();
                String raw = s.value();
                for (int i = 0; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    if (c == '§') {
                        i++;
                    } else if (c >= 0x20 && c != 0x7F) {
                        sb.append(c);
                    }
                }
                return cut(sb.toString().trim(), 24);
            }
            return null;
        }

        Ability ability(Node n, Path base, Slot slot, int index) {
            boolean ult = slot == Slot.ULTIMATE;
            String partNoun = ult ? "Ultimate" : "Skill";
            String peek = peekName(n);
            Path p = ult ? base.named("ultimate", peek) : base.item("skills", index, "skill", peek, false);
            if (!(n instanceof Obj o)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"name\": ...,"
                        + " \"form\": ..., \"effects\": [...] }, not " + shown(n), partNoun);
                return null;
            }
            Map<String, String> elsewhere = new HashMap<>();
            Fields f = new Fields(o, p, HeroCatalogue.ABILITY, elsewhere);
            Need need = new Need(Severity.PART_LEFT_OUT, partNoun);
            String name = text(f, "name", 24, need);
            if (name == null) {
                if (!f.has("name") || f.node("name") instanceof Str) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"name\" is missing. Give the "
                            + partNoun.toLowerCase(Locale.ROOT) + " a name, for example \"name\": \"Shield Slam\"",
                            partNoun);
                }
                return null;
            }
            if (!f.has("form")) {
                report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"form\" is missing. "
                        + allowed(HeroCatalogue.vocab("form")), partNoun);
                return null;
            }
            String fw = word(f, "form", "form", null, null, Mode.PART, partNoun);
            if (fw == null) {
                return null;
            }
            Form form = HeroCatalogue.enumOf(Form.class, fw);
            FormSpec fs = HeroCatalogue.FORMS.get(form);
            Map<String, Field> sh = HeroCatalogue.ABILITY;
            String formText = "form \"" + form.id() + "\"";
            // zone place first: it decides whether the zone uses a range
            ZoneAt zoneAt = ZoneAt.TARGET;
            double delay = 0, duration = 0, interval = 1;
            if (fs.zone()) {
                zoneAt = HeroCatalogue.enumOf(ZoneAt.class, word(f, "zone_at", "zone_at", "target", null,
                        Mode.OPTIONAL, null));
                delay = number(f, sh, "delay", zoneAt == ZoneAt.TARGET ? 1 : 0);
                duration = number(f, sh, "duration", 0);
                interval = number(f, sh, "interval", 1);
            } else {
                for (String k : List.of("zone_at", "delay", "duration", "interval")) {
                    f.unused(k, "\"" + k + "\" is not used by " + formText);
                }
            }
            double range = 0;
            if (fs.rangeFixed()) {
                f.unused("range", "a melee skill always reaches " + num(fs.rangeDefault()) + " blocks; \"range\" is"
                        + " fixed");
                range = fs.rangeDefault();
            } else if (fs.usesRange() && !(form == Form.ZONE && zoneAt != ZoneAt.TARGET)) {
                range = number(f, "range", fs.rangeMin(), fs.rangeMax(), fs.rangeDefault(), false, Unit.BLOCKS, null);
            } else {
                f.unused("range", "\"range\" is not used by " + formText + (form == Form.ZONE ? " at the hero" : ""));
            }
            double radius = 0;
            if (fs.usesRadius()) {
                radius = number(f, "radius", fs.radiusMin(), fs.radiusMax(), fs.radiusDefault(), false, Unit.BLOCKS,
                        null);
            } else {
                f.unused("radius", "\"radius\" is not used by " + formText);
            }
            int count = 3, angle = 70, hits = 1;
            double width = 1, hitInterval = 0.3;
            if (fs.count()) {
                count = (int) number(f, sh, "count", 3);
            } else {
                f.unused("count", "\"count\" is not used by " + formText);
            }
            if (fs.angle()) {
                angle = (int) number(f, sh, "angle", 70);
            } else {
                f.unused("angle", "\"angle\" is not used by " + formText);
            }
            if (fs.width()) {
                width = number(f, sh, "width", 1);
            } else {
                f.unused("width", "\"width\" is not used by " + formText);
            }
            if (fs.hits()) {
                hits = (int) number(f, sh, "hits", 1);
                hitInterval = number(f, sh, "hit_interval", 0.3);
            } else {
                f.unused("hits", "\"hits\" is not used by " + formText);
                f.unused("hit_interval", "\"hit_interval\" is not used by " + formText);
            }
            String projectile = "arrow";
            if (fs.projectile()) {
                projectile = word(f, "projectile", "projectile", "arrow", null, Mode.OPTIONAL, null);
            } else {
                f.unused("projectile", "\"projectile\" is not used by " + formText);
            }
            double windup = number(f, sh, "windup", HeroCatalogue.defaultWindup(slot, form));
            double cooldown = 8;
            int charges = 1, energyGain = 10, chargeRate = 100;
            if (ult) {
                for (String k : HeroCatalogue.SKILL_ONLY) {
                    if (f.has(k)) {
                        report(Severity.NOTE, Code.FIELD_ELSEWHERE, f.line(k), f.at(k), "\"" + k + "\" is only used by"
                                + " skills (the ultimate is charged by energy); it was ignored", null);
                    }
                }
                chargeRate = (int) number(f, sh, "charge_rate", 100);
            } else {
                if (f.has("charge_rate")) {
                    report(Severity.NOTE, Code.FIELD_ELSEWHERE, f.line("charge_rate"), f.at("charge_rate"),
                            "\"charge_rate\" is only used by the ultimate; it was ignored", null);
                }
                cooldown = number(f, sh, "cooldown", 8);
                charges = (int) number(f, sh, "charges", 1);
                energyGain = (int) number(f, sh, "energy_gain", 10);
            }
            UseWhen useDefault = HeroCatalogue.defaultUseWhen(slot, form);
            UseWhen useWhen = HeroCatalogue.enumOf(UseWhen.class, word(f, "use_when", "use_when", useDefault.id(),
                    null, Mode.OPTIONAL, null));
            if (useWhen == UseWhen.OUT_OF_COMBAT && !form.friendly()) {
                report(Severity.ADJUSTED, Code.OUT_OF_COMBAT, f.line("use_when"), f.at("use_when"), "\"out_of_combat\""
                        + " is only for heals and buffs (forms self, ally and allies); read as \"always\"", null);
                useWhen = UseWhen.ALWAYS;
            }
            ValueRange vr = HeroCatalogue.useValue(useWhen);
            double useValue = 0;
            if (vr != null) {
                useValue = number(f, "use_value", vr.min(), vr.max(), vr.dflt(), false, Unit.NONE, null);
            } else {
                f.unused("use_value", "\"use_value\" is not used by use_when \"" + useWhen.id() + "\"");
            }
            int unlock = (int) number(f, sh, "unlock_level", 1);
            if (!f.has("effects")) {
                report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"effects\" is missing: what the "
                        + partNoun.toLowerCase(Locale.ROOT) + " does", partNoun);
                return null;
            }
            FxCtx ctx = new FxCtx(slot, (ult ? "this ultimate" : "this skill"), form.id(), form.friendly(),
                    form == Form.SELF, form == Form.LEAP || form == Form.DASH, false, null);
            List<Effect> fx = effects(f, "effects", 5, ctx);
            if (fx.isEmpty()) {
                report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, o.line(), p, "the " + partNoun.toLowerCase(Locale.ROOT)
                        + " has no effect left that works", partNoun);
                return null;
            }
            Look look = look(f, p, HeroCatalogue.defaultPose(form), ult, HeroCatalogue.fangsForm(form));
            Ability a = new Ability(slot, name, orEmpty(text(f, "description", 200, null)), form, range, radius, count,
                    angle, width, hits, hitInterval, projectile, zoneAt, delay, duration, interval, windup, cooldown,
                    charges, energyGain, chargeRate, useWhen, useValue, unlock, fx, look,
                    text(f, "line", HeroCatalogue.LINE_MAX, null), Numbers.NONE);
            where.put(a, new Pos(p, o.line(), f));
            return a;
        }

        Look look(Fields owner, Path p, Pose dfltPose, boolean ultimate, boolean fangsOk) {
            Node n = owner.node("look");
            if (n == null) {
                return new Look(null, HeroDef.NO_COLOR, null, dfltPose, false, false);
            }
            Path lp = p.field("look");
            if (!(n instanceof Obj o)) {
                report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), lp, "should be an object { \"particle\": ... },"
                        + " not " + shown(n) + ". It was ignored", null);
                return new Look(null, HeroDef.NO_COLOR, null, dfltPose, false, false);
            }
            Fields f = fields(o, lp, HeroCatalogue.LOOK);
            String particle = word(f, "particle", "particle", null, "the element's particle", Mode.OPTIONAL, null);
            int color = color(f, "color", "the hero colour");
            String sound = format >= 2 ? v2Reader.sound(f) : word(f, "sound", "sound", null, null, Mode.OPTIONAL,
                    null);
            Pose pose = HeroCatalogue.enumOf(Pose.class, word(f, "pose", "pose", dfltPose.id(), null, Mode.OPTIONAL,
                    null));
            boolean lightning = bool(f, "lightning", false);
            if (lightning && !ultimate) {
                report(Severity.NOTE, Code.LIGHTNING_ULTIMATE, f.line("lightning"), f.at("lightning"), "the visual"
                        + " lightning is for ultimates only; it was ignored", null);
                lightning = false;
            }
            boolean fangs = bool(f, "fangs", false);
            if (fangs && !fangsOk) {
                report(Severity.NOTE, Code.FANGS_FORM, f.line("fangs"), f.at("fangs"), "the visual fangs rise only"
                        + " along forms line, nova and zone; they were ignored", null);
                fangs = false;
            }
            return new Look(particle, color, sound, pose, lightning, fangs);
        }

        // -------------------------------------------------------------- passives (v1 17)

        Passive passive(Node n, int index) {
            String peek = peekName(n);
            Path p = Path.ROOT.item("passives", index, "passive", peek, false);
            if (!(n instanceof Obj o)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"name\": ...,"
                        + " \"trigger\": ..., \"effects\": [...] }, not " + shown(n), "Passive");
                return null;
            }
            Map<String, Field> sh = format >= 2 ? HeroCatalogue.PASSIVE_2 : HeroCatalogue.PASSIVE;
            Fields f = fields(o, p, sh);
            Need need = new Need(Severity.PART_LEFT_OUT, "Passive");
            String name = text(f, "name", 24, need);
            if (name == null) {
                if (!f.has("name") || f.node("name") instanceof Str) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"name\" is missing. Give the passive a"
                            + " name, for example \"name\": \"Stubborn\"", "Passive");
                }
                return null;
            }
            if (!f.has("trigger")) {
                report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"trigger\" is missing. "
                        + allowed(HeroCatalogue.vocab(vocab("trigger"))), "Passive");
                return null;
            }
            if (format >= 2 && f.node("trigger") instanceof Str yt
                    && HeroCatalogue.YIELD_TRIGGERS.contains(HeroCatalogue.norm(yt.value()))) {
                report(Severity.PART_LEFT_OUT, Code.YIELD_WORD, yt.line(), f.at("trigger"), "heroes do not yield"
                        + " (the owner's rule of 2026-11-13); a passive for the end of a fight uses \"on_escape\" (its"
                        + " escape starts) or \"on_last_stand\" (a hero that will not leave drops under the health"
                        + " where it would have fled)", "Passive");
                return null;
            }
            String tw = word(f, "trigger", vocab("trigger"), null, null, Mode.PART, "Passive");
            if (tw == null) {
                return null;
            }
            Trigger trigger = HeroCatalogue.enumOf(Trigger.class, tw);
            TriggerSpec ts = HeroCatalogue.TRIGGERS.get(trigger);
            String fw = word(f, "form", "passive_form", ts.defaultForm().id(), null, Mode.PART, "Passive");
            if (fw == null) {
                return null;
            }
            PassiveForm form = HeroCatalogue.enumOf(PassiveForm.class, fw);
            if (!ts.forms().contains(form)) {
                List<String> ok = new ArrayList<>();
                for (PassiveForm pf : ts.forms()) {
                    ok.add(pf.id());
                }
                report(Severity.PART_LEFT_OUT, Code.TRIGGER_FORM, f.line("form"), f.at("form"), "form \"" + form.id()
                        + "\" does not fit trigger \"" + trigger.id() + "\". Allowed: " + String.join(", ", ok),
                        "Passive");
                return null;
            }
            double tv = 0;
            if (ts.value() != null) {
                tv = number(f, "trigger_value", ts.value().min(), ts.value().max(), ts.value().dflt(), false,
                        trigger == Trigger.EVERY ? Unit.SECONDS : Unit.PERCENT, null);
            } else {
                f.unused("trigger_value", "\"trigger_value\" is not used by trigger \"" + trigger.id() + "\"");
            }
            String mark = null;
            if (trigger == Trigger.ON_MARK_FULL) {
                if (!f.has("mark")) {
                    report(Severity.PART_LEFT_OUT, Code.MARK_REQUIRED, o.line(), p, "trigger \"on_mark_full\" needs"
                            + " \"mark\": the mark whose full stack sets it off", "Passive");
                    return null;
                }
                mark = mark(f, "mark", need);
                if (mark == null) {
                    return null;
                }
            } else {
                f.unused("mark", "\"mark\" is only used by trigger \"on_mark_full\"");
            }
            int chance = (int) number(f, sh, "chance", 100);
            double cooldown = 1;
            if (trigger == Trigger.EVERY || trigger == Trigger.ALWAYS) {
                f.unused("cooldown", "\"cooldown\" is not used by trigger \"" + trigger.id() + "\""
                        + (trigger == Trigger.EVERY ? " (its trigger_value is the period)" : ""));
            } else {
                cooldown = number(f, sh, "cooldown", 1);
            }
            double radius = HeroCatalogue.passiveRadius(form);
            if (HeroCatalogue.passiveFormUsesRadius(form)) {
                radius = number(f, sh, "radius", radius);
            } else {
                f.unused("radius", "\"radius\" is not used by passive form \"" + form.id() + "\"");
                radius = 0;
            }
            String projectile = "arrow";
            if (form == PassiveForm.SHOT) {
                projectile = word(f, "projectile", "projectile", "arrow", null, Mode.OPTIONAL, null);
            } else {
                f.unused("projectile", "\"projectile\" is only used by passive form \"shot\"");
            }
            Condition cond = null;
            if (f.has("if")) {
                cond = condition(f, "if", need);
                if (cond == null) {
                    return null;
                }
            }
            if (!f.has("effects")) {
                report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"effects\" is missing: what the passive"
                        + " does", "Passive");
                return null;
            }
            FxCtx ctx = new FxCtx(Slot.PASSIVE, "this passive", form.id(), form.friendly(), form == PassiveForm.SELF,
                    false, trigger == Trigger.ALWAYS, trigger);
            List<Effect> fx = effects(f, "effects", 3, ctx);
            if (fx.isEmpty()) {
                report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, o.line(), p, "the passive has no effect left that"
                        + " works", "Passive");
                return null;
            }
            if (mark != null) {
                refs.add(new MarkRef(mark, f.at("mark"), f.line("mark")));
                pendingOwn.add(new MarkRef(mark, f.at("mark"), f.line("mark")));
            }
            Look look = look(f, p, Pose.NONE, false, form == PassiveForm.NOVA);
            int unlock = format >= 2 ? unlockIgnored(f) : (int) number(f, sh, "unlock_level", 1);
            boolean once = format >= 2 && bool(f, "once_per_fight", false);
            Passive ps = new Passive(name, orEmpty(text(f, "description", 200, null)), trigger, tv, mark, chance,
                    cooldown, form, radius, projectile, cond, fx, look, text(f, "line", HeroCatalogue.LINE_MAX, null),
                    unlock, Numbers.NONE, once);
            where.put(ps, new Pos(p, o.line(), f));
            return ps;
        }

        /** {@code on_mark_full} passives whose mark this hero did not apply (yet): checked when the hero is read. */
        final List<MarkRef> pendingOwn = new ArrayList<>();

        /**
         * Format 2: an {@code unlock_level} is read and ignored with a NOTE, since a hero has everything from level 1
         * and only its power grows (HEROES_V2 8, the owner's answer of 2026-11-12). Returns 1.
         */
        int unlockIgnored(Fields f) {
            if (f.has("unlock_level")) {
                report(Severity.NOTE, Code.UNLOCK_IGNORED, f.line("unlock_level"), f.at("unlock_level"), "read and"
                        + " ignored: a hero has all of its abilities from level 1, and only their power grows with its"
                        + " level", null);
            }
            return 1;
        }

        // -------------------------------------------------------------- base abilities (v1 21)

        BaseAbility baseAbility(Node n, int index) {
            Path p = Path.ROOT.item("base_abilities", index, "base ability", peekName(n), false);
            String part = "Base ability";
            if (!(n instanceof Obj o)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"type\": ... },"
                        + " not " + shown(n), part);
                return null;
            }
            Node tn = o.get("type");
            BaseType type = null;
            if (tn instanceof Str ts) {
                String w = resolve(ts.value(), "base_type", null, null, Mode.PART, part, tn.line(), p.field("type"));
                type = w == null ? null : HeroCatalogue.enumOf(BaseType.class, w);
                if (type == null) {
                    return null;
                }
            }
            Map<String, Field> sh = new LinkedHashMap<>(HeroCatalogue.BASE_COMMON);
            if (type != null) {
                sh.putAll(HeroCatalogue.BASES.get(type));
            }
            Fields f = fields(o, p, sh);
            if (type == null) {
                if (!f.has("type")) {
                    report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"type\" is missing. "
                            + allowed(HeroCatalogue.vocab("base_type")), part);
                    return null;
                }
                String w = word(f, "type", "base_type", null, null, Mode.PART, part);
                if (w == null) {
                    return null;
                }
                type = HeroCatalogue.enumOf(BaseType.class, w);
                sh.putAll(HeroCatalogue.BASES.get(type));
                f = fields(o, p, sh);
            }
            String name = text(f, "name", 24, null);
            int unlock = (int) number(f, sh, "unlock_level", 1);
            Need need = new Need(Severity.PART_LEFT_OUT, part);
            switch (type) {
                case AURA -> {
                    Node an = f.node("affix");
                    if (an == null) {
                        report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"affix\" is missing. "
                                + allowed(HeroCatalogue.vocab("affix")), part);
                        return null;
                    }
                    if (an instanceof Str as && HeroCatalogue.REFUSED_AURA_AFFIXES.contains(
                            HeroCatalogue.norm(as.value()))) {
                        report(Severity.PART_LEFT_OUT, Code.AURA_AFFIX, an.line(), f.at("affix"), "\""
                                + HeroCatalogue.norm(as.value()) + "\" is an attribute or scouting affix, not a work"
                                + " affix; use a passive or a stat instead", part);
                        return null;
                    }
                    String affix = word(f, "affix", "affix", null, null, Mode.PART, part);
                    if (affix == null) {
                        return null;
                    }
                    List<String> roles = new ArrayList<>();
                    List<Node> rl = list(f, "roles", 32, "role", "Role", true);
                    for (int i = 0; i < rl.size(); i++) {
                        Node rn = rl.get(i);
                        Path rp = p.item("roles", i, "role", null, true);
                        if (!(rn instanceof Str rs)) {
                            report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, rn.line(), rp, "should be a role id in"
                                    + " quotes, not " + shown(rn), "Role");
                            continue;
                        }
                        String r = resolve(rs.value(), "role", null, null, Mode.PART, "Role", rn.line(), rp);
                        if (r != null && roles.contains(r)) {
                            report(Severity.ADJUSTED, Code.LIST_DUPLICATE, rn.line(), rp, "\"" + r + "\" is listed"
                                    + " twice; once is enough", null);
                        } else if (r != null) {
                            roles.add(r);
                        }
                    }
                    return new HeroDef.Aura(name, unlock, affix, (int) number(f, sh, "stacks", 1),
                            number(f, sh, "radius", 24), List.copyOf(roles.isEmpty()
                            ? HeroCatalogue.ROLE_IDS : roles));
                }
                case MEND -> {
                    return new HeroDef.Mend(name, unlock, (int) number(f, sh, "percent", 5), number(f, sh, "every", 10),
                            number(f, sh, "radius", 16));
                }
                case LOOKOUT -> {
                    return new HeroDef.Lookout(name, unlock, number(f, sh, "radius", 32));
                }
                case RALLY -> {
                    return new HeroDef.Rally(name, unlock, (int) number(f, sh, "percent", 10));
                }
                case DRILL -> {
                    return new HeroDef.Drill(name, unlock, (int) number(f, sh, "xp", 1), number(f, sh, "radius", 16));
                }
                default -> {
                    need(need, Code.WORD_PART, o.line(), p, "unknown base ability");
                    return null;
                }
            }
        }

        // -------------------------------------------------------------- lines (v1 23)

        Map<LineEvent, List<String>> lines(Fields top) {
            Map<LineEvent, List<String>> out = new EnumMap<>(LineEvent.class);
            Node n = top.node("lines");
            if (n == null) {
                return Collections.unmodifiableMap(out);
            }
            Path lp = top.at("lines");
            if (!(n instanceof Obj o)) {
                report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), lp, "should be an object { \"greeting\": [...] },"
                        + " not " + shown(n) + ". The built-in lines are used", null);
                return Collections.unmodifiableMap(out);
            }
            Vocab ev = HeroCatalogue.vocab(vocab("line_event"));
            boolean v2 = format >= 2;
            int maxLines = v2 ? HeroCatalogue.MAX_LINES_PER_EVENT_2 : HeroCatalogue.MAX_LINES_PER_EVENT;
            for (Member m : o.members()) {
                if (m.key().startsWith("_") || nothing(m, lp)) {
                    continue;
                }
                Path ep = lp.field(m.key());
                String w = HeroCatalogue.norm(m.key());
                if (v2 && HeroCatalogue.YIELD_EVENTS.contains(w)) {
                    report(Severity.NOTE, Code.YIELD_EVENT, m.line(), ep, "heroes do not yield; use \"escape\","
                            + " \"last_stand\" or \"fallen\". These lines were left out", null);
                    continue;
                }
                String e = ev.resolve(w);
                if (e == null) {
                    report(Severity.PART_LEFT_OUT, Code.LINE_EVENT, m.line(), ep, "\"" + m.key() + "\" is not a line"
                            + " event. " + allowed(ev) + "." + Problems.didYouMean(w, ev.words), "Event");
                    continue;
                }
                if (!e.equals(w)) {
                    report(Severity.ADJUSTED, Code.SYNONYM, m.line(), ep, "\"" + m.key() + "\" was read as \"" + e
                            + "\"", null);
                }
                LineEvent event = HeroCatalogue.enumOf(LineEvent.class, e);
                if (v2 && event == LineEvent.UNLOCK) {
                    report(Severity.NOTE, Code.UNLOCK_IGNORED, m.line(), ep, "the \"unlock\" event never fires for a"
                            + " format-2 hero (nothing unlocks: it has everything from level 1); these lines were left"
                            + " out", null);
                    continue;
                }
                List<Node> items;
                if (m.value() instanceof Arr a) {
                    items = a.items();
                } else {
                    report(Severity.ADJUSTED, Code.LIST_WRAPPED, m.value().line(), ep, "should be a list [ ... ];"
                            + " read as a list of one", null);
                    items = List.of(m.value());
                }
                List<String> lines = out.computeIfAbsent(event, k -> new ArrayList<>());
                for (int i = 0; i < items.size(); i++) {
                    Node ln = items.get(i);
                    Path p = ep.item("", i, "line", null, false);
                    p = new Path(p.friendly(), ep.machine() + "[" + i + "]");
                    if (lines.size() >= maxLines) {
                        report(Severity.PART_LEFT_OUT, Code.LIST_TOO_LONG, ln.line(), p, "an event has at most "
                                + maxLines + " lines", "Line");
                        continue;
                    }
                    String t = line(ln, p, event);
                    if (t != null) {
                        lines.add(t);
                    }
                }
            }
            Map<LineEvent, List<String>> frozen = new EnumMap<>(LineEvent.class);
            for (Map.Entry<LineEvent, List<String>> e : out.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    frozen.put(e.getKey(), List.copyOf(e.getValue()));
                }
            }
            return Collections.unmodifiableMap(frozen);
        }

        String line(Node n, Path p, LineEvent event) {
            if (!(n instanceof Str s)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be text in quotes, not "
                        + shown(n), "Line");
                return null;
            }
            String raw = s.value();
            if (raw.contains("\n") || raw.contains("\r")) {
                report(Severity.ADJUSTED, Code.LINE_BREAK, n.line(), p, "a line has no line breaks; they became"
                        + " spaces", null);
                raw = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
            }
            String t = clean(raw, n.line(), p);
            if (t.isEmpty()) {
                return null;
            }
            if (t.length() > HeroCatalogue.LINE_MAX) {
                report(Severity.ADJUSTED, Code.TEXT_CUT, n.line(), p, "is " + t.length() + " characters long; at most "
                        + HeroCatalogue.LINE_MAX + ". Cut to " + HeroCatalogue.LINE_MAX, null);
                t = cut(t, HeroCatalogue.LINE_MAX);
            }
            boolean v2 = format >= 2;
            List<String> ok = (v2 ? HeroCatalogue.PLACEHOLDERS_2 : HeroCatalogue.PLACEHOLDERS).get(event);
            Set<String> all = v2 ? HeroCatalogue.ALL_PLACEHOLDERS_2 : HeroCatalogue.ALL_PLACEHOLDERS;
            Matcher m = PLACEHOLDER.matcher(t);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String ph = m.group(1).toLowerCase(Locale.ROOT);
                if (v2 && ph.equals("player") && ok.contains("enemy") && !ok.contains("player")) {
                    // a fight line names the one it faces, a player or an NPC alike (HEROES_V2 6.15)
                    report(Severity.ADJUSTED, Code.EQUAL_TREATMENT, n.line(), p, "{player} was read as {enemy}: heroes"
                            + " treat players and NPCs alike, and a fight line names the one it faces, a player or an"
                            + " NPC", null);
                    m.appendReplacement(sb, Matcher.quoteReplacement("{enemy}"));
                    continue;
                }
                if (!all.contains(ph)) {
                    report(Severity.PART_LEFT_OUT, Code.LINE_PLACEHOLDER, n.line(), p, "{" + ph + "} is not a"
                            + " placeholder. Allowed for \"" + event.id() + "\": {" + String.join("}, {", ok) + "}",
                            "Line");
                    return null;
                }
                if (!ok.contains(ph)) {
                    report(Severity.NOTE, Code.LINE_SOMEONE, n.line(), p, "{" + ph + "} is not known when \""
                            + event.id() + "\" is said; it reads \"someone\". Allowed: {" + String.join("}, {", ok)
                            + "}", null);
                    m.appendReplacement(sb, "someone");
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement("{" + ph + "}"));
                }
            }
            m.appendTail(sb);
            return sb.toString();
        }

        // -------------------------------------------------------------- conditions (v1 11) and amounts (12)

        Condition condition(Fields owner, String name, Need need) {
            Node n = owner.node(name);
            Path p = owner.at(name);
            if (format >= 2) {
                return v2Reader.condition(n, p, need);
            }
            if (!(n instanceof Obj o)) {
                need(need, Code.WRONG_TYPE, n.line(), p, "should be an object { \"condition\": ... }, not " + shown(n));
                return null;
            }
            Fields f = fields(o, p, HeroCatalogue.CONDITION);
            if (!f.has("condition")) {
                need(need, Code.MISSING, o.line(), p, "\"condition\" is missing. "
                        + allowed(HeroCatalogue.vocab("condition")));
                return null;
            }
            String w = word(f, "condition", "condition", null, null, Mode.PART, need.part);
            if (w == null) {
                return null;
            }
            ConditionType t = HeroCatalogue.enumOf(ConditionType.class, w);
            int value = 0;
            String mark = null;
            HeroDef.TargetKind kind = null;
            if (HeroCatalogue.conditionNeedsValue(t)) {
                if (!f.has("value")) {
                    need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"" + t.id() + "\" needs \"value\": a health"
                            + " share in percent (1 to 99)");
                    return null;
                }
                double v = number(f, "value", 1, 99, Double.NaN, true, Unit.PERCENT, need);
                if (Double.isNaN(v)) {
                    return null;
                }
                value = (int) v;
            } else if (HeroCatalogue.conditionNeedsMark(t)) {
                if (!f.has("mark")) {
                    need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"" + t.id() + "\" needs \"mark\": the mark"
                            + " to look for");
                    return null;
                }
                mark = mark(f, "mark", need);
                if (mark == null) {
                    return null;
                }
                refs.add(new MarkRef(mark, f.at("mark"), f.line("mark")));
                value = (int) number(f, "value", 1, 10, 1, true, Unit.NONE, null);
            } else {
                f.unused("value", "\"value\" is not used by condition \"" + t.id() + "\"");
            }
            if (!HeroCatalogue.conditionNeedsMark(t)) {
                f.unused("mark", "\"mark\" is not used by condition \"" + t.id() + "\"");
            }
            if (t == ConditionType.TARGET_IS) {
                if (!f.has("kind")) {
                    need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"target_is\" needs \"kind\". "
                            + allowed(HeroCatalogue.vocab("target_kind")));
                    return null;
                }
                String k = word(f, "kind", "target_kind", null, null, Mode.PART, need.part);
                if (k == null) {
                    return null;
                }
                kind = HeroCatalogue.enumOf(HeroDef.TargetKind.class, k);
            } else {
                f.unused("kind", "\"kind\" is only used by condition \"target_is\"");
            }
            return new Condition(t, value, mark, kind);
        }

        Amount amount(Fields owner, String name, Need need) {
            Node n = owner.node(name);
            Path p = owner.at(name);
            Map<String, Field> sh = HeroCatalogue.AMOUNT;
            if (n instanceof Num || n instanceof Str) {
                double v = number(owner, name, 0, 30, Double.NaN, false, Unit.HEALTH, need);
                return Double.isNaN(v) ? null : Amount.of(v);
            }
            if (!(n instanceof Obj o)) {
                need(need, Code.WRONG_TYPE, n.line(), p, "should be a number of health points or an object of parts"
                        + " such as { \"attack_percent\": 120 }, not " + shown(n));
                return null;
            }
            Fields f = fields(o, p, sh);
            if (f.got.isEmpty()) {
                need(need, Code.MISSING, o.line(), p, "the amount has no parts. Give a number of health points or"
                        + " parts such as \"attack_percent\"");
                return null;
            }
            String mark = null;
            double per = 0;
            if (f.has("mark") != f.has("per_mark_stack")) {
                String has = f.has("mark") ? "mark" : "per_mark_stack";
                report(Severity.ADJUSTED, Code.AMOUNT_MARK, f.line(has), f.at(has), "\"per_mark_stack\" and \"mark\""
                        + " go together (health points per stack of that mark); this part was ignored", null);
            } else if (f.has("mark")) {
                mark = mark(f, "mark", need);
                if (mark == null) {
                    return null;
                }
                refs.add(new MarkRef(mark, f.at("mark"), f.line("mark")));
                per = number(f, sh, "per_mark_stack", 0);
            }
            return new Amount(number(f, sh, "base", 0), number(f, sh, "attack_percent", 0),
                    number(f, sh, "max_health_percent", 0), number(f, sh, "armor_percent", 0),
                    number(f, sh, "target_max_health_percent", 0), number(f, sh, "target_missing_health_percent", 0),
                    mark, per);
        }

        // -------------------------------------------------------------- effects (v1 13)

        List<Effect> effects(Fields owner, String name, int max, FxCtx ctx) {
            boolean shown = !name.equals("effects");
            List<Node> nodes = list(owner, name, max, "effect", "Effect", shown);
            List<Effect> out = new ArrayList<>();
            int damage = 0, moves = 0;
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                Path p = owner.path.item(name, i, "effect", null, shown);
                Effect e = effect(n, p, ctx);
                if (e == null) {
                    continue;
                }
                if (e.type() == EffectType.DAMAGE && ++damage > 2) {
                    report(Severity.PART_LEFT_OUT, Code.TWO_DAMAGE, n.line(), p, "at most 2 \"damage\" effects in one"
                            + " list; put the damage together in one Amount", "Effect");
                    continue;
                }
                if (e.type() == EffectType.MOVE_SELF && ++moves > 1) {
                    report(Severity.PART_LEFT_OUT, Code.ONE_MOVE, n.line(), p, "at most 1 \"move_self\" in one list",
                            "Effect");
                    continue;
                }
                where.put(e, new Pos(p, n.line(), effectFields));
                out.add(e);
            }
            return List.copyOf(out);
        }

        /** The fields of the effect {@link #effect} read last, for the balance pass's exact paths. */
        Fields effectFields;

        String label(Effect e) {
            return switch (e) {
                case HeroDef.PotionEffect x -> x.id();
                case HeroDef.StatMod x -> x.stat().id() + " " + (x.percent() > 0 ? "+" : "") + x.percent() + "%";
                default -> e.type().id();
            };
        }

        Effect effect(Node n, Path p, FxCtx ctx) {
            String part = "Effect";
            Need need = new Need(Severity.PART_LEFT_OUT, part);
            if (!(n instanceof Obj o)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"type\": ... },"
                        + " not " + shown(n), part);
                return null;
            }
            // the type decides the field table
            Member tm = typeMember(o);
            if (tm == null) {
                report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"type\" is missing. "
                        + allowed(HeroCatalogue.vocab("effect_type")), part);
                return null;
            }
            if (!(tm.value() instanceof Str ts)) {
                report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, tm.value().line(), p.field("type"), "should be an"
                        + " effect type in quotes, not " + shown(tm.value()), part);
                return null;
            }
            String presetPotion = null;
            String tw = HeroCatalogue.norm(ts.value());
            EffectType type;
            String typeVocab = vocab("effect_type");
            if (HeroCatalogue.vocab(typeVocab).resolve(tw) == null && (HeroCatalogue.HELPFUL_POTIONS.containsKey(tw)
                    || HeroCatalogue.HARMFUL_POTIONS.containsKey(tw))) {
                report(Severity.ADJUSTED, Code.SYNONYM, ts.line(), p.field("type"), "\"" + ts.value() + "\" was read"
                        + " as \"type\": \"effect\", \"id\": \"" + tw + "\"", null);
                type = EffectType.EFFECT;
                presetPotion = tw;
            } else {
                String w = resolve(ts.value(), typeVocab, null, null, Mode.PART, part, ts.line(), p.field("type"));
                if (w == null) {
                    return null;
                }
                type = HeroCatalogue.enumOf(EffectType.class, w);
            }
            if (ctx.always && type != EffectType.STAT) {
                report(Severity.PART_LEFT_OUT, Code.ALWAYS_STAT, o.line(), p, "an \"always\" passive holds stat"
                        + " changes only: \"stat\" effects without \"seconds\", not \"" + type.id() + "\"", part);
                return null;
            }
            Map<String, Field> sh = (format >= 2 ? EFFECT_SHAPES_2 : EFFECT_SHAPES).get(type);
            Map<String, String> elsewhere = new HashMap<>();
            for (String k : (format >= 2 ? ALL_EFFECT_FIELDS_2 : ALL_EFFECT_FIELDS).keySet()) {
                if (!sh.containsKey(k)) {
                    elsewhere.put(k, "is not used by the \"" + type.id() + "\" effect");
                }
            }
            Fields f = new Fields(o, p, sh, elsewhere);
            effectFields = f;
            // common fields
            boolean toGiven = f.has("to");
            To to = HeroCatalogue.enumOf(To.class, word(f, "to", "to", "targets", null, Mode.OPTIONAL, null));
            double radius = 8;
            if (to == To.ALLIES || to == To.ALLY) {
                radius = number(f, sh, "radius", 8);
            } else {
                f.unused("radius", "\"radius\" is only used with \"to\": \"allies\" or \"ally\"");
            }
            int chance = (int) number(f, sh, "chance", 100);
            Condition cond = null;
            if (f.has("if")) {
                cond = condition(f, "if", need);
                if (cond == null) {
                    return null;
                }
            }
            Common c = new Common(to, radius, chance, cond);
            Effect e = build(type, f, sh, c, p, o, need, presetPotion, ctx);
            if (e == null) {
                return null;
            }
            Effect r = recipients(e, toGiven, ctx, p, o.line());
            if (r != null && ctx.trigger == Trigger.ON_ESCAPE && r.c().to() != To.SELF && r.c().to() != To.TARGETS) {
                // an escaping hero starts no attack and helps nobody on the way: self and look effects only (6.6)
                report(Severity.PART_LEFT_OUT, Code.ESCAPE_SELF, o.line(), p, "an \"on_escape\" passive acts on the"
                        + " hero itself only (an escaping hero starts nothing against anyone); \"to\": \""
                        + r.c().to().id() + "\" is not allowed", "Effect");
                return null;
            }
            return r;
        }

        Member typeMember(Obj o) {
            Member exact = o.member("type");
            if (exact != null) {
                return exact;
            }
            for (Member m : o.members()) {
                String nk = HeroCatalogue.norm(m.key());
                if (!m.key().startsWith("_") && (nk.equals("type") || Problems.distance(nk, "type") <= 1)
                        && !(format >= 2 ? ALL_EFFECT_FIELDS_2 : ALL_EFFECT_FIELDS).containsKey(nk)) {
                    return m;
                }
            }
            return null;
        }

        boolean missing(Fields f, String field, Obj o, Path p, Need need, String what) {
            if (f.has(field)) {
                return false;
            }
            need(need, Code.MISSING, o.line(), p, "\"" + field + "\" is missing" + (what == null ? "" : ": " + what));
            return true;
        }

        Effect build(EffectType type, Fields f, Map<String, Field> sh, Common c, Path p, Obj o, Need need,
                     String presetPotion, FxCtx ctx) {
            switch (type) {
                case DAMAGE, DOT, HEAL, SHIELD -> {
                    if (missing(f, "amount", o, p, need, "how many health points, for example \"amount\": 6")) {
                        return null;
                    }
                    Amount a = amount(f, "amount", need);
                    if (a == null) {
                        return null;
                    }
                    return switch (type) {
                        case DAMAGE -> new HeroDef.Damage(c, a, elementOf(f), (int) number(f, sh, "drain", 0));
                        case DOT -> new HeroDef.Dot(c, a, number(f, sh, "seconds", 4), elementOf(f),
                                (int) number(f, sh, "max_stacks", 1));
                        case HEAL -> new HeroDef.Heal(c, a);
                        default -> new HeroDef.Shield(c, a, number(f, sh, "seconds", 10));
                    };
                }
                case EFFECT -> {
                    String id = presetPotion;
                    if (f.has("id")) {
                        Node idn = f.node("id");
                        if (idn instanceof Str s && HeroCatalogue.REFUSED_POTIONS.containsKey(HeroCatalogue.norm(s.value()))) {
                            String why = HeroCatalogue.REFUSED_POTIONS.get(HeroCatalogue.norm(s.value()));
                            report(Severity.PART_LEFT_OUT, Code.POTION_REFUSED, idn.line(), f.at("id"), "\""
                                    + HeroCatalogue.norm(s.value()) + "\" is not available to heroes"
                                    + (why.isEmpty() ? "" : "; " + why), "Effect");
                            return null;
                        }
                        id = word(f, "id", "potion", null, null, Mode.PART, "Effect");
                        if (id == null) {
                            return null;
                        }
                    } else if (id == null) {
                        missing(f, "id", o, p, need, "which potion effect, for example \"id\": \"slowness\"");
                        return null;
                    }
                    if (missing(f, "seconds", o, p, need, "how long it lasts")) {
                        return null;
                    }
                    double sec = number(f, sh, "seconds", need);
                    if (Double.isNaN(sec)) {
                        return null;
                    }
                    boolean helpful = HeroCatalogue.HELPFUL_POTIONS.containsKey(id);
                    return new HeroDef.PotionEffect(c, id, (int) number(f, sh, "level", 1), sec, helpful);
                }
                case STAT -> {
                    if (missing(f, "stat", o, p, need, HeroCatalogue.vocab("stat").allowed())
                            || missing(f, "percent", o, p, need, "a whole percent, for example 20 or -20")) {
                        return null;
                    }
                    String st = word(f, "stat", "stat", null, null, Mode.PART, "Effect");
                    double pc = number(f, sh, "percent", need);
                    if (st == null || Double.isNaN(pc)) {
                        return null;
                    }
                    if ((int) pc == 0) {
                        report(Severity.PART_LEFT_OUT, Code.STAT_ZERO, f.line("percent"), f.at("percent"), "a change of"
                                + " 0% does nothing", "Effect");
                        return null;
                    }
                    double sec = 8;
                    if (ctx.always) {
                        if (f.has("seconds")) {
                            report(Severity.PART_LEFT_OUT, Code.ALWAYS_STAT, f.line("seconds"), f.at("seconds"), "an"
                                    + " \"always\" passive holds its stats as long as its condition holds; leave"
                                    + " \"seconds\" out", "Effect");
                            return null;
                        }
                        sec = 0;
                    } else {
                        sec = number(f, sh, "seconds", 8);
                    }
                    return new HeroDef.StatMod(c, HeroCatalogue.enumOf(Stat.class, st), (int) pc, sec,
                            (int) number(f, sh, "max_stacks", 1));
                }
                case KNOCKBACK -> {
                    return new HeroDef.Knockback(c, number(f, sh, "strength", 2), number(f, sh, "up", 0));
                }
                case PULL -> {
                    return new HeroDef.Pull(c, number(f, sh, "strength", 1));
                }
                case IGNITE -> {
                    return new HeroDef.Ignite(c, number(f, sh, "seconds", 4));
                }
                case FREEZE -> {
                    return new HeroDef.Freeze(c, number(f, sh, "seconds", 3));
                }
                case STUN -> {
                    if (ctx.slot == Slot.ON_HIT) {
                        report(Severity.PART_LEFT_OUT, Code.STUN_ON_HIT, o.line(), p, "a stun is not allowed in "
                                + ctx.owner + " (it would stun on every hit); use \"every_nth_hit\"", "Effect");
                        return null;
                    }
                    return new HeroDef.Stun(c, number(f, sh, "seconds", 1));
                }
                case TAUNT -> {
                    return new HeroDef.Taunt(c, number(f, sh, "seconds", 3));
                }
                case CLEANSE -> {
                    return new HeroDef.Cleanse(c);
                }
                case DISPEL -> {
                    return new HeroDef.Dispel(c);
                }
                case ENERGY -> {
                    if (ctx.trigger == Trigger.ON_ULTIMATE) {
                        report(Severity.PART_LEFT_OUT, Code.ENERGY_ON_ULTIMATE, o.line(), p, "an \"on_ultimate\""
                                + " passive cannot give energy (it would refill the ultimate it answers)", "Effect");
                        return null;
                    }
                    if (missing(f, "amount", o, p, need, "how much energy (5 to 30)")) {
                        return null;
                    }
                    double a = number(f, sh, "amount", need);
                    return Double.isNaN(a) ? null : new HeroDef.Energy(c, (int) a);
                }
                case COOLDOWN_CUT -> {
                    if (missing(f, "seconds", o, p, need, "how many seconds (1 to 10)")) {
                        return null;
                    }
                    double s = number(f, sh, "seconds", need);
                    return Double.isNaN(s) ? null : new HeroDef.CooldownCut(c, s);
                }
                case MOVE_SELF -> {
                    if (ctx.moveForm) {
                        report(Severity.PART_LEFT_OUT, Code.MOVE_IN_LEAP, o.line(), p, "\"move_self\" cannot be used"
                                + " in a \"leap\" or a \"dash\" (the hero is already moving)", "Effect");
                        return null;
                    }
                    return new HeroDef.MoveSelf(c, HeroCatalogue.enumOf(HeroDef.Direction.class, word(f, "direction",
                            "direction", "back", null, Mode.OPTIONAL, null)), number(f, sh, "distance", 4));
                }
                case MARK -> {
                    if (missing(f, "mark", o, p, need, "the mark's id, for example \"mark\": \"pinned\"")) {
                        return null;
                    }
                    String m = mark(f, "mark", need);
                    if (m == null) {
                        return null;
                    }
                    applied.add(m);
                    return new HeroDef.Mark(c, m, (int) number(f, sh, "stacks", 1), (int) number(f, sh, "max_stacks",
                            5), number(f, sh, "seconds", 10), bool(f, "glow", false));
                }
                case CLEAR_MARKS -> {
                    if (missing(f, "mark", o, p, need, "the mark's id")) {
                        return null;
                    }
                    String m = mark(f, "mark", need);
                    if (m == null) {
                        return null;
                    }
                    refs.add(new MarkRef(m, f.at("mark"), f.line("mark")));
                    return new HeroDef.ClearMarks(c, m);
                }
                case EMPOWER -> {
                    if (format >= 2) {
                        return v2Reader.empower(f, sh, c, p, o, need);
                    }
                    if (!hasEmpowered) {
                        report(Severity.PART_LEFT_OUT, Code.EMPOWER_NO_STATE, o.line(), p, "\"empower\" needs an"
                                + " \"empowered\" state at the top level of the hero", "Effect");
                        return null;
                    }
                    if (missing(f, "seconds", o, p, need, "how long the state lasts (3 to 20)")) {
                        return null;
                    }
                    double s = number(f, sh, "seconds", need);
                    return Double.isNaN(s) ? null : new HeroDef.Empower(c, s);
                }
                case SUMMON -> {
                    if (ctx.slot != Slot.SKILL && ctx.slot != Slot.ULTIMATE) {
                        report(Severity.PART_LEFT_OUT, Code.SUMMON_SLOT, o.line(), p, "\"summon\" only works in skills"
                                + " and the ultimate", "Effect");
                        return null;
                    }
                    if (!opt.summons) {
                        report(Severity.PART_LEFT_OUT, Code.SUMMONS_OFF, o.line(), p, "summons are switched off on"
                                + " this server (heroes.summons = false)", "Effect");
                        return null;
                    }
                    if (missing(f, "creature", o, p, need, HeroCatalogue.vocab("creature").allowed())) {
                        return null;
                    }
                    String cr = word(f, "creature", "creature", null, null, Mode.PART, "Effect");
                    if (cr == null) {
                        return null;
                    }
                    return new HeroDef.Summon(c, HeroCatalogue.enumOf(HeroDef.Creature.class, cr),
                            (int) number(f, sh, "count", 1), number(f, sh, "seconds", 20), text(f, "name", 24, null));
                }
                case GUARD_BREAK -> {
                    return new HeroDef.GuardBreak(c, number(f, sh, "seconds", 5));
                }
                case POISE -> {
                    if (missing(f, "amount", o, p, need, "poise damage, 10 to 60")) {
                        return null;
                    }
                    double a = number(f, sh, "amount", need);
                    return Double.isNaN(a) ? null : new HeroDef.Poise(c, (int) a);
                }
                case SILENCE -> {
                    return new HeroDef.Silence(c, number(f, sh, "seconds", 2));
                }
                case TETHER -> {
                    return new HeroDef.Tether(c, number(f, sh, "seconds", 4), number(f, sh, "max", 6));
                }
                case BARRIER -> {
                    String focus = word(f, "focus", "add", null, null, Mode.OPTIONAL, null);
                    return new HeroDef.Barrier(c, (int) number(f, sh, "hits", 4), number(f, sh, "seconds", 8),
                            focus == null ? null : HeroCatalogue.enumOf(HeroDef.AddKind.class, focus));
                }
                case SPAWN_ADD -> {
                    if (ctx.slot != Slot.SKILL && ctx.slot != Slot.ULTIMATE) {
                        report(Severity.PART_LEFT_OUT, Code.SUMMON_SLOT, o.line(), p, "\"spawn_add\" only works in"
                                + " moves and the ultimate", "Effect");
                        return null;
                    }
                    if (missing(f, "add", o, p, need, HeroCatalogue.vocab("add").allowed())) {
                        return null;
                    }
                    String a = word(f, "add", "add", null, null, Mode.PART, "Effect");
                    if (a == null) {
                        return null;
                    }
                    return new HeroDef.SpawnAdd(c, HeroCatalogue.enumOf(HeroDef.AddKind.class, a),
                            (int) number(f, sh, "count", 1));
                }
                case CALL_GUARDS -> {
                    return new HeroDef.CallGuards(c, (int) number(f, sh, "count", 2));
                }
                default -> throw new IllegalStateException(type.name());
            }
        }

        Element elementOf(Fields f) {
            return HeroCatalogue.enumOf(Element.class, word(f, "element", "element", element.id(),
                    "the hero's element", Mode.OPTIONAL, null));
        }

        /** The recipient classes of v1 13 and 10.2. */
        Effect recipients(Effect e, boolean toGiven, FxCtx ctx, Path p, int line) {
            Reach reach = HeroCatalogue.reach(e);
            To to = e.c().to();
            String lbl = "\"" + label(e) + "\"";
            String inForm = ctx.owner + (ctx.formId == null ? "" : " (form \"" + ctx.formId + "\")");
            switch (reach) {
                case ENEMY -> {
                    if (to != To.TARGETS) {
                        report(Severity.PART_LEFT_OUT, Code.ENEMY_ONLY, line, p, lbl + " only reaches enemies, but"
                                + " \"to\": \"" + to.id() + "\" sends it to the hero's side. Leave \"to\" out",
                                "Effect");
                        return null;
                    }
                    if (ctx.friendly) {
                        report(Severity.PART_LEFT_OUT, Code.ENEMY_ONLY, line, p, lbl + " only reaches enemies, but "
                                + ctx.owner + " affects allies (form \"" + ctx.formId + "\"). Put it in an ability"
                                + " that hits enemies", "Effect");
                        return null;
                    }
                    return e;
                }
                case ALLY -> {
                    if (e instanceof HeroDef.PotionEffect pe && HeroCatalogue.SELF_ONLY_POTIONS.contains(pe.id())) {
                        boolean toHero = to == To.SELF || to == To.TARGETS && (ctx.receiverIsHero || !ctx.friendly);
                        if (!toHero) {
                            report(Severity.PART_LEFT_OUT, Code.SELF_POTION, line, p, lbl + " is for the hero only. Use"
                                    + " \"to\": \"self\" or a \"self\" form", "Effect");
                            return null;
                        }
                    }
                    if (!ctx.friendly && to == To.TARGETS) {
                        report(Severity.NOTE, Code.ALLY_TO_SELF, line, p, lbl + " only reaches allies, so in " + inForm
                                + " it goes to the hero. Write \"to\": \"self\", \"ally\" or \"allies\" to say who gets"
                                + " it", null);
                        return e.with(e.c().withTo(To.SELF));
                    }
                    return e;
                }
                case SELF -> {
                    if (toGiven && to != To.SELF && to != To.TARGETS) {
                        report(Severity.NOTE, Code.SELF_ONLY_TO, line, p, lbl + " always acts on the hero; \"to\": \""
                                + to.id() + "\" was ignored", null);
                    }
                    return e.with(e.c().withTo(To.SELF));
                }
                default -> {
                    // mark and clear_marks: enemies, or the hero with "to": "self"
                    if (to == To.SELF || to == To.TARGETS && ctx.receiverIsHero) {
                        return e.with(e.c().withTo(To.SELF));
                    }
                    if (to != To.TARGETS || ctx.friendly) {
                        report(Severity.PART_LEFT_OUT, Code.MARK_RECEIVER, line, p, "marks go on enemies, or on the"
                                + " hero with \"to\": \"self\"; " + (to != To.TARGETS ? "\"to\": \"" + to.id()
                                + "\" is not allowed" : ctx.owner + " affects allies (form \"" + ctx.formId
                                + "\")"), "Effect");
                        return null;
                    }
                    return e;
                }
            }
        }

        /** After the whole hero is read: {@code on_mark_full} passives whose mark the hero never applies. */
        void finishOwnMarks() {
            for (MarkRef m : pendingOwn) {
                if (!applied.contains(m.mark)) {
                    report(Severity.NOTE, Code.MARK_NOT_OWN, m.line, m.path, "\"on_mark_full\" fires when a mark this"
                            + " hero applies fills up, but no effect of this hero applies \"" + m.mark + "\"", null);
                }
            }
        }
    }
}
