import { config } from "../src/config.js";
import { publish } from "../src/relay.js";
import type { CompanionPayload } from "../src/types.js";

const payload: CompanionPayload = {
  v: 1,
  instance: {
    id: config.instanceId,
    label: config.label,
    color: config.color,
  },
  emotion: "happy",
  text: "smoke test from android-companion-mcp",
  sound: "soft",
  ttl_ms: 10000,
  ts: Date.now(),
};

console.error(
  `[smoke] POST ${config.ntfyUrl}/${config.ntfyTopic || "<unset-topic>"}`,
);
console.error(`[smoke] payload ${JSON.stringify(payload)}`);

const result = await publish(payload);

if (result.ok) {
  console.error(`[smoke] OK status=${result.status}`);
  console.log(`accepted status=${result.status}`);
} else {
  console.error(`[smoke] FAILED ${result.error ?? "unknown error"}`);
  console.log(`rejected error=${result.error ?? "unknown error"}`);
}

process.exit(result.ok ? 0 : 1);
