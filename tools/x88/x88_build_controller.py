from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Iterable

RUNES = {
    "origin": "ᚨ",
    "connect": "ᚷ",
    "transmute": "ᛏ",
    "create": "ᚲ",
    "protect": "ᛉ",
    "execute": "ᚱ",
    "evolve": "ᛞ",
    "core": "ᛟ",
}

IGNORED_PARTS = {".git", ".gradle", ".idea", ".x88", "build", "__pycache__"}


@dataclass(slots=True)
class X88Config:
    repo: Path
    android_dir: str = "android"
    gradle_task: str = ":app:assembleDebug"
    test_task: str = ":app:testDebugUnitTest"
    cache_file: str = ".x88/cache.json"
    state_file: str = ".x88/state.json"
    bridge_url: str = "http://127.0.0.1:8765"
    repair_url: str = "http://127.0.0.1:8877/repair"
    max_repairs: int = 5
    timeout_seconds: int = 900

    @property
    def android_root(self) -> Path:
        return self.repo / self.android_dir


@dataclass(slots=True)
class X88State:
    generation: int = 0
    phase: str = "IDLE"
    changed_files: list[str] = field(default_factory=list)
    last_build_ok: bool = False
    last_test_ok: bool = False
    build_ms: int = 0
    test_ms: int = 0
    last_error: str = ""


@dataclass(slots=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


class Shell:
    def __init__(self, runner: Callable[..., subprocess.CompletedProcess[str]] | None = None):
        self._runner = runner or subprocess.run

    def run(self, args: list[str], cwd: Path, timeout: int) -> CommandResult:
        print(f"{RUNES['execute']} {' '.join(args)}")
        completed = self._runner(
            args,
            cwd=cwd,
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
        )
        if completed.stdout:
            print(completed.stdout)
        if completed.stderr:
            print(completed.stderr)
        return CommandResult(completed.returncode, completed.stdout or "", completed.stderr or "")


class HashCache:
    def __init__(self, path: Path):
        self.path = path
        self.data = self._load()

    def _load(self) -> dict[str, str]:
        if not self.path.exists():
            return {}
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            return raw if isinstance(raw, dict) else {}
        except (OSError, json.JSONDecodeError):
            return {}

    @staticmethod
    def sha256(path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()

    @staticmethod
    def _ignored(path: Path, root: Path) -> bool:
        try:
            parts = path.relative_to(root).parts
        except ValueError:
            return True
        return any(part in IGNORED_PARTS for part in parts)

    def scan(self, root: Path) -> list[str]:
        current: dict[str, str] = {}
        changed: list[str] = []

        for path in root.rglob("*"):
            if not path.is_file() or self._ignored(path, root):
                continue
            rel = path.relative_to(root).as_posix()
            digest = self.sha256(path)
            current[rel] = digest
            if self.data.get(rel) != digest:
                changed.append(rel)

        changed.extend(rel for rel in self.data if rel not in current)
        self.data = current
        return sorted(set(changed))

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, indent=2, sort_keys=True), encoding="utf-8")


class X88BuildController:
    def __init__(self, config: X88Config, shell: Shell | None = None):
        self.cfg = config
        self.shell = shell or Shell()
        self.state_path = config.repo / config.state_file
        self.cache = HashCache(config.repo / config.cache_file)
        self.state = self._load_state()

    def _load_state(self) -> X88State:
        if not self.state_path.exists():
            return X88State()
        try:
            raw = json.loads(self.state_path.read_text(encoding="utf-8"))
            return X88State(**{k: raw[k] for k in X88State.__dataclass_fields__ if k in raw})
        except (OSError, json.JSONDecodeError, TypeError):
            return X88State()

    def checkpoint(self) -> None:
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.state_path.write_text(json.dumps(asdict(self.state), indent=2, sort_keys=True), encoding="utf-8")

    def observe(self) -> list[str]:
        self.state.phase = "OBSERVE"
        self.state.changed_files = self.cache.scan(self.cfg.repo)
        print(f"{RUNES['origin']} changed={len(self.state.changed_files)}")
        for item in self.state.changed_files:
            print(f"  Δ {item}")
        self.checkpoint()
        return self.state.changed_files

    def prepare_git(self) -> str:
        self.state.phase = "GIT"
        self.shell.run(["git", "status", "--short"], self.cfg.repo, self.cfg.timeout_seconds)
        branch = self.shell.run(
            ["git", "branch", "--show-current"], self.cfg.repo, self.cfg.timeout_seconds
        ).stdout.strip()
        print(f"{RUNES['connect']} branch={branch or 'DETACHED'}")
        self.checkpoint()
        return branch

    def _run_gradle(self, task: str) -> CommandResult:
        args = [
            "gradle",
            task,
            "--parallel",
            "--build-cache",
            "--configuration-cache",
            "--stacktrace",
        ]
        return self.shell.run(args, self.cfg.android_root, self.cfg.timeout_seconds)

    @staticmethod
    def _failure_text(result: CommandResult, limit: int = 16000) -> str:
        text = (result.stderr + "\n" + result.stdout).strip()
        return text[-limit:]

    def build(self) -> bool:
        self.state.phase = "BUILD"
        started = time.perf_counter()
        result = self._run_gradle(self.cfg.gradle_task)
        self.state.build_ms = int((time.perf_counter() - started) * 1000)
        self.state.last_build_ok = result.ok
        self.state.last_error = "" if result.ok else self._failure_text(result)
        self.checkpoint()
        return result.ok

    def test(self) -> bool:
        self.state.phase = "TEST"
        started = time.perf_counter()
        result = self._run_gradle(self.cfg.test_task)
        self.state.test_ms = int((time.perf_counter() - started) * 1000)
        self.state.last_test_ok = result.ok
        self.state.last_error = "" if result.ok else self._failure_text(result)
        self.checkpoint()
        return result.ok

    def sync_android_state(self) -> dict | None:
        try:
            with urllib.request.urlopen(f"{self.cfg.bridge_url}/state", timeout=3) as response:
                payload = json.loads(response.read().decode("utf-8"))
            print(f"{RUNES['connect']} Android bridge connected")
            return payload if isinstance(payload, dict) else None
        except (OSError, urllib.error.URLError, json.JSONDecodeError):
            print(f"{RUNES['connect']} Android bridge offline")
            return None

    def request_repair(self) -> bool:
        self.state.phase = "REPAIR"
        payload = {
            "generation": self.state.generation,
            "changedFiles": self.state.changed_files,
            "error": self.state.last_error,
            "rule": "Patch only the failing surface. Do not rewrite unaffected files.",
        }
        request = urllib.request.Request(
            self.cfg.repair_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                result = json.loads(response.read().decode("utf-8"))
            return bool(isinstance(result, dict) and result.get("patched"))
        except (OSError, urllib.error.URLError, json.JSONDecodeError):
            print(f"{RUNES['protect']} repair provider unavailable")
            return False

    def finalize_success(self) -> None:
        self.state.phase = "COMPLETE"
        self.state.generation += 1
        self.state.last_error = ""
        self.cache.scan(self.cfg.repo)
        self.cache.save()
        self.checkpoint()

    def run(self, force: bool = False, repair: bool = True) -> int:
        print("ᚨ X88::ABSOLUTE_AUTONOMY::CATALYST")
        if not self.cfg.android_root.is_dir():
            raise FileNotFoundError(f"Android root not found: {self.cfg.android_root}")

        self.prepare_git()
        changed = self.observe()
        self.sync_android_state()

        if not changed and not force:
            print(f"{RUNES['core']} NO SOURCE CHANGES")
            return 0

        for attempt in range(1, self.cfg.max_repairs + 1):
            print(f"{RUNES['transmute']} CYCLE {attempt}/{self.cfg.max_repairs}")

            if not self.build():
                if not repair or not self.request_repair():
                    self.state.phase = "FAILED"
                    self.checkpoint()
                    return 2
                continue

            if not self.test():
                if not repair or not self.request_repair():
                    self.state.phase = "FAILED"
                    self.checkpoint()
                    return 3
                continue

            self.finalize_success()
            print(
                f"{RUNES['core']} BUILD=PASS TEST=PASS generation={self.state.generation} "
                f"build_ms={self.state.build_ms} test_ms={self.state.test_ms}"
            )
            return 0

        self.state.phase = "FAILED"
        self.state.last_error = "MAX_REPAIR_ATTEMPTS_REACHED"
        self.checkpoint()
        return 4


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="X88 autonomous Android build controller")
    parser.add_argument("--repo", default=os.environ.get("X88_REPO", "."))
    parser.add_argument("--force", action="store_true", help="build even when hashes are unchanged")
    parser.add_argument("--no-repair", action="store_true", help="do not call the repair endpoint")
    parser.add_argument("--max-repairs", type=int, default=5)
    parser.add_argument("--bridge-url", default=os.environ.get("X88_BRIDGE_URL", "http://127.0.0.1:8765"))
    parser.add_argument("--repair-url", default=os.environ.get("X88_REPAIR_URL", "http://127.0.0.1:8877/repair"))
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(list(argv) if argv is not None else None)
    config = X88Config(
        repo=Path(args.repo).resolve(),
        bridge_url=args.bridge_url,
        repair_url=args.repair_url,
        max_repairs=max(1, args.max_repairs),
    )
    return X88BuildController(config).run(force=args.force, repair=not args.no_repair)


if __name__ == "__main__":
    raise SystemExit(main())
