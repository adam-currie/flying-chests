package com.fjjy;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

import com.fjjy.entity.FlyingChestEntityRenderer;

public class FlyingChestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(FlyingChests.FLYING_CHEST_ENTITY_TYPE, FlyingChestEntityRenderer::new);
	}
}