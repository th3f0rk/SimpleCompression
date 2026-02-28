import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class HuffmanDecoderTest {

    @Test
    public void singleSymbolHandcrafted () {
        //symbolCount=1, header: codeLen=1 sym=0x41, bitLen=1, payload=0x00
        //code: 0x41 → 0 (len 1); bit stream: 0 padded → 0b00000000
        byte[] encoded = {0x00, 0x01, 0x01, 0x41, 0x00, 0x00, 0x00, 0x01, 0x00};
        byte[] expected = {0x41};
        assertArrayEquals(expected, HuffmanDecoder.decode(encoded));
    }

    @Test
    public void singleSymbolRepeatedHandcrafted () {
        //symbolCount=1, header: codeLen=1 sym=0x41, bitLen=3, payload=0x00
        //three 0-bits packed high: 0b000_00000 = 0x00
        byte[] encoded = {0x00, 0x01, 0x01, 0x41, 0x00, 0x00, 0x00, 0x03, 0x00};
        byte[] expected = {0x41, 0x41, 0x41};
        assertArrayEquals(expected, HuffmanDecoder.decode(encoded));
    }

    @Test
    public void twoSymbolsHandcrafted () {
        //canonical codes: 0x41 → 0 (len 1), 0x42 → 1 (len 1)
        //encode [A, B, A]: bits 0,1,0 → outBits=0b010, flush: 0b010 << 5 = 0x40, bitLen=3
        //header entries sorted by (codeLen, sym): (1,0x41), (1,0x42)
        byte[] encoded = {0x00, 0x02, 0x01, 0x41, 0x01, 0x42, 0x00, 0x00, 0x00, 0x03, 0x40};
        byte[] expected = {0x41, 0x42, 0x41};
        assertArrayEquals(expected, HuffmanDecoder.decode(encoded));
    }

    @Test
    public void threeSymbolsHandcrafted () {
        //canonical codes: 0x41 → 0 (len 1), 0x42 → 10 (len 2), 0x43 → 11 (len 2)
        //encode [A, B, C, A]: bits 0,10,11,0 → 7 bits: 0b0101100 → flush: 0b0101100_0 = 0x58, bitLen=6
        //wait: outBitsLen=6 so flush: outBits(0b010110) << (8-6) = 0b01011000 = 0x58
        //header entries sorted: (1,0x41), (2,0x42), (2,0x43)
        byte[] encoded = {
            0x00, 0x03,             //symbolCount = 3
            0x01, 0x41,             //codeLen=1, sym=0x41
            0x02, 0x42,             //codeLen=2, sym=0x42
            0x02, 0x43,             //codeLen=2, sym=0x43
            0x00, 0x00, 0x00, 0x06, //bitLen = 6
            0x58                    //payload: 0b01011000
        };
        byte[] expected = {0x41, 0x42, 0x43, 0x41};
        assertArrayEquals(expected, HuffmanDecoder.decode(encoded));
    }

    @Test
    public void paddingBitsAreIgnored () {
        //two symbols A(0x41)→0 len 1, B(0x42)→1 len 1; encode [A, B], bitLen=2
        //normal payload: bits 01 shifted high → 0x40; change padding bits to all-ones → 0x7F
        //bits 7,6 of 0x7F are still 0,1 → A, B; bits 5-0 (all 1s) are padding and must be ignored
        byte[] encoded = {0x00, 0x02, 0x01, 0x41, 0x01, 0x42, 0x00, 0x00, 0x00, 0x02, 0x7F};
        byte[] expected = {0x41, 0x42};
        assertArrayEquals(expected, HuffmanDecoder.decode(encoded));
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
        byte[] original = new byte[512];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i & 0xFF);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripSkewedFrequencies () {
        //one very high-frequency symbol and one rare symbol
        byte[] original = new byte[200];
        Arrays.fill(original, 0, 190, (byte) 0x41);
        Arrays.fill(original, 190, 200, (byte) 0x42);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripLargeInput () {
        byte[] original = new byte[10_000];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i % 251);
        byte[] decoded = HuffmanDecoder.decode(HuffmanEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }
}
