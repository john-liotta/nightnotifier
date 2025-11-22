package hawkshock.nightnotifier;

import hawkshock.nightnotifier.config.NightNotifierConfig;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NightNotifier implements ModInitializer {
	public static final String MOD_ID = "nightnotifier"; // keep id for compatibility
	public static final Logger LOGGER = LoggerFactory.getLogger("NightNotifier"); // updated logger name

	@Override
	public void onInitialize() {
		LOGGER.info("[NightNotifier] Server init start");
		NightNotifierConfig.loadOrCreate();
		OverlayMessagePayload.registerTypeSafely();
		LOGGER.info("[NightNotifier] Server init complete");
		// (rest of original logic preserved)
	}
	// (keep existing methods unchanged below this point)
}
