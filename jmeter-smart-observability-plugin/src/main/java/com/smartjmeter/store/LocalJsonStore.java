package com.smartjmeter.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartjmeter.model.JMeterMetric;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Newline-delimited JSON local store for {@link JMeterMetric} events.
 *
 * <p>Each call to {@link #append(JMeterMetric)} writes exactly one
 * JSON object on its own line. This makes the file trivially
 * appendable, tail-friendly and Splunk-ingestible even if HEC is
 * temporarily disabled.</p>
 */
public class LocalJsonStore {

    private static final Logger LOG = Logger.getLogger(LocalJsonStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);

    private final Path filePath;

    public LocalJsonStore(String path) {
        this.filePath = Path.of(path);
    }

    public Path getFilePath() {
        return filePath;
    }

    public synchronized void append(JMeterMetric metric) {
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            String line = MAPPER.writeValueAsString(metric) + System.lineSeparator();
            Files.writeString(filePath, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unable to persist metric to local store: " + filePath, e);
        }
    }

    public synchronized void appendAll(List<JMeterMetric> metrics) {
        for (JMeterMetric m : metrics) {
            append(m);
        }
    }
}
