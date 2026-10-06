#!/usr/bin/env bash
# 원고(마크다운) → Pandoc(형식 파일 + Lua 필터) → 빈칸 채우기 → 학생용 문제지·정답지 docx
# 앱과 같은 Java 로직(WorksheetRenderer)을 CLI로 부른다. 로직이 스크립트와 Java 두 벌로 갈라지지 않게 하기 위해서다.
#
# 사용법:
#   bash .claude/skills/docx-build/scripts/build_docx.sh 원고.md 출력폴더 [형식파일.docx]
# 결과:
#   출력폴더/{원고이름}-student.docx   학생용(정답 없음)
#   출력폴더/{원고이름}-answers.docx   정답지(-M answers=true, 제목 뒤 "(정답)")
# 종료 코드: 0 정상, 1 남은 빈칸 있음, 2 실행 오류
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
source "$ROOT/.claude/skills/docx-tool.sh"

if [ $# -lt 2 ]; then
  echo "사용법: $0 원고.md 출력폴더 [형식파일.docx]" >&2
  exit 2
fi

MD="$1"
OUT="$2"
TEMPLATE="${3:-$ROOT/worksheet/templates/naesin-2col.docx}"
FILTER="$ROOT/worksheet/filters/worksheet.lua"

[ -f "$MD" ] || { echo "원고가 없습니다: $MD" >&2; exit 2; }
[ -f "$TEMPLATE" ] || { echo "형식 파일이 없습니다: $TEMPLATE (build_template.py로 생성)" >&2; exit 2; }

mkdir -p "$OUT"
NAME="$(basename "$MD" .md)"
COMMON=(--manuscript "$(abs_path "$MD")" --template "$(abs_path "$TEMPLATE")" --filter "$(abs_path "$FILTER")"
        --title "샘플 문제지" --academy "샘플 수학학원" --number "WS-0000")

status=0
docx_tool build "${COMMON[@]}" --out "$(abs_path "$OUT")/$NAME-student.docx" || status=$?
docx_tool build "${COMMON[@]}" --out "$(abs_path "$OUT")/$NAME-answers.docx" --answers || status=$?
exit $status
