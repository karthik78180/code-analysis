package com.yourcompany.platform.gradle.plugin.model;

/**
 * Represents a single violation found during analysis.
 */
public class Violation {

    private final String checkName;
    private final Severity severity;
    private final String file;
    private final int line;
    private final String message;
    private final String suggestion;

    public Violation(String checkName, Severity severity, String file,
                     int line, String message, String suggestion) {
        this.checkName = checkName;
        this.severity = severity;
        this.file = file;
        this.line = line;
        this.message = message;
        this.suggestion = suggestion;
    }

    public String getCheckName() {
        return checkName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s at %s:%d%s",
            severity, checkName, message, file, line,
            suggestion != null ? " (Suggestion: " + suggestion + ")" : "");
    }
}
