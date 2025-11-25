package hawkshock.nightnotifier.client.ui;

import hawkshock.nightnotifier.NightNotifierClient;
import hawkshock.nightnotifier.client.ClientHandshake;
import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

public class NightNotifierConfigScreen extends Screen {

	final Screen parent;
	ClientDisplayConfig cfg;
	final ClientDisplayConfig defaults = new ClientDisplayConfig();

	// Tabs: 0 = General, 1 = Message Style, 2 = Sound Effects, 3 = Progress Bar
	int selectedTab = 0;

	// Controls referenced across tabs (package-private so tab classes can access)
	CyclingButtonWidget<Boolean> notificationsToggle;
	CyclingButtonWidget<Boolean> styleToggle;
	CyclingButtonWidget<Boolean> phantomToggle;
	CyclingButtonWidget<Boolean> netherToggle;
	CyclingButtonWidget<Boolean> endToggle;

	TextFieldWidget colorField;
	TextFieldWidget offsetCombinedField;
	TextFieldWidget durationSecondsField;
	TextFieldWidget leadSecondsField;

	ScaleSlider scaleSlider;
	VolumeSlider nightVolSlider;
	VolumeSlider morningVolSlider;

	float scaleNorm;
	float nightVolNorm;
	float morningVolNorm;

	boolean dirty = false;

	public NightNotifierConfigScreen(Screen parent) {
		super(Text.literal("Night Notifier Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		cfg = ClientDisplayConfig.load();

		scaleNorm = normalizeScale(cfg.textScale);
		nightVolNorm = normalizeVolume(cfg.nightScreamVolume);
		morningVolNorm = normalizeVolume(cfg.morningScreamVolume);

		int left = this.width / 2 - 170;
		int right = this.width / 2 + 20;
		int yLeft = 50;
		int yRight = 50;
		int w = 150;
		int h = 20;

		// Tab buttons at top
		addDrawableChild(ButtonWidget.builder(Text.literal("General"), b -> {
			selectedTab = 0;
			this.init();
		}).dimensions(this.width / 2 - 170, 20, 120, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Message Style"), b -> {
			selectedTab = 1;
			this.init();
		}).dimensions(this.width / 2 - 40, 20, 120, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Sound Effects"), b -> {
			selectedTab = 2;
			this.init();
		}).dimensions(this.width / 2 + 90, 20, 120, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Progress Bar"), b -> {
			selectedTab = 3;
			this.init();
		}).dimensions(this.width / 2 + 220, 20, 120, 20).build());

		// Build controls per selected tab (delegated to top-level tab builders)
		switch (selectedTab) {
			case 0 -> GeneralTab.build(this, left, right, yLeft, yRight, w, h);
			case 1 -> MessageStyleTab.build(this, left, right, yLeft, yRight, w, h);
			case 2 -> SoundEffectsTab.build(this, left, right, yLeft, yRight, w, h);
			case 3 -> ProgressBarTab.build(this, left, right, yLeft, yRight, w, h);
		}

		// Always present Save/Cancel/Reset buttons
		addDrawableChild(ButtonWidget.builder(Text.literal("Reset All"), b -> restoreDefaults())
				.dimensions(this.width / 2 - 60, this.height - 65, 120, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> {
			applyFinal();
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 - 170, this.height - 35, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 + 20, this.height - 35, 150, 20).build());
	}

	// Delegating methods retained for compatibility
	void buildGeneralTab(int left, int right, int yLeft, int yRight, int w, int h) { GeneralTab.build(this, left, right, yLeft, yRight, w, h); }
	void buildMessageStyleTab(int left, int right, int yLeft, int yRight, int w, int h) { MessageStyleTab.build(this, left, right, yLeft, yRight, w, h); }
	void buildSoundEffectsTab(int left, int right, int yLeft, int yRight, int w, int h) { SoundEffectsTab.build(this, left, right, yLeft, yRight, w, h); }
	void buildProgressBarTab(int left, int right, int yLeft, int yRight, int w, int h) { ProgressBarTab.build(this, left, right, yLeft, yRight, w, h); }

	// Helper factories - package-private so tab classes can call them
	TextFieldWidget strField(int x, int y, int w, String value, String label, Runnable onChange) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
		f.setText(value);
		f.setChangedListener(s -> { dirty = true; onChange.run(); });
		addDrawableChild(f);
		return f;
	}

	ButtonWidget resetButton(int x, int y, Runnable action) {
		return ButtonWidget.builder(Text.literal("R"), b -> action.run())
				.dimensions(x, y, 22, 20)
				.tooltip(Tooltip.of(Text.literal("Reset to default")))
				.build();
	}

	// wrapper for Screen.addDrawableChild (protected). Allow tab classes to add widgets.
	<T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> T addChild(T child) {
		return this.addDrawableChild(child);
	}

	void restoreDefaults() {
		// restore general defaults and progress-bar defaults
		cfg = new ClientDisplayConfig();
		liveApplyAll();
	}

	void liveApplyToggles() {
		ClientDisplayConfig.save(cfg);
		NightNotifierClient.applyClientConfig(cfg);
	}

	void liveApplySliders() {
		cfg.textScale = denormalizeScale(scaleNorm);
		cfg.textScale = clampScale(cfg.textScale);
		cfg.nightScreamVolume = denormalizeVolume(nightVolNorm);
		cfg.morningScreamVolume = denormalizeVolume(morningVolNorm);
		ClientDisplayConfig.save(cfg);
		NightNotifierClient.applyClientConfig(cfg);
	}

	void liveApplyTextFields() {
		cfg.colorHex = safeColor(colorField.getText(), cfg.colorHex);
		cfg.textAlign = "LEFT";
		int[] xy = parseOffset(offsetCombinedField.getText(), cfg.offsetX, cfg.offsetY);
		cfg.offsetX = xy[0];
		cfg.offsetY = xy[1];
		int notifSeconds = parseInt(durationSecondsField.getText(), cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20);
		cfg.defaultDuration = notifSeconds <= 0 ? notifSeconds : notifSeconds * 20;
		int leadSeconds = parseInt(leadSecondsField.getText(), cfg.morningWarningLeadTicks / 20);
		cfg.morningWarningLeadTicks = Math.max(0, leadSeconds) * 20;
		ClientDisplayConfig.save(cfg);
		NightNotifierClient.applyClientConfig(cfg);
	}

	// Ensure the Save button compiles: applyFinal implements final apply behavior
	void applyFinal() {
		if (!dirty) return;
		liveApplyAll();
	}

	void liveApplyAll() {
		liveApplyToggles();
		liveApplySliders();
		liveApplyTextFields();
	}

	int[] parseOffset(String s, int defX, int defY) {
		if (s == null) return new int[]{defX, defY};
		String[] parts = s.trim().split(",");
		if (parts.length != 2) return new int[]{defX, defY};
		try {
			int x = Integer.parseInt(parts[0].trim());
			int y = Integer.parseInt(parts[1].trim());
			return new int[]{x, y};
		} catch (NumberFormatException e) {
			return new int[]{defX, defY};
		}
	}

	int parseInt(String s, int fallback) {
		try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
	}

	String safeColor(String in, String fallback) {
		if (in == null) return fallback;
		String s = in.trim().toUpperCase();
		if (!s.startsWith("#")) return fallback;
		int len = s.length();
		if (len != 7 && len != 9) return fallback;
		for (int i = 1; i < len; i++) {
			char c = s.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
			if (!hex) return fallback;
		}
		return s;
	}

	float normalizeScale(float s) { return (clampScale(s) - 0.5f) / 2.0f; }
	float denormalizeScale(float sliderVal) { return 0.5f + sliderVal * 2.0f; }
	float clampScale(float v) { return v < 0.5f ? 0.5f : (v > 2.5f ? 2.5f : v); }

	float normalizeVolume(float v) { return Math.min(1f, Math.max(0f, v / 3f)); }
	float denormalizeVolume(float sliderVal) { return sliderVal * 3f; }

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, 0x88000000);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
	}

	@Override public boolean shouldCloseOnEsc() { return true; }
	@Override public void close() { MinecraftClient.getInstance().setScreen(parent); }

	// Add this nested class near the bottom of the file (inside NightNotifierConfigScreen)
	private static final class GeneralTab {
		static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
			ClientDisplayConfig cfg = screen.cfg;
			ClientDisplayConfig defaults = screen.defaults;

			// Enable Notifications
			screen.notificationsToggle = CyclingButtonWidget.onOffBuilder(cfg.enableNotifications)
					.build(left, yLeft, w, h, Text.literal("Enable Notifications"),
							(b, v) -> { cfg.enableNotifications = v; screen.dirty = true; screen.liveApplyToggles(); });
			screen.notificationsToggle.setTooltip(Tooltip.of(Text.literal("Disabling hides all overlay messages")));
			screen.addDrawableChild(screen.notificationsToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
			screen.addDrawableChild(screen.netherToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
			screen.addDrawableChild(screen.endToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
				screen.addDrawableChild(offendersToggle);
				screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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

	private static final class MessageStyleTab {
		static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
			ClientDisplayConfig cfg = screen.cfg;
			ClientDisplayConfig defaults = screen.defaults;

			screen.styleToggle = CyclingButtonWidget.onOffBuilder(cfg.useClientStyle)
					.build(left, yLeft, w, h, Text.literal("Use My Message Style"),
							(b, v) -> { cfg.useClientStyle = v; screen.dirty = true; screen.liveApplyToggles(); });
			screen.styleToggle.setTooltip(Tooltip.of(Text.literal("Disable to use server default style")));
			screen.addDrawableChild(screen.styleToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
			screen.addDrawableChild(screen.scaleSlider);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
				screen.scaleNorm = screen.normalizeScale(defaults.textScale);
				screen.scaleSlider.force(screen.scaleNorm);
			}));
			yLeft += 24;
		}
	}

	private static final class SoundEffectsTab {
		static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
			ClientDisplayConfig cfg = screen.cfg;
			ClientDisplayConfig defaults = screen.defaults;

			screen.phantomToggle = CyclingButtonWidget.onOffBuilder(cfg.enablePhantomScreams)
					.build(left, yLeft, w, h, Text.literal("Phantom Screams"),
							(b, v) -> { cfg.enablePhantomScreams = v; screen.dirty = true; screen.liveApplyToggles(); });
			screen.phantomToggle.setTooltip(Tooltip.of(Text.literal("Play phantom sounds for night/morning")));
			screen.addDrawableChild(screen.phantomToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
			screen.addDrawableChild(screen.nightVolSlider);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
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
			screen.addDrawableChild(screen.morningVolSlider);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
				screen.morningVolNorm = screen.normalizeVolume(defaults.morningScreamVolume);
				screen.morningVolSlider.force(screen.morningVolNorm);
			}));
			yLeft += 24;
		}
	}

	private static final class ProgressBarTab {
		static void build(NightNotifierConfigScreen screen, int left, int right, int yLeft, int yRight, int w, int h) {
			ClientDisplayConfig cfg = screen.cfg;
			ClientDisplayConfig defaults = screen.defaults;

			CyclingButtonWidget<Boolean> progressToggle = CyclingButtonWidget.onOffBuilder(cfg.enableProgressBar)
					.build(left, yLeft, w, h, Text.literal("Enable Progress Bar"),
							(b, v) -> { cfg.enableProgressBar = v; screen.dirty = true; screen.liveApplyToggles(); });
			screen.addDrawableChild(progressToggle);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
				cfg.enableProgressBar = defaults.enableProgressBar;
				progressToggle.setValue(cfg.enableProgressBar);
				screen.liveApplyToggles();
			}));
			yLeft += 24;

			CyclingButtonWidget<Boolean> disableSun = CyclingButtonWidget.onOffBuilder(cfg.disableSunIcon)
					.build(left, yLeft, w, h, Text.literal("Disable Sun Icon"),
							(b, v) -> { cfg.disableSunIcon = v; screen.dirty = true; ClientDisplayConfig.save(cfg); });
			screen.addDrawableChild(disableSun);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
				cfg.disableSunIcon = defaults.disableSunIcon;
				disableSun.setValue(cfg.disableSunIcon);
			}));
			yLeft += 24;

			CyclingButtonWidget<Boolean> disableMoon = CyclingButtonWidget.onOffBuilder(cfg.disableMoonIcon)
					.build(left, yLeft, w, h, Text.literal("Disable Moon Icon"),
							(b, v) -> { cfg.disableMoonIcon = v; screen.dirty = true; ClientDisplayConfig.save(cfg); });
			screen.addDrawableChild(disableMoon);
			screen.addDrawableChild(screen.resetButton(left + w + 4, yLeft, () -> {
				cfg.disableMoonIcon = defaults.disableMoonIcon;
				disableMoon.setValue(cfg.disableMoonIcon);
			}));
			yLeft += 24;

			// Width/Height/Y Offset fields kept as text fields in this screen for parity with previous UI,
			// but cloth config uses sliders; here we keep simple text fields and an apply button.
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

			screen.addDrawableChild(ButtonWidget.builder(Text.literal("Apply Progress Bar Settings"), b -> {
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
}