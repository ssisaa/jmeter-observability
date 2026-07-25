package com.smartjmeter.splunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.model.JMeterMetric;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous, batching Splunk HEC dispatcher.
 *
 * <p>{@link SplunkHECClient} sends one HTTP POST per sample which is fine
 * for low-volume tests but back-pressures JMeter's sampler threads on
 * high-RPS runs. This class:</p>
 * <ul>
 *   <li>Enqueues metrics into a bounded {@link BlockingQueue} — no
 *       blocking on the sampler thread beyond a fast offer().</li>
 *   <li>A single background worker drains up to
 *       {@code batchSize} events per HTTP request, packaging them as
 *       newline-delimited HEC events (the format Splunk HEC natively
 *       accepts for batched ingestion).</li>
 *   <li>Flushes on timer ({@code flushIntervalMs}) as well as when the
 *       batch is full.</li>
 *   <li>{@link #close()} drains the remaining queue synchronously.</li>
 * </ul>
 *
 * <p>Failures are logged and dropped — a Splunk outage never breaks the
 * JMeter run.</p>
 */
public class AsyncBatchingHECClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AsyncBatchingHECClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JMeterMetric POISON = new JMeterMetric();

    private final String url;
    private final String token;
    private final String index;
    private final int batchSize;
    private final long flushIntervalMs;
    private final BlockingQueue<JMeterMetric> queue;
    private final HttpClient httpClient;
    private final Thread worker;

    public AsyncBatchingHECClient(String url, String token, String index,
                                  int batchSize, long flushIntervalMs, int queueCapacity) {
        this.url = url;
        this.token = token;
        this.index = index;
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(50, flushIntervalMs);
        this.queue = new LinkedBlockingQueue<>(Math.max(1000, queueCapacity));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.worker = new Thread(this::runLoop, "smart-o11y-hec-batcher");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * Non-blocking enqueue. Drops the metric with a WARNING if the queue
     * is full so a slow Splunk cannot back-pressure JMeter.
     */
    public void send(JMeterMetric metric) {
        if (url == null || url.isBlank() || token == null || token.isBlank()) return;
        if (!queue.offer(metric)) {
            LOG.log(Level.WARNING, "HEC queue full - dropping metric for transaction {0}",
                    metric.getTransaction());
        }
    }

    private void runLoop() {
        List<JMeterMetric> batch = new ArrayList<>(batchSize);
        while (true) {
            try {
                JMeterMetric first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first == POISON) {
                    // Drain any real metrics enqueued before POISON.
                    queue.drainTo(batch);
                    // Also drop any stray POISON copies out of the batch.
                    batch.removeIf(m -> m == POISON);
                    flushBatch(batch);
                    return;
                }
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - batch.size());
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "HEC batcher loop error", e);
                batch.clear();
            }
        }
    }

    /** Builds a HEC newline-delimited body for a batch. Exposed for tests. */
    public String buildBatchBody(List<JMeterMetric> batch) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (JMeterMetric m : batch) {
            String eventJson = MAPPER.writeValueAsString(m);
            sb.append("{\"event\":").append(eventJson)
              .append(",\"sourcetype\":\"jmeter\",\"index\":\"").append(index).append("\"}")
              .append('\n');
        }
        return sb.toString();
    }

    private void flushBatch(List<JMeterMetric> batch) {
        if (batch.isEmpty()) return;
        try {
            String body = buildBatchBody(batch);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Splunk " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                LOG.log(Level.WARNING, "HEC batch flush {0}: {1}",
                        new Object[]{resp.statusCode(), resp.body()});
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "HEC batch flush failed (" + batch.size() + " events)", e);
        }
    }

    /** Drain-and-stop. Blocks until the worker thread exits. */
    @Override
    public void close() {
        queue.offer(POISON);
        try {
            worker.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Visible for tests. */
    int queueSize() { return queue.size(); }
}
