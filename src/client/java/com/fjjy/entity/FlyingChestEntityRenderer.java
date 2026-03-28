package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class FlyingChestEntityRenderer extends EntityRenderer<FlyingChestEntity, FlyingChestEntityRenderer.FlyingChestRenderState> {
    private static final Identifier CHEST_TEXTURE = Identifier.withDefaultNamespace("textures/entity/chest/normal.png");
    private static final Identifier BEE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bee/bee.png");

    private static final float WING_FLAP_SPEED = 120.0F; // degrees per second
    private static final float WING_MAX_ANGLE_DEG = 25.0F;

    private final ModelPart chestBase;
    private final ModelPart chestLid;
    private final ModelPart chestLock;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public FlyingChestEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;

        ModelPart chestRoot = context.bakeLayer(ModelLayers.CHEST);
        this.chestBase = chestRoot.getChild("bottom");
        this.chestLid = chestRoot.getChild("lid");
        this.chestLock = chestRoot.getChild("lock");

        ModelPart beeRoot = context.bakeLayer(ModelLayers.BEE);
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
        Direction baseDirection = entity.getBaseDirection();
        state.yRot = entity.isDocked() ? baseDirection.toYRot() : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.getYHeadRot());
        state.lidAngle = Mth.lerp(partialTick, entity.lidAngleO, entity.lidAngle);
        state.wingFlapAngle = ((entity.tickCount + partialTick) * WING_FLAP_SPEED) % 360.0F;
    }

    @Override
    public void submit(FlyingChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));

        // Render chest body + lid using direct model parts
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.6F, 0.6F, 0.6F);
        poseStack.translate(-0.5, 0.4, -0.5); 
        var chestRenderType = RenderTypes.entitySolid(CHEST_TEXTURE);
        this.chestLid.resetPose();
        this.chestLock.resetPose();
        this.chestLid.xRot = -(float) (Math.PI / 2.0) * state.lidAngle;
        this.chestLock.xRot = this.chestLid.xRot;
        submitNodeCollector.submitModelPart(this.chestBase, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLid, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLock, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();

        // Render bee wings anchored to the top of the chest
        float flapRad = WING_MAX_ANGLE_DEG * Mth.DEG_TO_RAD * Mth.sin(state.wingFlapAngle * Mth.DEG_TO_RAD);
        rightWing.resetPose();
        leftWing.resetPose();
        rightWing.zRot = -flapRad;
        leftWing.zRot =  flapRad;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.9, 0.0);
        var wingRenderType = RenderTypes.entityTranslucent(BEE_TEXTURE);
        poseStack.pushPose();
        poseStack.translate(-0.1, 0.0, 0.0);
        submitNodeCollector.submitModelPart(rightWing, poseStack, wingRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.1, 0.0, 0.0);
        submitNodeCollector.submitModelPart(leftWing, poseStack, wingRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
        poseStack.popPose();

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
    }

    public static class FlyingChestRenderState extends EntityRenderState {
        public float yRot;
        public float wingFlapAngle;
        public float lidAngle;
    }
}