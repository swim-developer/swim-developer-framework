package com.github.swim_developer.framework.consumer.infrastructure.in.rest;

import com.github.swim_developer.framework.consumer.application.port.in.SwimQueryFeaturesPort;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.Map;

@Slf4j
@Path("/api/v1")
public abstract class AbstractFeatureResource {

    private final SwimQueryFeaturesPort queryFeaturesPort;

    protected AbstractFeatureResource() {
        this(null);
    }

    protected AbstractFeatureResource(SwimQueryFeaturesPort queryFeaturesPort) {
        this.queryFeaturesPort = queryFeaturesPort;
    }

    @GET
    @Path("/features")
    @Produces(MediaType.APPLICATION_XML)
    @Operation(
            summary = "Query Features (WFS GetFeature proxy)",
            description = "Proxies the OGC WFS GetFeature request to the upstream provider (Subscription Manager)."
    )
    @APIResponse(responseCode = "200", description = "Feature XML response",
            content = @Content(mediaType = MediaType.APPLICATION_XML))
    @APIResponse(responseCode = "502", description = "Provider unreachable or returned error")
    @APIResponse(responseCode = "503", description = "No provider configured")
    public Response getFeatures(
            @QueryParam("typeName")
            @Parameter(description = "Feature type", required = true)
            String typeName,
            @QueryParam("filter")
            @Parameter(description = "OGC Filter Encoding 2.0 expression")
            String filter,
            @QueryParam("validTime")
            @Parameter(description = "Validity time filter (ISO-8601)")
            String validTime,
            @QueryParam("providerId")
            @Parameter(description = "Provider ID (uses default if omitted)")
            String providerId) {

        try {
            String xml = queryFeaturesPort.queryFeatures(typeName, filter, validTime, providerId);
            return Response.ok(xml, MediaType.APPLICATION_XML_TYPE).build();

        } catch (IllegalStateException e) {
            log.warn("No provider configured for features query (providerId={}): {}", providerId, e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("WFS GetFeature proxy failed (providerId={}): {}", providerId, e.getMessage(), e);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of(
                            "error", "Provider request failed",
                            "detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                    ))
                    .build();
        }
    }
}
