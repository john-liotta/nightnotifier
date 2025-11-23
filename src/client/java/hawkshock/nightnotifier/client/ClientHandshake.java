package hawkshock.nightnotifier.client;

import hawkshock.nightnotifier.network.HandshakeAckPayload;
import hawkshock.nightnotifier.network.ModHandshakePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientHandshake {
    private ClientHandshake(){}
    public static volatile boolean authoritative = false;

    // Server-provided values (set on handshake ack)
    public static volatile int serverOverlayDuration = -1;
    public static volatile int serverRestThresholdTicks = -1;
    public static volatile int serverMorningLeadTicks = -1;
    public static volatile boolean serverEnablePhantomScreams = false;

    public static void register() {
        // Ensure payload types are registered locally before registering receivers or sending.
        try { HandshakeAckPayload.register(); } catch (Throwable ignored) {}
        try { ModHandshakePayload.register(); } catch (Throwable ignored) {}

        ClientPlayNetworking.registerGlobalReceiver(HandshakeAckPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    authoritative = payload.authoritative();
                    serverOverlayDuration = payload.overlayDuration();
                    serverRestThresholdTicks = payload.restThresholdTicks();
                    serverMorningLeadTicks = payload.morningWarningLeadTicks();
                    serverEnablePhantomScreams = payload.enablePhantomScreams();
                })
        );
    }

    public static void sendInitial() {
        try { ModHandshakePayload.register(); } catch (Throwable ignored) {}
        ClientPlayNetworking.send(new ModHandshakePayload("1.0.0", "overlay,sleep,insomnia"));
    }
}