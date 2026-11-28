package com.jglearning.jg4seasons.talk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학생 허용 행(ADR 0040): 행 없음 = 꺼짐, 학생마다 한 줄, 학원 범위 */
@IntegrationTest
@Transactional
class TalkStudentAccessStoreTest {

    @Autowired
    TalkStudentAccessStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long studentId;
    long operatorId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("허용 저장 학원");
        studentId = data.student(academyId, "학생");
        operatorId = data.operator("운영자");
    }

    @Test
    void 행이_없으면_꺼짐() {
        assertThat(store.isEnabled(academyId, studentId)).isFalse();
        assertThat(store.enabledStudents(academyId)).isEmpty();
    }

    @Test
    void 같은_학생을_두_번_쓰면_행_하나에_마지막_값() {
        store.upsert(academyId, List.of(studentId), true, operatorId);
        assertThat(store.isEnabled(academyId, studentId)).isTrue();

        store.upsert(academyId, List.of(studentId), false, operatorId);

        assertThat(store.isEnabled(academyId, studentId)).isFalse();
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void 다른_학원의_기존_행은_바꾸지_않는다() {
        store.upsert(academyId, List.of(studentId), true, operatorId);
        long otherAcademy = data.academy("다른 학원");

        store.upsert(otherAcademy, List.of(studentId), false, operatorId);

        assertThat(store.isEnabled(academyId, studentId)).isTrue();
        assertThat(store.isEnabled(otherAcademy, studentId)).isFalse();
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void enabledStudents는_그_학원의_켜진_학생만() {
        long off = data.student(academyId, "꺼진 학생");
        long otherAcademy = data.academy("다른 학원");
        long stranger = data.student(otherAcademy, "다른 학원 학생");
        store.upsert(academyId, List.of(studentId), true, operatorId);
        store.upsert(academyId, List.of(off), false, operatorId);
        store.upsert(otherAcademy, List.of(stranger), true, operatorId);

        assertThat(store.enabledStudents(academyId)).containsExactly(studentId);
    }

    private int rows(long student) {
        Integer count = jdbc.queryForObject(
                "select count(*) from talk_student_access where student_id = ?", Integer.class, student);
        return count == null ? 0 : count;
    }
}
