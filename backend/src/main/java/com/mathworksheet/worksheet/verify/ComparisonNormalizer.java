package com.mathworksheet.worksheet.verify;

import java.util.regex.Pattern;

/**
 * 역변환 비교용 정규화(설계서 8장 "검증" 5). <b>비교에만 쓰고 저장하지 않는다</b>(CLAUDE.md 금지 사항).
 * <p>
 * 규칙은 설계서 8장 목록에 적힌 것뿐이다: 공백(폭 없는 공백 포함) 제거, {@code \dfrac}→{@code \frac},
 * cases 환경, {@code \left}/{@code \right} 제거, 불필요한 중괄호 제거.
 * 비교가 실패한다고 규칙을 늘리지 않는다. 늘리려면 사용자 확인을 받고 설계서 목록에도 적는다.
 */
public final class ComparisonNormalizer {

    /** U+200B: Pandoc이 수식으로 끝나는 표 칸 뒤에 붙인다(2026-10-06 사용자 승인) */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u200B]+");
    /**
     * Word 수식에는 cases가 없어 Pandoc이 {@code \left\{ \begin{matrix}…\end{matrix} \right.\ }로 돌려준다
     * (2026-10-06 사용자 승인). 비교 전에 원래 모양으로 되돌린다.
     */
    private static final Pattern MATRIX_CASES = Pattern.compile(
            "\\\\left\\\\\\{\\s*\\\\begin\\{matrix}(.*?)\\\\end\\{matrix}\\s*\\\\right\\.(\\\\ )?", Pattern.DOTALL);
    private static final Pattern DFRAC = Pattern.compile("\\\\dfrac(?![a-zA-Z])");
    private static final Pattern LEFT_RIGHT = Pattern.compile("\\\\(left|right)(?![a-zA-Z])");
    /** 글자 하나만 감싼 중괄호. {@code \{}처럼 이스케이프된 괄호는 제외 */
    private static final Pattern SINGLE_CHAR_GROUP = Pattern.compile("(?<!\\\\)\\{([^{}\\\\])}");

    private ComparisonNormalizer() {
    }

    public static String text(String s) {
        return WHITESPACE.matcher(s).replaceAll("");
    }

    public static String math(String latex) {
        String s = MATRIX_CASES.matcher(latex).replaceAll(m ->
                java.util.regex.Matcher.quoteReplacement("\\begin{cases}" + m.group(1) + "\\end{cases}"));
        s = DFRAC.matcher(s).replaceAll("\\\\frac");
        s = LEFT_RIGHT.matcher(s).replaceAll("");
        s = WHITESPACE.matcher(s).replaceAll("");
        String previous;
        do {
            previous = s;
            s = SINGLE_CHAR_GROUP.matcher(s).replaceAll("$1");
        } while (!s.equals(previous));
        return s;
    }
}
