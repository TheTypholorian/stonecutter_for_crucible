package test;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SubprojectsAccessPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.subprojects(it -> it.getExtensions());
    }
}
