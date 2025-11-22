package hawkshock.sleepnotifier.client.ui;

import hawkshock.sleepnotifier.config.ClientDisplayConfig;
import hawkshock.sleepnotifier.SleepNotifierClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;

public class SleepNotifierConfigScreen extends Screen {
	private final Screen parent;
	private ClientDisplayConfig cfg;
	private final ClientDisplayConfig defaults = new ClientDisplayConfig();

	private CyclingButtonWidget<Boolean> notificationsToggle;
	private CyclingButtonWidget<Boolean> styleToggle;
	private CyclingButtonWidget<Boolean> phantomToggle;
	private boolean notificationsEnabled;
	private boolean styleEnabled;
	private boolean phantomEnabled;

	private TextFieldWidget colorField, anchorField, alignField, offsetXField, offsetYField,
			durationSecondsField, leadSecondsField;

	private BoundSlider scaleSlider, nightVolSlider, morningVolSlider;
	private float scaleNorm, nightVolNorm, morningVolNorm;

	private boolean dirty = false;

	public SleepNotifierConfigScreen(Screen parent) {
		super(Text.literal("Sleep Notifier Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		cfg = ClientDisplayConfig.load();

		int left = this.width / 2 - 170;
		int right = this.width / 2 + 20;
		int yLeft = 50;
		int yRight = 50;
		int w = 150;
		int h = 20;

		notificationsEnabled = cfg.enableNotifications;
		styleEnabled = cfg.useClientStyle;
		phantomEnabled = cfg.enablePhantomScreams;

		notificationsToggle = CyclingButtonWidget.onOffBuilder(notificationsEnabled)
				.build(left, yLeft, w, h, Text.literal("Enable Notifications"),
						(btn, value) -> { notificationsEnabled = value; dirty = true; liveApply(); });
		notificationsToggle.setTooltip(Tooltip.of(Text.literal("Disabling will prevent all messages from this mod")));
		addDrawableChild(notificationsToggle);
		addDrawableChild(resetBtn(left + w + 4, yLeft, () -> {
			notificationsEnabled = defaults.enableNotifications;
			notificationsToggle.setValue(notificationsEnabled);
			liveApply();
		}));
		yLeft += 24;

		styleToggle = CyclingButtonWidget.onOffBuilder(styleEnabled)
				.build(left, yLeft, w, h, Text.literal("Use My Message Style"),
						(btn, value) -> { styleEnabled = value; dirty = true; liveApply(); });
		styleToggle.setTooltip(Tooltip.of(Text.literal("Disabling will use the default notification style of the server")));
		addDrawableChild(styleToggle);
		addDrawableChild(resetBtn(left + w + 4, yLeft, () -> {
			styleEnabled = defaults.useClientStyle;
			styleToggle.setValue(styleEnabled);
			liveApply();
		}));
		yLeft += 24;

		phantomToggle = CyclingButtonWidget.onOffBuilder(phantomEnabled)
				.build(left, yLeft, w, h, Text.literal("Phantom Screams"),
						(btn, value) -> { phantomEnabled = value; dirty = true; liveApply(); });
		phantomToggle.setTooltip(Tooltip.of(Text.literal("Play phantom sounds for nightfall and morning warnings")));
		addDrawableChild(phantomToggle);
		addDrawableChild(resetBtn(left + w + 4, yLeft, () -> {
			phantomEnabled = defaults.enablePhantomScreams;
			phantomToggle.setValue(phantomEnabled);
			liveApply();
		}));
		yLeft += 24;

		scaleNorm = normalizeScale(cfg.textScale);
		nightVolNorm = normalizeVolume(cfg.nightScreamVolume);
		morningVolNorm = normalizeVolume(cfg.morningScreamVolume);

		scaleSlider = createSlider(left, yLeft, w, h, "Scale",
				scaleNorm,
				v -> { scaleNorm = (float)v; dirty = true; liveApply(); },
				() -> normalizeScale(defaults.textScale));
		yLeft += 24;

		nightVolSlider = createSlider(left, yLeft, w, h, "Night Vol",
				nightVolNorm,
				v -> { nightVolNorm = (float)v; dirty = true; liveApply(); },
				() -> normalizeVolume(defaults.nightScreamVolume));
		yLeft += 24;

		morningVolSlider = createSlider(left, yLeft, w, h, "Morning Vol",
				morningVolNorm,
				v -> { morningVolNorm = (float)v; dirty = true; liveApply(); },
				() -> normalizeVolume(defaults.morningScreamVolume));
		yLeft += 24;

		colorField = textField(right, yRight, w, cfg.colorHex, "Color");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { colorField.setText(defaults.colorHex); liveApplyTextFields(); }));
		yRight += 24;

		anchorField = textField(right, yRight, w, cfg.anchor, "Anchor");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { anchorField.setText(defaults.anchor); liveApplyTextFields(); }));
		yRight += 24;

		alignField  = textField(right, yRight, w, cfg.textAlign, "Align");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { alignField.setText(defaults.textAlign); liveApplyTextFields(); }));
		yRight += 24;

		offsetXField = intField(right, yRight, w, cfg.offsetX, "Offset X");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { offsetXField.setText(String.valueOf(defaults.offsetX)); liveApplyTextFields(); }));
		yRight += 24;

		offsetYField = intField(right, yRight, w, cfg.offsetY, "Offset Y");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { offsetYField.setText(String.valueOf(defaults.offsetY)); liveApplyTextFields(); }));
		yRight += 24;

		int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
		int defNotifSeconds = defaults.defaultDuration <= 0 ? defaults.defaultDuration : defaults.defaultDuration / 20;
		durationSecondsField = intField(right, yRight, w, notifSeconds, "Message Duration");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { durationSecondsField.setText(String.valueOf(defNotifSeconds)); liveApplyTextFields(); }));
		yRight += 24;

		int leadSeconds = cfg.morningWarningLeadTicks / 20;
		int defLeadSeconds = defaults.morningWarningLeadTicks / 20;
		leadSecondsField = intField(right, yRight, w, leadSeconds, "Seconds Until Morning");
		addDrawableChild(resetBtn(right + w + 4, yRight, () -> { leadSecondsField.setText(String.valueOf(defLeadSeconds)); liveApplyTextFields(); }));
		yRight += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Reset All"), b -> {
			restoreAllDefaults();
			liveApply();
			liveApplyTextFields();
		}).dimensions(this.width / 2 - 60, this.height - 65, 120, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> {
			apply();
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 - 170, this.height - 35, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 + 20, this.height - 35, 150, 20).build());
	}

	private BoundSlider createSlider(int x, int y, int w, int h, String label, double initialNormalized, DoubleConsumer onChange, SupplierDouble defaultSupplier) {
		BoundSlider slider = new BoundSlider(x, y, w, h, Text.literal(label), initialNormalized, onChange);
		addDrawableChild(slider);
		addDrawableChild(resetBtn(x + w + 4, y, () -> {
			double def = defaultSupplier.get();
			slider.force(def); // resets position and applies change
		}));
		return slider;
	}

	private ButtonWidget resetBtn(int x, int y, Runnable action) {
		return ButtonWidget.builder(Text.literal("R"), b -> action.run())
				.dimensions(x, y, 22, 20)
				.tooltip(Tooltip.of(Text.literal("Reset to default")))
				.build();
	}

	private void restoreAllDefaults() {
		notificationsEnabled = defaults.enableNotifications;
		styleEnabled = defaults.useClientStyle;
		phantomEnabled = defaults.enablePhantomScreams;
		scaleNorm = normalizeScale(defaults.textScale);
		nightVolNorm = normalizeVolume(defaults.nightScreamVolume);
		morningVolNorm = normalizeVolume(defaults.morningScreamVolume);

		colorField.setText(defaults.colorHex);
		anchorField.setText(defaults.anchor);
		alignField.setText(defaults.textAlign);
		offsetXField.setText(String.valueOf(defaults.offsetX));
		offsetYField.setText(String.valueOf(defaults.offsetY));
		durationSecondsField.setText(String.valueOf(defaults.defaultDuration / 20));
		leadSecondsField.setText(String.valueOf(defaults.morningWarningLeadTicks / 20));

		notificationsToggle.setValue(notificationsEnabled);
		styleToggle.setValue(styleEnabled);
		phantomToggle.setValue(phantomEnabled);

		scaleSlider.force(scaleNorm);
		nightVolSlider.force(nightVolNorm);
		morningVolSlider.force(morningVolNorm);

		dirty = true;
	}

	private void liveApply() {
		cfg.enableNotifications = notificationsEnabled;
		cfg.useClientStyle = styleEnabled;
		cfg.enablePhantomScreams = phantomEnabled;
		cfg.textScale = denormalizeScale(scaleNorm);
		if (cfg.textScale < 0.5f) cfg.textScale = 0.5f;
		if (cfg.textScale > 2.5f) cfg.textScale = 2.5f;
		cfg.nightScreamVolume = denormalizeVolume(nightVolNorm);
		cfg.morningScreamVolume = denormalizeVolume(morningVolNorm);
		ClientDisplayConfig.save(cfg);
		SleepNotifierClient.applyClientConfig(cfg);
	}

	private TextFieldWidget textField(int x, int y, int w, String value, String label) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
		f.setText(value);
		f.setChangedListener(s -> { dirty = true; liveApplyTextFields(); });
		addDrawableChild(f);
		return f;
	}
	private TextFieldWidget intField(int x, int y, int w, int value, String label) {
		return textField(x, y, w, String.valueOf(value), label);
	}

	private void liveApplyTextFields() {
		cfg.colorHex = safeColor(colorField.getText());
		cfg.anchor = anchorField.getText().trim().toUpperCase();
		cfg.textAlign = alignField.getText().trim().toUpperCase();
		cfg.offsetX = parseInt(offsetXField.getText(), cfg.offsetX);
		cfg.offsetY = parseInt(offsetYField.getText(), cfg.offsetY);
		int notifSeconds = parseInt(durationSecondsField.getText(), cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20);
		cfg.defaultDuration = notifSeconds <= 0 ? notifSeconds : notifSeconds * 20;
		int leadSeconds = parseInt(leadSecondsField.getText(), cfg.morningWarningLeadTicks / 20);
		cfg.morningWarningLeadTicks = Math.max(0, leadSeconds) * 20;
		ClientDisplayConfig.save(cfg);
		SleepNotifierClient.applyClientConfig(cfg);
	}

	private float normalizeScale(float s) { return (Math.min(2.5f, Math.max(0.5f, s)) - 0.5f) / 2.0f; }
	private float denormalizeScale(double v) { return 0.5f + (float)v * 2.0f; }
	private float normalizeVolume(float v) { return Math.min(1f, Math.max(0f, v / 3f)); }
	private float denormalizeVolume(double v) { return (float)v * 3f; }

	private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; } }

	private String safeColor(String s) {
		if (s == null) return cfg.colorHex;
		s = s.trim();
		if (!s.startsWith("#")) return cfg.colorHex;
		int len = s.length();
		if (len != 7 && len != 9) return cfg.colorHex;
		for (int i = 1; i < len; i++) {
			char c = s.charAt(i);
			boolean hex = (c >= '0' && c <= '9') ||
					(c >= 'a' && c <= 'f') ||
					(c >= 'A' && c <= 'F');
			if (!hex) return cfg.colorHex;
		}
		return s.toUpperCase();
	}

	private void apply() {
		if (!dirty) return;
		ClientDisplayConfig.save(cfg);
		SleepNotifierClient.applyClientConfig(cfg);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, 0x88000000);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
	}

	@Override public boolean shouldCloseOnEsc() { return true; }
	@Override public void close() { MinecraftClient.getInstance().setScreen(parent); }

	// Helper slider subclass with public force() and callback
	private static class BoundSlider extends SliderWidget {
		private final DoubleConsumer consumer;

		BoundSlider(int x, int y, int w, int h, Text label, double initial, DoubleConsumer consumer) {
			super(x, y, w, h, label, initial);
			this.consumer = consumer;
			updateMessage();
		}
		@Override protected void updateMessage() {
			setMessage(Text.literal(getMessage().getString().split(":")[0] + ": " + String.format("%.2f", this.valueDisplay())));
		}
		private double valueDisplay() {
			return switch (getMessage().getString().split(":")[0]) {
				case "Scale" -> 0.5 + this.value * 2.0;
				case "Night Vol" -> this.value * 3.0;
				case "Morning Vol" -> this.value * 3.0;
				default -> this.value;
			};
		}
		@Override protected void applyValue() {
			consumer.accept(this.value);
			updateMessage();
		}
		void force(double v) {
			this.value = v;
			applyValue();
		}
	}

	@FunctionalInterface
	private interface SupplierDouble { double get(); }
}