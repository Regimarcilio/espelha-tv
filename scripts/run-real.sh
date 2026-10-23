#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${WAYLAND_DISPLAY:-}" && "${ESPELHATV_ALLOW_WAYLAND:-0}" != "1" ]]; then
  cat >&2 <<'MSG'
Wayland detectado: a captura real via Robot/XWayland pode sair preta.

Use uma sessão X11 nativa ou valide primeiro com:
  ./scripts/run.sh --demo --token="demo"

Para tentar mesmo assim:
  ESPELHATV_ALLOW_WAYLAND=1 ./scripts/run-real.sh --token="..."
MSG
  exit 2
fi

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY não definido: não há sessão gráfica para capturar." >&2
  exit 2
fi

exec "$ROOT/scripts/run.sh" "$@"
