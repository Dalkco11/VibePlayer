package com.dalkco.vibeplayer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Rounded button with a drawn icon; highlights in the accent colour on hover. */
public class IconButton extends AbstractWidget {
    public interface Painter {
        void paint(GuiGraphicsExtractor g, int x, int y, int w, int h, int color);
    }

    private final Painter painter;
    private final Runnable action;
    private final boolean round;

    public IconButton(int x, int y, int w, int h, Component name, Painter painter, Runnable action, boolean round) {
        super(x, y, w, h, name);
        this.painter = painter;
        this.action = action;
        this.round = round;
        setTooltip(Tooltip.create(name));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int accent = VibeConfig.accent();
        boolean hover = isHoveredOrFocused() && this.active;
        int r = round ? Math.min(this.width, this.height) / 2 : 4;
        int bg = hover ? (accent & 0xFFFFFF) | 0xE6000000 : round ? (accent & 0xFFFFFF) | 0x40000000 : 0xC8141420;
        Draw.round(g, getX(), getY(), this.width, this.height, r, bg);
        int color = !this.active ? 0xFF6A6A72 : hover ? 0xFF101016 : 0xFFFFFFFF;
        painter.paint(g, getX(), getY(), this.width, this.height, color);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
