#!/bin/bash
source "$(dirname "$0")/_common.sh"
"$SCRIPT_DIR/stop-all.sh"
echo
sleep 2
"$SCRIPT_DIR/start-all.sh"
