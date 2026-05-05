package com.github.swim_developer.framework.unit.infrastructure.util;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.infrastructure.util.HashUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class HashUtilTest {

    @Test
    void sha256_producesKnownHash() {
        assertThat(HashUtil.sha256("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256_isDeterministic() {
        assertThat(HashUtil.sha256("same-input")).isEqualTo(HashUtil.sha256("same-input"));
    }

    @Test
    void sha256_produces64CharHexString() {
        assertThat(HashUtil.sha256("any-value"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void sha256_differentInputsProduceDifferentHashes() {
        assertThat(HashUtil.sha256("input-a")).isNotEqualTo(HashUtil.sha256("input-b"));
    }

    @Test
    void sha256_returnsEmptyForNull() {
        assertThat(HashUtil.sha256(null)).isEmpty();
    }

    @Test
    void sha256_returnsEmptyForEmptyString() {
        assertThat(HashUtil.sha256("")).isEmpty();
    }

    @Test
    void sha256_isCaseSensitive() {
        assertThat(HashUtil.sha256("Hello")).isNotEqualTo(HashUtil.sha256("hello"));
    }
}
