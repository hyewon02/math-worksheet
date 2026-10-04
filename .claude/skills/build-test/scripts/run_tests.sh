#!/usr/bin/env bash
# 백엔드·프론트엔드 빌드와 테스트, 결과 요약을 /output/tests/{실행ID}.md에 남긴다.
set -u

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT/output/tests"
REPORT="$OUT_DIR/$RUN_ID.md"
mkdir -p "$OUT_DIR"

backend_status="성공"
frontend_status="성공"

echo "# 빌드·테스트 결과 $RUN_ID" > "$REPORT"

echo "== 백엔드 테스트 =="
if ! (cd "$ROOT/backend" && ./gradlew test --console=plain) > "$OUT_DIR/$RUN_ID-backend.log" 2>&1; then
  backend_status="실패"
fi
tail -5 "$OUT_DIR/$RUN_ID-backend.log"

echo "== 프론트엔드 빌드 =="
if ! (cd "$ROOT/frontend" && npm run build) > "$OUT_DIR/$RUN_ID-frontend.log" 2>&1; then
  frontend_status="실패"
fi
tail -5 "$OUT_DIR/$RUN_ID-frontend.log"

{
  echo
  echo "| 대상 | 결과 | 로그 |"
  echo "| --- | --- | --- |"
  echo "| 백엔드 테스트 | $backend_status | $RUN_ID-backend.log |"
  echo "| 프론트엔드 빌드 | $frontend_status | $RUN_ID-frontend.log |"
  if [ -d "$ROOT/backend/build/test-results/test" ]; then
    echo
    echo "실패한 테스트:"
    grep -l "<failure" "$ROOT"/backend/build/test-results/test/*.xml 2>/dev/null | xargs -r -n1 basename || true
  fi
} >> "$REPORT"

echo "요약: $REPORT"
[ "$backend_status" = "성공" ] && [ "$frontend_status" = "성공" ]
