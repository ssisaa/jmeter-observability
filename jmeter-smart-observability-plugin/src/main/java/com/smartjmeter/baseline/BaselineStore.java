package com.smartjmeter.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persist / load the aggregate summary of a JMeter run as a baseline
 * that the next run can diff against.
 *
 * <p>The stored envelope is:</p>
 * <pre>
 * {
 *   "saved_at": "2026-01-25T15:00:00Z",
 *   "test_name": "...",
 *   "aggregate": { "overall": {...}, "per_transaction": {...} }
 * }
 * </pre>
 */
public class BaselineStore {

    private static final Logger LOG = Logger.getLogger(BaselineStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;

    public BaselineStore(Path filePath) {
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    public boolean exists() {
        return Files.exists(filePath);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> load() {
        if (!exists()) return Map.of();
        try {
            return MAPPER.readValue(filePath.toFile(), Map.class);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load baseline from " + filePath, e);
            return Map.of();
        }
    }

    public void save(String testName, Map<String, Object> aggregate) {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("saved_at", Instant.now().toString());
            envelope.put("test_name", testName);
            envelope.put("aggregate", aggregate);
            Files.writeString(filePath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save baseline to " + filePath, e);
        }
    }
}
