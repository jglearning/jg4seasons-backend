package com.jglearning.jg4seasons.clazz.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * class_teacher 읽기·쓰기. 이 테이블에는 academy_id가 없어(PK class_id·user_id) 모든 쿼리가 같은 모듈의 class와 조인해 학원 범위를 건다.
 */
@Component
public class ClassTeacherStore {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ClassTeacherStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<Long> classIdsOf(long academyId, long teacherId) {
        return jdbc.queryForList(
                "select ct.class_id from class_teacher ct join `class` c on c.id = ct.class_id"
                        + " where c.academy_id = ? and ct.user_id = ? order by ct.class_id",
                Long.class,
                academyId,
                teacherId);
    }

    /** classIds 중 그 학원 반의 수 */
    public int countClassesInAcademy(long academyId, Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return 0;
        }
        Integer count = named.queryForObject(
                "select count(*) from `class` where academy_id = :academyId and id in (:ids)",
                new MapSqlParameterSource("academyId", academyId).addValue("ids", classIds),
                Integer.class);
        return count == null ? 0 : count;
    }

    /** 그 학원 반에 대한 이 교사의 배정만 지운다 */
    public void deleteInAcademy(long academyId, long teacherId) {
        jdbc.update(
                "delete ct from class_teacher ct join `class` c on c.id = ct.class_id"
                        + " where c.academy_id = ? and ct.user_id = ?",
                academyId,
                teacherId);
    }

    /** 반이 그 학원 것인지 이미 확인한 뒤에 부른다 */
    public void insert(long classId, long teacherId) {
        jdbc.update("insert into class_teacher (class_id, user_id) values (?, ?)", classId, teacherId);
    }
}
