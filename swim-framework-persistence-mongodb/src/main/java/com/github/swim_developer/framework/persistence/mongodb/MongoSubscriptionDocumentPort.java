package com.github.swim_developer.framework.persistence.mongodb;

public interface MongoSubscriptionDocumentPort {

    String getSubscriptionId();

    String getQueueName();

    String getSubscriptionStatus();

    void setSubscriptionStatus(String status);

    String getType();
}
