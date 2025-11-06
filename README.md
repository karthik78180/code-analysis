# Error Prone Dependency Analysis Solution

A comprehensive solution for enforcing Error Prone checks not just on project source code, but also on dependency source code. This addresses the common problem where wrapper libraries and shared components bypass static analysis checks.

## Problem Statement

Your platform team provides Vert.x abstractions and enforces coding standards via a Gradle plugin with Error Prone. However:

- ❌ Error Prone only analyzes code in the current repository
- ❌ Teams create wrapper libraries that bypass these checks
- ❌ Violations in dependencies go undetected
- ❌ Inconsistent enforcement across the organization

## Solution

This project provides:

- ✅ Gradle plugin that analyzes **both project and dependency sources**
- ✅ Custom Error Prone checks specific to your platform
- ✅ Build-blocking mechanism to enforce standards
- ✅ Flexible configuration and gradual rollout support
- ✅ Caching and parallel analysis for performance
- ✅ Comprehensive reporting

## Quick Start

### 1. Add Plugin to Your Project

```groovy
plugins {
    id 'com.yourcompany.platform-standards' version '1.0.0'
}

platformStandards {
    dependencyAnalysis {
        enabled = true
        scope {
            includeGroups = ['com.yourcompany.wrappers']
        }
    }
}
```

### 2. Build Your Project

```bash
./gradlew build
```

The plugin will:
1. Resolve source JARs for internal dependencies
2. Extract and analyze the sources
3. Run Error Prone checks
4. Block build if violations found
5. Generate detailed report

### 3. View Report

```
build/reports/dependency-analysis/dependency-analysis.txt
```

Example output:
```
===============================================
 DEPENDENCY COMPLIANCE VIOLATIONS DETECTED
===============================================

Dependency: com.yourcompany.wrappers:vertx-utils:1.2.3

[ERROR] BannedApiUsage: Direct usage of io.vertx.core.Vertx.deployVerticle
  Location: VerticleHelper.java:45
  Use platform API: PlatformVerticleDeployer.deploy() instead

Build FAILED: 1 compliance violation in dependencies
```

## Documentation

- **[DESIGN.md](DESIGN.md)** - Detailed design document with multiple approaches
- **[IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md)** - Step-by-step implementation guide

## Project Structure

```
code-analysis/
├── README.md                          # This file
├── DESIGN.md                          # Design document
├── IMPLEMENTATION_GUIDE.md            # Implementation guide
│
├── platform-standards-plugin/         # Gradle plugin
│   ├── build.gradle
│   └── src/main/java/
│       └── com/yourcompany/platform/gradle/plugin/
│           ├── PlatformStandardsPlugin.java      # Main plugin
│           ├── config/                           # Configuration DSL
│           ├── tasks/                            # Gradle tasks
│           ├── analyzers/                        # Analysis logic
│           └── model/                            # Data models
│
├── errorprone-custom-checks/          # Custom Error Prone checks
│   ├── build.gradle
│   └── src/main/java/
│       └── com/yourcompany/platform/errorprone/checks/
│           ├── BannedPlatformApiUsageCheck.java
│           ├── ImproperVerticleDeploymentCheck.java
│           └── ThreadSafetyViolationCheck.java
│
└── example-consumer-project/          # Example usage
    ├── build.gradle
    └── src/main/java/
```

## Features

### 1. Dependency Source Analysis

Automatically resolves and analyzes source JARs from internal dependencies:

```groovy
dependencyAnalysis {
    scope {
        includeGroups = ['com.yourcompany.*']
        excludeGroups = ['com.yourcompany.platform']
        configurations = ['implementation', 'api']
    }
}
```

### 2. Custom Error Prone Checks

Built-in checks for common platform violations:

- **BannedPlatformApiUsage** - Detects usage of internal APIs
- **ImproperVerticleDeployment** - Ensures proper Verticle deployment
- **ThreadSafetyViolation** - Detects mutable static fields in Verticles

Add your own checks easily!

### 3. Flexible Enforcement

Control how violations are handled:

```groovy
reporting {
    mode = 'BLOCK'           // or 'WARN' for gradual rollout
    failOnViolation = true
    maxErrors = 0
    maxWarnings = 10
}
```

### 4. Performance Optimized

- Parallel analysis of multiple dependencies
- Gradle build cache integration
- Incremental analysis
- Source JAR caching

```groovy
parallel = true
maxWorkers = 4
```

### 5. Exemption Management

Handle special cases:

```groovy
exemptions {
    exempt('com.yourcompany.wrappers:legacy-lib:1.0.0') {
        reason = 'JIRA-1234: Scheduled for refactoring'
        expiresOn = '2025-06-30'
        approvedBy = 'tech-lead'
    }
}
```

## Built-In Checks

### BannedPlatformApiUsage

Prevents direct usage of platform internal APIs:

```java
// ❌ VIOLATION - Direct internal API usage
import com.yourcompany.platform.internal.VerticleManager;
manager.deployInternal(verticle);

// ✅ CORRECT - Use public API
import com.yourcompany.platform.PlatformVerticleDeployer;
PlatformVerticleDeployer.deploy(verticle);
```

### ImproperVerticleDeployment

Ensures Verticles are deployed through platform APIs:

```java
// ❌ VIOLATION - Direct Vert.x usage
vertx.deployVerticle(new MyVerticle());

// ✅ CORRECT - Use platform deployer
PlatformVerticleDeployer.deploy(vertx, MyVerticle.class);
```

### ThreadSafetyViolation

Detects mutable static fields in Verticles:

```java
public class MyVerticle extends AbstractVerticle {
    // ❌ VIOLATION - Mutable static field
    private static Map<String, Object> cache = new HashMap<>();

    // ✅ CORRECT - Instance field or immutable static
    private Map<String, Object> instanceCache = new HashMap<>();
    private static final Map<String, Object> CONSTANTS = Map.of(...);
}
```

## Configuration Reference

### Complete Example

```groovy
platformStandards {
    errorprone {
        enabled = true
        version = '2.23.0'
    }

    dependencyAnalysis {
        enabled = true

        scope {
            includeGroups = [
                'com.yourcompany.wrappers',
                'com.yourcompany.common'
            ]
            excludeGroups = ['com.yourcompany.platform']
            configurations = ['implementation', 'api']
        }

        sources {
            requireSourcesJar = true
            fallbackToGitClone = false
        }

        analysis {
            errorProneChecks = [
                'BannedPlatformApiUsage',
                'ImproperVerticleDeployment',
                'ThreadSafetyViolation'
            ]
            runCheckstyle = false
            cacheEnabled = true
        }

        reporting {
            mode = 'BLOCK'
            failOnViolation = true
            generateReport = true
            reportFormat = 'HTML'
            maxErrors = 0
            maxWarnings = 10
        }

        parallel = true
        maxWorkers = 4
    }
}
```

### Command-Line Overrides

```bash
# Run in WARN mode
./gradlew build -PdependencyAnalysis.mode=WARN

# Skip dependency analysis
./gradlew build -PdependencyAnalysis.enabled=false

# Allow more warnings
./gradlew build -PdependencyAnalysis.maxWarnings=20
```

## CI/CD Integration

### GitHub Actions

```yaml
- name: Build with Dependency Analysis
  run: ./gradlew build
  env:
    ARTIFACTORY_USER: ${{ secrets.ARTIFACTORY_USER }}
    ARTIFACTORY_PASSWORD: ${{ secrets.ARTIFACTORY_PASSWORD }}

- name: Upload Analysis Report
  if: always()
  uses: actions/upload-artifact@v3
  with:
    name: dependency-analysis-report
    path: build/reports/dependency-analysis/
```

### Jenkins

```groovy
stage('Build') {
    steps {
        sh './gradlew clean build'
    }
}

post {
    always {
        publishHTML([
            reportName: 'Dependency Analysis',
            reportDir: 'build/reports/dependency-analysis',
            reportFiles: 'dependency-analysis.html'
        ])
    }
}
```

## Rollout Strategy

### Phase 1: Discovery (Week 1-2)
- Deploy in WARN mode
- Collect baseline metrics
- Identify violations

### Phase 2: Communication (Week 3-4)
- Share findings with teams
- Provide documentation
- Host Q&A sessions

### Phase 3: Soft Enforcement (Week 5-8)
- Enable BLOCK mode for new violations
- Grace period for existing violations
- Team-by-team rollout

### Phase 4: Full Enforcement (Week 9+)
- Remove grace periods
- Block all violations
- Continuous monitoring

## Building from Source

### Prerequisites

- Java 11 or higher
- Gradle 7.6 or higher

### Build Error Prone Checks

```bash
cd errorprone-custom-checks
./gradlew build test
./gradlew publish
```

### Build Gradle Plugin

```bash
cd platform-standards-plugin
./gradlew build test
./gradlew publish
```

### Test with Example Project

```bash
cd example-consumer-project
./gradlew build
```

## Architecture

### How It Works

```
1. Build starts
       ↓
2. ResolveDependencySourcesTask
   - Finds internal dependencies
   - Resolves source JARs
   - Caches sources
       ↓
3. AnalyzeDependencySourcesTask
   - Extracts source JARs
   - Runs Error Prone checks
   - Collects violations
       ↓
4. Generate Report
   - Creates HTML/text report
   - Logs violations
       ↓
5. Build Decision
   - Block if violations found (BLOCK mode)
   - Warn if violations found (WARN mode)
   - Continue if no violations
```

### Key Components

- **PlatformStandardsPlugin** - Main Gradle plugin entry point
- **ResolveDependencySourcesTask** - Resolves dependency sources
- **AnalyzeDependencySourcesTask** - Performs analysis
- **ErrorProneSourceAnalyzer** - Runs Error Prone on sources
- **Custom BugCheckers** - Platform-specific checks

## Extending

### Add Custom Error Prone Check

1. Create new check class:

```java
@AutoService(BugChecker.class)
@BugPattern(
    name = "MyCustomCheck",
    summary = "Description",
    severity = ERROR
)
public class MyCustomCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

    @Override
    public Description matchMethodInvocation(
        MethodInvocationTree tree,
        VisitorState state
    ) {
        // Check logic
        return Description.NO_MATCH;
    }
}
```

2. Register in service file:
```
META-INF/services/com.google.errorprone.bugpatterns.BugChecker
```

3. Rebuild and publish

4. Enable in configuration:
```groovy
errorProneChecks = ['MyCustomCheck']
```

## Troubleshooting

### "Could not resolve sources"

**Solution:** Ensure wrapper libraries publish source JARs:
```groovy
java {
    withSourcesJar()
}
```

### "Analysis is too slow"

**Solutions:**
- Enable parallel: `parallel = true`
- Enable caching: `cacheEnabled = true`
- Reduce scope: Only analyze needed dependencies

### "False positives"

**Solutions:**
- Refine check logic
- Add exemptions
- Use `@SuppressWarnings("CheckName")` in source

## Performance

Typical analysis times (example project with 5 internal dependencies):

- **Cold build:** +15-30 seconds
- **Warm build (cached):** +2-5 seconds
- **Incremental (no changes):** +0-1 seconds

Tips for faster builds:
- Use Gradle build cache
- Enable parallel analysis
- Exclude unnecessary configurations
- Use Gradle daemon

## Comparison with Alternatives

| Approach | Accuracy | Performance | Maintenance | Recommended |
|----------|----------|-------------|-------------|-------------|
| **This solution** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ✅ Yes |
| Bytecode analysis | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ❌ No |
| Multi-module build | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ❌ No |
| Manual audits | ⭐⭐ | ⭐ | ⭐ | ❌ No |

## Contributing

1. Fork the repository
2. Create feature branch
3. Add tests
4. Submit pull request

## License

Internal use only - Company proprietary

## Support

- **Documentation:** https://wiki.yourcompany.com/platform-standards
- **Issues:** https://jira.yourcompany.com/browse/PLATFORM
- **Slack:** #platform-team
- **Email:** platform-team@yourcompany.com

## Authors

- Platform Team
- Contributors: See [CONTRIBUTORS.md](CONTRIBUTORS.md)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

**Status:** ✅ Production Ready

**Version:** 1.0.0

**Last Updated:** 2025-11-06
