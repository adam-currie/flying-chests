package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;


public class FlyingChestEntityRenderer extends EntityRenderer<FlyingChestEntity, FlyingChestEntityRenderer.FlyingChestRenderState> {
	private static final ItemStack CHEST_STACK = new ItemStack(Blocks.CHEST);

	private final ItemModelResolver itemModelResolver;

	public FlyingChestEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.35F;
	}

	@Override
	public FlyingChestRenderState createRenderState() {
		return new FlyingChestRenderState();
	}

	@Override
	public void extractRenderState(FlyingChestEntity entity, FlyingChestRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
		state.chestItem.clear();
		this.itemModelResolver.updateForNonLiving(state.chestItem, CHEST_STACK, ItemDisplayContext.FIXED, entity);
	}

	@Override
	public void submit(FlyingChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState cameraRenderState) {
		if (state.chestItem.isEmpty()) {
			return;
		}

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
		state.chestItem.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
	}

	public static class FlyingChestRenderState extends EntityRenderState {
		public final ItemStackRenderState chestItem = new ItemStackRenderState();
		public float yRot;
	}
}



