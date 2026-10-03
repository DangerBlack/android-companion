# How to Configure and Use the Android Companion MCP Server

Audience: an AI coding agent (e.g. OpenCode) that must install, configure, verify, and
call the `android_companion` MCP server. This file is intentionally explicit and
machine-followable.

---

## 1. What this is

A local **stdio MCP server** exposing tools that publish "companion" events to a
self-hosted **ntfy** relay. An Android app (home-screen widget + foreground service)
subscribes to the relay and renders a synthetic face (eyes with an emotion), an
optional text message, and an optional creature sound.

```
AI agent → MCP tool (stdio) → HTTP POST (JSON + Bearer token) → ntfy → Android app/widget
```

The MCP server lives in `mcp-server/`. It speaks the classic MCP stdio handshake
(protocol <= 2025-11-25) and is compatible with OpenCode **v1**.

---

## 2. Prerequisites

1. **ntfy relay** running (the AI may start it):
   ```bash
   docker compose up -d          # from the repo root; serves http://localhost:8080
   curl -s http://localhost:8080/v1/health
   ```
2. **Node.js >= 18**.
3. **A topic and (if auth is enabled) a token** in `.env`:
   - `NTFY_TOPIC` = long random string (treat as a secret).
   - `NTFY_TOKEN` = `tk_...` (required when the relay runs with `NTFY_AUTH_DEFAULT_ACCESS=deny-all`).
4. **The widget app installed on the phone** and its stream service started (see §9).

---

## 3. Build the MCP server

```bash
cd mcp-server
npm install
npm run build          # emits dist/index.js
npm run smoke          # publishes one test payload; prints the HTTP status
```

`dist/index.js` is the executable entrypoint (has a shebang). The server writes logs
to **stderr only**; stdout carries MCP JSON-RPC frames exclusively.

---

## 4. Configure the AI host (OpenCode)

OpenCode **v1** config schema: server entries live directly under `mcp` and use
`enabled` (not v2's `mcp.servers`/`disabled`). Place this in the project
`opencode.json` (or `~/.config/opencode/opencode.json`):

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "android_companion": {
      "type": "local",
      "command": ["node", "/ABSOLUTE/PATH/TO/android-companion/mcp-server/dist/index.js"],
      "enabled": true,
      "timeout": 15000,
      "environment": {
        "COMPANION_INSTANCE": "work",
        "COMPANION_LABEL": "Work",
        "COMPANION_COLOR": "#4F9CF9",
        "NTFY_URL": "http://localhost:8080",
        "NTFY_TOPIC": "companion-REPLACE-WITH-YOUR-TOPIC",
        "NTFY_TOKEN": "tk_REPLACE_WITH_YOUR_TOKEN"
      }
    }
  }
}
```

Rules and gotchas:

- `command` is a single **array** (executable + args), not `command`/`args`.
- Use an **absolute** path to `dist/index.js`.
- `timeout` is milliseconds for fetching the tool catalog; the default `5000` is tight — use `15000`.
- On success the model sees the tool as **`<serverName>_<toolName>`**. With the server
  name `android_companion`, the tools become:
  - `android_companion_companion_set`
  - `android_companion_companion_status`
- **Multi-instance = multi-color**: run a separate `opencode.json` per project with a
  different `COMPANION_INSTANCE` / `COMPANION_LABEL` / `COMPANION_COLOR`. The widget
  shows the color of the instance that published last (last-write-wins).

### Environment variables

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `NTFY_URL` | no | `http://localhost:8080` | Relay base URL |
| `NTFY_TOPIC` | yes (to publish) | `""` | Secret topic; empty ⇒ tool returns `accepted:false` |
| `NTFY_TOKEN` | if auth on | `""` | Bearer token for publishing |
| `COMPANION_INSTANCE` | no | `default` | Instance id sent in the payload |
| `COMPANION_LABEL` | no | `OpenCode` | Human label (used as ntfy message title) |
| `COMPANION_COLOR` | no | `#4F9CF9` | Hex color of this instance; tints the face |

---

## 5. Tool reference

### `companion_set`

Publishes an emotion event. Fire-and-forget; returns as soon as the relay acknowledges
(bounded by a 3 s HTTP timeout).

Input (JSON object):

| Field | Type | Required | Default | Constraints |
|---|---|---|---|---|
| `emotion` | string enum | yes | — | one of `neutral`, `happy`, `thinking`, `error`, `sleepy`, `listening` |
| `text` | string | no | — | short message shown under the face; keep <= ~60 chars |
| `sound` | string enum | no | `none` | `none` \| `soft` \| `alert` |
| `ttl_ms` | integer | no | — | ms after which the face degrades to `neutral` |

Returns (text content, JSON string):

```json
{"accepted": true, "instance": "work"}
```

On relay failure or unconfigured topic:

```json
{"accepted": false, "instance": "work", "error": "..."}
```

### `companion_status`

No input. Returns the last 20 in-memory events and relay configuration:

```json
{
  "device_hint": "unknown",
  "published_count": 3,
  "last": { "...last payload or null..." },
  "relay": { "url": "http://localhost:8080", "topic": "companion-...", "configured": true }
}
```

Use `companion_status` to check the relay is configured before reporting a problem to
the user. It does not confirm the phone received anything (the server has no phone
feedback channel).

---

## 6. Emotion semantics

The Android widget renders the face with geometry ported from
[`jeremy-prt/bloub`](https://github.com/jeremy-prt/bloub) (MIT). Each emotion maps to a
specific expression:

| `emotion` | Face | Use when |
|---|---|---|
| `neutral` | Resting face | idle / no strong signal |
| `happy` | Squinting happy eyes | task finished well |
| `thinking` | Asymmetric eyes, gaze aside | reasoning / working |
| `error` | Wide alarmed eyes | failure, exception, blocked |
| `sleepy` | Half-closed lids | idle/suspended, no work |
| `listening` | Attentive wide eyes | waiting for the user / input needed |

### Sound behavior

- `none` — silent.
- `soft` — short creature "boop"; plays only when the phone ringer is in **normal**
  mode (otherwise it vibrates).
- `alert` — the creature's nudge, **louder and repeated**, played on the alarm stream
  so it is audible **even in vibrate/silent** (still muted by total Do-Not-Disturb),
  plus a vibration.

---

## 7. Wire payload contract

The MCP server publishes this JSON as the ntfy message body:

```json
{
  "v": 1,
  "instance": { "id": "work", "label": "Work", "color": "#4F9CF9" },
  "emotion": "thinking",
  "text": "Compiling…",
  "sound": "none",
  "ttl_ms": 30000,
  "ts": 1759420000000
}
```

`ts` is `Date.now()` (ms). `text` and `ttl_ms` are omitted when not provided. The
Android app maps `instance.color` to the face color and `emotion` to the expression.

---

## 8. Calling conventions for the model

- Prefer calling `companion_set` at **meaningful state changes**, not every step:

  | Moment | Suggested call |
  |---|---|
  | start of a long task | `emotion:"thinking"`, `text:"<what you're doing>"` |
  | done | `emotion:"happy"`, `text:"<short result>"` |
  | error/blocked | `emotion:"error"`, `text:"<short reason>"`, `sound:"alert"` |
  | need the user | `emotion:"listening"`, `text:"<question>"`, `sound:"alert"` |
  | going idle | `emotion:"sleepy"` or `neutral` |

- Keep `text` short (the widget clamps to 2 lines).
- Use `ttl_ms` so a transient state (e.g. `thinking`) cannot get stuck; after the TTL
  the face returns to `neutral`.
- It is **non-blocking**: do not wait for a human acknowledgement; `accepted:true`
  means the relay took the message.
- Do not send the emotion name as `text` — `text` is for a human-readable message.

---

## 9. Verify end to end

1. **Relay reachable**: `curl -s http://localhost:8080/v1/health` → `{"healthy":true}`.
2. **Server publishes**: export the vars first, then `cd mcp-server && npm run smoke` →
   `status=200`. (The server reads `process.env` only — it does not load `.env`; the
   OpenCode config sources the env file, or `set -a; . .env; set +a` in the shell.)
3. **MCP handshake** (stdout must be pure JSON-RPC):
   ```bash
   printf '%s\n' \
     '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' \
     '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
     '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
     | NTFY_TOPIC="$NTFY_TOPIC" node dist/index.js
   ```
4. **Phone side**: ensure the app's stream service is running
   (`adb shell am start-foreground-service -n dev.danger.companion/.push.CompanionStreamService`)
   and that USB dev uses `adb reverse tcp:8080 tcp:8080`. Publish a payload and watch:
   ```bash
   adb logcat | grep CompanionStream       # "applying emotion=... instance=..."
   ```

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Tool `accepted:false`, error about topic | `NTFY_TOPIC` empty or wrong in the MCP env |
| ntfy returns 403 | auth enabled and `NTFY_TOKEN` missing/invalid |
| Tool not visible to the model | wrong OpenCode schema (v1 vs v2) or `enabled:false`; restart OpenCode |
| Widget doesn't update | phone stream service not running, or phone can't reach the relay |
| No sound | phone ringer set to silent/vibrate: use `sound:"alert"` (alarm stream) or switch ringer to normal |
| `System UI isn't responding` on an emulator | unrelated emulator issue |

---

## 11. Security notes

- The relay runs with `deny-all` + a Bearer token: **both publishing and reading require a
  token by default** (`NTFY_ANON_READ=false`). The Android app is given a read-only token.
  Keep tokens and the topic secret. `.env` and `opencode.json` are git-ignored.
- The web UI stays enabled and **requires login** (username/password) under `deny-all`.
- Prefer least privilege: a write-only user/token for the publisher, a read-only user/token
  for the app. Tokens are unscoped (they inherit the user's role/ACL), so never issue an
  `admin` token to the app or the MCP.
- If the relay is exposed beyond the LAN, put it behind TLS (reverse proxy / Tailscale).
- Never log the token or post it into chat.

---

## 12. File map

| Path | Purpose |
|---|---|
| `mcp-server/src/index.ts` | MCP server, tool registration |
| `mcp-server/src/relay.ts` | ntfy publish (HTTP + optional Bearer) |
| `mcp-server/src/config.ts` | env parsing / defaults |
| `mcp-server/src/types.ts` | payload types |
| `docker-compose.yml` | ntfy relay |
| `opencode.json` / `examples/opencode.mcp.json` | host config / template |
| `android/` | widget app + foreground stream service |
