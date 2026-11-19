package com.factionclash.hero;

import com.factionclash.logic.hero.HeroLinks;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcPathNavigation;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A hero's ground navigation (MOBILITY_AND_WORK 3.1-3.4; task A11): the faction NPCs' navigation with the hero's links
 * in its path search ({@link HeroNodeEvaluator}) and a follower that takes them with {@link HeroMovement}. A path keeps
 * plain nodes; the links its steps use are kept beside it, by node index. When the next node is a link's landing, the
 * hero walks to the centre of the start cell and stops, then the movement engine takes it there physically, on
 * vanilla's own physics: a jump-up (the crouch, a straight jump and its air jumps, over the edge), a gap leap solved
 * over the real blocks, a climb up the face or down one, a chimney of wall kicks, or a glide from the edge (a drop is
 * walked off, as vanilla walks a drop). While the action runs the navigation waits; the hero's job and fight go on
 * around it ({@link HeroMovement#navOwned}). When it ends on the landing cell the path goes on from there; when it does
 * not (a leap that fell short, a climb that clung, a jump that did not get over), the link is dead for 10 minutes and
 * the path is searched again without it. Nothing teleports: the stuck recovery of {@code NpcNavigator} is still the one
 * exception, as for any NPC.
 */
public final class HeroGroundNavigation extends NpcPathNavigation {
    /** A failed link is left out of the searches for this long (3.3: 10 minutes). */
    public static final int DEAD_TICKS = 10 * 60 * 20;
    /** Ticks to get to a link's start before it counts as failed. */
    static final int APPROACH_TICKS = 60;
    /** How close to the start cell's centre the hero stands before a link (blocks, horizontal). */
    static final double AT_START = 0.25, AT_GLIDE_EDGE = 0.4;

    /** The links each path's steps use (by the index of the node they land on); made on first use. */
    private Map<Path, Int2ObjectOpenHashMap<HeroLinks.Link>> pathLinks;
    /** Failed links (their two cells) and the game time they come back. */
    private Map<Long, Long> deadLinks;
    /** The evaluator (set while the super constructor runs, so not initialised here). */
    private HeroNodeEvaluator heroEvaluator;

    private HeroLinks.Link running;
    private Path runningPath;
    private int runningIndex;
    private boolean glideStarted;
    private int approach;
    private int approachIndex = -1;
    private Path approachPath;
    private boolean hurtDuring;

    /** Links taken, and links that failed (tests and the sheet). */
    public int linksTaken, linksFailed;
    /** The kinds of the links taken (tests). */
    public final java.util.EnumMap<HeroLinks.Kind, Integer> taken = new java.util.EnumMap<>(HeroLinks.Kind.class);

    public HeroGroundNavigation(HeroEntity hero, Level level) {
        super(hero, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        HeroNodeEvaluator e = new HeroNodeEvaluator(this);
        e.setCanPassDoors(true);
        this.nodeEvaluator = e;
        this.heroEvaluator = e;
        HeroGroundNavigation self = this;
        return new PathFinder(e, maxVisitedNodes) {
            @Override
            public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange, int accuracy,
                                 float searchDepthMultiplier) {
                LivingEntity t = mob.getTarget();
                e.gates(mob instanceof FactionNpc n ? n.opensGates() : t != null && t.isAlive());
                e.edges(mob instanceof FactionNpc n2 ? n2.edgeSense() : null); // the edge sense serves heroes too (T3)
                searches++;
                Path p = super.findPath(region, mob, targets, maxRange, accuracy, searchDepthMultiplier);
                self.remember(p, e);
                e.finish();
                return p;
            }

            @Override
            protected float distance(Node a, Node b) {
                return e.stepCost(a, b);
            }
        };
    }

    /** The hero's path search (tests read what it offered). */
    public HeroNodeEvaluator evaluator() {
        return heroEvaluator;
    }

    private void remember(Path p, HeroNodeEvaluator e) {
        if (p == null) {
            return;
        }
        Int2ObjectOpenHashMap<HeroLinks.Link> links = null;
        for (int i = 1; i < p.getNodeCount(); i++) {
            HeroLinks.Link l = e.edge(p.getNode(i - 1), p.getNode(i));
            if (l != null) {
                if (links == null) {
                    links = new Int2ObjectOpenHashMap<>();
                }
                links.put(i, l);
            }
        }
        if (links != null) {
            if (pathLinks == null) {
                pathLinks = new WeakHashMap<>();
            }
            pathLinks.put(p, links);
        }
    }

    /** The link a path's step onto node {@code index} uses (null: walking). */
    public HeroLinks.Link linkAt(Path p, int index) {
        if (p == null || pathLinks == null || index <= 0) {
            return null;
        }
        Int2ObjectOpenHashMap<HeroLinks.Link> m = pathLinks.get(p);
        return m == null ? null : m.get(index);
    }

    /** Every link a path uses (tests). */
    public java.util.List<HeroLinks.Link> linksOf(Path p) {
        java.util.List<HeroLinks.Link> out = new java.util.ArrayList<>();
        for (int i = 1; p != null && i < p.getNodeCount(); i++) {
            HeroLinks.Link l = linkAt(p, i);
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ dead links (3.3)

    private static long cells(HeroLinks.Pos a, HeroLinks.Pos b) {
        return BlockPos.asLong(a.x(), a.y(), a.z()) * 31 + BlockPos.asLong(b.x(), b.y(), b.z());
    }

    /** Whether the link between these two cells failed in the last 10 minutes. */
    boolean dead(HeroLinks.Pos from, HeroLinks.Pos to) {
        if (deadLinks == null || deadLinks.isEmpty()) {
            return false;
        }
        Long until = deadLinks.get(cells(from, to));
        return until != null && until > level.getGameTime();
    }

    private void markDead(HeroLinks.Link l) {
        if (deadLinks == null) {
            deadLinks = new HashMap<>();
        }
        long now = level.getGameTime();
        if (deadLinks.size() >= 256) {
            deadLinks.values().removeIf(t -> t <= now);
            if (deadLinks.size() >= 256) {
                deadLinks.clear();
            }
        }
        deadLinks.put(cells(l.from(), l.to()), now + DEAD_TICKS);
    }

    /** Dead links now (tests). */
    public int deadCount() {
        if (deadLinks == null) {
            return 0;
        }
        long now = level.getGameTime();
        return (int) deadLinks.values().stream().filter(t -> t > now).count();
    }

    /** The link running now (null: none). */
    public HeroLinks.Link running() {
        return running;
    }

    // ------------------------------------------------------------------ following

    @Override
    public void tick() {
        if (!(mob instanceof HeroEntity hero) || level.isClientSide) {
            super.tick();
            return;
        }
        HeroMovement mv = hero.movement();
        if (running != null) {
            hurtDuring |= hero.hurtTime > 0;
            if (mv.controls() && mv.navOwned()) {
                if (mv.state() == HeroMovement.State.CLING) {
                    mv.letGo(); // a climb whose height ran out on the face: it lets go, and the link failed
                }
                return;
            }
            if (running.kind() == HeroLinks.Kind.GLIDE && !glideStarted) {
                glideApproach(hero, mv);
                return;
            }
            finish(hero);
            return;
        }
        if (mv.controls()) {
            return; // an action of its own (a fight's leap, a downing, an operator's): the path waits
        }
        if (!isDone()) {
            int i = path.getNextNodeIndex();
            HeroLinks.Link l = linkAt(path, i);
            if (l != null && l.kind() != HeroLinks.Kind.DROP) {
                follow(hero, mv, l, i);
                return;
            }
        }
        approach = 0;
        super.tick();
        // vanilla's follower may have just stepped onto a link's landing (the first node is its own cell): it aimed the
        // move control at the landing, which jumps at a wall it cannot jump; the link's start is the aim from this tick
        if (!isDone()) {
            int i = path.getNextNodeIndex();
            HeroLinks.Link l = linkAt(path, i);
            if (l != null && l.kind() != HeroLinks.Kind.DROP) {
                follow(hero, mv, l, i);
            }
        }
    }

    /** Whether a link is running or the path's next step is one (a caller that re-paths waits for it). */
    public boolean atLink() {
        if (running != null) {
            return true;
        }
        if (isDone()) {
            return false;
        }
        HeroLinks.Link l = linkAt(path, path.getNextNodeIndex());
        return l != null && l.kind() != HeroLinks.Kind.DROP;
    }

    @Override
    public void stop() {
        super.stop();
        approach = 0;
    }

    /** Walks to the link's start, stops on its centre, and starts the link. */
    private void follow(HeroEntity hero, HeroMovement mv, HeroLinks.Link l, int index) {
        if (approachPath != path || approachIndex != index) {
            approachPath = path;
            approachIndex = index;
            approach = 0;
        }
        if (dead(l.from(), l.to())) {
            stop();
            return;
        }
        double cx = l.from().x() + 0.5, cz = l.from().z() + 0.5;
        double dx = cx - mob.getX(), dz = cz - mob.getZ(), d2 = dx * dx + dz * dz;
        boolean onCell = mob.getBlockX() == l.from().x() && mob.getBlockZ() == l.from().z()
                && Math.abs(mob.getY() - l.from().y()) < 1.0;
        double near = l.kind() == HeroLinks.Kind.GLIDE ? AT_GLIDE_EDGE : AT_START;
        if (++approach > APPROACH_TICKS) {
            fail(l, "it could not get to the start");
            return;
        }
        if (!mob.onGround() || !onCell || d2 > near * near) {
            mob.getMoveControl().setWantedPosition(cx, l.from().y(), cz, d2 < 1 ? speedModifier * 0.6 : speedModifier);
            return;
        }
        String no = start(hero, mv, l);
        if (no != null) {
            fail(l, no);
            return;
        }
        running = l;
        runningPath = path;
        runningIndex = index;
        glideStarted = false;
        hurtDuring = false;
        approach = 0;
        mv.navOwned(true);
    }

    /** The link's action (null: it started; else why not). A glide starts when it walks off the edge. */
    private String start(HeroEntity hero, HeroMovement mv, HeroLinks.Link l) {
        Direction d = Direction.getNearest(l.to().x() - l.from().x(), 0, l.to().z() - l.from().z());
        BlockPos to = new BlockPos(l.to().x(), l.to().y(), l.to().z());
        return switch (l.kind()) {
            case JUMP_UP -> mv.jumpUp(d, WalkNodeEvaluator.getFloorLevel(level, to));
            case GAP -> mv.leapTo(to);
            case CLIMB -> mv.climb(d);
            case CLIMB_DOWN -> mv.descend(d);
            case CHIMNEY -> mv.chimney(to);
            case GLIDE -> mv.profile().glides() ? null : "it does not glide";
            case DROP -> null;
        };
    }

    /** A glide link: off the edge toward the landing, and the glide opens as it leaves the ground. */
    private void glideApproach(HeroEntity hero, HeroMovement mv) {
        HeroLinks.Link l = running;
        Vec3 heading = new Vec3(l.to().x() - l.from().x(), 0, l.to().z() - l.from().z());
        if (!mob.onGround()) {
            String no = mv.state() == HeroMovement.State.GLIDE ? null : mv.glide(heading);
            if (no == null) {
                glideStarted = true;
                mv.navOwned(true);
                return;
            }
            fail(l, no);
            running = null;
            return;
        }
        if (++approach > APPROACH_TICKS) {
            fail(l, "it did not get off the edge");
            running = null;
            return;
        }
        mob.getMoveControl().setWantedPosition(l.to().x() + 0.5, l.from().y(), l.to().z() + 0.5, speedModifier);
    }

    /** The action is over: on the landing, the path goes on; else the link is dead and the path searched again. */
    private void finish(HeroEntity hero) {
        HeroLinks.Link l = running;
        running = null;
        BlockPos at = mob.blockPosition();
        double floor = WalkNodeEvaluator.getFloorLevel(level, new BlockPos(l.to().x(), l.to().y(), l.to().z()));
        boolean ok;
        if (l.kind() == HeroLinks.Kind.GLIDE) {
            double dx = at.getX() - l.to().x(), dz = at.getZ() - l.to().z();
            ok = mob.onGround() && dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(mob.getY() - floor) <= 1.5;
        } else {
            // on the landing cell, or standing on it with its centre just over the edge
            net.minecraft.world.phys.AABB box = mob.getBoundingBox();
            boolean onColumn = box.minX < l.to().x() + 1 && box.maxX > l.to().x() && box.minZ < l.to().z() + 1
                    && box.maxZ > l.to().z() && mob.onGround();
            ok = (at.getX() == l.to().x() && at.getZ() == l.to().z() || onColumn) && Math.abs(mob.getY() - floor) < 0.6;
        }
        if (ok) {
            linksTaken++;
            taken.merge(l.kind(), 1, Integer::sum);
            if (path != null && path == runningPath && !path.isDone() && path.getNextNodeIndex() == runningIndex) {
                path.advance();
            }
            return;
        }
        linksFailed++;
        if (!hurtDuring) {
            markDead(l); // a blow or a downing is not the link's fault
        }
        if (path == runningPath) {
            stop();
        }
    }

    private void fail(HeroLinks.Link l, String why) {
        linksFailed++;
        markDead(l);
        stop();
    }
}
