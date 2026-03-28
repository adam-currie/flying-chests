package com.fjjy;

import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.entity.FlyingChestEntityRenderer;
import com.fjjy.network.OpenFlyingChestPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class FlyingChestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(FlyingChests.FLYING_CHEST_ENTITY_TYPE, FlyingChestEntityRenderer::new);

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null || client.screen != null) return;
			if (!client.player.isAlive() || client.player.isPassenger()) return;
			if (client.gameMode != null && client.gameMode.getPlayerMode().isCreative()) return;

			if (!client.options.keyInventory.consumeClick()) return;

			FlyingChestEntity nearest = FlyingChests.findActiveChest(
				client.level, client.player.getUUID(), client.player.position()
			);

			if (nearest == null) {
				client.setScreen(new InventoryScreen(client.player));
				return;
			}

			ClientPlayNetworking.send(new OpenFlyingChestPayload(nearest.getId()));
		});
	}
}
