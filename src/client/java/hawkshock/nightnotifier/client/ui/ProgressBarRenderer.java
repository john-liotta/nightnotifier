package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class ProgressBarRenderer {
    private ProgressBarRenderer() {}

    private static final long NIGHT_START = 12541L;
    private static final long NIGHT_END   = 23458L;
    private static final long NIGHT_LENGTH = Math.floorMod(NIGHT_END - NIGHT_START, 24000L);

    public static void render(DrawContext ctx, ClientDisplayConfig cfg) {
        if (cfg == null || !cfg.enableProgressBar) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        long tod = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        boolean thundering = mc.world.isThundering();
        boolean naturalNight = tod >= NIGHT_START && tod <= NIGHT_END;
        if (!thundering && !naturalNight) return;

        long remainingTicks = Math.floorMod(NIGHT_END - tod, 24000L);
        float frac = NIGHT_LENGTH > 0 ? (float) remainingTicks / NIGHT_LENGTH : 0f;
        frac = Math.max(0f, Math.min(1f, frac));

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int barW = Math.max(120, Math.min(400, sw / 3));
        int barH = 10;
        int x = (sw - barW) / 2;
        int y = 8;

        // background
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x90000000);

        int fillW = Math.round(barW * frac);
        ctx.fill(x + (barW - fillW), y, x + barW, y + barH, 0xFF4A90E2);
        ctx.fill(x, y, x + (barW - fillW), y + barH, 0x40000000);

        // border
        ctx.fill(x, y - 1, x + barW, y, 0xFF000000);
        ctx.fill(x, y + barH, x + barW, y + barH + 1, 0xFF000000);
        ctx.fill(x - 1, y - 1, x, y + barH + 1, 0xFF000000);
        ctx.fill(x + barW, y - 1, x + barW + 1, y + barH + 1, 0xFF000000);

        // icon size
        int iconSize = Math.max(8, Math.round(12 * cfg.textScale));
        iconSize = Math.min(iconSize, 64);
        int spacing = 6;
        int extraRight = Math.max(2, Math.round(iconSize * 0.2f));
        int iconY = y + (barH - iconSize) / 2;

        int rightX = x + barW + spacing + extraRight;
        if (rightX + iconSize + 4 > sw) rightX = sw - iconSize - 4;

        int leftX = x - iconSize - spacing - extraRight;
        if (leftX < 4) leftX = 4;

        if (iconY < 4) iconY = 4;
        if (iconY + iconSize + 4 > sh) iconY = Math.max(4, sh - iconSize - 4);

        IconRender.renderMoon(ctx, cfg, leftX, iconY, iconSize);
        IconRender.renderSun(ctx, cfg, rightX, iconY, iconSize);
    }
}
