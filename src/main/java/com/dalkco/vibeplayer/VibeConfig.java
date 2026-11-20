package com.dalkco.vibeplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, saved to config/vibeplayer.json. */
public final class VibeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vibeplayer.json");

    /** -1 = colour of the cover; then purple, blue, green, orange, pink, white. */
    public static final int[] ACCENTS = {0xFFB48CFF, 0xFF62B0FF, 0xFF62E08F, 0xFFFFAD52, 0xFFFF78BE, 0xFFEDEDED};

    public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM }

    public enum Style { CARD, COMPACT }

    public static final class Data {
        public boolean hud = true;
        public Style style = Style.CARD;
        public Position position = Position.BOTTOM_RIGHT;
        public float customX = 0.85f;
        public float customY = 0.8f;
        public int scale = 100;
        public int opacity = 85;
        public int accent = -1;
        public boolean showCover = true;
        public boolean showProgress = true;
        public boolean showApp = true;
        public boolean equalizer = true;
        public boolean marquee = true;
        public boolean hideWhenPaused = false;
        public boolean hideWhenIdle = true;
        /** app to follow ("" = the one Windows considers current) */
        public String pinned = "";
    }

    private static Data data = new Data();

    private VibeConfig() {
    }

    public static Data get() {
        return data;
    }

    public static int accent() {
        if (data.accent < 0) {
            return CoverArt.has(MediaBridge.get().state()) ? CoverArt.color() : ACCENTS[0];
        }
        return ACCENTS[Mth.clamp(data.accent, 0, ACCENTS.length - 1)];
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    data = d;
                }
            }
        } catch (Exception e) {
            VibePlayerClient.LOGGER.warn("Failed to read {}", FILE, e);
        }
        normalize();
    }

    private static void normalize() {
        Data d = data;
        if (d.style == null) {
            d.style = Style.CARD;
        }
        if (d.position == null) {
            d.position = Position.BOTTOM_RIGHT;
        }
        if (d.pinned == null) {
            d.pinned = "";
        }
        d.customX = Mth.clamp(d.customX, 0f, 1f);
        d.customY = Mth.clamp(d.customY, 0f, 1f);
        d.scale = Mth.clamp(d.scale, 50, 200);
        d.opacity = Mth.clamp(d.opacity, 0, 100);
        d.accent = Mth.clamp(d.accent, -1, ACCENTS.length - 1);
    }

    public static void save() {
        normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            VibePlayerClient.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }

    public static void reset() {
        Data fresh = new Data();
        fresh.pinned = data.pinned;
        data = fresh;
        save();
    }
}
