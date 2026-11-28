package com.jglearning.jg4seasons.talk.persistence;

import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 학생 허용 표(ADR 0040). 행이 없으면 꺼짐. 모든 읽기·쓰기는 academy_id 조건을 단다. 쓰기와 여러 명 읽기는 auth가 그 학원 학생으로 확인한 묶음만 받는다(한 명 읽기는
 * JWT의 학원·학생). 시각은 UTC LocalDateTime
 */
@Component
public class TalkStudentAccessStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    TalkStudentAccessStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean isEnabled(long academyId, long studentId) {
        List<Boolean> found = jdbc.queryForList(
                "select enabled from talk_student_access where academy_id = :academy and student_id = :student",
                new MapSqlParameterSource().addValue("academy", academyId).addValue("student", studentId),
                Boolean.class);
        return !found.isEmpty() && Boolean.TRUE.equals(found.get(0));
    }

    /** 묶음 안에서 켜진 학생(수정 불가). 빈 묶음이면 빈 집합 */
    public Set<Long> enabledAmong(AcademyStudents students) {
        if (students.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList(
                "select student_id from talk_student_access where academy_id = :academy and enabled = true"
                        + " and student_id in (:ids)",
                new MapSqlParameterSource()
                        .addValue("academy", students.academyId())
                        .addValue("ids", students.ids()),
                Long.class));
    }

    /**
     * 학생마다 한 줄로 켜짐·꺼짐을 쓴다. 같은 값이면 enabled는 그대로, 마지막 변경자·시각은 갱신. 학생 고유 키에 걸린 기존 행이 다른 학원 것이면 바꾸지 않고
     * IllegalStateException — 확인된 묶음이면 생기지 않지만(학생의 학원은 바뀌지 않음) 생기면 조용히 성공으로 넘기지 않는다(호출자 트랜잭션이 되돌린다)
     */
    public void upsert(AcademyStudents students, boolean enabled, long actorId) {
        if (students.isEmpty()) {
            return;
        }
        long academyId = students.academyId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
        MapSqlParameterSource[] batch = students.ids().stream()
                .map(studentId -> new MapSqlParameterSource()
                        .addValue("academy", academyId)
                        .addValue("student", studentId)
                        .addValue("enabled", enabled)
                        .addValue("actor", actorId)
                        .addValue("now", now))
                .toArray(MapSqlParameterSource[]::new);
        // 같은 학원 행일 때만 세 칸을 바꾼다. 조건을 칸마다 거는 이유: ON DUPLICATE KEY UPDATE에는 WHERE가 없다
        jdbc.batchUpdate(
                "insert into talk_student_access (academy_id, student_id, enabled, changed_by, created_at, updated_at)"
                        + " values (:academy, :student, :enabled, :actor, :now, :now) as new"
                        + " on duplicate key update"
                        + " enabled = if(talk_student_access.academy_id = new.academy_id, new.enabled,"
                        + " talk_student_access.enabled),"
                        + " changed_by = if(talk_student_access.academy_id = new.academy_id, new.changed_by,"
                        + " talk_student_access.changed_by),"
                        + " updated_at = if(talk_student_access.academy_id = new.academy_id, new.updated_at,"
                        + " talk_student_access.updated_at)",
                batch);
        Integer foreign = jdbc.queryForObject(
                "select count(*) from talk_student_access where student_id in (:ids) and academy_id <> :academy",
                new MapSqlParameterSource().addValue("ids", students.ids()).addValue("academy", academyId),
                Integer.class);
        if (foreign != null && foreign > 0) {
            throw new IllegalStateException("다른 학원 행이 있어 프리토킹 허용을 쓰지 못했다: academy " + academyId + ", " + foreign + "명");
        }
    }
}
