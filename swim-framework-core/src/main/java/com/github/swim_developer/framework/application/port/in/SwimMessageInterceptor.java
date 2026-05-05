package com.github.swim_developer.framework.application.port.in;

import com.github.swim_developer.framework.application.model.MessageInterceptorContext;
import com.github.swim_developer.framework.application.model.InterceptorResult;

public interface SwimMessageInterceptor {

    int order();

    InterceptorResult intercept(String payload, MessageInterceptorContext context);
}
