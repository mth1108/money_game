#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  캔들 과거 조회 가능 범위 실측  (CLAUDE.md §8 / S0)
#
#  `nextBefore` 를 따라 마지막 페이지까지 걸어가며 경계를 확정한다.
#  (마지막 페이지는 nextBefore = null)
#
#  ── §1.1 준수 ──
#      POST /oauth2/token      토큰 발급
#      GET  /api/v1/candles    캔들 조회
#  이 둘 외에는 호출하지 않는다. 주문·계좌·잔고 엔드포인트는 건드리지 않는다.
#
#  ── 사용법 ── (저장소 루트에서)
#      bash scripts/probe-candle-range.sh 005930 1m
#  자격증명은 .env 에서 읽는다. 커맨드 인자로 넘기지 말 것.
#
#  ── 환경변수 ──
#      RESUME_BEFORE  이 시각부터 이어서 조회 (중단된 지점의 '가장 오래된 봉')
#      MAX_REQUESTS   요청 상한 (기본 3000)
#      SLEEP          요청 간 기본 대기 초 (기본 0.3)
#      MAX_RETRIES    429 재시도 상한 (기본 8)
#      SAVE_CSV       1 이면 data/samples/<symbol>_<interval>.csv 로 저장
#
#  ── 레이트리밋 ──
#  토큰 버킷이다. X-RateLimit-Limit 은 버스트 용량이고 Reset 은 토큰 1개가
#  재충전되기까지의 초다. 연속 요청은 버킷을 말리므로 429 를 받으면
#  Retry-After 만큼 쉬고 지수 백오프로 재시도한다.
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail
export PYTHONIOENCODING=utf-8

BASE="https://openapi.tossinvest.com"
SYMBOL="${1:-005930}"
INTERVAL="${2:-1m}"
MAX_REQUESTS="${MAX_REQUESTS:-3000}"
SLEEP="${SLEEP:-0.3}"
MAX_RETRIES="${MAX_RETRIES:-8}"
SAVE_CSV="${SAVE_CSV:-0}"
RESUME_BEFORE="${RESUME_BEFORE:-}"

case "$INTERVAL" in
  1m|1d) ;;
  *) echo "interval 은 1m 또는 1d 만 가능합니다 (받은 값: $INTERVAL)" >&2; exit 2 ;;
esac

PY="$(command -v python || command -v python3 || true)"
if [ -z "$PY" ]; then echo "python 이 필요합니다" >&2; exit 2; fi

# 저장소 루트의 .env (.gitignore 대상 — 키는 여기에만 둔다)
if [ -f ".env" ]; then set -a; . ./.env; set +a; fi

: "${TOSS_CLIENT_ID:?TOSS_CLIENT_ID 를 .env 에 설정하세요}"
: "${TOSS_CLIENT_SECRET:?TOSS_CLIENT_SECRET 를 .env 에 설정하세요}"

TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT
BODY="$TMPD/body"; HDRS="$TMPD/hdrs"

hdr() { tr -d '\r' < "$HDRS" | grep -i "^$1:" | tail -1 | cut -d' ' -f2- | tr -dc '0-9'; }

PARSE_PAGE='
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print("ERR\tparse-error\t응답을 JSON 으로 파싱할 수 없습니다"); sys.exit(0)
if d.get("error"):
    e = d["error"]
    print("ERR\t%s\t%s" % (e.get("code"), (e.get("message") or "").replace("\t", " ")))
    sys.exit(0)
r  = d.get("result") or {}
cs = r.get("candles") or []
nb = r.get("nextBefore") or ""
print("OK\t%d\t%s\t%s\t%s" % (len(cs),
      cs[0]["timestamp"]  if cs else "",
      cs[-1]["timestamp"] if cs else "", nb))
for c in cs:
    print(",".join([c["timestamp"], c["openPrice"], c["highPrice"],
                    c["lowPrice"], c["closePrice"], c["volume"]]))
'

# ── 토큰 발급 ────────────────────────────────────────────────────────
echo "[1/2] 토큰 발급 — 같은 client_id 의 기존 토큰은 무효화됩니다"
HTTP="$(curl -sS --compressed -o "$BODY" -w '%{http_code}' -X POST "$BASE/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=$TOSS_CLIENT_ID" \
  --data-urlencode "client_secret=$TOSS_CLIENT_SECRET")"
if [ "$HTTP" != "200" ]; then
  echo "      실패 — HTTP $HTTP" >&2
  echo "      응답: $(head -c 300 "$BODY")" >&2
  exit 1
fi
TOKEN="$("$PY" -c 'import sys,json; d=json.load(sys.stdin); print(d["access_token"])' < "$BODY")"
echo "      완료"

# ── 순회 ─────────────────────────────────────────────────────────────
echo "[2/2] $SYMBOL / $INTERVAL — nextBefore 를 따라 과거로 이동"
if [ -n "$RESUME_BEFORE" ]; then echo "      이어받기: $RESUME_BEFORE 부터"; fi

CSV_PATH=""
if [ "$SAVE_CSV" = "1" ]; then
  mkdir -p data/samples
  CSV_PATH="data/samples/${SYMBOL}_${INTERVAL}.csv"
  if [ ! -f "$CSV_PATH" ] || [ -z "$RESUME_BEFORE" ]; then
    echo "timestamp,open,high,low,close,volume" > "$CSV_PATH"
  fi
fi

before="$RESUME_BEFORE"; req=0; total=0; retries=0
newest=""; oldest=""; reason="?"; giveup=0
started="$(date +%s)"

while :; do
  if [ "$((req + 1))" -gt "$MAX_REQUESTS" ]; then
    reason="요청 상한($MAX_REQUESTS) 도달"; break
  fi

  url="$BASE/api/v1/candles?symbol=$SYMBOL&interval=$INTERVAL&count=200&adjusted=true"
  if [ -n "$before" ]; then
    enc="$("$PY" -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$before")"
    url="$url&before=$enc"
  fi

  # 429 재시도 루프
  attempt=0
  while :; do
    code="$(curl -sS --compressed -D "$HDRS" -o "$BODY" -w '%{http_code}' \
                 -H "Authorization: Bearer $TOKEN" "$url")"
    if [ "$code" != "429" ]; then break; fi
    attempt="$((attempt + 1))"; retries="$((retries + 1))"
    if [ "$attempt" -gt "$MAX_RETRIES" ]; then
      reason="429 재시도 ${MAX_RETRIES}회 초과"; giveup=1; break
    fi
    ra="$(hdr 'Retry-After')"; ra="${ra:-0}"
    back="$((1 << (attempt - 1)))"
    wait="$(( ra > back ? ra : back ))"
    if [ "$wait" -gt 60 ]; then wait=60; fi
    wait="$(( wait + RANDOM % 2 ))"
    printf '  [429] %d회차 재시도 — %d초 대기 (Retry-After=%s)\n' "$attempt" "$wait" "${ra:-?}"
    sleep "$wait"
  done
  if [ "$giveup" = "1" ]; then break; fi

  req="$((req + 1))"
  parsed="$("$PY" -c "$PARSE_PAGE" < "$BODY")"
  meta="$(printf '%s' "$parsed" | head -1)"
  IFS="$(printf '\t')" read -r status f1 f2 f3 f4 <<< "$meta"

  if [ "$status" = "ERR" ]; then
    echo "  [API 오류] code=$f1  message=${f2:-}" >&2
    reason="API 오류 ($f1)"; req="$((req - 1))"; break
  fi
  if [ "$f1" -eq 0 ]; then
    reason="빈 응답 — 더 이상 과거 봉이 없습니다"; req="$((req - 1))"; break
  fi

  if [ -z "$newest" ]; then newest="$f2"; fi
  oldest="$f3"
  total="$((total + f1))"

  if [ -n "$CSV_PATH" ]; then printf '%s\n' "$parsed" | tail -n +2 >> "$CSV_PATH"; fi

  remain="$(hdr 'X-RateLimit-Remaining')"
  if [ "$((req % 20))" -eq 0 ]; then
    printf '  %4d회 / %7d봉 — %s (버킷 잔량 %s)\n' "$req" "$total" "$oldest" "${remain:-?}"
  fi

  if [ -z "${f4:-}" ]; then
    reason="nextBefore = null — 마지막 페이지 (경계 확정)"; break
  fi
  before="$f4"

  # 버킷 잔량이 적으면 선제적으로 감속 (문서 권장)
  if [ -n "$remain" ] && [ "$remain" -le 3 ]; then sleep 2; else sleep "$SLEEP"; fi
done

# ── 결과 ─────────────────────────────────────────────────────────────
elapsed="$(( $(date +%s) - started ))"
echo
echo "══════════════════════════════════════════════════"
echo "  종목           : $SYMBOL"
echo "  단위           : $INTERVAL"
echo "  가장 최근 봉   : ${newest:-(없음)}"
echo "  가장 오래된 봉 : ${oldest:-(없음)}"
echo "  총 봉 수       : $total"
echo "  요청 수        : $req   (429 재시도 $retries회)"
echo "  소요           : ${elapsed}초"
echo "  종료 사유      : $reason"
if [ -n "$CSV_PATH" ]; then echo "  CSV            : $CSV_PATH"; fi
if [ -n "$oldest" ] && [ -n "$newest" ]; then
  "$PY" - "$oldest" "$newest" <<'PY2'
import sys
from datetime import datetime
o = datetime.fromisoformat(sys.argv[1]); n = datetime.fromisoformat(sys.argv[2])
d = (n - o).days
print("  구간 길이      : %d일 (약 %.1f개월)" % (d, d / 30.4))
PY2
fi
if [ "$reason" != "nextBefore = null — 마지막 페이지 (경계 확정)" ] && [ -n "$oldest" ]; then
  echo
  echo "  경계에 도달하지 못했습니다. 이어서 돌리려면:"
  echo "    RESUME_BEFORE='$oldest' bash scripts/probe-candle-range.sh $SYMBOL $INTERVAL"
fi
echo "══════════════════════════════════════════════════"
