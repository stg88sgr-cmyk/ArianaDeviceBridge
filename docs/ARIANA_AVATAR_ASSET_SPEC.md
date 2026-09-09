# ARIANA AVATAR ASSET SPEC v1

This document turns the Ariana Avatar Master into a production-ready Live2D asset contract. The goal is to let the art/rigging stage plug directly into the already implemented renderer-neutral Presence API.

## Visual lock

The current Ariana Avatar Master remains the visual source of truth:

- long dark hair with violet accents
- bright blue eyes
- elegant white / black / gold cyber armor
- violet luminous details
- forehead crystal / jewelry detail
- central violet Ariana core
- warm, confident facial expression

Do not redesign the character while cutting the Live2D source. Art changes after rigging begins should be treated as explicit revisions.

## Source canvas

Recommended master PSD:

- file: `Ariana_X88_Live2D_v1.psd`
- canvas: 4096 x 4096 minimum for half/full-body hybrid
- transparent background
- RGB / sRGB
- no destructive layer merges before Cubism import
- every moving part must contain enough hidden artwork to survive deformation

## Layer tree

```text
ARIANA_X88
  00_GUIDES_DO_NOT_EXPORT
    centerline
    eye_line
    mouth_line
    crop_safe

  10_HEAD
    hair_back
      hair_back_base
      hair_back_left
      hair_back_right
      hair_back_loose_01
      hair_back_loose_02
    neck_back
    ears
      ear_L
      ear_R
    face
      face_base
      face_shadow
      nose
      cheek_light
    eyes
      eye_L
        sclera_L
        iris_L
        pupil_L
        highlight_L_01
        highlight_L_02
        lid_upper_L
        lid_lower_L
        lashes_L
      eye_R
        sclera_R
        iris_R
        pupil_R
        highlight_R_01
        highlight_R_02
        lid_upper_R
        lid_lower_R
        lashes_R
    brows
      brow_L
      brow_R
    mouth
      mouth_outer_upper
      mouth_outer_lower
      mouth_inner
      teeth_upper
      teeth_lower
      tongue
      lip_highlight
    hair_front
      fringe_center
      fringe_L
      fringe_R
      side_lock_L_01
      side_lock_L_02
      side_lock_R_01
      side_lock_R_02
    head_accessories
      forehead_crystal
      forehead_crystal_glow
      temple_detail_L
      temple_detail_R

  20_BODY
    torso_base
    neck_front
    shoulder_L
    shoulder_R
    arm_L
    arm_R
    armor
      chest_black_base
      chest_white_L
      chest_white_R
      chest_gold_center
      collar_L
      collar_R
      shoulder_armor_L
      shoulder_armor_R
      arm_armor_L
      arm_armor_R
    ariana_core
      core_housing
      core_crystal
      core_inner_glow
      core_outer_glow
      core_bloom
    body_light_lines
      violet_line_L
      violet_line_R
      violet_line_center

  30_ACCESSORIES
    earring_L
    earring_R
    neck_jewel
    neck_jewel_glow

  90_REFERENCE
    color_palette
    master_thumbnail
```

## Cubism parameter contract

The rig must expose exactly these standard/custom parameter IDs because the Android Presence layer already maps to them:

| Parameter | Range | Purpose |
|---|---:|---|
| `ParamAngleX` | -30..30 | head yaw |
| `ParamAngleY` | -30..30 | head pitch |
| `ParamAngleZ` | -30..30 | head roll |
| `ParamEyeBallX` | -1..1 | gaze horizontal |
| `ParamEyeBallY` | -1..1 | gaze vertical |
| `ParamEyeLOpen` | 0..1 | left blink |
| `ParamEyeROpen` | 0..1 | right blink |
| `ParamMouthOpenY` | 0..1 | lip-sync opening |
| `ParamMouthForm` | -1..1 | rounded to wide mouth |
| `ParamBreath` | 0..1 | torso / breathing motion |
| `ParamArianaCoreGlow` | 0..1 | violet core intensity |

Do not rename these parameters inside the finished model.

## Expression IDs

The model must ship these expression files/IDs:

```text
neutral
soft_smile
happy
focused
stern
surprised
```

Expression files should alter brows, lids, mouth form and subtle facial shading only. Head pose and gaze remain controlled by runtime parameters.

## Deformer plan

Recommended hierarchy:

```text
ROOT
  BODY_XY
    TORSO_ROTATION
      CHEST_ARMOR
      ARIANA_CORE
      NECK
        HEAD_XY
          FACE_ROTATION
            EYES
            BROWS
            MOUTH
            HAIR_FRONT
            HEAD_ACCESSORIES
          HAIR_BACK
  ACCESSORY_PHYSICS
```

## Physics groups

### Hair

- front fringe: low amplitude, quick settle
- side locks: medium amplitude
- back hair: slower and heavier
- loose strands: slightly higher secondary motion

### Accessories

- earrings: short pendulum motion
- neck jewel: very subtle delay
- forehead crystal: fixed to head, glow only

### Core

The Ariana core is not physics-driven. Geometry stays stable and intensity is driven by `ParamArianaCoreGlow` so speaking and Presence state can pulse it deterministically.

## Mouth rig

`ParamMouthOpenY` and `ParamMouthForm` must combine cleanly across the full 2D grid:

- closed / rounded
- closed / neutral
- closed / wide
- half-open / rounded
- half-open / neutral
- half-open / wide
- open / rounded
- open / neutral
- open / wide

The current Android text-timing lip-sync already emits both values. The rig should therefore avoid expression-only mouth shapes that fight those parameters.

## Eye rig

Each eye must support independent `ParamEyeLOpen` / `ParamEyeROpen` while both share `ParamEyeBallX/Y` gaze. Hidden iris artwork should extend far enough to avoid clipping at the full gaze range.

## Breathing

`ParamBreath` should create subtle movement in:

- upper torso
- shoulders
- collar armor
- a tiny vertical neck/head compensation

It should not visibly scale the whole character.

## Core glow rig

`ParamArianaCoreGlow`:

- `0.0`: crystal remains visible but almost no bloom
- `0.65`: normal idle presence
- `0.8-0.95`: active speaking pulse
- `1.0`: maximum safe bloom without washing out armor detail

Drive opacity/emission layers rather than geometry wherever possible.

## Texture atlases

Preferred first pass:

- atlas 1: face, eyes, mouth, front hair, head accessories
- atlas 2: body, armor, back hair, core, accessories
- maximum 4096 x 4096 per atlas for the Android prototype

Keep the face and mouth at high texel density because they dominate perceived quality on a phone screen.

## Export bundle

```text
Ariana_X88_v1/
  Ariana_X88_v1.model3.json
  Ariana_X88_v1.moc3
  Ariana_X88_v1.physics3.json
  Ariana_X88_v1.cdi3.json
  textures/
    texture_00.png
    texture_01.png
  expressions/
    neutral.exp3.json
    soft_smile.exp3.json
    happy.exp3.json
    focused.exp3.json
    stern.exp3.json
    surprised.exp3.json
```

## Acceptance gate

The model is ready for the Android driver when all of the following are true:

1. Both eyes blink without mesh tearing.
2. Full X/Y gaze does not expose blank artwork.
3. Head X/Y/Z reaches the parameter limits without visible holes.
4. Hair physics settles naturally after head motion.
5. All nine mouth-grid combinations remain clean.
6. `ParamBreath` is visible but subtle.
7. `ParamArianaCoreGlow` is smooth from 0 to 1.
8. All six expression IDs load without changing runtime head/gaze parameters.
9. VTube Studio can import and animate the model with the same parameter IDs.
10. The bundle can later be consumed by a native Cubism Android driver without changing `ArianaPresenceController`.
