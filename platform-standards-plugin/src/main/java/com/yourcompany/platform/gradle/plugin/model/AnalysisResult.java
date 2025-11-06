package com.yourcompany.platform.gradle.plugin.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of analyzing a single dependency.
 */
public class AnalysisResult {

    private final DependencyCoordinate dependency;
    private final List<Violation> violations;
    private final boolean success;
    private final String errorMessage;

    public AnalysisResult(DependencyCoordinate dependency, List<Violation> violations) {
        this.dependency = dependency;
        this.violations = new ArrayList<>(violations);
        this.success = true;
        this.errorMessage = null;
    }

    public AnalysisResult(DependencyCoordinate dependency, String errorMessage) {
        this.dependency = dependency;
        this.violations = new ArrayList<>();
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public DependencyCoordinate getDependency() {
        return dependency;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getErrorCount() {
        return violations.stream()
            .filter(v -> v.getSeverity() == Violation.Severity.ERROR)
            .count();
    }

    public long getWarningCount() {
        return violations.stream()
            .filter(v -> v.getSeverity() == Violation.Severity.WARNING)
            .count();
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }
}
