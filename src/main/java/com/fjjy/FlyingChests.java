package com.fjjy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fjjy.block.FlyingChestBlock;
import com.fjjy.blockentity.FlyingChestBlockEntity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class FlyingChests implements ModInitializer {
	public static final String MOD_ID = "flying-chests";

	// Registry ids/keys for the anchor block + item.
	public static final Identifier FLYING_CHEST_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest");
	private static final ResourceKey<net.minecraft.world.level.block.Block> FLYING_CHEST_BLOCK_KEY =
		ResourceKey.create(Registries.BLOCK, FLYING_CHEST_ID);
	private static final ResourceKey<Item> FLYING_CHEST_ITEM_KEY = ResourceKey.create(Registries.ITEM, FLYING_CHEST_ID);

	// Reserved for future client-only visual flight tuning.
	public static final int FLYING_VISUAL_MIN_RADIUS = 16;
	public static final int FLYING_VISUAL_MAX_RADIUS = 32;

	// Anchor block (static world position).
	public static final FlyingChestBlock FLYING_CHEST_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		FLYING_CHEST_ID,
		new FlyingChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).setId(FLYING_CHEST_BLOCK_KEY))
	);

	public static final BlockEntityType<ChestBlockEntity> FLYING_CHEST_BLOCK_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		FLYING_CHEST_ID,
		FabricBlockEntityTypeBuilder.<ChestBlockEntity>create(FlyingChestBlockEntity::new, FLYING_CHEST_BLOCK).build()
	);

	public static final Item FLYING_CHEST = Registry.register(
		BuiltInRegistries.ITEM,
		FLYING_CHEST_ID,
		new BlockItem(FLYING_CHEST_BLOCK, new Item.Properties().setId(FLYING_CHEST_ITEM_KEY))
	);

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Registered flying chest block and item: {}", FLYING_CHEST_ID);
	}
}