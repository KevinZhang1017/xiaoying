#!/usr/bin/env sh
# 调整后端模拟行为（需 DEV_API_ENABLED=true）。只影响之后新建的任务。
#
#   ./scripts/sim.sh show                 查看当前配置
#   ./scripts/sim.sh fail GENERATE        之后的任务在「模拟生成」阶段失败（ASSET_CHECK / GENERATE / FINALIZE）
#   ./scripts/sim.sh ok                   取消失败注入
#   ./scripts/sim.sh slow 10000           把「模拟生成」耗时调为 10 秒
#   ./scripts/sim.sh delays 500 2000 500  分别设置三个阶段耗时（毫秒）
#   ./scripts/sim.sh reset                恢复启动时的默认配置
set -eu
API="${API:-http://localhost:8080}/api/dev/simulation"

put() { curl -fsS -X PUT -H 'Content-Type: application/json' -d "$1" "$API"; echo; }

case "${1:-show}" in
  show)   curl -fsS "$API"; echo ;;
  fail)   put "{\"failStage\":\"${2:?stage required}\"}" ;;
  ok)     put '{"failStage":"NONE"}' ;;
  slow)   put "{\"delaysMs\":{\"GENERATE\":${2:?ms required}}}" ;;
  delays) put "{\"delaysMs\":{\"ASSET_CHECK\":${2:?},\"GENERATE\":${3:?},\"FINALIZE\":${4:?}}}" ;;
  reset)  curl -fsS -X POST "$API/reset"; echo ;;
  *) echo "unknown command: $1" >&2; exit 1 ;;
esac
