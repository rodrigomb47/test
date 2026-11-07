package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** Chef: cooks raw food in the smoker and bakes bread from wheat at the crafting table (spec 8). */
public class ChefJob extends CookingJob {
    private int breadTimer;

    public ChefJob(FactionNpc npc) {
        super(npc);
    }

    @Override
    protected RecipeType<?> recipeType() {
        return RecipeType.SMOKING;
    }

    @Override
    protected List<String> stationKeys() {
        return List.of(BaseState.SMOKER);
    }

    @Override
    protected boolean wantsInput(ItemStack s) {
        return s.get(DataComponents.FOOD) != null || s.is(Items.KELP);
    }

    @Override
    protected String verb() {
        return "Cooking";
    }

    @Override
    protected String noun() {
        return "cook";
    }

    /** Wheat kept in storage for other uses (builders make bedrolls from it), PROPOSED. */
    public static final int WHEAT_RESERVE = 12;

    @Override
    protected void fetchExtras() {
        int wheat = Inv.count(npc.inventory(), s -> s.is(Items.WHEAT));
        int stored = com.factionclash.storage.FactionStorage.count(level(), base(), s -> s.is(Items.WHEAT));
        int spare = Math.max(0, stored - WHEAT_RESERVE);
        if (wheat < 18 && spare >= 3) {
            int want = Math.min(18 - wheat, spare);
            takeFromStorage(s -> s.is(Items.WHEAT), want - want % 3);
        }
    }

    @Override
    protected boolean hasExtras() {
        return Inv.count(npc.inventory(), s -> s.is(Items.WHEAT)) >= 3;
    }

    @Override
    protected int keep(ItemStack s) {
        return 0;
    }

    @Override
    protected boolean sideWork() {
        if (Inv.count(npc.inventory(), s -> s.is(Items.WHEAT)) < 3) {
            return false;
        }
        if (++breadTimer >= 2) {
            breadTimer = 0;
            lookAt(base().point(BaseState.CRAFTING));
            if (Crafting.craft(npc.inventory(), Crafting.BREAD, 1) > 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
        }
        return true;
    }
}
