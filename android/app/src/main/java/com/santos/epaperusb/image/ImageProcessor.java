package com.santos.epaperusb.image;

import android.content.ContentResolver;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class ImageProcessor {
    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    public static File importPhoto(ContentResolver resolver, Uri uri, File cache, BooleanSupplier cancelled) throws IOException {
        File file = File.createTempFile("photo-", ".image", cache);
        boolean complete = false;
        try (InputStream in = resolver.openInputStream(uri); OutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new IOException("Could not open photo");
            byte[] buffer = new byte[32768]; long total = 0; int n;
            while ((n = in.read(buffer)) != -1) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                total += n;
                if (total > MAX_FILE_BYTES) throw new IOException("Photo exceeds 32 MB");
                out.write(buffer, 0, n);
            }
            complete = true;
            return file;
        } finally { if (!complete) file.delete(); }
    }
    public static int sampleSize(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IOException("Unknown image format");
        int sample = 1;
        while ((long)Math.ceil(width / (double)sample) * (long)Math.ceil(height / (double)sample) > 3_000_000
                || Math.ceil(Math.max(width, height) / (double)sample) > 4096) {
            sample *= 2;
        }
        return sample;
    }
    public static PreparedImage prepare(File file, boolean crop, int turns, boolean dither, BooleanSupplier cancelled) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        if (cancelled.getAsBoolean()) throw new CancellationException();
        int orientation;
        try { orientation = new ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); }
        catch (IOException e) { orientation = ExifInterface.ORIENTATION_NORMAL; }
        Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
        if (decoded == null) throw new IOException("Could not decode image");
        Bitmap oriented = decoded, rotated = decoded, fitted = null;
        try {
            if (decoded.getAllocationByteCount() > 20_000_000) throw new IOException("Image exceeds memory limit");
            oriented = orient(decoded, orientation); rotated = oriented;
            if (turns % 4 != 0) {
                Matrix rotation = new Matrix(); rotation.postRotate((turns % 4) * 90);
                rotated = Bitmap.createBitmap(oriented, 0, 0, oriented.getWidth(), oriented.getHeight(), rotation, true);
            }
            fitted = fit(rotated, crop);
            int[] pixels = new int[800 * 480]; fitted.getPixels(pixels, 0, 800, 0, 0, 800, 480);
            return new PreparedImage(PaletteQuantizer.convert(pixels, 800, 480, dither, cancelled));
        } finally {
            if (fitted != null) fitted.recycle();
            if (rotated != oriented && rotated != decoded) rotated.recycle();
            if (oriented != decoded) oriented.recycle();
            decoded.recycle();
        }
    }
    public static Bitmap orient(Bitmap input, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2: matrix.setScale(-1, 1); break;
            case 3: matrix.setRotate(180); break;
            case 4: matrix.setRotate(180); matrix.postScale(-1, 1); break;
            case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case 6: matrix.setRotate(90); break;
            case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case 8: matrix.setRotate(-90); break;
            default: return input;
        }
        return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), matrix, true);
    }
    public static Bitmap fit(Bitmap input, boolean crop) {
        Bitmap out = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out); canvas.drawColor(Color.WHITE);
        float sx = 800f / input.getWidth(), sy = 480f / input.getHeight();
        float scale = crop ? Math.max(sx, sy) : Math.min(sx, sy);
        float w = input.getWidth() * scale, h = input.getHeight() * scale;
        canvas.drawBitmap(input, null, new RectF((800 - w) / 2, (480 - h) / 2, (800 + w) / 2, (480 + h) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }
    public static PreparedImage testPattern() {
        return testPattern(new String[]{"Black", "White", "Yellow", "Red", "TOP / 1", "BOTTOM / 2"});
    }
    public static PreparedImage testPattern(String[] labels) {
        if (labels.length != 6) throw new IllegalArgumentException("Six localized chart labels required");
        Bitmap bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint();
        String[] codes = {"00", "01", "10", "11"};
        for (int i = 0; i < 4; i++) {
            paint.setColor(PaletteQuantizer.COLORS[i]); canvas.drawRect(i * 200, 0, i * 200 + 200, 480, paint);
            paint.setColor(i == 0 ? Color.WHITE : Color.BLACK); paint.setTextSize(23); paint.setTypeface(Typeface.DEFAULT_BOLD);
            drawLabel(canvas, paint, labels[i] + " " + codes[i], i * 200 + 12, 240, 176, 23);
        }
        paint.setColor(Color.WHITE); canvas.drawRect(8, 8, 155, 52, paint);
        paint.setColor(Color.BLACK); paint.setTextSize(24); drawLabel(canvas, paint, labels[4], 18, 39, 127, 24);
        paint.setColor(Color.BLACK); canvas.drawRect(637, 424, 792, 472, paint);
        paint.setColor(Color.WHITE); drawLabel(canvas, paint, labels[5], 649, 457, 133, 24);
        int[] pixels = new int[800 * 480]; bitmap.getPixels(pixels, 0, 800, 0, 0, 800, 480); bitmap.recycle();
        return new PreparedImage(PaletteQuantizer.convert(pixels, 800, 480, false, () -> false));
    }
    private static void drawLabel(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, int size) {
        paint.setTextSize(size);
        float width = paint.measureText(text);
        if (width > maxWidth) paint.setTextSize(size * maxWidth / width);
        canvas.drawText(text, x, y, paint);
    }

}
