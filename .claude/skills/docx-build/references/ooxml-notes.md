# OOXML 메모 (형식 파일·Lua 필터)

## 단위

- twip = 1/1440 inch, 1cm = 567twip. A4 = 11906 × 16838.
- 여백 1020(1.8cm) → 본문 9866(17.4cm). 2단 간격 567 → 단 4649(8.2cm).
- 번호 칸(내어쓰기) 425(0.75cm). 보기 너비 = 4649 − 425.

## 스타일

- Pandoc의 `custom-style="이름"`은 형식 파일 styles.xml의 **스타일 이름**으로 찾는다. 이름 = ID로 맞춰 두었다.
- 단락 스타일: `<w:style w:type="paragraph" w:customStyle="1" w:styleId="Problem">`. 문자 스타일: `w:type="character"`, 표 스타일: `w:type="table"`.
- pPr 자식 순서는 스키마 순서를 지킨다: keepNext, keepLines, pBdr, tabs, autoSpaceDE/DN, spacing, ind, contextualSpacing, jc.
- `Table`은 Pandoc이 모든 표에 붙이는 기본 표 스타일이다. 기존 정의를 지우고 전체 테두리·가운데 정렬로 바꿔 끼운다.
- 표 스타일 지정: Pandoc 3.12는 Table의 `custom-style` 속성을 `w:tblStyle`로 쓴다(AnswerTable). 역변환에서는 표 스타일이 남지 않는다.
- 문자 스타일(CorrectChoice)은 수식(`m:oMath`) 안에는 적용되지 않는다. 단락 스타일(Answer)의 글자색은 수식에도 적용된다.

## 탭

- 마크다운의 탭은 공백이 되므로 `pandoc.RawInline('openxml', '<w:r><w:tab/></w:r>')`로 넣는다.
- 탭 위치(`w:tab w:pos`)는 단 왼쪽 끝 기준이다(들여쓰기 기준 아님).
- 내어쓰기 단락(`w:ind left=425 hanging=425`)에서는 첫 탭이 들여쓰기 위치로 간다(암묵적 탭).
- Choices5/3: 보기 칸 n개의 경계에 같은 간격 탭 n개(마지막은 단 오른쪽 끝).
- 역변환(`-f docx+styles`)에서 탭은 공백(Space)으로 읽힌다.

## 단락 유지(keepNext / keepLines)

- Problem·GroupStem·Choices*·BogiTitle·BogiBox·Figure·Answer는 스타일에 keepNext + keepLines.
- 사슬은 문제 끝의 빈 공간 단락에서 끊긴다(keepNext 없음). 묶음 문제는 마지막 하위 문제 전까지 빈 공간 단락에도 keepNext를 직접 넣어 한 덩어리로 만든다.
- keepNext 사슬이 한 단보다 길면 Word가 무시하고 나눈다.

## raw openxml 사용처 (Lua 필터)

| 위치 | XML |
| --- | --- |
| 번호 뒤 탭, 보기 사이 탭, 이어지는 단락 앞 탭 | `<w:r><w:tab/></w:r>` |
| 풀이 공간 | `<w:p><w:pPr>[<w:keepNext/>]<w:spacing w:before="0" w:after="0" w:line="{cm×567}" w:lineRule="exact"/><w:rPr><w:sz w:val="2"/></w:rPr></w:pPr></w:p>` |
| 정답표 뒤 쪽 나눔 | `<w:p><w:r><w:br w:type="page"/></w:r></w:p>` |

그 밖의 것(번호, 보기 강조, 정답 줄, 정답표)은 Pandoc 요소(Span/Div/Table + custom-style)로 만들어 수식이 Word 수식으로 바뀌고 역변환에서 스타일 이름이 남게 한다.

## Pandoc 동작 메모

- 블록 수식(`\[...\]`)이 든 단락은 Word에서 수식 앞·뒤로 단락이 나뉜다. 필터가 같은 위치에서 미리 나누고, 이어지는 단락 앞에 탭을 넣어 번호 칸 뒤에 맞춘다.
- 표 열 너비를 정하면 `tblW type="pct"`로 쓰는데, Word는 이 비율을 2단에서는 **단 너비**에 곱한다. 너비를 정하지 않으면 5.5in(7920twip) 기준 gridCol이 들어가 단 밖으로 넘친다.
- 표 칸이 수식으로 끝나면 Pandoc이 `U+200B`(폭 없는 공백) run을 붙인다. 역변환에 그대로 나온다.
- `\begin{cases}`는 역변환에서 `\left\{ \begin{matrix} … \end{matrix} \right.` 로 돌아온다.
- 머리말·꼬리말·구역 설정(sectPr)은 형식 파일의 것을 그대로 쓴다.
