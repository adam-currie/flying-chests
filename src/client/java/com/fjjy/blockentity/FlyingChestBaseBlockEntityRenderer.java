package com.fjjy.blockentity;

import java.util.EnumSet;

import com.mojang.blaze3d.vertex.PoseStack;

import com.fjjy.config.FlyingChestTextureConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class FlyingChestBaseBlockEntityRenderer
        implements BlockEntityRenderer<FlyingChestBlockEntity, BlockEntityRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("flying-chests", "flying_chest_base"), "main");

    private static final Identifier CUSTOM_TEXTURE =
            Identifier.fromNamespaceAndPath("flying-chests", "textures/block/flying_chest_base.png");

    private static Identifier resolveTexture() {
        Identifier resourcePackOverride = Minecraft.getInstance().getResourceManager()
            .getResource(CUSTOM_TEXTURE).isPresent() ? CUSTOM_TEXTURE : null;
        return FlyingChestTextureConfig.resolveBaseTexture(resourcePackOverride);
    }

    private final ModelPart leg;
    private final ModelPart platformTop;
    private final ModelPart platformBody;

    public FlyingChestBaseBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.leg = root.getChild("leg");
        this.platformTop = root.getChild("platform_top");
        this.platformBody = root.getChild("platform_body");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Platform top face only — texOffs chosen to map UP face to the lid top region of the chest texture
        root.addOrReplaceChild("platform_top",
            CubeListBuilder.create().texOffs(-14, 0)
                .addBox(-7, 0, -7, 14, 3, 14, EnumSet.of(Direction.UP)),
            PartPose.ZERO);

        // Platform body (sides + bottom) — texOffs chosen for the chest body sides
        root.addOrReplaceChild("platform_body",
            CubeListBuilder.create().texOffs(0, 19)
                .addBox(-7, 0, -7, 14, 3, 14, EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)),
            PartPose.ZERO);

        // Leg: two lock boxes back-to-back (lock UV texOffs(0,0), size 2x4x1)
        root.addOrReplaceChild("leg",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1, -.05f, 0, 2, 4, 1)
                .texOffs(0, 0).addBox(-1, -.05f, -1, 2, 4, 1),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);

        var renderType = RenderTypes.entitySolid(resolveTexture());

        submitNodeCollector.submitModelPart(platformTop, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        submitNodeCollector.submitModelPart(platformBody, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        // 4 corners: 2 straight, 2 rotated 90° Y
        float[][] corners = {{-7f/16, 0, 7f/16}, {7f/16, 0, 7f/16}, {7f/16, 0, -7f/16}, {-7f/16, 0, -7f/16}};
        for (int i = 0; i < corners.length; i++) {
            poseStack.pushPose();
            poseStack.translate(corners[i][0], corners[i][1], corners[i][2]);
            if (i % 2 == 0) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            submitNodeCollector.submitModelPart(leg, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
