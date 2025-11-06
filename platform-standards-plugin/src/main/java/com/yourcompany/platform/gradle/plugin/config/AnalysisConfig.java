package com.yourcompany.platform.gradle.plugin.config;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * Configuration for analysis behavior.
 */
public class AnalysisConfig {

    private final ListProperty<String> errorProneChecks;
    private final Property<Boolean> runCheckstyle;
    private final Property<Boolean> cacheEnabled;
    private final DirectoryProperty cacheDir;

    @Inject
    public AnalysisConfig(Project project) {
        ObjectFactory objects = project.getObjects();

        this.errorProneChecks = objects.listProperty(String.class)
            .convention(Arrays.asList(
                "BannedPlatformApiUsage",
                "ImproperVerticleDeployment",
                "ThreadSafetyViolation"
            ));

        this.runCheckstyle = objects.property(Boolean.class).convention(false);
        this.cacheEnabled = objects.property(Boolean.class).convention(true);
        this.cacheDir = objects.directoryProperty()
            .convention(project.getLayout().getBuildDirectory().dir("dependency-analysis-cache"));
    }

    public ListProperty<String> getErrorProneChecks() {
        return errorProneChecks;
    }

    public Property<Boolean> getRunCheckstyle() {
        return runCheckstyle;
    }

    public Property<Boolean> getCacheEnabled() {
        return cacheEnabled;
    }

    public DirectoryProperty getCacheDir() {
        return cacheDir;
    }
}
