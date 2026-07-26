# JMeter Smart Observability Plugin - Parameter Reference

Every field the plugin's Backend Listener exposes, what to type into it,
where to find the value in your environment, and a realistic sample.

Fields marked **Required** must be filled in for the plugin to start.
Everything else is optional - leave blank to disable the corresponding
feature.

**How JMeter's "Add" button works**: the Backend Listener parameter panel
already pre-populates every row when you drop the listener in. Click
"Add" only when you want to add extra ad-hoc rows (they are treated as
JMeter properties and ignored by the plugin). To remove a feature,
clear the value instead of the row.

---

## 1. Core - identity + report output

| Parameter           | Sample value                                    | Where to get it                                                                                                        |
| ------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `Test_Name`         | `checkout-load-2025-w03`                        | Any name your team uses to distinguish runs. Include the release date or sprint tag so reports sort chronologically. |
| `Environment`       | `staging`                                       | Free-form. Common values: `dev`, `qa`, `staging`, `preprod`, `prod`.                                                  |
| `Application`       | `smart-shop-api`                                | The application under test. Match the service name in your APM/observability platform so reports cross-link.        |
| `Apdex_Target_Ms`   | `500`                                           | Your product's response-time SLA (a common industry default is 500 ms).                                              |
| `Output_Directory`  | `./perf-reports/${__time(yyyy-MM-dd_HHmm,)}`   | Where the plugin writes `Performance_Report.*`. `.` is the current working dir of the JMeter process.               |
| `Report_Output_Path`| `Performance_Report.html`                       | Path (absolute or relative to `Output_Directory`). Keep `.html`.                                                     |
| `Json_Report_Path`  | `Performance_Report.json`                       | Machine-readable envelope for downstream automation.                                                                |
| `Csv_Report_Path`   | `Performance_Report.csv`                        | Per-transaction stats.                                                                                              |

---

## 2. Local storage

| Parameter               | Sample value        | Notes                                                                        |
| ----------------------- | ------------------- | ---------------------------------------------------------------------------- |
| `Enable_Local_Store`    | `true`              | `true` writes raw sample events under `Output_Directory/local/`.            |
| `Local_Store_Path`      | `local-store.json`  | Overrides the default local store filename.                                 |

---

## 3. Splunk HEC (log + event forwarding)

| Parameter           | Sample value                             | Where to get it                                                                                    |
| ------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `Enable_Splunk`     | `true`                                   | Turn on to forward events to Splunk HEC.                                                          |
| `Splunk_URL`        | `https://splunk.company.com:8088`        | Your Splunk HEC endpoint. Ask your Splunk admin for the HEC input URL.                             |
| `Splunk_Token`      | `abcd1234-5678-90ef-abcd-1234567890ef`   | Splunk Web -> Settings -> Data inputs -> HTTP Event Collector -> your token.                       |
| `Splunk_Index`      | `perf`                                   | The Splunk index that receives events. Ask your Splunk admin which index is allocated to perf.    |
| `Splunk_Sourcetype` | `jmeter:sample`                          | Free-form; Splunk uses this to route events to props/transforms.                                  |
| `Splunk_Batch_Size` | `500`                                    | Events flushed per HTTP call. 100-1000 works for most tests.                                       |
| `Splunk_Flush_Ms`   | `2000`                                   | Milliseconds between async flushes.                                                                |

---

## 4. Splunk Search (log correlation)

| Parameter                       | Sample value                     | Where to get it                                                                              |
| ------------------------------- | -------------------------------- | -------------------------------------------------------------------------------------------- |
| `Splunk_Search_URL`             | `https://splunk.company.com:8089`| Splunk **REST/search** port (not HEC). Ask your Splunk admin.                                |
| `Splunk_Search_Token`           | `Bearer eyJraWQiOi...`           | Splunk Web -> Settings -> Tokens -> new user token with the `search` capability.             |
| `Splunk_Log_Index`              | `app`                            | Index that stores your app logs (usually `app`, `main`, or a per-team index).                |
| `Enable_Correlation`            | `true`                           | Turn on to run a Splunk search around each JMeter error and attach evidence to the report.  |
| `Correlation_Window_Seconds`    | `30`                             | +/- window (seconds) around each error timestamp.                                            |
| `Correlation_Output_Path`       | `log-correlation.json`           | Where to persist the correlation output.                                                    |

---

## 5. Splunk Observability Cloud (o11y)

| Parameter                       | Sample value                                      | Where to get it                                                                                                                        |
| ------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `O11y_URL`                      | `https://api.us1.signalfx.com`                    | Your realm-specific SignalFx API endpoint. Find it under Profile -> API access token.                                                 |
| `O11y_Token`                    | `Xk7Y...` (org token)                             | Splunk O11y -> Profile -> Access tokens -> API access token.                                                                          |
| `O11y_Program_Text`             | `data('cpu.utilization', filter=filter('service','payment')).mean().publish()` | SignalFlow snippet. Test in the SignalFx UI's "Chart Builder -> SignalFlow" and copy the exact program text.                        |
| `O11y_Duration_Seconds`         | `900`                                             | Seconds of history to fetch (defaults to test duration).                                                                              |

---

## 6. AWS CloudWatch

| Parameter                        | Sample value                                                                                    | Where to get it                                                                                          |
| -------------------------------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `Enable_Cloudwatch`              | `true`                                                                                          | Turn on to pull CloudWatch metrics + alarms.                                                            |
| `Cloudwatch_Region`              | `us-east-1`                                                                                     | Your target AWS region.                                                                                 |
| `Cloudwatch_Namespace`           | `AWS/ApplicationELB`                                                                            | Namespace to query. See `aws cloudwatch list-metrics --output table`.                                   |
| `Cloudwatch_Metrics_Json`        | `[{"metric":"HTTPCode_Target_5XX_Count","stat":"Sum","dimensions":{"LoadBalancer":"app/checkout/abc"}}]` | Array of metric selectors. Each object needs `metric`, `stat`, `dimensions`.                            |
| `Cloudwatch_Alarms_Csv`          | `checkout-p95-high,payment-error-spike`                                                         | Comma-separated alarm names (fetch state during the test window).                                        |
| `Cloudwatch_Log_Group`           | `/aws/apigw/checkout-prod`                                                                      | Log group to scan for `ERROR|FATAL|Exception` around the test window.                                    |
| `Cloudwatch_Log_Filter_Pattern`  | `?ERROR ?Exception`                                                                             | CloudWatch Logs Insights filter pattern.                                                                 |

*AWS credentials* are picked up from the default AWS credential chain
(env vars `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, `~/.aws/credentials`,
or an IAM role on the JMeter host). Never paste keys into the JMX.

---

## 7. LLM analysis

| Parameter          | Sample value                                | Where to get it                                                                                     |
| ------------------ | ------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `Enable_Llm`       | `true`                                      | Turn on to send the aggregated metrics to an LLM for narrative analysis.                            |
| `Llm_Provider`     | `openai` \| `anthropic` \| `gemini` \| `grok` \| `groq` | Provider your key belongs to.                                                                       |
| `Llm_Model`        | `gpt-5.2` (OpenAI), `claude-sonnet-4.5` (Anthropic), `gemini-3-flash` (Gemini), `llama-3-70b` (Groq) | Model ID accepted by the provider. Leave blank for the plugin default.                              |
| `Llm_Api_Key`      | *(leave blank - use env var)*               | Never paste secrets here. Set `LLM_API_KEY` env var on the JMeter host, or override with `Llm_Api_Key_Env`. |
| `Llm_Api_Key_Env`  | `OPENAI_API_KEY`                            | Name of the env var to read the key from.                                                          |
| `Llm_Base_Url`     | *(leave blank)*                             | Override only when using a proxy/self-hosted LLM gateway.                                          |

---

## 8. Notifiers (v2.0.0+)

Every notifier below is best-effort and silently skipped when blank.

| Parameter               | Sample value                                                                     | Where to get it                                                                          |
| ----------------------- | -------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `Slack_Webhook_URL`     | `https://hooks.slack.com/services/T00/B00/xxxx`                                  | Slack: your workspace admin -> Incoming Webhooks -> Add.                                 |
| `Teams_Webhook_URL`     | `https://outlook.office.com/webhook/xxxx@xxxx/IncomingWebhook/xxxx/xxxx`         | Teams channel -> Connectors -> Incoming Webhook -> Configure.                            |
| `Email_Smtp`            | `smtp.gmail.com:587\|perfbot@company.com\|app-password\|true`                    | Format `host:port\|user\|pass\|starttls`. Use an app password (never your login).       |
| `Email_From_To`         | `perfbot@company.com\|sre-oncall@company.com,perf-team@company.com`              | Format `from\|to1,to2,...`.                                                              |
| `Jira_Config`           | `https://acme.atlassian.net\|PERF\|Task\|jira-bot@acme.com\|api-token`           | Format `baseUrl\|projectKey\|issueType\|email\|token`. Get the token from atlassian.com/account -> Security -> API tokens. |
| `ServiceNow_Config`     | `https://acme.service-now.com\|perfbot\|password\|incident`                      | Format `instanceUrl\|user\|password\|table`. `table` is usually `incident` or `sn_si_incident`. |
| `Notifier_Cooldown_Seconds` | `3600`                                                                        | Suppress repeat notifications for the same (sink, verdict, test) within this many seconds. |

---

## 9. CI gate

| Parameter          | Sample value                        | Where to get it                                                                    |
| ------------------ | ----------------------------------- | ---------------------------------------------------------------------------------- |
| `Fail_On_Verdict`  | `NO_GO` \| `NO_GO,GO_WITH_CONDITIONS` | The verdict(s) that should fail your CI step. Comma-separated. See `CiGate` CLI. |

---

## 10. External metric backends (v2.0.2+)

Add every third-party observability source you want the plugin to pull
during the run through the `Metric_Sources_Json` param. It is a JSON
array; each element declares one backend.

```json
[
  {
    "backend": "prometheus",
    "baseUrl": "https://prom.company.com",
    "queries": {"cpu": "avg(rate(container_cpu_usage_seconds_total[1m]))",
                "error_rate": "sum(rate(http_requests_total{status=~\"5..\"}[1m]))"},
    "outPath": "prometheus-metrics.json"
  },
  {
    "backend": "loki",
    "baseUrl": "https://loki.company.com",
    "headers": {"X-Scope-OrgID": "acme"},
    "queries": {"errors": "{app=\"checkout\"} |= \"error\""},
    "outPath": "loki-metrics.json"
  },
  {
    "backend": "elastic",
    "baseUrl": "https://es.company.com:9200/logs-*/_search",
    "headers": {"Authorization": "ApiKey abcd..."},
    "queries": {"error_bucket": "{\"aggs\":{\"per_min\":{\"date_histogram\":{\"field\":\"@timestamp\",\"fixed_interval\":\"1m\"}}}}"}
  },
  {
    "backend": "datadog",
    "baseUrl": "https://api.datadoghq.com",
    "headers": {"DD-API-KEY": "<api>", "DD-APPLICATION-KEY": "<app>"},
    "queries": {"error_rate_pct": "avg:trace.servlet.request.errors{env:prod,service:checkout}.as_rate() * 100"}
  },
  {
    "backend": "newrelic",
    "baseUrl": "https://api.newrelic.com",
    "headers": {"X-Api-Key": "NRAK-..."},
    "queries": {"apdex": "SELECT apdex(duration) FROM Transaction WHERE appName='checkout' SINCE 15 minutes ago"}
  },
  {
    "backend": "dynatrace",
    "baseUrl": "https://xyz.live.dynatrace.com",
    "headers": {"Authorization": "Api-Token dt0c01..."},
    "queries": {"failure_rate": "builtin:service.errors.total.rate:filter(eq(dt.entity.service,SERVICE-1234)):avg"}
  },
  {
    "backend": "azure",
    "baseUrl": "https://management.azure.com/subscriptions/<sub>/resourceGroups/<rg>/providers/Microsoft.Insights/metrics",
    "headers": {"Authorization": "Bearer <mgmt-token>"},
    "queries": {"cpu": "?metricnames=CpuPercentage&aggregation=Average"}
  },
  {
    "backend": "gcp",
    "baseUrl": "https://monitoring.googleapis.com/v3/projects/<proj>/timeSeries",
    "headers": {"Authorization": "Bearer <gcp-token>"},
    "queries": {"instance_cpu": "?filter=metric.type=\"compute.googleapis.com/instance/cpu/utilization\""}
  }
]
```

Where to source each:
- **Prometheus** URL: your Prom UI's URL (drop the `/graph` suffix).
- **Loki** URL: your Grafana Loki datasource URL.
- **Elastic** URL: the Elasticsearch endpoint + index pattern.
- **Datadog** DD-API-KEY / DD-APPLICATION-KEY: Datadog UI -> Organisation Settings -> API/Application Keys.
- **New Relic** X-Api-Key: New Relic UI -> API keys -> User key.
- **Dynatrace** token: Access tokens page in your tenant.
- **Azure** bearer token: `az account get-access-token --resource=https://management.azure.com/` (short-lived; use a managed identity in CI).
- **GCP** bearer token: `gcloud auth print-access-token`.

---

## 11. Forecast + rolling baselines (v2.0.2+)

| Parameter                        | Sample value       | Notes                                                                                       |
| -------------------------------- | ------------------ | ------------------------------------------------------------------------------------------- |
| `Baseline_History_Dir`           | `baseline-history` | Directory holding per-run p95 snapshots.                                                    |
| `Forecast_Sla_P95_Ms`            | `1000`             | Your product SLA in ms. The forecast prints days-until-breach against this number.         |
| `Baseline_History_Max`           | `100`              | Cap the on-disk history to N snapshots (older ones deleted).                                |
| `Baseline_History_Max_Days`      | `90`               | Delete snapshots older than this many days.                                                 |

---

## 12. Analysis Service split (v2.0.3+)

| Parameter               | Sample value               | Notes                                                                                                              |
| ----------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `Analysis_Service_Url`  | `http://analysis:7788`     | When set, the plugin delegates LLM analysis to the shared `AnalysisServer`. Every runner in the fleet shares one LLM budget. |

Run the service:

```bash
ANALYSIS_LLM_API_KEY=sk-... ANALYSIS_PROVIDER=openai ANALYSIS_MODEL=gpt-5.2 \
  java -cp jmeter-smart-observability-plugin-2.0.4.jar \
       com.smartjmeter.analysis.AnalysisServer
```

Health check: `curl http://localhost:7788/healthz`.

---

## 13. TLS

| Parameter          | Sample value | Notes                                                                       |
| ------------------ | ------------ | --------------------------------------------------------------------------- |
| `Tls_Insecure`     | `false`      | Set `true` **only** in isolated test networks with self-signed certs.       |

---

## Runtime examples

### Command-line override (any param above)

```bash
jmeter -n -t plan.jmx -l results.jtl \
  -Jplugin.slack=$SLACK_WEBHOOK \
  -Jplugin.gate=NO_GO \
  -Jout.dir=./perf-reports/$(date +%F_%H%M)
```

Then reference the JMeter property inside the Backend Listener value:
`${__P(plugin.slack,)}` .

### Docker one-liner

```bash
docker run --rm -v $PWD:/work smart-jmeter:2.0.4 \
  -n -t /work/plan.jmx -l /work/results.jtl \
  -Jplugin.gate=NO_GO
```

The `docker/` bundle in this repo starts the Analysis Service, a Splunk
mock, and a JMeter runner in a single `docker compose up -d`.
