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
    public List<Long> teacherIdsOf(long classId) {
        return jdbc.queryForList(
                "select user_id from class_teacher where class_id = ? order by user_id", Long.class, classId);
    }

    /**
     * 교체 직전 읽기. 잠금 읽기(FOR UPDATE)라 다른 트랜잭션이 방금 커밋한 행까지 본다 — 일반 SELECT는 REPEATABLE READ에서 트랜잭션의 첫 읽기
     * 시점 스냅샷을 보므로, 반 행 잠금을 기다린 뒤에도 옛 목록을 읽어 이미 있는 교사를 다시 넣는다(PK 중복 500). 반이 그 학원 것인지 이미 확인하고 반
     * 행을 잠근 뒤에 부른다
     */
    public List<Long> teacherIdsOfForUpdate(long classId) {
        return jdbc.queryForList(
                "select user_id from class_teacher where class_id = ? order by user_id for update",
                Long.class,
                classId);
    }

    /** 반이 그 학원 것인지 이미 확인한 뒤에 부른다. PK 한 행만 지운다 */
    public void delete(long classId, long teacherId) {
        jdbc.update("delete from class_teacher where class_id = ? and user_id = ?", classId, teacherId);
    }

    /** 반이 그 학원 것인지 이미 확인한 뒤에 부른다 */
    public void insert(long classId, long teacherId) {
        jdbc.update("insert into class_teacher (class_id, user_id) values (?, ?)", classId, teacherId);
    }
}
