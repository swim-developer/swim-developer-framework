package com.github.swim_developer.framework.unit.infrastructure.util;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.infrastructure.util.StringUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class StringUtilTest {

    @Test
    void isNullOrBlank_returnsTrueForNull() {
        assertThat(StringUtil.isNullOrBlank(null)).isTrue();
    }

    @Test
    void isNullOrBlank_returnsTrueForEmptyString() {
        assertThat(StringUtil.isNullOrBlank("")).isTrue();
    }

    @Test
    void isNullOrBlank_returnsTrueForWhitespaceOnly() {
        assertThat(StringUtil.isNullOrBlank("   \t\n")).isTrue();
    }

    @Test
    void isNullOrBlank_returnsFalseForNonBlankString() {
        assertThat(StringUtil.isNullOrBlank("EBBR")).isFalse();
    }

    @Test
    void hasValue_returnsFalseForNull() {
        assertThat(StringUtil.hasValue(null)).isFalse();
    }

    @Test
    void hasValue_returnsFalseForEmpty() {
        assertThat(StringUtil.hasValue("")).isFalse();
    }

    @Test
    void hasValue_returnsFalseForBlank() {
        assertThat(StringUtil.hasValue("  \t")).isFalse();
    }

    @Test
    void hasValue_returnsTrueForNonBlank() {
        assertThat(StringUtil.hasValue("swim-consumer")).isTrue();
    }

    @Test
    void hasValue_isComplementOfIsNullOrBlank() {
        String value = "test-value";
        assertThat(StringUtil.hasValue(value)).isEqualTo(!StringUtil.isNullOrBlank(value));
    }
}
