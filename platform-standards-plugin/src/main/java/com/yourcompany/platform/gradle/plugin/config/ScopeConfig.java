package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * Configuration for which dependencies to analyze.
 */
public class ScopeConfig {

    private final ListProperty<String> includeGroups;
    private final ListProperty<String> excludeGroups;
    private final ListProperty<String> configurations;

    @Inject
    public ScopeConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.includeGroups = objects.listProperty(String.class)
            .convention(Arrays.asList("com.yourcompany.*"));

        this.excludeGroups = objects.listProperty(String.class)
            .empty();

        this.configurations = objects.listProperty(String.class)
            .convention(Arrays.asList("implementation", "api", "runtimeOnly"));
    }

    public ListProperty<String> getIncludeGroups() {
        return includeGroups;
    }

    public ListProperty<String> getExcludeGroups() {
        return excludeGroups;
    }

    public ListProperty<String> getConfigurations() {
        return configurations;
    }
}
