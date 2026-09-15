# ARIANA X-88 V11 avatar asset slot

Runtime asset name:

`ariana_x88.glb`

The source tree deliberately does **not** require a binary model. When the asset is
missing or invalid, `X88AvatarHostView` keeps the existing procedural V7 avatar
visible. This preserves ordinary source builds and the current safety/UI behavior.

The dedicated `android-avatar-v11-apk.yml` workflow downloads the current V2 GLB
into this slot before compiling the V11 test APK.

## Replacement contract

A later rigged model should keep the same filename. If animation clips are present,
V11 looks for these names (case-insensitive):

- `Idle_Breath` / `Idle`
- `Listening` / `Listen`
- `Thinking` / `Think`
- `Speaking` / `Talk` / `Talking`
- `Attention` / `Alert`

Missing clips are safe. `STOPPED` never auto-plays an animation.

Preferred final format: glTF 2.0 binary (`.glb`), PBR materials, humanoid skinning,
mobile-friendly texture sizes and a compact skeleton.
