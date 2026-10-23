#!/usr/bin/env bash
# Carrega o toolchain local (.toolchain) ou o do sistema.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -x "$ROOT/.toolchain/jdk-21/bin/java" ]]; then
  export JAVA_HOME="$ROOT/.toolchain/jdk-21"
fi
if [[ -x "$ROOT/.toolchain/maven/bin/mvn" ]]; then
  export PATH="$ROOT/.toolchain/maven/bin:$PATH"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 não encontrado. Rode ./scripts/bootstrap-toolchain.sh ou instale openjdk-21-jdk." >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven não encontrado. Rode ./scripts/bootstrap-toolchain.sh ou instale maven." >&2
  exit 1
fi
