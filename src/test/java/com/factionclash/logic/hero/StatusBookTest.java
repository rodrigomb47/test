package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.StatusBook.DotDue;
import com.factionclash.logic.hero.StatusBook.Entry;
import com.factionclash.logic.hero.StatusBook.Expired;
import com.factionclash.logic.hero.StatusBook.Kind;
import com.factionclash.logic.hero.StatusBook.MarkResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatusBook} (v1 13, 19; task A3): marks stack to their maximum and report it once, DoTs refresh or stack and
 * tick once a second, stat modifiers sum per stat, shields replace, freezes hold, everything expires on time, a
 * cleanse removes the harmful part, and at most 32 statuses stay (the oldest goes).
 */
class StatusBookTest {
    private static final double EPS = 1e-9;

    @Test
    void marksStackToTheirMaximumAndReportItOnce() {
        StatusBook b = new StatusBook();
        MarkResult r = b.mark("toll", 2, 5, 10, "hero_a", false, true, 0);
        assertEquals(2, r.stacks());
        assertFalse(r.full());
        r = b.mark("toll", 2, 5, 10, "hero_a", false, true, 20);
        assertEquals(4, r.stacks());
        r = b.mark("toll", 2, 5, 10, "hero_a", false, true, 40);
        assertEquals(5, r.stacks());
        assertTrue(r.full(), "the application that fills it reports it");
        r = b.mark("toll", 1, 5, 10, "hero_a", false, true, 60);
        assertFalse(r.full(), "only once");
        assertEquals(5, b.marks("toll", 60));
        assertEquals(5, b.marks("toll", 259), "refreshed to 10 s at tick 60");
        assertEquals(0, b.marks("toll", 260));
        assertEquals(Map.of("toll", 5), b.marks(100));
    }

    @Test
    void markIdsAreSharedByEveryHero() {
        StatusBook b = new StatusBook();
        b.mark("pinned", 1, 3, 10, "hero_a", false, true, 0);
        MarkResult r = b.mark("pinned", 1, 3, 10, "hero_b", false, true, 10);
        assertEquals(2, r.stacks(), "another hero's stacks add to the same mark (v1 19)");
        assertEquals(1, b.size());
    }

    @Test
    void clearMarksSpendsTheStacks() {
        StatusBook b = new StatusBook();
        b.mark("brand", 3, 5, 10, "h", true, true, 0);
        assertTrue(b.glowing(10));
        assertEquals(3, b.clearMarks("brand", 10));
        assertEquals(0, b.marks("brand", 10));
        assertFalse(b.glowing(10));
        assertEquals(0, b.clearMarks("brand", 10));
    }

    @Test
    void aDotTicksOnceASecondForItsSeconds() {
        StatusBook b = new StatusBook();
        b.dot("h", "bleed", Element.PHYSICAL, 1.5, 4, 1, 100);
        int ticks = 0;
        double total = 0;
        for (long t = 100; t <= 300; t++) {
            for (DotDue d : b.dueDots(t)) {
                ticks++;
                total += d.perSecond();
                assertEquals(0, (t - 100) % 20, "on whole seconds from the application");
            }
        }
        assertEquals(4, ticks, "4 s, 4 ticks");
        assertEquals(6, total, EPS);
        assertFalse(b.hasDot(300));
    }

    @Test
    void aDotRefreshesWithOneStackAndStacksAboveOne() {
        StatusBook b = new StatusBook();
        b.dot("h", "burn", Element.FIRE, 1, 4, 1, 0);
        assertEquals(1, b.dot("h", "burn", Element.FIRE, 2, 4, 1, 30));
        assertEquals(2, b.dueDots(40).get(0).perSecond(), EPS, "the stronger amount stays");
        b.dot("h", "bleed", Element.PHYSICAL, 0.75, 4, 2, 0);
        assertEquals(2, b.dot("h", "bleed", Element.PHYSICAL, 0.75, 4, 2, 5));
        assertEquals(2, b.dot("h", "bleed", Element.PHYSICAL, 0.75, 4, 2, 6), "at most max_stacks");
        List<DotDue> due = b.dueDots(60);
        assertEquals(1, due.size(), "one sum per source");
        assertEquals(2 + 1.5, due.get(0).perSecond(), EPS, "burn 2 and bleed 0.75 x 2 stacks");
        assertEquals(Element.FIRE, due.get(0).element(), "the strongest entry's element");
    }

    @Test
    void dotsAreSummedPerSource() {
        StatusBook b = new StatusBook();
        b.dot("a", "x", Element.VENOM, 4, 4, 1, 0);
        b.dot("b", "x", Element.BLIGHT, 5, 4, 1, 0);
        List<DotDue> due = b.dueDots(20);
        assertEquals(2, due.size());
        assertEquals(6, HeroRules.dotTick(List.of(due.get(0).perSecond(), 3.0), HeroRules.MAX_DOT_PER_SECOND), EPS,
                "one hero's DoTs are held to 6 a second");
    }

    @Test
    void statsSumPerStatAndExpire() {
        StatusBook b = new StatusBook();
        b.stat("h", "haste", Stat.SPEED, 20, 3, 1, 0);
        b.stat("g", "slow", Stat.SPEED, -15, 5, 1, 0);
        b.stat("h", "fury", Stat.ATTACK, 10, 8, 3, 0);
        b.stat("h", "fury", Stat.ATTACK, 10, 8, 3, 10);
        assertTrue(b.consumeStatsDirty());
        assertFalse(b.consumeStatsDirty());
        assertEquals(5, b.statPercent(Stat.SPEED, 10), EPS);
        assertEquals(20, b.statPercent(Stat.ATTACK, 10), EPS, "two stacks");
        assertEquals(Map.of(Stat.SPEED, 5.0, Stat.ATTACK, 20.0), b.stats(10));
        Expired x = b.expire(60);
        assertTrue(x.stats());
        assertTrue(b.consumeStatsDirty());
        assertEquals(-15, b.statPercent(Stat.SPEED, 60), EPS, "the 3 s haste ran out");
        b.stat("p", "always", Stat.ARMOR, 10, 0, 1, 0);
        assertEquals(10, b.statPercent(Stat.ARMOR, 1_000_000), EPS, "a held stat never runs out");
        assertTrue(b.removeStat("p", "always"));
        assertEquals(0, b.statPercent(Stat.ARMOR, 0), EPS);
    }

    @Test
    void aShieldIsReplacedAndRunsOut() {
        StatusBook b = new StatusBook();
        b.shield(6, 10, 0);
        b.shield(8, 5, 20);
        Entry s = b.shield(30);
        assertNotNull(s);
        assertEquals(8, s.amount, EPS);
        assertEquals(1, b.size(), "they never add");
        Expired x = b.expire(121);
        assertEquals(8, x.shield(), EPS);
        assertNull(b.shield(121));
    }

    @Test
    void aFreezeHoldsTheLongerTime() {
        StatusBook b = new StatusBook();
        b.freeze(3, 0);
        b.freeze(1, 10);
        assertTrue(b.frozen(59));
        assertFalse(b.frozen(60));
        assertTrue(b.expire(60).freeze());
    }

    @Test
    void aCleanseRemovesTheHarmfulPart() {
        StatusBook b = new StatusBook();
        b.dot("enemy", "burn", Element.FIRE, 2, 4, 1, 0);
        b.mark("hex", 2, 5, 10, "enemy", false, true, 0);
        b.mark("ward", 1, 5, 10, "self", false, false, 0);
        b.freeze(3, 0);
        b.stat("enemy", "slow", Stat.SPEED, -20, 5, 1, 0);
        b.stat("ally", "haste", Stat.SPEED, 20, 5, 1, 0);
        b.stat("enemy", "frail", Stat.DAMAGE_TAKEN, 10, 5, 1, 0);
        b.stat("ally", "guard", Stat.DAMAGE_TAKEN, -10, 5, 1, 0);
        b.shield(4, 10, 0);
        b.consumeStatsDirty();
        assertTrue(b.cleanse(src -> false));
        assertFalse(b.hasDot(10));
        assertEquals(0, b.marks("hex", 10));
        assertEquals(1, b.marks("ward", 10), "its own side's mark stays");
        assertFalse(b.frozen(10));
        assertEquals(20, b.statPercent(Stat.SPEED, 10), EPS);
        assertEquals(-10, b.statPercent(Stat.DAMAGE_TAKEN, 10), EPS);
        assertNotNull(b.shield(10), "a shield is not harmful");
        b.mark("ward", 1, 5, 10, "turncoat", false, false, 0);
        b.cleanse(src -> src.equals("turncoat"));
        assertEquals(0, b.marks("ward", 10), "a source that is hostile now");
    }

    @Test
    void atMostThirtyTwoStatusesTheOldestGoes() {
        StatusBook b = new StatusBook();
        for (int i = 0; i < 40; i++) {
            b.mark("m" + i, 1, 1, 30, "h", false, true, i);
        }
        assertEquals(StatusBook.MAX_STATUSES, b.size());
        assertEquals(0, b.marks("m7", 40));
        assertEquals(1, b.marks("m8", 40));
        assertEquals(1, b.marks("m39", 40));
    }

    @Test
    void savedEntriesComeBack() {
        StatusBook b = new StatusBook();
        b.dot("h", "bleed", Element.PHYSICAL, 1, 4, 2, 0);
        b.stat("h", "fury", Stat.ATTACK, 10, 8, 3, 0);
        b.mark("toll", 2, 5, 10, "h", true, true, 0);
        StatusBook c = new StatusBook();
        for (Entry e : b.entries()) {
            Entry n = new Entry(e.kind, e.id, e.source);
            n.element = e.element;
            n.stat = e.stat;
            n.amount = e.amount;
            n.stacks = e.stacks;
            n.maxStacks = e.maxStacks;
            n.expiresAt = e.expiresAt;
            n.nextTickAt = e.nextTickAt;
            n.glow = e.glow;
            n.enemy = e.enemy;
            c.restore(n);
        }
        assertTrue(c.consumeStatsDirty(), "a loaded stat must be applied again");
        assertEquals(2, c.marks("toll", 100));
        assertEquals(10, c.statPercent(Stat.ATTACK, 100), EPS);
        assertEquals(1, c.dueDots(20).size());
        assertEquals(Kind.DOT, c.entries().get(0).kind);
        c.clear();
        assertTrue(c.isEmpty());
    }
}
