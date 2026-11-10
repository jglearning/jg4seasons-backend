package com.jglearning.jg4seasons.clazz.persistence;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 반 진도표(class_session) 읽기·쓰기. 이 테이블에는 academy_id가 없어, 반을 학원 범위로 확인한 뒤에만 부른다(ClassManagementService·ClassSchedulesService 등).
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

    /** 진도표를 바꿀 때 쓸 행. id null = 새 행. seq는 replace가 목록 순서로 매긴다 */
    public record SessionWrite(Long id, long lessonId, LocalDate scheduledDate, SessionStatus status, String note) {}

    /**
     * 반 진도표를 rows로 바꾼다(ADR 0030). 호출자가 반 행을 잠근 트랜잭션 안에서만. 유일 키 (class_id, seq)가 중간에 겹치지 않게 먼저 그 반의 seq를 음수로 비켜 두고
     * 행마다 고친 뒤 새 행을 넣는다. seq = 목록 순서(1부터, 빈틈 없음) — "경로 seq = 진도표 위치"라는 계약을 여기 한 곳에서 지킨다. rows는 그 반의 기존 행을 모두
     * 포함해야 한다(빠진 행은 음수 seq로 남아 예외 — 호출자 버그)
     */
    public void replace(long classId, List<SessionWrite> rows) {
        jdbc.update("update class_session set seq = -seq where class_id = ?", classId);
        for (int i = 0; i < rows.size(); i++) {
            SessionWrite row = rows.get(i);
            int seq = i + 1;
            if (row.id() == null) {
                jdbc.update(
                        "insert into class_session (class_id, seq, lesson_id, scheduled_date, status, note)"
                                + " values (?, ?, ?, ?, ?, ?)",
                        classId,
                        seq,
                        row.lessonId(),
                        row.scheduledDate(),
                        row.status().value(),
                        row.note());
            } else {
                int updated = jdbc.update(
                        "update class_session set seq = ?, scheduled_date = ?, status = ?, note = ?"
                                + " where id = ? and class_id = ?",
                        seq,
                        row.scheduledDate(),
                        row.status().value(),
                        row.note(),
                        row.id(),
                        classId);
                if (updated != 1) {
                    throw new IllegalStateException("진도표 행이 그 반에 없다: class " + classId + " id " + row.id());
                }
            }
        }
        // rows에 빠진 행이 있으면 음수 seq로 남는다 — 조용히 버리지 않고 트랜잭션째 되돌린다
        Integer left = jdbc.queryForObject(
                "select count(*) from class_session where class_id = ? and seq < 0", Integer.class, classId);
        if (left == null || left != 0) {
            throw new IllegalStateException("진도표를 바꾼 뒤 빠진 행이 있다: class " + classId + " " + left + "행");
        }
    }

    /**
     * 편집용 읽기(seq 순): 잠금 읽기라 이 트랜잭션의 앞선 일반 읽기 스냅숏(REPEATABLE READ)이 아니라 가장 최근에 커밋된 행을 본다. 반 행을 잠근 뒤에 부른다 — 일반
     * 읽기로 하면 먼저 끝난 다른 편집의 새 행을 못 보고 옛 진도표로 덮어쓴다(TeacherScheduleCommitTest)
     */
    public List<SessionRow> findByClassForUpdate(long classId) {
        return jdbc.query(
                "select id, seq, lesson_id, scheduled_date, status, note from class_session where class_id = ?"
                        + " order by seq for update",
                ClassSessionStore::row,
                classId);
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
