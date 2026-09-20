import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname } from "node:path";

export async function loadMemoryFile(filePath) {
  const raw = await readFile(filePath, "utf8");
  const parsed = JSON.parse(raw);

  if (!Array.isArray(parsed)) {
    throw new TypeError("Memory file must contain an array");
  }

  return parsed;
}

export async function saveMemoryFile(filePath, memories) {
  if (!Array.isArray(memories)) {
    throw new TypeError("memories must be an array");
  }

  await mkdir(dirname(filePath), { recursive: true });
  await writeFile(filePath, JSON.stringify(memories, null, 2) + "\n", "utf8");
}
