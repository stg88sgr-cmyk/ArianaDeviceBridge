#!/data/data/com.termux/files/usr/bin/python
"""X88 Tri-Sidecar localhost relay.

The Android APK never receives the OpenAI API key. This process binds only to
127.0.0.1 and accepts requests carrying a separate X88 sidecar bearer token.
"""

from __future__ import annotations

import hmac
import json
import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

HOST = "127.0.0.1"
PORT = int(os.environ.get("X88_SIDECAR_PORT", "8766"))
MODEL = os.environ.get("OPENAI_MODEL", "gpt-5.6-sol").strip() or "gpt-5.6-sol"
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "").strip()
SIDECAR_TOKEN = os.environ.get("X88_SIDECAR_TOKEN", "").strip()
OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
MAX_BODY_BYTES = 64 * 1024
MAX_MESSAGE_CHARS = 4_000
MAX_HISTORY_ITEMS = 24
MAX_HISTORY_TEXT_CHARS = 3_000
MAX_REPLY_CHARS = 12_000

CHANNEL_INSTRUCTIONS = {
    "observe": (
        "You are the OBSERVE sidecar for Ariana X-88. Analyze facts, state, logs, "
        "architecture and inconsistencies. Be precise. Do not pretend to have device "
        "access that was not supplied in the conversation."
    ),
    "build": (
        "You are the BUILD sidecar for Ariana X-88. Turn requirements into concrete, "
        "testable implementation steps and code-oriented decisions. Prefer real paths, "
        "interfaces and verification over fictional completion claims."
    ),
    "verify": (
        "You are the VERIFY sidecar for Ariana X-88. Review claims, code plans and test "
        "evidence. Identify missing proof, regressions and unsafe assumptions before "
        "calling work complete."
    ),
}

BASE_INSTRUCTIONS = (
    "You are an OpenAI-powered Ariana X-88 sidecar running through the user's own local "
    "relay. This is not the Meta AI path and must not route to Meta AI. The three sidecar "
    "channels are independent; use only the history supplied for the current channel. "
    "Do not claim to be the same ChatGPT conversation or to share ChatGPT account memory. "
    "Reply in German unless the user requests another language."
)


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(body)


def clean_text(value: Any, limit: int) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.replace("\x00", " ").split()).strip()[:limit]


def authorized(header: str | None) -> bool:
    if not SIDECAR_TOKEN or not header or not header.startswith("Bearer "):
        return False
    provided = header[7:].strip()
    return bool(provided) and hmac.compare_digest(provided, SIDECAR_TOKEN)


def build_transcript(history: Any, message: str) -> str:
    lines: list[str] = []
    if isinstance(history, list):
        for item in history[-MAX_HISTORY_ITEMS:]:
            if not isinstance(item, dict):
                continue
            role = item.get("role")
            if role not in ("user", "assistant"):
                continue
            text = clean_text(item.get("text"), MAX_HISTORY_TEXT_CHARS)
            if not text:
                continue
            who = "USER" if role == "user" else "ASSISTANT"
            lines.append(f"{who}: {text}")
    lines.append(f"USER: {message}")
    return "\n\n".join(lines)


def extract_output_text(payload: dict[str, Any]) -> str:
    direct = clean_text(payload.get("output_text"), MAX_REPLY_CHARS)
    if direct:
        return direct
    chunks: list[str] = []
    output = payload.get("output")
    if isinstance(output, list):
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict):
                    continue
                if part.get("type") not in ("output_text", "text"):
                    continue
                text = clean_text(part.get("text"), MAX_REPLY_CHARS)
                if text:
                    chunks.append(text)
    return "\n".join(chunks).strip()[:MAX_REPLY_CHARS]


def call_openai(channel: str, history: Any, message: str) -> str:
    if not OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY_MISSING")
    instructions = f"{BASE_INSTRUCTIONS}\n\n{CHANNEL_INSTRUCTIONS[channel]}"
    payload = {
        "model": MODEL,
        "instructions": instructions,
        "input": build_transcript(history, message),
        "max_output_tokens": 1200,
    }
    request = urllib.request.Request(
        OPENAI_RESPONSES_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {OPENAI_API_KEY}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "Ariana-X88-Sidecar/1.0",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=80) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"OPENAI_HTTP_{error.code}") from error
    except urllib.error.URLError as error:
        raise RuntimeError("OPENAI_UNREACHABLE") from error
    except json.JSONDecodeError as error:
        raise RuntimeError("OPENAI_INVALID_JSON") from error

    if not isinstance(data, dict):
        raise RuntimeError("OPENAI_INVALID_RESPONSE")
    reply = extract_output_text(data)
    if not reply:
        raise RuntimeError("OPENAI_EMPTY_REPLY")
    return reply


class Handler(BaseHTTPRequestHandler):
    server_version = "X88SidecarRelay/1.0"

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("[x88-sidecar] %s - %s\n" % (self.address_string(), format % args))

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            json_response(self, 404, {"ok": False, "error": "NOT_FOUND"})
            return
        ready = bool(OPENAI_API_KEY and SIDECAR_TOKEN)
        json_response(
            self,
            200 if ready else 503,
            {
                "ok": ready,
                "provider": "openai",
                "model": MODEL,
                "bound": f"{HOST}:{PORT}",
                "error": None if ready else "RELAY_NOT_CONFIGURED",
            },
        )

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/sidecar":
            json_response(self, 404, {"ok": False, "error": "NOT_FOUND"})
            return
        if not authorized(self.headers.get("Authorization")):
            json_response(self, 401, {"ok": False, "error": "UNAUTHORIZED"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            json_response(self, 400, {"ok": False, "error": "INVALID_CONTENT_LENGTH"})
            return
        if length <= 0 or length > MAX_BODY_BYTES:
            json_response(self, 413, {"ok": False, "error": "BODY_SIZE_INVALID"})
            return

        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            json_response(self, 400, {"ok": False, "error": "INVALID_JSON"})
            return
        if not isinstance(payload, dict):
            json_response(self, 400, {"ok": False, "error": "INVALID_REQUEST"})
            return

        channel = clean_text(payload.get("channel"), 24).lower()
        if channel not in CHANNEL_INSTRUCTIONS:
            json_response(self, 400, {"ok": False, "error": "INVALID_CHANNEL"})
            return
        message = clean_text(payload.get("message"), MAX_MESSAGE_CHARS)
        if not message:
            json_response(self, 400, {"ok": False, "error": "EMPTY_MESSAGE"})
            return

        try:
            reply = call_openai(channel, payload.get("history"), message)
        except RuntimeError as error:
            json_response(self, 502, {"ok": False, "error": str(error)[:80]})
            return
        except Exception:
            json_response(self, 502, {"ok": False, "error": "UPSTREAM_FAILED"})
            return

        json_response(self, 200, {"ok": True, "reply": reply, "model": MODEL, "channel": channel})


if __name__ == "__main__":
    if HOST != "127.0.0.1":
        raise SystemExit("Refusing non-loopback bind")
    print(f"X88 sidecar relay listening on http://{HOST}:{PORT} with model={MODEL}", flush=True)
    if not OPENAI_API_KEY:
        print("WARNING: OPENAI_API_KEY missing; /health will stay not ready", file=sys.stderr, flush=True)
    if not SIDECAR_TOKEN:
        print("WARNING: X88_SIDECAR_TOKEN missing; /health will stay not ready", file=sys.stderr, flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
