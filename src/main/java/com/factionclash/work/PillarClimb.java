package com.factionclash.work;

import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The pillar climb, as a player "nerd-poles": the driver of a {@link Scaffold.Pillar}, shared by every NPC that builds
 * one. A woodcutter climbs beside a trunk too tall to fell from the ground (and beside scaffold blocks left standing, to
 * take them down); a recon scout climbs where it can see over an enemy's walls ({@code docs/design/recon.md} 3.5, task
 * R4b). The caller walks to the column's foot; from there the climber stands over the column's middle, jumps and places
 * a block from its backpack under its feet as it rises, block by block, holding still between jumps; looks once at
 * each height ({@link Climber#look}); and stops at its planned height, when something it cannot clear is overhead,
 * after {@link #MAX_MISSES} jumps without a block, or with no blocks left. On the way down it breaks the block under
 * its feet at player speed (it goes back into the backpack), putting one back under that block first should the one
 * below have gone, so a drop is never more than one block.
 *
 * <p>The climb's state is the pillar's own (column, planned height, height, phase, why it comes down), saved with the
 * caller's job ({@link Scaffold.Pillar#save}), so a reload resumes it where it was. What is not saved starts again: a
 * jump under way lands (counted as a miss at worst, when its block went in before the save), and the look is taken
 * again where it next stands.</p>
 *
 * <p>Task R4a lifted this out of {@link WoodcutterJob}, which drives it as before.</p>
 */
public final class PillarClimb {
    private PillarClimb() {
    }

    /** PROPOSED: jumps on the pillar that land without a block going in under it before the climb stops there. */
    public static final int MAX_MISSES = 6;

    /** What the climber does where its pillar meets something: hooks, each one doing nothing unless the caller says. */
    public interface Climber {
        /** A block went in under its feet: the pillar is {@code p.height} high now. */
        default void placed(Scaffold.Pillar p) {
        }

        /**
         * Something with a collision shape fills the cell over its head ({@code over}, two above its feet), so a jump would
         * not lift it: true while it clears it (it works at it, at player speed, this tick), false when it cannot and the
         * pillar ends at this height.
         */
        default boolean clearOverhead(Scaffold.Pillar p, BlockPos over) {
            return false;
        }

        /**
         * The look at each block: once at each height it stands at on the way up, from the foot (height 0) on, when it has
         * landed there and before it decides whether to climb on. It may change {@code p.goal}: the climb stops when the
         * height reaches it (a recon scout stops two blocks above its first good look, recon.md 3.5).
         */
        default void look(Scaffold.Pillar p) {
        }

        /**
         * Each tick it works at the block underfoot on the way down, before the blow (and before it gives up on a block it
         * cannot break): {@code p.height} is still the height it stands at.
         */
        default void breaking(Scaffold.Pillar p) {
        }
    }

    /** A climber with no hooks. */
    public static final Climber PLAIN = new Climber() {
    };

    /** What one tick of the climb did. */
    public enum Climb {
        /** In the air on a jump: a block goes in under its feet once they are above the cell it stood in. */
        AIR,
        /** Cutting the vines in the cell it stands in (standing in them, a jump would only lift it half a block). */
        VINES,
        /** Clearing the cell over its head ({@link Climber#clearOverhead}). */
        CLEARING,
        /** It jumped. */
        JUMP,
        /** At its planned height: the climb is over and the work from the top begins ({@link Scaffold.Phase#WORK}). */
        REACHED,
        /**
         * Stopped short of its planned height, which becomes the height it stands at: something overhead it cannot clear,
         * {@link #MAX_MISSES} jumps without a block, or no blocks left. The work from the top begins.
         */
        STOPPED
    }

    /** What one tick of the way down did. */
    public enum Down {
        /** Still dropping onto the block below. */
        DROPPING,
        /** On the ground: the pillar is gone. */
        GROUND,
        /** Nothing under its feet after all: it drops, then looks again from where it lands ({@link Scaffold.Phase#WORK}). */
        GONE,
        /** The block under the one it stands on had gone: one from the backpack went back there first. */
        PUT_BACK,
        /** Breaking the block underfoot. */
        BREAKING,
        /** The block underfoot broke (it is one block lower now, {@link Scaffold.Phase#WORK}). */
        BROKE,
        /** A block it cannot break stands underfoot (a block entity, or unbreakable): someone put it there. */
        STUCK
    }

    /** Off its pillar: knocked or moved off the column once it left the walk to its foot. */
    public static boolean offColumn(FactionNpc npc, Scaffold.Pillar p) {
        return p.phase != Scaffold.Phase.WALK && !Scaffold.overColumn(npc, p.base, 0.8);
    }

    /** At the column's foot: over its middle, at the foot's level, on the ground. */
    public static boolean atFoot(FactionNpc npc, Scaffold.Pillar p) {
        return Scaffold.overColumn(npc, p.base, 0.7) && Math.abs(npc.getY() - p.base.getY()) < 0.5 && npc.onGround();
    }

    /**
     * At the foot: it stops walking and stands right over the middle of the column, and starts building ({@link
     * Scaffold.Phase#CLIMB}), or, for a pillar of height 0, works from the ground ({@link Scaffold.Phase#WORK}).
     */
    public static void begin(FactionNpc npc, Scaffold.Pillar p) {
        npc.getNavigation().stop();
        npc.setPos(p.base.getX() + 0.5, npc.getY(), p.base.getZ() + 0.5);
        p.height = 0;
        p.airTicks = 0;
        p.misses = 0;
        p.looked = -1;
        p.phase = p.goal > 0 ? Scaffold.Phase.CLIMB : Scaffold.Phase.WORK;
    }

    /**
     * One tick of building up, as a player pillars: on the ground (or the pillar's top) it looks once at this height
     * ({@link Climber#look}), cuts the vines it stands in ({@code breaker}) and has the cell over its head cleared
     * ({@link Climber#clearOverhead}), then jumps; once its feet are above the cell it stood in, a block goes in there from
     * the backpack, and it lands on it. At the planned height, or stopped short, it starts the work from the top.
     */
    public static Climb climb(FactionNpc npc, Scaffold.Pillar p, BlockWork.Breaker breaker, Climber climber) {
        ServerLevel level = (ServerLevel) npc.level();
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            p.airTicks++;
            BlockPos cell = p.feet();
            if (!p.placed && npc.getY() >= cell.getY() + 1.0 && Scaffold.place(npc, cell)) {
                p.placed = true;
                p.height++;
                climber.placed(p);
            }
            return Climb.AIR;
        }
        if (p.airTicks > 0 && !p.placed) {
            p.misses++; // landed back where it jumped from
        }
        p.airTicks = 0;
        p.placed = false;
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        if (p.looked != p.height) {
            p.looked = p.height;
            climber.look(p);
        }
        if (p.height >= p.goal || p.misses >= MAX_MISSES || Scaffold.carried(npc) == 0) {
            Climb why = p.height >= p.goal ? Climb.REACHED : Climb.STOPPED;
            p.goal = p.height;
            p.phase = Scaffold.Phase.WORK;
            return why;
        }
        BlockPos feet = p.feet();
        BlockState here = level.getBlockState(feet);
        if (here.is(BlockTags.CLIMBABLE) && here.canBeReplaced()) {
            // vines down the trunk: standing in them, a jump only lifts it half a block (it would hang on them), so it
            // cuts them away first, as a player clears the vines off the spot it pillars up from
            breaker.tick(npc, feet);
            return Climb.VINES;
        }
        BlockPos over = feet.above(2);
        BlockState s = level.getBlockState(over);
        if (!s.getCollisionShape(level, over).isEmpty()) {
            if (climber.clearOverhead(p, over)) {
                return Climb.CLEARING;
            }
            p.goal = p.height; // something else overhead (it changed since the column was chosen): the pillar ends here
            p.phase = Scaffold.Phase.WORK;
            return Climb.STOPPED;
        }
        npc.getJumpControl().jump();
        return Climb.JUMP;
    }

    /**
     * On the pillar between steps (its work at a height, a watch): it holds still over the column; false while it still
     * drops onto the block below, else true, with the height read from where it stands.
     */
    public static boolean settled(FactionNpc npc, Scaffold.Pillar p) {
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            return false;
        }
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        return true;
    }

    /**
     * The work at this height is done: it steps a block down next ({@link Scaffold.Phase#DOWN}); false when it already
     * stands on the ground, where the pillar is gone.
     */
    public static boolean down(Scaffold.Pillar p) {
        if (p.height == 0) {
            return false;
        }
        p.phase = Scaffold.Phase.DOWN;
        return true;
    }

    /**
     * One tick of the step down: the pillar block underfoot is broken at player speed ({@code breaker}: it goes back into
     * the backpack) and the climber drops one block onto the next. Should the block below that one have gone (blown up,
     * broken by someone), one from the backpack is put back under it first, so the drop is never more than a block.
     */
    public static Down stepDown(FactionNpc npc, Scaffold.Pillar p, BlockWork.Breaker breaker, Climber climber) {
        ServerLevel level = (ServerLevel) npc.level();
        Scaffold.holdStill(npc, p.base);
        if (!npc.onGround()) {
            return Down.DROPPING;
        }
        p.height = Math.max(0, npc.getBlockY() - p.base.getY());
        BlockPos under = p.top();
        if (under == null) {
            return Down.GROUND;
        }
        BlockState s = level.getBlockState(under);
        if (s.getCollisionShape(level, under).isEmpty()) {
            p.phase = Scaffold.Phase.WORK; // nothing under it after all: it drops, then looks again
            return Down.GONE;
        }
        BlockPos below = under.below();
        if (p.height >= 2 && level.getBlockState(below).getCollisionShape(level, below).isEmpty() && Scaffold.place(npc, below)) {
            return Down.PUT_BACK;
        }
        climber.breaking(p);
        if (s.hasBlockEntity() || s.getDestroySpeed(level, under) < 0) {
            return Down.STUCK; // not a block it can break: left to the stuck recovery, which digs or bridges down
        }
        if (breaker.tick(npc, under)) {
            p.height--;
            p.phase = Scaffold.Phase.WORK;
            return Down.BROKE;
        }
        return Down.BREAKING;
    }

    /**
     * It comes down now, {@code why} kept on the pillar: true when it had not climbed yet (still on its walk to the foot,
     * or on the ground at the foot), and the caller simply gives the pillar up; else it lands first, then steps down
     * without working any more ({@link Scaffold.Phase#WORK}, then {@link Scaffold.Phase#DOWN}).
     */
    public static boolean comeDown(FactionNpc npc, Scaffold.Pillar p, String why) {
        p.down = why;
        if (p.phase == Scaffold.Phase.WALK || p.height == 0 && npc.onGround() && p.phase != Scaffold.Phase.DOWN) {
            return true;
        }
        if (p.phase != Scaffold.Phase.DOWN) {
            p.phase = Scaffold.Phase.WORK; // (lands first, then steps down)
        }
        return false;
    }

    /** The pillar's blocks still standing in its column (from its first cell to one above its height), lowest first. */
    public static List<BlockPos> standing(ServerLevel level, Scaffold.Pillar p) {
        List<BlockPos> out = new ArrayList<>();
        for (int k = 0; k <= p.height + 1; k++) {
            BlockPos c = p.base.above(k);
            if (Scaffold.isBlock(level.getBlockState(c))) {
                out.add(c);
            }
        }
        return out;
    }
}
