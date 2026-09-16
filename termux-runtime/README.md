# ARIANA X-88 / LUNA XXY Termux Runtime

Canonical execution layer for the Android/Termux runtime.

## Architecture

- Android app: control plane
- Termux + tmux: execution plane
- X-88: control/orchestration sessions
- Luna XXY: creation/worker sessions
- Local bridge: `127.0.0.1:8765`

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

```bash
~/bin/x88-final-gate.sh
```

This checks bootstrap, all eight sessions, the localhost bridge and both adapters.

## Reboot proof

Install the Termux:Boot companion app, allow it to run, reboot Android, do not manually start X-88/Luna, then open Termux and run:

```bash
~/bin/x88-final-gate.sh --require-boot
tail -n 100 ~/.ariana/logs/boot.log
```

A successful reboot gate proves that the execution plane survived the full Android restart path.

## Runtime command overrides

`~/.ariana/x88-runtime.env` can replace default session commands without modifying repository files. This keeps machine-specific paths and secrets out of Git.

Example:

```bash
X88_BRIDGE_CMD='exec "$HOME/bin/x88-real-bridge.sh"'
LUNA_XXY_WORKER_CMD='exec "$HOME/bin/luna-xxy-real-worker.sh"'
```

The bridge remains localhost-only by design. Do not expose the control bridge on `0.0.0.0`.
