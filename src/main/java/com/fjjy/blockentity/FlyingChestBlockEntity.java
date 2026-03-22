package com.fjjy.blockentity;

import java.util.UUID;

import com.fjjy.FlyingChests;
import com.fjjy.entity.FlyingChestEntity;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FlyingChestBlockEntity extends ChestBlockEntity {
	private UUID linkedEntityUuid;

	public FlyingChestBlockEntity(BlockPos pos, BlockState state) {
		super(FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, pos, state);
	}

	public UUID getLinkedEntityUuid() {
		return this.linkedEntityUuid;
	}

	public void setLinkedEntityUuid(UUID linkedEntityUuid) {
		this.linkedEntityUuid = linkedEntityUuid;
		this.setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("LinkedEntityUuid", Codec.STRING, this.linkedEntityUuid.toString());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.linkedEntityUuid = input.read("LinkedEntityUuid", Codec.STRING)
			.map(UUID::fromString)
			.orElseThrow(() -> new IllegalStateException("Missing LinkedEntityUuid while loading FlyingChestBlockEntity at " + this.getBlockPos()));
	}

	@Override
	public void setRemoved() {
		if (this.level instanceof ServerLevel serverLevel) {
			Entity linkedEntity = serverLevel.getEntity(this.linkedEntityUuid);
			linkedEntity.discard();
		}
		super.setRemoved();
	}
}
