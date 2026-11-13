package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroBalance.Ref;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.Common;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.Problems.Code;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.factionclash.logic.hero.HeroReaderTest.hero;
import static com.factionclash.logic.hero.HeroReaderTest.of;
import static com.factionclash.logic.hero.HeroReaderTest.one;
import static com.factionclash.logic.hero.HeroReaderTest.read;
import static com.factionclash.logic.hero.HeroReaderTest.skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HERO_FORMAT.md 31.1 {@code HeroBalanceTest}: reference Amounts, slot caps, weights and floors, the other caps. */
class HeroBalanceTest {
    private static final double EPS = 1e-9;
    private static final Common T = new Common(To.TARGETS, 8, 100, null);
    private static final Ref REF = new Ref(7, 36, 19, Map.of("toll", 4));

    private static HeroDef example(String id) throws IOException {
        Path p = Path.of("docs/heroes/examples/" + id + ".json");
        return HeroReader.read(p.getFileName().toString(), Files.readString(p)).hero(id);
    }

    private static Ability skillOf(HeroDef d, String name) {
        return d.skills().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ reference Amounts (v1 12)

    @Test
    void everyAmountPartHasItsReference() {
        assertEquals(3, HeroBalance.reference(Amount.of(3), REF), EPS);
        assertEquals(7 * 1.2, HeroBalance.reference(new Amount(0, 120, 0, 0, 0, 0, null, 0), REF), EPS);
        assertEquals(36 * 0.2, HeroBalance.reference(new Amount(0, 0, 20, 0, 0, 0, null, 0), REF), EPS);
        assertEquals(19 * 0.2, HeroBalance.reference(new Amount(0, 0, 0, 20, 0, 0, null, 0), REF), EPS);
        assertEquals(20 * 0.15, HeroBalance.reference(new Amount(0, 0, 0, 0, 15, 0, null, 0), REF), EPS,
                "a receiver with 20 max health");
        assertEquals(10 * 0.3, HeroBalance.reference(new Amount(0, 0, 0, 0, 0, 30, null, 0), REF), EPS,
                "missing 10");
        assertEquals(4 * 1.5, HeroBalance.reference(new Amount(0, 0, 0, 0, 0, 0, "toll", 1.5), REF), EPS,
                "the mark at its max_stacks in the file");
        assertEquals(5 * 1.5, HeroBalance.reference(new Amount(0, 0, 0, 0, 0, 0, "other", 1.5), REF), EPS,
                "5 stacks when the file never applies the mark");
        assertEquals(2 + 7 + 3.6 + 1.9 + 2 + 1 + 4,
                HeroBalance.reference(new Amount(2, 100, 10, 10, 10, 10, "toll", 1), REF), EPS, "parts add up");
    }

    @Test
    void theReferenceComesFromTheHero() throws IOException {
        HeroDef bram = example("bram_ostler");
        Ref r = HeroBalance.ref(bram);
        assertEquals(6 + 1, r.attack(), EPS, "melee 6 + stats.attack");
        assertEquals(36, r.health(), EPS);
        assertEquals(4 + 15, r.armor(), EPS, "stats.armor + full iron");
        assertEquals(4, r.stacks("toll"));
        assertEquals(6 + 1, HeroBalance.ref(example("sela_quickfern")).attack(), EPS, "bow 6 + 1");
        assertEquals(3, HeroBalance.ref(example("maudie_brine")).attack(), EPS, "thrown: its amount");
        assertEquals(7 + 2, HeroCatalogue.referenceAttack(HeroDef.Style.CROSSBOW, 4, 2), EPS);
        assertEquals(8, HeroCatalogue.referenceAttack(HeroDef.Style.TRIDENT, 4, 0), EPS);
    }

    // ------------------------------------------------------------------ the weight table (v1 15)

    @Test
    void everyEffectHasItsWeight() {
        Amount six = Amount.of(6);
        double[][] rows = {
                {HeroBalance.effectWeight(new HeroDef.Damage(T, six, HeroDef.Element.FIRE, 0), 1, 1, REF, null), 6},
                {HeroBalance.effectWeight(new HeroDef.Dot(T, Amount.of(2), 4, HeroDef.Element.FIRE, 1), 1, 1, REF, null),
                        2 * 4 * 0.75},
                {HeroBalance.effectWeight(new HeroDef.Heal(T, Amount.of(4)), 1, 1, REF, null), 5},
                {HeroBalance.effectWeight(new HeroDef.Shield(T, six, 10), 1, 1, REF, null), 6},
                {HeroBalance.effectWeight(new HeroDef.PotionEffect(T, "slowness", 2, 6, false), 1, 1, REF, null), 3},
                {HeroBalance.effectWeight(new HeroDef.StatMod(T, HeroDef.Stat.ARMOR, -20, 8, 1), 1, 1, REF, null), 4},
                {HeroBalance.effectWeight(new HeroDef.Knockback(T, 3, 0), 1, 1, REF, null), 1.5},
                {HeroBalance.effectWeight(new HeroDef.Pull(T, 2), 1, 1, REF, null), 1},
                {HeroBalance.effectWeight(new HeroDef.Ignite(T, 4), 1, 1, REF, null), 2},
                {HeroBalance.effectWeight(new HeroDef.Freeze(T, 3), 1, 1, REF, null), 1.5},
                {HeroBalance.effectWeight(new HeroDef.Taunt(T, 4), 1, 1, REF, null), 2},
                {HeroBalance.effectWeight(new HeroDef.Stun(T, 1.5), 1, 1, REF, null), 6},
                {HeroBalance.effectWeight(new HeroDef.Cleanse(T), 1, 1, REF, null), 1},
                {HeroBalance.effectWeight(new HeroDef.Dispel(T), 1, 1, REF, null), 1},
                {HeroBalance.effectWeight(new HeroDef.Energy(T, 15), 1, 1, REF, null), 3},
                {HeroBalance.effectWeight(new HeroDef.CooldownCut(T, 3), 1.5, 1, REF, null), 3},
                {HeroBalance.effectWeight(new HeroDef.MoveSelf(T, HeroDef.Direction.BACK, 4), 1.5, 3, REF, null), 1},
                {HeroBalance.effectWeight(new HeroDef.Mark(T, "toll", 2, 5, 10, false), 1, 1, REF, null), 1},
                {HeroBalance.effectWeight(new HeroDef.ClearMarks(T, "toll"), 1, 1, REF, null), 0},
                {HeroBalance.effectWeight(new HeroDef.Empower(T, 8), 1, 1, REF, new HeroDef.Empowered("E", "",
                        List.of(new HeroDef.StatBonus(HeroDef.Stat.ARMOR, 30), new HeroDef.StatBonus(HeroDef.Stat.SPEED,
                                -10)), List.of(), null, -1, 100, false, null)), 8 / 4.0 * (1 + 40 / 20.0)},
                {HeroBalance.effectWeight(new HeroDef.Summon(T, HeroDef.Creature.WOLF, 2, 20, null), 1, 1, REF, null),
                        4},
                {HeroBalance.effectWeight(new HeroDef.Summon(T, HeroDef.Creature.SNOW_GOLEM, 2, 20, null), 1, 1, REF,
                        null), 2},
                {HeroBalance.effectWeight(new HeroDef.Summon(T, HeroDef.Creature.IRON_GOLEM, 1, 30, null), 1, 1, REF,
                        null), 9},
        };
        for (int i = 0; i < rows.length; i++) {
            assertEquals(rows[i][1], rows[i][0], EPS, "row " + i);
        }
        assertEquals(HeroDef.EffectType.values().length, rows.length - 2, "every effect type is in the table");
    }

    @Test
    void receiversHitsChanceAndConditionsScaleTheWeight() {
        HeroDef.Damage d = new HeroDef.Damage(T, Amount.of(4), HeroDef.Element.PHYSICAL, 0);
        assertEquals(4 * 1.5, HeroBalance.effectWeight(d, 1.5, 1, REF, null), EPS, "an area: A 1.5");
        assertEquals(4 * 1.5 * 3, HeroBalance.effectWeight(d, 1.5, 3, REF, null), EPS, "hits x pulses");
        assertEquals(4, HeroBalance.effectWeight(d.with(new Common(To.SELF, 8, 100, null)), 1.5, 1, REF, null), EPS,
                "to: self reaches one");
        HeroDef.Heal h = new HeroDef.Heal(new Common(To.ALLIES, 8, 50, null), Amount.of(4));
        assertEquals(5 * 1.5 * 0.5, HeroBalance.effectWeight(h, 1, 1, REF, null), EPS, "to: allies is 1.5; chance 50%");
        HeroDef.Damage cond = new HeroDef.Damage(new Common(To.TARGETS, 8, 100, new HeroDef.Condition(
                HeroDef.ConditionType.TARGET_BURNING, 0, null, null)), Amount.of(4), HeroDef.Element.FIRE, 0);
        assertEquals(3, HeroBalance.effectWeight(cond, 1, 1, REF, null), EPS, "a condition: x 0.75");
    }

    @Test
    void theExampleAbilitiesGetTheSpecifiedWeightsAndFloors() throws IOException {
        // v1 section 15's table, from the reference checker: weight, floor (both to one decimal)
        Object[][] table = {
                {"bram_ostler", "Shield Slam", "8.8", "6.4", 9.0},
                {"bram_ostler", "Gate Call", "10.2", "7.1", 14.0},
                {"bram_ostler", "Brace", "5.5", "4.8", 16.0},
                {"sela_quickfern", "Pinning Shot", "10.9", "7.5", 10.0},
                {"sela_quickfern", "Thornfan", "19.5", "11.8", 14.0},
                {"sela_quickfern", "Slip Away", "2.5", "3.2", 10.0},
                {"maudie_brine", "Mending Draught", "7.5", "5.8", 8.0},
                {"maudie_brine", "Clear Water", "3.4", "3.7", 18.0},
                {"maudie_brine", "Undertow", "4.8", "4.4", 11.0},
        };
        for (Object[] row : table) {
            Ability a = skillOf(example((String) row[0]), (String) row[1]);
            assertEquals(row[2], Problems.num1(a.numbers().weight()), row[1] + " weight");
            assertEquals(row[3], Problems.num1(a.numbers().floor()), row[1] + " floor");
            assertEquals((double) row[4], a.cooldown(), EPS, row[1] + ": the given cooldown is above its floor");
        }
        // v1 14: the least gap between ultimates
        assertEquals(27, Math.round(example("bram_ostler").ultimate().numbers().minGap()));
        assertEquals(45, Math.round(example("sela_quickfern").ultimate().numbers().minGap()));
        assertEquals(32, Math.round(example("maudie_brine").ultimate().numbers().minGap()));
        assertEquals(20, HeroBalance.ultimateGap(4), EPS);
        assertEquals(60, HeroBalance.ultimateGap(95), EPS);
        assertEquals(33.3, HeroBalance.ultimateGap(33.3), EPS);
    }

    @Test
    void theCooldownFloor() {
        assertEquals(2, HeroBalance.cooldownFloor(0, 1), EPS);
        assertEquals(6.4, HeroBalance.cooldownFloor(8.8, 1), EPS);
        assertEquals(6.4 * 1.5, HeroBalance.cooldownFloor(8.8, 2), EPS, "a second charge: x 1.5");
        assertEquals(6.4 * 2, HeroBalance.cooldownFloor(8.8, 3), EPS);
        Loaded l = read(hero(skill("\"form\": \"melee\", \"cooldown\": 2, \"charges\": 2, \"effects\": [{\"type\":"
                + " \"damage\", \"amount\": 6}, {\"type\": \"stun\", \"seconds\": 1}]")));
        Ability a = l.hero("test_hero").skills().get(0);
        assertEquals(10, a.numbers().weight(), EPS);
        assertEquals(10.5, a.cooldown(), EPS, "(2 + 10/2) x 1.5");
        Problems.Problem floor = one(l, Code.COOLDOWN_FLOOR);
        assertEquals("2 seconds is too short for a skill this strong (weight 10). Raised to 10.5 seconds, the shortest"
                + " allowed for it", floor.message());
        assertEquals("skill 1 \"S\" > cooldown", floor.path(), "at the exact field (v1 26.3's example)");
        assertEquals("skills[0].cooldown", floor.machinePath());
        Loaded leap = read(hero(skill("\"form\": \"leap\", \"cooldown\": 2, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 2}]")));
        assertEquals(2 * 1.5 + 2, leap.hero("test_hero").skills().get(0).numbers().weight(), EPS, "+2 for a leap");
        assertEquals(1.05, HeroBalance.upToTick(1.0001), EPS, "floors round up to the next tick");
        assertEquals(6.4, HeroBalance.upToTick(6.4), EPS);
    }

    @Test
    void passiveFloorsAndEveryPeriods() {
        assertEquals(1, HeroBalance.passiveFloor(1, 100, Trigger.ON_HIT), EPS, "never under 1 s");
        assertEquals(4, HeroBalance.passiveFloor(8, 100, Trigger.ON_HIT), EPS);
        assertEquals(2, HeroBalance.passiveFloor(8, 50, Trigger.ON_HIT), EPS, "the passive's chance halves it");
        assertEquals(2, HeroBalance.passiveFloor(1, 100, Trigger.ON_ALLY_ATTACK), EPS, "joining an attack: 2 s");
        Loaded l = read(hero("\"passives\": [{\"name\": \"Burst\", \"trigger\": \"every\", \"trigger_value\": 3,"
                + " \"form\": \"nova\", \"effects\": [{\"type\": \"damage\", \"amount\": 8}]}, {\"name\": \"Jab\","
                + " \"trigger\": \"on_hit\", \"cooldown\": 1, \"effects\": [{\"type\": \"damage\", \"amount\": 6}]}]"));
        HeroDef d = l.hero("test_hero");
        assertEquals(6, d.passives().get(0).triggerValue(), EPS, "\"8 damage to every enemy around me every 3 s\""
                + " becomes every 6 s");
        assertEquals(3, d.passives().get(1).cooldown(), EPS);
        one(l, Code.EVERY_FLOOR);
        one(l, Code.PASSIVE_FLOOR);
    }

    // ------------------------------------------------------------------ slot caps (v1 16.1)

    @Test
    void eachSlotCapScalesTheAbilityDown() {
        for (Slot slot : Slot.values()) {
            HeroCatalogue.SlotCaps caps = HeroCatalogue.CAPS.get(slot);
            List<Effect> fx = List.of(new HeroDef.Damage(T, Amount.of(50), HeroDef.Element.PHYSICAL, 0),
                    new HeroDef.Dot(T, Amount.of(10), 10, HeroDef.Element.FIRE, 1),
                    new HeroDef.Heal(new Common(To.SELF, 8, 100, null), Amount.of(50)),
                    new HeroDef.Shield(new Common(To.SELF, 8, 100, null), Amount.of(50), 10),
                    new HeroDef.Stun(T, 2));
            List<String> told = new ArrayList<>();
            List<Effect> out = HeroBalance.capSlot(fx, slot, 2, 1, REF, "where", null, (w, f, c, m) -> told.add(m),
                    new IdentityHashMap<>());
            HeroBalance.Totals t = HeroBalance.totals(out, 2, 1, REF);
            assertEquals(caps.damage(), t.damage(), 1e-6, slot.id());
            assertEquals(caps.dot(), t.dot(), 1e-6, slot.id());
            assertEquals(caps.heal(), t.heal(), 1e-6, slot.id());
            assertEquals(caps.shield(), t.shield(), 1e-6, slot.id());
            assertEquals(caps.stun(), t.stun(), 1e-6, slot.id());
            assertEquals(5, told.size(), slot.id());
        }
        List<Effect> under = List.of(new HeroDef.Damage(T, Amount.of(4), HeroDef.Element.PHYSICAL, 0));
        List<Effect> same = HeroBalance.capSlot(under, Slot.SKILL, 1, 1, REF, "x", null, (w, f, c, m) -> {
            throw new AssertionError(m);
        }, new IdentityHashMap<>());
        assertSame(under.get(0), same.get(0), "under the cap nothing changes");
    }

    @Test
    void aLastingZoneMayDealHalfAsMuchAgain() {
        List<String> told = new ArrayList<>();
        List<Effect> fx = List.of(new HeroDef.Damage(T, Amount.of(5), HeroDef.Element.PHYSICAL, 0),
                new HeroDef.Heal(new Common(To.ALLIES, 8, 100, null), Amount.of(3)));
        List<Effect> out = HeroBalance.capSlot(fx, Slot.SKILL, 1, 5, REF, "zone", null, (w, f, c, m) -> told.add(m),
                new IdentityHashMap<>());
        assertEquals(24, HeroBalance.totals(out, 1, 5, REF).damage(), 1e-6, "1.5 x 16 over all pulses");
        assertEquals(12, HeroBalance.totals(out, 1, 5, REF).heal(), 1e-6, "1.5 x 8");
        assertEquals("deals up to 25 health points at level 1; the cap for a lasting zone is 24 (1.5 x the skill cap"
                + " of 16). Scaled down to 24", told.get(0));
        Loaded l = read(hero(skill("\"form\": \"zone\", \"duration\": 4, \"interval\": 1, \"cooldown\": 60,"
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 5}]")));
        Ability z = l.hero("test_hero").skills().get(0);
        assertEquals(5, z.pulses());
        assertEquals(24 / 5.0, ((HeroDef.Damage) z.effects().get(0)).amount().base(), 1e-6);
        one(l, Code.SLOT_CAP);
    }

    @Test
    void theSkillCapMessageAndProportionalScaling() {
        Loaded l = read(hero(skill("\"form\": \"melee\", \"hits\": 2, \"cooldown\": 60, \"effects\": [{\"type\":"
                + " \"damage\", \"amount\": {\"base\": 6, \"attack_percent\": 100}}, {\"type\": \"damage\", \"amount\": 4}]")));
        Ability a = l.hero("test_hero").skills().get(0);
        HeroDef.Amount first = ((HeroDef.Damage) a.effects().get(0)).amount();
        double f = 16 / 36.0; // (6 + a striker's 8 attack power + 4) x 2 hits
        assertEquals(6 * f, first.base(), 1e-6, "every part of that kind, in proportion");
        assertEquals(100 * f, first.attackPercent(), 1e-6);
        assertEquals(4 * f, ((HeroDef.Damage) a.effects().get(1)).amount().base(), 1e-6);
        assertEquals("deals up to 36 health points at level 1; the skill cap is 16. Scaled down to 16",
                one(l, Code.SLOT_CAP).message());
    }

    @Test
    void basicAndEmpoweredOnHitShareOneCap() {
        Loaded l = read(hero("\"basic_attack\": {\"style\": \"melee\", \"on_hit\": [{\"type\": \"damage\", \"amount\":"
                + " 3}]}, \"empowered\": {\"name\": \"E\", \"on_hit\": [{\"type\": \"damage\", \"amount\": 3}]}")
                .replace("\"basic_attack\": {\"style\": \"melee\"}, ", ""));
        HeroDef d = l.hero("test_hero");
        assertEquals(3, ((HeroDef.Damage) d.basicAttack().onHit().get(0)).amount().base(), EPS, "the basic one keeps");
        assertEquals(1, ((HeroDef.Damage) d.empowered().onHit().get(0)).amount().base(), EPS, "4 - 3 left");
        assertEquals("while empowered, the basic attack's on_hit and the empowered on_hit add up to 6 health points of"
                + " damage; they share the on-hit cap of 4. The empowered on_hit was scaled down to 1",
                one(l, Code.SHARED_ON_HIT).message());
        Loaded fine = read(hero("\"empowered\": {\"name\": \"E\", \"on_hit\": [{\"type\": \"damage\", \"amount\": 3}]}"));
        assertTrue(of(fine, Code.SHARED_ON_HIT).isEmpty());
    }

    // ------------------------------------------------------------------ the other caps (v1 16.2, B.1)

    @Test
    void potionAndStatCaps() {
        List<String> told = new ArrayList<>();
        HeroBalance.Sink sink = (w, f, c, m) -> told.add(c.id() + " at " + f + ": " + m);
        HeroDef.PotionEffect slow = (HeroDef.PotionEffect) HeroBalance.capOne(new HeroDef.PotionEffect(T, "slowness",
                3, 12, false), false, sink, "x");
        assertEquals(3, slow.level());
        assertEquals(8, slow.seconds(), EPS);
        assertEquals(List.of("potion_seconds at seconds: \"slowness\" on enemies lasts at most 8 seconds. Set to 8"),
                told);
        for (Map.Entry<String, HeroCatalogue.PotionCap> e : HeroCatalogue.HELPFUL_POTIONS.entrySet()) {
            HeroDef.PotionEffect p = (HeroDef.PotionEffect) HeroBalance.capOne(new HeroDef.PotionEffect(T, e.getKey(), 3,
                    30, true), false, (w, f, c, m) -> { }, "x");
            assertEquals(e.getValue().maxLevel(), p.level(), e.getKey());
            assertEquals(e.getValue().maxSeconds(), p.seconds(), EPS, e.getKey());
        }
        told.clear();
        HeroDef.StatMod s = (HeroDef.StatMod) HeroBalance.capOne(new HeroDef.StatMod(T, HeroDef.Stat.ATTACK, 60, 8, 1),
                false, sink, "x");
        assertEquals(40, s.percent());
        assertEquals("stat_cap at percent: \"attack\" +60% is outside -30% to +40%. Set to +40%", told.get(0));
        assertEquals(20, ((HeroDef.StatMod) HeroBalance.capOne(new HeroDef.StatMod(T, HeroDef.Stat.ARMOR, 45, 0, 1),
                true, sink, "x")).percent(), "always passives: +-20%");
        assertEquals(0, HeroBalance.statRange(HeroDef.Stat.KNOCKBACK_RESISTANCE, 20).min(), EPS);
        assertEquals(20, HeroBalance.statRange(HeroDef.Stat.KNOCKBACK_RESISTANCE, 20).max(), EPS);
        HeroDef.Summon g = (HeroDef.Summon) HeroBalance.capOne(new HeroDef.Summon(T, HeroDef.Creature.SNOW_GOLEM, 3, 20,
                null), false, sink, "x");
        assertEquals(2, g.count());
        Loaded emp = read(hero("\"empowered\": {\"name\": \"E\", \"stats\": [{\"stat\": \"knockback_resistance\","
                + " \"percent\": -20}]}"));
        assertEquals(0, emp.hero("test_hero").empowered().stats().get(0).percent(), "no negative knockback resistance");
        one(emp, Code.STAT_CAP);
    }

    @Test
    void capsComeBeforeFloors() {
        // a weakness held to level 2 for 8 s weighs what the hero will really cast (v1 26.1 step 4's order)
        Loaded l = read(hero(skill("\"form\": \"bolt\", \"cooldown\": 10, \"effects\": [{\"type\": \"damage\","
                + " \"amount\": 8}, {\"type\": \"effect\", \"id\": \"weakness\", \"level\": 3, \"seconds\": 30}]")));
        Ability a = l.hero("test_hero").skills().get(0);
        assertEquals(8 + 2 * 8 / 4.0, a.numbers().weight(), EPS);
        assertEquals(10, a.cooldown(), EPS, "floor 8, the given 10 stands");
        assertEquals(2, of(l, Code.POTION_LEVEL).size() + of(l, Code.POTION_SECONDS).size());
    }

    @Test
    void theThrownRateCap() {
        Loaded l = read(hero("\"basic_attack\": {\"style\": \"thrown\", \"amount\": 10, \"interval\": 0.8}")
                .replace("\"basic_attack\": {\"style\": \"melee\"}, ", "").replace("\"gear\": [\"iron_sword\"]",
                        "\"gear\": []"));
        assertEquals(2, l.hero("test_hero").basicAttack().interval(), EPS);
        assertEquals("10 damage every 0.8 seconds is faster than a bow (at most 5 damage per second); the interval was"
                + " raised to 2 seconds", one(l, Code.THROWN_RATE).message());
        Loaded ok = read(hero("\"basic_attack\": {\"style\": \"thrown\", \"amount\": 5, \"interval\": 1}")
                .replace("\"basic_attack\": {\"style\": \"melee\"}, ", ""));
        assertTrue(of(ok, Code.THROWN_RATE).isEmpty(), "exactly 5 per second is allowed");
    }

    @Test
    void thePowerScale() {
        assertEquals(25, HeroBalance.clampPowerScale(5));
        assertEquals(200, HeroBalance.clampPowerScale(900));
        assertEquals(100, HeroBalance.clampPowerScale(100));
        assertEquals(8, HeroBalance.powerScaled(8, 100), EPS);
        assertEquals(12, HeroBalance.powerScaled(8, 150), EPS);
        assertEquals(2, HeroBalance.powerScaled(8, 10), EPS, "at least 25%");
    }
}
