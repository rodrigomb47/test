package com.factionclash.logic.hero;

import com.factionclash.logic.Affix;
import com.factionclash.logic.hero.HeroDef.TalentId;
import com.factionclash.logic.hero.WorkMastery.Action;
import com.factionclash.logic.hero.WorkMastery.Bonus;
import com.factionclash.logic.hero.WorkMastery.Drop;
import com.factionclash.logic.hero.WorkMastery.Ench;
import com.factionclash.logic.hero.WorkMastery.Kind;
import com.factionclash.logic.hero.WorkMastery.Worker;
import com.factionclash.logic.leader.Talents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 9-13: proficiency, specialties, Presence, the caps and the one-or-the-other rule. */
class WorkMasteryTest {

    private static Map<Affix, Integer> affixes(Object... kv) {
        Map<Affix, Integer> m = new EnumMap<>(Affix.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Affix) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static Worker hero(int level, String duty, TalentId talent, Affix aura, Map<Affix, Integer> a) {
        return Worker.hero(level, duty, talent, aura, a);
    }

    private static Worker hero(int level, String duty, TalentId talent) {
        return hero(level, duty, talent, null, Map.of());
    }

    // ------------------------------------------------------------------ workers unchanged (12.1)

    @Test
    void workersKeepTodaysNumbers() throws IOException {
        assertEquals(1.0, WorkMastery.workerSpeed(Map.of()), 1e-9);
        assertEquals(1.30, WorkMastery.workerSpeed(affixes(Affix.DILIGENCE, 5)), 1e-9);
        assertEquals(1.30, WorkMastery.workerSpeed(affixes(Affix.DILIGENCE, 9)), 1e-9, "the affix's own cap");
        assertEquals(0.68, WorkMastery.workerWear(affixes(Affix.THRIFTY, 4)), 1e-9);
        assertEquals(0.68, WorkMastery.workerHunger(affixes(Affix.HARDY, 4)), 1e-9);
        assertEquals(18, WorkMastery.WORKER_BACKPACK);
        // the sources those numbers come from today
        String npc = Files.readString(Path.of("src/main/java/com/factionclash/npc/FactionNpc.java"));
        String block = Files.readString(Path.of("src/main/java/com/factionclash/work/BlockWork.java"));
        assertTrue(npc.contains("new SimpleContainer(" + WorkMastery.WORKER_BACKPACK + ")"), "the backpack");
        assertTrue(block.contains("speed *= (float) (1.0 + npc.affix(Affix.DILIGENCE));"), "the work speed");
        assertTrue(npc.contains("Math.max(0.0, 1.0 - affix(Affix.HARDY))"), "the hunger");
        assertTrue(npc.contains("double c = affix(Affix.THRIFTY);"), "the wear");
        // a worker (work.mastery off is the same) gets exactly those through every method of the API
        Map<Affix, Integer> a = affixes(Affix.DILIGENCE, 3, Affix.THRIFTY, 2, Affix.HARDY, 1, Affix.PROSPECTOR, 4);
        Worker off = new Worker(20, "miner", TalentId.SURVEYOR, null, a, false, true);
        assertEquals(1.18, WorkMastery.workSpeed(off, "miner", Action.BREAK, 0.3), 1e-9);
        assertEquals(18, WorkMastery.backpackSlots(off, "miner"));
        assertEquals(4.5, WorkMastery.workReach(off, "miner", 4.5), 1e-9);
        assertEquals(0.84, WorkMastery.wearMultiplier(off, "miner"), 1e-9);
        assertEquals(0.92, WorkMastery.hungerMultiplier(off, "miner"), 1e-9);
        assertEquals(1.0, WorkMastery.xpMultiplier(off, "miner"), 1e-9);
        assertEquals(0.20, WorkMastery.extraRollChance(off, "miner", Kind.ORE), 1e-9);
        assertEquals(0, WorkMastery.virtualLevel(off, "miner", Ench.FORTUNE, Kind.ORE, 0, false));
        assertEquals(0, WorkMastery.alarmReachBonus(new Worker(20, "guard", null, null, a, false, true)));
    }

    // ------------------------------------------------------------------ proficiency (10)

    @Test
    void proficiencyByTierAtItsDuty() {
        int[][] tiers = {{1, 1}, {9, 1}, {10, 2}, {19, 2}, {20, 3}, {30, 3}};
        for (int[] lt : tiers) {
            assertEquals(lt[1], WorkMastery.proficiency(lt[0]).tier(), "level " + lt[0]);
        }
        double[] speed = {1.15, 1.25, 1.35};
        int[] backpack = {21, 24, 27};
        double[] reach = {0.5, 1.0, 1.5};
        double[] wear = {0.9, 0.8, 0.7};
        double[] hunger = {0.9, 0.85, 0.8};
        double[] xp = {1.1, 1.2, 1.3};
        int[] alarm = {8, 12, 16};
        int[] levels = {1, 10, 20};
        for (int i = 0; i < 3; i++) {
            Worker miner = hero(levels[i], "miner", null);
            assertEquals(speed[i], WorkMastery.workSpeed(miner, "miner", Action.BREAK, 0), 1e-9);
            for (Action act : Action.values()) {
                assertEquals(speed[i], WorkMastery.workSpeed(miner, "miner", act, 0), 1e-9, "every timer: " + act);
            }
            assertEquals(backpack[i], WorkMastery.backpackSlots(miner, "miner"));
            assertEquals(4 + reach[i], WorkMastery.workReach(miner, "miner", 4), 1e-9);
            assertEquals(wear[i], WorkMastery.wearMultiplier(miner, "miner"), 1e-9);
            assertEquals(hunger[i], WorkMastery.hungerMultiplier(miner, "miner"), 1e-9);
            assertEquals(xp[i], WorkMastery.xpMultiplier(miner, "miner"), 1e-9);
            assertEquals(0, WorkMastery.alarmReachBonus(miner), "a miner answers no alarm farther");
            Worker guard = hero(levels[i], "guard", TalentId.MEND);
            assertEquals(alarm[i], WorkMastery.alarmReachBonus(guard));
            assertEquals(1.0, WorkMastery.workSpeed(guard, "guard", Action.BREAK, 0), 1e-9,
                    "guard duty: an alarm reach instead of a work speed");
        }
        // only at its duty: a miner hero hauling for a moment works as a worker would
        Worker miner = hero(20, "miner", TalentId.SURVEYOR, null, affixes(Affix.DILIGENCE, 2));
        assertEquals(1.12, WorkMastery.workSpeed(miner, "hauler", Action.SORT, 0), 1e-9);
        assertEquals(18, WorkMastery.backpackSlots(miner, "hauler"));
        assertTrue(WorkMastery.proficient(miner, "miner"));
        assertFalse(WorkMastery.proficient(miner, null));
    }

    // ------------------------------------------------------------------ specialties (11.1)

    @Test
    void everySpecialtyWorksAtTierOneAndMatchesTheRostersDuties() throws IOException {
        for (TalentId id : TalentId.values()) {
            WorkMastery.Specialty s = WorkMastery.specialty(id);
            if (s == null) {
                assertTrue(WorkMastery.PRESENCE.contains(id), id + ": a guard or court talent has Presence instead");
                continue;
            }
            assertFalse(s.numbers().isEmpty(), id.name());
            for (Map.Entry<String, double[]> e : s.numbers().entrySet()) {
                assertTrue(e.getValue()[0] > 0, id + " " + e.getKey() + ": no part missing at tier I");
                assertEquals(3, e.getValue().length);
            }
        }
        // the parts the first draft gave only from tier II or III (11.1)
        assertEquals(1, WorkMastery.specialty(TalentId.SURVEYOR).at("vein_wider", 1), 1e-9);
        assertEquals(3, WorkMastery.specialty(TalentId.MERCHANT_PRINCE).at("extra_trip_every_days", 1), 1e-9);
        assertEquals(0.5, WorkMastery.specialty(TalentId.SAGE_OF_LETTERS).at("rerolls", 1), 1e-9);
        assertEquals(0.5, WorkMastery.specialty(TalentId.ALCHEMIST).at("rerolls", 1), 1e-9);
        assertEquals(1, WorkMastery.specialty(TalentId.ALCHEMIST).at("rerolls", 3), 1e-9, "as sage I / II / II");
        // the duties are the roster's TALENT_DUTY (check_roster.py), aura_<affix> included
        String py = Files.readString(Path.of("tools/roster/check_roster.py"));
        String block = py.substring(py.indexOf("TALENT_DUTY = {"), py.indexOf("}", py.indexOf("TALENT_DUTY = {")));
        Matcher m = Pattern.compile("\"([a-z_]+)\": \"([a-z_]+)\"").matcher(block);
        int n = 0;
        while (m.find()) {
            String talent = m.group(1), duty = m.group(2);
            if (talent.startsWith("aura_")) {
                Affix a = Affix.valueOf(talent.substring(5).toUpperCase(java.util.Locale.ROOT));
                assertEquals(duty, WorkMastery.duty(TalentId.AURA, a), talent);
            } else {
                assertEquals(duty, WorkMastery.duty(HeroCatalogue.enumOf(TalentId.class, talent), null), talent);
            }
            n++;
        }
        assertEquals(27, n);
        assertEquals("guard", WorkMastery.duty(TalentId.MEND, null), "guard and court talents: guard");
        assertEquals("guard", WorkMastery.duty(TalentId.AURA, null));
        // work.specialties off: proficiency only
        Worker plain = new Worker(20, "blacksmith", TalentId.FORGEMASTER, null, Map.of(), true, false);
        assertEquals(1.35, WorkMastery.workSpeed(plain, "blacksmith", Action.CRAFT, 0), 1e-9);
        assertFalse(WorkMastery.specialised(plain, "blacksmith"));
        assertTrue(WorkMastery.specialised(hero(20, "blacksmith", TalentId.FORGEMASTER), "blacksmith"));
        assertFalse(WorkMastery.specialised(hero(20, "miner", TalentId.FORGEMASTER), "miner"), "not its talent's job");
    }

    @Test
    void workSpeedFromEverySourceIsAtMostTwice() {
        Worker smith1 = hero(1, "blacksmith", TalentId.FORGEMASTER);
        assertEquals(1.40, WorkMastery.workSpeed(smith1, "blacksmith", Action.CRAFT, 0), 1e-9, "1 + 0.15 + 0.25");
        assertEquals(1.15, WorkMastery.workSpeed(smith1, "blacksmith", Action.BREAK, 0), 1e-9, "crafting only");
        Worker smith20 = hero(20, "blacksmith", TalentId.FORGEMASTER, null, affixes(Affix.DILIGENCE, 5));
        assertEquals(2.0, WorkMastery.workSpeed(smith20, "blacksmith", Action.CRAFT, 0.35), 1e-9,
                "1 + 0.3 + 0.35 + 0.55 + 0.35 = 2.55: capped at 2");
        assertEquals(1.9, WorkMastery.workSpeed(hero(20, "blacksmith", TalentId.FORGEMASTER), "blacksmith",
                Action.CRAFT, 0), 1e-9);
        assertEquals(1.75, WorkMastery.workSpeed(hero(20, "builder", TalentId.ARCHITECT), "builder", Action.BUILD,
                0), 1e-9);
        assertEquals(1.65, WorkMastery.workSpeed(hero(20, "builder", TalentId.ENGINEER), "builder", Action.BUILD,
                0), 1e-9);
        assertEquals(1.90, WorkMastery.workSpeed(hero(20, "hauler", TalentId.QUARTERMASTER), "hauler", Action.SORT,
                0), 1e-9);
        // aura_diligence: the hero itself counts 4 stacks over the cap at III (9 stacks, +54%)
        Worker aura = hero(20, "builder", TalentId.AURA, Affix.DILIGENCE, affixes(Affix.DILIGENCE, 5));
        assertEquals(0.54, WorkMastery.affix(aura, "builder", Affix.DILIGENCE), 1e-9);
        assertEquals(1.89, WorkMastery.workSpeed(aura, "builder", Action.BUILD, 0), 1e-9);
        assertEquals(0.30, WorkMastery.affix(aura, "miner", Affix.DILIGENCE), 1e-9, "only at the affix's job");
        for (int level = 1; level <= 30; level++) {
            for (TalentId t : TalentId.values()) {
                Worker w = hero(level, WorkMastery.duty(t, Affix.DILIGENCE), t, Affix.DILIGENCE,
                        affixes(Affix.DILIGENCE, 9));
                for (Action act : Action.values()) {
                    assertTrue(WorkMastery.workSpeed(w, w.duty(), act, 0.5) <= WorkMastery.MAX_WORK_SPEED + 1e-9);
                }
            }
        }
    }

    @Test
    void backpacksWearAndHungerKeepTheirCaps() {
        assertEquals(30, WorkMastery.backpackSlots(hero(1, "hauler", TalentId.QUARTERMASTER), "hauler"));
        assertEquals(33, WorkMastery.backpackSlots(hero(10, "trader", TalentId.MERCHANT_PRINCE), "trader"));
        assertEquals(36, WorkMastery.backpackSlots(hero(20, "hauler", TalentId.QUARTERMASTER), "hauler"));
        assertEquals(27, WorkMastery.backpackSlots(hero(20, "trader", TalentId.TRIBUTE_MASTER), "trader"));
        for (int level = 1; level <= 30; level++) {
            for (TalentId t : TalentId.values()) {
                Worker w = hero(level, WorkMastery.duty(t, Affix.THRIFTY), t, t == TalentId.AURA ? Affix.HARDY : null,
                        affixes(Affix.THRIFTY, 4, Affix.HARDY, 4));
                assertTrue(WorkMastery.backpackSlots(w, w.duty()) <= WorkMastery.MAX_BACKPACK);
                assertTrue(WorkMastery.wearMultiplier(w, w.duty()) >= WorkMastery.MIN_WEAR - 1e-9);
                assertTrue(WorkMastery.hungerMultiplier(w, w.duty()) >= WorkMastery.MIN_HUNGER - 1e-9);
            }
        }
        // Thrifty and proficiency multiply; aura_thrifty would go under the floor of 40%
        assertEquals(0.476, WorkMastery.wearMultiplier(hero(20, "smelter", null, null, affixes(Affix.THRIFTY, 4)),
                "smelter"), 1e-9);
        Worker auraThrifty = hero(20, "smelter", TalentId.AURA, Affix.THRIFTY, affixes(Affix.THRIFTY, 4));
        assertEquals(0.64, WorkMastery.affix(auraThrifty, "smelter", Affix.THRIFTY), 1e-9);
        assertEquals(0.4, WorkMastery.wearMultiplier(auraThrifty, "smelter"), 1e-9, "0.36 x 0.7: floored");
        Worker auraHardy = hero(20, "hauler", TalentId.AURA, Affix.HARDY, affixes(Affix.HARDY, 4));
        assertEquals(0.5, WorkMastery.hungerMultiplier(auraHardy, "hauler"), 1e-9, "0.36 x 0.8: floored");
    }

    // ------------------------------------------------------------------ loot (12.2, 13)

    @Test
    void virtualLevelsStayWithinTheVanillaMaximumAndNeverOnSilkTouch() {
        Worker surveyor1 = hero(1, "miner", TalentId.SURVEYOR), surveyor20 = hero(20, "miner", TalentId.SURVEYOR);
        assertEquals(1, WorkMastery.virtualLevel(surveyor1, "miner", Ench.FORTUNE, Kind.ORE, 0, false));
        assertEquals(2, WorkMastery.virtualLevel(hero(10, "miner", TalentId.SURVEYOR), "miner", Ench.FORTUNE, Kind.ORE,
                0, false));
        assertEquals(3, WorkMastery.virtualLevel(surveyor20, "miner", Ench.FORTUNE, Kind.ORE, 0, false));
        assertEquals(1, WorkMastery.virtualLevel(surveyor20, "miner", Ench.FORTUNE, Kind.ORE, 2, false),
                "Fortune III with the tool's own");
        assertEquals(0, WorkMastery.virtualLevel(surveyor20, "miner", Ench.FORTUNE, Kind.ORE, 3, false));
        assertEquals(0, WorkMastery.virtualLevel(surveyor20, "miner", Ench.FORTUNE, Kind.ORE, 0, true), "Silk Touch");
        assertEquals(0, WorkMastery.virtualLevel(surveyor20, "miner", Ench.FORTUNE, Kind.LEAVES, 0, false));
        assertEquals(0, WorkMastery.virtualLevel(surveyor20, "hauler", Ench.FORTUNE, Kind.ORE, 0, false),
                "only when it does the work itself");
        assertEquals(2, WorkMastery.virtualLevel(hero(10, "woodcutter", TalentId.FORESTER), "woodcutter",
                Ench.FORTUNE, Kind.LEAVES, 0, false));
        assertEquals(3, WorkMastery.virtualLevel(hero(20, "farmer", TalentId.HARVEST_BLESSING), "farmer",
                Ench.FORTUNE, Kind.CROP, 0, false));
        Worker angler = hero(10, "fisherman", TalentId.ANGLER_LORD);
        assertEquals(2, WorkMastery.virtualLevel(angler, "fisherman", Ench.LUCK_OF_THE_SEA, Kind.FISH, 0, false));
        assertEquals(1, WorkMastery.virtualLevel(angler, "fisherman", Ench.LURE, Kind.FISH, 2, false));
        assertEquals(2, WorkMastery.virtualLevel(angler, "fisherman", Ench.LURE, Kind.FISH, 0, true),
                "Silk Touch only stops Fortune");
        // renown: Looting on its own kills, at any duty
        assertEquals(3, WorkMastery.virtualLevel(hero(20, "guard", TalentId.RENOWN), "guard", Ench.LOOTING, Kind.KILL,
                0, false));
        assertEquals(0, WorkMastery.virtualLevel(hero(20, "guard", TalentId.MEND), "guard", Ench.LOOTING, Kind.KILL,
                0, false));
        for (Ench e : Ench.values()) {
            for (int tool = 0; tool <= 3; tool++) {
                for (TalentId t : TalentId.values()) {
                    Worker w = hero(30, WorkMastery.duty(t, null), t);
                    for (Kind k : Kind.values()) {
                        assertTrue(tool + WorkMastery.virtualLevel(w, w.duty(), e, k, tool, false)
                                <= Math.max(tool, WorkMastery.VANILLA_MAX));
                    }
                }
            }
        }
    }

    @Test
    void extraRollsAreAtMostSeventyFivePercentAndOne() {
        Worker miner = hero(20, "miner", null, null, affixes(Affix.PROSPECTOR, 5));
        assertEquals(0.25, WorkMastery.extraRollChance(miner, "miner", Kind.ORE), 1e-9);
        Worker auraProspector = hero(20, "miner", TalentId.AURA, Affix.PROSPECTOR, affixes(Affix.PROSPECTOR, 5));
        assertEquals(0.45, WorkMastery.extraRollChance(auraProspector, "miner", Kind.ORE), 1e-9, "9 stacks of 5%");
        assertEquals(0.35, WorkMastery.extraRollChance(hero(1, "miner", TalentId.AURA, Affix.PROSPECTOR,
                affixes(Affix.PROSPECTOR, 5)), "miner", Kind.ORE), 1e-9, "7 stacks at tier I");
        Worker auraGreen = hero(20, "farmer", TalentId.AURA, Affix.GREEN_THUMB, affixes(Affix.GREEN_THUMB, 5));
        assertEquals(0.75, WorkMastery.extraRollChance(auraGreen, "farmer", Kind.CROP), 1e-9, "0.9 capped");
        double[] shear = {0.25, 0.40, 0.55}, dish = {0.15, 0.25, 0.35}, loaf = {0.20, 0.30, 0.40},
                recruit = {0.15, 0.25, 0.35};
        int[] levels = {1, 10, 20};
        for (int i = 0; i < 3; i++) {
            assertEquals(shear[i], WorkMastery.extraRollChance(hero(levels[i], "animal_carer", TalentId.BEASTMASTER),
                    "animal_carer", Kind.SHEARING), 1e-9);
            assertEquals(dish[i], WorkMastery.extraRollChance(hero(levels[i], "chef", TalentId.MASTER_CHEF), "chef",
                    Kind.DISH), 1e-9);
            assertEquals(loaf[i], WorkMastery.extraRollChance(hero(levels[i], "chef", TalentId.MASTER_CHEF), "chef",
                    Kind.LOAF), 1e-9);
            assertEquals(recruit[i], WorkMastery.extraRollChance(hero(levels[i], "guard", TalentId.RALLY), "guard",
                    Kind.RECRUIT), 1e-9, "rally's personal effect: two recruits");
        }
        for (int level = 1; level <= 30; level++) {
            for (TalentId t : TalentId.values()) {
                for (Affix a : Affix.values()) {
                    Worker w = hero(level, WorkMastery.duty(t, a), t, a, affixes(a, 9));
                    for (Kind k : Kind.values()) {
                        assertTrue(WorkMastery.extraRollChance(w, w.duty(), k) <= WorkMastery.MAX_EXTRA_ROLL + 1e-9);
                    }
                }
            }
        }
    }

    @Test
    void aDropTakesVirtualLevelsOrAnExtraRollNeverBoth() {
        // Fortune on ores: 1.33 / 1.75 / 2.2 (vanilla's ore bonus)
        assertEquals(4 / 3.0, WorkMastery.countGain(Kind.ORE, 1), 1e-9);
        assertEquals(1.75, WorkMastery.countGain(Kind.ORE, 2), 1e-9);
        assertEquals(2.2, WorkMastery.countGain(Kind.ORE, 3), 1e-9);
        assertEquals(2.2, WorkMastery.countGain(Kind.ORE, 9), 1e-9, "III at most");
        assertEquals(2.0, WorkMastery.countGain(Kind.LEAVES, 3), 1e-6, "saplings 1/20 to 1/10");
        assertEquals(1.63, WorkMastery.countGain(Kind.CROP, 3), 0.01, "carrots and potatoes: 2.71 to 4.43");
        assertEquals(2.5, WorkMastery.countGain(Kind.KILL, 3), 1e-9);
        assertEquals(1, WorkMastery.countGain(Kind.FISH, 3), 1e-9, "Luck of the Sea changes what, not how many");
        // a surveyor III with Prospector V on an iron pickaxe: 2.2 over 1.25, the virtual levels
        Worker s = hero(20, "miner", TalentId.SURVEYOR, null, affixes(Affix.PROSPECTOR, 5));
        Drop d = WorkMastery.drop(s, "miner", Kind.ORE, 0, false);
        assertEquals(new Drop(Bonus.VIRTUAL_LEVELS, 3, 0), d);
        assertEquals(2.2, d.gain(Kind.ORE, 0), 1e-9);
        // a Fortune II pickaxe leaves one virtual level: 2.2 / 1.75 = 1.257 against 1.25
        assertEquals(Bonus.VIRTUAL_LEVELS, WorkMastery.drop(s, "miner", Kind.ORE, 2, false).bonus());
        // against a 45% roll (aura_prospector III) the roll wins
        assertEquals(new Drop(Bonus.EXTRA_ROLL, 0, 0.45), WorkMastery.drop(Kind.ORE, 1, 0.45, 2));
        // Silk Touch: no Fortune, so the roll
        assertEquals(new Drop(Bonus.EXTRA_ROLL, 0, 0.25), WorkMastery.drop(s, "miner", Kind.ORE, 0, true));
        // a tie goes to the roll; nothing at all is none
        assertEquals(Bonus.EXTRA_ROLL, WorkMastery.drop(Kind.ORE, 1, 1 / 3.0, 0).bonus());
        assertEquals(new Drop(Bonus.NONE, 0, 0), WorkMastery.drop(Kind.ORE, 0, 0, 0));
        assertEquals(new Drop(Bonus.NONE, 0, 0), WorkMastery.drop(hero(20, "miner", null), "miner", Kind.ORE, 3,
                false));
        // never both, and one ore block yields at most 2.2x the plain table with the tool's own
        for (int tool = 0; tool <= 3; tool++) {
            for (int v = 0; v <= 3; v++) {
                for (double roll = 0; roll <= 1.0001; roll += 0.05) {
                    Drop x = WorkMastery.drop(Kind.ORE, v, roll, tool);
                    assertTrue(x.virtualLevels() == 0 || x.rollChance() == 0, x.toString());
                    assertTrue(x.gain(Kind.ORE, tool) * WorkMastery.countGain(Kind.ORE, tool) <= 2.2 + 1e-9
                            || x.bonus() == Bonus.EXTRA_ROLL, x.toString());
                    assertTrue(x.gain(Kind.ORE, tool) <= Math.max(2.2, 1 + WorkMastery.MAX_EXTRA_ROLL) + 1e-9);
                }
            }
        }
    }

    @Test
    void theWorkedDayStaysUnderTheFlag() {
        // 13: a level-20 surveyor with a plain iron pickaxe breaks at most 2x as fast; a miner's day is 40% breaking
        assertEquals(1.25, WorkMastery.dailyOutput(0.4, 2.0, 1), 1e-9, "1 / (0.6 + 0.4 / 2)");
        double ores = WorkMastery.dailyOutput(0.4, 2.0, 2.2);
        assertEquals(2.75, ores, 1e-9);
        assertFalse(WorkMastery.outputFlag(ores, 1, 3.0));
        // the first draft let virtual Fortune and an extra roll stack: 2.2 x 1.75 a block, 4.8 a day: flagged
        double both = WorkMastery.dailyOutput(0.4, 2.0, 2.2 * 1.75);
        assertEquals(4.8, both, 0.05, "about 4.8");
        assertTrue(WorkMastery.outputFlag(both, 1, 3.0));
        assertEquals(1.5, WorkMastery.outputCap(0.1), 1e-9);
        assertEquals(5.0, WorkMastery.outputCap(9), 1e-9);
        assertFalse(WorkMastery.outputFlag(10, 0, 3.0), "no worker to compare with");
        // the surveyor's own day by its numbers: the speed from proficiency and Diligence, the gain from the drop
        Worker s = hero(20, "miner", TalentId.SURVEYOR, null, affixes(Affix.DILIGENCE, 5, Affix.PROSPECTOR, 5));
        double speed = WorkMastery.workSpeed(s, "miner", Action.BREAK, 0)
                / WorkMastery.workerSpeed(Map.of());
        double day = WorkMastery.dailyOutput(0.4, speed, WorkMastery.drop(s, "miner", Kind.ORE, 0, false)
                .gain(Kind.ORE, 0));
        assertTrue(day < 3.0, "" + day);
    }

    // ------------------------------------------------------------------ furnaces, prices, repairs, trips (11.1, 12.3)

    @Test
    void furnacesPricesRepairsAndTrips() {
        Worker ember = hero(20, "smelter", TalentId.EMBER_KEEPER);
        assertEquals(0.55, WorkMastery.furnaceBonus(ember, "smelter", false, 0), 1e-9);
        assertEquals(0.85, WorkMastery.furnaceBonus(ember, "smelter", false, 0.30), 1e-9);
        assertEquals(1.0, WorkMastery.furnaceBonus(ember, "smelter", false, 0.9), 1e-9, "at most 2x vanilla");
        assertEquals(0.40, WorkMastery.furnaceBonus(hero(20, "chef", TalentId.MASTER_CHEF), "chef", true, 0), 1e-9);
        assertEquals(2, WorkMastery.specialty(TalentId.EMBER_KEEPER).at("extra_furnaces", 3), 1e-9);
        // the accumulator: 200 ticks of vanilla cooking done in 129 at +55%, never past the last tick
        WorkMastery.FurnaceBoost boost = new WorkMastery.FurnaceBoost(0.55);
        int progress = 0, ticks = 0;
        while (progress < 200) {
            progress += 1; // vanilla's own tick (it finishes the item at 200)
            if (progress < 200) {
                int add = boost.tick(progress, 200);
                assertTrue(progress + add <= 199, "never past cookingTotalTime - 1");
                progress += add;
            }
            ticks++;
        }
        assertEquals(129, ticks, 1);
        assertEquals(1.0, new WorkMastery.FurnaceBoost(5).bonus(), 1e-9);
        assertEquals(0, new WorkMastery.FurnaceBoost(0.5).tick(199, 200));
        // prices: the specialty and Haggler together at most 40%
        Worker trader = hero(20, "trader", TalentId.MERCHANT_PRINCE, null, affixes(Affix.HAGGLER, 4));
        assertEquals(0.40, WorkMastery.priceCut(trader, "trader"), 1e-9, "0.2 + 0.3");
        assertEquals(0.10, WorkMastery.priceCut(hero(1, "trader", TalentId.MERCHANT_PRINCE), "trader"), 1e-9);
        assertEquals(0.20, WorkMastery.priceCut(hero(20, "trader", TalentId.TRIBUTE_MASTER), "trader"), 1e-9);
        assertEquals(-4, WorkMastery.priceDiff(0.4, 10));
        assertEquals(-1, WorkMastery.priceDiff(0.25, 7), "floor of 1.75");
        assertEquals(-4, WorkMastery.priceDiff(0.9, 10), "40% at most");
        // repairs: 30 / 35 / 40% a unit and 1 / 2 / 3 levels less, at least 1
        Worker smith = hero(20, "blacksmith", TalentId.FORGEMASTER);
        assertEquals(0.40, WorkMastery.repairUnit(smith, "blacksmith"), 1e-9);
        assertEquals(0.25, WorkMastery.repairUnit(hero(20, "blacksmith", null), "blacksmith"), 1e-9, "vanilla");
        assertEquals(2, WorkMastery.repairCost(smith, "blacksmith", 5));
        assertEquals(1, WorkMastery.repairCost(smith, "blacksmith", 2), "at least 1");
        assertEquals(4, WorkMastery.repairCost(hero(1, "blacksmith", TalentId.FORGEMASTER), "blacksmith", 5));
        // a scout's trips: runner and pathfinder together at most +40%
        Worker path = hero(20, "scout", TalentId.PATHFINDER);
        assertEquals(0.40, WorkMastery.tripBonus(path, "scout", 0.20), 1e-9);
        assertEquals(0.15, WorkMastery.tripBonus(hero(1, "scout", TalentId.PATHFINDER), "scout", 0), 1e-9);
        // sage of letters: rerolls 0.5 / 1 / 2 (tier I once at every second enchanting)
        Worker sage1 = hero(1, "enchanter", TalentId.SAGE_OF_LETTERS);
        assertEquals(List.of(0, 1, 0, 1), List.of(WorkMastery.rerolls(sage1, "enchanter", 0),
                WorkMastery.rerolls(sage1, "enchanter", 1), WorkMastery.rerolls(sage1, "enchanter", 2),
                WorkMastery.rerolls(sage1, "enchanter", 3)));
        assertEquals(1, WorkMastery.rerolls(hero(10, "enchanter", TalentId.SAGE_OF_LETTERS), "enchanter", 0));
        assertEquals(2, WorkMastery.rerolls(hero(20, "enchanter", TalentId.SAGE_OF_LETTERS), "enchanter", 0));
        assertEquals(1, WorkMastery.rerolls(hero(20, "enchanter", TalentId.ALCHEMIST), "enchanter", 0));
        assertEquals(0, WorkMastery.rerolls(hero(20, "miner", TalentId.SAGE_OF_LETTERS), "miner", 1));
    }

    // ------------------------------------------------------------------ Presence (11.2)

    @Test
    void presenceRaisesGuardAndCourtTalentsATierAtHome() {
        assertEquals(4, WorkMastery.presenceTier(TalentId.RALLY, 3, true, false));
        assertEquals(3, WorkMastery.presenceTier(TalentId.RALLY, 3, false, false), "away from its base");
        assertEquals(3, WorkMastery.presenceTier(TalentId.RALLY, 3, true, true), "on a raid");
        assertEquals(2, WorkMastery.presenceTier(TalentId.MEND, 1, true, false));
        assertEquals(1, WorkMastery.presenceTier(TalentId.SURVEYOR, 1, true, false), "a production talent");
        assertEquals(25, WorkMastery.presenceValue(TalentId.RALLY, "recruit_food_cheaper_percent", 4), 1e-9);
        assertEquals(20, WorkMastery.presenceValue(TalentId.RALLY, "recruit_food_cheaper_percent", 3), 1e-9);
        assertEquals(7, WorkMastery.presenceValue(TalentId.MEND, "heal_percent", 2), 1e-9, "Hinae at home, tier I");
        assertEquals(12, WorkMastery.presenceValue(TalentId.MEND, "heal_percent", 4), 1e-9);
        assertEquals(10, WorkMastery.presenceValue(TalentId.MEND, "every_seconds", 4), 1e-9, "unchanged at IV");
        assertEquals(50, WorkMastery.presenceValue(TalentId.REVIVAL_RITE, "sooner_percent", 4), 1e-9);
        assertEquals(4, WorkMastery.presenceValue(TalentId.MENTOR, "recruit_level", 4), 1e-9);
        assertEquals(3, WorkMastery.presenceValue(TalentId.WARDEN_OF_WALLS, "repair_budget_multiplier", 4), 1e-9);
        // every tier-IV number is one of Talents' quantities and goes on in the direction I -> III goes
        for (TalentId id : WorkMastery.PRESENCE) {
            for (String q : WorkMastery.tierFourQuantities(id)) {
                assertTrue(Talents.spec(id).numbers().containsKey(q), id + " " + q);
                double one = Talents.value(id, q, 1), three = Talents.value(id, q, 3);
                double four = WorkMastery.presenceValue(id, q, 4);
                assertTrue(Math.signum(four - three) == Math.signum(three - one) || three == one, id + " " + q);
                assertTrue(four != three, id + " " + q + ": tier IV is more");
            }
            if (id != TalentId.STEWARD) {
                assertFalse(WorkMastery.tierFourQuantities(id).isEmpty(), id.name());
            }
        }
        assertEquals(16, WorkMastery.PRESENCE.size());
        // the personal effects stay at the hero's own tier
        assertEquals(4, WorkMastery.personal(TalentId.MEND, "hand_heal", 1), 1e-9);
        assertEquals(8, WorkMastery.personal(TalentId.MEND, "hand_heal", 3), 1e-9);
        assertEquals(30, WorkMastery.personal(TalentId.PURIFIER, "cleanse_every_seconds", 3), 1e-9);
        assertEquals(4, WorkMastery.personal(TalentId.DRILL, "sparring_soldiers", 3), 1e-9);
        assertEquals(0, WorkMastery.personal(TalentId.SURVEYOR, "anything", 3), 1e-9);
        for (TalentId id : WorkMastery.PRESENCE) {
            assertTrue(WorkMastery.specialty(id) == null, id + ": Presence, not a specialty");
        }
    }
}
