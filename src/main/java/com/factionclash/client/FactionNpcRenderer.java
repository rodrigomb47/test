package com.factionclash.client;

import com.factionclash.FactionClash;
import com.factionclash.npc.FactionNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Humanoid NPC renderer with faction skins, armour and held items; archers show the bow-draw pose. */
public class FactionNpcRenderer extends HumanoidMobRenderer<FactionNpc, PlayerModel<FactionNpc>> {
    public FactionNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(FactionNpc npc) {
        return ResourceLocation.fromNamespaceAndPath(FactionClash.MODID,
                "textures/entity/npc/" + npc.getFaction().id + "_" + Math.floorMod(npc.variant(), FactionNpc.VARIANTS) + ".png");
    }

    @Override
    public void render(FactionNpc npc, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<FactionNpc> m = getModel();
        m.rightArmPose = npc.isAiming() ? HumanoidModel.ArmPose.BOW_AND_ARROW
                : (npc.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM);
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(npc, yaw, partialTicks, pose, buffers, light);
    }
}
