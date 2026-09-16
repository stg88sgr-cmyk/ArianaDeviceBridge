# Ariana X-88 X Space Gateway

Local-only adapter between Ariana X-88 and an X Space transport agent.

## Architecture

```text
X Space
  ↕
xspace-agent / compatible adapter
  ↕ JSON lines
Ariana X-88 XSpace Gateway
  ↕ WebSocket ws://127.0.0.1:8877/xspace/ws
Android XSpaceGatewayClient
  ↕
Ariana Device Bridge / Core
```

The gateway is deliberately bound to `127.0.0.1`. It is not a LAN service.

## Commands

The Android client can send:

- `join`
- `leave`
- `speak`
- `mute`
- `unmute`
- `status`

The gateway forwards transport events including transcripts and participant/status updates back to Android.

## Development smoke agent

Without a real X adapter, the gateway starts `mock-agent.mjs` by default. This gives deterministic protocol smoke coverage without X credentials.

```bash
npm install
npm run build
npm run smoke
```

## Real xspace-agent adapter

`xspace-agent-adapter.mjs` wraps the public `XSpaceAgent` lifecycle and event API behind the same JSON-lines contract used by the mock agent.

Example environment:

```bash
export X_AUTH_TOKEN='...'
export X_CT0='...'
export XSPACE_AGENT_CMD='node xspace-agent-adapter.mjs'
export XSPACE_AGENT_MODULE='xspace-agent'
node dist/server.js
```

Ariana Core remains the reasoning owner. The adapter config uses `autoRespond: false`; incoming transcription is sent to Ariana and outgoing Ariana text can be returned with the `speak` command.

## Android integration

The Android app contains `de.snowworks.ariana.xspace.XSpaceGatewayClient`, fixed to the loopback gateway endpoint. `ActionPolicy` classifies status/disconnect as safe and all public Space actions as confirmation-required.

CI verifies both sides:

- TypeScript gateway build + protocol smoke test
- Android `:app:compileDebugKotlin`

## Security invariants

- gateway binds only to loopback
- optional `X88_GATEWAY_TOKEN` bearer token
- X Space URL validation before spawn/join
- public speaking/join/mute state changes stay behind the Android confirmation policy
- credentials remain environment/config values and are never committed
