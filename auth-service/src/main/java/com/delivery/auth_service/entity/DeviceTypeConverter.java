package com.delivery.auth_service.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DeviceTypeConverter implements AttributeConverter<AuthSession.DeviceType, String> {

    @Override
    public String convertToDatabaseColumn(AuthSession.DeviceType attribute) {
        if (attribute == null)
            return null;
        return attribute.toString(); // Sử dụng method toString() đã trả về chữ thường
    }

    @Override
    public AuthSession.DeviceType convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
        return AuthSession.DeviceType.fromString(dbData);
    }
}
