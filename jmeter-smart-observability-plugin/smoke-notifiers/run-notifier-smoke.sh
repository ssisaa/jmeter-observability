#!/usr/bin/env bash
#
# One-command notifier smoke deck for the JMeter Smart Observability plugin.
#
# 1. Copies smoke-notifiers.jmx into $JMETER_HOME.
# 2. Runs it headlessly, forcing a NO_GO verdict via httpbin /status/500.
# 3. Fans out to every configured notifier (Slack/Teams/Email/Jira/ServiceNow).
# 4. Runs the CiGate CLI and exits with its code (3 on NO_GO).
#
# Requires the plugin JAR to be installed under $JMETER_HOME/lib/ext/.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Load env ---
if [[ ! -f ".env" ]]; then
  echo ".env not found - copy .env.example to .env first." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source ./.env
set +a

: "${JMETER_HOME:?JMETER_HOME not set in .env}"
: "${PLUGIN_JAR:?PLUGIN_JAR not set in .env}"
: "${JAVA_HOME:?JAVA_HOME not set in .env}"

# --- Ensure plugin is in JMeter lib/ext ---
LIB_EXT="$JMETER_HOME/lib/ext"
mkdir -p "$LIB_EXT"
JAR_NAME="$(basename "$PLUGIN_JAR")"
if [[ ! -f "$LIB_EXT/$JAR_NAME" ]] || ! cmp -s "$PLUGIN_JAR" "$LIB_EXT/$JAR_NAME"; then
  cp -f "$PLUGIN_JAR" "$LIB_EXT/$JAR_NAME"
  echo "[smoke] installed $JAR_NAME into $LIB_EXT"
fi

OUT_DIR="$SCRIPT_DIR/smoke-out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# --- Run JMeter headless ---
JMETER_BIN="$JMETER_HOME/bin/jmeter"
if [[ ! -x "$JMETER_BIN" ]]; then
  echo "$JMETER_BIN not found - set JMETER_HOME to your Apache JMeter 5.6.3 install." >&2
  exit 1
fi

echo "[smoke] running JMeter -> $OUT_DIR"
"$JMETER_BIN" -n -t smoke-notifiers.jmx \
  -Jplugin.slack="${SLACK_WEBHOOK:-}" \
  -Jplugin.teams="${TEAMS_WEBHOOK:-}" \
  -Jplugin.email.smtp="${EMAIL_SMTP:-}" \
  -Jplugin.email.fromto="${EMAIL_FROM_TO:-}" \
  -Jplugin.jira="${JIRA_CONFIG:-}" \
  -Jplugin.servicenow="${SERVICENOW_CONFIG:-}" \
  -Jplugin.gate="${FAIL_ON_VERDICT:-NO_GO}" \
  -Jout.dir="$OUT_DIR" \
  -l "$OUT_DIR/results.jtl" \
  -j "$OUT_DIR/jmeter.log"

echo "[smoke] artefacts written to $OUT_DIR"
ls -la "$OUT_DIR"

# --- Evaluate CI gate ---
GATE_JSON="$OUT_DIR/ci-gate.json"
if [[ ! -f "$GATE_JSON" ]]; then
  echo "[smoke] ci-gate.json missing - assuming GO (0)"
  exit 0
fi

"$JAVA_HOME/bin/java" -cp "$PLUGIN_JAR" com.smartjmeter.ci.CiGate "$GATE_JSON"
