# Deploy the Android Companion relay and MCP server

This guide puts the relay on a server and shows how to distribute the images on Docker Hub.

Two images are provided, published at **`dangerblack/android-companion`** (Docker Hub):

| Tag | Dockerfile | Purpose | Where it runs |
|---|---|---|---|
| `:relay` | `docker/relay/Dockerfile` | ntfy relay + startup user/topic bootstrap | **on the server** (must be reachable by the phone) |
| `:mcp` | `docker/mcp/Dockerfile` | the stdio MCP server spawned by OpenCode | wherever **OpenCode** runs; **OPTIONAL** (only if you don't want Node installed locally) |

Only `:relay` needs a server. `:mcp` is a packaging convenience — OpenCode can either run the
MCP server from source (`node mcp-server/dist/index.js`) or spawn this container.

---

## 1. Build and push to Docker Hub

Published namespace: **`dangerblack/android-companion`** with tags `:relay` and `:mcp`.

All base images (`binwiederhier/ntfy`, `node:22-alpine`, `caddy:2`) are **multi-arch with
`linux/arm64`**, so the images run natively on a Raspberry Pi 5 (arm64). On the Pi you can
simply use `docker build`; from another machine use `buildx` to publish an amd64+arm64
manifest list.

### Single-arch (built on the target machine)

```bash
docker build -f docker/relay/Dockerfile -t dangerblack/android-companion:relay .
docker build -f docker/mcp/Dockerfile   -t dangerblack/android-companion:mcp .
docker login
docker push dangerblack/android-companion:relay
docker push dangerblack/android-companion:mcp
```

### Multi-arch (amd64 + arm64, recommended)

```bash
docker buildx create --use --name companion 2>/dev/null || docker buildx use companion
docker login
docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/relay/Dockerfile -t dangerblack/android-companion:relay --push .
docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/mcp/Dockerfile -t dangerblack/android-companion:mcp --push .
```

The Pi pulls the `arm64` variant automatically; an x86 server pulls `amd64`.

---

## 2. Run the relay on the server (with TLS)

Requires a DNS `A`/`AAAA` record pointing your domain at the server; Caddy obtains the TLS
certificate automatically.

```bash
# on the server, in a directory containing docker-compose.prod.yml, deploy/Caddyfile
cp deploy/env.prod.example .env
# edit .env: DOMAIN, NTFY_PASSWORD, NTFY_TOPIC, NTFY_BASE_URL, RELAY_IMAGE

docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f relay
```

The relay bootstraps the admin user (`NTFY_USER`/`NTFY_PASSWORD`) and the topic ACL on first
start. Create a publish token once:

```bash
docker compose -f docker-compose.prod.yml exec relay ntfy token add "$NTFY_USER"
# -> tk_...   put this in the MCP server env and keep it secret
```

Health check: `curl -s https://$DOMAIN/v1/health` → `{"healthy":true}`.

### Without a public domain

- **Tailscale (recommended)** — install Tailscale on the server and the phone, keep the relay
  bound to the private interface, and use `http://<tailscale-ip>:8080`. Nothing is exposed to
  the internet.
- **Cloudflare Tunnel** — no open ports, TLS handled by Cloudflare.
- If you must expose plain HTTP on a LAN only, run the compose without Caddy and publish
  `8080:80`, then add that host to the app's `network_security_config.xml`.

---

## 3. Point the MCP server at the relay

### Option A — run the MCP server from source (OpenCode spawns it)

In the project `opencode.json`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "android_companion": {
      "type": "local",
      "command": ["node", "/path/to/android-companion/mcp-server/dist/index.js"],
      "enabled": true,
      "timeout": 15000,
      "environment": {
        "COMPANION_INSTANCE": "work",
        "COMPANION_LABEL": "Work",
        "COMPANION_COLOR": "#4F9CF9",
        "NTFY_URL": "https://companion.example.com",
        "NTFY_TOPIC": "companion-<random>",
        "NTFY_TOKEN": "tk_..."
      }
    }
  }
}
```

### Option B — run the MCP server from the Docker Hub image (stdio)

OpenCode can spawn a container; `-i` keeps stdin open for the MCP stdio transport:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "android_companion": {
      "type": "local",
      "command": [
        "docker", "run", "-i", "--rm",
        "-e", "NTFY_URL=https://companion.example.com",
        "-e", "NTFY_TOPIC=companion-<random>",
        "-e", "NTFY_TOKEN=tk_...",
        "-e", "COMPANION_INSTANCE=work",
        "-e", "COMPANION_LABEL=Work",
        "-e", "COMPANION_COLOR=#4F9CF9",
        "dangerblack/android-companion:mcp"
      ],
      "enabled": true,
      "timeout": 15000
    }
  }
}
```

---

## 4. Configure the phone

In the app settings:

- **Relay URL** = `https://companion.example.com` (or the Tailscale/LAN URL).
- **Topic** = the same `NTFY_TOPIC`.
- **Token** = leave empty if reads are anonymous; set it if you also restricted reads.
- Press **Start listening**.

For plain-HTTP LAN/Tailscale URLs, add the host to
`android/app/src/main/res/xml/network_security_config.xml`.

---

## 5. Security checklist

- `NTFY_AUTH_DEFAULT_ACCESS=deny-all`; keep tokens secret.
- **Least privilege**: issue a **write-only** token for the publisher (MCP) and a **read-only**
  token for the Android app; never hand the app/MCP an `admin` token (tokens inherit the
  user's role/ACL, so an admin token can read everything and manage users).
  ```bash
  ntfy user add --role=user companion-publisher && ntfy access companion-publisher "$NTFY_TOPIC" wo
  ntfy user add --role=user companion-reader    && ntfy access companion-reader    "$NTFY_TOPIC" ro
  ```
- The web UI stays enabled (`NTFY_WEB_ROOT=/`) and requires login under `deny-all`. Anonymous
  reads are off by default (`NTFY_ANON_READ=false`) so the app must set a token.
- Never expose the relay over plain HTTP on a public interface; use Caddy/Tailscale/Cloudflare.
- Use a long random topic; **rotate it** if it may have leaked. Treat it as a secret.
- Pin the image tag in production rather than `:latest`.
