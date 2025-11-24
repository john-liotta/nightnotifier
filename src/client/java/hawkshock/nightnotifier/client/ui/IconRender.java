package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Skeleton renderer for sun / moon icons shown near the overlay.
 * Keep this class static-style like other render helpers in the project.
 *
 * This file provides a conservative `renderAt` routine that draws simple
 * colored quads as placeholders for the sun/moon. Debug border removed.
 */
@Environment(EnvType.CLIENT)
public final class IconRender {
    private IconRender() {}

    // Example texture identifiers - left in place for later
    private static final Identifier SUN_ID  = Identifier.of("nightnotifier", "textures/icon/sun.png");
    private static final Identifier MOON_ID = Identifier.of("nightnotifier", "textures/icon/moon.png");

    private static boolean showSun = false;
    private static boolean showMoon = false;
    private static int ticksRemaining = 0;

    private static float alpha = 1.0f;
    private static boolean pulseDown = true;

    public static void set(boolean sun, boolean moon, int durationTicks) {
        showSun = sun;
        showMoon = moon;
        ticksRemaining = Math.max(0, durationTicks);
        alpha = 1.0f;
        pulseDown = true;
    }

    public static void tick() {
        if (ticksRemaining > 0) ticksRemaining--;
        if (ticksRemaining > 0) {
            if (pulseDown) {
                alpha -= 0.02f;
                if (alpha <= 0.6f) pulseDown = false;
            } else {
                alpha += 0.02f;
                if (alpha >= 1.0f) pulseDown = true;
            }
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        } else {
            showSun = false;
            showMoon = false;
            alpha = 1.0f;
            pulseDown = true;
        }
    }

    /**
     * Conservative drawing helper that renders the icons at the provided location and size.
     * Debug border removed so final visuals are clean.
     */
    public static void renderAt(DrawContext ctx, ClientDisplayConfig cfg, int topLeftX, int topLeftY, int iconSize) {
        if (cfg == null) cfg = ClientDisplayConfig.load();
        if (!cfg.enableNotifications) return;
        if (!showSun && !showMoon) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        // clamp coords defensively
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int drawX = Math.max(0, Math.min(topLeftX, sw - iconSize));
        int drawY = Math.max(0, Math.min(topLeftY, sh - iconSize));

        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        int sunColor = (a << 24) | 0xFFFFCC00;  // warm yellow/orange
        int moonColor = (a << 24) | 0xFFCFE6FF; // pale bluish

        int spacing = 4;

        if (showSun) {
            ctx.fill(drawX, drawY, drawX + iconSize, drawY + iconSize, sunColor);
            drawX += iconSize + spacing;
        }

        if (showMoon) {
            ctx.fill(drawX, drawY, drawX + iconSize, drawY + iconSize, moonColor);
        }
    }

    public static boolean isVisible() {
        return showSun || showMoon;
    }

    // debug probe for builtin sun (unchanged)
    public static boolean debugBuiltinSunPresent() {
        Identifier sunId = Identifier.of("minecraft", "textures/environment/sun.png");
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        try {
            java.util.Optional<Resource> opt = rm.getResource(sunId);
            if (opt.isEmpty()) {
                System.out.println("[NightNotifier] debug: builtin sun MISSING -> " + sunId);
                return false;
            }
            Resource r = opt.get();
            InputStream is = null;
            try {
                is = r.getInputStream();
                boolean ok = is != null;
                System.out.println("[NightNotifier] debug: builtin sun " + (ok ? "FOUND" : "MISSING") + " -> " + sunId);
                return ok;
            } finally {
                if (is != null) {
                    try { is.close(); } catch (IOException ignored) {}
                }
            }
        } catch (IOException e) {
            System.out.println("[NightNotifier] debug: builtin sun MISSING: " + e.getMessage());
            return false;
        }
    }
}