package com.github.swim_developer.framework.domain.model;


import java.util.List;

public record DeliveryResult(int matched, int delivered, int failed, List<FailedDeliveryInfo> failures) {

    public DeliveryResult(int matched, int delivered, int failed) {
        this(matched, delivered, failed, List.of());
    }
}
