package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Top-level extension for platform standards configuration.
 * Accessible as: platformStandards { ... }
 */
public class DependencyAnalysisExtension {

    private final DependencyAnalysisConfig dependencyAnalysis;
    private final ErrorProneConfig errorprone;
    private final Project project;

    @Inject
    public DependencyAnalysisExtension(Project project) {
        this.project = project;
        ObjectFactory objects = project.getObjects();

        this.dependencyAnalysis = objects.newInstance(DependencyAnalysisConfig.class, project);
        this.errorprone = objects.newInstance(ErrorProneConfig.class, project);
    }

    public DependencyAnalysisConfig getDependencyAnalysis() {
        return dependencyAnalysis;
    }

    public void dependencyAnalysis(org.gradle.api.Action<? super DependencyAnalysisConfig> action) {
        action.execute(dependencyAnalysis);
    }

    public ErrorProneConfig getErrorprone() {
        return errorprone;
    }

    public void errorprone(org.gradle.api.Action<? super ErrorProneConfig> action) {
        action.execute(errorprone);
    }

    public Project getProject() {
        return project;
    }
}
