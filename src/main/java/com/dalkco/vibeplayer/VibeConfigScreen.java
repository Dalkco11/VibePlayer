package com.dalkco.vibeplayer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Settings: look on the left, behaviour on the right, live preview of the card on top. */
public class VibeConfigScreen extends Screen {
    private static final int COL_W = 160;
    private static final int H = 20;
    private static final int GAP = 21;

    private final Screen parent;
    private int top;

    public VibeConfigScreen(Screen parent) {
        super(Component.translatable("vibeplayer.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VibeConfig.Data c = VibeConfig.get();
        top = Math.max(76, this.height / 2 - 4 * GAP - 4);
        int lx = this.width / 2 - COL_W - 4;
        int rx = this.width / 2 + 4;

        int y = top;
        y = toggle(lx, y, "hud", c.hud, v -> c.hud = v);
        addRenderableWidget(tip(CycleButton.<VibeConfig.Style>builder(
                        st -> Component.translatable("vibeplayer.style." + st.name().toLowerCase(Locale.ROOT)), c.style)
                .withValues(VibeConfig.Style.values())
                .create(lx, y, COL_W, H, Component.translatable("vibeplayer.config.style"),
                        (b, v) -> { c.style = v; VibeConfig.save(); }), "style"));
        y += GAP;
        addRenderableWidget(tip(CycleButton.<VibeConfig.Position>builder(
                        p -> Component.translatable("vibeplayer.position." + p.name().toLowerCase(Locale.ROOT)), c.position)
                .withValues(VibeConfig.Position.values())
                .create(lx, y, COL_W, H, Component.translatable("vibeplayer.config.position"),
                        (b, v) -> { c.position = v; VibeConfig.save(); }), "position"));
        y += GAP;
        addRenderableWidget(tip(CycleButton.<Integer>builder(i -> i < 0
                                ? Component.translatable("vibeplayer.accent.auto")
                                : Component.translatable("vibeplayer.accent." + i).withColor(VibeConfig.ACCENTS[i] & 0xFFFFFF),
                        c.accent)
                .withValues(-1, 0, 1, 2, 3, 4, 5)
                .create(lx, y, COL_W, H, Component.translatable("vibeplayer.config.accent"),
                        (b, v) -> { c.accent = v; VibeConfig.save(); }), "accent"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 50, 200, 10, c.scale,
                v -> Component.translatable("vibeplayer.config.scale", v), v -> c.scale = v), "scale"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 0, 100, 5, c.opacity,
                v -> Component.translatable("vibeplayer.config.opacity", v), v -> c.opacity = v), "opacity"));
        y += GAP;
        y = toggle(lx, y, "hide_paused", c.hideWhenPaused, v -> c.hideWhenPaused = v);

        y = top;
        y = toggle(rx, y, "cover", c.showCover, v -> c.showCover = v);
        y = toggle(rx, y, "progress", c.showProgress, v -> c.showProgress = v);
        y = toggle(rx, y, "app", c.showApp, v -> c.showApp = v);
        y = toggle(rx, y, "equalizer", c.equalizer, v -> c.equalizer = v);
        y = toggle(rx, y, "marquee", c.marquee, v -> c.marquee = v);
        y = toggle(rx, y, "hide_idle", c.hideWhenIdle, v -> c.hideWhenIdle = v);
        addRenderableWidget(Button.builder(Component.translatable("vibeplayer.config.reset"), b -> {
                    VibeConfig.reset();
                    rebuildWidgets();
                }).bounds(rx, y, COL_W, H)
                .tooltip(Tooltip.create(Component.translatable("vibeplayer.config.reset.tooltip"))).build());

        int by = Math.min(top + 7 * GAP + 16, this.height - 46);
        addRenderableWidget(Button.builder(Component.translatable("vibeplayer.config.player"),
                        b -> this.minecraft.gui.setScreen(new PlayerScreen(this)))
                .bounds(lx, by, COL_W, H).build());
        addRenderableWidget(Button.builder(Component.translatable("vibeplayer.config.restart"), b -> MediaBridge.get().restart())
                .bounds(rx, by, COL_W, H)
                .tooltip(Tooltip.create(Component.translatable("vibeplayer.config.restart.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, by + 24, 200, H).build());
    }

    private int toggle(int x, int y, String key, boolean value, Consumer<Boolean> setter) {
        addRenderableWidget(tip(CycleButton.onOffBuilder(value)
                .create(x, y, COL_W, H, Component.translatable("vibeplayer.config." + key), (b, v) -> {
                    setter.accept(v);
                    VibeConfig.save();
                }), key));
        return y + GAP;
    }

    private static <T extends AbstractWidget> T tip(T widget, String key) {
        widget.setTooltip(Tooltip.create(Component.translatable("vibeplayer.config." + key + ".tooltip")));
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // live preview: the real track if something plays, otherwise a sample
        VibeConfig.Data c = VibeConfig.get();
        MediaState st = MediaBridge.get().state();
        long now = System.currentTimeMillis();
        if (!st.active()) {
            st = new MediaState("Spotify.exe", Component.translatable("vibeplayer.preview.title").getString(),
                    Component.translatable("vibeplayer.preview.artist").getString(), "", "Playing",
                    (now / 1000.0) % 200, 200, now, true, true, true, true, List.of("Spotify.exe"), "", "preview");
        }
        int w = PlayerHud.width(this.font, st, c);
        int h = PlayerHud.height(c);
        float s = Math.min(c.scale / 100f, 1.4f);
        int areaTop = 20;
        int areaBottom = top - 16;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        int x = Math.round(this.width / 2f / s) - w / 2;
        int y = Math.round((areaTop + Math.max(0, (areaBottom - areaTop - h * s) / 2f)) / s);
        PlayerHud.paint(g, this.font, x, y, st, c.hud ? 1f : 0.45f, c, now);
        g.pose().popMatrix();

        Component status = Component.translatable("vibeplayer.status." + MediaBridge.get().status().name().toLowerCase(Locale.ROOT));
        g.centeredText(this.font, status, this.width / 2, top - 12, 0xFF9A9AA6);
    }

    @Override
    public void onClose() {
        VibeConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
