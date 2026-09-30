import java.util.Arrays;
import java.util.List;
public class TestEpaperProtocol {
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        byte[] expected = {(byte)0xAA,0x55,(byte)0xE1,(byte)0xBB,(byte)0x80,4,
            (byte)0xC4,0,(byte)0xE3,(byte)0xFF,13,10};
        require(Arrays.equals(expected, EpaperProtocol.handshake(false)), "handshake");
        require((EpaperProtocol.handshake(true)[8] & 255) == 0xE4, "red checksum");
        int[] rgb = new int[800*480];
        for(int i=0; i<rgb.length; i+=4) {
            rgb[i]=0; rgb[i+1]=0xffffff; rgb[i+2]=0xffff00; rgb[i+3]=0xff0000;
        }
        byte[] packed = EpaperProtocol.pack(rgb);
        require(packed.length == 96000, "payload size");
        for(byte b: packed) require(b==0x1b, "pixel order or color mapping");
        require(EpaperProtocol.containsRed(rgb), "red flag");
        List<byte[]> packets=EpaperProtocol.packets(packed);
        require(packets.size()==24 && packets.get(23).length==1794, "packet count/tail");
        int offset=0, total=0;
        for(byte[] p: packets) {
            require(p[p.length-2]==13 && p[p.length-1]==10, "terminator");
            for(int i=0;i<p.length-2;i++) require(p[i]==packed[offset++], "payload intact");
            total+=p.length;
        }
        require(offset==96000 && total==96048, "wire data size");
        byte[] ack={(byte)0xA0,0x50,(byte)0xF1,0,0,0,0,0,(byte)0xE1,(byte)0xFF};
        require(EpaperProtocol.isHandshakeAck(ack), "valid synthetic ACK");
        ack[8]=0;
        require(!EpaperProtocol.isHandshakeAck(ack), "bad checksum rejected");
        require(!EpaperProtocol.isHandshakeAck(new byte[3]), "partial ACK rejected");
        System.out.println("PASS: handshake, RGB packing, red flag, 24 blocks, payload preservation, ACK validation. No device accessed.");
    }
}
