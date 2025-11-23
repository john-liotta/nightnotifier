package hawkshock.nightnotifier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Client display configuration.
 * Added dimension visibility, offender display preference, and migration (version 9).
 */
public final class ClientDisplayConfig {
    public int configVersion = 9;

    public boolean enableNotifications = true;
    public boolean useClientStyle = true;
    public boolean enablePhantomScreams = true;

    public String anchor = "TOP_CENTER";
    public int offsetX = 0;
    public int offsetY = 80;

    public String colorHex = "#FFFFFF";
    public float textScale = 1.7f; // clamped to [0.5, 2.5]
    public String textAlign = "CENTER";

    // Default duration in ticks. 5s = 100 ticks.
    public int defaultDuration = 100;

    public float nightScreamVolume = 1.0f;
    public float morningScreamVolume = 2.0f;

    public int morningWarningLeadTicks = 1200;

    public boolean showNetherNotifications = false;
    public boolean showEndNotifications = false;

    // New: preference for showing all offenders or only the top offender (authoritative mode only).
    // Client-only simulation always shows local player only regardless of this setting.
    public boolean showAllOffenders = true;

    // New: client preference for showing the progress bar. Client choice trumps server setting.
    public boolean enableProgressBar = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "nightnotifier_client.json");

    public static ClientDisplayConfig load() {
        ClientDisplayConfig cfg = null;
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                cfg = GSON.fromJson(r, ClientDisplayConfig.class);
            } catch (IOException ignored) {}
        }
        if (cfg == null) {
            cfg = new ClientDisplayConfig();
            save(cfg);
            return cfg;
        }
        // Migration / clamp
        if (cfg.configVersion < 7) {
            if (cfg.textScale < 0.5f) cfg.textScale = 0.5f;
            if (cfg.textScale > 2.5f) cfg.textScale = 2.5f;
            cfg.configVersion = 7;
        }
        if (cfg.configVersion < 8) {
            // Introduced showAllOffenders flag
            if (!hasField(cfg, "showAllOffenders")) {
                cfg.showAllOffenders = true;
            }
            cfg.configVersion = 8;
        }
        if (cfg.configVersion < 9) {
            // Introduced enableProgressBar
            if (!hasField(cfg, "enableProgressBar")) {
                cfg.enableProgressBar = true;
            }
            cfg.configVersion = 9;
        }
        save(cfg);
        return cfg;
    }

    private static boolean hasField(Object o, String name) {
        try { return o.getClass().getDeclaredField(name) != null; } catch (NoSuchFieldException e) { return false; }
    }

    public static void save(ClientDisplayConfig cfg) {
        if (cfg.textScale < 0.5f) cfg.textScale = 0.5f;
        if (cfg.textScale > 2.5f) cfg.textScale = 2.5f;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(cfg, w);
            }
        } catch (IOException ignored) {}
    }
}
