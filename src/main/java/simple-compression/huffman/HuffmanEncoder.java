import java.util.Arrays;

public class HuffmanEncoder {

    private HuffmanEncoder() {}

    //node indices 0-255 are leaves, 256-510 are internal nodes
    private static final int MAX_NODES = 511;

    /** this is the encode method. it does canonical Huffman compression.
     *
     * @param data the raw byte array to compress
     * @return a Huffman encoded byte array
     */
    public static byte[] encode(byte[] data) {
        int[] freq = new int[MAX_NODES]; //flat frequency array indexed by symbol value
        int symbolCount = 0;

        if (data.length == 0) {
            throw new IllegalArgumentException("there is no data to compress. the bytearray passed is empty.");
        }

        for (int i = 0; i < data.length; i++) { //build frequency table
            int sym = data[i] & 0xFF;
            if (freq[sym] == 0) symbolCount++;
            freq[sym]++;
        }

        long[] heap = new long[MAX_NODES + 1]; //1-indexed min-heap
        int heapSize = 0;
        for (int i = 0; i < 256; i++) { //load active symbols into the heap
            if (freq[i] > 0) heap[++heapSize] = ((long) freq[i] << 10) | i;
        }
        for (int i = heapSize / 2; i >= 1; i--) siftDown(heap, i, heapSize); //heapify

        //child arrays, -1 means leaf
        int[] leftChild  = new int[MAX_NODES];
        int[] rightChild = new int[MAX_NODES];
        Arrays.fill(leftChild,  -1);
        Arrays.fill(rightChild, -1);

        int nextInternal = 256; //next available internal node slot

        while (heapSize > 1) { //merge lowest frequency nodes into tree
            long entryA = heapPoll(heap, heapSize--);
            long entryB = heapPoll(heap, heapSize--);
            int nodeA = (int)(entryA & 0x3FF);
            int nodeB = (int)(entryB & 0x3FF);
            int combined = freq[nodeA] + freq[nodeB];
            freq[nextInternal] = combined;
            leftChild[nextInternal]  = nodeA;
            rightChild[nextInternal] = nodeB;
            heap[++heapSize] = ((long) combined << 10) | nextInternal;
            siftUp(heap, heapSize);
            nextInternal++;
        }

        int root = (int)(heap[1] & 0x3FF); //last node remaining is the root

        int[] codeLen = new int[256]; //code bit-lengths per symbol, populated by walk

        if (symbolCount == 1) { //single unique symbol, force code length 1
            codeLen[root] = 1; //root index is the symbol
        } else {
            walk(root, 0, leftChild, rightChild, codeLen);
        }

        int[] pairLen = new int[symbolCount];
        int[] pairSym = new int[symbolCount];
        int pairCount = 0;
        for (int i = 0; i < 256; i++) { //collect (length, symbol) pairs
            if (codeLen[i] > 0) {
                pairLen[pairCount] = codeLen[i];
                pairSym[pairCount] = i;
                pairCount++;
            }
        }
        sortPairs(pairLen, pairSym, pairCount); //sort pairs for canonical assignment

        int[] codeWord = new int[256];
        int currentCode = 0;
        int previousLen = 0;
        for (int i = 0; i < pairCount; i++) { //assign canonical codes
            int len = pairLen[i];
            int sym = pairSym[i];
            if (len > previousLen) {
                currentCode <<= (len - previousLen);
            }
            codeWord[sym] = currentCode;
            codeLen[sym]  = len;
            currentCode++;
            previousLen = len;
        }

        byte[] encodedBuf = new byte[data.length * 4 + 64];
        int encodedPos = 0;
        long outBits    = 0;
        int  outBitsLen = 0;
        int  outLen     = 0; //total bits written

        for (int i = 0; i < data.length; i++) { //encode data
            int sym  = data[i] & 0xFF;
            int len  = codeLen[sym];
            int word = codeWord[sym];
            outBits    = (outBits << len) | word;
            outBitsLen += len;
            outLen     += len;

            while (outBitsLen >= 8) { //flush full bytes
                outBitsLen -= 8;
                encodedBuf[encodedPos++] = (byte)((outBits >> outBitsLen) & 0xFF);
                outBits &= (1L << outBitsLen) - 1;
            }
        }

        if (outBitsLen > 0) { //flush remaining bits with zero padding
            encodedBuf[encodedPos++] = (byte)((outBits << (8 - outBitsLen)) & 0xFF);
        }

        byte[] out = new byte[2 + symbolCount * 2 + 4 + encodedPos];
        int pos = 0;

        //symbol count
        out[pos++] = (byte)((symbolCount >> 8) & 0xFF);
        out[pos++] = (byte)(symbolCount & 0xFF);

        //header
        for (int i = 0; i < pairCount; i++) {
            out[pos++] = (byte) codeLen[pairSym[i]];
            out[pos++] = (byte) pairSym[i];
        }

        //bit length
        out[pos++] = (byte)((outLen >> 24) & 0xFF);
        out[pos++] = (byte)((outLen >> 16) & 0xFF);
        out[pos++] = (byte)((outLen >> 8)  & 0xFF);
        out[pos++] = (byte)(outLen & 0xFF);

        //packed bits
        System.arraycopy(encodedBuf, 0, out, pos, encodedPos);

        return out;
    }

    /** this is the walk method. it recursively visits each node and records the depth at each leaf as the code length
     *
     * @param node       the current node index
     * @param depth      current depth in the tree, equal to the code length at a leaf
     * @param leftChild  left child index array
     * @param rightChild right child index array
     * @param codeLen    output array populated with each symbol's code length
     */
    private static void walk(int node, int depth, int[] leftChild, int[] rightChild, int[] codeLen) {
        if (leftChild[node] == -1) { //leaf node
            codeLen[node] = depth;
            return;
        }
        walk(leftChild[node],  depth + 1, leftChild, rightChild, codeLen);
        walk(rightChild[node], depth + 1, leftChild, rightChild, codeLen);
    }

    /** this is the heap poll method. it removes and returns the minimum element
     *
     * @param heap the heap array
     * @param size the current heap size
     * @return the minimum element
     */
    private static long heapPoll(long[] heap, int size) {
        long min = heap[1];
        heap[1] = heap[size];
        siftDown(heap, 1, size - 1);
        return min;
    }

    private static void siftDown(long[] heap, int i, int size) {
        while (true) {
            int smallest = i;
            int l = i << 1, r = l | 1;
            if (l <= size && heap[l] < heap[smallest]) smallest = l;
            if (r <= size && heap[r] < heap[smallest]) smallest = r;
            if (smallest == i) break;
            long tmp = heap[i]; heap[i] = heap[smallest]; heap[smallest] = tmp;
            i = smallest;
        }
    }

    private static void siftUp(long[] heap, int i) {
        while (i > 1 && heap[i] < heap[i >> 1]) {
            long tmp = heap[i]; heap[i] = heap[i >> 1]; heap[i >> 1] = tmp;
            i >>= 1;
        }
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
