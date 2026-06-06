package com.github.swim_developer.framework.provider.infrastructure.out.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;


@Converter
@Slf4j
public class StringListConverter implements AttributeConverter<List<String>, String> {

    
    private static final ObjectMapper MAPPER = new ObjectMapper();

    
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting list to JSON: {}", attribute, e);
            return null;
        }
    }

    
    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to list: {}", dbData, e);
            return Collections.emptyList();
        }
    }
}
