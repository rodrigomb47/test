package com.factionclash.client.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.Decals;
import com.factionclash.net.HeroDecalPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws heroes' telegraph ground decals (HEROES_V2 6.4; task A6a; client only). The server sends a decal's pieces, its
 * colour and its wind-up ({@link HeroDecalPayload}); each is drawn as flat, full-bright, translucent quads just above the
 * ground, growing from its grow point to full size over the wind-up (a readable charging timer), with a brighter rim
 * that firms up as it fills. A clear from the server removes it; one whose clear never came, or whose hero is gone or
 * dead here, is dropped. Nothing here decides anything: it is what a player sees of a move the server times alone.
 */
@EventBusSubscriber(modid = FactionClash.MODID, value = Dist.CLIENT)
public final class HeroDecalRenderer {
    private HeroDecalRenderer() {
    }

    /** One decal on screen: what the server sent and the client tick it arrived. */
    private record Shown(HeroDecalPayload p, long since) {
    }

    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final double[] CORNERS = new double[8];
    private static final float LIFT = (float) Decals.LIFT, RIM = 0.12F;

    /** A decal message (the network thread hands it to the client's thread first). */
    public static void receive(HeroDecalPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (p.isClear()) {
            SHOWN.removeIf(s -> s.p().hero() == p.hero() && (p.id() == 0 || s.p().id() == p.id()));
            return;
        }
        SHOWN.removeIf(s -> s.p().hero() == p.hero() && s.p().id() == p.id());
        SHOWN.add(new Shown(p, level.getGameTime()));
    }

    /** How many decals are up (tests, debugging). */
    public static int count() {
        return SHOWN.size();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SHOWN.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWN.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SHOWN.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        for (Iterator<Shown> it = SHOWN.iterator(); it.hasNext(); ) {
            Shown s = it.next();
            HeroDecalPayload p = s.p();
            double age = now - s.since() + partial;
            Entity hero = level.getEntity(p.hero());
            if (age > p.growTicks() + Decals.LINGER_TICKS || hero == null || !hero.isAlive()) {
                it.remove();
                continue;
            }
            double g = Decals.growth(age, p.growTicks());
            int r = (p.color() >> 16) & 0xFF, gr = (p.color() >> 8) & 0xFF, b = p.color() & 0xFF;
            int fill = (int) (60 + 70 * g), rim = (int) (110 + 120 * g);
            float[] pieces = p.pieces();
            int n = pieces.length / Decals.PIECE_FLOATS;
            for (int i = 0; i < n; i++) {
                Decals.corners(pieces, i, g, CORNERS);
                float y = (float) (p.y() + pieces[i * Decals.PIECE_FLOATS + 1] - cam.y) + LIFT + i * 0.0005F;
                float ox = (float) (p.x() - cam.x), oz = (float) (p.z() - cam.z);
                quad(vc, m, ox, y, oz, CORNERS, r, gr, b, fill);
                rim(vc, m, ox, y + 0.0004F, oz, CORNERS, r, gr, b, rim);
            }
        }
        ps.popPose();
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ox, float y, float oz, double[] c, int r, int g, int b, int a) {
        for (int k = 0; k < 4; k++) {
            vc.addVertex(m, ox + (float) c[k * 2], y, oz + (float) c[k * 2 + 1]).setColor(r, g, b, a);
        }
    }

    /** A thin band inside each edge: the decal's outline reads on any ground. */
    private static void rim(VertexConsumer vc, Matrix4f m, float ox, float y, float oz, double[] c, int r, int g, int b, int a) {
        for (int e = 0; e < 4; e++) {
            int f = e, t = (e + 1) % 4, o = (e + 2) % 4;
            double ax = c[f * 2], az = c[f * 2 + 1], bx = c[t * 2], bz = c[t * 2 + 1];
            // inward: toward the opposite corner of the edge's far end, a fixed width
            double ix = c[o * 2] - bx, iz = c[o * 2 + 1] - bz;
            double il = Math.sqrt(ix * ix + iz * iz);
            if (il < 1e-4) {
                continue;
            }
            double w = Math.min(RIM, il / 2);
            ix = ix / il * w;
            iz = iz / il * w;
            vc.addVertex(m, ox + (float) ax, y, oz + (float) az).setColor(r, g, b, a);
            vc.addVertex(m, ox + (float) bx, y, oz + (float) bz).setColor(r, g, b, a);
            vc.addVertex(m, ox + (float) (bx + ix), y, oz + (float) (bz + iz)).setColor(r, g, b, a);
            vc.addVertex(m, ox + (float) (ax + ix), y, oz + (float) (az + iz)).setColor(r, g, b, a);
        }
    }
}
