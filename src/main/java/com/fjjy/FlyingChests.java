package com.fjjy;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fjjy.block.FlyingChestBlock;
import com.fjjy.blockentity.FlyingChestBlockEntity;
import com.fjjy.config.FlyingChestServerConfig;
import com.fjjy.entity.TamedFlyingChestEntity;
import com.fjjy.entity.WildFlyingChestEntity;
import com.fjjy.menu.CombinedFlyingChestInventoryMenu;
import com.fjjy.network.FallbackInventoryPayload;
import com.fjjy.network.OpenFlyingChestCombinedPayload;
import com.fjjy.network.WildChestBreakStatePayload;

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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
	public static final double OWNER_TO_CHEST_SCANNING_RANGE = Math.sqrt(OWNER_TO_BASE_OPERATING_RANGE_SQR)*2 + 4;

	private static final Identifier FLYING_CHEST_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_base");
	private static final Identifier FLYING_CHEST_ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "flying_chest_entity");
	private static final Identifier WILD_FLYING_CHEST_ENTITY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "wild_flying_chest_entity");

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
	public static final EntityType<TamedFlyingChestEntity> FLYING_CHEST_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		FLYING_CHEST_ENTITY_ID,
		EntityType.Builder.of(TamedFlyingChestEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 0.75F)
			.clientTrackingRange(8)
			.updateInterval(3)
			.build(FLYING_CHEST_ENTITY_KEY)
	);

	private static final ResourceKey<EntityType<?>> WILD_FLYING_CHEST_ENTITY_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, WILD_FLYING_CHEST_ENTITY_ID);
	public static final EntityType<WildFlyingChestEntity> WILD_FLYING_CHEST_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		WILD_FLYING_CHEST_ENTITY_ID,
		EntityType.Builder.of(WildFlyingChestEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 0.75F)
			.clientTrackingRange(8)
			.updateInterval(3)
			.build(WILD_FLYING_CHEST_ENTITY_KEY)
	);

	private static final ResourceKey<Item> FLYING_CHEST_KEY =
		ResourceKey.create(Registries.ITEM, FLYING_CHEST_ID);
	public static final Item FLYING_CHEST = Registry.register(
		BuiltInRegistries.ITEM,
		FLYING_CHEST_ID,
		new BlockItem(FLYING_CHEST_BLOCK, new Item.Properties().setId(FLYING_CHEST_KEY))
	);

	public static final MenuType<CombinedFlyingChestInventoryMenu> COMBINED_CHEST_MENU_TYPE = Registry.register(
		BuiltInRegistries.MENU,
		Identifier.fromNamespaceAndPath(MOD_ID, "combined_chest"),
		new MenuType<>((id, inv) -> new CombinedFlyingChestInventoryMenu(id, inv, new SimpleContainer(54)), FeatureFlags.VANILLA_SET)
	);

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Returns the nearest owned FlyingChestEntity within operating range of playerPos,
	 * measured by distance from playerPos to each chest's base station.
	 * Works on both client and server since ownerUuid is synced via SynchedEntityData.
	 */
	@Nullable
	public static TamedFlyingChestEntity findActiveChest(Level level, UUID playerId, Vec3 playerPos) {
		AABB searchBox = AABB.ofSize(playerPos, 1, 1, 1)
			.inflate(FlyingChests.OWNER_TO_CHEST_SCANNING_RANGE);
		List<TamedFlyingChestEntity> candidates = level.getEntitiesOfClass(
			TamedFlyingChestEntity.class,
			searchBox,
			e -> playerId.equals(e.getOwnerUuid())
		);
		TamedFlyingChestEntity winner = null;
		double winnerDistSqr = Double.MAX_VALUE;
		for (TamedFlyingChestEntity chest : candidates) {
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
		FlyingChestServerConfig.init();
		FabricDefaultAttributeRegistry.register(FLYING_CHEST_ENTITY_TYPE, TamedFlyingChestEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WILD_FLYING_CHEST_ENTITY_TYPE, WildFlyingChestEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(FLYING_CHEST));

		PayloadTypeRegistry.playC2S().register(OpenFlyingChestCombinedPayload.TYPE, OpenFlyingChestCombinedPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(WildChestBreakStatePayload.TYPE, WildChestBreakStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(FallbackInventoryPayload.TYPE, FallbackInventoryPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenFlyingChestCombinedPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				// executed when the client wants to open their personal inventory with an active flying chest's inventory included
				var player = context.player();
				var entity = ((ServerLevel) player.level()).getEntity(payload.entityId());
				if (entity instanceof TamedFlyingChestEntity chest && chest.activeOwner == player) {
					//trigger client to open regular inventory + chest inventory
					chest.openCombinedInventory(player);
				} else {
					// race condition: chest deactivated between client keypress and server handling
					ServerPlayNetworking.send(player, new FallbackInventoryPayload());
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(WildChestBreakStatePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				var entity = ((ServerLevel) player.level()).getEntity(payload.entityId());
				if (!(entity instanceof WildFlyingChestEntity chest)) return;
				switch (payload.state()) {
					case START -> chest.onBreakStart(player);
					case STOP  -> chest.onBreakStop(player);
				}
			});
		});

		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

		LOGGER.info("Registered flying chest block/item and flying entity");
	}

	private int tickCounter = 0;

	private void onServerTick(MinecraftServer server) {
		if (++tickCounter % 10 != 0) return;
		for (ServerLevel level : server.getAllLevels()) {
			List<? extends TamedFlyingChestEntity> chests = level.getEntities(FLYING_CHEST_ENTITY_TYPE, e -> true);
			if (chests.isEmpty()) continue;
			for (ServerPlayer player : level.players()) {
				TamedFlyingChestEntity winner = findActiveChest(level, player.getUUID(), player.position());
				for (TamedFlyingChestEntity chest : chests) {
					if (player.getUUID().equals(chest.getOwnerUuid())) {
						boolean isWinner = chest == winner;
						chest.setActiveOwner(isWinner ? player : null);
					}
				}
			}
		}
	}
}
