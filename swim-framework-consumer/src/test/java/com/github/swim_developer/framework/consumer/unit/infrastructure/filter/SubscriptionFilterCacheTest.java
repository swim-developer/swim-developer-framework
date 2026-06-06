package com.github.swim_developer.framework.consumer.unit.infrastructure.filter;

import com.github.swim_developer.framework.consumer.infrastructure.out.filter.SubscriptionFilterCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionFilterCacheTest {

    private SubscriptionFilterCache cache;

    @BeforeEach
    void setUp() {
        cache = new SubscriptionFilterCache();
    }

    @Test
    void isAllowed_returnsTrueForUnknownSubscription_noFilterConfigured() {
        assertThat(cache.isAllowed("sub-unknown", "airport", "EHAM")).isTrue();
    }

    @Test
    void isAllowed_returnsTrueWhenDimensionHasNoValues_emptyFilterMeansAllowAll() {
        cache.updateFilters("sub-1", "airport", List.of());

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
        assertThat(cache.isAllowed("sub-1", "airport", "LFPG")).isTrue();
    }

    @Test
    void isAllowed_returnsTrueWhenNullValuesProvided_treatedAsAllowAll() {
        cache.updateFilters("sub-1", "airport", null);

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
    }

    @Test
    void isAllowed_returnsTrueForValueInAllowList() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM", "LFPG", "EBBR"));

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
        assertThat(cache.isAllowed("sub-1", "airport", "LFPG")).isTrue();
    }

    @Test
    void isAllowed_returnsFalseForValueNotInAllowList() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM", "LFPG"));

        assertThat(cache.isAllowed("sub-1", "airport", "LGAV")).isFalse();
    }

    @Test
    void isAllowed_returnsTrueForUnknownDimension_noFilterForThatDimension() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));

        assertThat(cache.isAllowed("sub-1", "event_type", "RWY.CLS")).isTrue();
    }

    @Test
    void updateFilters_overwritesPreviousValuesForSameDimension() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-1", "airport", List.of("LFPG"));

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isFalse();
        assertThat(cache.isAllowed("sub-1", "airport", "LFPG")).isTrue();
    }

    @Test
    void updateFilters_supportMultipleDimensionsForSameSubscription() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-1", "event_type", List.of("RWY.CLS", "TWY.CLS"));

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
        assertThat(cache.isAllowed("sub-1", "airport", "LFPG")).isFalse();
        assertThat(cache.isAllowed("sub-1", "event_type", "RWY.CLS")).isTrue();
        assertThat(cache.isAllowed("sub-1", "event_type", "OBS.NEW")).isFalse();
    }

    @Test
    void removeSubscription_removesAllFiltersForThatSubscription() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-2", "airport", List.of("LFPG"));

        cache.removeSubscription("sub-1");

        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
        assertThat(cache.isAllowed("sub-2", "airport", "LFPG")).isTrue();
        assertThat(cache.isAllowed("sub-2", "airport", "EHAM")).isFalse();
    }

    @Test
    void removeSubscription_doesNotFailForUnknownSubscription() {
        cache.removeSubscription("ghost");

        assertThat(cache.size()).isZero();
    }

    @Test
    void size_reflectsNumberOfSubscriptionsWithFilters() {
        assertThat(cache.size()).isZero();

        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-2", "airport", List.of("LFPG"));

        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void clear_removesAllFilters() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-2", "airport", List.of("LFPG"));

        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.isAllowed("sub-1", "airport", "EHAM")).isTrue();
    }

    @Test
    void filtersAreIsolatedBetweenSubscriptions() {
        cache.updateFilters("sub-1", "airport", List.of("EHAM"));
        cache.updateFilters("sub-2", "airport", List.of("LFPG"));

        assertThat(cache.isAllowed("sub-1", "airport", "LFPG")).isFalse();
        assertThat(cache.isAllowed("sub-2", "airport", "EHAM")).isFalse();
    }
}
