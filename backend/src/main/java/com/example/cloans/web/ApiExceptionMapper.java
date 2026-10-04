package com.example.cloans.web;

import com.example.cloans.service.ApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Override
    public Response toResponse(ApiException e) {
        return Response.status(e.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", e.code, "message", e.getMessage()))
                .build();
    }
}
