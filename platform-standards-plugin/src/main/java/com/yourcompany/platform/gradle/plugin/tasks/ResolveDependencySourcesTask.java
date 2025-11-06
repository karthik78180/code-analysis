package com.yourcompany.platform.gradle.plugin.tasks;

import com.yourcompany.platform.gradle.plugin.config.DependencyAnalysisExtension;
import com.yourcompany.platform.gradle.plugin.model.DependencyCoordinate;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Task that resolves source JARs for dependencies matching the configured scope.
 */
@CacheableTask
public abstract class ResolveDependencySourcesTask extends DefaultTask {

    @Input
    public abstract Property<DependencyAnalysisExtension> getExtension();

    @OutputDirectory
    public abstract DirectoryProperty getSourcesDirectory();

    public ResolveDependencySourcesTask() {
        getSourcesDirectory().set(
            getProject().getLayout().getBuildDirectory().dir("dependency-sources")
        );
    }

    @TaskAction
    public void resolveSources() {
        if (!getExtension().get().getDependencyAnalysis().getEnabled().get()) {
            getLogger().info("Dependency analysis is disabled, skipping source resolution");
            return;
        }

        getLogger().lifecycle("Resolving dependency sources...");

        Set<DependencyCoordinate> dependencies = findInternalDependencies();
        getLogger().info("Found {} internal dependencies to analyze", dependencies.size());

        Map<DependencyCoordinate, File> resolvedSources = new HashMap<>();

        for (DependencyCoordinate dep : dependencies) {
            try {
                File sourceJar = resolveSourceJar(dep);
                if (sourceJar != null && sourceJar.exists()) {
                    resolvedSources.put(dep, sourceJar);
                    getLogger().info("Resolved sources for {}", dep);
                } else {
                    getLogger().warn("Could not resolve sources for {}", dep);
                }
            } catch (Exception e) {
                getLogger().error("Error resolving sources for {}: {}", dep, e.getMessage());
            }
        }

        getLogger().lifecycle(
            "Resolved sources for {}/{} dependencies",
            resolvedSources.size(),
            dependencies.size()
        );

        // Store resolved sources info for next task
        storeResolvedInfo(resolvedSources);
    }

    private Set<DependencyCoordinate> findInternalDependencies() {
        DependencyAnalysisExtension ext = getExtension().get();
        List<String> includePatterns = ext.getDependencyAnalysis().getScope()
            .getIncludeGroups().get();
        List<String> excludePatterns = ext.getDependencyAnalysis().getScope()
            .getExcludeGroups().get();
        List<String> configNames = ext.getDependencyAnalysis().getScope()
            .getConfigurations().get();

        Set<DependencyCoordinate> result = new HashSet<>();

        for (String configName : configNames) {
            Configuration config = getProject().getConfigurations().findByName(configName);
            if (config != null && config.isCanBeResolved()) {
                config.getResolvedConfiguration().getFirstLevelModuleDependencies()
                    .forEach(dep -> collectDependencies(dep, result, includePatterns, excludePatterns));
            }
        }

        return result;
    }

    private void collectDependencies(
        ResolvedDependency dep,
        Set<DependencyCoordinate> result,
        List<String> includePatterns,
        List<String> excludePatterns
    ) {
        String group = dep.getModuleGroup();

        // Check if matches include patterns
        boolean included = includePatterns.stream()
            .anyMatch(pattern -> matchesPattern(group, pattern));

        // Check if matches exclude patterns
        boolean excluded = excludePatterns.stream()
            .anyMatch(pattern -> matchesPattern(group, pattern));

        if (included && !excluded) {
            result.add(new DependencyCoordinate(
                dep.getModuleGroup(),
                dep.getModuleName(),
                dep.getModuleVersion()
            ));
        }

        // Recursively collect transitive dependencies
        dep.getChildren().forEach(child ->
            collectDependencies(child, result, includePatterns, excludePatterns)
        );
    }

    private boolean matchesPattern(String group, String pattern) {
        // Simple wildcard pattern matching
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return Pattern.matches(regex, group);
    }

    private File resolveSourceJar(DependencyCoordinate dep) {
        try {
            // Try to resolve sources using Gradle's dependency resolution
            org.gradle.api.artifacts.Dependency sourceDep = getProject().getDependencies().create(
                String.format("%s:%s:%s:sources", dep.getGroup(), dep.getArtifact(), dep.getVersion())
            );

            Configuration detachedConfig = getProject().getConfigurations()
                .detachedConfiguration(sourceDep);

            Set<File> files = detachedConfig.resolve();
            if (!files.isEmpty()) {
                return files.iterator().next();
            }
        } catch (Exception e) {
            getLogger().debug("Could not resolve sources for {}: {}", dep, e.getMessage());
        }

        return null;
    }

    private void storeResolvedInfo(Map<DependencyCoordinate, File> resolvedSources) {
        File outputDir = getSourcesDirectory().get().getAsFile();
        outputDir.mkdirs();

        // Write a simple manifest file
        File manifest = new File(outputDir, "resolved-sources.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(manifest)) {
            resolvedSources.forEach((dep, sourceJar) ->
                writer.println(dep.toCoordinateString() + "=" + sourceJar.getAbsolutePath())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to write manifest", e);
        }
    }
}
