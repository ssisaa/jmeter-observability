# One-click Docker demo

Bring up an **Analysis Service**, a **Splunk HEC mock**, and a **JMeter
runner** loaded with the v2.0.4 plugin, all in a single command.

```bash
# from the repo root
mvn -q -DskipTests clean package        # build the jar
docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit jmeter
```

When the JMeter container exits, the report is under `docker/out/`:

- `Performance_Report.html` - open in a browser
- `Performance_Report.json` - machine-readable envelope
- `Performance_Report.csv`  - per-transaction stats
- `ci-gate.json` - `{shouldFail, exitCode, verdict, ...}`

Verify the components:

```bash
curl http://localhost:7788/healthz          # Analysis Service
curl http://localhost:8088/services/collector/health   # Splunk HEC mock
```

## Enabling real LLM analysis

Set the API key on the analysis container:

```bash
OPENAI_API_KEY=sk-... docker compose -f docker/docker-compose.yml up --build
```

The demo test plan (`docker/plans/demo.jmx`) hits `httpbin.org/status/200`
and `/status/500` so the report always contains errors - useful for
practicing the notifier + CI-gate flow. Swap the samplers for your own
endpoints before running against a real environment.
