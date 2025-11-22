package hawkshock.nightnotifier.client.ui;

import hawkshock.nightnotifier.config.ClientDisplayConfig;
import hawkshock.nightnotifier.NightNotifierClient;
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
        // Adjust clamps to new min (0.25) for scale and keep 0-3 for volumes
        cfg.textScale = clamp(cfg.textScale, 0.25f, 2.5f);
        cfg.nightScreamVolume = clamp(cfg.nightScreamVolume, 0f, 3f);
        cfg.morningScreamVolume = clamp(cfg.morningScreamVolume, 0f, 3f);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("NightNotifier"))
                .setSavingRunnable(() -> {
                    ClientDisplayConfig.save(cfg);
                    NightNotifierClient.applyClientConfig(cfg);
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory overlay = builder.getOrCreateCategory(Text.literal("Message Overlay"));

        overlay.addEntry(eb.startBooleanToggle(Text.literal("Enable Notifications"), cfg.enableNotifications)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Disabling will prevent all messages from this mod"))
                .setSaveConsumer(v -> apply(cfg, c -> c.enableNotifications = v))
                .build());

        overlay.addEntry(eb.startBooleanToggle(Text.literal("Use My Message Style"), cfg.useClientStyle)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Disabling will use the default notification style of the server"))
                .setSaveConsumer(v -> apply(cfg, c -> c.useClientStyle = v))
                .build());

        overlay.addEntry(eb.startBooleanToggle(Text.literal("Show in Nether"), cfg.showNetherNotifications)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Display notifications while in the Nether"))
                .setSaveConsumer(v -> apply(cfg, c -> c.showNetherNotifications = v))
                .build());

        overlay.addEntry(eb.startBooleanToggle(Text.literal("Show in End"), cfg.showEndNotifications)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Display notifications while in The End"))
                .setSaveConsumer(v -> apply(cfg, c -> c.showEndNotifications = v))
                .build());

        overlay.addEntry(eb.startEnumSelector(Text.literal("Message Anchor"), Anchor.class, toAnchor(cfg.anchor))
                .setDefaultValue(Anchor.TOP_CENTER)
                .setTooltip(Text.literal("Screen anchor for the message overlay"))
                .setSaveConsumer(a -> apply(cfg, c -> c.anchor = a.name()))
                .build());

        // Offset X
        overlay.addEntry(eb.startIntField(Text.literal("Message Offset X"), cfg.offsetX)
                .setDefaultValue(0)
                .setTooltip(Text.literal("Horizontal offset from anchor"))
                .setSaveConsumer(v -> apply(cfg, c -> c.offsetX = v))
                .build());

        // Offset Y
        overlay.addEntry(eb.startIntField(Text.literal("Message Offset Y"), cfg.offsetY)
                .setDefaultValue(80)
                .setTooltip(Text.literal("Vertical offset from anchor"))
                .setSaveConsumer(v -> apply(cfg, c -> c.offsetY = v))
                .build());

        overlay.addEntry(eb.startEnumSelector(Text.literal("Text Alignment"), Align.class, toAlign(cfg.textAlign))
                .setDefaultValue(Align.CENTER)
                .setTooltip(Text.literal("Text alignment inside the overlay box"))
                .setSaveConsumer(a -> apply(cfg, c -> c.textAlign = a.name()))
                .build());

        // Text Scale as percent slider (25% - 250% -> 0.25f - 2.5f)
        int scalePercent = clampInt(Math.round(cfg.textScale * 100f), 25, 250);
        overlay.addEntry(eb.startIntSlider(Text.literal("Text Scale"), scalePercent, 25, 250)
                .setDefaultValue(170) // 1.7f -> 170%
                .setTooltip(Text.literal("Text scale (25% - 250%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.textScale = clamp(p / 100f, 0.25f, 2.5f)))
                .build());

        int notifSeconds = cfg.defaultDuration <= 0 ? cfg.defaultDuration : cfg.defaultDuration / 20;
        overlay.addEntry(eb.startIntField(Text.literal("Message Duration"), notifSeconds)
                .setDefaultValue(15)
                .setTooltip(Text.literal("Seconds the message stays (<=0 uses server duration)"))
                .setSaveConsumer(sec -> apply(cfg, c -> c.defaultDuration = (sec <= 0 ? sec : sec * 20)))
                .build());

        int leadSeconds = cfg.morningWarningLeadTicks / 20;
        overlay.addEntry(eb.startIntField(Text.literal("Seconds Until Morning"), leadSeconds)
                .setDefaultValue(60)
                .setTooltip(Text.literal("How many seconds before sunrise to warn"))
                .setSaveConsumer(sec -> apply(cfg, c -> c.morningWarningLeadTicks = Math.max(0, sec) * 20))
                .build());

        overlay.addEntry(eb.startStrField(Text.literal("Color (#RRGGBB or #AARRGGBB)"), cfg.colorHex)
                .setDefaultValue("#FFFFFF")
                .setTooltip(Text.literal("Base text color (optionally with alpha)"))
                .setSaveConsumer(v -> apply(cfg, c -> c.colorHex = validateColor(v, c.colorHex)))
                .build());

        ConfigCategory sound = builder.getOrCreateCategory(Text.literal("Phantom Sounds"));

        sound.addEntry(eb.startBooleanToggle(Text.literal("Enable Phantom Screams"), cfg.enablePhantomScreams)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Play phantom sounds for night/morning warnings"))
                .setSaveConsumer(v -> apply(cfg, c -> c.enablePhantomScreams = v))
                .build());

        // Night Scream Volume as percent slider (0% - 300% -> 0.0f - 3.0f)
        int nightPercent = clampInt(Math.round(cfg.nightScreamVolume * 100f), 0, 300);
        sound.addEntry(eb.startIntSlider(Text.literal("Night Scream Volume"), nightPercent, 0, 300)
                .setDefaultValue(100) // 1.0f -> 100%
                .setTooltip(Text.literal("Nightfall volume (0% - 300%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.nightScreamVolume = clamp(p / 100f, 0f, 3f)))
                .build());

        // Morning Scream Volume as percent slider (0% - 300% -> 0.0f - 3.0f)
        int morningPercent = clampInt(Math.round(cfg.morningScreamVolume * 100f), 0, 300);
        sound.addEntry(eb.startIntSlider(Text.literal("Morning Scream Volume"), morningPercent, 0, 300)
                .setDefaultValue(200) // 2.0f -> 200%
                .setTooltip(Text.literal("Morning volume (0% - 300%)"))
                .setSaveConsumer(p -> apply(cfg, c -> c.morningScreamVolume = clamp(p / 100f, 0f, 3f)))
                .build());

        return builder.build();
    }

    private static void apply(ClientDisplayConfig cfg, java.util.function.Consumer<ClientDisplayConfig> mut) {
        mut.accept(cfg);
        NightNotifierClient.applyClientConfig(cfg);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

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

    private static Anchor toAnchor(String s) {
        try { return Anchor.valueOf(sanitize(s)); } catch (Exception e) { return Anchor.TOP_CENTER; }
    }
    private static Align toAlign(String s) {
        try { return Align.valueOf(sanitize(s)); } catch (Exception e) { return Align.CENTER; }
    }
    private static String sanitize(String v) {
        return v == null ? "" : v.trim().toUpperCase();
    }
}