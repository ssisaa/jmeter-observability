# JMeter Smart Observability AI Plugin - PRD

## Vision
Answer "Can we safely deploy this release into Production?" without a human touching a log line.

## Architecture (delivered, in-plugin)
Java 21 Maven fat JAR (~16 MB, AWS SDK bundled), running as a JMeter Backend Listener. All enterprise capabilities delivered without a separate service; see `docs/ENTERPRISE_ARCHITECTURE.md` for the v2 design and roadmap.

## Package layout
- `plugin.SmartObservabilityBackendListener`
- `config.PluginConfig` (35+ GUI params)
- `model.JMeterMetric`
- `ai.MetricAggregator` (full percentile ladder + stddev + median + IQR + apdex + top-error-signatures)
- `ai.LlmClient` (OpenAI / Anthropic / Gemini / Grok / Groq)
- `ai.PromptBuilder` (JSON-output contract with schema)
- `ai.InsightExtractor` (strict-JSON parse w/ markdown fallback)
- `ai.AIAnalyzer`
- `score.Finding`, `score.HealthScores`, `score.Verdict`
- `score.HealthScorer` (8 component scores + weighted-geometric composite)
- `score.VerdictCompiler` (GO / GO_WITH_CONDITIONS / NO_GO / INSUFFICIENT_DATA + gates + rollout + rollback triggers)
- `correlate.RuleEngine` (10 deterministic rules: R-CODE-REG, R-CODE-REG-ERR, R-ERR-RATE, R-LATENCY, R-TAIL, R-OBS-GAP, R-INFRA-CPU, R-INFRA-MEM, R-GC-PAUSE, R-K8S-RESTART, R-CW-ALARM)
- `correlate.CorrelationEngine` (Splunk log-window merge)
- `splunk.SplunkHECClient`, `splunk.AsyncBatchingHECClient`, `splunk.SplunkSearchClient` (with job-cancel-on-timeout)
- `o11y.SplunkO11yMetricsClient`
- `cloudwatch.CloudWatchMetricsCollector` (AWS SDK v2 - metrics + alarm state)
- `baseline.BaselineStore`, `baseline.BaselineDiff`
- `report.ReportGenerator` (styled HTML with 12 sections: hero, KPIs, verdict badge, health-score gauges, findings table, per-transaction, baseline diff, correlation, o11y, cloudwatch, business impact + capacity, AI analysis, appendix)
- `report.Markdown` (safe MD -> HTML)
- `report.JsonExporter` (schema `report.v2.json`)
- `report.CsvExporter` (per-transaction + overall)
- `store.LocalJsonStore` (NDJSON)
- `util.HttpClientFactory` (TLS-insecure toggle)

## Live end-to-end verification (this session)
Enterprise smoke run with Groq (llama-3.3-70b-versatile) against `smoke.jmx`:
- **Verdict:** `NO_GO` at Production Confidence 53.3 / Risk 46.7 (correct — one transaction seeded to 100 % failure).
- **Findings:** R-ERR-RATE fired CRITICAL (0.95 confidence, evidence `aggregate.error_rate=0.5`); R-OBS-GAP fired MEDIUM (0.55, evidence `correlation.windows.event_count=0`).
- **11 Health-score gauges** rendered with colour-coded fill bars.
- **Gates:** SLA, Regression, Performance, Infrastructure, Application, Observability all passed; "No critical findings" failed.
- **All artefacts written** under `Output_Directory`: HTML (17 KB), JSON envelope (7.5 KB), CSV (356 B), NDJSON, baseline, correlation, o11y, cloudwatch (empty when disabled).
- Screenshot captured at `/tmp/enterprise_report_top.png`.

## Tests
- **51/51 pass** (`mvn clean test`) as of v2.0.0.

## Deliverable
- `target/jmeter-smart-observability-plugin-2.0.0.jar` (~41 MB fat jar) - drop into `$JMETER_HOME/lib/ext/`.
- `docs/ENTERPRISE_ARCHITECTURE.md` (52 KB) - Principal-Architect design.

## v2.0.0 (this release - P0 + P1)
Feb 2026:
- **PDF export** — `report.PdfExporter` renders the HTML report via openhtmltopdf/PDFBox.
- **PPTX export** — `report.PptxExporter` produces a 5-slide executive deck (title / verdict / scores / findings / rollout) via Apache POI XSLF.
- **Notifiers** — `notify.Notifiers` ships five sinks: Slack webhook, Teams MessageCard, SMTP email (Jakarta Mail), Jira REST v3, ServiceNow Table API. All best-effort, never throw.
- **CI gate** — `Fail_On_Verdict` param writes `ci-gate.json` at teardown; standalone `com.smartjmeter.ci.CiGate` CLI evaluates it and exits 0 (GO) / 2 (GO_WITH_CONDITIONS) / 3 (NO_GO) / 1 (unknown).
- **Extra metric sources scaffolding** — `metrics.GenericHttpMetricsCollector` covers Prometheus, Loki, Elastic, Datadog, New Relic, Dynatrace, Azure Monitor, GCP Ops through a single generic HTTP+JSON shape, driven by the `Metric_Sources_Json` param.

## Not yet implemented (backlog)
- **v2.0.1 (P2)** — Wire named per-backend collectors (Azure Monitor, GCP Ops, Datadog, Dynatrace, New Relic, Prometheus, Loki, Elastic) into the enterprise report sections + dedicated health scorers + explicit rule engine coverage.
- **v2.0.2 (P3)** — Waterfall / Sankey / dependency map SVG charts; sequence-model regression prediction + quantile-regression capacity forecast; separate Analysis Service for horizontal scale.

## Personas served
- CIO / CTO / Chief Architect / Engineering Director: page-one verdict + health scores
- Delivery Manager / Product Owner: gates, rollout, rollback, business impact
- Perf Engineer: findings table, per-txn stats, baseline diff, evidence pointers
- SRE: JSON envelope for downstream automation
