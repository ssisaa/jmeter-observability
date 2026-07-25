package com.smartjmeter.correlate;

import com.smartjmeter.model.JMeterMetric;

import java.util.ArrayList;
import java.util.List;

/**
 * Correlation of failed JMeter samples with an app-log time window.
 *
 * <p>For each failed sample we produce a {@link TimeWindow} spanning
 * {@code [timestamp - N, timestamp + N]} seconds. Adjacent / overlapping
 * windows are merged so we don't run duplicate Splunk searches.</p>
 */
public class CorrelationEngine {

    private final long windowSeconds;

    public CorrelationEngine(long windowSeconds) {
        if (windowSeconds < 0) {
            throw new IllegalArgumentException("windowSeconds must be >= 0");
        }
        this.windowSeconds = windowSeconds;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public List<TimeWindow> buildFailureWindows(List<JMeterMetric> metrics) {
        List<TimeWindow> raw = new ArrayList<>();
        for (JMeterMetric m : metrics) {
            if (!m.isSuccess()) {
                long epochSec = (long) (m.getTimestamp() / 1000d);
                raw.add(new TimeWindow(epochSec - windowSeconds, epochSec + windowSeconds));
            }
        }
        return mergeOverlapping(raw);
    }

    /**
     * Sort by start and merge overlapping windows. Package-private for
     * unit testing.
     */
    static List<TimeWindow> mergeOverlapping(List<TimeWindow> in) {
        if (in.isEmpty()) return in;
        List<TimeWindow> sorted = new ArrayList<>(in);
        sorted.sort((a, b) -> Long.compare(a.earliest(), b.earliest()));
        List<TimeWindow> out = new ArrayList<>();
        TimeWindow current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            TimeWindow next = sorted.get(i);
            if (next.earliest() <= current.latest()) {
                current = new TimeWindow(current.earliest(), Math.max(current.latest(), next.latest()));
            } else {
                out.add(current);
                current = next;
            }
        }
        out.add(current);
        return out;
    }

    /** Half-open unnecessary; treat both endpoints as inclusive epoch seconds. */
    public record TimeWindow(long earliest, long latest) { }
}
