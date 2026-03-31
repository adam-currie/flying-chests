package com.fjjy.menu;

import java.util.function.Consumer;

import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.menu.FlyingChestDisplayNameHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * provides a menu that combines the player's inventory and the chest inventory
 */
public class CombinedFlyingChestInventoryMenuProvider implements MenuProvider {
    private final FlyingChestEntity chest;
    private final Consumer<Player> onClose;

    public CombinedFlyingChestInventoryMenuProvider(FlyingChestEntity chest, Consumer<Player> onClose) {
        this.chest = chest;
        this.onClose = onClose;
    }

    @Override
    public Component getDisplayName() {
        return FlyingChestDisplayNameHelper.getDisplayName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CombinedFlyingChestInventoryMenu(containerId, playerInventory, chest.getInventory()) {
            @Override
            public void removed(Player p) {
                super.removed(p);
                onClose.accept(p);
            }
        };
    }
}

