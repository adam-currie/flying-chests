package com.fjjy.entity;

import com.fjjy.config.FlyingChestConfig;
import com.fjjy.network.WildChestBreakStatePayload;
import com.fjjy.network.WildChestBreakStatePayload.State;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class WildChestBreakHandler {

    // -1 = no active break session. set to entity id while the crosshair is on a wild chest and attack is held
    private int activeBreakEntityId = -1;
    // -1 = no paused session. block being broken but crosshair temporarily looks away
    private int pausedBreakEntityId = -1;

    private void setActive(int entityId) {
        if (activeBreakEntityId == entityId) return;
        // Cancel any in-progress block mining when switching to a chest
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (activeBreakEntityId != -1) {
            ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.STOP));
            activeBreakEntityId = -1;
        } else if (pausedBreakEntityId != -1 && pausedBreakEntityId != entityId) {
            ClientPlayNetworking.send(new WildChestBreakStatePayload(pausedBreakEntityId, State.STOP));
        }
        ClientPlayNetworking.send(new WildChestBreakStatePayload(entityId, State.START));
        activeBreakEntityId = entityId;
        pausedBreakEntityId = -1;
    }

    private void pause() {
        if (activeBreakEntityId == -1) return;
        ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.PAUSE));
        pausedBreakEntityId = activeBreakEntityId;
        activeBreakEntityId = -1;
    }

    private void stop() {
        int entityId = getActiveOrPaused();
        if (entityId == -1) return;
        ClientPlayNetworking.send(new WildChestBreakStatePayload(entityId, State.STOP));
        activeBreakEntityId = -1;
        pausedBreakEntityId = -1;
    }
    
    private int getActiveOrPaused() {
        return activeBreakEntityId != -1 ? activeBreakEntityId : pausedBreakEntityId;
    }

    public void register() {
        /* 
         * Hold-to-break state machine:
         *   start/continue → crosshair on chest + attack held
         *   pause          → look away while attack still held
         *   stop           → attack key released, or out of range
         */
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // if not attacking or disconnecting, end any progress
            if (!client.options.keyAttack.isDown() || client.player == null || client.level == null) {
                stop();
                return;
            }

            double maxDistSqr = Math.pow(FlyingChestConfig.INSTANCE.wildChestAttackRange, 2);

            // find which wild chest (if any) the crosshair is on
            int currentTargetId = -1;
            var hit = client.hitResult;
            if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr
                    && ehr.getEntity() instanceof WildFlyingChestEntity wildChest) {
                if (client.player.distanceToSqr(wildChest) <= maxDistSqr) {
                    currentTargetId = wildChest.getId();
                }
            }

            if (currentTargetId != -1) {
                setActive(currentTargetId);
			} else {
                int entityId = getActiveOrPaused();
                if (entityId != -1) {
                    // check distance to see if it's gone out of range
                    var e = client.level.getEntity(entityId);
                    if (e == null || client.player.distanceToSqr(e) > maxDistSqr) {
                        stop();
                    } else if (hit instanceof net.minecraft.world.phys.BlockHitResult) {
                        // aiming at a block = player is mining — stop progress entirely
                        stop();
                    } else {
                        // still in range, just looked away at air/entity
                        pause();
                    }
                }
            }
        });
    }
}
