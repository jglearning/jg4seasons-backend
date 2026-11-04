package com.jglearning.jg4seasons.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** user.role. DB·응답·JWT role 값은 소문자(student 등). */
public enum UserRole {
    STUDENT,
    TEACHER,
    ADMIN,
    SUPERADMIN;

    /** DB·응답·JWT에 쓰는 값 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Converter
    static class DbConverter implements AttributeConverter<UserRole, String> {

        @Override
        public String convertToDatabaseColumn(UserRole role) {
            return role == null ? null : role.value();
        }

        @Override
        public UserRole convertToEntityAttribute(String value) {
            return value == null ? null : UserRole.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
