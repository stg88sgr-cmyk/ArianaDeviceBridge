const memories = [
  {
    content: "Name: Stefan oder Snow je nach Benehmen...",
    confidence: 0.92,
    source: "user_context",
    type: "identity_preference",
    tags: ["stefan", "snow", "name", "identity"]
  }
];

function normalize(value) {
  return typeof value === "string"
    ? value.toLocaleLowerCase("de-DE").trim()
    : "";
}

function tokenize(value) {
  return normalize(value)
    .replace(/[^\p{L}\p{N}_-]+/gu, " ")
    .split(/\s+/)
    .filter(Boolean);
}

function scoreMemory(question, memory) {
  const questionTokens = new Set(tokenize(question));
  const searchable = [
    memory.content,
    memory.type,
    ...(memory.tags ?? [])
  ];
  const memoryTokens = new Set(tokenize(searchable.join(" ")));

  let matches = 0;
  for (const token of questionTokens) {
    if (memoryTokens.has(token)) matches += 1;
  }

  return matches;
}

export function findRelevantMemories(question, limit = 5) {
  if (!normalize(question) || !Number.isInteger(limit) || limit <= 0) {
    return [];
  }

  return memories
    .map(memory => ({
      memory: { ...memory, tags: [...(memory.tags ?? [])] },
      relevance: scoreMemory(question, memory)
    }))
    .filter(item => item.relevance > 0)
    .sort(
      (a, b) =>
        b.relevance - a.relevance ||
        b.memory.confidence - a.memory.confidence
    )
    .slice(0, limit)
    .map(item => item.memory);
}

export function getMemories() {
  return memories.map(memory => ({
    ...memory,
    tags: [...(memory.tags ?? [])]
  }));
}
