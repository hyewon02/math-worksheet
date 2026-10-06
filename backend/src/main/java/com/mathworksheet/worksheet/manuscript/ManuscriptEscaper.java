package com.mathworksheet.worksheet.manuscript;

/**
 * 원고 조립 때 본문에 붙이는 이스케이프(docs/manuscript-format.md "조립 규칙").
 * <p>
 * 수식 구간 밖의 {@code <}, {@code >} 앞에만 {@code \}를 붙인다. 붙이지 않으면 {@code <보기>}가 HTML 태그로 읽혀
 * 사라진다. 수식({@code \(...\)}, {@code \[...\]}) 안에 붙이면 LaTeX가 깨지므로 건드리지 않는다.
 * 글자를 고치는 것이 아니라 Pandoc이 글자 그대로 읽게 하는 표시이며, 역변환 결과는 원래 글자와 같다.
 */
public final class ManuscriptEscaper {

    private ManuscriptEscaper() {
    }

    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        String closing = null;
        int i = 0;
        while (i < text.length()) {
            if (closing == null) {
                if (text.startsWith("\\(", i) || text.startsWith("\\[", i)) {
                    closing = text.startsWith("\\(", i) ? "\\)" : "\\]";
                    out.append(text, i, i + 2);
                    i += 2;
                    continue;
                }
                char c = text.charAt(i);
                // 이미 \< 로 적힌 것은 두 번 붙이지 않는다
                if ((c == '<' || c == '>') && (i == 0 || text.charAt(i - 1) != '\\')) {
                    out.append('\\');
                }
                out.append(c);
                i++;
            } else {
                if (text.startsWith(closing, i)) {
                    out.append(closing);
                    i += 2;
                    closing = null;
                } else {
                    out.append(text.charAt(i));
                    i++;
                }
            }
        }
        return out.toString();
    }
}
