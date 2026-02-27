import java.util.Arrays;

public class RleDecoder {

    private byte[] data;

    public RleDecoder (byte[] data) {
        this.data = data;
    }

    /** this is the decode method. it takes PackBits style RLE encoded data and reconstructs the original bytes
     *
     * @return decoded - returns the original uncompressed byte array
     */
    public byte[] decode() {
        int outputSize = 0;
        int index = 0;

        if (this.data.length == 0) {
            throw new IllegalArgumentException("there is no data to decompress. the bytearray passed is empty.");
        }

        while (index < this.data.length) {
            int header = (int) this.data[index];

            if (header >= 0) { //literal block
                int count = header + 1;
                outputSize += count;
                index += 1 + count; //skip past the header and all its literal bytes
            } else { //run block
                int count = 1 - header;
                outputSize += count;
                index += 2; //skip past the header and the single run byte
            }
        }

        byte[] decoded = new byte[outputSize];
        int position = 0;
        index = 0;

        while (index < this.data.length) {
            int header = (int) this.data[index];

            if (header >= 0) { //literal block
                int count = header + 1;
                System.arraycopy(this.data, index + 1, decoded, position, count);
                position += count;
                index += 1 + count;
            } else { //run block
                int count = 1 - header;
                byte runByte = this.data[index + 1];
                Arrays.fill(decoded, position, position + count, runByte);
                position += count;
                index += 2;
            }
        }

        return decoded;
    }
}
