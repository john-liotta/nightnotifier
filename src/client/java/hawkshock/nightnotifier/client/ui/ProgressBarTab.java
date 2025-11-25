package hawkshock.nightnotifier.client.ui;

import hawkshock.nightnotifier.NightNotifierClient;
import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

public final class ProgressBarTab {
    private ProgressBarTab() {}

    static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
        ClientDisplayConfig cfg = screen.cfg;
        ClientDisplayConfig defaults = screen.defaults;

        CyclingButtonWidget<Boolean> progressToggle = CyclingButtonWidget.onOffBuilder(cfg.enableProgressBar)
                .build(left, yLeft, w, h, net.minecraft.text.Text.literal("Enable Progress Bar"),
                        (b, v) -> { cfg.enableProgressBar = v; screen.dirty = true; screen.liveApplyToggles(); });
        screen.addChild(progressToggle);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.enableProgressBar = defaults.enableProgressBar;
            progressToggle.setValue(cfg.enableProgressBar);
            screen.liveApplyToggles();
        }));
        yLeft += 24;

        CyclingButtonWidget<Boolean> disableSun = CyclingButtonWidget.onOffBuilder(cfg.disableSunIcon)
                .build(left, yLeft, w, h, net.minecraft.text.Text.literal("Disable Sun Icon"),
                        (b, v) -> { cfg.disableSunIcon = v; screen.dirty = true; ClientDisplayConfig.save(cfg); });
        screen.addChild(disableSun);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.disableSunIcon = defaults.disableSunIcon;
            disableSun.setValue(cfg.disableSunIcon);
        }));
        yLeft += 24;

        CyclingButtonWidget<Boolean> disableMoon = CyclingButtonWidget.onOffBuilder(cfg.disableMoonIcon)
                .build(left, yLeft, w, h, net.minecraft.text.Text.literal("Disable Moon Icon"),
                        (b, v) -> { cfg.disableMoonIcon = v; screen.dirty = true; ClientDisplayConfig.save(cfg); });
        screen.addChild(disableMoon);
        screen.addChild(screen.resetButton(left + w + 4, yLeft, () -> {
            cfg.disableMoonIcon = defaults.disableMoonIcon;
            disableMoon.setValue(cfg.disableMoonIcon);
        }));
        yLeft += 24;

        // Width/Height/Y Offset fields kept as text fields in this screen for parity with previous UI,
        TextFieldWidget widthField = screen.strField(right, yRight, w, String.valueOf(cfg.progressBarWidth), "Progress Bar Width (-1 auto)", () -> {});
        yRight += 24;
        TextFieldWidget heightField = screen.strField(right, yRight, w, String.valueOf(cfg.progressBarHeight), "Progress Bar Height (-1 auto)", () -> {});
        yRight += 24;
        TextFieldWidget yoffsetField = screen.strField(right, yRight, w, String.valueOf(cfg.progressBarYOffset), "Progress Bar Y Offset", () -> {});
        yRight += 24;

        // Progress section colors (4)
        TextFieldWidget color0Field = screen.strField(right, yRight, w, cfg.progressSectionColor0, "Dusk Color (hex)", () -> {});
        yRight += 24;
        TextFieldWidget color1Field = screen.strField(right, yRight, w, cfg.progressSectionColor1, "Mid Night Color (hex)", () -> {});
        yRight += 24;
        TextFieldWidget color2Field = screen.strField(right, yRight, w, cfg.progressSectionColor2, "Night Ending Color (hex)", () -> {});
        yRight += 24;
        TextFieldWidget color3Field = screen.strField(right, yRight, w, cfg.progressSectionColor3, "Morning Warning Color (hex)", () -> {});
        yRight += 24;

        screen.addChild(ButtonWidget.builder(net.minecraft.text.Text.literal("Apply Progress Bar Settings"), b -> {
             // parse and save width/height/yoffset and colors
             try { cfg.progressBarWidth = Integer.parseInt(widthField.getText().trim()); } catch (Throwable ignored) {}
             try { cfg.progressBarHeight = Integer.parseInt(heightField.getText().trim()); } catch (Throwable ignored) {}
             try { cfg.progressBarYOffset = Integer.parseInt(yoffsetField.getText().trim()); } catch (Throwable ignored) {}
 
             // validate and save colors via safeColor (keeps fallback if invalid)
             cfg.progressSectionColor0 = screen.safeColor(color0Field.getText(), cfg.progressSectionColor0);
             cfg.progressSectionColor1 = screen.safeColor(color1Field.getText(), cfg.progressSectionColor1);
             cfg.progressSectionColor2 = screen.safeColor(color2Field.getText(), cfg.progressSectionColor2);
             cfg.progressSectionColor3 = screen.safeColor(color3Field.getText(), cfg.progressSectionColor3);
 
             ClientDisplayConfig.save(cfg);
            NightNotifierClient.applyClientConfig(cfg);
         }).dimensions(right, yRight, w, 20).build());
    }
}