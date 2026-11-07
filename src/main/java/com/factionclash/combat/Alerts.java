package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;

/** Calls nearby same-faction combatants to deal with a threat. */
public final class Alerts {
    private Alerts() {
    }

    public static int callGuards(FactionNpc caller, LivingEntity threat, double radius) {
        if (threat == null || !threat.isAlive() || CombatRules.sameSide(caller, threat)) {
            return 0;
        }
        int n = 0;
        for (FactionNpc g : caller.level().getEntitiesOfClass(FactionNpc.class, caller.getBoundingBox().inflate(radius),
                o -> o != caller && o.isAlive() && o.getFaction() == caller.getFaction() && o.getRole().combatant)) {
            if (g.getTarget() == null || !g.getTarget().isAlive()) {
                g.setTarget(threat);
                n++;
            }
        }
        return n;
    }
}
