package com.smartjmeter.ci;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Standalone CI Gate runner.
 *
 * <p>Reads the {@code ci-gate.json} written by the Backend Listener at
 * teardown and exits the JVM with a code matching the verdict:</p>
 * <pre>
 *   GO                 -> 0
 *   GO_WITH_CONDITIONS -> 2
 *   NO_GO              -> 3
 *   any other          -> 1
 * </pre>
 *
 * <p>Wire this into your CI step after the {@code jmeter -n -t plan.jmx}
 * command so a bad verdict fails the build:</p>
 * <pre>
 *   java -cp jmeter-smart-observability-plugin-2.0.0.jar \
 *        com.smartjmeter.ci.CiGate ./ci-gate.json
 * </pre>
 */
public final class CiGate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CiGate() { }

    public static void main(String[] args) {
        Path gatePath = args.length > 0 ? Paths.get(args[0]) : Paths.get("ci-gate.json");
        int exit = evaluate(gatePath);
        System.exit(exit);
    }

    /** Returns the exit code for the gate file (0 when missing / GO). */
    @SuppressWarnings("unchecked")
    public static int evaluate(Path gatePath) {
        try {
            if (!Files.exists(gatePath)) {
                System.err.println("[ci-gate] " + gatePath + " not found - assuming GO (0)");
                return 0;
            }
            Map<String, Object> gate = MAPPER.readValue(Files.readString(gatePath), Map.class);
            boolean shouldFail = Boolean.TRUE.equals(gate.get("shouldFail"));
            int exitCode = 0;
            Object ec = gate.get("exitCode");
            if (ec instanceof Number n) exitCode = n.intValue();
            String verdict = String.valueOf(gate.getOrDefault("verdict", ""));
            System.out.println("[ci-gate] verdict=" + verdict + " shouldFail=" + shouldFail
                    + " exitCode=" + exitCode);
            System.out.println("[ci-gate] rationale: " + gate.getOrDefault("rationale", ""));
            return shouldFail ? exitCode : 0;
        } catch (Exception e) {
            System.err.println("[ci-gate] failed to evaluate: " + e.getMessage());
            return 1;
        }
    }
}
