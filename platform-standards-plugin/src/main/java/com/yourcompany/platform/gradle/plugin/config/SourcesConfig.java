package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for source resolution.
 */
public class SourcesConfig {

    private final Property<Boolean> requireSourcesJar;
    private final Property<Boolean> fallbackToGitClone;

    @Inject
    public SourcesConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.requireSourcesJar = objects.property(Boolean.class).convention(true);
        this.fallbackToGitClone = objects.property(Boolean.class).convention(false);
    }

    public Property<Boolean> getRequireSourcesJar() {
        return requireSourcesJar;
    }

    public Property<Boolean> getFallbackToGitClone() {
        return fallbackToGitClone;
    }
}
