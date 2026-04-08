package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

public class FlyingChestEffects {
    private static void spawnBreakParticles(BlockState blockState, AABB box, double density, float particleScale) {
        if (blockState.isAir() || !blockState.shouldSpawnTerrainParticles()) return;
        Minecraft mc = Minecraft.getInstance();
        int countX = (int) (box.getXsize() * density);
        int countY = (int) (box.getYsize() * density);
        int countZ = (int) (box.getZsize() * density);
        for (int xx = 0; xx < countX; xx++) {
            for (int yy = 0; yy < countY; yy++) {
                for (int zz = 0; zz < countZ; zz++) {
                    double relX = xx / (double) countX;
                    double relY = yy / (double) countY;
                    double relZ = zz / (double) countZ;
                    mc.particleEngine.add(new TerrainParticle(
                        mc.level,
                        box.minX + xx / (double) countX * box.getXsize(),
                        box.minY + yy / (double) countY * box.getYsize(),
                        box.minZ + zz / (double) countZ * box.getZsize(),
                        relX, relY, relZ,
                        blockState
                    ).scale(particleScale));
                }
            }
        }
    }

    /**
     * to be called every tick while the local player is breaking a flying chest
     * @param hit the position where the chest is being hit (in world coordinates)
     * @param ticksProgressed a tick counter that starts at 0 when breaking starts and increases by 1 every tick until the chest is fully broken
     */
    public static void breakingEffect(Vec3 hit, int ticksProgressed) {
        Minecraft client = Minecraft.getInstance();

        // sound
        if (ticksProgressed % 4 == 0) {
            var sound = Blocks.CHEST.defaultBlockState().getSoundType();
            client.level.playLocalSound(
                hit.x, hit.y, hit.z,
                sound.getHitSound(), SoundSource.NEUTRAL,
                sound.getVolume()*.5f, sound.getPitch()*.5f, false
            );
        }

        // particles
        var blockState = FlyingChestClientConfig.INSTANCE.chestVariant.particleBlock.defaultBlockState();
        if (blockState.isAir() || !blockState.shouldSpawnTerrainParticles()) return;
        var rand = client.level.getRandom();
        Function<Double, Double> spread = n -> n + (rand.nextDouble() - 0.5) * .3;
        client.particleEngine.add(
            new TerrainParticle(client.level,
                spread.apply(hit.x), spread.apply(hit.y), spread.apply(hit.z),
                0, 0, 0,
                blockState)
            .setPower(0.2F).scale(0.5F));
    }

    /**
     * to be called when a flying chest is broken
     */
    public static void breakEffect(FlyingChestEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // spawn break particles centered at the entity's actual position
        spawnBreakParticles(
            FlyingChestClientConfig.INSTANCE.chestVariant.particleBlock.defaultBlockState(), 
            entity.getBoundingBox(), 6, 0.8f);

        // play break sound (couldn't put this in entity because playSound stops working after the entity is removed)
        var sound = Blocks.CHEST.defaultBlockState().getSoundType();
        mc.level.playLocalSound(
            entity.getX(), entity.getY(), entity.getZ(),
            sound.getBreakSound(), SoundSource.NEUTRAL,
            sound.getVolume(), sound.getPitch(), false
        );
    }
}

