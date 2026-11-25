package com.factionclash.campaign;

import com.factionclash.faction.FactionType;

/**
 * Who stands on whose side. There is no diplomacy between factions: the pact against a conqueror was removed by the owner
 * on 2026-11-25, so every faction is hostile to every other and only the members of one faction are allies. Kept as the
 * one place that says so, for the fights, raids, alarms and heroes that ask.
 */
public final class Diplomacy {
    private Diplomacy() {
    }

    /** The same faction: no friendly fire, no retaliation, no alarms between them. */
    public static boolean allied(FactionType a, FactionType b) {
        return a != null && a == b;
    }

    /** Two different factions: their NPCs fight, and they may raid each other. */
    public static boolean hostile(FactionType a, FactionType b) {
        return a != null && b != null && a != b;
    }
}
