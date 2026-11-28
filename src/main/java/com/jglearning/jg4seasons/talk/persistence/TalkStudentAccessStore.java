package com.jglearning.jg4seasons.talk.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 학생 허용 표(ADR 0040). 행이 없으면 꺼짐. 모든 읽기·쓰기는 academy_id 조건을 단다 — 학생이 그 학원 학생인지는 부르는 쪽이 확인한다(쓰기는
 * AcademyStudents로). 시각은 UTC LocalDateTime
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

    public Set<Long> enabledStudents(long academyId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "select student_id from talk_student_access where academy_id = :academy and enabled = true"
                        + " order by student_id",
                new MapSqlParameterSource("academy", academyId),
                Long.class));
    }

    /**
     * 학생마다 한 줄로 켜짐·꺼짐을 쓴다(같은 값이면 그대로). 학생 고유 키에 걸린 기존 행이 다른 학원 것이면 바꾸지 않는다 — 학원 범위 밖 쓰기를 막는 마지막 조건
     */
    public void upsert(long academyId, List<Long> studentIds, boolean enabled, long actorId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
        MapSqlParameterSource[] batch = studentIds.stream()
                .distinct()
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
    }
}
