package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class ProgressBarRenderer {
    private ProgressBarRenderer() {}

    private static final long NIGHT_START = 12541L;
    private static final long NIGHT_END   = 23458L;
    private static final long NIGHT_LENGTH = Math.floorMod(NIGHT_END - NIGHT_START, 24000L);

    public static void render(DrawContext ctx, ClientDisplayConfig cfg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        long tod = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        boolean thundering = mc.world.isThundering();
        boolean naturalNight = tod >= NIGHT_START && tod <= NIGHT_END;
        boolean effectiveBarEnabled = cfg != null ? cfg.enableProgressBar : true;
        boolean show = effectiveBarEnabled && (thundering || naturalNight);
        if (!show) return;

        // remaining ticks until sunrise (robust around wrap)
        long remainingTicks = Math.floorMod(NIGHT_END - tod, 24000L);
        if (remainingTicks < 0) remainingTicks += 24000L;
        float frac = NIGHT_LENGTH > 0 ? (float) remainingTicks / (float) NIGHT_LENGTH : 0f;
        frac = Math.max(0f, Math.min(1f, frac));

        int sw = mc.getWindow().getScaledWidth();
        int base = sw / 3;

        // Use config width/height if provided; else fall back to computed defaults
        int barW = (cfg != null && cfg.progressBarWidth > 0) ? cfg.progressBarWidth
                : Math.max(120, Math.min(400, Math.round(base * 0.44f)));
        int barH = (cfg != null && cfg.progressBarHeight > 0) ? cfg.progressBarHeight : 10;

        int x = (sw - barW) / 2;
        int y = (cfg != null) ? cfg.progressBarYOffset : 8; // use config offset

        // Outer background (subtle dark)
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x90000000);

        // Choose color by fraction (medium -> dark -> light) and red when within client lead
        int clientLead = cfg.morningWarningLeadTicks; // ticks
        int color;
        if (remainingTicks <= clientLead) {
            color = parseColor(cfg.progressSectionColor0, 0xFFFF4444); // red
        } else if (frac > 0.66f) {
            color = parseColor(cfg.progressSectionColor1, 0xFF4A90E2); // medium blue
        } else if (frac > 0.33f) {
            color = parseColor(cfg.progressSectionColor2, 0xFF003366); // dark blue
        } else {
            color = parseColor(cfg.progressSectionColor3, 0xFF7FBFFF); // light blue
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

        // --- Icon positioning integration ---
        boolean night = naturalNight || thundering;
        boolean showMoon = night && !(cfg != null && cfg.disableMoonIcon);
        boolean showSun = night && !(cfg != null && cfg.disableSunIcon);

        // Use IconRender's animation state for both icons.
        IconRender.set(showSun, showMoon, (int) Math.max(0, Math.min(remainingTicks, 1200L)));
        IconRender.tick();

        // Compute icon size and positions (keep same logic, size may be scaled elsewhere)
        int baseIcon = Math.max(8, Math.round(12 * cfg.textScale));
        int iconSize = Math.max(8, Math.round(baseIcon * 0.75f));
        iconSize = Math.min(iconSize, 64);

        int spacing = 6;
        int extraRight = Math.max(2, Math.round(iconSize * 0.2f));
        int iconY = y + (barH - iconSize) / 2; // vertically center wrt bar

        // Right icon (sun)
        int rightX = x + barW + spacing + extraRight;
        if (rightX + iconSize + 4 > sw) rightX = sw - iconSize - 4;

        // Left icon (moon)
        int leftX = x - iconSize - spacing - extraRight;
        if (leftX < 4) leftX = 4;

        // Vertical clamp
        int sh = mc.getWindow().getScaledHeight();
        if (iconY < 4) iconY = 4;
        if (iconY + iconSize + 4 > sh) iconY = Math.max(4, sh - iconSize - 4);

        // Draw moon on left if enabled
        if (showMoon) {
            IconRender.renderMoon(ctx, cfg, leftX, iconY, iconSize);
        }

        // Draw sun on right if enabled
        if (showSun) {
            IconRender.renderSingle(ctx, cfg, rightX, iconY, iconSize);
        }
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (!s.startsWith("#")) return fallback;
        s = s.substring(1);
        try {
            long v = Long.parseLong(s, 16);
            if (s.length() == 6) {
                v |= 0xFF000000L;
            } else if (s.length() == 8) {
                // keep alpha as provided
            } else {
                return fallback;
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
