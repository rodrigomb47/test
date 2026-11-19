package com.factionclash.campaign;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * News a member carries home ({@code docs/design/missing_npcs.md} 5): a death it saw away from home, a line only
 * someone there could tell (a raid's end, an enemy hero's fall), or a line about the carrier itself (a level it gained
 * away, a hero's own fall nobody saw). Told when the carrier comes home (an NPC: entering a home area; a member player:
 * reporting at a station); the first carrier home tells it and the others' copies go.
 */
public final class News {
    /** DEATH: a member's death it saw; LINE: told as "W brings news: ..."; OWN: about the carrier itself, told as it is. */
    public enum Kind { DEATH, LINE, OWN }

    /** The same id in every carrier's copy: the dead member's UUID, or a fresh one for a line. */
    public final UUID id;
    public final Kind kind;
    /** A death: "Name (Role)" (a hero: its name); a line or its own: the line. */
    public final String text;
    public final boolean hero;
    public final int x, z;
    public final long at;

    public News(UUID id, Kind kind, String text, boolean hero, int x, int z, long at) {
        this.id = id;
        this.kind = kind;
        this.text = text;
        this.hero = hero;
        this.x = x;
        this.z = z;
        this.at = at;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.name());
        t.putString("text", text);
        t.putBoolean("hero", hero);
        t.putInt("x", x);
        t.putInt("z", z);
        t.putLong("at", at);
        return t;
    }

    public static News load(CompoundTag t) {
        if (!t.hasUUID("id")) {
            return null;
        }
        Kind k;
        try {
            k = Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new News(t.getUUID("id"), k, t.getString("text"), t.getBoolean("hero"), t.getInt("x"), t.getInt("z"), t.getLong("at"));
    }

    @Override
    public String toString() {
        return kind == Kind.DEATH ? "death of " + text + " at " + x + ", " + z + " (t=" + at + ")" : "\"" + text + "\"";
    }
}
