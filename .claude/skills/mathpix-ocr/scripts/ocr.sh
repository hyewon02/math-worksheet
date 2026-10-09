#!/usr/bin/env bash
# Mathpix 호출: 앱과 같은 Java 코드(MathpixOcrEngine)를 CLI로 부른다. 키는 backend/application-local.yml에서 읽고 화면에는 가린다.
# 사용:
#   ocr.sh check <사진> <출력폴더>              키 확인 + 1장 호출
#   ocr.sh batch <사진폴더> <출력폴더> <최대장수>  최대 N장 호출(이미 처리한 사진은 저장본 사용, 요금 없음)
# 응답 원문: <출력폴더>/raw/{사진이름}.json   종료 코드: 0 정상, 2 오류
set -u
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
source "$ROOT/.claude/skills/docx-tool.sh"
case "${1:-}" in
  check) ocr_tool check --image "$(abs_path "$2")" --out "$(abs_path "$3")" ;;
  batch) mkdir -p "$3"; ocr_tool batch --input "$(abs_path "$2")" --out "$(abs_path "$3")" --limit "${4:-1}" ;;
  *) echo "사용: $0 check <사진> <출력폴더> | batch <사진폴더> <출력폴더> <최대장수>"; exit 2 ;;
esac
