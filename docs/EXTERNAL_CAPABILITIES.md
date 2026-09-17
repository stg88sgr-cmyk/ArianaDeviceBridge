# X-88 External Capability Providers

Status: canonical registry for optional external capabilities.

External providers extend X-88 but do not become part of the trusted core. They cannot bypass Android permissions, X-88 execution gates, review gates, or user-controlled consent.

## Provider: Higgsfield

Role: `CREATIVE_IMAGE_VIDEO_PROVIDER`

Purpose:
- image generation;
- video generation;
- marketing/viral preset workflows;
- motion and product-shot generation.

Boundary:
- invoked only for explicit creative-generation jobs;
- generated media returns as an artifact/result;
- no direct authority over device control, repository merges or Android permissions.

## Provider: HyperFrames

Role: `CODE_DRIVEN_VIDEO_RENDERER`

Purpose:
- HTML/CSS/JS video compositions;
- GSAP animation;
- captions, overlays, transitions and voiceover-oriented compositions;
- website-to-video and structured motion output.

Boundary:
- composition source and render jobs stay outside the Android control core;
- X-88 may store references/results but does not grant HyperFrames device-control authority.

## Provider: Life Sciences NGS Analysis

Role: `ISOLATED_SCIENTIFIC_ANALYSIS_PROVIDER`

Purpose:
- sequencing workflow routing and analysis for BCL, FASTQ, BAM/CRAM, VCF and count matrices;
- RNA-seq, DNA variant, single-cell, epigenomics, metagenomics and related public analysis pipelines.

Boundary:
- only invoked for explicit sequencing/scientific workloads;
- no automatic upload of human data to cloud services;
- local/HPC/cloud execution policy must be resolved before running pipelines;
- NGS outputs cannot directly mutate X-88 production code.

## Provider: OpenAI Ads Conversions

Role: `MEASUREMENT_PROVIDER`

Purpose:
- OpenAI Ads Measurement Pixel for supported browser surfaces;
- optional server-side Conversions API (CAPI);
- conversion-event instrumentation and deduplication.

Boundary:
- native Android does not receive a browser Pixel by default;
- for native/mobile surfaces, prefer server-side CAPI only when a real conversion backend exists;
- never place CAPI secrets in Android source, client bundles, logs, docs or generated reports;
- consent/privacy requirements remain authoritative;
- conversion reporting must never block core application flows.

## X-88 routing rule

```text
ARIANA / X88 Router
    |
    +-- creative.image/video -> Higgsfield
    +-- creative.html-video  -> HyperFrames
    +-- science.ngs          -> NGS Analysis
    +-- measurement.ads      -> OpenAI Ads Conversions
```

These providers are optional adapters. Failure or absence of one provider must not prevent X-88 core startup.

## Security law

1. Provider credentials are never hard-coded.
2. Provider output is untrusted input until validated for the consuming subsystem.
3. Providers receive only the minimum data needed for the explicit task.
4. External provider calls cannot bypass Master Enable, lock levels, confirmation gates or Android permission surfaces.
5. Provider availability is reported independently from core health.
