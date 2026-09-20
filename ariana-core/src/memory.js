const memories = [
  {
    content: "Name: Stefan oder Snow je nach Benehmen...",
    confidence: 0.92,
    source: "user_context",
    type: "identity_preference"
  }
];

function normalize(value) {
  return typeof value === "string" ? value.toLocaleLowerCase("de-DE").trim() : "";
}

export function findRelevantMemories(question, limit = 5) {
  const normalizedQuestion = normalize(question);
  if (!normalizedQuestion) return [];

  const wantsStefan = /\bstefan\b|\bsnow\b/i.test(normalizedQuestion);
  if (!wantsStefan) return [];

  return memories
    .slice()
    .sort((a, b) => b.confidence - a.confidence)
    .slice(0, limit);
}

export function getMemories() {
  return memories.map(memory => ({ ...memory }));
}
