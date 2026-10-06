package com.mathworksheet.worksheet.docx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * 빈칸 채우기(설계서 8장 "문제지 생성 순서" 4). 머리말·꼬리말·본문의 {@code {{학원명}}} 같은 빈칸을 입력값으로 바꾼다.
 * <p>
 * Word는 한 단어를 여러 run(글자 묶음)으로 쪼개 저장하기도 한다("{{시험" + "제목}}"). 단락 단위로 글자를 이어 붙여
 * 빈칸을 찾고, 걸쳐 있는 run들의 글자만 고친다. 서식(run 속성)은 첫 run의 것이 남는다.
 * 넘겨받은 키의 빈칸만 바꾸므로 문제 본문 글자에는 영향이 없다.
 */
public class PlaceholderFiller {

    public static final String ACADEMY = "학원명";
    public static final String TITLE = "시험제목";
    public static final String DATE = "날짜";
    public static final String NUMBER = "문제지번호";
    public static final List<String> REQUIRED = List.of(ACADEMY, TITLE, DATE, NUMBER);

    static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    /**
     * @return 채우지 못하고 남은 빈칸 이름(형식 파일에 모르는 빈칸이 있거나 값을 안 넘긴 경우)
     */
    public Set<String> fill(Path source, Path target, Map<String, String> values) {
        try (InputStream in = Files.newInputStream(source); XWPFDocument doc = new XWPFDocument(in)) {
            Set<String> leftovers = new TreeSet<>();
            forEachParagraph(doc, paragraph -> {
                replace(paragraph, values);
                Matcher m = ANY_PLACEHOLDER.matcher(paragraph.getText());
                while (m.find()) {
                    leftovers.add(m.group(1));
                }
            });
            try (OutputStream out = Files.newOutputStream(target)) {
                doc.write(out);
            }
            return leftovers;
        } catch (IOException e) {
            throw new UncheckedIOException("빈칸을 채울 수 없습니다: " + source, e);
        }
    }

    /** 본문, 표 안, 모든 머리말·꼬리말(첫 쪽·짝수 쪽 포함)의 단락 */
    static void forEachParagraph(XWPFDocument doc, Consumer<XWPFParagraph> action) {
        visit(doc.getBodyElements(), action);
        for (XWPFHeaderFooter hf : headersAndFooters(doc)) {
            visit(hf.getBodyElements(), action);
        }
    }

    static void forEachParagraphIn(XWPFHeaderFooter headerFooter, Consumer<XWPFParagraph> action) {
        visit(headerFooter.getBodyElements(), action);
    }

    static List<XWPFHeaderFooter> headersAndFooters(XWPFDocument doc) {
        List<XWPFHeaderFooter> all = new ArrayList<>(doc.getHeaderList());
        all.addAll(doc.getFooterList());
        return all;
    }

    private static void visit(List<IBodyElement> elements, Consumer<XWPFParagraph> action) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                action.accept(paragraph);
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        visit(cell.getBodyElements(), action);
                    }
                }
            }
        }
    }

    static void replace(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty() || !paragraph.getText().contains("{{")) {
            return;
        }
        List<String> texts = new ArrayList<>();
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            texts.add(t == null ? "" : t);
        }

        // 바꾼 값 뒤에서부터 다시 찾는다. 입력값에 "{{…}}"가 들어 있어도 다시 바꾸지 않는다.
        int searchFrom = 0;
        while (true) {
            Matcher m = ANY_PLACEHOLDER.matcher(String.join("", texts));
            if (!m.find(searchFrom)) {
                break;
            }
            String value = values.get(m.group(1));
            if (value == null) {
                searchFrom = m.end();
                continue;
            }
            splice(texts, m.start(), m.end(), value);
            searchFrom = m.start() + value.length();
        }

        for (int i = 0; i < runs.size(); i++) {
            String original = runs.get(i).getText(0);
            String updated = texts.get(i);
            if (!updated.equals(original == null ? "" : original)) {
                runs.get(i).setText(updated, 0);
            }
        }
    }

    /** 이어 붙인 글자 기준 [start, end)를 value로 바꾼다. value는 시작 run에 넣고 걸쳐 있던 나머지 run에서는 해당 글자를 지운다. */
    private static void splice(List<String> texts, int start, int end, String value) {
        int offset = 0;
        boolean inserted = false;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            int runStart = offset;
            int runEnd = offset + t.length();
            offset = runEnd;
            if (runEnd <= start || runStart >= end) {
                continue;
            }
            int from = Math.max(start, runStart) - runStart;
            int to = Math.min(end, runEnd) - runStart;
            String replacement = inserted ? "" : value;
            inserted = true;
            texts.set(i, t.substring(0, from) + replacement + t.substring(to));
        }
    }
}
