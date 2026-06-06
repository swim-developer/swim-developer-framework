package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.exception.XmlValidationException;

public interface SwimXmlUnmarshallerPort<T> {

    T unmarshalAndValidate(String xml) throws XmlValidationException;
}
