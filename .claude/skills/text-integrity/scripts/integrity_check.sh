#!/usr/bin/env bash
# 본문 불변 회귀 검사(CLAUDE.md 절대 원칙 1): 샘플 원고마다 학생용·정답지를 만들고
# 역변환 비교로 문제 본문 글자가 한 글자도 바뀌지 않았는지 확인한다.
# 현재 범위(로드맵 2단계): 원고 → docx → 역변환. 4단계에서 OCR 원문 → 저장 → 원고 조립 구간을 앞에 붙인다.
#
# 사용: bash .claude/skills/text-integrity/scripts/integrity_check.sh [형식.docx]
# 출력: /output/integrity/{실행ID}/  종료 코드: 0 전체 일치, 1 불일치, 2 실행 오류
set -u
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
TEMPLATE="${1:-$ROOT/worksheet/templates/naesin-2col.docx}"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/output/integrity/$RUN_ID"
mkdir -p "$OUT"

status=0
for md in "$ROOT"/worksheet/samples/*.md; do
  name="$(basename "$md" .md)"
  bash "$ROOT/.claude/skills/docx-build/scripts/build_docx.sh" "$md" "$OUT" "$TEMPLATE" > "$OUT/$name-build.log" 2>&1 || { echo "생성 실패: $name"; status=2; continue; }
  for kind in student answers; do
    echo "== $name ($kind)"
    bash "$ROOT/.claude/skills/roundtrip-verify/scripts/roundtrip.sh" "$md" "$OUT/$name-$kind.docx" "$OUT/$name-$kind-verify.json"
    code=$?
    [ $code -gt $status ] && status=$code
  done
done
echo "결과: $OUT (종료 코드 $status)"
exit $status
