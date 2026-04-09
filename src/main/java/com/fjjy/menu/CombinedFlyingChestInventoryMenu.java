package com.fjjy.menu;

import java.util.List;
import java.util.Optional;

import com.fjjy.FlyingChests;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class CombinedFlyingChestInventoryMenu extends AbstractCraftingMenu {

    public static final int IMAGE_WIDTH  = 176;
    public static final int IMAGE_HEIGHT = 291;

    /**
     * Y offset where inventory.png is blitted.
     * generic_54.png is blitted from y=0..INVENTORY_OFFSET_Y (chest rows + separator only).
     * inventory.png is blitted at topPos + INVENTORY_OFFSET_Y (full 166px).
     */
    public static final int INVENTORY_OFFSET_Y = 122;

    // Slot indices
    public static final int CHEST_START          = 5;   // after result(0) + crafting(1-4)
    public static final int CHEST_SLOTS          = 54;
    public static final int ARMOR_START          = 59;
    public static final int OFFHAND_SLOT         = 63;
    public static final int PLAYER_STORAGE_START = 64;
    public static final int HOTBAR_START         = 91;
    public static final int TOTAL_SLOTS          = 100;

    // Slot pixel positions (all player-side slots shifted by INVENTORY_OFFSET_Y)
    // Crafting result: inventory.png (154, 28) + offset
    private static final int RESULT_X   = 154;
    private static final int RESULT_Y   = 28 + INVENTORY_OFFSET_Y;   // 158
    // Crafting grid top-left: inventory.png (98, 18) + offset
    private static final int CRAFTING_X = 98;
    private static final int CRAFTING_Y = 18 + INVENTORY_OFFSET_Y;   // 148

    private final Level level;
    private final Player player;
    private Slot craftingResultSlot;

    public CombinedFlyingChestInventoryMenu(int containerId, Inventory playerInventory, Container chestInventory) {
        super(FlyingChests.COMBINED_CHEST_MENU_TYPE, containerId, 2, 2);
        checkContainerSize(chestInventory, CHEST_SLOTS);
        level  = playerInventory.player.level();
        player = playerInventory.player;

        // Slot 0: crafting result
        craftingResultSlot = addResultSlot(player, RESULT_X, RESULT_Y);
        // Slots 1-4: 2x2 crafting inputs
        addCraftingGridSlots(CRAFTING_X, CRAFTING_Y);

        // Slots 5-58: chest (6 rows x 9)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(chestInventory, col + row * 9,
                    8 + col * 18, 18 + row * 18));
            }
        }

        // Slots 59-62: armor (head=39 to feet=36) with hint icons
        final Identifier[] armorIcons = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
        };
        for (int k = 0; k < 4; k++) {
            final Identifier icon = armorIcons[k];
            addSlot(new Slot(playerInventory, 39 - k,
                    8, 8 + k * 18 + INVENTORY_OFFSET_Y) {
                @Override public Identifier getNoItemIcon() { return icon; }
            });
        }

        // Slot 63: offhand (inventory slot 40) with shield hint icon
        addSlot(new Slot(playerInventory, 40, 77, 62 + INVENTORY_OFFSET_Y) {
            @Override public Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });

        // Slots 64-90: player storage (3 rows x 9), inventory.png y=84+row*18 + offset
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                    8 + col * 18, 84 + row * 18 + INVENTORY_OFFSET_Y));
            }
        }

        // Slots 91-99: hotbar, inventory.png y=142 + offset
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                8 + col * 18, 142 + INVENTORY_OFFSET_Y));
        }

        slotsChanged(craftSlots);
    }

    // -------------------------------------------------------------------------

    @Override
    public Slot getResultSlot() { return craftingResultSlot; }

    @Override
    public List<Slot> getInputGridSlots() {
        return slots.subList(1, 5);
    }

    @Override
    protected Player owner() { return player; }

    @Override
    public RecipeBookType getRecipeBookType() { return RecipeBookType.CRAFTING; }

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents contents) {
        craftSlots.fillStackedContents(contents);
    }

    @Override
    public RecipeBookMenu.PostPlaceAction handlePlacement(
            boolean placeAll, boolean placeFiltered,
            RecipeHolder<?> recipe, ServerLevel level, Inventory playerInventory) {
        return super.handlePlacement(placeAll, placeFiltered, recipe, level, playerInventory);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftSlots && !level.isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) level;
            CraftingInput input = craftSlots.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = serverLevel
                    .getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
            ItemStack result = optional
                    .map(r -> r.value().assemble(input, serverLevel.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            resultSlots.setRecipeUsed(optional.orElse(null));
            resultSlots.setItem(0, result);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!level.isClientSide()) {
            for (int i = 0; i < craftSlots.getContainerSize(); i++) {
                ItemStack stack = craftSlots.removeItemNoUpdate(i);
                if (!stack.isEmpty()) player.drop(stack, false);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index == 0) {
            // Result -> player storage
            if (!moveItemStackTo(stack, PLAYER_STORAGE_START, TOTAL_SLOTS, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, result);
        } else if (index >= CHEST_START && index < ARMOR_START) {
            // Chest -> player storage
            if (!moveItemStackTo(stack, PLAYER_STORAGE_START, TOTAL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (index >= 1 && index < CHEST_START) {
            // Crafting inputs -> player storage
            if (!moveItemStackTo(stack, PLAYER_STORAGE_START, TOTAL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (index >= ARMOR_START && index < PLAYER_STORAGE_START) {
            // Armor/offhand -> player storage
            if (!moveItemStackTo(stack, PLAYER_STORAGE_START, TOTAL_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            // Player storage/hotbar -> chest first, then crafting inputs
            if (!moveItemStackTo(stack, CHEST_START, ARMOR_START, false)) {
                if (!moveItemStackTo(stack, 1, CHEST_START, false)) return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }
}
