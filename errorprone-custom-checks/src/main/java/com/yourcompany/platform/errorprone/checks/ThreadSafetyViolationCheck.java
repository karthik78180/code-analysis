package com.yourcompany.platform.errorprone.checks;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import javax.lang.model.element.Modifier;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

/**
 * Error Prone check that detects thread safety issues common in Vert.x applications.
 *
 * Specifically detects:
 * - Mutable static fields in Verticle classes
 * - Shared mutable state that could cause race conditions
 */
@AutoService(BugChecker.class)
@BugPattern(
    name = "ThreadSafetyViolation",
    summary = "Mutable static field in Verticle may cause thread safety issues",
    explanation = "Verticles may be deployed multiple times and executed on different event loops. " +
                  "Mutable static fields can lead to race conditions and unexpected behavior. " +
                  "Use instance fields or make static fields immutable (final).",
    severity = WARNING,
    tags = BugPattern.StandardTags.CONCURRENCY
)
public class ThreadSafetyViolationCheck extends BugChecker
    implements BugChecker.VariableTreeMatcher {

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(tree);

        // Only check fields (not local variables or parameters)
        if (symbol == null || symbol.getKind() != javax.lang.model.element.ElementKind.FIELD) {
            return Description.NO_MATCH;
        }

        // Check if it's a static field
        if (!symbol.getModifiers().contains(Modifier.STATIC)) {
            return Description.NO_MATCH;
        }

        // Allow if it's final (immutable)
        if (symbol.getModifiers().contains(Modifier.FINAL)) {
            return Description.NO_MATCH;
        }

        // Check if the field is in a Verticle class
        ClassTree enclosingClass = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        if (!isVerticleClass(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        // Build violation
        Type fieldType = symbol.type;
        boolean isCollection = isCollectionType(fieldType);

        String message = String.format(
            "Mutable static field '%s' in Verticle class can cause thread safety issues. " +
            "%s",
            symbol.getSimpleName(),
            isCollection
                ? "Consider using instance fields or ConcurrentHashMap for thread-safe collections."
                : "Make the field final or use an instance field instead."
        );

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        // Suggest making it final if the type is immutable
        if (isEffectivelyImmutable(fieldType)) {
            fixBuilder.merge(
                SuggestedFix.prefixWith(tree, "final ")
            );
        }

        SuggestedFix fix = fixBuilder.isEmpty() ? null : fixBuilder.build();

        Description.Builder descBuilder = buildDescription(tree)
            .setMessage(message);

        if (fix != null) {
            descBuilder.addFix(fix);
        }

        return descBuilder.build();
    }

    /**
     * Checks if the class extends AbstractVerticle or implements Verticle.
     */
    private boolean isVerticleClass(ClassTree classTree, VisitorState state) {
        Symbol.ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
        if (classSymbol == null) {
            return false;
        }

        // Check if extends AbstractVerticle
        Type superclass = classSymbol.getSuperclass();
        if (superclass != null) {
            String superName = superclass.tsym.getQualifiedName().toString();
            if (superName.equals("io.vertx.core.AbstractVerticle")) {
                return true;
            }
        }

        // Check if implements Verticle interface
        for (Type iface : classSymbol.getInterfaces()) {
            String ifaceName = iface.tsym.getQualifiedName().toString();
            if (ifaceName.equals("io.vertx.core.Verticle")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a type is a collection type.
     */
    private boolean isCollectionType(Type type) {
        String typeName = type.tsym.getQualifiedName().toString();
        return typeName.startsWith("java.util.List") ||
               typeName.startsWith("java.util.Map") ||
               typeName.startsWith("java.util.Set") ||
               typeName.startsWith("java.util.Collection");
    }

    /**
     * Checks if a type is effectively immutable (can safely be made final).
     */
    private boolean isEffectivelyImmutable(Type type) {
        String typeName = type.tsym.getQualifiedName().toString();

        // Primitive wrappers and String are immutable
        return typeName.equals("java.lang.String") ||
               typeName.equals("java.lang.Integer") ||
               typeName.equals("java.lang.Long") ||
               typeName.equals("java.lang.Double") ||
               typeName.equals("java.lang.Boolean") ||
               typeName.equals("java.lang.Float") ||
               typeName.equals("java.lang.Byte") ||
               typeName.equals("java.lang.Short") ||
               typeName.equals("java.lang.Character");
    }
}
