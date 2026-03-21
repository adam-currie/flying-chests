package com.fjjy.blockentity;

import com.fjjy.FlyingChests;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FlyingChestBlockEntity extends ChestBlockEntity {
	public FlyingChestBlockEntity(BlockPos pos, BlockState state) {
		// Anchor-only block entity; client visuals can key off this position later.
		super(FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, pos, state);
	}
}
