package com.github.swim_developer.framework.domain.model;


public record ErrorDetail(
        ErrorCode errorCode,
        String erroneousFieldName,
        String errorMessage
) {}
