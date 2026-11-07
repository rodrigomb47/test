package com.factionclash.block;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.info.BaseInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The central reporting station. Scouts publish reports here; players read base information. */
public class ReportingStationBlock extends Block {
    public ReportingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BaseState base = BaseInfo.baseAtPoint(CampaignData.get(sp.server), BaseState.STATION, pos);
            if (base == null) {
                sp.sendSystemMessage(Component.literal("This reporting station is not part of any faction base."));
            } else {
                BaseInfo.sendBaseInfo(sp, base);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
