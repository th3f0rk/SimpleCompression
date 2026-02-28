public class SimpleCompression {

    private SimpleCompression() {}

    private static final byte HDR_RLE     = 0x01;
    private static final byte HDR_LZ77    = 0x02;
    private static final byte HDR_HUFFMAN = 0x03;
    private static final byte HDR_DELTA   = 0x04;
    private static final int  MAGIC_0     = 0x53; //'S'
    private static final int  MAGIC_1     = 0x43; //'C'

    /** this is the encode method. it auto-selects compression algorithms using the probe and returns
     * a framed byte array with a magic header describing the sequence used. it has a second form
     * that accepts a manual algorithm sequence
     *
     * @param data the raw byte array to compress
     * @return a framed and compressed byte array, or the original data if no algorithm was selected
     * @see #encode(byte[], String...)
     */
    public static byte[] encode(byte[] data) {
        return encode(data, Probe.select(data));
    }

    /** this is the encode method. it compresses data using the given algorithm sequence and frames the
     * result with magic bytes and a header that describes the sequence for later decoding
     *
     * @param data the raw byte array to compress
     * @param sequence the algorithm names to apply in order (e.g. "RLE", "LZ77", "HUFFMAN", "DELTA")
     * @return a framed and compressed byte array, or the original data if the sequence is empty
     */
    public static byte[] encode(byte[] data, String... sequence) {
        byte[] output  = data;
        byte[] headers = new byte[sequence.length];
        int headerCount = 0;

        for (String alg : sequence) {
            if      (alg.equals("RLE"))     { output = RleEncoder.encode(output);    headers[headerCount++] = HDR_RLE; }
            else if (alg.equals("LZ77"))    { output = Lz77Encoder.encode(output);   headers[headerCount++] = HDR_LZ77; }
            else if (alg.equals("HUFFMAN")) { output = HuffmanEncoder.encode(output); headers[headerCount++] = HDR_HUFFMAN; }
            else if (alg.equals("DELTA"))   { output = DeltaEncoder.encode(output);  headers[headerCount++] = HDR_DELTA; }
            else throw new IllegalArgumentException("unknown algorithm: " + alg);
        }

        if (headerCount == 0) return data; //no algorithms selected, return as-is

        byte[] framed = new byte[3 + headerCount + output.length];
        int pos = 0;
        framed[pos++] = (byte) MAGIC_0;
        framed[pos++] = (byte) MAGIC_1;
        framed[pos++] = (byte) headerCount;
        for (int i = 0; i < headerCount; i++) framed[pos++] = headers[i]; //write sequence header
        System.arraycopy(output, 0, framed, pos, output.length);
        return framed;
    }

    /** this is the decode method. it reads the magic-byte frame, extracts the algorithm sequence,
     * and applies the algorithms in reverse order to reconstruct the original data
     *
     * @param data the framed and compressed byte array to decompress
     * @return the original uncompressed byte array, or the input unchanged if it has no valid frame
     */
    public static byte[] decode(byte[] data) {
        if (data.length < 3) return data; //too short to be a valid frame, return as-is
        if ((data[0] & 0xFF) != MAGIC_0 || (data[1] & 0xFF) != MAGIC_1) return data; //no magic header

        int headerCount = data[2] & 0xFF;
        int cursor = 3;

        if (data.length < cursor + headerCount) {
            throw new IllegalArgumentException("invalid frame: header count exceeds data length.");
        }

        byte[] headers = new byte[headerCount];
        for (int i = 0; i < headerCount; i++) headers[i] = data[cursor++]; //read sequence header

        byte[] output = new byte[data.length - cursor];
        System.arraycopy(data, cursor, output, 0, output.length);

        for (int i = headerCount - 1; i >= 0; i--) { //decode in reverse order
            int hdr = headers[i] & 0xFF;
            if      (hdr == HDR_RLE)     output = RleDecoder.decode(output);
            else if (hdr == HDR_LZ77)    output = Lz77Decoder.decode(output);
            else if (hdr == HDR_HUFFMAN) output = HuffmanDecoder.decode(output);
            else if (hdr == HDR_DELTA)   output = DeltaDecoder.decode(output);
            else throw new IllegalArgumentException("invalid header byte: " + hdr);
        }

        return output;
    }
}
