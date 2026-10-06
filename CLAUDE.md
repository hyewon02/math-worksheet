# 수학 문제지 생성 프로그램

## 1. 프로젝트 개요

학원 PC 한 대에서 돌아가는 웹 프로그램. 문제집 쪽 사진 → Mathpix OCR → 문제 나누기 → 사람 검수 → 문제은행 → Word 문제지·정답지.

- 설계서: [docs/design.md](docs/design.md). 모든 결정의 기준이다. 작업 전에 해당 장을 읽는다.
- 선생님용 안내: [docs/사진 폴더 정리 규칙.md](docs/사진%20폴더%20정리%20규칙.md), [docs/형식 파일 만들기 프롬프트.md](docs/형식%20파일%20만들기%20프롬프트.md)

## 2. 절대 원칙

1. **본문 불변**: 문제 본문을 자동으로 정리·보정·교정하는 코드를 만들지 않는다. 본문이 바뀌는 곳은 검수 화면에서 사람이 고칠 때뿐이다. OCR 원문(OcrResult)은 읽기 전용이다.
2. **런타임 LLM 미사용**: 프로그램 안에서 생성형 AI를 호출하지 않는다. AI는 Mathpix OCR 하나뿐이다.
3. **API 키 비노출**: 키는 `MathpixCredentialsProvider`를 통해서만 읽는다. 코드·설치 파일·로그·테스트 픽스처에 키를 넣지 않는다. 화면에는 `••••1234`로 가린다.
4. 외부 통신은 Mathpix 호출뿐이다.

## 3. 저장소 구조

```
backend/     Spring Boot 3 + Java 21 (Gradle). 패키지 com.mathworksheet
frontend/    React + Vite + TypeScript. 빌드 결과는 backend/src/main/resources/static/
worksheet/   형식 파일(templates), Lua 필터(filters), 샘플 원고(samples)의 단일 원본. Gradle이 리소스로 복사
samples/     개발용 쪽 사진(pages)과 라벨(labels). git 제외
output/      에이전트 중간 산출물. git 제외
docs/        설계서, 선생님용 문서, 개발 일지(devlog/)
.claude/     스킬 8개, 서브에이전트 2개
```

런타임 데이터는 `%APPDATA%\MathWorksheet\`(db, images, templates, work, logs, config)에 저장된다. 경로는 `AppPaths`로만 얻는다.

## 4. 빌드·실행·테스트

```bash
# 백엔드 (Java 21은 Gradle 툴체인이 자동으로 받는다)
cd backend && ./gradlew test          # 테스트
cd backend && ./gradlew bootRun       # http://localhost:8080

# 프론트엔드
cd frontend && npm install
cd frontend && npm run dev            # http://localhost:5173 (/api는 8080으로 프록시)
cd frontend && npm run build          # backend static 폴더로 출력

# 전체 빌드·테스트 요약
bash .claude/skills/build-test/scripts/run_tests.sh
```

- 개발자 Mathpix 키: `backend/application-local.yml.example`을 `application-local.yml`로 복사하거나 환경 변수 `MATHPIX_APP_ID`, `MATHPIX_APP_KEY`.
- 키 우선순위: 설정 화면(settings.json) > application-local.yml > 환경 변수.
- Pandoc은 개발 PC의 PATH에 있어야 한다. 배포 시에는 설치 파일에 pandoc.exe를 포함한다.
- Node는 22.14 이상을 쓴다. 22.13 이하는 한글 경로에서 크래시한다.

## 5. 코딩 규칙

- Java 21, 패키지는 기능별(`ocr`, `storage`, `problem`, `worksheet` …), 그 안에서 controller / service / repository 계층.
- 스키마 변경은 Flyway(`backend/src/main/resources/db/migration/V{n}__설명.sql`)로만 한다. `ddl-auto`는 `validate`.
- 동시 수정이 있는 엔티티(Problem 등)는 `@Version` 낙관적 잠금.
- OCR 엔진과 이미지 저장소는 인터페이스(`OcrEngine`, `ImageStorage`) 뒤에 둔다.
- 기능 코드는 테스트와 함께 커밋한다. 테스트 이름은 한국어로 동작을 설명한다.
- 화면 문구는 한국어, 선생님이 이해하는 말로 쓴다(LaTeX, Spring 같은 용어 금지).
- 검수 화면은 1366px 노트북에서도 쓸 수 있어야 한다.

## 6. 작업 절차

설계서 11장 로드맵 순서로 진행한다. 단계마다:

1. 설계서 해당 장과 완료 기준을 읽고 작업 목록을 만들어 **사용자 승인**을 받는다.
2. 구현 → build-test 실행. 실패하면 고쳐서 재실행, 3회 실패하면 원인과 선택지를 보고한다.
3. 산출물 자기 검증(미리보기, 리포트) 후 완료 기준 충족 여부를 보고하고 사용자 확인을 받는다.
4. 개발 일지 `docs/devlog/YYYY-MM-DD-주제.md`를 쓰고 `docs/devlog/README.md` 목록에 추가한다. 백엔드 포트폴리오·면접 참고용이므로 결정 이유, 고려한 대안, 겪은 문제(증상→원인→해결), 예상 면접 질문을 남긴다. 양식은 `docs/devlog/README.md`.

현재 단계: **로드맵 2단계 Word 생성 검증 진행 중**. 생성·검증 파이프라인(Java `com.mathworksheet.worksheet`)과 내장 형식 1종(내신형 2단) 완료, 학원 시험지 샘플을 받으면 형식 2~3종으로 늘린다. Mathpix 결제 후 1단계.

## 7. 스킬·서브에이전트 호출 규칙

| 상황 | 사용 |
| --- | --- |
| 코드 변경 후 항상 | build-test |
| 본문 저장·조립·변환 경로 코드 변경 | text-integrity |
| OCR 품질 측정, OCR 옵션·분할 규칙 변경 | ocr-evaluator 서브에이전트(mathpix-ocr, split-eval) |
| 형식 파일·Lua 필터·원고 조립·정답지·그림 배치 | docx-builder 서브에이전트(docx-build, roundtrip-verify, template-lint, docx-preview) |

- 서브에이전트끼리는 직접 호출하지 않는다. 메인이 조율한다.
- 서브에이전트에는 경로와 짧은 변경 요구만 인라인으로 넘기고, 결과는 `summary.md` 경로와 한 줄 결론으로 받는다.

## 8. 중간 산출물 규칙

- 위치: `/output/{에이전트}/{실행ID}/`. 실행ID는 `YYYYMMDD-HHMMSS`.
- OCR 평가: `/output/ocr-eval/{실행ID}/raw/*.json`, `report.md`, `errors.json`, `split.json`
- Word 생성: `/output/docx/{실행ID}/*.docx`, `verify.json`, `preview/*.png`, `summary.md`
- 테스트 요약: `/output/tests/{실행ID}.md`
- 런타임 데이터(`%APPDATA%`)와 섞지 않는다.

## 9. 에스컬레이션 기준

다음은 직접 판단하지 말고 반드시 사용자에게 확인한다.

- OCR 원문이나 샘플 라벨을 "올바르게" 고치는 것
- 역변환 비교 실패를 정규화 규칙을 넓혀 덮는 것
- 테스트 기대값을 실제 결과에 맞춰 바꾸는 것
- 설계서와 다른 결정(스택, 데이터 모델, 화면 흐름 변경)
- Mathpix 키·결제 문제, 분할 정확도가 낮을 때
- 같은 실패가 3회 반복될 때

## 10. 금지 사항

- 문제 본문 자동 정리·보정 코드(공백 정리, 오탈자 교정, 수식 "정규화" 저장 포함). 정규화는 역변환 **비교용**으로만 쓴다.
- 런타임 LLM 호출, Mathpix 외 외부 통신
- API 키를 코드·로그·커밋에 남기는 것
- Mathpix 외부 이미지 링크 저장
- `/samples`, `/output`, `application-local.yml` 커밋
- Flyway 없이 스키마 변경, 이미 적용된 마이그레이션 파일 수정
