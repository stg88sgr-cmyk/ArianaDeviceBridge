import test from "node:test";
import assert from "node:assert/strict";
import { askAriana } from "../src/answer.js";
import { findRelevantMemories, getMemories } from "../src/memory.js";

test("answers Stefan question from stored memory", async () => {
  const answer = await askAriana("Was weißt du über Stefan?");
  assert.match(answer, /Stefan oder Snow/);
});

test("returns no-memory response for unrelated question", async () => {
  const answer = await askAriana("Was weißt du über Berlin?");
  assert.equal(answer, "Dazu habe ich aktuell keinen gespeicherten Kontext.");
});

test("ranks relevant memories by relevance before confidence", () => {
  const memories = findRelevantMemories("Stefan Snow Name");
  assert.equal(memories.length, 1);
  assert.equal(memories[0].confidence, 0.92);
});

test("returns defensive memory copies", () => {
  const memories = getMemories();
  memories[0].tags.push("changed");
  assert.deepEqual(getMemories()[0].tags, ["stefan", "snow", "name", "identity"]);
});

test("invalid limits return no results", () => {
  assert.deepEqual(findRelevantMemories("Stefan", 0), []);
  assert.deepEqual(findRelevantMemories("Stefan", -1), []);
});
