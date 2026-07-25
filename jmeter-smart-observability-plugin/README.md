# JMeter Smart Observability AI Plugin

**Version:** 1.0.0 (Phase 1 MVP)
**Requires:** Apache JMeter 5.6.x, Java 21

A JMeter Backend Listener that captures per-sample performance metrics and:

* Persists each sample to a local newline-delimited JSON file.
* Ships each sample to a Splunk HTTP Event Collector (HEC) endpoint.
* Runs an AI analysis skeleton at teardown and writes an HTML report.

Phase 2+ will add Splunk Search API correlation, Observability Cloud metrics,
trace/root-cause analysis, and Excel / PDF reporting.

## Build

```bash
mvn clean package
```

Produces:

```
target/jmeter-smart-observability-plugin-1.0.0.jar
```

The build shades Jackson and Apache HttpClient 5 into the artifact so the
single JAR can be dropped straight into JMeter.

## Install

```bash
cp target/jmeter-smart-observability-plugin-1.0.0.jar \
   $JMETER_HOME/lib/ext/
```

Restart JMeter.

## Configure

In the JMeter GUI:

1. `Test Plan` &rarr; right-click &rarr; **Add** &rarr; **Listener** &rarr; **Backend Listener**.
2. In *Backend Listener Implementation* pick
   `com.smartjmeter.SmartObservabilityBackendListener`.
3. Fill in the parameters (defaults are populated automatically):

| Parameter            | Example                                                       |
|----------------------|---------------------------------------------------------------|
| `Splunk_URL`         | `https://splunk.company.com:8088/services/collector`          |
| `Splunk_Token`       | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`                        |
| `Splunk_Index`       | `performance`                                                 |
| `Environment`        | `Performance-Test`                                            |
| `Application`        | `Migration-System`                                            |
| `Test_Name`          | `Migration-API-Load-Run-42`                                   |
| `Local_Store_Path`   | `jmeter-metrics.json`                                         |
| `Enable_Splunk`      | `true` / `false`                                              |
| `Enable_Local_Store` | `true` / `false`                                              |

Run the test. On teardown the plugin writes `Performance_Report.html` in the
JMeter working directory.

## Splunk dashboard example

```spl
index=performance sourcetype=jmeter
| stats avg(responseTime), perc95(responseTime), count by transaction
```

## Tests

```bash
mvn test
```
