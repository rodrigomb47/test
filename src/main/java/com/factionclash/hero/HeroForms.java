package com.factionclash.hero;

import com.factionclash.campaign.Diplomacy;
import com.factionclash.combat.CombatRules;
import com.factionclash.logic.hero.FormGeometry;
import com.factionclash.npc.FactionNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Who a form reaches (HERO_FORMAT.md 10.1; task A3), in the world. <b>Enemies</b> are exactly whom the hero's side
 * fights: NPCs and heroes of hostile factions (pacts respected), enemy players (never creative or spectator), hostile
 * monsters and its own target; <b>allies</b> are its side, players of its faction included. Animals, villagers,
 * golems, armour stands and display copies are never affected. The same tests for a player and an NPC: nothing here
 * treats a player differently except what vanilla itself does (creative and spectator players are never enemies,
 * 6.14 rule 6). Area queries are capped at {@link #MAX_RECEIVERS} receivers, nearest first.
 */
public final class HeroForms {
    private HeroForms() {
    }

    /** At most this many receivers of one area (v1 29.2: "AABB queries capped at radius 12, 16 receivers"). */
    public static final int MAX_RECEIVERS = 16;
    /** A melee strike reaches 3.5 blocks (v1 10.1), measured to the edge of the target's box. */
    public static final double MELEE_REACH = 3.5;
    /** A target may drift this much past a form's range during the wind-up before the move is called off. */
    public static final double RANGE_SLACK = 2.0;

    /**
     * Never touched by a hero's move: animals, villagers, golems, armour stands, display copies (a hero's summon is no
     * bystander: it is of its hero's side, A4).
     */
    static boolean bystander(LivingEntity e) {
        return (e instanceof Animal || e instanceof AbstractVillager || e instanceof AbstractGolem || e instanceof ArmorStand
                || e instanceof FactionNpc n && n.isDisplayOnly()) && !HeroSummons.isSummon(e);
    }

    /** Whether {@code e} is an enemy of the hero (see the class comment); the same test for players and NPCs. */
    public static boolean enemy(HeroEntity hero, LivingEntity e) {
        if (e == null || e == hero || !e.isAlive() || bystander(e)) {
            return false;
        }
        if (e instanceof FactionNpc o) {
            return Diplomacy.hostile(hero.getServer(), hero.getFaction(), o.getFaction());
        }
        if (e instanceof Player p) {
            return p instanceof ServerPlayer sp && CombatRules.isEnemyPlayer(hero, sp);
        }
        if (HeroSummons.isSummon(e)) {
            return Diplomacy.hostile(hero.getServer(), hero.getFaction(), HeroSummons.factionOf(e));
        }
        if (e instanceof com.factionclash.hero.entity.HeroDecoy d) {
            // an enemy hero's decoy fools a hero as it fools a player (HEROES_V2 6.11, 6.15)
            return d.faction() != null && Diplomacy.hostile(hero.getServer(), hero.getFaction(), d.faction());
        }
        return e == hero.getTarget() || e instanceof Enemy && CombatRules.isHostileMonster(e);
    }

    /** Whether {@code e} is of the hero's side (the hero itself included). */
    public static boolean ally(HeroEntity hero, LivingEntity e) {
        if (e == hero) {
            return true;
        }
        if (e == null || !e.isAlive() || e instanceof FactionNpc n && n.isDisplayOnly()) {
            return false;
        }
        if (e instanceof Player p && p.isSpectator()) {
            return false;
        }
        return CombatRules.sameSide(hero, e);
    }

    /** The distance from a point to the nearest point of an entity's box. */
    public static double distance(Entity e, Vec3 c) {
        return Math.sqrt(e.getBoundingBox().distanceToSqr(c));
    }

    /** Enemies whose box is within {@code r} of {@code c}, nearest first, at most {@link #MAX_RECEIVERS}. */
    public static List<LivingEntity> enemiesNear(HeroEntity hero, Vec3 c, double r) {
        return near(hero, c, r, true);
    }

    /** Allies (the hero included when it is in range) within {@code r} of {@code c}. */
    public static List<LivingEntity> alliesNear(HeroEntity hero, Vec3 c, double r) {
        return near(hero, c, r, false);
    }

    private static List<LivingEntity> near(HeroEntity hero, Vec3 c, double r, boolean enemies) {
        List<LivingEntity> found = hero.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1),
                e -> (enemies ? enemy(hero, e) : ally(hero, e)) && distance(e, c) <= r);
        found.sort((a, b) -> Double.compare(distance(a, c), distance(b, c)));
        return found.size() > MAX_RECEIVERS ? new ArrayList<>(found.subList(0, MAX_RECEIVERS)) : found;
    }

    /** {@code to: ally} and the {@code ally} form: the most hurt ally within {@code r} of the hero, possibly the hero. */
    public static LivingEntity mostHurtAlly(HeroEntity hero, double r) {
        LivingEntity best = hero;
        double low = hero.getHealth() / Math.max(1, hero.getMaxHealth());
        for (LivingEntity a : alliesNear(hero, hero.position(), r)) {
            double s = a.getHealth() / Math.max(1, a.getMaxHealth());
            if (s < low - 1e-6) {
                low = s;
                best = a;
            }
        }
        return best;
    }

    /** Whether the target's box is within melee reach of the hero. */
    public static boolean inMeleeReach(HeroEntity hero, LivingEntity t) {
        return t != null && distance(t, hero.position().add(0, hero.getBbHeight() / 2, 0)) <= MELEE_REACH
                && hero.getSensing().hasLineOfSight(t);
    }

    /** The horizontal unit facing from the hero toward {@code aim} (its body's facing when aim is on top of it). */
    public static Vec3 facing(HeroEntity hero, Vec3 aim) {
        double dx = aim.x - hero.getX(), dz = aim.z - hero.getZ();
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) {
            float yaw = hero.getYRot() * ((float) Math.PI / 180F);
            return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return new Vec3(dx / l, 0, dz / l);
    }

    /** Enemies in a cone in front of the hero toward {@code aim} (v1 10.1). */
    public static List<LivingEntity> cone(HeroEntity hero, Vec3 aim, double range, double angle) {
        Vec3 f = facing(hero, aim);
        Vec3 o = hero.position();
        List<LivingEntity> in = new ArrayList<>();
        for (LivingEntity e : enemiesNear(hero, o, range + 1)) {
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            if (FormGeometry.inCone(c.x - o.x, c.y - (o.y + hero.getBbHeight() / 2), c.z - o.z, f.x, f.z, angle, range,
                    e.getBbWidth() / 2)) {
                in.add(e);
            }
        }
        return in;
    }

    /**
     * Where a line from the hero's eyes toward {@code aim} ends: {@code range} blocks out, or at the first solid block
     * (v1 10.1: "stops at the first solid block").
     */
    public static Vec3 lineEnd(HeroEntity hero, Vec3 aim, double range) {
        Vec3 from = hero.getEyePosition();
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1e-6) {
            dir = facing(hero, aim);
        }
        Vec3 to = from.add(dir.normalize().scale(range));
        BlockHitResult hit = hero.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hero));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** Enemies along a line of {@code width} from the hero's eyes to {@code end}. */
    public static List<LivingEntity> line(HeroEntity hero, Vec3 end, double width) {
        Vec3 from = hero.getEyePosition();
        List<LivingEntity> in = new ArrayList<>();
        Vec3 mid = from.add(end).scale(0.5);
        double half = from.distanceTo(end) / 2 + width + 1;
        for (LivingEntity e : enemiesNear(hero, mid, half)) {
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            double reach = Math.max(e.getBbWidth() / 2, e.getBbHeight() / 2);
            if (FormGeometry.onLine(c.x, c.y, c.z, reach, from.x, from.y, from.z, end.x, end.y, end.z, width)) {
                in.add(e);
            }
        }
        return in;
    }
}
