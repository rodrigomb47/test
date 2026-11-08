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

    private boolean charcoal;

    /**
     * Out of coal with wood to spare: smelt logs into charcoal first, as a player would - one log burnt directly
     * smelts 1.5 items, one charcoal smelts 8. The charcoal goes to the storehouse and fuels the furnaces and smoker.
     */
    @Override
    protected void fetchExtras() {
        int fuelStock = com.factionclash.storage.FactionStorage.count(level(), base(), x -> x.is(Items.COAL) || x.is(Items.CHARCOAL));
        int logs = com.factionclash.storage.FactionStorage.count(level(), base(), x -> x.is(net.minecraft.tags.ItemTags.LOGS));
        // the same wood is kept back as from the cooks' fires (more while a tree plot still has to be enlarged)
        int reserve = Math.max(CHARCOAL_LOG_RESERVE, CookingJob.woodReserve(data(), base()));
        charcoal = fuelStock < 8 && logs > reserve;
        int have = Inv.count(npc.inventory(), x -> x.is(net.minecraft.tags.ItemTags.LOGS));
        if (charcoal && have < 8) {
            takeFromStorage(x -> x.is(net.minecraft.tags.ItemTags.LOGS), Math.min(8 - have, logs - reserve));
        }
    }

    /** PROPOSED: logs left for building and tools before any are made into charcoal. */
    static final int CHARCOAL_LOG_RESERVE = 24;

    @Override
    protected boolean hasExtras() {
        return charcoal && Inv.count(npc.inventory(), x -> x.is(net.minecraft.tags.ItemTags.LOGS)) > 0;
    }

    @Override
    protected boolean extraInput(ItemStack s) {
        return charcoal && s.is(net.minecraft.tags.ItemTags.LOGS);
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
