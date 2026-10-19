#!/bin/bash
set -euo pipefail

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  . "$NVM_DIR/nvm.sh"
fi
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

if command -v zsh >/dev/null 2>&1; then
  ZSH_MAVEN_OPTS="$(zsh -lc 'print -rn -- "$MAVEN_OPTS"' 2>/dev/null || true)"
  ZSH_MAVEN_REPOSITORY="$(zsh -lc 'print -rn -- "$MAVEN_LOCAL_REPOSITORY"' 2>/dev/null || true)"
  if [ -n "$ZSH_MAVEN_OPTS" ]; then export MAVEN_OPTS="$ZSH_MAVEN_OPTS"; fi
  if [ -n "$ZSH_MAVEN_REPOSITORY" ]; then
    export MAVEN_LOCAL_REPOSITORY="$ZSH_MAVEN_REPOSITORY"
  fi
fi

if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  if [ -n "$JAVA_HOME" ]; then export JAVA_HOME; fi
fi

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$HOME/.regent/release-center/service.env"
if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  . "$ENV_FILE"
fi

cd "$PROJECT_DIR"
exec node release-center/server.mjs
