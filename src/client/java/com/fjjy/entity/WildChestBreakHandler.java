package com.fjjy.entity;

import com.fjjy.config.FlyingChestServerConfig;
import com.fjjy.network.WildChestBreakStatePayload;
import com.fjjy.network.WildChestBreakStatePayload.State;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class WildChestBreakHandler {
    private int activeBreakEntityId = -1;
    private int ticksProgressed = 0;

    private void setActive(int entityId) {
        if (activeBreakEntityId == entityId) return;
        var mc = Minecraft.getInstance();

        // cancel any in-progress block mining when switching to a chest
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }

        // cancel any already in-progress breaking on another chest
        if (activeBreakEntityId != -1) {
            ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.STOP));
        }

        // start breaking the new chest
        ClientPlayNetworking.send(new WildChestBreakStatePayload(entityId, State.START));
        activeBreakEntityId = entityId;
    }

    private void stop() {
        if (activeBreakEntityId == -1) return;
        ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.STOP));
        activeBreakEntityId = -1;
        ticksProgressed = 0;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // if not attacking or client disconnecting, end any progress
            if (!client.options.keyAttack.isDown() || client.player == null || client.level == null) {
                stop();
                return;
            }

            double maxDistSqr = Math.pow(FlyingChestServerConfig.INSTANCE.wildChestAttackRange, 2);

            // start/stop attacking based on crosshair position and distance check
            if (
                client.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof WildFlyingChestEntity wildChest
                && client.player.distanceToSqr(wildChest) <= maxDistSqr
            ) {
                setActive(wildChest.getId());
                FlyingChestEffects.breakingEffect(ehr.getLocation(), ticksProgressed++);
            } else {
                stop();
            }
        });
    }
}
