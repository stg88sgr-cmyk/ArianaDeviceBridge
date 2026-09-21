# X-88 Termux/tmux Runtime

Persistent execution layer for ARIANA X-88 on Termux.

## Layout
- Android app: control plane
- Desktop Commander remote bridge: transport
- Termux + tmux: execution plane
- Sessions: `x88-bridge`, `x88-build`, `luna-worker`, `x88-logs`

## Install
```bash
pkg install tmux nodejs git openjdk-17 -y
cd ~/ArianaDeviceBridge
bash integrations/termux/tmux-runtime/install-x88-tmux-runtime.sh --bootstrap
```

## Commands
```bash
~/bin/x88-tmux-status.sh
~/bin/x88-adapter.sh STATUS
~/bin/x88-adapter.sh BUILD ~/ArianaDeviceBridge assembleDebug
~/bin/x88-adapter.sh LOGS all 100
```

Luna is intentionally bounded. Set `X88_LUNA_CMD` to the approved local Luna start command before using `LUNA_WORKER start` or `restart`. Arbitrary remote exec is not exposed.

## Safety
No Android permissions are changed. Build paths must resolve below `$HOME`. Gradle task names are validated. Runtime output is redacted for common token forms. Multi-session stop requires explicit `--force`.
