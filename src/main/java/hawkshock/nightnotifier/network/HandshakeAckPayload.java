package hawkshock.nightnotifier.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HandshakeAckPayload(boolean authoritative,
                                  int overlayDuration,
                                  int restThresholdTicks,
                                  int morningWarningLeadTicks,
                                  boolean enablePhantomScreams) implements CustomPayload {
    public static final Id<HandshakeAckPayload> ID =
            new Id<>(Identifier.of("nightnotifier","handshake_ack"));

    public static final PacketCodec<RegistryByteBuf, HandshakeAckPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BOOLEAN, HandshakeAckPayload::authoritative,
                    PacketCodecs.VAR_INT, HandshakeAckPayload::overlayDuration,
                    PacketCodecs.VAR_INT, HandshakeAckPayload::restThresholdTicks,
                    PacketCodecs.VAR_INT, HandshakeAckPayload::morningWarningLeadTicks,
                    PacketCodecs.BOOLEAN, HandshakeAckPayload::enablePhantomScreams,
                    HandshakeAckPayload::new
            );

    public Id<? extends CustomPayload> getId() { return ID; }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }
}