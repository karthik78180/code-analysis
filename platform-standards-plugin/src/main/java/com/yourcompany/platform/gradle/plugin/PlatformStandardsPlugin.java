package com.yourcompany.platform.gradle.plugin;

import com.yourcompany.platform.gradle.plugin.config.DependencyAnalysisExtension;
import com.yourcompany.platform.gradle.plugin.tasks.AnalyzeDependencySourcesTask;
import com.yourcompany.platform.gradle.plugin.tasks.ResolveDependencySourcesTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin that enforces platform standards including Error Prone checks
 * on both project source code and dependency sources.
 *
 * This plugin addresses the limitation where Error Prone only analyzes code
 * in the current compilation unit by also analyzing source code from dependencies.
 */
public class PlatformStandardsPlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "platformStandards";
    private static final String RESOLVE_TASK_NAME = "resolveDependencySources";
    private static final String ANALYZE_TASK_NAME = "analyzeDependencySources";

    @Override
    public void apply(Project project) {
        // Ensure Java plugin is applied
        project.getPluginManager().apply(JavaPlugin.class);

        // Create extension for configuration
        DependencyAnalysisExtension extension = project.getExtensions().create(
            EXTENSION_NAME,
            DependencyAnalysisExtension.class,
            project
        );

        // Register tasks
        TaskProvider<ResolveDependencySourcesTask> resolveTask = registerResolveTask(project, extension);
        TaskProvider<AnalyzeDependencySourcesTask> analyzeTask = registerAnalyzeTask(project, extension, resolveTask);

        // Hook into build lifecycle
        configureTaskDependencies(project, analyzeTask);

        // Log configuration
        project.afterEvaluate(p -> {
            if (extension.getDependencyAnalysis().getEnabled().get()) {
                project.getLogger().info(
                    "Platform Standards Plugin: Dependency analysis enabled for groups: {}",
                    extension.getDependencyAnalysis().getScope().getIncludeGroups().get()
                );
            }
        });
    }

    private TaskProvider<ResolveDependencySourcesTask> registerResolveTask(
        Project project,
        DependencyAnalysisExtension extension
    ) {
        return project.getTasks().register(
            RESOLVE_TASK_NAME,
            ResolveDependencySourcesTask.class,
            task -> {
                task.setGroup("verification");
                task.setDescription("Resolves source JARs for internal dependencies");
                task.getExtension().set(extension);
            }
        );
    }

    private TaskProvider<AnalyzeDependencySourcesTask> registerAnalyzeTask(
        Project project,
        DependencyAnalysisExtension extension,
        TaskProvider<ResolveDependencySourcesTask> resolveTask
    ) {
        return project.getTasks().register(
            ANALYZE_TASK_NAME,
            AnalyzeDependencySourcesTask.class,
            task -> {
                task.setGroup("verification");
                task.setDescription("Analyzes dependency sources with Error Prone checks");
                task.getExtension().set(extension);
                task.dependsOn(resolveTask);
            }
        );
    }

    private void configureTaskDependencies(
        Project project,
        TaskProvider<AnalyzeDependencySourcesTask> analyzeTask
    ) {
        // Make compileJava depend on dependency analysis
        project.getTasks().named("compileJava").configure(compileTask -> {
            compileTask.dependsOn(analyzeTask);
        });

        // Also run before 'check' task
        project.getTasks().named("check").configure(checkTask -> {
            checkTask.dependsOn(analyzeTask);
        });
    }
}
