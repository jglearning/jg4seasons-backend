package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 교사 화면용 반 학생 × 회차 진도(lesson_progress) 읽기. */
@IntegrationTest
@Transactional
class LessonProgressQueryTest {

    @Autowired
    LessonProgressQuery query;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long a;
    long b;
    long l4;
    long l5;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        long academyId = data.academy("진도 학원");
        a = data.student(academyId, "가");
        b = data.student(academyId, "나");
        l4 = data.lessonId("L2-B1-04");
        l5 = data.lessonId("L2-B1-05");
    }

    private void progress(long student, long lesson, String status, int done, int total, String completedAtUtc) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, required_done, required_total,"
                        + " completed_at) values (?, ?, ?, ?, ?, ?)",
                student,
                lesson,
                status,
                done,
                total,
                completedAtUtc == null ? null : LocalDateTime.parse(completedAtUtc));
    }

    @Test
    void 학생마다_회차마다_진도를_준다() {
        progress(a, l4, "completed", 6, 6, "2026-11-09T10:15:30");
        progress(a, l5, "in_progress", 3, 6, null);
        progress(b, l5, "not_started", 0, 6, null);

        Map<Long, Map<Long, LessonProgressSummary>> found = query.progressOf(List.of(a, b), List.of(l4, l5));

        assertThat(found.get(a).get(l4))
                .isEqualTo(new LessonProgressSummary(
                        LessonProgressStatus.COMPLETED, 6, 6, Instant.parse("2026-11-09T10:15:30Z")));
        assertThat(found.get(a).get(l5))
                .isEqualTo(new LessonProgressSummary(LessonProgressStatus.IN_PROGRESS, 3, 6, null));
        assertThat(found.get(b)).containsOnlyKeys(l5);
    }

    @Test
    void 목록_밖_학생과_회차는_빠진다() {
        progress(a, l4, "completed", 6, 6, "2026-11-09T10:00:00");
        progress(b, l5, "in_progress", 1, 6, null);

        Map<Long, Map<Long, LessonProgressSummary>> found = query.progressOf(List.of(a), List.of(l5));

        assertThat(found).isEmpty();
    }

    @Test
    void 완료가_아니면_completedAt은_null() {
        // 완료 뒤 상태가 바뀌어도(되돌림 없음이 원칙이지만) completed_at이 남아 있으면 완료가 아닌 칸에 시각을 싣지 않는다
        progress(a, l5, "in_progress", 5, 6, "2026-11-09T10:00:00");

        assertThat(query.progressOf(List.of(a), List.of(l5)).get(a).get(l5).completedAt())
                .isNull();
    }

    @Test
    void 빈_목록이면_쿼리를_보내지_않는다() {
        ProgressStore noDb = new ProgressStore(null, null); // 쿼리를 보내면 NullPointerException

        assertThat(noDb.lessonProgressOf(List.of(), List.of(l5))).isEmpty();
        assertThat(noDb.lessonProgressOf(List.of(a), List.of())).isEmpty();
    }
}
