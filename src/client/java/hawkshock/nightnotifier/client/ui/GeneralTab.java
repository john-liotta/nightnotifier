package hawkshock.nightnotifier.client.ui;

import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public final class GeneralTab {
    private GeneralTab() {}

    static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
        ClientDisplayConfig cfg = screen.cfg;
        ClientDisplayConfig defaults = screen.defaults;

        // Enable Notifications
        screen.notificationsToggle = CyclingButtonWidget.onOffBuilder(cfg.enableNotifications)
                .build(left, yLeft, w, h, Text.literal("Enable Notifications"),
                        (b, v) -> { cfg.enableNotifications = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.notificationsToggle.setTooltip(Tooltip.of(Text.literal("Disabling hides all overlay messages")));
        screen.addChild(screen.notificationsToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.enableNotifications = defaults.enableNotifications;
            screen.notificationsToggle.setValue(cfg.enableNotifications);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        // Nether toggle
        screen.netherToggle = CyclingButtonWidget.onOffBuilder(cfg.showNetherNotifications)
                .build(left, yLeft, w, h, Text.literal("Show in Nether"),
                        (b, v) -> { cfg.showNetherNotifications = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.netherToggle.setTooltip(Tooltip.of(Text.literal("Display notifications while in the Nether")));
        screen.addChild(screen.netherToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.showNetherNotifications = defaults.showNetherNotifications;
            screen.netherToggle.setValue(cfg.showNetherNotifications);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        // End toggle
        screen.endToggle = CyclingButtonWidget.onOffBuilder(cfg.showEndNotifications)
                .build(left, yLeft, w, h, Text.literal("Show in End"),
                        (b, v) -> { cfg.showEndNotifications = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.endToggle.setTooltip(Tooltip.of(Text.literal("Display notifications while in The End")));
        screen.addChild(screen.endToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.showEndNotifications = defaults.showEndNotifications;
            screen.endToggle.setValue(cfg.showEndNotifications);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        // Show all server players (only if server mod detected)
        if (ClientHandshake.authoritative) {
            var offendersToggle = CyclingButtonWidget.onOffBuilder(cfg.showAllOffenders)
                    .build(left, yLeft, w, h, Text.literal("Show All Server Players"),
                            (b, v) -> { cfg.showAllOffenders = v; screen.dirty = true; screen.liveApplyToggles(); });
            offendersToggle.setTooltip(Tooltip.of(Text.literal("Disable to only see messages about your lack of sleep.")));
            screen.addChild(offendersToggle);
            screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
                cfg.showAllOffenders = defaults.showAllOffenders;
                offendersToggle.setValue(cfg.showAllOffenders);
                screen.liveApplyToggles();
            }));
            yLeft += 24;
        }

        // Message-related fields (color, offset, duration, lead)
        screen.colorField = screen.strField(right, yRight, w, cfg.colorHex, "Color", screen::liveApplyTextFields); yRight += 24;
        screen.offsetCombinedField = screen.strField(right, yRight, w, cfg.offsetX + "," + cfg.offsetY, "Message Offset", screen::liveApplyTextFields); yRight += 24;

        int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
        screen.durationSecondsField = screen.strField(right, yRight, w, String.valueOf(notifSeconds), "Message Duration", screen::liveApplyTextFields); yRight += 24;

        int leadSeconds = cfg.morningWarningLeadTicks / 20;
        screen.leadSecondsField = screen.strField(right, yRight, w, String.valueOf(leadSeconds), "Seconds Until Morning", screen::liveApplyTextFields); yRight += 24;
    }
}