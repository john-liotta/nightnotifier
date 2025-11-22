package hawkshock.sleepnotifier;

import hawkshock.sleepnotifier.config.ClientDisplayConfig;
import hawkshock.sleepnotifier.network.OverlayMessagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.lang.reflect.Method;

public class SleepNotifierClient implements ClientModInitializer {

	private static ClientDisplayConfig CONFIG;

	private static class OverlayMessage {
		private static Text message = null;
		private static int ticksRemaining = 0;
		private static int color = 0xFFFFA000;
		private static float scale = 1.0f;

		// Client-side sound cache
		private static SoundEvent phantomScream;
		private static SoundEvent phantomFallbackNight;
		private static SoundEvent phantomFallbackWarn;
		private static boolean soundsResolved = false;

		static void set(String msg, int serverDuration, String eventType) {
			if (!CONFIG.enableOverlay) return;
			message = Text.literal(msg);
			int chosen = (CONFIG.defaultDuration > 0) ? CONFIG.defaultDuration : serverDuration;
			ticksRemaining = Math.max(10, chosen > 0 ? chosen : 300);
			color = parseColor(CONFIG.colorHex);
			scale = CONFIG.textScale > 0 ? CONFIG.textScale : 1.0f;

			// Client plays sound locally based on its own config (trumps server).
			if (CONFIG.enablePhantomScreams) {
				playClientSound(eventType);
			}
		}

		private static void resolveSoundsIfNeeded() {
			if (soundsResolved) return;
			var id = Identifier.of("minecraft", "entity.phantom.scream");
			phantomScream = Registries.SOUND_EVENT.get(id);
			if (phantomScream == SoundEvents.INTENTIONALLY_EMPTY) phantomScream = null;
			phantomFallbackNight = SoundEvents.ENTITY_PHANTOM_AMBIENT;
			phantomFallbackWarn  = SoundEvents.ENTITY_PHANTOM_SWOOP;
			soundsResolved = true;
		}

		private static void playClientSound(String eventType) {
			resolveSoundsIfNeeded();
			var client = MinecraftClient.getInstance();
			if (client == null || client.player == null) return;

			boolean nightStart = "NIGHT_START".equals(eventType);
			boolean sunriseImminent = "SUNRISE_IMMINENT".equals(eventType);

			SoundEvent chosen = null;
			float vol = 1.0f;
			if (nightStart) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackNight;
				vol = Math.max(0f, CONFIG.nightScreamVolume);
			} else if (sunriseImminent) {
				chosen = phantomScream != null ? phantomScream : phantomFallbackWarn;
				vol = Math.max(0f, CONFIG.morningScreamVolume);
			}
			if (chosen != null && vol > 0f) {
				// Client-side playback at player's position
				client.player.playSound(chosen, vol, 1.0f);
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

			int drawXScaled = switch (CONFIG.textAlign.toUpperCase()) {
				case "LEFT" -> anchorX;
				case "RIGHT" -> anchorX; // single line fills box; right aligns to anchorX
				default -> anchorX;      // center collapses to anchorX for single line
			};
			int drawYScaled = anchorY;

			int padX = 6;
			int padY = 4;
			int bgColor = 0x90000000;

			if (scale != 1.0f && tryScaleDraw(ctx, tr, message, drawXScaled, drawYScaled, tw, th, padX, padY, bgColor, color, scale)) {
				return;
			}

			ctx.fill(drawXScaled - padX, drawYScaled - padY,
					drawXScaled + tw + padX, drawYScaled + th + padY, bgColor);
			ctx.drawTextWithShadow(tr, message, drawXScaled, drawYScaled, color);
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
				ctx.fill(-padX, -padY, unscaledW + padX, unscaledH + padY, bgColor);
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
		CONFIG = ClientDisplayConfig.load();
		OverlayMessagePayload.registerTypeSafely();
		ClientPlayNetworking.registerGlobalReceiver(OverlayMessagePayload.ID, (payload, context) ->
				context.client().execute(() ->
						OverlayMessage.set(payload.message(), payload.duration(), payload.eventType()))
		);
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			OverlayMessage.render(drawContext);
			OverlayMessage.tick();
		});
	}

	private static int parseColor(String hex) {
		if (hex == null) return 0xFFFFA000;
		String h = hex.trim();
		if (h.startsWith("#")) h = h.substring(1);
		try {
			if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
			if (h.length() == 8) return (int) Long.parseLong(h, 16);
		} catch (NumberFormatException ignored) {}
		return 0xFFFFA000;
	}
}
