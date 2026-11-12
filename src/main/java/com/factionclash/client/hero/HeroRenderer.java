package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws a hero with its GeckoLib model at the rig's render scale (0.56 for rig v3: 16 model pixels are about 9
 * world pixels); GeckoLib multiplies in the entity's {@code generic.scale}. A2 grows this into the full renderer
 * (face frames, glow and item layers, sash tint, level of detail).
 */
public class HeroRenderer extends GeoEntityRenderer<HeroEntity> {
    public HeroRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HeroModel());
        this.shadowRadius = 0.4F;
    }

    @Override
    public void preRender(PoseStack poseStack, HeroEntity hero, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        float s = (float) hero.entry().visual().renderScale();
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.preRender(poseStack, hero, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
