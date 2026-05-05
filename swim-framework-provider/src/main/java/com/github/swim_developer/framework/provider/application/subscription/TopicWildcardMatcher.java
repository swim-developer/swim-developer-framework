package com.github.swim_developer.framework.provider.application.subscription;

/**
 * Matches topic names against AMQP-style wildcard patterns.
 *
 * <p>Conventions (aligned with AMQP 1.0 / JMS selector patterns):</p>
 * <ul>
 *   <li>{@code *} — matches exactly one dot-delimited segment</li>
 *   <li>{@code #} — matches zero or more dot-delimited segments</li>
 * </ul>
 *
 * <p>Examples:</p>
 * <pre>
 *   matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.*")      → true
 *   matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.#")      → true
 *   matches("DigitalNOTAMService.RWY.CLS", "DigitalNOTAMService.*.CLS")  → true
 *   matches("DigitalNOTAMService",          "DigitalNOTAMService")        → true
 *   matches("DigitalNOTAMService.RWY",      "ArrivalSequenceService.*")   → false
 * </pre>
 */
public final class TopicWildcardMatcher {

    private TopicWildcardMatcher() {
    }

    /**
     * Returns {@code true} if {@code topic} matches {@code pattern}.
     *
     * @param topic   concrete topic name (e.g. {@code "DigitalNOTAMService.RWY.CLS"})
     * @param pattern pattern which may contain {@code *} or {@code #} wildcards
     */
    public static boolean matches(String topic, String pattern) {
        if (topic == null || pattern == null) {
            return false;
        }
        if (!pattern.contains("*") && !pattern.contains("#")) {
            return topic.equals(pattern);
        }
        String[] topicParts = topic.split("\\.");
        String[] patternParts = pattern.split("\\.");
        return matchParts(topicParts, 0, patternParts, 0);
    }

    private static boolean matchParts(String[] topic, int ti, String[] pattern, int pi) {
        while (pi < pattern.length) {
            String seg = pattern[pi];

            if ("#".equals(seg)) {
                return matchHash(topic, ti, pattern, pi);
            }

            if (ti >= topic.length) {
                return false;
            }

            if (!"*".equals(seg) && !seg.equals(topic[ti])) {
                return false;
            }

            ti++;
            pi++;
        }
        return ti == topic.length;
    }

    private static boolean matchHash(String[] topic, int ti, String[] pattern, int pi) {
        if (pi == pattern.length - 1) {
            return true;
        }
        for (int i = ti; i <= topic.length; i++) {
            if (matchParts(topic, i, pattern, pi + 1)) {
                return true;
            }
        }
        return false;
    }
}
