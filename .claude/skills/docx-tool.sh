#!/usr/bin/env bash
# 스킬 스크립트 공용: 백엔드 Java CLI(WorksheetCli)를 Gradle로 부른다. source 해서 쓴다.
#   docx_tool <build|verify|lint> --옵션 값 ...
# CLI가 마지막 줄에 찍는 exit=N을 이 함수의 종료 코드로 돌려준다.

abs_path() {
  local dir
  dir="$(cd "$(dirname "$1")" && (pwd -W 2>/dev/null || pwd))"
  echo "$dir/$(basename "$1")"
}

docx_tool() {
  local root args="" a output code
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  # Gradle --args는 공백으로 나누므로 값마다 작은따옴표로 감싼다
  for a in "$@"; do args="$args '$a'"; done
  # Gradle 클라이언트가 CLI 출력을 자기 기본 인코딩(Windows는 CP949)으로 다시 찍으므로 UTF-8로 맞춘다
  output="$(cd "$root/backend" && JAVA_OPTS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8" ./gradlew -q --console=plain docxTool --args="$args" 2>&1)"
  echo "$output" | grep -v '^exit=[0-9]*$'
  code="$(echo "$output" | sed -n 's/^exit=\([0-9]*\)$/\1/p' | tail -1)"
  return "${code:-2}"
}
