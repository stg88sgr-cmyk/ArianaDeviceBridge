import readline from "node:readline";
import { pathToFileURL } from "node:url";
import path from "node:path";

const moduleSpec = process.env.XSPACE_AGENT_MODULE?.trim() || "xspace-agent";
const authToken = process.env.X_AUTH_TOKEN?.trim();
const ct0 = process.env.X_CT0?.trim();

if (!authToken || !ct0) {
  console.error("X_AUTH_TOKEN and X_CT0 are required");
  process.exit(2);
}

async function loadModule(spec) {
  if (spec.startsWith(".") || spec.startsWith("/")) {
    return import(pathToFileURL(path.resolve(spec)).href);
  }
  return import(spec);
}

const { XSpaceAgent } = await loadModule(moduleSpec);

const passiveProvider = {
  type: "socket",
  async generateResponse() {
    // Ariana Core owns reasoning. autoRespond=false keeps xspace-agent as media transport.
    return "";
  }
};

const agent = new XSpaceAgent({
  auth: { token: authToken, ct0 },
  ai: {
    provider: "custom",
    systemPrompt: "Ariana X-88 transport adapter. Reasoning is handled externally.",
    custom: passiveProvider
  },
  voice: {
    provider: process.env.XSPACE_TTS_PROVIDER || "browser",
    apiKey: process.env.XSPACE_TTS_API_KEY || undefined,
    voiceId: process.env.XSPACE_VOICE_ID || undefined
  },
  browser: {
    headless: process.env.XSPACE_HEADLESS !== "false",
    executablePath: process.env.XSPACE_CHROME_PATH || undefined
  },
  behavior: {
    autoRespond: false,
    respondToSelf: false
  }
});

function emit(event) {
  process.stdout.write(JSON.stringify(event) + "\n");
}

agent.on("transcription", ({ speaker, text }) => {
  emit({ type: "transcript", speaker: speaker ?? null, text, isFinal: true });
});
agent.on("status", status => emit({ type: "agent_status", status }));
agent.on("speaker-joined", data => emit({ type: "participant_joined", displayName: data.username }));
agent.on("speaker-left", data => emit({ type: "participant_left", displayName: data.username }));
agent.on("space-ended", () => emit({ type: "disconnected" }));
agent.on("error", error => emit({ type: "error", message: error?.message || String(error) }));

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });

rl.on("line", async line => {
  let command;
  try { command = JSON.parse(line); }
  catch { emit({ type: "error", message: "adapter received invalid JSON" }); return; }

  try {
    switch (command.type) {
      case "join":
        await agent.join(command.url);
        emit({ type: "connected" });
        break;
      case "leave":
        await agent.leave();
        emit({ type: "disconnected" });
        break;
      case "speak":
        await agent.say(String(command.text ?? "").slice(0, 2000));
        emit({ type: "spoken", text: String(command.text ?? "").slice(0, 2000) });
        break;
      case "mute":
        await agent.mute();
        emit({ type: "audio_state", muted: true });
        break;
      case "unmute":
        await agent.unmute();
        emit({ type: "audio_state", muted: false });
        break;
      default:
        emit({ type: "error", message: "unsupported adapter command" });
    }
  } catch (error) {
    emit({ type: "error", message: error instanceof Error ? error.message : String(error) });
  }
});

async function shutdown() {
  try { await agent.destroy(); } catch {}
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
