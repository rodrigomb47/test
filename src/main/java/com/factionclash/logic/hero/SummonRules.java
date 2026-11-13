package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Creature;

/**
 * Summons (HERO_FORMAT.md 13.5, 16.2 and 20; task A4): plain vanilla wolves, snow golems and iron golems that fight
 * for the hero's side for a while (their own looks are A8's). The caps: at most 3 alive per hero, of which at most one
 * iron golem (a new one replaces the old), at most 2 snow golems or 1 iron golem per use, 12 per faction. Pure,
 * unit-tested ({@code SummonRulesTest}).
 */
public final class SummonRules {
    private SummonRules() {
    }

    public static final int MAX_PER_HERO = 3, MAX_IRON_PER_HERO = 1, MAX_PER_FACTION = 12;
    /** A summon follows its hero within this many blocks and vanishes beyond {@link #VANISH_DISTANCE}. */
    public static final double LEASH = 16, VANISH_DISTANCE = 48;
    /** How far from the hero a summon is placed. */
    public static final double SPAWN_SPREAD = 2;

    /** The most of a creature one use may call (v1 13.5: wolves 3, snow golems 2, iron golems 1). */
    public static int perUse(Creature c) {
        return switch (c) {
            case WOLF -> 3;
            case SNOW_GOLEM -> 2;
            case IRON_GOLEM -> 1;
        };
    }

    /**
     * What one {@code summon} does: how many new creatures appear and how many of the hero's oldest summons vanish to
     * make room. {@code heroAlive} counts the hero's living summons, {@code heroIron} its iron golems among them,
     * {@code factionAlive} the faction's. A new iron golem replaces the hero's old one; past 3 a hero's oldest go;
     * past the faction's 12 fewer are called (another hero's summons are never sent away).
     */
    public record Plan(int spawn, int retireIron, int retireOldest) {
        public static final Plan NONE = new Plan(0, 0, 0);
    }

    public static Plan plan(Creature c, int count, int heroAlive, int heroIron, int factionAlive) {
        int want = Math.max(0, Math.min(count, perUse(c)));
        if (want == 0) {
            return Plan.NONE;
        }
        int retireIron = c == Creature.IRON_GOLEM ? Math.min(heroIron, MAX_IRON_PER_HERO) : 0;
        int mine = heroAlive - retireIron;
        int faction = factionAlive - retireIron;
        int room = MAX_PER_FACTION - faction;
        // the hero's own oldest go first when that frees faction room too
        int spawn = Math.min(want, MAX_PER_HERO);
        int retireOldest = Math.max(0, mine + spawn - MAX_PER_HERO);
        room += retireOldest;
        spawn = Math.max(0, Math.min(spawn, room));
        retireOldest = Math.max(0, Math.min(retireOldest, mine + spawn - MAX_PER_HERO));
        if (spawn == 0) {
            return new Plan(0, 0, 0);
        }
        return new Plan(spawn, retireIron, retireOldest);
    }

    /** Whether a summon at this distance from its hero must vanish (v1 20: beyond 48 blocks). */
    public static boolean tooFar(double distance) {
        return distance > VANISH_DISTANCE;
    }

    /** Whether a summon at this distance should walk back to its hero (v1 20: follow within 16 blocks). */
    public static boolean shouldFollow(double distance) {
        return distance > LEASH;
    }
}
