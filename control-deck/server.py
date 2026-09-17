#!/usr/bin/env python3
"""Local-only X-88 Control Deck sidecar.

Serves the dashboard on 127.0.0.1 and proxies a deliberately small allowlist to
Ariana's Android loopback bridge. Dialogue bearer tokens live only in this
process. The Android bridge token, when configured, is read from the process
environment and is never returned to the browser.
"""
from __future__ import annotations

import json
import os
import pathlib
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

ROOT = pathlib.Path(__file__).resolve().parent
HOST = "127.0.0.1"
PORT = int(os.environ.get("X88_CONTROL_DECK_PORT", "8788"))
BRIDGE = os.environ.get("X88_BRIDGE_URL", "http://127.0.0.1:8765").rstrip("/")
BRIDGE_TOKEN = os.environ.get("X88_BRIDGE_TOKEN", "").strip()
SESSION_TOKEN: str | None = None
SESSION_EXPIRES_AT: int | None = None
MAX_BODY = 16 * 1024


def read_json(handler: SimpleHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    if length < 0 or length > MAX_BODY:
        raise ValueError("REQUEST_TOO_LARGE")
    raw = handler.rfile.read(length) if length else b"{}"
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("JSON_OBJECT_REQUIRED")
    return value


def bridge_request(method: str, path: str, body: dict | None = None, auth: str = "none") -> tuple[int, dict]:
    headers = {"Content-Type": "application/json"}
    if auth == "session":
        if not SESSION_TOKEN:
            return 503, {"ok": False, "error": "PAIRING_REQUIRED"}
        headers["Authorization"] = f"Bearer {SESSION_TOKEN}"
    elif auth == "bridge":
        if not BRIDGE_TOKEN:
            return 503, {"ok": False, "error": "BRIDGE_TOKEN_NOT_CONFIGURED"}
        headers["x-ariana-token"] = BRIDGE_TOKEN

    payload = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(f"{BRIDGE}{path}", data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=3) as response:
            raw = response.read(MAX_BODY)
            return response.status, json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as error:
        raw = error.read(MAX_BODY)
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except Exception:
            parsed = {"ok": False, "error": f"BRIDGE_HTTP_{error.code}"}
        return error.code, parsed
    except Exception as error:
        return 502, {"ok": False, "error": "BRIDGE_UNREACHABLE", "detail": error.__class__.__name__}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def log_message(self, fmt: str, *args) -> None:
        print(f"[x88-control-deck] {self.address_string()} {fmt % args}")

    def send_json(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path == "/api/health":
            self.send_json(200, {
                "ok": True,
                "bind": f"{HOST}:{PORT}",
                "bridge": BRIDGE,
                "sessionActive": bool(SESSION_TOKEN),
                "sessionExpiresAtMs": SESSION_EXPIRES_AT,
                "rawBridgeStatusEnabled": bool(BRIDGE_TOKEN),
            })
            return
        if self.path == "/api/state":
            status, body = bridge_request("GET", "/state", auth="bridge")
            self.send_json(status, body)
            return
        if self.path == "/api/apk/status":
            status, body = bridge_request("GET", "/v2/apk/status", auth="bridge")
            self.send_json(status, body)
            return
        if self.path == "/api/apk/list":
            status, body = bridge_request("GET", "/v2/apk/list", auth="bridge")
            self.send_json(status, body)
            return
        if self.path.startswith("/api/"):
            self.send_json(404, {"ok": False, "error": "NOT_FOUND"})
            return
        super().do_GET()

    def do_POST(self) -> None:
        global SESSION_TOKEN, SESSION_EXPIRES_AT
        try:
            body = read_json(self)
        except Exception as error:
            self.send_json(400, {"ok": False, "error": str(error)})
            return

        if self.path == "/api/pair":
            code = str(body.get("pairingCode", "")).strip()
            status, result = bridge_request("POST", "/v1/session", {"pairingCode": code})
            token = result.get("token") if isinstance(result, dict) else None
            if status == 200 and isinstance(token, str) and token:
                SESSION_TOKEN = token
                SESSION_EXPIRES_AT = int(result.get("expiresAtMs") or 0) or None
                safe = dict(result)
                safe.pop("token", None)
                self.send_json(200, safe)
            else:
                self.send_json(status, result)
            return

        if self.path == "/api/dialogue":
            text = str(body.get("text", "")).strip()
            status, result = bridge_request("POST", "/v1/dialogue", {"request": {"text": text}}, auth="session")
            self.send_json(status, result)
            return

        if self.path == "/api/action/proposal":
            action = str(body.get("action", "")).strip()
            status, result = bridge_request("POST", "/v1/action/proposal", {"action": action}, auth="session")
            self.send_json(status, result)
            return

        self.send_json(404, {"ok": False, "error": "NOT_FOUND"})


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"X-88 Control Deck: http://{HOST}:{PORT}")
    print(f"Android Bridge: {BRIDGE}")
    print("Raw /state and APK endpoints require X88_BRIDGE_TOKEN in the sidecar process.")
    server.serve_forever()
