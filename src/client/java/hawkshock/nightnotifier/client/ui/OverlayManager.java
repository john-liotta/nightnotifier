package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.nightnotifier.client.sound.SoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.lang.reflect.Method;

public final class OverlayManager {
    private OverlayManager() {}

    private static Text message = null;
    private static int ticksRemaining = 0;
    private static int color = 0xFFFFFFFF;
    private static float scale = 1.0f;
    private static boolean styled = true;

    public static void set(String msg, int serverDuration, String eventType, ClientDisplayConfig cfg) {
        if (!cfg.enableNotifications) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            if (mc.world.getRegistryKey() == World.NETHER && !cfg.showNetherNotifications) return;
            if (mc.world.getRegistryKey() == World.END && !cfg.showEndNotifications) return;
        }

        String adjusted = adjustOffenderDisplay(msg, cfg);
        message = Text.literal(adjusted);
        int chosen = (cfg.defaultDuration > 0) ? cfg.defaultDuration : serverDuration;
        ticksRemaining = Math.max(10, chosen > 0 ? chosen : 300);
        applyCurrentStyle(cfg);

        if (cfg.enablePhantomScreams && (eventType != null)
                && (eventType.contains("NIGHT_START") || eventType.contains("SUNRISE_IMMINENT"))) {
            SoundManager.playForEvent(eventType, mc, cfg);
        }
    }

    // Exposed to allow NightNotifierClient to play the phantom warn sound when ignoring server overlay
    public static void playSoundForEvent(String eventType, MinecraftClient client, ClientDisplayConfig cfg) {
        SoundManager.playForEvent(eventType, client, cfg);
    }

    // Keep style application separate so NightNotifierClient can call it on config reload
    public static void applyCurrentStyle(ClientDisplayConfig cfg) {
        if (message == null) return;
        if (!cfg.enableNotifications) {
            message = null;
            ticksRemaining = 0;
            return;
        }
        styled = cfg.useClientStyle;
        // Keep a simple, clamped scale here for backward compatibility
        float ts = cfg.textScale;
        if (ts < 0.5f) ts = 0.5f;
        if (ts > 2.5f) ts = 2.5f;
        scale = ts;
        color = parseColor(cfg.colorHex);
    }

    public static void tick() {
        if (ticksRemaining > 0) ticksRemaining--;
        if (ticksRemaining == 0) message = null;
    }

    public static void render(DrawContext ctx) {
        if (message == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        // Read the live client config so anchor/offset/scale changes take effect immediately
        ClientDisplayConfig cfg = ClientDisplayConfig.load();

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int tw = tr.getWidth(message);
        int th = tr.fontHeight;

        // Use config scale (clamped)
        float cfgScale = cfg.textScale;
        if (cfgScale < 0.5f) cfgScale = 0.5f;
        if (cfgScale > 2.5f) cfgScale = 2.5f;

        int boxW = (int) (tw * cfgScale);
        int boxH = (int) (th * cfgScale);

        int anchorX;
        int anchorY;
        String anchor = cfg.anchor == null ? "TOP_CENTER" : cfg.anchor.trim().toUpperCase();
        switch (anchor) {
            case "TOP_LEFT" -> { anchorX = 0; anchorY = 0; }
            case "TOP_RIGHT" -> { anchorX = sw - boxW; anchorY = 0; }
            case "BOTTOM_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = sh - boxH; }
            case "BOTTOM_LEFT" -> { anchorX = 0; anchorY = sh - boxH; }
            case "BOTTOM_RIGHT" -> { anchorX = sw - boxW; anchorY = sh - boxH; }
            case "TOP_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
            default -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
        }

        // apply configured offset
        anchorX += cfg.offsetX;
        anchorY += cfg.offsetY;

        int padX = cfg.useClientStyle ? 6 : 0;
        int padY = cfg.useClientStyle ? 4 : 0;
        int bgColor = cfg.useClientStyle ? 0x90000000 : 0x00000000;
        int textColor = parseColor(cfg.colorHex);

        // Use tryScaleDraw helper to perform matrix scaling and translation if needed
        if (cfgScale != 1.0f && tryScaleDraw(ctx, tr, message, anchorX, anchorY, tw, th, padX, padY, bgColor, textColor, cfgScale)) return;

        if (cfg.useClientStyle && bgColor != 0) {
            ctx.fill(anchorX - padX, anchorY - padY, anchorX + tw + padX, anchorY + th + padY, bgColor);
        }
        // force left alignment internally (textAlign removed from UI)
        ctx.drawTextWithShadow(tr, message, anchorX, anchorY, textColor);
    }

    // Scaled draw helper (reflection-friendly)
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
            if (bgColor != 0) {
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

    // If server is authoritative and user disabled showAllOffenders, strip " Others:" section.
    private static String adjustOffenderDisplay(String original, ClientDisplayConfig cfg) {
        if (ClientHandshake.authoritative && !cfg.showAllOffenders) {
            int idx = original.indexOf(" Others:");
            if (idx > 0) {
                return original.substring(0, idx).trim();
            }
        }
        return original;
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
}