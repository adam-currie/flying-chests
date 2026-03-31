package com.fjjy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenFlyingChestCombinedPayload(int entityId) implements CustomPacketPayload {
	public static final Type<OpenFlyingChestCombinedPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("flying-chests", "open_chest"));

	public static final StreamCodec<ByteBuf, OpenFlyingChestCombinedPayload> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.INT, OpenFlyingChestCombinedPayload::entityId, OpenFlyingChestCombinedPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
