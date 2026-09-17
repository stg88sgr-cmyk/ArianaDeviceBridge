# ARIANA X-88 / LUNA XXY Termux Runtime

Canonical execution layer for the Android/Termux runtime.

## Architecture

- Android app: control plane
- Android `LocalBridgeServer`: sole owner of `127.0.0.1:8765`
- Termux + tmux: execution plane
- X-88: control/orchestration sessions
- Luna XXY: creation/worker sessions
- `x88-bridge`: coordinator/client slot, never a second port-8765 listener

This split prevents two bridge implementations from racing for the same loopback port. The Android bridge already owns authentication, policy gates, rate limiting and action routing, so Termux does not duplicate them.

The bootstrap is idempotent. Existing tmux sessions are preserved and are not duplicated.

## Sessions

- `x88-bridge`
- `x88-build`
- `x88-logs`
- `luna-worker`
- `luna-bridge`
- `luna-build`
- `luna-xxy-worker`
- `luna-logs`

## Canonical adapters

The installer deploys:

- `~/bin/x88-adapter.sh`
- `~/bin/luna-adapter.sh`
- `~/bin/tmux-adapter-common.sh`
- `~/bin/tmux-job-runner.sh`

Adapter jobs execute in short-lived tmux windows inside the selected persistent session. Commands are written to mode-600 job files, bounded to 4096 bytes by default, and results are collected with a configurable timeout. This avoids injecting commands into a pane that may not be sitting at an interactive shell prompt.

Examples:

```bash
~/bin/x88-adapter.sh BUILD 'printf "X-88 OK\n"'
~/bin/luna-adapter.sh LUNA_XXY_WORKER 'printf "Luna XXY OK\n"'
```

## Install on Termux

From the repository root:

```bash
chmod +x termux-runtime/*.sh
./termux-runtime/install-termux-runtime.sh
```

If prerequisites are missing:

```bash
pkg install tmux iproute2 -y
```

## Preflight

The Android master gate must be enabled so its loopback bridge is listening before the full runtime gate can become green.

```bash
~/bin/x88-final-gate.sh
```

This checks bootstrap, all eight sessions, the Android-owned localhost bridge and both adapters.

## Reboot proof

Install the Termux:Boot companion app, allow it to run, reboot Android, do not manually start X-88/Luna, then open Termux and run:

```bash
~/bin/x88-final-gate.sh --require-boot
tail -n 100 ~/.ariana/logs/boot.log
```

A successful reboot gate proves that the Termux execution plane came back through the full Android restart path and that the Android bridge is reachable again.

## Runtime configuration

`~/.ariana/x88-runtime.env` keeps machine-specific runtime configuration out of Git. The canonical bridge ownership is:

```bash
X88_BRIDGE_HOST=127.0.0.1
X88_BRIDGE_PORT=8765
X88_BRIDGE_OWNER=android
```

Worker commands may be overridden there when real Luna workers are installed. Do not configure Termux to bind another service to `127.0.0.1:8765`.
