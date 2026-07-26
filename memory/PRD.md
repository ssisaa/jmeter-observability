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
- **92/92 pass** (`mvn clean test`) as of v2.0.7.
- **14/14 O11y client tests pass** (`SplunkO11yMetricsClientTest` + `SplunkO11yMetricsClientPostTest`).
- **11/11 backend pytest pass** (`/app/backend/tests/test_downloads.py`).

## Deliverable
- `target/jmeter-smart-observability-plugin-2.0.7.jar` (~18 MB fat jar) - drop into `$JMETER_HOME/lib/ext/`.
- **Downloadable via preview URL**: `/api/downloads/plugin.jar`, `/api/downloads/demo/*` (HTML/JSON/CSV/**MD**), `/api/downloads/smoke/*`, `/api/downloads/docs/*`, `/api/downloads/docker/*`, `/api/downloads/templates/*`.
- **Frontend home page** now shows 6 sections and reports v2.0.7.
- `docs/PARAMETERS.md` (~19 KB) - every Backend Listener parameter documented with sample values.
- `docker/` bundle + `templates/smart-observability-default.jmx` JMeter template.
- `docs/ENTERPRISE_ARCHITECTURE.md` (52 KB) - Principal-Architect design.

## v2.0.7 (this release)
Feb 2026:
- **Splunk O11y endpoint FIXED (real fix)** - switched to `GET /v1/timeserieswindow`, the only bounded batch endpoint. `/v2/timeserieswindow` does not exist (returned 404); SignalFlow was ruled out because it streams indefinitely, unsuitable for JMeter start-to-end collection. See `SplunkO11yMetricsClient.java`.
- **Bottleneck logs during teardown** - every rule-engine `Finding` is logged with severity, category, rule id, confidence, title and evidence (WARN for CRITICAL/HIGH, INFO for MEDIUM/LOW). Operators see what tripped without opening the report.
- **Friendlier metric spec** - bare metric names (`cpu.utilization`) are auto-wrapped as `sf_metric:"..."`; SignalFlow programs (`data('foo').publish()`) are downgraded to the metric name (with a warning); raw filter expressions pass through unchanged.
- **Resolution auto-snap** - requested resolution is rounded UP to the nearest valid `/v1/timeserieswindow` bucket (1s / 1m / 5m / 1h) so the API never rejects it.

## v2.0.6 (previous release)
Feb 2026:
- **Splunk O11y bugfix** - removed the SignalFlow fallback (returned 406 on some realms). Batch-mode `POST /v2/timeserieswindow` is the sole path.
- **Report chart cleanup** - removed Latency percentiles, Latency distribution and Throughput-per-transaction bars. Remaining charts get full row width + taller 260x1400 SVG canvas.
- **CloudWatch panel** - every fetched CloudWatch metric is now charted; O11y and CW panels show a "Test window" banner.
- **Confluence-ready Markdown export** - `MarkdownReportExporter` produces `Performance_Report.md` with tables + bullets (no HTML/emoji). New `Markdown_Report_Path` param.

## v2.0.4 - v2.0.5 (previous releases)
Feb 2026:
- **PDF and PPTX exports REMOVED** - HTML-only reporting (fat jar shrank from 41 MB to 18 MB, no openhtmltopdf / POI surface).
- **Management-oriented Visual Analytics** - KPI hero strip (samples / error rate / p95 / apdex) + TPS/HPS/RTS/error-rate line charts + latency percentile grouped bars + latency distribution histogram + top-N throughput bars. Verdict-Sankey and dependency-map removed.
- **Baseline comparison chart** - current run's p95 next to previous baseline and historic average, coloured by delta, with a spark-line of the last N runs. Driven by `CapacityForecast.loadHistorySnapshots()`.
- **Time-series bucketing in `MetricAggregator`** - 1-second buckets (capped at 300 to stay readable). Powers TPS/HPS/RTS/error-rate charts.
- **Parameter reference doc** - `docs/PARAMETERS.md` with every Backend Listener field, sample value and where to source it.
- **One-click Docker demo** - Docker Compose bundle spins Analysis Service + Splunk HEC mock + JMeter runner. New team goes zero-to-full-demo in ~2 minutes.
- **Java 17 target** - dropped from Java 21 to Java 17 for wider CI compatibility (no 21-only features were in use).

## v2.0.3 (this release)
Feb 2026:
- **Download endpoints fixed** - `/api/plugin/info`, `/api/downloads/plugin.jar`, `/api/downloads/demo/{name}`, `/api/downloads/smoke/{name}`. Frontend home page renders download links, changelog and section IDs.
- **Rolling baselines** - `CapacityForecast.prune(dir, maxCount, maxAgeDays)`; params `Baseline_History_Max` (default 100) and `Baseline_History_Max_Days` (default 90). Prune runs after every snapshot.
- **Alert cooldowns** - `notify.NotifierCooldown` persists last-fire timestamps per (sink, verdict, testName) under `notifier-cooldowns.json`; param `Notifier_Cooldown_Seconds` (default 3600).
- **Analysis Service split** - `analysis.AnalysisServer` (main class) exposes `/healthz` + `POST /analyze` via JDK HttpServer; `analysis.AnalysisServiceClient` used when `Analysis_Service_Url` is set. All JMeter runners in a CI fleet can share one LLM budget.
- **Public demo report** - `demo.DemoReport` generates HTML/PDF/PPTX/JSON/CSV with realistic checkout-load data; used by both the download page and buyer preview.
- **PDF exporter robustness** - `PdfExporter.normaliseForXhtml` fixes lowercase doctype + named entities so the full report (not just simple test HTML) renders to PDF.

## v2.0.0 (previous release)
Feb 2026:
- **PDF export** — `report.PdfExporter` renders the HTML report via openhtmltopdf/PDFBox.
- **PPTX export** — `report.PptxExporter` produces a 5-slide executive deck via Apache POI XSLF.
- **Notifiers** — `notify.Notifiers` ships five sinks: Slack, Teams, SMTP email (Jakarta Mail), Jira REST v3, ServiceNow Table API.
- **CI gate** — `Fail_On_Verdict` param + standalone `com.smartjmeter.ci.CiGate` CLI (exit 0/2/3).
- **`GenericHttpMetricsCollector`** — unified HTTP+JSON shape for 8 metric backends.

## Not yet implemented (backlog)
- **Analysis Service split** for horizontal scale (documented in `ENTERPRISE_ARCHITECTURE.md`).

## Personas served
- CIO / CTO / Chief Architect / Engineering Director: page-one verdict + health scores
- Delivery Manager / Product Owner: gates, rollout, rollback, business impact
- Perf Engineer: findings table, per-txn stats, baseline diff, evidence pointers
- SRE: JSON envelope for downstream automation
