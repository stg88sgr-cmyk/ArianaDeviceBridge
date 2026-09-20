import { askAriana } from "./answer.js";
import {
  addMemory,
  getMemories,
  loadMemories,
  saveMemories,
  updateMemory
} from "./memory.js";

export {
  askAriana,
  addMemory,
  getMemories,
  loadMemories,
  saveMemories,
  updateMemory
};

export const arianaCore = Object.freeze({
  ask: askAriana,
  memory: Object.freeze({
    add: addMemory,
    getAll: getMemories,
    update: updateMemory,
    load: loadMemories,
    save: saveMemories
  })
});
