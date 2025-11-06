package com.yourcompany.platform.gradle.plugin.model;

import java.util.Objects;

/**
 * Maven coordinates for a dependency.
 */
public class DependencyCoordinate {

    private final String group;
    private final String artifact;
    private final String version;

    public DependencyCoordinate(String group, String artifact, String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getVersion() {
        return version;
    }

    public String toCoordinateString() {
        return String.format("%s:%s:%s", group, artifact, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyCoordinate that = (DependencyCoordinate) o;
        return Objects.equals(group, that.group) &&
               Objects.equals(artifact, that.artifact) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, artifact, version);
    }

    @Override
    public String toString() {
        return toCoordinateString();
    }
}
