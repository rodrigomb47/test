package com.factionclash.combat;

import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * When a nearby creeper starts priming, the NPC jumps backward onto a validated safe landing spot; only when the
 * backward jump is blocked or unsafe does it sidestep or run (spec 14).
 */
public class CreeperEvadeGoal extends Goal {
    private final FactionNpc npc;
    private Creeper creeper;
    private int cooldown;
    private int ticks;
    public int jumps;

    public CreeperEvadeGoal(FactionNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        List<Creeper> list = npc.level().getEntitiesOfClass(Creeper.class, npc.getBoundingBox().inflate(5.0),
                c -> c.isAlive() && (c.getSwellDir() > 0 || c.isIgnited()));
        if (list.isEmpty()) {
            return false;
        }
        creeper = list.get(0);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < 20 && creeper != null && creeper.isAlive();
    }

    @Override
    public void start() {
        ticks = 0;
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(creeper, 30F, 30F);
        Vec3 away = npc.position().subtract(creeper.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize();
        if (npc.level() instanceof ServerLevel level && npc.onGround()) {
            for (Vec3 dir : List.of(away, new Vec3(-away.z, 0, away.x), new Vec3(away.z, 0, -away.x))) {
                BlockPos landing = BlockPos.containing(npc.position().add(dir.scale(3.0)));
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos l = landing.above(dy);
                    if (NpcNavigator.isSafeStandingSpot(level, npc, l)) {
                        npc.setDeltaMovement(dir.x * 0.65, 0.42, dir.z * 0.65);
                        npc.hasImpulse = true;
                        jumps++;
                        npc.setStatus("Evading a creeper", "Evading");
                        return;
                    }
                }
            }
        }
        Vec3 run = DefaultRandomPos.getPosAway(npc, 8, 4, creeper.position());
        if (run != null) {
            npc.getNavigation().moveTo(run.x, run.y, run.z, 1.3);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (creeper != null) {
            npc.getLookControl().setLookAt(creeper, 30F, 30F);
        }
    }

    @Override
    public void stop() {
        cooldown = 10;
        creeper = null;
    }
}
