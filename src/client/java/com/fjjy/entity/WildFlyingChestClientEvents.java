package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;

import net.minecraft.client.Minecraft;

public class WildFlyingChestClientEvents {

    public static void onBreak(WildFlyingChestEntity entity) {
        // spawn break particles
        Minecraft.getInstance().level.addDestroyBlockEffect(
            entity.blockPosition(),
            FlyingChestClientConfig.INSTANCE.chestVariant.particleBlock.defaultBlockState()
        );
    }
}
