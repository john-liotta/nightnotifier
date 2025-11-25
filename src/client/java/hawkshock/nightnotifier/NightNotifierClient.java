package hawkshock.nightnotifier;

import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.shared.config.ClientDisplayConfig;
import hawkshock.nightnotifier.client.config.ConfigWatcher;
import hawkshock.nightnotifier.client.ui.OverlayManager;
import hawkshock.nightnotifier.client.ui.ProgressBarRenderer;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Keep IconRender import (used for sun/moon icons)
import hawkshock.nightnotifier.client.ui.IconRender;
import hawkshock.nightnotifier.client.ClientProbe;

import java.time.Instant;

public class NightNotifierClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("NightNotifierClient");
    private static boolean PROBE_PRINTED = false;

    private static ClientDisplayConfig CONFIG;
    private static Instant lastConfigTimestamp = Instant.EPOCH;

    private static boolean prevCanSleep = false;
    private static boolean sunriseWarned = false;

    private static final long NIGHT_START = 12541L;
    private static final long NIGHT_END   = 23458L;
    private static final long NIGHT_LENGTH = Math.floorMod(NIGHT_END - NIGHT_START, 24000L);

    @Override
    public void onInitializeClient() {
        LOG.info("[NightNotifier] Client init");
        CONFIG = ClientDisplayConfig.load();
        lastConfigTimestamp = ConfigWatcher.getConfigFileTimestamp();

        // ONE-TIME PROBE: prints DrawContext.drawTexture signatures to the run console.
        // Remove or comment out this line after you paste the printed signatures here.
        ClientProbe.printDrawContextSignatures();

        OverlayMessagePayload.registerTypeSafely();
        ClientHandshake.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sunriseWarned = false;
            prevCanSleep = false;
            ClientHandshake.sendInitial();
        });

        ClientPlayNetworking.registerGlobalReceiver(OverlayMessagePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    LOG.debug("[NightNotifier] Received overlay payload: type={}, duration={}, msg={}",
                            payload.eventType(), payload.duration(), payload.message());
                    if ("SUNRISE_IMMINENT".equals(payload.eventType())) {
                        int clientLead = CONFIG.morningWarningLeadTicks; 
                        int serverLead = ClientHandshake.serverMorningLeadTicks >= 0 ? ClientHandshake.serverMorningLeadTicks : 1200;
                        if (clientLead != serverLead) {
                            LOG.debug("[NightNotifier] Ignoring server sunrise overlay (serverLead={} != clientLead={})", serverLead, clientLead);
                            if (CONFIG.enablePhantomScreams) {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                OverlayManager.playSoundForEvent("SUNRISE_IMMINENT", mc, CONFIG);
                            }
                            return;
                        }
                    }
                    OverlayManager.set(payload.message(), payload.duration(), payload.eventType(), CONFIG);
                })
        );

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!PROBE_PRINTED) {
                ClientProbe.printDrawContextSignatures(); // one-time, on render thread
                PROBE_PRINTED = true;
            }
             lastConfigTimestamp = ConfigWatcher.checkAndReload(lastConfigTimestamp, NightNotifierClient::applyClientConfig);
             ProgressBarRenderer.render(drawContext, CONFIG);
             OverlayManager.render(drawContext);
         });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            boolean serverLeadKnown = ClientHandshake.serverMorningLeadTicks >= 0;
            if (ClientHandshake.authoritative && serverLeadKnown && ClientHandshake.serverMorningLeadTicks == CONFIG.morningWarningLeadTicks) return;
            if (client.world.getRegistryKey() != World.OVERWORLD) return;

            long dayTime = client.world.getTimeOfDay() % 24000L;
            boolean thundering = client.world.isThundering();
            boolean naturalNight = dayTime >= NIGHT_START && dayTime <= NIGHT_END;
            boolean canSleepNow = thundering || naturalNight;

            int lead = Math.max(0, CONFIG.morningWarningLeadTicks);
            long warningStartTick = Math.max(NIGHT_START, NIGHT_END - lead);

            if (canSleepNow && !prevCanSleep) {
                simulate("Nightfall", "CLIENT_SIM_NIGHT_START");
                sunriseWarned = false;
            }

            if (naturalNight && nothreading(client, thundering) && canSleepNow
                    && lead > 0 && dayTime >= warningStartTick && dayTime < NIGHT_END
                    && !sunriseWarned) {
                long remainingTicks = NIGHT_END - dayTime;
                if (remainingTicks < 0) remainingTicks += 24000L;
                int seconds = Math.max(0, (int) Math.ceil((double) remainingTicks / 20.0));
                simulate(seconds + "s Until Sunrise", "CLIENT_SIM_SUNRISE_IMMINENT");
                sunriseWarned = true;
            }

            if (!canSleepNow && prevCanSleep) sunriseWarned = false;

            OverlayManager.tick();
            prevCanSleep = canSleepNow;
        });
    }

    private static boolean nothreading(MinecraftClient client, boolean thundering) {
        return !thundering;
    }

    private static void renderProgressBar(DrawContext ctx) {
        ProgressBarRenderer.render(ctx, CONFIG);
    }

    private static void simulate(String label, String eventType) {
        if (!CONFIG.enableNotifications) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int tsr = mc.player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));

        int threshold = ClientHandshake.serverRestThresholdTicks >= 0 ? ClientHandshake.serverRestThresholdTicks : 56000;
        String msg;
        if (eventType != null && eventType.contains("SUNRISE")) {
            if (tsr >= threshold) {
                int nights = tsr / 24000;
                String nightsText = nights == 1 ? "1 night" : nights + " nights";
                msg = label + ": " + mc.player.getName().getString() + " hasn't slept for " + nightsText + ".";
            } else {
                msg = label;
            }
        } else {
            if (tsr < threshold) return;
            int nights = tsr / 24000;
            String nightsText = nights == 1 ? "1 night" : nights + " nights";
            msg = label + ": " + mc.player.getName().getString() + " hasn't slept for " + nightsText + ".";
        }

        int dur = (ClientHandshake.serverOverlayDuration >= 0)
                ? ClientHandshake.serverOverlayDuration
                : (CONFIG.defaultDuration > 0 ? CONFIG.defaultDuration : 100);
        OverlayManager.set(msg, dur, eventType, CONFIG);
    }

    public static void applyClientConfig(ClientDisplayConfig updated) {
        CONFIG = updated;
        OverlayManager.applyCurrentStyle(CONFIG);
        if (!CONFIG.enableNotifications) OverlayManager.set("", 0, null, CONFIG);
    }

    public static void reloadConfig() {
        CONFIG = ClientDisplayConfig.load();
        OverlayManager.applyCurrentStyle(CONFIG);
    }
}
