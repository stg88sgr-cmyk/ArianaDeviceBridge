# X88 Control Deck V1 Plan

## Goal

Unify Ariana dialogue, device action proposals, Android bridge state, APK state, AppPrompter pipeline visibility, and Termux execution behind one local control surface without replacing the existing X-88 core.

## Layers

1. Control Deck UI: presentation only. It never owns secrets or authority.
2. Local sidecar: loopback-only HTTP server and narrow reverse proxy.
3. Android LocalBridgeServer: authoritative policy/session boundary.
4. X-88 system core: capabilities, sessions, emergency stop, audit.
5. AppPrompter: Prompt -> AppSpec -> Validator -> ProjectPlanner -> ComposeProjectGenerator.
6. Build automation: test -> lint -> assemble -> bounded repair loop.
7. Termux/GitHub execution planes: real process/CI execution and artifact evidence.

## Security invariants

- Bind control deck and Android bridge to loopback only.
- Keep browser free of bridge/session secrets.
- CONFIRM actions remain human-in-the-loop.
- No UI string may claim BUILD SUCCESSFUL, SIGNED, VERIFIED, or DEVICE GREEN without observed evidence.
- Google Play Protect and Android/Samsung security controls are not bypassed.
- AppPrompter build execution is added only through an explicit allowlisted adapter.

## Next adapter

`X88PrompterBridgeAdapter` should expose only structured operations:

- `prompter.compile`
- `prompter.generate_project`
- `build.start`
- `build.status`
- `artifact.list`

Every operation returns structured state and audit IDs. Build start must use a real execution plane and cannot fabricate success.
