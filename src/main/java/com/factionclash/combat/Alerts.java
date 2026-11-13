package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;

/** Calls nearby same-faction combatants to deal with a threat. */
public final class Alerts {
    private Alerts() {
    }

    /** Its side's fighters within {@code radius} take up the threat: soldiers and heroes of any duty (v1 29.3, A4). */
    public static int callGuards(FactionNpc caller, LivingEntity threat, double radius) {
        if (threat == null || !threat.isAlive() || CombatRules.sameSide(caller, threat)) {
            return 0;
        }
        int n = 0;
        for (FactionNpc g : caller.level().getEntitiesOfClass(FactionNpc.class, caller.getBoundingBox().inflate(radius),
                o -> o != caller && o.isAlive() && !o.isDisplayOnly() && o.getFaction() == caller.getFaction() && o.fightsLikeSoldier())) {
            if ((g.getTarget() == null || !g.getTarget().isAlive()) && g.senses(threat)) {
                g.setTarget(threat);
                n++;
            }
        }
        return n;
    }

    /**
     * A hero's {@code call_guards} (HEROES_V2 6.6, 6.11; task A5): up to {@code count} real soldiers of its faction
     * within {@code radius} (never heroes, nothing spawned) take {@code threat} as their target, the free ones first,
     * then the nearest. A blinded soldier answers only within its sight. Returns how many came.
     */
    public static int callGuards(FactionNpc caller, LivingEntity threat, double radius, int count) {
        if (threat == null || !threat.isAlive() || count <= 0 || CombatRules.sameSide(caller, threat)) {
            return 0;
        }
        java.util.List<FactionNpc> found = caller.level().getEntitiesOfClass(FactionNpc.class, caller.getBoundingBox().inflate(radius),
                o -> o != caller && o.isAlive() && !o.isDisplayOnly() && !(o instanceof com.factionclash.hero.HeroEntity)
                        && o.getFaction() == caller.getFaction() && o.fightsLikeSoldier() && o.getTarget() != threat
                        && o.distanceToSqr(caller) <= radius * radius && o.senses(threat));
        found.sort((a, b) -> {
            boolean fa = a.getTarget() == null || !a.getTarget().isAlive(), fb = b.getTarget() == null || !b.getTarget().isAlive();
            if (fa != fb) {
                return fa ? -1 : 1;
            }
            return Double.compare(a.distanceToSqr(caller), b.distanceToSqr(caller));
        });
        int n = 0;
        for (FactionNpc g : found) {
            if (n >= count) {
                break;
            }
            g.setTarget(threat);
            n++;
        }
        return n;
    }

    /**
     * A fighter that cannot reach its target calls the archers and crossbowmen of its faction within {@code radius}
     * that have nothing else to shoot at: what a sword cannot reach, an arrow can.
     */
    public static int callArchers(FactionNpc caller, LivingEntity threat, double radius) {
        if (threat == null || !threat.isAlive() || CombatRules.sameSide(caller, threat)) {
            return 0;
        }
        int n = 0;
        for (FactionNpc g : caller.level().getEntitiesOfClass(FactionNpc.class, caller.getBoundingBox().inflate(radius),
                o -> o != caller && o.isAlive() && !o.isDisplayOnly() && o.getFaction() == caller.getFaction() && o.rangedFighter())) {
            if (g.getTarget() == null || !g.getTarget().isAlive()) {
                g.setTarget(threat);
                n++;
            }
        }
        return n;
    }
}
