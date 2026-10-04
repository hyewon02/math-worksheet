---
name: roundtrip-verify
description: 생성한 docx를 마크다운으로 역변환해 원고와 문제별로 비교한다. docx-build 실행 직후 항상, 정규화 규칙 변경 시 사용.
---

# roundtrip-verify

- 스크립트: `scripts/roundtrip.py`
- 명령: `pandoc -f docx+styles -t markdown`
- 정규화(비교용만): 공백 제거, `\dfrac`→`\frac`, `\left`/`\right` 제거, 불필요한 중괄호 제거. 번호·탭·정답 줄·정답표·풀이 공간은 비교에서 뺀다.
- 출력: `/output/docx/{실행ID}/verify.json`(문제별 일치 여부, 차이, 수식 개수)
- 불일치를 정규화 규칙을 넓혀 덮지 않는다. 원인이 불확실하면 사용자에게 보고한다.
- 4단계에서 Java로 옮긴 뒤에는 백엔드 CLI를 호출하도록 바꾼다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
