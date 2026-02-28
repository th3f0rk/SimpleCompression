import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class HuffmanEncoderTest {

    @Test
    public void singleByteExactOutput () {
        //one unique symbol: edge case forces code length 1, code value 0
        //format: symbolCount[2] | header(codeLen=1, sym=0x41)[2] | bitLen=1[4] | payload(0 padded to byte)[1]
        byte[] input = {0x41};
        byte[] expected = {0x00, 0x01, 0x01, 0x41, 0x00, 0x00, 0x00, 0x01, 0x00};
        assertArrayEquals(expected, HuffmanEncoder.encode(input));
    }

    @Test
    public void singleUniqueSymbolRepeatedExactOutput () {
        //three identical bytes: still one unique symbol, code=0 len=1, bitLen=3
        //payload: three 0-bits packed into the high bits of one byte → 0b000_00000 = 0x00
        byte[] input = {0x41, 0x41, 0x41};
        byte[] expected = {0x00, 0x01, 0x01, 0x41, 0x00, 0x00, 0x00, 0x03, 0x00};
        assertArrayEquals(expected, HuffmanEncoder.encode(input));
    }

    @Test
    public void symbolCountFieldReflectsDistinctBytes () {
        //symbolCount is the big-endian uint16 at bytes [0..1]
        byte[] input = {0x01, 0x02, 0x03};
        byte[] encoded = HuffmanEncoder.encode(input);
        int symbolCount = ((encoded[0] & 0xFF) << 8) | (encoded[1] & 0xFF);
        assertEquals(3, symbolCount);
    }

    @Test
    public void bitLenFieldReflectsEncodedBitCount () {
        //single unique symbol → one code bit per input byte, so bitLen == input.length
        //bitLen is the big-endian uint32 immediately after the header entries
        //offset = 2 (symbolCount) + 2*1 (one header entry) = 4
        byte[] input = new byte[13];
        Arrays.fill(input, (byte) 0x41);
        byte[] encoded = HuffmanEncoder.encode(input);
        int bitLen = ((encoded[4] & 0xFF) << 24) | ((encoded[5] & 0xFF) << 16)
                   | ((encoded[6] & 0xFF) << 8)  |  (encoded[7] & 0xFF);
        assertEquals(13, bitLen);
    }

    @Test
    public void outputLengthFormula () {
        //for a single unique symbol of count n:
        //output = 2 (symbolCount) + 2 (one header entry) + 4 (bitLen) + ceil(n/8) (payload)
        byte[] input = new byte[8];
        Arrays.fill(input, (byte) 0x41);
        byte[] encoded = HuffmanEncoder.encode(input);
        assertEquals(9, encoded.length); //2 + 2 + 4 + 1 = 9
    }

    @Test
    public void outputLengthFormulaCeilsPayload () {
        //9 bits of payload requires 2 payload bytes, not 1
        byte[] input = new byte[9];
        Arrays.fill(input, (byte) 0x41);
        byte[] encoded = HuffmanEncoder.encode(input);
        assertEquals(10, encoded.length); //2 + 2 + 4 + 2 = 10
    }

    @Test
    public void roundtripAsciiText () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripSingleRepeatedByte () {
        //exercises the single-unique-symbol edge case end to end
        byte[] original = new byte[200];
        Arrays.fill(original, (byte) 0x41);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripAllDistinctBytes () {
        //all 256 possible byte values, each appearing exactly once
        byte[] original = new byte[256];
        for (int i = 0; i < 256; i++) original[i] = (byte) i;
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripBinaryGradient () {
        //structured pattern with periodic repetition
        byte[] original = new byte[512];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i & 0xFF);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripSkewedFrequencies () {
        //one very high-frequency symbol and one rare symbol: exercises deep vs shallow tree path
        byte[] original = new byte[200];
        Arrays.fill(original, 0, 190, (byte) 0x41);
        Arrays.fill(original, 190, 200, (byte) 0x42);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripLargeInput () {
        //large input with a variety of symbol frequencies
        byte[] original = new byte[10_000];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i % 251);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }
}
