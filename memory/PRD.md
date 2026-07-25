# JMeter Smart Observability AI Plugin - PRD

## Original Problem Statement
Production-grade JMeter Backend Listener plugin. Phase 1 MVP: capture perf metrics,
local JSON store, Splunk HEC forward, JMeter GUI config, AI skeleton, HTML report.
Phase 2: Splunk Search API + log correlation. Phase 3: Splunk Observability Cloud
metrics. Phase 4: LLM-backed root cause analysis.

## Architecture
- Maven Java 21 (JMeter 5.6.3 `provided` + `ApacheJMeter_components`)
- Fat JAR via `maven-shade-plugin` (Jackson 2.17.2 + HttpClient5 shaded; JMeter external)
- Package layout: `SmartObservabilityBackendListener`, `config.PluginConfig`,
  `model.JMeterMetric`, `splunk.SplunkHECClient`, `splunk.SplunkSearchClient`,
  `correlate.CorrelationEngine`, `o11y.SplunkO11yMetricsClient`,
  `ai.LlmClient`/`AIAnalyzer`/`PromptBuilder`/`MetricAggregator`,
  `report.ReportGenerator`, `store.LocalJsonStore`.
- All external calls use JDK `HttpClient`; every client is safe on missing config
  (returns empty result, never throws to caller).

## Implemented

### 2026-01 (Phase 1) ✅
- Backend listener, HEC client, local NDJSON store, HTML report, AI skeleton.
- 6 tests, fat JAR ~4.1 MB.

### 2026-01 (Phase 2) ✅
- `SplunkSearchClient`: create-job → poll `isDone` → fetch-results (JSON output_mode).
- `CorrelationEngine`: `[t-N, t+N]` windows around failed samples with overlap merge.
- 6 new GUI params; `log-correlation.json` output.
- +7 tests (13 total).

### 2026-01 (Phase 3) ✅
- `o11y.SplunkO11yMetricsClient`: `GET /v2/timeserieswindow` + `X-SF-TOKEN` header;
  bare metric names auto-wrapped in `data('name').publish()`.
- Fetches over the JMeter test window `[start_ms, stop_ms]` derived from the
  aggregate summary.
- 6 new GUI params (`O11y_API_URL`, `O11y_Token`, `O11y_Metrics`,
  `O11y_Resolution_Ms`, `Enable_O11y`, `O11y_Output_Path`).
- Default metrics list: `cpu.utilization, memory.utilization,
  jvm.gc.collection.count, jvm.threads.count, db.latency, k8s.pod.cpu.usage`.
- Writes `o11y-metrics.json`.
- +6 tests.

### 2026-01 (Phase 4) ✅
- `ai.LlmClient`: single class, `Provider` enum for OPENAI, ANTHROPIC, GEMINI, GROK.
  - OpenAI/Grok: `POST /v1/chat/completions` (OpenAI-compatible), `Authorization: Bearer`.
  - Anthropic: `POST /v1/messages`, `x-api-key`, `anthropic-version: 2023-06-01`.
  - Gemini: `POST /v1beta/models/<model>:generateContent?key=...`.
- `ai.PromptBuilder`: system prompt + user prompt with three sections
  (aggregate, correlation, o11y metrics) as pretty JSON.
- `ai.MetricAggregator`: per-transaction + overall count/errors/error_rate/rt
  min/avg/p95/max, plus global start/stop epoch ms.
- `AIAnalyzer` now runs the LLM if configured, else falls back to the static
  Phase 1 text (also on any LLM exception).
- 6 new GUI params: `Enable_LLM`, `LLM_Provider`, `LLM_Model`, `LLM_API_Key`,
  `LLM_API_Key_Env`, `LLM_Base_URL`.
- Key resolution contract: GUI `LLM_API_Key` wins; else `LLM_API_Key_Env` env
  var; else static fallback with warning log.
- Default models: OpenAI `gpt-4o-mini`, Anthropic `claude-sonnet-4-5-20250929`,
  Gemini `gemini-2.5-flash`, Grok `grok-4.5`.
- +9 tests.

### Smoke tests (headless JMeter 5.6.3, non-GUI mode)
- Phase 1: 20 samples, `jmeter-metrics.json` NDJSON, `Performance_Report.html`.
- Phase 2: `Enable_Correlation=true` → 10 failures merged into 1 window,
  `log-correlation.json` written.
- Phase 3: `Enable_O11y=true` (no live endpoint) → `o11y-metrics.json` with
  all 6 configured metric keys, empty arrays (graceful degradation).
- All plugin logs report `correlation=true, o11y=true, llm=false/openai`.

### Totals
- **Tests: 28/28 pass** (`mvn clean test`).
- Fat JAR: `target/jmeter-smart-observability-plugin-1.0.0.jar` (~4.15 MB).

## Backlog

### P1
- Live LLM end-to-end verification against a real provider (requires user-supplied key).
- HEC async batching (single POST → batch-of-N on background thread) for high-RPS tests.

### P2
- Splunk Search job cancellation on teardown timeout; TLS trust-store toggle
  for self-signed HEC / Search endpoints.
- Excel + PDF report writers.
- Attach LLM analysis section into `Performance_Report.html` as a styled
  panel next to the raw metrics.
- Per-provider streaming responses (currently single-shot).

## Personas
- Perf QA Engineer: adds listener via JMeter GUI, points at Splunk HEC + Splunk
  Search REST + Splunk O11y + LLM provider.
- SRE/Observability Team: consumes performance events in Splunk index + reads
  `log-correlation.json` and `o11y-metrics.json`.
- Perf Lead: reads generated `Performance_Report.html` with LLM-authored root
  cause + recommendations.

## Next Tasks
1. Verify LLM path end-to-end with a live provider key (user action).
2. Backlog P1 items (HEC batching).
