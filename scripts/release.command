#!/bin/bash

cd "$(dirname "$0")/.."

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  . "$NVM_DIR/nvm.sh"
fi
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

node scripts/release-version.mjs "$@"
status=$?

echo
read -r -p "发布流程结束，按回车关闭窗口。" _
exit "$status"
