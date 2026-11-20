package com.dalkco.vibeplayer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The "now playing" card. Drawn by the HUD in game and on top of the chat (with controls and dragging). */
public final class PlayerHud {
    public static final int CARD_W = 184;
    private static final int GRAY = 0xFFA8A8B3;
    private static final int MARGIN = 4;

    public record Bounds(int x1, int y1, int x2, int y2) {
        public boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }

        Bounds scaled(float s) {
            return new Bounds(Math.round(x1 * s), Math.round(y1 * s), Math.round(x2 * s), Math.round(y2 * s));
        }
    }

    /** Where things ended up (card, progress bar, cover); bar / cover may be null. */
    public record Layout(Bounds card, Bounds bar, Bounds cover) {
    }

    private static Layout last;
    private static boolean dragging;
    private static boolean wasDown;
    private static double dragDx;
    private static double dragDy;

    private PlayerHud() {
    }

    /** Last drawn layout in GUI coordinates (null when the card is hidden). */
    public static Layout layout() {
        return last;
    }

    // ------------------------------------------------------------------ entry points

    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (Minecraft.getInstance().gui.screen() == null) {
            draw(g, false, -1, -1);
        }
    }

    public static void renderOverChat(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        draw(g, true, mouseX, mouseY);
    }

    private static void draw(GuiGraphicsExtractor g, boolean chat, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        VibeConfig.Data c = VibeConfig.get();
        MediaState st = MediaBridge.get().state();
        long now = System.currentTimeMillis();

        boolean visible = c.hud && (st.active() ? !(c.hideWhenPaused && !st.playing()) : !c.hideWhenIdle);
        if (chat) {
            if (!chatWasOpen) {
                hintUntil = now + 2500;
            }
            pollDrag(mc, mouseX, mouseY, g.guiWidth(), g.guiHeight());
            visible = true;
        } else {
            wasDown = false;
            stopDrag();
        }
        chatWasOpen = chat;
        if (!visible) {
            last = null;
            return;
        }
        float s = c.scale / 100f;
        int sw = Math.round(g.guiWidth() / s);
        int sh = Math.round(g.guiHeight() / s);
        int w = width(mc.font, st, c);
        int h = height(c);
        int x;
        int y;
        switch (c.position) {
            case TOP_LEFT -> { x = MARGIN; y = MARGIN; }
            case TOP_RIGHT -> { x = sw - w - MARGIN; y = MARGIN; }
            case BOTTOM_LEFT -> { x = MARGIN; y = sh - h - MARGIN; }
            case CUSTOM -> {
                x = Math.max(1, Math.min(sw - w - 1, Math.round(c.customX * sw - w / 2f)));
                y = Math.max(1, Math.min(sh - h - 1, Math.round(c.customY * sh - h / 2f)));
            }
            default -> { x = sw - w - MARGIN; y = sh - h - MARGIN; }
        }
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        Layout l = paint(g, mc.font, x, y, st, 1f, c, now);
        g.pose().popMatrix();
        last = new Layout(l.card().scaled(s), l.bar() == null ? null : l.bar().scaled(s),
                l.cover() == null ? null : l.cover().scaled(s));

        if (chat) {
            Bounds b = last.card();
            boolean overCard = b.contains(mouseX, mouseY)
                    && (last.bar() == null || !last.bar().contains(mouseX, mouseY))
                    && (last.cover() == null || !last.cover().contains(mouseX, mouseY));
            if (dragging || overCard || hintUntil > now) {
                g.outline(b.x1() - 1, b.y1() - 1, b.x2() - b.x1() + 2, b.y2() - b.y1() + 2, VibeConfig.accent());
                Component hint = Component.translatable("vibeplayer.chat.drag");
                int hw = mc.font.width(hint);
                int hx = Math.max(2, Math.min(g.guiWidth() - hw - 2, (b.x1() + b.x2()) / 2 - hw / 2));
                int hy = b.y1() > 14 ? b.y1() - 12 : b.y2() + 3;
                g.text(mc.font, hint, hx, hy, 0xFFDDDDDD, true);
            }
        }
    }

    // ------------------------------------------------------------------ dragging (polled while chat is open)

    /** Called on a click in chat; true if the click is ours (seek / open player / start dragging). */
    public static boolean click(double mouseX, double mouseY) {
        Layout l = last;
        if (l == null || !l.card().contains(mouseX, mouseY)) {
            return false;
        }
        MediaState st = MediaBridge.get().state();
        if (l.bar() != null && l.bar().contains(mouseX, mouseY)) {
            if (st.canSeek() && st.dur() > 0) {
                double f = (mouseX - l.bar().x1()) / Math.max(1.0, l.bar().x2() - l.bar().x1());
                MediaBridge.get().seek(st.dur() * Math.max(0, Math.min(1, f)));
            }
            return true;
        }
        if (l.cover() != null && l.cover().contains(mouseX, mouseY)) {
            Minecraft.getInstance().gui.setScreen(new PlayerScreen(null));
            return true;
        }
        startDrag(mouseX, mouseY);
        return true;
    }

    private static void startDrag(double mouseX, double mouseY) {
        Layout l = last;
        if (l == null) {
            return;
        }
        dragging = true;
        dragDx = mouseX - (l.card().x1() + l.card().x2()) / 2.0;
        dragDy = mouseY - (l.card().y1() + l.card().y2()) / 2.0;
    }

    /**
     * Moving: hold Ctrl and the card follows the mouse (no button needed), release Ctrl to drop it there.
     * Holding the left button on the card works too where the game reports it.
     */
    private static void pollDrag(Minecraft mc, double mouseX, double mouseY, int gw, int gh) {
        boolean ctrl = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
        boolean mouse = mc.mouseHandler.isLeftPressed();
        // grab only when the cursor is on the card, so Ctrl+V / Ctrl+A in chat do not move it
        Bounds b = last == null ? null : last.card();
        if (!dragging && ctrl && !wasCtrl && b != null
                && mouseX >= b.x1() - 6 && mouseX < b.x2() + 6 && mouseY >= b.y1() - 6 && mouseY < b.y2() + 6) {
            startDrag(Math.max(b.x1(), Math.min(b.x2() - 1, mouseX)), Math.max(b.y1(), Math.min(b.y2() - 1, mouseY)));
            ctrlDrag = true;
        }
        wasCtrl = ctrl;
        boolean down = ctrlDrag ? ctrl : mouse;
        if (dragging) {
            if (down) {
                VibeConfig.Data c = VibeConfig.get();
                c.position = VibeConfig.Position.CUSTOM;
                c.customX = (float) Math.max(0, Math.min(1, (mouseX - dragDx) / gw));
                c.customY = (float) Math.max(0, Math.min(1, (mouseY - dragDy) / gh));
            } else {
                dragging = false;
                ctrlDrag = false;
                VibeConfig.save();
            }
        }
        wasDown = mouse;
    }

    private static boolean ctrlDrag;
    private static boolean wasCtrl;
    /** show the "hold Ctrl" hint for a moment each time chat opens */
    private static long hintUntil;
    private static boolean chatWasOpen;

    public static void stopDrag() {
        if (dragging) {
            dragging = false;
            ctrlDrag = false;
            VibeConfig.save();
        }
    }

    // ------------------------------------------------------------------ layout & painting (also for previews)

    public static int height(VibeConfig.Data c) {
        if (c.style == VibeConfig.Style.COMPACT) {
            return 16;
        }
        return c.showProgress ? 50 : 38;
    }

    public static int width(Font font, MediaState st, VibeConfig.Data c) {
        if (c.style == VibeConfig.Style.CARD) {
            return CARD_W;
        }
        String[] t = texts(st, c);
        int text = font.width(t[0]) + (t[1].isEmpty() ? 0 : font.width(" — " + t[1]));
        return Math.min(260, 6 + 10 + Math.min(text, 220) + (c.equalizer ? 14 : 0) + 6);
    }

    /** Title and subtitle shown for this state. */
    private static String[] texts(MediaState st, VibeConfig.Data c) {
        if (!st.active()) {
            String sub = MediaBridge.get().status() == MediaBridge.Status.UNSUPPORTED
                    ? Component.translatable("vibeplayer.unsupported").getString()
                    : Component.translatable("vibeplayer.idle.sub").getString();
            return new String[]{Component.translatable("vibeplayer.idle").getString(), sub};
        }
        String app = Draw.appName(st.app());
        String title = st.title().isEmpty() ? app : st.title();
        String sub = st.artist();
        if (c.showApp && !app.isEmpty()) {
            sub = sub.isEmpty() ? app : sub + " · " + app;
        }
        return new String[]{title, sub};
    }

    public static Layout paint(GuiGraphicsExtractor g, Font font, int x, int y, MediaState st, float a,
                               VibeConfig.Data c, long now) {
        if (c.style == VibeConfig.Style.COMPACT) {
            return paintCompact(g, font, x, y, st, a, c, now);
        }
        int w = CARD_W;
        int h = height(c);
        int acc = VibeConfig.accent();
        int bg = Math.round(235 * (c.opacity / 100f));
        Draw.round(g, x, y, w, h, 6, Draw.alpha((bg << 24) | 0x0E0E14, a));
        Draw.round(g, x + 7, y, w - 14, 1, 0, Draw.alpha((acc & 0xFFFFFF) | 0xA0000000, a));

        Bounds coverB = null;
        int tx = x + 7;
        if (c.showCover) {
            int cs = h - 10;
            int cx = x + 5;
            int cy = y + 5;
            if (CoverArt.has(st)) {
                Draw.cover(g, cx, cy, cs, a);
            } else {
                Draw.noCover(g, font, cx, cy, cs, acc, a);
            }
            if (st.active() && !st.playing()) {
                Draw.round(g, cx, cy, cs, cs, 0, Draw.alpha(0x8C000000, a));
                Draw.pause(g, cx, cy, cs, cs, Draw.alpha(0xFFFFFFFF, a));
            }
            coverB = new Bounds(cx, cy, cx + cs, cy + cs);
            tx = cx + cs + 6;
        }
        int right = x + w - 6;
        int eqW = c.equalizer ? 13 : 0;
        String[] t = texts(st, c);
        marquee(g, font, t[0], tx, y + 6, right - eqW - tx, Draw.alpha(0xFFFFFFFF, a), c.marquee, now);
        g.text(font, Draw.trim(font, t[1], right - tx), tx, y + 17, Draw.alpha(GRAY, a), false);
        if (c.equalizer) {
            Draw.equalizer(g, right - 11, y + 14, 4, 8, st.playing(), Draw.alpha(acc, a));
        }

        Bounds barB = null;
        if (c.showProgress && st.active() && st.dur() > 0) {
            int by = y + h - 16;
            int bw = right - tx;
            double p = st.position(now);
            int fill = (int) Math.round(bw * Math.max(0, Math.min(1, p / st.dur())));
            Draw.round(g, tx, by, bw, 3, 1, Draw.alpha(0x40FFFFFF, a));
            Draw.round(g, tx, by, Math.max(1, fill), 3, 1, Draw.alpha(acc, a));
            Draw.round(g, tx + fill - 2, by - 1, 5, 5, 2, Draw.alpha(0xFFFFFFFF, a));
            String l = Draw.time(p);
            String r = "-" + Draw.time(st.dur() - p);
            g.text(font, l, tx, by + 6, Draw.alpha(GRAY, a), false);
            g.text(font, r, right - font.width(r), by + 6, Draw.alpha(GRAY, a), false);
            barB = new Bounds(tx, by - 3, tx + bw, by + 6);
        } else if (c.showProgress && st.active()) {
            g.text(font, Component.translatable(st.playing() ? "vibeplayer.state.playing" : "vibeplayer.state.paused"),
                    tx, y + h - 13, Draw.alpha(GRAY, a), false);
        }
        return new Layout(new Bounds(x, y, x + w, y + h), barB, coverB);
    }

    private static Layout paintCompact(GuiGraphicsExtractor g, Font font, int x, int y, MediaState st, float a,
                                       VibeConfig.Data c, long now) {
        int w = width(font, st, c);
        int h = 16;
        int acc = VibeConfig.accent();
        int bg = Math.round(235 * (c.opacity / 100f));
        Draw.round(g, x, y, w, h, 7, Draw.alpha((bg << 24) | 0x0E0E14, a));
        if (st.active() && !st.playing()) {
            Draw.pause(g, x + 3, y + 2, 12, 12, Draw.alpha(acc, a));
        } else {
            g.text(font, "♪", x + 6, y + 4, Draw.alpha(acc, a), false);
        }
        String[] t = texts(st, c);
        int tx = x + 16;
        int right = x + w - 6 - (c.equalizer ? 14 : 0);
        String line = t[1].isEmpty() ? t[0] : t[0] + " — " + t[1];
        marquee(g, font, line, tx, y + 4, right - tx, Draw.alpha(0xFFFFFFFF, a), c.marquee, now);
        if (c.equalizer) {
            Draw.equalizer(g, x + w - 17, y + 12, 4, 8, st.playing(), Draw.alpha(acc, a));
        }
        if (c.showProgress && st.active() && st.dur() > 0) {
            double f = Math.max(0, Math.min(1, st.position(now) / st.dur()));
            g.fill(x + 6, y + h - 2, x + 6 + (int) Math.round((w - 12) * f), y + h - 1, Draw.alpha(acc, a));
        }
        return new Layout(new Bounds(x, y, x + w, y + h), null, null);
    }

    /** Text that scrolls when it does not fit (pauses at the start of each loop). */
    static void marquee(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, int color, boolean enabled,
                        long now) {
        int sw = font.width(s);
        if (sw <= w || !enabled || w <= 10) {
            g.text(font, Draw.trim(font, s, w), x, y, color, false);
            return;
        }
        int cycle = sw + 28;
        long hold = 2200;
        long period = cycle * 32L + hold;
        long t = now % period;
        int off = t < hold ? 0 : (int) ((t - hold) / 32);
        g.enableScissor(x, y - 1, x + w, y + 10);
        g.text(font, s, x - off, y, color, false);
        g.text(font, s, x - off + cycle, y, color, false);
        g.disableScissor();
    }
}
