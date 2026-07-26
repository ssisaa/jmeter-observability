from fastapi import FastAPI, APIRouter, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
from pathlib import Path
from pydantic import BaseModel, Field, ConfigDict
from typing import List
import uuid
from datetime import datetime, timezone


ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

# Root of the JMeter plugin project (Maven layout)
PLUGIN_ROOT = Path('/app/jmeter-smart-observability-plugin')
PLUGIN_TARGET = PLUGIN_ROOT / 'target'
PLUGIN_DEMO_DIR = PLUGIN_ROOT / 'docs' / 'demo'
PLUGIN_SMOKE_DIR = PLUGIN_ROOT / 'smoke-notifiers'

# MongoDB connection
mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

# Create the main app without a prefix
app = FastAPI()

# Create a router with the /api prefix
api_router = APIRouter(prefix="/api")


# Define Models
class StatusCheck(BaseModel):
    model_config = ConfigDict(extra="ignore")  # Ignore MongoDB's _id field
    
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    client_name: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class StatusCheckCreate(BaseModel):
    client_name: str

# Add your routes to the router instead of directly to app
@api_router.get("/")
async def root():
    return {"message": "Hello World"}

@api_router.post("/status", response_model=StatusCheck)
async def create_status_check(input: StatusCheckCreate):
    status_dict = input.model_dump()
    status_obj = StatusCheck(**status_dict)
    
    # Convert to dict and serialize datetime to ISO string for MongoDB
    doc = status_obj.model_dump()
    doc['timestamp'] = doc['timestamp'].isoformat()
    
    _ = await db.status_checks.insert_one(doc)
    return status_obj

@api_router.get("/status", response_model=List[StatusCheck])
async def get_status_checks():
    # Exclude MongoDB's _id field from the query results
    status_checks = await db.status_checks.find({}, {"_id": 0}).to_list(1000)
    
    # Convert ISO string timestamps back to datetime objects
    for check in status_checks:
        if isinstance(check['timestamp'], str):
            check['timestamp'] = datetime.fromisoformat(check['timestamp'])
    
    return status_checks


# ---------------------------------------------------------------------------
# JMeter Smart Observability plugin downloads
# ---------------------------------------------------------------------------

def _latest_plugin_jar() -> Path | None:
    """Return the newest jmeter-smart-observability-plugin-*.jar under target/."""
    if not PLUGIN_TARGET.is_dir():
        return None
    candidates = sorted(
        PLUGIN_TARGET.glob('jmeter-smart-observability-plugin-*.jar'),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    return candidates[0] if candidates else None


@api_router.get("/plugin/info")
async def plugin_info():
    """Metadata for the frontend download page."""
    jar = _latest_plugin_jar()
    demos = []
    if PLUGIN_DEMO_DIR.is_dir():
        for p in sorted(PLUGIN_DEMO_DIR.iterdir()):
            if p.is_file():
                demos.append({
                    "name": p.name,
                    "size_bytes": p.stat().st_size,
                    "url": f"/api/downloads/demo/{p.name}",
                })
    smoke = []
    if PLUGIN_SMOKE_DIR.is_dir():
        for p in sorted(PLUGIN_SMOKE_DIR.iterdir()):
            if p.is_file() and not p.name.startswith('.'):
                smoke.append({
                    "name": p.name,
                    "size_bytes": p.stat().st_size,
                    "url": f"/api/downloads/smoke/{p.name}",
                })
    return {
        "jar": None if jar is None else {
            "name": jar.name,
            "size_bytes": jar.stat().st_size,
            "modified_at": datetime.fromtimestamp(jar.stat().st_mtime, tz=timezone.utc).isoformat(),
            "url": "/api/downloads/plugin.jar",
        },
        "demos": demos,
        "smoke": smoke,
    }


@api_router.get("/downloads/plugin.jar")
async def download_plugin_jar():
    """Stream the latest built plugin jar."""
    jar = _latest_plugin_jar()
    if jar is None:
        raise HTTPException(status_code=404, detail="Plugin jar not built yet - run 'mvn clean package'.")
    return FileResponse(
        path=str(jar),
        media_type="application/java-archive",
        filename=jar.name,
    )


def _safe_child(base: Path, name: str) -> Path:
    """Prevent path traversal - only allow direct children of base."""
    p = (base / name).resolve()
    if base.resolve() not in p.parents and p != base.resolve():
        raise HTTPException(status_code=400, detail="Invalid path")
    if not p.is_file():
        raise HTTPException(status_code=404, detail=f"{name} not found")
    return p


@api_router.get("/downloads/demo/{name}")
async def download_demo(name: str):
    """Serve a demo report file (HTML / PDF / PPTX / JSON)."""
    p = _safe_child(PLUGIN_DEMO_DIR, name)
    media = {
        ".html": "text/html",
        ".pdf": "application/pdf",
        ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ".json": "application/json",
        ".csv": "text/csv",
        ".svg": "image/svg+xml",
    }.get(p.suffix.lower(), "application/octet-stream")
    # HTML is served inline (viewable), everything else as attachment.
    headers = {}
    if p.suffix.lower() != ".html":
        headers["Content-Disposition"] = f'attachment; filename="{p.name}"'
    return FileResponse(path=str(p), media_type=media, headers=headers)


@api_router.get("/downloads/smoke/{name}")
async def download_smoke(name: str):
    """Serve a file from the notifier smoke deck."""
    p = _safe_child(PLUGIN_SMOKE_DIR, name)
    media = {
        ".jmx": "application/xml",
        ".sh": "text/x-shellscript",
        ".md": "text/markdown",
    }.get(p.suffix.lower(), "text/plain")
    return FileResponse(
        path=str(p),
        media_type=media,
        headers={"Content-Disposition": f'attachment; filename="{p.name}"'},
    )

# Include the router in the main app
app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=os.environ.get('CORS_ORIGINS', '*').split(','),
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()