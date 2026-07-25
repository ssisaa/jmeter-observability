# JMeter Smart Observability AI Plugin - PRD

## Original Problem Statement
Production-grade JMeter Backend Listener plugin.
- Phase 1: capture perf metrics, local JSON store, Splunk HEC, GUI config, AI skeleton, HTML report.
- Phase 2: Splunk Search API + log correlation.
- Phase 3: Splunk Observability Cloud metrics.
- Phase 4: LLM-backed root cause analysis.
- Phase 5 (backlog P1): HEC async batching for high-RPS tests.

## Architecture
- Maven Java 21 (JMeter 5.6.3 `provided` + `ApacheJMeter_components`)
- Fat JAR via `maven-shade-plugin` (Jackson 2.17.2 + HttpClient5 shaded; JMeter external)
- Package layout:
  - `SmartObservabilityBackendListener`, `config.PluginConfig`, `model.JMeterMetric`
  - `splunk.SplunkHECClient`, `splunk.AsyncBatchingHECClient`, `splunk.SplunkSearchClient`
  - `correlate.CorrelationEngine`
  - `o11y.SplunkO11yMetricsClient`
  - `ai.LlmClient`, `ai.AIAnalyzer`, `ai.PromptBuilder`, `ai.MetricAggregator`
  - `report.ReportGenerator`, `store.LocalJsonStore`
- All external calls: JDK `HttpClient`. Every client is safe on missing config (returns empty / no-op).

## Implemented

### 2026-01 (Phase 1) ✅
Backend listener, HEC client, local NDJSON store, HTML report, AI skeleton.

### 2026-01 (Phase 2) ✅
`SplunkSearchClient` (create-job → poll → results), `CorrelationEngine` with overlap merge, `log-correlation.json`.

### 2026-01 (Phase 3) ✅
`SplunkO11yMetricsClient` (`/v2/timeserieswindow` + `X-SF-TOKEN`), `o11y-metrics.json`, default 6 infra metrics.

### 2026-01 (Phase 4) ✅
`LlmClient` with 5 providers: OPENAI, ANTHROPIC, GEMINI, GROK (xAI), GROQ (Groq LPU).
- OpenAI + Grok + Groq → OpenAI-compatible `/v1/chat/completions`
- Anthropic → `/v1/messages` w/ `anthropic-version` header
- Gemini → `/v1beta/models/<model>:generateContent?key=...`
- Defaults: OpenAI `gpt-4o-mini`, Anthropic `claude-sonnet-4-5-20250929`,
  Gemini `gemini-2.5-flash`, Grok `grok-4.5`, Groq `llama-3.3-70b-versatile`.
- Key resolution: GUI `LLM_API_Key` wins → env var → static fallback with warning.
- `PromptBuilder` composes SRE-focused system prompt + aggregate/correlation/o11y JSON user prompt.
- `MetricAggregator` per-transaction + overall count/errors/error_rate/rt min/avg/p95/max.

### 2026-01 (Phase 5 — HEC async batching) ✅
`AsyncBatchingHECClient` (single daemon worker, bounded `LinkedBlockingQueue`,
newline-delimited HEC batch body, POISON-pill shutdown, non-blocking `send()`).
5 new GUI params: `HEC_Batch_Enabled`, `HEC_Batch_Size`, `HEC_Flush_Interval_Ms`,
`HEC_Queue_Capacity` (+ toggle in listener setup).

### Live E2E validation (this session)
- **Gemini 2.5 Flash**: produced structured 4-section root-cause report; correctly identified 100% failure rate on `synthetic-checkout` and 894 ms p95 on `synthetic-login`.
- **Groq Llama 3.3 70B**: same test, structured report with Executive Summary / Key Findings / Probable Root Causes / Recommended Actions.
- **HEC batching**: 20 samples → 17 batched POSTs to a local echo HTTP server; 20/20 event lines delivered; batch envelope shape verified as
  `{"event":{...},"sourcetype":"jmeter","index":"performance"}\n` per line.

### Totals
- Tests: **31/31 pass** (`mvn clean test`).
- Fat JAR: `target/jmeter-smart-observability-plugin-1.0.0.jar` (~4.15 MB).

## Backlog

### P1
- Splunk Search job cancellation on teardown timeout.
- TLS trust-store toggle for self-signed HEC / Search endpoints.
- Attach LLM analysis into `Performance_Report.html` as a styled panel next to raw metrics.

### P2
- Auto-detected regression baselines (compare against prior aggregate JSON).
- Excel + PDF report writers.
- Per-provider streaming responses.
- Absolute output paths (currently relative to JMeter cwd).

## Personas
- Perf QA Engineer: adds listener via JMeter GUI, points at Splunk HEC + Splunk Search REST + Splunk O11y + LLM provider.
- SRE / Observability: consumes performance events in Splunk index, reads `log-correlation.json` and `o11y-metrics.json`.
- Perf Lead: reads `Performance_Report.html` with LLM-authored root cause + recommendations.

## Next Tasks
- P1 backlog items.

## Test Credentials
See `/app/memory/test_credentials.md` for LLM API keys supplied by the user for validation.
