package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;

/**
 * SPI for publishing per-subscription heartbeat messages (provider-side).
 *
 * <p>Heartbeat messages travel in the same queue as business events. They are
 * distinguished by their AMQP {@code content-type} header, as required by
 * SWIM-TIYP-0037 (Yellow Profile, EUROCONTROL SPEC-170):</p>
 *
 * <ul>
 *   <li>Business events: {@code content-type = application/xml}</li>
 *   <li>Heartbeats:      {@code content-type = application/json} ({@link #HEARTBEAT_CONTENT_TYPE})</li>
 * </ul>
 *
 * <p>The provider iterates all active subscriptions and publishes a JSON heartbeat
 * to each subscription's business queue every 15 seconds (configurable).</p>
 */
public interface SubscriptionHeartbeatPublisher {

    /**
     * AMQP content-type value that identifies a heartbeat message.
     * Consumers route messages with this content-type to the heartbeat tracker
     * instead of the business inbox.
     */
    String HEARTBEAT_CONTENT_TYPE = "application/json";

    /**
     * Publishes a heartbeat to the subscription's business queue.
     *
     * @param queueName business queue name for the subscription
     * @param payload   heartbeat data
     */
    void publishHeartbeat(String queueName, SubscriptionHeartbeat payload);
}
