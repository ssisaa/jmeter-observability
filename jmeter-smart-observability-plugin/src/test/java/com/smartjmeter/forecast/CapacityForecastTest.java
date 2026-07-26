package com.smartjmeter.forecast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapacityForecastTest {

    private static Map<String, Object> agg(double p95) {
        return Map.of("overall", Map.of("rt_p95_ms", p95));
    }

    @Test
    void insufficientReturnsInsufficient(@TempDir Path dir) {
        CapacityForecast.Forecast f = CapacityForecast.forecast(dir, 1000, agg(400));
        assertTrue(f.insufficient());
        assertEquals("INSUFFICIENT_DATA", f.verdict());
    }

    @Test
    void appendCreatesJsonSnapshot(@TempDir Path dir) throws Exception {
        CapacityForecast.appendSnapshot(dir, agg(432));
        assertTrue(Files.list(dir).findAny().isPresent());
    }

    @Test
    void trendingUpForecastsBreach(@TempDir Path dir) throws Exception {
        // Seed 4 monotonically rising snapshots (yesterday - 3 days ago)
        long now = System.currentTimeMillis();
        seed(dir, now - 4L * 86_400_000, 400);
        seed(dir, now - 3L * 86_400_000, 500);
        seed(dir, now - 2L * 86_400_000, 600);
        seed(dir, now - 1L * 86_400_000, 700);
        CapacityForecast.Forecast f = CapacityForecast.forecast(dir, 1000, agg(800));
        assertFalse(f.insufficient());
        assertTrue(f.slopeMsPerDay() > 50, "expected positive slope, got " + f.slopeMsPerDay());
        assertTrue(f.daysToBreachP50() < 30, "expected breach soon, got " + f.daysToBreachP50());
        assertNotEquals("OK", f.verdict());
    }

    @Test
    void flatSeriesIsOk(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 6; i++) seed(dir, now - i * 86_400_000L, 300 + (i % 2));
        CapacityForecast.Forecast f = CapacityForecast.forecast(dir, 1000, agg(303));
        assertFalse(f.insufficient());
        assertEquals("OK", f.verdict(), "flat trend must be OK, was " + f.verdict());
    }

    @Test
    void alreadyBreachedFlagsBreached(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 5; i++) seed(dir, now - i * 86_400_000L, 800 + i * 5);
        CapacityForecast.Forecast f = CapacityForecast.forecast(dir, 1000, agg(1500));
        assertEquals("BREACHED", f.verdict());
    }

    private static void seed(Path dir, long ts, double p95) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("snapshot-" + ts + ".json"),
                "{\"timestamp_ms\":" + ts + ",\"p95_ms\":" + p95 + "}");
    }
}
