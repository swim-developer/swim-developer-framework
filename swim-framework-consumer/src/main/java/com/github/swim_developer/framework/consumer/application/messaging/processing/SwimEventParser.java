package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.domain.exception.XmlValidationException;

public interface SwimEventParser<P> {

    P unmarshalAndValidate(String xmlPayload) throws XmlValidationException;
}
