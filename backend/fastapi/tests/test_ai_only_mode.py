from pathlib import Path
import sys

from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[3]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(ROOT / "backend" / "fastapi") not in sys.path:
    sys.path.insert(0, str(ROOT / "backend" / "fastapi"))

from main import create_app


def test_ai_only_mode_hides_core_recommend_and_web(tmp_path, monkeypatch):
    dist = tmp_path / "web"
    dist.mkdir()
    (dist / "index.html").write_text("<title>Dippie</title>", encoding="utf-8")
    monkeypatch.setenv("DIPPIE_AI_ONLY", "true")
    app = create_app(
        web_dist=dist,
        model_mode="light",
        load_default=False,
        database_url=f"sqlite:///{tmp_path / 'ai_only.db'}",
        sync_path=tmp_path / "state.json",
        cache_dir=tmp_path / "cache",
    )

    try:
        with TestClient(app) as client:
            assert client.post("/api/auth/login", json={"usernameOrEmail": "admin", "password": "password123"}).status_code == 404
            assert client.post("/api/recommend", json={"mood": "happy"}).status_code == 404
            assert client.get("/search").status_code == 404
            health_response = client.get("/health")
            assert health_response.status_code == 200
            root_response = client.get("/")
            assert root_response.status_code == 200
            assert root_response.json()["service"] == "Dippie AI Gateway"
    finally:
        app.state.runtime.db.engine.dispose()


def test_default_mode_keeps_core_and_web_enabled(tmp_path, monkeypatch):
    dist = tmp_path / "web"
    dist.mkdir()
    (dist / "index.html").write_text("<title>Dippie</title>", encoding="utf-8")
    monkeypatch.setenv("DIPPIE_AI_ONLY", "false")
    app = create_app(
        web_dist=dist,
        model_mode="light",
        load_default=False,
        database_url=f"sqlite:///{tmp_path / 'full.db'}",
        sync_path=tmp_path / "state.json",
        cache_dir=tmp_path / "cache",
    )

    try:
        with TestClient(app) as client:
            assert client.post("/api/auth/login", json={}).status_code != 404
            assert client.get("/health").status_code == 200
            assert client.get("/").text == "<title>Dippie</title>"
    finally:
        app.state.runtime.db.engine.dispose()
