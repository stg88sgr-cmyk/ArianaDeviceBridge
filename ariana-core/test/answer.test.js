import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { askAriana } from "../src/answer.js";
import {
  addMemory,
  findRelevantMemories,
  getMemories,
  loadMemories,
  saveMemories,
  updateMemory
} from "../src/memory.js";

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

test("adds a memory and makes it retrievable", () => {
  const memory = addMemory({
    id: "project-x88",
    content: "Ariana gehört zum X88-Projekt.",
    confidence: 0.95,
    source: "user",
    type: "project",
    tags: ["ariana", "x88"]
  });

  assert.equal(memory.id, "project-x88");
  assert.equal(findRelevantMemories("Was weißt du über X88?")[0].id, "project-x88");
});

test("updates an existing memory without changing its id", () => {
  const updated = updateMemory("project-x88", {
    content: "Ariana gehört zum X88-Projekt und soll lokal weiterentwickelt werden.",
    tags: ["ariana", "x88", "lokal"]
  });

  assert.equal(updated.id, "project-x88");
  assert.match(updated.content, /lokal/);
  assert.deepEqual(updated.tags, ["ariana", "x88", "lokal"]);
});

test("persists and reloads memories from JSON", async () => {
  const directory = await mkdtemp(join(tmpdir(), "ariana-memory-"));
  const filePath = join(directory, "memories.json");

  try {
    await saveMemories(filePath);
    const saved = JSON.parse(await readFile(filePath, "utf8"));
    assert.ok(Array.isArray(saved));
    assert.ok(saved.some(memory => memory.id === "project-x88"));

    await loadMemories(filePath);
    assert.equal(findRelevantMemories("X88")[0].id, "project-x88");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
