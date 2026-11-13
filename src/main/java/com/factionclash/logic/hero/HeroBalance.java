package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.PotionCap;
import com.factionclash.logic.hero.HeroCatalogue.SlotCaps;
import com.factionclash.logic.hero.HeroCatalogue.ValueRange;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.BasicAttack;
import com.factionclash.logic.hero.HeroDef.Damage;
import com.factionclash.logic.hero.HeroDef.Dot;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Empowered;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Heal;
import com.factionclash.logic.hero.HeroDef.Mark;
import com.factionclash.logic.hero.HeroDef.NthHit;
import com.factionclash.logic.hero.HeroDef.Numbers;
import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.PassiveForm;
import com.factionclash.logic.hero.HeroDef.PotionEffect;
import com.factionclash.logic.hero.HeroDef.Shield;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.StatBonus;
import com.factionclash.logic.hero.HeroDef.StatMod;
import com.factionclash.logic.hero.HeroDef.Stun;
import com.factionclash.logic.hero.HeroDef.Summon;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.Problems.Code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.factionclash.logic.hero.Problems.num;
import static com.factionclash.logic.hero.Problems.num1;

/**
 * The balance pass (v1 26.1 step 4): potion, stat and summon caps per effect, the slot caps of v1 16.1 (an over-cap
 * ability is scaled down in proportion), the shared on-hit cap, the {@code thrown} rate cap (v1 9), and the weights of
 * v1 15 with the cooldown floor, the passive floors (17.4) and the least time between two ultimates (14). Every change
 * is reported as ADJUSTED. The numbers come from {@link HeroCatalogue}. Pure Java, unit-tested
 * ({@code HeroBalanceTest}).
 */
public final class HeroBalance {
    private HeroBalance() {
    }

    /**
     * Where a change is reported: {@code where} is the reader's own record (the basic attack, an ability, a passive,
     * an effect, the empowered state or one of its stats) and {@code field} the field of it the change is about
     * ({@code "cooldown"}, {@code "seconds"}...), or null when it is about the record as a whole.
     */
    public interface Sink {
        void report(Object where, String field, Code code, String message);
    }

    /**
     * The level-1 reference of v1 12: the hero's reference attack power, its health, its armour plus full iron, and
     * the highest {@code max_stacks} each mark has in this file.
     */
    public record Ref(double attack, double health, double armor, Map<String, Integer> markStacks) {
        public int stacks(String mark) {
            return markStacks.getOrDefault(mark, HeroCatalogue.REFERENCE_MARK_STACKS);
        }
    }

    public static Ref ref(HeroDef d) {
        return new Ref(d.referenceAttack(), d.stats().health(), d.stats().armor() + HeroCatalogue.REFERENCE_WORN_ARMOR,
                markStacks(d));
    }

    /** Every effect list of a hero, in file order. */
    public static List<List<Effect>> effectLists(HeroDef d) {
        List<List<Effect>> l = new ArrayList<>();
        l.add(d.basicAttack().onHit());
        if (d.basicAttack().everyNthHit() != null) {
            l.add(d.basicAttack().everyNthHit().effects());
        }
        for (Ability a : d.skills()) {
            l.add(a.effects());
        }
        if (d.ultimate() != null) {
            l.add(d.ultimate().effects());
        }
        for (Passive p : d.passives()) {
            l.add(p.effects());
        }
        if (d.empowered() != null) {
            l.add(d.empowered().onHit());
        }
        return l;
    }

    /** The highest {@code max_stacks} of every mark the hero applies. */
    public static Map<String, Integer> markStacks(HeroDef d) {
        Map<String, Integer> m = new HashMap<>();
        for (List<Effect> l : effectLists(d)) {
            for (Effect e : l) {
                if (e instanceof Mark mk) {
                    m.merge(mk.mark(), mk.maxStacks(), Math::max);
                }
            }
        }
        return m;
    }

    /** An Amount at the level-1 reference (v1 12). */
    public static double reference(Amount a, Ref r) {
        double v = a.base() + a.attackPercent() / 100 * r.attack() + a.maxHealthPercent() / 100 * r.health()
                + a.armorPercent() / 100 * r.armor()
                + a.targetMaxHealthPercent() / 100 * HeroCatalogue.REFERENCE_TARGET_MAX
                + a.targetMissingHealthPercent() / 100 * HeroCatalogue.REFERENCE_TARGET_MISSING;
        if (a.mark() != null) {
            v += a.perMarkStack() * r.stacks(a.mark());
        }
        return v;
    }

    // ------------------------------------------------------------------ weights (v1 15, 17.4, 14)

    /** A of v1 15 for an ability's form: 0.75 x count for a volley, 1.5 for an area or a splash radius, else 1. */
    public static double formMultiplier(Ability a) {
        if (a.form() == Form.VOLLEY) {
            return HeroCatalogue.VOLLEY_WEIGHT_PER_SHOT * a.count();
        }
        if (HeroCatalogue.areaForm(a.form()) || a.radius() > 0 && HeroCatalogue.FORMS.get(a.form()).usesRadius()) {
            return HeroCatalogue.AREA_WEIGHT;
        }
        return 1;
    }

    /** A for a passive's form: 1.5 for {@code nova} and {@code allies}, else 1. */
    public static double formMultiplier(PassiveForm f) {
        return f == PassiveForm.NOVA || f == PassiveForm.ALLIES ? HeroCatalogue.AREA_WEIGHT : 1;
    }

    /** One effect's weight (v1 15): its table value x A x hits and pulses x chance x 0.75 with a condition. */
    public static double effectWeight(Effect e, double formA, int reps, Ref r, Empowered emp) {
        boolean selfOnly = HeroCatalogue.reach(e) == HeroCatalogue.Reach.SELF;
        To to = e.c().to();
        double a = to == To.SELF || to == To.ALLY || selfOnly ? 1 : to == To.ALLIES ? HeroCatalogue.AREA_WEIGHT : formA;
        double w = switch (e) {
            case Damage x -> reference(x.amount(), r) * a;
            case Dot x -> reference(x.amount(), r) * x.seconds() * 0.75 * a;
            case Heal x -> reference(x.amount(), r) * 1.25 * a;
            case Shield x -> reference(x.amount(), r) * a;
            case PotionEffect x -> x.level() * x.seconds() / 4 * a;
            case StatMod x -> Math.abs(x.percent()) / 10.0 * x.seconds() / 4 * a;
            case HeroDef.Knockback x -> x.strength() / 2 * a;
            case HeroDef.Pull x -> x.strength() / 2 * a;
            case HeroDef.Ignite x -> x.seconds() / 2 * a;
            case HeroDef.Freeze x -> x.seconds() / 2 * a;
            case HeroDef.Taunt x -> x.seconds() / 2 * a;
            case Stun x -> x.seconds() * 4 * a;
            case HeroDef.Cleanse x -> a;
            case HeroDef.Dispel x -> a;
            case HeroDef.Energy x -> x.amount() / 5.0 * a;
            case HeroDef.CooldownCut x -> x.seconds();
            case HeroDef.MoveSelf x -> 1;
            case Mark x -> 0.5 * x.stacks() * a;
            case HeroDef.ClearMarks x -> 0;
            case HeroDef.Empower x -> x.seconds() / 4 * (1 + empoweredPercent(emp) / 20.0);
            case Summon x -> x.count() * x.seconds() / 10 * HeroCatalogue.SUMMONS.get(x.creature()).weight();
        };
        w *= e.c().chance() / 100.0;
        if (e.c().cond() != null) {
            w *= HeroCatalogue.CONDITION_WEIGHT;
        }
        return w * (selfOnly ? 1 : reps);
    }

    private static int empoweredPercent(Empowered emp) {
        int s = 0;
        if (emp != null) {
            for (StatBonus b : emp.stats()) {
                s += Math.abs(b.percent());
            }
        }
        return s;
    }

    /** An ability's weight: its effects', plus 2 for a {@code leap} or {@code dash}. */
    public static double abilityWeight(Ability a, Ref r, Empowered emp) {
        double formA = formMultiplier(a);
        int reps = a.effectiveHits() * a.pulses();
        double w = 0;
        for (Effect e : a.effects()) {
            w += effectWeight(e, formA, reps, r, emp);
        }
        if (a.form() == Form.LEAP || a.form() == Form.DASH) {
            w += HeroCatalogue.MOVE_FORM_WEIGHT;
        }
        return w;
    }

    public static double passiveWeight(Passive p, Ref r, Empowered emp) {
        double formA = formMultiplier(p.form());
        double w = 0;
        for (Effect e : p.effects()) {
            w += effectWeight(e, formA, 1, r, emp);
        }
        return w;
    }

    /** v1 15: a skill's cooldown is at least (2 + weight / 2) x (1 + 0.5 (charges - 1)) seconds. */
    public static double cooldownFloor(double weight, int charges) {
        return (2 + weight / 2) * (1 + 0.5 * (charges - 1));
    }

    /** v1 17.4: max(1, weight x chance / 2) seconds, at least 2 for {@code on_ally_attack}. */
    public static double passiveFloor(double weight, int chance, Trigger t) {
        double f = Math.max(1, weight * chance / 100.0 / 2);
        return t == Trigger.ON_ALLY_ATTACK ? Math.max(f, HeroCatalogue.ALLY_ATTACK_FLOOR) : f;
    }

    /** v1 14: at least clamp(weight, 20, 60) seconds between two ultimates of one hero. */
    public static double ultimateGap(double weight) {
        return Math.max(HeroCatalogue.ULTIMATE_GAP_MIN, Math.min(HeroCatalogue.ULTIMATE_GAP_MAX, weight));
    }

    /** Seconds rounded up to the next game tick, so a floor is never undercut by rounding. */
    static double upToTick(double seconds) {
        return Math.ceil(seconds * 20 - 1e-9) / 20.0;
    }

    // ------------------------------------------------------------------ slot caps (v1 16.1)

    /** Per receiver, per use, at the level-1 reference: what the cap compares. */
    public record Totals(double damage, double dot, double heal, double shield, double stun) {
    }

    /** {@code damage}, {@code heal} and {@code stun} count each hit and pulse; a DoT and a shield count once. */
    public static Totals totals(List<Effect> fx, int hits, int pulses, Ref r) {
        int m = hits * pulses;
        double dmg = 0, dot = 0, heal = 0, shield = 0, stun = 0;
        for (Effect e : fx) {
            switch (e) {
                case Damage x -> dmg += reference(x.amount(), r) * m;
                case Dot x -> dot += reference(x.amount(), r) * x.seconds();
                case Heal x -> heal += reference(x.amount(), r) * m;
                case Shield x -> shield += reference(x.amount(), r);
                case Stun x -> stun += x.seconds() * m;
                default -> {
                }
            }
        }
        return new Totals(dmg, dot, heal, shield, stun);
    }

    private enum Kind {
        DAMAGE("deals up to %s health points", "%s health points of damage"),
        DOT("deals up to %s health points over time", "%s health points of damage over time"),
        HEAL("heals up to %s health points", "%s health points of healing"),
        SHIELD("shields up to %s health points", "%s health points of shield"),
        STUN("stuns for up to %s seconds", "%s seconds of stun");

        final String text, total;

        Kind(String text, String total) {
            this.text = text;
            this.total = total;
        }
    }

    private static double total(Totals t, Kind k) {
        return switch (k) {
            case DAMAGE -> t.damage();
            case DOT -> t.dot();
            case HEAL -> t.heal();
            case SHIELD -> t.shield();
            case STUN -> t.stun();
        };
    }

    private static double cap(SlotCaps c, Kind k) {
        return switch (k) {
            case DAMAGE -> c.damage();
            case DOT -> c.dot();
            case HEAL -> c.heal();
            case SHIELD -> c.shield();
            case STUN -> c.stun();
        };
    }

    /** Scales every effect of that kind by {@code f}. */
    private static List<Effect> scale(List<Effect> fx, Kind k, double f, Map<Object, Object> origin) {
        List<Effect> out = new ArrayList<>(fx.size());
        for (Effect e : fx) {
            Effect n = switch (e) {
                case Damage x when k == Kind.DAMAGE -> x.withAmount(x.amount().scaled(f));
                case Dot x when k == Kind.DOT -> x.withAmount(x.amount().scaled(f));
                case Heal x when k == Kind.HEAL -> x.withAmount(x.amount().scaled(f));
                case Shield x when k == Kind.SHIELD -> x.withAmount(x.amount().scaled(f));
                case Stun x when k == Kind.STUN -> x.withSeconds(x.seconds() * f);
                default -> e;
            };
            if (n != e) {
                origin.put(n, originOf(origin, e));
            }
            out.add(n);
        }
        return out;
    }

    private static Object originOf(Map<Object, Object> origin, Object o) {
        Object v = origin.get(o);
        return v == null ? o : v;
    }

    private static String slotName(Slot s) {
        return switch (s) {
            case ON_HIT -> "the on-hit cap";
            case NTH_HIT -> "the finisher cap";
            case SKILL -> "the skill cap";
            case ULTIMATE -> "the ultimate cap";
            case PASSIVE -> "the passive cap";
        };
    }

    /** Applies one slot's caps to an effect list; reports against {@code where}. */
    static List<Effect> capSlot(List<Effect> fx, Slot slot, int hits, int pulses, Ref r, Object where, String field,
                                Sink sink, Map<Object, Object> origin) {
        SlotCaps caps = HeroCatalogue.CAPS.get(slot);
        List<Effect> cur = fx;
        for (Kind k : Kind.values()) {
            double t = total(totals(cur, hits, pulses, r), k);
            boolean zone = pulses > 1 && (k == Kind.DAMAGE || k == Kind.HEAL);
            double c = cap(caps, k) * (zone ? HeroCatalogue.ZONE_CAP_FACTOR : 1);
            if (t > c + 1e-9 && t > 0) {
                cur = scale(cur, k, c / t, origin);
                String capText = zone ? "the cap for a lasting zone is " + num(c) + " (1.5 x " + slotName(slot)
                        + " of " + num(cap(caps, k)) + ")" : slotName(slot) + " is " + num(c);
                sink.report(originOf(origin, where), field, Code.SLOT_CAP, String.format(k.text, num1(t))
                        + " at level 1; " + capText + ". Scaled down to " + num(c));
            }
        }
        return cur;
    }

    // ------------------------------------------------------------------ per-effect caps

    private static List<Effect> capEach(List<Effect> fx, boolean always, Sink sink, Map<Object, Object> origin) {
        List<Effect> out = new ArrayList<>(fx.size());
        for (Effect e : fx) {
            Effect n = capOne(e, always, sink, originOf(origin, e));
            if (n != e) {
                origin.put(n, originOf(origin, e));
            }
            out.add(n);
        }
        return out;
    }

    /** Potion level and seconds (B.1), stat ranges (13.3, 16.2) and summon counts (B.8) of one effect. */
    static Effect capOne(Effect e, boolean always, Sink sink, Object where) {
        if (e instanceof PotionEffect p) {
            PotionCap cap = p.helpful() ? HeroCatalogue.HELPFUL_POTIONS.get(p.id()) : HeroCatalogue.HARMFUL_POTIONS.get(p.id());
            int level = p.level();
            double sec = p.seconds();
            String who = p.helpful() ? "allies" : "enemies";
            if (level > cap.maxLevel()) {
                sink.report(where, "level", Code.POTION_LEVEL, "\"" + p.id() + "\" goes up to level " + cap.maxLevel()
                        + ". Set to " + cap.maxLevel());
                level = cap.maxLevel();
            }
            if (sec > cap.maxSeconds() + 1e-9) {
                sink.report(where, "seconds", Code.POTION_SECONDS, "\"" + p.id() + "\" on " + who + " lasts at most "
                        + num(cap.maxSeconds()) + " seconds. Set to " + num(cap.maxSeconds()));
                sec = cap.maxSeconds();
            }
            return level == p.level() && sec == p.seconds() ? e
                    : new PotionEffect(p.c(), p.id(), level, sec, p.helpful());
        }
        if (e instanceof StatMod s) {
            ValueRange r = statRange(s.stat(), always ? HeroCatalogue.ALWAYS_STAT_CAP : Integer.MAX_VALUE);
            int v = (int) Math.max(r.min(), Math.min(r.max(), s.percent()));
            if (v != s.percent()) {
                sink.report(where, "percent", Code.STAT_CAP, "\"" + s.stat().id() + "\" " + signed(s.percent())
                        + "% is outside "
                        + signed((int) r.min()) + "% to " + signed((int) r.max()) + "%" + (always
                        ? " (a stat an \"always\" passive holds)" : "") + ". Set to " + signed(v) + "%");
                return new StatMod(s.c(), s.stat(), v, s.seconds(), s.maxStacks());
            }
            return e;
        }
        if (e instanceof Summon s) {
            int max = HeroCatalogue.SUMMONS.get(s.creature()).maxCount();
            if (s.count() > max) {
                sink.report(where, "count", Code.SUMMON_COUNT, "at most " + max + " " + s.creature().id()
                        + (max == 1 ? "" : "s") + " per cast. Set to " + max);
                return new Summon(s.c(), s.creature(), max, s.seconds(), s.name());
            }
        }
        return e;
    }

    /** A stat's range of v1 13.3, narrowed to +-{@code limit} (the always and empowered caps). */
    public static ValueRange statRange(HeroDef.Stat stat, int limit) {
        ValueRange r = HeroCatalogue.STAT_RANGES.get(stat);
        return new ValueRange(Math.max(r.min(), -limit), Math.min(r.max(), limit), 0);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    // ------------------------------------------------------------------ the whole hero

    /** Applies every cap and floor; the result is the definition the game uses. */
    public static HeroDef apply(HeroDef d, Sink sink) {
        Map<Object, Object> origin = new IdentityHashMap<>();
        // 1. per-effect caps
        BasicAttack ba = d.basicAttack();
        List<Effect> onHit = capEach(ba.onHit(), false, sink, origin);
        NthHit nth = ba.everyNthHit() == null ? null
                : new NthHit(ba.everyNthHit().n(), capEach(ba.everyNthHit().effects(), false, sink, origin));
        List<Ability> skills = new ArrayList<>();
        for (Ability a : d.skills()) {
            Ability n = a.withEffects(capEach(a.effects(), false, sink, origin));
            origin.put(n, a);
            skills.add(n);
        }
        Ability ult = null;
        if (d.ultimate() != null) {
            ult = d.ultimate().withEffects(capEach(d.ultimate().effects(), false, sink, origin));
            origin.put(ult, d.ultimate());
        }
        List<Passive> passives = new ArrayList<>();
        for (Passive p : d.passives()) {
            Passive n = p.withEffects(capEach(p.effects(), p.trigger() == Trigger.ALWAYS, sink, origin));
            origin.put(n, p);
            passives.add(n);
        }
        Empowered emp = d.empowered();
        if (emp != null) {
            List<StatBonus> stats = new ArrayList<>();
            for (StatBonus b : emp.stats()) {
                ValueRange r = statRange(b.stat(), HeroCatalogue.EMPOWERED_STAT_CAP);
                int v = (int) Math.max(r.min(), Math.min(r.max(), b.percent()));
                if (v != b.percent()) {
                    sink.report(b, "percent", Code.STAT_CAP, "\"" + b.stat().id() + "\" " + signed(b.percent())
                            + "% is outside " + signed((int) r.min()) + "% to " + signed((int) r.max())
                            + "% (an empowered state's stat). Set to " + signed(v) + "%");
                }
                stats.add(new StatBonus(b.stat(), v));
            }
            Empowered n = new Empowered(emp.name(), emp.description(), List.copyOf(stats),
                    capEach(emp.onHit(), false, sink, origin), emp.particle(), emp.color(), emp.size(),
                    emp.skillsLocked(), emp.line());
            origin.put(n, emp);
            emp = n;
        }
        // 2. the thrown rate (v1 9)
        double interval = ba.interval();
        if (ba.style() == HeroDef.Style.THROWN && ba.amount() / ba.interval() > HeroCatalogue.THROWN_MAX_DPS + 1e-9) {
            interval = upToTick(ba.amount() / HeroCatalogue.THROWN_MAX_DPS);
            sink.report(ba, "interval", Code.THROWN_RATE, num(ba.amount()) + " damage every " + num(ba.interval())
                    + " seconds is faster than a bow (at most " + num(HeroCatalogue.THROWN_MAX_DPS)
                    + " damage per second); the interval was raised to " + num(interval) + " seconds");
        }
        HeroDef mid = new HeroDef(d.format(), d.id(), d.name(), d.title(), d.description(), d.rarity(), d.starterFor(),
                d.factions(), d.skinModel(), d.color(), d.element(), d.stats(), d.resistances(), d.gear(),
                d.behaviour(), new BasicAttack(ba.style(), ba.projectile(), ba.heldItem(), ba.amount(), interval,
                ba.element(), onHit, nth), skills, ult, passives, emp, d.baseAbilities(), d.lines());
        Ref r = ref(mid);
        // 3. slot caps
        BasicAttack mba = mid.basicAttack();
        List<Effect> capOnHit = capSlot(mba.onHit(), Slot.ON_HIT, 1, 1, r, ba, "on_hit", sink, origin);
        NthHit capNth = nth == null ? null : new NthHit(nth.n(), capSlot(nth.effects(), Slot.NTH_HIT, 1, 1, r, ba,
                "every_nth_hit", sink, origin));
        List<Ability> capSkills = new ArrayList<>();
        for (Ability a : skills) {
            capSkills.add(withOrigin(a.withEffects(capSlot(a.effects(), Slot.SKILL, a.effectiveHits(), a.pulses(), r,
                    a, null, sink, origin)), a, origin));
        }
        Ability capUlt = ult == null ? null : withOrigin(ult.withEffects(capSlot(ult.effects(), Slot.ULTIMATE,
                ult.effectiveHits(), ult.pulses(), r, ult, null, sink, origin)), ult, origin);
        List<Passive> capPassives = new ArrayList<>();
        for (Passive p : passives) {
            capPassives.add(withOrigin(p.withEffects(capSlot(p.effects(), Slot.PASSIVE, 1, 1, r, p, null, sink,
                    origin)), p, origin));
        }
        // 4. the shared on-hit cap: basic and empowered on-hit together within the on-hit row
        Empowered capEmp = emp;
        if (emp != null && !emp.onHit().isEmpty()) {
            List<Effect> e = emp.onHit();
            Totals base = totals(capOnHit, 1, 1, r);
            SlotCaps caps = HeroCatalogue.CAPS.get(Slot.ON_HIT);
            for (Kind k : Kind.values()) {
                double t = total(totals(e, 1, 1, r), k), room = Math.max(0, cap(caps, k) - total(base, k));
                if (t > room + 1e-9) {
                    e = scale(e, k, room / t, origin);
                    sink.report(originOf(origin, emp), "on_hit", Code.SHARED_ON_HIT, "while empowered, the basic"
                            + " attack's on_hit and the empowered on_hit add up to "
                            + String.format(k.total, num1(t + total(base, k)))
                            + "; they share the on-hit cap of " + num(cap(caps, k)) + ". The empowered on_hit was"
                            + " scaled down to " + num1(room));
                }
            }
            capEmp = withOrigin(emp.withOnHit(e), emp, origin);
        }
        // 5. weights and floors
        List<Ability> finalSkills = new ArrayList<>();
        for (Ability a : capSkills) {
            double w = abilityWeight(a, r, capEmp);
            double floor = cooldownFloor(w, a.charges());
            double cd = a.cooldown();
            if (cd < floor - 1e-9) {
                cd = upToTick(floor);
                sink.report(originOf(origin, a), "cooldown", Code.COOLDOWN_FLOOR, Problems.seconds(a.cooldown())
                        + " is too short for a skill this strong (weight " + num1(w) + "). Raised to "
                        + Problems.seconds(cd) + ", the shortest allowed for it");
            }
            finalSkills.add(a.withBalance(cd, new Numbers(w, floor, 0)));
        }
        Ability finalUlt = null;
        if (capUlt != null) {
            double w = abilityWeight(capUlt, r, capEmp);
            finalUlt = capUlt.withBalance(capUlt.cooldown(), new Numbers(w, 0, ultimateGap(w)));
        }
        List<Passive> finalPassives = new ArrayList<>();
        for (Passive p : capPassives) {
            double w = passiveWeight(p, r, capEmp);
            double floor = passiveFloor(w, p.chance(), p.trigger());
            double cd = p.cooldown(), tv = p.triggerValue();
            if (p.trigger() == Trigger.EVERY) {
                if (tv < floor - 1e-9) {
                    tv = upToTick(floor);
                    sink.report(originOf(origin, p), "trigger_value", Code.EVERY_FLOOR, "every "
                            + Problems.seconds(p.triggerValue()) + " is too often for a passive this strong (weight "
                            + num1(w) + "). Raised to every " + Problems.seconds(tv));
                }
            } else if (p.trigger() != Trigger.ALWAYS && cd < floor - 1e-9) {
                cd = upToTick(floor);
                sink.report(originOf(origin, p), "cooldown", Code.PASSIVE_FLOOR, Problems.seconds(p.cooldown())
                        + " is too short for a passive this strong (weight " + num1(w) + "). Raised to "
                        + Problems.seconds(cd) + ", the shortest allowed for it");
            }
            finalPassives.add(p.withBalance(cd, tv, new Numbers(w, floor, 0)));
        }
        return new HeroDef(d.format(), d.id(), d.name(), d.title(), d.description(), d.rarity(), d.starterFor(),
                d.factions(), d.skinModel(), d.color(), d.element(), d.stats(), d.resistances(), d.gear(),
                d.behaviour(), new BasicAttack(mba.style(), mba.projectile(), mba.heldItem(), mba.amount(),
                mba.interval(), mba.element(), List.copyOf(capOnHit), capNth), List.copyOf(finalSkills), finalUlt,
                List.copyOf(finalPassives), capEmp, d.baseAbilities(), d.lines());
    }

    private static <T> T withOrigin(T n, Object old, Map<Object, Object> origin) {
        if (n != old) {
            origin.put(n, originOf(origin, old));
        }
        return n;
    }

    // ------------------------------------------------------------------ server-wide tuning (v1 16.3)

    public static final int POWER_SCALE_MIN = 25, POWER_SCALE_MAX = 200;

    /** {@code heroes.powerScale} within 25-200%. */
    public static int clampPowerScale(int percent) {
        return Math.max(POWER_SCALE_MIN, Math.min(POWER_SCALE_MAX, percent));
    }

    /** An Amount after the caps, times {@code heroes.powerScale} (v1 16.3). */
    public static double powerScaled(double amount, int powerScale) {
        return amount * clampPowerScale(powerScale) / 100.0;
    }
}
