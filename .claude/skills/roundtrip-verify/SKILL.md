---
name: roundtrip-verify
description: 생성한 docx를 마크다운으로 역변환해 원고와 문제별로 비교한다. docx-build 실행 직후 항상, 정규화 규칙 변경 시 사용.
---

# roundtrip-verify

```bash
bash .claude/skills/roundtrip-verify/scripts/roundtrip.sh 원고.md 결과.docx output/docx/{실행ID}/verify.json
# 종료 코드: 0 전체 일치, 1 불일치 있음, 2 실행 오류
```

- 앱과 같은 Java 로직(`RoundtripVerifier`)을 CLI로 부른다. 스크립트에 비교 로직을 따로 두지 않는다.
- 원고와 결과를 `pandoc -t json`으로 AST로 읽어 문제별로 자른다. 결과 쪽은 `docx+styles`가 남긴 `custom-style`(ProblemNumber가 문제 시작, GroupStem이 묶음 지문)로 자른다.
- 비교에서 빼는 것: 번호(ProblemNumber), `<보 기>` 제목(BogiTitle), 정답 줄·정답표(Answer, AnswerTable), 원고의 `answer` 구역, 그림, 풀이 공간(빈 단락).
- 정규화(비교용만, `ComparisonNormalizer`): 공백·폭 없는 공백(U+200B) 제거, `\dfrac`→`\frac`, Word를 거친 `\left\{\begin{matrix}…\right.`→`cases`, `\left`/`\right` 제거, 글자 하나를 감싼 중괄호 제거.
- 수식 개수도 문제별로 같아야 일치다.
- 출력 `verify.json`: 문제별 `key`(번호 또는 `묶음@첫번호`), `matched`, `expectedMath`/`actualMath`, `differences`(앞 글자 `before`, 원고 `expected`, 결과 `actual`).
- **불일치를 정규화 규칙을 넓혀 덮지 않는다.** 규칙 추가는 사용자 확인 후 설계서 8장 목록과 `ComparisonNormalizer`에 함께 적는다.
