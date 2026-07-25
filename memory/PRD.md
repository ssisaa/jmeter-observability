# JMeter Smart Observability AI Plugin - PRD

## Original Problem Statement
Production-grade JMeter Backend Listener plugin capturing performance metrics,
forwarding to Splunk HEC, correlating with Splunk logs, fetching Splunk O11y
metrics, and producing an LLM-authored root-cause report.

## Phases
- Phase 1: capture + local NDJSON + HEC + GUI config + AI skeleton + HTML report.
- Phase 2: Splunk Search API + log correlation.
- Phase 3: Splunk Observability Cloud metrics.
- Phase 4: multi-provider LLM analysis (OpenAI / Anthropic / Gemini / Grok / Groq).
- Phase 5: HEC async batching (bounded queue, POISON-pill shutdown).
- Phase 6: styled HTML report, Splunk Search job cancellation, TLS insecure toggle.
- Phase 7: absolute-aware output paths, auto-baseline diff piped into LLM prompt.

## Architecture
- Maven Java 21 (JMeter 5.6.3 `provided` + `ApacheJMeter_components`).
- Fat JAR via `maven-shade-plugin`; JMeter deps stay external.
- Packages:
  - `SmartObservabilityBackendListener`, `config.PluginConfig`, `model.JMeterMetric`
  - `splunk.SplunkHECClient`, `splunk.AsyncBatchingHECClient`, `splunk.SplunkSearchClient`
  - `correlate.CorrelationEngine`
  - `o11y.SplunkO11yMetricsClient`
  - `baseline.BaselineStore`, `baseline.BaselineDiff`
  - `ai.LlmClient`, `ai.AIAnalyzer`, `ai.PromptBuilder`, `ai.MetricAggregator`
  - `report.ReportGenerator`, `report.Markdown`
  - `store.LocalJsonStore`
  - `util.HttpClientFactory` (shared TLS-aware HttpClient)

## Implemented (Phase 7 - this session)

### Absolute-aware output paths
- New GUI params `Output_Directory`, `Report_Output_Path`.
- `PluginConfig.resolvePath(String)`: absolute paths kept as-is; relative
  paths resolved against `Output_Directory`; blank output dir falls back
  to JMeter cwd (previous behaviour) but normalised to absolute.
- All four writers routed through `resolvePath`: local metrics store,
  correlation JSON, o11y JSON, and the HTML report itself.
- Listener now INFO-logs the resolved report path so users always know
  where their report landed.
- Report + baseline defaults auto-derive:
  - Report: `Performance_Report.html` under `Output_Directory`.
  - Baseline: `baseline-<Test_Name>.json` under `Output_Directory` (test
    name sanitised to `[A-Za-z0-9._-]`).

### Auto-baseline diff feeding the LLM
- New GUI params `Enable_Baseline_Diff`, `Baseline_Path`, `Baseline_Update_Mode`.
- `baseline.BaselineStore` reads/writes an envelope `{saved_at, test_name, aggregate}`.
- `baseline.BaselineDiff.compute(prev, current)` produces per-transaction and
  overall deltas (count delta, error-rate pp, avg RT %, p95 RT %, max RT %),
  plus a `notable` string list for entries crossing `|delta| >= 20%` (or 2pp
  for error rate). Handles `new` / `gone` transactions.
- Update modes: `always` (default), `never`, `on-success` (skip when errors > 0).
- `ai.PromptBuilder` inserts a `## Baseline Diff (vs. previous run at ...)` block
  into the LLM user prompt whenever `has_previous=true`, and the system prompt
  gained an explicit "Regressions vs. Baseline" report section.
- `report.ReportGenerator` renders a **Baseline Diff** panel: previous-run pill,
  colour-coded delta cells (`delta-worse` red / `delta-better` green /
  `delta-neutral` grey), plus a red `notable` list for eye-catching regressions.

## Live E2E Verification (this session)
- **Run 1** with `Enable_Baseline_Diff=true` on empty dir:
  - Log: `Baseline path: /tmp/smoke-out/baseline-smoke-run.json (previous=false)`
  - Wrote `Performance_Report.html`, `baseline-smoke-run.json`,
    `jmeter-metrics.json`, `log-correlation.json`, `o11y-metrics.json` -
    all inside the configured `Output_Directory`.
- **Run 2** against the saved baseline:
  - Log: `Baseline path: ... (previous=true)`
  - HTML report now has a **Baseline Diff** section with pill + colour-coded
    per-transaction table (screenshot captured).
  - Groq LLM output includes a dedicated
    `## Regressions vs. Baseline` section citing the actual %/pp deltas:
    e.g. *"synthetic-login decreasing by 3.2% and synthetic-checkout
    increasing by 3.0%"*.

## Tests
- **42/42 pass** (`mvn clean test`) - +8 vs previous:
  - `BaselineTest` (3): compute without previous, compute with prev / new / gone
    transactions + notable strings, store round-trip.
  - `PluginConfigPathTest` (5): relative resolve, absolute kept, blank output-dir
    falls back to cwd, baseline path derived from test name (with sanitisation),
    explicit baseline path honoured.

## Deliverable
- `target/jmeter-smart-observability-plugin-1.0.0.jar` (~4.18 MB) - drop into
  `$JMETER_HOME/lib/ext/`.
- Smoke plan `smoke/smoke.jmx` - drives all toggles from JMeter `-J` properties.

## Backlog

### P1
- Inline SVG sparklines per-transaction (rt-over-time, error % over-time).
- Retention / rotation of `baseline-*.json` files.

### P2
- Excel + PDF report writers.
- Per-provider streaming responses.
- CI-friendly non-zero exit code when notable regressions exceed threshold.

## Test Credentials
See `/app/memory/test_credentials.md`.
