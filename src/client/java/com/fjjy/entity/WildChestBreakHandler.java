package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;
import com.fjjy.config.FlyingChestServerConfig;
import com.fjjy.network.WildChestBreakStatePayload;
import com.fjjy.network.WildChestBreakStatePayload.State;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.TerrainParticle;

import java.util.function.Function;

public class WildChestBreakHandler {

    // -1 = no active break session. set to entity id while the crosshair is on a wild chest and attack is held
    private int activeBreakEntityId = -1;
    private int digParticleTicks = 0;

    private static final int DIG_PARTICLE_INTERVAL = 2;

    private void setActive(int entityId) {
        if (activeBreakEntityId == entityId) return;
        // Cancel any in-progress block mining when switching to a chest
        var mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (activeBreakEntityId != -1) {
            ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.STOP));
        }
        ClientPlayNetworking.send(new WildChestBreakStatePayload(entityId, State.START));
        activeBreakEntityId = entityId;
    }

    private void stop() {
        if (activeBreakEntityId == -1) return;
        ClientPlayNetworking.send(new WildChestBreakStatePayload(activeBreakEntityId, State.STOP));
        activeBreakEntityId = -1;
        digParticleTicks = 0;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // if not attacking or client disconnecting, end any progress
            if (!client.options.keyAttack.isDown() || client.player == null || client.level == null) {
                stop();
                return;
            }

            double maxDistSqr = Math.pow(FlyingChestServerConfig.INSTANCE.wildChestAttackRange, 2);

            //start/stop attacking based on crosshair position and distance check
            if (
                client.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof WildFlyingChestEntity wildChest
                && client.player.distanceToSqr(wildChest) <= maxDistSqr
            ) {
                setActive(wildChest.getId());

                if (++digParticleTicks >= DIG_PARTICLE_INTERVAL) {
                    digParticleTicks = 0;
                    var hit = ehr.getLocation();
                    var rand = client.level.getRandom();
                    var blockState = FlyingChestClientConfig.INSTANCE.chestVariant.particleBlock.defaultBlockState();
                    Function<Double, Double> spread = n -> n + (rand.nextDouble() - 0.5) * .3;
                    
                    client.particleEngine.add(
                        new TerrainParticle(client.level,
                            spread.apply(hit.x), spread.apply(hit.y), spread.apply(hit.z),
                            0.5, 0.5, 0.5,
                            blockState)
                        .setPower(0.2F).scale(0.5F));
                }
            } else {
                stop();
            }
        });
    }
}
