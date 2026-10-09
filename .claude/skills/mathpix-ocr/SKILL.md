---
name: mathpix-ocr
description: 샘플 쪽 사진을 Mathpix v3/text로 호출해 원문 JSON을 저장하고 사용량을 집계한다. 로드맵 1단계, Mathpix 연동 코드 작성·디버깅, OCR 옵션 변경 시 사용.
---

# mathpix-ocr

```bash
RUN=$(date +%Y%m%d-%H%M%S); mkdir -p output/ocr-eval/$RUN
bash .claude/skills/mathpix-ocr/scripts/ocr.sh check samples/pages/a.jpg output/ocr-eval/$RUN      # 키 확인 + 1장
bash .claude/skills/mathpix-ocr/scripts/ocr.sh batch samples/pages output/ocr-eval/$RUN 10          # 최대 10장
```

- 앱과 같은 Java 코드(`MathpixOcrEngine`, `OcrRetry`)를 CLI(`./gradlew ocrTool`)로 부른다.
- 키는 `MathpixCredentialsProvider`와 같은 우선순위(설정 파일 > `backend/application-local.yml` > 환경 변수)로 읽고, 화면에는 `••••1234`로만 보인다.
- **요금 보호**: `batch`는 지정한 장수까지만 호출한다(기본 1). 사진 지문(SHA-256)별 응답을 `output/ocr-eval/cache/`에 두어 같은 사진은 다시 호출하지 않는다.
- 응답 원문은 `output/ocr-eval/{실행ID}/raw/{사진이름}.json`에 그대로 저장한다. 수정하지 않는다.
- 요청 옵션: `formats: ["text"]`, `include_line_data: true`, 수식 구분자 `\(...\)`·`\[...\]`, `metadata.improve_mathpix: false`(사진 보관·학습 안 함).
- 오류: Mathpix는 대부분 **HTTP 200 + `error` 칸**으로 돌려준다. 401·403(키)·이미지 오류는 재시도하지 않고, `sys_exception`·429·5xx·연결 실패만 최대 3회 재시도한다.
- 요금 참고: 이미지 1장당 약 $0.002~0.005(설계서 10장). 콘솔 Usage 집계는 늦게 반영된다.
