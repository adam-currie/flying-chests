package com.fjjy.screen;

import com.fjjy.menu.CombinedFlyingChestInventoryMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

public class CombinedInventoryScreen extends AbstractContainerScreen<CombinedFlyingChestInventoryMenu> {

    // Vanilla textures -- automatically reskinned by any resource pack
    private static final Identifier CHEST_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");
    private static final Identifier INVENTORY_TEXTURE =
        AbstractContainerScreen.INVENTORY_LOCATION;

    public CombinedInventoryScreen(CombinedFlyingChestInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth      = CombinedFlyingChestInventoryMenu.IMAGE_WIDTH;
        this.imageHeight     = CombinedFlyingChestInventoryMenu.IMAGE_HEIGHT;
        this.titleLabelX     = 8;
        this.titleLabelY     = 6;
        // Push inventory label off-screen so it never renders
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight + 10;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Blit full generic_54.png — the inventory blit below covers the overlap
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE,
            x, y, 0.0f, 0.0f,
            this.imageWidth, 222,
            256, 256);

        // Blit inventory.png below, skipping its top 1px (srcY=1, height=165)
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
            x, y + CombinedFlyingChestInventoryMenu.INVENTORY_OFFSET_Y + 5, 0.0f, 5.0f,
            this.imageWidth, 166,
            256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render the player model
        int offsetY = CombinedFlyingChestInventoryMenu.INVENTORY_OFFSET_Y;
        int playerX1 = this.leftPos + 25;
        int playerY1 = this.topPos + offsetY + 14;
        int playerX2 = this.leftPos + 77;
        int playerY2 = this.topPos + offsetY + 76;
        int scale = 30;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics,
            playerX1, playerY1, playerX2, playerY2,
            scale, 0.0f,
            mouseX, mouseY,
            (LivingEntity) this.minecraft.player
        );

        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
