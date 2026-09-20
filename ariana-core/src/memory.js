let memories = [
  {
    id: "identity-stefan-snow",
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
    .replace(/[^\\p{L}\\p{N}_-]+/gu, " ")
    .split(/\\s+/)
    .filter(Boolean);
}

function scoreMemory(question, memory) {
  const questionTokens = new Set(tokenize(question));
  const searchable = [memory.content, memory.type, ...(memory.tags ?? [])];
  const memoryTokens = new Set(tokenize(searchable.join(" ")));

  let matches = 0;
  for (const token of questionTokens) {
    if (memoryTokens.has(token)) matches += 1;
  }
  return matches;
}

function cloneMemory(memory) {
  return { ...memory, tags: [...(memory.tags ?? [])] };
}

export function findRelevantMemories(question, limit = 5) {
  if (!normalize(question) || !Number.isInteger(limit) || limit <= 0) return [];

  return memories
    .map(memory => ({ memory: cloneMemory(memory), relevance: scoreMemory(question, memory) }))
    .filter(item => item.relevance > 0)
    .sort((a, b) => b.relevance - a.relevance || b.memory.confidence - a.memory.confidence)
    .slice(0, limit)
    .map(item => item.memory);
}

export function getMemories() {
  return memories.map(cloneMemory);
}

export function addMemory(memory) {
  if (!memory || typeof memory.content !== "string" || !memory.content.trim()) {
    throw new TypeError("memory.content must be a non-empty string");
  }

  const entry = {
    id: memory.id ?? `memory-${Date.now()}-${memories.length}`,
    content: memory.content.trim(),
    confidence: Number.isFinite(memory.confidence) ? memory.confidence : 0.5,
    source: memory.source ?? "user",
    type: memory.type ?? "general",
    tags: Array.isArray(memory.tags) ? [...new Set(memory.tags.map(String))] : []
  };

  memories = [...memories, entry];
  return cloneMemory(entry);
}

export function updateMemory(id, patch) {
  const index = memories.findIndex(memory => memory.id === id);
  if (index === -1) return null;

  const current = memories[index];
  const updated = {
    ...current,
    ...patch,
    id: current.id,
    tags: Array.isArray(patch?.tags)
      ? [...new Set(patch.tags.map(String))]
      : [...(current.tags ?? [])]
  };

  if (typeof updated.content !== "string" || !updated.content.trim()) {
    throw new TypeError("memory.content must be a non-empty string");
  }

  memories = memories.map((memory, i) => (i === index ? updated : memory));
  return cloneMemory(updated);
}
