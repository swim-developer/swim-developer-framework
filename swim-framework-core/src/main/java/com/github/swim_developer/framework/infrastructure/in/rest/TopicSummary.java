package com.github.swim_developer.framework.infrastructure.in.rest;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TopicSummary(
        String topicId,
        String title,
        String description
) {
}
