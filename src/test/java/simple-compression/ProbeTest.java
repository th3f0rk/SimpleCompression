import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class ProbeTest {

    //helper: single-window metrics array in metric-index order
    //RLE_GAIN=0, RLE_COVERAGE=1, LZ77_GAIN=2, LZ77_COVERAGE=3, HUFF_GAIN=4, HUFF_ENTROPY=5, DELTA_GAIN=6, DELTA_AVG_ABS=7
    private static double[][] win (double rleGain, double rleCov, double lz77Gain, double lz77Cov,
                                   double huffGain, double huffEntropy, double deltaGain, double deltaAvgAbs) {
        return new double[][]{{rleGain, rleCov, lz77Gain, lz77Cov, huffGain, huffEntropy, deltaGain, deltaAvgAbs}};
    }

    //--- process: empty and zero ---

    @Test
    public void processEmptyWindowsReturnsEmpty () {
        assertEquals(0, Probe.process(new double[0][0]).length);
    }

    @Test
    public void processAllZeroMetricsReturnsEmpty () {
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 0, 0, 0, 0)).length);
    }

    //--- process: individual algorithm selection ---

    @Test
    public void processSelectsRle () {
        String[] result = Probe.process(win(10, 0.2, 0, 0, 0, 8, 0, 100));
        assertArrayEquals(new String[]{"RLE"}, result);
    }

    @Test
    public void processDoesNotSelectRleWhenGainNotPositive () {
        assertEquals(0, Probe.process(win(0, 0.5, 0, 0, 0, 8, 0, 100)).length);
    }

    @Test
    public void processDoesNotSelectRleWhenGainNegative () {
        assertEquals(0, Probe.process(win(-5, 0.5, 0, 0, 0, 8, 0, 100)).length);
    }

    @Test
    public void processDoesNotSelectRleWhenCoverageTooLow () {
        assertEquals(0, Probe.process(win(10, 0.03, 0, 0, 0, 8, 0, 100)).length);
    }

    @Test
    public void processSelectsLz77 () {
        String[] result = Probe.process(win(0, 0, 15, 0.3, 0, 8, 0, 100));
        assertArrayEquals(new String[]{"LZ77"}, result);
    }

    @Test
    public void processDoesNotSelectLz77WhenGainNotPositive () {
        assertEquals(0, Probe.process(win(0, 0, 0, 0.5, 0, 8, 0, 100)).length);
    }

    @Test
    public void processDoesNotSelectLz77WhenCoverageTooLow () {
        assertEquals(0, Probe.process(win(0, 0, 15, 0.04, 0, 8, 0, 100)).length);
    }

    @Test
    public void processSelectsHuffman () {
        String[] result = Probe.process(win(0, 0, 0, 0, 20, 5.0, 0, 100));
        assertArrayEquals(new String[]{"HUFFMAN"}, result);
    }

    @Test
    public void processDoesNotSelectHuffmanWhenGainNotPositive () {
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 0, 5.0, 0, 100)).length);
    }

    @Test
    public void processDoesNotSelectHuffmanWhenEntropyTooHigh () {
        //entropy=7.9 fails the < 7.8 threshold
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 20, 7.9, 0, 100)).length);
    }

    @Test
    public void processSelectsDelta () {
        String[] result = Probe.process(win(0, 0, 0, 0, 0, 8, 5, 10));
        assertArrayEquals(new String[]{"DELTA"}, result);
    }

    @Test
    public void processDoesNotSelectDeltaWhenGainNotPositive () {
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 0, 8, 0, 10)).length);
    }

    @Test
    public void processDoesNotSelectDeltaWhenAvgAbsTooHigh () {
        //avgAbsDelta=70 fails the < 64 threshold
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 0, 8, 5, 70)).length);
    }

    //--- process: sequence ordering and combinations ---

    @Test
    public void processSequenceOrderIsDeltaRleLz77Huffman () {
        String[] result = Probe.process(win(10, 0.2, 15, 0.3, 20, 5.0, 5, 10));
        assertArrayEquals(new String[]{"DELTA", "RLE", "LZ77", "HUFFMAN"}, result);
    }

    @Test
    public void processSequenceDeltaAndHuffmanOnly () {
        //rle and lz77 gains are zero, only delta and huffman qualify
        String[] result = Probe.process(win(0, 0, 0, 0, 20, 5.0, 5, 10));
        assertArrayEquals(new String[]{"DELTA", "HUFFMAN"}, result);
    }

    @Test
    public void processSequenceRleAndLz77Only () {
        String[] result = Probe.process(win(10, 0.2, 15, 0.3, 0, 8, 0, 100));
        assertArrayEquals(new String[]{"RLE", "LZ77"}, result);
    }

    //--- process: boundary conditions ---

    @Test
    public void processBoundaryRleCoverageExactly005 () {
        //> 0.05 threshold means exactly 0.05 does NOT qualify
        assertEquals(0, Probe.process(win(10, 0.05, 0, 0, 0, 8, 0, 100)).length);
    }

    @Test
    public void processBoundaryLz77CoverageExactly005 () {
        assertEquals(0, Probe.process(win(0, 0, 10, 0.05, 0, 8, 0, 100)).length);
    }

    @Test
    public void processBoundaryHuffmanEntropyExactly78 () {
        //< 7.8 threshold means exactly 7.8 does NOT qualify
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 20, 7.8, 0, 100)).length);
    }

    @Test
    public void processBoundaryDeltaAvgAbsExactly64 () {
        //< 64 threshold means exactly 64 does NOT qualify
        assertEquals(0, Probe.process(win(0, 0, 0, 0, 0, 8, 5, 64)).length);
    }

    //--- process: multi-window aggregation ---

    @Test
    public void processAggregatesAcrossMultipleWindows () {
        //window1 rleGain=10, window2 rleGain=-4 → avg=3.0 > 0; coverage avg=0.15 > 0.05 → RLE selected
        double[][] metrics = {
            {10.0, 0.20, 0, 0, 0, 8, 0, 100},
            {-4.0, 0.10, 0, 0, 0, 8, 0, 100}
        };
        assertArrayEquals(new String[]{"RLE"}, Probe.process(metrics));
    }

    @Test
    public void processNegativeAverageExcludesAlgorithm () {
        //avg rleGain = (-10 + -5) / 2 = -7.5 → RLE not selected
        double[][] metrics = {
            {-10.0, 0.5, 0, 0, 0, 8, 0, 100},
            { -5.0, 0.5, 0, 0, 0, 8, 0, 100}
        };
        assertEquals(0, Probe.process(metrics).length);
    }

    //--- analyze: structural ---

    @Test
    public void analyzeSingleByteReturnsEmpty () {
        //loop condition i < dataLen-1 = 0 is immediately false
        assertEquals(0, Probe.analyze(new byte[]{0x42}).length);
    }

    @Test
    public void analyzeTwoBytesReturnsOneWindow () {
        //dataLen=2, maxSplit=1, loop runs once
        double[][] result = Probe.analyze(new byte[]{0x41, 0x41});
        assertEquals(1, result.length);
    }

    @Test
    public void analyzeReturnsDimensionedArray () {
        byte[] data = new byte[100];
        Arrays.fill(data, (byte) 0x41);
        double[][] result = Probe.analyze(data);
        assertTrue(result.length > 0);
        assertEquals(8, result[0].length); //METRIC_COUNT=8
    }

    @Test
    public void analyzeDoesNotReturnMoreWindowsThanDataLen () {
        //sanity: window count is bounded by data length
        byte[] data = new byte[50];
        Arrays.fill(data, (byte) 0x41);
        assertTrue(Probe.analyze(data).length <= data.length);
    }

    //--- analyze: metric values ---

    @Test
    public void analyzeRepetitiveDataHasPositiveRleGain () {
        //1000 identical bytes: every window has a long run, RLE gain is clearly positive
        byte[] data = new byte[1000];
        Arrays.fill(data, (byte) 0x41);
        double[][] result = Probe.analyze(data);
        double total = 0;
        for (double[] row : result) total += row[0]; //RLE_GAIN=0
        assertTrue(total > 0);
    }

    @Test
    public void analyzeAscendingDataHasSmallDeltaAvgAbs () {
        //step-1 ascent: delta magnitude per sample is 1.0
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        double[][] result = Probe.analyze(data);
        assertTrue(result.length > 0);
        for (double[] row : result) assertTrue(row[7] < 5.0); //DELTA_AVG_ABS=7, expected ~1.0
    }

    @Test
    public void analyzeIdenticalBytesHasZeroEntropy () {
        //single symbol → entropy is 0 (p=1, -1*log2(1)=0)
        byte[] data = new byte[1000];
        Arrays.fill(data, (byte) 0x41);
        double[][] result = Probe.analyze(data);
        for (double[] row : result) assertEquals(0.0, row[5], 1e-9); //HUFF_ENTROPY=5
    }

    //--- select: integration ---

    @Test
    public void selectDoesNotCrash () {
        String[] seq = Probe.select("the quick brown fox jumps over the lazy dog".getBytes());
        assertNotNull(seq);
    }

    @Test
    public void selectReturnedAlgorithmsAreAllValid () {
        byte[] data = new byte[500];
        Arrays.fill(data, (byte) 0x41);
        for (String alg : Probe.select(data)) {
            assertTrue(alg.equals("RLE") || alg.equals("LZ77") || alg.equals("HUFFMAN") || alg.equals("DELTA"),
                       "unexpected algorithm: " + alg);
        }
    }

    @Test
    public void selectHighRepetitionDataSelectsAtLeastOneAlgo () {
        //500 identical bytes should make at least one algorithm worthwhile
        byte[] data = new byte[500];
        Arrays.fill(data, (byte) 0x41);
        assertTrue(Probe.select(data).length > 0);
    }

    @Test
    public void selectSingleByteReturnsEmpty () {
        //analyze returns no windows → process returns [] → select returns []
        assertEquals(0, Probe.select(new byte[]{0x42}).length);
    }

    @Test
    public void selectResultIsUsableByEncode () {
        //the sequence returned by select must work directly as input to encode and survive a decode
        byte[] data = "hello world hello world hello world".getBytes();
        String[] seq = Probe.select(data);
        byte[] encoded = SimpleCompression.encode(data, seq);
        assertArrayEquals(data, SimpleCompression.decode(encoded));
    }

    @Test
    public void selectEmptySequenceOnTinyData () {
        //2 bytes: too small to gain from any algorithm, likely returns empty sequence
        //either way, encode+decode must still roundtrip
        byte[] data = {0x41, 0x42};
        String[] seq = Probe.select(data);
        assertNotNull(seq);
        byte[] encoded = SimpleCompression.encode(data, seq);
        assertArrayEquals(data, SimpleCompression.decode(encoded));
    }
}
