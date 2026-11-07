package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.npc.FactionNpc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** Smelter: processes raw ores into ingots in the workshop furnaces using real fuel. */
public class SmelterJob extends CookingJob {
    public SmelterJob(FactionNpc npc) {
        super(npc);
    }

    public static boolean isOre(ItemStack s) {
        return s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER);
    }

    @Override
    protected RecipeType<?> recipeType() {
        return RecipeType.SMELTING;
    }

    @Override
    protected List<String> stationKeys() {
        return List.of(BaseState.FURNACE, BaseState.FURNACE_2);
    }

    @Override
    protected boolean wantsInput(ItemStack s) {
        return isOre(s);
    }

    @Override
    protected String verb() {
        return "Smelting";
    }

    @Override
    protected String noun() {
        return "smelt";
    }
}
