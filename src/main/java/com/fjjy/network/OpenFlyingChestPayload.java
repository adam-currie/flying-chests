package com.fjjy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenFlyingChestPayload(int entityId) implements CustomPacketPayload {
	public static final Type<OpenFlyingChestPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("flying-chests", "open_chest"));

	public static final StreamCodec<ByteBuf, OpenFlyingChestPayload> STREAM_CODEC =
		StreamCodec.composite(ByteBufCodecs.INT, OpenFlyingChestPayload::entityId, OpenFlyingChestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
