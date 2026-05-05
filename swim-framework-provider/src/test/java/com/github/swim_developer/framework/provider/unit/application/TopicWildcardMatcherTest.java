package com.github.swim_developer.framework.provider.unit.application;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.application.subscription.TopicWildcardMatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa lógica de matching de tópicos com wildcards AMQP.
 *
 * Garantias de Negócio:
 * 1. Exact match funciona sem wildcards
 * 2. * casa exatamente um segmento
 * 3. # casa zero ou mais segmentos
 * 4. Combinação de * e # funciona
 * 5. Nomes de serviços reais SWIM funcionam
 * 6. Casos limite (null, segmentos a mais) não casam
 */
@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class TopicWildcardMatcherTest {

    @Test
    void exactMatch_withoutWildcard() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", "DigitalNOTAMService")).isTrue();
    }

    @Test
    void exactMatch_differentTopics() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", "ArrivalSequenceService")).isFalse();
    }

    @Test
    void singleStar_matchesOneSingleSegment() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY", "DigitalNOTAMService.*")).isTrue();
    }

    @Test
    void singleStar_doesNotMatchMultipleSegments() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.*")).isFalse();
    }

    @Test
    void singleStar_doesNotMatchZeroSegments() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", "DigitalNOTAMService.*")).isFalse();
    }

    @Test
    void singleStar_inMiddle_matchesExactly() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.*.CLS")).isTrue();
    }

    @Test
    void singleStar_inMiddle_doesNotMatchWrongEnd() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.SAA", "DigitalNOTAMService.*.CLS")).isFalse();
    }

    @Test
    void hash_matchesZeroRemainingSegments_amqpConvention() {
        // AMQP standard: a.# matches a (zero remaining segments after the prefix)
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", "DigitalNOTAMService.#")).isTrue();
    }

    @Test
    void hash_matchesOneSegment() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY", "DigitalNOTAMService.#")).isTrue();
    }

    @Test
    void hash_matchesMultipleSegments() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.#")).isTrue();
    }

    @Test
    void hash_matchesDeeplyNested() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS.EHAM", "DigitalNOTAMService.#")).isTrue();
    }

    @Test
    void hashAlone_matchesAnything() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS", "#")).isTrue();
        assertThat(TopicWildcardMatcher.matches("ArrivalSequenceService", "#")).isTrue();
    }

    @Test
    void hashAtEnd_afterStar() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS.EHAM", "DigitalNOTAMService.*.#")).isTrue();
    }

    @Test
    void realSwimTopics_dnotamExactSubscription() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", "DigitalNOTAMService")).isTrue();
    }

    @Test
    void realSwimTopics_dnotamWildcardSubscription() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RunwayClosure", "DigitalNOTAMService.*")).isTrue();
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.AirspaceActivation", "DigitalNOTAMService.*")).isTrue();
    }

    @Test
    void realSwimTopics_dnotamDeepWildcard() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.RWY.CLS.EHAM", "DigitalNOTAMService.#")).isTrue();
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService.SAA.ACT.EHAA", "DigitalNOTAMService.#")).isTrue();
    }

    @Test
    void nullTopic_returnsFalse() {
        assertThat(TopicWildcardMatcher.matches(null, "DigitalNOTAMService.*")).isFalse();
    }

    @Test
    void nullPattern_returnsFalse() {
        assertThat(TopicWildcardMatcher.matches("DigitalNOTAMService", null)).isFalse();
    }

    @Test
    void bothNull_returnsFalse() {
        assertThat(TopicWildcardMatcher.matches(null, null)).isFalse();
    }

    @Test
    void wrongRoot_doesNotMatch() {
        assertThat(TopicWildcardMatcher.matches("ArrivalSequenceService.RWY", "DigitalNOTAMService.*")).isFalse();
    }
}
