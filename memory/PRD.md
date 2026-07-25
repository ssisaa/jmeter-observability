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
- **42/42 pass** (`mvn clean test`).

## Deliverable
- `target/jmeter-smart-observability-plugin-1.0.0.jar` (~16 MB with AWS SDK) - drop into `$JMETER_HOME/lib/ext/`.
- `docs/ENTERPRISE_ARCHITECTURE.md` (52 KB) - Principal-Architect design.

## Not yet implemented (backlog)
- PDF + PPTX exports (heavy libs; HTML+CSS is print-safe today)
- Analysis Service split for horizontal scale (documented in the architecture doc)
- Slack / Teams / Jira / ServiceNow notification integrations
- Waterfall / Sankey / dependency map SVG charts
- Multi-cloud collectors (Azure Monitor, GCP Ops)
- APM connectors (Datadog, Dynatrace, New Relic)
- Prometheus / Elastic / Loki collectors
- Regression prediction + capacity forecast ML models
- CI gate mode (non-zero exit on `NO_GO`)

## Personas served
- CIO / CTO / Chief Architect / Engineering Director: page-one verdict + health scores
- Delivery Manager / Product Owner: gates, rollout, rollback, business impact
- Perf Engineer: findings table, per-txn stats, baseline diff, evidence pointers
- SRE: JSON envelope for downstream automation
