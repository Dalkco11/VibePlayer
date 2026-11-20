package com.dalkco.vibeplayer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** The cover of the current track as a texture, plus its dominant colour for the accent. */
public final class CoverArt {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vibeplayer", "cover");
    public static final int SIZE = 128;

    private static volatile String pendingKey;
    private static volatile byte[] pendingBytes;

    private static String key = "";
    private static DynamicTexture texture;
    private static boolean loaded;
    private static int color = 0xFFB48CFF;

    private CoverArt() {
    }

    /** Called from the bridge thread; the texture is made on the render thread in {@link #tick}. */
    public static void offer(String trackKey, byte[] bytes) {
        pendingBytes = bytes;
        pendingKey = trackKey;
    }

    public static void tick(Minecraft mc) {
        String k = pendingKey;
        if (k == null) {
            return;
        }
        byte[] bytes = pendingBytes;
        pendingKey = null;
        pendingBytes = null;
        key = k;
        if (texture != null) {
            mc.getTextureManager().release(ID);
            texture = null;
        }
        loaded = false;
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            NativeImage img = NativeImage.read(toPng(bytes));
            color = dominant(img);
            texture = new DynamicTexture(() -> "vibeplayer cover", img);
            mc.getTextureManager().register(ID, texture);
            loaded = true;
        } catch (Exception e) {
            VibePlayerClient.LOGGER.debug("Could not load the cover", e);
        }
    }

    /** PNG passes through; JPEG and others (Linux art URLs) are converted with ImageIO. */
    private static byte[] toPng(byte[] bytes) throws Exception {
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return bytes;
        }
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) {
            throw new IllegalArgumentException("unknown image format");
        }
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage dst = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, SIZE, SIZE, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                (src.getWidth() + side) / 2, (src.getHeight() + side) / 2, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", out);
        return out.toByteArray();
    }

    /** Average colour pushed to a bright, saturated accent. */
    private static int dominant(NativeImage img) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                int c = img.getPixel(x, y);
                int pr = (c >> 16) & 0xFF, pg = (c >> 8) & 0xFF, pb = c & 0xFF;
                int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
                // colourful pixels count more than grey ones
                int w = 1 + (max - min) / 16;
                r += (long) pr * w;
                g += (long) pg * w;
                b += (long) pb * w;
                n += w;
            }
        }
        if (n == 0) {
            return 0xFFB48CFF;
        }
        float[] hsb = Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (b / n), null);
        float sat = hsb[1] < 0.12f ? 0.15f : Math.max(0.45f, hsb[1]);
        int rgb = Color.HSBtoRGB(hsb[0], Math.min(0.85f, sat), Math.max(0.85f, hsb[2]));
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** True when the loaded cover belongs to this track. */
    public static boolean has(MediaState s) {
        return loaded && s.active() && key.equals(s.key());
    }

    public static int color() {
        return color;
    }
}
