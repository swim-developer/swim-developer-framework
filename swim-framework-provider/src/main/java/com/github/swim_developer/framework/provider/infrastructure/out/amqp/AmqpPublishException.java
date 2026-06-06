package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

public class AmqpPublishException extends RuntimeException {

    public AmqpPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
