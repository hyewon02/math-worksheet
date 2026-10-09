#!/usr/bin/env bash
# 문제 나누기 정확도: 앱과 같은 Java 코드(ProblemSplitter)로 원문을 나누고 라벨과 비교한다.
# 사용: split_eval.sh <원문폴더(raw)> <split.json> [라벨폴더(기본 samples/labels)]
# 종료 코드: 0 모든 사진 일치, 1 불일치 있음, 2 오류
set -u
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
source "$ROOT/.claude/skills/docx-tool.sh"
[ $# -ge 2 ] || { echo "사용: $0 <원문폴더> <split.json> [라벨폴더]"; exit 2; }
LABELS="${3:-$ROOT/samples/labels}"
ocr_tool split --raw "$(abs_path "$1")" --out "$(abs_path "$2")" --labels "$(abs_path "$LABELS")"
