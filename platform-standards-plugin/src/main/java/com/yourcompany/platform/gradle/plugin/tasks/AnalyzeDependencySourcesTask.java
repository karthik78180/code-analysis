package com.yourcompany.platform.gradle.plugin.tasks;

import com.yourcompany.platform.gradle.plugin.analyzers.ErrorProneSourceAnalyzer;
import com.yourcompany.platform.gradle.plugin.config.DependencyAnalysisExtension;
import com.yourcompany.platform.gradle.plugin.config.ReportingConfig;
import com.yourcompany.platform.gradle.plugin.model.AnalysisResult;
import com.yourcompany.platform.gradle.plugin.model.DependencyCoordinate;
import com.yourcompany.platform.gradle.plugin.model.Violation;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Task that analyzes resolved dependency sources with Error Prone checks.
 */
@CacheableTask
public abstract class AnalyzeDependencySourcesTask extends DefaultTask {

    @Input
    public abstract Property<DependencyAnalysisExtension> getExtension();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourcesDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getReportDirectory();

    public AnalyzeDependencySourcesTask() {
        getSourcesDirectory().set(
            getProject().getLayout().getBuildDirectory().dir("dependency-sources")
        );
        getReportDirectory().set(
            getProject().getLayout().getBuildDirectory().dir("reports/dependency-analysis")
        );
    }

    @TaskAction
    public void analyze() {
        if (!getExtension().get().getDependencyAnalysis().getEnabled().get()) {
            getLogger().info("Dependency analysis is disabled, skipping");
            return;
        }

        getLogger().lifecycle("Analyzing dependency sources with Error Prone...");

        Map<DependencyCoordinate, File> resolvedSources = loadResolvedSources();

        if (resolvedSources.isEmpty()) {
            getLogger().lifecycle("No dependency sources to analyze");
            return;
        }

        List<AnalysisResult> results = performAnalysis(resolvedSources);

        generateReport(results);

        handleViolations(results);
    }

    private Map<DependencyCoordinate, File> loadResolvedSources() {
        File manifest = new File(getSourcesDirectory().get().getAsFile(), "resolved-sources.txt");

        if (!manifest.exists()) {
            return Collections.emptyMap();
        }

        Map<DependencyCoordinate, File> result = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String[] coords = parts[0].split(":");
                    if (coords.length == 3) {
                        DependencyCoordinate dep = new DependencyCoordinate(
                            coords[0], coords[1], coords[2]
                        );
                        result.put(dep, new File(parts[1]));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resolved sources", e);
        }

        return result;
    }

    private List<AnalysisResult> performAnalysis(Map<DependencyCoordinate, File> resolvedSources) {
        DependencyAnalysisExtension ext = getExtension().get();
        boolean parallel = ext.getDependencyAnalysis().getParallel().get();
        int maxWorkers = ext.getDependencyAnalysis().getMaxWorkers().get();

        ErrorProneSourceAnalyzer analyzer = new ErrorProneSourceAnalyzer(
            getProject(),
            ext.getDependencyAnalysis().getAnalysis().getErrorProneChecks().get()
        );

        if (parallel && resolvedSources.size() > 1) {
            return analyzeInParallel(resolvedSources, analyzer, maxWorkers);
        } else {
            return analyzeSequentially(resolvedSources, analyzer);
        }
    }

    private List<AnalysisResult> analyzeSequentially(
        Map<DependencyCoordinate, File> resolvedSources,
        ErrorProneSourceAnalyzer analyzer
    ) {
        List<AnalysisResult> results = new ArrayList<>();

        for (Map.Entry<DependencyCoordinate, File> entry : resolvedSources.entrySet()) {
            getLogger().info("Analyzing {}", entry.getKey());
            AnalysisResult result = analyzer.analyze(entry.getKey(), entry.getValue());
            results.add(result);
        }

        return results;
    }

    private List<AnalysisResult> analyzeInParallel(
        Map<DependencyCoordinate, File> resolvedSources,
        ErrorProneSourceAnalyzer analyzer,
        int maxWorkers
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
        List<Future<AnalysisResult>> futures = new ArrayList<>();

        for (Map.Entry<DependencyCoordinate, File> entry : resolvedSources.entrySet()) {
            Future<AnalysisResult> future = executor.submit(() -> {
                getLogger().info("Analyzing {}", entry.getKey());
                return analyzer.analyze(entry.getKey(), entry.getValue());
            });
            futures.add(future);
        }

        List<AnalysisResult> results = new ArrayList<>();
        for (Future<AnalysisResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                getLogger().error("Analysis failed", e);
            }
        }

        executor.shutdown();
        return results;
    }

    private void generateReport(List<AnalysisResult> results) {
        File reportDir = getReportDirectory().get().getAsFile();
        reportDir.mkdirs();

        // Generate text report
        File textReport = new File(reportDir, "dependency-analysis.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(textReport)) {
            writer.println("===============================================");
            writer.println(" DEPENDENCY COMPLIANCE ANALYSIS REPORT");
            writer.println("===============================================");
            writer.println();

            long totalErrors = 0;
            long totalWarnings = 0;
            int violatingDeps = 0;

            for (AnalysisResult result : results) {
                if (result.hasViolations()) {
                    violatingDeps++;
                    writer.println("Dependency: " + result.getDependency());
                    writer.println();

                    for (Violation violation : result.getViolations()) {
                        writer.println(violation);
                    }

                    writer.println();
                    totalErrors += result.getErrorCount();
                    totalWarnings += result.getWarningCount();
                }
            }

            writer.println("===============================================");
            writer.println("SUMMARY:");
            writer.println("  Total dependencies analyzed: " + results.size());
            writer.println("  Dependencies with violations: " + violatingDeps);
            writer.println("  Total errors: " + totalErrors);
            writer.println("  Total warnings: " + totalWarnings);
            writer.println("===============================================");
        } catch (Exception e) {
            getLogger().error("Failed to generate report", e);
        }

        getLogger().lifecycle("Report generated at: {}", textReport.getAbsolutePath());
    }

    private void handleViolations(List<AnalysisResult> results) {
        ReportingConfig reporting = getExtension().get()
            .getDependencyAnalysis()
            .getReporting();

        long totalErrors = results.stream()
            .mapToLong(AnalysisResult::getErrorCount)
            .sum();

        long totalWarnings = results.stream()
            .mapToLong(AnalysisResult::getWarningCount)
            .sum();

        int maxErrors = reporting.getMaxErrors().get();
        int maxWarnings = reporting.getMaxWarnings().get();

        boolean shouldFail = reporting.getFailOnViolation().get() &&
                           reporting.getMode().get() == ReportingConfig.EnforcementMode.BLOCK &&
                           (totalErrors > maxErrors || totalWarnings > maxWarnings);

        if (totalErrors > 0 || totalWarnings > 0) {
            String message = String.format(
                "Found %d error(s) and %d warning(s) in dependencies",
                totalErrors,
                totalWarnings
            );

            if (shouldFail) {
                throw new GradleException(
                    message + ". Build FAILED due to compliance violations. " +
                    "See report at: " + getReportDirectory().get().getAsFile()
                );
            } else {
                getLogger().warn(message);
            }
        } else {
            getLogger().lifecycle("No violations found in dependencies");
        }
    }
}
