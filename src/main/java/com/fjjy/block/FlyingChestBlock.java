package com.fjjy.block;

import com.fjjy.FlyingChests;
import com.fjjy.blockentity.FlyingChestBlockEntity;
import com.fjjy.entity.FlyingChestEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/**
 * A chest block that follows the owner when nearby and opens/closes with the owners regular inventory.
 *
 * <p>Creates a corresponding block entity when placed {@link FlyingChestBlockEntity} (visually just the base station) 
 * and a proper entity {@link FlyingChestEntity} for visually flying around.
 * The block entity stores the link so the flying entity can be cleaned up when the base block is removed.
 */
public class FlyingChestBlock extends ChestBlock {
	public FlyingChestBlock(Properties properties) {
		super(() -> FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FlyingChestBlockEntity(pos, state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getPlayer() == null) {
			// Only players can own a flying chest; cancel placement for non-player placers.
			return null;
		}
		return super.getStateForPlacement(context);
	}

	@Override
	public boolean chestCanConnectTo(BlockState state) {
		return false;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) {
			return;
		}
		// getStateForPlacement already blocked non-player placers, so placer is always a Player here.
		Player player = (Player) placer;
		// spawn the flying chest entity and link it to the placed block entity (despawns together)
		if (level.getBlockEntity(pos) instanceof FlyingChestBlockEntity flyingChestBlockEntity) {
			flyingChestBlockEntity.setLinkedEntityUuid(
				FlyingChestEntity.spawnFromPlacement((ServerLevel) level, pos, player)
				.getUUID()
			);
		}
	}
}
