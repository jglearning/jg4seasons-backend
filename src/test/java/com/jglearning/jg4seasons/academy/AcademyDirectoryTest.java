package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class AcademyDirectoryTest {

    @Autowired
    AcademyDirectory academies;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 학원_이름을_돌려준다() {
        long id = new TestData(jdbc).academy("테스트 학원");

        assertThat(academies.nameOf(id)).isEqualTo("테스트 학원");
    }

    @Test
    void 없는_학원_이름은_예외다() {
        assertThatThrownBy(() -> academies.nameOf(-1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 정지된_학원은_활성이_아니다() {
        long active = new TestData(jdbc).academy("운영 학원");
        long suspended = new TestData(jdbc).academy("정지 학원");
        jdbc.update("update academy set status = 'suspended' where id = ?", suspended);

        assertThat(academies.isActive(active)).isTrue();
        assertThat(academies.isActive(suspended)).isFalse();
        assertThat(academies.isActive(-1L)).as("없는 학원").isFalse();
    }

    @Test
    void slug로_학원_id를_찾는다() {
        long id = new TestData(jdbc).academy("테스트 학원");
        String slug = jdbc.queryForObject("select slug from academy where id = ?", String.class, id);

        assertThat(academies.findIdBySlug(slug)).contains(id);
        assertThat(academies.findIdBySlug("no-such-slug")).isEmpty();
    }
}
