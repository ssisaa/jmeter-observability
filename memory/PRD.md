# JMeter Smart Observability AI Plugin - PRD

## Original Problem Statement
Production-grade JMeter Backend Listener plugin.
- Phase 1: capture perf metrics, local JSON store, Splunk HEC, GUI config, AI skeleton, HTML report.
- Phase 2: Splunk Search API + log correlation.
- Phase 3: Splunk Observability Cloud metrics.
- Phase 4: LLM-backed root cause analysis (multi-provider).
- Phase 5: HEC async batching for high-RPS tests.
- Phase 6: Styled HTML report, Splunk search job cancellation, TLS insecure toggle.

## Architecture
- Maven Java 21 (JMeter 5.6.3 `provided` + `ApacheJMeter_components`)
- Fat JAR via `maven-shade-plugin` (Jackson 2.17.2 + HttpClient5 shaded; JMeter external)
- Packages:
  - `SmartObservabilityBackendListener`, `config.PluginConfig`, `model.JMeterMetric`
  - `splunk.SplunkHECClient`, `splunk.AsyncBatchingHECClient`, `splunk.SplunkSearchClient`
  - `correlate.CorrelationEngine`
  - `o11y.SplunkO11yMetricsClient`
  - `ai.LlmClient`, `ai.AIAnalyzer`, `ai.PromptBuilder`, `ai.MetricAggregator`
  - `report.ReportGenerator`, `report.Markdown` (safe markdown-lite renderer)
  - `store.LocalJsonStore`
  - `util.HttpClientFactory` (shared TLS-aware HttpClient factory)
- All external clients: JDK `HttpClient`, safe on missing config, TLS-insecure toggle available.

## Implemented

### Phase 1-5 (prior sessions) ✅
- Backend listener + HEC forward + local NDJSON + AI skeleton + HTML report.
- Splunk Search API + correlation windows with overlap merge, `log-correlation.json`.
- Splunk Observability Cloud metrics client, `o11y-metrics.json`.
- LLM analyzer with 5 providers (OpenAI, Anthropic, Gemini, Grok/xAI, Groq/LPU).
- HEC async batcher (bounded queue, POISON-pill shutdown, batch size + flush interval + queue capacity).

### Phase 6 (this session) ✅

#### Styled HTML report
- Rewrote `ReportGenerator` to render a self-contained styled report:
  - Hero header (test / env / app / start / stop / generated ts)
  - KPI cards (samples, errors, error rate, avg / p95 / max RT, throughput)
  - Per-transaction table (Login/Checkout style rows, danger highlight)
  - Splunk Log Correlation panel (pill-row summary)
  - Splunk Observability Cloud Metrics table
  - AI Root Cause Analysis panel with provider/model chip
- New `Markdown` renderer: headings, bulleted lists, numbered lists, `**bold**`, inline `` `code` ``. Escapes any raw HTML so LLM output cannot inject scripts.
- Backwards-compatible: `generate(String analysis)` still supported.

#### Splunk Search job cancellation on teardown timeout
- `SplunkSearchClient` now calls `DELETE /services/search/jobs/{sid}` when a job doesn't reach `isDone` within `maxWaitMs`. INFO-logs the sid on success, WARN on failure — never throws.

#### TLS insecure toggle for self-signed HEC / Search / O11y endpoints
- New `util.HttpClientFactory` builds a shared `HttpClient` that either verifies TLS normally or trusts all when `insecure=true`.
- Emits a WARN log when the insecure client is created ("only use this for self-signed lab HEC/Search").
- Wired into all four HTTP clients (`SplunkHECClient`, `AsyncBatchingHECClient`, `SplunkSearchClient`, `SplunkO11yMetricsClient`).
- New GUI param `TLS_Insecure` (default false), read by `PluginConfig.isTlsInsecure()`.

### Live E2E verification
- **Groq (llama-3.3-70b-versatile)** run against `smoke.jmx`: full styled report rendered with all sections. Screenshots captured: KPI cards, per-transaction table, correlation pills, o11y table, LLM analysis panel with headings + bullets + bold + code inline.
- Screenshot proof at `/tmp/report_top.png` and `/tmp/report_llm.png`.

### Tests
- **34/34 pass** (`mvn clean test`) — 31 previous + 3 new (`ReportGeneratorTest`).
- Tests cover: legacy `generate(String)` still works; rich context renders KPIs / table / correlation / o11y / analysis; markdown correctly renders headings / lists / bold / code; injection attempts are escaped.

### Deliverable
- `target/jmeter-smart-observability-plugin-1.0.0.jar` (~4.15 MB) — drop into `$JMETER_HOME/lib/ext/`.
- Smoke plan at `smoke/smoke.jmx`.

## Backlog

### P1
- Auto-baseline diffing across runs (per-transaction p95 / error-rate deltas fed into LLM prompt).
- Absolute output paths (currently relative to JMeter cwd).

### P2
- Excel + PDF report writers.
- Per-provider streaming responses (currently single-shot).
- Charts (rt over time, error % over time) rendered as inline SVG.

## Personas
- Perf QA Engineer, SRE / Observability, Perf Lead — see previous sections.

## Next Tasks
- P1 backlog items.
