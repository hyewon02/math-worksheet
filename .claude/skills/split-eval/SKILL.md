---
name: split-eval
description: Mathpix 원문 JSON과 라벨로 문제 자동 분할 정확도를 계산한다. 로드맵 1단계, 분할 정규식 변경 후 사용.
---

# split-eval

```bash
bash .claude/skills/split-eval/scripts/split_eval.sh output/ocr-eval/{실행ID}/raw output/ocr-eval/{실행ID}/split.json [samples/labels]
# 종료 코드: 0 모든 사진 일치, 1 불일치 있음, 2 오류
```

- 앱과 같은 Java 코드(`ProblemSplitter`, `SplitEvaluation`)를 부른다. Mathpix를 다시 호출하지 않는다.
- 경계 규칙(`ProblemSplitter`): 줄 첫머리가 `10.`, `04 삼각형…`, `문제 4`, `[3~4]`이면 문제 시작, `예제 1`은 경계지만 문제 번호에서 뺀다. `page_info`(쪽·소단원 번호), 표 칸, 그림, 수식 블록, 답 칸, 손글씨 줄은 경계 후보가 아니다.
- 라벨: `samples/labels/{사진이름}.json`의 `problems`(읽는 순서). 기준은 `samples/labels/README.md`.
- 지표: 재현율(라벨 문제 중 찾은 비율), 정밀도(추천 경계 중 맞은 비율), 완전히 맞은 사진 수. 순서까지 맞아야 맞은 것으로 센다(최장 공통 부분열).
- `split.json`에는 사진별 경계(시작·끝 줄, 위쪽 y)와 그림 영역 사각형(diagram/chart)도 들어 있다.
- **라벨을 결과에 맞춰 고치지 않는다.** 규칙을 특정 사진에 맞춰 늘리지 않는다(과적합). 정확도가 낮으면 사용자에게 보고한다.
