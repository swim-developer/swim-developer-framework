package com.github.swim_developer.framework.application.port.out;

import java.util.List;
import java.util.Optional;

/**
 * SPI for extracting domain events from validated SWIM payloads.
 * Transforms XML/JSON into typed domain objects for persistence and routing.
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamEventExtractor} - Extracts NOTAM events from AIXM 5.1.1 XML</li>
 *   <li>{@code Ed254EventExtractor} - Extracts arrival sequences from ED-254 XML</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamEventExtractor implements SwimEventExtractor<DnotamEvent, Event> {
 *     @Override
 *     public List<Optional<DnotamEvent>> extract(Event aixmEvent) {
 *         // Parse AIXM, extract scenario, build domain event
 *         return List.of(Optional.of(new DnotamEvent(...)));
 *     }
 * }
 * }</pre>
 *
 * @param <E> domain event type (e.g., {@code DnotamEvent}, {@code Ed254ArrivalSequence})
 * @param <P> parsed payload type (e.g., JAXB {@code Event}, {@code FlightPlanUpdate})
 */
public interface SwimEventExtractor<E, P> {

    /**
     * Extracts zero or more domain events from validated payload.
     * Returns {@link Optional#empty()} for events that cannot be extracted.
     *
     * @param parsedPayload validated and unmarshalled payload
     * @return list of extracted events (may contain empty optionals)
     */
    List<Optional<E>> extract(P parsedPayload);

    default Optional<E> extractEvent(P parsedPayload) {
        List<Optional<E>> results = extract(parsedPayload);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return results.getFirst();
    }

    default String getTypeLabel(E event) {
        return "unknown";
    }
}
