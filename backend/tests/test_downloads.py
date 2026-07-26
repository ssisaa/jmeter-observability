"""Backend tests for JMeter plugin download endpoints."""
import os
import json
import pytest
import requests

BASE_URL = os.environ.get('REACT_APP_BACKEND_URL', 'https://jmeter-observability.preview.emergentagent.com').rstrip('/')
API = f"{BASE_URL}/api"


@pytest.fixture(scope="module")
def info():
    r = requests.get(f"{API}/plugin/info", timeout=30)
    assert r.status_code == 200, r.text
    return r.json()


# ----- /api/plugin/info -----
def test_plugin_info_shape(info):
    for k in ("jar", "demos", "smoke"):
        assert k in info, f"missing key {k}"
    assert info["jar"] is not None
    jar = info["jar"]
    assert jar["name"].startswith("jmeter-smart-observability-plugin-") and jar["name"].endswith(".jar")
    assert jar["size_bytes"] > 0
    assert jar["url"] == "/api/downloads/plugin.jar"


# ----- /api/downloads/plugin.jar -----
def test_download_plugin_jar(info):
    r = requests.get(f"{API}/downloads/plugin.jar", timeout=120)
    assert r.status_code == 200
    assert r.headers.get("content-type") == "application/java-archive"
    assert "attachment" in r.headers.get("content-disposition", "").lower()
    body = r.content
    assert len(body) == info["jar"]["size_bytes"]
    assert body[:4] == b"PK\x03\x04"


# ----- demo HTML (inline) -----
def test_demo_html_inline():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.html", timeout=30)
    assert r.status_code == 200
    assert r.headers.get("content-type", "").startswith("text/html")
    assert "attachment" not in r.headers.get("content-disposition", "").lower()


# ----- demo PDF -----
def test_demo_pdf():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pdf", timeout=30)
    assert r.status_code == 200
    assert r.headers.get("content-type") == "application/pdf"
    assert "attachment" in r.headers.get("content-disposition", "").lower()
    assert r.content[:5] == b"%PDF-"


# ----- demo PPTX -----
def test_demo_pptx():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.pptx", timeout=30)
    assert r.status_code == 200
    assert r.headers.get("content-type") == "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    assert r.content[:2] == b"PK"


# ----- demo JSON -----
def test_demo_json():
    r = requests.get(f"{API}/downloads/demo/Performance_Report.json", timeout=30)
    assert r.status_code == 200
    data = json.loads(r.content)
    assert "schema_version" in data


# ----- smoke jmx -----
def test_smoke_jmx():
    r = requests.get(f"{API}/downloads/smoke/smoke-notifiers.jmx", timeout=30)
    assert r.status_code == 200
    assert "attachment" in r.headers.get("content-disposition", "").lower()


# ----- Path traversal blocked -----
def test_path_traversal_blocked():
    r = requests.get(f"{API}/downloads/demo/..%2F..%2F..%2Fetc%2Fpasswd", timeout=30)
    assert r.status_code in (400, 404), f"got {r.status_code}"


def test_nonexistent_returns_404():
    r = requests.get(f"{API}/downloads/demo/does-not-exist.html", timeout=30)
    assert r.status_code == 404
