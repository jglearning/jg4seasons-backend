package com.jglearning.jg4seasons.wordtest.persistence;

import com.jglearning.jg4seasons.wordtest.grading.AnswerResult;
import com.jglearning.jg4seasons.wordtest.grading.UnsureReason;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 문항별 답안 읽기. 부르는 쪽이 그 제출의 주인을 먼저 확인한다 */
@Component
public class AnswerStore {

    /** 답안 한 행. 2단계 칸(두 번째 읽기·학생 확인·교사 정정)은 아직 읽지 않는다 */
    public record AnswerRow(long itemId, String recognizedText, UnsureReason unsureReason, AnswerResult result) {}

    private final NamedParameterJdbcTemplate jdbc;

    public AnswerStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AnswerRow> answers(long submissionId) {
        return jdbc.query(
                "select item_id, recognized_text, unsure_reason, result from word_test_answer"
                        + " where submission_id = :submission",
                Map.of("submission", submissionId),
                (rs, i) -> {
                    String reason = rs.getString("unsure_reason");
                    return new AnswerRow(
                            rs.getLong("item_id"),
                            rs.getString("recognized_text"),
                            reason == null ? null : UnsureReason.fromDb(reason),
                            AnswerResult.fromDb(rs.getString("result")));
                });
    }
}
