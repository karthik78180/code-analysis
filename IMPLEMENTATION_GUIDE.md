# Implementation Guide: Error Prone Dependency Analysis

This guide provides step-by-step instructions for implementing dependency analysis with Error Prone in your organization.

## Overview

This solution analyzes not just your project's source code, but also the source code of internal dependencies (wrapper libraries) to enforce coding standards across your entire codebase.

---

## Project Structure

```
code-analysis/
├── DESIGN.md                           # Detailed design document
├── IMPLEMENTATION_GUIDE.md             # This file
│
├── platform-standards-plugin/          # Gradle plugin for dependency analysis
│   ├── build.gradle
│   └── src/main/java/
│       └── com/yourcompany/platform/gradle/plugin/
│           ├── PlatformStandardsPlugin.java
│           ├── config/                 # Configuration DSL classes
│           ├── tasks/                  # Gradle tasks
│           ├── analyzers/              # Source analysis logic
│           ├── model/                  # Data models
│           └── utils/                  # Utilities
│
├── errorprone-custom-checks/           # Custom Error Prone checks
│   ├── build.gradle
│   └── src/main/java/
│       └── com/yourcompany/platform/errorprone/checks/
│           ├── BannedPlatformApiUsageCheck.java
│           ├── ImproperVerticleDeploymentCheck.java
│           └── ThreadSafetyViolationCheck.java
│
└── example-consumer-project/           # Example usage
    ├── build.gradle
    └── src/main/java/
```

---

## Phase 1: Build and Publish Custom Error Prone Checks

### Step 1.1: Build Error Prone Checks

```bash
cd errorprone-custom-checks
./gradlew build
```

### Step 1.2: Publish to Internal Repository

```bash
./gradlew publish \
  -PartifactoryUser=your-username \
  -PartifactoryPassword=your-token
```

Or set environment variables:
```bash
export ARTIFACTORY_USER=your-username
export ARTIFACTORY_PASSWORD=your-token
./gradlew publish
```

### Step 1.3: Verify Publication

Check that the artifact is available at:
```
https://artifactory.yourcompany.com/maven-internal/
  com/yourcompany/platform/errorprone-custom-checks/1.0.0/
```

---

## Phase 2: Build and Publish Gradle Plugin

### Step 2.1: Build Plugin

```bash
cd platform-standards-plugin
./gradlew build
```

### Step 2.2: Test Plugin Locally (Optional)

Before publishing, you can test locally:

```bash
./gradlew publishToMavenLocal
```

Then in a test project:
```groovy
buildscript {
    repositories {
        mavenLocal()
    }
    dependencies {
        classpath 'com.yourcompany.platform:platform-standards-plugin:1.0.0'
    }
}

apply plugin: 'com.yourcompany.platform-standards'
```

### Step 2.3: Publish Plugin

```bash
./gradlew publish \
  -PartifactoryUser=your-username \
  -PartifactoryPassword=your-token
```

### Step 2.4: Publish to Gradle Plugin Portal (Optional)

If you want to publish to the public Gradle Plugin Portal:

1. Set up account at https://plugins.gradle.org/
2. Configure credentials in `~/.gradle/gradle.properties`:
   ```properties
   gradle.publish.key=your-key
   gradle.publish.secret=your-secret
   ```
3. Publish:
   ```bash
   ./gradlew publishPlugins
   ```

---

## Phase 3: Ensure Dependencies Publish Sources

For dependency analysis to work, wrapper libraries must publish source JARs.

### Step 3.1: Update Wrapper Library Build Files

In each wrapper library's `build.gradle`:

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
}

java {
    // Enable source JAR generation
    withSourcesJar()
}

publishing {
    publications {
        maven(MavenPublication) {
            from components.java
            // This automatically includes the sources JAR
        }
    }
}
```

### Step 3.2: Verify Sources Are Published

After publishing, verify the sources JAR exists:
```
https://artifactory.yourcompany.com/maven-internal/
  com/yourcompany/wrappers/vertx-utils/1.2.3/
    vertx-utils-1.2.3.jar
    vertx-utils-1.2.3-sources.jar    ← Must exist
    vertx-utils-1.2.3.pom
```

### Step 3.3: Handle Legacy Libraries Without Sources

If some libraries don't have sources published yet:

**Option A:** Temporarily exclude them
```groovy
platformStandards {
    dependencyAnalysis {
        scope {
            excludeGroups = ['com.yourcompany.wrappers:legacy-lib']
        }
    }
}
```

**Option B:** Add grace period
```groovy
platformStandards {
    dependencyAnalysis {
        reporting {
            mode = 'WARN' // Warn but don't block
        }
    }
}
```

---

## Phase 4: Configure Consumer Projects

### Step 4.1: Basic Configuration

In your application's `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'com.yourcompany.platform-standards' version '1.0.0'
}

platformStandards {
    dependencyAnalysis {
        enabled = true

        scope {
            includeGroups = ['com.yourcompany.wrappers']
        }

        reporting {
            failOnViolation = true
        }
    }
}
```

### Step 4.2: Advanced Configuration

For more control:

```groovy
platformStandards {
    dependencyAnalysis {
        enabled = true

        scope {
            // Analyze these groups
            includeGroups = [
                'com.yourcompany.wrappers',
                'com.yourcompany.common'
            ]

            // But exclude these specific artifacts
            excludeGroups = [
                'com.yourcompany.platform:*',
                'com.yourcompany.wrappers:legacy-*'
            ]

            // Only analyze certain configurations
            configurations = ['implementation', 'api']
        }

        analysis {
            // Specify which checks to run
            errorProneChecks = [
                'BannedPlatformApiUsage',
                'ImproperVerticleDeployment',
                'ThreadSafetyViolation'
            ]

            // Enable caching for faster builds
            cacheEnabled = true
            cacheDir = file("${buildDir}/dependency-cache")
        }

        reporting {
            // Block mode: fail the build
            // Warn mode: only log warnings
            mode = 'BLOCK'

            failOnViolation = true
            generateReport = true

            // Allow some warnings during transition
            maxErrors = 0
            maxWarnings = 5
        }

        // Performance tuning
        parallel = true
        maxWorkers = Runtime.runtime.availableProcessors()
    }
}
```

### Step 4.3: Command-Line Overrides

You can override settings from the command line:

```bash
# Run in WARN mode only
./gradlew build -PdependencyAnalysis.mode=WARN

# Skip dependency analysis
./gradlew build -PdependencyAnalysis.enabled=false

# Allow more warnings
./gradlew build -PdependencyAnalysis.maxWarnings=20
```

---

## Phase 5: Gradual Rollout Strategy

### Week 1-2: Discovery Phase

1. Deploy in WARN mode to all projects:
   ```groovy
   reporting {
       mode = 'WARN'
       failOnViolation = false
   }
   ```

2. Collect metrics:
   - How many dependencies have violations?
   - What are the most common violations?
   - Which teams are affected?

3. Generate baseline reports:
   ```bash
   ./gradlew analyzeDependencySources --continue
   ```

### Week 3-4: Communication Phase

1. Share findings with teams
2. Provide documentation on approved patterns
3. Host Q&A sessions
4. Create migration examples

Example communication:

```
Subject: New Dependency Analysis for Platform Standards

We're introducing dependency analysis to maintain code quality across all
internal libraries. Current findings:

Top Violations:
1. Direct Vertx.deployVerticle() usage (15 occurrences)
   → Use PlatformVerticleDeployer.deploy()

2. Internal API usage (8 occurrences)
   → Use public platform APIs

Migration guide: https://wiki.yourcompany.com/platform-standards
Office hours: Tuesdays 2-3pm
Questions: #platform-team Slack channel
```

### Week 5-6: Fix Phase

1. Teams fix violations in their wrapper libraries
2. Platform team provides assistance
3. Publish updated versions

### Week 7-8: Enforcement Phase

1. Switch to BLOCK mode:
   ```groovy
   reporting {
       mode = 'BLOCK'
       failOnViolation = true
   }
   ```

2. Block new violations only (grace period for old):
   ```groovy
   // Custom logic in plugin to check violation timestamps
   ```

3. Monitor CI/CD for blocked builds

### Week 9+: Full Enforcement

1. Remove all grace periods
2. Block all violations
3. Continuous monitoring and improvement
4. Regular review of checks

---

## Phase 6: CI/CD Integration

### Step 6.1: GitHub Actions

```yaml
name: Build and Verify

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
            build/dependency-analysis-cache
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

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

### Step 6.2: Jenkins

```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
        }

        stage('Dependency Analysis') {
            steps {
                sh './gradlew analyzeDependencySources'
            }
        }
    }

    post {
        always {
            publishHTML([
                reportName: 'Dependency Analysis',
                reportDir: 'build/reports/dependency-analysis',
                reportFiles: 'dependency-analysis.html',
                keepAll: true
            ])
        }
        failure {
            slackSend(
                color: 'danger',
                message: "Build failed due to dependency compliance violations: ${env.BUILD_URL}"
            )
        }
    }
}
```

### Step 6.3: GitLab CI

```yaml
image: gradle:7.6-jdk11

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

cache:
  paths:
    - .gradle/wrapper
    - .gradle/caches
    - build/dependency-analysis-cache

stages:
  - build
  - analyze
  - publish

build:
  stage: build
  script:
    - gradle clean build

analyze-dependencies:
  stage: analyze
  script:
    - gradle analyzeDependencySources
  artifacts:
    when: always
    reports:
      junit: build/test-results/test/*.xml
    paths:
      - build/reports/dependency-analysis/
```

---

## Phase 7: Monitoring and Reporting

### Step 7.1: Centralized Dashboard

Create a dashboard to track compliance across the organization:

**Metrics to Track:**
- Number of analyzed dependencies per project
- Violation counts by type
- Violation trends over time
- Build failure rate due to violations
- Time to fix violations

**Implementation Options:**

**Option A: Gradle Enterprise**
If you have Gradle Enterprise, use custom values:

```groovy
// In plugin
buildScan {
    tag('dependency-analysis')
    value('violations-errors', result.totalErrors.toString())
    value('violations-warnings', result.totalWarnings.toString())
}
```

**Option B: Custom Dashboard**
Send metrics to InfluxDB/Prometheus:

```groovy
// In plugin
void reportMetrics(AnalysisResult result) {
    // Send to metrics system
    httpClient.post('http://metrics.yourcompany.com/api/violations') {
        json([
            project: project.name,
            timestamp: System.currentTimeMillis(),
            errors: result.totalErrors,
            warnings: result.totalWarnings
        ])
    }
}
```

### Step 7.2: Notifications

**Slack Integration:**

```groovy
// In plugin or CI/CD
void notifySlack(AnalysisResult result) {
    if (result.hasViolations()) {
        slackSend(
            channel: '#platform-violations',
            color: 'warning',
            message: """
                Dependency violations found in ${project.name}:
                - Errors: ${result.totalErrors}
                - Warnings: ${result.totalWarnings}
                See report: ${reportUrl}
            """
        )
    }
}
```

**Email Notifications:**

Configure in CI/CD to email team leads when violations are found.

---

## Phase 8: Advanced Features

### Step 8.1: Exemption Management

Create exemptions for specific cases:

```groovy
platformStandards {
    dependencyAnalysis {
        exemptions {
            // Temporary exemption
            exempt('com.yourcompany.wrappers:legacy-lib:1.0.0') {
                reason = 'JIRA-1234: Scheduled for refactoring in Q2'
                expiresOn = '2025-06-30'
                approvedBy = 'platform-team-lead'
                maxViolations = 3 // Allow up to 3 violations
            }

            // Permanent exemption (use sparingly)
            exempt('com.yourcompany.wrappers:special-case:*') {
                reason = 'ARCH-567: Architectural exception approved'
                permanent = true
            }
        }
    }
}
```

### Step 8.2: Custom Checks

Add your own Error Prone checks:

```java
@AutoService(BugChecker.class)
@BugPattern(
    name = "CustomCompanyCheck",
    summary = "Your custom check",
    severity = ERROR
)
public class CustomCompanyCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

    @Override
    public Description matchMethodInvocation(
        MethodInvocationTree tree,
        VisitorState state
    ) {
        // Your check logic
        return Description.NO_MATCH;
    }
}
```

Register in `META-INF/services/com.google.errorprone.bugpatterns.BugChecker`:
```
com.yourcompany.platform.errorprone.checks.CustomCompanyCheck
```

### Step 8.3: Allowlist Mode

Instead of analyzing all dependencies, maintain an allowlist:

```groovy
platformStandards {
    dependencyAnalysis {
        allowlistMode {
            enabled = true

            // Only these validated versions are allowed
            allow('com.yourcompany.wrappers:vertx-utils:1.2.3')
            allow('com.yourcompany.wrappers:database-helper:2.0.1')

            // Block any dependency not in the allowlist
            blockUnlisted = true

            // Where to find the allowlist
            allowlistFile = file('config/dependency-allowlist.json')
        }
    }
}
```

Allowlist file format:
```json
{
  "allowedDependencies": [
    {
      "coordinates": "com.yourcompany.wrappers:vertx-utils:1.2.3",
      "validatedOn": "2025-01-15",
      "validatedBy": "platform-team",
      "checks": ["BannedPlatformApiUsage", "ImproperVerticleDeployment"]
    }
  ]
}
```

---

## Troubleshooting

### Issue: "Could not resolve sources for dependency"

**Cause:** Source JAR not published

**Solution:**
1. Check if sources JAR exists in repository
2. Ensure wrapper library publishes sources (see Phase 3)
3. Temporarily exclude the dependency:
   ```groovy
   scope {
       excludeGroups = ['problematic.group:artifact']
   }
   ```

### Issue: "Analysis is too slow"

**Solutions:**
1. Enable parallel analysis:
   ```groovy
   parallel = true
   maxWorkers = 8
   ```

2. Enable caching:
   ```groovy
   analysis {
       cacheEnabled = true
   }
   ```

3. Reduce scope:
   ```groovy
   scope {
       configurations = ['implementation'] // Only analyze implementation
   }
   ```

4. Use Gradle Build Cache:
   ```properties
   org.gradle.caching=true
   ```

### Issue: "False positives in checks"

**Solutions:**
1. Refine the Error Prone check logic
2. Add exemptions for specific cases
3. Use @SuppressWarnings in source code:
   ```java
   @SuppressWarnings("BannedPlatformApiUsage")
   public void specialCase() {
       // Justified usage of internal API
   }
   ```

### Issue: "Build fails in CI but passes locally"

**Causes:**
- Different dependency versions resolved
- Cache not shared between local and CI
- Environment-specific configuration

**Solutions:**
1. Use dependency locking:
   ```groovy
   dependencyLocking {
       lockAllConfigurations()
   }
   ```

2. Share cache between local and CI (Gradle Enterprise)

3. Run with same flags:
   ```bash
   ./gradlew clean build --no-build-cache
   ```

---

## Best Practices

### 1. Start Small
- Begin with a single project/team
- Validate the approach
- Iterate based on feedback
- Then roll out organization-wide

### 2. Communicate Clearly
- Explain the "why" behind the checks
- Provide clear migration paths
- Offer support during transition
- Celebrate successes

### 3. Keep Checks Focused
- Each check should have a clear purpose
- Avoid overly restrictive rules
- Balance safety with developer productivity
- Regular review and refinement

### 4. Automate Everything
- Automatic violation detection
- Automatic reporting
- Automatic fixes where possible (Error Prone suggested fixes)
- Integration with development workflow

### 5. Make It Fast
- Use caching aggressively
- Analyze dependencies in parallel
- Only analyze relevant dependencies
- Share cache across team (Gradle Enterprise)

### 6. Provide Visibility
- Clear error messages
- Actionable suggestions
- Easy access to reports
- Metrics dashboard

### 7. Be Pragmatic
- Allow exemptions with justification
- Grace periods for legacy code
- WARN mode during rollout
- Balance perfection with progress

---

## Maintenance

### Regular Tasks

**Monthly:**
- Review violation trends
- Update checks based on new patterns
- Audit exemptions (remove expired ones)
- Review allowlist (if using)

**Quarterly:**
- Review and update documentation
- Gather feedback from teams
- Assess new Error Prone checks to add
- Performance optimization review

**Annually:**
- Major version updates
- Architectural review
- ROI assessment
- Strategic planning for new checks

### Versioning

Follow semantic versioning:

```
1.0.0 → Initial release
1.1.0 → New check added (backward compatible)
1.0.1 → Bug fix in existing check
2.0.0 → Breaking change (e.g., check made more strict)
```

Communicate breaking changes well in advance.

---

## Success Metrics

Track these to measure success:

1. **Coverage:**
   - % of projects using the plugin
   - % of internal dependencies analyzed

2. **Quality:**
   - Number of violations found and fixed
   - Reduction in production incidents related to detected patterns

3. **Performance:**
   - Average build time impact
   - Cache hit rate

4. **Adoption:**
   - Time to fix violations (lower is better)
   - Build failure rate (should stabilize over time)

---

## Support

### Resources
- **Documentation:** https://wiki.yourcompany.com/platform-standards
- **Source Code:** https://git.yourcompany.com/platform/standards-plugin
- **Issue Tracker:** https://jira.yourcompany.com/browse/PLATFORM
- **Slack:** #platform-team

### Getting Help
1. Check documentation first
2. Search existing issues/questions
3. Ask in Slack #platform-team
4. Create a JIRA ticket for bugs/feature requests

### Contributing
1. Fork the repository
2. Create a feature branch
3. Add tests for new checks
4. Submit pull request
5. Platform team will review

---

## Conclusion

This implementation provides:
- ✅ Comprehensive analysis of project and dependency code
- ✅ Automated enforcement of coding standards
- ✅ Clear feedback to developers
- ✅ Flexible configuration for different needs
- ✅ Scalable across organization
- ✅ Integration with existing CI/CD

The key to success is gradual rollout, clear communication, and continuous improvement based on feedback.

**Next Steps:**
1. Review the DESIGN.md for architectural details
2. Build and publish the Error Prone checks
3. Build and publish the Gradle plugin
4. Pilot with one team/project
5. Iterate and improve
6. Roll out organization-wide

Good luck! 🚀
