---
name: template-lint
description: 형식 파일(.docx)에 필수 스타일 13개와 빈칸 4개가 있는지 검사한다. 형식 파일 생성·수정, 등록 검사 로직 변경 시 사용.
---

# template-lint

```bash
bash .claude/skills/template-lint/scripts/lint_template.sh worksheet/templates/naesin-2col.docx
# 종료 코드: 0 통과, 1 실패, 2 실행 오류
```

- 앱의 형식 등록 검사와 같은 Java 로직(`TemplateInspector`)을 CLI로 부른다.
- 필수 스타일(설계서 8장): Problem, ProblemNumber(문자), GroupStem, Choices5, Choices3, Choices1, BogiTitle, BogiBox, Figure, Table(표), Answer, CorrectChoice(문자), AnswerTable(표). 이름과 종류를 모두 확인한다.
- 빈칸: 머리말·꼬리말에 `{{학원명}}`, `{{시험제목}}`, `{{날짜}}`, `{{문제지번호}}`. 글자가 run 여러 개로 쪼개져 있으면 "주의"(채우기는 정상 동작).
- 오류 문장은 선생님이 외부 AI에 그대로 붙여 넣을 수 있는 완결된 문장이다.
