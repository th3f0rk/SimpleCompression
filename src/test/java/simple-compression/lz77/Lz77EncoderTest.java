import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class Lz77EncoderTest {

    @Test
    public void singleByte () {
        //not enough bytes to form a trigram, must be a literal
        byte[] input = {0x42};
        byte[] expected = {0, 0x42}; //bitmask 0 (all literals), then the byte
        assertArrayEquals(expected, Lz77Encoder.encode(input));
    }

    @Test
    public void twoDistinctBytes () {
        byte[] input = {0x01, 0x02};
        byte[] expected = {0, 0x01, 0x02}; //bitmask 0, two literals
        assertArrayEquals(expected, Lz77Encoder.encode(input));
    }

    @Test
    public void noMatchWhenTooShortToHashLookup () {
        //cursor+2 >= data.length skips hash lookup so even a repeated byte stays literal
        byte[] input = {0x41, 0x42, 0x41};
        byte[] expected = {0, 0x41, 0x42, 0x41}; //all literals
        assertArrayEquals(expected, Lz77Encoder.encode(input));
    }

    @Test
    public void backReferenceToken () {
        //second occurrence of the trigram produces a match token at bit position 3
        //bitmask: bit0=lit, bit1=lit, bit2=lit, bit3=match → 0b00001000 = 8
        //match token: distance=3 encoded as two bytes [0, 3], length=3
        byte[] input = {0x41, 0x42, 0x43, 0x41, 0x42, 0x43};
        byte[] expected = {8, 0x41, 0x42, 0x43, 0, 3, 3};
        assertArrayEquals(expected, Lz77Encoder.encode(input));
    }

    @Test
    public void bitmaskWrapsAfterEight () {
        //9 distinct bytes span two bitmask bytes
        //all are literals so both bitmask bytes are 0
        byte[] input = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09};
        byte[] expected = {0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0, 0x09};
        assertArrayEquals(expected, Lz77Encoder.encode(input));
    }

    @Test
    public void roundtrip () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripRepeatingPattern () {
        //dense repetition exercises back-reference chaining
        byte[] original = new byte[500];
        Arrays.fill(original, (byte) 0x41);
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripBinaryData () {
        //structured binary pattern with periodic repetition
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i & 0xFF);
        byte[] decoded = Lz77Decoder.decode(Lz77Encoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void maxCandidatesVariantRoundtrip () {
        //confirm the two-argument overload also roundtrips correctly
        byte[] original = "abracadabra abracadabra abracadabra".getBytes();
        byte[] encoded = Lz77Encoder.encode(original, 16);
        byte[] decoded = Lz77Decoder.decode(encoded);
        assertArrayEquals(original, decoded);
    }
}
