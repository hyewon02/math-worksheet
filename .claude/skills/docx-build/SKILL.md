---
name: docx-build
description: 형식 파일을 만들고 원고(마크다운) → Pandoc → 빈칸 채우기로 문제지·정답지 docx를 생성한다. 형식 파일·Lua 필터·원고 조립 규칙 변경 시 사용.
---

# docx-build

원고 규격은 [docs/manuscript-format.md](../../../docs/manuscript-format.md), 서식 규격은 설계서 8장·9장.

## 1. 형식 파일 만들기(재생성)

```bash
py .claude/skills/docx-build/scripts/build_template.py            # → worksheet/templates/naesin-2col.docx
py .claude/skills/docx-build/scripts/build_template.py 다른경로.docx
```

- Pandoc 기본 reference.docx에서 시작해 styles.xml·settings.xml·document.xml(스타일 견본 + 구역 설정)·머리말 2개·꼬리말 2개를 바꿔 끼운다. 표준 라이브러리만 쓴다.
- 치수(여백, 단 너비, 번호 칸, 보기 탭 위치)는 스크립트 위쪽 상수에 있다. 바꾸면 `worksheet.lua`의 `BODY_TWIP`, `COLUMN_TWIP`도 맞춘다.
- 형식 파일은 손으로 Word에서 고치지 말고 스크립트를 고쳐 다시 만든다(빈칸이 run 여러 개로 쪼개지는 것을 막기 위해).

## 2. 문제지·정답지 만들기

```bash
bash .claude/skills/docx-build/scripts/build_docx.sh 원고.md 출력폴더 [형식파일.docx]
# → 출력폴더/{원고}-student.docx, 출력폴더/{원고}-answers.docx
```

앱과 같은 Java 로직(`WorksheetRenderer`: Pandoc 실행 + Apache POI 빈칸 채우기)을 CLI(`./gradlew docxTool`)로 부른다. 내부 Pandoc 명령:

```bash
pandoc -f markdown+tex_math_single_backslash \
  --reference-doc=형식.docx --lua-filter=worksheet/filters/worksheet.lua --resource-path=원고폴더 \
  -M choices5-max=5 -M choices3-max=10 [-M answers=true] -o out.docx 원고.md
```

- 보기 배치 기준(`choices5-max`, `choices3-max`)과 유형별 풀이 공간은 형식 파일 옆 같은 이름의 `.json`(형식 설정)에서 읽는다. 없으면 설계서 기본값 9/15.
- 빈칸 4개가 모두 채워져야 종료 코드 0이다. 남으면 1.
- 산출물은 `output/docx/{실행ID}/`에 둔다. 만든 직후 roundtrip-verify, 배치를 바꿨으면 docx-preview.

## 3. Lua 필터 요약 (`worksheet/filters/worksheet.lua`)

| 대상 | 결과 |
| --- | --- |
| problem | 첫 단락 앞 `[N.]{ProblemNumber}` + Word 탭, 단락은 `Problem`. 블록 수식 뒤 이어지는 단락 앞에도 탭. 끝에 `space` 높이의 빈 단락(줄 높이 고정) |
| choices | 줄마다 보기 1개. 가장 긴 보기 너비 ≤choices5-max `Choices5`, ≤choices3-max `Choices3`(3+2), 그 외 `Choices1`(기준은 형식 설정). 한 줄 = 한 단락, 보기 사이는 Word 탭 |
| bogi | `BogiTitle`("<보 기>") + 줄마다 `BogiBox` 단락(테두리가 이어져 한 상자) |
| 그림만 있는 단락 | `Figure` |
| 표 | 열 너비 = 본문 44% / 열 수(Word가 비율을 단 너비에 곱하므로 단 기준 0.934로 환산) |
| group | 첫 단락 앞 `[9~10]{ProblemNumber}`, `GroupStem`. 마지막 하위 문제 전까지 빈 공간 단락에도 keepNext |
| answer | 학생용: 제거. 정답지: `CorrectChoice`(정답 보기), `Answer` "정답 ②" / "정답 미입력 · 출처: …", 첫 쪽 `AnswerTable` + 쪽 나눔 |

자세한 OOXML 메모: [references/ooxml-notes.md](references/ooxml-notes.md)
