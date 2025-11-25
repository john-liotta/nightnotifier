package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.gui.widget.CyclingButtonWidget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

public final class MessageStyleTab {
    private MessageStyleTab() {}

    static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
        ClientDisplayConfig cfg = screen.cfg;
        ClientDisplayConfig defaults = screen.defaults;

        screen.styleToggle = CyclingButtonWidget.onOffBuilder(cfg.useClientStyle)
                .build(left, yLeft, w, h, net.minecraft.text.Text.literal("Use My Message Style"),
                        (b, v) -> { cfg.useClientStyle = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.styleToggle.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.literal("Disable to use server default style")));
        screen.addChild(screen.styleToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.useClientStyle = defaults.useClientStyle;
            screen.styleToggle.setValue(cfg.useClientStyle);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        // Message-prefixed settings already in General; MessageStyle keeps scale
        screen.scaleSlider = new ScaleSlider(left, yLeft, w, h, screen.scaleNorm,
                (DoubleUnaryOperator) v -> screen.denormalizeScale((float) v),
                (DoubleConsumer) v -> {
                    screen.scaleNorm = (float) v;
                    screen.dirty = true;
                    screen.liveApplySliders();
                });
        screen.addChild(screen.scaleSlider);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            screen.scaleNorm = screen.normalizeScale(defaults.textScale);
            screen.scaleSlider.force(screen.scaleNorm);
        }));
        yLeft += 24;
    }
}