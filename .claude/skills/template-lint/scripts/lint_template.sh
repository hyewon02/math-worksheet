#!/usr/bin/env bash
# 형식 파일 검사: 앱의 등록 검사와 같은 Java 로직(TemplateInspector)을 CLI로 부른다.
# 사용: lint_template.sh <형식.docx>   종료 코드: 0 통과, 1 실패, 2 실행 오류
set -u
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
[ $# -eq 1 ] || { echo "사용: $0 <형식.docx>"; exit 2; }
source "$ROOT/.claude/skills/docx-tool.sh"
docx_tool lint --template "$(abs_path "$1")"
