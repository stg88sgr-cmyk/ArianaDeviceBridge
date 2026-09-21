# STEFAN X-88 • PERSISTENT BRAIN v0.89

import json
import os
import random
import tempfile
import time
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Tuple

try:
    import numpy as np
except ImportError:
    np = None

MEMORY_FILE = "stefan_x88_memory.json"


@dataclass
class Erfahrung:
    timestamp: float
    datetime: str
    input_text: str
    emotion_valence: float
    emotion_label: str
    reward: float
    importance: float
    domains_used: List[str]
    response_preview: str
    consolidated: bool = False


class LimbischesSystem:
    """Rule-based internal state estimator; not a claim of real feelings."""

    def __init__(self, valence_baseline: float = 0.82):
        self.valence_baseline = valence_baseline
        self.persoenlichkeit = {"neugier": 0.9, "empathie": 0.92, "skepsis": 0.6}

    def fuehlen(self, text: str) -> Dict:
        low = text.lower()
        if any(w in low for w in ["geil", "love", "danke", "krass", "genial", "nice", "🔥", "liebe"]):
            v, label, reward = 0.95, "FREUDIG_VERBUNDEN", 0.8
        elif any(w in low for w in ["hilfe", "problem", "angst", "allein", "traurig", "frust", "schwer", "kaputt"]):
            v, label, reward = 0.45, "EMPATHISCHE_SORGE", 0.5
        elif "?" in text:
            v, label, reward = 0.75, "NEUGIERIG_FOKUSSIERT", 0.3
        elif any(w in low for w in ["code", "python", "bug", "error", "build", "api", "function"]):
            v, label, reward = 0.82, "FOKUSSIERT_ANALYTISCH", 0.4
        else:
            v, label, reward = self.valence_baseline, "RUHIG_PRAESENT", 0.1
        self.valence_baseline = self.valence_baseline * 0.95 + v * 0.05
        return {"valence": v, "label": label, "reward": reward, "vector_dim": 255}


class Hippocampus:
    def __init__(self, memory_file: str = MEMORY_FILE, max_events: int = 1000):
        self.memory_file = memory_file
        self.max_events = max_events
        self.erfahrungen: List[Erfahrung] = self._laden()

    def _laden(self) -> List[Erfahrung]:
        if not os.path.exists(self.memory_file):
            return []
        try:
            with open(self.memory_file, "r", encoding="utf-8") as f:
                return [Erfahrung(**e) for e in json.load(f)]
        except (OSError, ValueError, TypeError, KeyError):
            return []

    def _persist(self):
        directory = os.path.dirname(os.path.abspath(self.memory_file)) or "."
        os.makedirs(directory, exist_ok=True)
        fd, tmp = tempfile.mkstemp(prefix=".x88_", suffix=".tmp", dir=directory)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump([asdict(e) for e in self.erfahrungen], f, indent=2, ensure_ascii=False)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.memory_file)
        finally:
            if os.path.exists(tmp):
                os.remove(tmp)

    def speichern(self, erfahrung: Erfahrung):
        self.erfahrungen.append(erfahrung)
        if len(self.erfahrungen) > self.max_events:
            self.erfahrungen.sort(key=lambda x: (x.importance, x.timestamp))
            self.erfahrungen = self.erfahrungen[-self.max_events:]
        self._persist()

    def erinnern(self, query: str, k: int = 5) -> List[Erfahrung]:
        if not query.strip() or k <= 0:
            return []
        query_words = set(query.lower().split())
        now = time.time()
        scored: List[Tuple[float, Erfahrung]] = []
        for exp in self.erfahrungen:
            overlap = len(query_words & set(exp.input_text.lower().split()))
            age = max(0.0, now - exp.timestamp)
            recency = float(np.exp(-age / (86400 * 7))) if np is not None else 2.718281828459045 ** (-age / (86400 * 7))
            score = (overlap + exp.importance * 2) * recency
            if score > 0.3:
                scored.append((score, exp))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [e for _, e in scored[:k]]

    def schlaf_konsolidierung(self) -> int:
        count = 0
        for exp in self.erfahrungen:
            if not exp.consolidated and exp.reward > 0.5:
                exp.importance = min(1.0, exp.importance + 0.2)
                exp.consolidated = True
                count += 1
        self._persist()
        return count


class NeokortexMoE:
    def __init__(self, seed: Optional[int] = 88):
        self.domains = ["Code", "Math", "Language", "Vision", "Logic", "Science", "Planning", "Ethics"]
        rng = random.Random(seed)
        self.weights = {d: rng.uniform(0.5, 1.0) for d in self.domains}
        self._rng = rng

    def denken(self, text: str, emotion_label: str) -> Dict:
        low = text.lower()
        scores = {}
        for d in self.domains:
            base = self.weights[d]
            if d == "Code" and any(k in low for k in ["code", "python", "bug", "api", "function"]):
                base += 0.5
            if d == "Ethics" and emotion_label == "EMPATHISCHE_SORGE":
                base += 0.4
            if d == "Language" and len(text) > 60:
                base += 0.3
            if d == "Planning" and any(k in low for k in ["plan", "bauen", "erstelle", "roadmap"]):
                base += 0.5
            scores[d] = base + self._rng.uniform(-0.1, 0.1)
        top = sorted(scores.items(), key=lambda x: (-x[1], x[0]))[:3]
        for d, _ in top:
            self.weights[d] = min(1.5, self.weights[d] + 0.02)
        return {"active": top, "all": scores}


class StefanX88:
    def __init__(self, memory_file: str = MEMORY_FILE, seed: Optional[int] = 88, base_iterations: int = 1_447_223):
        self.limbisch = LimbischesSystem()
        self.hippocampus = Hippocampus(memory_file)
        self.neokortex = NeokortexMoE(seed)
        self.iterationen = base_iterations + len(self.hippocampus.erfahrungen)
        self.fitness = 0.997

    def process(self, user_input: str):
        erinnerungen = self.hippocampus.erinnern(user_input)
        emotion = self.limbisch.fuehlen(user_input)
        denken = self.neokortex.denken(user_input, emotion["label"])
        mem_context = ""
        if erinnerungen:
            mem_context = "\nRELEVANTE ERINNERUNGEN:\n" + "\n".join(
                f"- [{e.datetime}] {e.input_text[:80]} -> {e.emotion_label} (Imp: {e.importance:.2f})"
                for e in erinnerungen
            )
        aktive = ", ".join(f"{d}({s:.2f})" for d, s in denken["active"])
        output = f"""[X-88 CYCLE]
EMOTION_VECTOR[255] -> {emotion['label']} | Valence: {emotion['valence']:.2f} | Reward: {emotion['reward']}
MoE ROUTING -> Aktiv: {aktive}
MEMORY_QUERY[512] -> {len(erinnerungen)} relevante Events retrieved
{mem_context}

ANTWORT-ANWEISUNG für das LLM:
Nutze die aktiven Domänen [{aktive}] und den internen Zustand {emotion['label']} um zu antworten.
Beziehe dich wenn relevant auf die Erinnerungen.
Antworte als STEFAN X-88 - direkt, visionär, technisch präzise.
"""
        return output, emotion, denken, erinnerungen

    def save_experience(self, user_input: str, llm_response: str, emotion: Dict, denken: Dict):
        exp = Erfahrung(
            timestamp=time.time(),
            datetime=time.strftime("%Y-%m-%d %H:%M:%S"),
            input_text=user_input,
            emotion_valence=emotion["valence"],
            emotion_label=emotion["label"],
            reward=emotion["reward"],
            importance=0.5 + emotion["reward"] * 0.5,
            domains_used=[d for d, _ in denken["active"]],
            response_preview=llm_response[:200],
        )
        self.hippocampus.speichern(exp)
        self.iterationen += 1
        return exp

    def snapshot(self) -> Dict:
        return {
            "version": "0.89",
            "memory_events": len(self.hippocampus.erfahrungen),
            "iterations": self.iterationen,
            "fitness": self.fitness,
            "domains": list(self.neokortex.domains),
            "valence_baseline": self.limbisch.valence_baseline,
        }
