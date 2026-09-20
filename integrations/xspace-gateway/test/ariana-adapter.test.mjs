import test from "node:test";
import assert from "node:assert/strict";
import { askArianaFromGateway } from "../src/ariana-adapter.js";

test("gateway adapter reaches the Ariana core", async () => {
  const answer = await askArianaFromGateway("Was weißt du über Stefan?");
  assert.match(answer, /Stefan oder Snow/);
});

test("gateway adapter rejects empty input", async () => {
  await assert.rejects(
    () => askArianaFromGateway("   "),
    /empty Ariana question/
  );
});
