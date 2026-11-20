package com.dalkco.vibeplayer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Integer slider with a step and a translated label. */
public class IntSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<Component> label;
    private final IntConsumer setter;
    private int current;

    public IntSlider(int x, int y, int w, int h, int min, int max, int step, int value,
                     IntFunction<Component> label, IntConsumer setter) {
        super(x, y, w, h, Component.empty(), (Mth.clamp(value, min, max) - min) / (double) (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.setter = setter;
        this.current = Mth.clamp(value, min, max);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (label != null) {
            setMessage(label.apply(current));
        }
    }

    @Override
    protected void applyValue() {
        int v = min + (int) Math.round(value * (max - min) / step) * step;
        v = Mth.clamp(v, min, max);
        if (v != current) {
            current = v;
            setter.accept(v);
        }
    }
}
