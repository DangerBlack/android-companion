import { config, relayConfigured } from "./config.js";
import type { CompanionPayload } from "./types.js";

export interface PublishResult {
  ok: boolean;
  status?: number;
  error?: string;
}

const TIMEOUT_MS = 3000;

function priorityFor(payload: CompanionPayload): string {
  return payload.emotion === "error" || payload.sound === "alert"
    ? "high"
    : "default";
}

export async function publish(payload: CompanionPayload): Promise<PublishResult> {
  if (!relayConfigured()) {
    return { ok: false, error: "NTFY_TOPIC is not configured" };
  }

  const url = `${config.ntfyUrl}/${encodeURIComponent(config.ntfyTopic)}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Title: payload.instance.label,
      Priority: priorityFor(payload),
      Tags: "robot",
    };
    if (config.ntfyToken.trim().length > 0) {
      headers.Authorization = `Bearer ${config.ntfyToken}`;
    }

    const response = await fetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    if (!response.ok) {
      let detail = "";
      try {
        detail = await response.text();
      } catch {
        detail = "";
      }
      return {
        ok: false,
        status: response.status,
        error: `relay responded ${response.status}${detail ? `: ${detail}` : ""}`,
      };
    }

    return { ok: true, status: response.status };
  } catch (error) {
    const message =
      error instanceof Error
        ? error.name === "AbortError"
          ? `request timed out after ${TIMEOUT_MS}ms`
          : error.message
        : String(error);
    return { ok: false, error: message };
  } finally {
    clearTimeout(timer);
  }
}
