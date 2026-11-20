package com.jglearning.jg4seasons.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학생 설정 저장소(ADR 0035): 학생 1명당 1행, 처음 저장할 때 만든다. 읽기는 학원과 사용자를 함께 조건으로. */
@IntegrationTest
@Transactional
class UserSettingStoreTest {

    @Autowired
    UserSettingStore store;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long otherAcademyId;
    long studentId;
    long classmateId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        otherAcademyId = data.academy("다른 학원");
        studentId = data.student(academyId, "김학생");
        classmateId = data.student(academyId, "이학생");
    }

    private int rows(long userId) {
        return jdbc.queryForObject("select count(*) from user_setting where user_id = ?", Integer.class, userId);
    }

    @Test
    void 저장한_적_없으면_빈_값이고_읽어도_행이_생기지_않는다() {
        // 죽여야 하는 틀린 구현: 읽을 때 기본 행을 만드는 것
        assertThat(store.find(academyId, studentId)).isEmpty();
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 저장하면_그_학원의_그_학생에게만_보인다() {
        store.saveVoice(academyId, studentId, "uk_m");

        assertThat(store.find(academyId, studentId)).contains(new UserSettingStore.Row("uk_m"));
        // 죽여야 하는 틀린 구현: 사용자 조건을 빼는 것(같은 학원의 다른 학생에게 보인다)
        assertThat(store.find(academyId, classmateId)).as("같은 학원의 다른 학생").isEmpty();
        // 죽여야 하는 틀린 구현: 학원 조건을 빼는 것(다른 학원의 토큰으로 읽힌다)
        assertThat(store.find(otherAcademyId, studentId)).as("다른 학원으로 읽기").isEmpty();
    }

    @Test
    void 다시_저장하면_덮어쓰고_null이면_지우며_행은_하나다() {
        store.saveVoice(academyId, studentId, "us_w");
        store.saveVoice(academyId, studentId, "uk_m");
        assertThat(store.find(academyId, studentId)).contains(new UserSettingStore.Row("uk_m"));

        store.saveVoice(academyId, studentId, null);

        assertThat(store.find(academyId, studentId)).as("행은 남고 값만 비어 있다").contains(new UserSettingStore.Row(null));
        assertThat(rows(studentId)).isEqualTo(1);
    }

    @Test
    void 한_학생의_저장이_다른_학생의_값을_바꾸지_않는다() {
        store.saveVoice(academyId, studentId, "us_m");
        store.saveVoice(academyId, classmateId, "uk_w");

        assertThat(store.find(academyId, studentId)).contains(new UserSettingStore.Row("us_m"));
        assertThat(store.find(academyId, classmateId)).contains(new UserSettingStore.Row("uk_w"));
    }

    @Test
    void DB가_네_목소리_밖의_값을_거부한다() {
        // 코드의 검증을 건너뛰어도(직접 SQL, 다른 경로) 틀린 값이 남지 않는다
        assertThatThrownBy(() -> jdbc.update("insert into user_setting (user_id, voice) values (?, 'us_x')", studentId))
                .isInstanceOf(DataAccessException.class);
        // 표 기본 정렬(ai_ci)은 대소문자를 구분하지 않고 끝 공백도 무시할 수 있다 — 칸 정렬이 글자 그대로(utf8mb4_0900_bin)여야 거부된다
        for (String wrong : new String[] {"US_W", "Us_w", "us_w ", " us_w", ""}) {
            assertThatThrownBy(() -> store.saveVoice(academyId, studentId, wrong))
                    .as("[%s]", wrong)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThat(rows(studentId)).isZero();
    }

    @Test
    void 그_학원의_사용자가_아니면_저장되지_않는다() {
        // 죽여야 하는 틀린 구현: 쓰기에 학원 조건이 없는 것 — 호출자가 학원 확인을 빠뜨리면 다른 학원 학생의 행이 쓰인다
        store.saveVoice(otherAcademyId, studentId, "us_w");
        assertThat(rows(studentId)).as("다른 학원으로 처음 저장").isZero();

        store.saveVoice(academyId, studentId, "uk_m");
        store.saveVoice(otherAcademyId, studentId, "us_w");
        assertThat(store.find(academyId, studentId)).as("다른 학원으로 덮어쓰기").contains(new UserSettingStore.Row("uk_m"));

        store.saveVoice(academyId, -1L, "us_w");
        assertThat(rows(-1L)).as("없는 사용자").isZero();
    }
}
