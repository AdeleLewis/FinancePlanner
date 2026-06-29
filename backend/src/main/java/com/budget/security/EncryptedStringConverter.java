package com.budget.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently encrypts a {@code String} entity attribute at rest via {@link EncryptionService}. Apply with
 * {@code @Convert(converter = EncryptedStringConverter.class)} on secret columns; Hibernate then encrypts on
 * write and decrypts on read, so entities and JPQL queries are unaffected.
 *
 * <p>Registered as a Spring bean so {@link EncryptionService} can be injected — Spring Boot wires
 * {@code @Converter} beans into Hibernate through its {@code SpringBeanContainer}.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    public EncryptedStringConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}
