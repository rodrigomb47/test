package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.Temperament;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * A faction's name, short name and command slug (HEROES_V2 5.1, 5.3): the leader's first unused
 * {@code faction_names} entry and its {@code short_name} when it gives them, else a seeded name from generic English
 * word banks: a colour word from the faction's primary dye, a motif word from the leader's archetype and a group noun
 * from its temperament ("Crimson Anvil Warband", "Order of the Azure Crane", "The Ashen Watch"). Names, short names
 * and slugs are unique within the campaign. Base names are the short name and the style family's seat ("Still Crane
 * Keep"). Pure, unit-tested ({@code FactionNameGenTest}).
 */
public final class FactionNameGen {
    private FactionNameGen() {
    }

    public static final int NAME_MIN = 3, NAME_MAX = 32, SHORT_MAX = 14, SLUG_MIN = 3, SLUG_MAX = 24;

    /** A faction's identity text. */
    public record Names(String name, String shortName, String slug) {
    }

    // ------------------------------------------------------------------ word banks

    /** Motif words by archetype (ROSTER_PLAN 2.3's 54); an unknown archetype draws from {@link #GENERIC_MOTIFS}. */
    public static final Map<String, List<String>> MOTIFS = new LinkedHashMap<>();
    public static final List<String> GENERIC_MOTIFS = List.of("Crown", "Star", "Oak", "Stone", "River", "Hawk");

    private static void m(String archetype, String... words) {
        MOTIFS.put(archetype, List.of(words));
    }

    static {
        m("cat", "Whisker", "Calico", "Pawprint");
        m("fox", "Foxfire", "Ninetail", "Russet");
        m("wolf", "Howl", "Moonfang", "Greymane");
        m("tanuki", "Lantern", "Leafcoat", "Bellydrum");
        m("moon_rabbit", "Moonhare", "Mortar", "Pale Moon");
        m("oni", "Anvil", "Red Horn", "Ironclub");
        m("tengu", "Crag", "Gale", "Crowfeather");
        m("snow_spirit", "Frost", "Snowfall", "Rime");
        m("kappa", "River", "Reed", "Shell");
        m("spider_weaver", "Silk", "Web", "Loom");
        m("east_dragon", "Pearl", "Cloud", "Jade");
        m("west_dragon", "Wyrm", "Scale", "Hoard");
        m("komainu", "Stone Lion", "Gatekeeper", "Twin Guard");
        m("plant_spirit", "Bloom", "Thorn", "Root");
        m("shrine", "Bell", "Shrine", "Sacred Rope");
        m("onmyoji", "Paper Crane", "Talisman", "Five Stars");
        m("samurai", "Crane", "Blade", "Steel");
        m("wanderer", "Long Road", "Straw Hat", "Drifter");
        m("ninja", "Shadow", "Mist", "Smoke");
        m("warrior_monk", "Temple", "Staff", "Mountain");
        m("fan_dancer", "Fan", "Petal", "Silk");
        m("delinquent", "Rebel", "Street", "Scrap");
        m("battle_chef", "Cleaver", "Hearth", "Copper Pot");
        m("storm_drummer", "Thunder", "Drum", "Storm");
        m("sword_cultivator", "Flying Sword", "Jade Peak", "Cloud Step");
        m("martial_artist", "Fist", "Tiger", "Iron Palm");
        m("jiangshi", "Grave", "Paper Charm", "Still Night");
        m("phoenix", "Phoenix", "Flame", "Ash");
        m("knight", "Shield", "Lance", "Oath");
        m("paladin", "Dawn", "Halo", "Vow");
        m("battle_priest", "Censer", "Chapel", "Litany");
        m("witch", "Cauldron", "Broom", "Hex");
        m("elf", "Leaf", "Bow", "Glade");
        m("vampire", "Night", "Rose", "Fang");
        m("demon_lord", "Throne", "Brimstone", "Black Horn");
        m("angel", "Wing", "Halo", "Light");
        m("reaper", "Scythe", "Lantern", "Shroud");
        m("necromancer", "Bone", "Crypt", "Ashes");
        m("beast_tamer", "Pack", "Wildwood", "Whistle");
        m("dragoon", "Skylance", "Spear", "Dragonhelm");
        m("berserker", "Bear", "Axe", "Fury");
        m("arbalist", "Bolt", "Quarrel", "Windlass");
        m("corsair", "Anchor", "Black Tide", "Gull");
        m("bard", "Lute", "Song", "Ballad");
        m("alchemist", "Flask", "Mercury", "Retort");
        m("siren", "Sea Song", "Pearl", "Undertow");
        m("idol", "Star", "Stage", "Spotlight");
        m("magical", "Star", "Ribbon", "Wand");
        m("maid_butler", "Silver", "Tea", "Service");
        m("noble_fencer", "Rapier", "Rose", "Crest");
        m("android", "Circuit", "Gear", "Signal");
        m("tactician", "Chart", "Banner", "Gambit");
        m("smith", "Anvil", "Forge", "Hammer");
        m("grappler", "Bull", "Ring", "Iron Grip");
    }

    /** Colour words by the faction's primary dye. */
    public static final Map<Dye, List<String>> COLOURS = new EnumMap<>(Dye.class);

    static {
        String[][] c = {{"Ivory", "Snow"}, {"Amber", "Copper"}, {"Orchid", "Peony"}, {"Azure", "Sky"},
                {"Golden", "Saffron"}, {"Verdant", "Spring"}, {"Rose", "Blossom"}, {"Ashen", "Slate"},
                {"Silver", "Pale"}, {"Teal", "Tidal"}, {"Violet", "Twilight"}, {"Sapphire", "Indigo"},
                {"Umber", "Oaken"}, {"Emerald", "Pine"}, {"Crimson", "Scarlet"}, {"Ebon", "Midnight"}};
        Dye[] all = Dye.values();
        for (int i = 0; i < all.length; i++) {
            COLOURS.put(all[i], List.of(c[i]));
        }
    }

    /** Group nouns by temperament group: how the faction sees itself. */
    public enum Group {
        MARTIAL("Warband", "Host", "Legion", "Vanguard"), HONOUR("Order", "Watch", "Oath", "Guard"),
        GENTLE("Fellowship", "Commons", "Hearth", "Kin"), CUNNING("Syndicate", "Company", "Guild", "League"),
        SCHOLARLY("Conclave", "Lodge", "Circle", "Assembly"), LONE("Pack", "Band", "Wanderers", "Outriders");

        public final List<String> nouns;

        Group(String... nouns) {
            this.nouns = List.of(nouns);
        }
    }

    /** The temperament's group (no temperament: honour, the plainest). */
    public static Group group(Temperament t) {
        if (t == null) {
            return Group.HONOUR;
        }
        return switch (t) {
            case HOT_BLOODED, BATTLE_MANIAC, DELINQUENT, TYRANT, CHUUNIBYOU, YANDERE -> Group.MARTIAL;
            case STOIC, RIGHTEOUS, GRUFF_VETERAN, PROUD_NOBLE, TSUNDERE, PERFECTIONIST -> Group.HONOUR;
            case SWEET_NATURED, GENTLE_GIANT, BIG_SIBLING, SHY, SLEEPY, AIRHEAD, GLUTTON, COWARD_WHO_RISES, GENKI ->
                    Group.GENTLE;
            case SCHEMER, TRICKSTER, MERCHANT, CHARMER -> Group.CUNNING;
            case ANCIENT_SAGE, ECCENTRIC_GENIUS, COOL_HEADED, MELANCHOLIC -> Group.SCHOLARLY;
            case LONE_WOLF -> Group.LONE;
        };
    }

    // ------------------------------------------------------------------ names

    /**
     * The faction's names: the leader's first {@code faction_names} entry nobody in the campaign uses yet and its
     * {@code short_name} (else the name's key word), else a generated name. {@code taken} holds the campaign's names,
     * short names and slugs so far (any case), the legacy slot ids among them.
     */
    public static Names names(List<String> leaderNames, String leaderShort, long seed, String archetype,
                              Temperament t, Dye primary, Set<String> taken) {
        for (String n : leaderNames) {
            if (n != null && n.length() >= NAME_MIN && n.length() <= NAME_MAX && !isTaken(n, taken)) {
                List<String> shorts = new ArrayList<>();
                if (leaderShort != null && !leaderShort.isBlank() && leaderShort.length() <= SHORT_MAX) {
                    shorts.add(leaderShort);
                }
                shorts.addAll(shortCandidates(n));
                for (String s : shorts) {
                    if (!isTaken(s, taken)) {
                        return new Names(n, s, slug(s, taken));
                    }
                }
            }
        }
        return generate(seed, archetype, t, primary, taken);
    }

    /** A generated name, seeded: the same seed and taken set always give the same name. */
    public static Names generate(long seed, String archetype, Temperament t, Dye primary, Set<String> taken) {
        SplittableRandom r = new SplittableRandom(seed);
        List<String> motifs = MOTIFS.getOrDefault(archetype, GENERIC_MOTIFS);
        List<String> colours = COLOURS.get(primary == null ? Dye.WHITE : primary);
        List<String> nouns = group(t).nouns;
        for (int attempt = 0; attempt < 200; attempt++) {
            String col = attempt < 100 ? colours.get(r.nextInt(colours.size()))
                    : pick(r, COLOURS.values().stream().flatMap(List::stream).toList());
            String motif = attempt < 150 ? motifs.get(r.nextInt(motifs.size())) : pick(r, GENERIC_MOTIFS);
            String noun = nouns.get(r.nextInt(nouns.size()));
            String name, shortName;
            switch (r.nextInt(3)) {
                case 0 -> {
                    name = col + " " + motif + " " + noun;
                    shortName = col + " " + motif;
                }
                case 1 -> {
                    name = noun + " of the " + col + " " + motif;
                    shortName = col + " " + motif;
                }
                default -> {
                    name = "The " + col + " " + noun;
                    shortName = col + " " + noun;
                }
            }
            if (shortName.length() > SHORT_MAX) {
                shortName = motif.length() <= SHORT_MAX ? motif : motif.substring(0, SHORT_MAX).trim();
            }
            if (name.length() <= NAME_MAX && !isTaken(name, taken) && !isTaken(shortName, taken)) {
                return new Names(name, shortName, slug(shortName, taken));
            }
        }
        String base = "Faction " + Long.toUnsignedString(seed % 1000);
        return new Names(base, base, slug(base, taken));
    }

    private static String pick(SplittableRandom r, List<String> l) {
        return l.get(r.nextInt(l.size()));
    }

    private static boolean isTaken(String s, Set<String> taken) {
        for (String t : taken) {
            if (t.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A name's key word, for a short name the leader did not give: without a leading "The" or "... of the", without
     * a trailing group noun, at most 14 letters on a word boundary ("House of the Still Crane" is "Still Crane").
     */
    public static String keyWord(String name) {
        String s = name.trim();
        int of = s.toLowerCase(Locale.ROOT).lastIndexOf(" of the ");
        if (of >= 0) {
            s = s.substring(of + 8);
        } else if (s.toLowerCase(Locale.ROOT).startsWith("the ")) {
            s = s.substring(4);
        }
        String[] w = s.split("\\s+");
        List<String> words = new ArrayList<>(List.of(w));
        if (words.size() > 1 && isGroupNoun(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String x : words) {
            if (sb.length() + (sb.length() == 0 ? 0 : 1) + x.length() > SHORT_MAX) {
                break;
            }
            sb.append(sb.length() == 0 ? "" : " ").append(x);
        }
        if (sb.length() == 0) {
            return words.get(0).substring(0, Math.min(SHORT_MAX, words.get(0).length()));
        }
        return sb.toString();
    }

    /** Short names a name offers, best first: its key word, then its last two words, then its last word. */
    static List<String> shortCandidates(String name) {
        List<String> out = new ArrayList<>();
        out.add(keyWord(name));
        String[] w = name.trim().split("\\s+");
        if (w.length >= 2) {
            String two = w[w.length - 2] + " " + w[w.length - 1];
            if (two.length() <= SHORT_MAX) {
                out.add(two);
            }
        }
        if (w[w.length - 1].length() <= SHORT_MAX) {
            out.add(w[w.length - 1]);
        }
        return out;
    }

    private static boolean isGroupNoun(String w) {
        for (Group g : Group.values()) {
            for (String n : g.nouns) {
                if (n.equalsIgnoreCase(w)) {
                    return true;
                }
            }
        }
        return List.of("House", "Host", "Watch", "Warband", "Band", "Clan", "Covenant", "Alliance", "Legion")
                .stream().anyMatch(x -> x.equalsIgnoreCase(w));
    }

    /**
     * A command slug from a short name: lower case, {@code [a-z0-9_]}, 3-24 characters, made unique against
     * {@code taken} with {@code _2}, {@code _3}...
     */
    public static String slug(String shortName, Set<String> taken) {
        String s = shortName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() < SLUG_MIN) {
            s = (s + "_faction").replaceAll("^_+", "");
        }
        if (s.length() > SLUG_MAX) {
            s = s.substring(0, SLUG_MAX).replaceAll("_+$", "");
        }
        String out = s;
        for (int n = 2; isTaken(out, taken); n++) {
            String suffix = "_" + n;
            out = (s.length() + suffix.length() > SLUG_MAX ? s.substring(0, SLUG_MAX - suffix.length()) : s) + suffix;
        }
        return out;
    }

    /** A base's name: the short name and the style family's seat ("Still Crane Keep"). */
    public static String baseName(String shortName, String seat) {
        return shortName + " " + seat;
    }
}
