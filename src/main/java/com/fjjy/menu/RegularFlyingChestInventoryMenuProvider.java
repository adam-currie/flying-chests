package com.fjjy.menu;

import java.util.function.Consumer;

import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.menu.FlyingChestDisplayNameHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * provides a menu for opening the chest (right clicking)
 */
public class RegularFlyingChestInventoryMenuProvider implements MenuProvider {
    private final FlyingChestEntity chest;
    private final Consumer<Player> onClose;

    public RegularFlyingChestInventoryMenuProvider(FlyingChestEntity chest, Consumer<Player> onClose) {
        this.chest = chest;
        this.onClose = onClose;
    }

    @Override
    public Component getDisplayName() {
        return FlyingChestDisplayNameHelper.getDisplayName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, chest.getInventory(), 6) {
            @Override
            public void removed(Player p) {
                super.removed(p);
                onClose.accept(p);
            }
        };
    }
}
