package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

class BatWingRenderer extends WingRenderer {

    private static final float SPEED_MULTIPLIER = 0.5F;
    private static final float MAX_ANGLE_DEG    = 25.0F;
    private static final float TRANSLATE_Y      = 0.775F;
    private static final float TRANSLATE_Z      = -0.125F;
    private static final float RIGHT_OFFSET_X   = -0.1F;
    private static final float LEFT_OFFSET_X    =  0.1F;

    BatWingRenderer(EntityRendererProvider.Context context) {
        super(extractRight(context), extractLeft(context), SPEED_MULTIPLIER);
    }

    private static ModelPart extractRight(EntityRendererProvider.Context context) {
        return context.bakeLayer(ModelLayers.BAT).getChild("body").getChild("right_wing");
    }

    private static ModelPart extractLeft(EntityRendererProvider.Context context) {
        return context.bakeLayer(ModelLayers.BAT).getChild("body").getChild("left_wing");
    }

    @Override
    void render(PoseStack poseStack, SubmitNodeCollector collector,
                int lightCoords, float tickTime, Identifier texture) {
        float flap = flapRad(computeFlapAngle(tickTime), MAX_ANGLE_DEG);
        rightWing.resetPose();
        leftWing.resetPose();
        rightWing.yRot = -flap;
        leftWing.yRot  =  flap;

        var renderType = RenderTypes.entityTranslucent(texture);

        poseStack.pushPose();
        poseStack.translate(0.0, TRANSLATE_Y, TRANSLATE_Z);

        final var xRot = Axis.XP.rotation(2f);


        // Bat wings hang downward from the bat body in model space.
        // Rotate each wing ±90° around Y to fan them forward/backward.
        poseStack.pushPose();
        poseStack.translate(RIGHT_OFFSET_X, 0.0, 0.0);
        poseStack.mulPose(xRot);
        submitWing(rightWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(LEFT_OFFSET_X, 0.0, 0.0);
        poseStack.mulPose(xRot);
        submitWing(leftWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.popPose();
    }
}
