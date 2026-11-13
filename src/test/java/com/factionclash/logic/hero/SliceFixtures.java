package com.factionclash.logic.hero;

import java.io.IOException;

/**
 * The eight slice heroes' test fixtures ({@code src/test/resources/heroes/slice}, loaded once by {@link SliceKitsTest})
 * for tests outside this package, such as the leader rules of {@code logic/leader}.
 */
public final class SliceFixtures {
    private SliceFixtures() {
    }

    /** A slice hero as the reader loads it (the test fails when it does not load). */
    public static HeroDef load(String id) throws IOException {
        return SliceKitsTest.load(id);
    }
}
