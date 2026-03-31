package com.fjjy;

import org.jetbrains.annotations.Nullable;

import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.entity.FlyingChestEntityRenderer;
import com.fjjy.network.FallbackInventoryPayload;
import com.fjjy.network.OpenFlyingChestCombinedPayload;
import com.fjjy.screen.CombinedInventoryScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class FlyingChestsClient implements ClientModInitializer {

	@Nullable
	private static FlyingChestEntity activeClientChest = null;

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(FlyingChests.FLYING_CHEST_ENTITY_TYPE, FlyingChestEntityRenderer::new);
		MenuScreens.register(FlyingChests.COMBINED_CHEST_MENU_TYPE, CombinedInventoryScreen::new);

		/* listen for active state change on chests so we can track when the player associated with 
		   this client has an active chest that it should open with their inventory */
		FlyingChestEntity.onActiveStateChanged = chest -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.player == null) return;
			java.util.UUID localUuid = mc.player.getUUID();
			java.util.UUID chestOwner = chest.getOwnerUuid();
			if (chestOwner == null || !chestOwner.equals(localUuid)) return;

			if (chest.isActive()) {
				activeClientChest = chest;
			} else if (activeClientChest == chest) {
				activeClientChest = null;
			}
		};

		ClientPlayNetworking.registerGlobalReceiver(FallbackInventoryPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player != null) {
					context.client().setScreen(new InventoryScreen(context.client().player));
				}
			});
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (activeClientChest == null || client.screen != null) return;
			if (client.player == null || !client.player.isAlive() || client.player.isPassenger()) return;
			if (client.gameMode != null && client.gameMode.getPlayerMode().isCreative()) return;
			if (!client.options.keyInventory.consumeClick()) return;
			ClientPlayNetworking.send(new OpenFlyingChestCombinedPayload(activeClientChest.getId()));
		});
	}
}
