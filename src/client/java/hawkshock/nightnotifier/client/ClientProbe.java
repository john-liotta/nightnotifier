package hawkshock.nightnotifier.client;

import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class ClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("ClientProbe");
    private ClientProbe() {}

    public static void printDrawContextSignatures() {
        try {
            Method[] methods = DrawContext.class.getMethods();
            Arrays.stream(methods).forEach(m -> {
                // full signature
                LOG.info("DrawContext METHOD: {}", m.toString());

                // inspect parameter type names for likely relevant overloads
                String params = Arrays.stream(m.getParameterTypes())
                    .map(Class::getName)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
                if (params.contains("RenderPipeline") || params.contains("net.minecraft.util.Identifier")
                    || params.toLowerCase().contains("texture") || params.toLowerCase().contains("gpu")
                    || params.contains("com.mojang")) {
                    LOG.info("  -> Candidate (params): {}", params);
                }
            });
        } catch (Throwable t) {
            LOG.error("ClientProbe failed", t);
        }
    }
}