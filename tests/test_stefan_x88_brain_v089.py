import json
import tempfile
import unittest
from pathlib import Path

from stefan_x88_brain_v089 import StefanX88


class TestStefanX88(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.memory = str(Path(self.tmp.name) / "memory.json")
        self.brain = StefanX88(memory_file=self.memory, seed=88)

    def tearDown(self):
        self.tmp.cleanup()

    def test_code_input_routes_code(self):
        _, emotion, thinking, _ = self.brain.process("Baue eine Python API mit function und bug fix")
        self.assertEqual(emotion["label"], "FOKUSSIERT_ANALYTISCH")
        self.assertIn("Code", [d for d, _ in thinking["active"]])

    def test_experience_persists_and_is_recalled(self):
        _, emotion, thinking, _ = self.brain.process("Ariana X88 Speicher Architektur")
        self.brain.save_experience("Ariana X88 Speicher Architektur", "Antwort", emotion, thinking)
        reloaded = StefanX88(memory_file=self.memory, seed=88)
        memories = reloaded.hippocampus.erinnern("Ariana X88 Speicher Architektur")
        self.assertEqual(len(memories), 1)

    def test_sleep_consolidates_rewarded_experience(self):
        _, emotion, thinking, _ = self.brain.process("Danke, das ist genial")
        exp = self.brain.save_experience("Danke, das ist genial", "Antwort", emotion, thinking)
        self.assertFalse(exp.consolidated)
        self.assertEqual(self.brain.hippocampus.schlaf_konsolidierung(), 1)
        self.assertTrue(self.brain.hippocampus.erfahrungen[-1].consolidated)

    def test_snapshot_reports_state(self):
        snap = self.brain.snapshot()
        self.assertEqual(snap["version"], "0.89")
        self.assertEqual(snap["memory_events"], 0)


if __name__ == "__main__":
    unittest.main()
