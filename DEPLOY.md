# Deploy the Android Companion relay and MCP server

This guide puts the relay on a server and shows how to distribute the images on Docker Hub.

Two images are provided:

| Image | Dockerfile | Purpose |
|---|---|---|
| `android-companion-relay` | `docker/relay/Dockerfile` | ntfy + startup user/topic bootstrap |
| `android-companion-mcp` | `docker/mcp/Dockerfile` | the stdio MCP server (run by OpenCode) |

---

## 1. Build and push to Docker Hub

Replace `YOURUSER` with your Docker Hub account.

All base images (`binwiederhier/ntfy`, `node:22-alpine`, `caddy:2`) are **multi-arch with
`linux/arm64`**, so the images run natively on a Raspberry Pi 5 (arm64). On the Pi you can
simply use `docker build`; from another machine use `buildx` to publish an amd64+arm64
manifest list.

### Single-arch (built on the target machine)

```bash
docker build -f docker/relay/Dockerfile -t YOURUSER/android-companion-relay:0.1.0 -t YOURUSER/android-companion-relay:latest .
docker build -f docker/mcp/Dockerfile   -t YOURUSER/android-companion-mcp:0.1.0   -t YOURUSER/android-companion-mcp:latest   .
docker login
docker push YOURUSER/android-companion-relay:0.1.0
docker push YOURUSER/android-companion-relay:latest
docker push YOURUSER/android-companion-mcp:0.1.0
docker push YOURUSER/android-companion-mcp:latest
```

### Multi-arch (amd64 + arm64, recommended)

```bash
docker buildx create --use --name companion 2>/dev/null || docker buildx use companion
docker login
docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/relay/Dockerfile \
  -t YOURUSER/android-companion-relay:0.1.0 -t YOURUSER/android-companion-relay:latest --push .
docker buildx build --platform linux/amd64,linux/arm64 \
  -f docker/mcp/Dockerfile \
  -t YOURUSER/android-companion-mcp:0.1.0 -t YOURUSER/android-companion-mcp:latest --push .
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
        "YOURUSER/android-companion-mcp:latest"
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

- `NTFY_AUTH_DEFAULT_ACCESS=deny-all`; keep the publish token secret.
- Never expose the relay over plain HTTP on a public interface; use Caddy/Tailscale/Cloudflare.
- Use a long random topic; treat it as a password (anonymous reads rely on it).
- To also require auth for reads, remove the `everyone` ACL and set the token in the app.
- Pin image tags (`:0.1.0`) in production rather than `:latest`.
