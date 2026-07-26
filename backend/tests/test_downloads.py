"""Backend tests for JMeter plugin download endpoints - v2.0.6."""
import os
import json
import pytest
import requests

BASE_URL = os.environ['REACT_APP_BACKEND_URL'].rstrip('/')
API = f"{BASE_URL}/api"


@pytest.fixture(scope="module")
def info():
    r = requests.get(f"{API}/plugin/info", timeout=30)
    assert r.status_code == 200, r.text
    return r.json()


# ---- /api/plugin/info shape ----
def test_plugin_info_shape(info):
    for k in ("jar", "demos", "smoke", "docs", "docker"):
        assert k in info


def test_plugin_info_jar_v206(info):
    jar = info["jar"]
    assert jar["name"] == "jmeter-smart-observability-plugin-2.0.6.jar", jar["name"]
    assert jar["size_bytes"] > 10_000_000
    assert jar["url"] == "/api/downloads/plugin.jar"


def test_plugin_info_demos_v206(info):
    names = {d["name"] for d in info["demos"]}
    assert "Performance_Report.md" in names
    assert "Performance_Report.html" in names
    assert "Performance_Report.json" in names
    assert "Performance_Report.csv" in names
    assert "Performance_Report.pdf" not in names
    assert "Performance_Report.pptx" not in names


# ---- /api/downloads/plugin.jar ----
def test_download_plugin_jar(info):
    r = requests.get(f"{API}/downloads/plugin.jar", timeout=180)
    assert r.status_code == 200
    assert r.headers.get("content-type") == "application/java-archive"
    body = r.content
    assert len(body) == info["jar"]["size_bytes"]
    assert body[:4] == b"PK\x03\x04"


# ---- Demo Markdown ----
def test_demo_markdown_v206():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.md", timeout=30)
    assert r.status_code == 200
    ct = r.headers.get("content-type", "")
    assert ct.startswith("text/markdown"), ct
    body = r.text
    assert body.startswith("# Performance Test Report"), body[:120]
    required = [
        "## Executive Summary",
        "## Key Metrics",
        "## Key Issues",
        "## Per-Transaction Statistics",
        "## Root Cause Analysis",
        "## Recommendations",
        "## Rollout Plan",
        "## Rollback Triggers",
        "v2.0.6",
        "|---|---|",
        "| Transaction | Samples | Errors | Err % | p50 | p95 | p99 | Max |",
    ]
    for t in required:
        assert t in body, f"missing '{t}' in Performance_Report.md"


# ---- Demo HTML v2.0.6 ----
def test_demo_html_v206_content():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.html", timeout=30)
    assert r.status_code == 200
    assert r.headers.get("content-type", "").startswith("text/html")
    body = r.text
    required = [
        "Visual Analytics &mdash; JMeter",
        "Visual Analytics &mdash; Splunk Observability",
        "Visual Analytics &mdash; AWS CloudWatch",
        "Test window",
        "Active virtual users",
        "Baseline delta table",
        "Executive Summary",
        "Root Cause Analysis",
        "Recommendations",
        "v2.0.6",
    ]
    for t in required:
        assert t in body, f"missing '{t}' in demo html"
    forbidden = [
        "Latency percentiles",
        "Latency distribution",
        "Throughput per transaction (samples, top 12)",
    ]
    for t in forbidden:
        assert t not in body, f"forbidden '{t}' still in demo html"


# ---- Demo PDF/PPTX must 404 ----
def test_demo_pdf_removed():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pdf", timeout=30)
    assert r.status_code == 404


def test_demo_pptx_removed():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pptx", timeout=30)
    assert r.status_code == 404


# ---- Demo JSON still there ----
def test_demo_json():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.json", timeout=30)
    assert r.status_code == 200
    assert isinstance(json.loads(r.content), dict)


# ---- Docs PARAMETERS.md ----
def test_docs_parameters():
    r = requests.get(f"{API}/downloads/docs/PARAMETERS.md", timeout=30)
    assert r.status_code == 200
    assert "text/markdown" in r.headers.get("content-type", "")
    body = r.text
    for token in ("Splunk_URL", "Metric_Sources_Json"):
        assert token in body


# ---- Path traversal ----
def test_docs_path_traversal_blocked():
    r = requests.get(f"{API}/downloads/docs/..%2Fetc%2Fpasswd", timeout=30)
    # ingress may normalise to 404; backend guard returns 400. Must NOT be 200.
    assert r.status_code in (400, 404), r.status_code
    assert b"root:" not in r.content
