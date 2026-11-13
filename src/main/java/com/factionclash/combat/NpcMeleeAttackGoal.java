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
 *
 * <p>Heroes (task A4; HERO_FORMAT.md 5.2 and 8): the leash is the hero's aggression's ({@link FactionNpc#meleeLeash}), a
 * hero of any duty fights on under half health ({@link FactionNpc#fightsLikeSoldier}), a skirmisher steps 3 blocks
 * back after each hit until its next swing is ready, and a melee hero with a keep distance holds it while a move of its
 * can land from there, backing away from an enemy inside half of it ({@link FactionNpc#holdsDistance}). Soldiers are
 * unchanged.
 */
public class NpcMeleeAttackGoal extends Goal {
    private static final double SPEED = 1.2;
    private final FactionNpc npc;
    private Path path;
    private double pathedX, pathedY, pathedZ;
    private int repath;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private Pursuit.Mode lastMode = Pursuit.Mode.CHASE;
    /** Whether its path had been walked to its end at the last chase tick. */
    private boolean pathEnded;
    /** A skirmisher steps back until this tick (v1 5.2). */
    private int stepBackUntil;

    public NpcMeleeAttackGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean ranged() {
        return npc.rangedFighter() && (npc.getMainHandItem().getItem() instanceof BowItem
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
        return Math.sqrt(t.distanceToSqr(b.center.getX(), t.getY(), b.center.getZ())) > npc.meleeLeash();
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
        return ranged() || !npc.fightsInMelee() || npc.jobIgnoresCombat()
                || !npc.fightsLikeSoldier() && npc.getHealth() < npc.getMaxHealth() * 0.5F;
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
        path = npc.pursuit().pathTo(t);
        // no path is searched while it is in the air (knocked up by a blow): a target close by is still fought (live
        // check: in a pit, bounced by the player's blows from the edge, it never took up the fight at all)
        return path != null || npc.isWithinMeleeAttackRange(t)
                || !npc.onGround() && npc.distanceToSqr(t) < Pursuit.PLAN_RANGE * Pursuit.PLAN_RANGE;
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
        pathEnded = false;
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
        pursuit.afterJump(t);
        npc.getLookControl().setLookAt(t, 30.0F, 30.0F);
        Pursuit.Mode was = pursuit.mode();
        if (was == Pursuit.Mode.CHASE) {
            if (lastMode != Pursuit.Mode.CHASE) {
                repath = 0; // back from working or waiting: look for the way at once
                pathedX = pathedY = pathedZ = 0;
            }
            if (!spacing(t)) {
                chase(t);
            }
        } else {
            pursuit.tick(t);
        }
        // the mode it started this tick in: a switch to CHASE made during the tick (the block in the doorway just
        // broken, a way in found while waiting) is seen next tick, so it looks for the way at once (live check: it
        // stood about 2 s in the open doorway, in the player's reach, until its old search timer ran out)
        lastMode = was;
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        boolean canStrike = pursuit.canStrikeNow(t);
        checkAndPerformAttack(t);
        boolean fighting = canStrike || npc.tickCount - npc.lastAttackTick() < 40;
        pursuit.watchStuck(t, fighting);
        pursuit.watchBoxedIn(t, canStrike); // (a lucky blow from the bottom of a pit, at the top of a bounce, does not get it out)
    }

    /**
     * A hero's spacing (v1 5.2 and 8): a skirmisher's step back after a hit, or a held keep distance. True when it moved
     * itself this tick (no chase); soldiers never space.
     */
    private boolean spacing(LivingEntity t) {
        if (stepBackUntil > npc.tickCount) {
            if (npc.getNavigation().isDone()) {
                stepAway(t, com.factionclash.logic.hero.TargetRules.SKIRMISH_STEP);
            }
            return true;
        }
        double keep = npc.holdsDistance();
        if (keep <= 0) {
            return false;
        }
        double d = npc.distanceTo(t);
        if (com.factionclash.logic.hero.TargetRules.backAway(d, keep)) {
            if (npc.getNavigation().isDone() || npc.tickCount % 20 == 0) {
                stepAway(t, keep);
            }
            return true;
        }
        if (d <= keep + 0.5) {
            npc.getNavigation().stop();
            return true;
        }
        return false;
    }

    /** Walks away from the target, about {@code blocks} (a random spot that leads away, as a kiting archer's). */
    private void stepAway(LivingEntity t, double blocks) {
        net.minecraft.world.phys.Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(npc,
                (int) Math.ceil(blocks) + 2, 3, t.position());
        if (away != null) {
            npc.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
        }
    }

    /** Vanilla's chase: a fresh path to the target every 4-10 ticks (less often far away) while it moves or now and then. */
    private void chase(LivingEntity t) {
        boolean ended = npc.getNavigation().isDone();
        if (ended && !pathEnded) {
            // at the end of its path (a partial one, walked from afar up to the block in the doorway): it looks again
            // from here at once, instead of standing in the player's reach until the search timer runs out
            repath = 0;
            pathedX = pathedY = pathedZ = 0;
        }
        pathEnded = ended;
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
        Path p = npc.pursuit().pathTo(t);
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
            if (npc.doHurtTarget(target) && npc.skirmishes()) {
                // hit and run: back 3 blocks until the next swing is nearly ready (v1 5.2)
                stepBackUntil = npc.tickCount + Math.max(4, ticksUntilNextAttack - 4);
                stepAway(target, com.factionclash.logic.hero.TargetRules.SKIRMISH_STEP);
            }
        }
    }

    private boolean canPerformAttack(LivingEntity target) {
        return ticksUntilNextAttack <= 0 && npc.isWithinMeleeAttackRange(target) && npc.getSensing().hasLineOfSight(target);
    }
}
