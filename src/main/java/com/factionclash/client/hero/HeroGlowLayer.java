package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.hero.FaceRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The glow mask (HEROES_V2 4.7): {@code <texture>_glowmask.png} (the {@code _p2} texture's own mask in phase 2 and
 * later) drawn full-bright over the model, through the renderer's own path, so the face planes' frame shift applies
 * and the glowing-eye frame lines up. Skipped when the hero has no mask, when its current texture's mask is missing,
 * and, for a mask that glows nowhere but the glowing-eye frame (the generator's {@code glow_outside_face} false),
 * whenever that frame is not showing.
 */
public class HeroGlowLayer extends AutoGlowingGeoLayer<HeroEntity> {
    public HeroGlowLayer(GeoRenderer<HeroEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, HeroEntity hero, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!hero.entry().visual().glowmask() && hero.entry().ownModel()) {
            return;
        }
        ResourceLocation texture = getTextureResource(hero);
        String path = texture.getPath();
        ResourceLocation mask = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path.substring(0, path.length() - 4) + "_glowmask.png");
        if (!HeroRenderer.resourceExists(mask)) {
            return;
        }
        HeroClientState s = HeroClientState.of(hero);
        if (!s.look.glowOutsideFace() && s.face.frames().eyes() != FaceRules.EYES_GLOWING) {
            return;
        }
        super.render(poseStack, hero, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
    }
}
