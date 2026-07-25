# JMeter Smart Observability AI Plugin - PRD

## Original Problem Statement
Build a production-grade JMeter Backend Listener plugin (Phase 1 MVP) with:
- JMeter Backend Listener capture of performance metrics
- Local JSON store
- Splunk HEC forwarding
- JMeter GUI configuration
- AI analysis engine skeleton
- Report generation framework
- Maven multi-package layout under `com.smartjmeter`

Phase 2: Splunk Search API integration + log correlation (±30s window around JMeter timestamps).

## Architecture
- Maven Java 21 project (JMeter 5.6.3 `provided` scope; also `ApacheJMeter_components`)
- Fat JAR via `maven-shade-plugin` (bundles Jackson 2.17.2 + HttpClient5 5.3.1; JMeter jars stay external)
- Backend Listener: `com.smartjmeter.SmartObservabilityBackendListener extends AbstractBackendListenerClient`
- Config: typed `PluginConfig` mapping to `BackendListenerContext` parameters
- Splunk HEC via JDK `HttpClient` (envelope: `{"event": ..., "sourcetype": "jmeter", "index": ...}`)
- Splunk Search REST API client (`SplunkSearchClient`): create-job -> poll -> fetch-results
- Correlation engine (`CorrelationEngine`): builds and merges [t-N, t+N] second windows around failed samples
- Local store: newline-delimited JSON, thread-safe append
- Report: HTML via text-block template, escapes user analysis content

## Personas
- Perf QA Engineer: adds listener via JMeter GUI, points at Splunk HEC + Splunk Search REST
- SRE/Observability Team: consumes performance events in Splunk index for dashboards + reads `log-correlation.json`
- Perf Lead: reads generated `Performance_Report.html` after runs

## Core Requirements (static)
- Must compile on Java 21, package as a single JAR droppable into `$JMETER_HOME/lib/ext/`
- Must expose all params via `getDefaultParameters()` so JMeter GUI can render them
- Must not throw from HEC / Search send (never break a test run on Splunk outage)
- Local store must be append-only + thread-safe
- Correlation windows merged to avoid duplicate SPL calls

## Implemented

### 2026-01 (Phase 1)
- pom.xml (Java 21, shade, junit 5) ✅
- Full package layout: `SmartObservabilityBackendListener`, `config.PluginConfig`, `model.JMeterMetric`, `splunk.SplunkHECClient`, `ai.AIAnalyzer`, `report.ReportGenerator`, `store.LocalJsonStore` ✅
- `messages.properties` resource bundle ✅
- JUnit 5 tests (6 tests) ✅
- `mvn clean test` → 6/6 pass ✅
- `mvn clean package` → `target/jmeter-smart-observability-plugin-1.0.0.jar` (fat, ~4.1 MB) ✅

### 2026-01 (Phase 2 + Smoke)
- `splunk.SplunkSearchClient` (JDK HttpClient, Bearer/Splunk auth, create-job / poll / fetch-results) ✅
- `correlate.CorrelationEngine` (window builder + overlap merger, `TimeWindow` record) ✅
- Listener teardown wired to run correlation and dump `log-correlation.json` ✅
- 6 new params in `PluginConfig` + descriptor: `Splunk_Search_URL`, `Splunk_Search_Token`, `Splunk_Log_Index`, `Enable_Correlation`, `Correlation_Window_Seconds`, `Correlation_Output_Path` ✅
- 7 new tests (4 SplunkSearchClient + 3 CorrelationEngine); overall **13/13 pass** ✅
- **Headless JMeter 5.6.3 smoke test** (`smoke/smoke.jmx`) executed in non-GUI mode; plugin loads, 20 samples flushed, 10 failures merged into 1 window, `jmeter-metrics.json` (NDJSON), `log-correlation.json`, `Performance_Report.html` all produced ✅

## Backlog

### P1 (Phase 3)
- Splunk Observability Cloud metrics client (CPU, Memory, JVM GC, Thread Pool, DB latency, K8s)

### P2 (Phase 4)
- Real AI Agent (LLM-backed root cause + recommendations, consuming failures + correlated log events)
- Excel report writer (`Performance_Report.xlsx`)
- PDF report writer (`Performance_Report.pdf`)
- Performance knowledge-base seeding

### P2 (Ops)
- Splunk HEC batching + async queue (currently one HTTP POST per sample)
- Splunk Search job cancellation on teardown timeout
- Optional TLS trust-store toggle for self-signed HEC / Search endpoints
- Splunk index/source/sourcetype override per env
- GUI smoke test on a real display (out of scope for this container)

## Next Tasks
1. Kick off Phase 3: Splunk Observability Cloud metrics client.
2. Wire correlated events into the AIAnalyzer prompt once Phase 4 LLM integration lands.
