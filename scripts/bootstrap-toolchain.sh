#!/usr/bin/env bash
# Baixa JDK 21 (Temurin) e Maven 3.9.16 para .toolchain/ sem precisar de sudo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="$ROOT/.toolchain"
JDK_DIR="$TOOLCHAIN/jdk-21"
MVN_DIR="$TOOLCHAIN/maven"

mkdir -p "$TOOLCHAIN"

case "$(uname -m)" in
  x86_64)  ARCH="x64" ;;
  aarch64|arm64) ARCH="aarch64" ;;
  *) echo "Arquitetura não suportada: $(uname -m)" >&2; exit 1 ;;
esac

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
  echo "Baixando JDK 21 (Temurin $ARCH)..."
  TMP_JDK="$TOOLCHAIN/jdk21.tar.gz"
  curl -L --fail --retry 3 -o "$TMP_JDK" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/$ARCH/jdk/hotspot/normal/eclipse?project=jdk"
  mkdir -p "$TOOLCHAIN/jdk-extract"
  tar -xzf "$TMP_JDK" -C "$TOOLCHAIN/jdk-extract"
  JDK_EXTRACTED="$(find "$TOOLCHAIN/jdk-extract" -maxdepth 1 -type d -name 'jdk-*' | head -n 1)"
  ln -sfn "$JDK_EXTRACTED" "$JDK_DIR"
  rm -f "$TMP_JDK"
fi

if [[ ! -x "$MVN_DIR/bin/mvn" ]]; then
  echo "Baixando Maven 3.9.16..."
  TMP_MVN="$TOOLCHAIN/maven.tar.gz"
  curl -L --fail --retry 3 -o "$TMP_MVN" \
    "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz"
  mkdir -p "$TOOLCHAIN/maven-extract"
  tar -xzf "$TMP_MVN" -C "$TOOLCHAIN/maven-extract"
  MVN_EXTRACTED="$(find "$TOOLCHAIN/maven-extract" -maxdepth 1 -type d -name 'apache-maven-*' | head -n 1)"
  ln -sfn "$MVN_EXTRACTED" "$MVN_DIR"
  rm -f "$TMP_MVN"
fi

JAVA_HOME="$JDK_DIR" "$JDK_DIR/bin/java" -version
JAVA_HOME="$JDK_DIR" "$MVN_DIR/bin/mvn" -version | head -n 3
echo "Toolchain pronto em $TOOLCHAIN"
