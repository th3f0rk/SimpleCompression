import java.util.Arrays;

public class Lz77Decoder {

    private Lz77Decoder() {}

    /** this is the decode method. it takes LZ77 encoded data and reconstructs the original bytes
     *
     * @param data the LZ77 encoded byte array to decompress
     * @return the original uncompressed byte array
     */
    public static byte[] decode(byte[] data) {
        int cursor = 0;
        int bitMask = 0;
        int bitIndex = 0;
        int distance = 0;
        int length = 0;
        int copyStart = 0;
        int decodedCursor = 0;

        byte[] decoded = new byte[data.length * 4];

        if (data.length == 0) {
            throw new IllegalArgumentException("there is no data to decompress. the bytearray passed is empty.");
        }

        while (cursor < data.length) {
            if (bitIndex == 0) { //read next bitmask byte
                bitMask = data[cursor++] & 0xFF;
            }

            if (cursor >= data.length) break; //trailing bitmask with unused zero bits

            if ((bitMask & (1 << bitIndex)) != 0) { //match
                int high = data[cursor++] & 0xFF;
                int low = data[cursor++] & 0xFF;
                distance = (high << 8) | low;
                length = data[cursor++] & 0xFF;

                copyStart = decodedCursor - distance;

                if (decodedCursor + length >= decoded.length) { //grow buffer if needed
                    decoded = Arrays.copyOf(decoded, decoded.length * 2);
                }

                for (int i = 0; i < length; i++) { //copy byte by byte to handle overlapping matches
                    decoded[decodedCursor++] = decoded[copyStart + i];
                }
            } else { //literal
                if (decodedCursor >= decoded.length) { //grow buffer if needed
                    decoded = Arrays.copyOf(decoded, decoded.length * 2);
                }
                decoded[decodedCursor++] = (byte) (data[cursor++] & 0xFF);
            }

            bitIndex = (bitIndex + 1) & 7;
        }

        return Arrays.copyOf(decoded, decodedCursor);
    }
}
