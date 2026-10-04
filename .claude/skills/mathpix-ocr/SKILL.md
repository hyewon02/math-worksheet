---
name: mathpix-ocr
description: 샘플 쪽 사진을 Mathpix v3/text로 호출해 원문 JSON을 저장하고 사용량을 집계한다. 로드맵 1단계, Mathpix 연동 코드 작성·디버깅, OCR 옵션 변경 시 사용.
---

# mathpix-ocr

- 스크립트: `scripts/call_mathpix.py`(1장), `scripts/batch_ocr.py`(폴더)
- 참고: `references/`에 Mathpix 옵션(`include_line_data`, 표 출력 형식), 응답 구조(`text`, `line_data`), 요금 규칙($0.002/장, 12줄 초과 $0.005)
- 출력: `/output/ocr-eval/{실행ID}/raw/*.json`. 응답은 수정하지 않고 그대로 저장한다.
- 키는 환경 변수 `MATHPIX_APP_ID`, `MATHPIX_APP_KEY`에서만 읽는다. 출력·로그에 키를 남기지 않는다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
