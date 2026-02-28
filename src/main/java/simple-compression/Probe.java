import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class Probe {

    private Probe() {}

    private static final int RLE_GAIN      = 0;
    private static final int RLE_COVERAGE  = 1;
    private static final int LZ77_GAIN     = 2;
    private static final int LZ77_COVERAGE = 3;
    private static final int HUFF_GAIN     = 4;
    private static final int HUFF_ENTROPY  = 5;
    private static final int DELTA_GAIN    = 6;
    private static final int DELTA_AVG_ABS = 7;
    private static final int METRIC_COUNT  = 8;
    private static final int MIN_RUN       = 3;

    /** this is the analyze method. it samples random windows across the data and computes rle,
     * lz77, huffman, and delta metrics per window to inform sequencing decisions
     *
     * @param data the raw byte array to analyze
     * @return a 2d array of per-window metrics indexed by the probe metric constants
     */
    public static double[][] analyze(byte[] data) {
        int dataLen       = data.length;
        int maxSplit      = Math.max(dataLen / 5, 1);
        int maxWindowSize = Math.max(maxSplit - (dataLen / 8), 2);
        int minWindowSize = Math.max(maxWindowSize / 4, 1);
        if (minWindowSize >= maxWindowSize) minWindowSize = maxWindowSize - 1; //ensure valid nextInt range

        //count windows to pre-allocate results
        int maxWindows = 0;
        for (int i = 0; i < dataLen - 1; i += maxSplit) maxWindows++;

        double[][] results = new double[maxWindows][METRIC_COUNT];
        Random rng = new Random();
        int startIndex = 0;
        int w = 0;

        for (int i = 0; i < dataLen - 1; i += maxSplit) {
            if (startIndex >= dataLen) break;

            int windowSize = minWindowSize + rng.nextInt(maxWindowSize - minWindowSize);
            int endIndex   = Math.min(startIndex + windowSize, dataLen);
            int winLen     = endIndex - startIndex;

            //rle metrics
            int cursor   = startIndex;
            int runCount = 0;
            int runTotal = 0;
            while (cursor < endIndex) {
                int runLength = 1;
                while (cursor + runLength < endIndex && data[cursor] == data[cursor + runLength]) runLength++;
                if (runLength >= MIN_RUN) { runCount++; runTotal += runLength; cursor += runLength; }
                else cursor++;
            }
            if (runTotal > 0) {
                results[w][RLE_GAIN]     = runTotal - (runCount * 3.0); //3 bytes overhead per run
                results[w][RLE_COVERAGE] = (double) runTotal / winLen;
            }

            //lz77 metrics
            cursor = startIndex;
            int matchCount      = 0;
            int totalMatchBytes = 0;
            HashMap<Integer, Integer> seen = new HashMap<>();
            while (cursor + 4 < endIndex) {
                int chunk = (data[cursor] & 0xFF)
                          | ((data[cursor + 1] & 0xFF) << 8)
                          | ((data[cursor + 2] & 0xFF) << 16)
                          | ((data[cursor + 3] & 0xFF) << 24);
                if (seen.containsKey(chunk)) {
                    int prevPos     = seen.get(chunk);
                    int matchLength = 4;
                    while (cursor + matchLength < endIndex && prevPos + matchLength < endIndex
                           && data[cursor + matchLength] == data[prevPos + matchLength]) matchLength++;
                    if (matchLength > 4) {
                        matchCount++;
                        totalMatchBytes += matchLength;
                        cursor += matchLength;
                        continue;
                    }
                }
                seen.put(chunk, cursor);
                cursor++;
            }
            results[w][LZ77_GAIN]     = totalMatchBytes - (matchCount * 3.0); //3 bytes overhead per match
            results[w][LZ77_COVERAGE] = (double) totalMatchBytes / winLen;

            //huffman metrics — symbol entropy and estimated byte savings vs raw
            int[] freq  = new int[256];
            for (int j = startIndex; j < endIndex; j++) freq[data[j] & 0xFF]++;
            double entropy = 0.0;
            int alphabet   = 0;
            for (int f : freq) {
                if (f == 0) continue;
                alphabet++;
                double p = (double) f / winLen;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
            double estimatedBytes = entropy * winLen / 8.0;
            double headerBytes    = 1 + (alphabet * 2) + 4;
            results[w][HUFF_GAIN]    = winLen - (estimatedBytes + headerBytes);
            results[w][HUFF_ENTROPY] = entropy;

            //delta metrics — entropy of delta stream vs original, and average absolute delta magnitude
            int[] deltaFreq = new int[256];
            int absSum      = 0;
            for (int j = startIndex + 1; j < endIndex; j++) {
                deltaFreq[(data[j] - data[j - 1]) & 0xFF]++; //unsigned delta for frequency table
                absSum += Math.abs((byte)(data[j] - data[j - 1])); //signed magnitude
            }
            int deltaSamples = winLen - 1;
            if (deltaSamples > 0) {
                double deltaEntropy = 0.0;
                for (int f : deltaFreq) {
                    if (f == 0) continue;
                    double p = (double) f / deltaSamples;
                    deltaEntropy -= p * (Math.log(p) / Math.log(2));
                }
                results[w][DELTA_GAIN]    = (entropy - deltaEntropy) * winLen / 8.0; //estimated byte savings vs huffman alone
                results[w][DELTA_AVG_ABS] = (double) absSum / deltaSamples;
            }

            w++;
            startIndex += maxSplit + 1;
        }

        return Arrays.copyOf(results, w);
    }

    /** this is the process method. it aggregates per-window metrics and applies decision thresholds
     * to build a recommended compression sequence
     *
     * @param results the per-window metrics returned by analyze
     * @return a string array of algorithm names in recommended application order
     */
    public static String[] process(double[][] results) {
        int windowCount = results.length;
        if (windowCount == 0) return new String[0];

        double totalRleGain      = 0;
        double totalRleCoverage  = 0;
        double totalLz77Gain     = 0;
        double totalLz77Coverage = 0;
        double totalHuffGain     = 0;
        double totalEntropy      = 0;
        double totalDeltaGain    = 0;
        double totalDeltaAbsDelta = 0;

        for (double[] window : results) { //aggregate across all windows
            totalRleGain       += window[RLE_GAIN];
            totalRleCoverage   += window[RLE_COVERAGE];
            totalLz77Gain      += window[LZ77_GAIN];
            totalLz77Coverage  += window[LZ77_COVERAGE];
            totalHuffGain      += window[HUFF_GAIN];
            totalEntropy       += window[HUFF_ENTROPY];
            totalDeltaGain     += window[DELTA_GAIN];
            totalDeltaAbsDelta += window[DELTA_AVG_ABS];
        }

        double avgRleGain       = totalRleGain      / windowCount;
        double avgRleCoverage   = totalRleCoverage  / windowCount;
        double avgLz77Gain      = totalLz77Gain     / windowCount;
        double avgLz77Coverage  = totalLz77Coverage / windowCount;
        double avgHuffGain      = totalHuffGain     / windowCount;
        double avgEntropy       = totalEntropy      / windowCount;
        double avgDeltaGain     = totalDeltaGain    / windowCount;
        double avgDeltaAbsDelta = totalDeltaAbsDelta / windowCount;

        //decision logic
        boolean useRle     = avgRleGain    > 0 && avgRleCoverage   > 0.05;
        boolean useLz77    = avgLz77Gain   > 0 && avgLz77Coverage  > 0.05;
        boolean useHuffman = avgHuffGain   > 0 && avgEntropy       < 7.8;
        boolean useDelta   = avgDeltaGain  > 0 && avgDeltaAbsDelta < 64;

        //build sequence — delta first as a pre-transform, then structural, then entropy
        String[] sequence = new String[4];
        int count = 0;
        if (useDelta)   sequence[count++] = "DELTA";
        if (useRle)     sequence[count++] = "RLE";
        if (useLz77)    sequence[count++] = "LZ77";
        if (useHuffman) sequence[count++] = "HUFFMAN";

        return Arrays.copyOf(sequence, count);
    }

    /** this is the select method. it chains analyze and process to return a ready-to-use compression sequence
     *
     * @param data the raw byte array to analyze
     * @return a string array of algorithm names in recommended application order
     */
    public static String[] select(byte[] data) {
        return process(analyze(data));
    }
}
