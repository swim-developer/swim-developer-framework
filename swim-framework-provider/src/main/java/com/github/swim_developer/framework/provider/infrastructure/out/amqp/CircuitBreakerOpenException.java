package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String queue) {
        super("Circuit breaker OPEN for queue: " + queue);
    }
}
