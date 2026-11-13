package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The hero statuses one living entity carries (HERO_FORMAT.md 13 and 19; HEROES_V2 15.2 A3): named marks with stacks,
 * damage over time, timed stat modifiers, a shield and a freeze hold, each with its expiry as a game time. Any entity
 * may carry them (a player, an NPC, a hero, a monster), with the same rules for all: the game side keeps one book per
 * entity ({@code hero/HeroStatus}), saves it with the entity and applies what it says (attribute modifiers, absorption,
 * frozen ticks, the DoT damage).
 *
 * <ul>
 * <li><b>Marks</b> are global ids (v1 19): one entry per id, whoever applied it; new stacks add up to the mark's
 * {@code max_stacks} and refresh its time. Reaching the maximum is reported once ({@link MarkResult#full}) for the
 * {@code on_mark_full} passives (A4).</li>
 * <li><b>DoTs</b> are kept per source (the hero) and key (which effect): with {@code max_stacks} 1 a new application
 * refreshes, above 1 it adds a stack (damage x stacks, time refreshed). They tick once a second from the application;
 * {@link #dueDots} hands the game each source's sum, which {@link HeroRules#dotTick} holds to
 * {@code heroes.maxDotPerSecond}.</li>
 * <li><b>Stat modifiers</b> per source and key, refreshed or stacked the same way; seconds 0 is held until removed.
 * {@link #statPercent} is their sum per stat.</li>
 * <li><b>Control</b> (task A4, HEROES_V2 6.14-6.15): a stun stops its carrier for its seconds, whoever carries it, and
 * leaves an immunity for twice as long after it ends ({@link BossRules#stunImmuneUntil}): the immunity belongs to the
 * carrier, so no second stun from anyone lands inside it. A taunt names the hero it binds the carrier to.</li>
 * <li>Format 2's effects (task A5, HEROES_V2 6.6), the same on every carrier: a <b>silence</b> (a hero cannot start
 * moves; nobody else has moves to stop), a <b>tether</b> to the spot where it landed (pulled back past its length), a
 * <b>broken guard</b> (no shield goes up until it ends), and a hero's <b>barrier</b> (it blocks all damage until it has
 * taken its hits; with a living focus add its last hit holds).</li>
 * <li>At most {@link #MAX_STATUSES} entries (v1 19); the oldest goes first (never a stun or its immunity, so the cap
 * cannot be used to clear a stun).</li>
 * </ul>
 * Pure, unit-tested ({@code StatusBookTest}).
 */
public final class StatusBook {
    /** At most this many statuses per entity; the oldest goes first (v1 19). */
    public static final int MAX_STATUSES = 32;
    /** A DoT ticks once a second. */
    public static final int DOT_PERIOD = 20;
    /** A held status (an {@code always} passive's stat): it never expires by time. */
    public static final long HELD = Long.MAX_VALUE;

    public enum Kind {
        MARK, DOT, STAT, SHIELD, FREEZE,
        /** stopped: cannot move, attack, use items or start moves (A4) */
        STUN,
        /** no new stun lands until it runs out (6.14: twice the stun's length after it ends) */
        IMMUNE,
        /** bound to the hero named by {@code source} (A4, HEROES_V2 6.15) */
        TAUNT,
        /** cannot start moves (A5, 6.6) */
        SILENCE,
        /** pulled back toward its anchor ({@code x, y, z}) past {@code amount} blocks (A5) */
        TETHER,
        /** its shield is down and stays down (A5: {@code guard_break}) */
        GUARD,
        /** a hero's barrier: blocks all damage until {@code stacks} more hits (A5) */
        BARRIER
    }

    /**
     * One status. {@code id}: the mark id, the DoT's or the stat's key; {@code source}: who applied it (a hero's id
     * string, "" for unknown); {@code amount}: a DoT's damage per second per stack, a stat's percent per stack, a
     * shield's absorption; {@code enemy}: applied by someone hostile to the carrier (a cleanse removes such marks).
     */
    public static final class Entry {
        public final Kind kind;
        public final String id;
        public final String source;
        public Element element;
        public Stat stat;
        public double amount;
        public int stacks = 1;
        public int maxStacks = 1;
        public long expiresAt;
        public long nextTickAt;
        public boolean glow;
        public boolean enemy;
        /** A tether's anchor. */
        public double x, y, z;
        long order;

        public Entry(Kind kind, String id, String source) {
            this.kind = kind;
            this.id = id == null ? "" : id;
            this.source = source == null ? "" : source;
        }

        public long order() {
            return order;
        }

        /** Whether it is still on at {@code now}. */
        public boolean active(long now) {
            return now < expiresAt;
        }

        boolean same(Kind k, String i, String s) {
            return kind == k && id.equals(i) && source.equals(s);
        }
    }

    /** A mark after an application: its stacks now, and whether this application filled it. */
    public record MarkResult(int stacks, boolean full) {
    }

    /** One second of a source's DoTs: its element (the strongest entry's) and the per-second damage of them all. */
    public record DotDue(String source, Element element, double perSecond) {
    }

    /**
     * What an {@link #expire} removed: whether stats changed, the shield that ran out (its amount), a freeze, marks, a
     * stun or a taunt ending.
     */
    public record Expired(boolean stats, double shield, boolean freeze, boolean marks, boolean stun, boolean taunt) {
        public static final Expired NONE = new Expired(false, 0, false, false, false, false);

        public Expired(boolean stats, double shield, boolean freeze, boolean marks) {
            this(stats, shield, freeze, marks, false, false);
        }

        public boolean any() {
            return stats || shield > 0 || freeze || marks || stun || taunt;
        }
    }

    /**
     * What a {@link #dispel} took: whether a stat changed, the shield it removed (its amount; 0: none) and whether a
     * barrier went (A5).
     */
    public record Dispelled(boolean stats, double shield, boolean barrier) {
        public Dispelled(boolean stats, double shield) {
            this(stats, shield, false);
        }

        public boolean any() {
            return stats || shield > 0 || barrier;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private long nextOrder;
    private boolean statsDirty;

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Every entry, oldest first (read-only; for saving and inspection). */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Puts a saved entry back (loading): it keeps its expiry; the order follows the saved order. */
    public void restore(Entry e) {
        e.order = nextOrder++;
        entries.add(e);
        if (e.kind == Kind.STAT) {
            statsDirty = true;
        }
        trim();
    }

    /** Whether a stat entry was added, changed or removed since the last call (the game re-applies its attributes). */
    public boolean consumeStatsDirty() {
        boolean d = statsDirty;
        statsDirty = false;
        return d;
    }

    private Entry find(Kind k, String id, String source) {
        for (Entry e : entries) {
            if (e.same(k, id, source)) {
                return e;
            }
        }
        return null;
    }

    private Entry add(Entry e) {
        e.order = nextOrder++;
        entries.add(e);
        trim();
        return e;
    }

    /** Over {@link #MAX_STATUSES}: the oldest entries go. */
    private void trim() {
        while (entries.size() > MAX_STATUSES) {
            Entry oldest = null;
            for (Entry e : entries) {
                if (e.kind != Kind.STUN && e.kind != Kind.IMMUNE && (oldest == null || e.order < oldest.order)) {
                    oldest = e;
                }
            }
            if (oldest == null) {
                return;
            }
            entries.remove(oldest);
            if (oldest.kind == Kind.STAT) {
                statsDirty = true;
            }
        }
    }

    private static long until(long now, double seconds) {
        return now + Math.max(1, HeroRules.ticks(seconds));
    }

    // ------------------------------------------------------------------ marks (v1 19)

    /**
     * Adds {@code stacks} of a mark (at most {@code maxStacks}) and refreshes its time to {@code seconds}.
     * {@code enemy}: the applier is hostile to the carrier.
     */
    public MarkResult mark(String id, int stacks, int maxStacks, double seconds, String source, boolean glow,
                           boolean enemy, long now) {
        int max = Math.max(1, maxStacks);
        Entry e = null;
        for (Entry x : entries) {
            if (x.kind == Kind.MARK && x.id.equals(id)) {
                e = x;
                break;
            }
        }
        int before = 0;
        if (e != null && e.active(now)) {
            before = e.stacks;
        }
        if (e == null || !e.source.equals(source == null ? "" : source)) {
            if (e != null) {
                entries.remove(e);
            }
            e = add(new Entry(Kind.MARK, id, source));
        }
        e.maxStacks = Math.max(max, 1);
        e.stacks = Math.min(e.maxStacks, before + Math.max(1, stacks));
        e.expiresAt = until(now, seconds);
        e.glow = glow || e.glow && before > 0;
        e.enemy = enemy;
        return new MarkResult(e.stacks, before < e.maxStacks && e.stacks >= e.maxStacks);
    }

    /** The stacks of a mark on at {@code now} (0: none). */
    public int marks(String id, long now) {
        for (Entry e : entries) {
            if (e.kind == Kind.MARK && e.id.equals(id) && e.active(now)) {
                return e.stacks;
            }
        }
        return 0;
    }

    /** Every mark on at {@code now} with its stacks (for a {@link Sense}). */
    public Map<String, Integer> marks(long now) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.kind == Kind.MARK && e.active(now)) {
                m.put(e.id, e.stacks);
            }
        }
        return m;
    }

    /** Whether any mark on it glows ({@code glow}: the vanilla Glowing effect while marked). */
    public boolean glowing(long now) {
        for (Entry e : entries) {
            if (e.kind == Kind.MARK && e.glow && e.active(now)) {
                return true;
            }
        }
        return false;
    }

    /** {@code clear_marks}: removes a mark; returns the stacks it had. */
    public int clearMarks(String id, long now) {
        int had = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.kind == Kind.MARK && e.id.equals(id)) {
                if (e.active(now)) {
                    had = Math.max(had, e.stacks);
                }
                entries.remove(i);
            }
        }
        return had;
    }

    // ------------------------------------------------------------------ damage over time (v1 13.1)

    /**
     * A DoT from {@code source} (keyed by {@code key}: which effect): {@code perSecond} raw damage per stack for
     * {@code seconds}, ticking once a second from now. With {@code maxStacks} 1 a new application refreshes it (the
     * stronger amount stays); above 1 it adds a stack. Returns its stacks now.
     */
    public int dot(String source, String key, Element element, double perSecond, double seconds, int maxStacks,
                   long now) {
        Entry e = find(Kind.DOT, key, source);
        if (e != null && !e.active(now)) {
            entries.remove(e);
            e = null;
        }
        int max = Math.max(1, maxStacks);
        if (e == null) {
            e = add(new Entry(Kind.DOT, key, source));
            e.stacks = 1;
            e.amount = Math.max(0, perSecond);
            e.nextTickAt = now + DOT_PERIOD;
        } else {
            e.stacks = max > 1 ? Math.min(max, e.stacks + 1) : 1;
            e.amount = max > 1 ? Math.max(0, perSecond) : Math.max(e.amount, perSecond);
        }
        e.element = element;
        e.maxStacks = max;
        // a tick lands on its last second too: 4 s is four ticks from the application
        e.expiresAt = Math.max(e.expiresAt, now + Math.max(1, HeroRules.ticks(seconds)) + 1);
        return e.stacks;
    }

    /** Whether it carries any hero damage over time ({@code target_has_dot}). */
    public boolean hasDot(long now) {
        for (Entry e : entries) {
            if (e.kind == Kind.DOT && e.active(now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The DoTs due at {@code now}, summed per source (amount x stacks of each due entry), each moved on to its next
     * second. The element is that of the source's largest due entry.
     */
    public List<DotDue> dueDots(long now) {
        Map<String, double[]> sum = null;
        Map<String, Element> el = null;
        for (Entry e : entries) {
            if (e.kind != Kind.DOT || !e.active(now) || e.nextTickAt > now) {
                continue;
            }
            if (sum == null) {
                sum = new LinkedHashMap<>();
                el = new LinkedHashMap<>();
            }
            double v = e.amount * e.stacks;
            double[] s = sum.computeIfAbsent(e.source, k -> new double[2]);
            s[0] += v;
            if (v >= s[1]) {
                s[1] = v;
                el.put(e.source, e.element);
            }
            e.nextTickAt += DOT_PERIOD;
        }
        if (sum == null) {
            return List.of();
        }
        List<DotDue> out = new ArrayList<>(sum.size());
        for (Map.Entry<String, double[]> m : sum.entrySet()) {
            out.add(new DotDue(m.getKey(), el.get(m.getKey()), m.getValue()[0]));
        }
        return out;
    }

    // ------------------------------------------------------------------ stat modifiers (v1 13.3)

    /**
     * A timed stat modifier from {@code source} (keyed by {@code key}): {@code percent} per stack for
     * {@code seconds} (0: held). A repeat refreshes it, or adds a stack up to {@code maxStacks}.
     */
    public int stat(String source, String key, Stat stat, int percent, double seconds, int maxStacks, long now) {
        Entry e = find(Kind.STAT, key, source);
        if (e != null && !e.active(now)) {
            entries.remove(e);
            e = null;
        }
        int max = Math.max(1, maxStacks);
        if (e == null) {
            e = add(new Entry(Kind.STAT, key, source));
            e.stacks = 1;
        } else {
            e.stacks = Math.min(max, e.stacks + (max > 1 ? 1 : 0));
        }
        e.stat = stat;
        e.amount = percent;
        e.maxStacks = max;
        e.expiresAt = seconds <= 0 ? HELD : until(now, seconds);
        statsDirty = true;
        return e.stacks;
    }

    /** Removes a held or timed stat modifier (an {@code always} passive whose condition stopped holding). */
    public boolean removeStat(String source, String key) {
        Entry e = find(Kind.STAT, key, source);
        if (e == null) {
            return false;
        }
        entries.remove(e);
        statsDirty = true;
        return true;
    }

    /** The sum of a stat's modifiers on at {@code now}, in percent (percent x stacks of each). */
    public double statPercent(Stat stat, long now) {
        double s = 0;
        for (Entry e : entries) {
            if (e.kind == Kind.STAT && e.stat == stat && e.active(now)) {
                s += e.amount * e.stacks;
            }
        }
        return s;
    }

    /** Every stat with a modifier on at {@code now} and its sum, in percent. */
    public Map<Stat, Double> stats(long now) {
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        for (Entry e : entries) {
            if (e.kind == Kind.STAT && e.active(now) && e.stat != null) {
                m.merge(e.stat, e.amount * e.stacks, Double::sum);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ shield and freeze (v1 13.1-13.2)

    /** The shield it carries now (null: none): its granted absorption and expiry. */
    public Entry shield(long now) {
        for (Entry e : entries) {
            if (e.kind == Kind.SHIELD && e.active(now)) {
                return e;
            }
        }
        return null;
    }

    /** Records a shield of {@code amount} absorption for {@code seconds} (it replaces the one before). */
    public void shield(double amount, double seconds, long now) {
        entries.removeIf(e -> e.kind == Kind.SHIELD);
        Entry e = add(new Entry(Kind.SHIELD, "shield", ""));
        e.amount = Math.max(0, amount);
        e.expiresAt = until(now, seconds);
    }

    /** Holds the carrier fully frozen for {@code seconds} (the longer hold wins). */
    public void freeze(double seconds, long now) {
        long t = until(now, seconds);
        for (Entry e : entries) {
            if (e.kind == Kind.FREEZE) {
                e.expiresAt = Math.max(e.expiresAt, t);
                return;
            }
        }
        add(new Entry(Kind.FREEZE, "freeze", "")).expiresAt = t;
    }

    public boolean frozen(long now) {
        for (Entry e : entries) {
            if (e.kind == Kind.FREEZE && e.active(now)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ control (A4: stun, taunt, dispel)

    private Entry first(Kind k) {
        for (Entry e : entries) {
            if (e.kind == k) {
                return e;
            }
        }
        return null;
    }

    /**
     * A stun of {@code seconds} (HEROES_V2 6.14-6.15): the carrier is stopped until it runs out, then cannot be stunned
     * again by anyone for twice its length ({@link BossRules#stunImmuneUntil}). False, and nothing changes, while an
     * earlier stun's immunity holds (a stun running now holds it too).
     */
    public boolean stun(double seconds, long now) {
        if (seconds <= 0 || stunImmune(now)) {
            return false;
        }
        entries.removeIf(e -> e.kind == Kind.STUN || e.kind == Kind.IMMUNE);
        add(new Entry(Kind.STUN, "stun", "")).expiresAt = until(now, seconds);
        add(new Entry(Kind.IMMUNE, "stun", "")).expiresAt = Math.max(now + 1, BossRules.stunImmuneUntil(now, seconds));
        return true;
    }

    /** Whether it is stunned at {@code now}. */
    public boolean stunned(long now) {
        Entry e = first(Kind.STUN);
        return e != null && e.active(now);
    }

    /** Whether a new stun would do nothing at {@code now} (a stun or its immunity is on). */
    public boolean stunImmune(long now) {
        Entry e = first(Kind.IMMUNE);
        return e != null && e.active(now) || stunned(now);
    }

    /** The game time its stun ends (0: not stunned). */
    public long stunEndsAt(long now) {
        Entry e = first(Kind.STUN);
        return e != null && e.active(now) ? e.expiresAt : 0;
    }

    /**
     * A taunt by the hero {@code source} (its id string) for {@code seconds}: the newest taunt replaces any other (one
     * hero holds the carrier at a time; v1 13.4 caps a taunt at 8 s).
     */
    public void taunt(String source, double seconds, long now) {
        entries.removeIf(e -> e.kind == Kind.TAUNT);
        add(new Entry(Kind.TAUNT, "taunt", source)).expiresAt = until(now, seconds);
    }

    /** The hero that taunts it now (its id string), or null. */
    public String tauntedBy(long now) {
        Entry e = first(Kind.TAUNT);
        return e != null && e.active(now) ? e.source : null;
    }

    /**
     * {@code dispel} (v1 13.4): removes the helpful stat modifiers ({@link Stat#helpful}), the hero shield and a barrier
     * (A5). The potion effects and the absorption are the game's to remove.
     */
    public Dispelled dispel() {
        boolean stats = false, barrier = false;
        double shield = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.kind == Kind.STAT && e.stat != null && helpful(e.stat, e.amount)) {
                entries.remove(i);
                stats = true;
            } else if (e.kind == Kind.SHIELD) {
                entries.remove(i);
                shield = Math.max(shield, e.amount > 0 ? e.amount : Double.MIN_VALUE);
            } else if (e.kind == Kind.BARRIER) {
                entries.remove(i);
                barrier = true;
            }
        }
        if (stats) {
            statsDirty = true;
        }
        return new Dispelled(stats, shield, barrier);
    }

    // ------------------------------------------------------------------ format 2's effects (A5: silence, tether, guard, barrier)

    private Entry timed(Kind k, String id, double seconds, long now, boolean longerWins) {
        long t = until(now, seconds);
        Entry e = first(k);
        if (e != null && e.active(now)) {
            e.expiresAt = longerWins ? Math.max(e.expiresAt, t) : t;
            return e;
        }
        entries.removeIf(x -> x.kind == k);
        e = add(new Entry(k, id, ""));
        e.expiresAt = t;
        return e;
    }

    /** A silence for {@code seconds} (the longer one wins): a hero cannot start moves. */
    public void silence(double seconds, long now) {
        timed(Kind.SILENCE, "silence", seconds, now, true);
    }

    public boolean silenced(long now) {
        Entry e = first(Kind.SILENCE);
        return e != null && e.active(now);
    }

    /**
     * A tether of {@code max} blocks to the anchor {@code (ax, ay, az)} for {@code seconds}, by {@code source}: a newer
     * tether replaces an older one.
     */
    public void tether(String source, double seconds, double max, double ax, double ay, double az, long now) {
        entries.removeIf(e -> e.kind == Kind.TETHER);
        Entry e = add(new Entry(Kind.TETHER, "tether", source));
        e.amount = Math.max(0, max);
        e.x = ax;
        e.y = ay;
        e.z = az;
        e.expiresAt = until(now, seconds);
    }

    /** The tether on now (its anchor and length), or null. */
    public Entry tether(long now) {
        Entry e = first(Kind.TETHER);
        return e != null && e.active(now) ? e : null;
    }

    /** A broken guard for {@code seconds} (the longer one wins): no shield goes up until it ends. */
    public void guardBreak(double seconds, long now) {
        timed(Kind.GUARD, "guard", seconds, now, true);
    }

    public boolean guardBroken(long now) {
        Entry e = first(Kind.GUARD);
        return e != null && e.active(now);
    }

    /** The game time a broken guard ends (0: none). */
    public long guardUntil(long now) {
        Entry e = first(Kind.GUARD);
        return e != null && e.active(now) ? e.expiresAt : 0;
    }

    /** A barrier of {@code hits} for {@code seconds}: it replaces an earlier one. */
    public void barrier(int hits, double seconds, long now) {
        entries.removeIf(e -> e.kind == Kind.BARRIER);
        Entry e = add(new Entry(Kind.BARRIER, "barrier", ""));
        e.stacks = Math.max(1, hits);
        e.maxStacks = e.stacks;
        e.expiresAt = until(now, seconds);
    }

    /** The hits a barrier still blocks (0: none). */
    public int barrierHits(long now) {
        Entry e = first(Kind.BARRIER);
        return e != null && e.active(now) ? e.stacks : 0;
    }

    /** What one blow did to a barrier. */
    public enum BarrierHit {
        /** no barrier: the blow lands */
        NONE,
        /** blocked; the barrier holds (one hit fewer, or held at its last by a living focus) */
        BLOCKED,
        /** blocked, and that was its last hit: the barrier is gone */
        BROKE
    }

    /**
     * A blow on a barrier: blocked, and one hit fewer; the last hit breaks it, unless {@code focusAlive} (its focus add
     * lives), which keeps it at one.
     */
    public BarrierHit barrierHit(long now, boolean focusAlive) {
        Entry e = first(Kind.BARRIER);
        if (e == null || !e.active(now)) {
            return BarrierHit.NONE;
        }
        if (e.stacks <= 1 && focusAlive) {
            e.stacks = 1;
            return BarrierHit.BLOCKED;
        }
        e.stacks--;
        if (e.stacks <= 0) {
            entries.remove(e);
            return BarrierHit.BROKE;
        }
        return BarrierHit.BLOCKED;
    }

    /** Ends a barrier at once (its focus add was killed); true when there was one. */
    public boolean breakBarrier() {
        return entries.removeIf(e -> e.kind == Kind.BARRIER);
    }

    // ------------------------------------------------------------------ expiry and cleansing

    /** Removes what ran out by {@code now} and says what the game must undo. */
    public Expired expire(long now) {
        if (entries.isEmpty()) {
            return Expired.NONE;
        }
        boolean stats = false, freeze = false, marks = false, stun = false, taunt = false;
        double shield = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.active(now)) {
                continue;
            }
            entries.remove(i);
            switch (e.kind) {
                case STAT -> stats = true;
                case SHIELD -> shield = Math.max(shield, e.amount > 0 ? e.amount : Double.MIN_VALUE);
                case FREEZE -> freeze = true;
                case MARK -> marks = true;
                case STUN -> stun = true;
                case TAUNT -> taunt = true;
                default -> {
                }
            }
        }
        if (stats) {
            statsDirty = true;
        }
        return stats || freeze || marks || shield > 0 || stun || taunt
                ? new Expired(stats, shield, freeze, marks, stun, taunt) : Expired.NONE;
    }

    /**
     * {@code cleanse} (v1 13.2): removes hero DoTs, marks an enemy put on it, the freeze hold, a stun (its immunity
     * stays: a cleanse never opens the way to a second stun), a silence, a tether, a broken guard (A5) and harmful stat
     * modifiers ({@link Stat#helpful}). {@code sourceIsEnemy} tells, for a mark whose applier is known only by its id
     * string, whether that applier is hostile now (the entry's own {@code enemy} flag counts too). Returns whether a
     * stat changed.
     */
    public boolean cleanse(Predicate<String> sourceIsEnemy) {
        boolean stats = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            boolean go = switch (e.kind) {
                case DOT, FREEZE, STUN, SILENCE, TETHER, GUARD -> true;
                case MARK -> e.enemy || sourceIsEnemy != null && sourceIsEnemy.test(e.source);
                case STAT -> e.stat != null && !helpful(e.stat, e.amount);
                case SHIELD, IMMUNE, TAUNT, BARRIER -> false;
            };
            if (go) {
                entries.remove(i);
                stats |= e.kind == Kind.STAT;
            }
        }
        if (stats) {
            statsDirty = true;
        }
        return stats;
    }

    /** {@link Stat#helpful} for a percent that may have a fraction. */
    static boolean helpful(Stat stat, double percent) {
        return stat == Stat.DAMAGE_TAKEN ? percent < 0 : percent > 0;
    }

    /** Drops everything (the carrier died). */
    public void clear() {
        boolean stats = entries.stream().anyMatch(e -> e.kind == Kind.STAT);
        entries.clear();
        statsDirty |= stats;
    }
}
