package com.dalkco.vibeplayer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Invisible clickable area over a part of the card (progress bar, cover) while chat is open. */
public class HitArea extends AbstractWidget {
    private final Consumer<MouseButtonEvent> onClick;

    public HitArea(Consumer<MouseButtonEvent> onClick) {
        super(0, 0, 0, 0, Component.empty());
        this.onClick = onClick;
        this.visible = false;
    }

    public void place(PlayerHud.Bounds b) {
        if (b == null) {
            this.visible = false;
            return;
        }
        this.visible = true;
        setX(b.x1());
        setY(b.y1());
        setWidth(Math.max(1, b.x2() - b.x1()));
        setHeight(Math.max(1, b.y2() - b.y1()));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // drawn by PlayerHud
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onClick.accept(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
