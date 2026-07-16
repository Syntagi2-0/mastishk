package com.syntagi.servicecatalog.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek dayOfWeek) {
        return dayOfWeek == null ? null : (short) dayOfWeek.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short databaseValue) {
        return databaseValue == null ? null : DayOfWeek.of(databaseValue);
    }
}
