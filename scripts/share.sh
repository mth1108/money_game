#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  임시 공개 — 이 맥에서 게임을 띄우고 Cloudflare 임시 터널로 밖에 연다.
#  (CLAUDE.md §4 「임시 공개」, §9-12)
#
#      브라우저 ─https─▶ *.trycloudflare.com ─▶ cloudflared
#                         ─▶ vite preview :4173 (dist + /api·/ws 프록시)
#                         ─▶ 백엔드 127.0.0.1:8080 (share 프로파일)
#
#  - 주소는 실행할 때마다 바뀐다. 터미널에 찍힌 https 주소를 나눠 준다
#  - 이 스크립트가 떠 있는 동안만 열린다. Ctrl+C 로 셋 다 내린다
#  - 인증이 없다 (CLAUDE.md §3 M6). 주소를 아는 사람은 누구나 남의 닉네임으로 들어온다
#  - 개발 서버(npm run dev)가 아니라 빌드 결과만 내보낸다. 개발 서버는 소스를 내보낸다
#
#  사용법 (저장소 루트에서)
#      bash scripts/share.sh                         1틱 = 1초
#      bash scripts/share.sh --room.tick-millis=500  뒤의 인자는 백엔드에 그대로 넘긴다
#
#  필요한 것: MySQL 실행 중, cloudflared (brew install cloudflared), nvm Node
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$(mktemp -d)"

command -v cloudflared >/dev/null || { echo "cloudflared 가 없습니다: brew install cloudflared" >&2; exit 2; }
if ! command -v npm >/dev/null; then
  # 비대화형 셸에서는 nvm 이 PATH 에 없다
  # shellcheck disable=SC1090
  . "$HOME/.nvm/nvm.sh"
fi
if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1 || lsof -nP -iTCP:4173 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "8080 또는 4173 포트를 이미 쓰고 있습니다. 띄워 둔 백엔드·프론트를 먼저 내리세요." >&2
  exit 2
fi

PIDS=()
cleanup() {
  trap - EXIT INT TERM
  echo
  echo "내리는 중..."
  for pid in ${PIDS[@]+"${PIDS[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
  rm -rf "$LOG_DIR"
}
trap cleanup EXIT INT TERM

wait_for() {  # wait_for <설명> <URL> <로그>
  for _ in $(seq 1 90); do
    if curl -fsS -o /dev/null "$2" 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "$1 이(가) 뜨지 않았습니다. 로그:" >&2
  tail -30 "$3" >&2
  exit 1
}

echo "[1/4] 빌드"
(cd "$ROOT/backend" && ./gradlew -q bootJar)
(cd "$ROOT/frontend" && npm run -s build >/dev/null)
JAR="$(ls "$ROOT"/backend/build/libs/*.jar | grep -v -- '-plain' | head -1)"

echo "[2/4] 백엔드 (share 프로파일)"
# csv-dir 등 상대 경로는 backend/ 기준이다
(cd "$ROOT/backend" && exec java -jar "$JAR" --spring.profiles.active=share "$@") >"$LOG_DIR/backend.log" 2>&1 &
PIDS+=($!)
wait_for "백엔드" "http://127.0.0.1:8080/api/rooms/options" "$LOG_DIR/backend.log"

echo "[3/4] 프론트 (vite preview :4173)"
(cd "$ROOT/frontend" && exec ./node_modules/.bin/vite preview) >"$LOG_DIR/frontend.log" 2>&1 &
PIDS+=($!)
wait_for "프론트" "http://localhost:4173/api/rooms/options" "$LOG_DIR/frontend.log"

echo "[4/4] 터널"
cloudflared tunnel --no-autoupdate --url http://localhost:4173 >"$LOG_DIR/tunnel.log" 2>&1 &
PIDS+=($!)
URL=""
for _ in $(seq 1 60); do
  URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG_DIR/tunnel.log" | head -1 || true)"
  [ -n "$URL" ] && break
  sleep 1
done
[ -n "$URL" ] || { echo "터널 주소를 받지 못했습니다. 로그:" >&2; tail -30 "$LOG_DIR/tunnel.log" >&2; exit 1; }

echo
echo "  ┌──────────────────────────────────────────────────────────"
echo "  │  공개 주소  $URL"
echo "  │  내 맥에서  http://localhost:4173"
echo "  │  Ctrl+C 로 내립니다. 주소가 처음 열리기까지 30초쯤 걸릴 수 있습니다"
echo "  └──────────────────────────────────────────────────────────"
echo
echo "로그: $LOG_DIR"

# 셋 중 하나라도 죽으면 전부 내린다
while true; do
  for pid in "${PIDS[@]}"; do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "프로세스 $pid 가 종료됐습니다. 로그:" >&2
      tail -20 "$LOG_DIR"/*.log >&2
      exit 1
    fi
  done
  sleep 2
done
