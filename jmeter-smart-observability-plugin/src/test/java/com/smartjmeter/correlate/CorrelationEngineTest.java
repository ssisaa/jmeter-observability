package com.smartjmeter.correlate;

import com.smartjmeter.model.JMeterMetric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationEngineTest {

    @Test
    void buildsAndMergesOverlappingWindows() {
        CorrelationEngine engine = new CorrelationEngine(30);
        JMeterMetric a = failure(1_700_000_000_000L);  // t=1700000000 s
        JMeterMetric b = failure(1_700_000_020_000L);  // t=1700000020 s -> window overlaps A
        JMeterMetric c = failure(1_700_000_500_000L);  // t=1700000500 s -> separate

        List<CorrelationEngine.TimeWindow> windows = engine.buildFailureWindows(List.of(a, b, c));
        assertEquals(2, windows.size());

        assertEquals(1_699_999_970L, windows.get(0).earliest());
        assertEquals(1_700_000_050L, windows.get(0).latest());

        assertEquals(1_700_000_470L, windows.get(1).earliest());
        assertEquals(1_700_000_530L, windows.get(1).latest());
    }

    @Test
    void ignoresSuccessfulSamples() {
        CorrelationEngine engine = new CorrelationEngine(10);
        JMeterMetric ok = new JMeterMetric();
        ok.setSuccess(true);
        ok.setTimestamp(1_700_000_000_000d);
        assertTrue(engine.buildFailureWindows(List.of(ok)).isEmpty());
    }

    @Test
    void rejectsNegativeWindow() {
        assertThrows(IllegalArgumentException.class, () -> new CorrelationEngine(-1));
    }

    private JMeterMetric failure(long tsMs) {
        JMeterMetric m = new JMeterMetric();
        m.setSuccess(false);
        m.setTimestamp(tsMs);
        return m;
    }
}
