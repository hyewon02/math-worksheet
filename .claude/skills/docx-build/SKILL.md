---
name: docx-build
description: 형식 파일을 만들고 원고(마크다운) → Pandoc → 빈칸 채우기로 문제지·정답지 docx를 생성한다. 형식 파일·Lua 필터·원고 조립 규칙 변경 시 사용.
---

# docx-build

- 스크립트: `scripts/build_template.py`, `scripts/build_docx.sh`, `scripts/fill_placeholders.py`
- 명령: `pandoc -f markdown+tex_math_single_backslash --reference-doc=형식.docx --lua-filter=worksheet/filters/worksheet.lua -o out.docx in.md` (정답지는 `-M answers=true`)
- 참고: `references/`에 Pandoc 옵션, OOXML 스타일·탭·단락 유지 메모, Lua 필터 규칙(보기 너비 9/15칸 기준, 풀이 공간, 그림 cm 크기, 표 열 너비 44%)
- 출력: `/output/docx/{실행ID}/*.docx`. 빈칸 `{{학원명}}`, `{{시험제목}}`, `{{날짜}}`, `{{문제지번호}}`가 남지 않아야 한다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
