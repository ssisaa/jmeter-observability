package com.smartjmeter.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NotifierCooldownTest {

    @Test
    void allowsFirstFireAndSuppressesRepeats(@TempDir Path dir) {
        Path state = dir.resolve("cd.json");
        NotifierCooldown c = new NotifierCooldown(state, 3_600);
        assertTrue(c.allow("slack", "NO_GO", "checkout-load"));
        c.record("slack", "NO_GO", "checkout-load");
        assertFalse(c.allow("slack", "NO_GO", "checkout-load"));
    }

    @Test
    void zeroCooldownDisablesThrottle(@TempDir Path dir) {
        NotifierCooldown c = new NotifierCooldown(dir.resolve("cd.json"), 0);
        assertTrue(c.allow("slack", "NO_GO", "t"));
        c.record("slack", "NO_GO", "t");
        assertTrue(c.allow("slack", "NO_GO", "t"));
    }

    @Test
    void differentVerdictsAreIndependent(@TempDir Path dir) {
        NotifierCooldown c = new NotifierCooldown(dir.resolve("cd.json"), 3_600);
        c.record("slack", "NO_GO", "t1");
        assertFalse(c.allow("slack", "NO_GO", "t1"));
        assertTrue(c.allow("slack", "GO_WITH_CONDITIONS", "t1"));
        assertTrue(c.allow("teams", "NO_GO", "t1"));
        assertTrue(c.allow("slack", "NO_GO", "t2"));
    }

    @Test
    void statePersistsAcrossInstances(@TempDir Path dir) throws Exception {
        Path state = dir.resolve("cd.json");
        NotifierCooldown a = new NotifierCooldown(state, 3_600);
        a.record("jira", "NO_GO", "app");
        assertTrue(Files.exists(state));
        NotifierCooldown b = new NotifierCooldown(state, 3_600);
        assertFalse(b.allow("jira", "NO_GO", "app"));
    }
}
