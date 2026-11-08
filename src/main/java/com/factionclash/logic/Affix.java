package com.factionclash.logic;

import java.util.EnumSet;
import java.util.Set;

/**
 * Level-up affixes. Each has a per-stack value and a stack cap so repeated rolls cannot grow without limit
 * (spec 18). Values and caps are PROPOSED defaults (see BALANCE.md).
 */
public enum Affix {
    VITALITY("Vitality", "+2 max health", 2.0, 5, null),
    SWIFTNESS("Swiftness", "+3% movement speed", 0.03, 5, null),
    MIGHT("Might", "+1 attack damage", 1.0, 5, null),
    TOUGHNESS("Toughness", "+1 armor", 1.0, 5, null),
    DILIGENCE("Diligence", "+6% work speed", 0.06, 5, null),
    PROSPECTOR("Prospector", "+5% chance of an extra ore", 0.05, 5, Set.of(Job.MINER)),
    REGROWTH("Regrowth", "+8% chance a replanted sapling grows instantly", 0.08, 5, Set.of(Job.WOODCUTTER)),
    GREEN_THUMB("Green Thumb", "+10% chance of an extra crop", 0.10, 5, Set.of(Job.FARMER)),
    PATHFINDER("Pathfinder", "+16 blocks scouting range", 16.0, 4, Set.of(Job.SCOUT)),
    MASON("Mason", "+10% building speed", 0.10, 5, Set.of(Job.BUILDER)),
    MARKSMAN("Marksman", "+10% arrow damage", 0.10, 5, Set.of(Job.ARCHER)),
    THRIFTY("Thrifty", "-8% gear wear", 0.08, 4, null),
    HARDY("Hardy", "-8% hunger", 0.08, 4, null),
    DUELIST("Duelist", "+10% critical-hit damage", 0.10, 5, Set.of(Job.MELEE)),
    ANGLER("Angler", "+8% chance of a second catch", 0.08, 5, Set.of(Job.FISHER)),
    HAGGLER("Haggler", "-5% villager prices (like a good reputation)", 0.05, 4, Set.of(Job.TRADER));

    /** Coarse job groups used for job-specific affixes (kept separate from the entity role enum). */
    public enum Job { MINER, WOODCUTTER, FARMER, SCOUT, BUILDER, ARCHER, OTHER, MELEE, FISHER, TRADER }

    public final String displayName;
    public final String description;
    public final double perStack;
    public final int maxStacks;
    private final Set<Job> jobs;

    Affix(String displayName, String description, double perStack, int maxStacks, Set<Job> jobs) {
        this.displayName = displayName;
        this.description = description;
        this.perStack = perStack;
        this.maxStacks = maxStacks;
        this.jobs = jobs == null ? null : EnumSet.copyOf(jobs);
    }

    public boolean isGeneral() {
        return jobs == null;
    }

    public boolean appliesTo(Job job) {
        return jobs == null || jobs.contains(job);
    }
}
