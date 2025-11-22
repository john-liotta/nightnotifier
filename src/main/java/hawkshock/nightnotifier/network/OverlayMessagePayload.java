package hawkshock.nightnotifier.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> Client overlay notification.
 * Guarded against double registration (integrated server calls both main + client entrypoints).
 */
public record OverlayMessagePayload(String message, int duration, String eventType) implements CustomPayload {

	public static final Id<OverlayMessagePayload> ID =
			new CustomPayload.Id<>(Identifier.of("nightnotifier", "overlay_msg"));

	public static final PacketCodec<RegistryByteBuf, OverlayMessagePayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, OverlayMessagePayload::message,
					PacketCodecs.VAR_INT, OverlayMessagePayload::duration,
					PacketCodecs.STRING, OverlayMessagePayload::eventType,
					OverlayMessagePayload::new
			);

	private static boolean registered = false;

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/**
	 * Safe registration - ignores duplicate attempts (integrated server scenario).
	 */
	public static void registerTypeSafely() {
		if (registered) return;
		try {
			PayloadTypeRegistry.playS2C().register(ID, CODEC);
			registered = true;
		} catch (IllegalArgumentException ignored) {
			// Already registered by other entrypoint (server or client).
			registered = true;
		}
	}
}