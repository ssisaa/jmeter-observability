package com.smartjmeter.splunk;

import com.smartjmeter.model.JMeterMetric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsyncBatchingHECClientTest {

    @Test
    void buildsNewlineDelimitedBatchBody() throws Exception {
        AsyncBatchingHECClient c = new AsyncBatchingHECClient(
                "http://ignored", "tok", "performance", 100, 100, 100);
        try {
            String body = c.buildBatchBody(List.of(metric("Login", 100), metric("Checkout", 200)));
            String[] lines = body.split("\n");
            assertEquals(2, lines.length);
            assertTrue(lines[0].contains("\"event\":"));
            assertTrue(lines[0].contains("\"sourcetype\":\"jmeter\""));
            assertTrue(lines[0].contains("\"index\":\"performance\""));
            assertTrue(lines[0].contains("\"transaction\":\"Login\""));
            assertTrue(lines[1].contains("\"transaction\":\"Checkout\""));
        } finally {
            c.close();
        }
    }

    @Test
    void sendWithoutConfigIsNoop() {
        AsyncBatchingHECClient c = new AsyncBatchingHECClient(
                "", "", "performance", 10, 100, 100);
        try {
            c.send(metric("X", 1));
            // Nothing queued because url/token are blank
            assertEquals(0, c.queueSize());
        } finally {
            c.close();
        }
    }

    @Test
    void closeIsIdempotentAndDrainsQueue() throws Exception {
        // Point at an unreachable URL so flushes fail fast without blocking.
        AsyncBatchingHECClient c = new AsyncBatchingHECClient(
                "http://127.0.0.1:1/collector", "tok", "performance", 10, 100, 100);
        for (int i = 0; i < 5; i++) c.send(metric("T" + i, i));
        c.close();
        c.close(); // idempotent
        assertEquals(0, c.queueSize());
    }

    private JMeterMetric metric(String txn, long rt) {
        JMeterMetric m = new JMeterMetric();
        m.setTransaction(txn);
        m.setResponseTime(rt);
        m.setSuccess(true);
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }
}
