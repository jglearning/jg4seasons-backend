package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 음원 범위 /audio/<권 코드>-*(ADR 0031 결정 6)가 기대는 기준 데이터의 전제. 음원 키는 audio/<회차 코드>/…(import가 강제)라, 회차 코드가 자기 권 코드 + "-"로
 * 시작하고 다른 권 코드 + "-"로는 시작하지 않아야 권 하나만 열린다. 마이그레이션이 이 모양을 깨면 연계 학원 학생의 로그인이 500이 되거나(권 코드 형식) 음원이 조용히
 * 403이 되거나(접두어) 다른 권이 열린다.
 */
@IntegrationTest
class AudioScopeReferenceDataTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 모든_권_코드는_음원_범위_형식이다() {
        List<String> books = jdbc.queryForList("select code from book", String.class);

        assertThat(books).isNotEmpty();
        assertThat(books)
                .allSatisfy(code ->
                        assertThatCode(() -> AudioScope.book(code)).as(code).doesNotThrowAnyException());
    }

    @Test
    void 회차_코드는_자기_권_코드_대시로만_시작한다() {
        List<String> books = jdbc.queryForList("select code from book", String.class);
        List<Map<String, Object>> lessons =
                jdbc.queryForList("select l.code lesson, b.code book from lesson l join book b on b.id = l.book_id");

        assertThat(lessons).isNotEmpty();
        assertThat(lessons).allSatisfy(row -> {
            String lesson = (String) row.get("lesson");
            String own = (String) row.get("book");
            assertThat(lesson).as("자기 권").startsWith(own + "-");
            assertThat(books.stream()
                            .filter(other -> !other.equals(own))
                            .filter(other -> lesson.startsWith(other + "-")))
                    .as("다른 권 범위에 걸리는 회차 " + lesson)
                    .isEmpty();
        });
    }
}
