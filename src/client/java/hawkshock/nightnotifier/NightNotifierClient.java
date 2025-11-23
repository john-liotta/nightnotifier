package hawkshock.nightnotifier;

import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.nightnotifier.client.config.ConfigWatcher;
import hawkshock.nightnotifier.client.ui.OverlayManager;
import hawkshock.nightnotifier.client.ui.ProgressBarRenderer;
import hawkshock.nightnotifier.config.ClientDisplayConfig;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class NightNotifierClient implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("NightNotifierClient");

    private static ClientDisplayConfig CONFIG;
    private static Instant lastConfigTimestamp = Instant.EPOCH;

    // Simulation state
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
                        int clientLead = CONFIG.morningWarningLeadTicks; // ticks
                        int serverLead = ClientHandshake.serverMorningLeadTicks >= 0 ? ClientHandshake.serverMorningLeadTicks : 1200;
                        if (clientLead != serverLead) {
                            LOG.debug("[NightNotifier] Ignoring server sunrise overlay (serverLead={} ticks != clientLead={} ticks)", serverLead, clientLead);

                            // Still play the phantom "warn" sound for modded client even when ignoring overlay,
                            // because server sound fallback only reaches unmodded clients.
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
            lastConfigTimestamp = ConfigWatcher.checkAndReload(lastConfigTimestamp, NightNotifierClient::applyClientConfig);
            ProgressBarRenderer.render(drawContext, CONFIG);
            OverlayManager.render(drawContext);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            boolean serverLeadKnown = ClientHandshake.serverMorningLeadTicks >= 0;
            if (ClientHandshake.authoritative && serverLeadKnown && ClientHandshake.serverMorningLeadTicks == CONFIG.morningWarningLeadTicks) {
                return;
            }

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

            if (naturalNight && !thundering && canSleepNow
                    && lead > 0
                    && dayTime >= warningStartTick && dayTime < NIGHT_END
                    && !sunriseWarned) {
                long remainingTicks = NIGHT_END - dayTime;
                if (remainingTicks < 0) remainingTicks += 24000L;
                int seconds = Math.max(0, (int) Math.ceil((double) remainingTicks / 20.0));
                simulate(seconds + "s Until Sunrise", "CLIENT_SIM_SUNRISE_IMMINENT");
                sunriseWarned = true;
            }

            if (!canSleepNow && prevCanSleep) {
                sunriseWarned = false;
            }

            OverlayManager.tick();
            prevCanSleep = canSleepNow;
        });
    }

    // Keep a small delegator so other code can still call renderProgressBar if needed.
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
        lastConfigTimestamp = Instant.now();
        OverlayManager.applyCurrentStyle(CONFIG);
        if (!CONFIG.enableNotifications) {
            // clear overlay state on disable
            OverlayManager.set("", 0, null, CONFIG);
        }
    }

    public static void reloadConfig() {
        CONFIG = ClientDisplayConfig.load();
        lastConfigTimestamp = ConfigWatcher.getConfigFileTimestamp();
        OverlayManager.applyCurrentStyle(CONFIG);
    }

    // Remaining config watcher moved into ConfigWatcher; keep helper to trigger periodic checks from the HUD loop
    private static void checkExternalConfigModification() {
        lastConfigTimestamp = ConfigWatcher.checkAndReload(lastConfigTimestamp, NightNotifierClient::applyClientConfig);
    }
}