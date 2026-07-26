"""Minimal Splunk HEC mock used by the smart-observability demo stack."""

from datetime import datetime, timezone

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

app = FastAPI(title="Splunk HEC mock", version="2.0.4")

_events: list[dict] = []


@app.get("/healthz")
async def healthz():
    return {"ok": True, "events_received": len(_events)}


@app.post("/services/collector")
async def collector(request: Request):
    body = await request.body()
    text = body.decode("utf-8", errors="replace")
    # HEC accepts NDJSON with one event per line
    for line in text.splitlines():
        if line.strip():
            _events.append({
                "at": datetime.now(timezone.utc).isoformat(),
                "raw": line[:2000],
            })
    return JSONResponse({"text": "Success", "code": 0})


@app.get("/services/collector/health")
async def collector_health():
    return {"text": "HEC is healthy", "code": 200}


@app.get("/events")
async def events(limit: int = 100):
    return {"count": len(_events), "events": _events[-limit:]}
