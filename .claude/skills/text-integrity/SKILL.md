---
name: text-integrity
description: 샘플 원문 → 저장 → 원고 → docx → 역변환까지 본문 글자가 바뀌지 않았는지 검사한다. 본문 저장·조립·변환 경로의 코드 변경 시 사용.
---

# text-integrity

```bash
bash .claude/skills/text-integrity/scripts/integrity_check.sh [형식.docx]
# 출력: output/integrity/{실행ID}/  종료 코드: 0 전체 일치, 1 불일치, 2 실행 오류
```

- 본문 불변 원칙(CLAUDE.md 절대 원칙 1)의 회귀 검사다. 실패하면 원인 코드를 고치고, 기대값은 바꾸지 않는다.
- `worksheet/samples/*.md` 전부를 학생용·정답지로 만들고(docx-build) 역변환 비교(roundtrip-verify)한다.
- 현재 범위(로드맵 2단계)는 원고 → docx → 역변환이다. 4단계에서 OCR 원문 → 저장 → 원고 조립 구간을 앞에 붙인다.
