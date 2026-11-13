package com.factionclash.hero;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.Decals;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Decal;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.leader.Palette;
import com.factionclash.net.HeroDecalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A hero's telegraph ground decals (HEROES_V2 6.4; task A6a). When a heavy, area or ultimate move's wind-up starts, the
 * server works out its shapes ({@link Decals#forMove}: where it lands and how large, never less than the ground the move
 * can hit), fits them in the hero's budget of 12 pieces and sends them, with their colour and the wind-up's length, to
 * the players within 32 blocks ({@link HeroDecalPayload}); their client draws them flat on the ground, growing from
 * nothing to full size as the wind-up ends ({@code client/hero/HeroDecalRenderer}). They go at the hit, when the move
 * is interrupted or ends, and when the hero dies; a client drops one the clear never reached
 * {@link Decals#LINGER_TICKS} after its wind-up. A rain draws one disc per impact as each is called, gone as it lands.
 *
 * <p>Presentation only (6.1): with no player within 32 blocks nothing is sent, and a move's timing, reach and damage
 * never depend on a decal. Instead of 6.4's block displays the client draws the shapes itself: nothing is spawned in
 * the world, so nothing is left behind by a crash or saved with a chunk, and no access transformer is needed.
 * {@code boss.telegraphDecals = false} sends none (the particle tells stay).
 */
public final class HeroTelegraphs {
    /** Decals go to the players within this many blocks of the hero (6.1). */
    public static final double PLAYER_RADIUS = 32;

    private final HeroEntity hero;
    private int nextId = 1;
    private final List<Live> live = new ArrayList<>(4);

    /** A decal that is up: its id, its pieces (for the budget), when it lapses and who was sent it. */
    private record Live(int id, int pieces, long lapsesAt, List<ServerPlayer> to) {
    }

    HeroTelegraphs(HeroEntity hero) {
        this.hero = hero;
    }

    // ------------------------------------------------------------------ what was sent (tests, the marker log)

    /** One decal sent ({@code shown}) or cleared, as the players near received it. */
    public record Sent(int hero, int id, boolean shown, Decal kind, int pieces, int growTicks, int color, float[] packed,
                       double x, double y, double z, List<UUID> to, long at) {
    }

    private static final int RECENT_MAX = 64;
    private static final ArrayDeque<Sent> RECENT = new ArrayDeque<>();

    /** The last decals shown and cleared on this server, oldest first (a copy). */
    public static synchronized List<Sent> recent() {
        return new ArrayList<>(RECENT);
    }

    private static synchronized void log(Sent s) {
        if (RECENT.size() >= RECENT_MAX) {
            RECENT.removeFirst();
        }
        RECENT.addLast(s);
    }

    // ------------------------------------------------------------------ showing and clearing

    private long now() {
        return hero.level().getGameTime();
    }

    /** The pieces of its decals that are up now. */
    public int livePieces() {
        int n = 0;
        for (Live l : live) {
            n += l.pieces();
        }
        return n;
    }

    /** How many of its decals are up now. */
    public int liveCount() {
        return live.size();
    }

    /** The players within 32 blocks of the hero (empty: nobody to show a decal to). */
    private List<ServerPlayer> playersNear(ServerLevel sl) {
        List<ServerPlayer> out = null;
        for (ServerPlayer p : sl.players()) {
            if (!p.isRemoved() && p.distanceToSqr(hero) <= PLAYER_RADIUS * PLAYER_RADIUS) {
                if (out == null) {
                    out = new ArrayList<>(2);
                }
                out.add(p);
            }
        }
        return out == null ? List.of() : out;
    }

    /**
     * The wind-up of {@code m} starts ({@code windupTicks} long, aimed at {@code aim}, {@code target} its target or
     * null): its decal, when it draws one and a player is near. Returns the decal's id (-1: none).
     */
    public int show(Move m, int windupTicks, LivingEntity target, Vec3 aim) {
        if (windupTicks <= 0 || !(hero.level() instanceof ServerLevel sl) || !FCConfig.BOSS_TELEGRAPH_DECALS.get()) {
            return -1;
        }
        Decal kind = m.telegraph() == null ? HeroCatalogue.defaultDecal(m.cls(), m.form()) : m.telegraph().shape();
        if (kind == Decal.NONE || kind == Decal.IMPACTS) {
            return -1; // light moves draw nothing; a rain draws each impact as it is called
        }
        List<ServerPlayer> to = playersNear(sl);
        if (to.isEmpty()) {
            return -1;
        }
        Vec3 o = hero.position();
        Vec3 f = HeroForms.facing(hero, aim);
        double tx = aim.x - o.x, tz = aim.z - o.z;
        List<Decals.Shape> shapes = Decals.forMove(m, new Decals.Aim(f.x, f.z, tx, tz, tx, tz, List.of()));
        return send(sl, to, m, kind, shapes, windupTicks, target != null && target.isAlive() ? target.position() : aim);
    }

    /** One rain impact called at {@code at}, landing {@code growTicks} later: its disc (-1: none). */
    public int impact(Move m, Vec3 at, int growTicks) {
        if (growTicks <= 0 || !(hero.level() instanceof ServerLevel sl) || !FCConfig.BOSS_TELEGRAPH_DECALS.get()
                || m.telegraph() != null && m.telegraph().shape() == Decal.NONE) {
            return -1;
        }
        List<ServerPlayer> to = playersNear(sl);
        if (to.isEmpty()) {
            return -1;
        }
        Vec3 o = hero.position();
        Decals.Shape disc = Decals.disc(at.x - o.x, at.z - o.z, Decals.IMPACT_RADIUS);
        return send(sl, to, m, Decal.IMPACTS, List.of(disc), growTicks, at);
    }

    private int send(ServerLevel sl, List<ServerPlayer> to, Move m, Decal kind, List<Decals.Shape> requested, int growTicks,
                     Vec3 far) {
        prune();
        List<Decals.Shape> shapes = Decals.fit(requested, livePieces());
        if (shapes.isEmpty()) {
            return -1;
        }
        Vec3 o = hero.position();
        double[] ys = new double[shapes.size()];
        for (int i = 0; i < shapes.size(); i++) {
            ys[i] = groundUnder(sl, shapes.get(i), o, far) - o.y;
        }
        float[] packed = Decals.pack(shapes, ys);
        int color = color(m);
        int id = nextId++;
        if (nextId <= 0) {
            nextId = 1;
        }
        HeroDecalPayload p = new HeroDecalPayload(hero.getId(), id, growTicks, color, o.x, o.y, o.z, packed);
        List<UUID> ids = new ArrayList<>(to.size());
        for (ServerPlayer pl : to) {
            PacketDistributor.sendToPlayer(pl, p);
            ids.add(pl.getUUID());
        }
        live.add(new Live(id, Decals.pieces(shapes), now() + growTicks + Decals.LINGER_TICKS, to));
        log(new Sent(hero.getId(), id, true, kind, Decals.pieces(shapes), growTicks, color, packed, o.x, o.y, o.z, ids, now()));
        return id;
    }

    /**
     * The ground a shape lies on: the top of the first solid block near the height of whichever is closer to the
     * shape's centre, the hero or the target, so a landing disc on a step lies on that step.
     */
    private double groundUnder(Level level, Decals.Shape s, Vec3 o, Vec3 far) {
        double cx = 0, cz = 0;
        for (Decals.Piece p : s.pieces()) {
            cx += p.x();
            cz += p.z();
        }
        cx /= s.pieces().size();
        cz /= s.pieces().size();
        double fx = far.x - o.x, fz = far.z - o.z;
        double toHero = cx * cx + cz * cz, toFar = (cx - fx) * (cx - fx) + (cz - fz) * (cz - fz);
        double nearY = toFar < toHero ? far.y : o.y;
        BlockPos top = BlockPos.containing(o.x + cx, nearY + 1.5, o.z + cz);
        for (int i = 0; i <= 5; i++) {
            BlockPos q = top.below(i);
            var shape = level.getBlockState(q).getCollisionShape(level, q);
            if (!shape.isEmpty()) {
                return q.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return nearY;
    }

    /**
     * The decal's colour (6.4): red for an ultimate, purple for an unblockable move (a grab always), else the move's
     * own telegraph colour or the hero's accent, as the nearest of the 16 dyes.
     */
    private int color(Move m) {
        HeroDef d = hero.definition();
        int base = m.telegraph() != null && m.telegraph().color() != HeroDef.NO_COLOR ? m.telegraph().color() : accent(d);
        HeroDef.Dye normal = base == HeroDef.NO_COLOR ? null : Palette.nearestDye(base);
        HeroDef.Dye dye = Decals.dye(m.cls(), m.unblockable() || m.form() == HeroDef.Form.GRAB, normal);
        return Palette.DYE_RGB.get(dye);
    }

    private static int accent(HeroDef d) {
        if (d == null) {
            return HeroDef.NO_COLOR;
        }
        if (d.v2() != null && d.v2().leader() != null && d.v2().leader().accent() != HeroDef.NO_COLOR) {
            return d.v2().leader().accent();
        }
        return d.color();
    }

    /** Decal {@code id} goes (the hit, an interrupt, the end of a move, an impact landing). */
    public void clear(int id) {
        if (id <= 0) {
            return;
        }
        for (int i = 0; i < live.size(); i++) {
            Live l = live.get(i);
            if (l.id() == id) {
                live.remove(i);
                sendClear(l, id);
                return;
            }
        }
    }

    /** Every decal of the hero goes (death, a reload). */
    public void clearAll() {
        for (Live l : live) {
            sendClear(l, l.id());
        }
        live.clear();
    }

    private void sendClear(Live l, int id) {
        HeroDecalPayload p = HeroDecalPayload.clear(hero.getId(), id);
        List<UUID> ids = new ArrayList<>(l.to().size());
        for (ServerPlayer pl : l.to()) {
            if (!pl.hasDisconnected()) {
                PacketDistributor.sendToPlayer(pl, p);
                ids.add(pl.getUUID());
            }
        }
        log(new Sent(hero.getId(), id, false, Decal.NONE, 0, 0, 0, new float[0], 0, 0, 0, ids, now()));
    }

    /** Decals whose clear never came (their move outlived them by far) leave the budget, as clients drop them. */
    private void prune() {
        if (live.isEmpty()) {
            return;
        }
        long now = now();
        live.removeIf(l -> now >= l.lapsesAt());
    }

    /** Every tick: the budget forgets decals that lapsed (no allocation while none is up). */
    void tick() {
        if (!live.isEmpty() && (hero.tickCount & 7) == 0) {
            prune();
        }
    }
}
