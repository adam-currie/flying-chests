package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

public class WildFlyingChestClientEvents {

    public static void onBreak(WildFlyingChestEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // spawn break particles
        mc.level.addDestroyBlockEffect(
            entity.blockPosition(),
            FlyingChestClientConfig.INSTANCE.chestVariant.particleBlock.defaultBlockState()
        );

        // play break sound (couldn't put this in entity because playSound stops working after the entity is removed)
        var sound = Blocks.CHEST.defaultBlockState().getSoundType();
        mc.level.playLocalSound(
            entity.getX(), entity.getY(), entity.getZ(),
            sound.getBreakSound(), SoundSource.BLOCKS,
            sound.getVolume(), sound.getPitch(), false
        );
    }
}
