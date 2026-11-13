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
