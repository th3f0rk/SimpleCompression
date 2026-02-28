import java.util.Arrays;

public class HuffmanDecoder {

    private HuffmanDecoder() {}

    /** this is the decode method. it takes Huffman encoded data and reconstructs the original bytes
     *
     * @param data the Huffman encoded byte array to decompress
     * @return the original uncompressed byte array
     */
    public static byte[] decode(byte[] data) {
        int cursor = 0;

        if (data.length == 0) {
            throw new IllegalArgumentException("there is no data to decompress. the bytearray passed is empty.");
        }

        int symbolCount = ((data[cursor] & 0xFF) << 8) | (data[cursor + 1] & 0xFF);
        cursor += 2;

        int[] pairLen = new int[symbolCount];
        int[] pairSym = new int[symbolCount];
        int maxCodeLen = 0;
        for (int i = 0; i < symbolCount; i++) { //parse the header
            pairLen[i] = data[cursor] & 0xFF;
            pairSym[i] = data[cursor + 1] & 0xFF;
            if (pairLen[i] > maxCodeLen) maxCodeLen = pairLen[i];
            cursor += 2;
        }

        //read bitLen
        int bitLen = ((data[cursor] & 0xFF) << 24) |
                     ((data[cursor + 1] & 0xFF) << 16) |
                     ((data[cursor + 2] & 0xFF) << 8) |
                     (data[cursor + 3] & 0xFF);
        cursor += 4;

        sortPairs(pairLen, pairSym, symbolCount); //reconstruct canonical codes

        int currentCode = 0;
        int previousLen = 0;
        int[] codeWord = new int[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            if (pairLen[i] > previousLen) {
                currentCode <<= (pairLen[i] - previousLen);
            }
            codeWord[i] = currentCode;
            currentCode++;
            previousLen = pairLen[i];
        }

        int tableSize = 1 << maxCodeLen;
        int[] table = new int[tableSize]; //flat lookup table

        for (int i = 0; i < symbolCount; i++) { //fill lookup table
            int len        = pairLen[i];
            int sym        = pairSym[i];
            int code       = codeWord[i];
            int suffixBits = maxCodeLen - len;
            int base       = code << suffixBits; //left-align code
            int slots      = 1 << suffixBits;   //slots sharing this prefix
            int entry      = (sym << 8) | len;
            for (int j = 0; j < slots; j++) {
                table[base | j] = entry;
            }
        }

        byte[] decoded = new byte[data.length * 4];
        int decodedPos   = 0;
        long bitBuf      = 0; //bit buffer
        int  bitCount    = 0; //valid bits in bitBuf
        int  bitsDecoded = 0;
        int  dataPos     = cursor;

        while (bitsDecoded < bitLen) { //decode the bitstream
            while (bitCount < 56 && dataPos < data.length) { //refill buffer
                bitBuf = (bitBuf << 8) | (data[dataPos++] & 0xFF);
                bitCount += 8;
            }

            //extract lookup key from bit buffer
            int idx;
            if (bitCount >= maxCodeLen) {
                idx = (int)((bitBuf >> (bitCount - maxCodeLen)) & (tableSize - 1));
            } else {
                idx = (int)((bitBuf << (maxCodeLen - bitCount)) & (tableSize - 1)); //zero-pad right for final symbols
            }

            int entry = table[idx];
            int sym   = (entry >> 8) & 0xFF;
            int len   = entry & 0xFF;

            if (decodedPos >= decoded.length) { //grow output buffer if needed
                decoded = Arrays.copyOf(decoded, decoded.length * 2);
            }
            decoded[decodedPos++] = (byte) sym;
            bitCount    -= len;
            bitsDecoded += len;
            bitBuf      &= (1L << bitCount) - 1;
        }

        return Arrays.copyOf(decoded, decodedPos);
    }

    /** this is the sort method. it does insertion sort on the parallel length and symbol arrays
     *
     * @param lens the code lengths to sort
     * @param syms the symbols sorted in tandem with lens
     * @param n    the number of entries
     */
    private static void sortPairs(int[] lens, int[] syms, int n) {
        for (int i = 1; i < n; i++) {
            int kl = lens[i], ks = syms[i];
            int j = i - 1;
            while (j >= 0 && (lens[j] > kl || (lens[j] == kl && syms[j] > ks))) {
                lens[j + 1] = lens[j];
                syms[j + 1] = syms[j];
                j--;
            }
            lens[j + 1] = kl;
            syms[j + 1] = ks;
        }
    }
}
