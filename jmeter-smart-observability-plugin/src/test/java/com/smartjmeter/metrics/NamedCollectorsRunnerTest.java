package com.smartjmeter.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NamedCollectorsRunnerTest {

    @Test
    void emptyConfigReturnsEmpty(@TempDir Path dir) {
        assertTrue(NamedCollectorsRunner.run("[]", 0, 1, false, dir).isEmpty());
        assertTrue(NamedCollectorsRunner.run("", 0, 1, false, dir).isEmpty());
        assertTrue(NamedCollectorsRunner.run(null, 0, 1, false, dir).isEmpty());
    }

    @Test
    void malformedJsonReturnsEmpty(@TempDir Path dir) {
        assertTrue(NamedCollectorsRunner.run("not-json", 0, 1, false, dir).isEmpty());
    }

    @Test
    void unreachableBackendYieldsEmptyResultButNoException(@TempDir Path dir) {
        // Bogus URL - the collector must swallow the failure.
        String cfg = """
                [{"backend":"prometheus","baseUrl":"http://127.0.0.1:1","queries":{"cpu":"up"}}]""";
        Map<String, Map<String, List<Map<String, Object>>>> out =
                NamedCollectorsRunner.run(cfg, 0, 60_000, false, dir);
        assertNotNull(out);
        // May be empty or contain "prometheus" with empty query results, both acceptable.
        if (out.containsKey("prometheus")) {
            assertTrue(out.get("prometheus").getOrDefault("cpu", List.of()).isEmpty());
        }
    }

    @Test
    void writesPerBackendJson(@TempDir Path dir) throws Exception {
        String cfg = """
                [{"backend":"prometheus","baseUrl":"http://127.0.0.1:1","queries":{"cpu":"up"},"outPath":"prom.json"}]""";
        NamedCollectorsRunner.run(cfg, 0, 60_000, false, dir);
        // outPath is relative; NamedCollectorsRunner writes it relative to outputDir
        Path expected = dir.resolve("prom.json");
        assertTrue(Files.exists(expected), "expected " + expected + " to exist");
    }
}
