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
 * Client display configuration (V4: adds morningWarningLeadTicks).
 */
public final class ClientDisplayConfig {
	public int configVersion = 4;

	public boolean enableOverlay = true;

	public String anchor = "TOP_CENTER";
	public int offsetX = 0;
	public int offsetY = 80;

	public String colorHex = "#FFFFFF";
	public float textScale = 1.7f;
	public String textAlign = "CENTER";

	public int defaultDuration = 300;

	public boolean enablePhantomScreams = true;
	public float nightScreamVolume = 1.0f;
	public float morningScreamVolume = 2.0f;

	// Client preferred lead time (ticks) before dawn for warning.
	// Note: Server timing controls when packet is sent; this value may be used later for client-side overrides.
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
		if (cfg.configVersion < 4) {
			cfg.configVersion = 4;
		}
		save(cfg);
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
