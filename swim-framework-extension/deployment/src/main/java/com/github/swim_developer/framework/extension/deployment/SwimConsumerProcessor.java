package com.github.swim_developer.framework.extension.deployment;

import com.github.swim_developer.framework.extension.runtime.SwimConsumer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.List;

class SwimConsumerProcessor {

    private static final Logger LOG = Logger.getLogger(SwimConsumerProcessor.class);
    private static final String FEATURE = "swim-framework";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDomainClassesForReflection(CombinedIndexBuildItem combinedIndex,
                                            BuildProducer<ReflectiveClassBuildItem> reflective) {
        Collection<AnnotationInstance> annotations = combinedIndex.getIndex()
                .getAnnotations(SwimConsumer.class);

        for (AnnotationInstance annotation : annotations) {
            AnnotationValue domainValue = annotation.value("domain");
            if (domainValue == null) {
                continue;
            }

            String serviceName = annotation.value("service").asString();
            List<Type> domainTypes = List.of(domainValue.asClassArray());

            for (Type type : domainTypes) {
                String className = type.name().toString();
                LOG.infof("[%s] Auto-registering for reflection: %s", serviceName, className);
                reflective.produce(ReflectiveClassBuildItem.builder(className)
                        .methods(true)
                        .fields(true)
                        .build());
            }

            LOG.infof("[%s] Registered %d domain classes for reflection", serviceName, domainTypes.size());
        }
    }
}
