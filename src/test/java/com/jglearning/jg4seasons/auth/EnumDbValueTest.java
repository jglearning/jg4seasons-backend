package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB·JWT의 enum 값(소문자)은 JVM 기본 로캘과 무관하다. 터키어 로캘에서는 i/I 대소문자 변환이 다르다(ı, İ). */
class EnumDbValueTest {

    Locale original;

    @BeforeEach
    void turkish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restore() {
        Locale.setDefault(original);
    }

    @Test
    void 역할_값은_로캘과_무관하게_소문자_영문이다() {
        assertThat(UserRole.ADMIN.value()).isEqualTo("admin");
        assertThat(new UserRole.DbConverter().convertToDatabaseColumn(UserRole.SUPERADMIN))
                .isEqualTo("superadmin");
        assertThat(new UserRole.DbConverter().convertToEntityAttribute("admin")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 상태와_인증_수단_값도_로캘과_무관하다() {
        assertThat(new UserStatus.DbConverter().convertToDatabaseColumn(UserStatus.INACTIVE))
                .isEqualTo("inactive");
        assertThat(new UserStatus.DbConverter().convertToEntityAttribute("inactive"))
                .isEqualTo(UserStatus.INACTIVE);
        assertThat(new AuthProvider.DbConverter().convertToEntityAttribute("kakao"))
                .isEqualTo(AuthProvider.KAKAO);
    }
}
