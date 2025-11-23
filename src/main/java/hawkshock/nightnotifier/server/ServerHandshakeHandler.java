package hawkshock.nightnotifier.server;

import hawkshock.nightnotifier.config.NightNotifierConfig;
import hawkshock.nightnotifier.network.HandshakeAckPayload;
import hawkshock.nightnotifier.network.ModHandshakePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerHandshakeHandler {
    private ServerHandshakeHandler(){}

    public static void register(NightNotifierConfig cfg) {
        ServerPlayNetworking.registerGlobalReceiver(ModHandshakePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                HandshakeAckPayload ack = new HandshakeAckPayload(
                        true,
                        cfg.overlayDuration,
                        cfg.restThresholdTicks,
                        cfg.morningWarningLeadTicks,
                        cfg.enablePhantomScreams
                );
                ServerPlayNetworking.send(player, ack);
            });
        });
    }
}