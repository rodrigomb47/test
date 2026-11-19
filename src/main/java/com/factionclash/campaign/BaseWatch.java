package com.factionclash.campaign;

import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a faction's people see of an enemy settlement or a Hollow lair (knowledge review of 2026-11-19,
 * {@code docs/design/knowledge.md}): the scouts' and raiders' looks at its defence (the soldiers and heroes in sight, how
 * much of the yard was in view), its ruins once its heart is gone, and the enemy soldiers the faction saw fall at home.
 * The war council judges an enemy base only by these ({@link Intel}); it never reads another faction's roster, raid party,
 * gear or heroes. Server thread only.
 */
public final class BaseWatch {
    private BaseWatch() {
    }

    /** How far (horizontally, from the centre) an observer looks at a base: the scouts' 64 blocks. */
    public static final double SIGHT = 64;
    /** PROPOSED: a raider sees its target's heart (standing or gone) within this many blocks with a clear view. */
    public static final double HEART_SIGHT = 48;
    /** The yard's look-out points per side (5 x 5 over the walls' inside; a lair: its clearing). */
    static final int GRID = 5;

    /**
     * What {@code observer} sees of {@code b} now, as a report of its faction (ENEMY_BASE or LAIR, by nobody yet), or null
     * when nothing of it is in sight: it is beyond {@link #SIGHT}, or neither its walls, its yard, its heart's place nor
     * one of its people is in view. A base whose heart is gone and whose heart's place is in view is reported fallen.
     */
    public static Report look(ServerLevel level, LivingEntity observer, BaseState b, long now) {
        if (b == null || b.center == null || observer.level() != level || horizontal(observer.position(), b.center) > SIGHT) {
            return null;
        }
        Report.Kind kind = b.isSettlement() ? Report.Kind.ENEMY_BASE : Report.Kind.LAIR;
        String what = b.faction.displayName + (kind == Report.Kind.LAIR ? " lair" : " settlement");
        BlockPos heart = b.point(BaseState.HEART) != null ? b.point(BaseState.HEART) : b.center;
        boolean heartInView = clear(level, observer, Vec3.atCenterOf(heart));
        if (b.destroyed && heartInView) {
            Report r = new Report(kind, b.center, now, b.faction, 0, what + " in ruins, its heart gone");
            r.fallen = true;
            return r;
        }
        int half = BaseZones.half(b);
        int reach = Math.max(1, half - 2);
        int inView = 0;
        double y = b.center.getY() + 1.5;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                double dx = reach * (2.0 * i / (GRID - 1) - 1), dz = reach * (2.0 * j / (GRID - 1) - 1);
                if (clear(level, observer, new Vec3(b.center.getX() + 0.5 + dx, y, b.center.getZ() + 0.5 + dz))) {
                    inView++;
                }
            }
        }
        double visible = inView / (double) (GRID * GRID);
        AABB area = new AABB(b.center).inflate(half + 8, 12, half + 8);
        int soldiers = 0;
        double power = 0;
        Map<String, Float> heroes = new LinkedHashMap<>();
        if (b.isSettlement()) {
            for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, area, n -> n.isAlive() && n.getFaction() == b.faction
                    && !n.isDisplayOnly() && n.fightsLikeSoldier())) {
                if (!observer.hasLineOfSight(n)) {
                    continue;
                }
                if (n instanceof com.factionclash.hero.HeroEntity h) {
                    heroes.put(h.heroId(), (float) seenHeroPower(h));
                } else {
                    soldiers++;
                    power += FactionDirector.power(n); // what anyone sees: the armour worn and the weapon held
                }
            }
        } else {
            for (Monster m : level.getEntitiesOfClass(Monster.class, area, CombatRules::isHostileMonster)) {
                if (observer.hasLineOfSight(m)) {
                    soldiers++;
                    power += 1;
                }
            }
        }
        boolean wallsInView = b.isSettlement() && wallsInView(level, observer, b, half);
        if (inView == 0 && soldiers == 0 && heroes.isEmpty() && !heartInView && !wallsInView) {
            return null; // nothing of it in sight (behind a hill)
        }
        double heroPower = heroes.values().stream().mapToDouble(Float::doubleValue).sum();
        Intel.Look look = new Intel.Look(soldiers, visible, soldiers > 0 ? power / soldiers : 0, heroPower, now);
        String seen = soldiers + (kind == Report.Kind.LAIR ? (soldiers == 1 ? " monster" : " monsters") : (soldiers == 1 ? " soldier" : " soldiers"))
                + (heroes.isEmpty() ? "" : " and " + heroes.size() + (heroes.size() == 1 ? " hero" : " heroes")) + " seen, "
                + Math.round(visible * 100) + "% of " + (kind == Report.Kind.LAIR ? "its ground" : "the yard") + " in view";
        Report r = new Report(kind, b.center, now, b.faction, soldiers, what + ", " + seen);
        r.look = look;
        r.heroes = Report.joinHeroes(heroes);
        return r;
    }

    /** A hero's war power as seen: its full strength (its health bar and level show it; it heals before a defence). */
    static double seenHeroPower(com.factionclash.hero.HeroEntity h) {
        return com.factionclash.logic.hero.HeroRules.heroPower(h.getMaxHealth(), h.npcLevel(), FCConfig.HERO_POWER_PERCENT.get());
    }

    /** The middle of each wall's top, any one in view. */
    private static boolean wallsInView(ServerLevel level, LivingEntity observer, BaseState b, int half) {
        double y = b.center.getY() + 4.5;
        double cx = b.center.getX() + 0.5, cz = b.center.getZ() + 0.5;
        // just outside each wall, at its top: a ray that reaches it has the wall in view
        double[][] at = {{cx - half - 1.2, cz}, {cx + half + 1.2, cz}, {cx, cz - half - 1.2}, {cx, cz + half + 1.2}};
        for (double[] p : at) {
            if (clear(level, observer, new Vec3(p[0], y, p[1]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code e} sees that {@code target} has fallen: its heart is gone and the heart's place is in view within
     * {@link #HEART_SIGHT} (a raider marching on a base learns of its fall there, not from the far side of the map).
     */
    public static boolean seesFall(Entity e, BaseState target) {
        if (target == null || !target.destroyed || !(e.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos heart = target.point(BaseState.HEART) != null ? target.point(BaseState.HEART) : target.center;
        Vec3 spot = Vec3.atCenterOf(heart);
        return e.getEyePosition().distanceToSqr(spot) <= HEART_SIGHT * HEART_SIGHT && clear(level, e, spot);
    }

    /** A clear view from {@code e}'s eyes to {@code to} (blocks with a collision shape block it; fluids do not). */
    static boolean clear(Level level, Entity e, Vec3 to) {
        Vec3 eye = e.getEyePosition();
        if (eye.distanceToSqr(to) > 128 * 128) {
            return false;
        }
        return level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
    }

    private static double horizontal(Vec3 p, BlockPos c) {
        double dx = p.x - (c.getX() + 0.5), dz = p.z - (c.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ losses seen at home

    /**
     * An enemy soldier fell ({@code FactionNpc.die}): every faction whose people saw it fall at home (inside its home
     * area, or seen by a member standing in it; {@link RollCall#witnesses}) counts one loss against its report of the
     * dead soldier's settlement: the nearest settlement of that faction its scouts reported (whose soldier it was, the
     * sash alone does not tell). The council's estimate of that garrison is lowered by it until a newer look. A hero is
     * no loss (it rises again at home).
     */
    public static void enemyFell(ServerLevel level, CampaignData data, FactionNpc victim) {
        if (victim.isDisplayOnly() || victim instanceof com.factionclash.hero.HeroEntity || !victim.fightsLikeSoldier()
                || victim.getFaction() == null || !victim.getFaction().human) {
            return;
        }
        BlockPos at = victim.blockPosition();
        long now = level.getGameTime();
        for (FactionType seer : FactionType.humans()) {
            FactionState f = data.faction(seer);
            if (seer == victim.getFaction() || !f.alive || !Diplomacy.hostile(level.getServer(), seer, victim.getFaction())) {
                continue;
            }
            boolean seen = RollCall.inHomeArea(data, seer, level, at);
            if (!seen) {
                List<RollCall.Witness> w = RollCall.witnesses(level, data, victim, seer);
                seen = w.stream().anyMatch(x -> x.home() && !x.player());
            }
            if (!seen) {
                continue;
            }
            Report nearest = null;
            for (Report r : f.knowledge) {
                if (r.kind == Report.Kind.ENEMY_BASE && r.subject == victim.getFaction() && !r.fallen
                        && (nearest == null || r.pos.distSqr(at) < nearest.pos.distSqr(at))) {
                    nearest = r;
                }
            }
            if (nearest != null) {
                nearest.losses++;
                nearest.lossAt = now;
                data.setDirty();
            }
        }
    }
}
