package hawkshock.nightnotifier;

import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.nightnotifier.config.ClientDisplayConfig;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
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

    // Phantom sounds (client-side)
    private static SoundEvent phantomScream;
    private static SoundEvent phantomFallbackNight;
    private static SoundEvent phantomFallbackWarn;
    private static boolean soundsResolved = false;

    private static void resolveClientPhantomSounds() {
        if (soundsResolved) return;
        phantomScream = Registries.SOUND_EVENT.get(Identifier.of("minecraft","entity.phantom.scream"));
        if (phantomScream == SoundEvents.INTENTIONALLY_EMPTY) phantomScream = null;
        phantomFallbackNight = SoundEvents.ENTITY_PHANTOM_AMBIENT;
        phantomFallbackWarn  = SoundEvents.ENTITY_PHANTOM_SWOOP;
        soundsResolved = true;
    }

    @Override
    public void onInitializeClient() {
        LOG.info("[NightNotifier] Client init");
        CONFIG = ClientDisplayConfig.load();
        lastConfigTimestamp = getConfigFileTimestamp();

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
                    // If this is a server SUNRISE_IMMINENT, allow the client to override it when the client
                    // has chosen a different lead time. In that case the client will ignore the server message
                    // and display its own local notification at the client-configured time.
                    if ("SUNRISE_IMMINENT".equals(payload.eventType())) {
                        int clientLead = CONFIG.morningWarningLeadTicks; // ticks
                        // Use server-provided lead if known; otherwise assume server default 1200 ticks (60s).
                        int serverLead = ClientHandshake.serverMorningLeadTicks >= 0 ? ClientHandshake.serverMorningLeadTicks : 1200;
                        // If the client has explicitly chosen a different lead than the server, ignore the server overlay.
                        if (clientLead != serverLead) {
                            LOG.debug("[NightNotifier] Ignoring server sunrise overlay (serverLead={} ticks != clientLead={} ticks)", serverLead, clientLead);

                            // Still play the phantom "warn" sound for modded client even when ignoring overlay,
                            // because server sound fallback only reaches unmodded clients.
                            if (CONFIG.enablePhantomScreams) {
                                resolveClientPhantomSounds();
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc != null && mc.player != null && mc.world != null) {
                                    SoundEvent chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
                                    float vol = CONFIG.morningScreamVolume;
                                    if (vol < 0f) vol = 0f;
                                    if (vol > 3f) vol = 3f;
                                    if (chosen != null && vol > 0f) {
                                        mc.world.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                                chosen, SoundCategory.HOSTILE, vol, 1.0f);
                                    }
                                }
                            }

                            return;
                        }
                    }
                    OverlayMessage.set(payload.message(), payload.duration(), payload.eventType());
                })
        );

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            checkExternalConfigModification();
            renderProgressBar(drawContext);
            OverlayMessage.render(drawContext);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            // Autonomous simulation:
            // - If server is authoritative and the server's configured lead equals the client's, skip client simulation.
            // - If server is authoritative but configured lead differs, allow the client to simulate (client preference wins).
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

            // Decrement overlay ticks once per game tick (20 TPS) so durations configured in ticks behave correctly.
            OverlayMessage.tick();
            prevCanSleep = canSleepNow;
        });
    }

    private static void renderProgressBar(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        long tod = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        boolean thundering = mc.world.isThundering();
        boolean naturalNight = tod >= NIGHT_START && tod <= NIGHT_END;
        // Respect client preference for the progress bar (client-only feature).
        boolean effectiveBarEnabled = CONFIG != null ? CONFIG.enableProgressBar : true;
        boolean show = effectiveBarEnabled && (thundering || naturalNight);
        if (!show) return;

        // remaining ticks until sunrise (robust around wrap)
        long remainingTicks = Math.floorMod(NIGHT_END - tod, 24000L);
        if (remainingTicks < 0) remainingTicks += 24000L;
        float frac = NIGHT_LENGTH > 0 ? (float) remainingTicks / (float) NIGHT_LENGTH : 0f;
        frac = Math.max(0f, Math.min(1f, frac));

        int sw = mc.getWindow().getScaledWidth();
        // Reduce bar width to ~44% of previous responsive size (another ~33% reduction)
        int base = sw / 3;
        int barW = Math.max(120, Math.min(400, Math.round(base * 0.44f)));
        int barH = 10;
        int x = (sw - barW) / 2;
        int y = 8; // top center

        // Outer background (subtle dark)
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x90000000);

        // Choose color by fraction (medium -> dark -> light) and red when within client lead
        int clientLead = CONFIG.morningWarningLeadTicks; // ticks
        int color;
        if (remainingTicks <= clientLead) {
            color = 0xFFFF4444; // red
        } else if (frac > 0.66f) {
            color = 0xFF4A90E2; // medium blue
        } else if (frac > 0.33f) {
            color = 0xFF003366; // dark blue
        } else {
            color = 0xFF7FBFFF; // light blue
        }

        // Filled width (bar shrinks left->right). Anchor filled portion to the right so it visually
        // shrinks from left->right as time progresses.
        int fillW = Math.max(0, Math.round(barW * frac));
        int filledX = x + (barW - fillW); // anchor filled to right
        if (fillW > 0) {
            ctx.fill(filledX, y, x + barW, y + barH, color);
        }
        // Draw the empty portion on the left
        if (fillW < barW) {
            ctx.fill(x, y, filledX, y + barH, 0x40000000);
        }

        // Thin border around bar for visibility
        ctx.fill(x, y - 1, x + barW, y, 0xFF000000);
        ctx.fill(x, y + barH, x + barW, y + barH + 1, 0xFF000000);
        ctx.fill(x - 1, y - 1, x, y + barH + 1, 0xFF000000);
        ctx.fill(x + barW, y - 1, x + barW + 1, y + barH + 1, 0xFF000000);
    }

    private static void simulate(String label, String eventType) {
        if (!CONFIG.enableNotifications) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int tsr = mc.player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));

        // Determine threshold: prefer server-provided, fall back to sensible default (56000 ticks).
        int threshold = ClientHandshake.serverRestThresholdTicks >= 0 ? ClientHandshake.serverRestThresholdTicks : 56000;
        // For sunrise warning we always show the "seconds until morning" label (client preference),
        // but only append the "hasn't slept" shaming text if the player crossed the rest threshold.
        String msg;
        if (eventType != null && eventType.contains("SUNRISE")) {
            if (tsr >= threshold) {
                int nights = tsr / 24000;
                String nightsText = nights == 1 ? "1 night" : nights + " nights";
                msg = label + ": " + mc.player.getName().getString() + " hasn't slept for " + nightsText + ".";
            } else {
                // Show only the countdown label (no "0 nights" shaming)
                msg = label;
            }
        } else {
            // Nightfall / general events: only show shaming if threshold crossed (keeps original behavior)
            if (tsr < threshold) return;
            int nights = tsr / 24000;
            String nightsText = nights == 1 ? "1 night" : nights + " nights";
            msg = label + ": " + mc.player.getName().getString() + " hasn't slept for " + nightsText + ".";
        }

        int dur = (ClientHandshake.serverOverlayDuration >= 0)
                ? ClientHandshake.serverOverlayDuration
                : (CONFIG.defaultDuration > 0 ? CONFIG.defaultDuration : 100);
        OverlayMessage.set(msg, dur, eventType);
    }

    private static class OverlayMessage {
        private static Text message = null;
        private static int ticksRemaining = 0;
        private static int color = 0xFFFFFFFF;
        private static float scale = 1.0f;
        private static boolean styled = true;

        static void set(String msg, int serverDuration, String eventType) {
            if (!CONFIG.enableNotifications) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                if (mc.world.getRegistryKey() == World.NETHER && !CONFIG.showNetherNotifications) return;
                if (mc.world.getRegistryKey() == World.END && !CONFIG.showEndNotifications) return;
            }

            String adjusted = adjustOffenderDisplay(msg, eventType);
            message = Text.literal(adjusted);
            int chosen = (CONFIG.defaultDuration > 0) ? CONFIG.defaultDuration : serverDuration;
            ticksRemaining = Math.max(10, chosen > 0 ? chosen : 300);
            applyCurrentStyle();
            // Play phantom sounds for both server events and client-simulated events.
            // Use contains() so CLIENT_SIM_* event types also trigger the correct sound.
            if (CONFIG.enablePhantomScreams && (eventType != null)
                    && (eventType.contains("NIGHT_START") || eventType.contains("SUNRISE_IMMINENT"))) {
                playClientSound(eventType);
            }
        }

        // If user disabled showAllOffenders, strip "Others:" section.
        private static String adjustOffenderDisplay(String original, String eventType) {
            if (ClientHandshake.authoritative && !CONFIG.showAllOffenders) {
                int idx = original.indexOf(" Others:");
                if (idx > 0) {
                    return original.substring(0, idx).trim();
                }
            }
            return original;
        }

        static void applyCurrentStyle() {
            if (message == null) return;
            if (!CONFIG.enableNotifications) {
                message = null;
                ticksRemaining = 0;
                return;
            }
            styled = CONFIG.useClientStyle;
            if (styled) {
                color = parseColor(CONFIG.colorHex);
                float ts = CONFIG.textScale;
                if (ts < 0.5f) ts = 0.5f;
                if (ts > 2.5f) ts = 2.5f;
                scale = ts;
            } else {
                color = 0xFFFFFFFF;
                scale = 1.0f;
            }
        }

        private static void playClientSound(String eventType) {
            resolveClientPhantomSounds();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            SoundEvent chosen = null;
            float vol = 0f;

            // Accept both server event names and client-simulated variants (e.g. "CLIENT_SIM_SUNRISE_IMMINENT")
            if (eventType != null && eventType.contains("NIGHT_START")) {
                chosen = phantomScream != null ? phantomScream : phantomFallbackNight;
                vol = CONFIG.nightScreamVolume;
            } else if (eventType != null && eventType.contains("SUNRISE_IMMINENT")) {
                chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
                vol = CONFIG.morningScreamVolume;
            }

            if (vol < 0f) vol = 0f;
            if (vol > 3f) vol = 3f;

            if (chosen != null && vol > 0f && client.world != null) {
                client.world.playSound(
                        client.player,
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        chosen,
                        SoundCategory.HOSTILE,
                        vol,
                        1.0f
                );
            }
        }

        static void tick() {
            if (ticksRemaining > 0) ticksRemaining--;
            if (ticksRemaining == 0) message = null;
        }

        static void render(DrawContext ctx) {
            if (message == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer tr = client.textRenderer;
            if (tr == null) return;

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();
            int tw = tr.getWidth(message);
            int th = tr.fontHeight;

            int boxW = (int)(tw * scale);
            int boxH = (int)(th * scale);

            int anchorX;
            int anchorY;
            switch (CONFIG.anchor) {
                case "TOP_LEFT" -> { anchorX = 0; anchorY = 0; }
                case "TOP_RIGHT" -> { anchorX = sw - boxW; anchorY = 0; }
                case "BOTTOM_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = sh - boxH; }
                case "BOTTOM_LEFT" -> { anchorX = 0; anchorY = sh - boxH; }
                case "BOTTOM_RIGHT" -> { anchorX = sw - boxW; anchorY = sh - boxH; }
                case "TOP_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
                default -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
            }
            anchorX += CONFIG.offsetX;
            anchorY += CONFIG.offsetY;

            int padX = styled ? 6 : 0;
            int padY = styled ? 4 : 0;
            int bgColor = styled ? 0x90000000 : 0x00000000;

            if (scale != 1.0f && tryScaleDraw(ctx, tr, message, anchorX, anchorY, tw, th, padX, padY, bgColor, color, scale)) return;

            if (styled && bgColor != 0) {
                ctx.fill(anchorX - padX, anchorY - padY, anchorX + tw + padX, anchorY + th + padY, bgColor);
            }
            ctx.drawTextWithShadow(tr, message, anchorX, anchorY, color);
        }

        private static boolean tryScaleDraw(DrawContext ctx,
                                            TextRenderer tr,
                                            Text msg,
                                            int topLeftX,
                                            int topLeftY,
                                            int unscaledW,
                                            int unscaledH,
                                            int padX,
                                            int padY,
                                            int bgColor,
                                            int textColor,
                                            float s) {
            Object stack = ctx.getMatrices();
            Class<?> c = stack.getClass();
            Method push = find(c, "pushMatrix", "push");
            Method pop = find(c, "popMatrix", "pop");
            Method scaleM = findScale(c);
            Method translateM = findTranslate(c);
            if (push == null || pop == null || scaleM == null || translateM == null) return false;
            try {
                push.invoke(stack);
                translateM.invoke(stack, (float) topLeftX, (float) topLeftY);
                scaleM.invoke(stack, s, s);
                if (styled && bgColor != 0) {
                    ctx.fill(-padX, -padY, unscaledW + padX, unscaledH + padY, bgColor);
                }
                ctx.drawTextWithShadow(tr, msg.asOrderedText(), 0, 0, textColor);
                pop.invoke(stack);
                return true;
            } catch (Throwable ignored) {
                try { pop.invoke(stack); } catch (Throwable ignored2) {}
                return false;
            }
        }

        private static Method find(Class<?> c, String... names) {
            for (String n : names)
                for (Method m : c.getMethods())
                    if (m.getName().equals(n)) return m;
            return null;
        }
        private static Method findScale(Class<?> c) {
            for (Method m : c.getMethods())
                if (m.getName().equals("scale") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == float.class
                        && m.getParameterTypes()[1] == float.class)
                    return m;
            return null;
        }
        private static Method findTranslate(Class<?> c) {
            for (Method m : c.getMethods())
                if (m.getName().equals("translate") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == float.class
                        && m.getParameterTypes()[1] == float.class)
                    return m;
            return null;
        }
    }

    private static int parseColor(String hex) {
        if (hex == null) return 0xFFFFFFFF;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) {}
        return 0xFFFFFFFF;
    }

    public static void applyClientConfig(ClientDisplayConfig updated) {
        CONFIG = updated;
        lastConfigTimestamp = Instant.now();
        OverlayMessage.applyCurrentStyle();
        if (!CONFIG.enableNotifications) {
            OverlayMessage.message = null;
        }
    }

    public static void reloadConfig() {
        CONFIG = ClientDisplayConfig.load();
        lastConfigTimestamp = getConfigFileTimestamp();
        OverlayMessage.applyCurrentStyle();
    }

    private static void checkExternalConfigModification() {
        Path p = Paths.get("config", "nightnotifier_client.json");
        if (!Files.exists(p)) return;
        Instant ts = getConfigFileTimestamp();
        if (ts.isAfter(lastConfigTimestamp)) {
            reloadConfig();
        }
    }

    private static Instant getConfigFileTimestamp() {
        try {
            return Files.getLastModifiedTime(Paths.get("config", "nightnotifier_client.json")).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}