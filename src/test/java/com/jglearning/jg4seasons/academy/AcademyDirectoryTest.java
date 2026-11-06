package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
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
    void 타임존이_없으면_서울() {
        long noSettings = new TestData(jdbc).academy("설정 없음");
        long emptySettings = new TestData(jdbc).academy("빈 설정");
        jdbc.update("update academy set settings = '{}' where id = ?", emptySettings);

        assertThat(academies.zoneOf(noSettings)).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(academies.zoneOf(emptySettings)).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 설정한_타임존을_쓴다() {
        long id = new TestData(jdbc).academy("도쿄 학원");
        jdbc.update("update academy set settings = '{\"timezone\":\"Asia/Tokyo\"}' where id = ?", id);

        assertThat(academies.zoneOf(id)).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void 잘못된_타임존은_IllegalState() {
        long id = new TestData(jdbc).academy("잘못된 설정");
        jdbc.update("update academy set settings = '{\"timezone\":\"Mars/Base\"}' where id = ?", id);

        assertThatThrownBy(() -> academies.zoneOf(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 설정이_객체가_아니면_IllegalState() {
        long text = new TestData(jdbc).academy("문자열 설정");
        long array = new TestData(jdbc).academy("배열 설정");
        jdbc.update("update academy set settings = '\"Asia/Tokyo\"' where id = ?", text);
        jdbc.update("update academy set settings = '[]' where id = ?", array);

        assertThatThrownBy(() -> academies.zoneOf(text)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> academies.zoneOf(array)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 타임존이_문자열이_아니면_IllegalState() {
        long id = new TestData(jdbc).academy("숫자 타임존");
        jdbc.update("update academy set settings = '{\"timezone\":9}' where id = ?", id);

        assertThatThrownBy(() -> academies.zoneOf(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 없는_학원의_타임존은_IllegalState() {
        assertThatThrownBy(() -> academies.zoneOf(-1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void slug로_학원_id를_찾는다() {
        long id = new TestData(jdbc).academy("테스트 학원");
        String slug = jdbc.queryForObject("select slug from academy where id = ?", String.class, id);

        assertThat(academies.findIdBySlug(slug)).contains(id);
        assertThat(academies.findIdBySlug("no-such-slug")).isEmpty();
    }

    @Test
    void 관리_쓰기_학원_잠금은_트랜잭션_안에서_잡힌다() {
        long id = new TestData(jdbc).academy("잠금 학원");

        academies.lockForAdminWrite(id);
        academies.lockForAdminWrite(-1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 관리_쓰기_학원_잠금은_트랜잭션_밖에서_부르면_예외() {
        assertThatThrownBy(() -> academies.lockForAdminWrite(1L)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
