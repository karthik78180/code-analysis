package com.yourcompany.platform.errorprone.checks;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

/**
 * Error Prone check that ensures Verticles are deployed through the platform's
 * deployment API rather than directly through Vert.x.
 *
 * This ensures proper lifecycle management, monitoring, and configuration.
 */
@AutoService(BugChecker.class)
@BugPattern(
    name = "ImproperVerticleDeployment",
    summary = "Verticles must be deployed through PlatformVerticleDeployer",
    explanation = "Direct usage of Vertx.deployVerticle() bypasses platform lifecycle management, " +
                  "monitoring, and configuration. Use PlatformVerticleDeployer.deploy() instead to " +
                  "ensure proper integration with platform services.",
    severity = ERROR,
    tags = BugPattern.StandardTags.FRAGILE_CODE
)
public class ImproperVerticleDeploymentCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

    // Match Vertx.deployVerticle() method calls
    private static final Matcher<ExpressionTree> VERTX_DEPLOY_MATCHER =
        MethodMatchers.instanceMethod()
            .onDescendantOf("io.vertx.core.Vertx")
            .namedAnyOf("deployVerticle");

    // But allow if it's within PlatformVerticleDeployer itself
    private static final Matcher<MethodInvocationTree> WITHIN_PLATFORM_DEPLOYER =
        (tree, state) -> {
            String enclosingClass = ASTHelpers.enclosingClass(
                ASTHelpers.getSymbol(tree)
            ).getQualifiedName().toString();

            return enclosingClass.equals("com.yourcompany.platform.PlatformVerticleDeployer") ||
                   enclosingClass.startsWith("com.yourcompany.platform.internal");
        };

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // Check if it's a Vertx.deployVerticle() call
        if (!VERTX_DEPLOY_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // Allow if within platform code
        if (WITHIN_PLATFORM_DEPLOYER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // Build the violation description
        SuggestedFix fix = buildSuggestedFix(tree, state);

        return buildDescription(tree)
            .setMessage(
                "Use PlatformVerticleDeployer.deploy() instead of Vertx.deployVerticle() " +
                "to ensure proper lifecycle management and platform integration"
            )
            .addFix(fix)
            .build();
    }

    private SuggestedFix buildSuggestedFix(MethodInvocationTree tree, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();

        // Add import for PlatformVerticleDeployer
        fix.addImport("com.yourcompany.platform.PlatformVerticleDeployer");

        // Try to build a replacement
        // This is a simplified example - real implementation would parse arguments
        String receiver = state.getSourceForNode(ASTHelpers.getReceiver(tree));

        fix.replace(
            tree,
            String.format("PlatformVerticleDeployer.deploy(%s, /* verticle config */)", receiver)
        );

        return fix.build();
    }
}
