package hawkshock.nightnotifier.client.ui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

public class VolumeSlider extends SliderWidget {
    private final String label;
    private final DoubleUnaryOperator denormalizer;
    private final DoubleConsumer onApply;

    public VolumeSlider(int x, int y, int width, int height, String label, double initial,
                        DoubleUnaryOperator denormalizer,
                        DoubleConsumer onApply) {
        super(x, y, width, height, Text.literal(label), initial);
        this.label = label;
        this.denormalizer = denormalizer;
        this.onApply = onApply;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        double denorm = denormalizer.applyAsDouble(this.value);
        setMessage(Text.literal(label + ": " + String.format("%.2f", denorm)));
    }

    @Override
    protected void applyValue() {
        onApply.accept(this.value);
        updateMessage();
    }

    public void force(double v) {
        this.value = v;
        applyValue();
    }
}