# X-88 Control Deck V1

Local-first control surface for Ariana X-88.

## Architecture

```text
Browser Control Deck (127.0.0.1:8788)
        |
        v
Python sidecar, local-only
        |
        +--> /v1/session          pairing exchange
        +--> /v1/dialogue         Ariana dialogue
        +--> /v1/action/proposal  policy-gated proposals
        +--> /state               optional raw status
        +--> /v2/apk/*            optional APK status
        |
        v
Android LocalBridgeServer (127.0.0.1:8765)
```

The browser never receives the Android bridge token or the dialogue bearer token.
The dialogue token exists only in sidecar memory and disappears when the sidecar stops.

## Run in Termux

```bash
cd ~/ArianaDeviceBridge
python control-deck/server.py
```

Open `http://127.0.0.1:8788` on the same device.

Pairing uses the existing X-88 visible pairing flow. Enter the short-lived pairing code in the dashboard. Dialogue and action proposals then use the short-lived session token.

Raw `/state` and `/v2/apk/*` are optional because the current Android bridge protects them with its separate app-private bridge token. If an authorized local launcher intentionally provides that token to the sidecar process, set `X88_BRIDGE_TOKEN` before starting. Do not place it in HTML, JavaScript, source control, shell history, or logs.

## V1 truth boundary

Implemented now: local dashboard, local-only sidecar, session pairing, dialogue proxy, action proposal proxy, real bridge/APK polling when the bridge token is explicitly available.

Not claimed yet: browser-triggered AppPrompter build execution. The Kotlin AppPrompter, ProjectPlanner, Compose generator, BuildRunner, and RepairLoop already exist in the Android project, but V1 does not pretend that a browser button can execute them until an explicit bridge adapter is added and tested.
