package com.yourcompany.platform.errorprone.checks;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.*;

/**
 * Error Prone check that detects usage of banned platform internal APIs.
 *
 * This check ensures teams use public platform abstractions instead of
 * accessing internal implementation details.
 */
@AutoService(BugChecker.class)
@BugPattern(
    name = "BannedPlatformApiUsage",
    summary = "Direct usage of platform internal APIs is not allowed",
    explanation = "Platform internal APIs (packages under com.yourcompany.platform.internal.*) " +
                  "are subject to change without notice. Use the public platform APIs instead.",
    severity = ERROR,
    tags = BugPattern.StandardTags.FRAGILE_CODE
)
public class BannedPlatformApiUsageCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

    private static final String INTERNAL_PACKAGE_PREFIX = "com.yourcompany.platform.internal";

    // Match any method call from internal packages
    private static final Matcher<ExpressionTree> INTERNAL_API_MATCHER =
        anyOf(
            // Match methods from classes in internal packages
            (tree, state) -> {
                MethodSymbol method = ASTHelpers.getSymbol((MethodInvocationTree) tree);
                if (method == null) return false;

                String className = method.owner.getQualifiedName().toString();
                return className.startsWith(INTERNAL_PACKAGE_PREFIX);
            }
        );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!INTERNAL_API_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        MethodSymbol method = ASTHelpers.getSymbol(tree);
        String className = method.owner.getQualifiedName().toString();
        String methodName = method.getSimpleName().toString();

        String message = String.format(
            "Usage of internal API %s.%s() is not allowed. Use public platform APIs instead.",
            className, methodName
        );

        // Try to suggest a replacement if we have a known mapping
        SuggestedFix fix = suggestPublicAlternative(className, methodName, tree, state);

        Description.Builder builder = buildDescription(tree)
            .setMessage(message);

        if (fix != null) {
            builder.addFix(fix);
        }

        return builder.build();
    }

    /**
     * Suggests public API alternatives for common internal API usage.
     */
    private SuggestedFix suggestPublicAlternative(
        String className,
        String methodName,
        MethodInvocationTree tree,
        VisitorState state
    ) {
        // Example mappings - customize for your platform
        if (className.contains("InternalVerticleManager")) {
            return SuggestedFix.builder()
                .replace(tree, "PlatformVerticleDeployer.deploy(/* configure here */)")
                .addImport("com.yourcompany.platform.PlatformVerticleDeployer")
                .build();
        }

        // Add more mappings as needed
        return null;
    }
}
