package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** class.status ↔ ClassStatus. DB 값은 ClassStatus.value()(소문자). */
@Converter
class ClassStatusConverter implements AttributeConverter<ClassStatus, String> {

    @Override
    public String convertToDatabaseColumn(ClassStatus status) {
        return status == null ? null : status.value();
    }

    @Override
    public ClassStatus convertToEntityAttribute(String value) {
        return value == null ? null : ClassStatus.fromValue(value);
    }
}
