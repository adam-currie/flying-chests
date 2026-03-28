package com.fjjy;

import java.util.Comparator;

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

			var playerId = client.player.getUUID();
			var owned = client.level.getEntitiesOfClass(
				FlyingChestEntity.class,
				client.player.getBoundingBox().inflate(FlyingChestEntity.OWNER_TO_BASE_OPERATING_RANGE),
				e -> {
					var owner = e.getOwnerInRange();
					return owner != null && owner.getUUID().equals(playerId);
				}
			);

			if (owned.isEmpty()) {
				client.setScreen(new InventoryScreen(client.player));
				return;
			}

			FlyingChestEntity nearest = owned.stream()
				.min(Comparator.comparingDouble(e -> client.player.distanceToSqr(e.getBaseStationPos())))
				.orElseThrow();
			ClientPlayNetworking.send(new OpenFlyingChestPayload(nearest.getId()));
		});
	}
}
