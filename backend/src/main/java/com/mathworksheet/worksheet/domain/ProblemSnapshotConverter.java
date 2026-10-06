package com.mathworksheet.worksheet.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 스냅샷을 JSON 문자열로 저장한다. 나중에 칸이 늘어도 예전 스냅샷을 읽을 수 있게 모르는 칸은 무시한다 */
@Converter
public class ProblemSnapshotConverter implements AttributeConverter<ProblemSnapshot, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(ProblemSnapshot snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷을 저장할 수 없습니다", e);
        }
    }

    @Override
    public ProblemSnapshot convertToEntityAttribute(String json) {
        try {
            return MAPPER.readValue(json, ProblemSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷을 읽을 수 없습니다", e);
        }
    }
}
