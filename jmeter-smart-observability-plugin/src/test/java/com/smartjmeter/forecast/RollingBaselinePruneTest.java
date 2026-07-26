package com.smartjmeter.forecast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RollingBaselinePruneTest {

    @Test
    void pruneByAgeDropsOldSnapshots(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        seed(dir, now - 200L * 86_400_000L, 500);   // 200d old  -> pruned
        seed(dir, now - 100L * 86_400_000L, 520);   // 100d old  -> pruned
        seed(dir, now -  30L * 86_400_000L, 540);   //  30d old  -> kept
        seed(dir, now -   5L * 86_400_000L, 560);   //   5d old  -> kept

        int removed = CapacityForecast.prune(dir, 100, 90);
        assertEquals(2, removed);
        assertEquals(2, Files.list(dir).count());
    }

    @Test
    void pruneByCountKeepsNewest(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 10; i++) {
            seed(dir, now - i * 3_600_000L, 300 + i);
        }
        int removed = CapacityForecast.prune(dir, 5, 365);
        assertEquals(5, removed);
        assertEquals(5, Files.list(dir).count());
    }

    @Test
    void pruneAgeAndCountBothApplied(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        // 3 very old (age-prune)
        seed(dir, now - 400L * 86_400_000L, 500);
        seed(dir, now - 300L * 86_400_000L, 510);
        seed(dir, now - 200L * 86_400_000L, 520);
        // 10 recent (count-prune to 5)
        for (int i = 1; i <= 10; i++) seed(dir, now - i * 3_600_000L, 400 + i);

        int removed = CapacityForecast.prune(dir, 5, 90);
        // 3 by age + 5 by count = 8
        assertEquals(8, removed);
        assertEquals(5, Files.list(dir).count());
    }

    @Test
    void pruneReturnsZeroOnMissingDir(@TempDir Path dir) {
        assertEquals(0, CapacityForecast.prune(dir.resolve("nope"), 10, 30));
    }

    @Test
    void pruneNoOpWhenUnderLimits(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 3; i++) seed(dir, now - i * 86_400_000L, 200 + i);
        assertEquals(0, CapacityForecast.prune(dir, 100, 90));
        assertEquals(3, Files.list(dir).count());
    }

    private static void seed(Path dir, long ts, double p95) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("snapshot-" + ts + ".json"),
                "{\"timestamp_ms\":" + ts + ",\"p95_ms\":" + p95 + "}");
    }
}
