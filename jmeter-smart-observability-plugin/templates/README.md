# SMART OBSERVABILITY DEFAULT - JMeter template

The `smart-observability-default.jmx` file in this folder is a JMeter test
plan whose Backend Listener already contains every parameter row the
Smart Observability plugin understands, with sensible defaults, sample
values and short in-tooltip descriptions. Fill in only the values you
need; every optional sink (Splunk, notifiers, LLM, cloud collectors) is
skipped silently when its value is blank.

## Two ways to use it

### 1. As a plain JMX file (recommended)

Open `smart-observability-default.jmx` in JMeter (File -> Open) and start
customising. Every row you would otherwise have to add manually is
already there.

### 2. As a JMeter template

Copy both files into JMeter's `bin/templates/`:

```bash
cp smart-observability-default.jmx  $JMETER_HOME/bin/templates/
cp templates.xml                    $JMETER_HOME/bin/templates/
```

Now File -> Templates -> Load... shows a **SMART OBSERVABILITY DEFAULT**
option that expands into a full test plan on click.

## Parameter reference

Every field has a JMeter tooltip (the "metadata" column in the
Backend Listener panel). The full reference with sample values and
where to source each value lives at [`docs/PARAMETERS.md`](../docs/PARAMETERS.md).

## Common properties you can pass on the CLI

```bash
jmeter -n -t smart-observability-default.jmx -l results.jtl \
  -Jsplunk.token=$SPLUNK_TOKEN \
  -Jo11y.token=$O11Y_TOKEN \
  -Jplugin.slack=$SLACK_WEBHOOK \
  -Jplugin.jira=$JIRA_CONFIG
```

The template already uses `${__P(...)}` for all secret fields so nothing
sensitive lands in the JMX file itself.
