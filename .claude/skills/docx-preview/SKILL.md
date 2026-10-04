---
name: docx-preview
description: docx를 PDF → PNG로 렌더링해 배치를 눈으로 확인한다. 서식·배치 변경 후 사용.
---

# docx-preview

- 스크립트: `scripts/render_preview.sh`(LibreOffice headless → PDF → PNG)
- 출력: `/output/docx/{실행ID}/preview/*.png`
- 한계: LibreOffice가 Word 수식을 제대로 그리지 못할 수 있다. 렌더링이 안 되면 건너뛰고 사유를 로그에 남긴다(스킵 + 로그).

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
