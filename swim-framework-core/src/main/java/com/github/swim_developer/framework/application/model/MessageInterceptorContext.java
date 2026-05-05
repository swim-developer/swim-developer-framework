package com.github.swim_developer.framework.application.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageInterceptorContext {

    private final String subscriptionId;
    private final String queueName;
    private final String amqpMessageId;
    private final int messageIndex;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public MessageInterceptorContext(String subscriptionId, String queueName,
                                     String amqpMessageId, int messageIndex) {
        this.subscriptionId = subscriptionId;
        this.queueName = queueName;
        this.amqpMessageId = amqpMessageId;
        this.messageIndex = messageIndex;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getAmqpMessageId() {
        return amqpMessageId;
    }

    public int getMessageIndex() {
        return messageIndex;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
}
