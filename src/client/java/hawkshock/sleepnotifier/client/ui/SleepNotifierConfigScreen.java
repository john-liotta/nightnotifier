package hawkshock.sleepnotifier.client.ui;

import hawkshock.sleepnotifier.config.ClientDisplayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SleepNotifierConfigScreen extends Screen {
	private final Screen parent;
	private ClientDisplayConfig cfg;

	// Toggles (use CyclingButtonWidget for compatibility)
	private CyclingButtonWidget<Boolean> overlayToggle;
	private CyclingButtonWidget<Boolean> phantomToggle;
	private boolean overlayEnabled;
	private boolean phantomEnabled;

	private TextFieldWidget colorField;
	private TextFieldWidget anchorField;
	private TextFieldWidget alignField;
	private TextFieldWidget offsetXField;
	private TextFieldWidget offsetYField;
	private TextFieldWidget durationField;
	private TextFieldWidget morningLeadField;

	private SliderWidget scaleSlider;
	private SliderWidget nightVolSlider;
	private SliderWidget morningVolSlider;

	// Local slider state
	private float scaleNorm;
	private float nightVolNorm;
	private float morningVolNorm;

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

		overlayEnabled = cfg.enableOverlay;
		phantomEnabled = cfg.enablePhantomScreams;

		overlayToggle = CyclingButtonWidget.onOffBuilder(overlayEnabled)
				.build(left, yLeft, w, h, Text.literal("Enable Overlay"),
						(button, value) -> { overlayEnabled = value; dirty = true; });
		addDrawableChild(overlayToggle);
		yLeft += 24;

		phantomToggle = CyclingButtonWidget.onOffBuilder(phantomEnabled)
				.build(left, yLeft, w, h, Text.literal("Phantom Screams"),
						(button, value) -> { phantomEnabled = value; dirty = true; });
		addDrawableChild(phantomToggle);
		yLeft += 24;

		// Sliders: keep normalized values locally
		scaleNorm = normalizeScale(cfg.textScale);
		nightVolNorm = normalizeVolume(cfg.nightScreamVolume);
		morningVolNorm = normalizeVolume(cfg.morningScreamVolume);

		scaleSlider = new SliderWidget(left, yLeft, w, h, Text.literal("Scale"), scaleNorm) {
			@Override protected void updateMessage() {
				setMessage(Text.literal("Scale: " + String.format("%.2f", denormalizeScale((float) this.value))));
			}
			@Override protected void applyValue() { scaleNorm = (float) this.value; dirty = true; }
		};
		scaleSlider.setMessage(Text.literal("Scale: " + String.format("%.2f", denormalizeScale(scaleNorm))));
		addDrawableChild(scaleSlider);
		yLeft += 24;

		nightVolSlider = new SliderWidget(left, yLeft, w, h, Text.literal("Night Vol"), nightVolNorm) {
			@Override protected void updateMessage() {
				setMessage(Text.literal("Night Vol: " + String.format("%.2f", denormalizeVolume((float) this.value))));
			}
			@Override protected void applyValue() { nightVolNorm = (float) this.value; dirty = true; }
		};
		nightVolSlider.setMessage(Text.literal("Night Vol: " + String.format("%.2f", denormalizeVolume(nightVolNorm))));
		addDrawableChild(nightVolSlider);
		yLeft += 24;

		morningVolSlider = new SliderWidget(left, yLeft, w, h, Text.literal("Morning Vol"), morningVolNorm) {
			@Override protected void updateMessage() {
				setMessage(Text.literal("Morning Vol: " + String.format("%.2f", denormalizeVolume((float) this.value))));
			}
			@Override protected void applyValue() { morningVolNorm = (float) this.value; dirty = true; }
		};
		morningVolSlider.setMessage(Text.literal("Morning Vol: " + String.format("%.2f", denormalizeVolume(morningVolNorm))));
		addDrawableChild(morningVolSlider);
		yLeft += 24;

		// Right column fields
		colorField = textField(right, yRight, w, cfg.colorHex, "Color"); yRight += 24;
		anchorField = textField(right, yRight, w, cfg.anchor, "Anchor"); yRight += 24;
		alignField  = textField(right, yRight, w, cfg.textAlign, "Align"); yRight += 24;
		offsetXField = intField(right, yRight, w, cfg.offsetX, "Offset X"); yRight += 24;
		offsetYField = intField(right, yRight, w, cfg.offsetY, "Offset Y"); yRight += 24;
		durationField = intField(right, yRight, w, cfg.defaultDuration, "Duration"); yRight += 24;
		morningLeadField = intField(right, yRight, w, cfg.morningWarningLeadTicks, "Morning Lead (ticks)"); yRight += 24;

		// Buttons
		addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> {
			apply();
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 - 170, this.height - 35, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
			MinecraftClient.getInstance().setScreen(parent);
		}).dimensions(this.width / 2 + 20, this.height - 35, 150, 20).build());
	}

	private TextFieldWidget textField(int x, int y, int w, String value, String label) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
		f.setText(value);
		f.setChangedListener(s -> dirty = true);
		addDrawableChild(f);
		return f;
	}

	private TextFieldWidget intField(int x, int y, int w, int value, String label) {
		return textField(x, y, w, String.valueOf(value), label);
	}

	private float normalizeScale(float s) {
		return (Math.min(3.5f, Math.max(0.5f, s)) - 0.5f) / 3.0f; // 0.5..3.5 -> 0..1
	}
	private float denormalizeScale(float sliderVal) {
		return 0.5f + sliderVal * 3.0f;
	}

	private float normalizeVolume(float v) {
		return Math.min(1f, Math.max(0f, v / 2f)); // 0..2 -> 0..1
	}
	private float denormalizeVolume(float sliderVal) {
		return sliderVal * 2f;
	}

	private int parseInt(String s, int fallback) {
		try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
	}

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
		cfg.enableOverlay = overlayEnabled;
		cfg.enablePhantomScreams = phantomEnabled;
		cfg.colorHex = safeColor(colorField.getText());
		cfg.anchor = anchorField.getText().trim().toUpperCase();
		cfg.textAlign = alignField.getText().trim().toUpperCase();

		cfg.textScale = denormalizeScale(scaleNorm);
		cfg.nightScreamVolume = denormalizeVolume(nightVolNorm);
		cfg.morningScreamVolume = denormalizeVolume(morningVolNorm);

		cfg.offsetX = parseInt(offsetXField.getText(), cfg.offsetX);
		cfg.offsetY = parseInt(offsetYField.getText(), cfg.offsetY);
		cfg.defaultDuration = parseInt(durationField.getText(), cfg.defaultDuration);
		cfg.morningWarningLeadTicks = parseInt(morningLeadField.getText(), cfg.morningWarningLeadTicks);

		ClientDisplayConfig.save(cfg);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Do NOT call Screen.renderBackground(...) here – ModMenu already applies blur.
		// Drawing our own dim background avoids "Can only blur once per frame".
		ctx.fill(0, 0, this.width, this.height, 0x88000000);

		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}