export interface ArianaCore {
  ask(question: string): Promise<string>;
}

let corePromise: Promise<ArianaCore> | null = null;

function loadCore(): Promise<ArianaCore> {
  if (!corePromise) {
    const coreUrl = new URL("../../../ariana-core/src/core.js", import.meta.url);
    corePromise = import(coreUrl.href).then(module => {
      if (!module || typeof module.askAriana !== "function") {
        throw new Error("ariana-core does not export askAriana");
      }
      return { ask: module.askAriana };
    });
  }
  return corePromise;
}

export async function askArianaFromGateway(text: string): Promise<string> {
  const question = text.trim();
  if (!question) throw new Error("empty Ariana question");

  const core = await loadCore();
  return core.ask(question);
}
