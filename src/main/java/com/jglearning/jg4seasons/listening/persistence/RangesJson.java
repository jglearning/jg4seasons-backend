package com.jglearning.jg4seasons.listening.persistence;

import com.jglearning.jg4seasons.listening.segment.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** track_progress.merged_ranges JSON([[start, end], …], ms) 읽기·쓰기. 형식이 틀리면 데이터 오류. */
final class RangesJson {

    private RangesJson() {}

    static List<Range> read(JsonMapper jsonMapper, String json) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("merged_ranges JSON을 읽을 수 없다", e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("merged_ranges가 배열이 아니다");
        }
        List<Range> ranges = new ArrayList<>();
        for (JsonNode pair : root) {
            if (!pair.isArray()
                    || pair.size() != 2
                    || !pair.get(0).isInt()
                    || !pair.get(1).isInt()) {
                throw new IllegalStateException("merged_ranges 항목은 [start, end] 정수 쌍이어야 한다");
            }
            int start = pair.get(0).intValue();
            int end = pair.get(1).intValue();
            if (!Range.isValid(start, end)) {
                throw new IllegalStateException("merged_ranges 구간은 0 <= 시작 < 끝이어야 한다: " + start + "~" + end);
            }
            ranges.add(new Range(start, end));
        }
        return ranges;
    }

    static String write(List<Range> ranges) {
        return ranges.stream()
                .map(range -> "[" + range.startMs() + "," + range.endMs() + "]")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
