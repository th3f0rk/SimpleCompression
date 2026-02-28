import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class SimpleCompressionTest {

    //--- frame structure ---

    @Test
    public void emptySequenceReturnsOriginalData () {
        byte[] input = {0x01, 0x02, 0x03};
        assertArrayEquals(input, SimpleCompression.encode(input, new String[0]));
    }

    @Test
    public void frameHasMagicBytes () {
        byte[] encoded = SimpleCompression.encode("abcdefghij".getBytes(), "RLE");
        assertEquals(0x53, encoded[0] & 0xFF); //'S'
        assertEquals(0x43, encoded[1] & 0xFF); //'C'
    }

    @Test
    public void frameHeaderCountReflectsSequenceLength () {
        byte[] encoded = SimpleCompression.encode("hello world".getBytes(), "RLE", "HUFFMAN");
        assertEquals(2, encoded[2] & 0xFF);
    }

    @Test
    public void frameAlgorithmByteForRle () {
        byte[] encoded = SimpleCompression.encode("aaaaabbbbb".getBytes(), "RLE");
        assertEquals(1,    encoded[2] & 0xFF); //headerCount=1
        assertEquals(0x01, encoded[3] & 0xFF); //HDR_RLE
    }

    @Test
    public void frameAlgorithmByteForLz77 () {
        byte[] encoded = SimpleCompression.encode("aaaaabbbbb".getBytes(), "LZ77");
        assertEquals(1,    encoded[2] & 0xFF);
        assertEquals(0x02, encoded[3] & 0xFF); //HDR_LZ77
    }

    @Test
    public void frameAlgorithmByteForHuffman () {
        byte[] encoded = SimpleCompression.encode("aaaaabbbbb".getBytes(), "HUFFMAN");
        assertEquals(1,    encoded[2] & 0xFF);
        assertEquals(0x03, encoded[3] & 0xFF); //HDR_HUFFMAN
    }

    @Test
    public void frameAlgorithmByteForDelta () {
        byte[] encoded = SimpleCompression.encode(new byte[]{10, 20, 30, 40, 50}, "DELTA");
        assertEquals(1,    encoded[2] & 0xFF);
        assertEquals(0x04, encoded[3] & 0xFF); //HDR_DELTA
    }

    @Test
    public void frameSequenceOrderPreserved () {
        byte[] encoded = SimpleCompression.encode("abcdefghij abcdefghij".getBytes(), "DELTA", "RLE", "HUFFMAN");
        assertEquals(3,    encoded[2] & 0xFF); //headerCount=3
        assertEquals(0x04, encoded[3] & 0xFF); //DELTA
        assertEquals(0x01, encoded[4] & 0xFF); //RLE
        assertEquals(0x03, encoded[5] & 0xFF); //HUFFMAN
    }

    @Test
    public void framePayloadStartsAfterHeaders () {
        //confirm the framed payload is exactly 3+1 bytes before the encoded content
        byte[] raw = {0x01, 0x02, 0x03};
        byte[] encoded = SimpleCompression.encode(raw, "DELTA");
        //frame = [S][C][1][0x04][delta-encoded bytes]
        //delta of {1,2,3} = {1,1,1}
        assertEquals(0x01, encoded[4] & 0xFF); //first delta byte
        assertEquals(0x01, encoded[5] & 0xFF);
        assertEquals(0x01, encoded[6] & 0xFF);
    }

    //--- encode errors ---

    @Test
    public void unknownAlgorithmThrows () {
        assertThrows(IllegalArgumentException.class, () ->
            SimpleCompression.encode(new byte[]{1, 2, 3}, "GZIP"));
    }

    //--- decode passthrough ---

    @Test
    public void decodeEmptyReturnsAsIs () {
        byte[] input = {};
        assertArrayEquals(input, SimpleCompression.decode(input));
    }

    @Test
    public void decodeTwoByteReturnsAsIs () {
        byte[] input = {0x53, 0x43};
        assertArrayEquals(input, SimpleCompression.decode(input));
    }

    @Test
    public void decodeNoMagicReturnsAsIs () {
        byte[] input = {0x01, 0x02, 0x03, 0x04};
        assertArrayEquals(input, SimpleCompression.decode(input));
    }

    @Test
    public void decodeFirstMagicWrongReturnsAsIs () {
        byte[] input = {0x00, 0x43, 0x01, 0x01, 0x41};
        assertArrayEquals(input, SimpleCompression.decode(input));
    }

    @Test
    public void decodeSecondMagicWrongReturnsAsIs () {
        byte[] input = {0x53, 0x00, 0x01, 0x01, 0x41};
        assertArrayEquals(input, SimpleCompression.decode(input));
    }

    @Test
    public void decodeZeroHeaderCountReturnsPayload () {
        //valid magic + headerCount=0 → no algorithm bytes, payload returned as-is
        byte[] input = {0x53, 0x43, 0x00, 0x41, 0x42};
        assertArrayEquals(new byte[]{0x41, 0x42}, SimpleCompression.decode(input));
    }

    @Test
    public void decodeExactlyThreeBytesWithZeroHeaders () {
        //minimum valid frame: magic + headerCount=0, empty payload
        byte[] input = {0x53, 0x43, 0x00};
        assertArrayEquals(new byte[0], SimpleCompression.decode(input));
    }

    //--- decode errors ---

    @Test
    public void decodeCorruptHeaderCountThrows () {
        //magic OK but headerCount=5 with no bytes following
        byte[] input = {0x53, 0x43, 5};
        assertThrows(IllegalArgumentException.class, () -> SimpleCompression.decode(input));
    }

    @Test
    public void decodeUnknownHeaderByteThrows () {
        //valid frame with an unknown header byte 0xFF
        byte[] encoded = SimpleCompression.encode(new byte[]{1, 2, 3}, "RLE");
        encoded[3] = (byte) 0xFF; //corrupt the algorithm header byte
        assertThrows(IllegalArgumentException.class, () -> SimpleCompression.decode(encoded));
    }

    //--- roundtrips per algorithm ---

    @Test
    public void roundtripRle () {
        byte[] original = "aaaaaabbbbcccdd".getBytes();
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "RLE")));
    }

    @Test
    public void roundtripLz77 () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "LZ77")));
    }

    @Test
    public void roundtripHuffman () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "HUFFMAN")));
    }

    @Test
    public void roundtripDelta () {
        byte[] original = {10, 20, 30, 40, 50, 60, 70, 80};
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "DELTA")));
    }

    //--- roundtrips multi-algorithm ---

    @Test
    public void roundtripDeltaThenHuffman () {
        byte[] original = new byte[200];
        for (int i = 0; i < original.length; i++) original[i] = (byte)(i * 2);
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "DELTA", "HUFFMAN")));
    }

    @Test
    public void roundtripLz77ThenHuffman () {
        byte[] original = "abracadabra abracadabra abracadabra".getBytes();
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "LZ77", "HUFFMAN")));
    }

    @Test
    public void roundtripDeltaRleLz77Huffman () {
        byte[] original = new byte[500];
        Arrays.fill(original, (byte) 0x41);
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original, "DELTA", "RLE", "LZ77", "HUFFMAN")));
    }

    //--- auto-mode ---

    @Test
    public void autoModeRoundtripText () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original)));
    }

    @Test
    public void autoModeRoundtripRepetitiveData () {
        byte[] original = new byte[200];
        Arrays.fill(original, (byte) 0x42);
        assertArrayEquals(original, SimpleCompression.decode(SimpleCompression.encode(original)));
    }

    @Test
    public void autoModeSingleByteNoFraming () {
        //1-byte input: probe produces no windows, select returns [], encode returns data unchanged
        byte[] original = {0x42};
        byte[] encoded = SimpleCompression.encode(original);
        assertArrayEquals(original, encoded); //no framing
        assertArrayEquals(original, SimpleCompression.decode(encoded)); //passthrough decode
    }

    @Test
    public void autoModeEncodedOutputIsDecodable () {
        //any data auto-encoded must survive a decode cycle
        byte[] original = new byte[300];
        for (int i = 0; i < original.length; i++) original[i] = (byte)(i % 251);
        byte[] encoded = SimpleCompression.encode(original);
        assertArrayEquals(original, SimpleCompression.decode(encoded));
    }
}
