package com.dalkco.vibeplayer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

/** Drawing helpers: rounded shapes, vector icons, the cover, text helpers. */
public final class Draw {
    private Draw() {
    }

    public static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** Filled rectangle with rounded corners. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** The cover texture (128×128) drawn at size×size. */
    public static void cover(GuiGraphicsExtractor g, int x, int y, int size, float a) {
        int color = alpha(0xFFFFFFFF, a);
        g.blit(RenderPipelines.GUI_TEXTURED, CoverArt.ID, x, y, 0f, 0f, size, size, CoverArt.SIZE, CoverArt.SIZE,
                CoverArt.SIZE, CoverArt.SIZE, color);
    }

    /** A music-note placeholder in the accent colour. */
    public static void noCover(GuiGraphicsExtractor g, Font font, int x, int y, int size, int accent, float a) {
        round(g, x, y, size, size, Math.max(2, size / 8), alpha((accent & 0xFFFFFF) | 0x55000000, a));
        round(g, x + 1, y + 1, size - 2, size - 2, Math.max(2, size / 8), alpha(0xCC101018, a));
        g.pose().pushMatrix();
        float s = Math.max(1f, size / 14f);
        g.pose().translate(x + size / 2f, y + size / 2f);
        g.pose().scale(s, s);
        g.text(font, "♪", -font.width("♪") / 2, -4, alpha(accent, a), false);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------- icons (vector, any size)

    /** Right-pointing triangle centred in the box. */
    public static void play(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int a = Math.max(2, Math.min(w, h) * 3 / 10);
        int cx = x + w / 2 - a / 3;
        int cy = y + h / 2;
        for (int c = 0; c < a; c++) {
            int half = Math.round((a - c) * 0.9f);
            g.fill(cx - a / 2 + c, cy - half, cx - a / 2 + c + 1, cy + half, color);
        }
    }

    public static void pause(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int a = Math.max(2, Math.min(w, h) * 3 / 10);
        int bw = Math.max(1, a * 2 / 5);
        int cx = x + w / 2;
        int cy = y + h / 2;
        g.fill(cx - a / 2 - bw / 2 - 1, cy - a, cx - a / 2 + bw / 2, cy + a, color);
        g.fill(cx + a / 2 - bw / 2 + 1, cy - a, cx + a / 2 + bw / 2 + 2, cy + a, color);
    }

    public static void next(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int a = Math.max(2, Math.min(w, h) / 4);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int left = cx - a / 2 - 1;
        for (int c = 0; c < a; c++) {
            int half = Math.round((a - c) * 0.9f);
            g.fill(left + c, cy - half, left + c + 1, cy + half, color);
        }
        g.fill(left + a, cy - a, left + a + Math.max(1, a / 3), cy + a, color);
    }

    public static void prev(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int a = Math.max(2, Math.min(w, h) / 4);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int right = cx + a / 2 + 1;
        for (int c = 0; c < a; c++) {
            int half = Math.round((a - c) * 0.9f);
            g.fill(right - c - 1, cy - half, right - c, cy + half, color);
        }
        g.fill(right - a - Math.max(1, a / 3), cy - a, right - a, cy + a, color);
    }

    /** A glyph from the font centred in the box (⚙, ☰…). */
    public static void glyph(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, int h, int color) {
        g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, color, false);
    }

    /** Animated equalizer bars; flat when paused. */
    public static void equalizer(GuiGraphicsExtractor g, int x, int bottom, int bars, int maxH, boolean playing,
                                 int color) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < bars; i++) {
            int h = 1;
            if (playing) {
                double v = Math.abs(Math.sin(t / 170.0 + i * 1.7)) * (0.55 + 0.45 * Math.sin(t / 470.0 + i * 0.9));
                h = Math.max(1, (int) Math.round(1 + (maxH - 1) * Math.abs(v)));
            }
            g.fill(x + i * 3, bottom - h, x + i * 3 + 2, bottom, color);
        }
    }

    // ------------------------------------------------------------- text

    public static String time(double seconds) {
        int s = (int) Math.max(0, Math.round(seconds));
        int h = s / 3600;
        int m = (s / 60) % 60;
        int sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    public static String trim(Font font, String s, int max) {
        if (font.width(s) <= max) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, max - font.width("…"))) + "…";
    }

    private static final Map<String, String> APPS = Map.ofEntries(
            Map.entry("spotify", "Spotify"), Map.entry("chrome", "Chrome"), Map.entry("msedge", "Edge"),
            Map.entry("firefox", "Firefox"), Map.entry("308046b0af4a39cb", "Firefox"), Map.entry("opera", "Opera"),
            Map.entry("brave", "Brave"), Map.entry("vivaldi", "Vivaldi"), Map.entry("yandex", "Yandex"),
            Map.entry("telegram", "Telegram"), Map.entry("discord", "Discord"), Map.entry("vlc", "VLC"),
            Map.entry("aimp", "AIMP"), Map.entry("foobar2000", "foobar2000"), Map.entry("zunemusic", "Media Player"),
            Map.entry("applemusic", "Apple Music"), Map.entry("itunes", "iTunes"), Map.entry("deezer", "Deezer"),
            Map.entry("soundcloud", "SoundCloud"), Map.entry("tidal", "TIDAL"), Map.entry("winamp", "Winamp"),
            Map.entry("musicbee", "MusicBee"), Map.entry("vk", "VK"));

    /** "Spotify.exe" → "Spotify", "Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic" → "Media Player". */
    public static String appName(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String low = id.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : APPS.entrySet()) {
            if (low.contains(e.getKey())) {
                return e.getValue();
            }
        }
        String s = id;
        int bang = s.indexOf('!');
        if (bang > 0) {
            s = s.substring(bang + 1);
        }
        if (s.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            s = s.substring(0, s.length() - 4);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot < s.length() - 1) {
            s = s.substring(dot + 1);
        }
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static Component app(String id) {
        return Component.literal(appName(id));
    }
}
