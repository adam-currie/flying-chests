package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

class AllayWingRenderer extends WingRenderer {

    private static final float SPEED_MULTIPLIER = 0.33F;
    private static final float MAX_ANGLE_DEG    = 28.0F;

    AllayWingRenderer(EntityRendererProvider.Context context) {
        super(extractRight(context), extractLeft(context), SPEED_MULTIPLIER);
    }

    private static ModelPart extractRight(EntityRendererProvider.Context context) {
        ModelPart baked = context.bakeLayer(ModelLayers.ALLAY);
        return baked.getChild("root").getChild("body").getChild("right_wing");
    }

    private static ModelPart extractLeft(EntityRendererProvider.Context context) {
        ModelPart baked = context.bakeLayer(ModelLayers.ALLAY);
        return baked.getChild("root").getChild("body").getChild("left_wing");
    }

    @Override
    void render(PoseStack poseStack, SubmitNodeCollector collector,
                int lightCoords, float tickTime, Identifier texture) {
        float flap = flapRad(computeFlapAngle(tickTime), MAX_ANGLE_DEG);
        rightWing.resetPose();
        leftWing.resetPose();
        
        leftWing.xRot = rightWing.xRot = .5f;
        leftWing.zRot = rightWing.zRot = (float) Math.PI;

        leftWing.yRot = -.55f - flap;
        rightWing.yRot  = .55f + flap;

        var renderType = RenderTypes.entityTranslucent(texture);

        poseStack.pushPose();
        poseStack.translate(0.0, .75, 0.0);

        poseStack.pushPose();
        poseStack.translate(-0.24F, 0.0, 0.0);
        submitWing(rightWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.24F, 0.0, 0.0);
        submitWing(leftWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.popPose();
    }
}
