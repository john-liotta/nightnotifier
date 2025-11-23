package hawkshock.nightnotifier.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModHandshakePayload(String version, String features) implements CustomPayload {
    public static final Id<ModHandshakePayload> ID =
            new Id<>(Identifier.of("nightnotifier","handshake_c2s"));

    public static final PacketCodec<RegistryByteBuf, ModHandshakePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, ModHandshakePayload::version,
                    PacketCodecs.STRING, ModHandshakePayload::features,
                    ModHandshakePayload::new
            );

    public Id<? extends CustomPayload> getId() { return ID; }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }
}