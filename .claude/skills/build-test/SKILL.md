---
name: build-test
description: 백엔드·프론트엔드를 빌드하고 테스트한 뒤 결과를 요약한다. 코드 변경 후 항상 사용.
---

# build-test

- 실행: `bash .claude/skills/build-test/scripts/run_tests.sh`
- 출력: `/output/tests/{실행ID}.md`
- 실패하면 고쳐서 재실행, 3회 실패하면 사용자에게 보고한다. 테스트 기대값을 결과에 맞춰 바꾸지 않는다.

> 상세 절차와 스크립트는 해당 로드맵 단계를 구현할 때 작성한다(설계서 15장).
