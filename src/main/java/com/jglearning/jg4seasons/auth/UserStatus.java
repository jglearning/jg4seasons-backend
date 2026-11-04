package com.jglearning.jg4seasons.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** user.status. inactive = 퇴원·비활성(로그인·refresh 불가). */
public enum UserStatus {
    ACTIVE,
    INACTIVE;

    @Converter
    static class DbConverter implements AttributeConverter<UserStatus, String> {

        @Override
        public String convertToDatabaseColumn(UserStatus status) {
            return status == null ? null : status.name().toLowerCase();
        }

        @Override
        public UserStatus convertToEntityAttribute(String value) {
            return value == null ? null : UserStatus.valueOf(value.toUpperCase());
        }
    }
}
