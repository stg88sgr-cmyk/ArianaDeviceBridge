import test from "node:test";
import assert from "node:assert/strict";
import { arianaCore, askAriana } from "../src/core.js";

test("core exposes the same answer function", () => {
  assert.equal(arianaCore.ask, askAriana);
});

test("core exposes memory operations", () => {
  assert.equal(typeof arianaCore.memory.add, "function");
  assert.equal(typeof arianaCore.memory.getAll, "function");
  assert.equal(typeof arianaCore.memory.update, "function");
  assert.equal(typeof arianaCore.memory.load, "function");
  assert.equal(typeof arianaCore.memory.save, "function");
});
