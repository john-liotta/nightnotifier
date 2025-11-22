package hawkshock.sleepnotifier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Client display configuration (V5).
 * Migration:
 *  - Old field enableOverlay -> enableNotifications
 *  - New field useClientStyle (default true)
 */
public final class ClientDisplayConfig {
	public int configVersion = 5;

	// Master notification toggle (replaces enableOverlay).
	public boolean enableNotifications = true;

	// Whether to apply client styling (color, scale, box).
	public boolean useClientStyle = true;

	// Legacy field for migration (may exist in older files).
	public boolean enableOverlay = true;

	// Positioning
	public String anchor = "TOP_CENTER";
	public int offsetX = 0;
	public int offsetY = 80;

	// Visuals
	public String colorHex = "#FFFFFF";
	public float textScale = 1.7f;
	public String textAlign = "CENTER";

	// Duration override in ticks (>0 overrides server)
	public int defaultDuration = 300;

	// Phantom sound settings
	public boolean enablePhantomScreams = true;
	public float nightScreamVolume = 1.0f;
	public float morningScreamVolume = 2.0f;

	// Morning warning lead time (ticks before sunrise)
	public int morningWarningLeadTicks = 1200;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = Paths.get("config", "sleepnotifier_client.json");

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
		// Migration
		if (cfg.configVersion < 5) {
			cfg.enableNotifications = cfg.enableOverlay;
			cfg.useClientStyle = true;
			cfg.configVersion = 5;
		}
		save(cfg); // rewrite with new fields
		return cfg;
	}

	public static void save(ClientDisplayConfig cfg) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(cfg, w);
			}
		} catch (IOException ignored) {}
	}
}
