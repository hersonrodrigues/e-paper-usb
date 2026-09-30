package com.santos.epaperusb;

import com.santos.epaperusb.protocol.*;
import com.santos.epaperusb.image.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ProtocolTest {
    static byte[] ack() { return new byte[]{(byte)0xa0,0x50,(byte)0xf1,0,0,0,0,0,(byte)0xe1,(byte)0xff}; } // synthetic fixture, never production
    static PreparedImage chart() {
        int[] pixels = new int[800 * 480];
        for (int i = 0; i < pixels.length; i++) pixels[i] = PaletteQuantizer.COLORS[i % 4];
        return new PreparedImage(pixels);
    }
    @Test public void originalDossierVectorsPreserved() { TestEpaperProtocol.main(new String[0]); }
    @Test public void parserHandlesEverySplitAndCoalescedNoise() {
        for (int split = 1; split < 10; split++) {
            AckParser parser = new AckParser();
            assertNull(parser.accept(new byte[]{12, (byte)0xa0}, 2));
            assertNull(parser.accept(Arrays.copyOfRange(ack(), 0, split), split));
            byte[] tail = Arrays.copyOfRange(ack(), split, 13);
            assertArrayEquals(ack(), parser.accept(tail, tail.length));
        }
    }
    @Test public void parserResynchronizesAfterCorruptionAndWrongCommand() {
        AckParser parser = new AckParser();
        byte[] bad = ack(); bad[8]++;
        assertNull(parser.accept(bad, 10));
        byte[] f2 = ack(); f2[2]++; f2[8]++;
        assertNull(parser.accept(f2, 10));
        byte[] noise = new byte[60000]; Arrays.fill(noise, (byte)0xa0);
        assertNull(parser.accept(noise, noise.length));
        assertArrayEquals(ack(), parser.accept(ack(), 10));
    }
    @Test public void immutablePreviewExactlyReconstructsPayload() {
        PreparedImage prepared = chart();
        byte[] before = prepared.payload(); int[] preview = prepared.pixels();
        for (int i = 0; i < preview.length; i++) {
            int code = (before[i / 4] >> (6 - 2 * (i % 4))) & 3;
            assertEquals(PaletteQuantizer.COLORS[code], preview[i]);
        }
        Arrays.fill(preview, 0); byte[] copy = prepared.payload(); Arrays.fill(copy, (byte)0);
        assertArrayEquals(before, prepared.payload()); assertEquals(0xff000000, prepared.pixels()[0]);
        int[] constructorInput = chart().pixels(); PreparedImage immutable = new PreparedImage(constructorInput); Arrays.fill(constructorInput, 0);
        assertArrayEquals(before, immutable.payload());
    }
    @Test public void quantizationIsOpaqueAndUsesOnlyFourCodes() {
        int[] input = {0x00ff0000, 0x80000000, 0xffff0000, 0xffffff00, 0xff000000, 0xffffffff, 0xff247abc, 0xff44ab21};
        for (boolean dither : new boolean[]{false, true}) {
            int[] out = PaletteQuantizer.convert(input, 4, 2, dither, () -> false);
            assertEquals(0xffffffff, out[0]);
            for (int color : out) assertTrue(Arrays.stream(PaletteQuantizer.COLORS).anyMatch(c -> c == color));
        }
        assertEquals(0xff7f7f7f, PaletteQuantizer.compositeWhite(0x80000000));
    }
    @Test(expected = IllegalArgumentException.class) public void invalidPaletteRejected() { new PreparedImage(new int[800 * 480]); }
    @Test(expected = IllegalArgumentException.class) public void incorrectPayloadSizeRejected() { EpaperProtocol.packets(new byte[48000]); }
}
