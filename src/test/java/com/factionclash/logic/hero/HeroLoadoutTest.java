package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.GearItem;
import com.factionclash.logic.hero.HeroDef.GearSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a definition makes of the hero's entity: role, kit, bare hands, numbers with boss health (task A1b). */
class HeroLoadoutTest {
    private static final String SWORD = ", \"gear\": [\"iron_sword\"]";

    /** The minimal hero with its gear and basic attack replaced. */
    private static HeroDef hero(String gear, String basic, String role) {
        String t = V2Files.hero2("").replace(SWORD, gear == null ? "" : ", \"gear\": " + gear)
                .replace("{\"style\": \"melee\"}", basic).replace("\"striker\"", "\"" + role + "\"");
        return V2Files.def(t);
    }

    private static HeroDef melee(String gear) {
        return hero(gear, "{\"style\": \"melee\"}", "striker");
    }

    @Test
    void theRoleFitsTheBasicAttackAndTheWeapon() {
        assertEquals("archer", HeroLoadout.roleId(hero(null, "{\"style\": \"bow\"}", "marksman"), "bow"));
        assertEquals("crossbowman", HeroLoadout.roleId(hero(null, "{\"style\": \"crossbow\"}", "marksman"), "crossbow"));
        assertEquals("swordsman", HeroLoadout.roleId(hero(null, "{\"style\": \"thrown\"}", "support"), "focus"),
                "a thrown style is a swordsman with its own goal");
        assertEquals("swordsman", HeroLoadout.roleId(hero(null, "{\"style\": \"trident\"}", "striker"), "polearm"));
        assertEquals("swordsman", HeroLoadout.roleId(melee("[\"iron_sword\"]"), "katana"));
        assertEquals("swordsman", HeroLoadout.roleId(melee("[\"iron_sword\"]"), null), "no model of its own");
        assertEquals("axe_fighter", HeroLoadout.roleId(melee("[\"iron_axe\"]"), "katana"), "an axe in hand");
        assertEquals("axe_fighter", HeroLoadout.roleId(melee("[\"mace\"]"), "hammer"), "a mace (Benika's club)");
        assertEquals("axe_fighter", HeroLoadout.roleId(melee("[\"mace\"]"), null), "a mace whatever the model's style");
        assertEquals("axe_fighter", HeroLoadout.roleId(melee("[\"iron_sword\"]"), "Hammer"), "a hammer style, any case");
        assertEquals("axe_fighter", HeroLoadout.roleId(melee("[\"iron_sword\"]"), "scythe"));
        assertEquals("swordsman", HeroLoadout.roleId(melee(null), "fists"), "bare fists");
        assertEquals("swordsman", HeroLoadout.roleId(hero(null, "{\"style\": \"melee\"}", "striker"), "bow"),
                "a melee hero is never a ranged role, whatever its model's style says");
    }

    @Test
    void aFistsHeroHoldsNoWeaponButAnAuthoredOneStays() {
        HeroDef none = melee(null);
        GearItem added = HeroLoadout.mainHand(none.gear());
        assertEquals("stone_sword", added.item(), "the reader's placeholder (v1 7)");
        assertTrue(added.added());
        assertTrue(HeroLoadout.kit(none, "fists").isEmpty(), "a fists hero leaves the placeholder out");
        assertTrue(HeroLoadout.bareHanded(none, "fists"));
        assertTrue(HeroLoadout.bareHanded(none, "FISTS"));
        assertEquals(List.of(added), HeroLoadout.kit(none, "katana"), "any other style keeps it");
        assertFalse(HeroLoadout.bareHanded(none, "katana"));
        assertFalse(HeroLoadout.bareHanded(none, null));
        HeroDef authored = melee("[\"stone_sword\"]");
        assertFalse(HeroLoadout.mainHand(authored.gear()).added());
        assertFalse(HeroLoadout.bareHanded(authored, "fists"), "a weapon its file names is held");
        HeroDef claws = melee("[{\"item\": \"iron_sword\", \"name\": \"Claw Gauntlets\"}]");
        assertEquals("Claw Gauntlets", HeroLoadout.kit(claws, "fists").get(0).name(), "Natsuho's gauntlets stay");
        HeroDef armour = melee("[\"iron_chestplate\", \"iron_boots\"]");
        assertEquals(2, HeroLoadout.kit(armour, "fists").size(), "only the main-hand placeholder goes; armour stays");
        assertTrue(HeroLoadout.bareHanded(armour, "fists"));
    }

    @Test
    void thrownHeroesFightBareHandedAndBowsCarryAnArrow() {
        HeroDef thrown = hero(null, "{\"style\": \"thrown\"}", "support");
        assertTrue(HeroLoadout.bareHanded(thrown, "focus"), "the reader adds nothing for thrown");
        assertFalse(HeroLoadout.needsArrow(thrown));
        HeroDef bow = hero(null, "{\"style\": \"bow\"}", "marksman");
        assertEquals("bow", HeroLoadout.mainHand(HeroLoadout.kit(bow, "bow")).item());
        assertTrue(HeroLoadout.needsArrow(bow));
        assertTrue(HeroLoadout.needsArrow(hero(null, "{\"style\": \"crossbow\"}", "marksman")));
        assertFalse(HeroLoadout.needsArrow(melee(null)));
    }

    @Test
    void onlyAKitWithAShieldTakesOne() {
        assertTrue(HeroLoadout.hasShield(melee("[\"iron_sword\", \"shield\"]"), "sword_shield"));
        assertFalse(HeroLoadout.hasShield(melee("[\"iron_sword\"]"), "katana"));
        assertNull(HeroLoadout.mainHand(List.of()));
    }

    @Test
    void bossHealthIsTheHeroesHealthWhateverItFights() {
        HeroDef d = melee("[\"iron_sword\"]"); // a rare striker: 28 health, scale 300, role factor 1.2
        HeroLoadout.Numbers n = HeroLoadout.numbers(d, 1, false, 100);
        assertEquals(28, n.baseHealth(), 1e-9);
        assertEquals(300, n.healthScale(), 1e-9);
        assertEquals(1.2, n.roleFactor(), 1e-9);
        assertEquals(28 * 3 * 1.2, n.maxHealth(), 1e-9);
        assertEquals(3 * 1.2 - 1, n.bossModifier(), 1e-9, "one ADD_MULTIPLIED_TOTAL modifier on the v1 health");
        assertEquals(n.maxHealth(), n.baseHealth() * (1 + n.bossModifier()), 1e-9);
        assertEquals(2, n.attackBonus(), 1e-9);
        assertEquals(1, n.armorBonus(), 1e-9);
        assertEquals(0, n.speedModifier(), 1e-9, "100% of a player's speed");
        assertEquals(0.10, n.knockbackResistance(), 1e-9);
        assertEquals(12, n.maxHitShare());
        assertEquals(100, n.poise());
        assertEquals(n, HeroLoadout.numbers(d, 0, false, 100), "an NPC level 0 reads as level 1");
    }

    @Test
    void leadingTheConfigScaleAndLevelsMoveIt() {
        HeroDef d = melee("[\"iron_sword\"]");
        assertEquals(28 * 3.5 * 1.2, HeroLoadout.numbers(d, 1, true, 100).maxHealth(), 1e-9, "a leader +50");
        assertEquals(28 * 1.5 * 1.2, HeroLoadout.numbers(d, 1, false, 50).maxHealth(), 1e-9, "boss.healthScale 50%");
        assertEquals(28 * 0.75 * 1.2, HeroLoadout.numbers(d, 1, false, 1).maxHealth(), 1e-9, "the config floor is 25%");
        assertEquals(28 * 9 * 1.2, HeroLoadout.numbers(d, 1, false, 999).maxHealth(), 1e-9, "the config cap is 300%");
        HeroLoadout.Numbers l11 = HeroLoadout.numbers(d, 11, false, 100); // fierce: +0.3 health, +0.12 attack, +0.03 armour
        assertEquals(31, l11.baseHealth(), 1e-9);
        assertEquals(31 * 3.6, l11.maxHealth(), 1e-9);
        assertEquals(3.2, l11.attackBonus(), 1e-9);
        assertEquals(1.3, l11.armorBonus(), 1e-9);
        assertEquals(HeroLoadout.numbers(d, 30, false, 100), HeroLoadout.numbers(d, 99, false, 100), "level 30 at most");
        HeroDef vanguard = hero("[\"iron_sword\"]", "{\"style\": \"melee\"}", "vanguard");
        HeroLoadout.Numbers v = HeroLoadout.numbers(vanguard, 1, false, 100);
        assertEquals(34 * 3 * 1.0, v.maxHealth(), 1e-9, "role factor 1.0");
        assertEquals(-0.05, v.speedModifier(), 1e-9, "95% of a player's speed");
        assertEquals(0.30, v.knockbackResistance(), 1e-9);
        HeroDef marksman = hero(null, "{\"style\": \"bow\"}", "marksman");
        assertEquals(24 * 3 * 1.3, HeroLoadout.numbers(marksman, 1, false, 100).maxHealth(), 1e-9, "role factor 1.3");
        HeroDef epic = V2Files.def(V2Files.hero2("\"rarity\": \"epic\""));
        assertEquals(350, HeroLoadout.numbers(epic, 1, false, 100).healthScale(), 1e-9);
        HeroDef own = V2Files.def(V2Files.hero2("\"boss\": {\"health_scale\": 500, \"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}]}"));
        assertEquals(28 * 5 * 1.2, HeroLoadout.numbers(own, 1, false, 100).maxHealth(), 1e-9, "its own health_scale");
    }

    @Test
    void aDormantHeroIsAPlainSoldier() {
        assertEquals(20, HeroLoadout.PLAIN.maxHealth(), 1e-9);
        assertEquals(20, HeroLoadout.PLAIN.baseHealth(), 1e-9);
        assertEquals(0, HeroLoadout.PLAIN.bossModifier(), 1e-9);
        assertEquals(0, HeroLoadout.PLAIN.attackBonus() + HeroLoadout.PLAIN.armorBonus() + HeroLoadout.PLAIN.speedModifier()
                + HeroLoadout.PLAIN.knockbackResistance(), 1e-9);
    }

    @Test
    void theKitSignatureChangesWithTheKit() {
        GearItem a = new GearItem("iron_sword", null, Map.of(), GearSlot.MAINHAND);
        GearItem named = new GearItem("iron_sword", "Blade", Map.of(), GearSlot.MAINHAND);
        GearItem ench = new GearItem("iron_sword", null, Map.of("sharpness", 1), GearSlot.MAINHAND);
        String s = HeroLoadout.signature(List.of(a), false);
        assertEquals(s, HeroLoadout.signature(List.of(new GearItem("iron_sword", null, Map.of(), GearSlot.MAINHAND)), false));
        assertNotEquals(s, HeroLoadout.signature(List.of(named), false));
        assertNotEquals(s, HeroLoadout.signature(List.of(ench), false));
        assertNotEquals(s, HeroLoadout.signature(List.of(a), true), "the arrow counts");
        assertNotEquals(s, HeroLoadout.signature(List.of(), false));
    }

    @Test
    void aWorkerDutyMakesTheRoleButNotHowItFights() {
        HeroDef guard = melee("[\"iron_sword\"]");
        assertEquals("guard", guard.behaviour().duty());
        assertNull(HeroLoadout.dutyRoleId(guard));
        assertEquals("swordsman", HeroLoadout.roleId(guard, "katana"));
        String miner = V2Files.hero2("").replace("\"combat_role\": \"striker\"", "\"combat_role\": \"striker\", \"duty\": \"miner\"");
        HeroDef m = V2Files.def(miner);
        assertEquals("miner", HeroLoadout.dutyRoleId(m));
        assertEquals("miner", HeroLoadout.roleId(m, "katana"), "it counts as that worker (v1 8)");
        assertEquals("swordsman", HeroLoadout.combatRoleId(m, "katana"), "and fights with its style");
    }
}
