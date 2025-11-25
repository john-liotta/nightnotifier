package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class IconRender {
    private IconRender() {}

    // Use the vanilla sky sun texture (the full atlas). We'll crop the 16x16 sun disc from it.
    private static final Identifier SUN_TEX  = Identifier.of("minecraft", "textures/environment/sun.png");
    private static final Identifier MOON_TEX = Identifier.of("minecraft", "textures/environment/moon_phases.png");

    // Simple animation state used by ProgressBarRenderer (kept minimal and deterministic)
    private static boolean showSun = false;
    private static boolean showMoon = false;
    private static int ticksRemaining = 0;
    private static float alpha = 1.0f;
    private static boolean pulseDown = true;

    /**
     * Called by the progress-bar integration to enable/disable icons and set a simple
     * animation duration (in ticks). This mirrors the previous small API so callers compile.
     */
    public static void set(boolean sun, boolean moon, int durationTicks) {
        showSun = sun;
        showMoon = moon;
        ticksRemaining = Math.max(0, durationTicks);
        alpha = 1.0f;
        pulseDown = true;
    }

    /**
     * Tick the simple animation state. Call each frame from the progress bar renderer.
     */
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
            alpha = Math.max(0f, Math.min(1f, alpha));
        } else {
            showSun = false;
            showMoon = false;
            alpha = 1.0f;
            pulseDown = true;
        }
    }

    // Render the vanilla sun by sampling the 16x16 sun disc centered inside the 32x32 sun atlas.
    public static void renderSun(DrawContext ctx, ClientDisplayConfig cfg, int x, int y, int size) {
        final int texW = 32;     // full sun.png atlas width (you reported 32x32)
        final int texH = 32;     // full sun.png atlas height
        final int region = 16;   // actual sun disc region size
        final int srcX = (texW - region) / 2; // center -> (8)
        final int srcY = (texH - region) / 2; // center -> (8)

        // Draw the 16x16 sun disc (pixel-perfect crop) into the destination rectangle.
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SUN_TEX,
                x, y,
                (float) srcX, (float) srcY,
                region, region,
                texW, texH
        );
    }

    // Backwards-compatible convenience for existing callers that expect a single-icon draw.
    public static void renderSingle(DrawContext ctx, ClientDisplayConfig cfg, int x, int y, int size) {
        renderSun(ctx, cfg, x, y, size);
    }

    // Render the moon frame at native 16x16 size from the 128x64 moon sheet.
    public static void renderMoon(DrawContext ctx, ClientDisplayConfig cfg, int x, int y, int size) {
        int phase = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null) {
            try {
                phase = mc.world.getMoonPhase();
            } catch (Throwable ignored) {}
        }

        // sheet is 128x64, first frame starts at (8,8), frames 16x16 separated by 16 horizontally
        final int frameW = 16;
        final int frameH = 16;
        final int atlasW = 128;
        final int atlasH = 64;
        final int startX = 8;
        final int startY = 8;

        int srcX = startX + (phase % 8) * 16;
        int srcY = startY;

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                MOON_TEX,
                x, y,
                (float) srcX, (float) srcY,
                frameW, frameH,
                atlasW, atlasH
        );
    }
}
