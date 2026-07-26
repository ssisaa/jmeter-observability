"""Backend tests for JMeter plugin download endpoints - v2.0.4."""
import os
import json
import pytest
import requests

BASE_URL = os.environ['REACT_APP_BACKEND_URL'].rstrip('/') if os.environ.get('REACT_APP_BACKEND_URL') else 'https://jmeter-observability.preview.emergentagent.com'
API = f"{BASE_URL}/api"


@pytest.fixture(scope="module")
def info():
    r = requests.get(f"{API}/plugin/info", timeout=30)
    assert r.status_code == 200, r.text
    return r.json()


# ---- /api/plugin/info shape ----
def test_plugin_info_shape(info):
    for k in ("jar", "demos", "smoke", "docs", "docker"):
        assert k in info, f"missing key {k}"


def test_plugin_info_jar_v204(info):
    jar = info["jar"]
    assert jar is not None
    assert jar["name"] == "jmeter-smart-observability-plugin-2.0.4.jar", jar["name"]
    assert jar["size_bytes"] > 10_000_000, jar["size_bytes"]
    assert jar["url"] == "/api/downloads/plugin.jar"


def test_plugin_info_demos_no_pdf_no_pptx(info):
    names = {d["name"] for d in info["demos"]}
    assert "Performance_Report.html" in names
    assert "Performance_Report.json" in names
    assert "Performance_Report.csv" in names
    assert "Performance_Report.pdf" not in names
    assert "Performance_Report.pptx" not in names


def test_plugin_info_docs_has_parameters(info):
    names = {d["name"] for d in info["docs"]}
    assert "PARAMETERS.md" in names, names


def test_plugin_info_docker_has_compose(info):
    names = {d["name"] for d in info["docker"]}
    assert "docker-compose.yml" in names, names


# ---- /api/downloads/plugin.jar ----
def test_download_plugin_jar(info):
    r = requests.get(f"{API}/downloads/plugin.jar", timeout=120)
    assert r.status_code == 200
    assert r.headers.get("content-type") == "application/java-archive"
    body = r.content
    assert len(body) == info["jar"]["size_bytes"]
    assert body[:4] == b"PK\x03\x04"


# ---- Demo HTML ----
def test_demo_html_v204_content():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.html", timeout=30)
    assert r.status_code == 200
    assert r.headers.get("content-type", "").startswith("text/html")
    body = r.text
    required = [
        "Visual Analytics",
        "Total samples",
        "Transactions per second",
        "Hits per second",
        "Response time series",
        "Error rate over time",
        "Latency percentiles",
        "Latency distribution",
        "Throughput per transaction",
        "p95 vs baseline",
        "v2.0.4",
    ]
    for token in required:
        assert token in body, f"missing '{token}' in demo html"
    forbidden = ["Verdict &rarr; gate flow", "Transaction dependency map"]
    for token in forbidden:
        assert token not in body, f"forbidden '{token}' still in demo html"


# ---- Demo PDF/PPTX must 404 in v2.0.4 ----
def test_demo_pdf_removed():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pdf", timeout=30)
    assert r.status_code == 404, r.status_code


def test_demo_pptx_removed():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pptx", timeout=30)
    assert r.status_code == 404, r.status_code


# ---- Demo JSON ----
def test_demo_json():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.json", timeout=30)
    assert r.status_code == 200
    data = json.loads(r.content)
    assert isinstance(data, dict)


# ---- Docs PARAMETERS.md ----
def test_docs_parameters():
    r = requests.get(f"{API}/downloads/docs/PARAMETERS.md", timeout=30)
    assert r.status_code == 200
    ct = r.headers.get("content-type", "")
    assert "text/markdown" in ct, ct
    body = r.text
    for token in ("Splunk_URL", "Metric_Sources_Json", "Fail_On_Verdict"):
        assert token in body, f"missing {token} in PARAMETERS.md"


# ---- Docker bundle ----
def test_docker_compose_yml():
    r = requests.get(f"{API}/downloads/docker/docker-compose.yml", timeout=30)
    assert r.status_code == 200
    body = r.text
    for token in ("analysis:", "splunk-mock:", "jmeter:"):
        assert token in body, f"missing {token} in docker-compose.yml"


def test_docker_analysis_dockerfile():
    r = requests.get(f"{API}/downloads/docker/analysis.Dockerfile", timeout=30)
    assert r.status_code == 200
    assert "com.smartjmeter.analysis.AnalysisServer" in r.text


def test_docker_jmeter_dockerfile():
    r = requests.get(f"{API}/downloads/docker/jmeter.Dockerfile", timeout=30)
    assert r.status_code == 200
    body = r.text
    assert "jmeter-smart-observability-plugin" in body
    assert "lib/ext" in body


# ---- Path traversal ----
# Note: FastAPI's `{name}` path parameter only matches a single URL segment,
# so a URL-encoded multi-segment traversal like `..%2F..%2Fetc%2Fpasswd` is
# collapsed by Starlette back to slashes and simply fails to route (404).
# To actually exercise the _safe_child guard we send a single-segment `..`
# which reaches the handler and must be rejected with 400.
def _assert_traversal_blocked(sub: str):
    # Ingress may normalise '..' and return 404 before it hits FastAPI; the
    # backend's _safe_child returns 400. Either way, MUST NOT be 200 / leak.
    r = requests.get(f"{API}/downloads/{sub}/%2E%2E", timeout=30)
    assert r.status_code in (400, 404), f"{sub} '..' -> {r.status_code}"
    assert b"root:" not in r.content
    r2 = requests.get(f"{API}/downloads/{sub}/..%2F..%2F..%2Fetc%2Fpasswd", timeout=30)
    assert r2.status_code in (400, 404), f"{sub} multi-seg -> {r2.status_code}"
    assert b"root:" not in r2.content


def test_docs_path_traversal_blocked():
    _assert_traversal_blocked("docs")


def test_docker_path_traversal_blocked():
    _assert_traversal_blocked("docker")


def test_demo_path_traversal_still_blocked():
    _assert_traversal_blocked("demo")
