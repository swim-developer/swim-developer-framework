package com.github.swim_developer.framework.provider.unit.infrastructure;

import java.util.Collections;
import java.util.List;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.infrastructure.out.converter.StringListConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    void convertToDatabaseColumnReturnsNullForNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumnReturnsNullForEmptyList() {
        assertThat(converter.convertToDatabaseColumn(Collections.emptyList())).isNull();
    }

    @Test
    void convertToDatabaseColumnSerializesListAsJson() {
        String json = converter.convertToDatabaseColumn(List.of("EHAM", "EBBR", "LFPG"));

        assertThat(json).isEqualTo("[\"EHAM\",\"EBBR\",\"LFPG\"]");
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForNull() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForBlank() {
        assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
    }

    @Test
    void convertToEntityAttributeDeserializesJson() {
        List<String> result = converter.convertToEntityAttribute("[\"ESSA\",\"ENGM\"]");

        assertThat(result).containsExactly("ESSA", "ENGM");
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForMalformedJson() {
        assertThat(converter.convertToEntityAttribute("{not-valid-json")).isEmpty();
    }

    @Test
    void roundTripPreservesData() {
        List<String> original = List.of("RWY.CLS", "SAA.ACT", "OBS.NEW");

        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).isEqualTo(original);
    }
}
