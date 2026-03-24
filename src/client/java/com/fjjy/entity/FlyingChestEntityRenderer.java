package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;


public class FlyingChestEntityRenderer extends EntityRenderer<FlyingChestEntity, FlyingChestEntityRenderer.FlyingChestRenderState> {
	private static final ItemStack CHEST_STACK = new ItemStack(Blocks.CHEST);
	private static final Identifier BEE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bee/bee.png");

	private static final float WING_FLAP_SPEED = 120.0F; // degrees per second
	private static final float WING_MAX_ANGLE_DEG = 25.0F;

	private final ItemModelResolver itemModelResolver;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	// A BeeRenderState is used only to drive BeeModel.setupAnim for wing pose reuse
	private final BeeModel beeModel;
	private final BeeRenderState wingAnimState = new BeeRenderState();

	public FlyingChestEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.35F;

		ModelPart beeRoot = context.bakeLayer(ModelLayers.BEE);
		this.beeModel = new BeeModel(beeRoot);
		ModelPart bone = beeRoot.getChild("bone");
		this.rightWing = bone.getChild("right_wing");
		this.leftWing  = bone.getChild("left_wing");
	}

	@Override
	public FlyingChestRenderState createRenderState() {
		return new FlyingChestRenderState();
	}

	@Override
	public void extractRenderState(FlyingChestEntity entity, FlyingChestRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yRot = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.getYHeadRot());
		state.chestItem.clear();
		this.itemModelResolver.updateForNonLiving(state.chestItem, CHEST_STACK, ItemDisplayContext.FIXED, entity);
		state.wingFlapAngle = ((entity.tickCount + partialTick) * WING_FLAP_SPEED) % 360.0F;
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

		// Render bee wings anchored to the top of the chest
		float flapRad = WING_MAX_ANGLE_DEG * Mth.DEG_TO_RAD * Mth.sin(state.wingFlapAngle * Mth.DEG_TO_RAD);
		rightWing.resetPose();
		leftWing.resetPose();
		rightWing.zRot = -flapRad;
		leftWing.zRot =  flapRad;

		// Position wings at the top edge of the chest (chest is ~0.875 units tall in FIXED context)
		poseStack.pushPose();
		poseStack.translate(0.0, 0.4, 0.0);
		var wingRenderType = RenderTypes.entityTranslucent(BEE_TEXTURE);
		submitNodeCollector.submitModelPart(rightWing, poseStack, wingRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		submitNodeCollector.submitModelPart(leftWing,  poseStack, wingRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();

		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
	}

	public static class FlyingChestRenderState extends EntityRenderState {
		public final ItemStackRenderState chestItem = new ItemStackRenderState();
		public float yRot;
		public float wingFlapAngle;
	}
}



