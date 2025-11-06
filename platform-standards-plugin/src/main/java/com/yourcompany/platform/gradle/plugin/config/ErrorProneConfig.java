package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for standard Error Prone on project sources.
 */
public class ErrorProneConfig {

    private final Property<Boolean> enabled;
    private final Property<String> version;

    @Inject
    public ErrorProneConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.enabled = objects.property(Boolean.class).convention(true);
        this.version = objects.property(String.class).convention("2.23.0");
    }

    public Property<Boolean> getEnabled() {
        return enabled;
    }

    public Property<String> getVersion() {
        return version;
    }
}
