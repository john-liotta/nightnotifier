package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.gui.widget.CyclingButtonWidget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

public final class SoundEffectsTab {
    private SoundEffectsTab() {}

    static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
        ClientDisplayConfig cfg = screen.cfg;
        ClientDisplayConfig defaults = screen.defaults;

        screen.phantomToggle = CyclingButtonWidget.onOffBuilder(cfg.enablePhantomScreams)
                .build(left, yLeft, w, h, net.minecraft.text.Text.literal("Phantom Screams"),
                        (b, v) -> { cfg.enablePhantomScreams = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.phantomToggle.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.literal("Play phantom sounds for night/morning")));
        screen.addChild(screen.phantomToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.enablePhantomScreams = defaults.enablePhantomScreams;
            screen.phantomToggle.setValue(cfg.enablePhantomScreams);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        screen.nightVolSlider = new VolumeSlider(left, yLeft, w, h, "Night Vol", screen.nightVolNorm,
                (DoubleUnaryOperator) v -> screen.denormalizeVolume((float) v),
                (DoubleConsumer) v -> {
                    screen.nightVolNorm = (float) v;
                    screen.dirty = true;
                    screen.liveApplySliders();
                });
        screen.addChild(screen.nightVolSlider);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            screen.nightVolNorm = screen.normalizeVolume(defaults.nightScreamVolume);
            screen.nightVolSlider.force(screen.nightVolNorm);
        }));
        yLeft += 24;

        screen.morningVolSlider = new VolumeSlider(left, yLeft, w, h, "Morning Vol", screen.morningVolNorm,
                (DoubleUnaryOperator) v -> screen.denormalizeVolume((float) v),
                (DoubleConsumer) v -> {
                    screen.morningVolNorm = (float) v;
                    screen.dirty = true;
                    screen.liveApplySliders();
                });
        screen.addChild(screen.morningVolSlider);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            screen.morningVolNorm = screen.normalizeVolume(defaults.morningScreamVolume);
            screen.morningVolSlider.force(screen.morningVolNorm);
        }));
        yLeft += 24;
    }
}