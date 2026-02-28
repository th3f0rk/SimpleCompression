import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/** this is the Canterbury benchmark. it runs the full SimpleCompression auto pipeline on each
 * file in the Canterbury corpus and reports compression ratio, probe time, encode time, decode
 * time, total time per file, encode throughput, decode throughput, and the algorithm sequence
 * selected by the probe.
 *
 * probe, encode, and decode are timed separately by calling Probe.select() and
 * SimpleCompression.encode(data, seq) directly rather than the combined auto-mode overload.
 * throughput is computed from nanosecond timings so sub-millisecond operations are accurate.
 *
 * usage: java -cp bin/main:bin/benchmark Benchmark [corpus-dir]
 * default corpus-dir is benchmark/corpus (run from repo root)
 */
public class Benchmark {

    //format strings — two rows per file: compression stats then timing stats
    private static final String FMT_COMP_HDR = "%-16s  %11s  %11s  %7s  %s";
    private static final String FMT_COMP_ROW = "%-16s  %,11d  %,11d  %7.4f  %s";
    private static final String FMT_TIME_HDR = "%-16s  %10s  %8s  %8s  %10s  %10s  %10s";
    private static final String FMT_TIME_ROW = "%-16s  %10s  %8s  %8s  %10s  %10s  %10s";

    public static void main(String[] args) throws IOException {
        String corpusDir = args.length > 0 ? args[0] : "benchmark/corpus";

        File dir = new File(corpusDir);
        File[] files = dir.listFiles(File::isFile);

        if (files == null || files.length == 0) {
            System.err.println("no files found in: " + corpusDir);
            System.err.println("run benchmark/run.sh to download the Canterbury corpus first.");
            System.exit(1);
        }

        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));

        //pre-run totals
        long totalOrig    = 0;
        long totalComp    = 0;
        long totalProbeNs = 0;
        long totalEncNs   = 0;
        long totalDecNs   = 0;
        boolean allOk     = true;

        //per-file result storage so we print all at once after running
        int n = files.length;
        String[]  names    = new String[n];
        long[]    origs    = new long[n];
        long[]    comps    = new long[n];
        long[]    probeNs  = new long[n];
        long[]    encNs    = new long[n];
        long[]    decNs    = new long[n];
        String[]  algs     = new String[n];
        boolean[] ok       = new boolean[n];

        for (int i = 0; i < n; i++) {
            File f      = files[i];
            byte[] data = Files.readAllBytes(f.toPath());

            long t0       = System.nanoTime();
            String[] seq  = Probe.select(data);      //timed separately from encode
            probeNs[i]    = System.nanoTime() - t0;

            long t1       = System.nanoTime();
            byte[] enc    = SimpleCompression.encode(data, seq);
            encNs[i]      = System.nanoTime() - t1;

            long t2       = System.nanoTime();
            byte[] dec    = SimpleCompression.decode(enc);
            decNs[i]      = System.nanoTime() - t2;

            names[i]  = f.getName();
            origs[i]  = data.length;
            comps[i]  = enc.length;
            algs[i]   = seq.length > 0 ? String.join("+", seq) : "(none)";
            ok[i]     = Arrays.equals(data, dec);
            if (!ok[i]) allOk = false;

            totalOrig    += data.length;
            totalComp    += enc.length;
            totalProbeNs += probeNs[i];
            totalEncNs   += encNs[i];
            totalDecNs   += decNs[i];
        }

        //--- compression table ---
        String compHdr = String.format(FMT_COMP_HDR, "file", "original", "compressed", "ratio", "algorithms");
        String compSep = "-".repeat(compHdr.length());

        System.out.println();
        System.out.println("  compression results");
        System.out.println("  " + compSep);
        System.out.println("  " + compHdr);
        System.out.println("  " + compSep);

        for (int i = 0; i < n; i++) {
            double ratio = (double) comps[i] / origs[i];
            String flag  = ok[i] ? "" : "  MISMATCH";
            System.out.println("  " + String.format(FMT_COMP_ROW,
                    names[i], origs[i], comps[i], ratio, algs[i] + flag));
        }

        System.out.println("  " + compSep);
        System.out.println("  " + String.format(FMT_COMP_ROW,
                "TOTAL", totalOrig, totalComp, (double) totalComp / totalOrig, ""));

        //--- timing table ---
        String timeHdr = String.format(FMT_TIME_HDR,
                "file", "probe(ms)", "enc(ms)", "dec(ms)", "total(ms)", "enc MB/s", "dec MB/s");
        String timeSep = "-".repeat(timeHdr.length());

        System.out.println();
        System.out.println("  timing & throughput  (enc MB/s = input / encode time,  dec MB/s = output / decode time)");
        System.out.println("  " + timeSep);
        System.out.println("  " + timeHdr);
        System.out.println("  " + timeSep);

        for (int i = 0; i < n; i++) {
            long totalMs = (probeNs[i] + encNs[i] + decNs[i]) / 1_000_000;
            System.out.println("  " + String.format(FMT_TIME_ROW,
                    names[i],
                    fmtMs(probeNs[i]),
                    fmtMs(encNs[i]),
                    fmtMs(decNs[i]),
                    totalMs + " ms",
                    fmtMBs(origs[i], encNs[i]),
                    fmtMBs(origs[i], decNs[i])));
        }

        System.out.println("  " + timeSep);

        long grandTotalMs = (totalProbeNs + totalEncNs + totalDecNs) / 1_000_000;
        System.out.println("  " + String.format(FMT_TIME_ROW,
                "TOTAL",
                fmtMs(totalProbeNs),
                fmtMs(totalEncNs),
                fmtMs(totalDecNs),
                grandTotalMs + " ms",
                fmtMBs(totalOrig, totalEncNs),
                fmtMBs(totalOrig, totalDecNs)));

        System.out.println();

        if (!allOk) {
            System.err.println("WARNING: one or more files failed the roundtrip check.");
            System.exit(1);
        }
    }

    /** formats nanoseconds as a millisecond string with one decimal place.
     *
     * @param nanos elapsed nanoseconds
     * @return formatted string like "12.3 ms"
     */
    private static String fmtMs(long nanos) {
        return String.format("%.1f ms", nanos / 1_000_000.0);
    }

    /** computes and formats throughput in MB/s.
     * returns ">9999" for sub-microsecond operations to avoid noise from timer resolution.
     *
     * @param bytes number of bytes processed
     * @param nanos elapsed nanoseconds
     * @return formatted string like "234.5 MB/s" or ">9999 MB/s"
     */
    private static String fmtMBs(long bytes, long nanos) {
        if (nanos < 1_000) return ">9999 MB/s"; //less than 1 µs — timing noise
        double mbs = (bytes / 1_000_000.0) / (nanos / 1_000_000_000.0);
        if (mbs >= 9999.5) return ">9999 MB/s";
        return String.format("%.1f MB/s", mbs);
    }
}
