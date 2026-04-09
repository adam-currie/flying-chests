package com.fjjy.blockentity;

import java.util.UUID;

import com.fjjy.FlyingChests;
import com.fjjy.entity.TamedFlyingChestEntity;

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
		return linkedEntityUuid;
	}

	public void setLinkedEntityUuid(UUID uuid) {
		linkedEntityUuid = uuid;
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (linkedEntityUuid != null) {
			output.store("LinkedEntityUuid", Codec.STRING, linkedEntityUuid.toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		linkedEntityUuid = input.read("LinkedEntityUuid", Codec.STRING)
			.map(UUID::fromString)
			.orElse(null);
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel serverLevel && linkedEntityUuid != null) {
			Entity linked = serverLevel.getEntity(linkedEntityUuid);
			if (linked != null && linked instanceof TamedFlyingChestEntity flyingChest) {
				flyingChest.snapToBase();
				flyingChest.performBreak(serverLevel);
			}
		}
		super.setRemoved();
	}
}
