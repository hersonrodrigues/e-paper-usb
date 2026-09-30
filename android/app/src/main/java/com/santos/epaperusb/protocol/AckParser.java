package com.santos.epaperusb.protocol;

import java.util.Arrays;

/** Bounded sliding window; keeps synchronization across fragmented and combined reads. */
public final class AckParser {
    private final byte[] window = new byte[10];
    private int count;
    public byte[] accept(byte[] data, int length) {
        if (length < 0 || length > data.length) throw new IllegalArgumentException("length");
        byte[] accepted = null;
        for (int i = 0; i < length; i++) {
            if (count == 10) {
                System.arraycopy(window, 1, window, 0, 9);
                count = 9;
            }
            window[count++] = data[i];
            if (count == 10 && EpaperProtocol.isHandshakeAck(window) && accepted == null)
                accepted = Arrays.copyOf(window, 10);
        }
        return accepted;
    }
}
