---
name: split-eval
description: Mathpix 원문 JSON과 라벨로 문제 자동 분할 정확도를 계산한다. 로드맵 1단계, 분할 정규식 변경 후 사용.
---

# split-eval

- 스크립트: `scripts/split_eval.py`
- 입력: `/output/ocr-eval/{실행ID}/raw/`, `/samples/labels/`
- 참고: `references/`에 문제 번호 패턴 목록(`12.`, `1203`, `[1~2]` 등)
- 출력: `split.json`(사진별 추천 경계, 정답 경계, 일치 여부), 정확도 요약
- 라벨을 결과에 맞춰 고치지 않는다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
