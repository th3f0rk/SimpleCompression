import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class RleDecoderTest {

    @Test
    public void emptyInputThrows () {
        assertThrows(IllegalArgumentException.class, () -> RleDecoder.decode(new byte[]{}));
    }

    @Test
    public void singleLiteralToken () {
        byte[] input = {0, 0x42}; //header 0 = count 1
        byte[] expected = {0x42};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void multiLiteralToken () {
        byte[] input = {2, 0x01, 0x02, 0x03}; //header 2 = count 3
        byte[] expected = {0x01, 0x02, 0x03};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void maxLiteralToken () {
        //header 127 = 128 literals
        byte[] input = new byte[129];
        input[0] = 127;
        for (int i = 1; i < 129; i++) input[i] = (byte) (i - 1);

        byte[] expected = new byte[128];
        for (int i = 0; i < 128; i++) expected[i] = (byte) i;

        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void singleRunToken () {
        byte[] input = {(byte) -2, 0x41}; //header -2 = count 1-(-2) = 3
        byte[] expected = {0x41, 0x41, 0x41};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void maxRunToken () {
        //header -127 = count 1-(-127) = 128
        byte[] input = {(byte) -127, 0x41};
        byte[] expected = new byte[128];
        Arrays.fill(expected, (byte) 0x41);
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void mixedLiteralAndRunTokens () {
        //3 literals then a run of 3
        byte[] input = {2, 0x01, 0x02, 0x41, (byte) -2, 0x41};
        byte[] expected = {0x01, 0x02, 0x41, 0x41, 0x41, 0x41};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void consecutiveRunTokens () {
        //run of 3 followed immediately by a run of 2
        byte[] input = {(byte) -2, 0x41, (byte) -1, 0x42};
        byte[] expected = {0x41, 0x41, 0x41, 0x42, 0x42};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void consecutiveLiteralTokens () {
        //two separate literal blocks that would have been merged if under 128 each
        byte[] input = {1, 0x01, 0x02, 0, 0x03};
        byte[] expected = {0x01, 0x02, 0x03};
        assertArrayEquals(expected, RleDecoder.decode(input));
    }

    @Test
    public void roundtrip () {
        byte[] original = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = RleDecoder.decode(RleEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }

    @Test
    public void roundtripHighRunDensity () {
        //long run that spans the 128-byte overflow boundary
        byte[] original = new byte[300];
        Arrays.fill(original, (byte) 0xFF);
        byte[] decoded = RleDecoder.decode(RleEncoder.encode(original));
        assertArrayEquals(original, decoded);
    }
}
