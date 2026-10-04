---
name: text-integrity
description: 샘플 원문 → 저장 → 원고 → docx → 역변환까지 본문 글자가 바뀌지 않았는지 검사한다. 본문 저장·조립·변환 경로의 코드 변경 시 사용.
---

# text-integrity

- 스크립트: `scripts/integrity_check.sh`
- 본문 불변 원칙(CLAUDE.md 절대 원칙 1)의 회귀 검사다. 실패하면 원인 코드를 고치고, 기대값은 바꾸지 않는다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
