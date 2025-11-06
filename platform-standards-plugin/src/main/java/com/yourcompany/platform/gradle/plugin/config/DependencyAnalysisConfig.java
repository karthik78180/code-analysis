package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for dependency analysis behavior.
 * Accessible as: platformStandards { dependencyAnalysis { ... } }
 */
public class DependencyAnalysisConfig {

    private final Property<Boolean> enabled;
    private final ScopeConfig scope;
    private final SourcesConfig sources;
    private final AnalysisConfig analysis;
    private final ReportingConfig reporting;
    private final Property<Boolean> parallel;
    private final Property<Integer> maxWorkers;

    @Inject
    public DependencyAnalysisConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.enabled = objects.property(Boolean.class).convention(true);
        this.scope = objects.newInstance(ScopeConfig.class, project);
        this.sources = objects.newInstance(SourcesConfig.class, project);
        this.analysis = objects.newInstance(AnalysisConfig.class, project);
        this.reporting = objects.newInstance(ReportingConfig.class, project);
        this.parallel = objects.property(Boolean.class).convention(true);
        this.maxWorkers = objects.property(Integer.class)
            .convention(Runtime.getRuntime().availableProcessors());
    }

    public Property<Boolean> getEnabled() {
        return enabled;
    }

    public ScopeConfig getScope() {
        return scope;
    }

    public void scope(org.gradle.api.Action<? super ScopeConfig> action) {
        action.execute(scope);
    }

    public SourcesConfig getSources() {
        return sources;
    }

    public void sources(org.gradle.api.Action<? super SourcesConfig> action) {
        action.execute(sources);
    }

    public AnalysisConfig getAnalysis() {
        return analysis;
    }

    public void analysis(org.gradle.api.Action<? super AnalysisConfig> action) {
        action.execute(analysis);
    }

    public ReportingConfig getReporting() {
        return reporting;
    }

    public void reporting(org.gradle.api.Action<? super ReportingConfig> action) {
        action.execute(reporting);
    }

    public Property<Boolean> getParallel() {
        return parallel;
    }

    public Property<Integer> getMaxWorkers() {
        return maxWorkers;
    }
}
