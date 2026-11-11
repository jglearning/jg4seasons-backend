package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 누가 어느 음원 범위를 받나(ADR 0031 결정 6). 범위는 하나뿐이라 하나로 좁힐 수 없으면 전체 */
class AudioScopeRuleTest {

    @Test
    void 운영자는_음원_쿠키가_없다() {
        assertThat(AudioScopeRule.of(UserRole.SUPERADMIN, false, List.of())).isEqualTo(AudioScope.none());
        assertThat(AudioScopeRule.of(UserRole.SUPERADMIN, true, List.of("L2-B1")))
                .isEqualTo(AudioScope.none());
    }

    @Test
    void 교사_관리자는_사용권과_상관없이_전체() {
        for (UserRole role : List.of(UserRole.TEACHER, UserRole.ADMIN)) {
            assertThat(AudioScopeRule.of(role, false, List.of())).isEqualTo(AudioScope.all());
            assertThat(AudioScopeRule.of(role, true, List.of())).isEqualTo(AudioScope.all());
            assertThat(AudioScopeRule.of(role, true, List.of("L2-B1"))).isEqualTo(AudioScope.all());
        }
    }

    @Test
    void 직영_학원_학생은_전체() {
        assertThat(AudioScopeRule.of(UserRole.STUDENT, false, List.of())).isEqualTo(AudioScope.all());
        assertThat(AudioScopeRule.of(UserRole.STUDENT, false, List.of("L2-B1"))).isEqualTo(AudioScope.all());
    }

    @Test
    void 연계_학원_학생이_쓸_수_있는_권이_없으면_없음() {
        assertThat(AudioScopeRule.of(UserRole.STUDENT, true, List.of())).isEqualTo(AudioScope.none());
    }

    @Test
    void 연계_학원_학생의_권이_하나면_그_권() {
        assertThat(AudioScopeRule.of(UserRole.STUDENT, true, List.of("L2-B1"))).isEqualTo(AudioScope.book("L2-B1"));
    }

    @Test
    void 같은_권_반이_둘이어도_그_권() {
        assertThat(AudioScopeRule.of(UserRole.STUDENT, true, List.of("L2-B1", "L2-B1")))
                .isEqualTo(AudioScope.book("L2-B1"));
    }

    @Test
    void 연계_학원_학생의_권이_둘_이상이면_전체() {
        assertThat(AudioScopeRule.of(UserRole.STUDENT, true, List.of("L2-B1", "K-B1")))
                .isEqualTo(AudioScope.all());
    }
}
