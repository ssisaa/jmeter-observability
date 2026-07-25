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

## Architecture
- Maven Java 21 project (JMeter 5.6.3 `provided` scope)
- Fat JAR via `maven-shade-plugin` (bundles Jackson 2.17.2 + HttpClient5 5.3.1; JMeter jars stay external)
- Backend Listener: `com.smartjmeter.SmartObservabilityBackendListener extends AbstractBackendListenerClient`
- Config: typed `PluginConfig` mapping to `BackendListenerContext` parameters
- Splunk HEC via JDK `HttpClient` (envelope: `{"event": ..., "sourcetype": "jmeter", "index": ...}`)
- Local store: newline-delimited JSON, thread-safe append
- Report: HTML via text-block template, escapes user analysis content

## Personas
- Perf QA Engineer: adds listener via JMeter GUI, points at Splunk HEC
- SRE/Observability Team: consumes performance events in Splunk index for dashboards
- Perf Lead: reads generated `Performance_Report.html` after runs

## Core Requirements (static)
- Must compile on Java 21, package as a single JAR droppable into `$JMETER_HOME/lib/ext/`
- Must expose all params via `getDefaultParameters()` so JMeter GUI can render them
- Must not throw from HEC send (never break a test run on Splunk outage)
- Local store must be append-only + thread-safe

## Implemented (2026-01)
- pom.xml (Java 21, shade, junit 5) ✅
- Full package layout: `SmartObservabilityBackendListener`, `config.PluginConfig`, `model.JMeterMetric`, `splunk.SplunkHECClient`, `ai.AIAnalyzer`, `report.ReportGenerator`, `store.LocalJsonStore` ✅
- `messages.properties` resource bundle ✅
- JUnit 5 tests (6 tests): metric serialisation, HEC envelope, no-op send, ND-JSON append, AI analyzer text, HTML report escaping ✅
- `mvn clean test` → 6/6 pass ✅
- `mvn clean package` → `target/jmeter-smart-observability-plugin-1.0.0.jar` (fat, ~4.1 MB) ✅

## Backlog

### P1 (Phase 2)
- Splunk Search API client for log correlation (index=app, error/timeout/exception, ±30s window)
- Correlate by JMeter timestamp

### P1 (Phase 3)
- Splunk Observability Cloud metrics client (CPU, Memory, JVM GC, Thread Pool, DB latency, K8s)

### P2 (Phase 4)
- Real AI Agent (LLM-backed root cause + recommendations)
- Excel report writer (`Performance_Report.xlsx`)
- PDF report writer (`Performance_Report.pdf`)
- Performance knowledge-base seeding

### P2 (Ops)
- Splunk HEC batching + async queue (currently one HTTP POST per sample)
- Optional TLS trust-store toggle for self-signed HEC endpoints
- Splunk index/source/sourcetype override per env

## Next Tasks
1. Run plugin inside a JMeter GUI smoke instance (out of scope for this container).
2. Implement Phase 2 Splunk Search API client.
