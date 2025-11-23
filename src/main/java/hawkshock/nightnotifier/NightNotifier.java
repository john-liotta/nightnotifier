package hawkshock.nightnotifier;

import hawkshock.nightnotifier.config.NightNotifierConfig;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Original flow restored:
 *  - Trigger once at night start and once lead-ticks before sunrise.
 *  - Multi-offender listing added (configurable).
 *  - Phantom screams only for NIGHT_START / SUNRISE_IMMINENT for unmodded clients.
 */
public class NightNotifier implements ModInitializer {
    public static final String MOD_ID = "nightnotifier";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static NightNotifierConfig CONFIG;

    private final Map<RegistryKey<World>, Boolean> priorCanSleep = new HashMap<>();
    private final Map<RegistryKey<World>, Boolean> sunriseWarned = new HashMap<>();

    private static final int TICKS_PER_DAY = 24000;
    private static final long NIGHT_START = 12541L;
    private static final long NIGHT_END   = 23458L;

    private SoundEvent phantomScream;
    private SoundEvent phantomFallbackNight;
    private SoundEvent phantomFallbackWarn;

    @Override
    public void onInitialize() {
        LOGGER.info("[NightNotifier] Server init start");
        ensureConfig();
        resolvePhantomSounds();
        OverlayMessagePayload.registerTypeSafely();
        ServerTickEvents.START_WORLD_TICK.register(this::onWorldTick);
        LOGGER.info("[NightNotifier] Server init complete");
    }

    private static NightNotifierConfig ensureConfig() {
        if (CONFIG == null) CONFIG = NightNotifierConfig.loadOrCreate();
        return CONFIG;
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

    private void onWorldTick(ServerWorld world) {
        // Only Overworld (match original)
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        long dayTime = world.getTimeOfDay() % 24000L;
        boolean thundering = world.isThundering();
        boolean naturalNight = dayTime >= NIGHT_START && dayTime <= NIGHT_END;
        boolean canSleepNow = thundering || naturalNight;
        boolean previous = priorCanSleep.getOrDefault(world.getRegistryKey(), false);

        NightNotifierConfig cfg = ensureConfig();
        int lead = Math.max(0, cfg.morningWarningLeadTicks);
        long warningStartTick = Math.max(NIGHT_START, NIGHT_END - lead);

        if (canSleepNow && !previous) {
            sendNightStart(world);
            sunriseWarned.put(world.getRegistryKey(), false);
        }

        if (naturalNight && !thundering && canSleepNow
                && lead > 0
                && dayTime >= warningStartTick && dayTime < NIGHT_END
                && !sunriseWarned.getOrDefault(world.getRegistryKey(), false)) {

            boolean success = sendSunriseLead(world);
            sunriseWarned.put(world.getRegistryKey(), true);
        }

        if (!canSleepNow && previous) {
            sunriseWarned.remove(world.getRegistryKey());
        }

        priorCanSleep.put(world.getRegistryKey(), canSleepNow);
    }

    private void sendNightStart(ServerWorld world) {
        List<ServerPlayerEntity> offenders = collectOffenders(world);
        if (offenders.isEmpty()) {
            LOGGER.info("Night start: no players met rest threshold (>= {}).", CONFIG.restThresholdTicks);
            return;
        }
        broadcast(world, "Nightfall", offenders, "NIGHT_START");
    }

    private boolean sendSunriseLead(ServerWorld world) {
        List<ServerPlayerEntity> offenders = collectOffenders(world);
        if (offenders.isEmpty()) {
            LOGGER.info("Morning warning skipped: no players meet rest threshold (>= {}).", CONFIG.restThresholdTicks);
            return false;
        }

        // Use top offender to compute nights text
        ServerPlayerEntity top = offenders.get(0);
        int ticks = top.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
        int nights = ticks / TICKS_PER_DAY;
        String nightsText = nights == 1 ? "1 night" : nights + " nights";

        // Compute current seconds until actual sunrise and use that in the label
        long dayTime = world.getTimeOfDay() % 24000L;
        long remainingTicks = NIGHT_END - dayTime;
        if (remainingTicks < 0) remainingTicks += 24000L;
        int seconds = Math.max(0, (int) Math.ceil((double) remainingTicks / 20.0));
        String label = seconds + "s Until Sunrise";

        broadcast(world, label, offenders, "SUNRISE_IMMINENT");
        return true;
    }

    private List<ServerPlayerEntity> collectOffenders(ServerWorld world) {
        int threshold = CONFIG.restThresholdTicks;
        List<ServerPlayerEntity> offenders = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) {
            int tsr = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
            if (tsr >= threshold) {
                offenders.add(p);
            }
        }
        // Sort by highest time since rest descending
        offenders.sort((a, b) -> Integer.compare(
                b.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST)),
                a.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST))
        ));
        return offenders;
    }

    private void broadcast(ServerWorld world,
                           String eventLabel,
                           List<ServerPlayerEntity> offenders,
                           String eventType) {

        NightNotifierConfig cfg = ensureConfig();
        ServerPlayerEntity top = offenders.get(0);

        int topTicks = top.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
        int topNights = topTicks / TICKS_PER_DAY;
        String topNightsText = topNights == 1 ? "1 night" : topNights + " nights";
        String topName = top.getName().getString();

        // Additional offenders list
        List<String> extraNames = offenders.stream().skip(1).limit(Math.max(0, cfg.maxOffenderNames))
                .map(p -> {
                    int t = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
                    int n = t / TICKS_PER_DAY;
                    return p.getName().getString() + "(" + n + "n)";
                })
                .collect(Collectors.toList());

        int remaining = Math.max(0, offenders.size() - 1 - extraNames.size());

        String extrasPart = extraNames.isEmpty() ? "" :
                " Others: " + String.join(", ", extraNames) + (remaining > 0 ? " +" + remaining : "");

        String full = eventLabel + ": " + topName + " hasn't slept for " + topNightsText + "." + extrasPart;

        boolean enableTitle = cfg.sendTitle;
        boolean enableSubtitle = cfg.sendSubtitle;
        boolean enableActionBar = cfg.sendActionBar;
        boolean sendVanillaToModded = cfg.sendVanillaToModdedClients;

        Text titleTextSplit = Text.literal(eventLabel);
        Text subtitleTextSplit = Text.literal(topName + " hasn't slept for " + topNightsText + ".");
        Text combinedFull = Text.literal(full);
        Text actionBarEvent = Text.literal(eventLabel);
        Text actionBarFull = Text.literal(full);

        boolean nightStart = eventType.equals("NIGHT_START");
        boolean sunriseImminent = eventType.equals("SUNRISE_IMMINENT");

        SoundEvent chosen = null;
        float serverVolume = 1.0f;
        if (cfg.enablePhantomScreams) {
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
                world.playSound(
                        null,
                        player.getBlockPos(),
                        chosen,
                        SoundCategory.HOSTILE,
                        serverVolume,
                        1.0f
                );
            }

            if (modded) {
                int dur = cfg.overlayDuration > 0 ? cfg.overlayDuration : 100;
                ServerPlayNetworking.send(player, new OverlayMessagePayload(full, dur, eventType));
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
                if (titleToSend != null)    player.networkHandler.sendPacket(new TitleS2CPacket(titleToSend));
                if (subtitleToSend != null) player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleToSend));
            }
            if (actionBarToSend != null) {
                player.sendMessage(actionBarToSend, true);
            }
        }
    }
}
