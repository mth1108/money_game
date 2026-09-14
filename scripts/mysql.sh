#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  application-local.yml 의 접속 정보로 MySQL 클라이언트를 연다.
#
#  포트와 계정을 손으로 적지 않는다. 이 저장소에서 3306 을 가정했다가
#  거기 떠 있던 MariaDB 에 붙어 인증 오류로 시간을 쓴 적이 있다.
#  설정 파일이 유일한 출처다.
#
#  비밀번호는 커맨드라인에 올리지 않는다. 권한 600 의 임시 파일을 만들어
#  --defaults-extra-file 로 넘기고 종료 시 지운다.
#
#  사용법 (저장소 루트에서)
#      bash scripts/mysql.sh                        대화형 접속
#      bash scripts/mysql.sh -e "SHOW TABLES"       한 줄 실행
#      bash scripts/mysql.sh < backend/src/main/resources/db/schema.sql
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

CONF="backend/src/main/resources/application-local.yml"
if [ ! -f "$CONF" ]; then
  echo "설정 파일이 없습니다: $CONF" >&2
  echo "toss.api.* 와 spring.datasource.* 를 채워주세요. (.gitignore 대상)" >&2
  exit 2
fi

PY="$(command -v python || command -v python3 || true)"
if [ -z "$PY" ]; then echo "python 이 필요합니다" >&2; exit 2; fi

MYSQL="${MYSQL_BIN:-}"
if [ -z "$MYSQL" ]; then
  MYSQL="$(command -v mysql || true)"
fi
if [ -z "$MYSQL" ]; then
  MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
fi
if [ ! -x "$MYSQL" ] && ! command -v "$MYSQL" >/dev/null 2>&1; then
  echo "mysql 클라이언트를 찾지 못했습니다. MYSQL_BIN 으로 경로를 지정하세요." >&2
  exit 2
fi

CNF="$(mktemp)"
trap 'rm -f "$CNF"' EXIT

"$PY" - "$CONF" "$CNF" <<'PYEOF'
import os, re, sys
conf, out = sys.argv[1], sys.argv[2]
cfg = {}
for line in open(conf, encoding="utf-8"):
    for key in ("password", "username", "url"):
        m = re.match(rf"\s*{key}:\s*(.*)$", line)
        if m:
            cfg[key] = m.group(1).strip()
if "url" not in cfg:
    sys.exit("spring.datasource.url 이 없습니다")
m = re.search(r"//([^:/]+):(\d+)/(\w+)", cfg["url"])
if not m:
    sys.exit("url 에서 host:port/db 를 읽지 못했습니다: " + cfg["url"])
host, port, db = m.groups()
fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as f:
    f.write("[client]\n")
    f.write(f"host={host}\nport={port}\n")
    f.write(f"user={cfg.get('username', 'root')}\n")
    if cfg.get("password"):
        f.write(f"password={cfg['password']}\n")
    f.write(f"database={db}\n")
    f.write("default-character-set=utf8mb4\n")
sys.stderr.write(f"접속: {host}:{port}/{db} (user={cfg.get('username','root')})\n")
PYEOF

exec "$MYSQL" --defaults-extra-file="$CNF" "$@"
