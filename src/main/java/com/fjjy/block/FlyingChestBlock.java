package com.fjjy.block;

import com.fjjy.blockentity.FlyingChestBlockEntity;
import com.fjjy.entity.TamedFlyingChestEntity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlyingChestBlock extends Block implements EntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty IS_DOCKED = BooleanProperty.create("is_docked");
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
	private static final VoxelShape BODY_SHAPE = Block.box(3.6, 4, 3.6, 12.4, 14, 12.4);
	private static final VoxelShape DOCKED_SHAPE = Shapes.or(SHAPE, BODY_SHAPE);

	public FlyingChestBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(IS_DOCKED, true));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FlyingChestBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, IS_DOCKED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getPlayer() == null) {
			return null;
		}
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(IS_DOCKED) ? DOCKED_SHAPE : SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(IS_DOCKED) ? DOCKED_SHAPE : SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(IS_DOCKED)) return InteractionResult.PASS;
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof FlyingChestBlockEntity blockEntity) {
			UUID uuid = blockEntity.getLinkedEntityUuid();
			if (uuid != null) {
				Entity entity = ((ServerLevel) level).getEntity(uuid);
				if (entity instanceof TamedFlyingChestEntity chest) {
					chest.openInventory(serverPlayer);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) {
			return;
		}
		Direction facing = state.getValue(FACING);
		TamedFlyingChestEntity entity = TamedFlyingChestEntity.spawnFromPlacement((ServerLevel) level, pos, facing);
		entity.setOwnerUuid(placer.getUUID());
		if (level.getBlockEntity(pos) instanceof FlyingChestBlockEntity blockEntity) {
			blockEntity.setLinkedEntityUuid(entity.getUUID());
		}
	}
}

