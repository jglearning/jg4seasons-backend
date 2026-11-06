package com.jglearning.jg4seasons.listening.completion;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 레벨별 완료 규칙(completion_rule, ADR-07)을 읽은 값. 1차가 지원하는 형태는 하나:
 * {@code [{"kind":"passage","eachPassage":true,"anyOf":["1.0", …]}]} — 지문마다 anyOf 속도 중 하나를 threshold 이상 들으면 그 지문 완료.
 * 다른 형태는 데이터 오류라 IllegalStateException(규칙을 조용히 무시하지 않는다).
 */
public record CompletionRule(BigDecimal threshold, Set<String> requiredSpeeds) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 지문 트랙 variant의 속도와 같은 형식(content의 p{n}:{속도}) — 다르면 어떤 속도도 필수가 아니게 되어 회차가 끝나지 않는다 */
    private static final Pattern SPEED = Pattern.compile("^\\d\\.\\d$");

    /** 생성자에서 검사한다 — parse를 거치지 않고 만들어도 같은 불변식 */
    public CompletionRule {
        if (threshold == null || threshold.signum() <= 0 || threshold.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("완료 기준 청취율은 0보다 크고 1 이하여야 한다: " + threshold);
        }
        if (requiredSpeeds == null || requiredSpeeds.isEmpty()) {
            throw new IllegalStateException("필수 속도가 없다");
        }
        for (String speed : requiredSpeeds) {
            if (speed == null || !SPEED.matcher(speed).matches()) {
                throw new IllegalStateException("필수 속도 형식은 0.0이어야 한다: " + speed);
            }
        }
        requiredSpeeds = Set.copyOf(requiredSpeeds);
    }

    public static CompletionRule parse(BigDecimal coverageThreshold, String requiredTracksJson) {
        JsonNode tracks;
        try {
            tracks = JSON.readTree(requiredTracksJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("required_tracks JSON을 읽을 수 없다", e);
        }
        if (tracks == null || !tracks.isArray() || tracks.size() != 1) {
            throw new IllegalStateException("required_tracks는 항목 하나짜리 배열이어야 한다: " + requiredTracksJson);
        }
        JsonNode track = tracks.get(0);
        if (!"passage".equals(text(track, "kind"))) {
            throw new IllegalStateException("지원하지 않는 required_tracks kind: " + requiredTracksJson);
        }
        JsonNode eachPassage = track.get("eachPassage");
        if (eachPassage == null || !eachPassage.isBoolean() || !eachPassage.booleanValue()) {
            throw new IllegalStateException("required_tracks는 eachPassage=true만 지원한다: " + requiredTracksJson);
        }
        JsonNode anyOf = track.get("anyOf");
        if (anyOf == null || !anyOf.isArray() || anyOf.isEmpty()) {
            throw new IllegalStateException("required_tracks anyOf가 비었다: " + requiredTracksJson);
        }
        Set<String> speeds = new LinkedHashSet<>();
        for (JsonNode speed : anyOf) {
            if (!speed.isString()) {
                throw new IllegalStateException("required_tracks anyOf 속도는 문자열이어야 한다: " + requiredTracksJson);
            }
            speeds.add(speed.asString());
        }
        return new CompletionRule(coverageThreshold, speeds);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }
}
