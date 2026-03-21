package com.fjjy;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.ChestRenderer;

public class FlyingChestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(FlyingChests.FLYING_CHEST_BLOCK_ENTITY_TYPE, ChestRenderer::new);
	}
}