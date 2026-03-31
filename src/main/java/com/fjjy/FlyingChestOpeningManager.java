package com.fjjy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.menu.CombinedFlyingChestInventoryMenuProvider;
import com.fjjy.menu.RegularFlyingChestInventoryMenuProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;


/**
 * Handles opening/closing of the flying chest's inventory screen, including tracking which players have it open and playing sounds.
 */
public class FlyingChestOpeningManager {
    private final FlyingChestEntity chest;
    private final CombinedFlyingChestInventoryMenuProvider combinedMenuProvider;
    private final RegularFlyingChestInventoryMenuProvider regularMenuProvider;
    private final Set<UUID> openingPlayers = new HashSet<>();
    private final ContainerOpenersCounter openersCounter;

    public FlyingChestOpeningManager(FlyingChestEntity chest, Consumer<Boolean> onOpenStateChanged) {
        this.chest = chest;
        this.combinedMenuProvider = new CombinedFlyingChestInventoryMenuProvider(chest, this::onMenuClosed);
        this.regularMenuProvider = new RegularFlyingChestInventoryMenuProvider(chest, this::onMenuClosed);
        this.openersCounter = new ContainerOpenersCounter() {
            @Override
            protected void onOpen(Level level, BlockPos pos, BlockState blockState) {
                chest.playSound(SoundEvents.CHEST_OPEN, 0.5F,
                    chest.level().random.nextFloat() * 0.1F + 0.9F);
            }

            @Override
            protected void onClose(Level level, BlockPos pos, BlockState blockState) {
                chest.playSound(SoundEvents.CHEST_CLOSE, 0.5F,
                    chest.level().random.nextFloat() * 0.1F + 0.9F);
            }

            @Override
            protected void openerCountChanged(Level level, BlockPos pos, BlockState blockState, int oldCount, int newCount) {
                if ((oldCount == 0) != (newCount == 0)) {
                    onOpenStateChanged.accept(newCount > 0);
                }
            }

            @Override
            public boolean isOwnContainer(Player player) {
                return openingPlayers.contains(player.getUUID());
            }
        };
    }

    public void onMenuClosed(Player player) {
        openingPlayers.remove(player.getUUID());
        if (!chest.isRemoved()) {
            openersCounter.decrementOpeners(player, chest.level(), chest.blockPosition(),
                chest.level().getBlockState(chest.blockPosition()));
        }
    }

    public void openCombined(ServerPlayer player) {
        openingPlayers.add(player.getUUID());
        player.openMenu(combinedMenuProvider);
        openersCounter.incrementOpeners(player, chest.level(), chest.blockPosition(),
            chest.level().getBlockState(chest.blockPosition()), 8.0);
    }

    public void openRegular(ServerPlayer player) {
        openingPlayers.add(player.getUUID());
        player.openMenu(regularMenuProvider);
        openersCounter.incrementOpeners(player, chest.level(), chest.blockPosition(),
            chest.level().getBlockState(chest.blockPosition()), 8.0);
    }
}
