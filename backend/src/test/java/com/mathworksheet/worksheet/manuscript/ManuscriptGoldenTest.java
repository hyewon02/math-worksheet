package com.mathworksheet.worksheet.manuscript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.problem.service.SampleProblemImporter;
import com.mathworksheet.worksheet.PandocTestSupport;
import com.mathworksheet.worksheet.domain.SpaceSize;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Item;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Layout;
import com.mathworksheet.worksheet.service.SnapshotFactory;

/**
 * 본문 불변 회귀 검사의 한 구간: 문제은행(DB) → 스냅샷 → 원고 조립 결과가 손으로 쓴 기준 원고(sample-01.md)와 한 글자도 다르지 않아야 한다.
 * sample-01.md는 역변환 비교 13/13을 통과한 원고다. 이 테스트가 깨지면 기대값(원고)을 고치지 말고 조립 코드를 고친다.
 */
@SpringBootTest
@Transactional
class ManuscriptGoldenTest {

    @Autowired
    SampleProblemImporter importer;

    @Autowired
    ProblemRepository problems;

    @Autowired
    SnapshotFactory snapshots;

    @Test
    void 샘플_문제를_DB에_넣고_조립하면_기준_원고와_같다() throws Exception {
        List<Long> ids = importer.importClasspath(SampleProblemImporter.SAMPLE_01);
        List<Item> items = ids.stream()
                .map(id -> new Item(snapshots.of(problems.findById(id).orElseThrow()), SpaceSize.NORMAL))
                .toList();
        Layout naesin = new Layout(type -> switch (type) {
            case CHOICE -> new BigDecimal("1.0");
            case SHORT -> new BigDecimal("3.0");
            case ESSAY -> new BigDecimal("8.0");
        }, new BigDecimal("8.2"));

        String assembled = new ManuscriptAssembler().assemble(items, naesin).markdown();

        String expected = Files.readString(PandocTestSupport.worksheetDir().resolve("samples/sample-01.md"))
                .replace("\r\n", "\n");
        assertThat(assembled).isEqualTo(expected);
    }
}
