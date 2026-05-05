package com.github.swim_developer.framework.domain.model;


import java.util.List;

public record DataValidationResult(
        ValidationResultType dataValResult,
        List<ErrorDetail> errorReport
) {

    public static DataValidationResult wrongFormat(String errorMessage) {
        return new DataValidationResult(
                ValidationResultType.WRONG_FORMAT,
                List.of(new ErrorDetail(ErrorCode.NOT_READABLE, null, errorMessage))
        );
    }

    public static DataValidationResult dataInvalid(ErrorCode code, String field, String message) {
        return new DataValidationResult(
                ValidationResultType.DATA_INVALID,
                List.of(new ErrorDetail(code, field, message))
        );
    }

    public static DataValidationResult nonSubscribedData(String message) {
        return new DataValidationResult(
                ValidationResultType.NON_SUBSCRIBED_DATA,
                List.of(new ErrorDetail(ErrorCode.LOGIC_VIOLATION, null, message))
        );
    }

    public static DataValidationResult sequenceGaps(List<String> missingFlights) {
        return new DataValidationResult(
                ValidationResultType.SEQUENCE_GAPS,
                missingFlights.stream()
                        .map(flight -> new ErrorDetail(
                                ErrorCode.LOGIC_VIOLATION, null,
                                "Flight " + flight + " disappeared without lastFiledRecord=true"))
                        .toList()
        );
    }
}
