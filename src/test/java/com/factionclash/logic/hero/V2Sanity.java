package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every rule of format 2 a loaded definition must obey, whatever its file said (HEROES_V2 6.3-6.13, 12, 14.1;
 * MOBILITY_AND_WORK 8.3): what the reader and the balance pass promise game code.
 */
final class V2Sanity {
    private V2Sanity() {
    }

    private static final double EPS = 1e-6;

    static void sane(HeroDef d) {
        HeroDef.V2 v = d.v2();
        assertNotNull(v, d.id());
        String h = d.id() + ": ";
        if (d.format() >= 2) {
            assertTrue(d.skills().isEmpty() && d.ultimate() == null && d.empowered() == null
                    && d.baseAbilities().isEmpty(), h + "format 2 keeps format 1's parts empty");
        }
        assertTrue(v.moves().size() <= HeroCatalogue.MAX_MOVES, h + "moves");
        assertTrue(v.states().size() <= HeroCatalogue.MAX_STATES, h + "states");
        Set<String> ids = new HashSet<>();
        for (Move m : v.allMoves()) {
            String w = h + m.id() + ": ";
            assertTrue(ids.add(m.id()), w + "ids are unique");
            assertTrue(m.id().matches("[a-z][a-z0-9_]{1,23}"), w + "id form");
            assertFalse(HeroCatalogue.LOCOMOTION_NAMES.contains(m.id()), w + "not a locomotion name");
            boolean ult = m.ability().slot() == HeroDef.Slot.ULTIMATE;
            assertEquals(ult, m.cls() == MoveClass.ULTIMATE, w + "class ultimate is the ultimate's");
            if (!ult) {
                assertTrue(m.cls().atLeast(HeroCatalogue.classOf(m.form())) || m.effectLists().stream()
                        .noneMatch(HeroBalance::hitsHard), w + "never lighter than its form while it hits hard");
                if (m.effectLists().stream().anyMatch(HeroBalance::stunsEnemies)) {
                    assertTrue(m.cls().atLeast(MoveClass.HEAVY), w + "a stun needs a heavy tell");
                }
                assertTrue(m.ability().cooldown() >= m.ability().numbers().floor() - EPS, w + "cooldown floor");
            } else {
                double gap = m.ability().numbers().minGap();
                assertTrue(gap >= HeroCatalogue.ULTIMATE_GAP_MIN - EPS && gap <= HeroCatalogue.ULTIMATE_GAP_MAX + EPS,
                        w + "ultimate gap");
            }
            assertTrue(m.frames().windup() >= m.cls().minWindup - EPS, w + "wind-up minimum");
            assertTrue(m.frames().recovery() >= m.cls().minRecovery - EPS, w + "recovery minimum");
            assertFalse(Double.isNaN(m.frames().active()), w + "active");
            assertNotNull(m.telegraph().shape(), w + "decal");
            if (m.cls() != MoveClass.LIGHT || m.unblockable()) {
                assertTrue(m.telegraph().shape() != HeroDef.Decal.NONE, w + "a heavier or unblockable move draws a mark");
            }
            if (m.form() == HeroDef.Form.GRAB) {
                assertTrue(m.unblockable(), w + "a grab is unblockable");
            }
            if (m.form() == HeroDef.Form.ZONE) {
                assertEquals(0, m.ability().delay(), EPS, w + "a zone's delay is inside its wind-up");
            }
            assertTrue(m.weight() >= 0 && m.weight() <= 10, w + "weight");
            assertFalse(m.bands().isEmpty(), w + "bands");
            for (int p : m.phases()) {
                assertTrue(p >= 1 && p <= v.boss().phases().size(), w + "phase " + p);
            }
            assertTrue(m.form() != HeroDef.Form.COMBO || m.strikes().size() >= 2 && m.strikes().size() <= 4,
                    w + "strikes");
            assertTrue(m.form() == HeroDef.Form.BARRAGE || m.perShot().isEmpty(), w + "per_shot only on a barrage");
            for (HeroDef.Followup f : m.followups()) {
                Move to = v.move(f.move());
                assertNotNull(to, w + "followup " + f.move());
                assertTrue(to.ability().slot() != HeroDef.Slot.ULTIMATE, w + "no ultimate follows");
                assertTrue(!f.move().equals(m.id()) || m.repeatable(), w + "only a repeatable move follows itself");
                assertTrue(f.chance() >= 1 && f.chance() <= 100, w + "chance");
            }
            assertTrue(m.channel() == 0 || m.channel() >= 5 && m.channel() <= 30, w + "channel");
            capped(m, d, w);
        }
        // strings: no loop through other moves, at most 4 moves (repeats aside: the runtime ends them)
        Map<String, List<String>> g = new java.util.LinkedHashMap<>();
        for (Move m : v.allMoves()) {
            List<String> to = new java.util.ArrayList<>();
            for (HeroDef.Followup f : m.followups()) {
                if (!f.move().equals(m.id())) {
                    to.add(f.move());
                }
            }
            g.put(m.id(), to);
        }
        assertTrue(HeroReader2.longest(g) <= HeroCatalogue.STRING_MAX, h + "strings");
        // the boss block
        HeroDef.Boss b = v.boss();
        assertTrue(b.healthScale() >= 200 && b.healthScale() <= 600, h + "health scale");
        assertTrue(b.maxHitShare() >= 5 && b.maxHitShare() <= 25, h + "per-hit cap");
        assertTrue(b.poise() == 0 || b.poise() >= 50 && b.poise() <= 200, h + "poise");
        assertTrue(!b.phases().isEmpty() && b.phases().size() <= 3, h + "phases");
        assertEquals(100, b.phases().get(0).at(), h + "the first phase");
        for (int i = 1; i < b.phases().size(); i++) {
            assertTrue(b.phases().get(i).at() < b.phases().get(i - 1).at(), h + "phases go down");
        }
        assertTrue(b.reactions().size() <= HeroCatalogue.MAX_REACTIONS, h + "reactions");
        for (HeroDef.Reaction r : b.reactions()) {
            Move m = v.move(r.move());
            assertNotNull(m, h + "reaction " + r.move());
            assertTrue(m.ability().slot() != HeroDef.Slot.ULTIMATE, h + "a reaction answers with a move");
        }
        assertTrue(b.weakPoints().size() <= HeroCatalogue.MAX_WEAK_POINTS, h + "weak points");
        assertTrue(!b.rebirth() || d.rarity() == HeroDef.Rarity.LEGENDARY, h + "rebirth");
        for (HeroDef.Phase ph : b.phases()) {
            for (String st : ph.states()) {
                assertNotNull(v.state(st), h + "phase state " + st);
            }
            for (HeroDef.StatBonus sb : ph.stats()) {
                assertTrue(Math.abs(sb.percent()) <= HeroCatalogue.EMPOWERED_STAT_CAP, h + "phase stat");
            }
            assertTrue(ph.transition().seconds() >= 0.8 - EPS && ph.transition().seconds() <= 2 + EPS, h
                    + "transition");
        }
        for (HeroDef.State st : v.states()) {
            assertTrue(st.size() >= 100 && st.size() <= HeroCatalogue.MAX_SIZE, h + "state size");
            for (HeroDef.StatBonus sb : st.stats()) {
                assertTrue(Math.abs(sb.percent()) <= HeroCatalogue.EMPOWERED_STAT_CAP, h + "state stat");
            }
        }
        // empower effects name a state
        for (List<Effect> l : HeroBalance.effectLists(d)) {
            for (Effect e : l) {
                if (e instanceof HeroDef.Empower em && d.format() >= 2) {
                    assertNotNull(v.state(em.state()), h + "empower " + em.state());
                }
            }
        }
        // the end of a fight
        HeroDef.EndOfFight end = v.end();
        assertTrue(end.fleeAt() >= EscapeRules.FLEE_FLOOR && end.fleeAt() <= EscapeRules.FLEE_MAX, h + "flee_at");
        assertTrue(end.kind() == HeroDef.WhenLosing.ESCAPE || end.standsFor().isEmpty(), h + "a last stand stands"
                + " everywhere");
        assertTrue(end.fleeAt() <= Math.max(EscapeRules.FLEE_FLOOR, b.lastPhaseAt() - EscapeRules.LAST_PHASE_MARGIN),
                h + "flee_at inside the last phase");
        // personality
        HeroDef.Personality p = v.personality();
        for (HeroDef.Trait t : HeroDef.Trait.values()) {
            int x = p.trait(t);
            assertTrue(x >= 0 && x <= 100, h + t);
            if (p.temperament() != null) {
                int dflt = HeroCatalogue.TEMPERAMENT_TRAITS.get(p.temperament())[t.ordinal()];
                assertTrue(Math.abs(x - dflt) <= HeroCatalogue.TRAIT_DRIFT, h + t + " within 20 of the temperament");
            }
        }
        // talents, voice, relationships, revival, mobility
        assertTrue(v.talents().size() <= HeroCatalogue.MAX_TALENTS, h + "talents");
        assertTrue(v.talents().stream().filter(HeroDef.Talent::realm).count() <= 1, h + "one realm talent");
        assertEquals(v.talents().size(), v.talents().stream().map(HeroDef.Talent::id).distinct().count(), h
                + "talents unique");
        HeroDef.Voice vo = v.voice();
        assertTrue(vo.pitch() >= 0.5 && vo.pitch() <= 2 && vo.rate() >= 2 && vo.rate() <= 4, h + "voice");
        assertTrue(v.relationships().size() <= HeroCatalogue.MAX_RELATIONSHIPS, h + "relationships");
        assertTrue(v.relationships().stream().noneMatch(r -> r.hero().equals(d.id())), h + "no relationship with"
                + " itself");
        if (v.revival() != null) {
            assertTrue(v.revival().seconds() >= 30 && v.revival().seconds() <= 3600, h + "revival");
        }
        HeroDef.Mobility mo = v.mobility();
        if (mo != null) {
            assertTrue(mo.primary().tier() >= 1 && mo.primary().tier() <= 3, h + "primary tier");
            assertFalse(mo.primary().trait().footing(), h + "a footing is no primary");
            if (d.rarity() == HeroDef.Rarity.COMMON || d.rarity() == HeroDef.Rarity.RARE) {
                assertTrue(mo.primary().tier() <= 2, h + "tier 3 only epic and legendary");
            }
            if (mo.secondary() != null) {
                assertTrue(mo.secondary().tier() >= 1 && mo.secondary().tier() <= Math.min(2, mo.primary().tier()),
                        h + "secondary tier");
                assertEquals(null, HeroReader2.pairProblem(mo.primary().trait(), mo.secondary().trait(),
                        d.element()), h + "an allowed pair");
            }
            assertTrue(mo.aerial() == null || mo.primary().trait() == HeroDef.MobilityTrait.FLYER, h + "aerial");
            if (mo.aerial() != null) {
                assertTrue(mo.aerial().maxAir() >= 4 && mo.aerial().maxAir() <= 8 && mo.aerial().dives() >= 1
                        && mo.aerial().dives() <= 3 && mo.aerial().weight() >= 0 && mo.aerial().weight() <= 10,
                        h + "aerial numbers");
            }
            assertTrue(mo.avoid().size() <= 4, h + "avoid");
        } else {
            assertEquals(1, d.format(), h + "a format-2 hero always has its mobility");
        }
    }

    /** The damage slot caps of HEROES_V2 6.5 by form, per receiver at the level-1 reference. */
    private static void capped(Move m, HeroDef d, String w) {
        HeroDef.Ability a = m.ability();
        double cap = HeroCatalogue.CAPS.get(a.slot()).damage();
        HeroBalance.Ref r = HeroBalance.ref(d);
        double t;
        double limit = cap;
        switch (a.form()) {
            case COMBO -> {
                t = 0;
                for (HeroDef.Strike s : m.strikes()) {
                    t += HeroBalance.totals(s.effects(), 1, 1, r).damage();
                }
                limit = cap * HeroCatalogue.COMBO_CAP_FACTOR;
            }
            case BARRAGE -> {
                t = HeroBalance.totals(a.effects(), 1, 1, r).damage() * a.count();
                for (int i = 0; i < m.perShot().size(); i++) {
                    t += HeroBalance.totals(m.perShot().get(i), 1, 1, r).damage()
                            * HeroBalance.shotsOf(i, m.perShot().size(), a.count());
                }
            }
            case RAIN -> {
                t = HeroBalance.totals(a.effects(), 1, 1, r).damage() * a.count() / 3.0;
                limit = cap * HeroCatalogue.ZONE_CAP_FACTOR;
            }
            case WAVE -> t = HeroBalance.totals(a.effects(), Math.max(1, m.extra().waves()), 1, r).damage();
            case CHARGE, GRAB, COUNTER, SWEEP -> t = HeroBalance.totals(a.effects(), 1, 1, r).damage();
            default -> {
                t = HeroBalance.totals(a.effects(), a.effectiveHits(), a.pulses(), r).damage();
                limit = cap * (a.pulses() > 1 ? HeroCatalogue.ZONE_CAP_FACTOR : 1);
            }
        }
        assertTrue(t <= limit + 1e-6, w + "damage " + t + " within " + limit);
    }
}
