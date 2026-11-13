package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/** Retaliates against attackers (never allies) and alerts nearby same-faction combatants. */
public class FactionHurtByTargetGoal extends HurtByTargetGoal {
    private final FactionNpc npc;

    public FactionHurtByTargetGoal(FactionNpc npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = npc.getLastHurtByMob();
        if (attacker != null && CombatRules.sameSide(npc, attacker)) {
            return false;
        }
        if (npc.jobIgnoresCombat()) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public void start() {
        super.start();
        LivingEntity attacker = npc.getLastHurtByMob();
        if (attacker != null) {
            Alerts.callGuards(npc, attacker, 20);
        }
    }
}
