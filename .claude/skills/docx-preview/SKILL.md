---
name: docx-preview
description: docx를 PDF → PNG로 렌더링해 배치를 눈으로 확인한다. 서식·배치 변경 후 사용.
---

# docx-preview

```bash
bash .claude/skills/docx-preview/scripts/render_preview.sh 출력폴더/preview a.docx [b.docx ...]
# → preview/{이름}.pdf, preview/{이름}-p{쪽}.png (110dpi)
```

- `docx_to_pdf.ps1`: Microsoft Word COM으로 docx를 읽기 전용으로 열어 PDF로 저장한다(Word 수식이 정확히 그려진다). 한 파일에 수 초~수십 초.
- `pdf_to_png.py`: PyMuPDF(`import pymupdf`)로 쪽마다 PNG. 세 번째 인자로 dpi를 바꾼다.
- LibreOffice는 쓰지 않는다(Word 수식을 제대로 그리지 못하고, 학원 PC에도 Word가 있다).
- 실패하면(Word 없음, COM 오류, PyMuPDF 없음) 그 파일은 건너뛰고 `preview/SKIPPED.txt`에 사유를 남긴다. 종료 코드는 0이다. summary.md에 사유를 옮겨 적는다.
- Word 창이 남아 있으면 작업 관리자에서 WINWORD.EXE를 닫고 다시 실행한다.

## 볼 것(배치 평가)

2단·구분선, 첫 쪽/다음 쪽 머리말, 꼬리말 쪽 번호, 보기 배치(Choices5/3/1)와 넘침, 풀이 공간 차이, 묶음 문제가 갈라지지 않는지, 표가 단 안에 들어가는지, 그림 크기(cm), 정답지의 정답표·빨간 표시.
