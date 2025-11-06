package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for reporting and build failure behavior.
 */
public class ReportingConfig {

    private final Property<Boolean> failOnViolation;
    private final Property<Boolean> generateReport;
    private final Property<String> reportFormat;
    private final Property<Integer> maxErrors;
    private final Property<Integer> maxWarnings;
    private final Property<EnforcementMode> mode;

    @Inject
    public ReportingConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.failOnViolation = objects.property(Boolean.class).convention(true);
        this.generateReport = objects.property(Boolean.class).convention(true);
        this.reportFormat = objects.property(String.class).convention("HTML");
        this.maxErrors = objects.property(Integer.class).convention(0);
        this.maxWarnings = objects.property(Integer.class).convention(10);
        this.mode = objects.property(EnforcementMode.class).convention(EnforcementMode.BLOCK);
    }

    public Property<Boolean> getFailOnViolation() {
        return failOnViolation;
    }

    public Property<Boolean> getGenerateReport() {
        return generateReport;
    }

    public Property<String> getReportFormat() {
        return reportFormat;
    }

    public Property<Integer> getMaxErrors() {
        return maxErrors;
    }

    public Property<Integer> getMaxWarnings() {
        return maxWarnings;
    }

    public Property<EnforcementMode> getMode() {
        return mode;
    }

    public enum EnforcementMode {
        WARN,   // Only log warnings
        BLOCK   // Fail the build
    }
}
