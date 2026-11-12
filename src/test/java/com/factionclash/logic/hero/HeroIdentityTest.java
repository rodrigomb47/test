package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroIdentityTest {
    @Test
    void heroIdsAreLowerCaseWordsAndLibrariesAreNotHeroes() {
        assertTrue(HeroIdentity.isHeroId("test_rig"));
        assertTrue(HeroIdentity.isHeroId("natsuho_komezaki"));
        assertTrue(HeroIdentity.isHeroId("fixture_bow2"));
        assertFalse(HeroIdentity.isHeroId("_fallback"), "the neutral model");
        assertFalse(HeroIdentity.isHeroId("_core"));
        assertFalse(HeroIdentity.isHeroId(""));
        assertFalse(HeroIdentity.isHeroId(null));
        assertFalse(HeroIdentity.isHeroId("Test_Rig"));
        assertFalse(HeroIdentity.isHeroId("test-rig"));
        assertFalse(HeroIdentity.isHeroId("a".repeat(41)));
        assertTrue(HeroIdentity.isHeroId("a".repeat(40)));
    }

    @Test
    void aNameFromTheIdUntilADefinitionGivesOne() {
        assertEquals("Test Rig", HeroIdentity.nameFromId("test_rig"));
        assertEquals("Natsuho Komezaki", HeroIdentity.nameFromId("natsuho_komezaki"));
        assertEquals("Solo", HeroIdentity.nameFromId("solo"));
        assertEquals("A B", HeroIdentity.nameFromId("a__b_"));
    }

    @Test
    void theWeaponStyleSetsTheSoldierRole() {
        assertEquals("swordsman", HeroIdentity.combatRoleId("katana"));
        assertEquals("swordsman", HeroIdentity.combatRoleId("fists"));
        assertEquals("swordsman", HeroIdentity.combatRoleId("focus"), "thrown styles count as swordsmen");
        assertEquals("swordsman", HeroIdentity.combatRoleId("staff"));
        assertEquals("axe_fighter", HeroIdentity.combatRoleId("hammer"));
        assertEquals("axe_fighter", HeroIdentity.combatRoleId("axe"));
        assertEquals("axe_fighter", HeroIdentity.combatRoleId("Scythe"));
        assertEquals("archer", HeroIdentity.combatRoleId("bow"));
        assertEquals("crossbowman", HeroIdentity.combatRoleId("crossbow"));
        assertEquals("swordsman", HeroIdentity.combatRoleId("unknown_style"));
        assertEquals("swordsman", HeroIdentity.combatRoleId(null));
    }
}
