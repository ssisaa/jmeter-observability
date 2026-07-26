package com.smartjmeter.ci;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CiGateTest {

    @Test
    void missingFileReturnsZero(@TempDir Path dir) {
        assertEquals(0, CiGate.evaluate(dir.resolve("nope.json")));
    }

    @Test
    void goReturnsZero(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("ci-gate.json");
        Files.writeString(p, """
                {"verdict":"GO","shouldFail":false,"exitCode":0,"rationale":"all good"}""");
        assertEquals(0, CiGate.evaluate(p));
    }

    @Test
    void noGoReturnsExitCode(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("ci-gate.json");
        Files.writeString(p, """
                {"verdict":"NO_GO","shouldFail":true,"exitCode":3,"rationale":"regressed"}""");
        assertEquals(3, CiGate.evaluate(p));
    }

    @Test
    void goWithConditionsReturnsTwo(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("ci-gate.json");
        Files.writeString(p, """
                {"verdict":"GO_WITH_CONDITIONS","shouldFail":true,"exitCode":2,"rationale":"watch"}""");
        assertEquals(2, CiGate.evaluate(p));
    }

    @Test
    void shouldFailFalseAlwaysZero(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("ci-gate.json");
        Files.writeString(p, """
                {"verdict":"NO_GO","shouldFail":false,"exitCode":3,"rationale":"soft"}""");
        assertEquals(0, CiGate.evaluate(p));
    }

    @Test
    void malformedJsonReturnsOne(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("ci-gate.json");
        Files.writeString(p, "not json");
        assertEquals(1, CiGate.evaluate(p));
    }
}
