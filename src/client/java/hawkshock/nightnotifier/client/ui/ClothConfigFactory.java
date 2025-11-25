package hawkshock.nightnotifier.client.ui;

import hawkshock.shared.config.ClientDisplayConfig;
import hawkshock.nightnotifier.NightNotifierClient;
import hawkshock.nightnotifier.client.ClientHandshake;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

public final class ClothConfigFactory {

    private ClothConfigFactory() {}

    private enum Anchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
    // Text alignment removed from UI; alignment is now internal-left only.

    public static boolean isClothPresent() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("cloth-config2")
            || loader.isModLoaded("cloth-config")
            || loader.isModLoaded("cloth_config");
    }

    public static Screen create(Screen parent) {
        if (!isClothPresent()) {
            return new NightNotifierConfigScreen(parent);
        }
        return buildClothScreen(parent);
    }

    private static Screen buildClothScreen(Screen parent) {
        ClientDisplayConfig cfg = ClientDisplayConfig.load();
        cfg.textScale = clamp(cfg.textScale, 0.25f, 2.5f);
        cfg.nightScreamVolume = clamp(cfg.nightScreamVolume, 0f, 3f);
        cfg.morningScreamVolume = clamp(cfg.morningScreamVolume, 0f, 3f);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("NightNotifier"))
                .setSavingRunnable(() -> {
                    // force left alignment internally
                    cfg.textAlign = "LEFT";
                    ClientDisplayConfig.save(cfg);
                    NightNotifierClient.applyClientConfig(cfg);
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // General category
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(eb.startBooleanToggle(Text.literal("Enable Notifications"), cfg.enableNotifications)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Disabling will prevent all messages from this mod"))
                .setSaveConsumer(v -> apply(cfg, c -> c.enableNotifications = v))
                .build());

        general.addEntry(eb.startBooleanToggle(Text.literal("Show in Nether"), cfg.showNetherNotifications)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Display notifications while in the Nether"))
                .setSaveConsumer(v -> apply(cfg, c -> c.showNetherNotifications = v))
                .build());

        general.addEntry(eb.startBooleanToggle(Text.literal("Show in End"), cfg.showEndNotifications)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Display notifications while in The End"))
                .setSaveConsumer(v -> apply(cfg, c -> c.showEndNotifications = v))
                .build());

        // Show All Server Players (only if server mod is detected)
        if (ClientHandshake.authoritative) {
            general.addEntry(eb.startBooleanToggle(Text.literal("Show All Server Players"), cfg.showAllOffenders)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Disable to only see messages about your lack of sleep."))
                    .setSaveConsumer(v -> apply(cfg, c -> c.showAllOffenders = v))
                    .build());
        }

        // Message Style category
        ConfigCategory style = builder.getOrCreateCategory(Text.literal("Message Style"));

        style.addEntry(eb.startBooleanToggle(Text.literal("Use My Message Style"), cfg.useClientStyle)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Disabling will use the default notification style of the server"))
                .setSaveConsumer(v -> apply(cfg, c -> c.useClientStyle = v))
                .build());

        // Message-prefixed settings first (in requested order)
        style.addEntry(eb.startIntField(Text.literal("Message Offset X"), cfg.offsetX)
                .setDefaultValue(0)
                .setTooltip(Text.literal("Horizontal offset from anchor"))
                .setSaveConsumer(v -> apply(cfg, c -> c.offsetX = v))
                .build());

        style.addEntry(eb.startIntField(Text.literal("Message Offset Y"), cfg.offsetY)
                .setDefaultValue(80)
                .setTooltip(Text.literal("Vertical offset from anchor"))
                .setSaveConsumer(v -> apply(cfg, c -> c.offsetY = v))
                .build());

        int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
        // Message Duration default changed to 4 seconds per request
        style.addEntry(eb.startIntField(Text.literal("Message Duration (s)"), notifSeconds)
                .setDefaultValue(4)
                .setTooltip(Text.literal("Seconds the message stays (<=0 uses server duration)"))
                .setSaveConsumer(sec -> apply(cfg, c -> c.defaultDuration = (sec <= 0 ? sec : sec * 20)))
                .build());

        int leadSeconds = cfg.morningWarningLeadTicks / 20;
        style.addEntry(eb.startIntField(Text.literal("Seconds Until Morning"), leadSeconds)
                .setDefaultValue(60)
                .setTooltip(Text.literal("Seconds before sunrise to warn (client styling only)"))
                .setSaveConsumer(sec -> apply(cfg, c -> c.morningWarningLeadTicks = Math.max(0, sec) * 20))
                .build());

        // Then Text-prefixed settings
        style.addEntry(eb.startStrField(Text.literal("Text Color (#RRGGBB)"), cfg.colorHex)
                .setDefaultValue("#FFFFFF")
                .setTooltip(Text.literal("Text color in #RRGGBB format"))
                .setSaveConsumer(v -> apply(cfg, c -> c.colorHex = validateTextColor(v, c.colorHex)))
                .build());

        int scalePercent = clampInt(Math.round(cfg.textScale * 100f), 25, 250);
        // Text Scale default changed to 150
        style.addEntry(eb.startIntSlider(Text.literal("Text Scale"), scalePercent, 25, 250)
                .setDefaultValue(150)
                .setTooltip(Text.literal("Text scale (25% - 250%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.textScale = clamp(p / 100f, 0.25f, 2.5f)))
                .build());

        // Sound Effects category (renamed from Phantom Sounds)
        ConfigCategory sound = builder.getOrCreateCategory(Text.literal("Sound Effects"));

        sound.addEntry(eb.startBooleanToggle(Text.literal("Enable Phantom Screams"), cfg.enablePhantomScreams)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Play phantom sounds for night/morning warnings (authoritative only for actual events)"))
                .setSaveConsumer(v -> apply(cfg, c -> c.enablePhantomScreams = v))
                .build());

        int nightPercent = clampInt(Math.round(cfg.nightScreamVolume * 100f), 0, 300);
        sound.addEntry(eb.startIntSlider(Text.literal("Night Scream Volume"), nightPercent, 0, 300)
                .setDefaultValue(100)
                .setTooltip(Text.literal("Nightfall volume (0% - 300%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.nightScreamVolume = clamp(p / 100f, 0f, 3f)))
                .build());

        int morningPercent = clampInt(Math.round(cfg.morningScreamVolume * 100f), 0, 300);
        sound.addEntry(eb.startIntSlider(Text.literal("Morning Scream Volume"), morningPercent, 0, 300)
                .setDefaultValue(200)
                .setTooltip(Text.literal("Morning volume (0% - 300%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.morningScreamVolume = clamp(p / 100f, 0f, 3f)))
                .build());

        // Progress Bar category
        ConfigCategory progress = builder.getOrCreateCategory(Text.literal("Progress Bar"));

        progress.addEntry(eb.startBooleanToggle(Text.literal("Show Progress Bar"), cfg.enableProgressBar)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Display top-center progress bar counting down to morning (client overrides server)"))
                .setSaveConsumer(v -> apply(cfg, c -> c.enableProgressBar = v))
                .build());

        progress.addEntry(eb.startBooleanToggle(Text.literal("Disable Sun Icon"), cfg.disableSunIcon)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Hide the sun icon when progress bar is shown"))
                .setSaveConsumer(v -> apply(cfg, c -> c.disableSunIcon = v))
                .build());

        progress.addEntry(eb.startBooleanToggle(Text.literal("Disable Moon Icon"), cfg.disableMoonIcon)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Hide the moon icon when progress bar is shown"))
                .setSaveConsumer(v -> apply(cfg, c -> c.disableMoonIcon = v))
                .build());

        // Compute suggested defaults using current window size when available
        int suggestedW;
        try {
            var win = MinecraftClient.getInstance().getWindow();
            int sw = win != null ? win.getScaledWidth() : 900;
            int base = sw / 3;
            suggestedW = Math.max(120, Math.min(400, Math.round(base * 0.44f)));
        } catch (Throwable t) {
            suggestedW = 200;
        }
        int suggestedH = cfg.progressBarHeight > 0 ? cfg.progressBarHeight : 10;

        int wMin = Math.max(20, Math.round(suggestedW * 0.25f));
        int wMax = Math.max(wMin + 1, Math.round(suggestedW * 2f));
        int wDefault = Math.max(wMin, Math.round(suggestedW * 0.9f));

        int hMin = Math.max(2, Math.round(suggestedH * 0.25f));
        int hMax = Math.max(hMin + 1, Math.round(suggestedH * 2f));
        int hDefault = Math.max(hMin, Math.round(suggestedH * 0.9f));

        // Width slider
        progress.addEntry(eb.startIntSlider(Text.literal("Progress Bar Width"), cfg.progressBarWidth > 0 ? cfg.progressBarWidth : wDefault, wMin, wMax)
                .setDefaultValue(wDefault)
                .setTooltip(Text.literal("Custom width in pixels; adjust between ~25% and ~200% of suggested"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressBarWidth = v))
                .build());

        // Height slider
        progress.addEntry(eb.startIntSlider(Text.literal("Progress Bar Height"), cfg.progressBarHeight > 0 ? cfg.progressBarHeight : hDefault, hMin, hMax)
                .setDefaultValue(hDefault)
                .setTooltip(Text.literal("Custom height in pixels; adjust between ~25% and ~200% of suggested"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressBarHeight = v))
                .build());

        progress.addEntry(eb.startIntField(Text.literal("Progress Bar Y Offset"), cfg.progressBarYOffset)
                .setDefaultValue(8)
                .setTooltip(Text.literal("Vertical offset for the progress bar"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressBarYOffset = v))
                .build());

        progress.addEntry(eb.startStrField(Text.literal("Dusk Color (#AARRGGBB or #RRGGBB)"), cfg.progressSectionColor0)
                .setDefaultValue(cfg.progressSectionColor0)
                .setTooltip(Text.literal("Color for client-lead section"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressSectionColor0 = validateColor(v, c.progressSectionColor0)))
                .build());

        progress.addEntry(eb.startStrField(Text.literal("Mid Night Color (#AARRGGBB or #RRGGBB)"), cfg.progressSectionColor1)
                .setDefaultValue(cfg.progressSectionColor1)
                .setTooltip(Text.literal("Color for top fraction"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressSectionColor1 = validateColor(v, c.progressSectionColor1)))
                .build());

        progress.addEntry(eb.startStrField(Text.literal("Night Ending Color (#AARRGGBB or #RRGGBB)"), cfg.progressSectionColor2)
                .setDefaultValue(cfg.progressSectionColor2)
                .setTooltip(Text.literal("Color for mid fraction"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressSectionColor2 = validateColor(v, c.progressSectionColor2)))
                .build());

        progress.addEntry(eb.startStrField(Text.literal("Morning Warning Color (#AARRGGBB or #RRGGBB)"), cfg.progressSectionColor3)
                .setDefaultValue(cfg.progressSectionColor3)
                .setTooltip(Text.literal("Color for low fraction"))
                .setSaveConsumer(v -> apply(cfg, c -> c.progressSectionColor3 = validateColor(v, c.progressSectionColor3)))
                .build());

        return builder.build();
    }

    private static void apply(ClientDisplayConfig cfg, java.util.function.Consumer<ClientDisplayConfig> mut) {
        mut.accept(cfg);
        NightNotifierClient.applyClientConfig(cfg);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int clampInt(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

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

    // New: stricter validator for the Text Color field - only accept #RRGGBB
    private static String validateTextColor(String in, String fallback) {
        if (in == null) return fallback;
        String s = in.trim().toUpperCase();
        if (!s.startsWith("#")) return fallback;
        if (s.length() != 7) return fallback;
        for (int i = 1; i < 7; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
            if (!hex) return fallback;
        }
        return s;
    }

    private static Anchor toAnchor(String s) {
        try { return Anchor.valueOf(sanitize(s)); } catch (Exception e) { return Anchor.TOP_CENTER; }
    }
    private static String sanitize(String v) { return v == null ? "" : v.trim().toUpperCase(); }
}