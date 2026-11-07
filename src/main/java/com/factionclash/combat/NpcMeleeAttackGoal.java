package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.BowItem;

/**
 * Melee with the NPC's actual tool or weapon (one hit per attack cooldown). Home guards do not chase enemies far
 * from their base unless they are on a raid.
 */
public class NpcMeleeAttackGoal extends MeleeAttackGoal {
    private static final double LEASH = 56;
    private final FactionNpc npc;

    public NpcMeleeAttackGoal(FactionNpc npc) {
        super(npc, 1.2, true);
        this.npc = npc;
    }

    private boolean ranged() {
        return npc.getRole().ranged && (npc.getMainHandItem().getItem() instanceof BowItem
                || npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) && !npc.ammo().isEmpty();
    }

    private boolean leashed() {
        LivingEntity t = npc.getTarget();
        BaseState b = npc.base();
        if (t == null || b == null || npc.level().getServer() == null) {
            return false;
        }
        FactionState f = CampaignData.get(npc.level().getServer()).factions.get(npc.getFaction());
        boolean raiding = f != null && f.raiders.contains(npc.getUUID());
        boolean away = npc.getRole() == com.factionclash.npc.NpcRole.SCOUT;
        if (raiding || away) {
            return false;
        }
        return Math.sqrt(t.distanceToSqr(b.center.getX(), t.getY(), b.center.getZ())) > LEASH;
    }

    /**
     * Time between hits from the held weapon's attack speed, as for a player at full strength: a sword (1.6/s)
     * about every 12 ticks, an axe (0.8-1.0/s) every 20-25 ticks, at least half a second bare-handed.
     */
    @Override
    protected int getAttackInterval() {
        double speed = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        int ticks = (int) Math.round(20.0 / Math.max(0.25, speed));
        return this.adjustedTickDelay(net.minecraft.util.Mth.clamp(ticks, 10, 40));
    }

    @Override
    public boolean canUse() {
        if (ranged() || npc.job() != null && npc.job().ignoresCombat()) {
            return false;
        }
        if (npc.getRole().isCivilian() && npc.getHealth() < npc.getMaxHealth() * 0.5F) {
            return false;
        }
        if (leashed()) {
            npc.setTarget(null);
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (ranged() || leashed() || (npc.getRole().isCivilian() && npc.getHealth() < npc.getMaxHealth() * 0.5F)) {
            return false;
        }
        return super.canContinueToUse();
    }
}
