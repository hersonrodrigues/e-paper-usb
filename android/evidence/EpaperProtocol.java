import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Protocol reconstructed statically from the supplied ImageToUSB v4.0.
 * Scope: 800x480, four colors. NOT yet verified with a live device.
 * No USB operations are performed by this class.
 */
public final class EpaperProtocol {
    public static final int WIDTH = 800, HEIGHT = 480;
    public static final int BAUD_RATE = 115200, BLOCK_SIZE = 4096;
    public static final int VENDOR_ID = 0x1A86, PRODUCT_ID = 0x7523;
    private EpaperProtocol() {}

    // Matches GetPictureData_4Color, including its coarse RGB thresholds.
    // For photographs, quantize/dither to the display palette BEFORE this step.
    public static int pixelCode(int rgb) {
        int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
        if (r <= 50 && g <= 50 && b <= 50) return 0;
        if (r >= 200 && g >= 200 && b >= 200) return 1;
        return ((r + g + b) / 3 <= 127) ? 3 : 2;
    }

    /** Opaque RGB pixels, row-major, exactly 800x480. Alpha is ignored. */
    public static byte[] pack(int[] rgb) {
        if (rgb == null || rgb.length != WIDTH * HEIGHT)
            throw new IllegalArgumentException("Expected 800x480 pixels");
        byte[] out = new byte[WIDTH * HEIGHT / 4];
        for (int i = 0; i < rgb.length; i += 4) {
            out[i / 4] = (byte) ((pixelCode(rgb[i]) << 6)
                | (pixelCode(rgb[i+1]) << 4)
                | (pixelCode(rgb[i+2]) << 2) | pixelCode(rgb[i+3]));
        }
        return out;
    }

    public static boolean containsRed(int[] rgb) {
        if (rgb == null || rgb.length != WIDTH * HEIGHT)
            throw new IllegalArgumentException("Expected 800x480 pixels");
        for (int pixel : rgb) {
            if (((pixel >>> 16) & 255) > 100 && ((pixel >>> 8) & 255) <= 100
                && (pixel & 255) <= 100) return true;
        }
        return false;
    }

    /** The length field is the ONE-bit plane size (48000), even for 4 colors. */
    public static byte[] handshake(boolean redPresent) {
        byte[] frame = {(byte)0xAA, 0x55, (byte)0xE1, (byte)0xBB, (byte)0x80,
            4, (byte)0xC4, (byte)(redPresent ? 1 : 0), 0, (byte)0xFF, 13, 10};
        int sum = 0;
        for (int i = 0; i < 8; i++) sum += frame[i] & 255;
        frame[8] = (byte)sum;
        return frame;
    }

    /** Reassemble a full 10-byte frame before calling: serial reads may split it. */
    public static boolean isHandshakeAck(byte[] frame) {
        if (frame == null || frame.length != 10 || (frame[0] & 255) != 0xA0
            || (frame[1] & 255) != 0x50 || (frame[2] & 255) != 0xF1
            || (frame[9] & 255) != 0xFF) return false;
        int sum = 0;
        for (int i = 0; i < 8; i++) sum += frame[i] & 255;
        return (sum & 255) == (frame[8] & 255);
    }

    /** Send only AFTER a valid handshake response. Each packet includes CR LF.
     * Match the original 100 ms pause after each full block; handle write errors.
     * These are application blocks, not physical USB packet boundaries.
     */
    public static List<byte[]> packets(byte[] pixels) {
        if (pixels == null || pixels.length != 96000)
            throw new IllegalArgumentException("Expected 96000 bytes");
        List<byte[]> out = new ArrayList<>();
        for (int offset = 0; offset < pixels.length; offset += BLOCK_SIZE) {
            int count = Math.min(BLOCK_SIZE, pixels.length - offset);
            byte[] packet = Arrays.copyOfRange(pixels, offset, offset + count + 2);
            packet[count] = 13;
            packet[count + 1] = 10;
            out.add(packet);
        }
        return out;
    }
}
