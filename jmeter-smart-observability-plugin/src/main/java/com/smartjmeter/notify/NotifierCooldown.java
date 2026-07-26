package com.smartjmeter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * v2.0.3 per-sink notifier throttle.
 *
 * <p>Persists a {@code notifier-cooldowns.json} file under the plugin
 * output directory that maps {@code "<sink>|<verdict>|<testName>"} to
 * the last-fire epoch-millis. Notifiers consult it before dispatching
 * so on-call is not paged repeatedly for the same NO_GO.</p>
 *
 * <p>Cooldown decisions are per-sink, per-verdict, per-test. Different
 * tests with the same verdict fire independently; GO_WITH_CONDITIONS
 * and NO_GO don't suppress each other.</p>
 */
public final class NotifierCooldown {

    private static final Logger LOG = Logger.getLogger(NotifierCooldown.class.getName());
    private static final ObjectMapper M = new ObjectMapper();

    private final Path stateFile;
    private final long cooldownMs;
    private final Map<String, Long> state;

    public NotifierCooldown(Path stateFile, long cooldownSeconds) {
        this.stateFile = stateFile;
        this.cooldownMs = Math.max(0, cooldownSeconds) * 1000L;
        this.state = load(stateFile);
    }

    /** Return true when the notifier is *allowed* to fire (cooldown expired). */
    public boolean allow(String sink, String verdict, String testName) {
        if (cooldownMs <= 0) return true;
        String key = key(sink, verdict, testName);
        Long last = state.get(key);
        long now = System.currentTimeMillis();
        if (last == null || now - last >= cooldownMs) {
            return true;
        }
        long remain = (cooldownMs - (now - last)) / 1000;
        LOG.log(Level.INFO, "Cooldown suppress {0}: {1}s remaining", new Object[]{key, remain});
        return false;
    }

    /** Mark a successful fire so the next call within cooldown is suppressed. */
    public void record(String sink, String verdict, String testName) {
        state.put(key(sink, verdict, testName), System.currentTimeMillis());
        save();
    }

    /** Persist state. Best-effort. */
    private synchronized void save() {
        try {
            if (stateFile.getParent() != null) Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, M.writerWithDefaultPrettyPrinter().writeValueAsString(state));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save notifier cooldowns to " + stateFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> load(Path stateFile) {
        if (stateFile == null || !Files.isRegularFile(stateFile)) return new LinkedHashMap<>();
        try {
            Map<String, Object> raw = M.readValue(Files.readString(stateFile), Map.class);
            Map<String, Long> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Number n) out.put(e.getKey(), n.longValue());
            }
            return out;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load notifier cooldowns from " + stateFile, e);
            return new LinkedHashMap<>();
        }
    }

    private static String key(String sink, String verdict, String testName) {
        return safe(sink) + "|" + safe(verdict) + "|" + safe(testName);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
