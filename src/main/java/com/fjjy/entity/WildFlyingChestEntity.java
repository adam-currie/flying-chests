package com.fjjy.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.level.Level;

public class WildFlyingChestEntity extends FlyingChestEntity {

	public WildFlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new WaterAvoidingRandomFlyingGoal(this, 1.0));
	}

	@Override
	public boolean isPickable() {
		return true;
	}
}
