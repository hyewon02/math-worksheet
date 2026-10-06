# 수학 문제지 생성 프로그램

학원 선생님이 문제집 쪽 사진을 넣으면 **수식 OCR → 문제 자동 분할 → 사람 검수 → 문제은행 → Word 문제지·정답지**까지 만들어 주는 사내용 웹 프로그램입니다. 학원 PC 한 대에 설치해 서버로 쓰고, 다른 선생님은 같은 네트워크에서 브라우저로 접속합니다.

> 개발 진행 중 · Word 생성·검증 파이프라인, 데이터 모델, 문제은행·문제지 API 완료 / OCR·검수 화면 예정

## 핵심 원칙

- **본문 불변**: 범용 AI처럼 원문을 "자연스럽게" 고쳐 쓰지 않습니다. 수식 전용 OCR(Mathpix)의 결과를 원문으로 보관하고, 본문은 검수 화면에서 사람이 고칠 때만 바뀝니다.
- **런타임 LLM 미사용**: 외부 통신은 Mathpix OCR 호출 하나뿐입니다.
- **서버 비용 0원**: 설치 파일 하나(JRE·Pandoc 포함)로 학원 PC에서 돌아갑니다.

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| DB | H2 (파일 모드) |
| 프론트엔드 | React 19, Vite, TypeScript, KaTeX, MathLive, CodeMirror 6 |
| 문서 생성 | Pandoc + reference.docx + Lua 필터, Apache POI |
| OCR | Mathpix API v3/text |
| CI | GitHub Actions (Windows) |

## 구조

```
backend/     Spring Boot (Gradle). 패키지는 기능별(ocr, storage, problem, worksheet …)
frontend/    React + Vite. 빌드 결과가 backend의 static 폴더로 들어가 한 프로그램으로 배포
worksheet/   Word 형식 파일·Lua 필터·샘플 원고 (Gradle이 리소스로 복사)
docs/        설계서(design.md), 개발 일지(devlog/)
```

## 실행

```bash
cd backend && ./gradlew test       # 테스트 (Java 21은 Gradle 툴체인이 자동으로 받음)
cd backend && ./gradlew bootRun    # http://localhost:8080

cd frontend && npm install
cd frontend && npm run dev         # http://localhost:5173 (/api는 8080으로 프록시)
cd frontend && npm run build       # backend static 폴더로 출력
```

- 샘플 문제 12개가 든 상태로 실행: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
- 필요 도구: Node 22.14 이상(개발·CI는 24), Pandoc 3.x
- Mathpix 키: `backend/application-local.yml.example`을 `application-local.yml`로 복사하거나 환경 변수 `MATHPIX_APP_ID`, `MATHPIX_APP_KEY`

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/problems?textbook=&grade=&type=&tag=&answerMissing=&q=&page=&size=` | 문제은행 검색(검수 완료된 문제만). `q`는 띄어쓰기·LaTeX 명령어 무시 |
| GET | `/api/problems/{id}` | 문제 하나(보기·그림·묶음 포함) |
| PATCH | `/api/problems/{id}/answer` | 정답 수정. `version`이 다르면 409 |
| GET | `/api/figures/{marker}` | 그림 |
| GET | `/api/templates` | 문제지 형식 목록 |
| GET / POST | `/api/worksheets` | 저장된 문제지 목록 / 새 문제지(담을 때 문제 내용을 스냅샷으로 복사) |
| GET / PUT | `/api/worksheets/{id}` | 문제지 / 순서·풀이 공간·제목 수정(`version` 확인) |
| POST | `/api/worksheets/{id}/refresh` | 문제은행에서 수정된 문제를 최신으로 갱신 |
| POST | `/api/worksheets/{id}/duplicate` | 복제 |
| POST | `/api/worksheets/{id}/generate` | 원고 조립 → Word 변환 → 빈칸 채우기 → 역변환 비교. 결과와 내려받기 토큰 |
| GET | `/api/downloads/{token}/{student\|answers\|both}` | 문제지 / 정답지 / 둘 다(zip) |

## 문서

- [설계서](docs/design.md): 요구사항, 처리 흐름, 데이터 모델, 화면, 로드맵, 설계 결정 기록
- [개발 일지](docs/devlog/README.md): 단계별로 한 일, 결정과 이유, 겪은 문제
