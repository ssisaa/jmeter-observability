package com.smartjmeter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigPathTest {

    @Test
    void relativePathResolvesAgainstOutputDirectory(@TempDir Path tmp) {
        PluginConfig cfg = new PluginConfig.Builder()
                .testName("smoke run !@#")
                .outputDirectory(tmp.toString())
                .build();
        Path resolved = cfg.resolvePath("metrics.json");
        assertTrue(resolved.isAbsolute());
        assertEquals(tmp.resolve("metrics.json").normalize(), resolved);
    }

    @Test
    void absolutePathIsKept(@TempDir Path tmp) {
        PluginConfig cfg = new PluginConfig.Builder().outputDirectory(tmp.toString()).build();
        Path abs = tmp.resolve("elsewhere/file.json");
        assertEquals(abs.normalize(), cfg.resolvePath(abs.toString()));
    }

    @Test
    void blankOutputDirFallsBackToCwd() {
        PluginConfig cfg = new PluginConfig.Builder().outputDirectory("").build();
        Path resolved = cfg.resolvePath("x.json");
        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.toString().endsWith("x.json"));
    }

    @Test
    void baselinePathDerivesFromTestName(@TempDir Path tmp) {
        PluginConfig cfg = new PluginConfig.Builder()
                .outputDirectory(tmp.toString())
                .testName("smoke run !@#")
                .build();
        Path resolved = cfg.resolveBaselinePath();
        // Sanitised: spaces + symbols -> underscores
        assertEquals("baseline-smoke_run____.json", resolved.getFileName().toString());
        assertTrue(resolved.startsWith(tmp));
    }

    @Test
    void explicitBaselinePathIsUsed(@TempDir Path tmp) {
        PluginConfig cfg = new PluginConfig.Builder()
                .outputDirectory(tmp.toString())
                .baselinePath("saved/base.json")
                .build();
        assertEquals(tmp.resolve("saved/base.json").normalize(), cfg.resolveBaselinePath());
    }
}
