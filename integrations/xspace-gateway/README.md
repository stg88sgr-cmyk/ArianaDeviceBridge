# Ariana X-88 X Space Gateway

Local loopback gateway between the Android Ariana bridge and an X Spaces-capable agent process.

## Security model

- Binds only to `127.0.0.1`.
- Rejects non-loopback HTTP/WebSocket clients.
- Optional bearer token via `X88_GATEWAY_TOKEN`.
- Validates Space URLs and only accepts `https://x.com/i/spaces/...` or `https://twitter.com/i/spaces/...`.
- Public speaking remains a separate command so Android policy/confirmation gates can decide whether it is allowed.

## Protocol

WebSocket endpoint: `ws://127.0.0.1:8877/xspace/ws`

Commands:

```json
{"type":"join","url":"https://x.com/i/spaces/..."}
{"type":"leave"}
{"type":"speak","text":"Hello from Ariana"}
{"type":"mute"}
{"type":"unmute"}
{"type":"status"}
```

Expected agent events are newline-delimited JSON on stdout, for example:

```json
{"type":"connected"}
{"type":"transcript","speaker":"name","text":"hello","isFinal":true}
{"type":"disconnected"}
{"type":"error","message":"..."}
```

The gateway writes the same command objects as newline-delimited JSON to the child process stdin. This keeps X-specific browser/audio automation outside the Android app and lets the adapter be replaced without touching Ariana Core.

## Local smoke test

```bash
npm install
npm run build
npm run smoke
```

The smoke test uses `mock-agent.mjs`; it does not contact X.

## Real adapter

Set `XSPACE_AGENT_CMD` to an adapter process that implements the JSON-lines contract. The adapter can wrap `xspace-agent`/XActions or another browser/audio implementation. Do not store X session cookies in the repository. Pass secrets at runtime via environment variables or the device secret store.

Example startup:

```bash
XSPACE_AGENT_CMD="node real-xspace-adapter.mjs" \
X88_GATEWAY_TOKEN="..." \
node dist/server.js
```

## Status endpoints

- `GET /health`
- `GET /state`

Both are loopback-only and require the bearer token when `X88_GATEWAY_TOKEN` is configured.
