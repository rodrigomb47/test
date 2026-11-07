package com.factionclash.block;

import com.factionclash.campaign.CampaignManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The faction heart is the base's core (PROPOSED campaign rule): a base is lost when its heart is destroyed, and a
 * faction is eliminated when it has no bases left. The reporting station is deliberately NOT the defeat core.
 */
public class FactionHeartBlock extends Block {
    public FactionHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(this) && level instanceof ServerLevel sl) {
            CampaignManager.onHeartRemoved(sl, pos);
        }
    }
}
