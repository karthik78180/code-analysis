# Error Prone Dependency Analysis - Design Document

## Problem Statement

Your platform team provides Vert.x abstraction libraries and a Gradle plugin with Error Prone checks. However, Error Prone only analyzes code in the current repository and misses violations in:
- Wrapper libraries created by other teams
- Reusable components distributed as dependencies
- Code that wraps your platform APIs

This creates inconsistent enforcement of coding standards across your organization.

## Root Cause

Error Prone operates during Java compilation and only processes source files being compiled in the current build. Dependencies are included as compiled `.class` files, so their source code is never analyzed by Error Prone in downstream projects.

---

## Solution Approaches

### Approach 1: **Dependency Source Analysis** ⭐ RECOMMENDED
Analyze source code from dependencies during build time.

#### Architecture
```
Build Pipeline:
1. Resolve dependencies with sources classifier
2. Extract dependency sources to temporary directory
3. Run custom Error Prone checks on dependency sources
4. Aggregate violations and fail build if needed
5. Continue with normal compilation
```

#### Pros
- Most accurate - analyzes actual source code
- Reuses existing Error Prone checks
- Can provide detailed error messages with line numbers
- Works with your existing Error Prone infrastructure

#### Cons
- Requires dependencies to publish sources (sources jar)
- Slightly increases build time
- Need to manage dependency resolution and caching

#### Implementation Complexity: **Medium**

---

### Approach 2: **Bytecode Analysis with Custom Rules**
Analyze compiled bytecode from dependency JARs.

#### Architecture
```
Build Pipeline:
1. Resolve dependency JARs
2. Use ASM/ByteBuddy to analyze bytecode
3. Implement custom detectors for anti-patterns
4. Block build on violations
```

#### Pros
- Doesn't require source JARs
- Can analyze all dependencies including third-party
- Can detect runtime behavior patterns

#### Cons
- Cannot reuse Error Prone checks (different analysis model)
- Need to write bytecode-level detectors
- More complex to implement
- Error messages less clear (no source line numbers)

#### Implementation Complexity: **High**

---

### Approach 3: **Central Validation at Publishing Time** ⭐ RECOMMENDED FOR ENTERPRISE
Prevent non-compliant code from being published to your internal repository.

#### Architecture
```
Publishing Pipeline:
1. Team builds wrapper library
2. Pre-publish validation hook runs Error Prone
3. If checks pass → publish to Artifactory/Nexus
4. If checks fail → block publishing
5. Downstream teams can only use validated dependencies
```

#### Pros
- **Shift-left:** Catch issues at source
- No build-time overhead for consuming teams
- Centralized enforcement
- Works with Artifactory/Nexus repository managers
- Can use standard Error Prone checks

#### Cons
- Requires repository manager setup
- Need organizational process enforcement
- Teams might bypass by using different repositories

#### Implementation Complexity: **Medium** (infrastructure setup required)

---

### Approach 4: **Multi-Module Source Inclusion**
Include wrapper libraries as source modules in consuming projects.

#### Architecture
```
Project Structure:
my-service/
├── build.gradle
├── settings.gradle (includes wrapper library source)
└── src/
```

#### Pros
- Simple to implement
- Full Error Prone coverage
- Works with existing tooling

#### Cons
- Doesn't scale for many dependencies
- Slower builds (recompiling dependencies)
- Not suitable for your use case (multiple teams/repos)

#### Implementation Complexity: **Low** (but not recommended for your scenario)

---

### Approach 5: **Classpath Scanning with Annotation-Based Validation**
Use custom annotations and build-time scanning.

#### Architecture
```
1. Platform team marks deprecated/banned APIs with @Deprecated or custom annotations
2. Build plugin scans classpath for usage
3. Detect violations via reflection or compile-time annotation processing
4. Block build
```

#### Pros
- Lightweight
- Works at compile time
- Can detect specific API usage patterns

#### Cons
- Limited to annotation-based rules
- Cannot enforce arbitrary coding patterns
- Requires modifying platform APIs

#### Implementation Complexity: **Low-Medium**

---

## Recommended Solution: **Hybrid Approach**

Combine **Approach 1 (Dependency Source Analysis)** and **Approach 3 (Central Validation)** for maximum effectiveness:

### Phase 1: Immediate - Dependency Source Analysis
Implement in your existing Gradle plugin to analyze dependency sources at build time.

### Phase 2: Long-term - Central Validation
Set up publishing validation in your artifact repository to prevent non-compliant code from being published.

---

## Detailed Implementation Plan

### Implementation: Dependency Source Analysis

#### 1. Gradle Plugin Architecture

```
platform-standards-plugin/
├── src/main/java/
│   ├── DependencyAnalyzerPlugin.java
│   ├── tasks/
│   │   ├── AnalyzeDependencySourcesTask.java
│   │   └── ValidateComplianceTask.java
│   ├── analyzers/
│   │   ├── ErrorProneSourceAnalyzer.java
│   │   └── ViolationReporter.java
│   └── config/
│       └── DependencyAnalysisExtension.java
```

#### 2. Plugin Features

**A. Dependency Classification**
```groovy
dependencyAnalysis {
    // Only analyze internal company dependencies
    includeGroups = ['com.yourcompany.platform', 'com.yourcompany.wrappers']

    // Exclude third-party
    excludeGroups = ['org.springframework', 'io.vertx']

    // Fail build on violations
    failOnViolation = true

    // Custom Error Prone checks to run
    errorProneChecks = [
        'BannedApiUsage',
        'CustomVerticleDeployment',
        'ThreadSafetyViolation'
    ]
}
```

**B. Source Resolution Strategy**
1. Check Gradle metadata for sources variant
2. Try to resolve `${artifactId}-${version}-sources.jar`
3. If not available, optionally clone from internal Git (if accessible)
4. Cache resolved sources

**C. Analysis Execution**
1. Extract sources to `build/dependency-analysis/sources/`
2. Create temporary Gradle project or direct Java compilation
3. Apply Error Prone with your custom checks
4. Collect and aggregate violations
5. Generate report in `build/reports/dependency-analysis/`

#### 3. Error Prone Custom Checks

Create checks specific to your platform:

**Example: Banned API Usage Check**
- Detect usage of internal APIs not meant for external use
- Enforce use of platform abstractions
- Prevent direct Vert.x API usage (force wrapper usage)

**Example: Verticle Deployment Pattern Check**
- Ensure verticles are deployed through platform APIs
- Detect manual deployment
- Enforce configuration patterns

#### 4. Violation Reporting

```
===============================================
 DEPENDENCY COMPLIANCE VIOLATIONS DETECTED
===============================================

Dependency: com.yourcompany.wrappers:vertx-utils:1.2.3

[ERROR] BannedApiUsage: Direct usage of io.vertx.core.Vertx.deployVerticle
  Location: VerticleHelper.java:45
  Use platform API: PlatformVerticleDeployer.deploy() instead

[ERROR] ThreadSafetyViolation: Mutable static field in Verticle
  Location: CacheVerticle.java:12
  Static mutable state is not allowed in Verticles

Build FAILED: 2 compliance violations in dependencies
```

---

## Implementation: Central Validation (Enterprise)

### Architecture with Artifactory/Nexus

```
Developer → Push to Git → CI/CD Pipeline
                               ↓
                    Run Error Prone Validation
                               ↓
                    ┌──────────┴──────────┐
                 PASS                    FAIL
                    ↓                      ↓
            Publish to Artifactory    Block & Notify
                    ↓
        Available to other teams
```

### Implementation Options

#### Option A: Artifactory User Plugin
```groovy
// Artifactory beforeUpload hook
beforeUpload {
    if (isInternalArtifact(repoPath)) {
        def sourceJar = findSourceJar(repoPath)
        def violations = runErrorProneAnalysis(sourceJar)

        if (violations.isNotEmpty()) {
            message = "Upload blocked: ${violations.size()} compliance violations"
            status = 403
        }
    }
}
```

#### Option B: CI/CD Gate
```yaml
# GitHub Actions / Jenkins / GitLab CI
steps:
  - name: Build
    run: ./gradlew build

  - name: Compliance Validation
    run: ./gradlew checkCompliance --fail-on-violations

  - name: Publish (only if compliant)
    run: ./gradlew publish
    if: success()
```

#### Option C: Gradle Publishing Plugin Extension
Extend `maven-publish` plugin to run validation before publishing.

---

## Gradle Plugin Implementation Details

### Configuration DSL

```groovy
apply plugin: 'com.yourcompany.platform-standards'

platformStandards {
    errorprone {
        enabled = true
        // Your existing Error Prone config
    }

    dependencyAnalysis {
        enabled = true

        // Scope: which dependencies to analyze
        scope {
            includeGroups = ['com.yourcompany.*']
            excludeGroups = ['com.yourcompany.platform:.*'] // Don't analyze platform itself

            // Only analyze specific configurations
            configurations = ['implementation', 'api']
        }

        // Source resolution
        sources {
            requireSourcesJar = true
            fallbackToGitClone = false

            // Custom source repository if needed
            sourceRepository {
                url = "https://git.yourcompany.com"
                credentials {
                    username = project.findProperty('gitUsername')
                    password = project.findProperty('gitToken')
                }
            }
        }

        // Analysis behavior
        analysis {
            // Run custom Error Prone checks
            errorProneChecks = ['BannedApiUsage', 'CustomVerticleDeployment']

            // Additional static analysis
            runCheckstyle = true

            // Caching
            cacheEnabled = true
            cacheDir = file("${buildDir}/dependency-analysis-cache")
        }

        // Reporting
        reporting {
            failOnViolation = true
            generateReport = true
            reportFormat = 'HTML' // HTML, JSON, XML

            // Violation thresholds
            maxErrors = 0
            maxWarnings = 10
        }

        // Performance
        parallel = true
        maxWorkers = 4
    }
}
```

### Task Dependencies

```
compileJava
    ↑
    depends on
    ↑
analyzeDependencySources
    ↑
    depends on
    ↑
resolveDependencySources
```

---

## Custom Error Prone Checks Examples

### Check 1: Banned API Usage

```java
@BugPattern(
    name = "BannedPlatformApiUsage",
    summary = "Direct usage of platform internal APIs is not allowed",
    severity = ERROR
)
public class BannedPlatformApiUsageCheck extends BugChecker
    implements MethodInvocationTreeMatcher {

    private static final String BANNED_API_PATTERN =
        "com.yourcompany.platform.internal.*";

    @Override
    public Description matchMethodInvocation(
        MethodInvocationTree tree, VisitorState state) {

        MethodSymbol method = ASTHelpers.getSymbol(tree);
        if (method == null) return Description.NO_MATCH;

        String className = method.owner.getQualifiedName().toString();

        if (matchesPattern(className, BANNED_API_PATTERN)) {
            return buildDescription(tree)
                .setMessage("Use public platform APIs instead of internal APIs")
                .build();
        }

        return Description.NO_MATCH;
    }
}
```

### Check 2: Verticle Deployment Pattern

```java
@BugPattern(
    name = "ImproperVerticleDeployment",
    summary = "Verticles must be deployed through PlatformVerticleDeployer",
    severity = ERROR
)
public class VerticleDeploymentCheck extends BugChecker
    implements MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> BANNED_DEPLOY =
        MethodMatchers.instanceMethod()
            .onDescendantOf("io.vertx.core.Vertx")
            .named("deployVerticle");

    @Override
    public Description matchMethodInvocation(
        MethodInvocationTree tree, VisitorState state) {

        if (BANNED_DEPLOY.matches(tree, state)) {
            return buildDescription(tree)
                .setMessage(
                    "Use PlatformVerticleDeployer.deploy() instead of " +
                    "Vertx.deployVerticle() to ensure proper lifecycle management"
                )
                .addFix(suggestReplacement(tree, state))
                .build();
        }

        return Description.NO_MATCH;
    }

    private SuggestedFix suggestReplacement(
        MethodInvocationTree tree, VisitorState state) {
        // Provide automated fix suggestion
        return SuggestedFix.builder()
            .replace(tree,
                "PlatformVerticleDeployer.deploy(vertx, verticleClass)")
            .addImport("com.yourcompany.platform.PlatformVerticleDeployer")
            .build();
    }
}
```

---

## Build Blocking Mechanism

### 1. Fail Fast Strategy

```groovy
task analyzeDependencySources {
    doLast {
        def violations = performAnalysis()

        if (violations.hasErrors()) {
            def report = generateReport(violations)
            logger.error(report)

            throw new GradleException(
                "Build blocked: ${violations.errorCount} compliance " +
                "violations found in dependencies. " +
                "See report at ${reportFile}"
            )
        }
    }
}

// Block compilation if analysis fails
tasks.compileJava.dependsOn(analyzeDependencySources)
```

### 2. Grace Period Strategy

```groovy
dependencyAnalysis {
    enforcement {
        // Warn only mode (for gradual rollout)
        mode = 'WARN' // or 'BLOCK'

        // Grace period for specific dependencies
        gracePeriod {
            'com.yourcompany.wrappers:legacy-utils' {
                until = '2025-12-31'
                maxViolations = 5
            }
        }
    }
}
```

### 3. Exemption Mechanism

```groovy
dependencyAnalysis {
    exemptions {
        // Temporary exemption with justification
        exempt('com.yourcompany.wrappers:special-lib:1.0.0') {
            reason = 'JIRA-1234: Legacy code, will be fixed in 2.0.0'
            expiresOn = '2025-06-30'
            approvedBy = 'platform-team'
        }
    }
}
```

---

## Caching and Performance Optimization

### 1. Multi-Level Caching

```
Level 1: Gradle Build Cache
  - Cache analysis results per dependency version
  - Key: dependency coordinates + Error Prone checks version

Level 2: Local File Cache
  - Cache downloaded source JARs
  - Reuse across builds

Level 3: Remote Cache (CI/CD)
  - Share analysis results across team
  - Gradle Enterprise or custom cache
```

### 2. Incremental Analysis

```groovy
task analyzeDependencySources {
    inputs.files(configurations.runtimeClasspath)
    inputs.property('errorProneVersion', errorProneVersion)
    inputs.property('checksVersion', checksVersion)

    outputs.file("${buildDir}/reports/dependency-analysis/report.json")
    outputs.cacheIf { true }

    doLast {
        // Only analyze changed dependencies
        def changedDeps = detectChangedDependencies()
        analyzeOnly(changedDeps)
    }
}
```

### 3. Parallel Analysis

```java
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);

List<Future<AnalysisResult>> futures = dependencies.stream()
    .map(dep -> executor.submit(() -> analyzeDependency(dep)))
    .collect(Collectors.toList());

List<AnalysisResult> results = futures.stream()
    .map(f -> f.get())
    .collect(Collectors.toList());
```

---

## Monitoring and Reporting

### 1. Dashboard Metrics

Track across organization:
- Number of violations per team
- Most common violations
- Compliance trends over time
- Build blocking frequency

### 2. Integration with Tools

```
- Slack/Teams notifications on violations
- JIRA ticket creation for violations
- Metrics to Prometheus/Grafana
- Reports to Confluence/Wiki
```

### 3. Violation Report Format

```json
{
  "analysisTimestamp": "2025-11-06T10:30:00Z",
  "dependencies": [
    {
      "coordinates": "com.yourcompany.wrappers:vertx-utils:1.2.3",
      "violations": [
        {
          "check": "BannedPlatformApiUsage",
          "severity": "ERROR",
          "file": "src/main/java/com/example/VerticleHelper.java",
          "line": 45,
          "message": "Direct usage of internal API",
          "suggestion": "Use PlatformVerticleDeployer instead"
        }
      ],
      "totalErrors": 1,
      "totalWarnings": 0
    }
  ],
  "summary": {
    "totalDependenciesAnalyzed": 5,
    "dependenciesWithViolations": 1,
    "totalErrors": 1,
    "totalWarnings": 0,
    "buildBlocked": true
  }
}
```

---

## Migration Strategy

### Phase 1: Discovery (Week 1-2)
1. Enable plugin in WARN mode
2. Collect baseline metrics
3. Identify most common violations
4. Communicate findings to teams

### Phase 2: Education (Week 3-4)
1. Document approved patterns
2. Provide migration examples
3. Office hours for teams
4. Update wrapper libraries

### Phase 3: Soft Enforcement (Week 5-8)
1. Enable BLOCK mode for new violations
2. Grace period for existing violations
3. Team-by-team rollout
4. Monitor and assist

### Phase 4: Full Enforcement (Week 9+)
1. Remove grace periods
2. Block all violations
3. Continuous monitoring
4. Regular check updates

---

## Alternative: Allowlist Approach

Instead of analyzing all dependencies, maintain an allowlist:

```groovy
dependencyAnalysis {
    allowlist {
        // Only these validated dependencies are allowed
        allow('com.yourcompany.wrappers:vertx-utils:1.2.3') {
            validatedOn = '2025-01-15'
            validatedBy = 'platform-team'
        }

        // Block everything else
        blockUnlisted = true
    }
}
```

This is simpler but requires manual maintenance.

---

## Comparison Matrix

| Approach | Accuracy | Performance | Complexity | Maintenance | Recommended |
|----------|----------|-------------|------------|-------------|-------------|
| Dependency Source Analysis | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ✅ Yes |
| Bytecode Analysis | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ❌ No |
| Central Validation | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ✅ Yes |
| Multi-Module | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ❌ No |
| Annotation-Based | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⚠️ Limited |

---

## Summary

**Recommended Implementation:**
1. **Short-term:** Implement Dependency Source Analysis in your Gradle plugin
2. **Long-term:** Add Central Validation at publishing time
3. **Custom checks:** Create Error Prone checks specific to your platform APIs
4. **Build blocking:** Fail builds on violations with exemption mechanism
5. **Gradual rollout:** Start with WARN mode, collect data, then enforce

This hybrid approach provides:
- ✅ Comprehensive coverage (current repo + dependencies)
- ✅ Reuses existing Error Prone infrastructure
- ✅ Scales across organization
- ✅ Maintains build performance with caching
- ✅ Provides clear feedback to developers
- ✅ Prevents future violations at source

Next steps: I'll create example implementations for you.
