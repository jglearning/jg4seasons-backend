package com.jglearning.jg4seasons.talk.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.AcademyStudents;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학생 허용 행(ADR 0040): 행 없음 = 꺼짐, 학생마다 한 줄, 학원 범위. 쓰기·여러 명 읽기는 auth가 확인한 묶음만 받는다 */
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
        assertThat(store.enabledAmong(students(academyId, studentId))).isEmpty();
    }

    @Test
    void 같은_학생을_두_번_쓰면_행_하나에_마지막_값() {
        store.upsert(students(academyId, studentId), true, operatorId);
        assertThat(store.isEnabled(academyId, studentId)).isTrue();

        store.upsert(students(academyId, studentId), false, operatorId);

        assertThat(store.isEnabled(academyId, studentId)).isFalse();
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void 다른_학원의_기존_행에_걸리면_예외이고_기존_값은_그대로() {
        // 학원 확인을 거친 묶음이면 생기지 않는다. 생기면 조용히 넘어가지 않고 실패(#96 리뷰 5)
        store.upsert(students(academyId, studentId), true, operatorId);
        long otherAcademy = data.academy("다른 학원");

        assertThatThrownBy(() -> store.upsert(students(otherAcademy, studentId), false, operatorId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.isEnabled(academyId, studentId)).isTrue();
        assertThat(store.isEnabled(otherAcademy, studentId)).isFalse();
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void enabledAmong은_묶음_안에서_그_학원의_켜진_학생만() {
        long off = data.student(academyId, "꺼진 학생");
        long outside = data.student(academyId, "묶음 밖 학생");
        store.upsert(students(academyId, studentId), true, operatorId);
        store.upsert(students(academyId, off), false, operatorId);
        store.upsert(students(academyId, outside), true, operatorId);

        assertThat(store.enabledAmong(students(academyId, studentId, off))).containsExactly(studentId);
        assertThat(store.enabledAmong(students(academyId))).isEmpty();
    }

    private static AcademyStudents students(long academy, long... ids) {
        return AcademyStudents.of(academy, Arrays.stream(ids).boxed().toList());
    }

    private int rows(long student) {
        Integer count = jdbc.queryForObject(
                "select count(*) from talk_student_access where student_id = ?", Integer.class, student);
        return count == null ? 0 : count;
    }
}
