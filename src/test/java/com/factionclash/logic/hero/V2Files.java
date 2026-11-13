package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.Problems.Problem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Small format-2 hero files for the tests (HEROES_V2 12). {@link #MIN2} is the smallest hero that loads with nothing
 * to report: one phase (so no phase "only changes numbers"), a weak point (a weakness, 6.9) and a dash (a gap
 * closer, 6.12).
 */
final class V2Files {
    private V2Files() {
    }

    static final String LUNGE = "{\"id\": \"lunge\", \"name\": \"Lunge\", \"form\": \"dash\", \"effects\": [{\"type\":"
            + " \"damage\", \"amount\": 2}]}";

    static final String MIN2 = "{\"format\": 2, \"id\": \"test_hero\", \"name\": \"Test Hero\", \"behaviour\":"
            + " {\"combat_role\": \"striker\"}, \"basic_attack\": {\"style\": \"melee\"}, \"gear\": [\"iron_sword\"]";

    static final String BOSS = "\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}]}";

    /** The minimal clean format-2 hero plus {@code more} top-level fields (a {@code boss} or {@code moves} replaces its own). */
    static String hero2(String more) {
        StringBuilder sb = new StringBuilder(MIN2);
        if (!more.contains("\"boss\":")) {
            sb.append(", ").append(BOSS);
        }
        if (!more.contains("\"moves\":")) {
            sb.append(", \"moves\": [").append(LUNGE).append("]");
        }
        if (!more.isEmpty()) {
            sb.append(", ").append(more);
        }
        return sb.append("}").toString();
    }

    /** A hero whose moves are the lunge and one more ({@code fields} of a move object). */
    static String withMove(String fields) {
        return hero2("\"moves\": [" + LUNGE + ", {" + fields + "}]");
    }

    static Loaded read(String text) {
        return HeroReader.read("test_hero.json", text);
    }

    static HeroDef def(String text) {
        Loaded l = read(text);
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, () -> "should load:\n" + l.problems().report());
        return d;
    }

    static List<Problem> of(Loaded l, Problems.Code code) {
        return l.problems().all().stream().filter(p -> p.code().equals(code.id())).toList();
    }

    static Problem one(Loaded l, Problems.Code code) {
        List<Problem> p = of(l, code);
        assertEquals(1, p.size(), () -> "one " + code + " in:\n" + l.problems().report());
        return p.get(0);
    }

    static HeroDef.Move move(HeroDef d, String id) {
        HeroDef.Move m = d.v2().move(id);
        assertNotNull(m, id);
        return m;
    }
}
