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

/** The world-map board: shows only knowledge the faction's scouts have delivered (spec 12). */
public class WorldMapBoardBlock extends Block {
    public WorldMapBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            CampaignData data = CampaignData.get(sp.server);
            BaseState base = null;
            for (BaseState b : data.bases.values()) {
                if (b.center.distSqr(pos) < 40 * 40 && b.isSettlement()) {
                    base = b;
                }
            }
            if (base == null) {
                sp.sendSystemMessage(Component.literal("This map board belongs to no faction."));
            } else {
                BaseInfo.sendMap(sp, base.faction);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
