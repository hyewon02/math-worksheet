---
name: template-lint
description: 형식 파일(.docx)에 필수 스타일 13개와 빈칸 4개가 있는지 검사한다. 형식 파일 생성·수정, 등록 검사 로직 변경 시 사용.
---

# template-lint

- 스크립트: `scripts/lint_template.py`
- 필수 스타일(설계서 8장): Problem, ProblemNumber, GroupStem, Choices5, Choices3, Choices1, BogiTitle, BogiBox, Figure, Table, Answer, CorrectChoice, AnswerTable
- 빈칸: `{{학원명}}`, `{{시험제목}}`, `{{날짜}}`, `{{문제지번호}}`(Word가 글자를 쪼개 저장했는지도 확인)
- 실패 메시지는 외부 AI에 그대로 붙여 넣을 수 있는 문장으로 쓴다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
