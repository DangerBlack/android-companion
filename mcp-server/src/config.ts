export interface Config {
  instanceId: string;
  label: string;
  color: string;
  ntfyUrl: string;
  ntfyTopic: string;
  ntfyToken: string;
}

function env(name: string, fallback: string): string {
  const value = process.env[name];
  return value === undefined || value === "" ? fallback : value;
}

export const config: Config = {
  instanceId: env("COMPANION_INSTANCE", "default"),
  label: env("COMPANION_LABEL", "OpenCode"),
  color: env("COMPANION_COLOR", "#4F9CF9"),
  ntfyUrl: env("NTFY_URL", "http://localhost:8080").replace(/\/+$/, ""),
  ntfyTopic: env("NTFY_TOPIC", ""),
  ntfyToken: env("NTFY_TOKEN", ""),
};

export function relayConfigured(): boolean {
  return config.ntfyTopic.trim().length > 0;
}
