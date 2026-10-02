# Android Companion

Un **widget Android permanente** che mostra una faccia sintetica minimale (due occhi con emozione) e reagisce agli eventi inviati da **OpenCode** tramite un tool **MCP**. Il colore della faccia dipende dall'istanza OpenCode che lo chiama; il widget può mostrare un testo e riprodurre una notifica sonora.

```
OpenCode ──MCP (stdio)──▶ android_companion_companion_set
                              │  HTTP POST (JSON + Bearer token)
                              ▼
                      ntfy (Docker, self-hosted)
                              │  stream JSON
                              ▼
              Android: CompanionStreamService (foreground service)
                  ├─▶ widget: occhi + emozione + colore istanza + testo
                  └─▶ notifica sonora (soft / alert)
```

## Componenti

| Componente | Percorso | Descrizione |
|---|---|---|
| Relay | `docker-compose.yml` | ntfy self-hosted (cache + auth) |
| MCP server | `mcp-server/` | Server stdio con i tool `companion_set` / `companion_status` |
| Config OpenCode | `opencode.json` | Registrazione del server MCP (schema OpenCode v1) |
| App Android | `android/` | Widget Glance + foreground service + impostazioni |

## Requisiti

- Docker + Docker Compose
- Node.js ≥ 18 (testato su v22)
- JDK 17, Android SDK (platform/build-tools 35+)
- OpenCode **v1** (testato 1.18.34)

## 1. Avvio del relay (ntfy)

```bash
cp .env.example .env
# genera un topic segreto lungo e mettilo in .env (NTFY_TOPIC)
docker compose up -d
```

Endpoint: `http://localhost:8080` · health: `GET /v1/health`.

### Autenticazione (hardening)

Con `NTFY_AUTH_DEFAULT_ACCESS=deny-all` la pubblicazione richiede un token; la lettura resta anonima così l'app non ha bisogno di credenziali. Setup una tantum:

```bash
printf '%s\n%s\n' "$PASS" "$PASS" | docker compose exec -T ntfy ntfy user add --role=user companion
docker compose exec -T ntfy ntfy access companion "$NTFY_TOPIC" rw
docker compose exec -T ntfy ntfy access everyone   "$NTFY_TOPIC" ro
docker compose exec -T ntfy ntfy token add companion   # -> tk_...
```

Metti il token in `.env` (`NTFY_TOKEN`) e in `opencode.json`.

## 2. Build del server MCP

```bash
cd mcp-server
npm install
npm run build
npm run smoke   # pubblica un payload di prova sul relay
```

Il server espone (transport **stdio**, SDK MCP v1):

- **`companion_set(emotion, text?, sound?, ttl_ms?)`** — fire-and-forget; ritorna `{ accepted, instance }`.
  - `emotion`: `neutral | happy | thinking | error | sleepy | listening`
  - `sound`: `none | soft | alert`
- **`companion_status()`** — stato del relay e ultimi eventi.

## 3. Configurazione OpenCode

`opencode.json` (schema v1: chiavi direttamente sotto `mcp`, campo `enabled`):

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
        "NTFY_TOPIC": "il-tuo-topic",
        "NTFY_TOKEN": "tk_..."
      },
      "timeout": 15000
    }
  }
}
```

L'LLM vedrà il tool come **`android_companion_companion_set`**.

### Multi-istanza / colore

Ogni progetto OpenCode può avere un `opencode.json` con `COMPANION_INSTANCE`/`COMPANION_LABEL`/`COMPANION_COLOR` diversi: il widget si colora in base all'istanza chiamante (last-write-wins).

## 4. Build e install dell'app Android

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Aggiungi il widget alla home (widget picker → *Android Companion*).

### Canale di rete

- Apri l'app, imposta **Relay URL** / **Topic** / **Token** e premi **Save**.
- Premi **Start listening**: avvia il foreground service che legge lo stream ntfy.
- Il servizio riparte al boot solo se era attivo (`enabled`).

#### Sviluppo via USB

```bash
adb reverse tcp:8080 tcp:8080   # il telefono raggiunge il PC su localhost:8080
```

#### Uso wireless

Imposta `Relay URL` all'IP LAN del PC (es. `http://192.168.1.133:8080`) e aggiungi quell'host a
`android/app/src/main/res/xml/network_security_config.xml` (oppure esponi ntfy dietro un reverse proxy TLS).

## 5. Sfondo del widget

In app, card **"Widget background"**: `T` = trasparente, più alcuni colori predefiniti. Il tap applica subito lo sfondo al widget.

## Note

- **I widget Android non animano**: ogni frame è un aggiornamento `RemoteViews`. Gli occhi sono ridisegnati su `Bitmap` con `android.graphics.Canvas` (`EyeRenderer.kt`) perché **Glance non ha `Canvas`/`drawBehind`**.
- **TTL**: se impostato, passato il tempo lo stato degrada a `neutral` (evita espressioni "congelate").
- **Broadcast di test**: `adb shell am broadcast -a dev.danger.companion.PUSH --es emotion happy --es text "hi"`.

## Riferimenti utili (ispirazione grafica)

- [jeremy-prt/bloub](https://github.com/jeremy-prt/bloub) — avatar x.ai/Grok
- [CyberAgentAILab/Web-Eye-Animation](https://github.com/CyberAgentAILab/Web-Eye-Animation) — occhi line-art
- [orji123/Irisoled](https://github.com/orji123/Irisoled) — 32 espressioni monocromatiche (MIT)
- [dicebear Line Face](https://dicebear.com/styles/line-face/) — facce a tratto singolo (CC0)
