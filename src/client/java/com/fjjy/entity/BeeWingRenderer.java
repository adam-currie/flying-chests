package com.fjjy.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

class BeeWingRenderer extends WingRenderer {

    private static final float SPEED_MULTIPLIER = 1.0F;
    private static final float MAX_ANGLE_DEG    = 25.0F;
    private static final float TRANSLATE_Y      = 0.9F;
    private static final float RIGHT_OFFSET_X   = -0.1F;
    private static final float LEFT_OFFSET_X    =  0.1F;

    BeeWingRenderer(EntityRendererProvider.Context context) {
        this(context.bakeLayer(ModelLayers.BEE));
    }

    BeeWingRenderer(EntityModelSet modelSet) {
        this(modelSet.bakeLayer(ModelLayers.BEE));
    }

    private BeeWingRenderer(ModelPart beeRoot) {
        super(beeRoot.getChild("bone").getChild("right_wing"),
              beeRoot.getChild("bone").getChild("left_wing"),
              SPEED_MULTIPLIER);
    }

    @Override
    void render(PoseStack poseStack, SubmitNodeCollector collector,
                int lightCoords, float tickTime, Identifier texture) {
        float flap = flapRad(computeFlapAngle(tickTime), MAX_ANGLE_DEG);
        rightWing.resetPose();
        leftWing.resetPose();
        rightWing.zRot = -flap;
        leftWing.zRot  =  flap;

        var renderType = RenderTypes.entityTranslucent(texture);

        poseStack.pushPose();
        poseStack.translate(0.0, TRANSLATE_Y, 0.0);

        poseStack.pushPose();
        poseStack.translate(RIGHT_OFFSET_X, 0.0, 0.0);
        submitWing(rightWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(LEFT_OFFSET_X, 0.0, 0.0);
        submitWing(leftWing, poseStack, collector, renderType, lightCoords);
        poseStack.popPose();

        poseStack.popPose();
    }
}
