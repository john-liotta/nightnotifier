package hawkshock.nightnotifier.client.ui;

import hawkshock.nightnotifier.NightNotifierClient;
import hawkshock.nightnotifier.config.ClientDisplayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public class NightNotifierConfigScreen extends Screen {

	private final Screen parent;
	private ClientDisplayConfig cfg;
	private final ClientDisplayConfig defaults = new ClientDisplayConfig();

	private CyclingButtonWidget<Boolean> notificationsToggle;
	private CyclingButtonWidget<Boolean> styleToggle;
	private CyclingButtonWidget<Boolean> phantomToggle;
	private CyclingButtonWidget<Boolean> netherToggle;
	private CyclingButtonWidget<Boolean> endToggle;

	private boolean notificationsEnabled;
	private boolean styleEnabled;
	private boolean phantomEnabled;
	private boolean netherEnabled;
	private boolean endEnabled;

	private TextFieldWidget colorField;
	private TextFieldWidget anchorField;
	private TextFieldWidget alignField;
	private TextFieldWidget offsetCombinedField;
	private TextFieldWidget durationSecondsField;
	private TextFieldWidget leadSecondsField;

	private ScaleSlider scaleSlider;
	private VolumeSlider nightVolSlider;
	private VolumeSlider morningVolSlider;

	private float scaleNorm;
	private float nightVolNorm;
	private float morningVolNorm;

	private boolean dirty = false;

	public NightNotifierConfigScreen(Screen parent) {
		super(Text.literal("Night Notifier Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		cfg = ClientDisplayConfig.load();

		notificationsEnabled = cfg.enableNotifications;
		styleEnabled = cfg.useClientStyle;
		phantomEnabled = cfg.enablePhantomScreams;
		netherEnabled = cfg.showNetherNotifications;
		endEnabled = cfg.showEndNotifications;

		scaleNorm = normalizeScale(cfg.textScale);
		nightVolNorm = normalizeVolume(cfg.nightScreamVolume);
		morningVolNorm = normalizeVolume(cfg.morningScreamVolume);

		int left = this.width / 2 - 170;
		int right = this.width / 2 + 20;
		int yLeft = 50;
		int yRight = 50;
		int w = 150;
		int h = 20;

		notificationsToggle = CyclingButtonWidget.onOffBuilder(notificationsEnabled)
				.build(left, yLeft, w, h, Text.literal("Enable Notifications"),
						(b, v) -> { notificationsEnabled = v; dirty = true; liveApplyToggles(); });
		notificationsToggle.setTooltip(Tooltip.of(Text.literal("Disabling hides all overlay messages")));
		addDrawableChild(notificationsToggle);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			notificationsEnabled = defaults.enableNotifications;
			notificationsToggle.setValue(notificationsEnabled);
			liveApplyToggles();
		}));
		yLeft += 24;

		styleToggle = CyclingButtonWidget.onOffBuilder(styleEnabled)
				.build(left, yLeft, w, h, Text.literal("Use My Message Style"),
						(b, v) -> { styleEnabled = v; dirty = true; liveApplyToggles(); });
		styleToggle.setTooltip(Tooltip.of(Text.literal("Disable to use server default style")));
		addDrawableChild(styleToggle);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			styleEnabled = defaults.useClientStyle;
			styleToggle.setValue(styleEnabled);
			liveApplyToggles();
		}));
		yLeft += 24;

		phantomToggle = CyclingButtonWidget.onOffBuilder(phantomEnabled)
				.build(left, yLeft, w, h, Text.literal("Phantom Screams"),
						(b, v) -> { phantomEnabled = v; dirty = true; liveApplyToggles(); });
		phantomToggle.setTooltip(Tooltip.of(Text.literal("Play phantom sounds for night/morning")));
		addDrawableChild(phantomToggle);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			phantomEnabled = defaults.enablePhantomScreams;
			phantomToggle.setValue(phantomEnabled);
			liveApplyToggles();
		}));
		yLeft += 24;

		netherToggle = CyclingButtonWidget.onOffBuilder(netherEnabled)
				.build(left, yLeft, w, h, Text.literal("Show in Nether"),
						(b, v) -> { netherEnabled = v; dirty = true; liveApplyToggles(); });
		netherToggle.setTooltip(Tooltip.of(Text.literal("Display notifications while in the Nether")));
		addDrawableChild(netherToggle);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			netherEnabled = defaults.showNetherNotifications;
			netherToggle.setValue(netherEnabled);
			liveApplyToggles();
		}));
		yLeft += 24;

		endToggle = CyclingButtonWidget.onOffBuilder(endEnabled)
				.build(left, yLeft, w, h, Text.literal("Show in End"),
						(b, v) -> { endEnabled = v; dirty = true; liveApplyToggles(); });
		endToggle.setTooltip(Tooltip.of(Text.literal("Display notifications while in The End")));
		addDrawableChild(endToggle);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			endEnabled = defaults.showEndNotifications;
			endToggle.setValue(endEnabled);
			liveApplyToggles();
		}));
		yLeft += 24;

		scaleSlider = new ScaleSlider(left, yLeft, w, h, scaleNorm);
		addDrawableChild(scaleSlider);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			scaleNorm = normalizeScale(defaults.textScale);
			scaleSlider.force(scaleNorm);
		}));
		yLeft += 24;

		nightVolSlider = new VolumeSlider(left, yLeft, w, h, Text.literal("Night Vol"), nightVolNorm);
		addDrawableChild(nightVolSlider);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			nightVolNorm = normalizeVolume(defaults.nightScreamVolume);
			nightVolSlider.force(nightVolNorm);
		}));
		yLeft += 24;

		morningVolSlider = new VolumeSlider(left, yLeft, w, h, Text.literal("Morning Vol"), morningVolNorm);
		addDrawableChild(morningVolSlider);
		addDrawableChild(resetButton(left + w + 4, yLeft, () -> {
			morningVolNorm = normalizeVolume(defaults.morningScreamVolume);
			morningVolSlider.force(morningVolNorm);
		}));
		yLeft += 24;

		colorField = strField(right, yRight, w, cfg.colorHex, "Color", this::liveApplyTextFields); yRight += 24;
		anchorField = strField(right, yRight, w, cfg.anchor, "Anchor", this::liveApplyTextFields); yRight += 24;
		alignField  = strField(right, yRight, w, cfg.textAlign, "Align", this::liveApplyTextFields); yRight += 24;
		offsetCombinedField = strField(right, yRight, w, cfg.offsetX + "," + cfg.offsetY, "Message Offset", this::liveApplyTextFields); yRight += 24;

		int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
		durationSecondsField = strField(right, yRight, w, String.valueOf(notifSeconds), "Message Duration", this::liveApplyTextFields); yRight += 24;

		int leadSeconds = cfg.morningWarningLeadTicks / 20;
		leadSecondsField = strField(right, yRight, w, String.valueOf(leadSeconds), "Seconds Until Morning", this::liveApplyTextFields); yRight += 24;

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

	private TextFieldWidget strField(int x, int y, int w, String value, String label, Runnable onChange) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
		f.setText(value);
		f.setChangedListener(s -> { dirty = true; onChange.run(); });
		addDrawableChild(f);
		return f;
	}

	private ButtonWidget resetButton(int x, int y, Runnable action) {
		return ButtonWidget.builder(Text.literal("R"), b -> action.run())
				.dimensions(x, y, 22, 20)
				.tooltip(Tooltip.of(Text.literal("Reset to default")))
				.build();
	}

	private void restoreDefaults() {
		notificationsEnabled = defaults.enableNotifications;
		styleEnabled = defaults.useClientStyle;
		phantomEnabled = defaults.enablePhantomScreams;
		netherEnabled = defaults.showNetherNotifications;
		endEnabled = defaults.showEndNotifications;
		scaleNorm = normalizeScale(defaults.textScale);
		nightVolNorm = normalizeVolume(defaults.nightScreamVolume);
		morningVolNorm = normalizeVolume(defaults.morningScreamVolume);

		colorField.setText(defaults.colorHex);
		anchorField.setText(defaults.anchor);
		alignField.setText(defaults.textAlign);
		offsetCombinedField.setText(defaults.offsetX + "," + defaults.offsetY);
		durationSecondsField.setText(String.valueOf(defaults.defaultDuration / 20));
		leadSecondsField.setText(String.valueOf(defaults.morningWarningLeadTicks / 20));

		notificationsToggle.setValue(notificationsEnabled);
		styleToggle.setValue(styleEnabled);
		phantomToggle.setValue(phantomEnabled);
		netherToggle.setValue(netherEnabled);
		endToggle.setValue(endEnabled);
		scaleSlider.force(scaleNorm);
		nightVolSlider.force(nightVolNorm);
		morningVolSlider.force(morningVolNorm);

		dirty = true;
		liveApplyAll();
	}

	private void liveApplyToggles() {
		cfg.enableNotifications = notificationsEnabled;
		cfg.useClientStyle = styleEnabled;
		cfg.enablePhantomScreams = phantomEnabled;
		cfg.showNetherNotifications = netherEnabled;
		cfg.showEndNotifications = endEnabled;
		ClientDisplayConfig.save(cfg);
		NightNotifierClient.applyClientConfig(cfg);
	}

	private void liveApplySliders() {
		cfg.textScale = denormalizeScale(scaleNorm);
		cfg.textScale = clampScale(cfg.textScale);
		cfg.nightScreamVolume = denormalizeVolume(nightVolNorm);
		cfg.morningScreamVolume = denormalizeVolume(morningVolNorm);
		ClientDisplayConfig.save(cfg);
		NightNotifierClient.applyClientConfig(cfg);
	}

	private void liveApplyTextFields() {
		cfg.colorHex = safeColor(colorField.getText(), cfg.colorHex);
		cfg.anchor = anchorField.getText().trim().toUpperCase();
		cfg.textAlign = alignField.getText().trim().toUpperCase();
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

	private void liveApplyAll() {
		liveApplyToggles();
		liveApplySliders();
		liveApplyTextFields();
	}

	private void applyFinal() {
		if (!dirty) return;
		liveApplyAll();
	}

	private int[] parseOffset(String s, int defX, int defY) {
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

	private int parseInt(String s, int fallback) {
		try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
	}

	private String safeColor(String in, String fallback) {
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

	private float normalizeScale(float s) { return (clampScale(s) - 0.5f) / 2.0f; }
	private float denormalizeScale(float sliderVal) { return 0.5f + sliderVal * 2.0f; }
	private float clampScale(float v) { return v < 0.5f ? 0.5f : (v > 2.5f ? 2.5f : v); }

	private float normalizeVolume(float v) { return Math.min(1f, Math.max(0f, v / 3f)); }
	private float denormalizeVolume(float sliderVal) { return sliderVal * 3f; }

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, 0x88000000);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
	}

	@Override public boolean shouldCloseOnEsc() { return true; }
	@Override public void close() { MinecraftClient.getInstance().setScreen(parent); }

	private class ScaleSlider extends SliderWidget {
		ScaleSlider(int x, int y, int w, int h, double initial) {
			super(x, y, w, h, Text.literal("Scale"), initial);
			updateMessage();
		}
		@Override protected void updateMessage() {
			// Cast value (double) to float for helper
			setMessage(Text.literal("Scale: " + String.format("%.2f", denormalizeScale((float)this.value))));
		}
		@Override protected void applyValue() {
			scaleNorm = (float)this.value;
			dirty = true;
			liveApplySliders();
			updateMessage();
		}
		void force(double v) {
			this.value = v;
			applyValue();
		}
	}

	private class VolumeSlider extends SliderWidget {
		VolumeSlider(int x, int y, int w, int h, Text label, double initial) {
			super(x, y, w, h, label, initial);
			updateMessage();
		}
		@Override protected void updateMessage() {
			setMessage(Text.literal(getMessage().getString().split(":")[0] + ": " +
					String.format("%.2f", denormalizeVolume((float)this.value))));
		}
		@Override protected void applyValue() {
			if (getMessage().getString().startsWith("Night")) {
				nightVolNorm = (float)this.value;
			} else {
				morningVolNorm = (float)this.value;
			}
			dirty = true;
			liveApplySliders();
			updateMessage();
		}
		void force(double v) {
			this.value = v;
			applyValue();
		}
	}
}