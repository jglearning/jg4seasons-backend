package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 교사 담당 반(class_teacher). 반은 항상 그 학원 것인지 확인한다(다른 학원 반이 섞이면 404, 아무것도 안 바뀜). */
@IntegrationTest
@Transactional
class ClassTeachersTest {

    @Autowired
    ClassTeachers classTeachers;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long otherAcademy;
    long classA;
    long classB;
    long otherClass;
    long teacherId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        otherAcademy = data.academy("다른 학원");
        classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        classB = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
        otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        teacherId = data.staff(academyId, "교사", "teacher");
    }

    @Test
    void 배정을_교체한다() {
        classTeachers.replace(academyId, teacherId, Set.of(classA));
        assertThat(classTeachers.classIdsOf(academyId, teacherId)).containsExactly(classA);

        classTeachers.replace(academyId, teacherId, Set.of(classB, classA));

        assertThat(classTeachers.classIdsOf(academyId, teacherId)).containsExactly(classA, classB);
    }

    @Test
    void 빈_목록이면_모두_해제한다() {
        classTeachers.replace(academyId, teacherId, Set.of(classA, classB));

        classTeachers.replace(academyId, teacherId, Set.of());

        assertThat(classTeachers.classIdsOf(academyId, teacherId)).isEmpty();
    }

    @Test
    void 다른_학원_반이_섞이면_404이고_아무것도_안_바뀐다() {
        classTeachers.replace(academyId, teacherId, Set.of(classA));

        assertThatThrownBy(() -> classTeachers.replace(academyId, teacherId, Set.of(classB, otherClass)))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(classTeachers.classIdsOf(academyId, teacherId)).containsExactly(classA);
    }

    @Test
    void 없는_반_id도_404() {
        assertThatThrownBy(() -> classTeachers.replace(academyId, teacherId, Set.of(-1L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 다른_학원의_배정은_건드리지도_보이지도_않는다() {
        // 같은 사용자 id에 다른 학원 반 배정이 있어도(잘못된 데이터) 이 학원 범위만 읽고 바꾼다
        data.assignTeacher(otherClass, teacherId);

        classTeachers.replace(academyId, teacherId, Set.of(classA));

        assertThat(classTeachers.classIdsOf(academyId, teacherId)).containsExactly(classA);
        assertThat(classTeachers.classIdsOf(otherAcademy, teacherId)).containsExactly(otherClass);
    }
}
