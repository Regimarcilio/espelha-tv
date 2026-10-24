#!/usr/bin/env bash
# Smoke test ponta a ponta sem navegador: health, login, estado, claim,
# input, stream MJPEG e logout. Usa uma instância --demo isolada.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=env.sh
source "$ROOT/scripts/env.sh"

command -v curl >/dev/null 2>&1 || {
  echo "curl é necessário para o smoke test." >&2
  exit 1
}

PORT="${ESPELHA_SMOKE_PORT:-$((20000 + RANDOM % 20000))}"
TOKEN="${ESPELHA_SMOKE_TOKEN:-smoke-$RANDOM}"
BASE="http://127.0.0.1:$PORT"
TMP="$(mktemp -d)"
PID=""

cleanup() {
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT

cd "$ROOT"
java -jar target/espelha-tv.jar --demo --port="$PORT" --bind=127.0.0.1 --token="$TOKEN" \
  >"$TMP/server.log" 2>&1 &
PID=$!

ready=0
for _ in $(seq 1 50); do
  if curl -fsS "$BASE/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 0.2
done
if [[ "$ready" != "1" ]]; then
  echo "Servidor não respondeu em $BASE/health" >&2
  cat "$TMP/server.log" >&2
  exit 1
fi

curl -fsS "$BASE/health" | grep -q '"status":"ok"'

CODE="$(curl -sS -o "$TMP/login.json" -w '%{http_code}' \
  -c "$TMP/cookies" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'X-EspelhaTV: 1' \
  --data-urlencode "token=$TOKEN" \
  "$BASE/api/login")"
[[ "$CODE" == "200" ]] || {
  echo "Login falhou: HTTP $CODE" >&2
  cat "$TMP/login.json" >&2
  exit 1
}

curl -fsS -b "$TMP/cookies" "$BASE/api/state" | grep -q '"authenticated":true'

CLAIM="$(curl -fsS -b "$TMP/cookies" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'X-EspelhaTV: 1' \
  --data 'action=claim' \
  "$BASE/api/control")"
CONTROL_TOKEN="$(printf '%s' "$CLAIM" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
[[ -n "$CONTROL_TOKEN" ]] || {
  echo "Claim não retornou token: $CLAIM" >&2
  exit 1
}

CODE="$(curl -sS -o /dev/null -w '%{http_code}' -b "$TMP/cookies" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'X-EspelhaTV: 1' \
  --data-urlencode "token=$CONTROL_TOKEN" \
  --data 'type=move' --data 'x=0.5' --data 'y=0.5' \
  "$BASE/api/input")"
[[ "$CODE" == "204" ]] || {
  echo "Input falhou: HTTP $CODE" >&2
  exit 1
}

curl -sS --max-time 2 -b "$TMP/cookies" "$BASE/stream" -o "$TMP/stream.bin" 2>/dev/null || true
grep -aq -- '--frame' "$TMP/stream.bin" || {
  echo "Stream não enviou partes MJPEG." >&2
  exit 1
}

CODE="$(curl -sS -o /dev/null -w '%{http_code}' -b "$TMP/cookies" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'X-EspelhaTV: 1' \
  --data '' \
  "$BASE/api/logout")"
[[ "$CODE" == "200" ]] || {
  echo "Logout falhou: HTTP $CODE" >&2
  exit 1
}

echo "OK: health, login, estado, claim, input, stream MJPEG e logout."
