package com.dalkco.vibeplayer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Big player: large cover, track info, seekable progress, controls and the source picker. */
public class PlayerScreen extends Screen {
    private static final int W = 340;
    private static final int H = 184;
    private static final int COVER = 120;

    private final Screen parent;
    private int px;
    private int py;
    private int barX;
    private int barY;
    private int barW;
    private IconButton playButton;
    private IconButton prevButton;
    private IconButton nextButton;
    private Button sourceButton;

    public PlayerScreen(Screen parent) {
        super(Component.translatable("vibeplayer.player.title"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        px = this.width / 2 - W / 2;
        py = Math.max(8, this.height / 2 - H / 2 - 8);
        int rx = px + 16 + COVER + 14;
        int rw = px + W - 16 - rx;
        barX = rx;
        barW = rw;
        barY = py + 104;

        int cy = py + 124;
        int mid = rx + rw / 2;
        MediaBridge bridge = MediaBridge.get();
        prevButton = addRenderableWidget(new IconButton(mid - 15 - 6 - 22, cy + 4, 22, 22,
                Component.translatable("vibeplayer.control.prev"), Draw::prev, bridge::previous, true));
        playButton = addRenderableWidget(new IconButton(mid - 15, cy, 30, 30,
                Component.translatable("vibeplayer.control.toggle"),
                (g, x, y, w, h, color) -> {
                    if (MediaBridge.get().state().playing()) {
                        Draw.pause(g, x, y, w, h, color);
                    } else {
                        Draw.play(g, x, y, w, h, color);
                    }
                }, bridge::togglePlay, true));
        nextButton = addRenderableWidget(new IconButton(mid + 15 + 6, cy + 4, 22, 22,
                Component.translatable("vibeplayer.control.next"), Draw::next, bridge::next, true));

        int by = py + H - 26;
        sourceButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleSource())
                .bounds(px + 16, by, 168, 20)
                .tooltip(Tooltip.create(Component.translatable("vibeplayer.player.source.tooltip"))).build());
        addRenderableWidget(new IconButton(px + 16 + 172, by, 20, 20, Component.translatable("vibeplayer.control.settings"),
                (g, x, y, w, h, color) -> Draw.glyph(g, this.font, "⚙", x, y, w, h, color),
                () -> this.minecraft.gui.setScreen(new VibeConfigScreen(this)), false));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(px + W - 16 - 100, by, 100, 20).build());
    }

    /** auto → each running app → auto. */
    private void cycleSource() {
        VibeConfig.Data c = VibeConfig.get();
        List<String> options = new ArrayList<>();
        options.add("");
        for (String s : MediaBridge.get().state().sessions()) {
            if (!options.contains(s)) {
                options.add(s);
            }
        }
        int i = options.indexOf(c.pinned == null ? "" : c.pinned);
        c.pinned = options.get((i + 1) % options.size());
        VibeConfig.save();
        MediaBridge.get().pin(c.pinned);
    }

    private Component sourceLabel() {
        String pinned = VibeConfig.get().pinned;
        Component value = pinned == null || pinned.isEmpty()
                ? Component.translatable("vibeplayer.player.source.auto")
                : Draw.app(pinned);
        return Component.translatable("vibeplayer.player.source", value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        MediaState st = MediaBridge.get().state();
        long now = System.currentTimeMillis();
        int acc = VibeConfig.accent();

        playButton.active = st.active();
        prevButton.active = st.active() && st.canPrev();
        nextButton.active = st.active() && st.canNext();
        sourceButton.setMessage(sourceLabel());

        // panel with a soft glow in the accent (cover) colour
        Draw.round(g, px - 3, py - 3, W + 6, H + 6, 12, (acc & 0xFFFFFF) | 0x26000000);
        Draw.round(g, px - 1, py - 1, W + 2, H + 2, 10, (acc & 0xFFFFFF) | 0x50000000);
        Draw.round(g, px, py, W, H, 9, 0xF00C0C12);
        g.fillGradient(px + 10, py + 1, px + W - 10, py + 40, (acc & 0xFFFFFF) | 0x22000000, 0x000C0C12);

        int cx = px + 16;
        int cy = py + 16;
        if (CoverArt.has(st)) {
            Draw.cover(g, cx, cy, COVER, 1f);
        } else {
            Draw.noCover(g, this.font, cx, cy, COVER, acc, 1f);
        }
        if (st.active()) {
            Draw.equalizer(g, cx + COVER - 18, cy + COVER - 4, 5, 14, st.playing(), (acc & 0xFFFFFF) | 0xE0000000);
        }

        int rx = barX;
        int rw = barW;
        int ty = py + 18;
        if (st.active()) {
            String app = Draw.appName(st.app());
            int chipW = this.font.width(app) + 8;
            Draw.round(g, rx, ty - 2, chipW, 11, 3, (acc & 0xFFFFFF) | 0xD0000000);
            g.text(this.font, app, rx + 4, ty, 0xFF101016, false);
            ty += 15;
            List<FormattedCharSequence> lines = this.font.split(Component.literal(
                    st.title().isEmpty() ? app : st.title()), rw);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.pose().pushMatrix();
                g.pose().translate(rx, ty);
                g.pose().scale(1.15f, 1.15f);
                g.text(this.font, lines.get(i), 0, 0, 0xFFFFFFFF, false);
                g.pose().popMatrix();
                ty += 12;
            }
            ty += 2;
            g.text(this.font, Draw.trim(this.font, st.artist(), rw), rx, ty, 0xFFC8C8D2, false);
            ty += 11;
            if (!st.album().isEmpty() && !st.album().equals(st.title())) {
                g.text(this.font, Draw.trim(this.font, st.album(), rw), rx, ty, 0xFF80808C, false);
            }

            if (st.dur() > 0) {
                double p = st.position(now);
                int fill = (int) Math.round(rw * Math.max(0, Math.min(1, p / st.dur())));
                boolean hover = st.canSeek() && mouseX >= rx && mouseX < rx + rw && mouseY >= barY - 4 && mouseY < barY + 8;
                Draw.round(g, rx, barY, rw, 4, 2, 0x40FFFFFF);
                Draw.round(g, rx, barY, Math.max(2, fill), 4, 2, acc);
                Draw.round(g, rx + fill - 3, barY - 2, 8, 8, 4, 0xFFFFFFFF);
                if (hover) {
                    double hp = st.dur() * (mouseX - rx) / (double) rw;
                    String tip = Draw.time(hp);
                    int tw = this.font.width(tip) + 6;
                    int tx = Math.max(rx, Math.min(rx + rw - tw, mouseX - tw / 2));
                    Draw.round(g, tx, barY - 15, tw, 11, 3, 0xE0202028);
                    g.text(this.font, tip, tx + 3, barY - 13, 0xFFFFFFFF, false);
                }
                g.text(this.font, Draw.time(p), rx, barY + 8, 0xFFA8A8B3, false);
                String d = Draw.time(st.dur());
                g.text(this.font, d, rx + rw - this.font.width(d), barY + 8, 0xFFA8A8B3, false);
            }
        } else {
            Component idle = Component.translatable("vibeplayer.idle");
            g.text(this.font, idle, rx, ty + 14, 0xFFFFFFFF, false);
            Component sub = MediaBridge.get().status() == MediaBridge.Status.UNSUPPORTED
                    ? Component.translatable("vibeplayer.unsupported")
                    : Component.translatable("vibeplayer.idle.sub");
            for (FormattedCharSequence line : this.font.split(sub, rw)) {
                ty += 11;
                g.text(this.font, line, rx, ty + 16, 0xFFA8A8B3, false);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MediaState st = MediaBridge.get().state();
        if (event.button() == 0 && st.active() && st.canSeek() && st.dur() > 0
                && event.x() >= barX && event.x() < barX + barW && event.y() >= barY - 4 && event.y() < barY + 8) {
            MediaBridge.get().seek(st.dur() * (event.x() - barX) / barW);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
