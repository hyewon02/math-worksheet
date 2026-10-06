#!/usr/bin/env bash
# docx → PDF(Word COM) → PNG(PyMuPDF) 미리보기
#
# 사용법:
#   bash .claude/skills/docx-preview/scripts/render_preview.sh 출력폴더 a.docx [b.docx ...]
# 결과:
#   출력폴더/{이름}.pdf, 출력폴더/{이름}-p{쪽}.png
# 렌더링이 환경 문제(Word 없음, COM 실패, PyMuPDF 없음)로 안 되면 그 파일은 건너뛰고
# 사유를 출력폴더/SKIPPED.txt에 남긴다. 종료 코드는 0(선택적 확인이므로 빌드를 막지 않는다).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ $# -lt 2 ]; then
  echo "사용법: $0 출력폴더 a.docx [b.docx ...]" >&2
  exit 2
fi
OUT="$1"; shift
mkdir -p "$OUT"

winpath() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
PY="$(command -v py || command -v python3 || command -v python)"

skip() {
  echo "[건너뜀] $1: $2" | tee -a "$OUT/SKIPPED.txt" >&2
}

for DOCX in "$@"; do
  NAME="$(basename "$DOCX" .docx)"
  PDF="$OUT/$NAME.pdf"
  if ! powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(winpath "$HERE/docx_to_pdf.ps1")" \
       -Docx "$(winpath "$DOCX")" -Pdf "$(winpath "$PDF")"; then
    skip "$DOCX" "Word COM으로 PDF를 만들지 못함(Word 미설치 또는 COM 오류)"
    continue
  fi
  if [ -z "$PY" ] || ! "$PY" "$HERE/pdf_to_png.py" "$PDF" "$OUT"; then
    skip "$DOCX" "PDF → PNG 변환 실패(PyMuPDF 확인)"
    continue
  fi
done
exit 0
