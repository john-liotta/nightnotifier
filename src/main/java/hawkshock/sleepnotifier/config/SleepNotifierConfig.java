package hawkshock.sleepnotifier.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SleepNotifierConfig {
	public boolean sendTitle = true;
	public boolean sendSubtitle = true;
	public boolean sendActionBar = false;

	// Do NOT show vanilla title/subtitle/action bar to clients that have the overlay capability
	public boolean sendVanillaToModdedClients = false;

	// Overlay duration (ticks) for modded clients; if <=0 client falls back to its own default.
	public int overlayDuration = 100;

	// Phantom sound settings (server). Used for unmodded clients; modded clients use their own client config.
	public boolean enablePhantomScreams = true;
	public float nightScreamVolume = 1.0f;   // 100%
	public float morningScreamVolume = 2.0f; // 200%

	// How many ticks before NIGHT_END the morning warning should trigger (default 1200 = 1 minute).
	public int morningWarningLeadTicks = 1200;

	public int titleFadeIn = 10;
	public int titleStay = 60;
	public int titleFadeOut = 10;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = Paths.get("config", "sleepnotifier.json");

	public static SleepNotifierConfig loadOrCreate() {
		try {
			if (Files.notExists(CONFIG_PATH)) {
				SleepNotifierConfig cfg = new SleepNotifierConfig();
				save(cfg);
				return cfg;
			}
			try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
				SleepNotifierConfig cfg = GSON.fromJson(r, SleepNotifierConfig.class);
				return cfg != null ? cfg : new SleepNotifierConfig();
			}
		} catch (IOException e) {
			return new SleepNotifierConfig();
		}
	}

	public static void save(SleepNotifierConfig cfg) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(cfg, w);
			}
		} catch (IOException ignored) {}
	}
}