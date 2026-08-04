package com.wenzejin.gui;

import com.wenzejin.core.ProgressListener;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * GUI implementation of {@link ProgressListener}.
 * Bridges progress events from worker threads to the Swing EDT.
 */
public class GuiProgressListener implements ProgressListener {

    private final Consumer<ProgressSnapshot> onSnapshot;
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    private final List<String> messages = new ArrayList<>();
    private volatile int totalSteps = -1;
    private volatile boolean done = false;
    private volatile boolean error = false;
    private volatile String errorMessage = null;
    private volatile String finishMessage = null;

    public GuiProgressListener(Consumer<ProgressSnapshot> onSnapshot) {
        this.onSnapshot = onSnapshot;
    }

    @Override
    public void onStart(int totalSteps, String taskName) {
        stepCounter.set(0);
        this.totalSteps = totalSteps;
        this.done = false;
        this.error = false;
        synchronized (messages) { messages.clear(); }
        postSnapshot(null);
    }

    @Override
    public void onStep(int steps, String message) {
        stepCounter.addAndGet(steps);
        synchronized (messages) {
            messages.add(message);
            // Keep last 500 messages
            if (messages.size() > 500) {
                messages.subList(0, messages.size() - 500).clear();
            }
        }
        postSnapshot(message);
    }

    @Override
    public void onFinish(String message) {
        this.done = true;
        this.finishMessage = message;
        postSnapshot(message);
    }

    @Override
    public void onError(String errorMessage) {
        this.error = true;
        this.errorMessage = errorMessage;
        postSnapshot(errorMessage);
    }

    public boolean isFinished() { return done; }
    public boolean hasError() { return error; }
    public String getErrorMessage() { return errorMessage; }
    public String getFinishMessage() { return finishMessage; }

    public List<String> getRecentMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    private void postSnapshot(String latestMessage) {
        ProgressSnapshot snapshot = new ProgressSnapshot(
            totalSteps,
            stepCounter.get(),
            latestMessage,
            done,
            error
        );
        if (SwingUtilities.isEventDispatchThread()) {
            onSnapshot.accept(snapshot);
        } else {
            SwingUtilities.invokeLater(() -> onSnapshot.accept(snapshot));
        }
    }

    /** Immutable snapshot of current progress state. */
    public record ProgressSnapshot(
        int totalSteps,
        int currentSteps,
        String latestMessage,
        boolean done,
        boolean error
    ) {
        public double getFraction() {
            if (totalSteps <= 0) return 0.0;
            return Math.min(1.0, (double) currentSteps / totalSteps);
        }

        public int getPercent() {
            return (int) (getFraction() * 100);
        }

        public boolean isIndeterminate() {
            return totalSteps <= 0;
        }
    }
}
