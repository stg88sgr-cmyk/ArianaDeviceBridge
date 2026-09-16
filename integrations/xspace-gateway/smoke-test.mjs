import { spawn } from "node:child_process";
import { WebSocket } from "ws";

const child = spawn("node", ["dist/server.js"], {
  cwd: new URL(".", import.meta.url),
  env: { ...process.env, XSPACE_AGENT_CMD: "node mock-agent.mjs", X88_XSPACE_PORT: "8877" },
  stdio: ["ignore", "pipe", "pipe"]
});

const timeout = setTimeout(() => finish(new Error("smoke test timeout")), 10000);
let ws;
let sawTranscript = false;
let sawSpoken = false;
let finished = false;

function finish(error) {
  if (finished) return;
  finished = true;
  clearTimeout(timeout);
  try { ws?.close(); } catch {}
  child.kill("SIGTERM");
  if (error) {
    console.error(error);
    process.exitCode = 1;
  } else {
    console.log("XSPACE_GATEWAY_SMOKE_PASS");
  }
}

child.stderr.on("data", chunk => process.stderr.write(chunk));
child.stdout.setEncoding("utf8");
child.stdout.on("data", chunk => {
  process.stdout.write(chunk);
  if (!chunk.includes("ARIANA_X88_XSPACE_GATEWAY")) return;

  ws = new WebSocket("ws://127.0.0.1:8877/xspace/ws");
  ws.on("open", () => ws.send(JSON.stringify({ type: "join", url: "https://x.com/i/spaces/1TESTSPACE" })));
  ws.on("message", raw => {
    const event = JSON.parse(raw.toString());
    if (event.type === "transcript" && event.text === "Gateway smoke transcript") {
      sawTranscript = true;
      ws.send(JSON.stringify({ type: "speak", text: "Ariana smoke reply" }));
    }
    if (event.type === "spoken" && event.text === "Ariana smoke reply") sawSpoken = true;
    if (sawTranscript && sawSpoken) finish();
  });
  ws.on("error", finish);
});

child.on("exit", code => {
  if (!finished && code !== 0) finish(new Error(`gateway exited with code ${code}`));
});
