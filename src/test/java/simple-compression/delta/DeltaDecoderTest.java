import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class DeltaDecoderTest {

    @Test
    public void emptyInputThrows () {
        assertThrows(IllegalArgumentException.class, () -> DeltaDecoder.decode(new byte[]{}));
    }

    @Test
    public void singleByte () {
        byte[] input = {0x42};
        byte[] expected = {0x42};
        assertArrayEquals(expected, DeltaDecoder.decode(input));
    }

    @Test
    public void positiveDeltas () {
        //start at 10, add 5 each step
        byte[] input = {10, 5, 5, 5};
        byte[] expected = {10, 15, 20, 25};
        assertArrayEquals(expected, DeltaDecoder.decode(input));
    }

    @Test
    public void negativeDeltas () {
        //(byte)(-10) + 40 = 30, (byte)(-10) + 30 = 20
        byte[] input = {40, (byte) -10, (byte) -10};
        byte[] expected = {40, 30, 20};
        assertArrayEquals(expected, DeltaDecoder.decode(input));
    }

    @Test
    public void zeroDeltas () {
        //all-zero deltas after the first byte produce a flat run
        byte[] input = {7, 0, 0, 0};
        byte[] expected = {7, 7, 7, 7};
        assertArrayEquals(expected, DeltaDecoder.decode(input));
    }

    @Test
    public void byteWrapsAtBoundary () {
        //inverse of the encoder boundary test: 0xFE + 0x01 = 0xFF, 0x02 + 0xFF = 0x01
        byte[] input = {0x01, (byte) 0xFE, 0x02};
        byte[] expected = {0x01, (byte) 0xFF, 0x01};
        assertArrayEquals(expected, DeltaDecoder.decode(input));
    }

    @Test
    public void roundtrip () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = DeltaDecoder.decode(DeltaEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripRepeatedByte () {
        //flat input encodes to {byte, 0, 0, ...} and must decode back cleanly
        byte[] original = new byte[200];
        Arrays.fill(original, (byte) 0x41);
        byte[] decoded = DeltaDecoder.decode(DeltaEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripBinaryGradient () {
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
