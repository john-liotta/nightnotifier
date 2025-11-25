package hawkshock.nightnotifier.client.sound;

import hawkshock.shared.config.ClientDisplayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class SoundManager {
    private SoundManager() {}

    private static SoundEvent phantomScream;
    private static SoundEvent phantomFallbackNight;
    private static SoundEvent phantomFallbackWarn;
    private static boolean resolved = false;

    public static void initIfNeeded() {
        if (resolved) return;
        phantomScream = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.phantom.scream"));
        if (phantomScream == SoundEvents.INTENTIONALLY_EMPTY) phantomScream = null;
        phantomFallbackNight = SoundEvents.ENTITY_PHANTOM_AMBIENT;
        phantomFallbackWarn  = SoundEvents.ENTITY_PHANTOM_SWOOP;
        resolved = true;
    }

    /**
     * Play the appropriate phantom sound for the given eventType if enabled.
     * Accepts both server event names and client-simulated variants (e.g. "CLIENT_SIM_SUNRISE_IMMINENT").
     */
    public static void playForEvent(String eventType, MinecraftClient client, ClientDisplayConfig cfg) {
        if (cfg == null || !cfg.enablePhantomScreams) return;
        initIfNeeded();
        if (client == null || client.player == null) return;

        SoundEvent chosen = null;
        float vol = 0f;

        if (eventType != null && eventType.contains("NIGHT_START")) {
            chosen = phantomScream != null ? phantomScream : phantomFallbackNight;
            vol = cfg.nightScreamVolume;
        } else if (eventType != null && eventType.contains("SUNRISE_IMMINENT")) {
            chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
            vol = cfg.morningScreamVolume;
        }

        if (vol < 0f) vol = 0f;
        if (vol > 3f) vol = 3f;

        if (chosen != null && vol > 0f && client.world != null) {
            client.world.playSound(
                    client.player,
                    client.player.getX(),
                    client.player.getY(),
                    client.player.getZ(),
                    chosen,
                    SoundCategory.HOSTILE,
                    vol,
                    1.0f
            );
        }
    }
}