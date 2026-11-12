package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The form-scaled keys' variable (HEROES_V2 4.4, G4): its value per form and its name in the shipped library. */
class FormScaleTest {
    @Test
    void valuePerForm() {
        assertEquals(-6.25, FormScale.of(12.0), 1e-9, "chibi hips at 12 px");
        assertEquals(6.25, FormScale.of(24.5), 1e-9, "adult hips at 24.5 px");
        assertEquals(0.0, FormScale.of(18.25), 1e-9, "the middle form reads the written value");
        assertEquals(0.0, FormScale.of(Double.NaN), 1e-9);
    }

    @Test
    void theCoreLibraryUsesThisVariable() throws IOException {
        String core = Files.readString(Path.of("src/main/resources/assets/factionclash/animations/hero/_core.animation.json"));
        assertTrue(core.contains("* " + FormScale.VARIABLE + "\""), "the core library's form-scaled keys name " + FormScale.VARIABLE);
    }
}
