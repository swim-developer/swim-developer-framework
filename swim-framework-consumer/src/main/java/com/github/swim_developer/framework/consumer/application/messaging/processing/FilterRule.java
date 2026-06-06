package com.github.swim_developer.framework.consumer.application.messaging.processing;

import java.util.function.Function;

public record FilterRule<E>(String dimension, Function<E, String> valueExtractor) {
}
