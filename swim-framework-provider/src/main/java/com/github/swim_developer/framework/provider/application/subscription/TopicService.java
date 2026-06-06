package com.github.swim_developer.framework.provider.application.subscription;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class TopicService {

    @ConfigProperty(name = "swim.topics")
    List<String> configuredTopics;

    public List<String> getAllTopics() {
        log.debug("Retrieving all configured topics: {}", configuredTopics);
        return configuredTopics;
    }

    public String getTopic(String topicId) {
        log.debug("Retrieving topic: {}", topicId);
        return findTopic(topicId)
                .orElseThrow(() -> {
                    log.warn("Topic not found: {}", topicId);
                    return new NotFoundException("Topic not found: " + topicId);
                });
    }

    public void validateTopicActiveForSubscription(String topicId) {
        findTopic(topicId)
                .orElseThrow(() -> {
                    log.warn("Cannot subscribe to non-existent topic: {}", topicId);
                    return new IllegalArgumentException("Topic not available for subscription: " + topicId);
                });
    }

    private Optional<String> findTopic(String topicId) {
        return configuredTopics.stream()
                .filter(t -> TopicWildcardMatcher.matches(t, topicId)
                          || TopicWildcardMatcher.matches(topicId, t))
                .findFirst();
    }
}
