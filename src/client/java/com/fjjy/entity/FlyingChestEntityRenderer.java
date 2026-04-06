package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;
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
import net.minecraft.client.resources.model.ModelBakery;
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
        state.breakStage = entity instanceof WildFlyingChestEntity wild ? wild.getBreakProgress() : 0;
    }

    @Override
    public void submit(FlyingChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));

        // Render chest body + lid
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.63F, 0.63F, 0.63F);
        poseStack.translate(-0.5, 0.4, -0.5);
        var chestRenderType = RenderTypes.entitySolid(FlyingChestClientConfig.resolveChestTexture());
        this.chestLid.resetPose();
        this.chestLock.resetPose();
        this.chestLid.xRot = -(float) (Math.PI / 2.0) * state.lidAngle;
        this.chestLock.xRot = this.chestLid.xRot;
        submitNodeCollector.submitModelPart(this.chestBase, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLid, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(this.chestLock, poseStack, chestRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        // replicating block breaking overlay: 6 flat quads, one per face, with the 16x16 destroy_stage texture
        if (state.breakStage > 0) {
            int crackIdx = Math.max(0, Math.min(ModelBakery.DESTROY_TYPES.size() - 1, state.breakStage - 1));
            var crumbleType = ModelBakery.DESTROY_TYPES.get(crackIdx);
            final float x0 = 0.06f, x1 = 0.94f;
            final float y0 = 0.0f, y1 = 0.875f;
            final float z0 = 0.06f, z1 = 0.94f;
            final int lm = state.lightCoords;
            final int ov = OverlayTexture.NO_OVERLAY;
            // sampling the breaking texture smaller because the chest texture is only 14x14 instead of the full 16x16
            final float u0 = 1/16f, u1 = 15/16f, v0 = 1/16f, v1 = 15/16f;
            submitNodeCollector.submitCustomGeometry(poseStack, crumbleType, (pose, buf) -> {
                // Top (y+)
                buf.addVertex(pose,x0,y1,z0).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,1,0);
                buf.addVertex(pose,x0,y1,z1).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,1,0);
                buf.addVertex(pose,x1,y1,z1).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,1,0);
                buf.addVertex(pose,x1,y1,z0).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,1,0);
                // Bottom (y-)
                buf.addVertex(pose,x0,y0,z1).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,-1,0);
                buf.addVertex(pose,x0,y0,z0).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,-1,0);
                buf.addVertex(pose,x1,y0,z0).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,-1,0);
                buf.addVertex(pose,x1,y0,z1).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,-1,0);
                // Front (z+)
                buf.addVertex(pose,x1,y1,z1).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,0,1);
                buf.addVertex(pose,x0,y1,z1).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,0,1);
                buf.addVertex(pose,x0,y0,z1).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,0,1);
                buf.addVertex(pose,x1,y0,z1).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,0,1);
                // Back (z-)
                buf.addVertex(pose,x0,y1,z0).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,0,-1);
                buf.addVertex(pose,x1,y1,z0).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,0,0,-1);
                buf.addVertex(pose,x1,y0,z0).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,0,-1);
                buf.addVertex(pose,x0,y0,z0).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,0,0,-1);
                // Right (x+)
                buf.addVertex(pose,x1,y1,z0).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,1,0,0);
                buf.addVertex(pose,x1,y1,z1).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,1,0,0);
                buf.addVertex(pose,x1,y0,z1).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,1,0,0);
                buf.addVertex(pose,x1,y0,z0).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,1,0,0);
                // Left (x-)
                buf.addVertex(pose,x0,y1,z1).setColor(-1).setUv(u0,v0).setOverlay(ov).setLight(lm).setNormal(pose,-1,0,0);
                buf.addVertex(pose,x0,y1,z0).setColor(-1).setUv(u1,v0).setOverlay(ov).setLight(lm).setNormal(pose,-1,0,0);
                buf.addVertex(pose,x0,y0,z0).setColor(-1).setUv(u1,v1).setOverlay(ov).setLight(lm).setNormal(pose,-1,0,0);
                buf.addVertex(pose,x0,y0,z1).setColor(-1).setUv(u0,v1).setOverlay(ov).setLight(lm).setNormal(pose,-1,0,0);
            });
        }
        poseStack.popPose();

        // Delegate wing rendering to the active variant's renderer
        int wIdx = FlyingChestClientConfig.INSTANCE.wingsVariant.ordinal();
        wingRenderers[wIdx].render(poseStack, submitNodeCollector, state.lightCoords,
                state.wingTickTime, FlyingChestClientConfig.resolveWingsTexture());

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
    }

    public static class FlyingChestRenderState extends EntityRenderState {
        public float yRot;
        public float wingTickTime;
        public float lidAngle;
        /** 0 = not breaking, 1-10 = crack stages (maps to destroy_stage_1 through destroy_stage_9) */
        public byte breakStage;
    }
}