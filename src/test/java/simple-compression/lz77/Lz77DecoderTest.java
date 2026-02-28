import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class Lz77DecoderTest {

    @Test
    public void singleLiteral () {
        byte[] input = {0, 0x41}; //bitmask 0 = all literals
        byte[] expected = {0x41};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void twoLiterals () {
        byte[] input = {0, 0x41, 0x42};
        byte[] expected = {0x41, 0x42};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void literalThenMatch () {
        //bitmask 8 = 0b00001000: bits 0-2 are literals, bit 3 is a match
        //match token: distance=3 (bytes [0,3]), length=3
        byte[] input = {8, 0x41, 0x42, 0x43, 0, 3, 3};
        byte[] expected = {0x41, 0x42, 0x43, 0x41, 0x42, 0x43};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void overlappingMatch () {
        //distance=1, length=5: each copied byte is the one just written, extending a single byte into a run
        //bitmask 2 = 0b00000010: bit 0 is literal, bit 1 is match
        byte[] input = {2, 0x41, 0, 1, 5};
        byte[] expected = {0x41, 0x41, 0x41, 0x41, 0x41, 0x41};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void bitmaskWrapsAfterEight () {
        //first bitmask byte covers 8 decisions, second covers the 9th
        byte[] input = {0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0, 0x09};
        byte[] expected = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void trailingBitmaskWithUnusedBits () {
        //a bitmask byte whose high bits are unused zero bits should not be misread as tokens
        //two literals fit in the low two bits of the bitmask; the remaining 6 bits are zero
        byte[] input = {0, 0x41, 0x42}; //bitmask 0: all unused high bits are literal (zero)
        byte[] expected = {0x41, 0x42};
        assertArrayEquals(expected, Lz77Decoder.decode(input));
    }

    @Test
    public void roundtrip () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripRepeatingData () {
        //long run of identical bytes produces many back-references; decoder must handle them all
        byte[] original = new byte[500];
        Arrays.fill(original, (byte) 0x41);
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripBinaryData () {
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i & 0xFF);
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }
}
