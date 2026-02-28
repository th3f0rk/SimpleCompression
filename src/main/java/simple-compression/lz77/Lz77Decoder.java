import java.util.Arrays;

public class Lz77Decoder {
    private byte[] data;

    public Lz77Decoder(byte[] data) {
        this.data = data;
    }

    public byte[] decode() {
        int cursor = 0;
        int bitMask = 0;
        int bitIndex = 0;
        int distance = 0;
        int length = 0;
        int copyStart = 0;
        int decodedCursor = 0;

        byte[] decoded = new byte[this.data.length * 4];

        while (cursor < this.data.length) {
            if (bitIndex == 0) { //read next bitmask byte
                bitMask = this.data[cursor++] & 0xFF;
            }

            if (cursor >= this.data.length) break; //trailing bitmask with unused zero bits

            if ((bitMask & (1 << bitIndex)) != 0) { //match
                int high = this.data[cursor++] & 0xFF;
                int low = this.data[cursor++] & 0xFF;
                distance = (high << 8) | low;
                length = this.data[cursor++] & 0xFF;

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
                decoded[decodedCursor++] = (byte) (this.data[cursor++] & 0xFF);
            }

            bitIndex = (bitIndex + 1) & 7;
        }

        return Arrays.copyOf(decoded, decodedCursor);
    }
}

