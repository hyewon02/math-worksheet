-- 문제지 Lua 필터(설계서 8장 "문제지 생성 순서" 3).
-- 로드맵 2단계(Word 생성 검증)에서 구현한다.
--   - 문제 번호와 Word 탭 삽입(ProblemNumber)
--   - 보기 너비 계산 → Choices5(9칸 이하) / Choices3(15칸 이하) / Choices1
--   - space 높이만큼 풀이 공간 삽입
--   - 묶음 문제 "다음 단락과 함께"
--   - -M answers=true: 빠른 정답표(AnswerTable), 정답 보기 강조(CorrectChoice), 정답 줄(Answer)
--   - 표 열 너비를 본문 너비의 44% / 열 수로 조정

return {}
