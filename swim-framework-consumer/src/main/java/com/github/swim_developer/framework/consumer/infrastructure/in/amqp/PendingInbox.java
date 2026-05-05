package com.github.swim_developer.framework.consumer.infrastructure.in.amqp;

import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;
import io.vertx.amqp.AmqpMessage;

public record PendingInbox(InboxEnvelope envelope, AmqpMessage message) {
}
