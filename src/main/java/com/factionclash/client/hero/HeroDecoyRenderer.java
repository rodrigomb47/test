package com.factionclash.client.hero;

import com.factionclash.hero.entity.HeroDecoy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A hero decoy's placeholder look (task A5): a plain humanoid in the default skin. A7 replaces it with the owner
 * hero's own model, its action two ticks late and no shadow (HEROES_V2 6.11).
 */
public class HeroDecoyRenderer extends HumanoidMobRenderer<HeroDecoy, HumanoidModel<HeroDecoy>> {
    private static final ResourceLocation SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public HeroDecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(HeroDecoy decoy) {
        return SKIN;
    }
}
