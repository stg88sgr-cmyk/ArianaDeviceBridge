from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

ANDROID_NS = "http://schemas.android.com/apk/res/android"
A = f"{{{ANDROID_NS}}}"


@dataclass(slots=True)
class GateResult:
    name: str
    ok: bool
    detail: str


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _version_tuple(value: str) -> tuple[int, ...]:
    numbers = re.findall(r"\d+", value)
    return tuple(int(item) for item in numbers[:3])


def _pad(version: tuple[int, ...], width: int = 3) -> tuple[int, ...]:
    return version + (0,) * max(0, width - len(version))


def _check_build_config(repo: Path) -> list[GateResult]:
    root_gradle = _read(repo / "android/build.gradle.kts")
    app_gradle = _read(repo / "android/app/build.gradle.kts")

    agp_match = re.search(
        r'id\("com\.android\.application"\)\s+version\s+"([^"]+)"',
        root_gradle,
    )
    compile_match = re.search(r"\bcompileSdk\s*=\s*(\d+)", app_gradle)
    target_match = re.search(r"\btargetSdk\s*=\s*(\d+)", app_gradle)

    agp = agp_match.group(1) if agp_match else "missing"
    compile_sdk = int(compile_match.group(1)) if compile_match else -1
    target_sdk = int(target_match.group(1)) if target_match else -1

    return [
        GateResult(
            "AGP >= 8.9.1",
            agp_match is not None and _pad(_version_tuple(agp)) >= (8, 9, 1),
            f"AGP={agp}",
        ),
        GateResult("compileSdk >= 36", compile_sdk >= 36, f"compileSdk={compile_sdk}"),
        GateResult("targetSdk >= 36", target_sdk >= 36, f"targetSdk={target_sdk}"),
    ]


def _check_manifest(repo: Path) -> list[GateResult]:
    manifest_path = repo / "android/app/src/main/AndroidManifest.xml"
    root = ET.fromstring(_read(manifest_path))

    permissions = {
        node.attrib.get(f"{A}name", "")
        for node in root.findall("uses-permission")
    }
    required_permissions = {
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_CAMERA",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
    }
    missing = sorted(required_permissions - permissions)

    services = {
        node.attrib.get(f"{A}name", ""): node
        for node in root.findall("./application/service")
    }
    capture = services.get("de.snowworks.ariana.session.ArianaCaptureService")
    wakeword = services.get(".widget.ArianaWakewordService")

    capture_types = set(
        (capture.attrib.get(f"{A}foregroundServiceType", "") if capture is not None else "").split("|")
    )
    wakeword_types = set(
        (wakeword.attrib.get(f"{A}foregroundServiceType", "") if wakeword is not None else "").split("|")
    )

    return [
        GateResult(
            "foreground-service permissions",
            not missing,
            "all required permissions declared" if not missing else f"missing={','.join(missing)}",
        ),
        GateResult(
            "capture foregroundServiceType",
            {"camera", "microphone", "mediaProjection"}.issubset(capture_types),
            f"types={','.join(sorted(item for item in capture_types if item)) or 'missing'}",
        ),
        GateResult(
            "wakeword foregroundServiceType",
            "microphone" in wakeword_types,
            f"types={','.join(sorted(item for item in wakeword_types if item)) or 'missing'}",
        ),
    ]


def _check_boot_safety(repo: Path) -> GateResult:
    source = _read(repo / "android/app/src/main/java/de/snowworks/app/boot/X88BootReceiver.kt")
    banned = [
        "startForegroundService(",
        "startService(",
        "ArianaWakewordService.start(",
        "ArianaCaptureService",
    ]
    found = [token for token in banned if token in source]
    return GateResult(
        "BOOT_COMPLETED stays passive",
        not found,
        "no camera/mic/projection service start" if not found else f"forbidden={','.join(found)}",
    )


def _check_loopback(repo: Path) -> list[GateResult]:
    bridge = _read(repo / "android/app/src/main/java/de/snowworks/ariana/bridge/LocalBridgeServer.kt")
    network_path = repo / "android/app/src/main/res/xml/network_security_config.xml"
    network_root = ET.fromstring(_read(network_path))

    base = network_root.find("base-config")
    base_cleartext = base.attrib.get("cleartextTrafficPermitted", "") if base is not None else ""

    loopback_domains: set[str] = set()
    for config in network_root.findall("domain-config"):
        if config.attrib.get("cleartextTrafficPermitted") != "true":
            continue
        for domain in config.findall("domain"):
            if domain.text:
                loopback_domains.add(domain.text.strip())

    return [
        GateResult(
            "bridge binds loopback",
            'InetAddress.getByName("127.0.0.1")' in bridge,
            "127.0.0.1 binding present",
        ),
        GateResult(
            "cleartext denied by default",
            base_cleartext == "false",
            f"base.cleartextTrafficPermitted={base_cleartext or 'missing'}",
        ),
        GateResult(
            "cleartext limited to loopback",
            {"127.0.0.1", "localhost"}.issubset(loopback_domains)
            and loopback_domains.issubset({"127.0.0.1", "localhost"}),
            f"cleartext domains={','.join(sorted(loopback_domains)) or 'none'}",
        ),
    ]


def _check_android16_source(repo: Path) -> list[GateResult]:
    source_root = repo / "android/app/src/main/java"
    legacy_back_hits: list[str] = []
    edge_opt_out_hits: list[str] = []

    for path in source_root.rglob("*.kt"):
        text = _read(path)
        rel = path.relative_to(repo).as_posix()
        if "override fun onBackPressed" in text or "KeyEvent.KEYCODE_BACK" in text:
            legacy_back_hits.append(rel)
        if "windowOptOutEdgeToEdgeEnforcement" in text:
            edge_opt_out_hits.append(rel)

    return [
        GateResult(
            "no legacy back interception",
            not legacy_back_hits,
            "predictive-back compatible surface" if not legacy_back_hits else f"hits={','.join(legacy_back_hits)}",
        ),
        GateResult(
            "no edge-to-edge opt-out",
            not edge_opt_out_hits,
            "no Android 15 edge-to-edge escape hatch" if not edge_opt_out_hits else f"hits={','.join(edge_opt_out_hits)}",
        ),
    ]


def run_checks(repo: Path) -> list[GateResult]:
    repo = repo.resolve()
    return [
        *_check_build_config(repo),
        *_check_manifest(repo),
        _check_boot_safety(repo),
        *_check_loopback(repo),
        *_check_android16_source(repo),
    ]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="X88 Android 16/API 36 static release gate")
    parser.add_argument("--repo", default=".", help="repository root")
    parser.add_argument("--json", action="store_true", help="emit machine-readable output")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(list(argv) if argv is not None else None)
    results = run_checks(Path(args.repo))
    ok = all(item.ok for item in results)

    if args.json:
        print(json.dumps({"ok": ok, "checks": [asdict(item) for item in results]}, indent=2))
    else:
        for item in results:
            state = "PASS" if item.ok else "FAIL"
            print(f"[{state}] {item.name}: {item.detail}")
        print(f"X88_ANDROID16_GATE={'PASS' if ok else 'FAIL'}")

    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
