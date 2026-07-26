package com.smartjmeter.analysis;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceClientTest {

    @Test
    void blankUrlReturnsStaticFallback() {
        AnalysisServiceClient c = new AnalysisServiceClient("", false);
        Map<String, Object> out = c.analyze("sys", "user");
        assertEquals(false, out.get("structured"));
        assertTrue(String.valueOf(out.get("markdown")).contains("Static analysis"));
        assertEquals("analysis-service-url-blank", out.get("fallback_reason"));
    }

    @Test
    void unreachableUrlReturnsStaticFallback() {
        // Bogus port - the client swallows the ConnectException.
        AnalysisServiceClient c = new AnalysisServiceClient("http://127.0.0.1:1", false);
        Map<String, Object> out = c.analyze("sys", "user");
        assertEquals(false, out.get("structured"));
        assertTrue(String.valueOf(out.get("markdown")).contains("Static analysis"));
        assertNotNull(out.get("fallback_reason"));
    }

    @Test
    void healthOnBlankUrlIsFalse() {
        assertFalse(new AnalysisServiceClient("", false).isHealthy());
    }

    @Test
    void healthOnUnreachableIsFalse() {
        assertFalse(new AnalysisServiceClient("http://127.0.0.1:1", false).isHealthy());
    }
}
