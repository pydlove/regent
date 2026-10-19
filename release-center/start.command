#!/bin/bash

cd "$(dirname "$0")"
./start.sh
status=$?

echo
read -r -p "按回车关闭窗口。" _
exit "$status"
