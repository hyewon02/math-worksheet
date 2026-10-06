package com.mathworksheet.problem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.mathworksheet.problem.repository.ProblemRepository;

/**
 * dev 프로필에서 문제은행이 비어 있으면 샘플 문제 12개를 넣는다. 배포본(기본 프로필)에서는 동작하지 않는다.
 * 실행: {@code ./gradlew bootRun --args='--spring.profiles.active=dev'}
 */
@Component
@Profile("dev")
public class DevSampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSampleDataLoader.class);

    private final ProblemRepository problems;
    private final SampleProblemImporter importer;

    public DevSampleDataLoader(ProblemRepository problems, SampleProblemImporter importer) {
        this.problems = problems;
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (problems.count() > 0) {
            return;
        }
        int count = importer.importClasspath(SampleProblemImporter.SAMPLE_01).size();
        log.info("개발용 샘플 문제 {}개를 넣었습니다", count);
    }
}
