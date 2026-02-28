import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class RleEncoderTest {

    @Test
    public void emptyInputThrows () {
        assertThrows(IllegalArgumentException.class, () -> RleEncoder.encode(new byte[]{}));
    }

    @Test
    public void singleByte () {
        byte[] input = {0x42};
        byte[] expected = {0, 0x42}; //header 0 = 1 literal
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void threeDistinctBytes () {
        byte[] input = {0x01, 0x02, 0x03};
        byte[] expected = {2, 0x01, 0x02, 0x03}; //header 2 = 3 literals
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void twoIdenticalBytesStaysLiteral () {
        //runCount never hits 2 so no run forms
        byte[] input = {0x41, 0x41};
        byte[] expected = {1, 0x41, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void threeIdenticalBytesStaysLiteral () {
        //the third identical byte puts runCount at 2, but the run threshold fires on the next byte
        byte[] input = {0x41, 0x41, 0x41};
        byte[] expected = {2, 0x41, 0x41, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void fourIdenticalBytesProducesRun () {
        //one literal escapes before the run is committed, then a run of 3 follows
        byte[] input = {0x41, 0x41, 0x41, 0x41};
        byte[] expected = {0, 0x41, (byte) -2, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void tenIdenticalBytes () {
        //one literal then a run of 9: header -(9-1) = -8
        byte[] input = new byte[10];
        Arrays.fill(input, (byte) 0x41);
        byte[] expected = {0, 0x41, (byte) -8, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void mixedLiteralsAndRun () {
        //two distinct bytes then a run of four: the run detection leaves one of the run bytes
        //in the preceding literal block before committing the run
        byte[] input = {0x01, 0x02, 0x41, 0x41, 0x41, 0x41};
        byte[] expected = {2, 0x01, 0x02, 0x41, (byte) -2, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void literalCountOverflowFlushesAt128 () {
        //129 distinct bytes forces a flush of the first 128 into their own literal block
        byte[] input = new byte[129];
        for (int i = 0; i < 129; i++) input[i] = (byte) i;

        byte[] result = RleEncoder.encode(input);

        assertEquals(131, result.length);
        assertEquals((byte) 127, result[0]);  //first block header: 128 literals
        assertEquals((byte) 0, result[129]);  //second block header: 1 literal
        assertEquals((byte) 128, result[130]); //the 129th byte
    }

    @Test
    public void runCountOverflowSplitsAt128 () {
        //131 identical bytes: run of 128 is flushed mid-stream, the remaining 2 follow as literals
        byte[] input = new byte[131];
        Arrays.fill(input, (byte) 0x41);

        //[0, 0x41] = 1 literal before run detection
        //[(byte)-127, 0x41] = run of 128
        //[1, 0x41, 0x41] = 2 literals left over
        byte[] expected = {0, 0x41, (byte) -127, 0x41, 1, 0x41, 0x41};
        assertArrayEquals(expected, RleEncoder.encode(input));
    }

    @Test
    public void roundtrip () {
        byte[] input = "the quick brown fox jumps over the lazy dog".getBytes();
        byte[] decoded = RleDecoder.decode(RleEncoder.encode(input));
        assertArrayEquals(input, decoded);
    }

    @Test
    public void roundtripBinaryPattern () {
        //alternating bytes produce no runs, exercises the pure-literal path end to end
        byte[] input = new byte[64];
        for (int i = 0; i < input.length; i++) input[i] = (byte) (i % 2 == 0 ? 0xAA : 0x55);
        byte[] decoded = RleDecoder.decode(RleEncoder.encode(input));
        assertArrayEquals(input, decoded);
    }
}
