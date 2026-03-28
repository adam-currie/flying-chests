package com.fjjy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FallbackInventoryPayload() implements CustomPacketPayload {
	public static final Type<FallbackInventoryPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("flying-chests", "fallback_inventory"));

	public static final StreamCodec<ByteBuf, FallbackInventoryPayload> STREAM_CODEC =
		StreamCodec.unit(new FallbackInventoryPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
