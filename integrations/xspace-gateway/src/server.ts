import { createServer, IncomingMessage } from "node:http";
import { spawn, ChildProcessWithoutNullStreams } from "node:child_process";
import { WebSocketServer, WebSocket } from "ws";

const HOST = "127.0.0.1";
const PORT = Number(process.env.X88_XSPACE_PORT ?? "8877");
const TOKEN = process.env.X88_GATEWAY_TOKEN?.trim() || null;
const AGENT_CMD = process.env.XSPACE_AGENT_CMD?.trim() || "node mock-agent.mjs";

let agent: ChildProcessWithoutNullStreams | null = null;
let agentBuffer = "";
let activeSpaceUrl: string | null = null;
let muted = true;

const clients = new Set<WebSocket>();

function sendToClients(message: unknown) {
  const payload = JSON.stringify(message);
  for (const client of clients) {
    if (client.readyState === WebSocket.OPEN) client.send(payload);
  }
}

function state() {
  return {
    connected: Boolean(agent && activeSpaceUrl),
    listening: Boolean(agent && activeSpaceUrl),
    speaking: false,
    muted,
    activeSpaceUrl,
  };
}

function authorized(req: IncomingMessage): boolean {
  if (!TOKEN) return true;
  const auth = req.headers.authorization;
  return auth === `Bearer ${TOKEN}`;
}

function validateSpaceUrl(value: unknown): string {
  if (typeof value !== "string") throw new Error("space url must be a string");
  const url = new URL(value);
  if (url.protocol !== "https:" || (url.hostname !== "x.com" && url.hostname !== "twitter.com")) {
    throw new Error("only https://x.com or https://twitter.com Space URLs are allowed");
  }
  if (!url.pathname.includes("/i/spaces/")) throw new Error("not an X Space URL");
  return url.toString();
}

function writeAgent(command: unknown) {
  if (!agent || agent.killed) throw new Error("xspace agent is not running");
  agent.stdin.write(JSON.stringify(command) + "\n");
}

function startAgent() {
  if (agent && !agent.killed) return;

  agent = spawn(AGENT_CMD, {
    cwd: process.cwd(),
    env: process.env,
    shell: true,
    stdio: ["pipe", "pipe", "pipe"],
  });

  agent.stdout.setEncoding("utf8");
  agent.stdout.on("data", (chunk: string) => {
    agentBuffer += chunk;
    while (true) {
      const newline = agentBuffer.indexOf("\n");
      if (newline < 0) break;
      const line = agentBuffer.slice(0, newline).trim();
      agentBuffer = agentBuffer.slice(newline + 1);
      if (!line) continue;
      try {
        const event = JSON.parse(line);
        sendToClients(event);
      } catch {
        sendToClients({ type: "error", message: "agent emitted invalid JSON" });
      }
    }
  });

  agent.stderr.setEncoding("utf8");
  agent.stderr.on("data", (chunk: string) => {
    const message = chunk.trim();
    if (message) sendToClients({ type: "error", message });
  });

  agent.on("exit", (code, signal) => {
    agent = null;
    activeSpaceUrl = null;
    muted = true;
    sendToClients({ type: "disconnected", code, signal });
  });
}

function stopAgent() {
  if (!agent) return;
  try { writeAgent({ type: "leave" }); } catch {}
  agent.kill("SIGTERM");
  agent = null;
  activeSpaceUrl = null;
  muted = true;
}

const http = createServer((req, res) => {
  if (req.socket.remoteAddress !== "127.0.0.1" && req.socket.remoteAddress !== "::ffff:127.0.0.1" && req.socket.remoteAddress !== "::1") {
    res.writeHead(403).end("loopback only");
    return;
  }
  if (!authorized(req)) {
    res.writeHead(401).end("unauthorized");
    return;
  }
  if (req.url === "/health") {
    res.setHeader("content-type", "application/json");
    res.end(JSON.stringify({ ok: true, host: HOST, port: PORT }));
    return;
  }
  if (req.url === "/state") {
    res.setHeader("content-type", "application/json");
    res.end(JSON.stringify(state()));
    return;
  }
  res.writeHead(404).end("not found");
});

const wss = new WebSocketServer({ noServer: true });

http.on("upgrade", (req, socket, head) => {
  const remote = req.socket.remoteAddress;
  const loopback = remote === "127.0.0.1" || remote === "::ffff:127.0.0.1" || remote === "::1";
  if (!loopback || !authorized(req) || req.url !== "/xspace/ws") {
    socket.write("HTTP/1.1 403 Forbidden\r\n\r\n");
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, req));
});

wss.on("connection", ws => {
  clients.add(ws);
  ws.send(JSON.stringify({ type: "state", ...state() }));

  ws.on("message", raw => {
    let command: any;
    try { command = JSON.parse(raw.toString()); }
    catch {
      ws.send(JSON.stringify({ type: "error", message: "invalid JSON" }));
      return;
    }

    try {
      switch (command.type) {
        case "join": {
          const url = validateSpaceUrl(command.url);
          startAgent();
          activeSpaceUrl = url;
          writeAgent({ type: "join", url });
          sendToClients({ type: "connected" });
          break;
        }
        case "leave":
          stopAgent();
          sendToClients({ type: "disconnected" });
          break;
        case "speak": {
          if (typeof command.text !== "string" || !command.text.trim()) throw new Error("empty speak text");
          writeAgent({ type: "speak", text: command.text.trim().slice(0, 2000) });
          break;
        }
        case "mute":
          muted = true;
          writeAgent({ type: "mute" });
          sendToClients({ type: "state", ...state() });
          break;
        case "unmute":
          muted = false;
          writeAgent({ type: "unmute" });
          sendToClients({ type: "state", ...state() });
          break;
        case "status":
          ws.send(JSON.stringify({ type: "state", ...state() }));
          break;
        default:
          throw new Error("unsupported command");
      }
    } catch (error) {
      ws.send(JSON.stringify({ type: "error", message: error instanceof Error ? error.message : String(error) }));
    }
  });

  ws.on("close", () => clients.delete(ws));
});

process.on("SIGINT", () => { stopAgent(); http.close(() => process.exit(0)); });
process.on("SIGTERM", () => { stopAgent(); http.close(() => process.exit(0)); });

http.listen(PORT, HOST, () => {
  console.log(`ARIANA_X88_XSPACE_GATEWAY http://${HOST}:${PORT}`);
});
