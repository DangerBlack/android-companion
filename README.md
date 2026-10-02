# Android Companion

A **permanent Android home-screen widget** that shows a minimal synthetic face — a filled
circle with two expressive eye-holes — and reacts to events pushed by **OpenCode** through
an **MCP** tool. The face color depends on the OpenCode instance that calls it; the widget
can also show a short text message and play a creature sound.

```
OpenCode ──MCP (stdio)──▶ android_companion_companion_set
                              │  HTTP POST (JSON + Bearer token)
                              ▼
                      ntfy (Docker, self-hosted)
                              │  JSON stream
                              ▼
              Android: CompanionStreamService (foreground service)
                  ├─▶ widget: blob face (emotion) + instance color + message
                  └─▶ creature sound (soft / alert)
```

## Components

| Component | Path | Description |
|---|---|---|
| Relay | `docker-compose.yml` | Self-hosted ntfy (cache + auth) |
| MCP server | `mcp-server/` | stdio server exposing `companion_set` / `companion_status` |
| OpenCode config | `opencode.json` | MCP server registration (OpenCode v1 schema) |
| Android app | `android/` | Glance widget + foreground stream service + settings |

## Requirements

- Docker + Docker Compose
- Node.js >= 18 (tested on v22)
- JDK 17, Android SDK (platform/build-tools 35+)
- OpenCode **v1** (tested 1.18.34)

## 1. Start the relay (ntfy)

```bash
cp .env.example .env
# generate a long random topic and put it in .env (NTFY_TOPIC)
docker compose up -d
```

Endpoint: `http://localhost:8080` · health: `GET /v1/health`.

### Authentication (hardening)

With `NTFY_AUTH_DEFAULT_ACCESS=deny-all`, publishing requires a token; reads stay
anonymous so the app needs no credentials. One-time setup:

```bash
printf '%s\n%s\n' "$PASS" "$PASS" | docker compose exec -T ntfy ntfy user add --role=user companion
docker compose exec -T ntfy ntfy access companion "$NTFY_TOPIC" rw
docker compose exec -T ntfy ntfy access everyone   "$NTFY_TOPIC" ro
docker compose exec -T ntfy ntfy token add companion   # -> tk_...
```

Put the token in `.env` (`NTFY_TOKEN`) and in `opencode.json`.

## 2. Build the MCP server

```bash
cd mcp-server
npm install
npm run build
npm run smoke   # publishes a test payload to the relay
```

Tools exposed (transport **stdio**, MCP SDK v1):

- **`companion_set(emotion, text?, sound?, ttl_ms?)`** — fire-and-forget; returns
  `{ accepted, instance }`.
  - `emotion`: `neutral | happy | thinking | error | sleepy | listening`
  - `sound`: `none | soft | alert`
- **`companion_status()`** — relay config and recent events.

See **[HOW-TO-CONFIGURE-MCP.md](HOW-TO-CONFIGURE-MCP.md)** for the full agent-oriented
configuration and usage guide.

## 3. Configure OpenCode

`opencode.json` (v1 schema: server entries directly under `mcp`, field `enabled`):

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "android_companion": {
      "type": "local",
      "command": ["node", "/ABSOLUTE/PATH/android-companion/mcp-server/dist/index.js"],
      "enabled": true,
      "environment": {
        "COMPANION_INSTANCE": "work",
        "COMPANION_LABEL": "Work",
        "COMPANION_COLOR": "#4F9CF9",
        "NTFY_URL": "http://localhost:8080",
        "NTFY_TOPIC": "your-topic",
        "NTFY_TOKEN": "tk_..."
      },
      "timeout": 15000
    }
  }
}
```

The model sees the tool as **`android_companion_companion_set`**.

### Multi-instance / color

Each OpenCode project can have its own `opencode.json` with different
`COMPANION_INSTANCE` / `COMPANION_LABEL` / `COMPANION_COLOR`: the widget is colored after
the instance that published last (last-write-wins).

## 4. Build and install the Android app

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Add the widget to the home screen (widget picker → *Android Companion*).

### Network channel

- Open the app, set **Relay URL** / **Topic** / **Token** and press **Save**.
- Press **Start listening**: it starts the foreground service that reads the ntfy stream.
- The service restarts on boot only if it was enabled.

#### USB development

```bash
adb reverse tcp:8080 tcp:8080   # the phone reaches the PC at localhost:8080
```

#### Wireless use

Set **Relay URL** to the PC LAN IP (e.g. `http://192.168.1.133:8080`) and add that host to
`android/app/src/main/res/xml/network_security_config.xml` (or expose ntfy behind a TLS
reverse proxy).

## 5. Widget background

In the app, the **Widget background** card offers `T` (transparent) plus a few colors.
Tapping applies the background immediately.

## 6. Face

The face geometry is ported from [jeremy-prt/bloub](https://github.com/jeremy-prt/bloub)
(MIT, © Jérémy Perret): a filled circle whose eyes are **holes** painted on a sphere
(head yaw/pitch/roll), so perspective and the ~26° lean come from the sphere math. With a
transparent widget background the eyes show the wallpaper; with an opaque background they
show that color. Each emotion maps to a bloub expression. On each event the face plays a
short blink/gaze burst, then settles on the rest pose.

## 7. Creature sound

`CreatureSound.kt` synthesizes short "nudges" with `AudioTrack` — no audio assets, no
library. Each emotion has its own contour. `soft` respects the ringer; `alert` is louder,
repeated, and played on the alarm stream so it is audible even in vibrate/silent (still
muted by total Do-Not-Disturb).

## 8. Production deployment (Docker)

For running the relay on a server (with TLS and optional Docker Hub images), see
**[DEPLOY.md](DEPLOY.md)**.

## Notes

- **Android widgets cannot animate continuously**: every frame is a `RemoteViews` update.
  The eyes are redrawn to a `Bitmap` with `android.graphics.Canvas` because **Glance has no
  `Canvas`/`drawBehind`**.
- **TTL**: when set, the face degrades to `neutral` after the duration (no stuck expression).
- **Test broadcast**: `adb shell am broadcast -a dev.danger.companion.PUSH --es emotion happy --es text "hi"`.

## Credits / third-party

- Face geometry: [jeremy-prt/bloub](https://github.com/jeremy-prt/bloub) — MIT, © Jérémy
  Perret. License text in [`third_party/bloub-LICENSE`](third_party/bloub-LICENSE).

## Other references (visual inspiration)

- [CyberAgentAILab/Web-Eye-Animation](https://github.com/CyberAgentAILab/Web-Eye-Animation) — line-art eyes
- [orji123/Irisoled](https://github.com/orji123/Irisoled) — 32 monochrome expressions (MIT)
- [dicebear Line Face](https://dicebear.com/styles/line-face/) — single-stroke faces (CC0)
