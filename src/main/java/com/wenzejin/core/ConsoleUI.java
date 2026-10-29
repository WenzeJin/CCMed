package com.wenzejin.core;

/**
 * Console UI utilities — welcome banner and result summary.
 * All line-drawing uses plain ASCII so it renders correctly on any terminal.
 */
public final class ConsoleUI {

    // ── ANSI codes ──────────────────────────────────────────────────
    private static final String RST  = "\033[0m";
    private static final String BLD  = "\033[1m";
    private static final String DIM  = "\033[2m";
    private static final String GRN  = "\033[32m";
    private static final String RED  = "\033[31m";
    private static final String YLW  = "\033[33m";
    private static final String CYN  = "\033[36m";

    private static final String BAR  = "──────────────────────────────────────────────────────";

    // ── Welcome ─────────────────────────────────────────────────────

    public static void printWelcome(Config config, String configPath) {
        String version = Config.class.getPackage().getImplementationVersion();
        if (version == null) version = "1.2.0";

        System.out.println();
        System.out.println(BLD + CYN + "  CCMed v" + version + RST);
        System.out.println("  " + DIM + configPath + "  →  " + config.getTargetPath() + RST);
        System.out.println();
    }

    // ── Result ──────────────────────────────────────────────────────

    /**
     * Print the final result summary.
     *
     * @param sourceFailed     true if some source cases did not succeed
     * @param totalSource      total source cases tested
     * @param failedSource     number of source cases that failed
     * @param totalFollowUp    total follow-up cases tested (0 if source failed)
     * @param failedFollowUp   number of follow-up cases that failed
     * @param assertionFails   count of assertion failures among failed cases
     * @param crashes          count of crashed cases
     * @param timeouts         count of timed-out cases
     * @param invalidOutputs   count of invalid-output cases
     * @param totalTimeMs      total wall-clock time in ms
     * @param workspace        workspace directory path
     */
    public static void printResult(
            boolean sourceFailed,
            int totalSource, int failedSource,
            int totalFollowUp, int failedFollowUp,
            int assertionFails, int crashes, int timeouts, int invalidOutputs,
            long totalTimeMs, String workspace) {

        boolean passed = !sourceFailed && failedFollowUp == 0;

        System.out.println();

        // ── Verdict banner ───────────────────────────────────────
        String verdict;
        String color;
        if (sourceFailed) {
            verdict = "SOURCE FAIL";
            color  = YLW;
        } else if (passed) {
            verdict = "PASS";
            color  = GRN;
        } else {
            verdict = "FAIL";
            color  = RED;
        }

        System.out.println("  " + BAR);
        System.out.print("  ");
        System.out.print(BLD + color);
        System.out.print(center("TEST RESULT: " + verdict, 52));
        System.out.println(RST);
        System.out.println("  " + BAR);
        System.out.println();

        // ── Failure breakdown ────────────────────────────────────
        int totalFailed = failedSource + failedFollowUp;

        if (totalFailed > 0) {
            System.out.println(BLD + "  Failures:       " + totalFailed + RST);

            if (assertionFails > 0) treeRow(false, "Assertion",   String.valueOf(assertionFails), YLW);
            if (crashes > 0)        treeRow(false, "Crashed",     String.valueOf(crashes),        RED);
            if (timeouts > 0)       treeRow(false, "Timeout",     String.valueOf(timeouts),       RED);
            if (invalidOutputs > 0) treeRow(invalidOutputs == totalFailed
                    && assertionFails == 0 && crashes == 0 && timeouts == 0,
                    "Invalid output", String.valueOf(invalidOutputs), RED);

            if (sourceFailed) {
                System.out.println();
                System.out.println(DIM + "  Source cases failed — follow-up tests were skipped." + RST);
            }
            System.out.println();
        }

        // ── Case statistics ──────────────────────────────────────
        row("Source cases",
                passedCount(totalSource - failedSource, totalSource)
                + (failedSource > 0 ? RED + "  " + failedSource + " failed" + RST : ""));
        if (totalFollowUp > 0) {
            row("Follow-up cases",
                    passedCount(totalFollowUp - failedFollowUp, totalFollowUp)
                    + (failedFollowUp > 0 ? RED + "  " + failedFollowUp + " failed" + RST : ""));
        }

        // ── Timing & output ──────────────────────────────────────
        double seconds = totalTimeMs / 1000.0;
        row("Total time", String.format("%.1f s", seconds));
        row("Result saved to", workspace + "/result.json");

        System.out.println();
        System.out.println(DIM + "  " + BAR + RST);
        System.out.println();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static void row(String label, String value) {
        System.out.println("  " + BLD + fixed(label, 14) + RST + " " + DIM + value + RST);
    }

    private static void treeRow(boolean last, String label, String value, String color) {
        String prefix = last ? "  └─ " : "  ├─ ";
        System.out.println(prefix + BLD + fixed(label, 14) + RST + " " + color + value + RST);
    }

    private static String passedCount(int passed, int total) {
        return GRN + passed + " passed" + RST + DIM + " / " + total + RST;
    }

    private static String fixed(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String center(String s, int width) {
        if (s.length() >= width) return s;
        int pad = (width - s.length()) / 2;
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < pad; i++) sb.append(' ');
        sb.append(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }
}
