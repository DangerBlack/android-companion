#!/bin/sh
set -e

AUTH_FILE="${NTFY_AUTH_FILE:-/var/lib/ntfy/user.db}"
export NTFY_AUTH_FILE="$AUTH_FILE"
mkdir -p "$(dirname "$AUTH_FILE")"

ntfy serve &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null' TERM INT

if [ -n "$NTFY_USER" ] && [ -n "$NTFY_PASSWORD" ]; then
  i=0
  while [ ! -f "$AUTH_FILE" ] && [ "$i" -lt 30 ]; do
    sleep 1
    i=$((i + 1))
  done
  if ! ntfy user list 2>/dev/null | grep -q "$NTFY_USER"; then
    printf '%s\n%s\n' "$NTFY_PASSWORD" "$NTFY_PASSWORD" | ntfy user add --role="${NTFY_USER_ROLE:-admin}" "$NTFY_USER" || true
    if [ -n "$NTFY_TOPIC" ]; then
      ntfy access "$NTFY_USER" "$NTFY_TOPIC" rw || true
      ntfy access everyone "$NTFY_TOPIC" ro || true
    fi
  fi
fi

wait "$SERVER_PID"
