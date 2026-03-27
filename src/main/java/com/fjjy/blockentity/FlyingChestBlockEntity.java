package com.fjjy.blockentity;

import java.util.UUID;

import com.fjjy.FlyingChests;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;



public class FlyingChestBlockEntity extends BlockEntity {
	private UUID linkedEntityUuid;

	public FlyingChestBlockEntity(BlockPos pos, BlockState state) {
		super(FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, pos, state);
	}

	public UUID getLinkedEntityUuid() {
		return this.linkedEntityUuid;
	}

	public void setLinkedEntityUuid(UUID uuid) {
		this.linkedEntityUuid = uuid;
		this.setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.linkedEntityUuid != null) {
			output.store("LinkedEntityUuid", Codec.STRING, this.linkedEntityUuid.toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.linkedEntityUuid = input.read("LinkedEntityUuid", Codec.STRING)
			.map(UUID::fromString)
			.orElse(null);
	}

	@Override
	public void setRemoved() {
		if (this.level instanceof ServerLevel serverLevel && this.linkedEntityUuid != null) {
			Entity linked = serverLevel.getEntity(this.linkedEntityUuid);
			if (linked != null) {
				linked.discard();
			}
		}
		super.setRemoved();
	}
}
