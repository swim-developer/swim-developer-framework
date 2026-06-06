package com.github.swim_developer.framework.application.model;

public record PreparedEvent<E>(ProcessingContext ctx, E event, String contentHash) {}
