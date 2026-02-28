import java.util.Arrays;

public class RleEncoder {

    private byte[] data;

    public RleEncoder (byte[] data) {
        this.data = data;
    }

    /** this is the encode method. it does PackBits style RLE encoding
     *
     * @return encoded - returns an RLE encoded byte array
     */
    public byte[] encode() {
        int originalBytes = this.data.length;
        int runCount = 0;
        int literalCount = 0;
        int tail = 0;

        boolean isRunning = false;

        byte[] out = new byte[this.data.length + (this.data.length / 128) + 3];
        int pos = 0;
        byte[] slip = new byte[128];

        if (this.data.length == 0) {
            throw new IllegalArgumentException("there is no data to compress. the bytearray passed is empty.");
        }

        for (int i = 0; i < this.data.length; i++) {
            boolean lastPass = (i == originalBytes - 1);
            byte current = this.data[i];

            if (i == 0) {
                slip[0] = current;
                literalCount = 1;
                if (lastPass) {
                    out[pos++] = (byte) 0;
                    out[pos++] = current;
                }
                continue;
            }

            //guards against literalCount integer overflow
            if (!isRunning && literalCount == 128) {
                out[pos++] = (byte) (literalCount - 1);
                System.arraycopy(slip, 0, out, pos, 128);
                pos += 128;
                tail = 0;
                slip[0] = current;
                literalCount = 1;
                runCount = 0;
                if (lastPass) {
                    out[pos++] = (byte) 0;
                    out[pos++] = current;
                }
                continue;
            }

            //guards against runCount integer overflow
            if (isRunning && runCount == 128) {
                out[pos++] = (byte) -(runCount - 1);
                out[pos++] = slip[0];
                tail = 0;
                slip[0] = current;
                literalCount = 1;
                runCount = 0;
                isRunning = false;
                if (lastPass) {
                    out[pos++] = (byte) 0;
                    out[pos++] = current;
                }
                continue;
            }

            if (isRunning) { //we have a real run
                if (current == slip[tail]) {
                    int remaining = Math.min(128 - runCount, originalBytes - i);
                    int j = 0;
                    while (j < remaining && this.data[i + j] == slip[tail]) { j++; }
                    runCount += j;
                    i += j - 1;
                    lastPass = (i == originalBytes - 1);
                } else { //run is done now
                    out[pos++] = (byte) -(runCount - 1);
                    out[pos++] = slip[tail];
                    tail = 0;
                    slip[0] = current;
                    literalCount = 1;
                    runCount = 0;
                    isRunning = false;
                }
            } else { //buffering literals and watching for runs to form
                if (current == slip[tail] && runCount < 2) { //distinguishes short runs
                    runCount++;
                    tail++;
                    slip[tail] = current;
                    literalCount++; //if run busts early then stays literal till it hits 128 limit or run comes
                } else if (current == slip[tail] && runCount >= 2) { //we now have a real run
                    tail -= 2;
                    literalCount -= 2;
                    if (literalCount > 0) { //adds the header flag indicating this is a slip of literals
                        out[pos++] = (byte) (literalCount - 1);
                        System.arraycopy(slip, 0, out, pos, tail + 1);
                        pos += tail + 1;
                    }
                    tail = 0;
                    slip[0] = current;
                    runCount++;
                    isRunning = true;
                    literalCount = 0;

                    int remaining = Math.min(128 - runCount, originalBytes - 1 - i);
                    int j = 0;
                    while (j < remaining && this.data[i + 1 + j] == current) { j++; }
                    runCount += j;
                    i += j;
                    lastPass = (i == originalBytes - 1);
                } else { //works for all literals and terminates short runs
                    tail++;
                    slip[tail] = current;
                    literalCount++;
                    runCount = 0;
                }
            }

            if (lastPass) { //last pass logic
                if (isRunning && runCount > 0) { //ends active runs
                    out[pos++] = (byte) -(runCount - 1);
                    out[pos++] = slip[tail];
                } else if (!isRunning && literalCount > 0) { //ends active literals
                    out[pos++] = (byte) (literalCount - 1);
                    System.arraycopy(slip, 0, out, pos, tail + 1);
                    pos += tail + 1;
                }
            }
        }
        return Arrays.copyOf(out, pos);
    }
}
