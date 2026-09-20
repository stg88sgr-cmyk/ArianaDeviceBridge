import { findRelevantMemories } from "./memory.js";

function uniqueById(memories) {
  const seen = new Set();
  return memories.filter(memory => {
    if (seen.has(memory.id)) return false;
    seen.add(memory.id);
    return true;
  });
}

export async function askAriana(question, options = {}) {
  const limit = options.limit ?? 5;
  const memories = uniqueById(findRelevantMemories(question, limit));

  if (memories.length === 0) {
    return "Dazu habe ich aktuell keinen gespeicherten Kontext.";
  }

  const context = memories
    .map(memory => memory.content.trim())
    .filter(Boolean)
    .join(" ");

  return `Ich habe dazu folgenden gespeicherten Kontext: ${context}`;
}
