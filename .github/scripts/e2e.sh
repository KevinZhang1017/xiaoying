#!/usr/bin/env bash
# 端到端冒烟测试：针对 docker compose 启动的整套服务，经由 Next.js(3000) 代理访问 Java。
set -euo pipefail
WEB="${WEB:-http://localhost:3000}"
API="${API:-http://localhost:8080}"
JAR_A="$(mktemp)"; JAR_B="$(mktemp)"
fail() { echo "E2E FAILED: $*" >&2; exit 1; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1], {}, {'d': d}))" "$1"; }

echo "--- wait for frontend"
for i in $(seq 1 60); do curl -fsS -o /dev/null "$WEB/login" && break; sleep 2; done
curl -fsS -o /dev/null "$WEB/login" || fail "frontend not reachable"

echo "--- pages"
code=$(curl -s -o /dev/null -w '%{http_code}' "$WEB/")
[ "$code" = "307" ] || fail "anonymous / should redirect to /login, got $code"
curl -fsS "$WEB/api/templates" | json "len(d)" | grep -qx 8 || fail "templates via proxy"
curl -fsS -o /dev/null "$WEB/demo/covers/demo-postcard.jpg" || fail "cover via proxy"

echo "--- login"
curl -fsS -c "$JAR_A" -H 'Content-Type: application/json' \
  -d '{"email":"alice@demo.local","password":"demo1234"}' "$WEB/api/auth/login" >/dev/null || fail "login alice"
curl -fsS -c "$JAR_B" -H 'Content-Type: application/json' \
  -d '{"email":"bob@demo.local","password":"demo1234"}' "$WEB/api/auth/login" >/dev/null || fail "login bob"
curl -fsS -b "$JAR_A" "$WEB/" | grep -q "旅行明信片" || fail "template list page (SSR) for logged-in user"
curl -fsS -b "$JAR_A" "$WEB/templates/demo-postcard" | grep -q "上传图片" || fail "template detail page"
code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_A" "$WEB/templates/nope")
[ "$code" = "404" ] || fail "unknown template page should 404, got $code"

upload() { # jar template file
  curl -fsS -b "$1" -F "file=@$3" "$WEB/api/assets?templateId=$2" | json "d['id']"
}
create() { # jar template ids_json
  curl -fsS -b "$1" -H 'Content-Type: application/json' \
    -d "{\"templateId\":\"$2\",\"assetIds\":$3}" "$WEB/api/tasks"
}
await() { # jar id -> final json
  for i in $(seq 1 120); do
    t=$(curl -fsS -b "$1" "$WEB/api/tasks/$2")
    s=$(echo "$t" | json "d['status']")
    if [ "$s" = "SUCCEEDED" ] || [ "$s" = "FAILED" ]; then echo "$t"; return 0; fi
    sleep 0.5
  done
  fail "task $2 did not finish"
}

echo "--- success flow"
curl -fsS -X PUT -H 'Content-Type: application/json' \
  -d '{"delaysMs":{"ASSET_CHECK":300,"GENERATE":800,"FINALIZE":300},"failStage":"NONE"}' "$API/api/dev/simulation" >/dev/null
a1=$(upload "$JAR_A" demo-postcard samples/beach.jpg)
a2=$(upload "$JAR_A" demo-postcard samples/pet.png)
created=$(create "$JAR_A" demo-postcard "[$a1,$a2]")
[ "$(echo "$created" | json "d['status']")" = "PENDING" ] || fail "create should return PENDING"
ok_id=$(echo "$created" | json "d['id']")
seen_running=""
for i in $(seq 1 20); do
  st=$(curl -fsS -b "$JAR_A" "$WEB/api/tasks/$ok_id" | json "d['status']+':'+str(d['stage'])")
  case "$st" in RUNNING:*) seen_running="$st"; break;; esac; sleep 0.1
done
[ -n "$seen_running" ] || fail "never observed RUNNING state"
done_json=$(await "$JAR_A" "$ok_id")
[ "$(echo "$done_json" | json "d['status']")" = "SUCCEEDED" ] || fail "expected SUCCEEDED: $done_json"
[ "$(echo "$done_json" | json "','.join(s['stage']+'='+s['status'] for s in d['steps'])")" = \
  "ASSET_CHECK=DONE,GENERATE=DONE,FINALIZE=DONE" ] || fail "steps: $done_json"
curl -fsS -b "$JAR_A" -o /tmp/result.jpg -w '%{content_type}\n' "$WEB/api/tasks/$ok_id/result" | grep -q image/jpeg || fail "result image"

echo "--- injected failure at GENERATE"
curl -fsS -X PUT -H 'Content-Type: application/json' -d '{"failStage":"GENERATE"}' "$API/api/dev/simulation" >/dev/null
a3=$(upload "$JAR_A" art-poster samples/city.jpg)
bad_id=$(create "$JAR_A" art-poster "[$a3]" | json "d['id']")
bad=$(await "$JAR_A" "$bad_id")
[ "$(echo "$bad" | json "d['status']+':'+d['stage']+':'+d['errorCode']")" = "FAILED:GENERATE:SIMULATED_FAILURE" ] || fail "fail flow: $bad"
[ "$(echo "$bad" | json "','.join(s['stage']+'='+s['status'] for s in d['steps'])")" = "ASSET_CHECK=DONE,GENERATE=FAILED" ] || fail "finalize must not run: $bad"
curl -fsS -X PUT -H 'Content-Type: application/json' -d '{"failStage":"NONE"}' "$API/api/dev/simulation" >/dev/null

echo "--- validation"
code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_A" -F "file=@samples/invalid-type.gif" "$WEB/api/assets?templateId=art-poster")
[ "$code" = "400" ] || fail "gif should be rejected, got $code"
code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_A" -F "file=@samples/too-large-for-sticker.png" "$WEB/api/assets?templateId=pet-sticker")
[ "$code" = "400" ] || fail "oversized sticker should be rejected, got $code"

echo "--- isolation"
code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_B" "$WEB/api/tasks/$ok_id")
[ "$code" = "404" ] || fail "bob must not see alice's task, got $code"
[ "$(curl -fsS -b "$JAR_B" "$WEB/api/tasks" | json "len(d['items'])")" = "0" ] || fail "bob history should be empty"
[ "$(curl -fsS -b "$JAR_A" "$WEB/api/tasks" | json "len(d['items'])")" = "2" ] || fail "alice history should have 2"

echo "--- restart boundary (history persisted in MySQL)"
docker compose restart backend >/dev/null
for i in $(seq 1 60); do curl -fsS -o /dev/null "$API/actuator/health" && break; sleep 2; done
[ "$(curl -fsS -b "$JAR_A" "$WEB/api/tasks/$ok_id" | json "d['status']")" = "SUCCEEDED" ] || fail "history lost after restart"

echo "E2E OK"
