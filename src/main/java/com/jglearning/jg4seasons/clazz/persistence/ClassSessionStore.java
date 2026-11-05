package com.jglearning.jg4seasons.clazz.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 반 진도표(class_session) 읽기·쓰기. 이 테이블에는 academy_id가 없어, 반을 학원 범위로 확인한 뒤에만 부른다(ClassManagementService).
 */
@Component
public class ClassSessionStore {

    /** status: scheduled · done · cancelled */
    public record SessionRow(int seq, long lessonId, LocalDate scheduledDate, String status) {}

    private final JdbcTemplate jdbc;

    public ClassSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 새 반의 진도표 행을 모두 scheduled로 넣는다 */
    public void insertScheduled(long classId, List<SessionRow> rows) {
        jdbc.batchUpdate(
                "insert into class_session (class_id, seq, lesson_id, scheduled_date, status)"
                        + " values (?, ?, ?, ?, 'scheduled')",
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setLong(1, classId);
                    ps.setInt(2, row.seq());
                    ps.setLong(3, row.lessonId());
                    ps.setObject(4, row.scheduledDate());
                });
    }

    /** seq 순 */
    public List<SessionRow> findByClass(long classId) {
        return jdbc.query(
                "select seq, lesson_id, scheduled_date, status from class_session where class_id = ? order by seq",
                (rs, i) -> new SessionRow(
                        rs.getInt("seq"),
                        rs.getLong("lesson_id"),
                        rs.getObject("scheduled_date", LocalDate.class),
                        rs.getString("status")),
                classId);
    }
}
