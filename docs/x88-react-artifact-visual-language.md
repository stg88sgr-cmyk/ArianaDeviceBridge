# X-88 React Artifact Visual Language

Source reference: user-provided compiled React/Tailwind production artifact, reviewed 2026-09-17.

## Native translation target

The web artifact is treated as a visual reference, not as Android runtime code. The Android app keeps the existing native X-88 runtime and procedural renderer.

## Extracted visual DNA

- Base space: near-black `#020205` / `#05070D`
- Primary energy: cyan `#67E8F9` / `#22D3EE`
- Secondary energy: magenta/fuchsia `#E879F9`
- Core geometry: amber/gold `#FDE68A`
- Highlight: white with low-alpha overlays
- Geometry: concentric circles, radial ticks, crosshair lines, nested percentage insets
- Motion vocabulary: pulse, ping, twinkle, short buzz/glitch, slow orbital rotation
- Compositing vocabulary: screen-like glow, soft-light overlays, small blur radii, thin luminous strokes
- Typography: compact mono/status labels with wide tracking and uppercase HUD treatment

## Mapping into X88AvatarView

Existing native renderer already covers the primary palette, circular shell, radial ticks, mode-driven energy, breathing, gaze, blink, head drift and expression/viseme states.

Next native renderer pass should add only missing pieces:

1. Sparse twinkle nodes around the outer ring.
2. Opposed cyan/magenta orbital arcs with different angular velocities.
3. A faint amber geometric core lattice behind the face/core.
4. ATTENTION-only micro-jitter instead of global UI shake.
5. Mode-dependent glow envelope that remains cheap enough for a custom Canvas View.

## Constraints

- Do not embed React or a WebView just to reproduce the look.
- Do not replace the verified X-88 state/runtime/security path.
- Keep animations deterministic and tied to X88AvatarView.Mode.
- Avoid allocations inside `onDraw` where practical.
- STOPPED must remain visually calm and clearly distinct.
- Accessibility and reduced-motion behavior should be added before making motion more aggressive.

## Status

This document captures the reusable design system from the compiled artifact so the visual layer can evolve without importing minified web-runtime code into the Android APK.
