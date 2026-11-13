package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Growth;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Rarity;
import com.factionclash.logic.hero.HeroRules.HitContext;
import com.factionclash.logic.hero.HeroRules.Nature;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HERO_FORMAT.md 31.1 {@code HeroRulesTest}, plus the revival rules of HEROES_V2 3.4. */
class HeroRulesTest {
    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------ the damage formula (v1 6.3)

    @Test
    void theFourFactorsAreClampedAndTheirProductAtMostDoubles() {
        assertEquals(1, HeroRules.damageFactor(0, 1, 0, 0), EPS);
        assertEquals(1.8, HeroRules.damageFactor(200, 1, 0, 0), EPS, "damage_dealt at most +80%");
        assertEquals(0.5, HeroRules.damageFactor(-90, 1, 0, 0), EPS, "at least -50%");
        assertEquals(0.4, HeroRules.damageFactor(0, 1, 90, 0), EPS, "a resistance at most 60%");
        assertEquals(1.3, HeroRules.damageFactor(0, 1, -50, 0), EPS, "a weakness at most 30%");
        assertEquals(1.5, HeroRules.damageFactor(0, 1, 0, 99), EPS, "damage_taken at most +50%");
        assertEquals(0.4, HeroRules.damageFactor(0, 1, 0, -70), EPS, "at least -60%");
        assertEquals(2.0, HeroRules.damageFactor(80, 1.3, -30, 50), EPS, "1.8 x 1.3 x 1.3 x 1.5 = 4.56, clamped");
        assertEquals(0, HeroRules.damageFactor(80, 0, 0, 50), EPS, "immune stays immune");
        assertEquals(1.2 * 1.3 * 0.9 * 1.1, HeroRules.damageFactor(20, 1.3, 10, 10), EPS);
    }

    @Test
    void elementsAgainstWhatTheTargetIs() {
        Set<Nature> none = EnumSet.noneOf(Nature.class);
        for (Element e : Element.values()) {
            assertEquals(1, HeroRules.elementFactor(e, none), EPS, e.id());
        }
        assertEquals(0, HeroRules.elementFactor(Element.FIRE, EnumSet.of(Nature.FIRE_IMMUNE)), EPS);
        assertEquals(1.3, HeroRules.elementFactor(Element.FIRE, EnumSet.of(Nature.SNOW_GOLEM)), EPS);
        assertEquals(0, HeroRules.elementFactor(Element.FROST, EnumSet.of(Nature.FREEZE_IMMUNE)), EPS);
        assertEquals(1.3, HeroRules.elementFactor(Element.FROST, EnumSet.of(Nature.FREEZE_HURTS_EXTRA)), EPS);
        assertEquals(1.3, HeroRules.elementFactor(Element.SHOCK, EnumSet.of(Nature.WET)), EPS);
        assertEquals(0, HeroRules.elementFactor(Element.VENOM, EnumSet.of(Nature.UNDEAD)), EPS, "undead ignore poison");
        assertEquals(1.3, HeroRules.elementFactor(Element.RADIANT, EnumSet.of(Nature.UNDEAD)), EPS);
        assertEquals(0, HeroRules.elementFactor(Element.BLIGHT, EnumSet.of(Nature.WITHER_IMMUNE)), EPS);
        assertEquals(1, HeroRules.elementFactor(Element.ARCANE, EnumSet.allOf(Nature.class)), EPS,
                "arcane: vanilla's own witch rule, no factor of ours");
        assertEquals(1, HeroRules.elementFactor(Element.PHYSICAL, EnumSet.allOf(Nature.class)), EPS);
    }

    @Test
    void anAmountAtHitTimeUsesAtMostHalfAgainTheReferenceGear() {
        Amount a = new Amount(1, 100, 10, 20, 5, 10, "toll", 2);
        HitContext c = new HitContext(8, 30, 10, 40, 12, 3, false, 7, 19);
        assertEquals(1 + 8 + 3 + 2 + 2 + 1.2 + 6, HeroRules.amountAtHit(a, c), EPS);
        HitContext geared = new HitContext(40, 30, 50, 40, 12, 3, false, 7, 19);
        assertEquals(1 + 10.5 + 3 + 0.2 * 28.5 + 2 + 1.2 + 6, HeroRules.amountAtHit(a, geared), EPS,
                "netherite and Strength II: attack at most 1.5 x 7, armour at most 1.5 x 19");
        HitContext boss = new HitContext(8, 30, 10, 300, 0, 0, true, 7, 19);
        assertEquals(0.02 * 300, HeroRules.amountAtHit(new Amount(0, 0, 0, 0, 20, 0, null, 0), boss), EPS,
                "against a boss, a max-health share counts at most 2%");
        assertEquals(0, HeroRules.amountAtHit(Amount.of(0), c), EPS);
    }

    @Test
    void rawDealtAndTheHitCap() {
        assertEquals(10, HeroRules.raw(10, Growth.BALANCED, 1, 100), EPS, "level 1: no ability power");
        assertEquals(10 * 1.29 * 1.5, HeroRules.raw(10, Growth.BALANCED, 30, 150), 1e-6);
        assertEquals(40, HeroRules.dealt(30, 2.0, HeroRules.MAX_HIT_DEFAULT), EPS, "one hit at most heroes.maxHit");
        assertEquals(15, HeroRules.dealt(10, 1.5, 40), EPS);
        assertEquals(0, HeroRules.dealt(10, 0, 40), EPS);
        assertEquals(1.5, HeroRules.drain(20, 17, 50), EPS, "half of the 3 really dealt");
        assertEquals(0, HeroRules.drain(20, 22, 50), EPS);
        assertEquals(6, HeroRules.dotTick(List.of(4.0, 3.0), HeroRules.MAX_DOT_PER_SECOND), EPS);
        assertEquals(5, HeroRules.dotTick(List.of(2.0, 3.0, -1.0), 6), EPS);
        assertEquals(12, HeroRules.shieldAfter(0, 15, 20), EPS, "at most 60% of max health");
        assertEquals(8, HeroRules.shieldAfter(8, 5, 20), EPS, "a weaker shield never replaces a stronger one");
        assertEquals(9, HeroRules.shieldAfter(8, 9, 20), EPS, "they never add up");
    }

    // ------------------------------------------------------------------ energy and cooldowns (v1 14, 13.5)

    @Test
    void energyGainsAndTheChargeRate() {
        assertEquals(5, HeroRules.energyGain(HeroRules.ENERGY_BASIC_HIT, 100, 0));
        assertEquals(6, HeroRules.energyGain(5, 110, 0), "Bram's 110% charge rate: 5.5 rounds to 6");
        assertEquals(15, HeroRules.energyGain(10, 100, 50), "the energy_gain stat");
        assertEquals(8, HeroRules.energyGain(10, 50, 50), "7.5 rounds to 8");
        assertEquals(0, HeroRules.energyGain(-3, 100, 0));
        assertEquals(3, HeroRules.energyFromDamage(6), EPS, "1 per 2 health points lost");
        assertEquals(8, HeroRules.energyFromDamage(40), EPS, "at most 8 per hit");
        assertEquals(8, HeroRules.energyFromHealing(30), EPS);
        assertEquals(100, HeroRules.addEnergy(95, 20));
        assertEquals(0, HeroRules.addEnergy(5, -20));
        assertTrue(HeroRules.ultimateReady(100, 1000, 0, 27));
        assertFalse(HeroRules.ultimateReady(99, 1000, 0, 27));
        assertFalse(HeroRules.ultimateReady(100, 1000 + 539, 1000, 27), "27 s = 540 ticks after the last");
        assertTrue(HeroRules.ultimateReady(100, 1000 + 540, 1000, 27));
    }

    @Test
    void aCooldownCutNeverMoreThanHalvesACooldown() {
        // a 10 s (200 tick) cooldown used at tick 1000, ready at 1200
        assertEquals(1140, HeroRules.cutCooldown(1200, 1000, 200, 60), "3 s off");
        assertEquals(1100, HeroRules.cutCooldown(1200, 1000, 200, 160), "never earlier than half the cooldown");
        assertEquals(1100, HeroRules.cutCooldown(1150, 1000, 200, 100));
        assertEquals(1101, HeroRules.cutCooldown(1201, 1000, 201, 400), "an odd cooldown rounds the half up");
        assertEquals(1200, HeroRules.cutCooldown(1200, 1000, 200, 0));
        assertEquals(1050, HeroRules.cutCooldown(1050, 1000, 200, 30), "already sooner: never later");
        assertEquals(160, HeroRules.ticks(8));
        assertEquals(7, HeroRules.ticks(0.33));
    }

    // ------------------------------------------------------------------ growth (v1 5.3)

    @Test
    void growthAtLevelsOneFifteenAndThirty() {
        HeroRules.GrowthBonus one = HeroRules.growth(Growth.TOUGH, 1);
        assertEquals(0, one.health(), EPS);
        assertEquals(0, one.abilityPowerPercent(), EPS);
        HeroRules.GrowthBonus fifteen = HeroRules.growth(Growth.TOUGH, 15);
        assertEquals(0.8 * 14, fifteen.health(), 1e-6);
        assertEquals(0.03 * 14, fifteen.attack(), 1e-6);
        assertEquals(0.12 * 14, fifteen.armor(), 1e-6);
        assertEquals(0.5 * 14, fifteen.abilityPowerPercent(), 1e-6);
        HeroRules.GrowthBonus thirty = HeroRules.growth(Growth.SWIFT, 30);
        assertEquals(0.4 * 29, thirty.health(), 1e-6);
        assertEquals(0.3 * 29, thirty.speedPercent(), 1e-6);
        assertEquals(HeroRules.growth(Growth.SWIFT, 30), HeroRules.growth(Growth.SWIFT, 99), "level 30 at most");
        assertEquals(HeroRules.growth(Growth.SWIFT, 1), HeroRules.growth(Growth.SWIFT, -4));
        assertEquals(1, HeroRules.abilityPower(Growth.MYSTIC, 1), EPS, "every ability from level 1, at its base power");
        assertEquals(1.28, HeroRules.abilityPower(Growth.MYSTIC, 15), 1e-6);
        assertEquals(1.58, HeroRules.abilityPower(Growth.MYSTIC, 30), 1e-6);
        assertEquals(1.29, HeroRules.abilityPower(Growth.FIERCE, 30), 1e-6);
        assertEquals(1.145, HeroRules.abilityPower(Growth.TOUGH, 30), 1e-6);
        for (Growth g : Growth.values()) {
            assertTrue(HeroRules.abilityPower(g, 20) > HeroRules.abilityPower(g, 10), g.id() + " grows with level");
        }
    }

    @Test
    void aHerosOwnNumbersAtALevel() throws java.io.IOException {
        HeroDef bram = HeroReader.read("bram_ostler.json", java.nio.file.Files.readString(java.nio.file.Path.of(
                "docs/heroes/examples/bram_ostler.json"))).hero("bram_ostler");
        HeroRules.LevelStats one = HeroRules.statsAt(bram.stats(), 1);
        assertEquals(new HeroRules.LevelStats(36, 1, 4, 95, 1), one, "level 1: what the file says");
        HeroRules.LevelStats thirty = HeroRules.statsAt(bram.stats(), 30);
        assertEquals(36 + 0.8 * 29, thirty.health(), 1e-9, "tough: +0.8 health a level");
        assertEquals(1 + 0.03 * 29, thirty.attack(), 1e-9);
        assertEquals(4 + 0.12 * 29, thirty.armor(), 1e-9);
        assertEquals(95, thirty.speedPercent(), 1e-9, "only swift heroes get faster");
        assertEquals(1.145, thirty.abilityPower(), 1e-9);
        HeroRules.LevelStats swift = HeroRules.statsAt(new HeroDef.Stats(26, 2, 0, 108, 0, 100, Growth.SWIFT), 30);
        assertEquals(108 + 0.3 * 29, swift.speedPercent(), 1e-9);
        // v1 26.4's example line: "Shield Slam: 5.8 damage ...; at level 30: 6.6 damage"
        HeroDef.Damage slam = (HeroDef.Damage) bram.skills().get(0).effects().get(0);
        double reference = HeroBalance.reference(slam.amount(), HeroBalance.ref(bram));
        assertEquals("5.8", Problems.num1(reference));
        assertEquals("6.6", Problems.num1(HeroRules.referenceAt(reference, bram.stats().growth(), 30, 100)));
        assertEquals("5.8", Problems.num1(HeroRules.referenceAt(reference, bram.stats().growth(), 1, 100)));
        assertEquals(reference * 1.145 * 0.5, HeroRules.referenceAt(reference, bram.stats().growth(), 30, 50), 1e-9,
                "heroes.powerScale tunes every hero at once");
    }

    // ------------------------------------------------------------------ XP (v1 22)

    @Test
    void combatXp() {
        assertEquals(20, HeroRules.killXp(20), EPS, "a zombie");
        assertEquals(10, HeroRules.killXp(0), EPS);
        assertEquals(160, HeroRules.killXp(300), EPS, "the wither");
        assertEquals(2, HeroRules.healXp(10), EPS, "1 per 5 points");
        assertEquals(20, HeroRules.xp(20, 100));
        assertEquals(30, HeroRules.xp(20, 150));
        assertEquals(0, HeroRules.xp(20, 0));
        assertEquals(100, HeroRules.xp(20, 9999), "xpRate at most 500%");
        assertEquals(5, HeroRules.XP_ULTIMATE);
        assertEquals(100, HeroRules.XP_RAID_WON);
        assertEquals(30, HeroRules.XP_DEFENCE);
        assertEquals(40, HeroRules.XP_DAY_ALIVE);
        assertEquals((40 + 30 * 5) + (40 + 30 * 6), HeroRules.duplicateTokenXp(5), "two levels at level 5");
    }

    // ------------------------------------------------------------------ dialogue (v1 23)

    @Test
    void oneLinePerHeroPerCooldownExceptTheBigMoments() {
        long now = 100_000;
        long last = now - 300; // 15 s ago
        assertFalse(HeroRules.maySpeak(LineEvent.KILL, now, last, 0, 0, 20));
        assertTrue(HeroRules.maySpeak(LineEvent.KILL, now, now - 400, 0, 0, 20), "20 s later");
        assertTrue(HeroRules.maySpeak(LineEvent.KILL, now, 0, 0, 0, 20), "never spoke");
        for (LineEvent e : List.of(LineEvent.SUMMONED, LineEvent.FALLEN, LineEvent.REVIVED, LineEvent.ULTIMATE)) {
            assertTrue(HeroRules.maySpeak(e, now, now - 1, now - 1, now - 1, 20), e.id());
        }
        assertTrue(HeroRules.maySpeak(LineEvent.GREETING, now, now - 1, now - 100, 0, 20), "greetings wait 5 s");
        assertFalse(HeroRules.maySpeak(LineEvent.GREETING, now, 0, now - 99, 0, 20));
        assertFalse(HeroRules.maySpeak(LineEvent.IDLE, now, 0, 0, now - 15 * 60 * 20 + 1, 20), "idle every 15 min");
        assertTrue(HeroRules.maySpeak(LineEvent.IDLE, now, 0, 0, now - 15 * 60 * 20, 20));
        assertFalse(HeroRules.maySpeak(LineEvent.IDLE, now, now - 10, 0, 0, 20), "and the cooldown");
        assertTrue(HeroRules.lineRoll(LineEvent.FIGHT_START, 49.9));
        assertFalse(HeroRules.lineRoll(LineEvent.FIGHT_START, 50));
        assertFalse(HeroRules.lineRoll(LineEvent.SKILL, 25));
        assertTrue(HeroRules.lineRoll(LineEvent.KILL, 29));
        assertTrue(HeroRules.lineRoll(LineEvent.VICTORY, 99.9));
    }

    // ------------------------------------------------------------------ rarity draws (v1 25.3, HEROES_V2 5.2)

    @Test
    void seededRarityDraws() {
        List<Rarity> pool = List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY);
        assertEquals(HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, 42L),
                HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, 42L), "the same seed, the same hero");
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (long seed = 0; seed < 15_000; seed++) {
            counts.merge(pool.get(HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, seed)), 1, Integer::sum);
        }
        assertEquals(8000, counts.get(Rarity.COMMON), 400, "8 of 15");
        assertEquals(4000, counts.get(Rarity.RARE), 300);
        assertEquals(2000, counts.get(Rarity.EPIC), 250);
        assertEquals(1000, counts.get(Rarity.LEGENDARY), 200);
        assertEquals(0, HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, 0.0));
        assertEquals(1, HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, 8 / 15.0), "the boundary goes to the next");
        assertEquals(3, HeroRules.drawIndex(pool, HeroRules.TOKEN_WEIGHTS, 0.99999));
        assertEquals(-1, HeroRules.drawIndex(List.of(), HeroRules.TOKEN_WEIGHTS, 0.5));
        Map<Rarity, Integer> onlyEpic = new EnumMap<>(Rarity.class);
        onlyEpic.put(Rarity.EPIC, 1);
        assertEquals(2, HeroRules.drawIndex(pool, onlyEpic, 0.0), "a token of one rarity draws only that rarity");
        assertEquals(2, HeroRules.drawIndex(pool, onlyEpic, 0.999));
        assertEquals(-1, HeroRules.drawIndex(List.of(Rarity.COMMON), onlyEpic, 0.5));
        assertEquals(4, HeroRules.LEADER_WEIGHTS.get(Rarity.COMMON));
        assertEquals(4, HeroRules.LEADER_WEIGHTS.get(Rarity.RARE));
        assertFalse(HeroRules.mayBeRandomStarter(Rarity.LEGENDARY));
        assertTrue(HeroRules.mayBeRandomStarter(Rarity.EPIC));
    }

    // ------------------------------------------------------------------ revival (HEROES_V2 3.4)

    @Test
    void everyHeroReturnsAfterTenMinutesUnlessADeathAbilitySaysOtherwise() {
        assertEquals(600, HeroRules.REVIVE_SECONDS, "the owner's ten minutes");
        assertEquals(600, HeroRules.reviveDelaySeconds(600, 0, 0), "leaders included: no other timer");
        assertEquals(480, HeroRules.reviveDelaySeconds(600, 0, 1), "revival_rite I: 20% sooner");
        assertEquals(420, HeroRules.reviveDelaySeconds(600, 0, 2));
        assertEquals(360, HeroRules.reviveDelaySeconds(600, 0, 3), "III: 40% sooner");
        assertEquals(360, HeroRules.reviveDelaySeconds(600, 0, 9), "tiers above III count as III");
        assertEquals(120, HeroRules.reviveDelaySeconds(600, 120, 0), "its own revival block comes first");
        assertEquals(72, HeroRules.reviveDelaySeconds(600, 120, 3), "and the talent shortens it");
        assertEquals(1800, HeroRules.reviveDelaySeconds(600, 1800, 0), "a longer own delay too");
        assertEquals(30, HeroRules.reviveDelaySeconds(600, 30, 3), "never under 30 s");
        assertEquals(30, HeroRules.reviveDelaySeconds(40, 0, 3));
        assertEquals(1000 + 360 * 20, HeroRevival.reviveAt(1000, HeroRules.reviveDelaySeconds(600, 0, 3)),
                "the due game time");
        assertEquals(1000 + 600 * 20, HeroRules.reviveAt(1000, 600, 0, 0), "ten minutes are 12000 game ticks");
        assertEquals(1000 + 72 * 20, HeroRules.reviveAt(1000, 600, 120, 3));
        assertEquals(1000 + 30 * 20, HeroRules.reviveAt(1000, 600, 1, 3), "the floor, whatever the abilities say");
        assertEquals(30, HeroRules.MIN_REVIVE_SECONDS);
        assertEquals(3600, HeroRules.reviveDelaySeconds(600, 99999, 0), "an own delay is an hour at most");
        assertEquals(600, HeroRules.reviveDelaySeconds(600, -5, 0), "no own delay: the config's");
        assertEquals(600, HeroRules.reviveDelaySeconds(600, 0, -2), "no talent: nothing shortened");
        assertEquals(30, HeroRules.reviveDelaySeconds(0, 0, 0), "a broken config value still gives the floor");
        assertTrue(HeroRevival.due(1000 + 12000, HeroRules.reviveAt(1000, 600, 0, 0)));
        assertFalse(HeroRevival.due(1000 + 11999, HeroRules.reviveAt(1000, 600, 0, 0)));
        assertEquals(1, HeroRules.talentTier(1));
        assertEquals(1, HeroRules.talentTier(9));
        assertEquals(2, HeroRules.talentTier(10));
        assertEquals(3, HeroRules.talentTier(20));
        assertEquals(3, HeroRules.talentTier(30));
    }

    @Test
    void aHeroThatRisesAtNightWaitsForNightfall() {
        assertTrue(HeroRules.isNight(13000));
        assertTrue(HeroRules.isNight(22999));
        assertFalse(HeroRules.isNight(23000));
        assertFalse(HeroRules.isNight(6000));
        assertTrue(HeroRules.isNight(24000 * 5 + 18000), "any day");
        assertTrue(HeroRules.revivalDue(5000, 5000, false, 6000), "by day without at: night");
        assertFalse(HeroRules.revivalDue(5000, 5000, true, 6000), "at: night waits");
        assertTrue(HeroRules.revivalDue(5000, 5000, true, 14000));
        assertFalse(HeroRules.revivalDue(4999, 5000, true, 14000), "not before its delay");
        assertFalse(HeroRules.revivalDue(5000, 0, false, 14000), "nothing due");
        assertEquals(7000, HeroRules.ticksToNightfall(6000));
        assertEquals(0, HeroRules.ticksToNightfall(15000));
        assertEquals(14000, HeroRules.ticksToNightfall(23000), "dawn: next evening");
        assertEquals(1000, HeroRules.ticksToNightfall(-12000), "negative day times wrap");
    }

    // ------------------------------------------------------------------ rewards at a death (HEROES_V2 6.13)

    @Test
    void rewardsGoToTheSideOfTheLastPersonWhoHurtIt() {
        assertEquals(7, HeroRules.rewardXp(1));
        assertEquals(70, HeroRules.rewardXp(10));
        assertEquals(98, HeroRules.rewardXp(14));
        assertEquals(100, HeroRules.rewardXp(15), "at most 100");
        assertEquals(100, HeroRules.rewardXp(30));
        assertEquals(7, HeroRules.rewardXp(-3));
        HeroRules.Side ember = HeroRules.Side.faction("ember_legion");
        HeroRules.Side grove = HeroRules.Side.faction("grove_covenant");
        HeroRules.Side loner = HeroRules.Side.loner("1234");
        // a member player and an NPC soldier of the same faction are one side
        assertEquals(HeroRules.Side.faction("ember_legion"), ember);
        assertEquals(grove, HeroRules.winningSide(List.of(new HeroRules.Blow(100, ember),
                new HeroRules.Blow(200, grove), new HeroRules.Blow(250, null)), 260), "a monster's blow after it"
                + " counts for the last person's side");
        assertEquals(loner, HeroRules.winningSide(List.of(new HeroRules.Blow(100, ember),
                new HeroRules.Blow(150, loner)), 200), "a factionless player is a side of one");
        assertEquals(null, HeroRules.winningSide(List.of(new HeroRules.Blow(100, ember)), 400),
                "no person within 15 s: no orbs, no crest");
        assertEquals(ember, HeroRules.winningSide(List.of(new HeroRules.Blow(100, ember)), 399));
        assertEquals(null, HeroRules.winningSide(List.of(new HeroRules.Blow(100, null)), 110), "lava alone");
        assertEquals(null, HeroRules.winningSide(List.of(), 110));
        assertEquals(grove, HeroRules.winningSide(List.of(new HeroRules.Blow(100, ember),
                new HeroRules.Blow(100, grove)), 110), "the same tick: the later record");
        // one crest per hero per winning side per in-game day
        assertEquals(HeroRules.crestKey("benika_akagane", ember, 100), HeroRules.crestKey("benika_akagane", ember,
                23_999));
        assertFalse(HeroRules.crestKey("benika_akagane", ember, 100).equals(HeroRules.crestKey("benika_akagane", ember,
                24_000)));
        assertFalse(HeroRules.crestKey("benika_akagane", ember, 100).equals(HeroRules.crestKey("benika_akagane", grove,
                100)));
    }

    // ------------------------------------------------------------------ war power: a hook (HEROES_V2 3.2, 8)

    /**
     * The design is recomputing what a hero weighs in the war council now that it has its boss health in NPC wars.
     * Until that formula lands, {@link HeroRules#heroPower} is v1 25.7's placeholder and says so. Whoever replaces it
     * rewrites this test with the new numbers and sets the flag to false.
     */
    @Test
    void warPowerIsAHookUntilTheDesignLands() {
        assertTrue(HeroRules.WAR_POWER_IS_PROVISIONAL, "the new war power is in: rewrite this test for it");
        assertEquals(5.25, HeroRules.heroPower(2.25, 20), EPS, "v1: a level-20 hero in full iron");
        assertEquals(3.35, HeroRules.heroPower(2.25, 1), 1e-9);
        assertEquals(6.25, HeroRules.heroPower(2.25, 99), 1e-9, "level 30 at most");
        assertEquals(2, HeroRules.HERO_DEFENDERS);
    }

    // ------------------------------------------------------------------ equal treatment (HEROES_V2 6)

    @Test
    void noRuleAsksWhetherSomeoneIsAPlayer() {
        // the owner's rule: "player only features interactions to be removed, regardless if its good or bad for the
        // player". The arithmetic has no input that could tell a player from an NPC, so it cannot treat one specially.
        for (java.lang.reflect.RecordComponent c : HitContext.class.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("player"), c.getName());
        }
        for (java.lang.reflect.Method m : HeroRules.class.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().contains("player"), m.getName());
        }
        for (java.lang.reflect.Field f : HeroRules.class.getDeclaredFields()) {
            assertFalse(f.getName().toLowerCase().contains("player"), f.getName());
        }
        // one hit, whoever it lands on: the same formula, the same clamps, the same heroes.maxHit
        double raw = HeroRules.raw(HeroRules.amountAtHit(new Amount(4, 100, 0, 0, 0, 0, null, 0),
                new HitContext(9, 30, 10, 20, 0, 0, false, 7, 19)), Growth.FIERCE, 20, 100);
        assertEquals(13 * 1.19, raw, 1e-9);
        assertEquals(13 * 1.19, HeroRules.dealt(raw, HeroRules.damageFactor(0, 1, 0, 0), 40), 1e-9,
                "no multiplier against anyone");
        // a hero has boss health against everyone, so a max-health share counts 2% against it as against the wither
        Amount share = new Amount(0, 0, 0, 0, 20, 0, null, 0);
        assertEquals(0.02 * 165, HeroRules.amountAtHit(share, new HitContext(9, 30, 10, 165, 0, 0, true, 7, 19)), EPS);
        assertEquals(0.20 * 20, HeroRules.amountAtHit(share, new HitContext(9, 30, 10, 20, 0, 0, false, 7, 19)), EPS,
                "a soldier or a player with 20 health alike");
        assertEquals(10, HeroRules.clampMaxHit(3), EPS, "heroes.maxHit stays within 10-100");
        assertEquals(100, HeroRules.clampMaxHit(500), EPS);
        assertEquals(40, HeroRules.clampMaxHit(HeroRules.MAX_HIT_DEFAULT), EPS);
        // the slot caps are part of the loaded definition, so they hold against any target too
        HeroDef d = HeroReaderTest.def(HeroReaderTest.hero(HeroReaderTest.skill("\"form\": \"melee\", \"cooldown\": 60,"
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 30}]")));
        assertEquals(16, ((HeroDef.Damage) d.skills().get(0).effects().get(0)).amount().base(), 1e-9);
    }
}
