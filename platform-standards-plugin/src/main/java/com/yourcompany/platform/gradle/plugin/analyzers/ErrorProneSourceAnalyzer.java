package com.yourcompany.platform.gradle.plugin.analyzers;

import com.yourcompany.platform.gradle.plugin.model.AnalysisResult;
import com.yourcompany.platform.gradle.plugin.model.DependencyCoordinate;
import com.yourcompany.platform.gradle.plugin.model.Violation;
import org.gradle.api.Project;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Analyzes Java source code from dependencies using Error Prone checks.
 *
 * This class:
 * 1. Extracts source JAR to temporary directory
 * 2. Compiles the sources with Error Prone annotations
 * 3. Runs custom Error Prone checkers
 * 4. Collects and reports violations
 */
public class ErrorProneSourceAnalyzer {

    private final Project project;
    private final List<String> errorProneChecks;
    private final File tempDir;

    public ErrorProneSourceAnalyzer(Project project, List<String> errorProneChecks) {
        this.project = project;
        this.errorProneChecks = errorProneChecks;
        this.tempDir = new File(project.getBuildDir(), "tmp/dependency-analysis");
        this.tempDir.mkdirs();
    }

    /**
     * Analyzes a dependency source JAR.
     *
     * @param dependency The dependency coordinates
     * @param sourceJar The source JAR file
     * @return Analysis result with any violations found
     */
    public AnalysisResult analyze(DependencyCoordinate dependency, File sourceJar) {
        try {
            // Extract source JAR
            File extractedDir = extractSourceJar(dependency, sourceJar);

            // Find all Java source files
            List<File> sourceFiles = findJavaFiles(extractedDir);

            if (sourceFiles.isEmpty()) {
                project.getLogger().warn("No Java source files found in {}", dependency);
                return new AnalysisResult(dependency, Collections.emptyList());
            }

            // Analyze with Error Prone
            List<Violation> violations = analyzeSourceFiles(dependency, sourceFiles, extractedDir);

            return new AnalysisResult(dependency, violations);

        } catch (Exception e) {
            project.getLogger().error("Failed to analyze {}: {}", dependency, e.getMessage(), e);
            return new AnalysisResult(dependency, "Analysis failed: " + e.getMessage());
        }
    }

    private File extractSourceJar(DependencyCoordinate dependency, File sourceJar) throws IOException {
        String dirName = String.format("%s-%s-%s",
            dependency.getGroup().replace('.', '-'),
            dependency.getArtifact(),
            dependency.getVersion()
        );

        File extractDir = new File(tempDir, dirName);

        // Check if already extracted (for caching)
        if (extractDir.exists()) {
            project.getLogger().debug("Using cached extraction for {}", dependency);
            return extractDir;
        }

        extractDir.mkdirs();

        try (JarFile jar = new JarFile(sourceJar)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File entryFile = new File(extractDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();

                    try (InputStream in = jar.getInputStream(entry);
                         FileOutputStream out = new FileOutputStream(entryFile)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }

        return extractDir;
    }

    private List<File> findJavaFiles(File directory) throws IOException {
        if (!directory.exists()) {
            return Collections.emptyList();
        }

        return Files.walk(directory.toPath())
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
    }

    private List<Violation> analyzeSourceFiles(
        DependencyCoordinate dependency,
        List<File> sourceFiles,
        File baseDir
    ) {
        List<Violation> violations = new ArrayList<>();

        // For demonstration, we'll do simple pattern-based analysis
        // In a real implementation, you would:
        // 1. Use javax.tools.JavaCompiler with Error Prone
        // 2. Or use Error Prone's API directly
        // 3. Or invoke Error Prone as an annotation processor

        for (File sourceFile : sourceFiles) {
            try {
                violations.addAll(analyzeFile(sourceFile, baseDir));
            } catch (IOException e) {
                project.getLogger().warn("Failed to analyze file {}: {}",
                    sourceFile.getName(), e.getMessage());
            }
        }

        return violations;
    }

    /**
     * Simplified analysis using pattern matching.
     * In production, this should use actual Error Prone compilation.
     */
    private List<Violation> analyzeFile(File sourceFile, File baseDir) throws IOException {
        List<Violation> violations = new ArrayList<>();

        String relativePath = baseDir.toPath().relativize(sourceFile.toPath()).toString();
        List<String> lines = Files.readAllLines(sourceFile.toPath());

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNumber = i + 1;

            // Example check: Detect banned API usage
            if (errorProneChecks.contains("BannedPlatformApiUsage")) {
                violations.addAll(checkBannedApiUsage(line, lineNumber, relativePath));
            }

            // Example check: Detect improper verticle deployment
            if (errorProneChecks.contains("ImproperVerticleDeployment")) {
                violations.addAll(checkVerticleDeployment(line, lineNumber, relativePath));
            }

            // Example check: Thread safety violations
            if (errorProneChecks.contains("ThreadSafetyViolation")) {
                violations.addAll(checkThreadSafety(line, lineNumber, relativePath));
            }
        }

        return violations;
    }

    private List<Violation> checkBannedApiUsage(String line, int lineNumber, String file) {
        List<Violation> violations = new ArrayList<>();

        // Check for direct usage of platform internal APIs
        if (line.contains("com.yourcompany.platform.internal")) {
            violations.add(new Violation(
                "BannedPlatformApiUsage",
                Violation.Severity.ERROR,
                file,
                lineNumber,
                "Direct usage of platform internal APIs is not allowed",
                "Use public platform APIs instead"
            ));
        }

        // Check for direct Vert.x usage when platform wrapper should be used
        if (line.contains("io.vertx.core.Vertx") &&
            !line.contains("import") &&
            line.contains("deployVerticle")) {

            violations.add(new Violation(
                "BannedPlatformApiUsage",
                Violation.Severity.ERROR,
                file,
                lineNumber,
                "Direct Vertx.deployVerticle() usage is not allowed",
                "Use PlatformVerticleDeployer.deploy() instead"
            ));
        }

        return violations;
    }

    private List<Violation> checkVerticleDeployment(String line, int lineNumber, String file) {
        List<Violation> violations = new ArrayList<>();

        // Check for manual verticle deployment
        if (line.matches(".*\\.deployVerticle\\s*\\(.*") &&
            !line.contains("PlatformVerticleDeployer")) {

            violations.add(new Violation(
                "ImproperVerticleDeployment",
                Violation.Severity.ERROR,
                file,
                lineNumber,
                "Verticles must be deployed through PlatformVerticleDeployer",
                "Replace with PlatformVerticleDeployer.deploy()"
            ));
        }

        return violations;
    }

    private List<Violation> checkThreadSafety(String line, int lineNumber, String file) {
        List<Violation> violations = new ArrayList<>();

        // Check for mutable static fields (common source of bugs in Vert.x)
        if (line.matches(".*\\bstatic\\b.*(?!final).*=.*") &&
            !line.contains("final") &&
            !line.contains("//")) {

            // Simple heuristic - check if line has static but not final
            String trimmed = line.trim();
            if (trimmed.contains("static") &&
                !trimmed.contains("final") &&
                !trimmed.contains("private static final") &&
                trimmed.contains("=")) {

                violations.add(new Violation(
                    "ThreadSafetyViolation",
                    Violation.Severity.WARNING,
                    file,
                    lineNumber,
                    "Mutable static field detected - potential thread safety issue",
                    "Consider using instance fields or making the field final"
                ));
            }
        }

        return violations;
    }
}
