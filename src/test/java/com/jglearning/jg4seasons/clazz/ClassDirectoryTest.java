package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 입장에 쓰는 반 조회: 가입 코드 → 활성 반, 현재 소속 확인. */
@IntegrationTest
@Transactional
class ClassDirectoryTest {

    @Autowired
    ClassDirectory classes;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
    }

    @Test
    void 가입_코드로_활성_반을_찾는다() {
        String code = TestData.uniqueAccessCode();
        long classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", code, "active");

        assertThat(classes.findActiveByAccessCode(code))
                .contains(new ClassEntry(classId, academyId, data.bookId("L2-B1"), "겨울 그린 A반"));
    }

    @Test
    void 비활성_반은_가입_코드로_찾지_않는다() {
        String finished = TestData.uniqueAccessCode();
        String archived = TestData.uniqueAccessCode();
        data.clazz(academyId, "L2-B1", "끝난 반", finished, "finished");
        data.clazz(academyId, "L2-B1", "보관 반", archived, "archived");

        assertThat(classes.findActiveByAccessCode(finished)).isEmpty();
        assertThat(classes.findActiveByAccessCode(archived)).isEmpty();
    }

    @Test
    void 없는_가입_코드는_찾지_않는다() {
        assertThat(classes.findActiveByAccessCode(TestData.uniqueAccessCode())).isEmpty();
    }

    @Test
    void 현재_소속만_멤버다() {
        long classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        long classB = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
        long current = data.student(academyId, "현재");
        long left = data.student(academyId, "퇴원");
        data.enroll(classA, current);
        data.enroll(classA, left);
        data.leave(classA, left);

        assertThat(classes.isCurrentMember(academyId, classA, current)).isTrue();
        assertThat(classes.isCurrentMember(academyId, classA, left)).isFalse();
        assertThat(classes.isCurrentMember(academyId, classB, current)).isFalse();
    }

    @Test
    void 다른_학원_반으로는_소속을_확인하지_않는다() {
        long classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        long student = data.student(academyId, "학생");
        data.enroll(classId, student);
        long otherAcademy = data.academy("다른 학원");

        assertThat(classes.isCurrentMember(otherAcademy, classId, student)).isFalse();
    }
}
