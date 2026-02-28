import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class DeltaEncoderTest {

    @Test
    public void emptyInputThrows () {
        assertThrows(IllegalArgumentException.class, () -> DeltaEncoder.encode(new byte[]{}));
    }

    @Test
    public void singleByte () {
        byte[] input = {0x42};
        byte[] expected = {0x42}; //single byte stored as-is, no previous to diff against
        assertArrayEquals(expected, DeltaEncoder.encode(input));
    }

    @Test
    public void uniformIncrement () {
        //each step is +10 so all deltas are 10
        byte[] input = {10, 20, 30, 40};
        byte[] expected = {10, 10, 10, 10};
        assertArrayEquals(expected, DeltaEncoder.encode(input));
    }

    @Test
    public void uniformDecrement () {
        //each step is -10, (byte)(30-40) = (byte)(-10) = 246 unsigned
        byte[] input = {40, 30, 20, 10};
        byte[] expected = {40, (byte) -10, (byte) -10, (byte) -10};
        assertArrayEquals(expected, DeltaEncoder.encode(input));
    }

    @Test
    public void zeroDeltas () {
        //identical bytes produce all-zero deltas after the first
        byte[] input = {5, 5, 5, 5};
        byte[] expected = {5, 0, 0, 0};
        assertArrayEquals(expected, DeltaEncoder.encode(input));
    }

    @Test
    public void byteWrapsAtBoundary () {
        //0xFF to 0x01: delta = 1 - (-1) = 2; 0x01 to 0xFF: delta = -1 - 1 = -2 = 0xFE
        byte[] input = {0x01, (byte) 0xFF, 0x01};
        byte[] expected = {0x01, (byte) 0xFE, 0x02};
        assertArrayEquals(expected, DeltaEncoder.encode(input));
    }

    @Test
    public void outputSameLengthAsInput () {
        byte[] input = new byte[100];
        Arrays.fill(input, (byte) 0x42);
        assertEquals(100, DeltaEncoder.encode(input).length);
    }

    @Test
    public void roundtrip () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = DeltaDecoder.decode(DeltaEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripBinaryGradient () {
        //all 256 byte values in order, uniform deltas of 1
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;
        byte[] decoded = DeltaDecoder.decode(DeltaEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripLargeInput () {
        byte[] original = new byte[10_000];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i % 251);
        byte[] decoded = DeltaDecoder.decode(DeltaEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }
}
