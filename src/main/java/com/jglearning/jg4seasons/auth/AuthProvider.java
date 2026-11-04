package com.jglearning.jg4seasons.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** auth_identity.provider. DB 값은 소문자(personal_code 등). */
public enum AuthProvider {
    PERSONAL_CODE,
    PASSWORD,
    KAKAO;

    @Converter
    static class DbConverter implements AttributeConverter<AuthProvider, String> {

        @Override
        public String convertToDatabaseColumn(AuthProvider provider) {
            return provider == null ? null : provider.name().toLowerCase();
        }

        @Override
        public AuthProvider convertToEntityAttribute(String value) {
            return value == null ? null : AuthProvider.valueOf(value.toUpperCase());
        }
    }
}
