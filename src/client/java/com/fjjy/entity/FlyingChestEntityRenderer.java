package com.fjjy.entity;

import com.fjjy.config.FlyingChestTextureConfig;
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
import net.minecraft.util.Mth;

public class FlyingChestEntityRenderer extends EntityRenderer<FlyingChestEntity, FlyingChestEntityRenderer.FlyingChestRenderState> {

    private final ModelPart chestBase;
    private final ModelPart chestLid;
    private final ModelPart chestLock;

    // Wing renderers indexed by WingVariant.ordinal(): [0]=BEE, [1]=ALLAY, [2]=BAT
    private final WingRenderer[] wingRenderers;

    public FlyingChestEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;

        ModelPart chestRoot = context.bakeLayer(ModelLayers.CHEST);
        this.chestBase = chestRoot.getChild("bottom");
        this.chestLid = chestRoot.getChild("lid");
        this.chestLock = chestRoot.getChild("lock");

        this.wingRenderers = new WingRenderer[]{
            new BeeWingRenderer(context),
            new AllayWingRenderer(context),
            new BatWingRenderer(context)
        };
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
        state.wingTickTime = entity.tickCount + partialTick;
    }

    @Override
    public void submit(FlyingChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));

        // Render chest body + lid using direct model parts
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.63F, 0.63F, 0.63F);
        poseStack.translate(-0.5, 0.4, -0.5); 
        var chestRenderType = RenderTypes.entitySolid(FlyingChestTextureConfig.resolveChestTexture());
        this.chestLid.resetPose();
        this.chestLock.resetPose();
        this.chestLid.xRot = -(float) (Math.PI / 2.0) * state.lidAngle;
        this.chestLock.xRot = this.chestLid.xRot;
        submitNodeCollector.submitModelPart(this.chestBase, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLid, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLock, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();

        // Delegate wing rendering to the active variant's renderer
        int wIdx = FlyingChestTextureConfig.INSTANCE.wingsVariant.ordinal();
        wingRenderers[wIdx].render(poseStack, submitNodeCollector, state.lightCoords,
                state.wingTickTime, FlyingChestTextureConfig.resolveWingsTexture());

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
    }

    public static class FlyingChestRenderState extends EntityRenderState {
        public float yRot;
        public float wingTickTime;
        public float lidAngle;
    }
}