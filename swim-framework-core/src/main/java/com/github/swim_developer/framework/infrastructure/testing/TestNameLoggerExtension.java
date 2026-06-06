package com.github.swim_developer.framework.infrastructure.testing;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class TestNameLoggerExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        String className = context.getRequiredTestClass().getSimpleName();
        String displayName = context.getDisplayName();
        log.info("\n══ ▶ {}.{}", className, displayName);
    }
}
