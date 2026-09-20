import test from "node:test";
import assert from "node:assert/strict";
import { askAriana } from "../src/answer.js";
import { findRelevantMemories } from "../src/memory.js";

test("answers Stefan question from stored memory", async () => {
  const answer = await askAriana("Was weißt du über Stefan?");
  assert.match(answer, /Stefan oder Snow/);
});

test("returns no-memory response for unrelated question", async () => {
  const answer = await askAriana("Was weißt du über Berlin?");
  assert.equal(answer, "Dazu habe ich aktuell keinen gespeicherten Kontext.");
});

test("memory keeps confidence metadata", () => {
  const memories = findRelevantMemories("Erzähl mir etwas über Stefan");
  assert.equal(memories.length, 1);
  assert.equal(memories[0].confidence, 0.92);
  assert.equal(memories[0].type, "identity_preference");
});
