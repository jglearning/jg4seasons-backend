package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** 반 상태 DB 값은 JVM 기본 로캘과 무관하다(터키어 로캘의 i/I). */
class ClassStatusDbValueTest {

    @Test
    void 반_상태_값은_로캘과_무관하다() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(new ClassStatus.DbConverter().convertToDatabaseColumn(ClassStatus.ARCHIVED))
                    .isEqualTo("archived");
            assertThat(new ClassStatus.DbConverter().convertToEntityAttribute("archived"))
                    .isEqualTo(ClassStatus.ARCHIVED);
        } finally {
            Locale.setDefault(original);
        }
    }
}
