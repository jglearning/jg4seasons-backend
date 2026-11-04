package com.jglearning.jg4seasons.clazz;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** class.status. DB 값은 소문자. */
public enum ClassStatus {
    ACTIVE,
    FINISHED,
    ARCHIVED;

    @Converter
    static class DbConverter implements AttributeConverter<ClassStatus, String> {

        @Override
        public String convertToDatabaseColumn(ClassStatus status) {
            return status == null ? null : status.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public ClassStatus convertToEntityAttribute(String value) {
            return value == null ? null : ClassStatus.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
