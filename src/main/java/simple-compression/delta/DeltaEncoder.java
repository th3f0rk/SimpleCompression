public class DeltaEncoder {

    private DeltaEncoder() {}

    /** this is the encode method. it does delta encoding by storing the difference between each consecutive byte
     *
     * @param data the raw byte array to compress
     * @return a delta encoded byte array
     */
    public static byte[] encode(byte[] data) {
        byte[] out = new byte[data.length];

        if (data.length == 0) {
            throw new IllegalArgumentException("there is no data to compress. the bytearray passed is empty.");
        }

        out[0] = data[0]; //first byte stored as-is

        for (int i = 1; i < data.length; i++) { //store difference from previous byte
            out[i] = (byte)(data[i] - data[i - 1]);
        }

        return out;
    }
}
