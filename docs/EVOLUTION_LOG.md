# EVOLUTION LOG

This ledger records evidence-backed lessons and Codex-change proposals.

## 2026-09-17

### Learned
- External AI reports can contain useful hypotheses but must not be treated as repository truth without direct branch/build evidence.
- The existing X-88 tree already contained a valid Android 16 baseline and a functioning CI path.
- A successful CI artifact plus independently matching APK SHA-256 is a stronger release signal than prose claiming an expected build state.

### Inefficiency observed
- Parallel descriptions of alternate project layouts create drift from the real repository.
- Generic foreground-service permissions/types proposed without mapping to an actual service increase complexity and policy risk.

### Codex proposal
- Make repository evidence authoritative over assistant-generated status reports.
- Preserve the existing project tree unless a migration requirement explicitly justifies a restructure.
- Keep mutation/evolution proposal-driven and architect-gated.

Status: incorporated into Codex v1.0-canonical.