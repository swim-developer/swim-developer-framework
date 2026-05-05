package com.github.swim_developer.framework.extension.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SwimConsumer {

    String service();

    String inboxChannel() default "";

    Class<?>[] domain() default {};
}
