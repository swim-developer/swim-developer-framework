package com.github.swim_developer.framework.consumer.infrastructure.in.rest;

import com.github.swim_developer.framework.consumer.application.port.in.ConsumerStatisticsPort;
import com.github.swim_developer.framework.consumer.application.port.out.DeadLetterStore;
import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterMessage;
import com.github.swim_developer.framework.infrastructure.in.rest.PageResponse;
import com.github.swim_developer.framework.infrastructure.out.messaging.DlqMessageDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;
import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class AbstractOperationalResource {

    private final DeadLetterStore dlqRepository;
    private final ConsumerStatisticsPort statisticsPort;

    protected AbstractOperationalResource() {
        this(null, null);
    }

    protected AbstractOperationalResource(DeadLetterStore dlqRepository,
                                          ConsumerStatisticsPort statisticsPort) {
        this.dlqRepository = dlqRepository;
        this.statisticsPort = statisticsPort;
    }

    @GET
    @Path("/dlq")
    @Operation(summary = "List DLQ messages", description = "Retrieves messages from the Dead Letter Queue with pagination")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Paginated list of DLQ messages",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = PageResponse.class)))
    })
    public Response listDlqMessages(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size", example = "20")
            @QueryParam("size") @DefaultValue("20") int size) {

        List<DeadLetterMessage> dlqMessages = dlqRepository.findAllPaginated(page, size);
        long totalElements = dlqRepository.countAll();

        List<DlqMessageDTO> dtos = dlqMessages.stream()
                .map(AbstractOperationalResource::toDlqDTO)
                .toList();

        return Response.ok(PageResponse.of(dtos, page, size, totalElements)).build();
    }

    @GET
    @Path("/dlq/count")
    @Operation(summary = "Count DLQ messages", description = "Returns the total number of messages in the Dead Letter Queue")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "DLQ message count",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    public Response countDlqMessages() {
        long count = dlqRepository.countAll();
        return Response.ok(Map.of("count", count)).build();
    }

    @GET
    @Path("/stats")
    @Operation(summary = "Consumer statistics", description = "Returns aggregate statistics about the consumer state")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Consumer statistics",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    public Response getStats() {
        return Response.ok(statisticsPort.getStatistics()).build();
    }

    private static DlqMessageDTO toDlqDTO(DeadLetterMessage dlq) {
        return new DlqMessageDTO(
                dlq.getId(), dlq.getAmqpMessageId(), dlq.getMessageIndex(),
                dlq.getSubscriptionId(), dlq.getQueueName(),
                dlq.getErrorType(), dlq.getErrorMessage(),
                dlq.getRawPayload(), dlq.getReceivedAt(), dlq.getFailedAt());
    }
}
