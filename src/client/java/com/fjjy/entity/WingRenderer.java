package com.fjjy.entity;

import com.fjjy.config.FlyingChestClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

abstract class WingRenderer {

    private static final float BASE_FLAP_SPEED = 120.0F; // degrees per tick

    protected final ModelPart rightWing;
    protected final ModelPart leftWing;
    private final float speedMultiplier;

    WingRenderer(ModelPart rightWing, ModelPart leftWing, float speedMultiplier) {
        this.rightWing       = rightWing;
        this.leftWing        = leftWing;
        this.speedMultiplier = speedMultiplier;
    }

    /**
     * Renders both wings into the pose stack.
     *
     * @param poseStack   the current pose stack (already translated to chest-centre origin)
     * @param collector   the submit node collector
     * @param lightCoords packed light coordinates
     * @param tickTime    current tick time (tickCount + partialTick); flap angle is computed internally
     * @param texture     resolved texture identifier for this frame
     */
    abstract void render(PoseStack poseStack, SubmitNodeCollector collector,
                         int lightCoords, float tickTime, Identifier texture);

    /** Computes the current flap angle in degrees using this variant's speed and the global config multiplier. */
    protected float computeFlapAngle(float tickTime) {
        float speed = BASE_FLAP_SPEED * speedMultiplier * FlyingChestClientConfig.INSTANCE.flapSpeed;
        return (tickTime * speed) % 360.0F;
    }

    /** Renders the wings in their resting pose for non-hand display contexts (hotbar, inventory, ground). */
    abstract void renderResting(PoseStack poseStack, SubmitNodeCollector collector,
                                int lightCoords, Identifier texture);

    /** Helper: submit a single wing part with a given render type. */
    protected void submitWing(ModelPart part, PoseStack poseStack, SubmitNodeCollector collector,
                               net.minecraft.client.renderer.rendertype.RenderType renderType, int lightCoords) {
        collector.submitModelPart(part, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY, null);
    }

    /** Convenience: compute flap radians from a flap angle (degrees) and a per-variant max-angle constant. */
    protected static float flapRad(float flapAngle, float maxAngleDeg) {
        return maxAngleDeg * Mth.DEG_TO_RAD * Mth.sin(flapAngle * Mth.DEG_TO_RAD);
    }
}
