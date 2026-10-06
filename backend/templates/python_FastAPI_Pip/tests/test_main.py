from fastapi.testclient import TestClient
from app.main import app
def test_ping(): assert TestClient(app).get("/api/v1/ping").status_code == 200
