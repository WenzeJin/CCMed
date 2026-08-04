package com.wenzejin.core;

public class CLIProgressListener implements ProgressListener {

    // ── ANSI escape codes ────────────────────────────────────────────
    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String DIM    = "\033[2m";
    private static final String GREEN  = "\033[32m";
    private static final String CYAN   = "\033[36m";
    private static final String YELLOW = "\033[33m";

    // ── Layout constants ─────────────────────────────────────────────
    /** Maximum displayed length of the task name. */
    private static final int TASK_MAX  = 20;
    /** Width of the percentage field ("  0%" .. "100%"). */
    private static final int PCT_WIDTH = 4;
    /** Fixed width for the extra-message column. */
    private static final int EXTRA_MAX = 28;

    // ── 1/8-block characters for smooth partial fills ────────────────
    private static final char[] BLOCKS = {
        ' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█'
    };

    // ── Spinner for indeterminate mode ───────────────────────────────
    private static final char[] SPINNER = {
        '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'
    };

    // ── Instance state ───────────────────────────────────────────────
    private int     totalSteps;
    private int     currentStep;
    private String  taskName;
    private int     barCells;       // computed from terminal width
    private boolean started;
    private int     spinnerIdx;

    // ── Helpers ──────────────────────────────────────────────────────

    private static int detectTerminalWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try { return Integer.parseInt(columns); } catch (NumberFormatException ignored) {}
        }
        try {
            Process p = new ProcessBuilder("stty", "size")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split(" ");
            if (parts.length >= 2) return Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        try {
            Process p = new ProcessBuilder("tput", "cols")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !out.isEmpty()) return Integer.parseInt(out);
        } catch (Exception ignored) {}
        return 120;
    }

    /** Pad or truncate `s` to exactly `width` characters. */
    private static String fixedWidth(String s, int width) {
        if (s.length() > width) return s.substring(0, width);
        return String.format("%-" + width + "s", s);
    }

    // ── ProgressListener implementation ──────────────────────────────

    @Override
    public synchronized void onStart(int totalSteps, String taskName) {
        this.totalSteps  = totalSteps;
        this.currentStep = 0;
        this.taskName    = taskName;
        this.started     = true;
        this.spinnerIdx  = 0;

        // task(20) + " ["(2) + " ]"(2) + " "(1) + pct(5) + " │ "(3) + extra(EXTRA_MAX)
        int fixed = TASK_MAX + 2 + 2 + 1 + (PCT_WIDTH + 1) + 3 + EXTRA_MAX;
        this.barCells = Math.max(12, detectTerminalWidth() - fixed);

        render();
    }

    @Override
    public synchronized void onStep(int steps, String message) {
        if (!started) return;
        currentStep += steps;
        render(message);
    }

    @Override
    public synchronized void onFinish(String message) {
        if (!started) return;
        System.out.print("\r\033[K");
        if (message != null) {
            System.out.println(message);
        } else {
            String bar = BOLD + GREEN + fillBar(barCells * 8) + RESET;
            String pct = BOLD + GREEN + "100%" + RESET;
            String name = fixedWidth(truncateTaskName(), TASK_MAX);
            System.out.println(name + " [" + bar + "] " + pct);
        }
        started = false;
    }

    @Override
    public synchronized void onError(String errorMessage) {
        if (started) {
            System.out.print("\r\033[K");
            started = false;
        }
        System.err.println("Error: " + errorMessage);
    }

    // ── Rendering ────────────────────────────────────────────────────

    private void render() {
        render(null);
    }

    private void render(String message) {
        spinnerIdx++;

        String name = BOLD + fixedWidth(truncateTaskName(), TASK_MAX) + RESET;

        String bar;
        if (totalSteps > 0) {
            bar = renderDeterminateBar();
        } else {
            bar = renderIndeterminateBar();
        }

        String pct;
        if (totalSteps > 0) {
            int p = (int) (currentStep * 100.0 / totalSteps);
            if (p > 100) p = 100;
            pct = CYAN + String.format("%" + PCT_WIDTH + "d", p) + "%" + RESET;
        } else {
            char spin = SPINNER[spinnerIdx % SPINNER.length];
            pct = CYAN + " " + spin + "  " + RESET;
        }

        String extra = (message != null && !message.isEmpty())
                ? YELLOW + fixedWidth(message, EXTRA_MAX) + RESET
                : fixedWidth("", EXTRA_MAX);

        // name [bar] pct% │ extra
        String line = "\r" + name + " [" + bar + "] " + pct + " │ " + extra + "\033[K";
        System.out.print(line);
        System.out.flush();
    }

    // ── Bar rendering ────────────────────────────────────────────────

    private String renderDeterminateBar() {
        double fraction = (totalSteps <= 0) ? 0 : (double) currentStep / totalSteps;
        if (fraction > 1.0) fraction = 1.0;
        if (fraction < 0.0) fraction = 0.0;

        int totalTicks = barCells * 8;
        int filledTicks = (int) (fraction * totalTicks);
        return fillBar(filledTicks);
    }

    private String fillBar(int filledTicks) {
        StringBuilder sb = new StringBuilder(barCells);
        for (int i = 0; i < barCells; i++) {
            int cellStart = i * 8;
            int inCell    = Math.max(0, Math.min(8, filledTicks - cellStart));

            if (inCell <= 0) {
                sb.append(DIM).append('·').append(RESET);
            } else if (inCell >= 8) {
                sb.append(GREEN).append('█').append(RESET);
            } else {
                sb.append(GREEN).append(BLOCKS[inCell]).append(RESET);
            }
        }
        return sb.toString();
    }

    private String renderIndeterminateBar() {
        int period = (barCells - 4) * 2;
        if (period <= 0) period = 2;
        int pos = spinnerIdx % period;
        if (pos >= barCells - 4) {
            pos = period - pos;
        }

        StringBuilder sb = new StringBuilder(barCells);
        for (int i = 0; i < barCells; i++) {
            if (i >= pos && i < pos + 4) {
                sb.append(GREEN).append('█').append(RESET);
            } else {
                sb.append(DIM).append('·').append(RESET);
            }
        }
        return sb.toString();
    }

    private String truncateTaskName() {
        if (taskName == null) return "";
        return taskName.length() > TASK_MAX ? taskName.substring(0, TASK_MAX) : taskName;
    }
}
