package com.jglearning.jg4seasons.clazz.persistence;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 반 진도표(class_session) 읽기·쓰기. 이 테이블에는 academy_id가 없어, 반을 학원 범위로 확인한 뒤에만 부른다(ClassManagementService).
 */
@Component
public class ClassSessionStore {

    /** 읽은 행. note는 없으면 null */
    public record SessionRow(
            long id, int seq, long lessonId, LocalDate scheduledDate, SessionStatus status, String note) {}

    /** 새로 넣을 행(상태는 항상 scheduled) */
    public record NewSessionRow(int seq, long lessonId, LocalDate scheduledDate) {}

    private final JdbcTemplate jdbc;

    public ClassSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 새 반의 진도표 행을 모두 scheduled로 넣는다 */
    public void insertScheduled(long classId, List<NewSessionRow> rows) {
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

    private static SessionRow row(ResultSet rs, int rowNum) throws SQLException {
        return new SessionRow(
                rs.getLong("id"),
                rs.getInt("seq"),
                rs.getLong("lesson_id"),
                rs.getObject("scheduled_date", LocalDate.class),
                SessionStatus.fromValue(rs.getString("status")),
                rs.getString("note"));
    }

    /** seq 순 */
    public List<SessionRow> findByClass(long classId) {
        return jdbc.query(
                "select id, seq, lesson_id, scheduled_date, status, note from class_session where class_id = ? order by seq",
                ClassSessionStore::row,
                classId);
    }

    /** 한 회차의 진도표 행(휴강 시프트 뒤에는 둘 이상일 수 있음), seq 순 */
    public List<SessionRow> findByClassAndLesson(long classId, long lessonId) {
        return jdbc.query(
                "select id, seq, lesson_id, scheduled_date, status, note from class_session"
                        + " where class_id = ? and lesson_id = ? order by seq",
                ClassSessionStore::row,
                classId,
                lessonId);
    }
}
