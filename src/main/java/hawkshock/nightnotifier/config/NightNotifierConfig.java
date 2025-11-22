package hawkshock.nightnotifier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

<<<<<<< Updated upstream:src/main/java/hawkshock/nightnotifier/config/NightNotifierConfig.java
public final class NightNotifierConfig {
=======
/**
 * Server configuration for Sleep Notifier.
 * Added:
 *  - enableNetherNotifications
 *  - enableEndNotifications
 */
public final class SleepNotifierConfig {
>>>>>>> Stashed changes:src/main/java/hawkshock/sleepnotifier/config/SleepNotifierConfig.java
	public boolean sendTitle = true;
	public boolean sendSubtitle = true;
	public boolean sendActionBar = false;

	// Do NOT show vanilla title/subtitle/action bar to clients that have the overlay capability
	public boolean sendVanillaToModdedClients = false;

	// Overlay duration (ticks) for modded clients; if <=0 client falls back to its own default.
	public int overlayDuration = 100;

	// Phantom sound settings (server). Used for unmodded clients; modded clients use their own client config.
	public boolean enablePhantomScreams = true;
	public float nightScreamVolume = 1.0f;
	public float morningScreamVolume = 2.0f;

	// How many ticks before NIGHT_END the morning warning should trigger (default 1200 = 1 minute).
	public int morningWarningLeadTicks = 1200;

	// Dimension toggles (default false): generate notifications in Nether / End?
	public boolean enableNetherNotifications = false;
	public boolean enableEndNotifications = false;

	public int titleFadeIn = 10;
	public int titleStay = 60;
	public int titleFadeOut = 10;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = Paths.get("config", "nightnotifier.json");

	public static NightNotifierConfig loadOrCreate() {
		try {
			if (Files.notExists(CONFIG_PATH)) {
				NightNotifierConfig cfg = new NightNotifierConfig();
				save(cfg);
				return cfg;
			}
			try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
				NightNotifierConfig cfg = GSON.fromJson(r, NightNotifierConfig.class);
				return cfg != null ? cfg : new NightNotifierConfig();
			}
		} catch (IOException e) {
			return new NightNotifierConfig();
		}
	}

	public static void save(NightNotifierConfig cfg) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(cfg, w);
			}
		} catch (IOException ignored) {}
	}
}