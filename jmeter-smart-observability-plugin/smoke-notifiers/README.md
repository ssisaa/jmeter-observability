# Notifier Smoke Deck

One-command way to prove your Slack / Teams / Email / Jira / ServiceNow
integrations are wired up end-to-end - without waiting for a real
performance run.

## How it works

The bundled `smoke-notifiers.jmx` sends two threads at
`https://httpbin.org/status/500` (guaranteed HTTP 500s) so the Smart
Observability Backend Listener will always compile a **NO_GO** verdict.
That verdict is then fanned out to every notifier you have configured
in `.env`, an HTML/PDF/PPTX report is written under `smoke-out/`, and
the standalone `CiGate` CLI evaluates the gate file and exits **3**.

If your CI shell picks up that exit code, you have proven the whole
pipeline in ~30 seconds.

## Prerequisites

- Apache JMeter 5.6.3 unpacked somewhere (set `JMETER_HOME` in `.env`).
- JDK 21 (`JAVA_HOME` in `.env`).
- The plugin fat-jar built (`mvn clean package` in the repo root).
- Outbound network access to `httpbin.org` and to whichever notifier
  endpoints you enable.

## Usage

```bash
cp .env.example .env
# edit .env - fill in the sinks you want to test
./run-notifier-smoke.sh
echo "gate exit code: $?"    # 3 on NO_GO, 0 on GO
```

## What each `.env` value expects

| Variable            | Format                                                                     |
| ------------------- | -------------------------------------------------------------------------- |
| `SLACK_WEBHOOK`     | `https://hooks.slack.com/services/...`                                     |
| `TEAMS_WEBHOOK`     | Full incoming-webhook URL                                                  |
| `EMAIL_SMTP`        | `host:port|user|pass|starttls`  (`starttls` is `true` / `false`)          |
| `EMAIL_FROM_TO`     | `from@example.com|to1@example.com,to2@example.com`                        |
| `JIRA_CONFIG`       | `https://your.atlassian.net|PROJKEY|Task|user@example.com|API_TOKEN`      |
| `SERVICENOW_CONFIG` | `https://instance.service-now.com|user|pass|incident`                     |
| `FAIL_ON_VERDICT`   | `NO_GO` or `NO_GO,GO_WITH_CONDITIONS`                                     |

Every notifier is best-effort: any sink left blank is skipped silently,
any HTTP failure is logged as a `WARNING` and the run continues.

## Artefacts produced under `smoke-out/`

- `Performance_Report.html`  full enterprise report
- `Performance_Report.pdf`   HTML rendered to PDF
- `Performance_Report.pptx`  5-slide exec deck
- `Performance_Report.json`  structured envelope for downstream systems
- `Performance_Report.csv`   per-transaction stats
- `ci-gate.json`             machine-readable gate file consumed by `CiGate`
- `results.jtl`              raw JMeter results
- `jmeter.log`               JMeter runtime log

## Wiring into CI

```yaml
# GitHub Actions example
- name: Perf smoke + notifier check
  env:
    SLACK_WEBHOOK: ${{ secrets.SLACK_WEBHOOK }}
    JIRA_CONFIG: ${{ secrets.JIRA_CONFIG }}
    JMETER_HOME: /opt/apache-jmeter-5.6.3
    JAVA_HOME: /opt/java/jdk-21.0.12
    PLUGIN_JAR: ${{ github.workspace }}/target/jmeter-smart-observability-plugin-2.0.2.jar
    FAIL_ON_VERDICT: NO_GO
  run: |
    cp smoke-notifiers/.env.example smoke-notifiers/.env
    envsubst < smoke-notifiers/.env > smoke-notifiers/.env.tmp && mv smoke-notifiers/.env.tmp smoke-notifiers/.env
    smoke-notifiers/run-notifier-smoke.sh
```

The script exits **3** on NO_GO, which fails the CI step.
