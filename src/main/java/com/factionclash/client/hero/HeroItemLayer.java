package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroKit;
import com.factionclash.hero.HeroLook;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.npc.ToolKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Vanilla items in a hero's hands (HEROES_V2 4.7): the main-hand item at {@code item_r}, the off-hand item at
 * {@code item_l} (pickaxe, axe, hoe, rod, food, a shield, a torch), at vanilla size (the model is drawn at the rig's
 * render scale, so the item is scaled back up by its inverse). A hero whose model carries its own weapon
 * ({@code weapon_drawn} bones) never shows a held weapon or its kit item as a vanilla item, and shows no item at all
 * while its weapon is drawn; a hero without one (the fists style, the neutral model) shows what it holds.
 */
public class HeroItemLayer extends BlockAndItemGeoLayer<HeroEntity> {
    public HeroItemLayer(GeoRenderer<HeroEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, HeroEntity hero) {
        return switch (bone.getName()) {
            case "item_r" -> shown(hero, hero.getMainHandItem());
            case "item_l" -> shown(hero, hero.getOffhandItem());
            default -> null;
        };
    }

    /** The item if it is drawn, otherwise null. */
    static ItemStack shown(HeroEntity hero, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        HeroLook look = HeroClientState.of(hero).look;
        boolean ownWeapon = !look.weaponDrawn().isEmpty();
        if (ownWeapon) {
            if (hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN) || HeroKit.isKit(stack) || isWeapon(stack)) {
                return null;
            }
        }
        return stack;
    }

    private static boolean isWeapon(ItemStack s) {
        return ToolKind.SWORD.matches(s) || ToolKind.BOW.matches(s) || ToolKind.CROSSBOW.matches(s);
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, HeroEntity hero) {
        return "item_l".equals(bone.getName()) ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, HeroEntity hero, MultiBufferSource bufferSource,
                                      float partialTick, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        float back = (float) (1.0 / hero.entry().visual().renderScale());
        poseStack.scale(back, back, back);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
        if (stack.getItem() instanceof ShieldItem) {
            boolean left = "item_l".equals(bone.getName());
            poseStack.translate(0, 0.125, left ? 0.25 : -0.25);
            if (left) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180F));
            }
        }
        super.renderStackForBone(poseStack, bone, stack, hero, bufferSource, partialTick, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
