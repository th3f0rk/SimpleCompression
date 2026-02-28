import java.util.Arrays;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class Lz77Encoder {
    private byte[] data;
    private int windowSize;
    private int minMatch;
    private int maxMatch;
    private int maxCandidates;

    private static final int TABLE_BITS = 13;
    private static final int TABLE_SIZE = 1 << TABLE_BITS;
    private static final int TABLE_MASK = TABLE_SIZE - 1;

    //VarHandle lets us read 8 bytes at once as a long for fast match extension
    private static final VarHandle LONG_VIEW =
        MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    public Lz77Encoder (byte[] data) {
        this(data, 8);
    }

    public Lz77Encoder (byte[] data, int maxCandidates) {
        this.data = data;
        this.windowSize = 4096;
        this.minMatch = 3;
        this.maxMatch = 255;
        this.maxCandidates = maxCandidates;
    }

    private int hash(int cursor) { //knuth multiplicative hash over 3 bytes into table index
        int v = (this.data[cursor] & 0xFF)
              | ((this.data[cursor + 1] & 0xFF) << 8)
              | ((this.data[cursor + 2] & 0xFF) << 16);
        return (v * 0x9E3779B1) >>> (32 - TABLE_BITS) & TABLE_MASK;
    }

    private int extendMatch(int candPos, int headStart, int limit) {
        int j = 0;
        while (j + 8 <= limit) { //compare 8 bytes at a time as a long
            long a = (long) LONG_VIEW.get(data, candPos + j);
            long b = (long) LONG_VIEW.get(data, headStart + j);
            if (a != b) {
                //find first differing byte within this long via trailing zero bits
                j += Long.numberOfTrailingZeros(a ^ b) >> 3;
                return j;
            }
            j += 8;
        }
        while (j < limit) { //byte by byte for the remaining tail
            if (data[candPos + j] != data[headStart + j]) break;
            j++;
        }
        return j;
    }

    public byte[] encode() {
        int cursor = 0;
        int tailStart;
        int tailEnd;
        int headStart;
        int headEnd;
        int maxEnd;
        int bestLength = 0;
        int bestDistance = 0;
        int candCounter = 0;
        int matchLength = 0;
        int matchDistance = 0;

        int bitMask = 0;
        int bitIndex = 0;
        int maskPosition = 0;
        int encodedCursor = 0;
        int step = 0;

        int slotSize = this.maxCandidates + 1;
        int[] index = new int[TABLE_SIZE * slotSize]; //flat 1D array, tighter layout for prefetcher

        byte[] encoded = new byte[this.data.length * 2];
        encoded[encodedCursor++] = 0;

        while (cursor < this.data.length) {
            if ((cursor - this.windowSize) > 0) { //keeps window in check as the loop progresses
                tailStart = cursor - this.windowSize;
            } else { //base case for the start of the loop
                tailStart = 0;
            }

            tailEnd = cursor; //tail is the look back window
            headStart = cursor; //head is the look ahead window
            maxEnd = headStart + this.maxMatch;

            if (maxEnd < this.data.length) {
                headEnd = maxEnd;
            } else {
                headEnd = this.data.length;
            }

            int slotBase = 0;
            if ((cursor + 2) < this.data.length) { //if at least 3 bytes left form trigram and lookup past positions within tail range
                slotBase = hash(cursor) * slotSize;
                candCounter = index[slotBase];
            } else { //not enough bytes left in input to form trigram
                candCounter = 0;
            }

            bestDistance = 0;
            bestLength = 0;

            int maxMatchable = headEnd - headStart; //hoisted constant, does not change per outer iteration

            for (int i = candCounter; i != 0; i--) { //reverse iterate through candidates
                int candPos = index[slotBase + i];
                if (candPos < tailStart) { //if candidate is outside of tail range we break
                    break;
                }
                if (data[candPos] != data[headStart]) continue; //fast bail on hash collision or mismatch
                int limit = Math.min(maxMatchable, tailEnd - candPos);
                matchLength = extendMatch(candPos, headStart, limit);
                matchDistance = cursor - candPos;

                if (matchLength > bestLength) { //tracks best match across all candidates
                    bestLength = matchLength;
                    bestDistance = matchDistance;
                    if (bestLength == this.maxMatch) break; //can't do better, early exit
                }
            }

            if (bestLength >= this.minMatch) { //we have a valid match
                bitMask |= (1 << bitIndex);
                encoded[encodedCursor++] = (byte) ((bestDistance >> 8) & 0xFF);
                encoded[encodedCursor++] = (byte) (bestDistance & 0xFF);
                encoded[encodedCursor++] = (byte) (bestLength & 0xFF);
                step = bestLength;
            } else { //we have a literal
                encoded[encodedCursor++] = (byte) (this.data[cursor] & 0xFF);
                step = 1;
            }

            bitIndex++;

            if (bitIndex == 8) { //if we fill the bitMask we write it and start again
                encoded[maskPosition] = (byte) bitMask;
                maskPosition = encodedCursor;
                encoded[encodedCursor++] = 0;
                bitMask = 0;
                bitIndex = 0;
            }

            if ((cursor + 2) < this.data.length) { //lazy insertion — only index cursor position, not entire match
                int count = index[slotBase];
                if (count < this.maxCandidates) {
                    count++;
                    index[slotBase + count] = cursor;
                    index[slotBase] = count;
                } else {
                    System.arraycopy(index, slotBase + 2, index, slotBase + 1, this.maxCandidates - 1);
                    index[slotBase + this.maxCandidates] = cursor;
                }
            }

            cursor += step;
        }
        encoded[maskPosition] = (byte) bitMask; //write bitMask that is remaining after loop finishes
        return Arrays.copyOf(encoded, encodedCursor); //convert to byte[] for output
    }
}
