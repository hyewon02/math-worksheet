package com.mathworksheet.split;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 분할 정확도(split-eval). 사진마다 추천한 문제 번호 순서를 라벨(samples/labels/*.json)과 비교한다.
 * 순서까지 맞아야 맞은 것으로 센다(최장 공통 부분열). 번호 비교는 공백만 무시한다.
 * <p>
 * 한계: 번호(경계 시작점)가 맞는지만 본다. 경계선의 정확한 위치는 검수 화면에서 사람이 확정한다.
 */
public final class SplitEvaluation {

    private SplitEvaluation() {
    }

    /**
     * @param found  맞게 찾은 문제
     * @param missed 라벨에는 있는데 못 찾은 문제
     * @param extra  잘못 끊은 곳(문제가 아닌데 경계로 추천)
     */
    public record PageResult(String image, List<String> expected, List<String> recommended,
                             List<String> found, List<String> missed, List<String> extra, boolean exact) {
    }

    public record Report(List<PageResult> pages, int expected, int recommended, int found) {

        /** 재현율: 라벨 문제 중 맞게 찾은 비율 */
        public double recall() {
            return expected == 0 ? 1 : (double) found / expected;
        }

        /** 정밀도: 추천한 경계 중 맞은 비율 */
        public double precision() {
            return recommended == 0 ? 1 : (double) found / recommended;
        }

        public long exactPages() {
            return pages.stream().filter(PageResult::exact).count();
        }

        public boolean allPagesCorrect() {
            return exactPages() == pages.size();
        }

        public String summary() {
            return "문제 %d개 중 %d개를 맞게 찾음(재현율 %.0f%%), 추천 %d곳 중 %d곳이 맞음(정밀도 %.0f%%), 사진 %d장 중 %d장 완전히 맞음"
                    .formatted(expected, found, recall() * 100, recommended, found, precision() * 100,
                            pages.size(), exactPages());
        }
    }

    public static Report evaluate(Map<String, ProblemSplitter.Split> results, Path labelsDir) {
        ObjectMapper json = new ObjectMapper();
        List<PageResult> pages = new ArrayList<>();
        int expectedTotal = 0;
        int recommendedTotal = 0;
        int foundTotal = 0;
        for (Map.Entry<String, ProblemSplitter.Split> e : results.entrySet()) {
            Path label = labelsDir.resolve(e.getKey() + ".json");
            if (!Files.exists(label)) {
                continue;
            }
            List<String> expected = new ArrayList<>();
            try {
                for (JsonNode n : json.readTree(label.toFile()).path("problems")) {
                    expected.add(n.asText());
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("라벨을 읽을 수 없습니다: " + label, ex);
            }
            PageResult page = compare(e.getKey(), expected, e.getValue().numbers());
            pages.add(page);
            expectedTotal += expected.size();
            recommendedTotal += page.recommended().size();
            foundTotal += page.found().size();
        }
        return new Report(pages, expectedTotal, recommendedTotal, foundTotal);
    }

    static PageResult compare(String image, List<String> expected, List<String> recommended) {
        List<String> e = expected.stream().map(SplitEvaluation::key).toList();
        List<String> r = recommended.stream().map(SplitEvaluation::key).toList();
        int[][] lcs = new int[e.size() + 1][r.size() + 1];
        for (int i = e.size() - 1; i >= 0; i--) {
            for (int j = r.size() - 1; j >= 0; j--) {
                lcs[i][j] = e.get(i).equals(r.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String> found = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < e.size() && j < r.size()) {
            if (e.get(i).equals(r.get(j))) {
                found.add(expected.get(i));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                missed.add(expected.get(i++));
            } else {
                extra.add(recommended.get(j++));
            }
        }
        while (i < e.size()) {
            missed.add(expected.get(i++));
        }
        while (j < r.size()) {
            extra.add(recommended.get(j++));
        }
        return new PageResult(image, expected, recommended, found, missed, extra, missed.isEmpty() && extra.isEmpty());
    }

    private static String key(String label) {
        return label.replaceAll("\\s+", "");
    }
}
