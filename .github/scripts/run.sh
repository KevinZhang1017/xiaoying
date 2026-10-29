#!/usr/bin/env bash
# 运行命令；失败时把关键输出写成 GitHub 注解（::error），便于在 PR / API 中直接查看原因。
set -uo pipefail
title="$1"; shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  enc() { sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}' | sed -e 's/\r/%0D/g'; }
  errors=$(grep -E "\[ERROR\]|ERROR|Error:|error TS|FAIL|✗|×|AssertionError|Expected|Received|Caused by|Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]" "$log" \
    | grep -v "^\s*at " | head -n 60 | cut -c1-400)
  echo "::error title=${title} (matched lines)::$(printf '%s\n' "$errors" | enc)"
  echo "::error title=${title} (tail)::$(tail -n 60 "$log" | cut -c1-400 | enc)"
fi
exit "$status"
