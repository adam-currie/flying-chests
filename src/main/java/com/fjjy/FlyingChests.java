package com.fjjy;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fjjy.block.FlyingChestBlock;
import com.fjjy.blockentity.FlyingChestBlockEntity;
import com.fjjy.entity.FlyingChestEntity;
import com.fjjy.network.OpenFlyingChestPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FlyingChests implements ModInitializer {
	public static final String MOD_ID = "flying-chests";

	public static final double OWNER_TO_BASE_OPERATING_RANGE = 32.0;
	public static final double OWNER_TO_BASE_OPERATING_RANGE_SQR =
		OWNER_TO_BASE_OPERATING_RANGE * OWNER_TO_BASE_OPERATING_RANGE;

	/* 	range to scan for entities when considering chests to activate, twice the
		operating range to account for cases like teleportation where the entity 
		is at the other end of the range, plus 4 to account for random pathing when following player*/
	public static final double OWNER_TO_CHEST_SCANNING_RANGE_SQR = OWNER_TO_BASE_OPERATING_RANGE_SQR*2 + 4*4;

	private static final Identifier FLYING_CHEST_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_base");
	private static final Identifier FLYING_CHEST_ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_entity");

	private static final ResourceKey<net.minecraft.world.level.block.Block> FLYING_CHEST_BLOCK_KEY =
		ResourceKey.create(Registries.BLOCK, FLYING_CHEST_ID);
	public static final FlyingChestBlock FLYING_CHEST_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		FLYING_CHEST_ID,
		new FlyingChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).noOcclusion().setId(FLYING_CHEST_BLOCK_KEY))
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

	/**
	 * Returns the nearest owned FlyingChestEntity within operating range of playerPos,
	 * measured by distance from playerPos to each chest's base station.
	 * Works on both client and server since ownerUuid is synced via SynchedEntityData.
	 */
	@Nullable
	public static FlyingChestEntity findActiveChest(Level level, UUID playerId, Vec3 playerPos) {
		AABB searchBox = AABB.ofSize(playerPos, 1, 1, 1)
			.inflate(FlyingChests.OWNER_TO_CHEST_SCANNING_RANGE_SQR);
		List<FlyingChestEntity> candidates = level.getEntitiesOfClass(
			FlyingChestEntity.class,
			searchBox,
			e -> playerId.equals(e.getOwnerUuid())
		);
		FlyingChestEntity winner = null;
		double winnerDistSqr = Double.MAX_VALUE;
		for (FlyingChestEntity chest : candidates) {
			Vec3 bsp = chest.getBaseStationPos();
			if (bsp == null) continue;
			double distSqr = playerPos.distanceToSqr(bsp);
			if (distSqr <= FlyingChests.OWNER_TO_BASE_OPERATING_RANGE_SQR && distSqr < winnerDistSqr) {
				winnerDistSqr = distSqr;
				winner = chest;
			}
		}
		return winner;
	}

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(FLYING_CHEST_ENTITY_TYPE, FlyingChestEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(FLYING_CHEST));

		PayloadTypeRegistry.playC2S().register(OpenFlyingChestPayload.TYPE, OpenFlyingChestPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenFlyingChestPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				var entity = ((ServerLevel) player.level()).getEntity(payload.entityId());
				if (entity instanceof FlyingChestEntity chest && chest.activeOwner == player) {
					player.openMenu(chest);
				}
			});
		});

		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

		LOGGER.info("Registered flying chest block/item and flying entity");
	}

	private int tickCounter = 0;

	private void onServerTick(MinecraftServer server) {
		if (++this.tickCounter % 10 != 0) return;
		for (ServerLevel level : server.getAllLevels()) {
			List<? extends FlyingChestEntity> chests = level.getEntities(FLYING_CHEST_ENTITY_TYPE, e -> true);
			if (chests.isEmpty()) continue;
			for (ServerPlayer player : level.players()) {
				FlyingChestEntity winner = findActiveChest(level, player.getUUID(), player.position());
				for (FlyingChestEntity chest : chests) {
					if (player.getUUID().equals(chest.getOwnerUuid())) {
						chest.activeOwner = (chest == winner) ? player : null;
					}
				}
			}
		}
	}
}
