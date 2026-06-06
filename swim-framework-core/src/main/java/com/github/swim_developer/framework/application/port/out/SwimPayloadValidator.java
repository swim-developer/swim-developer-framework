package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.ValidationResult;

public interface SwimPayloadValidator {

    ValidationResult validate(String payload);
}
