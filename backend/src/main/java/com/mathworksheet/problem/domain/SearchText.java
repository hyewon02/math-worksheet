package com.mathworksheet.problem.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 검색용 텍스트(설계서 4장 "본문 검색은 LIKE"). 본문에서 LaTeX 명령어·기호·구역 표시를 걷어 내고 공백을 없앤다.
 * 검색어도 같은 규칙으로 바꿔 {@code LIKE '%등비수열%'}로 찾는다. "등비 수열"처럼 띄어 써도 찾힌다.
 * <p>
 * 검색 전용 칸에만 쓰고, 본문(body_markdown)은 바꾸지 않는다.
 */
public final class SearchText {

    private static final Pattern FIGURE = Pattern.compile("\\[그림:[^\\]]*]");
    private static final Pattern DIV_FENCE = Pattern.compile("(?m)^:::.*$");
    private static final Pattern LATEX_COMMAND = Pattern.compile("\\\\[a-zA-Z]+");
    private static final Pattern SYMBOLS = Pattern.compile("[\\\\{}^_$|]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchText() {
    }

    public static String of(String... parts) {
        StringBuilder all = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                all.append(part).append(' ');
            }
        }
        return normalize(all.toString());
    }

    public static String normalize(String text) {
        String s = FIGURE.matcher(text).replaceAll(" ");
        s = DIV_FENCE.matcher(s).replaceAll(" ");
        s = LATEX_COMMAND.matcher(s).replaceAll(" ");
        s = SYMBOLS.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
