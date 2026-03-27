package com.fjjy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fjjy.block.FlyingChestBlock;
import com.fjjy.blockentity.FlyingChestBlockEntity;
import com.fjjy.entity.FlyingChestEntity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class FlyingChests implements ModInitializer {
	public static final String MOD_ID = "flying-chests";
	private static final Identifier FLYING_CHEST_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_base");
	private static final Identifier FLYING_CHEST_ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_entity");

	private static final ResourceKey<net.minecraft.world.level.block.Block> FLYING_CHEST_BLOCK_KEY =
		ResourceKey.create(Registries.BLOCK, FLYING_CHEST_ID);
	public static final FlyingChestBlock FLYING_CHEST_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		FLYING_CHEST_ID,
		new FlyingChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).setId(FLYING_CHEST_BLOCK_KEY))
	);

	public static final BlockEntityType<FlyingChestBlockEntity> FLYING_CHEST_BLOCK_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		FLYING_CHEST_ID,
		FabricBlockEntityTypeBuilder.create(FlyingChestBlockEntity::new, FLYING_CHEST_BLOCK).build()
	);

	private static final ResourceKey<EntityType<?>> FLYING_CHEST_ENTITY_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, FLYING_CHEST_ENTITY_ID);
	public static final EntityType<FlyingChestEntity> FLYING_CHEST_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		FLYING_CHEST_ENTITY_ID,
		EntityType.Builder.of(FlyingChestEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 0.9F)
			.clientTrackingRange(8)
			.updateInterval(3)
			.build(FLYING_CHEST_ENTITY_KEY)
	);

	private static final ResourceKey<Item> FLYING_CHEST_KEY =
		ResourceKey.create(Registries.ITEM, FLYING_CHEST_ID);
	public static final Item FLYING_CHEST = Registry.register(
		BuiltInRegistries.ITEM,
		FLYING_CHEST_ID,
		new BlockItem(FLYING_CHEST_BLOCK, new Item.Properties().setId(FLYING_CHEST_KEY))
	);

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(FLYING_CHEST_ENTITY_TYPE, FlyingChestEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(FLYING_CHEST));
		LOGGER.info("Registered flying chest block/item and flying entity");
	}
}