import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from x88_build_controller import HashCache, Shell, X88BuildController, X88Config


class FakeRunner:
    def __init__(self, returncodes=None):
        self.returncodes = list(returncodes or [])
        self.calls = []

    def __call__(self, args, cwd, text, capture_output, timeout, check):
        self.calls.append((list(args), Path(cwd)))
        if args[:3] == ["git", "branch", "--show-current"]:
            return SimpleNamespace(returncode=0, stdout="test-branch\n", stderr="")
        if args[:2] == ["git", "status"]:
            return SimpleNamespace(returncode=0, stdout="", stderr="")
        code = self.returncodes.pop(0) if self.returncodes else 0
        return SimpleNamespace(
            returncode=code,
            stdout="BUILD SUCCESSFUL" if code == 0 else "",
            stderr="" if code == 0 else "synthetic failure",
        )


class HashCacheTest(unittest.TestCase):
    def test_detects_change_and_then_stabilizes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "android").mkdir()
            source = root / "android" / "A.kt"
            source.write_text("one", encoding="utf-8")
            cache = HashCache(root / ".x88" / "cache.json")
            self.assertEqual(cache.scan(root), ["android/A.kt"])
            cache.save()

            again = HashCache(root / ".x88" / "cache.json")
            self.assertEqual(again.scan(root), [])
            source.write_text("two", encoding="utf-8")
            self.assertEqual(again.scan(root), ["android/A.kt"])


class ControllerTest(unittest.TestCase):
    def make_repo(self):
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        (root / "android").mkdir()
        (root / "android" / "dummy.txt").write_text("x", encoding="utf-8")
        return temp, root

    def test_successful_forced_cycle(self):
        temp, root = self.make_repo()
        self.addCleanup(temp.cleanup)
        fake = FakeRunner([0, 0])
        controller = X88BuildController(X88Config(repo=root), shell=Shell(fake))
        controller.sync_android_state = lambda: None
        rc = controller.run(force=True, repair=False)
        self.assertEqual(rc, 0)
        self.assertTrue(controller.state.last_build_ok)
        self.assertTrue(controller.state.last_test_ok)
        self.assertEqual(controller.state.generation, 1)
        self.assertTrue((root / ".x88" / "cache.json").exists())

    def test_build_failure_without_repair_stops(self):
        temp, root = self.make_repo()
        self.addCleanup(temp.cleanup)
        fake = FakeRunner([1])
        controller = X88BuildController(X88Config(repo=root), shell=Shell(fake))
        controller.sync_android_state = lambda: None
        rc = controller.run(force=True, repair=False)
        self.assertEqual(rc, 2)
        self.assertEqual(controller.state.phase, "FAILED")
        self.assertIn("synthetic failure", controller.state.last_error)

    def test_state_is_persisted(self):
        temp, root = self.make_repo()
        self.addCleanup(temp.cleanup)
        fake = FakeRunner([0, 0])
        controller = X88BuildController(X88Config(repo=root), shell=Shell(fake))
        controller.sync_android_state = lambda: None
        controller.run(force=True, repair=False)
        state = json.loads((root / ".x88" / "state.json").read_text(encoding="utf-8"))
        self.assertEqual(state["phase"], "COMPLETE")
        self.assertEqual(state["generation"], 1)


if __name__ == "__main__":
    unittest.main()
