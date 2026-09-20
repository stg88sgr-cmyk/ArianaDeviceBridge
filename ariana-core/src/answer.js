import { findRelevantMemories } from "./memory.js";

export async function askAriana(question) {
  const memories = findRelevantMemories(question);

  if (memories.length === 0) {
    return "Dazu habe ich aktuell keinen gespeicherten Kontext.";
  }

  const context = memories.map(memory => memory.content).join(" ");
  return `Ich habe dazu folgenden gespeicherten Kontext: ${context}`;
}
