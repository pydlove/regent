#!/bin/sh
# OpenSSH 的无 TTY 密码登录辅助程序。密码只从调用进程的环境读取，不落盘。
printf '%s\n' "${REGENT_SSH_PASSWORD:-}"
