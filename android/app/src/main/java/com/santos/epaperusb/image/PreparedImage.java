package com.santos.epaperusb.image;

import com.santos.epaperusb.protocol.EpaperProtocol;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** One immutable source for both preview and wire data. */
public final class PreparedImage {
    private final int[] pixels;
    private final byte[] payload;
    private final boolean red;
    public PreparedImage(int[] pixels) {
        if (pixels.length != 800 * 480) throw new IllegalArgumentException("800 × 480 obrigatório");
        this.pixels = pixels.clone();
        for (int color : pixels) {
            if (color != 0xff000000 && color != 0xffffffff && color != 0xffffff00 && color != 0xffff0000)
                throw new IllegalArgumentException("Paleta inválida");
        }
        payload = EpaperProtocol.pack(this.pixels);
        red = EpaperProtocol.containsRed(this.pixels);
    }
    public int[] pixels() { return pixels.clone(); }
    public byte[] payload() { return payload.clone(); }
    public byte[] header() { return EpaperProtocol.handshake(red); }
    public String sha256() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
