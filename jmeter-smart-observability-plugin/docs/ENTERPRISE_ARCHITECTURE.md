# JMeter Smart Observability AI Platform — Enterprise Architecture

**Document owner:** Performance Engineering CoE
**Audience:** CIO, CTO, Chief Architect, Engineering Directors, Delivery Managers, Product Owners, Business Stakeholders, Performance Engineering Managers
**Baseline:** JMeter Smart Observability Plugin v1.0.0 (already shipped: HEC forward, Search API correlation, O11y metrics, multi-provider LLM analysis, HEC async batching, baseline diff, styled HTML report).
**Design target:** v2.0 — "One-Click Release Readiness Report" for Fortune 500 performance programs.

> **The report must answer, on page one, exactly one question:**
> **"Can we safely deploy this release into Production?"**

---

## 0. Executive Summary of the Design

Performance engineers today stitch together outputs from JMeter, Splunk Enterprise, Splunk Observability Cloud and AWS CloudWatch by hand. It is slow, inconsistent, and unreadable by executives. This platform turns that stitching into **software** so that at the moment JMeter finishes, a single Executive Performance Report is produced, exported, and delivered — with a **Go / No-Go verdict** at the top and a full evidence trail beneath.

The solution is delivered as an enterprise extension of the existing **JMeter Smart Observability Plugin** — a JMeter Backend Listener that runs inside the JMeter JVM, plus an out-of-process **Analysis Service** for heavier AI, PDF, and PowerPoint rendering.

Non-negotiables:

- **Runs unattended.** Zero manual analysis after `Test Plan → Stop`.
- **Enterprise-safe.** No secrets in JMX. mTLS. Vault-backed. Audit-logged. Fails closed, never leaks.
- **Explainable AI.** Every LLM claim is backed by a metric or a Splunk event. Confidence scores exposed.
- **Executive first, engineer second.** Verdict → Scores → Evidence → Appendix.

---

# SECTION 1 — Executive Performance Report Structure

The report is a single artefact (HTML + PDF + PPTX + JSON) with a fixed section order so executives learn where to look once.

### 1.1 Cover Page

- **Verdict badge** (large): `GO` / `GO WITH CONDITIONS` / `NO-GO` / `INSUFFICIENT DATA`
- Release name, environment, build SHA, test window, tester, sign-off table
- **Release Readiness Score** (0–100) and **Production Confidence** (0–100)
- QR code to the live HTML report

### 1.2 Executive Summary (≤ 200 words, LLM-authored, evidence-anchored)

- Purpose: give the CIO/CTO the whole story in 30 seconds.
- Metrics referenced: overall p95, error rate, throughput vs SLA, top regression.
- AI insight: "This release is safe to deploy because … Two risks remain: …"

### 1.3 Overall Test Verdict

- Purpose: single-line answer.
- Verdict rules table (SLA breach, regression > threshold, sat/error triggers).
- Confidence score `[0, 1]` with rationale.

### 1.4 Release Readiness Panel

- Traffic-light matrix: Functional Pass, SLA Pass, Regression Pass, Stability Pass, Capacity Pass, Observability Coverage Pass.
- Any RED gates block deployment.

### 1.5 Production Recommendation

- Purpose: what the platform recommends operationally.
- Options: `Deploy`, `Deploy behind feature flag`, `Deploy in canary 5% → 25% → 100%`, `Deploy with rollback plan`, `Do not deploy`.
- Includes rollback trigger conditions.

### 1.6 Risk Level

- Overall risk = `max(functional, sla, regression, stability, capacity)`.
- Levels: Critical / High / Medium / Low / None.
- Top 3 risks with owners.

### 1.7 Business Impact

- Purpose: translate p95 and error rate into revenue / SLA-credit language.
- Uses `businessImpact.yaml` config: `{ transaction: cost_per_error_usd, cost_per_second_slowdown_usd, downtime_penalty_per_min_usd }`.
- Chart: projected impact at production TPS.

### 1.8 SLA Compliance

- Per-transaction SLA (target p95, target error rate) versus measured.
- Chart: SLA-adherence heatmap over test duration.
- AI insight: "Login p95 within SLA; Checkout breached SLA between 09:41 and 09:47."

### 1.9 KPI Summary

- Throughput, avg RT, p95, p99, p99.9, error %, max concurrent users, transactions/sec by business flow.
- Delta vs baseline for every KPI.

### 1.10 AI Executive Insights

- 5 bullets, ranked. Each bullet cites evidence links (Splunk search URL, O11y chart URL, JMeter sample id).

### 1.11 Performance Health Score

- Composite 0–100. Formula documented in §9.

### 1.12 Capacity Score

- Modelled headroom: `((peak_supported_tps − observed_tps) / peak_supported_tps) × 100`.
- Estimated cliff: at what TPS p95 crosses 2× SLA.

### 1.13 Stability Score

- Function of error-rate variance, GC pause distribution, response-time jitter, restart count.

### 1.14 Scalability Score

- Slope of p95 vs concurrency (linear = 100, quadratic ≥ 50 = risk, exponential = 0).

### 1.15 Infrastructure Health

- CPU / Memory / Disk / Network / Pod restarts from CloudWatch + O11y.
- Colour-coded per host / pod / service.

### 1.16 Application Health

- Thread pool utilisation, connection pool utilisation, queue depth, circuit breakers, request queue.

### 1.17 Database Health

- Slow queries, lock waits, deadlocks, active sessions, cache hit ratio, RDS CPU / IOPS / freeable memory.

### 1.18 Network Health

- ALB target 5xx, connection resets, ELB latency, VPC flow anomalies, packet drops.

### 1.19 Error Analysis

- Top 10 error signatures with count, first-seen, last-seen, sample trace ID, sample Splunk event.

### 1.20 Bottleneck Analysis

- Ordered list of dominant bottlenecks (DB, GC, Thread starvation, Downstream API, Network, Client) with weight of each.

### 1.21 Root Cause Analysis

- Fishbone view (People / Process / Tech / Data), plus a narrative RCA authored by the LLM with evidence citations.

### 1.22 Performance Trends

- Trends across last N runs of the same test plan (retrieved from the baseline store): p95, error %, throughput.

### 1.23 Recommendations

- Prioritised: immediate (before deploy), short-term (this sprint), long-term (this quarter).

### 1.24 Capacity Forecast

- Linear + quantile-regression forecast up to 12 months at expected traffic growth. Shows the month the current infra reaches red.

### 1.25 Deployment Recommendation

- Restated from §1.5 with rollout plan, canary %, rollback triggers.

### 1.26 Appendix

- All raw evidence: full sample table, complete Splunk queries used, O11y and CloudWatch chart URLs, JMeter run metadata, plugin config snapshot, signed hash of report inputs.

Each section renders to **HTML**, **PDF**, and (for §1.1–1.10 + §1.24) **PowerPoint**.

---

# SECTION 2 — JMeter Metrics Collection

Collected by the existing `SmartObservabilityBackendListener` at every batch of `SampleResult`s (already implemented) and extended for v2. All metrics are stamped with `test_run_id` (UUIDv7) so downstream systems can join.

### 2.1 Per-sample metrics (already captured)

`testName, transaction, responseTime, latency, connectTime, bytesSent, bytesReceived, success, timestamp, environment, application, responseCode, threadName`.

### 2.2 Per-sample additions in v2

`sub_result_count, assertion_failure_message, first_byte_time, idle_time, sample_start, sample_end, group_threads, all_threads, sampler_class, url, trace_id (from response header), span_id, user_agent, size_in_bytes, header_size, body_size, transaction_controller_name, transaction_controller_success`.

### 2.3 Aggregate metrics (computed by `MetricAggregator`, extended in v2)

Per transaction and overall: `count, errors, error_rate, rt_min_ms, rt_avg_ms, rt_median_ms, rt_p50_ms, rt_p75_ms, rt_p90_ms, rt_p95_ms, rt_p99_ms, rt_p999_ms, rt_max_ms, rt_stddev_ms, rt_iqr_ms, throughput_rps, bytes_sent_total, bytes_received_total, bytes_sent_avg, bytes_received_avg, first_error_ts, last_error_ts, apdex_score, top_error_signatures`.

### 2.4 Timing waterfall metrics

`dns_lookup_ms, tcp_connect_ms, tls_handshake_ms, request_send_ms, wait_ttfb_ms, response_download_ms` (parsed from `HTTPSampleResult` via the JMeter subresult timing API).

### 2.5 Load-shape metrics

`rampup_sec, thread_group_target, thread_group_active, thread_group_started, thread_group_finished, ramp_slope_rps_per_sec, steady_state_start_ts, steady_state_end_ts, cooldown_start_ts`.

### 2.6 Controller / logic metrics

`transaction_controller_results, if_controller_hit_rate, throughput_controller_actual_rate, timer_delay_ms`.

### 2.7 JMeter process telemetry

`jmeter_heap_used_mb, jmeter_gc_pauses_ms, jmeter_cpu_pct, jmeter_thread_count`. Sampled every 5 s by an in-process scheduler thread inside the plugin.

### 2.8 Backend Listener queue metrics

`backend_queue_depth, backend_queue_drops, hec_batch_size_avg, hec_flush_latency_ms`. Exposed via JMX MBeans and Splunk HEC.

### 2.9 CSV Data Set / Config metrics

Row counter, unique keys used, dataset exhaustion events.

### 2.10 HTML Dashboard metrics parity

Every metric surfaced in the JMeter native HTML Dashboard is also surfaced in this report, so teams can retire the native dashboard.

### 2.11 Test plan hash

`plan_sha256` computed over the JMX so the report can prove which plan produced which numbers.

---

# SECTION 3 — Splunk Enterprise Collection

Splunk Enterprise is the source of truth for logs. The plugin uses two paths:

- **Live path** (existing): `SplunkSearchClient` (create-job → poll → results → cancel-on-timeout) for correlation windows.
- **Batch path** (v2): a `SplunkExportClient` using `/services/search/jobs/export` with `output_mode=json_rows` for large log slices.

### 3.1 Log sources ingested via SPL

- Application logs (Spring Boot, WebLogic, Tomcat)
- Business / audit logs
- Exception + stack trace logs
- GC logs (parsed for pause distribution)
- Thread dumps (parsed for lock-held-by patterns)
- Database logs (Oracle alert log, MySQL slow query log, PostgreSQL log)
- Kafka broker + consumer lag logs
- API gateway access logs
- Container `stdout/stderr` (via Splunk Connect for Kubernetes)
- Kubernetes control-plane + kubelet + event logs
- Nginx / Apache access + error logs
- Custom application audit logs

### 3.2 Standard SPL queries (config-driven, editable per test plan)

```spl
# Errors around a failed sample window
index=$APP_INDEX earliest=$T-30s latest=$T+30s
  (error OR exception OR timeout OR "OutOfMemoryError" OR "SocketTimeout")
| stats count by error_signature, host, service
| sort -count

# Slow queries
index=$DB_INDEX earliest=$WINDOW_START latest=$WINDOW_END
  sourcetype IN (oracle:alert, mysql:slow, pg:log)
  duration_ms>1000
| stats avg(duration_ms) p95(duration_ms) count by query_signature

# GC pause distribution
index=$APP_INDEX sourcetype=jvm:gc
| stats p50(pause_ms) p95(pause_ms) max(pause_ms) count by service, host

# Deadlocks
index=$DB_INDEX ("deadlock detected" OR "ORA-00060")
| stats count first(_time) as first_seen by host

# Connection pool exhaustion
index=$APP_INDEX ("HikariPool" OR "PoolExhausted" OR "connection pool")
  ("timeout" OR "exhausted")
| stats count by service

# Business transaction correlation
index=$APP_INDEX correlation_id=*
| stats count values(status) by correlation_id
| where mvcount(status) > 1
```

### 3.3 Correlation IDs harvested

`trace_id, span_id, correlation_id, session_id, request_id, business_transaction_id, user_id (hashed), tenant_id`. All propagated from JMeter via injected headers (`X-Correlation-ID`, `traceparent`).

### 3.4 Derived signals

`top_10_error_signatures, error_frequency_over_time, first_seen_ts, last_seen_ts, unique_hosts_affected, blast_radius_service_count, retry_storm_score, thread_starvation_score, memory_pressure_score`.

### 3.5 Field-extraction contract

Every log source is expected to expose `service`, `env`, `host`, `severity`, `message`, `error_signature`, `duration_ms` (where applicable). A repo-shipped `props.conf` / `transforms.conf` pack enforces this contract on the Splunk indexer side.

---

# SECTION 4 — Splunk Observability Cloud Collection

Existing `SplunkO11yMetricsClient` (config-driven metric list, `X-SF-TOKEN`) is extended to also pull SignalFlow charts, service maps, and traces.

### 4.1 Golden signals (per service)

`request_rate, error_rate, latency_p50_ms, latency_p95_ms, latency_p99_ms, saturation_pct` — the **RED** and **USE** models combined.

### 4.2 Infrastructure

`cpu.utilization, memory.utilization, memory.used, disk.utilization, disk.io.read_bytes, disk.io.write_bytes, net.rx_bytes, net.tx_bytes, net.retransmits, filesystem.free`.

### 4.3 Kubernetes

`k8s.pod.cpu.usage, k8s.pod.memory.usage, k8s.pod.restart_count, k8s.container.restart_count, k8s.deployment.replicas.available, k8s.deployment.replicas.unavailable, k8s.hpa.current_replicas, k8s.hpa.desired_replicas, k8s.node.condition.ready, k8s.event.count`.

### 4.4 JVM

`jvm.memory.heap.used, jvm.memory.heap.committed, jvm.memory.nonheap.used, jvm.gc.pause_ms, jvm.gc.count, jvm.threads.count, jvm.threads.deadlocked, jvm.class.loaded, jvm.uptime, jvm.buffer.pool.used`.

### 4.5 Application

`http.server.request.duration, http.client.request.duration, http.server.active_requests, db.client.operation.duration, db.client.connections.usage, messaging.consumer.lag, cache.operation.duration, cache.hit_ratio, thread_pool.active, thread_pool.queue.size, circuit.breaker.state`.

### 4.6 Traces & service map

- OpenTelemetry traces retrieved via SignalFlow `data('spans')`.
- Service map graph exported as `{nodes, edges}` with request rate and error rate per edge.
- Trace analytics: top N slow traces per business transaction, dependency errors, external call failures.

### 4.7 Apdex per business flow

`apdex_target_ms` per transaction (from `sla.yaml`), computed on ingest.

### 4.8 SignalFlow programs shipped with the plugin

```
data('cpu.utilization', filter=filter('service', $svc)).mean().publish()
data('http.server.request.duration', filter=filter('service', $svc)).percentile(95).publish()
data('jvm.gc.pause_ms', filter=filter('service', $svc)).max().publish()
data('k8s.pod.restart_count', filter=filter('deployment', $dep)).sum().publish()
```

---

# SECTION 5 — AWS CloudWatch Collection

New `CloudWatchMetricsClient` using AWS SDK v2 (`software.amazon.awssdk:cloudwatch`, `logs`, `sts`). Assumes an IAM role via STS (short-lived credentials).

### 5.1 Compute

- **EC2**: `CPUUtilization, DiskReadBytes, DiskWriteBytes, NetworkIn, NetworkOut, StatusCheckFailed, EBSByteBalance%, EBSIOBalance%, CPUCreditBalance`.
- **ECS**: `CPUUtilization, MemoryUtilization, RunningTaskCount, PendingTaskCount, ServiceEvents`.
- **Lambda**: `Invocations, Errors, Duration, Throttles, ConcurrentExecutions, ProvisionedConcurrencyInvocations, IteratorAge`.
- **EKS**: `pod_cpu_utilization, pod_memory_utilization, cluster_failed_node_count, node_cpu_reserved_capacity, node_memory_reserved_capacity`.

### 5.2 Storage / DB

- **RDS**: `CPUUtilization, DatabaseConnections, FreeStorageSpace, FreeableMemory, ReadIOPS, WriteIOPS, ReadLatency, WriteLatency, ReplicaLag, DeadlockCount, Aurora Serverless V2 ACU`.
- **DynamoDB**: `ConsumedReadCapacityUnits, ConsumedWriteCapacityUnits, ThrottledRequests, SystemErrors, UserErrors, SuccessfulRequestLatency`.
- **ElastiCache Redis**: `CPUUtilization, EngineCPUUtilization, CacheHits, CacheMisses, Evictions, ReplicationLag, NewConnections, CurrConnections`.

### 5.3 Networking

- **ALB / NLB**: `RequestCount, HTTPCode_Target_5XX_Count, HTTPCode_Target_4XX_Count, TargetResponseTime, UnHealthyHostCount, HealthyHostCount, RejectedConnectionCount, ClientTLSNegotiationErrorCount`.
- **API Gateway**: `Count, 5XXError, 4XXError, Latency, IntegrationLatency, CacheHitCount, CacheMissCount`.
- **CloudFront**: `Requests, TotalErrorRate, 5xxErrorRate, OriginLatency, CacheHitRate`.

### 5.4 Streaming / Messaging

- **MSK**: `BytesInPerSec, BytesOutPerSec, MessagesInPerSec, MaxOffsetLag, CpuUser, CpuSystem, HeapMemoryAfterGC, UnderReplicatedPartitions`.
- **SQS**: `ApproximateNumberOfMessagesVisible, ApproximateAgeOfOldestMessage, NumberOfMessagesSent, NumberOfMessagesReceived, NumberOfMessagesDeleted`.
- **SNS**: `NumberOfMessagesPublished, NumberOfNotificationsFailed`.

### 5.5 Object stores / edge

- **S3**: `BucketSizeBytes, NumberOfObjects, 5xxErrors, TotalRequestLatency, FirstByteLatency`.

### 5.6 Signals

- **Auto Scaling events**: `GroupInServiceInstances, GroupTotalInstances, GroupPendingInstances, ScalingActivities`.
- **CloudWatch Alarms**: alarm state changes over the test window (via `describe_alarm_history`).
- **CloudWatch Logs Insights** queries against application log groups for pattern extraction:

```
fields @timestamp, @message, @logStream
| filter @message like /(?i)(error|exception|timeout|OutOfMemory)/
| stats count() by bin(30s), errorType
```

### 5.7 Custom metrics namespace

`Namespace=SmartObservability, Dimensions=[TestRunId, Env, App]` — the plugin emits its own aggregates into CloudWatch so ops teams can alarm on future baseline drift directly.

---

# SECTION 6 — AI Correlation Engine

The correlation engine is a deterministic pipeline followed by an LLM step. Determinism first (auditable), LLM last (explainable).

### 6.1 Stages

```
[Ingest]
  → NormalizedEventStream (JMeter samples + Splunk events + O11y points + CW datapoints)
     [common schema: {ts, source, service, kind, severity, key, value, correlation_id?, evidence_url?}]
[Timeline Alignment]
  → Snap to 1s / 5s / 10s buckets
[Windowing]
  → For each failed sample and each SLA breach window: build ±N s bucket
[Feature Extraction]
  → Compute change-point indicators, cross-source counts, saturation markers
[Rule Engine]
  → Fires deterministic rules (see §6.3), each yielding a Finding with a score
[Trace Join]
  → Join by trace_id / correlation_id / request_id to link JMeter samples ↔ Splunk logs ↔ O11y spans
[LLM Reasoner]
  → PromptBuilder assembles: aggregate + baseline diff + top findings + evidence samples
  → LLM produces narrative RCA, ranked probable causes, and recommended actions
[Confidence Scoring]
  → Every finding & recommendation carries {confidence 0..1, supporting_evidence[]}
[Verdict Compiler]
  → Compose §1.1–§1.5 verdict / readiness / risk / recommendation
```

### 6.2 Normalised event schema

```json
{
  "ts": "2026-01-25T09:41:12.512Z",
  "source": "splunk|o11y|cloudwatch|jmeter",
  "service": "checkout-svc",
  "kind": "log|metric|sample|trace|event",
  "severity": "info|warn|error|fatal",
  "key": "http.server.request.duration",
  "value": 1873,
  "unit": "ms",
  "correlation_id": "c-9f0a...",
  "trace_id": "abc123...",
  "evidence_url": "https://splunk.corp/en-US/app/search/search?q=..."
}
```

### 6.3 Rule catalog (each rule → a `Finding`)

| Rule ID | Fires when | Root cause hypothesis |
|---|---|---|
| `R-DB-SLOW` | p95 latency spike AND slow-query count > baseline × 3 | Database slow query |
| `R-GC-PAUSE` | p95 spike within ±3 s of `jvm.gc.pause_ms > 500` | Long GC pause |
| `R-THREAD-STARV` | HikariPool "exhausted" logs AND thread-pool queue depth up | Thread / connection starvation |
| `R-OOM` | `OutOfMemoryError` in logs AND `jvm.memory.heap.used ≈ committed` | Memory leak / undersizing |
| `R-DEADLOCK` | `deadlock detected` in DB logs | DB deadlock |
| `R-DOWNSTREAM` | upstream p95 ↑ AND downstream service `error_rate ↑` | Downstream API failure |
| `R-SCALE-LAG` | `HPA desired > current` sustained AND p95 ↑ | Scaling lag |
| `R-NET-DROP` | `ALB 5XX ↑` AND `NetworkTransmitPackets` retransmits ↑ | Network bottleneck |
| `R-QUEUE-BACKLOG` | `SQS/MSK lag ↑` AND consumer error_rate ↑ | Queue back-pressure |
| `R-CONFIG-CHANGE` | Deployment event within 15 min of first sample failure | Config / release regression |
| `R-DEP-VERSION` | Dependency version delta detected in deployment metadata | Dependency issue |
| `R-CODE-REG` | p95 delta > 25% vs baseline AND no infra saturation | Code regression |

Each rule carries `base_confidence`, boosted / dampened by number of correlating evidence points.

### 6.4 Confidence score formula

```
finding.confidence =
    sigmoid( w_evidence  * n_evidence_normalized
           + w_time      * temporal_overlap_score
           + w_baseline  * baseline_deviation_score
           + w_priorprob * rule.base_confidence )
```
Weights are tuned per tenant and stored in `weights.yaml` (Bayesian update from historical outcomes).

### 6.5 Explainability

Every LLM sentence carries markers `[E:<evidence_id>]` that the report renderer converts into deep-links to Splunk / O11y / CloudWatch.

---

# SECTION 7 — AI-Generated Insights

Structured LLM output, JSON-schema-validated before rendering.

```json
{
  "top_findings": [
    { "title": "Checkout p95 breached SLA between 09:41 and 09:47",
      "impact": "1350 users experienced >2s at checkout",
      "confidence": 0.92,
      "evidence": ["ev-splunk-1", "ev-o11y-3"] }
  ],
  "top_risks": [...],
  "business_impact": {"lost_conversions_est": 320, "usd_est": 8400},
  "critical_alerts": [...],
  "regressions": [...],
  "capacity_estimate": {
    "peak_supported_tps": 780,
    "cliff_tps": 620,
    "months_of_headroom_at_current_growth": 7.3
  },
  "deployment_risk": {"level": "MEDIUM", "reasons": [...]},
  "predicted_failures": [
    { "signal": "jvm.memory.heap.used", "eta_days": 21, "confidence": 0.71 }
  ],
  "anomalies": [...],
  "patterns": [...],
  "trend": [...],
  "recommended_actions": {
    "immediate": [...],
    "short_term": [...],
    "long_term": [...]
  }
}
```

Every insight is a first-class object in the JSON export, so downstream systems (ServiceNow, Jira) can subscribe.

---

# SECTION 8 — Visual Dashboard

The report renders an executive-grade **HTML dashboard**. Charts are inline SVG (no runtime JS deps) so the PDF exporter can render them faithfully.

| Widget | Chart type | Data source |
|---|---|---|
| Traffic over time | Area | JMeter throughput |
| Concurrency | Line | JMeter active threads |
| Response Time (p50 / p95 / p99) | Multi-line | JMeter aggregate |
| Errors over time | Stacked area (by error_signature) | JMeter + Splunk |
| Latency heatmap | Time × latency bucket | JMeter |
| Infrastructure grid | Card grid (CPU / Mem / Disk / Net) | O11y + CW |
| Service dependency map | Sankey | O11y traces |
| Health-score gauge | Gauge (0–100) | Composite (§9) |
| AI Risk meter | Semicircle gauge | Verdict compiler |
| Timeline correlation | Timeline lanes: samples, logs, deploys, alarms | All sources |
| Incident timeline | Vertical event list | Correlated events |
| Waterfall | Per-transaction breakdown | JMeter subresults |
| Trend charts | Sparkline per transaction | Baseline store history |
| Executive KPIs strip | KPI cards | §1.9 |

**Design language:** dark hero header, light content, colour-blind-safe palette (Okabe-Ito), monospaced numerics, no gradients other than the hero. Print-safe.

---

# SECTION 9 — Overall AI Health Score

All scores are on `[0, 100]`. Composite scores are weighted geometric means (so a single 0 collapses the whole score — safer than arithmetic mean).

### 9.1 Component scores

| Score | Formula (0–100) |
|---|---|
| **Performance Score** | `100 − clamp(0, 100, (p95_ratio − 1) × 50)` where `p95_ratio = observed_p95 / sla_p95` |
| **Infrastructure Score** | `100 − max(cpu_sat, mem_sat, disk_sat, net_sat) × 100`, sat = `p95(util) / threshold` |
| **Application Score** | `100 − w_err × error_rate × 100 − w_gc × gc_pause_ratio − w_pool × pool_saturation` |
| **Database Score** | `100 − w_slow × slow_query_ratio − w_dead × deadlock_rate − w_conn × conn_pool_saturation` |
| **Observability Score** | `%(services_with_all_signals_present)` — measures how much the platform could *see* |
| **Scalability Score** | `100 × R²` of linear fit of p95 vs concurrency during ramp-up (deviation ↓ score) |
| **Reliability Score** | `100 × (1 − restart_count / target_replicas)` per service, aggregated |
| **Availability Score** | `100 × (successful_requests / total_requests)` weighted by business_criticality |

### 9.2 Composite

```
Overall Performance Health =
    ( Performance × Infrastructure × Application × Database × Scalability ) ^ (1/5)

Overall Release Readiness =
    min( Overall Performance Health,
         SLA Pass % × 100,
         Regression Pass × 100,
         Observability Score )

Overall Production Confidence =
    Overall Release Readiness × prior( historical_go_rate_for_this_test_plan )

Overall Risk Score = 100 − Overall Production Confidence
```

### 9.3 Verdict thresholds (tenant-configurable)

| Verdict | Rule |
|---|---|
| **GO** | Production Confidence ≥ 85 AND no CRITICAL finding |
| **GO WITH CONDITIONS** | 70 ≤ Confidence < 85 AND no CRITICAL finding |
| **NO-GO** | Confidence < 70 OR any CRITICAL finding |
| **INSUFFICIENT DATA** | Observability Score < 60 |

---

# SECTION 10 — Plugin Enhancement Design (v2 Architecture)

### 10.1 Deployment topology

```
                    ┌─────────────────────────────────────┐
                    │   JMeter JVM (Load Generator)       │
                    │   ┌───────────────────────────────┐ │
                    │   │ SmartObservabilityBackend     │ │  ← Backend Listener (existing)
                    │   │  Listener (thin, async)       │ │
                    │   └───────────────┬───────────────┘ │
                    └───────────────────┼─────────────────┘
                                        │ gRPC / HTTPS (mTLS)
                                        ▼
       ┌──────────────────────────────────────────────────────────────┐
       │        SmartObservability Analysis Service (Spring Boot)     │
       │                                                              │
       │  Collectors ─► Correlation ─► AI ─► Report ─► Export ─►      │
       │                                                              │
       │  Backed by:                                                  │
       │   • PostgreSQL (runs, findings, baselines)                   │
       │   • Redis    (cache, dedup)                                  │
       │   • S3       (artefacts, PDF/PPTX)                           │
       │   • Vault    (secrets)                                       │
       └──────────────────────────────────────────────────────────────┘
                     │
                     ▼
      Splunk Enterprise / Splunk O11y / AWS CloudWatch
```

Rationale: the plugin runs *inside* JMeter and must stay lightweight. Heavy work (LLM, PDF, PPTX, forecasting) is pushed to the Analysis Service so it cannot slow the load generator.

### 10.2 Java package layout (v2)

```
com.smartjmeter/
├── plugin/                             # JMeter-side, runs in JMeter JVM
│   ├── SmartObservabilityBackendListener.java   (exists)
│   ├── config/PluginConfig.java                 (exists, extended)
│   ├── model/JMeterMetric.java                  (exists)
│   ├── model/TestRunContext.java                (new: run id, plan hash)
│   ├── collector/JmeterAggregateCollector.java  (wraps MetricAggregator)
│   ├── collector/JmeterProcessTelemetry.java    (new: heap/cpu/gc of JMeter JVM)
│   ├── transport/AnalysisServiceClient.java     (new: gRPC to analysis svc)
│   ├── transport/HecUploader.java               (existing HEC clients moved here)
│   └── report/ReportGenerator.java              (exists, extended)
│
├── analysis/                           # Runs in Analysis Service
│   ├── boot/AnalysisApplication.java            (Spring Boot main)
│   ├── api/RestController.java                  (REST: /api/v2/reports, /verdict)
│   ├── api/GrpcService.java                     (bi-di stream from plugin)
│   │
│   ├── collector/SplunkExportClient.java        (bulk search jobs)
│   ├── collector/O11yTraceClient.java           (service maps, traces)
│   ├── collector/CloudWatchMetricsClient.java   (AWS SDK v2)
│   ├── collector/CloudWatchLogsInsightsClient.java
│   ├── collector/DeploymentMetadataClient.java  (Argo/GitOps API)
│   │
│   ├── correlate/EventNormalizer.java
│   ├── correlate/TimelineAligner.java
│   ├── correlate/RuleEngine.java                (+ rules/*.java)
│   ├── correlate/TraceJoiner.java
│   ├── correlate/ConfidenceScorer.java
│   │
│   ├── ai/LlmClient.java                        (existing)
│   ├── ai/PromptBuilder.java                    (existing, richer schema)
│   ├── ai/InsightExtractor.java                 (JSON-schema-validated output)
│   ├── ai/CapacityForecaster.java               (quantile regression)
│   ├── ai/AnomalyDetector.java                  (rolling z-score + isolation forest)
│   │
│   ├── score/HealthScorer.java
│   ├── score/VerdictCompiler.java
│   │
│   ├── report/HtmlRenderer.java                 (Thymeleaf)
│   ├── report/PdfExporter.java                  (openhtmltopdf)
│   ├── report/PowerPointExporter.java           (Apache POI XSLF)
│   ├── report/JsonExporter.java
│   ├── report/CsvExporter.java
│   ├── report/ChartRenderer.java                (batik SVG)
│   │
│   ├── storage/PostgresRunRepository.java
│   ├── storage/S3ArtefactStore.java
│   ├── storage/RedisCache.java
│   ├── storage/VaultSecretProvider.java
│   │
│   ├── security/AuthN.java                      (OIDC + PAT)
│   ├── security/AuthZ.java                      (Casbin ABAC)
│   ├── security/AuditLog.java                   (JSONL + Splunk HEC)
│   │
│   ├── integration/JiraClient.java
│   ├── integration/ServiceNowClient.java
│   ├── integration/SlackClient.java
│   ├── integration/TeamsClient.java
│   ├── integration/EmailClient.java             (SMTP + SendGrid)
│   │
│   └── util/HttpClientFactory.java              (existing)
```

### 10.3 Interfaces (contract-first)

```java
public interface MetricsCollector<T> {
    CollectionResult<T> collect(CollectionRequest req);   // idempotent, cancellable
}

public interface Correlator {
    List<Finding> correlate(NormalizedEventStream events, RunContext ctx);
}

public interface Scorer {
    HealthScoreBundle score(RunAggregate agg, List<Finding> findings);
}

public interface Reporter {
    ReportArtefact render(ReportContext ctx, ReportFormat format);
}

public interface Exporter {
    void export(ReportArtefact art, ExportTarget target);
}
```

Each impl is a Spring `@Component` with `@Qualifier` so tenants can swap collectors (e.g. New Relic replaces O11y) without touching the pipeline.

### 10.4 Cross-cutting

- **Thread safety:** all collectors immutable; use `CopyOnWriteArrayList` on the plugin side, virtual threads (`Thread.ofVirtual().factory()`) on the service side.
- **Retries:** Resilience4j on every external call — exponential backoff, circuit breaker per external system.
- **Caching:** Redis with 10-min TTL on Splunk query results keyed by SPL hash + window; keeps the same test replayable without hammering Splunk.
- **Backpressure:** plugin uses the existing bounded queue + drop-with-WARN, gRPC stream client-side flow control.
- **Configuration:** JMX file references a `pluginProfile` (dev / stage / prod). Profile YAML lives in Vault / Git.
- **Security:** never store keys in JMX; plugin only holds a short-lived OIDC token issued by the Analysis Service.
- **Auth:** OIDC (Keycloak / Okta) between plugin and service; JWT with `aud=perf-platform`. AuthN → SPIFFE-compatible mTLS optional.
- **AuthZ:** Casbin ABAC — `{subject, tenant, project, action}`.
- **Audit:** every collector call, every LLM call, every export → `audit-log` HEC-forwarded to Splunk.
- **Observability of the platform itself:** the platform is instrumented with OpenTelemetry (traces + metrics), so ops teams can operate it with the same signals it produces.
- **Plugin lifecycle:** `setupTest` → validate config → warm connections; `handleSampleResults` → enqueue only; `teardownTest` → flush → send `runFinished` event; the Analysis Service assembles asynchronously; the plugin polls for `report_ready` with `?timeout=90s` long-poll.
- **Performance:** plugin adds < 2 % overhead at 5 000 TPS (validated via HEC batching + async queue; already implemented in v1.0).
- **Scalability:** Analysis Service is horizontally scalable; each run is a message on a durable queue (SQS / Kafka). Autoscale on queue depth.

---

# SECTION 11 — Plugin Workflow (Sequence)

```
Perf Engineer               JMeter JVM (Plugin)          Analysis Service          Splunk/O11y/CW          LLM
     │                            │                            │                        │                    │
     │ Start test                 │                            │                        │                    │
     ├───────────────────────────▶│                            │                        │                    │
     │                            │ setupTest()                │                        │                    │
     │                            ├──── runStart(runId) ──────▶│                        │                    │
     │                            │                            │ createRun(runId)       │                    │
     │                            │                            │────────┐               │                    │
     │                            │                            │◀───────┘               │                    │
     │                            │◀──── ok ──────────────────│                        │                    │
     │                            │                            │                        │                    │
     │                            │ handleSampleResults(...)   │                        │                    │
     │                            │  (loop, HEC batched)       │                        │                    │
     │                            ├──── metrics stream ───────▶│                        │                    │
     │                            │                            │─── HEC (fanout) ──────▶ Splunk              │
     │                            │                            │                        │                    │
     │                            │ teardownTest()             │                        │                    │
     │                            ├──── runFinished ──────────▶│                        │                    │
     │                            │                            │──── SPL search ───────▶│                    │
     │                            │                            │◀── log events ─────────│                    │
     │                            │                            │──── O11y query ───────▶│                    │
     │                            │                            │◀── metric points ──────│                    │
     │                            │                            │──── CloudWatch ───────▶│                    │
     │                            │                            │◀── datapoints ─────────│                    │
     │                            │                            │                        │                    │
     │                            │                            │ correlate + rule engine│                    │
     │                            │                            │────────┐               │                    │
     │                            │                            │◀───────┘               │                    │
     │                            │                            │ buildPrompt            │                    │
     │                            │                            ├──── chat.completions ──────────────────────▶│
     │                            │                            │◀────────── narrative + insights ────────────│
     │                            │                            │ score + verdict compiler                    │
     │                            │                            │ render HTML / PDF / PPTX / JSON / CSV       │
     │                            │                            │ push to S3, notify integrations             │
     │                            │◀── report_ready + URL ────│                        │                    │
     │◀── report URL + verdict ──│                            │                        │                    │
     │                            │                            │                        │                    │
```

Time budget target: **≤ 90 seconds from test end to report URL** for a 30-minute perf run.

---

# SECTION 12 — Output Deliverables

Every run yields, deterministically, the following artefacts. All are stored in S3 with a signed URL and posted to Slack / Teams / email.

| Artefact | Format | Renderer | Consumers |
|---|---|---|---|
| Executive PDF | PDF/A | openhtmltopdf (HTML→PDF) | CIO, CTO, execs |
| HTML Dashboard | HTML + inline SVG | Thymeleaf + Batik | anyone with browser |
| PowerPoint Presentation | .pptx | Apache POI XSLF | Steering-committee decks |
| Detailed Engineering Report | HTML + PDF | Thymeleaf | Perf engineers, SRE |
| JSON Report | JSON (schema `report.v2.json`) | Jackson | Downstream systems, ML training |
| CSV Report | CSV (per-transaction + per-metric) | Jackson CSV | Excel-native teams, auditors |
| Management Dashboard | HTML | Thymeleaf | Delivery managers |
| AI Findings Report | Markdown + JSON | Custom | Bug tracker imports |
| Release Recommendation Report | 1-pager PDF | HTML→PDF | Change Advisory Board |
| Capacity Planning Report | PDF + XLSX | POI XSSF | Capacity planners |
| Root Cause Report | PDF + JSON | Custom | Post-mortem docs |

Signed hash of the underlying JMeter / Splunk / O11y / CW inputs is embedded in every artefact so evidence can be verified after the fact.

---

# SECTION 13 — Technology Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 21** | LTS, virtual threads, records, text blocks (already in use) |
| Web framework | **Spring Boot 3.3** | Actuator, virtual-thread executor, OIDC support |
| Build | **Maven 3.9** + `maven-shade-plugin` | Already established |
| gRPC | **grpc-java + protobuf** | Efficient plugin ↔ service stream |
| REST | **Spring Web** + **springdoc-openapi** | API contract auto-generated |
| Observability SDK | **OpenTelemetry Java Agent** | Same signals we consume |
| Splunk SDK | **splunk-sdk-java** for advanced; keep raw HTTP for HEC | Existing raw HTTP proven fine |
| AWS SDK | **AWS SDK v2** (`cloudwatch`, `logs`, `sts`, `s3`) | Modern async, low deps |
| JSON | **Jackson 2.17** | Already pinned |
| Excel | **Apache POI 5** (XSSF) | Industry standard |
| PDF | **openhtmltopdf** (HTML→PDF) | Print-safe, uses same HTML source |
| PPTX | **Apache POI XSLF** | Only serious PPTX in Java |
| Charts | **Batik SVG** rendered server-side | No JS deps in PDF export |
| LLM | Multi-provider: OpenAI, Anthropic, Gemini, Grok (xAI), Groq (LPU) via `LlmClient` (existing) | Already implemented; add function-calling to enforce JSON schema |
| Vector DB (opt) | **pgvector** on PostgreSQL | Reuse Postgres — no new operational surface |
| Cache | **Redis 7** | Standard, cheap |
| Primary DB | **PostgreSQL 16** | Runs, findings, baselines, audit |
| Object store | **S3** (or S3-compatible: MinIO for on-prem) | Report artefacts |
| Secrets | **HashiCorp Vault** (or AWS Secrets Manager) | Never in JMX |
| Config | **Spring Cloud Config** backed by Git | GitOps-friendly |
| Auth | **OIDC** (Keycloak / Okta / Azure AD) | Enterprise SSO |
| Authorization | **Casbin ABAC** | Fine-grained, YAML policy |
| Packaging | Fat JAR for plugin, Docker + Helm for service | Already have fat JAR |
| CI/CD | **GitHub Actions** or **Jenkins** — build, test, JAR, container, Helm release; Trivy/CodeQL/OWASP dependency-check gate | Standard |
| Test strategy | JUnit 5 unit; Testcontainers for Postgres/Redis/S3 integration; WireMock for Splunk/CW; contract tests via Spring Cloud Contract; k6 or JMeter self-test at 5 k TPS | Already 42/42 unit-tested |

---

# SECTION 14 — Future Roadmap

### 14.1 Near term (0–6 months)

- **GenAI Chat Assistant** — "Why did checkout regress last Friday?" — RAG over the JSON reports stored in Postgres/pgvector.
- **Natural-Language Report Query** — CLI + Slack bot; converts NL → SPL / SignalFlow / CloudWatch Metrics Insights query.
- **Automatic RCA loop** — RCA output becomes a rule-engine rule for the next run (self-learning rulebook, human-approved).
- **Automatic Capacity Planning** — quantile-regression forecast against forecast traffic curves loaded from finance / product plans.
- **Regression prediction** — sequence model over the baseline history predicting probability of the next run failing SLA.
- **Self-healing suggestions** — parameterised runbook links per rule (`R-DB-SLOW` → "raise `hikari.max` from 20 to 30 via values.yaml").

### 14.2 Medium term (6–12 months)

- **Multi-cloud** collectors:
  - **Azure Monitor** (`azure-monitor-query`)
  - **Google Cloud Monitoring** (Cloud Ops SDK)
- **APM connectors**:
  - **Dynatrace** (SmartScape, problems API)
  - **Datadog** (metrics + logs + APM)
  - **New Relic** (NerdGraph)
- **OSS observability**:
  - **Prometheus / Thanos** (PromQL)
  - **Grafana Loki** (LogQL)
  - **Elastic APM / ELK**
- **ITSM integrations**:
  - **ServiceNow** — auto-open Change Request with the report attached
  - **Jira** — auto-create bug per critical finding with reproducer

### 14.3 Long term (12–24 months)

- **Notifications hub**: Slack, Microsoft Teams, Email, PagerDuty, Opsgenie — all routed via a single `Notifier` interface with template packs per audience (Exec vs Perf vs SRE).
- **Continuous performance** — plugin runs as a **daemon** in production shadow-traffic mode, generating hourly readiness reports.
- **Perf-CI Gate** — GitHub Actions / Jenkins step: PR blocks if verdict != GO.
- **What-if simulator** — LLM-driven counterfactual: "If we halved p95 on `/pricing`, would overall verdict flip to GO?"
- **On-prem air-gapped LLM** — plug in an internal Llama 3.3 70B (Groq or self-hosted vLLM) so regulated tenants keep everything inside the perimeter.

---

# APPENDIX A — Data Flow Diagram

```
   ┌──────────────┐        ┌──────────────┐        ┌────────────────┐        ┌─────────────┐
   │   JMeter     │ metrics│   Backend    │ gRPC   │   Analysis     │        │    Splunk   │
   │  Test Plan   ├───────▶│  Listener    ├───────▶│    Service     │───────▶│   HEC + REST│
   └──────────────┘        └──────────────┘        └───────┬────────┘        └─────────────┘
                                                            │
                                                            │
                             ┌──────────────┐               │                 ┌─────────────┐
                             │  Deployment  │               │                 │  Splunk O11y│
                             │  metadata    │──────────────▶│                 │  Cloud APIs │
                             │  (Argo / GH) │               │◀────────────────┤             │
                             └──────────────┘               │                 └─────────────┘
                                                            │
                                                            │                 ┌─────────────┐
                                                            │                 │  AWS        │
                                                            │◀────────────────┤ CloudWatch  │
                                                            │                 │  Logs + Mets│
                                                            │                 └─────────────┘
                                                            │
                              ┌──────────────┐              │                 ┌─────────────┐
                              │   Vault /    │──────────────▶│◀───────────────│    LLM      │
                              │   Secrets    │  short-lived │      chat      │  providers  │
                              └──────────────┘  tokens      │                 └─────────────┘
                                                            │
                       ┌────────────────────────────────────┼────────────────────────┐
                       │                                    │                        │
                       ▼                                    ▼                        ▼
                ┌─────────────┐                    ┌──────────────┐          ┌───────────────┐
                │  PostgreSQL │                    │   S3 (PDF /  │          │  Slack/Teams/ │
                │  + pgvector │                    │  PPTX / HTML)│          │  Email/Jira/  │
                └─────────────┘                    └──────────────┘          │  ServiceNow   │
                                                                             └───────────────┘
```

---

# APPENDIX B — Implementation Roadmap (12-week plan)

| Sprint | Deliverables | Exit criteria |
|---|---|---|
| **S1** | Analysis Service skeleton, Postgres, S3, Vault wiring, OIDC | `POST /api/v2/runs` accepts a run, persists it, uses secrets from Vault |
| **S2** | Plugin ↔ Service gRPC stream, run lifecycle, backpressure | 5 k TPS load test with < 2 % plugin overhead |
| **S3** | New collectors: `CloudWatchMetricsClient`, `O11yTraceClient`, `SplunkExportClient` | Sample real-account run produces normalized events |
| **S4** | `EventNormalizer`, `TimelineAligner`, `RuleEngine` with 6 core rules | Findings JSON reproducible for a seeded dataset |
| **S5** | `ConfidenceScorer`, `HealthScorer`, `VerdictCompiler` | Verdict deterministic on seeded dataset; unit-tested |
| **S6** | `PromptBuilder v2`, JSON-schema output, evidence linking | LLM output validates 100 % of the time in CI |
| **S7** | `HtmlRenderer`, `ChartRenderer` (Batik), full styled report | Report generated end-to-end for reference test |
| **S8** | `PdfExporter`, `PowerPointExporter`, `CsvExporter`, `JsonExporter` | Every deliverable in §12 produced for reference test |
| **S9** | ITSM: `JiraClient`, `ServiceNowClient`, `SlackClient`, `TeamsClient`, `EmailClient` | Report auto-delivered to configured targets |
| **S10** | Security hardening: Casbin, audit log HEC, dependency & container scans, Trivy in CI | Passes internal AppSec review |
| **S11** | Multi-tenant: per-tenant Vault paths, per-tenant weights.yaml, quota management | Two tenants isolated in staging |
| **S12** | Perf, chaos + resiliency tests, docs, GA readiness | GA sign-off with runbooks & SRE handover |

---

# APPENDIX C — Best Practices & Enterprise Recommendations

1. **Deterministic before probabilistic.** Rule engine first, LLM last. Every LLM claim carries an evidence pointer.
2. **Fail closed on secrets.** Missing key → static analysis fallback with WARN; never leak, never crash the run.
3. **Idempotent runs.** `runId = uuidv7(planHash + startTs + env)`; re-executing produces byte-identical evidence set (subject to source availability).
4. **Contract-first APIs.** Proto + OpenAPI in the repo. Breaking changes gated by contract tests.
5. **Explainability is a feature, not documentation.** Verdict includes machine-readable "why", not just a number.
6. **Everything is a config change.** Weights, thresholds, SLA per transaction, business impact per error → YAML in Git, no code deploys for tuning.
7. **Observability of the observer.** The platform emits its own OTEL metrics and logs to Splunk. If the platform is misbehaving, ops teams see it in the same pane they already use.
8. **Data minimisation.** Never ship raw PII to LLMs — hash `user_id`, redact known PII fields via a `redaction.yaml` before prompt assembly.
9. **Backwards compatibility of reports.** JSON export follows a versioned schema. Old reports render forever.
10. **Human-in-the-loop.** Every self-healing suggestion links to a runbook and is *never* auto-applied without a human tick.
11. **Cost governance.** Track LLM token spend per run, per tenant, per model in Postgres; alarm on drift.
12. **Golden signals for verdicts.** RED + USE + Apdex + business impact are always present; missing signals reduce the Observability Score directly.
13. **Everything reproducible offline.** Given the JSON export, the HTML/PDF/PPTX can be regenerated without hitting Splunk or the LLM again.
14. **Least privilege.** Splunk read-only role, CloudWatch `ReadOnlyAccess` scoped to test env, LLM keys per tenant. No wildcards.
15. **Design for regulators.** Signed report hash, immutable S3 versioning, WORM retention 7 years by default.

---

## Sign-off matrix (page 1 of every report)

| Role | Name | Verdict | Signed at |
|---|---|---|---|
| Performance Engineering Lead | — | GO / GWC / NO-GO | — |
| SRE On-Call | — | Ack | — |
| Delivery Manager | — | Approved | — |
| Product Owner | — | Aware | — |
| Change Advisory Board | — | Approved | — |

*End of document.*
