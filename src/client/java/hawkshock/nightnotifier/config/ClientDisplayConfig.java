package hawkshock.shared.config;

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
 *
 * This is a shared copy used while migrating callers to a single canonical config class.
 */
public final class ClientDisplayConfig {
    public int configVersion = 10;

    public boolean enableNotifications = true;
    public boolean useClientStyle = true;
    public boolean enablePhantomScreams = true;

    public String anchor = "TOP_CENTER";
    public int offsetX = 0;
    public int offsetY = 80;

    public String colorHex = "#FFFFFF";
    public float textScale = 1.7f; // clamped to [0.5, 2.5]
    // Force default alignment to LEFT internally
    public String textAlign = "LEFT";

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

    // New: progress bar dimension overrides. -1 = automatic (computed)
    public int progressBarWidth = -1;
    public int progressBarHeight = -1;

    // New: Y offset for the progress bar (overrides internal y)
    public int progressBarYOffset = 8;

    // New: icons toggles
    public boolean disableSunIcon = false;
    public boolean disableMoonIcon = false;

    // New: progress bar section colors (hex). Section 0 = client-lead (red), 1 = top fraction, 2 = mid, 3 = low
    public String progressSectionColor0 = "#FFFF4444"; // within client lead (red)
    public String progressSectionColor1 = "#FF4A90E2"; // top (medium blue)
    public String progressSectionColor2 = "#FF003366"; // mid (dark blue)
    public String progressSectionColor3 = "#FF7FBFFF"; // low (light blue)

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
        if (cfg.configVersion < 10) {
            // Introduced progress bar overrides, y-offset, icon toggles and section colors
            if (!hasField(cfg, "progressBarWidth")) cfg.progressBarWidth = -1;
            if (!hasField(cfg, "progressBarHeight")) cfg.progressBarHeight = -1;
            if (!hasField(cfg, "progressBarYOffset")) cfg.progressBarYOffset = 8;
            if (!hasField(cfg, "disableSunIcon")) cfg.disableSunIcon = false;
            if (!hasField(cfg, "disableMoonIcon")) cfg.disableMoonIcon = false;
            if (!hasField(cfg, "progressSectionColor0")) cfg.progressSectionColor0 = "#FFFF4444";
            if (!hasField(cfg, "progressSectionColor1")) cfg.progressSectionColor1 = "#FF4A90E2";
            if (!hasField(cfg, "progressSectionColor2")) cfg.progressSectionColor2 = "#FF003366";
            if (!hasField(cfg, "progressSectionColor3")) cfg.progressSectionColor3 = "#FF7FBFFF";
            cfg.configVersion = 10;
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