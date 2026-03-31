package com.fjjy.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class FlyingChestDisplayNameHelper {
    public static MutableComponent getDisplayName() {
        // only use a label when the current resource pack normally uses labels for these types of things
        return Component.translatable("container.chest").getString().isEmpty()
            ? Component.translatable("")
            : Component.translatable("container.flying-chests.flying_chest");
    }
}
