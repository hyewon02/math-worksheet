#!/usr/bin/env bash
# 역변환 비교: 앱과 같은 Java 로직(RoundtripVerifier)을 CLI로 부른다.
# 사용: roundtrip.sh <원고.md> <결과.docx> <verify.json>
# 종료 코드: 0 전체 일치, 1 불일치 있음, 2 실행 오류
set -u
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
[ $# -eq 3 ] || { echo "사용: $0 <원고.md> <결과.docx> <verify.json>"; exit 2; }
source "$ROOT/.claude/skills/docx-tool.sh"
mkdir -p "$(dirname "$3")"
docx_tool verify --manuscript "$(abs_path "$1")" --docx "$(abs_path "$2")" --report "$(abs_path "$3")"
