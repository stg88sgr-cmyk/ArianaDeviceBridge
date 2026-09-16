import readline from "node:readline";

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
let joined = false;
let muted = true;

function emit(event) {
  process.stdout.write(JSON.stringify(event) + "\n");
}

rl.on("line", line => {
  let command;
  try { command = JSON.parse(line); }
  catch { emit({ type: "error", message: "mock received invalid JSON" }); return; }

  switch (command.type) {
    case "join":
      joined = true;
      emit({ type: "connected" });
      emit({ type: "transcript", speaker: "X88-SMOKE", text: "Gateway smoke transcript", isFinal: true });
      break;
    case "leave":
      joined = false;
      emit({ type: "disconnected" });
      break;
    case "speak":
      if (!joined) emit({ type: "error", message: "not joined" });
      else emit({ type: "spoken", text: String(command.text ?? "") });
      break;
    case "mute":
      muted = true;
      emit({ type: "audio_state", muted });
      break;
    case "unmute":
      muted = false;
      emit({ type: "audio_state", muted });
      break;
    default:
      emit({ type: "error", message: "unsupported mock command" });
  }
});
