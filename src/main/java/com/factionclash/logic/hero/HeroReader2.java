package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.CondValue;
import com.factionclash.logic.hero.HeroCatalogue.Field;
import com.factionclash.logic.hero.HeroCatalogue.FormSpec;
import com.factionclash.logic.hero.HeroCatalogue.RolePreset;
import com.factionclash.logic.hero.HeroCatalogue.Unit;
import com.factionclash.logic.hero.HeroCatalogue.ValueRange;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.AddKind;
import com.factionclash.logic.hero.HeroDef.AddSpec;
import com.factionclash.logic.hero.HeroDef.Aerial;
import com.factionclash.logic.hero.HeroDef.Aggression;
import com.factionclash.logic.hero.HeroDef.Band;
import com.factionclash.logic.hero.HeroDef.Banner;
import com.factionclash.logic.hero.HeroDef.BannerLayer;
import com.factionclash.logic.hero.HeroDef.BaseAbility;
import com.factionclash.logic.hero.HeroDef.Behaviour;
import com.factionclash.logic.hero.HeroDef.Boss;
import com.factionclash.logic.hero.HeroDef.CarrierSpec;
import com.factionclash.logic.hero.HeroDef.Common;
import com.factionclash.logic.hero.HeroDef.Condition;
import com.factionclash.logic.hero.HeroDef.ConditionType;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.EffectType;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.EndOfFight;
import com.factionclash.logic.hero.HeroDef.Enrage;
import com.factionclash.logic.hero.HeroDef.EnrageTrigger;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.Followup;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.FormExtra;
import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Gender;
import com.factionclash.logic.hero.HeroDef.Intro;
import com.factionclash.logic.hero.HeroDef.Leader;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Mobility;
import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.HeroDef.Numbers;
import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.Personality;
import com.factionclash.logic.hero.HeroDef.Phase;
import com.factionclash.logic.hero.HeroDef.Rarity;
import com.factionclash.logic.hero.HeroDef.Reaction;
import com.factionclash.logic.hero.HeroDef.Recruit;
import com.factionclash.logic.hero.HeroDef.Relationship;
import com.factionclash.logic.hero.HeroDef.Revival;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.StandsFor;
import com.factionclash.logic.hero.HeroDef.StatBonus;
import com.factionclash.logic.hero.HeroDef.State;
import com.factionclash.logic.hero.HeroDef.Stats;
import com.factionclash.logic.hero.HeroDef.Strike;
import com.factionclash.logic.hero.HeroDef.Style;
import com.factionclash.logic.hero.HeroDef.Talent;
import com.factionclash.logic.hero.HeroDef.TalentId;
import com.factionclash.logic.hero.HeroDef.Telegraph;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.hero.HeroDef.TraitTier;
import com.factionclash.logic.hero.HeroDef.Transition;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroDef.UseWhen;
import com.factionclash.logic.hero.HeroDef.V2;
import com.factionclash.logic.hero.HeroDef.WeakPoint;
import com.factionclash.logic.hero.HeroDef.WeakSpot;
import com.factionclash.logic.hero.HeroDef.WhenLosing;
import com.factionclash.logic.hero.HeroDef.ZoneAt;
import com.factionclash.logic.hero.HeroJson.Arr;
import com.factionclash.logic.hero.HeroJson.Member;
import com.factionclash.logic.hero.HeroJson.Node;
import com.factionclash.logic.hero.HeroJson.Num;
import com.factionclash.logic.hero.HeroJson.Obj;
import com.factionclash.logic.hero.HeroJson.Str;
import com.factionclash.logic.hero.HeroReader.Ctx;
import com.factionclash.logic.hero.HeroReader.FxCtx;
import com.factionclash.logic.hero.HeroReader.Mode;
import com.factionclash.logic.hero.HeroReader.Need;
import com.factionclash.logic.hero.HeroReader.Pos;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Path;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.factionclash.logic.hero.Problems.num;

/**
 * Format 2 of the hero file (HEROES_V2 12): the parts {@link HeroReader} hands over for a {@code "format": 2} hero,
 * read with the same forgiving rules and message format as format 1 (v1 26), and the sense rules of HEROES_V2 14.1:
 * moves with ids, classes, frames, bands, phases and followup strings; the eight new forms; states; the boss block as
 * it is after the owner's decisions (no damage to players, no yield, no attack tokens, no health per player: each
 * removed field is read and ignored with a NOTE); personality with the end of a fight (escape or last stand);
 * leader, talents, voice, recruit, relationships, the hero's own revival, mobility (MOBILITY_AND_WORK 8) and the
 * {@code generated} block herogen writes. A format-1 hero is mapped to the same shape by {@link #mapV1} without a
 * word reported (the file did not ask for any of it). Nothing here reads whether anyone is a player. Pure Java,
 * unit-tested ({@code HeroReaderV2Test}, {@code HeroMessagesTest}, {@code SliceKitsTest}).
 */
final class HeroReader2 {
    private final Ctx c;
    private final Ctx.Fields top;

    // what has been read so far, in the order readHero calls the parts
    private Gender gender;
    private String archetype, hybrid;
    private Personality personality;
    private boolean personalityGiven;
    private Ctx.Fields personalityFields;
    private Rarity rarity = Rarity.RARE;
    private final List<State> states = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private Move ultimate, awakened;
    private final Map<String, Pos> moveWhere = new HashMap<>();
    private final Map<Move, Path> movePaths = new LinkedHashMap<>();
    private final Map<Move, Integer> moveLines = new LinkedHashMap<>();
    /** Conditions naming a state, checked once the states are known (they are read first: always known). */
    private final List<String> leaderNames = new ArrayList<>();

    HeroReader2(Ctx c, Ctx.Fields top) {
        this.c = c;
        this.top = top;
        c.v2Reader = this;
    }

    // ================================================================== early: identity and personality

    /** Gender, archetype, the source-only blocks and the personality (the fight AI's defaults follow from it). */
    void readEarly() {
        String g = c.word(top, "gender", "gender", null, null, Mode.OPTIONAL, null);
        gender = g == null ? null : HeroCatalogue.enumOf(Gender.class, g);
        archetype = c.word(top, "archetype", "archetype", null, null, Mode.OPTIONAL, null);
        hybrid = c.word(top, "hybrid", "archetype", null, null, Mode.OPTIONAL, null);
        if (hybrid != null && hybrid.equals(archetype)) {
            c.report(Severity.NOTE, Code.FIELD_UNUSED, top.line("hybrid"), top.at("hybrid"), "the hybrid is the"
                    + " hero's own archetype; it was ignored", null);
            hybrid = null;
        }
        for (String k : List.of("art", "animations")) {
            if (top.has(k)) {
                c.report(Severity.NOTE, Code.SOURCE_ONLY, top.line(k), top.at(k), "\"" + k + "\" is a source field"
                        + " that herogen reads and leaves out of the server file (HEROES_V2 12.1); the server ignores"
                        + " it", null);
            }
        }
        personality = personality();
        if (c.opt.builtin()) {
            for (String k : List.of("gender", "archetype", "personality", "leader")) {
                if (!top.has(k)) {
                    c.report(Severity.NOTE, Code.BUILTIN_MISSING, top.obj.line(), Path.ROOT, "a built-in hero needs"
                            + " \"" + k + "\" (HEROES_V2 12.3); "
                            + switch (k) {
                                case "gender" -> "the roster keeps 100 female and 100 male heroes";
                                case "archetype" -> "it sets the defaults of its look, mobility and base style";
                                case "personality" -> "its temperament and traits drive its decisions";
                                default -> "the faction it leads takes its name and colours from it";
                            }, null);
                }
            }
        }
    }

    Personality personality() {
        Node n = top.node("personality");
        if (n == null) {
            return new Personality(null, HeroCatalogue.defaultTraits(null), null, null, List.of(), List.of(),
                    List.of());
        }
        Path p = top.at("personality");
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"temperament\": ... },"
                    + " not " + c.shown(n) + ". It was ignored", null);
            return new Personality(null, HeroCatalogue.defaultTraits(null), null, null, List.of(), List.of(),
                    List.of());
        }
        personalityGiven = true;
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.PERSONALITY);
        personalityFields = f;
        String tw = c.word(f, "temperament", "temperament", null, null, Mode.OPTIONAL, null);
        Temperament t = tw == null ? null : HeroCatalogue.enumOf(Temperament.class, tw);
        Map<Trait, Integer> traits = HeroCatalogue.defaultTraits(t);
        Node tn = f.node("traits");
        if (tn instanceof Obj to) {
            Map<String, Field> sh = new LinkedHashMap<>();
            for (Member m : to.members()) {
                if (!m.key().startsWith("_") && !(m.value() instanceof HeroJson.Null)) {
                    sh.put(m.key(), new Field(m.key(), HeroCatalogue.Kind.INT, Unit.NONE, 0, 100, 50, null, false));
                }
            }
            Ctx.Fields tf = c.fields(to, f.at("traits"), sh);
            for (String k : sh.keySet()) {
                String w = c.resolve(k, "trait", null, null, Mode.PART, "Trait", tf.got.get(k).line(), tf.at(k));
                if (w == null) {
                    continue;
                }
                double v = c.number(tf, sh, k, new Need(Severity.PART_LEFT_OUT, "Trait"));
                if (Double.isNaN(v)) {
                    continue;
                }
                Trait tr = HeroCatalogue.enumOf(Trait.class, w);
                int iv = (int) v;
                if (t != null) {
                    int d = HeroCatalogue.TEMPERAMENT_TRAITS.get(t)[tr.ordinal()];
                    int lo = Math.max(0, d - HeroCatalogue.TRAIT_DRIFT), hi = Math.min(100, d + HeroCatalogue.TRAIT_DRIFT);
                    if (iv < lo || iv > hi) {
                        int cl = Math.max(lo, Math.min(hi, iv));
                        c.report(Severity.ADJUSTED, Code.TRAIT_DRIFT, tf.line(k), tf.at(k), w + " " + iv + " is more"
                                + " than " + HeroCatalogue.TRAIT_DRIFT + " from a " + t.id() + "'s " + d + " (so the"
                                + " temperament stays true). Set to " + cl, null);
                        iv = cl;
                    }
                }
                traits.put(tr, iv);
            }
        } else if (tn != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, tn.line(), f.at("traits"), "should be an object"
                    + " { \"aggression\": 60 }, not " + c.shown(tn) + ". The temperament's traits are used", null);
        }
        WhenLosing wl = null;
        Node wn = f.node("when_losing");
        if (wn instanceof Str ws && HeroCatalogue.YIELD_WORDS.contains(HeroCatalogue.norm(ws.value()))) {
            c.report(Severity.PART_LEFT_OUT, Code.YIELD_WORD, ws.line(), f.at("when_losing"), "heroes do not yield"
                    + " (the owner's rule of 2026-11-13: a hero escapes or fights until it dies). Allowed: escape,"
                    + " last_stand; the temperament's default applies", "When losing");
        } else {
            String w = c.word(f, "when_losing", "when_losing", null, null, Mode.OPTIONAL, null);
            wl = w == null ? null : HeroCatalogue.enumOf(WhenLosing.class, w);
        }
        List<StandsFor> stands = null;
        if (f.has("stands_for")) {
            stands = new ArrayList<>();
            for (String w : words(f, "stands_for", 2, "stands_for", "place", "Place")) {
                StandsFor sf = HeroCatalogue.enumOf(StandsFor.class, w);
                if (!stands.contains(sf)) {
                    stands.add(sf);
                }
            }
        }
        return new Personality(t, traits, wl, stands, itemIds(f, "likes"), itemIds(f, "dislikes"),
                texts(f, "quirks", 3, 120, "quirk"));
    }

    /** Vanilla item ids, checked for their form only (the registry decides in game). */
    List<String> itemIds(Ctx.Fields f, String name) {
        List<String> out = new ArrayList<>();
        List<Node> items = c.list(f, name, 6, "item", "Item", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item(name, i, "item", null, true);
            String id = n instanceof Str s ? HeroCatalogue.norm(s.value()) : null;
            if (id == null || !id.matches("[a-z0-9_]+(/[a-z0-9_]+)*")) {
                c.report(Severity.PART_LEFT_OUT, Code.ITEM_ID, n.line(), p, (n instanceof Str s ? "\"" + s.value()
                        + "\"" : c.shown(n)) + " is not an item id such as \"bamboo\" or \"minecraft:cherry_sapling\"",
                        "Item");
                continue;
            }
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    List<String> texts(Ctx.Fields f, String name, int max, int len, String noun) {
        List<String> out = new ArrayList<>();
        List<Node> items = c.list(f, name, max, noun, "Text", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item(name, i, noun, null, true);
            if (!(n instanceof Str s)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be text in quotes, not "
                        + c.shown(n), "Text");
                continue;
            }
            String t = c.clean(s.value(), n.line(), p);
            if (t.length() > len) {
                c.report(Severity.ADJUSTED, Code.TEXT_CUT, n.line(), p, "is " + t.length() + " characters long; at"
                        + " most " + len + ". Cut to " + len, null);
                t = HeroReader.cut(t, len);
            }
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** A list of words of {@code vocab}; unknown words are left out, repeats read once. */
    List<String> words(Ctx.Fields f, String name, int max, String vocab, String noun, String part) {
        List<String> out = new ArrayList<>();
        List<Node> items = c.list(f, name, max, noun, part, true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item(name, i, noun, null, true);
            if (!(n instanceof Str s)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be "
                        + HeroCatalogue.vocab(vocab).noun + " in quotes, not " + c.shown(n), part);
                continue;
            }
            String w = c.resolve(s.value(), vocab, null, null, Mode.PART, part, n.line(), p);
            if (w == null) {
                continue;
            }
            if (out.contains(w)) {
                c.report(Severity.ADJUSTED, Code.LIST_DUPLICATE, n.line(), p, "\"" + w + "\" is listed twice; once is"
                        + " enough", null);
                continue;
            }
            out.add(w);
        }
        return out;
    }

    /**
     * HEROES_V2 5.4: format 1's behaviour, with the fight AI's defaults from the personality where the file sets
     * none (the aggression word, {@code retreat_below} and the temperament's focus), {@code duel_style}, and
     * {@code duty: court} refused (the game sets it while the hero leads).
     */
    Behaviour behaviour(Ctx.Fields f, Role role, RolePreset rp) {
        int a = personality.trait(Trait.AGGRESSION), cau = personality.trait(Trait.CAUTION);
        Focus tf = HeroCatalogue.temperamentFocus(personality.temperament());
        Focus focusDefault = tf != null ? tf : rp.focus();
        Aggression aggDefault = personalityGiven ? HeroCatalogue.aggressionWord(a) : rp.aggression();
        int retreatDefault = personalityGiven ? EscapeRules.retreatBelow(rp.retreatBelow(), a, cau)
                : rp.retreatBelow();
        if (f == null) {
            return new Behaviour(role, focusDefault, rp.keepDistance(), aggDefault, rp.protects(), retreatDefault,
                    rp.raids(), "guard", null, false);
        }
        Map<String, Field> sh = HeroCatalogue.BEHAVIOUR_2;
        String focus = c.word(f, "focus", "focus", focusDefault.id(), tf != null ? "the temperament's focus"
                : "the role's focus", Mode.OPTIONAL, null);
        String agg = c.word(f, "aggression", "aggression", aggDefault.id(), personalityGiven
                ? "the personality's aggression" : "the role's aggression", Mode.OPTIONAL, null);
        String prot = c.word(f, "protects", "protects", rp.protects().id(), "the role's", Mode.OPTIONAL, null);
        String raids = c.word(f, "raids", "raids", rp.raids().id(), "the role's", Mode.OPTIONAL, null);
        String duty = "guard";
        if (f.node("duty") instanceof Str ds && HeroCatalogue.norm(ds.value()).equals("court")) {
            c.report(Severity.ADJUSTED, Code.COURT_DUTY, ds.line(), f.at("duty"), "\"court\" is set by the game while"
                    + " the hero leads its faction (HEROES_V2 5.6); read as \"guard\"", null);
        } else {
            duty = c.word(f, "duty", "duty", "guard", null, Mode.OPTIONAL, null);
        }
        String duel = c.word(f, "duel_style", "duel_style", null, null, Mode.OPTIONAL, null);
        return new Behaviour(role, HeroCatalogue.enumOf(Focus.class, focus), c.number(f, sh, "keep_distance",
                rp.keepDistance()), HeroCatalogue.enumOf(Aggression.class, agg),
                HeroCatalogue.enumOf(HeroDef.Protects.class, prot), (int) c.number(f, sh, "retreat_below",
                retreatDefault), HeroCatalogue.enumOf(HeroDef.Raids.class, raids), duty,
                duel == null ? null : HeroCatalogue.enumOf(HeroDef.DuelStyle.class, duel), f.has("retreat_below"));
    }

    // ================================================================== states (12.9)

    void readStates(Stats stats) {
        List<Node> sl = c.list(top, "states", HeroCatalogue.MAX_STATES, "state", "State", false);
        for (int i = 0; i < sl.size(); i++) {
            State s = state(sl.get(i), top.path.item("states", i, "state", peekName(sl.get(i)), false), stats);
            if (s != null) {
                if (state(s.name()) != null) {
                    c.report(Severity.PART_LEFT_OUT, Code.NAME_TAKEN, sl.get(i).line(), top.path.item("states", i,
                            "state", s.name(), false), "another state is already named \"" + s.name() + "\"", "State");
                    continue;
                }
                states.add(s);
            }
        }
        if (top.has("empowered")) {
            c.report(Severity.NOTE, Code.V1_FIELD, top.line("empowered"), top.at("empowered"), "\"empowered\" is a"
                    + " format-1 field; it was read as a state (HEROES_V2 12.9)", null);
            if (states.size() >= HeroCatalogue.MAX_STATES) {
                c.report(Severity.PART_LEFT_OUT, Code.LIST_TOO_LONG, top.line("empowered"), top.at("empowered"), "a"
                        + " hero has at most " + HeroCatalogue.MAX_STATES + " states", "State");
            } else {
                State s = state(top.node("empowered"), top.at("empowered"), stats);
                if (s != null && state(s.name()) == null) {
                    states.add(s);
                }
            }
        }
    }

    State state(String name) {
        for (State s : states) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    String peekName(Node n) {
        return c.peekName(n);
    }

    State state(Node n, Path p, Stats stats) {
        String part = "State";
        if (!(n instanceof Obj o)) {
            c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"name\": ... }, not "
                    + c.shown(n), part);
            return null;
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.STATE);
        Node nn = f.node("name");
        if (nn == null) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"name\" is missing: the state's id, for"
                    + " example \"name\": \"frenzy\"", part);
            return null;
        }
        String name = id(nn, f.at("name"), "state", part);
        if (name == null) {
            return null;
        }
        List<StatBonus> bonus = statBonuses(f, "stats", 4, p);
        FxCtx ctx = new FxCtx(Slot.ON_HIT, "the state's on_hit", null, false, false, false, false, null);
        List<Effect> onHit = c.effects(f, "on_hit", 2, ctx);
        int size = (int) c.number(f, HeroCatalogue.STATE, "size", 100);
        if (size > HeroCatalogue.MAX_SIZE) {
            size = HeroCatalogue.MAX_SIZE;
        }
        String particle = c.word(f, "particle", "particle", null, "the element's particle", Mode.OPTIONAL, null);
        List<String> moveIds = idList(f, "moves", HeroCatalogue.MAX_MOVES, "move");
        List<String> show = boneList(f, "show"), hide = boneList(f, "hide");
        String face = c.word(f, "face", "mood", null, null, Mode.OPTIONAL, null);
        State s = new State(name, c.orEmpty(c.text(f, "description", 200, null)), bonus, onHit, particle,
                c.color(f, "color", "the hero colour"), size, c.bool(f, "skills_locked", false), moveIds, show, hide,
                face, c.text(f, "line", HeroCatalogue.LINE_MAX, null));
        c.where.put(s, new Pos(p, o.line(), f));
        return s;
    }

    /** Stat bonuses ({@code { stat, percent }}), as format 1's empowered state reads them. */
    List<StatBonus> statBonuses(Ctx.Fields f, String name, int max, Path p) {
        List<StatBonus> out = new ArrayList<>();
        List<Node> sl = c.list(f, name, max, "stat", "Stat", true);
        for (int i = 0; i < sl.size(); i++) {
            Path sp = p.item(name, i, "stat", null, true);
            Node sn = sl.get(i);
            if (!(sn instanceof Obj so)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, sn.line(), sp, "should be an object { \"stat\":"
                        + " \"armor\", \"percent\": 20 }, not " + c.shown(sn), "Stat");
                continue;
            }
            Ctx.Fields sf = c.fields(so, sp, HeroCatalogue.STAT_BONUS);
            if (!sf.has("stat") || !sf.has("percent")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, so.line(), sp, "\"" + (sf.has("stat") ? "percent"
                        : "stat") + "\" is missing", "Stat");
                continue;
            }
            String st = c.word(sf, "stat", "stat", null, null, Mode.PART, "Stat");
            double pc = c.number(sf, HeroCatalogue.STAT_BONUS, "percent", new Need(Severity.PART_LEFT_OUT, "Stat"));
            if (st != null && !Double.isNaN(pc)) {
                StatBonus b = new StatBonus(HeroCatalogue.enumOf(HeroDef.Stat.class, st), (int) pc);
                c.where.put(b, new Pos(sp, so.line(), sf));
                out.add(b);
            }
        }
        return out;
    }

    /** An id of a move or a state: 2-24 of {@code a-z 0-9 _}, starting with a letter; normalised (ADJUSTED). */
    String id(Node n, Path p, String noun, String part) {
        if (!(n instanceof Str s)) {
            c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be " + noun + " id in quotes, not "
                    + c.shown(n), part);
            return null;
        }
        String id = HeroReader.normaliseId(s.value());
        if (id.length() < HeroCatalogue.MOVE_ID_MIN || id.length() > HeroCatalogue.MOVE_ID_MAX
                || !id.matches("[a-z][a-z0-9_]*")) {
            c.report(Severity.PART_LEFT_OUT, Code.MARK_INVALID, n.line(), p, "\"" + s.value() + "\" is not " + noun
                    + " id: 2 to 24 lowercase letters, digits or _, starting with a letter", part);
            return null;
        }
        if (!id.equals(s.value())) {
            c.report(Severity.ADJUSTED, Code.MARK_RENAMED, n.line(), p, "\"" + s.value() + "\" was read as \"" + id
                    + "\"", null);
        }
        return id;
    }

    List<String> idList(Ctx.Fields f, String name, int max, String noun) {
        List<String> out = new ArrayList<>();
        List<Node> items = c.list(f, name, max, noun, "Name", true);
        for (int i = 0; i < items.size(); i++) {
            String id = id(items.get(i), f.path.item(name, i, noun, null, true), "a " + noun, "Name");
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Bone or part names ({@code p3_weapon_l}): {@code a-z 0-9 _ /}, checked for their form only. */
    List<String> boneList(Ctx.Fields f, String name) {
        List<String> out = new ArrayList<>();
        List<Node> items = c.list(f, name, 12, "bone", "Bone", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item(name, i, "bone", null, true);
            if (!(n instanceof Str s) || !s.value().matches("[a-z0-9_/]{1,48}")) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be a bone or part name such as"
                        + " \"p3_weapon_l\", not " + c.shown(n), "Bone");
                continue;
            }
            out.add(s.value());
        }
        return out;
    }

    // ================================================================== moves (12.7, 12.8)

    void readMoves(Rarity r) {
        rarity = r;
        List<Node> ml = c.list(top, "moves", HeroCatalogue.MAX_MOVES, "move", "Move", false);
        for (int i = 0; i < ml.size(); i++) {
            addMove(move(ml.get(i), top.path.item("moves", i, "move", c.peekName(ml.get(i)), false), Slot.SKILL,
                    false), ml.get(i));
        }
        if (top.has("skills")) {
            c.report(Severity.NOTE, Code.V1_FIELD, top.line("skills"), top.at("skills"), "\"skills\" is a format-1"
                    + " field; its entries were read as moves (HEROES_V2 12.3)", null);
            List<Node> sk = c.list(top, "skills", HeroCatalogue.MAX_MOVES, "skill", "Move", false);
            for (int i = 0; i < sk.size(); i++) {
                if (moves.size() >= HeroCatalogue.MAX_MOVES) {
                    c.report(Severity.PART_LEFT_OUT, Code.LIST_TOO_LONG, sk.get(i).line(), top.path.item("skills", i,
                            "skill", c.peekName(sk.get(i)), false), "a hero has at most " + HeroCatalogue.MAX_MOVES
                            + " moves", "Move");
                    break;
                }
                addMove(move(sk.get(i), top.path.item("skills", i, "skill", c.peekName(sk.get(i)), false), Slot.SKILL,
                        true), sk.get(i));
            }
        }
        Node un = top.node("ultimate");
        if (un != null) {
            ultimate = move(un, Path.ROOT.named("ultimate", c.peekName(un)), Slot.ULTIMATE, false);
            if (ultimate != null && un instanceof Obj uo && uo.get("awakened") != null) {
                Node an = uo.get("awakened");
                Path ap = Path.ROOT.named("ultimate", c.peekName(un)).named("awakened", c.peekName(an));
                awakened = move(an, ap, Slot.ULTIMATE, false);
                if (awakened != null && awakened.id().equals(ultimate.id())) {
                    c.report(Severity.PART_LEFT_OUT, Code.NAME_TAKEN, an.line(), ap, "the awakened ultimate needs an"
                            + " id of its own, not \"" + ultimate.id() + "\"", "Ultimate");
                    awakened = null;
                }
            }
            for (Move m : moves) {
                for (Move u : new Move[]{ultimate, awakened}) {
                    if (u != null && m.id().equals(u.id())) {
                        c.report(Severity.PART_LEFT_OUT, Code.NAME_TAKEN, un.line(), movePaths.get(u), "a move is"
                                + " already named \"" + u.id() + "\"", "Ultimate");
                        if (u == ultimate) {
                            ultimate = null;
                            awakened = null;
                        } else {
                            awakened = null;
                        }
                    }
                }
            }
        }
    }

    private void addMove(Move m, Node n) {
        if (m == null) {
            return;
        }
        for (Move o : moves) {
            if (o.id().equals(m.id())) {
                c.report(Severity.PART_LEFT_OUT, Code.NAME_TAKEN, n.line(), movePaths.get(m), "another move already"
                        + " has the id \"" + m.id() + "\"; ids are unique in a file", "Move");
                return;
            }
        }
        moves.add(m);
    }

    /** A move of the moves list, a v1 skill read as a move ({@code fromSkill}), the ultimate or its awakened twin. */
    Move move(Node n, Path p, Slot slot, boolean fromSkill) {
        boolean ult = slot == Slot.ULTIMATE;
        String partNoun = ult ? "Ultimate" : "Move";
        if (!(n instanceof Obj o)) {
            c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"id\": ...,"
                    + " \"name\": ..., \"form\": ... }, not " + c.shown(n), partNoun);
            return null;
        }
        Map<String, String> elsewhere = new HashMap<>();
        Ctx.Fields f = c.new Fields(o, p, HeroCatalogue.MOVE, elsewhere);
        Need need = new Need(Severity.PART_LEFT_OUT, partNoun);
        String name = c.text(f, "name", 24, need);
        if (name == null) {
            if (!f.has("name") || f.node("name") instanceof Str) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"name\" is missing. Give the "
                        + partNoun.toLowerCase(Locale.ROOT) + " a name, for example \"name\": \"Three Cuts\"", partNoun);
            }
            return null;
        }
        // id first: every later message about the move can name it
        String id;
        if (f.has("id")) {
            id = id(f.node("id"), f.at("id"), "a move", partNoun);
            if (id == null) {
                return null;
            }
        } else {
            id = HeroReader.normaliseId(name);
            if (id.length() < HeroCatalogue.MOVE_ID_MIN || !id.matches("[a-z][a-z0-9_]*")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"id\" is missing and the name gives"
                        + " none. Add one, for example \"id\": \"three_cut\"", partNoun);
                return null;
            }
            if (id.length() > HeroCatalogue.MOVE_ID_MAX) {
                id = HeroReader.cut(id, HeroCatalogue.MOVE_ID_MAX).replaceAll("_+$", "");
            }
            if (!fromSkill) {
                c.report(Severity.ADJUSTED, Code.MOVE_ID, o.line(), p, "\"id\" is missing; the name gives it: \"" + id
                        + "\" (its animation is move." + id + ")", null);
            }
        }
        if (HeroCatalogue.LOCOMOTION_NAMES.contains(id)) {
            c.report(Severity.PART_LEFT_OUT, Code.MOVE_ID_RESERVED, f.line("id"), f.has("id") ? f.at("id") : p, "\""
                    + id + "\" is a movement animation's name (move." + id + " would replace it for this hero); give"
                    + " the move another id", partNoun);
            return null;
        }
        if (!f.has("form")) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"form\" is missing. "
                    + c.allowed(HeroCatalogue.vocab("form2")), partNoun);
            return null;
        }
        String fw = c.word(f, "form", "form2", null, null, Mode.PART, partNoun);
        if (fw == null) {
            return null;
        }
        Form form = HeroCatalogue.enumOf(Form.class, fw);
        FormSpec fs = HeroCatalogue.FORMS.get(form);
        boolean fresh = form.since() >= 2;
        Map<String, Field> ff = fresh ? HeroCatalogue.FORM_FIELDS.get(form) : Map.of();
        Map<String, Field> ab = HeroCatalogue.ABILITY;
        String formText = "form \"" + form.id() + "\"";
        ZoneAt zoneAt = ZoneAt.TARGET;
        double delay = 0, duration = 0, interval = 1, range = 0, radius = 0, width = 1, hitInterval = 0.3;
        int count = 3, angle = 70, hits = 1;
        String projectile = "arrow";
        FormExtra extra = FormExtra.NONE;
        if (!fresh) {
            for (String k : HeroCatalogue.NEW_FORM_FIELD_NAMES) {
                f.unused(k, "\"" + k + "\" is not used by " + formText);
            }
            if (fs.zone()) {
                zoneAt = HeroCatalogue.enumOf(ZoneAt.class, c.word(f, "zone_at", "zone_at", "target", null,
                        Mode.OPTIONAL, null));
                delay = c.number(f, ab, "delay", zoneAt == ZoneAt.TARGET ? 1 : 0);
                duration = c.number(f, ab, "duration", 0);
                interval = c.number(f, ab, "interval", 1);
            } else {
                for (String k : List.of("zone_at", "delay", "duration", "interval")) {
                    f.unused(k, "\"" + k + "\" is not used by " + formText);
                }
            }
            if (fs.rangeFixed()) {
                f.unused("range", "a melee move always reaches " + num(fs.rangeDefault()) + " blocks; \"range\" is"
                        + " fixed");
                range = fs.rangeDefault();
            } else if (fs.usesRange() && !(form == Form.ZONE && zoneAt != ZoneAt.TARGET)) {
                range = c.number(f, "range", fs.rangeMin(), fs.rangeMax(), fs.rangeDefault(), false, Unit.BLOCKS, null);
            } else {
                f.unused("range", "\"range\" is not used by " + formText + (form == Form.ZONE ? " at the hero" : ""));
            }
            if (fs.usesRadius()) {
                radius = c.number(f, "radius", fs.radiusMin(), fs.radiusMax(), fs.radiusDefault(), false, Unit.BLOCKS,
                        null);
            } else {
                f.unused("radius", "\"radius\" is not used by " + formText);
            }
            if (fs.count()) {
                count = (int) c.number(f, ab, "count", 3);
            } else {
                f.unused("count", "\"count\" is not used by " + formText);
            }
            if (fs.angle()) {
                angle = (int) c.number(f, ab, "angle", 70);
            } else {
                f.unused("angle", "\"angle\" is not used by " + formText);
            }
            if (fs.width()) {
                width = c.number(f, ab, "width", 1);
            } else {
                f.unused("width", "\"width\" is not used by " + formText);
            }
            if (fs.hits()) {
                hits = (int) c.number(f, ab, "hits", 1);
                hitInterval = c.number(f, ab, "hit_interval", 0.3);
            } else {
                f.unused("hits", "\"hits\" is not used by " + formText);
                f.unused("hit_interval", "\"hit_interval\" is not used by " + formText);
            }
            if (fs.projectile()) {
                projectile = c.word(f, "projectile", "projectile", "arrow", null, Mode.OPTIONAL, null);
            } else {
                f.unused("projectile", "\"projectile\" is not used by " + formText);
            }
        } else {
            for (String k : List.of("zone_at", "delay", "duration", "interval", "range", "radius", "count", "angle",
                    "width", "hits", "hit_interval", "projectile")) {
                if (!ff.containsKey(k)) {
                    f.unused(k, "\"" + k + "\" is not used by " + formText);
                }
            }
            for (String k : HeroCatalogue.NEW_FORM_FIELD_NAMES) {
                if (!ff.containsKey(k)) {
                    f.unused(k, "\"" + k + "\" is not used by " + formText);
                }
            }
            range = ff.containsKey("range") ? c.number(f, ff, "range", ff.get("range").dflt())
                    : fs.rangeFixed() ? fs.rangeDefault() : 0;
            radius = ff.containsKey("radius") ? c.number(f, ff, "radius", ff.get("radius").dflt()) : 0;
            count = ff.containsKey("count") ? (int) c.number(f, ff, "count", ff.get("count").dflt()) : 3;
            duration = ff.containsKey("duration") ? c.number(f, ff, "duration", ff.get("duration").dflt()) : 0;
            delay = ff.containsKey("delay") ? c.number(f, ff, "delay", ff.get("delay").dflt()) : 0;
            interval = ff.containsKey("interval") ? c.number(f, ff, "interval", ff.get("interval").dflt()) : 1;
            width = ff.containsKey("width") ? c.number(f, ff, "width", ff.get("width").dflt()) : 1;
            angle = ff.containsKey("arc") ? (int) c.number(f, ff, "arc", ff.get("arc").dflt()) : 70;
            if (ff.containsKey("projectile")) {
                projectile = c.word(f, "projectile", "projectile", "arrow", null, Mode.OPTIONAL, null);
            }
            extra = new FormExtra(ff.containsKey("speed") ? c.number(f, ff, "speed", ff.get("speed").dflt()) : 0,
                    ff.containsKey("wall_stun") && c.bool(f, "wall_stun", false),
                    ff.containsKey("waves") ? (int) c.number(f, ff, "waves", 1) : 0,
                    ff.containsKey("throw") ? c.number(f, ff, "throw", 2) : 0,
                    ff.containsKey("up") ? c.number(f, ff, "up", 0) : 0,
                    ff.containsKey("stance") ? c.number(f, ff, "stance", 1) : 0,
                    ff.containsKey("absorb") ? (int) c.number(f, ff, "absorb", 80) : 0,
                    ff.containsKey("ranged") && c.bool(f, "ranged", false));
            if (ff.containsKey("from")) {
                // a dive's start: from the file, else from the hero's mobility once it is read (diveRules)
                String dw = c.word(f, "from", "dive_from", null, "its mobility's", Mode.OPTIONAL, null);
                extra = extra.withDiveFrom(dw == null ? null : HeroCatalogue.enumOf(HeroDef.DiveFrom.class, dw));
            }
        }
        Map<String, Field> sh = HeroCatalogue.MOVE;
        double windup = c.number(f, sh, "windup", Double.NaN);
        double cooldown = 8;
        int charges = 1, energyGain = 10, chargeRate = 100;
        if (ult) {
            for (String k : HeroCatalogue.SKILL_ONLY) {
                if (f.has(k)) {
                    c.report(Severity.NOTE, Code.FIELD_ELSEWHERE, f.line(k), f.at(k), "\"" + k + "\" is only used by"
                            + " moves (the ultimate is charged by energy); it was ignored", null);
                }
            }
            chargeRate = (int) c.number(f, sh, "charge_rate", 100);
        } else {
            if (f.has("charge_rate")) {
                c.report(Severity.NOTE, Code.FIELD_ELSEWHERE, f.line("charge_rate"), f.at("charge_rate"),
                        "\"charge_rate\" is only used by the ultimate; it was ignored", null);
            }
            if (f.has("awakened")) {
                c.report(Severity.NOTE, Code.FIELD_ELSEWHERE, f.line("awakened"), f.at("awakened"), "\"awakened\" is"
                        + " only used by the ultimate (its second ultimate); it was ignored", null);
            }
            cooldown = c.number(f, sh, "cooldown", 8);
            charges = (int) c.number(f, sh, "charges", 1);
            energyGain = (int) c.number(f, sh, "energy_gain", 10);
        }
        UseWhen useDefault = HeroCatalogue.defaultUseWhen(slot, form);
        UseWhen useWhen = HeroCatalogue.enumOf(UseWhen.class, c.word(f, "use_when", "use_when", useDefault.id(), null,
                Mode.OPTIONAL, null));
        if (useWhen == UseWhen.OUT_OF_COMBAT && !form.friendly()) {
            c.report(Severity.ADJUSTED, Code.OUT_OF_COMBAT, f.line("use_when"), f.at("use_when"), "\"out_of_combat\""
                    + " is only for heals and buffs (forms self, ally and allies); read as \"always\"", null);
            useWhen = UseWhen.ALWAYS;
        }
        ValueRange vr = HeroCatalogue.useValue(useWhen);
        double useValue = 0;
        if (vr != null) {
            useValue = c.number(f, "use_value", vr.min(), vr.max(), vr.dflt(), false, Unit.NONE, null);
        } else {
            f.unused("use_value", "\"use_value\" is not used by use_when \"" + useWhen.id() + "\"");
        }
        c.unlockIgnored(f);
        boolean moveForm = form == Form.LEAP || form == Form.DASH || form == Form.CHARGE || form == Form.DIVE;
        FxCtx ctx = new FxCtx(slot, ult ? "this ultimate" : "this move", form.id(), form.friendly(), form == Form.SELF,
                moveForm, false, null);
        List<Effect> fx;
        List<Strike> strikes = List.of();
        if (form == Form.COMBO) {
            f.unused("effects", "a combo's effects are its strikes'");
            strikes = strikes(f, p, ctx);
            if (strikes.size() < 2) {
                c.report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, o.line(), p, "a combo needs at least 2 strikes that"
                        + " work", partNoun);
                return null;
            }
            fx = List.of();
        } else {
            if (!f.has("effects")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"effects\" is missing: what the "
                        + partNoun.toLowerCase(Locale.ROOT) + " does", partNoun);
                return null;
            }
            fx = c.effects(f, "effects", 5, ctx);
            if (fx.isEmpty()) {
                c.report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, o.line(), p, "the " + partNoun.toLowerCase(Locale.ROOT)
                        + " has no effect left that works", partNoun);
                return null;
            }
        }
        List<List<Effect>> perShot = form == Form.BARRAGE ? perShot(f, p, ctx, count) : List.of();
        HeroDef.Look look = c.look(f, p, HeroCatalogue.defaultPose(form), ult, HeroCatalogue.fangsForm(form));
        Ability a = new Ability(slot, name, c.orEmpty(c.text(f, "description", 200, null)), form, range, radius, count,
                angle, width, hits, hitInterval, projectile, zoneAt, delay, duration, interval,
                Double.isNaN(windup) ? 0 : windup, cooldown, charges, energyGain, chargeRate, useWhen, useValue, 1, fx,
                look, c.text(f, "line", HeroCatalogue.LINE_MAX, null), Numbers.NONE);
        c.where.put(a, new Pos(p, o.line(), f));
        // the move's own fields
        MoveClass cls = null;
        String cw = c.word(f, "class", "move_class", null, null, Mode.OPTIONAL, null);
        if (cw != null) {
            cls = HeroCatalogue.enumOf(MoveClass.class, cw);
            if (ult && cls != MoveClass.ULTIMATE || !ult && cls == MoveClass.ULTIMATE) {
                MoveClass to = ult ? MoveClass.ULTIMATE : HeroCatalogue.classOf(form);
                c.report(Severity.ADJUSTED, Code.CLASS_RAISED, f.line("class"), f.at("class"), "class \"" + cls.id()
                        + "\" " + (ult ? "is not for an ultimate; an ultimate is class \"ultimate\""
                        : "belongs to the ultimate; read as its form's class, \"" + to.id() + "\""), null);
                cls = to;
            }
        }
        Frames frames = frames(f, windup);
        List<Band> bands = bands(f, form, range, zoneAt);
        int weight = (int) c.number(f, sh, "weight", 5);
        List<Integer> phases = phaseNumbers(f, "phases");
        Condition prefer = f.has("prefer_when") ? c.condition(f, "prefer_when", new Need(Severity.PART_LEFT_OUT,
                "Preference")) : null;
        List<Followup> followups = followups(f);
        boolean repeatable = c.bool(f, "repeatable", false), breaksGuard = c.bool(f, "breaks_guard", false),
                unblockable = c.bool(f, "unblockable", false), superArmor = c.bool(f, "super_armor", false);
        if (form == Form.GRAB && !unblockable) {
            if (f.has("unblockable")) {
                c.report(Severity.ADJUSTED, Code.GRAB_RULE, f.line("unblockable"), f.at("unblockable"), "a grab is"
                        + " always unblockable (HEROES_V2 6.5: a shield does not stop a hand); read as true", null);
            }
            unblockable = true;
        }
        int channel = channel(f);
        Telegraph tele = telegraph(f);
        String anim = ult ? (p.machine().endsWith("awakened") ? "ultimate.awakened" : "ultimate") : "move." + id;
        Move m = new Move(id, a, cls, frames, bands, weight, phases, prefer, followups, repeatable, breaksGuard,
                unblockable, superArmor, channel, tele, extra, strikes, perShot, anim);
        movePaths.put(m, p);
        moveLines.put(m, o.line());
        moveWhere.put(id, new Pos(p, o.line(), f));
        if (ult && a.line() == null) {
            c.report(Severity.NOTE, Code.ULTIMATE_LINE, o.line(), p, "an ultimate speaks a line as its wind-up"
                    + " starts (HEROES_V2 6.4); this one has none, so its temperament's fallback line is said", null);
        }
        return m;
    }

    /** The authored frames; parts the file leaves out are NaN (the balance pass gives them the class minimum). */
    Frames frames(Ctx.Fields f, double v1Windup) {
        Node n = f.node("frames");
        double w = v1Windup, a = Double.NaN, r = Double.NaN;
        if (n instanceof Obj o) {
            Ctx.Fields ff = c.fields(o, f.at("frames"), HeroCatalogue.FRAMES);
            if (ff.has("windup")) {
                if (!Double.isNaN(v1Windup)) {
                    c.report(Severity.NOTE, Code.FIELD_UNUSED, f.line("windup"), f.at("windup"), "\"windup\" is"
                            + " \"frames.windup\" in format 2, and the file gives both; \"frames\" was used", null);
                }
                w = c.number(ff, HeroCatalogue.FRAMES, "windup", Double.NaN);
            }
            a = c.number(ff, HeroCatalogue.FRAMES, "active", Double.NaN);
            r = c.number(ff, HeroCatalogue.FRAMES, "recovery", Double.NaN);
            c.where.put(ff, new Pos(f.at("frames"), o.line(), ff));
        } else if (n != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("frames"), "should be an object { \"windup\":"
                    + " 0.8, \"active\": 0.2, \"recovery\": 0.8 }, not " + c.shown(n) + ". It was ignored", null);
        }
        return new Frames(w, a, r);
    }

    List<Band> bands(Ctx.Fields f, Form form, double range, ZoneAt zoneAt) {
        if (!f.has("band")) {
            return HeroCatalogue.defaultBands(form, range, zoneAt);
        }
        Node n = f.node("band");
        List<Node> items = n instanceof Arr a ? a.items() : List.of(n);
        Set<Band> out = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Node in = items.get(i);
            Path p = f.path.item("band", i, "band", null, true);
            if (in instanceof Str s && HeroCatalogue.norm(s.value()).equals("any")) {
                out.addAll(List.of(Band.values()));
                continue;
            }
            if (!(in instanceof Str s)) {
                c.report(Severity.ADJUSTED, Code.WRONG_TYPE, in.line(), p, "should be close, mid, far or any, not "
                        + c.shown(in) + ". It was ignored", null);
                continue;
            }
            String w = c.resolve(s.value(), "band", null, null, Mode.OPTIONAL, null, in.line(), p);
            if (w != null) {
                out.add(HeroCatalogue.enumOf(Band.class, w));
            }
        }
        if (!(n instanceof Arr) && !(n instanceof Str)) {
            return HeroCatalogue.defaultBands(form, range, zoneAt);
        }
        List<Band> l = new ArrayList<>();
        for (Band b : Band.values()) {
            if (out.contains(b)) {
                l.add(b);
            }
        }
        return l.isEmpty() ? HeroCatalogue.defaultBands(form, range, zoneAt) : l;
    }

    /** Phase numbers 1-3 (a single number reads as a list of one). */
    List<Integer> phaseNumbers(Ctx.Fields f, String name) {
        if (!f.has(name)) {
            return List.of();
        }
        Node n = f.node(name);
        List<Node> items = n instanceof Arr a ? a.items() : List.of(n);
        Set<Integer> out = new java.util.TreeSet<>();
        for (int i = 0; i < items.size(); i++) {
            Node in = items.get(i);
            Path p = f.path.item(name, i, "phase", null, true);
            double v = in instanceof Num x ? x.value() : Double.NaN;
            if (Double.isNaN(v) || v != Math.rint(v)) {
                c.report(Severity.ADJUSTED, Code.WRONG_TYPE, in.line(), p, "should be a phase number 1, 2 or 3, not "
                        + c.shown(in) + ". It was ignored", null);
                continue;
            }
            int iv = (int) v;
            if (iv < 1 || iv > HeroCatalogue.MAX_PHASES) {
                c.report(Severity.ADJUSTED, Code.CLAMPED, in.line(), p, iv + " is not a phase. Allowed 1 to "
                        + HeroCatalogue.MAX_PHASES + ". It was ignored", null);
                continue;
            }
            out.add(iv);
        }
        return List.copyOf(out);
    }

    List<Followup> followups(Ctx.Fields f) {
        List<Followup> out = new ArrayList<>();
        List<Node> items = c.list(f, "followups", 4, "followup", "Followup", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item("followups", i, "followup", null, true);
            if (n instanceof Str) {
                String id = id(n, p, "a move", "Followup");
                if (id != null) {
                    out.add(new Followup(id, 100, 0));
                }
                continue;
            }
            if (!(n instanceof Obj o)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"move\": ...,"
                        + " \"chance\": 70 }, not " + c.shown(n), "Followup");
                continue;
            }
            Ctx.Fields ff = c.fields(o, p, HeroCatalogue.FOLLOWUP);
            if (!ff.has("move")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"move\" is missing: the id of the move"
                        + " that follows", "Followup");
                continue;
            }
            String id = id(ff.node("move"), ff.at("move"), "a move", "Followup");
            if (id == null) {
                continue;
            }
            double max = c.number(ff, HeroCatalogue.FOLLOWUP, "max", Double.NaN);
            Followup fu = new Followup(id, (int) c.number(ff, HeroCatalogue.FOLLOWUP, "chance", 100),
                    Double.isNaN(max) ? 0 : (int) max);
            c.where.put(fu, new Pos(p, o.line(), ff));
            out.add(fu);
        }
        return out;
    }

    int channel(Ctx.Fields f) {
        Node n = f.node("channel");
        if (n == null) {
            return 0;
        }
        if (n instanceof HeroJson.Bool b) {
            return b.value() ? 15 : 0;
        }
        if (n instanceof Num) {
            Map<String, Field> sh = Map.of("channel", new Field("channel", HeroCatalogue.Kind.INT, Unit.PERCENT, 5, 30,
                    15, null, false));
            return (int) c.number(f, sh, "channel", 15);
        }
        if (n instanceof Obj o) {
            Ctx.Fields cf = c.fields(o, f.at("channel"), HeroCatalogue.CHANNEL);
            return (int) c.number(cf, HeroCatalogue.CHANNEL, "interrupt", 15);
        }
        c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("channel"), "should be an object { \"interrupt\":"
                + " 15 }, not " + c.shown(n) + ". It was ignored", null);
        return 0;
    }

    Telegraph telegraph(Ctx.Fields f) {
        Node n = f.node("telegraph");
        if (n == null) {
            return null;
        }
        if (n instanceof Str s) {
            String w = c.resolve(s.value(), "decal", null, null, Mode.OPTIONAL, null, s.line(), f.at("telegraph"));
            return w == null ? null : new Telegraph(HeroCatalogue.enumOf(Decal.class, w), HeroDef.NO_COLOR);
        }
        if (n instanceof Obj o) {
            Ctx.Fields tf = c.fields(o, f.at("telegraph"), HeroCatalogue.TELEGRAPH);
            String w = c.word(tf, "shape", "decal", null, null, Mode.OPTIONAL, null);
            int color = c.color(tf, "color", "the hero's accent");
            return w == null && color == HeroDef.NO_COLOR ? null : new Telegraph(w == null ? null
                    : HeroCatalogue.enumOf(Decal.class, w), color);
        }
        c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("telegraph"), "should be an object { \"shape\":"
                + " \"disc\" }, not " + c.shown(n) + ". It was ignored", null);
        return null;
    }

    List<Strike> strikes(Ctx.Fields f, Path p, FxCtx ctx) {
        List<Strike> out = new ArrayList<>();
        List<Node> items = c.list(f, "strikes", 4, "strike", "Strike", true);
        if (!f.has("strikes")) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, f.obj.line(), p, "\"strikes\" is missing: a combo is 2 to 4"
                    + " strikes, each with its effects", "Move");
            return out;
        }
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path sp = p.item("strikes", i, "strike", null, true);
            if (!(n instanceof Obj o)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), sp, "should be an object { \"effects\":"
                        + " [...] }, not " + c.shown(n), "Strike");
                continue;
            }
            Ctx.Fields sf = c.fields(o, sp, HeroCatalogue.STRIKE);
            if (!sf.has("effects")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), sp, "\"effects\" is missing: what the strike"
                        + " does", "Strike");
                continue;
            }
            double radius = c.number(sf, HeroCatalogue.STRIKE, "radius", 0);
            List<Effect> fx = c.effects(sf, "effects", 3, ctx);
            if (fx.isEmpty()) {
                c.report(Severity.PART_LEFT_OUT, Code.NO_EFFECTS, o.line(), sp, "the strike has no effect left that"
                        + " works", "Strike");
                continue;
            }
            Strike s = new Strike(c.number(sf, HeroCatalogue.STRIKE, "windup", 0),
                    c.number(sf, HeroCatalogue.STRIKE, "active", 0), radius, fx);
            c.where.put(s, new Pos(sp, o.line(), sf));
            out.add(s);
        }
        return out;
    }

    List<List<Effect>> perShot(Ctx.Fields f, Path p, FxCtx ctx, int count) {
        List<List<Effect>> out = new ArrayList<>();
        List<Node> items = c.list(f, "per_shot", Math.min(10, count), "shot", "Shot", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path sp = p.item("per_shot", i, "shot", null, true);
            if (n instanceof Arr a && a.items().isEmpty()) {
                // an empty list is a shot with only the move's effects: kept, so the lists still cycle in order
                out.add(List.of());
                continue;
            }
            Obj holder = new Obj(n.line(), n.col(), List.of(new Member("effects", n.line(), n.col(), n)));
            Ctx.Fields sf = c.fields(holder, sp, Map.of("effects", new Field("effects", HeroCatalogue.Kind.LIST,
                    Unit.NONE, 1, 3, Double.NaN, null, true)));
            List<Effect> fx = c.effects(sf, "effects", 3, ctx);
            if (fx.isEmpty()) {
                // its effects were left out (each reported); the shot keeps its place in the cycle with none
                c.report(Severity.ADJUSTED, Code.NO_EFFECTS, n.line(), sp, "this shot's list has no effect left that"
                        + " works; the shot keeps only the move's effects", null);
            }
            out.add(fx);
        }
        return out;
    }

    // ================================================================== effects and conditions, format 2's parts

    /** Format 2's {@code empower}: it enters a named state of {@code states} (12.9). */
    Effect empower(Ctx.Fields f, Map<String, Field> sh, Common cm, Path p, Obj o, Need need) {
        if (states.isEmpty()) {
            c.report(Severity.PART_LEFT_OUT, Code.EMPOWER_NO_STATE, o.line(), p, "\"empower\" needs a state in"
                    + " \"states\" at the top level of the hero", "Effect");
            return null;
        }
        if (c.missing(f, "seconds", o, p, need, "how long the state lasts (3 to 20)")) {
            return null;
        }
        double s = c.number(f, sh, "seconds", need);
        if (Double.isNaN(s)) {
            return null;
        }
        String state;
        if (f.has("state")) {
            state = id(f.node("state"), f.at("state"), "a state", "Effect");
            if (state == null) {
                return null;
            }
            if (state(state) == null) {
                c.report(Severity.PART_LEFT_OUT, Code.UNKNOWN_REFERENCE, f.line("state"), f.at("state"), "\"" + state
                        + "\" is not a state of this hero. Its states: " + stateNames(), "Effect");
                return null;
            }
        } else if (states.size() == 1) {
            state = states.get(0).name();
        } else {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"state\" is missing: which state it enters"
                    + " (" + stateNames() + ")", "Effect");
            return null;
        }
        return new HeroDef.Empower(cm, s, state);
    }

    private String stateNames() {
        List<String> l = new ArrayList<>();
        for (State s : states) {
            l.add(s.name());
        }
        return String.join(", ", l);
    }

    /**
     * Format 2's condition: an object, or a bare word for one without a required number ({@code "target_blocking"});
     * {@code target_is_player} and {@code players_engaged} read with the owner's rule (6.15).
     */
    Condition condition(Node n, Path p, Need need) {
        if (n instanceof Str s) {
            n = new Obj(s.line(), s.col(), List.of(new Member("condition", s.line(), s.col(), s)));
        }
        if (!(n instanceof Obj o)) {
            c.need(need, Code.WRONG_TYPE, n.line(), p, "should be an object { \"condition\": ... } or a condition's"
                    + " name, not " + c.shown(n));
            return null;
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.CONDITION_2);
        if (!f.has("condition")) {
            c.need(need, Code.MISSING, o.line(), p, "\"condition\" is missing. "
                    + c.allowed(HeroCatalogue.vocab("condition2")));
            return null;
        }
        ConditionType t;
        HeroDef.TargetKind kind = null;
        boolean player = f.node("condition") instanceof Str cs
                && HeroCatalogue.norm(cs.value()).equals(HeroCatalogue.TARGET_IS_PLAYER);
        if (player) {
            c.report(Severity.ADJUSTED, Code.EQUAL_TREATMENT, f.line("condition"), f.at("condition"), "\""
                    + HeroCatalogue.TARGET_IS_PLAYER + "\" was read as \"target_is\" with the kind \"person\": heroes"
                    + " treat players and NPCs alike, and a person is a player, an NPC or a hero alike", null);
            t = ConditionType.TARGET_IS;
            kind = HeroDef.TargetKind.PERSON;
        } else {
            String w = c.word(f, "condition", "condition2", null, null, Mode.PART, need.part());
            if (w == null) {
                return null;
            }
            t = HeroCatalogue.enumOf(ConditionType.class, w);
        }
        int value = 0;
        String mark = null, state = null;
        CondValue cv = HeroCatalogue.CONDITION_VALUES.get(t);
        if (HeroCatalogue.conditionNeedsValue(t)) {
            if (!f.has("value")) {
                c.need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"" + t.id() + "\" needs \"value\": a health"
                        + " share in percent (1 to 99)");
                return null;
            }
            double v = c.number(f, "value", 1, 99, Double.NaN, true, Unit.PERCENT, need);
            if (Double.isNaN(v)) {
                return null;
            }
            value = (int) v;
        } else if (HeroCatalogue.conditionNeedsMark(t)) {
            if (!f.has("mark")) {
                c.need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"" + t.id() + "\" needs \"mark\": the mark"
                        + " to look for");
                return null;
            }
            mark = c.mark(f, "mark", need);
            if (mark == null) {
                return null;
            }
            c.refs.add(new HeroReader.MarkRef(mark, f.at("mark"), f.line("mark")));
            value = (int) c.number(f, "value", 1, 10, 1, true, Unit.NONE, null);
        } else if (cv != null) {
            value = (int) Math.round(c.number(f, "value", cv.min(), cv.max(), cv.dflt(), true, cv.unit(), null));
        } else {
            f.unused("value", "\"value\" is not used by condition \"" + t.id() + "\"");
        }
        if (!HeroCatalogue.conditionNeedsMark(t)) {
            f.unused("mark", "\"mark\" is not used by condition \"" + t.id() + "\"");
        }
        if (t == ConditionType.TARGET_IS && !player) {
            if (!f.has("kind")) {
                c.need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"target_is\" needs \"kind\". "
                        + c.allowed(HeroCatalogue.vocab("target_kind")));
                return null;
            }
            String k = c.word(f, "kind", "target_kind", null, null, Mode.PART, need.part());
            if (k == null) {
                return null;
            }
            kind = HeroCatalogue.enumOf(HeroDef.TargetKind.class, k);
        } else if (!player) {
            f.unused("kind", "\"kind\" is only used by condition \"target_is\"");
        }
        if (t == ConditionType.SELF_STATE) {
            if (!f.has("state")) {
                c.need(need, Code.CONDITION_INCOMPLETE, o.line(), p, "\"self_state\" needs \"state\": the name of one"
                        + " of the hero's states");
                return null;
            }
            state = id(f.node("state"), f.at("state"), "a state", need.part());
            if (state == null) {
                return null;
            }
            if (state(state) == null) {
                c.need(need, Code.UNKNOWN_REFERENCE, f.line("state"), f.at("state"), "\"" + state + "\" is not a state"
                        + " of this hero" + (states.isEmpty() ? " (it has none)" : ". Its states: " + stateNames()));
                return null;
            }
        } else {
            f.unused("state", "\"state\" is only used by condition \"self_state\"");
        }
        return new Condition(t, value, mark, kind, state);
    }

    /**
     * A sound id in a {@code look} (SOUND.md 5), checked for its form only: a word of format 1's list, a vanilla event
     * ({@code minecraft:...} or bare), or {@code factionclash:hero.<set>.<name>}. Vanilla ids lose their prefix.
     */
    String sound(Ctx.Fields f) {
        Node n = f.node("sound");
        if (n == null) {
            return null;
        }
        if (n instanceof Str s && HeroCatalogue.soundIdForm(s.value())) {
            String v = s.value().trim();
            return v.startsWith("minecraft:") ? v.substring("minecraft:".length()) : v;
        }
        c.report(Severity.ADJUSTED, Code.SOUND_ID, n.line(), f.at("sound"), c.shown(n) + " is not a sound id. Write a"
                + " vanilla event such as \"item.shield.block\" or \"minecraft:entity.cat.hiss\", or a hero sound such"
                + " as \"factionclash:hero.natsuho_komezaki.claw\". It was ignored", null);
        return null;
    }

    // ================================================================== the rest of the hero, and the sense rules

    /** Everything after the passives and lines: assembled into the hero's {@link V2} with the sense rules of 14.1. */
    V2 finish(String name, Rarity r, Role role, Style style, Element element, Map<Element, Integer> resist,
              Stats stats, Behaviour behaviour, List<Passive> passives, Map<LineEvent, List<String>> lines) {
        rarity = r;
        applyGenerated();
        Boss boss = boss(role, resist);
        Leader leader = leader();
        List<Talent> talents = talents();
        HeroDef.Voice voice = voice();
        Recruit recruit = recruit();
        List<Relationship> rel = relationships();
        Revival revival = revival();
        Mobility mobility = mobility(element);
        diveRules(mobility);
        moveRules(boss);
        EndOfFight end = endOfFight(behaviour, boss);
        return new V2(gender, archetype, hybrid, moves, ultimate, awakened, states, boss, personality, end, leader,
                talents, voice, recruit, rel, revival, mobility, generatedHash);
    }

    private String generatedHash;

    /** herogen's {@code generated} block: frames and animation names from the markers win over the file's. */
    void applyGenerated() {
        Node n = top.node("generated");
        if (n == null) {
            return;
        }
        Path p = top.at("generated");
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be the object herogen writes, not "
                    + c.shown(n) + ". It was ignored", null);
            return;
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.GENERATED);
        generatedHash = c.text(f, "hash", 64, null);
        c.text(f, "generator", 40, null);
        List<Node> items = c.list(f, "moves", HeroCatalogue.MAX_MOVES + 2, "generated move", "Generated move", true);
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Node in = items.get(i);
            Path ip = p.item("moves", i, "generated move", null, true);
            if (!(in instanceof Obj io)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, in.line(), ip, "should be an object { \"id\": ...,"
                        + " \"frames\": {...} }, not " + c.shown(in), "Generated move");
                continue;
            }
            Ctx.Fields gf = c.fields(io, ip, HeroCatalogue.GENERATED_MOVE);
            if (!gf.has("id")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, io.line(), ip, "\"id\" is missing", "Generated move");
                continue;
            }
            String id = id(gf.node("id"), gf.at("id"), "a move", "Generated move");
            if (id == null) {
                continue;
            }
            Move m = findMove(id);
            if (m == null) {
                c.report(Severity.NOTE, Code.FRAMES_GENERATED, io.line(), ip, "\"" + id + "\" is not a move of this"
                        + " hero; the generated entry was ignored (rebuild the hero with herogen)", null);
                continue;
            }
            seen.add(id);
            String anim = c.text(gf, "anim", 48, null);
            Frames g = frames(gf, Double.NaN);
            Frames own = m.frames();
            Frames merged = new Frames(pick(g.windup(), own.windup()), pick(g.active(), own.active()),
                    pick(g.recovery(), own.recovery()));
            if (differs(g.windup(), own.windup()) || differs(g.active(), own.active())
                    || differs(g.recovery(), own.recovery())) {
                c.report(Severity.NOTE, Code.FRAMES_GENERATED, io.line(), ip, "the move \"" + id + "\" gives frames"
                        + " that differ from its animation's markers; the generated frames were used (they are what"
                        + " the animation shows)", null);
            }
            replace(m, new Move(m.id(), m.ability(), m.cls(), merged, m.bands(), m.weight(), m.phases(),
                    m.preferWhen(), m.followups(), m.repeatable(), m.breaksGuard(), m.unblockable(), m.superArmor(),
                    m.channel(), m.telegraph(), m.extra(), m.strikes(), m.perShot(), anim == null ? m.anim() : anim));
        }
        for (Move m : allMoves()) {
            if (!seen.contains(m.id())) {
                c.report(Severity.NOTE, Code.FRAMES_GENERATED, f.line("moves"), p.field("moves"), "the move \""
                        + m.id() + "\" has no generated animation; it plays a default made from its form and class"
                        + " (HEROES_V2 12.6)", null);
            }
        }
    }

    private static double pick(double generated, double own) {
        return Double.isNaN(generated) ? own : generated;
    }

    private static boolean differs(double generated, double own) {
        return !Double.isNaN(generated) && !Double.isNaN(own) && Math.abs(generated - own) > 0.05 + 1e-9;
    }

    private void replace(Move old, Move n) {
        Path p = movePaths.remove(old);
        Integer l = moveLines.remove(old);
        movePaths.put(n, p);
        moveLines.put(n, l);
        int i = moves.indexOf(old);
        if (i >= 0) {
            moves.set(i, n);
        } else if (old == ultimate) {
            ultimate = n;
        } else if (old == awakened) {
            awakened = n;
        }
    }

    Move findMove(String id) {
        for (Move m : allMoves()) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    List<Move> allMoves() {
        List<Move> l = new ArrayList<>(moves);
        if (ultimate != null) {
            l.add(ultimate);
        }
        if (awakened != null) {
            l.add(awakened);
        }
        return l;
    }

    // ------------------------------------------------------------------ the boss block (12.10)

    Boss boss(Role role, Map<Element, Integer> resist) {
        HeroCatalogue.RoleBoss rb = HeroCatalogue.ROLE_BOSS.get(role);
        int scale = HeroCatalogue.HEALTH_SCALE.get(rarity);
        int defPhases = HeroCatalogue.defaultPhaseCount(rarity);
        Node n = top.node("boss");
        Ctx.Fields f = null;
        Path p = top.at("boss");
        if (n instanceof Obj o) {
            Map<String, Field> sh = new LinkedHashMap<>(HeroCatalogue.BOSS);
            for (String k : HeroCatalogue.REMOVED_BOSS_FIELDS.keySet()) {
                if (!k.equals("players")) {
                    sh.put(k, new Field(k, HeroCatalogue.Kind.OBJECT, Unit.NONE, 0, 0, Double.NaN, null, false));
                }
            }
            f = c.fields(o, p, sh);
            for (String k : HeroCatalogue.REMOVED_BOSS_FIELDS.keySet()) {
                if (f.has(k)) {
                    c.report(Severity.NOTE, Code.REMOVED_FIELD, f.line(k), f.at(k), HeroCatalogue.REMOVED_BOSS_FIELDS
                            .get(k), null);
                }
            }
        } else if (n != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"poise\": ... }, not "
                    + c.shown(n) + ". The role's and rarity's boss numbers are used", null);
        }
        List<WeakSpot> weak = f == null ? List.of() : weakPoints(f);
        boolean otherWeakness = !weak.isEmpty() || resist.values().stream().anyMatch(v -> v <= -10);
        int healthScale = scale, maxHit = 12, poise = rb.poise();
        Enrage enrage = Enrage.DEFAULT;
        List<AddSpec> adds = List.of();
        Intro intro = null;
        boolean rebirth = false;
        HeroDef.Arena arena = HeroDef.Arena.OPEN;
        List<Phase> phases;
        List<Reaction> reactions = List.of();
        if (f != null) {
            healthScale = healthScale(f, scale);
            maxHit = (int) c.number(f, HeroCatalogue.BOSS, "max_hit_share", 12);
            if (f.has("poise")) {
                double pv = c.number(f, HeroCatalogue.BOSS, "poise", rb.poise());
                poise = (int) pv;
                if (poise > 0 && poise < 50) {
                    c.report(Severity.ADJUSTED, Code.POISE_RANGE, f.line("poise"), f.at("poise"), "poise is 0 (none)"
                            + " or 50 to 200. Set to 50", null);
                    poise = 50;
                } else if (poise == 0 && !otherWeakness) {
                    c.report(Severity.ADJUSTED, Code.POISE_RANGE, f.line("poise"), f.at("poise"), "poise 0 (a hero"
                            + " that never staggers) needs another weakness, a weak point or a resistance of -10 or"
                            + " lower. Set to the role's " + rb.poise(), null);
                    poise = rb.poise();
                }
            }
            enrage = enrage(f);
            adds = adds(f, "adds", p);
            intro = intro(f);
            rebirth = c.bool(f, "rebirth", false);
            if (rebirth && rarity != Rarity.LEGENDARY) {
                c.report(Severity.ADJUSTED, Code.REBIRTH_RARITY, f.line("rebirth"), f.at("rebirth"), "a rebirth (a"
                        + " final phase instead of death, once per fight) is for legendary heroes only; read as"
                        + " false", null);
                rebirth = false;
            }
            arena = arena(f);
            phases = phases(f, defPhases);
            reactions = reactions(f);
        } else {
            phases = defaultPhases(defPhases);
        }
        if (!otherWeakness) {
            c.report(Severity.NOTE, Code.NO_WEAKNESS, f == null ? top.obj.line() : f.obj.line(), f == null ? Path.ROOT
                    : p, "the hero has no weakness: no weak point and no resistance of -10 or lower (HEROES_V2 6.9"
                    + " asks for one, so a fight can be won by skill)", null);
        }
        if (weak.stream().anyMatch(w -> w.point() == WeakPoint.CHANNEL)
                && allMoves().stream().noneMatch(m -> m.channel() > 0)) {
            c.report(Severity.NOTE, Code.CHANNEL_NONE, f.line("weak_points"), f.at("weak_points"), "the \"channel\""
                    + " weak point breaks a channelled move, but no move of this hero has \"channel\"", null);
        }
        return new Boss(healthScale, maxHit, poise, phases, reactions, weak, enrage, adds, intro, rebirth, arena);
    }

    int healthScale(Ctx.Fields f, int dflt) {
        Node n = f.node("health_scale");
        if (n instanceof Obj o) {
            // a first draft wrote { "base": 350, "players": 50 }: the per-player part is gone (12.10)
            Map<String, Field> sh = new LinkedHashMap<>();
            sh.put("base", new Field("base", HeroCatalogue.Kind.INT, Unit.PERCENT, 200, 600, dflt, null, false));
            for (String k : List.of("players", "per_player", "health_per_player")) {
                sh.put(k, new Field(k, HeroCatalogue.Kind.NUM, Unit.NONE, 0, 0, Double.NaN, null, false));
            }
            Ctx.Fields hf = c.fields(o, f.at("health_scale"), sh);
            for (String k : List.of("players", "per_player", "health_per_player")) {
                if (hf.has(k)) {
                    c.report(Severity.NOTE, Code.REMOVED_FIELD, hf.line(k), hf.at(k),
                            HeroCatalogue.REMOVED_BOSS_FIELDS.get("players"), null);
                }
            }
            return (int) c.number(hf, sh, "base", dflt);
        }
        return (int) c.number(f, HeroCatalogue.BOSS, "health_scale", dflt);
    }

    List<WeakSpot> weakPoints(Ctx.Fields f) {
        List<WeakSpot> out = new ArrayList<>();
        List<Node> items = c.list(f, "weak_points", HeroCatalogue.MAX_WEAK_POINTS, "weak point", "Weak point", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item("weak_points", i, "weak point", null, true);
            String w;
            String hint = null;
            if (n instanceof Str s) {
                w = c.resolve(s.value(), "weak_point", null, null, Mode.PART, "Weak point", n.line(), p);
            } else if (n instanceof Obj o) {
                Ctx.Fields wf = c.fields(o, p, HeroCatalogue.WEAK_SPOT);
                if (!wf.has("point")) {
                    c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"point\" is missing. "
                            + c.allowed(HeroCatalogue.vocab("weak_point")), "Weak point");
                    continue;
                }
                w = c.word(wf, "point", "weak_point", null, null, Mode.PART, "Weak point");
                hint = c.text(wf, "hint", HeroCatalogue.LINE_MAX, null);
            } else {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be a weak point such as \"back\""
                        + " or an object { \"point\": ..., \"hint\": ... }, not " + c.shown(n), "Weak point");
                continue;
            }
            if (w == null) {
                continue;
            }
            WeakPoint wp = HeroCatalogue.enumOf(WeakPoint.class, w);
            if (out.stream().anyMatch(x -> x.point() == wp)) {
                c.report(Severity.ADJUSTED, Code.LIST_DUPLICATE, n.line(), p, "\"" + w + "\" is listed twice; once is"
                        + " enough", null);
                continue;
            }
            out.add(new WeakSpot(wp, hint));
        }
        return out;
    }

    Enrage enrage(Ctx.Fields f) {
        Node n = f.node("enrage");
        if (n == null) {
            return Enrage.DEFAULT;
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("enrage"), "should be an object { \"after\":"
                    + " 180 }, not " + c.shown(n) + ". The default enrage is used", null);
            return Enrage.DEFAULT;
        }
        Ctx.Fields ef = c.fields(o, f.at("enrage"), HeroCatalogue.ENRAGE);
        double after = c.number(ef, HeroCatalogue.ENRAGE, "after", Double.NaN);
        List<EnrageTrigger> tr = new ArrayList<>();
        for (String w : words(ef, "triggers", 3, "enrage_trigger", "trigger", "Trigger")) {
            tr.add(HeroCatalogue.enumOf(EnrageTrigger.class, w));
        }
        return new Enrage(Double.isNaN(after) ? 0 : after, tr, line(ef, "line", LineEvent.ENRAGE));
    }

    List<AddSpec> adds(Ctx.Fields f, String name, Path p) {
        List<AddSpec> out = new ArrayList<>();
        List<Node> items = c.list(f, name, 4, "add", "Add", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path ap = f.path.item(name, i, "add", null, true);
            String w;
            int count = 1;
            if (n instanceof Str s) {
                w = c.resolve(s.value(), "add", null, null, Mode.PART, "Add", n.line(), ap);
            } else if (n instanceof Obj o) {
                Ctx.Fields af = c.fields(o, ap, HeroCatalogue.ADD);
                if (!af.has("id")) {
                    c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), ap, "\"id\" is missing. "
                            + c.allowed(HeroCatalogue.vocab("add")), "Add");
                    continue;
                }
                w = c.word(af, "id", "add", null, null, Mode.PART, "Add");
                count = (int) c.number(af, HeroCatalogue.ADD, "count", 1);
            } else {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), ap, "should be an add such as \"decoy\" or"
                        + " { \"id\": \"decoy\", \"count\": 2 }, not " + c.shown(n), "Add");
                continue;
            }
            if (w != null) {
                out.add(new AddSpec(HeroCatalogue.enumOf(AddKind.class, w), count));
            }
        }
        return out;
    }

    Intro intro(Ctx.Fields f) {
        Node n = f.node("intro");
        if (n == null) {
            return null;
        }
        if (n instanceof Str) {
            return new Intro(c.line(n, f.at("intro"), LineEvent.INTRO), null);
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("intro"), "should be an object { \"line\": ..."
                    + " }, not " + c.shown(n) + ". It was ignored", null);
            return null;
        }
        Ctx.Fields inf = c.fields(o, f.at("intro"), HeroCatalogue.INTRO);
        return new Intro(line(inf, "line", LineEvent.INTRO), c.text(inf, "title", 32, null));
    }

    HeroDef.Arena arena(Ctx.Fields f) {
        Node n = f.node("arena");
        if (n == null) {
            return HeroDef.Arena.OPEN;
        }
        String w = null;
        if (n instanceof Str s) {
            w = c.resolve(s.value(), "arena", "open", null, Mode.OPTIONAL, null, n.line(), f.at("arena"));
        } else if (n instanceof Obj o) {
            Ctx.Fields af = c.fields(o, f.at("arena"), HeroCatalogue.ARENA);
            w = c.word(af, "prefers", "arena", "open", null, Mode.OPTIONAL, null);
        } else {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), f.at("arena"), "should be an object { \"prefers\":"
                    + " \"tight\" }, not " + c.shown(n) + ". Used \"open\"", null);
        }
        return w == null ? HeroDef.Arena.OPEN : HeroCatalogue.enumOf(HeroDef.Arena.class, w);
    }

    List<Phase> defaultPhases(int count) {
        List<Phase> l = new ArrayList<>();
        for (int at : HeroCatalogue.defaultPhaseStarts(count)) {
            l.add(new Phase(at, List.of(), List.of(), List.of(), List.of(), Transition.DEFAULT));
        }
        return l;
    }

    List<Phase> phases(Ctx.Fields f, int dflt) {
        if (!f.has("phases")) {
            return defaultPhases(dflt);
        }
        List<Phase> out = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        List<Node> items = c.list(f, "phases", HeroCatalogue.MAX_PHASES, "phase", "Phase", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item("phases", i, "phase", null, true);
            if (!(n instanceof Obj o)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"at\": 60, ... },"
                        + " not " + c.shown(n), "Phase");
                continue;
            }
            Ctx.Fields pf = c.fields(o, p, HeroCatalogue.PHASE);
            if (!pf.has("at")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"at\" is missing: the share of health"
                        + " (in percent) at which the phase begins", "Phase");
                continue;
            }
            double at = c.number(pf, HeroCatalogue.PHASE, "at", new Need(Severity.PART_LEFT_OUT, "Phase"));
            if (Double.isNaN(at)) {
                continue;
            }
            c.unlockIgnored(pf);
            List<String> mv = new ArrayList<>();
            for (String id : idList(pf, "moves", HeroCatalogue.MAX_MOVES, "move")) {
                if (findMove(id) == null) {
                    c.report(Severity.PART_LEFT_OUT, Code.UNKNOWN_REFERENCE, pf.line("moves"), pf.at("moves"), "\"" + id
                            + "\" is not a move of this hero", "Name");
                } else {
                    mv.add(id);
                }
            }
            List<String> st = new ArrayList<>();
            for (String id : idList(pf, "states", HeroCatalogue.MAX_STATES, "state")) {
                if (state(id) == null) {
                    c.report(Severity.PART_LEFT_OUT, Code.UNKNOWN_REFERENCE, pf.line("states"), pf.at("states"), "\""
                            + id + "\" is not a state of this hero" + (states.isEmpty() ? " (it has none)"
                            : ". Its states: " + stateNames()), "Name");
                } else {
                    st.add(id);
                }
            }
            phaseNumber = i + 1;
            Phase ph = new Phase((int) at, mv, st, adds(pf, "adds", p), statBonuses(pf, "stats", 4, p),
                    transition(pf, p));
            c.where.put(ph, new Pos(p, o.line(), pf));
            out.add(ph);
            lines.add(o.line());
            paths.add(p);
        }
        if (out.isEmpty()) {
            return defaultPhases(dflt);
        }
        // phases begin at 100 and go down: sorted, repeats left out
        List<Phase> sorted = new ArrayList<>(out);
        sorted.sort((a, b) -> Integer.compare(b.at(), a.at()));
        if (!sorted.equals(out)) {
            c.report(Severity.ADJUSTED, Code.PHASE_ORDER, f.line("phases"), f.at("phases"), "the phases were put in"
                    + " order, from the highest health share down", null);
        }
        List<Phase> l = new ArrayList<>();
        for (Phase ph : sorted) {
            if (!l.isEmpty() && l.get(l.size() - 1).at() == ph.at()) {
                int k = out.indexOf(ph);
                c.report(Severity.PART_LEFT_OUT, Code.PHASE_REPEATED, lines.get(k), paths.get(k), "another phase"
                        + " already begins at " + ph.at() + "%", "Phase");
                continue;
            }
            l.add(ph);
        }
        if (l.get(0).at() != 100) {
            Phase first = l.get(0);
            c.report(Severity.ADJUSTED, Code.PHASE_ORDER, c.where.get(first).line(), c.where.get(first).path()
                    .field("at"), "the first phase begins at full health; " + first.at() + " was read as 100", null);
            Phase fixed = new Phase(100, first.moves(), first.states(), first.adds(), first.stats(),
                    first.transition());
            c.where.put(fixed, c.where.get(first));
            l.set(0, fixed);
        }
        return l;
    }

    /** The phase being read (1-3 by its place in the file), for its transition line's event. */
    private int phaseNumber;

    /** A line of the boss block, with the rules of {@code lines} for its event (placeholders, {player} as {enemy}). */
    String line(Ctx.Fields f, String field, LineEvent event) {
        Node n = f.node(field);
        return n == null ? null : c.line(n, f.at(field), event);
    }

    Transition transition(Ctx.Fields pf, Path p) {
        Node n = pf.node("transition");
        if (n == null) {
            return Transition.DEFAULT;
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), pf.at("transition"), "should be an object"
                    + " { \"seconds\": 1.2, \"line\": ... }, not " + c.shown(n) + ". The default transition is used",
                    null);
            return Transition.DEFAULT;
        }
        Ctx.Fields tf = c.fields(o, pf.at("transition"), HeroCatalogue.TRANSITION);
        double radius = 4, strength = 2;
        Node sn = tf.node("shockwave");
        if (sn instanceof Obj so) {
            Ctx.Fields sf = c.fields(so, tf.at("shockwave"), HeroCatalogue.SHOCKWAVE);
            radius = c.number(sf, HeroCatalogue.SHOCKWAVE, "radius", 4);
            strength = c.number(sf, HeroCatalogue.SHOCKWAVE, "strength", 2);
        } else if (sn != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, sn.line(), tf.at("shockwave"), "should be an object"
                    + " { \"radius\": 4, \"strength\": 2 }, not " + c.shown(sn) + ". The default shockwave is used",
                    null);
        }
        FxCtx ctx = new FxCtx(Slot.PASSIVE, "a phase transition", "self", true, true, false, false, null);
        List<Effect> fx = c.effects(tf, "effects", 3, ctx);
        Transition t = new Transition(c.number(tf, HeroCatalogue.TRANSITION, "seconds", 1),
                line(tf, "line", phaseNumber == 3 ? LineEvent.PHASE_3 : LineEvent.PHASE_2), radius, strength, fx,
                c.word(tf, "face", "mood", null, null, Mode.OPTIONAL, null));
        c.where.put(t, new Pos(pf.at("transition"), o.line(), tf));
        return t;
    }

    List<Reaction> reactions(Ctx.Fields f) {
        List<Reaction> out = new ArrayList<>();
        List<Node> items = c.list(f, "reactions", HeroCatalogue.MAX_REACTIONS, "reaction", "Reaction", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = f.path.item("reactions", i, "reaction", null, true);
            if (!(n instanceof Obj o)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"when\": ...,"
                        + " \"move\": ... }, not " + c.shown(n), "Reaction");
                continue;
            }
            Ctx.Fields rf = c.fields(o, p, HeroCatalogue.REACTION);
            if (!rf.has("when") || !rf.has("move")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"" + (rf.has("when") ? "move" : "when")
                        + "\" is missing", "Reaction");
                continue;
            }
            Condition when = c.condition(rf, "when", new Need(Severity.PART_LEFT_OUT, "Reaction"));
            if (when == null) {
                continue;
            }
            String id = id(rf.node("move"), rf.at("move"), "a move", "Reaction");
            if (id == null) {
                continue;
            }
            Move m = findMove(id);
            if (m == null || m.ability().slot() == Slot.ULTIMATE) {
                c.report(Severity.PART_LEFT_OUT, Code.UNKNOWN_REFERENCE, rf.line("move"), rf.at("move"), "\"" + id
                        + "\" is not a move of this hero" + (m != null ? " (an ultimate fires on its energy, not as"
                        + " a reaction)" : ""), "Reaction");
                continue;
            }
            Reaction r = new Reaction(when, id, (int) c.number(rf, HeroCatalogue.REACTION, "chance", 50),
                    c.number(rf, HeroCatalogue.REACTION, "cooldown", 10));
            c.where.put(r, new Pos(p, o.line(), rf));
            out.add(r);
        }
        return out;
    }

    // ------------------------------------------------------------------ moves across the hero (6.3, 6.7, 6.12)

    /**
     * The rules that need every move: phases, followup strings (HEROES_V2 6.3 and the kit-gaps decision: a move
     * follows itself only when {@code repeatable}, the string of 4 moves counts every repeat, {@code max} caps the
     * repeats; no loops through other moves), moves nothing leads to, reach, and phases that add nothing.
     */
    /**
     * The {@code dive} form's rules (MOBILITY_AND_WORK 7.3 and 8.3), now that the mobility is known; each one is NOT
     * LOADED for that move: a dive needs an airborne trait (leaper, acrobat, glider or flyer, primary or secondary); a
     * dive {@code from: air} needs flight or gliding; a dive is never unblockable (a shield blocks it like any heavy
     * move); and a dive never leads into a grab (nothing carries anyone through the air, 5.1.12: a grab in an aerial
     * window). A dive whose file gives no {@code from} starts from the air when the hero flies or glides, else from
     * its jump's apex.
     */
    void diveRules(Mobility mob) {
        boolean airborne = mob != null && (mob.primary().trait().airborne()
                || mob.secondary() != null && mob.secondary().trait().airborne());
        boolean flight = mob != null && (has(mob, MobilityTrait.FLYER) || has(mob, MobilityTrait.GLIDER));
        Map<String, Move> byId = new LinkedHashMap<>();
        for (Move m : allMoves()) {
            byId.put(m.id(), m);
        }
        for (Move m : new ArrayList<>(allMoves())) {
            if (m.form() != Form.DIVE) {
                continue;
            }
            String why = null;
            if (!airborne) {
                why = "a dive needs an airborne mobility trait (leaper, acrobat, glider or flyer: MOBILITY_AND_WORK 7.3)"
                        + "; this hero " + (mob == null ? "has no mobility" : "is a " + mob.primary().trait().id()
                        + (mob.secondary() == null ? "" : " and " + mob.secondary().trait().id()));
            } else if (m.unblockable()) {
                why = "a dive is never unblockable: a shield blocks it like any heavy move (MOBILITY_AND_WORK 7.3)";
            } else if (m.extra().diveFrom() == HeroDef.DiveFrom.AIR && !flight) {
                why = "\"from\": \"air\" needs flight or gliding; this hero dives from its jump's apex (\"from\":"
                        + " \"apex\")";
            } else {
                for (Followup fu : m.followups()) {
                    Move to = byId.get(fu.move());
                    if (to != null && to.form() == Form.GRAB) {
                        why = "a dive may not lead into a grab (\"" + to.id() + "\"): nothing carries anyone through"
                                + " the air (MOBILITY_AND_WORK 5.1.12)";
                        break;
                    }
                }
            }
            if (why != null) {
                c.report(Severity.PART_LEFT_OUT, Code.DIVE_RULE, moveLines.get(m), movePaths.get(m), why,
                        m.ability().slot() == Slot.ULTIMATE ? "Ultimate" : "Move");
                dropMove(m);
                continue;
            }
            if (m.extra().diveFrom() == null) {
                HeroDef.DiveFrom from = flight ? HeroDef.DiveFrom.AIR : HeroDef.DiveFrom.APEX;
                replace(m, new Move(m.id(), m.ability(), m.cls(), m.frames(), m.bands(), m.weight(), m.phases(),
                        m.preferWhen(), m.followups(), m.repeatable(), m.breaksGuard(), m.unblockable(),
                        m.superArmor(), m.channel(), m.telegraph(), m.extra().withDiveFrom(from), m.strikes(),
                        m.perShot(), m.anim()));
            }
        }
    }

    private static boolean has(Mobility m, MobilityTrait t) {
        return m.primary().trait() == t || m.secondary() != null && m.secondary().trait() == t;
    }

    /** Leaves a move out (its path and line go with it; an ultimate left out takes its awakened twin along). */
    private void dropMove(Move m) {
        movePaths.remove(m);
        moveLines.remove(m);
        if (!moves.remove(m)) {
            if (m == ultimate) {
                ultimate = null;
                awakened = null;
            } else if (m == awakened) {
                awakened = null;
            }
        }
    }

    void moveRules(Boss boss) {
        int phaseCount = boss.phases().size();
        // phases: the move's own list, else the boss phases that list it, else every phase
        boolean listed = boss.phases().stream().anyMatch(ph -> !ph.moves().isEmpty());
        for (Move m : new ArrayList<>(allMoves())) {
            List<Integer> ph = new ArrayList<>(m.phases());
            if (ph.isEmpty() && listed && m.ability().slot() != Slot.ULTIMATE) {
                for (int i = 0; i < phaseCount; i++) {
                    if (boss.phases().get(i).moves().contains(m.id())) {
                        ph.add(i + 1);
                    }
                }
            }
            if (ph.isEmpty()) {
                for (int i = 1; i <= phaseCount; i++) {
                    ph.add(i);
                }
            }
            List<Integer> in = new ArrayList<>();
            for (int i : ph) {
                if (i <= phaseCount) {
                    in.add(i);
                }
            }
            if (in.isEmpty()) {
                c.report(Severity.NOTE, Code.NEVER_PICKED, moveLines.get(m), movePaths.get(m), "the move is in no phase"
                        + " this hero has (it has " + phaseCount + (phaseCount == 1 ? " phase" : " phases") + ")",
                        null);
            }
            replace(m, m.withPhases(in));
        }
        followupRules();
        // weight 0: only a followup or a reaction starts it
        Set<String> led = new LinkedHashSet<>();
        for (Move m : allMoves()) {
            for (Followup fu : m.followups()) {
                led.add(fu.move());
            }
        }
        for (Reaction r : boss.reactions()) {
            led.add(r.move());
        }
        for (Move m : moves) {
            if (m.weight() == 0 && !led.contains(m.id())) {
                c.report(Severity.NOTE, Code.NEVER_PICKED, moveLines.get(m), movePaths.get(m), "weight 0 means it is"
                        + " never picked on its own, and no followup or reaction leads to it", null);
            }
        }
        // reach (6.12): a far-band move or a gap closer
        boolean reach = allMoves().stream().anyMatch(m -> m.bands().contains(Band.FAR)
                || HeroCatalogue.gapCloser(m.form()));
        if (!reach) {
            c.report(Severity.NOTE, Code.NO_REACH, top.obj.line(), Path.ROOT.field("moves"), "the hero has no far-band"
                    + " move and no gap closer (leap, dash or charge), so an enemy out of reach on a pillar or a wall"
                    + " meets only its mobility (HEROES_V2 6.12)", null);
        }
        // phases that add nothing (6.7)
        Set<String> before = new LinkedHashSet<>();
        for (int i = 0; i < phaseCount; i++) {
            Phase ph = boss.phases().get(i);
            Set<String> here = new LinkedHashSet<>();
            for (Move m : moves) {
                if (m.phases().contains(i + 1)) {
                    here.add(m.id());
                }
            }
            if (i > 0) {
                Set<String> added = new LinkedHashSet<>(here);
                added.removeAll(before);
                if (added.isEmpty() && ph.states().isEmpty() && ph.adds().isEmpty()) {
                    Pos pos = c.where.get(ph);
                    c.report(Severity.NOTE, Code.PHASE_NO_CHANGE, pos == null ? top.obj.line() : pos.line(),
                            pos == null ? Path.ROOT.field("boss") : pos.path(), "phase " + (i + 1) + " only changes"
                            + " numbers: it adds no move, state or add (HEROES_V2 6.7 asks each phase for a new"
                            + " mechanic)", null);
                }
            }
            before = here;
        }
    }

    void followupRules() {
        Map<String, Move> byId = new LinkedHashMap<>();
        for (Move m : allMoves()) {
            byId.put(m.id(), m);
        }
        // each move's followups, checked one by one in file order; edges kept so far form the graph
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (String id : byId.keySet()) {
            graph.put(id, new ArrayList<>());
        }
        for (Move m : new ArrayList<>(allMoves())) {
            List<Followup> kept = new ArrayList<>();
            Path base = movePaths.get(m);
            for (int i = 0; i < m.followups().size(); i++) {
                Followup fu = m.followups().get(i);
                Path p = base.item("followups", i, "followup", null, true);
                int line = c.where.containsKey(fu) ? c.where.get(fu).line() : moveLines.get(m);
                Move to = byId.get(fu.move());
                if (to == null) {
                    c.report(Severity.PART_LEFT_OUT, Code.UNKNOWN_REFERENCE, line, p, "\"" + fu.move() + "\" is not a"
                            + " move of this hero", "Followup");
                    continue;
                }
                if (to.ability().slot() == Slot.ULTIMATE) {
                    c.report(Severity.PART_LEFT_OUT, Code.FOLLOWUP_ULTIMATE, line, p, "\"" + fu.move() + "\" is an"
                            + " ultimate, which fires on its energy and never as a followup", "Followup");
                    continue;
                }
                if (fu.move().equals(m.id())) {
                    if (!m.repeatable()) {
                        c.report(Severity.PART_LEFT_OUT, Code.FOLLOWUP_SELF, line, p, "a move follows itself only"
                                + " when it is \"repeatable\": true (the string limit of " + HeroCatalogue.STRING_MAX
                                + " moves then ends the repeats)", "Followup");
                        continue;
                    }
                    kept.add(fu);
                    continue;
                }
                if (fu.max() > 0) {
                    c.report(Severity.NOTE, Code.FIELD_UNUSED, line, p.field("max"), "\"max\" caps how often a move"
                            + " repeats itself; this followup is another move, so it was ignored", null);
                    fu = new Followup(fu.move(), fu.chance(), 0);
                }
                graph.get(m.id()).add(fu.move());
                if (reaches(graph, fu.move(), m.id())) {
                    graph.get(m.id()).remove(graph.get(m.id()).size() - 1);
                    c.report(Severity.PART_LEFT_OUT, Code.FOLLOWUP_LOOP, line, p, "followups may not form a loop (\""
                            + m.id() + "\" -> \"" + fu.move() + "\" -> ... -> \"" + m.id() + "\"); this one was left"
                            + " out", "Followup");
                    continue;
                }
                if (longest(graph) > HeroCatalogue.STRING_MAX) {
                    graph.get(m.id()).remove(graph.get(m.id()).size() - 1);
                    c.report(Severity.PART_LEFT_OUT, Code.FOLLOWUP_STRING, line, p, "it would make a string of more"
                            + " than " + HeroCatalogue.STRING_MAX + " moves; a string is at most "
                            + HeroCatalogue.STRING_MAX + " moves long", "Followup");
                    continue;
                }
                kept.add(fu);
            }
            if (kept.size() != m.followups().size() || !kept.equals(m.followups())) {
                Move cur = byId.get(m.id());
                Move n = cur.withFollowups(kept);
                replace(cur, n);
                byId.put(m.id(), n);
            }
        }
    }

    private static boolean reaches(Map<String, List<String>> g, String from, String to) {
        java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>(List.of(from));
        Set<String> seen = new LinkedHashSet<>();
        while (!q.isEmpty()) {
            String x = q.poll();
            if (x.equals(to)) {
                return true;
            }
            if (seen.add(x)) {
                q.addAll(g.getOrDefault(x, List.of()));
            }
        }
        return false;
    }

    /** The longest string of moves in an acyclic followup graph (self-repeats not counted: the runtime caps them). */
    static int longest(Map<String, List<String>> g) {
        Map<String, Integer> memo = new HashMap<>();
        int best = 0;
        for (String k : g.keySet()) {
            best = Math.max(best, depth(g, k, memo));
        }
        return best;
    }

    private static int depth(Map<String, List<String>> g, String k, Map<String, Integer> memo) {
        Integer d = memo.get(k);
        if (d != null) {
            return d;
        }
        int best = 1;
        for (String n : g.getOrDefault(k, List.of())) {
            best = Math.max(best, 1 + depth(g, n, memo));
        }
        memo.put(k, best);
        return best;
    }

    // ------------------------------------------------------------------ the end of a fight (6.13)

    EndOfFight endOfFight(Behaviour b, Boss boss) {
        Temperament t = personality.temperament();
        Map<Trait, Integer> traits = personality.traits();
        EndOfFight end = EscapeRules.end(t, traits, personality.whenLosing(), personality.standsFor(),
                b.retreatBelow(), b.retreatBelowAuthored(), boss.lastPhaseAt());
        Ctx.Fields pf = personalityFields;
        if (t != null && personalityGiven) {
            WhenLosing dflt = EscapeRules.byTraits(t, HeroCatalogue.defaultTraits(t));
            WhenLosing mine = EscapeRules.byTraits(t, traits);
            if (dflt != mine) {
                int a = traits.get(Trait.AGGRESSION), h = traits.get(Trait.HONOUR), ca = traits.get(Trait.CAUTION);
                c.report(Severity.NOTE, Code.KIND_FLIP, pf.line("traits"), top.at("personality").field("traits"),
                        "a " + t.id().replace('_', ' ') + " usually " + (dflt == WhenLosing.LAST_STAND
                        ? "fights to the death" : "escapes") + "; with these traits this one " + (mine
                        == WhenLosing.LAST_STAND ? "fights to the death" : "escapes") + " (aggression " + a
                        + " + honour " + h + " - caution " + ca + " = " + EscapeRules.nerve(a, h, ca) + "; "
                        + EscapeRules.NERVE_LAST_STAND + " or more is a last stand)" + (personality.whenLosing() != null
                        ? ", although its \"when_losing\" decides" : ""), null);
            }
        }
        if (end.kind() == WhenLosing.LAST_STAND && personality.standsFor() != null && !personality.standsFor()
                .isEmpty()) {
            c.report(Severity.NOTE, Code.STANDS_IGNORED, pf.line("stands_for"), top.at("personality")
                    .field("stands_for"), "a hero that fights to the death stands everywhere; \"stands_for\" only"
                    + " matters for an escaping hero, so it was ignored", null);
        }
        if (end.kind() == WhenLosing.ESCAPE && boss.phases().size() > 1
                && boss.lastPhaseAt() < EscapeRules.LATE_LAST_PHASE) {
            c.report(Severity.NOTE, Code.LATE_LAST_PHASE, top.line("boss"), top.at("boss").field("phases"), "this hero"
                    + " escapes, and its last phase starts at " + boss.lastPhaseAt() + "%, under "
                    + EscapeRules.LATE_LAST_PHASE + "%: it leaves as that phase begins (it escapes under "
                    + end.fleeAt() + "%)", null);
        }
        return end;
    }

    // ------------------------------------------------------------------ leader (12.12)

    Leader leader() {
        Node n = top.node("leader");
        if (n == null) {
            return Leader.DEFAULT;
        }
        Path p = top.at("leader");
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"title\": ... }, not "
                    + c.shown(n) + ". The identity is generated", null);
            return Leader.DEFAULT;
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.LEADER);
        boolean eligible = c.bool(f, "eligible", true);
        String title = c.orEmpty(c.text(f, "title", 40, null));
        List<String> names = new ArrayList<>();
        for (String t : texts(f, "faction_names", 4, 32, "faction name")) {
            if (t.length() < 3) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, f.line("faction_names"), f.at("faction_names"),
                        "\"" + t + "\" is too short for a faction name (3 to 32 characters)", "Text");
            } else if (!names.contains(t)) {
                names.add(t);
            }
        }
        if (f.has("faction_names") && names.size() < 2) {
            c.report(Severity.NOTE, Code.LEADER_SHORT, f.line("faction_names"), f.at("faction_names"), "a leader"
                    + " names 2 to 4 factions (the first unused one is taken); the name generator fills the rest", null);
        }
        String shortName = c.text(f, "short_name", 14, null);
        leaderNames.addAll(names);
        if (shortName != null) {
            leaderNames.add(shortName);
        }
        int[] colors = {HeroDef.NO_COLOR, HeroDef.NO_COLOR, HeroDef.NO_COLOR};
        Node cn = f.node("colors");
        if (cn instanceof Obj co) {
            Ctx.Fields cf = c.fields(co, f.at("colors"), HeroCatalogue.COLORS);
            colors[0] = c.color(cf, "primary", "a generated colour");
            colors[1] = c.color(cf, "secondary", "a generated colour");
            colors[2] = c.color(cf, "accent", "a generated colour");
        } else if (cn != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, cn.line(), f.at("colors"), "should be an object { \"primary\":"
                    + " \"#RRGGBB\", ... }, not " + c.shown(cn) + ". The colours are generated", null);
        }
        Banner banner = banner(f);
        String bs = c.word(f, "base_style", "base_style", null, null, Mode.OPTIONAL, null);
        String cu = c.word(f, "culture", "culture", null, null, Mode.OPTIONAL, null);
        List<HeroDef.EconomyFocus> focus = new ArrayList<>();
        for (String w : words(f, "economy_focus", 2, "economy_focus", "focus", "Focus")) {
            focus.add(HeroCatalogue.enumOf(HeroDef.EconomyFocus.class, w));
        }
        return new Leader(eligible, title, names, shortName, c.orEmpty(c.text(f, "motto", 80, null)), colors[0],
                colors[1], colors[2], banner, bs == null ? null : HeroCatalogue.enumOf(HeroDef.BaseStyle.class, bs),
                cu == null ? null : HeroCatalogue.enumOf(HeroDef.Culture.class, cu), focus, c.text(f, "seat", 16, null));
    }

    Banner banner(Ctx.Fields f) {
        Node n = f.node("banner");
        if (n == null) {
            return null;
        }
        Path p = f.at("banner");
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"base\": ...,"
                    + " \"patterns\": [...] }, not " + c.shown(n) + ". The banner is generated", null);
            return null;
        }
        Ctx.Fields bf = c.fields(o, p, HeroCatalogue.BANNER);
        if (!bf.has("base")) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"base\" is missing: the banner's dye. "
                    + c.allowed(HeroCatalogue.vocab("dye")), "Banner");
            return null;
        }
        String base = c.word(bf, "base", "dye", null, null, Mode.PART, "Banner");
        if (base == null) {
            return null;
        }
        List<BannerLayer> layers = new ArrayList<>();
        List<Node> items = c.list(bf, "patterns", 4, "layer", "Layer", true);
        for (int i = 0; i < items.size(); i++) {
            Node in = items.get(i);
            Path lp = p.item("patterns", i, "layer", null, true);
            if (!(in instanceof Obj lo)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, in.line(), lp, "should be an object { \"pattern\":"
                        + " \"circle\", \"color\": \"white\" }, not " + c.shown(in), "Layer");
                continue;
            }
            Ctx.Fields lf = c.fields(lo, lp, HeroCatalogue.BANNER_LAYER);
            if (!lf.has("pattern") || !lf.has("color")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, lo.line(), lp, "\"" + (lf.has("pattern") ? "color"
                        : "pattern") + "\" is missing", "Layer");
                continue;
            }
            if (lf.node("pattern") instanceof Str ps && HeroCatalogue.norm(ps.value()).equals(
                    HeroCatalogue.REFUSED_PATTERN)) {
                c.report(Severity.PART_LEFT_OUT, Code.BANNER_MOJANG, ps.line(), lf.at("pattern"), "the company-logo"
                        + " pattern is not for faction banners (HEROES_V2 12.12)", "Layer");
                continue;
            }
            String pat = c.word(lf, "pattern", "banner_pattern", null, null, Mode.PART, "Layer");
            String col = c.word(lf, "color", "dye", null, null, Mode.PART, "Layer");
            if (pat != null && col != null) {
                layers.add(new BannerLayer(pat, HeroCatalogue.enumOf(HeroDef.Dye.class, col)));
            }
        }
        if (layers.size() < 2) {
            c.report(Severity.NOTE, Code.LEADER_SHORT, bf.line("patterns"), bf.at("patterns"), "a banner has 2 to 4"
                    + " pattern layers; the archetype's emblem fills the rest", null);
        }
        return new Banner(HeroCatalogue.enumOf(HeroDef.Dye.class, base), layers);
    }

    // ------------------------------------------------------------------ talents (7.2, 12.13)

    List<Talent> talents() {
        List<Talent> out = new ArrayList<>();
        List<Node> items = c.list(top, "talents", HeroCatalogue.MAX_TALENTS, "talent", "Talent", true);
        boolean realm = false;
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = top.path.item("talents", i, "talent", null, true);
            Talent t = talent(n, p);
            if (t == null) {
                continue;
            }
            if (out.stream().anyMatch(x -> x.id() == t.id())) {
                c.report(Severity.ADJUSTED, Code.LIST_DUPLICATE, n.line(), p, "\"" + t.id().id() + "\" is listed twice;"
                        + " once is enough", null);
                continue;
            }
            Talent kept = t;
            if (t.realm() && realm) {
                c.report(Severity.ADJUSTED, Code.REALM_ONE, n.line(), p, "only one talent of a leader works in every"
                        + " base of the faction (\"realm\": true); this one was read as false", null);
                kept = new Talent(t.id(), false, t.affix(), t.roles());
            }
            realm |= kept.realm();
            out.add(kept);
        }
        if (top.has("base_abilities")) {
            c.report(Severity.NOTE, Code.V1_FIELD, top.line("base_abilities"), top.at("base_abilities"),
                    "\"base_abilities\" is a format-1 field; its entries were read as talents, whose numbers belong to"
                    + " the mod (HEROES_V2 7.2)", null);
            List<Node> bs = c.list(top, "base_abilities", HeroCatalogue.MAX_TALENTS, "base ability", "Base ability",
                    false);
            for (int i = 0; i < bs.size(); i++) {
                BaseAbility b = c.baseAbility(bs.get(i), i);
                Talent t = b == null ? null : fromBase(b);
                if (t != null && out.size() < HeroCatalogue.MAX_TALENTS && out.stream().noneMatch(x -> x.id() == t.id())) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    static Talent fromBase(BaseAbility b) {
        return switch (b) {
            case HeroDef.Aura a -> new Talent(TalentId.AURA, false, a.affix(), a.roles());
            case HeroDef.Mend m -> new Talent(TalentId.MEND, false, null, List.of());
            case HeroDef.Lookout l -> new Talent(TalentId.LOOKOUT, false, null, List.of());
            case HeroDef.Rally r -> new Talent(TalentId.RALLY, false, null, List.of());
            case HeroDef.Drill d -> new Talent(TalentId.DRILL, false, null, List.of());
        };
    }

    Talent talent(Node n, Path p) {
        String part = "Talent";
        if (n instanceof Str s) {
            String w = c.resolve(s.value(), "talent", null, null, Mode.PART, part, n.line(), p);
            if (w == null) {
                return null;
            }
            TalentId id = HeroCatalogue.enumOf(TalentId.class, w);
            if (id == TalentId.AURA) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, n.line(), p, "\"aura\" needs \"affix\": write it as an"
                        + " object { \"id\": \"aura\", \"affix\": \"prospector\" }", part);
                return null;
            }
            return new Talent(id, false, null, List.of());
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be a talent such as \"drill\" or an"
                    + " object { \"id\": ..., \"realm\": true }, not " + c.shown(n), part);
            return null;
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.TALENT);
        if (!f.has("id")) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"id\" is missing. "
                    + c.allowed(HeroCatalogue.vocab("talent")), part);
            return null;
        }
        String w = c.word(f, "id", "talent", null, null, Mode.PART, part);
        if (w == null) {
            return null;
        }
        TalentId id = HeroCatalogue.enumOf(TalentId.class, w);
        boolean realm = c.bool(f, "realm", false);
        String affix = null;
        List<String> roles = List.of();
        if (id == TalentId.AURA) {
            Node an = f.node("affix");
            if (an == null) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"affix\" is missing. "
                        + c.allowed(HeroCatalogue.vocab("affix")), part);
                return null;
            }
            if (an instanceof Str as && HeroCatalogue.REFUSED_AURA_AFFIXES.contains(HeroCatalogue.norm(as.value()))) {
                c.report(Severity.PART_LEFT_OUT, Code.AURA_AFFIX, an.line(), f.at("affix"), "\""
                        + HeroCatalogue.norm(as.value()) + "\" is an attribute or scouting affix, not a work affix",
                        part);
                return null;
            }
            affix = c.word(f, "affix", "affix", null, null, Mode.PART, part);
            if (affix == null) {
                return null;
            }
            roles = words(f, "roles", 32, "role", "role", "Role");
            if (roles.isEmpty()) {
                roles = HeroCatalogue.ROLE_IDS;
            }
        } else {
            f.unused("affix", "\"affix\" is only used by the \"aura\" talent");
            f.unused("roles", "\"roles\" is only used by the \"aura\" talent");
        }
        return new Talent(id, realm, affix, roles);
    }

    // ------------------------------------------------------------------ voice, recruit, relationships, revival

    HeroDef.Voice voice() {
        double pitch0 = gender == Gender.FEMALE ? HeroCatalogue.VOICE_PITCH_FEMALE : gender == Gender.MALE
                ? HeroCatalogue.VOICE_PITCH_MALE : HeroCatalogue.VOICE_PITCH_OTHER;
        HeroDef.Blip blip0 = gender == Gender.MALE ? HeroDef.Blip.BASS : HeroDef.Blip.HARP;
        Node n = top.node("voice");
        if (n == null) {
            return new HeroDef.Voice(blip0, pitch0, 3, pitch0);
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), top.at("voice"), "should be an object { \"blip\":"
                    + " \"bell\", ... }, not " + c.shown(n) + ". The default voice is used", null);
            return new HeroDef.Voice(blip0, pitch0, 3, pitch0);
        }
        Ctx.Fields f = c.fields(o, top.at("voice"), HeroCatalogue.VOICE);
        String b = c.word(f, "blip", "blip", blip0.id(), null, Mode.OPTIONAL, null);
        double pitch = c.number(f, HeroCatalogue.VOICE, "pitch", pitch0);
        return new HeroDef.Voice(HeroCatalogue.enumOf(HeroDef.Blip.class, b), pitch,
                (int) c.number(f, HeroCatalogue.VOICE, "rate", 3), c.number(f, HeroCatalogue.VOICE, "hurt_pitch",
                pitch));
    }

    Recruit recruit() {
        Node n = top.node("recruit");
        if (n == null) {
            return null;
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), top.at("recruit"), "should be an object"
                    + " { \"condition\": \"duel\" }, not " + c.shown(n) + ". The temperament's condition is used",
                    null);
            return null;
        }
        Ctx.Fields f = c.fields(o, top.at("recruit"), HeroCatalogue.RECRUIT);
        if (!f.has("condition")) {
            c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), top.at("recruit"), "\"condition\" is missing. "
                    + c.allowed(HeroCatalogue.vocab("recruit_condition")), "Recruit");
            return null;
        }
        String w = c.word(f, "condition", "recruit_condition", null, null, Mode.PART, "Recruit");
        return w == null ? null : new Recruit(HeroCatalogue.enumOf(HeroDef.RecruitCondition.class, w),
                (int) c.number(f, HeroCatalogue.RECRUIT, "value", 0));
    }

    List<Relationship> relationships() {
        List<Relationship> out = new ArrayList<>();
        List<Node> items = c.list(top, "relationships", HeroCatalogue.MAX_RELATIONSHIPS, "relationship",
                "Relationship", true);
        for (int i = 0; i < items.size(); i++) {
            Node n = items.get(i);
            Path p = top.path.item("relationships", i, "relationship", null, true);
            if (!(n instanceof Obj o)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, n.line(), p, "should be an object { \"hero\": ...,"
                        + " \"kind\": \"rival\" }, not " + c.shown(n), "Relationship");
                continue;
            }
            Ctx.Fields f = c.fields(o, p, HeroCatalogue.RELATIONSHIP);
            if (!f.has("hero") || !f.has("kind")) {
                c.report(Severity.PART_LEFT_OUT, Code.MISSING, o.line(), p, "\"" + (f.has("hero") ? "kind" : "hero")
                        + "\" is missing", "Relationship");
                continue;
            }
            Node hn = f.node("hero");
            String hero = hn instanceof Str s ? HeroReader.normaliseId(s.value()) : null;
            if (!HeroReader.validId(hero)) {
                c.report(Severity.PART_LEFT_OUT, Code.WRONG_TYPE, hn.line(), f.at("hero"), c.shown(hn) + " is not a"
                        + " hero id", "Relationship");
                continue;
            }
            if (hero.equals(c.hero)) {
                c.report(Severity.PART_LEFT_OUT, Code.RELATION_SELF, hn.line(), f.at("hero"), "a hero has no"
                        + " relationship with itself", "Relationship");
                continue;
            }
            String k = c.word(f, "kind", "bond", null, null, Mode.PART, "Relationship");
            if (k == null) {
                continue;
            }
            if (out.stream().anyMatch(r -> r.hero().equals(hero))) {
                c.report(Severity.ADJUSTED, Code.LIST_DUPLICATE, n.line(), p, "\"" + hero + "\" is listed twice; once"
                        + " is enough", null);
                continue;
            }
            Relationship r = new Relationship(hero, HeroCatalogue.enumOf(HeroDef.Bond.class, k));
            c.where.put(r, new Pos(p, o.line(), f));
            out.add(r);
        }
        return out;
    }

    Revival revival() {
        Node n = top.node("revival");
        if (n == null) {
            return null;
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), top.at("revival"), "should be an object"
                    + " { \"seconds\": 120, \"where\": \"where_it_fell\" }, not " + c.shown(n) + ". The hero returns"
                    + " after 10 minutes at home", null);
            return null;
        }
        Ctx.Fields f = c.fields(o, top.at("revival"), HeroCatalogue.REVIVAL);
        String w = c.word(f, "where", "rise_where", "home", null, Mode.OPTIONAL, null);
        String at = c.word(f, "at", "rise_at", "any", null, Mode.OPTIONAL, null);
        return new Revival((int) c.number(f, HeroCatalogue.REVIVAL, "seconds", 600),
                HeroCatalogue.enumOf(HeroDef.RiseWhere.class, w), HeroCatalogue.enumOf(HeroDef.RiseAt.class, at),
                c.text(f, "line", HeroCatalogue.LINE_MAX, null));
    }

    // ------------------------------------------------------------------ mobility (MOBILITY_AND_WORK 8)

    Mobility mobility(Element element) {
        MobilityTrait archDefault = archetype == null ? MobilityTrait.RUNNER
                : HeroCatalogue.ARCH_PRIMARY.getOrDefault(archetype, MobilityTrait.RUNNER);
        Node n = top.node("mobility");
        Path p = top.at("mobility");
        if (n == null) {
            return mobilityDefaults(new TraitTier(archDefault, 1), null, null, null, true, List.of(), null);
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"primary\": ... }, not "
                    + c.shown(n) + ". The archetype's mobility is used", null);
            return mobilityDefaults(new TraitTier(archDefault, 1), null, null, null, true, List.of(), null);
        }
        Ctx.Fields f = c.fields(o, p, HeroCatalogue.MOBILITY);
        TraitTier primary = traitTier(f, "primary", archDefault, 3, false);
        if (primary == null) {
            primary = new TraitTier(archDefault, 1);
        }
        if (primary.tier() == 3 && (rarity == Rarity.COMMON || rarity == Rarity.RARE)) {
            c.report(Severity.ADJUSTED, Code.MOBILITY_ADJUSTED, f.line("primary"), f.at("primary"), "tier 3 is for"
                    + " epic and legendary heroes; set to 2", null);
            primary = new TraitTier(primary.trait(), 2);
        }
        TraitTier secondary = f.has("secondary") ? traitTier(f, "secondary", null, Math.min(2, primary.tier()), true)
                : null;
        if (secondary != null) {
            String drop = pairProblem(primary.trait(), secondary.trait(), element);
            if (drop != null) {
                c.report(Severity.ADJUSTED, Code.MOBILITY_ADJUSTED, f.line("secondary"), f.at("secondary"), drop
                        + "; the secondary was dropped", null);
                secondary = null;
            }
        }
        CarrierSpec carrier = null;
        Node cn = f.node("carrier");
        if (cn instanceof Obj co) {
            Ctx.Fields cf = c.fields(co, f.at("carrier"), HeroCatalogue.CARRIER);
            if (!cf.has("kind")) {
                c.report(Severity.NOTE, Code.MOBILITY_UNKNOWN, co.line(), f.at("carrier"), "\"kind\" is missing; the"
                        + " archetype's carrier applies", null);
            } else if (cf.node("kind") instanceof Str ks && HeroCatalogue.vocab("carrier").resolve(
                    HeroCatalogue.norm(ks.value())) == null) {
                c.report(Severity.NOTE, Code.MOBILITY_UNKNOWN, ks.line(), cf.at("kind"), "\"" + ks.value() + "\" is not"
                        + " a carrier. " + c.allowed(HeroCatalogue.vocab("carrier")) + ". The archetype's carrier"
                        + " applies", null);
            } else {
                String k = c.word(cf, "kind", "carrier", null, null, Mode.OPTIONAL, null);
                String part = c.text(cf, "part", 48, null);
                if (part != null && !part.equals("wings") && !part.equals("weapon")
                        && !part.matches("[a-z0-9_]+/[a-z0-9_]+")) {
                    c.report(Severity.ADJUSTED, Code.WRONG_TYPE, cf.line("part"), cf.at("part"), "\"" + part + "\" is"
                            + " not a part id (\"<category>/<name>\"), \"wings\" or \"weapon\"; it was ignored", null);
                    part = null;
                }
                carrier = k == null ? null : new CarrierSpec(HeroCatalogue.enumOf(HeroDef.Carrier.class, k), part);
            }
        } else if (cn != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, cn.line(), f.at("carrier"), "should be an object { \"kind\":"
                    + " \"broom\", \"part\": \"weapon\" }, not " + c.shown(cn) + ". It was ignored", null);
        }
        Aerial aerial = null;
        Node an = f.node("aerial");
        if (an instanceof Obj ao) {
            Ctx.Fields af = c.fields(ao, f.at("aerial"), HeroCatalogue.AERIAL);
            if (primary.trait() != MobilityTrait.FLYER) {
                c.report(Severity.ADJUSTED, Code.MOBILITY_ADJUSTED, ao.line(), f.at("aerial"), "aerial windows are for"
                        + " flyers (this hero's primary is " + primary.trait().id() + "); \"aerial\" was removed",
                        null);
            } else {
                List<Integer> ph = phaseNumbers(af, "phases");
                aerial = new Aerial(ph.isEmpty() ? HeroCatalogue.defaultAerialPhases(rarity) : ph,
                        mobNumber(af, "max_air", 8), (int) mobNumber(af, "dives", 3), (int) mobNumber(af, "weight", 4));
            }
        } else if (an != null) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, an.line(), f.at("aerial"), "should be an object { \"phases\":"
                    + " [2] }, not " + c.shown(an) + ". It was ignored", null);
        }
        List<HeroDef.Avoid> avoid = new ArrayList<>();
        for (String w : words(f, "avoid", 4, "avoid", "terrain", "Terrain")) {
            avoid.add(HeroCatalogue.enumOf(HeroDef.Avoid.class, w));
        }
        return mobilityDefaults(primary, secondary, carrier, aerial, c.bool(f, "travel", true), avoid, f);
    }

    /** {@code max_air}, {@code dives} and {@code weight} outside their range are ADJUSTED (MOBILITY_AND_WORK 8.3). */
    private double mobNumber(Ctx.Fields af, String k, double dflt) {
        Field d = HeroCatalogue.AERIAL.get(k);
        Node n = af.node(k);
        if (n instanceof Num x && (x.value() < d.min() || x.value() > d.max())) {
            double v = Math.max(d.min(), Math.min(d.max(), x.value()));
            c.report(Severity.ADJUSTED, Code.MOBILITY_ADJUSTED, n.line(), af.at(k), num(x.value()) + " is outside "
                    + num(d.min()) + " to " + num(d.max()) + ". Set to " + num(v), null);
            return d.whole() ? Math.round(v) : v;
        }
        return c.number(af, HeroCatalogue.AERIAL, k, dflt);
    }

    TraitTier traitTier(Ctx.Fields f, String name, MobilityTrait dflt, int maxTier, boolean secondary) {
        Node n = f.node(name);
        Path p = f.at(name);
        if (n == null) {
            // no primary: the archetype's, silently (MOBILITY_AND_WORK 8.2)
            return dflt == null ? null : new TraitTier(dflt, 1);
        }
        if (!(n instanceof Obj o)) {
            c.report(Severity.ADJUSTED, Code.WRONG_TYPE, n.line(), p, "should be an object { \"trait\": \"leaper\","
                    + " \"tier\": 2 }, not " + c.shown(n) + (dflt == null ? ". It was ignored"
                    : ". The archetype's primary applies"), null);
            return null;
        }
        Ctx.Fields tf = c.fields(o, p, HeroCatalogue.TRAIT_TIER);
        Node tn = tf.node("trait");
        String w = tn instanceof Str ts ? HeroCatalogue.vocab("mobility_trait").resolve(HeroCatalogue.norm(ts.value()))
                : null;
        if (w == null) {
            c.report(Severity.NOTE, Code.MOBILITY_UNKNOWN, tn == null ? o.line() : tn.line(), tf.at("trait"),
                    (tn == null ? "\"trait\" is missing" : c.shown(tn) + " is not a mobility trait") + ". "
                    + c.allowed(HeroCatalogue.vocab("mobility_trait")) + (dflt == null ? ". The secondary was ignored"
                    : ". The archetype's " + dflt.id() + " applies"), null);
            return dflt == null ? null : new TraitTier(dflt, 1);
        }
        if (!w.equals(HeroCatalogue.norm(((Str) tn).value()))) {
            c.report(Severity.ADJUSTED, Code.SYNONYM, tn.line(), tf.at("trait"), "\"" + ((Str) tn).value() + "\" was"
                    + " read as \"" + w + "\"", null);
        }
        MobilityTrait t = HeroCatalogue.enumOf(MobilityTrait.class, w);
        if (!secondary && t.footing()) {
            c.report(Severity.NOTE, Code.MOBILITY_UNKNOWN, tn.line(), tf.at("trait"), "\"" + w + "\" is a footing,"
                    + " only a secondary trait. The archetype's " + (dflt == null ? "primary" : dflt.id())
                    + " applies", null);
            return dflt == null ? null : new TraitTier(dflt, 1);
        }
        int tier = 1;
        Node tiern = tf.node("tier");
        if (tiern instanceof Num x) {
            int v = (int) Math.round(x.value());
            int cl = Math.max(1, Math.min(maxTier, v));
            if (cl != v || x.value() != Math.rint(x.value())) {
                c.report(Severity.ADJUSTED, Code.MOBILITY_ADJUSTED, tiern.line(), tf.at("tier"), "tier " + x.text()
                        + " is outside 1 to " + maxTier + (secondary ? " (a secondary is at most tier 2 and at most"
                        + " the primary's)" : "") + ". Set to " + cl, null);
            }
            tier = cl;
        } else if (tiern != null) {
            tier = (int) c.number(tf, HeroCatalogue.TRAIT_TIER, "tier", 1);
            tier = Math.max(1, Math.min(maxTier, tier));
        }
        return new TraitTier(t, tier);
    }

    /** MOBILITY_AND_WORK 1.14 and 8.3: the pairs that are not allowed (null: allowed). */
    static String pairProblem(MobilityTrait primary, MobilityTrait sec, Element element) {
        if (sec == MobilityTrait.FLYER) {
            return "a flyer is a primary trait only";
        }
        if (sec == primary) {
            return "the secondary repeats the primary";
        }
        if (primary == MobilityTrait.FLYER && (sec == MobilityTrait.GLIDER || sec == MobilityTrait.HOVER)) {
            return "a flyer already glides and never takes " + sec.id();
        }
        if (primary == MobilityTrait.GLIDER && sec == MobilityTrait.HOVER
                || primary == MobilityTrait.HOVER && sec == MobilityTrait.GLIDER) {
            return "glider and hover never pair";
        }
        if (sec == MobilityTrait.SNOWSTRIDE && element != Element.FROST) {
            return "snowstride needs the frost element (or the snow spirit archetype)";
        }
        if (sec == MobilityTrait.EMBER_STRIDE && element != Element.FIRE) {
            return "ember_stride needs the fire element (or the phoenix archetype)";
        }
        return null;
    }

    Mobility mobilityDefaults(TraitTier primary, TraitTier secondary, CarrierSpec carrier, Aerial aerial,
                              boolean travel, List<HeroDef.Avoid> avoid, Ctx.Fields f) {
        // the footings' archetype exceptions (MOBILITY_AND_WORK 1.14)
        MobilityTrait t = primary.trait();
        CarrierSpec cs = carrier;
        if (cs == null && (t == MobilityTrait.FLYER || t == MobilityTrait.GLIDER || t == MobilityTrait.HOVER)) {
            HeroDef.Carrier k = t == MobilityTrait.HOVER ? HeroDef.Carrier.LEVITATION
                    : archetype == null ? null : HeroCatalogue.ARCH_CARRIER.get(archetype);
            if (k == null) {
                c.report(Severity.NOTE, Code.MOBILITY_CARRIER, f == null ? top.obj.line() : f.obj.line(),
                        f == null ? Path.ROOT.field("mobility") : f.path, "a " + t.id() + " needs a carrier its model"
                        + " can show (wings, a mount part or a cape); none is given and its archetype has none", null);
            } else {
                cs = new CarrierSpec(k, null);
            }
        }
        Aerial ae = aerial;
        if (ae == null && t == MobilityTrait.FLYER) {
            ae = new Aerial(HeroCatalogue.defaultAerialPhases(rarity), 8, 3, 4);
        }
        List<HeroDef.Avoid> av = new ArrayList<>(avoid);
        if (f == null || !f.has("avoid")) {
            for (Node wn : weakPointNodes()) {
                if (wn instanceof Str s && HeroCatalogue.norm(s.value()).equals("water")
                        || wn instanceof Obj o && o.get("point") instanceof Str s2 && HeroCatalogue.norm(s2.value())
                        .equals("water")) {
                    if (!av.contains(HeroDef.Avoid.WATER)) {
                        av.add(HeroDef.Avoid.WATER);
                    }
                }
            }
        }
        return new Mobility(primary, secondary, cs, ae, travel, av);
    }

    private List<Node> weakPointNodes() {
        if (top.node("boss") instanceof Obj b && b.get("weak_points") instanceof Arr a) {
            return a.items();
        }
        return List.of();
    }

    // ================================================================== across files (14.1)

    /** What the checks across files need of one hero. */
    record Across(String hero, String file, List<String> leaderNames, String signature, List<Relationship> relations,
                  boolean flyer, int line) {
    }

    Across across(HeroDef def) {
        V2 v = def.v2();
        String sig = null;
        if (archetype != null) {
            Move sigMove = v.moves().isEmpty() ? null : v.moves().get(0);
            List<String> types = new ArrayList<>();
            if (sigMove != null) {
                for (List<Effect> l : sigMove.effectLists()) {
                    for (Effect e : l) {
                        if (!types.contains(e.type().id())) {
                            types.add(e.type().id());
                        }
                    }
                }
                java.util.Collections.sort(types);
            }
            sig = archetype + "/" + def.element().id() + "/" + def.basicAttack().style().id() + "/"
                    + (v.ultimate() == null ? "-" : v.ultimate().form().id()) + "/"
                    + (sigMove == null ? "-" : sigMove.form().id() + ":" + String.join("+", types));
        }
        return new Across(def.id(), c.file, List.copyOf(leaderNames), sig, v.relationships(),
                v.mobility() != null && v.mobility().primary().trait() == MobilityTrait.FLYER, top.obj.line());
    }

    /** Leader names, the uniqueness signature, relationships and the share of flyers (NOTEs). */
    static void acrossFiles(Collection<HeroReader.Read> loaded, Problems problems, HeroReader.Options opt) {
        Map<String, String> nameOwner = new LinkedHashMap<>();
        Map<String, String> sigOwner = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        int flyers = 0, total = 0;
        for (HeroReader.Read r : loaded) {
            ids.add(r.def().id());
        }
        for (HeroReader.Read r : loaded) {
            Across a = r.across();
            if (a == null) {
                continue;
            }
            total++;
            if (a.flyer()) {
                flyers++;
            }
            for (String n : a.leaderNames()) {
                String key = n.toLowerCase(Locale.ROOT);
                String other = nameOwner.putIfAbsent(key, a.hero());
                if (other != null && !other.equals(a.hero())) {
                    problems.add(new Problem(Severity.NOTE, Code.LEADER_NAME_TAKEN.id(), a.hero(), a.file(), 0, 0,
                            "leader", "leader", "\"" + n + "\" is also a faction name of \"" + other + "\"; when both"
                            + " lead, the second takes its next unused name", null));
                }
            }
            if (a.signature() != null) {
                String other = sigOwner.putIfAbsent(a.signature(), a.hero());
                if (other != null) {
                    problems.add(new Problem(Severity.NOTE, Code.SIGNATURE_SHARED.id(), a.hero(), a.file(), 0, 0, "",
                            "", "\"" + a.hero() + "\" and \"" + other + "\" share archetype, element, style, ultimate"
                            + " form and signature move (" + a.signature() + "); make one of them more distinct", null));
                }
            }
            for (Relationship rel : a.relations()) {
                if (!ids.contains(rel.hero())) {
                    problems.add(new Problem(Severity.NOTE, Code.RELATION_UNKNOWN.id(), a.hero(), a.file(), 0, 0,
                            "relationships", "relationships", "\"" + rel.hero() + "\" is not a loaded hero; the "
                            + rel.kind().id() + " relationship waits until it is", null));
                }
            }
        }
        if (opt.builtin() && total >= 8 && flyers > HeroCatalogue.MAX_FLYER_SHARE * total) {
            problems.add(new Problem(Severity.NOTE, Code.FLYERS_SHARE.id(), null, "(all heroes)", 0, 0, "", "",
                    flyers + " of the " + total + " loaded heroes fly, more than "
                    + Math.round(HeroCatalogue.MAX_FLYER_SHARE * 100) + "% (MOBILITY_AND_WORK 8.3)", null));
        }
    }

    // ================================================================== format 1 read as format 2 (12.1)

    /**
     * The format-2 shape of a format-1 hero, made without a word reported (HEROES_V2 12.1: skills read as moves,
     * {@code empowered} as one state, {@code base_abilities} as talents; boss numbers from its role and rarity; its
     * moves keep their v1 wind-up raised to their class's minimum, which is what makes every hero a boss; no
     * personality, so it escapes or stands by its {@code retreat_below} and neutral traits; no mobility).
     */
    static V2 mapV1(HeroDef d) {
        List<Move> moves = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        for (Ability a : d.skills()) {
            moves.add(mapMove(a, uniqueId(a.name(), taken)));
        }
        Move ult = d.ultimate() == null ? null : mapMove(d.ultimate(), uniqueId(d.ultimate().name(), taken));
        List<State> states = new ArrayList<>();
        if (d.empowered() != null) {
            HeroDef.Empowered e = d.empowered();
            String sn = HeroReader.normaliseId(e.name());
            states.add(new State(sn.length() >= 2 && sn.matches("[a-z][a-z0-9_]*") ? HeroReader.cut(sn, 24)
                    : "empowered", e.description(), e.stats(), e.onHit(), e.particle(), e.color(), e.size(),
                    e.skillsLocked(), List.of(), List.of(), List.of(), null, e.line()));
        }
        int count = HeroCatalogue.defaultPhaseCount(d.rarity());
        List<Phase> phases = new ArrayList<>();
        for (int at : HeroCatalogue.defaultPhaseStarts(count)) {
            phases.add(new Phase(at, List.of(), List.of(), List.of(), List.of(), Transition.DEFAULT));
        }
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            all.add(i);
        }
        List<Move> phased = new ArrayList<>();
        for (Move m : moves) {
            phased.add(m.withPhases(all));
        }
        Boss boss = new Boss(HeroCatalogue.HEALTH_SCALE.get(d.rarity()), 12, HeroCatalogue.ROLE_BOSS.get(
                d.behaviour().role()).poise(), phases, List.of(), List.of(), Enrage.DEFAULT, List.of(), null, false,
                HeroDef.Arena.OPEN);
        Personality p = new Personality(null, HeroCatalogue.defaultTraits(null), null, null, List.of(), List.of(),
                List.of());
        EndOfFight end = EscapeRules.end(null, p.traits(), null, null, d.behaviour().retreatBelow(), true,
                boss.lastPhaseAt());
        List<Talent> talents = new ArrayList<>();
        for (BaseAbility b : d.baseAbilities()) {
            Talent t = fromBase(b);
            if (talents.stream().noneMatch(x -> x.id() == t.id())) {
                talents.add(t);
            }
        }
        return new V2(null, null, null, phased, ult == null ? null : ult.withPhases(all), null, states, boss, p, end,
                Leader.DEFAULT, talents, new HeroDef.Voice(HeroDef.Blip.HARP, HeroCatalogue.VOICE_PITCH_OTHER, 3,
                HeroCatalogue.VOICE_PITCH_OTHER), null, List.of(), null, null, null);
    }

    private static String uniqueId(String name, Set<String> taken) {
        String base = HeroReader.normaliseId(name);
        if (base.length() < 2 || !base.matches("[a-z][a-z0-9_]*") || HeroCatalogue.LOCOMOTION_NAMES.contains(base)) {
            base = "move_" + base;
        }
        base = HeroReader.cut(base, HeroCatalogue.MOVE_ID_MAX - 2).replaceAll("_+$", "");
        String id = base;
        for (int i = 2; !taken.add(id); i++) {
            id = base + "_" + i;
        }
        return id;
    }

    static Move mapMove(Ability a, String id) {
        boolean ult = a.slot() == Slot.ULTIMATE;
        MoveClass cls = ult ? MoveClass.ULTIMATE : HeroCatalogue.classOf(a.form());
        if (!ult && !cls.atLeast(MoveClass.HEAVY) && HeroBalance.stunsEnemies(a.effects())) {
            cls = MoveClass.HEAVY;
        }
        double windup = Math.max(a.windup(), cls.minWindup);
        if (a.form() == Form.ZONE) {
            // as in format 2, a zone lands as its wind-up ends: the wind-up covers the v1 delay (HEROES_V2 6.4)
            windup = Math.max(windup, HeroBalance.upToTick(a.delay()));
            a = HeroBalance.withDelay(a, 0);
        }
        Frames fr = new Frames(windup, HeroCatalogue.DEFAULT_ACTIVE, cls.minRecovery);
        return new Move(id, a, cls, fr, HeroCatalogue.defaultBands(a.form(), a.range(), a.zoneAt()),
                5, List.of(), null, List.of(), false, false, false, false, 0,
                new Telegraph(HeroCatalogue.defaultDecal(cls, a.form()), HeroDef.NO_COLOR), FormExtra.NONE, List.of(),
                List.of(), ult ? "ultimate" : "move." + id);
    }

    // ------------------------------------------------------------------ for the sheet

    /** "Kagenori Tsurumine: fights to the death": what {@code /fc hero check} prints for the end of a fight. */
    static String endLine(HeroDef d) {
        return d.name() + ": " + d.v2().end().sheetLine();
    }

    static Map<String, Integer> counts(List<Move> moves) {
        Map<String, Integer> m = new TreeMap<>();
        for (Move x : moves) {
            m.merge(x.form().id(), 1, Integer::sum);
        }
        return m;
    }

    static Map<EffectType, Integer> effectCounts(List<Move> moves) {
        Map<EffectType, Integer> m = new EnumMap<>(EffectType.class);
        for (Move x : moves) {
            for (List<Effect> l : x.effectLists()) {
                for (Effect e : l) {
                    m.merge(e.type(), 1, Integer::sum);
                }
            }
        }
        return m;
    }
}
