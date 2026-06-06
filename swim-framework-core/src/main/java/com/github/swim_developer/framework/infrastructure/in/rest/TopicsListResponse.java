package com.github.swim_developer.framework.infrastructure.in.rest;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record TopicsListResponse(List<TopicSummary> topics) {
}
