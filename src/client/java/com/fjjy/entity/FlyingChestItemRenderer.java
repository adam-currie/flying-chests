package com.fjjy.entity;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.fjjy.config.FlyingChestTextureConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class FlyingChestItemRenderer implements SpecialModelRenderer<FlyingChestItemRenderer.ConfigSnapshot> {

	private final ModelPart chestBase;
	private final ModelPart chestLid;
	private final ModelPart chestLock;
	private final WingRenderer[] wingRenderers;

	private FlyingChestItemRenderer(ModelPart chestBase, ModelPart chestLid, ModelPart chestLock,
			WingRenderer[] wingRenderers) {
		this.chestBase = chestBase;
		this.chestLid = chestLid;
		this.chestLock = chestLock;
		this.wingRenderers = wingRenderers;
	}

	@Override
	public void submit(ConfigSnapshot data, ItemDisplayContext displayContext, PoseStack poseStack,
			SubmitNodeCollector collector, int lightCoords, int overlay, boolean hasFoil, int seed) {
		Minecraft mc = Minecraft.getInstance();
		boolean inHand = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				|| displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		float tickTime = mc.level != null ? mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true) : 0f;

		// Chest/Body
		var chestTex = FlyingChestTextureConfig.resolveChestTexture();
		var chestRenderType = RenderTypes.entitySolid(chestTex);
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.translate(-0.5, -0.5, -0.5);
		chestLid.resetPose();
		chestLock.resetPose();
		collector.submitModelPart(chestBase, poseStack, chestRenderType, lightCoords, OverlayTexture.NO_OVERLAY, null);
		collector.submitModelPart(chestLid, poseStack, chestRenderType, lightCoords, OverlayTexture.NO_OVERLAY, null);
		collector.submitModelPart(chestLock, poseStack, chestRenderType, lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();

		// Wings
		int wIdx = FlyingChestTextureConfig.INSTANCE.wingsVariant.ordinal();
		poseStack.pushPose();
		poseStack.translate(0.5, -0.42, 0.5);
        poseStack.scale(1.66F, 1.66F, 1.66F);
		if (inHand) {
			wingRenderers[wIdx].render(poseStack, collector, lightCoords,
					tickTime, FlyingChestTextureConfig.resolveWingsTexture());
		} else {
			wingRenderers[wIdx].renderResting(poseStack, collector, lightCoords,
					FlyingChestTextureConfig.resolveWingsTexture());
		}
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		consumer.accept(new Vector3f(-1f, -0.1f, -1f));
		consumer.accept(new Vector3f(1f, 1f, 1f));
	}

	@Override
	public ConfigSnapshot extractArgument(ItemStack stack) {
		FlyingChestTextureConfig cfg = FlyingChestTextureConfig.INSTANCE;
		return new ConfigSnapshot(cfg.chestVariant, cfg.chestUseResourcePack, cfg.wingsVariant, cfg.wingsUseResourcePack);
	}

	public record ConfigSnapshot(
		FlyingChestTextureConfig.ChestVariant chestVariant,
		boolean chestUseResourcePack,
		FlyingChestTextureConfig.WingVariant wingsVariant,
		boolean wingsUseResourcePack
	) {}

	public static class Unbaked implements SpecialModelRenderer.Unbaked {

		public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
			return CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext ctx) {
			var modelSet = ctx.entityModelSet();
			ModelPart chestRoot = modelSet.bakeLayer(ModelLayers.CHEST);
			WingRenderer[] wings = {
				new BeeWingRenderer(modelSet),
				new AllayWingRenderer(modelSet),
				new BatWingRenderer(modelSet)
			};
			return new FlyingChestItemRenderer(
				chestRoot.getChild("bottom"),
				chestRoot.getChild("lid"),
				chestRoot.getChild("lock"),
				wings
			);
		}
	}

}
