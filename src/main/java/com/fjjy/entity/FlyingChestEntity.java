package com.fjjy.entity;

import com.fjjy.FlyingChestOpeningManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public abstract class FlyingChestEntity extends PathfinderMob {

	private static final EntityDataAccessor<Boolean> IS_OPEN =
		SynchedEntityData.defineId(FlyingChestEntity.class, EntityDataSerializers.BOOLEAN);

	// Client-side lid animation (0.0 = closed, 1.0 = fully open)
	public float lidAngle;
	public float lidAngleO;

	private final SimpleContainer inventory = new SimpleContainer(54);

	public SimpleContainer getInventory() {
		return inventory;
	}

	protected final FlyingChestOpeningManager openingManager;

	public FlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.openingManager = new FlyingChestOpeningManager(this, open -> this.entityData.set(IS_OPEN, open));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_OPEN, false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.FLYING_SPEED, 0.10D)
			.add(Attributes.MOVEMENT_SPEED, 0.10D);
	}

	/** Whether this chest is currently docked at a base station. Wild chests are never docked. */
	public boolean isDocked() {
		return false;
	}

	/** Direction the chest's base station faces, used for yRot when docked. */
	public Direction getBaseDirection() {
		return Direction.NORTH;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation pathNavigation = new FlyingPathNavigation(this, level);
		pathNavigation.setCanOpenDoors(false);
		pathNavigation.setCanFloat(true);
		pathNavigation.setRequiredPathLength(48.0F);
		return pathNavigation;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.lidAngleO = this.lidAngle;
			if (this.entityData.get(IS_OPEN)) {
				this.lidAngle = Math.min(1.0F, this.lidAngle + 0.1F);
			} else {
				this.lidAngle = Math.max(0.0F, this.lidAngle - 0.1F);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			openingManager.openRegular(serverPlayer);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.chest").getString().isEmpty()
			? Component.empty()
			: Component.translatable("container.flying-chests.flying_chest");
	}

	@Override
	public void travel(Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	protected void playBlockFallSound() {
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	public LivingEntity.Fallsounds getFallSounds() {
		return new LivingEntity.Fallsounds(SoundEvents.EMPTY, SoundEvents.EMPTY);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Items", ItemContainerContents.CODEC,
			ItemContainerContents.fromItems(this.inventory.getItems()));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("Items", ItemContainerContents.CODEC)
			.ifPresent(contents -> contents.copyInto(this.inventory.getItems()));
	}
}
