#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { config, relayConfigured } from "./config.js";
import { publish } from "./relay.js";
import type { CompanionPayload, Emotion, Sound } from "./types.js";

const EMOTIONS = [
  "neutral",
  "happy",
  "thinking",
  "error",
  "sleepy",
  "listening",
] as const satisfies readonly Emotion[];

const SOUNDS = ["none", "soft", "alert"] as const satisfies readonly Sound[];

const RING_SIZE = 20;
const recent: CompanionPayload[] = [];
let publishedCount = 0;

function remember(payload: CompanionPayload): void {
  recent.push(payload);
  if (recent.length > RING_SIZE) {
    recent.splice(0, recent.length - RING_SIZE);
  }
  publishedCount += 1;
}

function textResult(value: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value) }],
  };
}

function buildPayload(args: {
  emotion: Emotion;
  text?: string;
  sound?: Sound;
  ttl_ms?: number;
}): CompanionPayload {
  const payload: CompanionPayload = {
    v: 1,
    instance: {
      id: config.instanceId,
      label: config.label,
      color: config.color,
    },
    emotion: args.emotion,
    sound: args.sound ?? "none",
    ts: Date.now(),
  };
  if (args.text !== undefined) payload.text = args.text;
  if (args.ttl_ms !== undefined) payload.ttl_ms = args.ttl_ms;
  return payload;
}

const server = new McpServer({
  name: "android_companion",
  version: "0.1.0",
});

server.registerTool(
  "companion_set",
  {
    title: "Set companion state",
    description:
      "Publish a companion emotion/text/sound event to the ntfy relay.",
    inputSchema: {
      emotion: z.enum(EMOTIONS),
      text: z.string().optional(),
      sound: z.enum(SOUNDS).optional(),
      ttl_ms: z.number().int().positive().optional(),
    },
  },
  async (args) => {
    const payload = buildPayload(args);
    remember(payload);

    if (!relayConfigured()) {
      return textResult({
        accepted: false,
        instance: config.instanceId,
        error: "NTFY_TOPIC is not configured",
      });
    }

    const result = await publish(payload);
    if (!result.ok) {
      return textResult({
        accepted: false,
        instance: config.instanceId,
        error: result.error ?? "publish failed",
      });
    }
    return textResult({ accepted: true, instance: config.instanceId });
  },
);

server.registerTool(
  "companion_status",
  {
    title: "Companion status",
    description:
      "Report the in-memory publish history and relay configuration.",
  },
  async () =>
    textResult({
      device_hint: "unknown",
      published_count: publishedCount,
      last: recent.length > 0 ? recent[recent.length - 1] : null,
      relay: {
        url: config.ntfyUrl,
        topic: config.ntfyTopic,
        configured: relayConfigured(),
      },
    }),
);

async function start(): Promise<void> {
  console.error(
    `[android_companion] starting (instance="${config.instanceId}", relay=${config.ntfyUrl}, topic=${
      relayConfigured() ? "configured" : "MISSING"
    })`,
  );
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("[android_companion] connected over stdio");
}

start().catch((error: unknown) => {
  console.error(
    "[android_companion] fatal:",
    error instanceof Error ? error.message : String(error),
  );
  process.exit(1);
});
