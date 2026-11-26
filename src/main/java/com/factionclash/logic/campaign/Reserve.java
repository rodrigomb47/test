package com.factionclash.logic.campaign;

import com.factionclash.logic.hero.HeroRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a war council plans against ({@code docs/design/recon.md} 5.5). The owner, on counting only the soldiers a scout
 * saw: "they can assume that what they saw is not the enemy mainforce". The estimate stays the faces counted
 * ({@link Intel#estimate}); the council plans against more: what it counted and a reserve for what it could not see,
 * the larger of a share of the count and the soldiers of that faction its people saw away from that base and did not see
 * fall, with the heroes it knows of, under the soldier backbone ({@link HeroRules#countedPower}). Pure rules, no
 * Minecraft classes.
 *
 * <p>Task R10; the war council plans on it since the switch, task R5, and since task R11 with the faction's sightings
 * ledger ({@link Sightings}: what its people saw of that faction's soldiers and heroes away from that base), and its lines
 * say what the plan rests on ({@link #defenceText}). Without a fresh good recon there is no plan, whatever the sightings
 * hold.
 */
public final class Reserve {
    private Reserve() {
    }

    /**
     * PROPOSED: the share of the count a council adds for what its scout could not see: half again. At a half, two thirds
     * of the plan is the count head for head, so a council attacks with at least as many soldiers as its scout counted.
     */
    public static final double SHARE = 0.5;
    /** PROPOSED: lairs get no share for now (a party of 3 could not raid a lair of 5 with one; recon.md 12, question 2). */
    public static final double SHARE_LAIR = 0;
    /** PROPOSED: the counter-attack's plan has no share: the army that struck us was in sight, and what walked away is known. */
    public static final double COUNTER_SHARE = 0;

    /** The share for a target: a lair's, a counter-attack's, else a raid's at the interval. */
    public static double share(boolean lair, boolean counter) {
        return lair ? SHARE_LAIR : counter ? COUNTER_SHARE : SHARE;
    }

    /**
     * A plan: the recon it rests on and the share used; the soldiers counted and their mean power (1 when none was seen);
     * the share's soldiers; the soldiers seen away that count ({@code seenAway}); the reserve (the larger of the two terms)
     * and its summed power; the heroes in the plan by hero id (the watch's, then those known from sightings that the watch
     * missed, {@code knownHeroes}); the soldiers' and the heroes' summed power; the plan's power under the soldier backbone;
     * and its unit, one soldier: the mean power of the plan's soldiers, counted and reserve together (1 when it holds none).
     */
    public record Plan(Intel.Recon recon, double share, int counted, double countedPower, int shareSoldiers,
                       List<Sightings.Sighting> seenAway, int reserve, double reservePower, Map<String, Double> heroes,
                       List<Sightings.Sighting> knownHeroes, double soldierPower, double heroPower, double power, double unit) {
        public Plan {
            seenAway = List.copyOf(seenAway);
            knownHeroes = List.copyOf(knownHeroes);
            heroes = Collections.unmodifiableMap(new LinkedHashMap<>(heroes));
        }

        /** The plan's soldiers: the count and the reserve. */
        public int soldiers() {
            return counted + reserve;
        }

        /** Whether the soldiers seen away, not the share, make the reserve. */
        public boolean fromSightings() {
            return seenAway.size() > shareSoldiers;
        }

        /** Whether the plan's heroes stand with no soldier beside them, so the backbone counts them at nothing. */
        public boolean heroesAlone() {
            return soldierPower <= 0 && heroPower > 0;
        }

        /** The plan in soldiers: its power over its unit (the council chooses the target with the least). */
        public double heads() {
            return power / unit;
        }
    }

    /**
     * The plan against a base (recon.md 5.5): null with no usable recon ({@link Intel#usable}: none, blind or stale),
     * whatever {@code sightings} hold. Otherwise:
     *
     * <pre>
     * plan soldiers = counted + max( ceil(share x counted), seenAway )
     * seenAway      = the soldiers among {@code sightings} that count now ({@link Sightings#counts}), not among the
     *                 faces the watch counted
     * plan heroes   = the watch's heroes + the heroes among {@code sightings} that count now and the watch missed
     * plan          = HeroRules.countedPower(the soldiers' power, the heroes' power)
     * </pre>
     *
     * {@code sightings} are the faction's sightings that count for this base ({@link Sightings#forBase}); an empty ledger
     * gives the count, its share and the watch's heroes. The share's soldiers count at the watch's mean power; when the
     * soldiers seen away are more than the share, they make the reserve, each at its own visible power (the larger term,
     * not the sum: both answer how many are out of sight, and adding them would count a party our people saw twice). A
     * hero is counted once by its id, at its war power at full strength: the watch's power for one it saw, else its newest
     * sighting's. {@code share}: {@link #share}; {@code reviveTicks}: the heroes' revival every faction knows.
     */
    public static Plan plan(Intel.Recon recon, long now, double share, Collection<Sightings.Sighting> sightings, long reviveTicks) {
        Intel.Estimate e = Intel.estimate(recon, now);
        if (e == null) {
            return null;
        }
        int counted = recon.soldiers();
        double countedPower = e.power();
        int shareSoldiers = (int) Math.ceil(Math.max(0, share) * counted - 1e-9);
        List<Sightings.Sighting> away = new ArrayList<>();
        Map<String, Sightings.Sighting> heroSightings = new LinkedHashMap<>();
        for (Sightings.Sighting s : sightings == null ? List.<Sightings.Sighting>of() : sightings) {
            if (!Sightings.counts(s, now, reviveTicks)) {
                continue;
            }
            if (s.isHero()) {
                if (!recon.heroes().containsKey(s.hero())) {
                    heroSightings.merge(s.hero(), s, (a, b) -> b.at() > a.at() ? b : a);
                }
            } else if (!recon.faces().contains(s.id())) {
                away.add(s);
            }
        }
        away.sort(Comparator.comparingLong(Sightings.Sighting::at));
        int reserve = Math.max(shareSoldiers, away.size());
        double reservePower = 0;
        if (away.size() > shareSoldiers) {
            for (Sightings.Sighting s : away) {
                reservePower += Math.max(1, s.power());
            }
        } else {
            reservePower = shareSoldiers * countedPower;
        }
        Map<String, Double> heroes = new LinkedHashMap<>(recon.heroes());
        List<Sightings.Sighting> known = new ArrayList<>(heroSightings.values());
        known.sort(Comparator.comparingLong(Sightings.Sighting::at));
        for (Sightings.Sighting s : known) {
            heroes.put(s.hero(), s.power());
        }
        double heroPower = 0;
        for (double p : heroes.values()) {
            heroPower += Math.max(0, p);
        }
        double soldierPower = counted * countedPower + reservePower;
        int soldiers = counted + reserve;
        double unit = soldiers > 0 ? soldierPower / soldiers : 1;
        double power = HeroRules.countedPower(soldierPower, heroPower);
        return new Plan(recon, share, counted, countedPower, shareSoldiers, away, reserve, reservePower, heroes, known,
                soldierPower, heroPower, power, unit);
    }

    // ---- What the lines say the plan rests on (recon.md 5.4; the sightings' forms since task R11) ----

    /** "half again", "a quarter again", "as many again", or "N% again": the share in words. */
    static String shareWords(double share) {
        if (Math.abs(share - 0.5) < 1e-9) {
            return "half again";
        }
        if (Math.abs(share - 0.25) < 1e-9) {
            return "a quarter again";
        }
        if (Math.abs(share - 1) < 1e-9) {
            return "as many again";
        }
        return Math.round(share * 100) + "% again";
    }

    /**
     * The reserve's part of a council's line (recon.md 5.4): "a reserve of 2 (half again the 4 counted)"; from sightings,
     * "a reserve of 3 (3 Stoneguard soldiers seen at our walls on day 4, not seen to fall)", the sources grouped and joined
     * ("2 Stoneguard soldiers seen at our walls on day 4 and 1 met by scout Edrin Cole on day 5, not seen to fall"); with no
     * reserve, "no soldier of theirs seen away" (the empty yard's and the counter-attack's form; a lair's line leaves it
     * out). {@code faction} is the enemy faction's name ("Stoneguard"); a day is the chat lines' day ({@link Whereabouts#day}).
     */
    public static String reserveText(Plan p, String faction) {
        if (p.reserve() <= 0) {
            return "no soldier of theirs seen away";
        }
        if (!p.fromSightings()) {
            return "a reserve of " + p.reserve() + " (" + shareWords(p.share()) + " the " + p.counted() + " counted)";
        }
        // the sightings grouped by who saw them, in the order first seen; each with its newest day
        Map<String, List<Sightings.Sighting>> groups = new LinkedHashMap<>();
        for (Sightings.Sighting s : p.seenAway()) {
            groups.computeIfAbsent(s.source() + "|" + s.by(), k -> new ArrayList<>()).add(s);
        }
        StringBuilder b = new StringBuilder("a reserve of ").append(p.reserve()).append(" (");
        int i = 0;
        for (List<Sightings.Sighting> g : groups.values()) {
            Sightings.Sighting newest = g.get(g.size() - 1);
            int n = g.size();
            b.append(i == 0 ? "" : i == groups.size() - 1 ? " and " : ", ").append(n);
            if (i == 0) {
                b.append(' ').append(faction).append(n == 1 ? " soldier" : " soldiers");
            }
            b.append(' ').append(seen(newest)).append(" on day ").append(Whereabouts.day(newest.at()));
            i++;
        }
        return b.append(", not seen to fall)").toString();
    }

    /**
     * A hero in the plan as a line names it: {@code name} alone for one the watch saw, else with how our people know of it
     * ("Hinae Shirasuzu (seen at our walls on day 4)"); "(alone: not counted)" when no soldier stands beside the plan's
     * heroes, so the backbone counts them at nothing.
     */
    public static String heroText(Plan p, String hero, String name) {
        List<String> notes = new ArrayList<>();
        for (Sightings.Sighting s : p.knownHeroes()) {
            if (s.hero().equals(hero)) {
                notes.add((s.fallen() ? "seen to fall" + (s.source() == Sightings.Source.HOME ? " at our walls" : by(s))
                        : seen(s)) + " on day " + Whereabouts.day(s.at()));
            }
        }
        if (p.heroesAlone()) {
            notes.add("alone: not counted");
        }
        return notes.isEmpty() ? name : name + " (" + String.join("; ", notes) + ")";
    }

    // ---- The council's lines (recon.md 5.4, task R5; the sightings' forms since task R11) ----

    /**
     * What a council's line says it plans against (recon.md 5.4): the count, the heroes in the plan, the recon it rests on
     * and the reserve. "about 4 defenders and Hinae Shirasuzu (scout Edrin Cole, day 5) and a reserve of 2 (half again the 4
     * counted)"; "an empty yard (scout Edrin Cole, day 5) and no soldier of theirs seen away"; at a lair, "about 6 monsters
     * (scout Edrin Cole, day 5)" (a lair's line leaves the reserve out: it has none). A hero the backbone counts at nothing
     * is named "(alone: not counted)": "an empty yard but for Hinae Shirasuzu (alone: not counted) (scout ...)". A hero the
     * watch missed and the faction's sightings know of follows the reserve, with how our people know of it (task R11):
     * "about 2 defenders (scout Edrin Cole, day 5) and a reserve of 3 (3 Stoneguard soldiers seen at our walls on day 4,
     * not seen to fall) and Hinae Shirasuzu (seen at our walls on day 4)". The day
     * is the recon's last sample's ({@link Whereabouts#day}); {@code heroNames} names the heroes by id (an id it does not
     * know is shown as it is).
     */
    public static String defenceText(Plan p, boolean lair, String faction, Map<String, String> heroNames) {
        // the heroes the watch saw go with the count; those known only from sightings follow the reserve (recon.md 5.4)
        List<String> heroes = new ArrayList<>(), known = new ArrayList<>();
        for (String id : p.heroes().keySet()) {
            String name = heroNames == null ? null : heroNames.get(id);
            String text = heroText(p, id, name == null || name.isEmpty() ? id : name);
            (p.recon().heroes().containsKey(id) ? heroes : known).add(text);
        }
        StringBuilder b = new StringBuilder();
        if (p.counted() > 0) {
            b.append("about ").append(p.counted()).append(lair ? (p.counted() == 1 ? " monster" : " monsters")
                    : (p.counted() == 1 ? " defender" : " defenders"));
            if (!heroes.isEmpty()) {
                b.append(heroes.size() == 1 ? " and " : ", ").append(names(heroes));
            }
        } else {
            b.append(lair ? "no monsters" : "an empty yard");
            if (!heroes.isEmpty()) {
                b.append(" but for ").append(names(heroes));
            }
        }
        Intel.Recon r = p.recon();
        b.append(" (").append(r.by().isEmpty() ? "a scout's watch" : "scout " + r.by()).append(", day ").append(Whereabouts.day(r.at())).append(')');
        if (!lair) {
            b.append(" and ").append(reserveText(p, faction));
        }
        if (!known.isEmpty()) {
            b.append(" and ").append(names(known));
        }
        return b.toString();
    }

    /**
     * How the council ranks the targets it may judge (recon.md 5.4): the least plan in soldiers ({@link Plan#heads}: the
     * count, the reserve and the heroes under the backbone), then the nearest ({@code distance}, blocks from home). Lower
     * is chosen first. By the plan, not the bare count: a base with the smaller count and the larger reserve seen away
     * from it could hold the council back while another base would go.
     */
    public static double score(Plan p, double distance) {
        return p.heads() * 1000.0 + distance;
    }

    /** "A", "A and B", "A, B and C". */
    private static String names(List<String> names) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /**
     * The fewest soldiers of {@code soldier} power each a council must send against this plan (recon.md 5.4): two thirds
     * of it at the interval ({@link Intel#twoThirds}), 3 to 2 for a counter-attack ({@link Intel#threeToTwo}), in the plan's
     * unit; the waiting line's "N needed".
     */
    public static int needed(Plan p, double soldier, boolean counter) {
        double s = Math.max(Intel.TIE, soldier);
        for (int n = 0; n < 10_000; n++) {
            if (counter ? Intel.threeToTwo(n * s, p.power(), p.unit()) : Intel.twoThirds(n * s, p.power(), p.unit())) {
                return n;
            }
        }
        return 10_000;
    }

    /**
     * The launch line (recon.md 5.4): "Launching a raid with 7 soldiers on the Stoneguard settlement reported at 400, 67,
     * 16, against " and what it plans against ({@link #defenceText}); {@code against} null (an operator's raid with no
     * usable recon) says "..., with no scout's report of it". {@code what} is "the Stoneguard settlement".
     */
    public static String launchLine(int party, String what, String at, String against) {
        return "Launching a raid with " + party + (party == 1 ? " soldier" : " soldiers") + " on " + what + " reported at " + at
                + (against == null ? ", with no scout's report of it" : ", against " + against);
    }

    /**
     * The council's line while it waits on a target it judged (recon.md 5.4): "waiting on the Stoneguard settlement: 6
     * soldiers to send, 7 needed against about 4 defenders and Hinae Shirasuzu (scout Edrin Cole, day 5) and a reserve of
     * 2 (half again the 4 counted)".
     */
    public static String waitLine(String what, int party, int needed, String against) {
        return "waiting on " + what + ": " + party + (party == 1 ? " soldier" : " soldiers") + " to send, " + needed
                + " needed against " + against;
    }

    /** How a sighting was made, as a line says it. */
    private static String seen(Sightings.Sighting s) {
        return switch (s.source()) {
            case HOME -> "seen at our walls";
            case FIELD -> "met" + by(s);
            case WATCH -> (s.isHero() ? "seen" : "seen leaving") + by(s);
        };
    }

    private static String by(Sightings.Sighting s) {
        return s.by().isEmpty() ? "" : " by scout " + s.by();
    }
}
