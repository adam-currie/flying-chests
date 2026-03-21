package com.fjjy.block;

import com.fjjy.FlyingChests;
import com.fjjy.blockentity.FlyingChestBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FlyingChestBlock extends ChestBlock {
	public FlyingChestBlock(Properties properties) {
		super(() -> FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FlyingChestBlockEntity(pos, state);
	}

	@Override
	public boolean chestCanConnectTo(BlockState state) {
		// Keep this as a stable single-block anchor for future client visual flight.
		return false;
	}
}
