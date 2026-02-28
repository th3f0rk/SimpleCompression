public class DeltaDecoder {

    private DeltaDecoder() {}

    /** this is the decode method. it takes delta encoded data and reconstructs the original bytes
     *
     * @param data the delta encoded byte array to decompress
     * @return the original uncompressed byte array
     */
    public static byte[] decode(byte[] data) {
        byte[] out = new byte[data.length];

        if (data.length == 0) {
            throw new IllegalArgumentException("there is no data to decompress. the bytearray passed is empty.");
        }

        out[0] = data[0]; //first byte stored as-is

        for (int i = 1; i < data.length; i++) { //add each delta to the previous value
            out[i] = (byte)(data[i] + out[i - 1]);
        }

        return out;
    }
}
