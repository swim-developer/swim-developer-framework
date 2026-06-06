package com.github.swim_developer.framework.consumer.infrastructure.in.rest;

import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;

public final class ConsumerRestResponses {

    public static final String ERROR_KEY = "error";
    public static final String SUBSCRIPTION_NOT_FOUND_PREFIX = "Subscription not found: ";

    private ConsumerRestResponses() {
    }

    public static Response ok(Object entity) {
        return Response.ok(entity).build();
    }

    public static Response created(Object entity) {
        return Response.status(Response.Status.CREATED).entity(entity).build();
    }

    public static Response noContent() {
        return Response.noContent().build();
    }

    public static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(ERROR_KEY, message))
                .build();
    }

    public static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(ERROR_KEY, message))
                .build();
    }

    public static Response serviceUnavailable(String message) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of(ERROR_KEY, message))
                .build();
    }

    public static boolean isValidSubscriptionStatus(String status) {
        return SubscriptionStatus.ACTIVE.name().equals(status)
                || SubscriptionStatus.PAUSED.name().equals(status)
                || SubscriptionStatus.DELETED.name().equals(status);
    }

    public static Instant parseDateOrThrow(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid date format — expected ISO-8601 (e.g. 2025-12-01T00:00:00Z): " + dateStr);
        }
    }

    public static boolean isValidDateRange(Instant start, Instant end) {
        return !start.isAfter(end);
    }
}
