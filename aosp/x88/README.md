# X88 AOSP privileged backend scaffold

This directory is a build-time integration scaffold for a future AOSP/custom-ROM target. It is intentionally not wired into the current Samsung runtime and does not mutate a live device.

## Boundary

- Current app backend remains the safe default.
- A privileged backend is selected only when a trusted platform probe confirms it is present.
- No global SELinux permissive mode.
- No Verified Boot bypass as a design dependency.
- No blanket privileged permission grants.
- No automatic sensor activation at boot.
- Master authority and active sessions remain runtime state and are not restored after reboot.

## Intended build-time placement

A production AOSP integration should provide:

1. A privileged X88 package in a privileged system-image path.
2. Explicit privileged-permission allowlisting under the image's `etc/permissions` configuration.
3. A Binder service with a dedicated service label and narrowly scoped SELinux rules.
4. A signature-level X88 access permission or equivalent platform-owned caller check.
5. An adapter implementing the same `X88SystemGateway` semantics used by the app layer.
6. A fail-closed boot path. Failure of X88 must never prevent Android from booting.

## Integration fragments in this scaffold

- `permissions/privapp-permissions-de.snowworks.app.xml`
- `framework/de/snowworks/x88/IX88SystemService.aidl`
- `sepolicy/x88_system_service.te`
- `sepolicy/service_contexts.fragment`

These files are reference fragments, not a claim that an AOSP image has already been built or flashed.
