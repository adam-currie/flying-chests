package com.fjjy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WildChestBreakStatePayload(int entityId, State state) implements CustomPacketPayload {

	public enum State { START, PAUSE, STOP }

	public static final Type<WildChestBreakStatePayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("flying-chests", "wild_chest_break_state"));

	private static final StreamCodec<ByteBuf, State> STATE_CODEC = ByteBufCodecs.BYTE.map(
		b -> State.values()[b],
		s -> (byte) s.ordinal()
	);

	public static final StreamCodec<ByteBuf, WildChestBreakStatePayload> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT, WildChestBreakStatePayload::entityId,
			STATE_CODEC, WildChestBreakStatePayload::state,
			WildChestBreakStatePayload::new
		);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
