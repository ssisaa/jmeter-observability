package com.smartjmeter.baseline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaselineTest {

    @Test
    void diffWithoutPreviousReturnsHasPreviousFalse() {
        Map<String, Object> d = BaselineDiff.compute(Map.of(), Map.of("overall", Map.of("count", 10)));
        assertEquals(false, d.get("has_previous"));
    }

    @Test
    void diffComputesPercentagesAndPercentagePoints() {
        Map<String, Object> prevAgg = Map.of(
                "overall", Map.of("count", 10L, "error_rate", 0.1, "rt_avg_ms", 100L, "rt_p95_ms", 200L, "rt_max_ms", 300L),
                "per_transaction", Map.of(
                        "Login", Map.of("count", 10L, "error_rate", 0.0, "rt_avg_ms", 50L, "rt_p95_ms", 80L, "rt_max_ms", 100L),
                        "Old",   Map.of("count", 5L,  "error_rate", 0.0, "rt_avg_ms", 20L, "rt_p95_ms", 30L,  "rt_max_ms", 40L)));
        Map<String, Object> prevEnv = Map.of("saved_at", "2026-01-01T00:00:00Z", "aggregate", prevAgg);

        Map<String, Object> curAgg = Map.of(
                "overall", Map.of("count", 12L, "error_rate", 0.25, "rt_avg_ms", 120L, "rt_p95_ms", 280L, "rt_max_ms", 300L),
                "per_transaction", Map.of(
                        "Login",    Map.of("count", 10L, "error_rate", 0.0,  "rt_avg_ms", 55L, "rt_p95_ms", 82L,  "rt_max_ms", 105L),
                        "Checkout", Map.of("count", 2L,  "error_rate", 1.0,  "rt_avg_ms", 500L,"rt_p95_ms", 800L, "rt_max_ms", 900L)));

        Map<String, Object> d = BaselineDiff.compute(prevEnv, curAgg);
        assertEquals(true, d.get("has_previous"));

        @SuppressWarnings("unchecked")
        Map<String, Object> overall = (Map<String, Object>) d.get("overall");
        assertEquals(2L, overall.get("count_delta"));
        assertEquals(15.0, (Double) overall.get("error_rate_pp"));      // 0.25 - 0.10 = 0.15 => +15.0pp
        assertEquals(20.0, (Double) overall.get("rt_avg_pct"));         // (120-100)/100 = +20%
        assertEquals(40.0, (Double) overall.get("rt_p95_pct"));         // (280-200)/200 = +40%

        @SuppressWarnings("unchecked")
        Map<String, Object> perTxn = (Map<String, Object>) d.get("per_transaction");
        assertEquals("new", ((Map<?, ?>) perTxn.get("Checkout")).get("status"));
        assertEquals("gone", ((Map<?, ?>) perTxn.get("Old")).get("status"));

        @SuppressWarnings("unchecked")
        List<String> notable = (List<String>) d.get("notable");
        assertTrue(notable.stream().anyMatch(s -> s.contains("Checkout is new")));
        assertTrue(notable.stream().anyMatch(s -> s.contains("Old missing")));
        assertTrue(notable.stream().anyMatch(s -> s.contains("overall p95 +40.0% vs baseline")));
    }

    @Test
    void storeRoundTripsEnvelope(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("baseline.json");
        BaselineStore s = new BaselineStore(file);
        assertFalse(s.exists());

        Map<String, Object> agg = Map.of("overall", Map.of("count", 3L));
        s.save("myTest", agg);
        assertTrue(s.exists());

        Map<String, Object> loaded = s.load();
        assertEquals("myTest", loaded.get("test_name"));
        assertNotNull(loaded.get("saved_at"));
        assertTrue(Files.readString(file).contains("myTest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> aggLoaded = (Map<String, Object>) loaded.get("aggregate");
        assertEquals(3, ((Number) ((Map<?, ?>) aggLoaded.get("overall")).get("count")).intValue());
    }
}
