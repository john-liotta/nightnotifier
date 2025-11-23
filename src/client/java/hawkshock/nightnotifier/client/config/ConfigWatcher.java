package hawkshock.nightnotifier.client.config;

import hawkshock.shared.config.ClientDisplayConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.function.Consumer;

public final class ConfigWatcher {
    private ConfigWatcher() {}

    // Returns the last-modified timestamp of the client config file or Instant.EPOCH on error.
    public static Instant getConfigFileTimestamp() {
        try {
            return Files.getLastModifiedTime(Paths.get("config", "nightnotifier_client.json")).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    /**
     * If the on-disk config has a newer timestamp than lastKnown, load the config and invoke applyFn.
     * Returns the new timestamp (or the original lastKnown if nothing changed).
     */
    public static Instant checkAndReload(Instant lastKnown, Consumer<ClientDisplayConfig> applyFn) {
        Path p = Paths.get("config", "nightnotifier_client.json");
        if (!Files.exists(p)) return lastKnown;
        Instant ts = getConfigFileTimestamp();
        if (ts.isAfter(lastKnown)) {
            try {
                ClientDisplayConfig cfg = ClientDisplayConfig.load();
                applyFn.accept(cfg);
                return ts;
            } catch (Exception ignored) {
                // If loading fails, do not update timestamp so we'll retry next tick.
                return lastKnown;
            }
        }
        return lastKnown;
    }
}