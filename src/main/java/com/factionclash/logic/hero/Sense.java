package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.TargetKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a fighting hero senses at one check (HEROES_V2 6.3, 6.6, 6.8; MOBILITY_AND_WORK 7.5): itself, its target (a
 * {@link TargetState}) and the enemies and allies around it. The game fills it the same way whoever the target is: a
 * player, a faction NPC, a hero or a monster ({@code isBlocking()}, a drawn bow or crossbow, eating, airborne...). It
 * has no field that says whether anyone is a player (HEROES_V2 15.6: "it has no 'is a player' field"), so reactions,
 * conditions and {@code use_when} read every target the same way. A plain mutable value with fluent setters, filled
 * fresh for each check; pure Java.
 */
public final class Sense {
    /** The target as the hero sees it; {@link #present} false when it has none. */
    public static final class TargetState {
        public boolean present = true;
        /** Distance from the hero, blocks. */
        public double distance;
        /** In line of sight. */
        public boolean visible = true;
        public double healthShare = 1;
        public double maxHealth = 20;
        public final Set<TargetKind> kinds = EnumSet.noneOf(TargetKind.class);
        public boolean blocking, drawingBow, eating, airborne, flying, inWater, behind, unreachable;
        /** Feet height above the hero's feet (negative: below), blocks. */
        public double heightAbove;
        public boolean burning, freezing, slowed, stunned, hasDot;
        public final Map<String, Integer> marks = new HashMap<>();

        /** Back to a present, visible target with nothing known about it (a sense is reused from check to check). */
        public TargetState clear() {
            present = true;
            distance = 0;
            visible = true;
            healthShare = 1;
            maxHealth = 20;
            kinds.clear();
            blocking = drawingBow = eating = airborne = flying = inWater = behind = unreachable = false;
            heightAbove = 0;
            burning = freezing = slowed = stunned = hasDot = false;
            marks.clear();
            return this;
        }

        public TargetState distance(double d) {
            distance = d;
            return this;
        }

        public TargetState health(double share, double max) {
            healthShare = share;
            maxHealth = max;
            return this;
        }

        public TargetState kind(TargetKind... k) {
            kinds.addAll(List.of(k));
            return this;
        }

        public TargetState blocking(boolean b) {
            blocking = b;
            return this;
        }

        public TargetState drawingBow(boolean b) {
            drawingBow = b;
            return this;
        }

        public TargetState eating(boolean b) {
            eating = b;
            return this;
        }

        public TargetState airborne(boolean b) {
            airborne = b;
            return this;
        }

        public TargetState flying(boolean b) {
            flying = b;
            return this;
        }

        public TargetState behind(boolean b) {
            behind = b;
            return this;
        }

        public TargetState above(double blocks) {
            heightAbove = blocks;
            return this;
        }

        public TargetState unreachable(boolean b) {
            unreachable = b;
            return this;
        }

        public TargetState visible(boolean b) {
            visible = b;
            return this;
        }

        public TargetState mark(String id, int stacks) {
            marks.put(id, stacks);
            return this;
        }
    }

    /** An enemy near the fight: its distance from the hero and from the hero's target. */
    public record Near(double toSelf, double toTarget) {
    }

    /** An ally of the hero's side (a player, an NPC or a hero alike): its distance and health share. */
    public record Ally(double distance, double healthShare, boolean hero) {
    }

    public TargetState target = new TargetState();

    public double healthShare = 1;
    public int phase = 1;
    public boolean enraged;
    /** Engaged enemies of every kind (6.1). */
    public int enemiesEngaged;
    public final List<Near> enemies = new ArrayList<>();
    public final List<Ally> allies = new ArrayList<>();
    public int addsAlive;
    public boolean inHomeBase, night, raining, lowCeiling, inWater;
    /** The named state it is in, or null. */
    public String state;
    public boolean empowered, shielded, airborne, flying, climbing;
    public double staminaShare = 1;
    public final Map<String, Integer> marks = new HashMap<>();
    /** No enemy for this many ticks (v1 {@code out_of_combat}: 5 s). */
    public int ticksWithoutEnemy;
    /** Ultimate energy (0-100). */
    public int energy;
    /** Water or lava along a dash or charge path (6.12: those moves are skipped). */
    public boolean dashPathHazard;
    /** Cannot start moves: stunned, silenced (6.6), eating, or escaping (a cornered hero asks again). */
    public boolean stunned, silenced, escaping;

    /** Back to the defaults (no target, nothing near): a hero reuses one sense for every check, allocating nothing. */
    public Sense clear() {
        target.clear();
        target.present = false;
        healthShare = 1;
        phase = 1;
        enraged = false;
        enemiesEngaged = 0;
        enemies.clear();
        allies.clear();
        addsAlive = 0;
        inHomeBase = night = raining = lowCeiling = inWater = false;
        state = null;
        empowered = shielded = airborne = flying = climbing = false;
        staminaShare = 1;
        marks.clear();
        ticksWithoutEnemy = 0;
        energy = 0;
        dashPathHazard = false;
        stunned = silenced = escaping = false;
        return this;
    }

    public Sense target(TargetState t) {
        target = t;
        return this;
    }

    public Sense health(double share) {
        healthShare = share;
        return this;
    }

    public Sense phase(int p) {
        phase = p;
        return this;
    }

    public Sense enraged(boolean b) {
        enraged = b;
        return this;
    }

    public Sense engaged(int n) {
        enemiesEngaged = n;
        return this;
    }

    public Sense enemy(double toSelf, double toTarget) {
        enemies.add(new Near(toSelf, toTarget));
        return this;
    }

    public Sense ally(double distance, double share, boolean hero) {
        allies.add(new Ally(distance, share, hero));
        return this;
    }

    public Sense energy(int e) {
        energy = e;
        return this;
    }

    public Sense state(String s) {
        state = s;
        return this;
    }

    public Sense mark(String id, int stacks) {
        marks.put(id, stacks);
        return this;
    }

    /** Enemies within {@code r} of the hero, the target included when it is in range. */
    public int enemiesNearSelf(double r) {
        int n = target.present && target.distance <= r ? 1 : 0;
        for (Near e : enemies) {
            if (e.toSelf() <= r) {
                n++;
            }
        }
        return n;
    }

    /** Enemies within {@code r} of the target, the target itself included. */
    public int enemiesNearTarget(double r) {
        if (!target.present) {
            return 0;
        }
        int n = 1;
        for (Near e : enemies) {
            if (e.toTarget() <= r) {
                n++;
            }
        }
        return n;
    }

    /** The lowest health share among allies within {@code r} (1 with none); heroes only when {@code heroesOnly}. */
    public double lowestAlly(double r, boolean heroesOnly) {
        double low = 1;
        for (Ally a : allies) {
            if (a.distance() <= r && (!heroesOnly || a.hero())) {
                low = Math.min(low, a.healthShare());
            }
        }
        return low;
    }

    /** Allies within {@code r}. */
    public int alliesWithin(double r) {
        int n = 0;
        for (Ally a : allies) {
            if (a.distance() <= r) {
                n++;
            }
        }
        return n;
    }
}
