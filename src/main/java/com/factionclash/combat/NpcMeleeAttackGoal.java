package com.factionclash.combat;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * Melee with the NPC's actual tool or weapon (one hit per attack cooldown). Home guards do not chase enemies far
 * from their base unless they are on a raid. Walks and swings as vanilla's melee goal does, but a target it cannot
 * walk up to and hit is left to its {@link Pursuit}: a gate or door is opened on the way, a block it may break is
 * broken, it builds itself up or breaks the block under a target standing too high, or it waits out of the target's
 * reach until there is a way. While chasing, the path toward the target is searched again every 4 to 10 ticks as in
 * vanilla; once the target is known to be out of reach that stops.
 */
public class NpcMeleeAttackGoal extends Goal {
    private static final double LEASH = 56;
    private static final double SPEED = 1.2;
    private final FactionNpc npc;
    private Path path;
    private double pathedX, pathedY, pathedZ;
    private int repath;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private Pursuit.Mode lastMode = Pursuit.Mode.CHASE;

    public NpcMeleeAttackGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
     * about every 12 ticks, an axe (0.8-1.0/s) every 20-25 ticks, at least half a second bare-handed. (Not used for
     * the cooldown: vanilla's melee goal, which this one replaced, always waited 20 ticks, and that is kept.)
     */
    protected int getAttackInterval() {
        double speed = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        int ticks = (int) Math.round(20.0 / Math.max(0.25, speed));
        return this.adjustedTickDelay(net.minecraft.util.Mth.clamp(ticks, 10, 40));
    }

    private boolean blocked() {
        return ranged() || npc.job() != null && npc.job().ignoresCombat()
                || npc.getRole().isCivilian() && npc.getHealth() < npc.getMaxHealth() * 0.5F;
    }

    @Override
    public boolean canUse() {
        long now = npc.level().getGameTime();
        if (now - lastCanUseCheck < 20L) {
            return false;
        }
        lastCanUseCheck = now;
        if (blocked()) {
            return false;
        }
        if (leashed()) {
            npc.setTarget(null);
            return false;
        }
        LivingEntity t = npc.getTarget();
        if (t == null || !t.isAlive()) {
            return false;
        }
        if (npc.pursuit().mode() != Pursuit.Mode.CHASE && t == npc.pursuit().target()) {
            path = null;
            return true; // already working or waiting on this one: no new search
        }
        path = npc.getNavigation().createPath(t, 0);
        return path != null || npc.isWithinMeleeAttackRange(t);
    }

    @Override
    public boolean canContinueToUse() {
        if (blocked() || leashed()) {
            return false;
        }
        LivingEntity t = npc.getTarget();
        if (t == null || !t.isAlive() || !npc.isWithinRestriction(t.blockPosition())) {
            return false;
        }
        return !(t instanceof Player p) || !p.isSpectator() && !p.isCreative();
    }

    @Override
    public void start() {
        LivingEntity t = npc.getTarget();
        npc.pursuit().bind(t, false);
        if (path != null) {
            npc.getNavigation().moveTo(path, SPEED);
            if (!path.canReach() || npc.pursuit().futile(t, true)) {
                npc.pursuit().unreachable(t, path);
            } else {
                npc.pursuit().reachable();
            }
        }
        npc.setAggressive(true);
        repath = 0;
        ticksUntilNextAttack = 0;
        lastMode = npc.pursuit().mode();
    }

    @Override
    public void stop() {
        LivingEntity t = npc.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(t)) {
            npc.setTarget(null);
        }
        npc.setAggressive(false);
        npc.getNavigation().stop();
        npc.pursuit().reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = npc.getTarget();
        if (t == null) {
            return;
        }
        Pursuit pursuit = npc.pursuit();
        pursuit.bind(t, false);
        npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (pursuit.mode() == Pursuit.Mode.CHASE) {
            if (lastMode != Pursuit.Mode.CHASE) {
                repath = 0; // back from working or waiting: look for the way at once
                pathedX = pathedY = pathedZ = 0;
            }
            chase(t);
        } else {
            pursuit.tick(t);
        }
        lastMode = pursuit.mode();
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        boolean canStrike = pursuit.canStrikeNow(t);
        checkAndPerformAttack(t);
        pursuit.watchStuck(t, canStrike || npc.tickCount - npc.lastAttackTick() < 40);
    }

    /** Vanilla's chase: a fresh path to the target every 4-10 ticks (less often far away) while it moves or now and then. */
    private void chase(LivingEntity t) {
        repath = Math.max(repath - 1, 0);
        if (repath > 0 || !(pathedX == 0 && pathedY == 0 && pathedZ == 0 || t.distanceToSqr(pathedX, pathedY, pathedZ) >= 1.0
                || npc.getRandom().nextFloat() < 0.05F)) {
            return;
        }
        pathedX = t.getX();
        pathedY = t.getY();
        pathedZ = t.getZ();
        repath = 4 + npc.getRandom().nextInt(7);
        double d = npc.distanceToSqr(t);
        if (d > 1024.0) {
            repath += 10;
        } else if (d > 256.0) {
            repath += 5;
        }
        Path p = npc.getNavigation().createPath(t, 0);
        repath = adjustedTickDelay(repath);
        // a path that reaches a spot of the target's it found it cannot get at (a 1x1 hole) counts as none
        if (p != null && (!p.canReach() || npc.pursuit().futile(t, true))) {
            repath += 15; // out of reach: searching again every few ticks would find the same
            Pursuit pursuit = npc.pursuit();
            pursuit.unreachable(t, p);
            if (pursuit.mode() != Pursuit.Mode.CHASE) {
                return; // it works its way there, or waits out of reach
            }
            if (pursuit.canStrikeNow(t)) {
                npc.getNavigation().stop(); // in reach where it stands (on its pillar, at the fence): stay
                return;
            }
        }
        if (p != null && p.canReach()) {
            npc.pursuit().reachable();
        }
        if (p == null || !npc.getNavigation().moveTo(p, SPEED)) {
            repath += 15;
        }
    }

    /**
     * Fights like a player: just before the next swing is ready it jumps, and strikes on the way down for a critical
     * hit (half as much again). Not from water, a ladder or while holding the shield up in front.
     */
    protected void checkAndPerformAttack(LivingEntity target) {
        int wait = ticksUntilNextAttack;
        if (npc.onGround() && !npc.isInWater() && wait > 0 && wait <= 7 && npc.isWithinMeleeAttackRange(target)
                && npc.getRandom().nextFloat() < 0.6F && npc.pursuit().mode() == Pursuit.Mode.CHASE) {
            npc.getJumpControl().jump();
        }
        if (!npc.onGround() && npc.getDeltaMovement().y > 0.05 && canPerformAttack(target)) {
            return; // still rising: strike on the way down
        }
        if (canPerformAttack(target)) {
            ticksUntilNextAttack = adjustedTickDelay(20);
            npc.swing(InteractionHand.MAIN_HAND);
            npc.doHurtTarget(target);
        }
    }

    private boolean canPerformAttack(LivingEntity target) {
        return ticksUntilNextAttack <= 0 && npc.isWithinMeleeAttackRange(target) && npc.getSensing().hasLineOfSight(target);
    }
}
