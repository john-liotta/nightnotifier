package hawkshock.sleepnotifier.client.ui;

import hawkshock.sleepnotifier.config.ClientDisplayConfig;
import hawkshock.sleepnotifier.SleepNotifierClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

public final class ClothConfigFactory {

	private ClothConfigFactory() {}

	private enum Anchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
	private enum Align  { LEFT, CENTER, RIGHT }

	public static boolean isClothPresent() {
		return FabricLoader.getInstance().isModLoaded("cloth-config");
	}

	public static Screen create(Screen parent) {
		if (!isClothPresent()) {
			return new SleepNotifierConfigScreen(parent);
		}

		ClientDisplayConfig cfg = ClientDisplayConfig.load();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.literal("Sleep Notifier"))
				.setSavingRunnable(() -> {
					ClientDisplayConfig.save(cfg);
					SleepNotifierClient.applyClientConfig(cfg);
				});

		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory overlay = builder.getOrCreateCategory(Text.literal("Message Overlay"));

		overlay.addEntry(eb.startBooleanToggle(Text.literal("Enable Notifications"), cfg.enableNotifications)
				.setDefaultValue(true)
				.setTooltip(Text.literal("Disabling will prevent all messages from this mod"))
				.setSaveConsumer(v -> { cfg.enableNotifications = v; SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		overlay.addEntry(eb.startBooleanToggle(Text.literal("Use My Message Style"), cfg.useClientStyle)
				.setDefaultValue(true)
				.setTooltip(Text.literal("Disabling will use the default notification style of the server"))
				.setSaveConsumer(v -> { cfg.useClientStyle = v; SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		// Message Anchor
		overlay.addEntry(eb.startEnumSelector(Text.literal("Message Anchor"), Anchor.class, toAnchor(cfg.anchor))
				.setDefaultValue(Anchor.TOP_CENTER)
				.setTooltip(Text.literal("Screen anchor for the message overlay"))
				.setSaveConsumer(a -> { cfg.anchor = a.name(); SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		// Message Offset X
		overlay.addEntry(eb.startIntField(Text.literal("Message Offset X"), cfg.offsetX)
				.setDefaultValue(0)
				.setTooltip(Text.literal("Horizontal offset from the anchor"))
				.setSaveConsumer(v -> { cfg.offsetX = v; SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		// Message Offset Y
		overlay.addEntry(eb.startIntField(Text.literal("Message Offset Y"), cfg.offsetY)
				.setDefaultValue(80)
				.setTooltip(Text.literal("Vertical offset from the anchor"))
				.setSaveConsumer(v -> { cfg.offsetY = v; SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		// Text Alignment
		overlay.addEntry(eb.startEnumSelector(Text.literal("Text Alignment"), Align.class, toAlign(cfg.textAlign))
				.setDefaultValue(Align.CENTER)
				.setTooltip(Text.literal("Text alignment inside the overlay box"))
				.setSaveConsumer(a -> { cfg.textAlign = a.name(); SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		// Text Scale
		overlay.addEntry(eb.startFloatField(Text.literal("Text Scale"), cfg.textScale)
				.setMin(0.5f).setMax(3.5f)
				.setTooltip(Text.literal("Scale multiplier for the notification text"))
				.setSaveConsumer(v -> { cfg.textScale = clamp(v, 0.5f, 3.5f); SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
		overlay.addEntry(eb.startIntField(Text.literal("Message Duration"), notifSeconds)
				.setDefaultValue(15)
				.setTooltip(Text.literal("How many seconds the message stays (<=0 uses server duration)"))
				.setSaveConsumer(sec -> {
					cfg.defaultDuration = sec <= 0 ? sec : sec * 20;
					SleepNotifierClient.applyClientConfig(cfg);
				})
				.build());

		int leadSeconds = cfg.morningWarningLeadTicks / 20;
		overlay.addEntry(eb.startIntField(Text.literal("Seconds Until Morning"), leadSeconds)
				.setDefaultValue(60)
				.setTooltip(Text.literal("How many seconds before sunrise should you be warned?"))
				.setSaveConsumer(sec -> {
					cfg.morningWarningLeadTicks = Math.max(0, sec) * 20;
					SleepNotifierClient.applyClientConfig(cfg);
				})
				.build());

		overlay.addEntry(eb.startStrField(Text.literal("Color (#RRGGBB or #AARRGGBB)"), cfg.colorHex)
				.setDefaultValue("#FFFFFF")
				.setTooltip(Text.literal("Base text color (optionally include alpha)"))
				.setSaveConsumer(v -> {
					cfg.colorHex = validateColor(v, cfg.colorHex);
					SleepNotifierClient.applyClientConfig(cfg);
				})
				.build());

		ConfigCategory sound = builder.getOrCreateCategory(Text.literal("Phantom Sounds"));

		sound.addEntry(eb.startBooleanToggle(Text.literal("Enable Phantom Screams"), cfg.enablePhantomScreams)
				.setDefaultValue(true)
				.setTooltip(Text.literal("Play phantom sounds for nightfall and morning warnings"))
				.setSaveConsumer(v -> { cfg.enablePhantomScreams = v; SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		sound.addEntry(eb.startFloatField(Text.literal("Night Scream Volume"), cfg.nightScreamVolume)
				.setMin(0f).setMax(3f)
				.setTooltip(Text.literal("Volume multiplier for nightfall phantom sound (0-3.0)"))
				.setSaveConsumer(v -> { cfg.nightScreamVolume = clamp(v, 0f, 3f); SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		sound.addEntry(eb.startFloatField(Text.literal("Morning Scream Volume"), cfg.morningScreamVolume)
				.setMin(0f).setMax(3f)
				.setTooltip(Text.literal("Volume multiplier for morning warning phantom sound (0-3.0)"))
				.setSaveConsumer(v -> { cfg.morningScreamVolume = clamp(v, 0f, 3f); SleepNotifierClient.applyClientConfig(cfg); })
				.build());

		return builder.build();
	}

	private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

	private static String validateColor(String in, String fallback) {
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

	private static Anchor toAnchor(String s) { try { return Anchor.valueOf(sanitize(s)); } catch (Exception e) { return Anchor.TOP_CENTER; } }
	private static Align toAlign(String s)  { try { return Align.valueOf(sanitize(s)); }  catch (Exception e) { return Align.CENTER; } }
	private static String sanitize(String v) { return v == null ? "" : v.trim().toUpperCase(); }
}