package com.factionclash.client.hero;

import com.factionclash.FactionClash;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroLook;
import com.factionclash.logic.hero.FaceRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a hero (HEROES_V2 4.6, 4.7, 4.9) with its GeckoLib model at the rig's render scale (0.56: 16 model pixels are
 * about 9 world pixels; GeckoLib multiplies in the entity's {@code generic.scale}).
 *
 * <ul>
 *   <li><b>Face frames</b>: the {@code eyes}, {@code mouth} and {@code cheeks} planes map frame 0 of their strip; while
 *   their cubes are drawn, every vertex's UV is shifted to the frame {@link HeroFace} chose, normalised by the baked
 *   model's texture size (GeckoLib bakes UVs as texel / size). The glow layer renders through the same path, so the
 *   glow mask's eye frames line up.</li>
 *   <li><b>Faction tint</b>: the bones the visual file lists as {@code tint} ({@code sash}, {@code fine_sash*}) are
 *   painted greyscale and drawn with the faction's colour as the vertex colour, in the same pass.</li>
 *   <li><b>Layers</b>: the glow mask ({@link HeroGlowLayer}) and vanilla items at {@code item_r}/{@code item_l}
 *   ({@link HeroItemLayer}).</li>
 *   <li><b>Level of detail</b> by camera distance before the animation pass ({@link HeroModel#updateLod}); vanilla
 *   frustum culling uses the entity's inflated culling box (1.5 blocks, 3.1).</li>
 * </ul>
 *
 * {@code -Dfactionclash.heroRenderStats=true} logs the average time per hero render every 600 renders (HEROES_V2 11).
 */
public class HeroRenderer extends GeoEntityRenderer<HeroEntity> {
    private static final boolean STATS = Boolean.getBoolean("factionclash.heroRenderStats");
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private float shiftU, shiftV;
    private BakedGeoModel drawing;
    private long statNanos;
    private int statCount;

    public HeroRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HeroModel());
        this.shadowRadius = 0.4F;
        addRenderLayer(new HeroGlowLayer(this));
        addRenderLayer(new HeroItemLayer(this));
    }

    @Override
    public void render(HeroEntity hero, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        long t0 = STATS ? System.nanoTime() : 0;
        super.render(hero, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (STATS) {
            statNanos += System.nanoTime() - t0;
            if (++statCount >= 600) {
                FactionClash.LOGGER.info("[Heroes] render: {} us per hero over {} renders", String.format(java.util.Locale.ROOT, "%.1f",
                        statNanos / 1000.0 / statCount), statCount);
                statNanos = 0;
                statCount = 0;
            }
        }
    }

    @Override
    public void preRender(PoseStack poseStack, HeroEntity hero, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        drawing = model;
        if (!isReRender) {
            HeroModel.updateLod(hero, Math.sqrt(this.entityRenderDispatcher.distanceToSqr(hero)));
        }
        float s = (float) hero.entry().visual().renderScale();
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.preRender(poseStack, hero, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** Face planes get their frame's UV shift; tint bones the faction colour. */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        HeroEntity hero = getAnimatable();
        if (hero == null || bone.isHidden() || bone.getCubes().isEmpty()) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
            return;
        }
        HeroClientState s = HeroClientState.of(hero);
        String name = bone.getName();
        int c = colour;
        FaceRules.Region region = null;
        int frame = 0;
        HeroLook.Face face = s.look.face();
        switch (name) {
            case "eyes" -> {
                region = face.eyes();
                frame = s.face.frames().eyes();
            }
            case "mouth" -> {
                region = face.mouth();
                frame = s.face.frames().mouth();
            }
            case "cheeks" -> {
                region = face.cheeks();
                frame = s.face.frames().cheeks();
            }
            default -> {
                if (s.tint.contains(name) || name.startsWith("fine_sash")) {
                    c = tint(colour, hero.getFaction().rgb);
                }
            }
        }
        if (region != null && frame != 0 && drawing != null) {
            shiftU = region.shiftU(frame, HeroModel.textureWidth(drawing));
            shiftV = region.shiftV(frame, HeroModel.textureHeight(drawing));
        }
        try {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, c);
        } finally {
            shiftU = 0;
            shiftV = 0;
        }
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (shiftU == 0 && shiftV == 0) {
            super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, colour);
            return;
        }
        for (GeoVertex vertex : quad.vertices()) {
            Vector3f position = vertex.position();
            Vector4f v = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0F));
            buffer.addVertex(v.x(), v.y(), v.z(), colour, vertex.texU() + shiftU, vertex.texV() + shiftV, packedOverlay, packedLight,
                    normal.x(), normal.y(), normal.z());
        }
    }

    /** {@code argb} with its colour multiplied by {@code rgb} (the texture is then multiplied by it in the same pass). */
    static int tint(int argb, int rgb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = r * ((rgb >> 16) & 0xFF) / 255;
        g = g * ((rgb >> 8) & 0xFF) / 255;
        b = b * (rgb & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Whether the client's resources have this file (cached until the next resource reload). */
    static boolean resourceExists(ResourceLocation rl) {
        synchronized (EXISTS) {
            return EXISTS.computeIfAbsent(rl, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
        }
    }

    /** Forgets what exists (a resource reload). */
    static void forgetResources() {
        synchronized (EXISTS) {
            EXISTS.clear();
        }
    }
}
