package com.santos.epaperusb.image;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class PaletteQuantizer {
    public static final int[] COLORS = {0xff000000, 0xffffffff, 0xffffff00, 0xffff0000};
    private PaletteQuantizer() {}
    public static int compositeWhite(int color) {
        int a = color >>> 24;
        int r = (((color >> 16) & 255) * a + 255 * (255 - a) + 127) / 255;
        int g = (((color >> 8) & 255) * a + 255 * (255 - a) + 127) / 255;
        int b = ((color & 255) * a + 255 * (255 - a) + 127) / 255;
        return 0xff000000 | r << 16 | g << 8 | b;
    }
    public static int[] convert(int[] source, int width, int height, boolean dither, BooleanSupplier cancelled) {
        if (width <= 0 || height <= 0 || source.length != width * height) throw new IllegalArgumentException("size");
        int[] out = new int[source.length];
        float[][] current = new float[3][width + 2], next = new float[3][width + 2];
        for (int y = 0; y < height; y++) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            for (int x = 0; x < width; x++) {
                int input = compositeWhite(source[y * width + x]);
                float r = clamp(((input >> 16) & 255) + current[0][x + 1]);
                float g = clamp(((input >> 8) & 255) + current[1][x + 1]);
                float b = clamp((input & 255) + current[2][x + 1]);
                int best = 0; float distance = Float.MAX_VALUE;
                for (int c = 0; c < COLORS.length; c++) {
                    float dr = r - ((COLORS[c] >> 16) & 255), dg = g - ((COLORS[c] >> 8) & 255), db = b - (COLORS[c] & 255);
                    float d = dr * dr + dg * dg + db * db;
                    if (d < distance) { distance = d; best = c; }
                }
                int color = COLORS[best]; out[y * width + x] = color;
                if (dither) {
                    float[] error = {r - ((color >> 16) & 255), g - ((color >> 8) & 255), b - (color & 255)};
                    for (int c = 0; c < 3; c++) {
                        current[c][x + 2] += error[c] * (7f / 16);
                        next[c][x] += error[c] * (3f / 16);
                        next[c][x + 1] += error[c] * (5f / 16);
                        next[c][x + 2] += error[c] * (1f / 16);
                    }
                }
            }
            float[][] old = current; current = next; next = old;
            for (float[] row : next) java.util.Arrays.fill(row, 0);
        }
        return out;
    }
    private static float clamp(float v) { return Math.max(0, Math.min(255, v)); }
}
