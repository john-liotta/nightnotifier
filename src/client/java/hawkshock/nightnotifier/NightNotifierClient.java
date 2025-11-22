package hawkshock.nightnotifier;

import hawkshock.nightnotifier.config.ClientDisplayConfig;
import hawkshock.nightnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class NightNotifierClient implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("NightNotifierClient");

	private static ClientDisplayConfig CONFIG;
	private static Instant lastConfigTimestamp = Instant.EPOCH;

	private static SoundEvent phantomScream;
	private static SoundEvent phantomFallbackNight;
	private static SoundEvent phantomFallbackWarn;
	private static boolean soundsResolved = false;

	private static void resolveClientPhantomSounds() {
		if (soundsResolved) return;
		phantomScream = Registries.SOUND_EVENT.get(Identifier.of("minecraft","entity.phantom.scream"));
		if (phantomScream == SoundEvents.INTENTIONALLY_EMPTY) phantomScream = null;
		phantomFallbackNight = SoundEvents.ENTITY_PHANTOM_AMBIENT;
		phantomFallbackWarn  = SoundEvents.ENTITY_PHANTOM_SWOOP;
		soundsResolved = true;
	}

	private static class OverlayMessage {
		private static Text message = null;
		private static int ticksRemaining = 0;
		private static int color = 0xFFFFFFFF;
		private static float scale = 1.0f;
		private static boolean styled = true;

		static void set(String msg, int serverDuration, String eventType) {
			if (!CONFIG.enableNotifications) return;

			// Dimension filtering (client preference)
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world != null) {
				if (mc.world.getRegistryKey() == World.NETHER && !CONFIG.showNetherNotifications) return;
				if (mc.world.getRegistryKey() == World.END && !CONFIG.showEndNotifications) return;
			}

			message = Text.literal(msg);
			int chosen = (CONFIG.defaultDuration > 0) ? CONFIG.defaultDuration : serverDuration;
			ticksRemaining = Math.max(10, chosen > 0 ? chosen : 300);
			applyCurrentStyle();
			if (CONFIG.enablePhantomScreams) {
				playClientSound(eventType);
			}
		}

		static void applyCurrentStyle() {
			if (message == null) return;
			if (!CONFIG.enableNotifications) {
				message = null;
				ticksRemaining = 0;
				return;
			}
			styled = CONFIG.useClientStyle;
			if (styled) {
				color = parseColor(CONFIG.colorHex);
				float ts = CONFIG.textScale;
				if (ts < 0.5f) ts = 0.5f;
				if (ts > 2.5f) ts = 2.5f;
				scale = ts;
			} else {
				color = 0xFFFFFFFF;
				scale = 1.0f;
			}
		}

		private static void playClientSound(String eventType) {
			resolveClientPhantomSounds();
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null) return;
			SoundEvent chosen = null;
			float vol = 0f;

			if ("NIGHT_START".equals(eventType)) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackNight;
				vol = CONFIG.nightScreamVolume;
			} else if ("SUNRISE_IMMINENT".equals(eventType)) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
				vol = CONFIG.morningScreamVolume;
			}

			if (vol < 0f) vol = 0f;
			if (vol > 3f) vol = 3f;

			if (chosen != null && vol > 0f) {
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

		static void tick() {
			if (ticksRemaining > 0) ticksRemaining--;
			if (ticksRemaining == 0) message = null;
		}

		static void render(DrawContext ctx) {
			if (message == null) return;
			MinecraftClient client = MinecraftClient.getInstance();
			TextRenderer tr = client.textRenderer;
			if (tr == null) return;

			int sw = client.getWindow().getScaledWidth();
			int sh = client.getWindow().getScaledHeight();
			int tw = tr.getWidth(message);
			int th = tr.fontHeight;

			int boxW = (int)(tw * scale);
			int boxH = (int)(th * scale);

			int anchorX;
			int anchorY;
			switch (CONFIG.anchor) {
				case "TOP_LEFT" -> { anchorX = 0; anchorY = 0; }
				case "TOP_RIGHT" -> { anchorX = sw - boxW; anchorY = 0; }
				case "BOTTOM_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = sh - boxH; }
				case "BOTTOM_LEFT" -> { anchorX = 0; anchorY = sh - boxH; }
				case "BOTTOM_RIGHT" -> { anchorX = sw - boxW; anchorY = sh - boxH; }
				case "TOP_CENTER" -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
				default -> { anchorX = (sw - boxW) / 2; anchorY = 0; }
			}
			anchorX += CONFIG.offsetX;
			anchorY += CONFIG.offsetY;

			int padX = styled ? 6 : 0;
			int padY = styled ? 4 : 0;
			int bgColor = styled ? 0x90000000 : 0x00000000;

			if (scale != 1.0f && tryScaleDraw(ctx, tr, message, anchorX, anchorY, tw, th, padX, padY, bgColor, color, scale)) {
				return;
			}

			if (styled && bgColor != 0) {
				ctx.fill(anchorX - padX, anchorY - padY, anchorX + tw + padX, anchorY + th + padY, bgColor);
			}
			ctx.drawTextWithShadow(tr, message, anchorX, anchorY, color);
		}

		private static boolean tryScaleDraw(DrawContext ctx,
		                                    TextRenderer tr,
		                                    Text msg,
		                                    int topLeftX,
		                                    int topLeftY,
		                                    int unscaledW,
		                                    int unscaledH,
		                                    int padX,
		                                    int padY,
		                                    int bgColor,
		                                    int textColor,
		                                    float s) {
			Object stack = ctx.getMatrices();
			Class<?> c = stack.getClass();
			Method push = find(c, "pushMatrix", "push");
			Method pop = find(c, "popMatrix", "pop");
			Method scaleM = findScale(c);
			Method translateM = findTranslate(c);
			if (push == null || pop == null || scaleM == null || translateM == null) return false;
			try {
				push.invoke(stack);
				translateM.invoke(stack, (float) topLeftX, (float) topLeftY);
				scaleM.invoke(stack, s, s);
				if (styled && bgColor != 0) {
					ctx.fill(-padX, -padY, unscaledW + padX, unscaledH + padY, bgColor);
				}
				ctx.drawTextWithShadow(tr, msg.asOrderedText(), 0, 0, textColor);
				pop.invoke(stack);
				return true;
			} catch (Throwable ignored) {
				try { pop.invoke(stack); } catch (Throwable ignored2) {}
				return false;
			}
		}

		private static Method find(Class<?> c, String... names) {
			for (String n : names)
				for (Method m : c.getMethods())
					if (m.getName().equals(n)) return m;
			return null;
		}
		private static Method findScale(Class<?> c) {
			for (Method m : c.getMethods())
				if (m.getName().equals("scale") && m.getParameterCount() == 2
						&& m.getParameterTypes()[0] == float.class
						&& m.getParameterTypes()[1] == float.class)
					return m;
			return null;
		}
		private static Method findTranslate(Class<?> c) {
			for (Method m : c.getMethods())
				if (m.getName().equals("translate") && m.getParameterCount() == 2
						&& m.getParameterTypes()[0] == float.class
						&& m.getParameterTypes()[1] == float.class)
					return m;
			return null;
		}
	}

	@Override
	public void onInitializeClient() {
		LOG.info("[Night Notifier] Client init");
		ClientDisplayConfig.load();
		OverlayMessagePayload.registerTypeSafely();

		ClientPlayNetworking.registerGlobalReceiver(OverlayMessagePayload.ID, (payload, context) ->
				context.client().execute(() -> OverlayMessage.set(payload.message(), payload.duration(), payload.eventType()))
		);

		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			checkExternalConfigModification();
			OverlayMessage.render(drawContext);
			OverlayMessage.tick();
		});
	}

	private static int parseColor(String hex) {
		if (hex == null) return 0xFFFFFFFF;
		String h = hex.trim();
		if (h.startsWith("#")) h = h.substring(1);
		try {
			if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
			if (h.length() == 8) return (int) Long.parseLong(h, 16);
		} catch (NumberFormatException ignored) {}
		return 0xFFFFFFFF;
	}

	public static void applyClientConfig(ClientDisplayConfig updated) {
		CONFIG = updated;
		lastConfigTimestamp = Instant.now();
		OverlayMessage.applyCurrentStyle();
		if (!CONFIG.enableNotifications) {
			OverlayMessage.message = null;
		}
	}

	public static void reloadConfig() {
		CONFIG = ClientDisplayConfig.load();
		lastConfigTimestamp = getConfigFileTimestamp();
		OverlayMessage.applyCurrentStyle();
	}

	private static void checkExternalConfigModification() {
		Path p = Paths.get("config", "nightnotifier_client.json");
		if (!Files.exists(p)) return;
		Instant ts = getConfigFileTimestamp();
		if (ts.isAfter(lastConfigTimestamp)) {
			reloadConfig();
		}
	}

	private static Instant getConfigFileTimestamp() {
		try {
			return Files.getLastModifiedTime(Paths.get("config", "nightnotifier_client.json")).toInstant();
		} catch (Exception e) {
			return Instant.EPOCH;
		}
	}
}
