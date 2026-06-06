package com.github.swim_developer.framework.infrastructure.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/")
@PermitAll
public class RootResource {

    private static final String SWAGGER_UI_PATH = "/swagger-ui";

    @GET
    @Operation(hidden = true)
    public Response redirectToSwaggerUI() {
        return Response.seeOther(UriBuilder.fromPath(SWAGGER_UI_PATH).build()).build();
    }
}
