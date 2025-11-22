package hawkshock.sleepnotifier;

import hawkshock.sleepnotifier.config.SleepNotifierConfig;
import hawkshock.sleepnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds Nether / End dimension toggles.
 */
public class SleepNotifier implements ModInitializer {
	public static final String MOD_ID = "sleepnotifier";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SleepNotifierConfig CONFIG;

	private final Map<RegistryKey<World>, Boolean> priorCanSleep = new HashMap<>();
	private final Map<RegistryKey<World>, Boolean> oneMinuteWarned = new HashMap<>();

	private static final int REST_THRESHOLD_TICKS = 56000;
	private static final int TICKS_PER_DAY = 24000;
	private static final long NIGHT_START = 12541L;
	private static final long NIGHT_END   = 23458L;

	private SoundEvent phantomScream;
	private SoundEvent phantomFallbackNight;
	private SoundEvent phantomFallbackWarn;

	@Override
	public void onInitialize() {
		ensureConfig();
		resolvePhantomSounds();
		OverlayMessagePayload.registerTypeSafely();
		ServerTickEvents.START_WORLD_TICK.register(this::onWorldTick);
	}

	private void resolvePhantomSounds() {
		Identifier screamId = Identifier.of("minecraft", "entity.phantom.scream");
		phantomScream = Registries.SOUND_EVENT.get(screamId);
		if (phantomScream == SoundEvents.INTENTIONALLY_EMPTY) {
			phantomScream = null;
		}
		phantomFallbackNight = SoundEvents.ENTITY_PHANTOM_AMBIENT;
		phantomFallbackWarn  = SoundEvents.ENTITY_PHANTOM_SWOOP;
	}

	private static SleepNotifierConfig ensureConfig() {
		if (CONFIG == null) CONFIG = SleepNotifierConfig.loadOrCreate();
		return CONFIG;
	}

	private boolean dimensionEnabled(ServerWorld world) {
		RegistryKey<World> key = world.getRegistryKey();
		if (key.equals(World.OVERWORLD)) return true; // always enabled in overworld
		if (key.equals(World.NETHER) && CONFIG.enableNetherNotifications) return true;
		if (key.equals(World.END) && CONFIG.enableEndNotifications) return true;
		return false;
	}

	private void onWorldTick(ServerWorld world) {
		if (!dimensionEnabled(world)) return;

		long dayTime = world.getTimeOfDay() % 24000L;
		boolean thundering = world.isThundering();
		boolean naturalNight = dayTime >= NIGHT_START && dayTime <= NIGHT_END;
		boolean canSleepNow = thundering || naturalNight;
		boolean previous = priorCanSleep.getOrDefault(world.getRegistryKey(), false);

		int lead = Math.max(0, ensureConfig().morningWarningLeadTicks);
		long warningStartTick = Math.max(NIGHT_START, NIGHT_END - lead);

		if (canSleepNow && !previous) {
			sendNightStart(world);
			oneMinuteWarned.put(world.getRegistryKey(), false);
		}

		if (naturalNight && !thundering && canSleepNow
				&& lead > 0
				&& dayTime >= warningStartTick && dayTime < NIGHT_END
				&& !oneMinuteWarned.getOrDefault(world.getRegistryKey(), false)) {

			boolean success = sendOneMinuteLeft(world);
			oneMinuteWarned.put(world.getRegistryKey(), true); // suppress repeats regardless
		}

		if (!canSleepNow && previous) {
			oneMinuteWarned.remove(world.getRegistryKey());
		}

		priorCanSleep.put(world.getRegistryKey(), canSleepNow);
	}

	private void sendNightStart(ServerWorld world) {
		ServerPlayerEntity triggering = findTriggering(world);
		if (triggering == null) {
			LOGGER.debug("Night start: no player meets threshold (>= {}) in {}", REST_THRESHOLD_TICKS, world.getRegistryKey().getValue());
			return;
		}
		int ticks = triggering.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
		int nights = ticks / TICKS_PER_DAY;
		String nightsText = nights == 1 ? "1 night" : nights + " nights";
		String name = triggering.getName().getString();
		broadcast(world, "Nightfall", name, nightsText, "NIGHT_START");
	}

	private boolean sendOneMinuteLeft(ServerWorld world) {
		ServerPlayerEntity triggering = findTriggering(world);
		if (triggering == null) {
			LOGGER.debug("Morning warning skipped: no player meets threshold (>= {}) in {}", REST_THRESHOLD_TICKS, world.getRegistryKey().getValue());
			return false;
		}
		int ticks = triggering.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
		int nights = ticks / TICKS_PER_DAY;
		String nightsText = nights == 1 ? "1 night" : nights + " nights";
		String name = triggering.getName().getString();
		broadcast(world, "1 Minute Until Sunrise", name, nightsText, "SUNRISE_IMMINENT");
		return true;
	}

	private ServerPlayerEntity findTriggering(ServerWorld world) {
		ServerPlayerEntity best = null;
		int max = -1;
		for (ServerPlayerEntity p : world.getPlayers()) {
			int tsr = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
			if (tsr >= REST_THRESHOLD_TICKS && tsr > max) {
				max = tsr;
				best = p;
			}
		}
		return best;
	}

	private void broadcast(ServerWorld world, String eventLabel, String name, String nightsText, String eventType) {
		SleepNotifierConfig cfg = ensureConfig();

		final String full = eventLabel + ": " + name + " hasn't slept for " + nightsText + ".";
		final String detailOnly = name + " hasn't slept for " + nightsText + ".";

		boolean enableTitle = cfg.sendTitle;
		boolean enableSubtitle = cfg.sendSubtitle;
		boolean enableActionBar = cfg.sendActionBar;
		boolean sendVanillaToModded = cfg.sendVanillaToModdedClients;

		Text titleTextSplit = Text.literal(eventLabel);
		Text subtitleTextSplit = Text.literal(detailOnly);
		Text combinedFull = Text.literal(full);
		Text actionBarEvent = Text.literal(eventLabel);
		Text actionBarFull = Text.literal(full);

		boolean nightStart = eventType.equals("NIGHT_START");
		boolean sunriseImminent = eventType.equals("SUNRISE_IMMINENT");

		SoundEvent chosen = null;
		float serverVolume = 1.0f;
		// Restrict server phantom sounds to Overworld only (they don't spawn elsewhere)
		if (cfg.enablePhantomScreams && world.getRegistryKey().equals(World.OVERWORLD)) {
			if (nightStart) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackNight;
				serverVolume = Math.max(0f, cfg.nightScreamVolume);
			} else if (sunriseImminent) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
				serverVolume = Math.max(0f, cfg.morningScreamVolume);
			}
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean modded = ServerPlayNetworking.canSend(player, OverlayMessagePayload.ID);

			if (!modded && chosen != null && serverVolume > 0f) {
				world.playSound(null,
						player.getBlockPos(),
						chosen,
						SoundCategory.HOSTILE,
						serverVolume,
						1.0f);
			}

			if (modded) {
				String overlayMsg = (nightStart ? "Nightfall: " : eventLabel + ": ") + name + " hasn't slept for " + nightsText + ".";
				int dur = cfg.overlayDuration > 0 ? cfg.overlayDuration : 100;
				ServerPlayNetworking.send(player, new OverlayMessagePayload(overlayMsg, dur, eventType));
				if (!sendVanillaToModded) continue;
			}

			Text titleToSend = null;
			Text subtitleToSend = null;
			Text actionBarToSend = null;

			if (enableTitle && enableSubtitle) {
				titleToSend = titleTextSplit;
				subtitleToSend = subtitleTextSplit;
				if (enableActionBar) actionBarToSend = actionBarEvent;
			} else if (enableTitle) {
				titleToSend = combinedFull;
				if (enableActionBar) actionBarToSend = actionBarEvent;
			} else if (enableSubtitle) {
				subtitleToSend = combinedFull;
				if (enableActionBar) actionBarToSend = actionBarEvent;
			} else if (enableActionBar) {
				actionBarToSend = actionBarFull;
			}

			if (titleToSend != null || subtitleToSend != null) {
				player.networkHandler.sendPacket(new TitleFadeS2CPacket(cfg.titleFadeIn, cfg.titleStay, cfg.titleFadeOut));
				if (titleToSend != null) player.networkHandler.sendPacket(new TitleS2CPacket(titleToSend));
				if (subtitleToSend != null) player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleToSend));
			}
			if (actionBarToSend != null) {
				player.sendMessage(actionBarToSend, true);
			}
		}
	}
}
